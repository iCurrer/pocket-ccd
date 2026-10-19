# 用 GitHub Actions 构建 Fotufilm Android 原生产物

**日期：** 2026-10-18  
**用途：** 不依赖本机 WSL/macOS/Linux，在 GitHub Actions Linux 上构建：

- `fotufilm` CLI；
- 3 个 FSWP pack；
- Android arm64 CPU AOT kernels；
- 上游 `verify-android-aot.sh` 的 parity 日志。

**APK 不在这里构建。** APK 继续用 Windows 的 Android Studio 编；Actions 只产出 native 产物。

---

## 1. 前置

当前仓库还没有 GitHub remote。先把仓库推到 GitHub：

```bash
git remote add origin https://github.com/<你的账号>/<仓库名>.git
git push -u origin main
```

然后确认：

- GitHub 仓库 Actions 已启用；
- 仓库是 public 或 private（private 会消耗 Actions 分钟）；
- workflow 文件已经提交：

```text
.github/workflows/fotufilm-android-native.yml
```

---

## 2. 手动触发

GitHub → Actions → **Fotufilm Android native POC** → Run workflow。

输入：

```text
gold200 portra400 trix400
```

也可以只跑一个：

```text
gold200
```

---

## 3. 它做什么

```text
1. checkout Pocket CCD
2. checkout fotufilm-engine @ 2d07c48
3. 安装 Swift / LLVM 22 / Android NDK / Halide 依赖
4. 构建 Halide（首次 30+ 分钟；之后走 cache）
5. swift build fotufilm
6. 生成 .fswp packs
7. generate_halide_android -> arm64 CPU kernels
8. verify-android-aot.sh -> parity 日志
9. 上传 artifacts
```

首次运行可能 45–90 分钟；Halide cache 命中后会快很多。

---

## 4. 下载产物

Actions run 页面底部 **Artifacts**：

```text
fotufilm-android-poc
```

包含：

```text
film/gold200.fswp
film/portra400.fswp
film/trix400.fswp
aot/fotufilm_halide_android_develop.a
aot/fotufilm_halide_android_print*.a
android-aot-parity.log
```

---

## 5. 拿到 Windows 工程

解压后：

```text
app/src/main/assets/film/*.fswp
app/src/main/jniLibs/arm64-v8a/...
```

但注意：**当前 workflow 还没有编 JNI `.so`**，只产出了 AOT kernels 和 packs。  
下一步 FOT-004 需要：

- 写 `jni/fotufilm_jni.cpp`；
- 用 NDK 把 AOT 静态库链接成 `libfotufilm_android.so`；
- 在 workflow 里再加一个 `Build JNI .so` step。

---

## 5.1 2026-10-18 修正记录（首次 run 前）

原 workflow 有一处**必然失败**和若干高概率失败点，已逐项核对上游源码后修掉。
核对方式：GitHub API 读取 `DhaliwalX/fotufilm-engine @ 2d07c48` 的真实文件内容。

