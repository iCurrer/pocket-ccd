# Pocket CCD — 技术决策记录（Decision Log）

**建立日期：** 2026-10-17  
**维护规则：** 每个影响架构、依赖、数据、权限、验收的决策都必须有 ID、状态、背景、选项、结论、后果和复审触发条件。  
**状态定义：**

| 状态 | 含义 |
|---|---|
| `ACCEPTED` | 已确认，当前工作按此执行 |
| `PROPOSED` | 有明确建议，但尚未通过构建/实验/负责人确认；不得当作已定事实 |
| `OPEN` | 需要产品/技术负责人选择，当前不阻塞文档工作，但会阻塞对应实现任务 |
| `SUPERSEDED` | 已被新决策取代，保留历史 |

**证据标记：** `[F]` 已核实事实、`[I]` 工程推断、`[H]` 待验证假设（与 `SPEC_REVIEW.md` 一致）。

---

## 决策总览

| ID | 决策 | 状态 | 影响阶段 | 复审触发 |
|---|---|---|---|---|
| D-001 | 以 `DEVELOPMENT_SPEC.md` 为产品意图基线，先文档后实现 | ACCEPTED | 全部 | 规范大版本更新 |
| D-002 | 研究结论强制区分事实/推断/假设，未执行测试不得标通过 | ACCEPTED | 全部 | 无 |
| D-003 | 平台基线：compileSdk 37、targetSdk 36、minSdk 29 | PROPOSED（构建已验证；minSdk 待确认） | 1 | 产品确认 minSdk |
| D-004 | 构建工具链：AGP 9.3.3 + Gradle 9.6.1 + JDK 21 | ACCEPTED（T1-003 构建通过） | 1 | 依赖升级/构建回归 |
| D-005 | 相机 API：CameraX 优先，Camera2 仅按缺口接入 | PROPOSED | 2 | EXP-01/EXP-02 |
| D-006 | UI 相机预览使用 `PreviewView`，暂不使用 `camera-compose` | PROPOSED | 2 | T2-002 原型 |
| D-007 | 页面导航使用显式状态机 + `AnimatedContent`，不引入 Navigation-Compose | PROPOSED | 1 | T1-005 评审 |
| D-008 | 存储：MediaStore 发布成片 + Room 元数据 + 应用私有临时文件 | PROPOSED | 5 | EXP-10 |
| D-009 | 图像处理：CPU 参考实现优先，GPU 仅在基准证明必要时引入 | PROPOSED | 3 | EXP-06 |
| D-010 | JPEG：MVP 使用 Android 编码器 q=92，不宣称复刻老相机 JPEG 指纹 | PROPOSED | 3 | EXP-03 |
| D-011 | 颜色：sRGB / 8-bit / gamma 编码工作空间，显式 ICC，禁用 UltraHDR | PROPOSED | 3 | EXP-04 |
| D-012 | 滤镜参数与代码分离，版本化 JSON + 数据库保存解析后快照 | PROPOSED | 3/4 | T3-001 |
| D-013 | 统一错误模型：错误码 + 阶段 + 可重试性 + 用户动作 | PROPOSED | 2–5 | T2-005 |
| D-014 | 原图保留策略：7 天（推荐） | OPEN | 5 | 产品确认 |
| D-015 | 权限：MVP 仅 CAMERA；API 29+ 不申请存储权限 | PROPOSED | 1/5 | 产品确认 minSdk |
| D-016 | 测试栈：JUnit4 + coroutines-test + Compose UI Test + 真机手测 | PROPOSED | 1–6 | T1-003 |
| D-017 | 不引入付费服务、不上传照片/个人数据 | ACCEPTED | 全部 | 需求变更 |
| D-018 | 参考相机仅作研究，不在 UI/商店造成官方合作误解 | ACCEPTED | 4/7 | 发布前法务审查 |
| D-019 | 阶段 1 只做工程初始化 + 静态 UI，不实现相机 | ACCEPTED | 1 | 负责人变更 |
| D-020 | 应用 ID / 应用名最终值 | OPEN | 1 | 产品确认 |
| D-021 | 性能预算采用暂行目标，不作为对外承诺 | PROPOSED | 6 | 真机基准 |
| D-022 | 不做方向锁；适配竖屏/横屏/≥sw600dp | PROPOSED | 1 | T1-013 |
| D-023 | 诊断日志仅本地，Release 不含照片/EXIF 内容 | ACCEPTED | 全部 | 隐私审查 |
| D-024 | 依赖锁定精确版本，使用版本目录；新增依赖需许可证审查 | ACCEPTED | 全部 | 每次新增依赖 |
| D-025 | 目标设备档位与最低 Android 版本 | OPEN | 1/6 | 产品确认 |
| D-026 | 阶段 1 单 `:app` 模块分包，不预建空模块 | PROPOSED | 1 | 代码规模增长 |

---

## D-001 — 以开发规范为产品意图基线，先文档后实现

- **日期：** 2026-10-17
- **状态：** ACCEPTED
- **类型：** 流程
- **背景：** 当前工作区只有 `DEVELOPMENT_SPEC.md`，没有代码、Git 或工程结构。规范包含产品意图、技术方向、测试原则，但存在未决项和不可验证要求。
- **选项：**
  1. 直接开始写相机 App；
  2. 先补齐任务板、决策记录、测试计划，再按阶段实现；
  3. 重写规范。
- **决定：** 选 2。规范不重写，只审查并建立配套工程文档。
- **理由：** 直接实现会把未决项固化成代码；重写规范会丢失产品意图。先把“已知/未知/待验证”分开，后续每个阶段都能追溯。
- **后果：** 阶段 1 启动前需要负责人确认若干开放问题；文档成为后续变更的合同。
- **验证：** `SPEC_REVIEW.md`、`TASK_BOARD.md`、`TEST_PLAN.md`、`PROJECT_STATE.md` 存在且互相引用。
- **复审触发：** 规范升级到 V2，或产品目标发生实质变化。

---

## D-002 — 证据分级与“未执行不得标通过”

