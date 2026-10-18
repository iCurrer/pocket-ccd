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

## 6. 常见失败

| 失败点 | 原因 | 处理 |
|---|---|---|
| `setup-swift` 找不到 6.2 | runner/action 版本不匹配 | 改成可用的 Swift 版本（如 6.1） |
| `llvm.sh 22` 失败 | apt.llvm.org 不支持当前 runner | 用 Ubuntu 22.04 runner 或调整 LLVM 版本 |
| `build-halide-linux.sh` 失败 | 缺 Vulkan/LLVM 组件 | 确认 `libvulkan-dev`、`lld-22`、`llvm-22-dev` |
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
