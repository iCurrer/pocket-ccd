# Fotufilm Android 原生移植评估与实施计划

**日期：** 2026-10-18  
**目标：** 把 Fotufilm 的胶片引擎真正移植进 Pocket CCD，做成 Android 胶卷相机，而不是只做 LUT 滤镜。  
**本地源码基线：** `DhaliwalX/fotufilm-engine`，commit `2d07c48c14199be24972db760a7631dc95f74012`（2026-10-17）  
**本地路径（分析用）：** `%TEMP%\fotufilm-engine`

---

## 1. 已核实的事实

以下来自实际 clone 的源码，不只是官网介绍。

### 1.1 引擎与平台

| 项 | 事实 |
|---|---|
| 语言/管线 | Swift + C++ + Halide；GPU 后端 Metal / CUDA / Vulkan / WebGPU |
| 官方平台 | macOS 13+、iOS 17+、Linux、Web |
| Android 代码 | `AndroidMath.swift`、`MappedBuffer.swift`、`FotufilmHalideAndroid.cpp`、`FotufilmHalideAndroidGPU.cpp` |
| Android 工具 | `tools/generate_halide_android.cpp`、`generate_halide_android_gpu.cpp`、`verify-android-aot.sh`、`vulkan-parity` |
| Android 公开产物 | **没有** AAR / Maven / JNI `.so` / Android Gradle module / APK |
| Release | macOS、Linux、iOS/Web；**没有 Android 包** |
| C API | `Sources/CFotufilmHost/include/fotufilm.h`，但宿主是 Swift 动态库，不是 Android 库 |
| 公开 Android App | 源码树没有；`ControlsExport.swift` 能生成 `android/app/.../EditState.kt`，说明上游有 Android 客户端规划/私有代码，但未随公开仓库发布 |

### 1.2 胶片数据

- `Sources/FotufilmCore/Stocks/` 有 **47 个正式 profile + 3 个示例**；
- JSON 合计约 **14.2 MB**；
- 每个 profile 包含：
  - 光谱灵敏度 / 染料密度 / 最小密度；
  - 特性曲线（density/logExposure）；
  - 颗粒（grain size/strength/density profile）；
  - halation、coupler、adjacency、flare；
  - 相纸曲线、显影条件、reciprocity；
  - 负片/反转片/黑白标志。
- 包括 Gold 200、Portra 400、Ektar 100、Velvia 50、Provia 100F、CineStill 800T、Tri-X 400、HP5 Plus、Kodachrome 64、instax 等。

### 1.3 关键 Android 证据

`tools/verify-android-aot.sh` 已经做了一条完整链路：

```text
fotufilm --dump-wasm-pack example.fswp
  -> generate_halide_android (AOT kernels)
  -> FotufilmHalideAndroid.cpp
  -> android-aot-parity
  -> 与 CPU JIT 对比，64 个 case 通过
```

这说明：

1. Android AOT kernel + FSWP pack 是上游验证过的路径；
2. **不需要在 Android 上跑完整 Swift App**，可以用 C++ AOT + pack；
3. 这条脚本是在主机上跑的 parity，不是 Android App；JNI/解码/相机/存储仍需我们补。

### 1.4 License

| 内容 | License | 义务 |
|---|---|---|
| 引擎代码 | Apache-2.0 | 保留 LICENSE/NOTICE；修改文件标注 |
| 47 个 film profiles | CC BY-SA 4.0 | 署名 Fotufilm、链接官网、保留 MUAStudio 版权、标注修改；修改后的 profile 按 CC BY-SA 分发 |
| 渲染出的照片/视频 | 不受 profile ShareAlike 影响 | 可正常导出 |
| 字体/glyph | CC BY-SA 4.0 | 单独署名/ShareAlike |
| 第三方 | Halide MIT、rawtoaces Apache-2.0、colour-science BSD 等 | 完整 notices |
| 商标 | Fotufilm/MUA Studio/胶片名 | 不得暗示官方合作；胶片名需商标说明 |

来源：`LICENSING.md`、`THIRD_PARTY_NOTICES.md`、`licenses/`。

---

## 2. 结论：走哪条原生路线

### 不推荐