- **日期：** 2026-10-17
- **状态：** ACCEPTED
- **类型：** 流程 / 质量
- **背景：** 规范 §14.2 要求区分实际运行、自动化、代码审查、未测试、需真机验证。AI 辅助开发容易把“看起来应该可行”写成“已通过”。
- **决定：** 全项目使用 `[F]`/`[I]`/`[H]` 标记；测试状态只允许 `NOT_RUN / RUNNING / PASS / FAIL / BLOCKED`；任何 `PASS` 必须带命令、构建哈希、设备信息和原始输出路径。
- **理由：** 让研究结论和验收结果可复查，避免“口头通过”。
- **后果：** 文档更冗长；但阶段验收不会建立在猜测上。
- **验证：** `TEST_PLAN.md` 的证据模板；`TASK_BOARD.md` 的状态规则。
- **复审触发：** 无。

---

## D-003 — 平台基线：compileSdk 37 / targetSdk 36 / minSdk 29

- **日期：** 2026-10-17
- **状态：** PROPOSED（compileSdk 37 / targetSdk 36 已由 T1-003 构建验证；minSdk 29 仍待产品确认）
- **类型：** 平台兼容性
- **背景：**
  - `[F]` Google Play 自 2026-08-31 起要求新应用/更新 target API 36+。
  - `[F]` Compose 1.12.0-alpha01 的 release notes 写明 compileSdk 37 与 AGP 9.2+；1.12.1 为同一 minor 的 stable，按该约束规划，T1-003 实际验证。
  - `[F]` Android 10（API 29）起，访问本应用写入 MediaStore 的照片不需要存储权限。
  - `[F]` CameraX 1.5.0 起默认 minSdk 为 23。
- **选项：**
  1. compileSdk 37 / targetSdk 36 / minSdk 29（推荐）；
  2. compileSdk 37 / targetSdk 36 / minSdk 26（覆盖 Android 8/9，增加旧存储权限分支）；
  3. compileSdk 36 / targetSdk 36 / minSdk 29 + Compose 1.11.x（更保守，但需放弃 1.12）。
- **决定：** 采用选项 1 作为提议基线。minSdk 29 的最终值仍需产品确认（见 D-025）；阶段 1 静态 UI 不受影响。
- **理由：**
  - target 36 是上架硬要求；
  - compileSdk 37 是当前 Compose stable 的要求，且本机已安装 platform 37；
  - minSdk 29 消除 `WRITE_EXTERNAL_STORAGE` 分支，简化权限、MediaStore 和 `ImageDecoder` 使用；
  - CameraX 1.5.0 起默认 minSdk 23；1.6.2 预计沿用（T2 验证），低于 29，不冲突。
- **后果：**
  - 放弃 Android 8.0/8.1/9 的官方支持；若产品认为旧手机是核心用户，需回到选项 2，并新增旧存储权限、`BitmapFactory` 兼容路径和额外测试。
  - targetSdk 36 会触发 Android 16 的 edge-to-edge、大屏方向/可调整尺寸行为，UI 必须适配。
- **验证：** 2026-10-17 T1-003 `assembleDebug` 通过；`aapt dump badging` 确认 compileSdk 37 / targetSdk 36 / minSdk 29；Android 16 模拟器/真机检查 edge-to-edge 仍待 T1-015。
- **复审触发：** 产品确认 minSdk；Google Play 2027 年 target 要求变化；Compose 1.13 要求 compileSdk 37.1 时。

---

## D-004 — 构建工具链：AGP 9.3.3 + Gradle 9.6.1 + JDK 21

- **日期：** 2026-10-17
- **状态：** ACCEPTED（2026-10-17 本机构建通过）
- **类型：** 构建系统
- **背景：**
  - `[F]` AGP 9.3 最高支持 API 37，最低 Gradle 9.5.0，最低 JDK 17，Build Tools 36.0.0。
  - `[F]` 本机已缓存 AGP 9.3.2/9.3.3、Gradle 9.6.1、Kotlin 2.3.21/2.4.10、Compose BOM 2026.08.00。
  - `[F]` AGP 9.0 起默认内建 Kotlin，`org.jetbrains.kotlin.android` 与新 DSL 不兼容。
  - `[F]` 本机 Android Studio 2026.1.2 的 JBR 为 21.0.10。
- **选项：**
  1. AGP 9.3.3 + Gradle 9.6.1 + JDK 21 + 内建 Kotlin + Compose 插件 2.3.21 + BOM 2026.08.00（→ Compose 1.12.0 / Material3 1.4.0）；
  2. AGP 9.2.0 + Gradle 9.6.1 + BOM 2026.06.01（本机 LENS-Gallery 已有构建产物，但那是另一个项目）；
  3. AGP 8.13.2 + Gradle 8.13 + compileSdk 36 + Compose 1.11.4（保守，但需要关闭内建 Kotlin/使用旧 DSL）。
- **决定：** 采用选项 1 作为提议基线；T1-003 首次构建失败时，按 2 → 3 的顺序回退，并记录实际错误。
- **理由：**
  - 选项 1 与 compileSdk 37、Compose 1.12、当前 Studio 匹配；
  - 主要构建插件与 Compose 1.12.0 已在本地缓存；Material3 1.4.0 等个别 artifact 可能仍需下载，网络风险由 T1-003 记录；
  - 内建 Kotlin 减少插件版本冲突；
  - 选项 2/3 作为可回退路径，不提前锁死。
- **后果：**
  - 使用 AGP 9 新 DSL：`compileSdk { version = release(37) }`，不再使用旧的 `compileSdk = 37`（具体语法以 T1-003 验证为准）。
  - 不再应用 `org.jetbrains.kotlin.android`；Compose 编译器插件版本需与内建 Kotlin 匹配。
  - `kotlinOptions` 迁移到 `kotlin { compilerOptions { } }`。