| # | 问题 | 证据 | 修正 |
|---|---|---|---|
| 1 | `Export Halide root` 步骤必定失败 | `echo HALIDE_ROOT=... >> $GITHUB_ENV` 只对**后续**步骤生效，当前 shell 里 `$HALIDE_ROOT` 为空 → `test -f "$HALIDE_ROOT/include/Halide.h"` 实际执行 `test -f /include/Halide.h` → 退出 1 | 改成先用局部变量 `HALIDE_INSTALL` 校验，再写入 `GITHUB_ENV` |
| 2 | Halide 缓存丢失风险 | `actions/cache@v4` 在 job 失败时不保存 | 拆成 `cache/restore` + 编完立刻 `cache/save` |
| 3 | 缺 `libclang-*-dev` | `build-halide-linux.sh` 走 `cmake find_package(Clang)`，`llvm-*-dev` 不含 Clang 的 CMake config | LLVM 步骤补 `libclang-${LLVM_VERSION}-dev` |
| 4 | 缺 `libffi-dev` / `libelf-dev` | 上游脚本自身错误提示列出 libffi；Halide 需要 libelf | 加入 apt 列表 |
| 5 | parity 会重复编译一遍 | `verify-android-aot.sh` 在未设 `FOTUFILM_PARITY_CLI` 时执行 `swift run -c release fotufilm` | 设为 `.build/release/fotufilm` |
| 6 | `--list-stocks \| head` 吞掉失败 | GitHub 默认 shell 是 `bash -e`，无 `pipefail`，管道退出码取 `head` | 去掉管道，改为失败即中断 |
| 7 | parity 失败时 artifacts 全丢 | 后续步骤默认不执行 | `Collect` / `Upload` 改 `if: always()`，设 `retention-days: 14` |
| 8 | 磁盘可能不够 | Halide 源码 + 构建产物约 8–10GB，runner `/` 约 14GB | 新增 `Free disk space` 步骤并打印 `df -h` |
| 9 | 子模块缺失会静默失败 | `build-halide-linux.sh` 依赖 `third_party/Halide` 且用 `git ls-tree HEAD` | 新增 `Verify upstream layout` 前置检查 |

**已核实的事实（不是推断）：**

- 上游子模块只有 `third_party/Halide` → `github.com/halide/Halide`，pin `58868f0c`；
- 该 pin 的 Halide `CMakeLists.txt` 写 `Halide_LLVM 21...99 REQUIRED` → **LLVM 22 满足最低要求**；
- `generate_halide_android OUTPUT_DIR [--arm32 | --host]`，不传 flag 才是 arm64 Android 目标（原 workflow 的调用方式正确）；
- `resolve-halide-toolchain.sh` **认可 `HALIDE_ROOT`**，因此 parity 步骤能找到自建 Halide；
- stocks 经 SwiftPM `.copy("Stocks")` 打包，`gold200.json` / `portra400.json` / `trix400.json` 均存在；
- `NDK 28.2.13676358` 在 `dl.google.com/android/repository/repository2-3.xml` 中存在。

**仍未验证的（必须靠 run 才知道）：**

- apt.llvm.org 上 LLVM 22 的 Ubuntu 24.04 包是否带 RTTI（Halide 的 `Halide_feature(... DEPENDS LLVM_ENABLE_RTTI)`）；
- `swift build` 本仓库（2500+ 文件）在 runner 上的耗时；
- 4 核 runner 上 Halide 全量编译是否会撞 360 分钟上限。

（`setup-swift` 的 6.1 可用性已在首次 run 中被证实，见 §5.2。）

---

## 5.2 2026-10-18 首次 run 结果（run 39140385613，失败）

前 10 步全绿，卡在第 11 步 `Build Halide` 的 **CMake configure**，耗时约 3 分钟。

```
-- Could NOT find LLD (missing: LLD_DIR)
CMake Error at .../FindPackageHandleStandardArgs.cmake:233 (message):
  Could NOT find Halide_LLVM (missing: WebAssembly) (found suitable version
  "22.1.8", required range is "21...99")

      Reason given by package: WebAssembly was not found because liblld is missing.
      Did you `apt install liblld-dev` or `brew install lld`?
Call Stack:
  cmake/FindHalide_LLVM.cmake:109 (find_package_handle_standard_args)
  CMakeLists.txt:194 (find_package)
```

**根因**（`cmake/FindHalide_LLVM.cmake:60-77`，pin `58868f0c`）：

apt 的 LLVM 22 在 `LLVM_TARGETS_TO_BUILD` 里带 `WebAssembly`，于是 Halide 会对该组件执行
`find_package(LLD "${Halide_LLVM_VERSION}" EXACT)`；找不到就把 `Halide_LLVM_WebAssembly_FOUND` 置 0，
最终以 `(missing: WebAssembly)` 整体失败。

