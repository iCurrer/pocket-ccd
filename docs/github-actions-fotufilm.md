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