- **验证：**
  - 2026-10-17 `./gradlew :app:assembleDebug` **BUILD SUCCESSFUL**（首次 59s；wrapper 复跑 up-to-date）；
  - 实际解析：Kotlin stdlib 2.3.21、Compose UI 1.12.0；
  - `aapt dump badging`：compileSdk 37 / targetSdk 36 / minSdk 29；
  - APK：T1-003 首次 29,570,230 bytes / `B77DB717...`；T1-005 重建 29,658,967 bytes / `508A109F...`（APK 随代码变化，哈希不作为固定指纹，详见 `TEST_PLAN.md` 5.6）；
  - 构建日志与 APK 证据已记录在 `PROJECT_STATE.md` / `TEST_PLAN.md`。
- **复审触发：** AGP 9.4/9.5 稳定并需要 API 37.1；Gradle 升级；Compose 插件与内建 Kotlin 版本冲突。

---

## D-005 — 相机 API：CameraX 优先，Camera2 仅按缺口接入

- **日期：** 2026-10-17
- **状态：** PROPOSED（原则沿用规范 §3.2；最终由 EXP-01/02 确认）
- **类型：** 相机架构
- **背景：**
  - `[F]` 官方推荐新应用从 CameraX 开始；Camera2 适合需要低层控制的复杂场景。
  - `[F]` CameraX 1.6.0 起使用 CameraPipe；1.6.2 修复 API 37 动态范围崩溃。
  - `[F]` CameraX 的 Camera2 interop 可读 `CameraCharacteristics`，也可覆盖部分 CaptureRequest，但被标记为实验性，覆盖 CameraX 设置需自行测试。
  - `[I]` 本项目的风格处理发生在拍摄后，不需要 Camera2 的逐帧控制；高分辨率 JPEG 采集是 CameraX 能覆盖的主路径。
- **决定：** 阶段 2 用 CameraX 完成预览 + `ImageCapture` + 生命周期；只有出现以下缺口之一才评估 Camera2：
  1. CameraX 无法获得满足验收的高分辨率 4:3 JPEG；
  2. 需要读取/控制 CameraX 未暴露的采集参数（例如特定 RAW/手动曝光）；
  3. 某目标设备上 CameraX 稳定复现缺陷且无 workaround。
- **禁止：** 因为“底层控制更多”而整体改用 Camera2；Camera2 不会自动解决 JPEG 量化表/风格问题（见 D-010）。
- **后果：** 阶段 2 的接口必须隔离 CameraX 类型，避免后续局部替换时重写 UI。
- **验证：** EXP-01（高分辨率与预览一致性）、EXP-02（Camera2 缺口评估）。
- **复审触发：** EXP-01/02 结果；目标设备出现 CameraX 阻塞缺陷。

---

## D-006 — 相机预览使用 `PreviewView`，暂不使用 `camera-compose`

- **日期：** 2026-10-17
- **状态：** PROPOSED
- **类型：** UI / 相机集成
- **背景：**
  - `[F]` CameraX 提供 `camera-view` 的 `PreviewView`，也提供较新的 `camera-compose`。
  - `[F]` CameraX 1.6.x 的 `camera-compose` 是稳定 artifact，但 Compose 原生取景器仍在快速演进；CameraX 1.7 alpha 才加入内置 pinch-to-zoom/tap-to-focus 等。
  - `[I]` 本项目阶段 2 只需要预览、拍照、镜头切换、闪光灯；`PreviewView` + `AndroidView` 是最成熟、问题最少的路径。
- **决定：** 阶段 2 用 `PreviewView` 包装进 Compose；如果后续需要 Compose 原生手势/覆盖层，再评估 `camera-compose`。
- **后果：** Compose 预览层需要处理 View 生命周期、裁剪和变换；不能把 `PreviewView` 的尺寸当作成片尺寸。
- **验证：** T2-002 在目标设备上检查预览比例、旋转、前后台恢复。
- **复审触发：** `camera-compose` 稳定且通过目标设备测试；需要 Compose 原生手势。

---

## D-007 — 页面导航使用显式状态机 + `AnimatedContent`

- **日期：** 2026-10-17
- **状态：** PROPOSED
- **类型：** UI 架构
- **背景：**
  - 规范 §2.3 已给出显式页面状态图，且要求“不要仅依赖动画控制页面切换”。
  - 本应用只有 5 个顶层页面，无深链接、无多 back stack 需求。
  - `[I]` 引入 Navigation-Compose 会增加依赖、路由字符串和参数序列化，但对当前规模收益有限。
- **决定：** 阶段 1 用 `AppDestination` 密封状态 + `AnimatedContent`；返回手势由 `BackHandler` 映射到状态转换；不使用 Navigation-Compose。
- **后果：** 需要自己保证状态恢复、返回栈和转场期间不重复触发；测试要覆盖所有转换。
- **验证：** T1-005 状态机单元测试；T1-007 手势/转场测试。
- **复审触发：** 需要深链接、多级图库详情、或页面数量显著增加。

---

## D-008 — 存储：MediaStore 发布成片 + Room 元数据 + 应用私有临时文件

- **日期：** 2026-10-17
- **状态：** PROPOSED
- **类型：** 数据 / 存储
- **背景：**
  - 规范 §8 要求数据库保存元数据、不把图片字节存库；§5.3 区分 Capture Source / Processed Image / Published Photo。
  - `[F]` Android 10+ 写入自有 MediaStore 照片不需要存储权限；`IS_PENDING` 可用于“写入中不可见”。
- **决定：**
  - 采集原图：可丢弃的中间文件放 `cacheDir`；需要保留用于重处理的原图放 `filesDir/ccd_sources`（`cacheDir` 可能被系统回收，不能承诺保留）；
  - 成片：MediaStore `Images`，插入时 `IS_PENDING=1`，写完校验后置 0；
  - 元数据：Room `PhotoRecord`，保存 MediaStore URI/ID、状态、尺寸、滤镜快照、时间、错误码；
  - 图库列表：以 Room 记录为索引，缩略图缓存；不扫描全设备图库。
- **后果：**
  - 应用卸载重装后 MediaStore 归属丢失，Room 记录也没了；旧照片仍在系统图库，但应用图库不显示。这是需要向用户说明的限制。
  - 需要启动对账：`OUTPUT_WRITING` 但文件缺失/仍 pending 的记录要恢复或标记失败。
