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

add_library(fotufilm_android SHARED
  fotufilm_jni.cpp
  "${FOTUFILM_ROOT}/Sources/FotufilmHalide/FotufilmHalideAndroid.cpp"
)

target_include_directories(fotufilm_android PRIVATE
  "${FOTUFILM_KERNELS}"
  # 上游这个目录里既有 include/ 下的公共头，也有 include/ 之外的
  # FotufilmResolvedFrameParams.h 和 Pipeline/、Stages/ 子目录，两者都要在路径上。
  "${FOTUFILM_ROOT}/Sources/FotufilmHalide"
  "${FOTUFILM_ROOT}/Sources/FotufilmHalide/include"
  "${HALIDE_INCLUDE}"
)

target_compile_definitions(fotufilm_android PRIVATE FOTUFILM_HALIDE_ANDROID_AOT=1)

# -fvisibility=hidden：只导出 JNIEXPORT 标注的符号，别把 Halide 内部符号泄出去。
target_compile_options(fotufilm_android PRIVATE -O2 -fvisibility=hidden -fvisibility-inlines-hidden)

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
