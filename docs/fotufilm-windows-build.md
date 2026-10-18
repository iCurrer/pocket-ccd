# Windows-only 环境怎么构建 Fotufilm Android 移植

**日期：** 2026-10-18  
**适用：** 只有 Windows + Android Studio，没有 macOS/Linux 主机。  
**结论：** 不要在原生 Windows 上硬编 Fotufilm Swift/Halide；用 **WSL2 Ubuntu 做构建机**，Android Studio 仍留在 Windows。

---

## 1. 为什么不能直接在 Windows 上编

Fotufilm 的构建链是：

- Swift Package（`swift build`）；
- Halide AOT generator；
- Android NDK / arm64 target。

上游 `Package.swift` 只声明 macOS / iOS 平台；README 的构建说明是 Xcode/Halide（macOS）或 Linux。没有 Windows 构建支持。  
在 Windows 上装 Swift + Halide + NDK 去硬编，问题会集中在：

- Swift Foundation / SwiftPM 在 Windows 的依赖差异；
- Halide Windows 版到 Android target 的交叉编译支持；
- `FotufilmCore` 的 Linux/Apple 条件编译；
- 上游脚本都是 bash。

所以最稳的是：**WSL2 Linux 负责编译，Windows 负责 Android Studio、adb、安装和调试。**

---

## 2. 推荐架构

```text
Windows
 ├── Android Studio / Gradle / adb / 真机
 ├── D:\Android\pocket ccd        （现有 Android 工程）
 └── WSL2 Ubuntu
      ├── Swift Linux toolchain
      ├── Halide SDK（含 Android target）
      ├── Android NDK（Linux 版）
      ├── fotufilm-engine 源码
      └── 产出：libfotufilm_android.so + .fswp packs
           └── 复制到 Windows 工程 jniLibs / assets
```

WSL2 不需要 GPU；先做 CPU AOT。Vulkan 是后续阶段。

---

## 3. 安装 WSL2

在 Windows 管理员 PowerShell：

```powershell
wsl --install -d Ubuntu
```

然后重启，首次启动 Ubuntu 设置用户名/密码。

检查：

```powershell
wsl --status
wsl -l -v
```

要求：

- WSL 版本 2；
- BIOS 开启虚拟化（SVM/VMX）；
- Windows 功能启用“虚拟机平台 / Windows Subsystem for Linux”。

如果公司策略禁止 WSL，直接看第 7 节 GitHub Actions。

---

## 4. WSL Ubuntu 内安装依赖

以下在 WSL 终端执行，不要用 `/mnt/c` 作为源码目录（I/O 慢）。

```bash
sudo apt update
sudo apt install -y \
  build-essential cmake ninja-build clang lld git curl unzip \
  libxml2-dev zlib1g-dev libjpeg-dev libpng-dev libtiff-dev \
  liblcms2-dev libheif-dev libopenexr-dev pkg-config python3
```

### Swift Linux toolchain

从 <https://www.swift.org/install/linux/> 安装对应 Ubuntu 版本的 Swift 6.x toolchain，确认：

```bash
swift --version
```

### Android NDK（Linux 版）

从 Android NDK 官方下载 Linux zip，解压到例如：

```bash
export ANDROID_NDK_ROOT=$HOME/android-ndk-r28
export PATH=$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH
```

不要直接用 Windows 的 `C:\...\ndk` 路径跑 Linux clang；可以用 Windows 的 NDK 仅给 Android Studio/Gradle 用，WSL 里另装 Linux NDK。

### Halide

需要 **带 Android target 的 Halide**。两条路：

1. 上游脚本：`tools/build-halide.sh`（需要 LLVM/LLD/CMake，耗时长）；
2. 官方 Halide release/SDK，确认包含 Android codegen。

```bash
export HALIDE_ROOT=$HOME/halide
# 检查
ls $HALIDE_ROOT/include/Halide.h
ls $HALIDE_ROOT/lib/libHalide.so
```

---

## 5. 构建 Fotufilm

源码放到 WSL 家目录：

```bash
cd ~
git clone https://github.com/DhaliwalX/fotufilm-engine.git
cd fotufilm-engine
git checkout 2d07c48c14199be24972db760a7631dc95f74012

export HALIDE_ROOT=$HOME/halide
swift build -c release --product fotufilm
```