- **验证：** EXP-10（崩溃一致性）；T5-001..T5-007。
- **复审触发：** 需要“导入系统照片编辑”功能；需要跨重装恢复。

---

## D-009 — 图像处理：CPU 参考实现优先，GPU 仅在基准证明必要时引入

- **日期：** 2026-10-17
- **状态：** PROPOSED
- **类型：** 成像架构
- **背景：**
  - 规范 §3.1/§6.3 不预设 CPU/GPU；要求用真实大图基准决定。
  - `[I]` CPU 实现更容易做到确定性、可单测、跨设备一致；GPU 有纹理精度、色彩空间、设备兼容性和调试成本。
- **决定：**
  - 阶段 3 先实现纯 Kotlin/CPU 的参考管线；
  - 图像数据用 `FloatArray`/`IntArray` 的自定义缓冲，Android `Bitmap` 只做解码/编码适配；
  - 只有 CPU 基准超过预算，才做 GPU 实验；GPU 结果必须与 CPU 参考在容差内。
- **后果：** 12MP 处理的 CPU 时间可能较长；需要通过降采样/并行/分块优化，而不是直接上 GPU。
- **验证：** EXP-06（CPU/GPU 基准）；T3-001..T3-007。
- **复审触发：** CPU 处理时间超过暂行预算 2 倍；目标低端设备无法在可接受时间完成。

---

## D-010 — JPEG：MVP 使用 Android 编码器 q=92，不宣称复刻老相机 JPEG 指纹

- **日期：** 2026-10-17
- **状态：** PROPOSED
- **类型：** 成像 / 编码
- **背景：**
  - `[F]` Android `Bitmap.compress(JPEG, quality)` 只暴露质量参数；没有量化表、色度抽样、重启间隔等控制。
  - `[I]` 旧相机风格的一部分来自 JPEG 编码指纹；只用质量参数无法精确复刻。
  - 规范 §7.1 警告“重复压缩”，但滤镜链路不可避免地要再编码一次（见 `SPEC_REVIEW.md` C-01）。
- **决定：**
  - MVP 明确接受“相机 JPEG → 解码 → 处理 → 再编码”；
  - 初始质量 q=92，输出 sRGB JPEG；
  - 验收只承诺色彩/色调/对比度/锐化等风格，不承诺 JPEG 伪影逐项复刻；
  - EXP-03 比较 Android 编码器与可控编码器（libjpeg-turbo + 自定义量化表/色度抽样），有证据再替换。
- **后果：** 文件大小、伪影、色度细节与参考相机可能有明显差异；必须在滤镜“已知限制”中写明。
- **验证：** EXP-03；T3-001；T4-005。
- **复审触发：** 盲评显示 JPEG 伪影是主要差距；需要商业级“CCD 味道”。

---

## D-011 — 颜色：sRGB / 8-bit / gamma 编码工作空间，显式 ICC，禁用 UltraHDR

- **日期：** 2026-10-17
- **状态：** PROPOSED
- **类型：** 颜色管理
- **背景：**
  - 规范 §6.4 要求明确输入/输出颜色空间、线性/非线性、GPU 纹理格式。
  - `[I]` 多数 Android 相机默认输出 sRGB JPEG；部分设备支持 P3/UltraHDR/HDR，若隐式处理会导致不可控的色调映射。
- **决定：**
  - 输入：按 JPEG ICC 解码；无 ICC 时按 sRGB；
  - 工作空间：sRGB 原色、8-bit、gamma 编码；需要物理正确的混合/曲线时再线性化；
  - 输出：sRGB JPEG，写入 sRGB ICC（若编码器支持）；
  - MVP 显式关闭 UltraHDR/RAW，避免 10-bit/HDR 色调映射进入管线；
  - 禁止在未声明颜色空间的情况下直接对 RGB 数值套曲线。
- **后果：** 广色域素材会被映射到 sRGB，可能损失饱和度；这是 MVP 的可控取舍。
- **验证：** EXP-04（ICC/P3/线性化实验）；T3-002。
- **复审触发：** 需要 P3 输出；相机默认输出变为 HDR/UltraHDR。

---

## D-012 — 滤镜参数与代码分离，版本化 JSON + 数据库保存解析后快照

- **日期：** 2026-10-17
- **状态：** PROPOSED
- **类型：** 滤镜 / 数据
- **背景：**
  - 规范 §6.6 要求每个滤镜有参数定义、版本号、参考样片、测试输入、对照、耗时、限制。
  - 只存 `filterId`/`filterVersion` 不足以复现历史结果：参数默认值、代码实现、LUT 文件都可能变化。
- **决定：**
  - 滤镜定义放 `assets/filters/*.json`，包含 `id`、`version`、`colorSpace`、`curves`、`matrix`、可选 `lut`、`sharpening`、`grain`、`jpegQuality`；
  - `PhotoRecord` 保存 `filterParamsJson`（解析后的完整快照）+ `pipelineVersion` + `filterVersion`；
  - 参数变更必须升版本；旧记录不自动重算；
  - 随机颗粒使用固定种子，种子存入快照。
- **后果：** 参数文件需要 schema 校验和迁移策略；数据库字段变大，但可复现。
- **验证：** T3-001/T4-001；单元测试固定输入 + 固定快照。
- **复审触发：** 引入 LUT 编辑器/用户自定义滤镜。

---

## D-013 — 统一错误模型：错误码 + 阶段 + 可重试性 + 用户动作

- **日期：** 2026-10-17
- **状态：** PROPOSED
- **类型：** 错误处理
- **背景：**
  - 规范 §10 给出错误码列表，但没有阶段、可重试性、用户动作的映射；不同层容易各自定义异常。
- **决定：**
  - 定义 `PocketCcdError(code, stage, retryable, userMessageRes, diagnostic)`；
  - `stage` 至少：`PERMISSION / CAMERA_OPEN / PREVIEW / CAPTURE / DECODE / PROCESS / ENCODE / WRITE / DB / CLEANUP`；
  - 每个错误码在 `TEST_PLAN.md` 有对应测试用例；
  - UI 只展示用户可理解的文案；诊断信息只进本地日志，不含照片内容/EXIF 敏感值。