**装错的原因是包名**：`lld-22` 只提供可执行文件 `/usr/lib/llvm-22/bin/lld`；
提供 CMake 配置 `lib/cmake/lld/LLDConfig.cmake` 的是 **`liblld-22-dev`**。
`llvm.sh` 和 `build-halide-linux.sh` 的报错文案都写作 `liblld-dev` / `lld-devel`，容易误导。

**修正：**

- LLVM 步骤补 `liblld-${LLVM_VERSION}-dev`（已在 `apt.llvm.org/noble` 的 22 源中确认存在）；
- 新增 3 秒内失败的预检：`LLVMConfig.cmake` / `ClangConfig.cmake` / `LLDConfig.cmake` 三个文件都必须在，
  避免每次都要等 3 分钟的 configure 才发现缺包；
- `Export Halide root` 不再硬性要求 `libHalide.so`：Debian 的 LLVM 开了 `LLVM_LINK_LLVM_DYLIB`，
  Halide 会走 shared 模式产出 `.so`；但静态构建只会产 `.a`，两者都接受并打印实际模式。

**这次 run 顺带证实的事实：**

- `swift-actions/setup-swift@v2` 的 **6.1 在 ubuntu-24.04 上可用**（步骤 7 绿）；
- `llvm.sh 22` 在 ubuntu-24.04 上可用，装到的是 **LLVM 22.1.8**，且 `-dev` 包带 `LLVMConfig`/`ClangConfig`（步骤 8 绿）；
- `sdkmanager` 装 **NDK 28.2.13676358** 成功（步骤 9 绿）；
- Halide configure 已找到 `FFI` / `ZLIB` / `zstd` / `LibXml2` / `Threads`，说明 `libffi-dev` 等补充是正确的；
- Halide 源码 + 4 个 Vulkan patch 就位（步骤 4 绿）。

---

## 5.3 2026-10-18 第二次 run（run 39141045564）：LLD 修好了，但 pack 生成是 macOS-only

### 这一轮解决了什么

第 11 步 `Build Halide` **成功**——`liblld-22-dev` 是对的。日志实证：

```
-- Found Halide_LLVM: /usr/lib/llvm-22/cmake/LLVMConfig.cmake
   (found suitable version "22.1.8", required range is "21...99")
   found components: WebAssembly X86 AArch64 ARM Hexagon NVPTX PowerPC RISCV
[1978/1978] Linking CXX shared module src/autoschedulers/anderson2021/libautoschedule_anderson2021.so
```

`WebAssembly` 组件已经在 found 列表里，`libHalide.so.22.0.0` 已链出，第 13 步 `Export Halide root` 也过了。
Halide 编译只用 **3.7 分钟**（4 核、`-DWITH_TESTS=OFF` 等全关），远低于原估的 30–60 分钟；
`Build fotufilm CLI` 2.1 分钟。整条 Linux 链只剩最后两步没验证过。

### 新的失败：`--dump-wasm-pack` 在 Linux 上是硬 fail

```
+ .build/release/fotufilm --dump-wasm-pack artifacts/film/gold200.fswp --stock gold200 --pack-size 1600x1200
Image I/O requires macOS (ImageIO). The FotufilmCore library itself is portable.
Error: Process completed with exit code 1.
```

**根因（读 `Sources/fotufilm/main.swift` 得出）：**

| 行 | 内容 |
|---|---|
| 757 | `#if canImport(ImageIO)` —— 从这里开始，整个 main 流程被门控 |
| 1158 | `if let packPath = flags["--dump-wasm-pack"] { ... }` —— pack 导出**在这个门控内** |
| 1485–1486 | `#else` / `fail("Image I/O requires macOS (ImageIO)...")` |

也就是说，Linux 上编译出的 `fotufilm` 二进制里，`--dump-wasm-pack` 的实现根本没被编进去，
程序一进来就走到那句 `fail`。这也解释了为什么 `--list-stocks` 能正常工作——它在第 757 行之前就 `exit(0)` 了。

**这不是我们配置错了。** 上游自己的 CI（`.github/workflows/ci.yml @ 2d07c48`）是 `runs-on: macos-26`，
从来没在 Linux 上跑过 CLI。`tools/verify-android-aot.sh` 里也调用了同一个 `--dump-wasm-pack`，
所以**它同样只能在 macOS 上跑**。

