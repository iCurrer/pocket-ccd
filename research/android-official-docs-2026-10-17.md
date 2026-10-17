# 官方文档核查记录 — Android / CameraX / Camera2 / Compose / AGP

**核查日期：** 2026-10-17（Asia/Shanghai）  
**核查人：** AI 工程准备会话  
**核查对象：** Android 平台版本、Google Play target API、Android Gradle Plugin、Kotlin、Jetpack Compose、CameraX、Camera2、MediaStore/权限、Android 16 行为变更  
**用途：** 为 `DECISION_LOG.md` / `TASK_BOARD.md` / `TEST_PLAN.md` 提供版本与来源依据

---

## 0. 方法与证据边界

- 官方页面通过本机 HTTP 客户端抓取（系统代理 `127.0.0.1:7897`）；抓取的是 2026-10-17 当日的页面内容。
- 记录中的 `[F]` 表示“官方页面明确写出”或“本机命令输出明确显示”；`[I]` 表示工程推断；`[H]` 表示待验证假设。
- **“文档版本”说明：**
  - Release notes 有明确的库版本和发布日期；
  - Guide/overview 页面是 living document，通常没有版本号，只能记录访问日期；此类条目标注“living doc / 访问日期”。
- 本记录**不**包含本项目对这些依赖组合的实际构建验证；构建验证属于 `TASK_BOARD.md` T1-003。
- 本记录**不**包含任何相机实拍、图像质量或性能结论。

---

## 1. 平台与 Google Play