- **后果：** 需要维护错误码到文案的映射；新增错误码必须同时加测试。
- **验证：** T2-005；故障注入测试。
- **复审触发：** 引入新的相机/存储 API。

---

## D-014 — 原图保留策略：7 天（推荐，待确认）

- **日期：** 2026-10-17
- **状态：** OPEN
- **类型：** 产品 / 隐私 / 存储
- **背景：**
  - 规范 §5.3 建议处理后删除临时原图；§2.2 C 要求“重新处理”。
  - 没有原图就无法精确重处理；永久保留会增加隐私和存储负担。
- **选项：**
  1. 发布成功后立即删除原图：最省空间/隐私最好，但无法重处理；
  2. 保留 7 天：窗口内可重处理，之后自动清理；推荐；
  3. 永久保留：可随时重处理，但存储/隐私成本最高，需要用户控制。
- **决定：** 暂不决定；阶段 1 UI 不承诺“永久可重处理”。在阶段 5 前由产品确认。
- **后果：** 若选 1，图库“重新处理”必须删除或标注不可用；若选 2，需要后台清理和到期提示；若选 3，需要“保留原图”设置和存储统计。
- **验证：** T5-006；清理任务测试。
- **复审触发：** 存储压力测试结果；隐私审查。

---

## D-015 — 权限：MVP 仅 CAMERA；API 29+ 不申请存储权限

- **日期：** 2026-10-17
- **状态：** PROPOSED（依赖 D-003/D-025 的 minSdk 确认）
- **类型：** 权限 / 隐私
- **背景：**
  - 规范 §9 要求最小权限，不提前申请麦克风。
  - `[F]` Android 10+ 访问自有 MediaStore 照片不需要存储权限。
  - `[F]` `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO` 受 Google Play 政策限制，推荐 Photo Picker。
- **决定：**
  - minSdk 29 时，Manifest 只声明 `CAMERA`；不声明麦克风、位置、存储、`READ_MEDIA_IMAGES`；
  - 不申请 `ACCESS_MEDIA_LOCATION`，默认不保留 GPS；
  - 若最终 minSdk 26，增加 `WRITE_EXTERNAL_STORAGE`（`maxSdkVersion=28`）并单独测试旧权限路径。
- **后果：** 应用只能访问自己写入的照片；重装后不能浏览旧照片（见 D-008）。
- **验证：** Manifest 合并检查；`aapt dump permissions`；权限拒绝/永久拒绝测试。
- **复审触发：** 需要导入/编辑系统照片；minSdk 改为 26。

---

## D-016 — 测试栈：JUnit4 + coroutines-test + Compose UI Test + 真机手测

- **日期：** 2026-10-17
- **状态：** PROPOSED
- **类型：** 测试
- **背景：**
  - 规范 §11 要求单元、图像、设备兼容测试，但没有指定框架。
  - 本机无测试依赖缓存、无 AVD、无设备；需要先补环境。
- **决定：**
  - 单元测试：JUnit4 + `kotlinx-coroutines-test` + Truth（或 Kotlin `assertEquals`，二选一，T1-003 定）；
  - Compose UI：`androidx.compose.ui:ui-test-junit4` + `createComposeRule`；
  - 仪器测试：`androidx.test.ext:junit` + `androidx.test.espresso:espresso-core`（仅 AndroidView 需要）；
  - 真机：相机、色彩、性能、存储必须真机；模拟器只做 UI/状态；
  - 不使用付费设备农场；不把模拟器相机结果当作相机兼容性结论。
- **后果：** 需要下载测试依赖并创建 AVD/连接设备；阶段 1 的 UI 测试在环境补齐前只能 `BLOCKED`。
- **验证：** T1-003 的测试依赖；`TEST_PLAN.md` 环境准备清单。
- **复审触发：** 需要截图回归（Paparazzi/Roborazzi）或 Macrobenchmark。

---

## D-017 — 不引入付费服务、不上传照片/个人数据

- **日期：** 2026-10-17
- **状态：** ACCEPTED
- **类型：** 隐私 / 范围
- **背景：** 用户明确要求；规范 §9/§15 也要求默认本地处理。
- **决定：** MVP 不接账号、云同步、分析 SDK、崩溃上报、在线 LUT、AI 生成；不请求 `INTERNET` 权限（R8/依赖若间接引入需审查）。
- **后果：** 无法远程诊断；只能本地日志导出。未来若引入，必须重新做隐私审查和权限说明。
- **验证：** Manifest 权限检查；依赖许可证/网络调用审查。
- **复审触发：** 产品要求云功能。

---

## D-018 — 参考相机只作研究，不造成官方合作误解

- **日期：** 2026-10-17
- **状态：** ACCEPTED
- **类型：** 法务 / 品牌
- **背景：** 规范 §6.1/§15.1 提到 Canon/Sony/Fujifilm 作为研究候选。
- **决定：**
  - UI/商店文案不出现品牌 Logo、机型名作为产品名；
  - 可用“参考早期消费级数码相机的色彩响应”描述；
  - 参考图片必须有明确许可；不复制商业 LUT/预设/闭源代码；
  - 发布前做商标和素材授权审查。
- **后果：** 营销表达受限；需保留来源/许可记录。
- **验证：** `research/reference-images.md`（阶段 4 建立）；发布检查表。
- **复审触发：** 商业发布/融资/品牌合作。

---

## D-019 — 阶段 1 只做工程初始化 + 静态 UI

- **日期：** 2026-10-17
- **状态：** ACCEPTED
- **类型：** 范围
- **背景：** 用户明确要求当前不做完整相机功能。
- **决定：** 阶段 1 不引入 CameraX 依赖、不申请相机权限、不写图像处理；相机页用静态占位，成片页用打包测试图，图库用 fake repository。
- **后果：** 阶段 1 无法验证真实相机行为；这是有意的范围控制。
- **验证：** T1-003 的依赖/权限检查；T1-008 的“无相机初始化”测试。
- **复审触发：** 阶段 1 验收通过。

---

## D-020 — 应用 ID / 应用名最终值