### 方案：按能力拆成两个 job

pack 的内容是纯参数序列化（`FSWP` 头 + 配置 floats + 曝光/纸质 LUT + 尺寸阶梯），与平台无关，
所以 macOS 产出的 pack 与 Linux 会产出的 pack 是同一串字节。据此：

```
packs job（macos-14）
  swift build -c release --product fotufilm   ← 不依赖 FotufilmHalide，不用 brew install halide
  --dump-wasm-pack  <stock>__1600x1200.fswp   × 3
  --dump-wasm-pack  example-negative-400__96x64.fswp   ← 给 parity 脚本用
  → artifact: fotufilm-packs

android-native job（ubuntu-24.04, needs: packs）
  Halide（缓存命中，跳过编译）
  generate_halide_android → arm64 CPU kernels
  verify-android-aot.sh，FOTUFILM_PARITY_CLI 指向 .github/scripts/fotufilm-pack-shim.sh
```

**关于 shim**（`.github/scripts/fotufilm-pack-shim.sh`）：

`verify-android-aot.sh` 硬编码要调 CLI 生成 `example.fswp`。shim 解析
`--dump-wasm-pack <out> --stock <id> --pack-size <WxH>`，把 `$FOTUFILM_PACK_DIR/<stock>__<WxH>.fswp`
复制到 `<out>`，并在日志里打印来源。它**不伪造任何数据**，只是把 macOS 真实 CLI 的输出搬过来。
parity 真正要验的 AOT vs JIT 对比仍然在 Linux 本地跑。

文件命名必须精确匹配 `<stock>__<WxH>.fswp`，两边常量都在 workflow 的 `env:` 里
（`PARITY_STOCK` / `PARITY_SIZE`），改一处即可。

### 仍然没有解决的部分

- **`libfotufilm_android.so` 依然不存在**——那是 FOT-004 的 JNI 工作，不在本 workflow 范围；
- 磁盘：Halide 缓存在本次已保存（key `...-v2`），后续 run 会跳过编译，但缓存约几百 MB；
- macOS job 时长还没测过（Swift 全量 release 编译 + 4 次 pack 导出）；
- pack 只按 `1600x1200`（约 1.9MP）导出，**不代表 12MP 输出**。

---

## 5.4 2026-10-18 第三次 run（run 39143317856）：全绿

```
status: completed   conclusion: success   墙钟 12.0 min
packs           3.6 min   （macos-26）
android-native  8.2 min   （ubuntu-24.04，Halide 命中缓存）
```

### 产物（artifact `fotufilm-android-poc`，10.5 MB）

```
film/gold200__1600x1200.fswp                      1.83 MB   FSWP v2 1600x1200
film/portra400__1600x1200.fswp                    1.84 MB   FSWP v2 1600x1200
film/trix400__1600x1200.fswp                      1.74 MB   FSWP v2 1600x1200
film/example-negative-400__96x64.fswp             1.83 MB   FSWP v2 96x64

aot/fotufilm_halide_android_develop.a             2.26 MB   ← arm64 CPU AOT
aot/fotufilm_halide_android_print.a                86 KB
aot/fotufilm_halide_android_print_monochrome.a     84 KB
aot/fotufilm_halide_android_print_reversal.a       59 KB
aot/fotufilm_halide_android_print_reversal_monochrome.a  57 KB
aot/*.h                                          5 份头文件

android-aot-parity.log
aot.bin / jit.bin                                 各 4.5 MB（parity 中间产物）
```

**parity 日志原文：**

```
Wrote Android kernels to "build/android-parity/kernels"
shim: example-negative-400 @ 96x64 <- .../prebuilt-packs/example-negative-400__96x64.fswp (1833260 bytes)
Android AOT / CPU reference: 64 cases pass, including RGB/donor exposure,
gate bypass, tile origins, compact output/guards and invalid-field/region rejection.
```

