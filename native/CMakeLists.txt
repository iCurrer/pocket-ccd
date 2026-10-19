# libfotufilm_android.so 的构建脚本。
#
# 这个库只做一件事：把 5 个 Halide AOT 内核和上游的 Android 适配层
# (Sources/FotufilmHalide/FotufilmHalideAndroid.cpp) 链成 Android 可加载的 .so。
# 不掺 Swift、不掺 Metal —— 上游适配层只 include HalideBuffer.h、生成的 kernel 头
# 和两个纯 C++ 头，正是为此设计的。
#
# 三个外部路径由调用方注入，不写死绝对路径：
#   -DFOTUFILM_ROOT=<上游源码根，含 Sources/FotufilmHalide>
#   -DFOTUFILM_KERNELS=<generate_halide_android 的输出目录，含 *.a 与 *.h>
#   -DHALIDE_INCLUDE=<Halide 安装目录>/include
#
# 交叉编译用法（NDK r28c）：
#   cmake -S native -B build/so-arm64 \
#     -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake \
#     -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_static \
#     -DCMAKE_BUILD_TYPE=Release -DFOTUFILM_ROOT=... -DFOTUFILM_KERNELS=... -DHALIDE_INCLUDE=...

cmake_minimum_required(VERSION 3.22)
project(fotufilm_android LANGUAGES CXX)

foreach(required FOTUFILM_ROOT FOTUFILM_KERNELS HALIDE_INCLUDE)
  if(NOT DEFINED ${required} OR "${${required}}" STREQUAL "")
    message(FATAL_ERROR "缺少构建变量 ${required}")
  endif()
endforeach()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS ON)

file(GLOB FOTUFILM_KERNEL_ARCHIVES "${FOTUFILM_KERNELS}/*.a")
list(LENGTH FOTUFILM_KERNEL_ARCHIVES kernel_count)
if(kernel_count EQUAL 0)
  message(FATAL_ERROR "在 ${FOTUFILM_KERNELS} 下没有找到 AOT kernel 归档（*.a）")
endif()
message(STATUS "fotufilm_android: 链接 ${kernel_count} 个 AOT kernel 归档")

# 头文件到底从哪儿来的，必须在 configure 期就打出来。
#
# 三个头分散在三处路径上：include/ 下的 FotufilmHalide.h、FotufilmConfigLayout.h、
# FotufilmAotVariants.h（后两个由 FotufilmHalide.h 间接 include），以及源码根下的
# FotufilmResolvedFrameParams.h。漏掉任何一个，编译期只会报一串"未声明的标识符"，
# 指向 jni.cpp 而不是真正缺的那一环 —— 上一轮就是这么丢掉 3 条错误原文的。
#
# 这里逐个 find_path 并打印命中位置，缺了就当场 FATAL_ERROR 并说明该去哪找。
#
# NO_CMAKE_FIND_ROOT_PATH 是必须的：NDK 的 android.toolchain.cmake 会设置
# CMAKE_FIND_ROOT_PATH（sysroot）且 CMAKE_FIND_ROOT_PATH_MODE_INCLUDE=ONLY，
# 于是 find_path 会把上面这些绝对路径**重新根化**（变成 <sysroot>/home/runner/...），
# 结果三个头明明就在磁盘上也一律落空 —— 而 file(GLOB) 不受影响，所以 .a 找得到、
# 头找不到。这层重定向只对 find_* 命令生效，正是这次 configure 失败的真因。
foreach(header_name FotufilmHalide FotufilmConfigLayout FotufilmResolvedFrameParams)
  find_path(FOTUFILM_HEADER_DIR_${header_name} "${header_name}.h"
    PATHS
      "${FOTUFILM_ROOT}/Sources/FotufilmHalide"
      "${FOTUFILM_ROOT}/Sources/FotufilmHalide/include"
      "${FOTUFILM_KERNELS}"
    NO_DEFAULT_PATH
    NO_CMAKE_FIND_ROOT_PATH)
  if(NOT FOTUFILM_HEADER_DIR_${header_name})
    message(FATAL_ERROR
      "找不到 ${header_name}.h。已查找：\n"
      "  ${FOTUFILM_ROOT}/Sources/FotufilmHalide\n"
      "  ${FOTUFILM_ROOT}/Sources/FotufilmHalide/include\n"
      "  ${FOTUFILM_KERNELS}\n"
      "请确认上游 checkout 完整（git submodule 与 Sources/FotufilmHalide 都在）。")
  endif()
  message(STATUS "fotufilm_android: ${header_name}.h -> ${FOTUFILM_HEADER_DIR_${header_name}}")