- **日期：** 2026-10-17
- **状态：** OPEN
- **类型：** 产品 / 发布
- **背景：** 应用 ID 一旦发布不可更改；当前没有域名/商标信息。
- **选项：**
  1. `com.pocketccd.app`（简短，但依赖域名/商标可用性）；
  2. `io.github.<owner>.pocketccd`（开源风格，需 owner）；
  3. 临时 `com.pocketccd.prototype`，发布前替换。
- **决定：** 阶段 1 用临时 ID；最终值由产品确认。临时 ID 不得用于发布。
- **后果：** 发布前需全局替换 `applicationId`/`namespace`/签名配置，并重新跑安装/升级测试。
- **验证：** T1-001；发布检查表。
- **复审触发：** 产品确认品牌名/域名。

---

## D-021 — 性能预算采用暂行目标，不作为对外承诺

- **日期：** 2026-10-17
- **状态：** PROPOSED
- **类型：** 性能
- **背景：** 规范 §7.3 要求先测量再承诺；但没有预算就无法判断“无限增加”。
- **决定（暂行目标，待真机修正）：**

| 指标 | 中端机目标 | 低端机目标 | 说明 |
|---|---|---|---|
| 冷启动到首页首帧 | ≤1.5s | ≤2.5s | 不含首次安装 dex 优化 |
| 相机打开到预览首帧 | ≤800ms | ≤1.5s | P95 |
| 快门到采集完成 | ≤1.5s | ≤2.5s | 12MP，MAXIMIZE_QUALITY |
| 12MP 处理耗时 | ≤2.5s | ≤5s | CPU 参考实现 |
| 处理峰值内存 | ≤250MB | ≤200MB | 12MP 输入+输出+中间缓冲 |
| 连续拍摄失败率 | 0/10 | 0/10 | 同场景 10 连拍 |

- **后果：** 目标未验证前只能写“目标”，不能写“达标”。低端机目标可能过严，需 EXP-06 修正。
- **验证：** T6-002；`TEST_PLAN.md` 性能协议。
- **复审触发：** 首轮真机基准完成。

---

## D-022 — 不做方向锁；适配竖屏/横屏/≥sw600dp

- **日期：** 2026-10-17
- **状态：** PROPOSED
- **类型：** UI / 平台行为
- **背景：**
  - `[F]` Android 16 target 36 时，大屏（≥sw600dp）会忽略方向/可调整尺寸/宽高比限制。
  - 规范 §11.3 要求测试设备旋转；§2.1 没有明确方向策略。
- **决定：**
  - Manifest 不锁 `screenOrientation`；
  - 首页/图库/设置支持竖屏和横屏；相机页横屏使用左右布局（预览 + 控制区），不做拉伸；
  - 使用 `WindowSizeClass`/`BoxWithConstraints` 做自适应；
  - 转场和相机重新绑定要处理旋转。
- **后果：** 设计和测试工作量增加；但避免 Android 16 大屏黑边/拉伸。
- **验证：** T1-013；T2-002 旋转测试。
- **复审触发：** 产品决定仅竖屏（仍不能阻止大屏窗口化，需要单独兼容策略）。

---

## D-023 — 诊断日志仅本地，Release 不含照片/EXIF 内容

- **日期：** 2026-10-17
- **状态：** ACCEPTED
- **类型：** 隐私 / 诊断
- **背景：** 规范 §10/§9 要求不把完整照片数据写日志，Debug/Release 区分。
- **决定：**
  - 日志只记录错误码、阶段、耗时、设备型号/API、图片尺寸（不含缩略图/像素/GPS）；
  - Debug 构建提供诊断页和日志导出；Release 关闭详细日志；
  - 不接入第三方崩溃/分析 SDK。
- **后果：** 线上问题只能靠用户手动导出；MVP 可接受。
- **验证：** T1-011 Debug 入口检查；日志脱敏单元测试。
- **复审触发：** 引入崩溃上报/分析。

---

## D-024 — 依赖锁定精确版本，使用版本目录；新增依赖需许可证审查

- **日期：** 2026-10-17
- **状态：** ACCEPTED
- **类型：** 依赖治理
- **背景：** 规范 §14.3 要求不随意升级依赖；本项目需要可复现构建。
- **决定：**
  - 使用 `gradle/libs.versions.toml`，禁止动态版本（`+`/`latest`）；
  - 每个新增依赖记录：用途、版本、许可证、维护状态、替代方案；
  - 优先 AndroidX/Google 官方库；不引入付费 SDK；
  - 构建时锁定 wrapper 版本；记录 JDK/AGP/Gradle。
- **后果：** 升级需要显式 PR/决策记录；构建更可复现。
- **验证：** T1-003 版本目录；依赖审查表。
- **复审触发：** 安全漏洞/许可证冲突/官方停止维护。

---

## D-025 — 目标设备档位与最低 Android 版本

- **日期：** 2026-10-17
- **状态：** OPEN
- **类型：** 产品 / 兼容性
- **背景：** 规范 §7.3 要求高/中/低三档设备，§15 又说旧手机是否核心用户未定。
- **选项：**
  1. 现代设备优先：minSdk 29，测试 Android 10–17 三档；
  2. 覆盖旧设备：minSdk 26，增加 Android 8/9 测试和旧存储权限；
  3. 只保证旗舰/中端：minSdk 31，减少兼容分支。
- **决定：** 暂按选项 1 规划；最终由产品确认。测试设备型号必须在阶段 2 前登记。
- **后果：** 影响 minSdk、权限、CameraX 行为、测试矩阵和发布说明。
- **验证：** `TEST_PLAN.md` 设备矩阵；T6-001。
- **复审触发：** 用户研究/市场数据；真实设备测试结果。

---

## D-026 — 阶段 1 单 `:app` 模块分包，不预建空模块

- **日期：** 2026-10-17
- **状态：** PROPOSED
- **类型：** 工程结构
- **背景：** 规范 §4.1 给出逻辑目录树，但明确“MVP 不要为了架构完整创建大量空模块”。
- **决定：**
  - 阶段 1 单模块 `:app`，按 `ui/`, `navigation/`, `domain/`, `data/`, `camera/`, `imaging/`, `filters/`, `storage/`, `diagnostics/` 分包；
  - 阶段 3 如果成像内核需要 JVM 单测，再拆 `:core:imaging`；
  - 不预建无代码模块。