上游脚本里那 64 组断言（RGB/donor 曝光、gate bypass、tile origin、guard、非法 region 拒绝）
全部通过，误差阈值 `< 1e-4`。**这是 Phase 0 想证明的东西，现在证明了。**

### 本轮修掉的两个问题

| 问题 | 现象 | 原因 | 修法 |
|---|---|---|---|
| macOS 版本太低 | `MTLCompileOptions has no member 'mathMode'` + 一批 type-check 超时 | `macos-14` 自带 Xcode 15.4 / Swift 5.10；`mathMode` 要 macOS 15 SDK | 换 `macos-26`（与上游 ci.yml 一致），并在 Toolchain 步骤解析 Xcode 主版本，`< 16` 时 3 秒内失败 |
| artifact 多一层目录 | `test -s prebuilt-packs/xxx.fswp` 失败 | `upload-artifact` 对多路径上传按**最小公共父目录**重组，`packs/*.fswp` + `stocks.txt` 导致根变成 `packs/` | 上传改为单目录 `third_party/fotufilm/packs/`；Linux 侧用 `find` 定位并 export `FOTUFILM_PACK_DIR`，与层级解耦 |

### 耗时基准（重要）

| 阶段 | 首次（无缓存） | 有缓存 |
|---|---|---|
| Halide 编译 | 3.7 min | 0（`cache-hit`） |
| macOS `swift build -c release --product fotufilm` | 3.2 min | 3.2 min（未缓存） |
| Android AOT kernels | 1.3 min | 1.3 min |
| parity（含 AOT/JIT 各跑 64 例） | 5.1 min | 5.1 min |
| **合计墙钟** | ~12 min | ~12 min |

macOS 那段占了 1/3 的墙钟，如果后面要频繁迭代 Linux 侧，
值得给 `packs` job 加一层 `actions/cache`（key 用 commit + stocks 输入）。

### 现在还没有的

- **`libfotufilm_android.so`**：本次完全不涉及。FOT-004 才写 JNI、用 NDK 链接
  上面这 5 个 `.a`、产出 `.so`，再拷回 Windows 工程给 Android Studio 打包 APK；
- pack 是 `1600x1200`（约 1.9 MP），**不能当 12 MP 的画质或性能结论**；
- 没有在真机上跑过产物——AOT `.a` 是否能在 arm64 Android 上正常加载，要等 FOT-004 的 `.so` + 一台设备。

---

## 5.5 2026-10-19 pack 帧尺寸改为可配置，默认 12MP

FOT-004 的验收是"真机处理 1 张 12MP"，但原来的 `PACK_SIZE` 是写死的 `1600x1200`，
`workflow_dispatch` 覆盖不了。改成输入项，默认 `4000x3000`（4:3 的 12MP）。

### 为什么不能"用小 pack 外推"

`Sources/fotufilm/main.swift` 的 `packFrameSize()` 上面有一段注释（约 1123 行）：

> Every spatial parameter in a pack is a diffusion length in millimetres multiplied by this
> frame's pixels-per-mm, so the size is part of the export, not a detail of the caller.
> A frame of another size needs another pack.

即：扩散半径、mottle 相关长度、金字塔抽稀步长等**空间参数都按该帧的像素/毫米烘焙过了**。
所以

- 12MP 不能拿 1.9MP 的 pack 缩放得到，必须重新导出；
- **实际采集分辨率必须与 pack 帧尺寸完全相等**。若设备原生输出 4032×3024，
  要先缩放到 4000×3000 再送进引擎，而不是拿 4000×3000 的 pack 去处理 4032×3024；
- 1.9MP 下的耗时和峰值内存不能外推到 12MP——工作集和半径都在放大。

这条契约已写入 `DECISION_LOG.md` D-037。

### 怎么用

```text
Actions → Fotufilm Android native POC → Run workflow
  stocks:    gold200 portra400 trix400
  pack_size: 4000x3000
```