endforeach()

# FotufilmAotVariants.h 由 generate_halide_android 产出，或在 include/ 里随源码提供。
find_path(FOTUFILM_AOT_VARIANTS_DIR "FotufilmAotVariants.h"
  PATHS
    "${FOTUFILM_KERNELS}"
    "${FOTUFILM_ROOT}/Sources/FotufilmHalide/include"
  NO_DEFAULT_PATH
  NO_CMAKE_FIND_ROOT_PATH)
if(NOT FOTUFILM_AOT_VARIANTS_DIR)
  message(FATAL_ERROR
    "找不到 FotufilmAotVariants.h（FotufilmHalide.h 会 include 它）。"
    "已查找 ${FOTUFILM_KERNELS} 与 ${FOTUFILM_ROOT}/Sources/FotufilmHalide/include。")
endif()
message(STATUS "fotufilm_android: FotufilmAotVariants.h -> ${FOTUFILM_AOT_VARIANTS_DIR}")

# 遮蔽检测：生成目录里若也躺着一份同名头，include 顺序一变就会安静地选中它。
# 症状是 jni.cpp 里那三个引用（FOTUFILM_FRAME_REVERSAL / FOTUFILM_FRAME_CONFIGURATION_COUNT /
# FOTUFILM_CONFIG_DIFFUSION_RADIUS）同时报"未声明"，看起来像代码写错，其实是头换了。
foreach(header_name FotufilmHalide FotufilmConfigLayout FotufilmResolvedFrameParams FotufilmAotVariants)
  if(EXISTS "${FOTUFILM_KERNELS}/${header_name}.h")
    message(WARNING
      "生成目录 ${FOTUFILM_KERNELS} 里存在同名头 ${header_name}.h。"
      "本工程已把上游源码目录排在 include 路径前面，上游版本会胜出；"
      "但这层遮蔽关系需要记录在案，以免日后调换顺序时重现。")
  endif()
endforeach()

add_library(fotufilm_android SHARED
  fotufilm_jni.cpp
  "${FOTUFILM_ROOT}/Sources/FotufilmHalide/FotufilmHalideAndroid.cpp"
)

# 顺序有意义：上游源码目录排在最前，生成目录垫底。
# 生成目录里只该有变体 kernel 头（fotufilm_halide_android_*.h）；万一哪天它也产出同名
# 的 FotufilmHalide.h，排在前面就会安静地盖掉上游版本。
target_include_directories(fotufilm_android PRIVATE
  # 上游这个目录里既有 include/ 下的公共头，也有 include/ 之外的
  # FotufilmResolvedFrameParams.h 和 Pipeline/、Stages/ 子目录，两者都要在路径上。
  "${FOTUFILM_ROOT}/Sources/FotufilmHalide"
  "${FOTUFILM_ROOT}/Sources/FotufilmHalide/include"
  "${FOTUFILM_KERNELS}"
  "${HALIDE_INCLUDE}"
)

target_compile_definitions(fotufilm_android PRIVATE FOTUFILM_HALIDE_ANDROID_AOT=1)

# -fvisibility=hidden：只导出 JNIEXPORT 标注的符号，别把 Halide 内部符号泄出去。
# -ferror-limit=0：不限错误条数。默认 20 条足够，但一旦头文件层面出问题，
# 真正的第一条错误经常被后续连带错误挤出头几屏，日志被截断时就更找不到。
target_compile_options(fotufilm_android PRIVATE
  -O2 -ferror-limit=0 -fvisibility=hidden -fvisibility-inlines-hidden)

# --no-undefined：宁可在链接期失败，也不要产出一个 loadLibrary 时才炸的 .so。
# Halide 的 Android 运行时用 __android_log_print 和 dlopen，所以带上 log/android/dl。
target_link_libraries(fotufilm_android
  ${FOTUFILM_KERNEL_ARCHIVES}
  log
  android
  dl
)
target_link_options(fotufilm_android PRIVATE -Wl,--no-undefined -Wl,--gc-sections)

set_target_properties(fotufilm_android PROPERTIES
  OUTPUT_NAME "fotufilm_android"
  CXX_VISIBILITY_PRESET hidden
)