- **后果：** 初期编译快、重构灵活；后期拆模块需要移动文件。
- **验证：** T1-003 目录结构；阶段 3 评审。
- **复审触发：** 单模块编译时间显著上升；需要独立测试/复用成像内核。

---

## D-027 — V2 UI 用“实体玩具相机”结构替代网页布局

- **日期：** 2026-10-17
- **状态：** IMPLEMENTED（用户反馈“好像还可以”，细节继续优化；真机视觉评审待做）
- **类型：** 设计 / 交互
- **背景：** 用户反馈“UI 太像网页，不像玩具相机和 app 的质感”。V1 的燕麦/灰绿配色虽然降了刺激，但仍是整页背景、文本链接、列表分隔线的网页结构。
- **决定：**
  1. 引入 `ToyCameraBody`、`ToyScreen`、`ToyRoundButton`：黄油塑料外壳、奶油内面板、深青 LCD、物理圆键；
  2. 首页改为一台玩具相机正面（镜头/闪光灯/珊瑚快门/FILM 键/设置键），去掉底部文本链接行；
  3. 拍摄页、照片墙、成片页、设置页都放进机身/屏幕结构，而不是整页网页；
  4. 主要字体从等宽改成加粗圆润无衬线 + 字距，模拟塑料压印；
  5. 颜色改为黄油 `#F2C14E`、奶油 `#FFF4E2`、薄荷 `#9FD8C4`、珊瑚 `#E76F51`、深青 `#173B3A`。
- **后果：** 页面更像实体设备，但也更容易显得幼龄/廉价；需要真机看黄油面积、阴影和对比度。
- **验证：** `TC-P1-038` 静态结构检查；`TC-P1-030` V2 对比度；T1-015 真机视觉验收。
- **复审触发：** 真机反馈“太童趣/太亮/太塑料”；或后续品牌设计提供正式视觉。

---

## D-028 — 拍摄页改为红色复古玩具相机前面板

- **日期：** 2026-10-17
- **状态：** IMPLEMENTED（真机视觉/相机功能未验证）
- **类型：** 设计 / 交互
- **背景：** 用户恢复 V2 后，给出红色复古玩具相机参考提示词，要求拍摄页呈现“红色塑料机身 + 厚重黑取景器 + 绿色闪光灯 + 橙色大快门 + 紧凑品牌”的第一眼印象。
- **决定：**
  1. 拍摄页全屏使用 `#D9382E` 红塑料渐变，`#A8231C` 暗部（调研后从 `#F12D27` 降刺激，见 `docs/red-camera-research.md`）；
  2. 近方形取景器：红色多层塑料边框 + 高光 + 20dp 阴影，内部纯黑 `#101010`，预览区 4:3 圆角内嵌，顶部中央绿色状态灯；
  3. 按钮一行：绿色闪光灯（前置时禁用/降级）、104dp 橙色快门（深色外圈 + 按压 0.94 缩放）、红色循环切换键；
  4. 快门下方居中 `POCKET CCD` 品牌；不添加顶部导航/标题/设置入口，返回由系统返回处理；
  5. 相册区按用户要求暂不实现；照片墙仍是 V2 胶片仓。
- **后果：** 拍摄页从“机身背板”变成红色前面板；V2 其他页面材质不变。预览目前是静态测试画面，不能冒充实时预览。
- **验证：** `TC-P1-039` 静态检查；`assembleDebug` 通过；12/12 单测；无设备截图。
- **复审触发：** 用户提供参考截图/真机反馈；或开始 CameraX 实时预览接入。

---

## D-029 — 首页统一红色复古主题

- **日期：** 2026-10-17
- **状态：** IMPLEMENTED（真机视觉未验证）
- **类型：** 设计 / 交互
- **背景：** 用户反馈“主页和相机 UI 太割裂”，要求首页也换成红色同款主题。
- **决定：**
  1. 首页全屏改为 `#D9382E` 红塑料渐变（与拍摄页同一色板）；
  2. 中央红色机身面板：黑色大镜头（薄荷绿圈）、绿色闪光窗、橙色 88dp 快门、绿色 `FILM` 键、深红设置小圆键；
  3. 品牌 `POCKET CCD` 放在机身面板顶部；不恢复网页式文本链接；
  4. 保留下拉进相机、上拉进照片墙；共享机身转场仍用同一 `camera-body` key。
- **后果：** 首页与拍摄页第一眼统一为红色玩具相机；照片墙/成片/设置仍是 V2 黄油 LCD，下一步可能需要继续统一。
- **验证：** `TC-P1-041` 静态检查；`TC-P1-030/040` 对比度；`assembleDebug`/`assembleRelease` 通过；无设备截图。
- **复审触发：** 真机视觉反馈；用户要求继续统一其他页面。

---

## D-030 — 红色主题细节：系统栏、转场闪角、品牌区减线

- **日期：** 2026-10-17
- **状态：** IMPLEMENTED（真机未验证）
- **类型：** 设计 / 动画 / 系统 UI
- **背景：** 用户反馈：转场时出现长方形边角闪烁；首页红色丑；顶部导航栏和主题不同色；品牌区 AI 痕迹重、线条过多。
- **决定：**
  1. 红色从 `#F12D27` 降刺激为 `#D9382E`，参考 softened Kodak red；奶油/薄荷/橙同步降饱和（详见 `docs/red-camera-research.md`）；
  2. 首页删除 `PULL DOWN · SHOOT`、外圈白线、闪光窗分割线、镜头弧线；品牌缩为 16sp/1.5sp，不再像网页大标题；
  3. `PocketCcdApp` 按目的地给 `Surface` 上色：HOME/CAMERA 红色，其余浅薄荷；用 `WindowCompat` 动态切换系统栏图标深浅；
  4. `sharedBounds` 增加 36dp `OverlayClip` 和 `zIndexInOverlay=1f`，避免 overlay 以矩形 bounds 闪角；
  5. `themes.xml` 启动背景改红，避免冷启动闪奶油色。