产物文件名会跟着变：`gold200__4000x3000.fswp`。parity 用的 `example-negative-400__96x64.fswp`
不受影响（`PARITY_SIZE` 是独立的 env）。

### 防呆

- `PACK_SIZE` 从输入求值，带 `'4000x3000'` 字面量兜底，非 dispatch 触发不会产出 `<stock>__.fswp`；
- `Dump FSWP packs` 第一步断言 `^[0-9]+x[0-9]+$`，格式不对在 macOS 编译前就失败，
  不会等 4 分钟才发现文件名是坏的。

### 本轮状态（run 39145196597）

`packs` job 成功，`Dump FSWP packs` 与 `Upload packs` 均 passed。artifact `fotufilm-packs` 3.7 MB。

下载后逐个核对头部（小端 `FSWP` + uint32 version + int32 width/height）：

| 文件 | magic | version | 尺寸 | 像素 | 大小 |
|---|---|---|---|---|---|
| `gold200__4000x3000.fswp` | FSWP | 2 | 4000×3000 | 12,000,000 | 1.83 MB |
| `portra400__4000x3000.fswp` | FSWP | 2 | 4000×3000 | 12,000,000 | 1.84 MB |
| `trix400__4000x3000.fswp` | FSWP | 2 | 4000×3000 | 12,000,000 | 1.84 MB |
| `example-negative-400__96x64.fswp` | FSWP | 2 | 96×64 | 6,144 | 1.83 MB |

`[F]` 正好 12 MP。也印证了 pack 体积主要由 LUT 决定，1.9 MP 与 12 MP 的体积几乎相同（1.83 vs 1.84 MB）。

> 注：头部字段是**小端**。用大端读会得到 `version=33554432`、`width=1610612736` 这类 `value << 24` 的怪数字——那是字节序错了，不是文件坏了。

---

## 5.6 2026-10-19 FOT-004 首次 .so 构建（run 39193539878）：编译期失败

commit `1864f56` 加入 `native/fotufilm_jni.cpp` + `native/CMakeLists.txt` 后的第一次运行。

### 走到哪一步

| 步骤 | 结果 | 耗时 |
|---|---|---|
| `packs` job 全部 8 步 | success | 2m36s |
| `Build Android CPU AOT kernels` | success | 60s |
| `Run upstream Android AOT parity` | success | 3m47s |
| **`Build libfotufilm_android.so`** | **failure** | **1s** |
| `Verify .so architecture and JNI exports` | skipped | — |

摘要只有三行：

```text
2 warnings and 3 errors generated.
gmake[2]: *** [CMakeFiles/fotufilm_android.dir/build.make:79: CMakeFiles/fotufilm_android.dir/fotufilm_jni.cpp.o] Error 1
gmake[1]: *** [CMakeFiles/Makefile2:87: CMakeFiles/fotufilm_android.dir/all] Error 2
```

### 从这三个数字读出来的

1. **`gmake` 而不是链接器报错** → 失败在编译，不在链接。`--no-undefined` 那层断言根本没轮到。
2. **只有 `fotufilm_jni.cpp.o`** → 上游的 `FotufilmHalideAndroid.cpp` 没报错（`Waiting for unfinished jobs` 说明它还在编，被中断）。
3. **1 秒** → 预处理阶段或极早的语义检查就挂了。`FotufilmConfigLayout.h` 有 18271 个枚举值，正常编译一个 TU 要好几秒；1 秒内结束说明 clang 没走多远。

### 已排除的

对着 pin `2d07c48` 逐项核过：