- ❌ 直接把 Swift `FotufilmHost` 编成 Android `.so`：Swift Android 工具链、Foundation、平台代码、依赖链太重，且公开仓库没有 Android 构建配置。
- ❌ WebView + WASM：不是真正的原生相机，包体/性能/体验都不适合作为主链路。
- ❌ 服务端：照片上传，违反隐私要求。

### 推荐：离线 FSWP pack + C++ AOT + JNI

```text
构建机（macOS/Linux）
  ├── swift build -c release --product fotufilm
  ├── fotufilm --dump-wasm-pack gold200.fswp --stock gold200
  ├── generate_halide_android (CPU)
  ├── generate_halide_android_gpu (Vulkan)
  └── 产出 libfotufilm_android.so + packs

Android App
  ├── CameraX 采集
  ├── 解码为线性 Rec.2020 RGB float
  ├── JNI -> fotufilm_halide_process_tile / process_region
  ├── 输出 Display P3 / sRGB
  └── JPEG 保存到 app-owned 相册
```

**理由：**

- 复用上游已经验证的 FSWP + AOT 路径；
- 不需要在 Android 上运行 Swift 主程序；
- 保留真正的胶片模型（光谱/曲线/颗粒/halation），不是 LUT 近似；
- JNI 边界可控，CPU/GPU 两条后端都能接；
- 可以按 stock 分发 pack，控制首次包体。

**代价：**

- 需要 macOS/Linux 构建机（Swift + Halide + Android NDK）；
- 需要自己写 JNI、FSWP 解析、Bitmap/色彩转换、CameraX 链路；
- pack 大小/性能/包体必须实测；
- 47 个 stock 全量可能过大，先做精选。

---

## 3. 分阶段计划

### Phase 0 — 技术可行性 spike（建议 3–5 天）

**目标：** 证明“Android arm64 上能跑 Fotufilm 真引擎”，不碰正式 UI。

任务：

1. 准备构建机：macOS 或 Linux + Swift + Halide + Android NDK；
2. `swift build -c release --product fotufilm`；
3. 生成 1 个 stock 的 FSWP pack；
4. 用 `tools/generate_halide_android.cpp` 生成 arm64 AOT kernels；
5. 编译最小 `libfotufilm_android.so`（CPU 先，Vulkan 后）；
6. 写最小 JNI：输入 RGB float / 输出 RGBA8；
7. 在真机或 arm64 模拟器上处理一张 12MP 图；
8. 记录：耗时、峰值内存、包体、发热、输出与桌面参考的差异。

**通过标准：**

- arm64 真机能加载 `.so`；
- 单张 12MP 处理成功，输出无 NaN/黑图；
- CPU 结果与桌面 `fotufilm` 参考在可接受误差内；
- 记录 pack 大小、so 大小、首帧/单张耗时；
- License/第三方 notices 清单可落地。

**不过则：**

- 退回 LUT + shader 方案，不再继续原生。

### Phase 1 — 单 stock 端到端（建议 1–2 周）

- CameraX 拍照 → 读到 RGBA → 转线性 Rec.2020；
- JNI 调用真引擎；
- 输出 P3/sRGB → JPEG → MediaStore；
- 3 个 stock：Gold 200、Portra 400、Tri-X 400；
- 相册显示真实拍摄照片；
- 基础错误态（权限、初始化失败、内存不足）。

### Phase 2 — 12 个精选 stock + 预览/成片分离（建议 2–3 周）

- 胶片选择器（12 个）；
- 实时预览：低分辨率引擎/或 LUT 预览；
- 拍照后全分辨率真引擎；
- CPU/Vulkan 后端切换与 fallback；
- pack 压缩/按需解压；
- 性能/热/内存门槛。

### Phase 3 — 47 stock + 完整能力（建议 3–4 周）

- 全量 47 个 profile；
- push/pull、相纸、halation/颗粒参数（若 pack/API 支持）；
- 包体优化（按 stock 下载/内置分组/压缩）；
- 完善 License 页、attribution、商标声明；
- 设备矩阵/回归。

> 时间是工程估算，不是承诺；Phase 0 的实测结果会改变后续估算。

---

## 4. 任务拆分（建议写入 TASK_BOARD M8）