列出 stock：

```bash
swift run -c release fotufilm --list-stocks | head
```

生成 3 个 pack（先选 Gold200 / Portra400 / TriX400）：

```bash
mkdir -p ~/fotufilm-packs
swift run -c release fotufilm \
  --dump-wasm-pack ~/fotufilm-packs/gold200.fswp \
  --stock gold200 --pack-size 1600x1200

swift run -c release fotufilm \
  --dump-wasm-pack ~/fotufilm-packs/portra400.fswp \
  --stock portra400 --pack-size 1600x1200

swift run -c release fotufilm \
  --dump-wasm-pack ~/fotufilm-packs/trix400.fswp \
  --stock trix400 --pack-size 1600x1200
```

> `--pack-size`/参数以 `fotufilm --help` 为准；这里只是 Phase 0 的 4:3 参考尺寸。

---

## 6. 生成 Android AOT

上游脚本先验证主机：

```bash
export ANDROID_NDK_ROOT=$HOME/android-ndk-r28
bash tools/verify-android-aot.sh
```

它会：

1. 编 `generate_halide_android`；
2. 生成 arm64 kernels；
3. 编 `FotufilmHalideAndroid.cpp`；
4. 与 CPU JIT 对比 64 个 case。

Phase 0 需要的新增部分：

- 写 `jni/fotufilm_jni.cpp`：
  - `JNI_OnLoad`；
  - `nativeCreate` / `nativeDestroy`；
  - `nativeProcessRgb`；
  - 解析 `.fswp`；
  - 调用 `fotufilm_halide_process_tile`；
- 用 NDK clang 编 `libfotufilm_android.so`；
- 把 `.so` 放到：

```text
app/src/main/jniLibs/arm64-v8a/libfotufilm_android.so
```

- pack 放：

```text
app/src/main/assets/film/gold200.fswp
...
```

- Android Studio Windows 侧编译 Kotlin/JNI，`adb install` 到真机。

WSL 和 Windows 文件互访：

```bash
# WSL 写入 Windows 工程
cp ~/fotufilm-packs/*.fswp "/mnt/d/Android/pocket ccd/app/src/main/assets/film/"
```

注意路径有空格时要引号。

---

## 7. 没有 WSL 时的替代：GitHub Actions Linux 构建

如果本机不能装 WSL，用 Linux CI 当构建机：

```yaml
name: fotufilm-android-pack
on: workflow_dispatch
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Install deps
        run: |
          sudo apt-get update
          sudo apt-get install -y build-essential cmake ninja-build clang lld \
            libxml2-dev zlib1g-dev libjpeg-dev libpng-dev libtiff-dev \
            liblcms2-dev libheif-dev libopenexr-dev
      # 安装 Swift Linux toolchain、Halide、Android NDK
      - name: Build packs + AOT
        run: |
          swift build -c release --product fotufilm
          # generate .fswp / AOT / .so
      - uses: actions/upload-artifact@v4
        with:
          name: fotufilm-android
          path: |
            libfotufilm_android.so
            *.fswp
```

下载 artifact，放进 Windows 工程。  
优点：不用买 Mac；缺点：首次配置 Halide/NDK 仍然要花时间，且需要有 GitHub 仓库/Actions 权限。

---

## 8. 本机现状

当前机器检查结果：

```text
Windows NT 10.0.26300.0
wsl --status: 未安装 Linux 的 Windows 子系统
wsl -l -v: 无发行版
```

所以下一步不是写 JNI，而是：

1. 安装 WSL2 + Ubuntu（需要管理员权限和重启）；
2. 在 WSL 里装 Swift/Halide/NDK；
3. 跑通 `verify-android-aot.sh`；
4. 再开始 JNI/Android 集成。

---

## 9. 建议的执行顺序

```text
Windows: 安装 WSL2
  → WSL: Ubuntu + Swift + Halide + NDK
  → fotufilm --list-stocks
  → 3 个 FSWP pack
  → verify-android-aot.sh
  → 最小 JNI .so
  → Windows Android Studio 接 .so + packs
  → 真机 12MP 测试
```

Phase 0 通过前，不要动主相机链路。