- `fotufilm_halide_process_strip` 的 21 个参数（6 指针 + 8 尺寸/原点 + 4 表 + 维度 + mask + seed）与 `include/FotufilmHalide.h` 的声明**逐位对齐**；
- `fotufilm::ResolvedFrameParams` 的构造签名是 `(const float*, int32_t, int32_t, uint32_t, int32_t, int32_t, int32_t)`，`nativeMaxHalo` 传 7 个参数，类型能隐式转换；
- 用到的成员 `mtf_radius_0..2`、`mtf_luma_radius`、`coupler_radius`、`adjacency_radius`、`adjacency_secondary_radius`、`fringe_radius`、`grain_radius`、`mottle_radius`、`print_mtf_radius`、`halation_radius_0..2` **全部存在**；
- `FOTUFILM_CONFIG_DIFFUSION_RADIUS = 8740`（占 3 槽），`FOTUFILM_FRAME_CONFIGURATION_COUNT = 18271`，都在 `include/FotufilmConfigLayout.h` 里；
- 四个头在 pin 里都在：`FotufilmHalide.h` / `FotufilmConfigLayout.h` / `FotufilmAotVariants.h` 在 `Sources/FotufilmHalide/include/`，`FotufilmResolvedFrameParams.h` 在 `Sources/FotufilmHalide/`。

所以**不是调用写错，也不是头缺失**。

### 最可能的根因：同名头遮蔽

`jni.cpp` 里只引用了三个来自上游头的标识符：

```cpp
FOTUFILM_FRAME_REVERSAL            // nativeMaxHalo
FOTUFILM_FRAME_CONFIGURATION_COUNT // parsePack
FOTUFILM_CONFIG_DIFFUSION_RADIUS   // nativeMaxHalo
```

三个，正好对上 **3 errors**。而原 `CMakeLists.txt` 把 `${FOTUFILM_KERNELS}`（AOT 生成目录）排在
include 路径**第一位**——只要生成目录里也产出一份同名 `FotufilmHalide.h`，它就会安静地盖掉上游那份，
三个符号同时"未声明"。

### 这一轮改了什么

`native/CMakeLists.txt`：

- include 顺序改为**上游源码目录在前、生成目录垫底**；
- configure 期对四个头逐个 `find_path` 并 `message(STATUS)` 打印**实际命中路径**，缺一个就 `FATAL_ERROR`；
- 若生成目录存在同名头，打 `WARNING` 记录这层遮蔽关系；
- 编译选项加 `-ferror-limit=0`，不再让后续连带错误把第一条挤掉。

`.github/workflows/fotufilm-android-native.yml`：

- configure 与 build 输出分别 `tee` 到 `build/so-configure.log`、`build/so-build.log`；
- 两个日志都进 `Collect artifacts`（`if: always()`），网页日志被截断时仍有原始证据。

### 还缺什么

**那 3 条 `error:` 的原文**（含 `文件:行:列`）。上面是推理，不是结论——若加固后的 configure 打印出的
头路径全部指向 `Sources/FotufilmHalide`，遮蔽假设即被否掉，得回去看真正的未声明标识符是什么。

### 定论（run 39197692827）：遮蔽假设被否，真因是两处独立缺陷

加固后的 configure 打印出四个头的实际命中路径全部落在
`third_party/fotufilm/Sources/FotufilmHalide/`，**"同名头遮蔽"假设就此否掉**。真正的两个原因是：

**1）configure 找不到头：`find_path` 被 NDK sysroot 重根化（已修：0c6610c）**

NDK 的 `android.toolchain.cmake` 会设置 `CMAKE_FIND_ROOT_PATH`（指向 NDK sysroot），并把
`CMAKE_FIND_ROOT_PATH_MODE_INCLUDE` 定为 `ONLY`。这使 `find_path` 把传入的绝对搜索路径
**重新根化**为 `<sysroot>/home/runner/...`，于是三个头明明在磁盘上也一律落空；而
`file(GLOB "${FOTUFILM_KERNELS}/*.a")` 不是 `find_*` 命令、不受影响 —— 这正好解释了
"`.a` 找得到、头找不到"这个反直觉现象。

> 佐证：AOT 生成步骤用**相对路径** `-ISources/FotufilmHalide/include` 编译
> `tools/generate_halide_android.cpp`（其第 3 行 `#include "../Sources/FotufilmHalide/Pipeline/Cpu.h"`）
> 是成功的，说明该目录在检出里确实存在。