| ID | 任务 | 前置 | 交付物 | 验收 |
|---|---|---|---|---|
| FOT-001 | License/来源清单 | 无 | `docs/fotufilm-license-inventory.md` | 代码/profile/第三方/商标逐项列出 |
| FOT-002 | 构建机与工具链 | FOT-001 | macOS/Linux + Swift + Halide + NDK 环境记录 | `fotufilm --help` 和 AOT 生成可运行 |
| FOT-003 | 生成 3 个代表性 FSWP pack | FOT-002 | Gold200/Portra400/TriX400 `.fswp` | pack 可被 AOT parity 工具读取 |
| FOT-004 | arm64 CPU AOT `.so` | FOT-003 | `libfotufilm_android.so` + JNI 最小调用 | 真机处理 1 张 12MP 图 |
| FOT-005 | FSWP 解析 + JNI API | FOT-004 | Kotlin `FilmEngine` 接口、C++ parser | 3 个 stock 可选/可渲染 |
| FOT-006 | 解码/色彩/输出 | FOT-004 | 线性 Rec.2020 输入、P3/sRGB 输出、JPEG | 与桌面参考对比 |
| FOT-007 | CameraX 采集到引擎 | FOT-005/M2 | 拍照→处理→保存闭环 | 真机照片进入相册 |
| FOT-008 | 胶片选择器 + 12 stock | FOT-005 | UI + metadata | 切换 stock 生效 |
| FOT-009 | 预览/成片双路径 | FOT-007 | 低分辨率预览、全分辨率成片 | 预览不冒充成片 |
| FOT-010 | Vulkan GPU + fallback | FOT-004 | GPU `.so`/后端选择 | GPU 失败自动 CPU |
| FOT-011 | 性能/包体/热 | FOT-007/010 | 报告 + 优化 | 目标设备可接受 |
| FOT-012 | 全量 47 stock | FOT-008/011 | pack 集合 + 选择器 | 47 个可加载 |
| FOT-013 | Attribution/License 页 | FOT-001 | App 内开源声明/许可页 | 满足 CC BY-SA/Apache/第三方 |
| FOT-014 | 设备验收 | FOT-012/013 | 真机矩阵报告 | 端到端通过 |

---

## 5. 关键风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 构建机/工具链不可用 | 直接阻塞 | 先找 macOS/Linux + Halide；或用 CI 构建机 |
| AOT `.so` 与 Android 16KB page size/ABI 不兼容 | 加载失败 | arm64-v8a 优先；真机测试；必要时 x86_64 |
| pack 太大（47 个） | 包体不可接受 | 先 12 个；压缩；按需下载（离线包） |
| 12MP CPU 处理慢/发热 | 体验差 | Vulkan GPU；预览低分辨率；tile/region |
| 色彩管理错误 | 颜色不对 | 线性 Rec.2020 输入/输出 P3；与桌面参考比对 |
| License/ShareAlike | 发布风险 | FOT-001/FOT-013；保留 notices；法务确认 |
| 胶片名商标 | 商店风险 | 商标声明；避免暗示官方 |
| 上游 API/格式变化 | 维护成本 | 固定 commit；记录 pack schema；升级需回归 |
| 与 Pocket CCD 玩具 CCD 风格冲突 | 产品方向 | 先做“胶卷模式”，保留 CCD 模式；不做二选一 |

---

## 6. 需要先确认的产品问题

1. **首批 stock 数量**：建议 12 个，不是 47 个；
2. **预览方式**：低分辨率真引擎 vs LUT 预览；建议先 LUT/低分辨率，成片用真引擎；
3. **UI 入口**：在拍摄页加胶片拨盘/选择器，还是新开“胶卷”模式；
4. **输出**：只做静态照片，还是也要视频（先不做视频）；
5. **品牌**：Pocket CCD 下增加“Film”能力，还是另做独立 App；
6. **发布**：开源 App 还是闭源；CC BY-SA 资产合规方式。

---

## 7. 结论

**可行，但必须先做 Phase 0 spike。**

- 公开仓库的 Android AOT + FSWP 路线是真实存在的，不是幻想；
- 但没有现成 Android SDK，JNI/解码/相机/存储/性能都要我们补；
- 最稳的路径是：**离线 pack + C++ AOT + JNI**，不是 Swift Android 全量移植；
- 先 3 个 stock、单张 12MP、真机跑通，再谈 47 个和实时预览；
- 在 Phase 0 通过前，不应把 M8 任务标成 `DONE`，也不应替换现有相机链路。