| ID | 事实 | 文档/版本 | 来源 | 访问日期 |
|---|---|---|---|---|
| F-01 | Android 17 = API 37（`CINNAMON_BUN`）；Android 16 = API 36（`BAKLAVA`）；Android 15 = API 35（`VANILLA_ICE_CREAM`） | 《Android API levels》living doc；表中列到 Android 17 | [developer.android.com/guide/topics/manifest/uses-sdk-element#ApiLevels](https://developer.android.com/guide/topics/manifest/uses-sdk-element#ApiLevels) | 2026-10-17 |
| F-02 | 自 2026-08-31 起，新应用和应用更新必须 target Android 16（API 36）或更高才能提交 Google Play；Wear OS/Automotive 为 API 35+，TV/XR 为 API 34+。可申请延期到 2026-11-01 | 《Target API level requirements for Google Play apps》living doc；页面明确写“Starting August 31 2026” | [developer.android.com/google/play/requirements/target-sdk](https://developer.android.com/google/play/requirements/target-sdk) | 2026-10-17 |
| F-03 | 现有应用若 target API 34 或更低，将只对运行其 target API 或更低版本系统的设备可见 | 同上 | 同上 | 2026-10-17 |

**工程含义 `[I]`：**
- Pocket CCD 若计划上 Google Play，`targetSdk` 不能低于 36；否则 2026-08-31 后无法提交更新。
- `compileSdk` 可以高于 `targetSdk`；compileSdk 37 不会自动启用 Android 17 的行为变更，行为变更由 targetSdk 决定。`[I]`
- 本项目暂定 `targetSdk 36`，以避免在 MVP 阶段同时吸收 Android 17 的行为变更；发布前再评估是否升 37。`[D]`（D-003，PROPOSED）

---

## 2. Android Gradle Plugin / Gradle / JDK / Kotlin

| ID | 事实 | 文档/版本 | 来源 | 访问日期 |
|---|---|---|---|---|
| F-04 | AGP 8.13.2：最高支持 API 36；最低/默认 Gradle 8.13；Build Tools 35.0.0；JDK 17；NDK 27.0.12077973 | AGP 8.13.2 release notes | [developer.android.com/build/releases/gradle-plugin](https://developer.android.com/build/releases/gradle-plugin) | 2026-10-17 |
| F-05 | AGP 9.4：最高支持 API 37；最低/默认 Gradle 9.6.0；Build Tools 36.0.0；JDK 17；NDK 28.2.13676358 | AGP 9.4.0 release notes | [developer.android.com/build/releases/agp-9-4-0-release-notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes) | 2026-10-17 |
| F-06 | AGP 9.3：最高支持 API 37；最低/默认 Gradle 9.5.0；Build Tools 36.0.0；JDK 17 | AGP 9.3.0 release notes（含 9.3.3 fixed issues） | [developer.android.com/build/releases/agp-9-3-0-release-notes](https://developer.android.com/build/releases/agp-9-3-0-release-notes) | 2026-10-17 |
| F-07 | AGP 9.0 起默认启用内建 Kotlin；`org.jetbrains.kotlin.android` 插件与新 DSL 不兼容；AGP 9.0 运行时依赖 KGP 2.2.10（最低/默认），低于该版本的 KGP 会被自动升级 | 《Built-in Kotlin》+ AGP 9.0 release notes | [developer.android.com/build/migrate-to-built-in-kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)、[AGP 9.0 notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) | 2026-10-17 |
| F-08 | Kotlin 2.4 要求 AGP 8.5.2+、R8 9.1.29；Kotlin 2.3 要求 AGP 8.2.2–8.13、R8 8.13.19 | 《Kotlin support》table | [developer.android.com/build/kotlin-support](https://developer.android.com/build/kotlin-support) | 2026-10-17 |
| F-09 | AGP 9.4 是 minor release；AGP 10 起 Variant API 强制、内建 Kotlin 不可再关闭（页面明确 AGP 10 相关迁移） | AGP 9.4.0 notes | 同 F-05 | 2026-10-17 |

**工程含义 `[I]`：**
- 若用 AGP 9.3.3：Gradle 9.6.1 满足最低 9.5；JDK 21 高于最低 17，但应把 Java/Kotlin target 固定为 17，避免跨版本字节码差异。
- 若用 AGP 9.x：不要照搬 `org.jetbrains.kotlin.android` 的旧模板；Compose 编译器插件仍需单独应用，版本要与内建 Kotlin 兼容。
- 若用 AGP 8.13.2：只能 compileSdk 36；Compose 1.12 要求 compileSdk 37/AGP 9.2，因此必须退到 Compose 1.11.x 或升级 AGP。`[I]`（基于 F-04/F-06/F-12）
- 本机同时缓存 AGP 9.2.0/9.3.2/9.3.3；AGP 9.4 未缓存。为了降低首次构建的网络风险，`DECISION_LOG.md` 提议 AGP 9.3.3，而不是最新的 9.4。`[D]`（D-004，PROPOSED）

**官方文档中的组合注意：**
- AGP 9.3/9.4 页面没有重复列出 KGP 版本；不能假设它们与 AGP 9.0 的 KGP 2.2.10 完全相同。实际内建 Kotlin 版本需在 T1-003 通过 `./gradlew buildEnvironment` 或依赖报告确认。`[H]`

---

## 3. Jetpack Compose

| ID | 事实 | 文档/版本 | 来源 | 访问日期 |
|---|---|---|---|---|
| F-10 | Compose UI 最新 stable 1.12.1（2026-10-07）；Compose Foundation/Material/Runtime 同为 1.12.1；Material3 stable 1.4.0（2026-10-07） | Compose UI / Material3 release notes | [Compose UI](https://developer.android.com/jetpack/androidx/releases/compose-ui)、[Material3](https://developer.android.com/jetpack/androidx/releases/compose-material3) | 2026-10-17 |
| F-11 | Compose BOM 最新为 2026.09.00；BOM mapping 页面列出 2026.09.00 到 2022.10.00 的版本 | 《Compose BOM mapping》living doc | [developer.android.com/develop/ui/compose/bom/bom-mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping) | 2026-10-17 |
| F-11b | Compose BOM 2026.08.00 的官方 POM 映射：`compose.ui`/`runtime`/`foundation`/`animation` 为 1.12.0，`material3` 为 1.4.0 | Google Maven POM `androidx.compose:compose-bom:2026.08.00` | `https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.08.00/compose-bom-2026.08.00.pom` | 2026-10-17 |
| F-12 | Compose 1.12.0-alpha01（2026-04-22）说明：Compose 库的 `compileSdk` 更新到 API 37，因此使用 Compose 的最低 AGP 版本为 9.2.0 | Compose UI release notes，1.12.0-alpha01 | 同 F-10 | 2026-10-17 |
| F-13 | Compose 1.13.0-alpha03（2026-09-09）说明：Compose 库 `compileSdk` 更新到 37.1，所有使用 Compose 的 App/库都需要随之更新 compileSdk | Compose UI release notes，1.13.0-alpha03 | 同 F-10 | 2026-10-17 |
| F-14 | Compose 1.13.0-beta01（2026-10-07）说明：库 target Kotlin 2.2，并要求 kotlinc 2.2 | Compose UI release notes，1.13.0-beta01 | 同 F-10 | 2026-10-17 |

**工程含义 `[I]`：**
- 若使用 Compose 1.12.1（BOM 2026.08/09 附近），必须 `compileSdk 37` 且 AGP ≥ 9.2。这与 AGP 9.3.3 候选基线一致。
- 若坚持 `compileSdk 36`，只能使用 Compose 1.11.x 或更早；需要确认 1.11.x 的 compileSdk 要求（本记录未找到 1.11 的明确 compileSdk 行，标 `[H]`）。
- Compose 1.13 仍为 beta/alpha；MVP 不采用 1.13，避免把 alpha 行为带入基线。
- BOM 2026.09.00 是最新，但本机缓存的是 2026.08.00；`DECISION_LOG.md` 提议 2026.08.00 以降低首次构建风险。具体映射需在 T1-003 用 BOM 的 `dependencies` 结果确认。`[H]`

---

## 4. CameraX

| ID | 事实 | 文档/版本 | 来源 | 访问日期 |
|---|---|---|---|---|
| F-15 | CameraX 最新 stable 为 1.6.2（2026-08-26）；1.7.0-alpha03（2026-08-12）仍在 alpha；1.6.2 修复了较新 JDK 上解析 JSpecify 类型注解导致的编译崩溃 | CameraX release notes；页面最后更新 2026-08-26 | [developer.android.com/jetpack/androidx/releases/camera](https://developer.android.com/jetpack/androidx/releases/camera) | 2026-10-17 |
| F-16 | CameraX 1.5.0-rc01 起默认 minSdk 从 API 21 移到 API 23 | CameraX release notes，1.5.0-rc01 条目 | 同上 | 2026-10-17 |
| F-17 | CameraX 1.6.0（2026-03-25）起使用 CameraPipe；修复 Android 17/API 37 设备上因未知动态范围剖面导致的 `NullPointerException`/`IllegalArgumentException`；建议 1.5.2 或 1.6.0+ 以避开该问题 | CameraX release notes，1.6.0 条目 | 同上 | 2026-10-17 |
| F-18 | CameraX 概览页仍写“支持 Android 5.0 (API 21) 及以上”，与 F-16 的 release notes 不一致 | 《CameraX overview》living doc | [developer.android.com/media/camera/camerax](https://developer.android.com/media/camera/camerax) | 2026-10-17 |
| F-19 | CameraX 提供 Preview / ImageAnalysis / ImageCapture / VideoCapture 用例；`ImageCapture` 支持 `CAPTURE_MODE_MAXIMIZE_QUALITY`（质量优先）和 `CAPTURE_MODE_MINIMIZE_LATENCY`（默认，延迟优先）；`setJpegQuality` 范围为 1–100 | 《Image capture》/《Additional options》living docs | [Take photo](https://developer.android.com/media/camera/camerax/take-photo)、[Options](https://developer.android.com/media/camera/camerax/take-photo/options) | 2026-10-17 |
| F-19b | CameraX 1.5.0-alpha05 起把 `compileSdk` 升到 35；使用 CameraX 的应用也需要相应升级 `compileSdk` | CameraX release notes，1.5.0-alpha05 条目 | [CameraX release notes](https://developer.android.com/jetpack/androidx/releases/camera) | 2026-10-17 |
| F-20 | CameraX 与 Camera2 的 interop：可用 `Camera2CameraInfo` 读取 `CameraCharacteristics`；可用 `Camera2CameraControl`/`Camera2Interop.Extender` 覆盖部分 `CaptureRequest` 设置；官方明确标记为 experimental，覆盖 CameraX 设置需自行测试 | 《CameraX architecture》living doc | [developer.android.com/media/camera/camerax/architecture](https://developer.android.com/media/camera/camerax/architecture) | 2026-10-17 |

**工程含义 `[I]`：**
- 阶段 2 用 CameraX 1.6.2 stable；不要用 1.7.0-alpha03。
- 应用 minSdk 29 不受 CameraX 1.5 的 minSdk 23 影响。
- 目标 API 37 设备上，CameraX 必须 ≥ 1.5.2/1.6.0；1.6.2 是当前安全选择。
- `CAPTURE_MODE_MAXIMIZE_QUALITY` 可能增加快门延迟；阶段 2 需要实测“画质 vs 延迟”，不能默认选质量优先。
- CameraX 没有承诺“预览流 = 拍照流”的分辨率/裁切/色彩；必须实拍验证（规范 §6.5 的要求是合理的）。`[F]`/`[I]`

---

## 5. Camera2

| ID | 事实 | 文档/版本 | 来源 | 访问日期 |
|---|---|---|---|---|
| F-21 | Camera2 是低层相机包，替代已废弃的 Camera1；支持 Android 5.0（API 21）+；提供深入的设备配置/请求控制，但需要处理设备差异 | 《Camera2 overview》living doc | [developer.android.com/media/camera/camera2](https://developer.android.com/media/camera/camera2) | 2026-10-17 |
| F-22 | 官方建议大多数开发者用 CameraX；只有需要特定低层功能时才用 Camera2 | 《Choose a camera library》living doc | [developer.android.com/training/camera/choose-camera-library](https://developer.android.com/training/camera/choose-camera-library) | 2026-10-17 |
| F-23 | Camera2 `CaptureRequest` 公开的 JPEG 控制键包括 `JPEG_QUALITY`（压缩质量，典型 85–95）和 `JPEG_ORIENTATION`；公开参考页没有量化表/色度抽样/重启间隔键 | Camera2 `CaptureRequest` API reference | [developer.android.com/reference/android/hardware/camera2/CaptureRequest](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest) | 2026-10-17 |
| F-24 | Camera2 的硬件级别包括 LEGACY/LIMITED/FULL/LEVEL_3/EXTERNAL；不同级别支持的流组合/能力不同 | Camera2 `CameraCharacteristics` reference | [developer.android.com/reference/android/hardware/camera2/CameraCharacteristics](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics) | 2026-10-17 |

> F-23 的证据边界：上表是“公开参考页列出的 JPEG 键”这一观察；由此推断“Camera2 不能通过公开 API 控制量化表/色度抽样”属于 `[I]`，不是官方原话。厂商可能存在私有扩展，但本项目不应依赖。

**工程含义 `[I]`：**
- Camera2 不能自动解决“老相机 JPEG 味道”问题；它只暴露质量/方向等有限 JPEG 参数，编码器仍由相机 HAL 控制。要控制量化表/色度抽样，需要自定义编码器（或 CameraX 内存采集 + 自研编码），这与选 CameraX 还是 Camera2 是两件事。
- Camera2 的硬件级别/能力差异很大；若未来接入，必须先做设备能力矩阵，而不是假设 `FULL`。
- 阶段 2 不引入 Camera2；只保留 `Camera2CameraInfo` 读取能力（如果不需要覆盖设置，风险较低）。`[D]`（D-005，PROPOSED）

---

## 6. 权限、MediaStore 与 Android 16 行为

| ID | 事实 | 文档/版本 | 来源 | 访问日期 |
|---|---|---|---|---|
| F-25 | Android 10（API 29）+ 上，访问/修改本应用拥有的 MediaStore 媒体（包括自己拍摄的照片）不需要存储相关权限 | 《Access media files from shared storage》living doc | [developer.android.com/training/data-storage/shared/media](https://developer.android.com/training/data-storage/shared/media) | 2026-10-17 |
| F-26 | 要访问其他应用创建的媒体，才需要 `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO`/`READ_EXTERNAL_STORAGE` 等；`READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO` 受 Google Play 政策限制，推荐 Photo Picker | 同上；页面顶部 Caution | 同上 | 2026-10-17 |
| F-27 | `MediaStore` 写入可用 `IS_PENDING=1` 标记“写入中”，写完置 0 后才对其他应用可见；这是“先写后发布”的官方机制 | 同上，`IS_PENDING` 小节 | 同上 | 2026-10-17 |
| F-28 | 旧设备（Android 9 或更低）或退出 scoped storage 时，访问/修改媒体需要 `READ_EXTERNAL_STORAGE`/`WRITE_EXTERNAL_STORAGE` | 同上，legacy 小节 | 同上 | 2026-10-17 |
| F-29 | Android 15 起 target 35 强制 edge-to-edge；Android 16 起 target 36 不能 opt-out，必须处理 insets | 《Android 16 behavior changes》living doc | [developer.android.com/about/versions/16/behavior-changes-16](https://developer.android.com/about/versions/16/behavior-changes-16) | 2026-10-17 |
| F-30 | Android 16 target 36 时，大屏（≥sw600dp）会忽略方向、可调整尺寸、宽高比限制；临时 opt-out 到 target 37 失效 | 同上，orientation/resizability 小节 | 同上 | 2026-10-17 |
| F-31 | 运行时权限应在用户触发具体功能时请求，并提供解释；Android 12+ 有相机/麦克风隐私指示器 | 《Request runtime permissions》living doc | [developer.android.com/training/permissions/requesting](https://developer.android.com/training/permissions/requesting) | 2026-10-17 |

**工程含义 `[I]`：**
- minSdk 29 可以让 Manifest 只保留 `CAMERA`，不需要存储权限；这与规范的“最小权限”一致。
- 如果最终 minSdk 26，必须为 API 26–28 增加 `WRITE_EXTERNAL_STORAGE`（`maxSdkVersion=28`）并测试旧路径；这会增加权限拒绝/引导分支。
- 图库只展示应用自己的照片时，不需要 `READ_MEDIA_IMAGES`；这也避免 Play 政策审查。`[I]`
- 保存流程必须用 `IS_PENDING`，不能“先写普通文件再标记成功”；这与规范 §8.2 的“写入后验证”一致。
- 不能锁竖屏来适配相机页；Android 16 大屏会忽略方向限制。UI 必须支持横屏/大屏。`[F]`/`[I]`

---

## 7. 本机环境事实（非官方文档）

> 这些是 2026-10-17 的本机命令输出，不是官方文档结论；用于判断“能否立即构建/测试”。

| ID | 事实 | 命令/来源 |
|---|---|---|
| L-01 | 工作区 `D:\Android\pocket ccd` 审查前只有 `DEVELOPMENT_SPEC.md` | `Get-ChildItem` |
| L-02 | 工作区不是 Git 仓库 | `git status` → not a git repository |
| L-03 | Android Studio 2026.1.2（`AI-261.25134.95.2612.15822958`） | `product-info.json` |
| L-04 | JBR OpenJDK 21.0.10 | `java -version` |
| L-05 | SDK Platform 35/36/36.1/37.0 | SDK `platforms/` |
| L-06 | Build Tools 34.0.0/36.0.0 | SDK `build-tools/` |
| L-07 | Platform Tools/adb 37.0.0 | `adb version` |
| L-08 | Emulator 36.6.11.0；无 AVD、无 system image | SDK 目录 + `adb devices` |
| L-09 | 无 `sdkmanager`/`avdmanager`（无 cmdline-tools） | SDK 目录 |
| L-10 | Gradle 9.6.1/9.5.0/8.10.2 已缓存 | `~/.gradle/wrapper/dists` |
| L-11 | AGP 9.2.0/9.3.2/9.3.3；Kotlin 2.3.21/2.4.10；Compose BOM 2026.06.01/2026.08.00；CameraX 1.6.2；Room 2.8.5；ExifInterface 1.4.2。注意：缓存的是部分 artifact，不代表 BOM 内所有组件都已缓存（例如 Material3 1.4.0 未在缓存列表中） | `~/.gradle/caches/modules-2/files-2.1` |
| L-12 | `java`/`gradle`/`adb` 不在 PATH | `Get-Command` |
| L-13 | 系统代理 127.0.0.1:7897；Java/Node 可访问官方文档；Windows schannel curl 失败 | `curl`/Node fetch 测试 |
| L-14 | 工作区 ACL 写入问题已修复；恢复脚本在 `D:\Android\pocket-ccd-acl-report` | DSH ACL 诊断脚本输出 |

**环境结论 `[I]`：**
- 可以立即开始 Gradle 工程初始化；但**不能**立即执行安装/UI/相机测试，因为无 AVD/设备。
- 首次构建可能受代理/缓存影响；应优先使用缓存版本（AGP 9.3.3、BOM 2026.08.00、CameraX 1.6.2）。
- 命令行构建需要显式使用 JBR 21 或配置 `org.gradle.java.home`；不能依赖 PATH 中的 `java`。

---

## 8. 兼容性矩阵（提议，不是已验证结论）

| 方案 | AGP | Gradle | JDK | compileSdk | targetSdk | minSdk | Compose | CameraX | 状态 |
|---|---|---|---|---|---|---|---|---|---|
| A（推荐） | 9.3.3 | 9.6.1 | 21（target 17） | 37 | 36 | 29 | BOM 2026.08.00 | 1.6.2 | `[D]` PROPOSED；待 T1-003 |
| B（回退） | 9.2.0 | 9.6.1 | 21 | 37 | 36 | 29 | BOM 2026.06.01 | 1.6.2 | `[F]` 本机 LENS-Gallery 有构建产物（非本项目）；`[H]` 本项目未验证 |
| C（保守） | 8.13.2 | 8.13 | 17/21 | 36 | 36 | 29 | 1.11.4 | 1.6.2 | `[H]` 需要旧 Kotlin 插件/关闭内建 Kotlin；未验证 |

**方案 A 的约束核对：**

| 约束 | 要求 | A 是否满足 | 依据 |
|---|---|---|---|
| AGP 最大 API | 37 | 是（9.3 最高 37） | F-06 |
| Gradle 最低 | 9.5.0 | 是（9.6.1） | F-06 |
| JDK 最低 | 17 | 是（21） | F-06 |
| Compose compileSdk | 37 | 是 | F-12 |
| Compose 最低 AGP | 9.2.0 | 是（9.3.3） | F-12 |
| CameraX compileSdk | 35+ | 是（37） | F-19b |
| CameraX API 37 兼容 | 1.6.0+ | 是（1.6.2） | F-17 |
| Play target | 36+ | 是（36） | F-02 |

> 上表是**静态兼容性核对**，不是构建通过证明。`[H]`

---

## 9. 官方文档中的不一致/需要以 release notes 为准的点

| 点 | 文档 A | 文档 B | 处理 |
|---|---|---|---|
| CameraX 最低 API | 概览页：Android 5.0/API 21+ | 1.5.0-rc01 notes：默认 minSdk 21→23 | 以 release notes 的库行为为准；本项目 minSdk 29，不受影响。`[F]` |
| Compose compileSdk | BOM/概览页未统一强调 | 1.12.0-alpha01 notes：compileSdk 37/AGP 9.2 | 以 release notes 为准；方案 A 满足。`[F]` |
| AGP 稳定版本 | 主 release notes 页仍展示 AGP 8.13.2 示例 | 9.4.0 页面已存在 | 以具体版本的 release notes 为准；组合必须实测。`[F]`/`[H]` |
| AGP 9.3/9.4 的 KGP 版本 | AGP 9.0 页面写 KGP 2.2.10 | 9.3/9.4 页面未列 KGP | 不假设；T1-003 用依赖报告确认。`[H]` |

---

## 10. 仍未验证的事项（不得当作事实）

| ID | 未验证项 | 需要的实验/任务 |
|---|---|---|
| H-01 | 方案 A（AGP 9.3.3 + Gradle 9.6.1 + BOM 2026.08.00）能否在本机成功构建 | T1-003 / TC-ENV-002 |
| H-02 | AGP 9.3.3 的内建 Kotlin 版本与 Compose 插件 2.3.21 是否匹配 | T1-003 `buildEnvironment` |
| H-03 | Compose BOM 2026.08.00 的确切库版本映射 | 已由 F-11b 的官方 POM 确认（Compose 1.12.0 / Material3 1.4.0）；T1-003 仍应核对实际解析结果 |
| H-04 | 目标设备上 CameraX 1.6.2 能否输出 4:3 ≥12MP JPEG | EXP-01 |
| H-05 | 预览与成片的构图/裁切差异 | EXP-01 / T2-007 |
| H-06 | 是否需要 Camera2 interop | EXP-02 |
| H-07 | Android JPEG 编码器的代际损失/风格差距 | EXP-03 |
| H-08 | sRGB/8-bit 是否足够，是否需要 P3/ICC | EXP-04 |
| H-09 | 3D LUT vs 曲线 vs 混合 | EXP-05 |
| H-10 | CPU vs GPU 性能 | EXP-06 |
| H-11 | MediaStore + Room 崩溃一致性 | EXP-10 |
| H-12 | 目标设备相机能力（闪光灯/前摄/旋转/硬件级别） | T2-006/T2-008 |
| H-13 | Room 2.8.5、ExifInterface 1.4.2 等后续依赖的官方 release notes/兼容性 | 阶段 5 前核对；本记录只记录了本机缓存版本 |

---

## 11. 来源清单

### Android 平台 / Play

1. [Android API levels](https://developer.android.com/guide/topics/manifest/uses-sdk-element#ApiLevels)
2. [Target API level requirements for Google Play apps](https://developer.android.com/google/play/requirements/target-sdk)
3. [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16)
4. [Request runtime permissions](https://developer.android.com/training/permissions/requesting)

### 构建 / Kotlin

5. [Android Gradle plugin release notes (incl. 8.13.2)](https://developer.android.com/build/releases/gradle-plugin)
6. [AGP 9.4.0 release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
7. [AGP 9.3.0 release notes](https://developer.android.com/build/releases/agp-9-3-0-release-notes)
8. [AGP 9.0.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
9. [Built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)
10. [Kotlin support](https://developer.android.com/build/kotlin-support)

### Compose

11. [Compose UI release notes](https://developer.android.com/jetpack/androidx/releases/compose-ui)
12. [Compose Material3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)
13. [Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping)
14. [Compose setup](https://developer.android.com/develop/ui/compose/setup)
15. [Compose compiler](https://developer.android.com/develop/ui/compose/compiler)
16. [Compose BOM 2026.08.00 POM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.08.00/compose-bom-2026.08.00.pom)

### CameraX / Camera2

17. [CameraX release notes](https://developer.android.com/jetpack/androidx/releases/camera)
18. [CameraX overview](https://developer.android.com/media/camera/camerax)
19. [CameraX architecture / Camera2 interop](https://developer.android.com/media/camera/camerax/architecture)
20. [CameraX image capture](https://developer.android.com/media/camera/camerax/take-photo)
21. [CameraX image capture options](https://developer.android.com/media/camera/camerax/take-photo/options)
22. [Camera2 overview](https://developer.android.com/media/camera/camera2)
23. [Choose a camera library](https://developer.android.com/training/camera/choose-camera-library)
24. [Camera2 CaptureRequest reference](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest)
25. [Camera2 CameraCharacteristics reference](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics)

### 存储 / 媒体

26. [Access media files from shared storage](https://developer.android.com/training/data-storage/shared/media)

---

## 12. 记录维护

- 每次升级 AGP/Compose/CameraX 或改变 targetSdk，必须新增一份 `research/android-official-docs-YYYY-MM-DD.md` 或在本文追加“复核记录”，不得覆盖历史结论。
- 官方文档是 living document；本文件的“访问日期”是结论有效性的边界，不是永久事实。
- 本文件不替代 `DECISION_LOG.md`：文档事实不等于项目决策；决策需要负责人确认并记录理由。