- **后果：** 红页系统栏与内容同色；品牌区更安静；转场矩形角理论上被裁剪，但仍需真机慢放确认。
- **验证：** `TC-P1-042`、`TC-P1-043`、`TC-P1-044` 静态检查；`assembleDebug`/`assembleRelease` 通过；12/12 单测。
- **复审触发：** 真机仍看到闪角/系统栏色差；红色仍被反馈刺眼或廉价。

---

## D-031 — 首页/拍摄页改为单一持久机身 Stage，废除 sharedBounds 跨页

- **日期：** 2026-10-17
- **状态：** IMPLEMENTED（真机动画未验证）
- **类型：** 架构 / 动画
- **背景：** 用户要求跟手动画、按钮在新页面重新排列；下拉开相机时页面闪动。排查发现旧方案是 `AnimatedContent` 两个页面 + `sharedBounds` overlay：Home 退出、Camera 进入时，overlay/圆角/背景层可能各画一帧，产生矩形边角闪动；且无法跟手。
- **决定：**
  1. 新增 `CameraStageScreen`，HOME/CAMERA 共用**同一个** `ToyCameraBody`，不再用 `AnimatedContent` 在两个页面间切换；
  2. 纵向拖动直接驱动 `Animatable<Float> progress`（0=首页，1=拍摄页）；机身从 300×300 的红框放大到接近全屏；
  3. 快门/闪光灯是同一个控件，位置和大小随 progress 插值；品牌上下移动；镜头淡出/取景器淡入；FILM/设置淡出，翻转键淡入；
  4. 移除 `SharedTransitionLayout`/`sharedBounds`；只保留 Gallery/Result/Settings 的轻量 `AnimatedContent` 淡入淡出；
  5. 首页/拍摄页红色外框 + 奶油内面板，照片墙/设置页的 `ToyCameraBody` 也统一为红色外框。
- **后果：** 下拉不再有第二个页面层；动画跟手。风险是自定义归一化布局需要真机调位置，按钮插值可能在小屏上偏位。
- **验证：** `TC-P1-045`、`TC-P1-046` 静态检查；`assembleDebug`/`assembleRelease` 通过；12/12 单测；无设备慢放。
- **复审触发：** 真机发现按钮重叠/比例不对；或 Stage 影响性能。

---

## D-032 — 设置页保留红框，内容改为 App 式布局

- **日期：** 2026-10-17
- **状态：** IMPLEMENTED（真机视觉未验证）
- **类型：** 设计 / 排版
- **背景：** 用户认可设置页红框，但反馈内容“字体太小、布局像网页，不像 App”。
- **决定：**
  1. 保留 `ToyCameraBody` 红框，去掉深青 8sp LCD 行；
  2. 内容改为 `LazyColumn` + 分组卡片；
  3. 页面标题 24sp，行标题 15sp，副标题 12sp，分组标签 11sp；
  4. 默认滤镜改为 44dp 三段式控件；
  5. 保存位置/质量/版本/隐私用 62dp 行，Debug 诊断只在 debug 显示。
- **后果：** 设置页更像常规 Android App，同时仍嵌在玩具相机红框里；小屏可滚动。
- **验证：** `TC-P1-047` 静态检查；`assembleDebug`/`assembleRelease` 通过。
- **复审触发：** 真机字体/间距仍不协调；或需要持久化设置后重排控件。

---

## D-033 — Fotufilm 采用离线 FSWP pack + C++ AOT + JNI 移植

- **日期：** 2026-10-18
- **状态：** PROPOSED（Phase 0 spike 未开始）
- **类型：** 架构 / 图像管线
- **背景：** 用户决定走原生移植，保留 Fotufilm 的真实胶片模型；公开仓库有 Android AOT kernel 与 FSWP pack 验证脚本，但没有 Android AAR/JNI/工程。
- **决定：**
  1. 不在 Android 上编译完整 Swift App；
  2. 在 macOS/Linux 构建机用 `fotufilm` 生成 FSWP pack，用 `generate_halide_android*` 生成 arm64 AOT kernels；
  3. Android 侧写 C++ JNI + pack parser，调用 `fotufilm_halide_process_tile/process_region`；
  4. CameraX 采集、Bitmap/线性 Rec.2020 解码、P3/sRGB 输出、JPEG 保存由 Pocket CCD 负责；
  5. 首批 3 个 stock（Gold200/Portra400/TriX400），再扩到 12、47。
- **后果：** 保留真实光谱/曲线/颗粒/halation；但需要 macOS/Linux 构建机、NDK、Halide、JNI、pack 解析和真机性能验证。
- **验证：** M8/FOT-002..004 + EXP-13；Phase 0 通过前不进入正式替换。
- **复审触发：** Phase 0 失败；pack/so 过大；12MP 性能不达标；上游 Android 格式变化。

---

## D-034 — Fotufilm 开源来源与 CC BY-SA 合规策略

- **日期：** 2026-10-18
- **状态：** ACCEPTED（法务复核仍待做）
- **类型：** License / 发布
- **背景：** 用户明确可以在 App 内标明开源来源；Fotufilm 引擎 Apache-2.0，47 个 film profiles CC BY-SA 4.0。
- **决定：**
  1. App 内增加“开源声明”页，注明 Fotufilm、链接、MUAStudio 版权、Apache-2.0/CC BY-SA 4.0；
  2. 不修改/不冒充 Fotufilm 品牌；胶片名加商标声明；
  3. 若分发 profile/LUT/pack，按 CC BY-SA 4.0 提供 attribution、修改说明和可获取的资产；
  4. 具体 ShareAlike 对 App 代码的边界由法务确认；确认前不发布含 profile 的 Release。
- **后果：** 增加发布合规工作；不影响渲染照片的归属。
- **验证：** `docs/fotufilm-license-inventory.md`；FOT-013。
- **复审触发：** 法务意见；改用 LUT 而非 profile；开源/闭源决策变化。
