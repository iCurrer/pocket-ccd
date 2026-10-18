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