修法：两处 `find_path` 补 `NO_CMAKE_FIND_ROOT_PATH`。

**2）编译期 3 条 error：most vexing parse（本轮修复）**

`native/fotufilm_jni.cpp:270` 原写作：

```cpp
std::vector<uint8_t> raw(size_t(length));
```

C++ 把它解析成**函数声明** `std::vector<uint8_t> raw(size_t length)`（即"类型 (参数名)"形式），
`raw` 因此成了函数类型，紧接的 271/279 行 `raw.data()` / `raw.size()` 报
`member reference base type 'std::vector<uint8_t> (size_t)' ... is not a structure or union`
—— 这就是那 3 条 error 的真正来源，与上游头无关。

修法：改写为 `static_cast<size_t>(length)`，彻底消除歧义。

### 当前进度

- configure：**通过**（头命中路径已打印）；
- 以上 3 条 error 已定位并修复，`Build libfotufilm_android.so` 与 `Verify .so` 待下一次 run 验证。

## 6. 常见失败

| 失败点 | 原因 | 处理 |
|---|---|---|
| `setup-swift` 找不到 6.2 | runner/action 版本不匹配 | 改成可用的 Swift 版本（如 6.1） |
| `llvm.sh 22` 失败 | apt.llvm.org 不支持当前 runner | 用 Ubuntu 22.04 runner 或调整 LLVM 版本 |
| `build-halide-linux.sh` 失败 | 缺 Vulkan/LLVM 组件 | 确认 `libvulkan-dev`、`libclang-22-dev`、`lld-22`、`llvm-22-dev`、`libffi-dev` |
| cmake 报 `find_package(Clang)` 找不到 | 只装了 `llvm-22-dev`，没装 `libclang-22-dev` | 补 `libclang-${LLVM_VERSION}-dev` |
| cmake 报 `Could NOT find LLD (missing: LLD_DIR)` 并连带 `Halide_LLVM (missing: WebAssembly)` | 只装了 `lld-22`（可执行文件），缺 `liblld-22-dev`（CMake config） | 补 `liblld-${LLVM_VERSION}-dev`。**这是首次 run 的真实失败**，见 §5.2 |
| cmake 报 LLVM 未启用 RTTI | apt 的 LLVM 构建可能与 Halide 的 RTTI 需求不符 | 换 `apt.llvm.org` 的对应版本，或改用 Locally-built LLVM |
| `sdkmanager` 找不到 | runner 没有 cmdline-tools | 先安装 Android command-line tools |
| AOT parity 失败 | NDK/Halide 目标不匹配 | 固定 NDK 版本；检查 `ANDROID_NDK_ROOT` |
| `Image I/O requires macOS (ImageIO)` | `--dump-wasm-pack` 与 `verify-android-aot.sh` 只在 macOS 可用 | 由 `packs` job 在 macOS 产 pack，Linux 侧用 shim 消费。见 §5.3 |
| macOS 报 `MTLCompileOptions has no member 'mathMode'` / 大量 type-check 超时 | runner 的 Xcode 太旧（15.4 = Swift 5.10），`mathMode` 要 macOS 15 SDK | 用 `macos-26`（或 Xcode 16+）。见 §5.4 |
| `test -s prebuilt-packs/xxx.fswp` 失败 | `upload-artifact` 多路径上传时按最小公共父目录重组，多出一层 `packs/` | 上传单目录；消费端用 `find` 定位，别写死层级 |
| cache 太大/超时 | Halide 构建缓存 | 删除 cache 或缩短 cache 路径 |

---

## 7. 为什么先只做 POC

GitHub Actions 能解决“没有 Linux 构建机”的问题，但不能替我们写：

- JNI bridge；
- FSWP pack parser 的 Android 侧；
- CameraX 采集；
- 色彩/存储；
- 真机性能验证。

所以 workflow 的第一步是 **Phase 0 POC**：证明 pack + AOT 能在 CI 生成，且 parity 通过。  
完成 FOT-004 后，再把 `.so` 加进 workflow。
