# Pocket CCD — 任务板

**建立日期：** 2026-10-17  
**当前里程碑：** M1 — 项目初始化 + 静态 UI 原型（优先执行）  
**规范基线：** `DEVELOPMENT_SPEC.md` V1.0  
**配套文件：** `SPEC_REVIEW.md`、`DECISION_LOG.md`、`TEST_PLAN.md`、`PROJECT_STATE.md`

---

## 0. 使用规则

### 0.1 状态定义

| 状态 | 含义 | 是否可声称完成 |
|---|---|---|
| `TODO` | 已识别，未开始 | 否 |
| `READY` | 前置条件已满足，可开始 | 否 |
| `IN_PROGRESS` | 正在开发 | 否 |
| `BLOCKED` | 被决策、环境、依赖或设备阻塞 | 否 |
| `DONE` | 交付物已产出，但验收测试尚未执行 | 否 |
| `VERIFIED` | 验收标准已实际执行并通过，有证据 | 是 |

**规则：**
- `DONE` ≠ `VERIFIED`。没有运行过的测试不能把任务标成 `VERIFIED`。
- 每个 `VERIFIED` 必须关联 `TEST_PLAN.md` 的测试用例 ID 和证据路径。
- 任务范围变化时，先改本板，再改代码；不得静默扩大范围。
- 阶段 1 不实现相机；任何“先把相机接上再说”的提议都视为范围变更。

### 0.2 证据标记

`[F]` 已核实事实、`[I]` 工程推断、`[H]` 待验证假设、`[D]` 决策。详见 `SPEC_REVIEW.md`。

### 0.3 里程碑总览

| 里程碑 | 内容 | 任务数 | 状态 | 进入条件 |
|---|---|---:|---|---|
| M0 | 规范审查与工程文档 | 5 | `DONE`（文档已产出，待负责人评审） | 无 |
| M1 | 项目初始化 + 静态 UI 原型 | 16 | `IN_PROGRESS`（T1-002 `VERIFIED`；T1-003 构建 `DONE`；T1-005 `DONE`；T1-001/T1-015 受阻） | 本板确认 |
| M2 | 相机基础链路 | 8 | `TODO` | M1 退出 + EXP-01/02 |
| M3 | 图像处理内核 | 7 | `TODO` | M2 采集样本 + EXP-03/04/06 |
| M4 | 三个滤镜 | 5 | `TODO` | M3 管线稳定 + EXP-05/08/11 |
| M5 | 图库 / 成片 / 存储 | 7 | `TODO` | M3 输出稳定 + D-014 |
| M6 | 兼容性与性能 | 5 | `TODO` | M2–M5 功能可用 |
| M7 | 发布准备 | 4 | `TODO` | M6 通过 |

> 说明：M0 的 `DONE` 仅指文档文件已建立；不包含任何 App 构建或相机测试。文档内容仍需负责人评审后才能作为最终基线。

---

# M0 — 规范审查与工程文档

**目标：** 在不写 App 代码的前提下，把规范转成可执行的任务、决策和测试合同。  
**范围：** 研究、审查、文档；不包含 Android 工程、构建、相机、图像处理。

| ID | 任务 | 前置 | 模块/文件 | 交付物 | 验收标准 | 测试方式 | 状态 |
|---|---|---|---|---|---|---|---|
| G-001 | 规范逐节审查 | 读取 `DEVELOPMENT_SPEC.md` 全文 | `SPEC_REVIEW.md` | 矛盾/缺失/不可验证项清单；修订建议 | 每项有位置、影响、建议；区分事实/推断/假设 | 人工交叉检查 | `DONE` |
| G-002 | 官方文档版本核查 | 网络可访问官方页面 | `research/android-official-docs-2026-10-17.md` | API/AGP/Compose/CameraX/Camera2/MediaStore 事实表 + 来源 | 每条 `[F]` 有 URL、版本/日期、访问日期 | 逐条对照来源 | `DONE` |
| G-003 | 任务拆分 | G-001 | `TASK_BOARD.md` | M1 详细任务 + M2–M7 目录 + EXP 计划 | 每个 M1 任务有前置/模块/交付物/验收/测试 | 人工评审 | `DONE` |
| G-004 | 决策记录 | G-001/G-002 | `DECISION_LOG.md` | ADR 风格决策；ACCEPTED/PROPOSED/OPEN | 每个决策有背景/选项/结论/后果/复审触发 | 人工评审 | `DONE` |
| G-005 | 测试与状态文档 | G-003/G-004 | `TEST_PLAN.md`、`PROJECT_STATE.md` | 测试用例/证据规则/环境/风险/下一步 | 未执行项标 `NOT_RUN`/`BLOCKED`；无虚假通过 | 人工交叉检查 | `DONE` |

---

# M1 — 项目初始化 + 静态 UI 原型

**目标：** 在不依赖相机、不申请相机权限的前提下，交付一个可构建、可安装、可演示主要页面流程的 Compose 原型。  
**范围边界：** 静态 UI、fake 数据、状态机、设计令牌、手势、空/错/加载态、无障碍。  
**不包含：** CameraX、Camera2、图像处理、MediaStore 写入、Room 持久化、真实图库。

**M1 退出条件（摘要）：**
1. `assembleDebug` 成功，APK 可安装并启动；
2. 首页 → 图库 / 首页 → 拍摄 / 拍摄 → 图库 / 成片 → 拍摄/图库 可演示；
3. Manifest 不含 `CAMERA`、`INTERNET`、存储权限；
4. 无相机初始化代码；
5. 竖屏/横屏/大屏、200% 字体、TalkBack 基本通过；
6. 截图基线和评审记录存在；
7. `PROJECT_STATE.md`、`TEST_PLAN.md` 已更新，未执行项明确标 `NOT_RUN`。

---

## T1-001 — 确认产品标识与平台基线

| 字段 | 内容 |
|---|---|
| 优先级 | P0 |
| 状态 | `BLOCKED`（等待负责人确认；不阻塞 T1-003 使用临时 ID） |
| 预估 | 0.5 天（会议/确认） |
| 前置条件 | 产品负责人可决策；`DECISION_LOG.md` D-003/D-020/D-025 已阅读 |
| 涉及模块 | 构建配置、`docs/`、发布配置 |
| 交付物 | 更新后的 D-003/D-020/D-025；一页 `docs/project-baseline.md`（应用名、applicationId、minSdk、targetSdk、compileSdk、目标设备档位、是否上 Google Play） |
| 验收标准 | 所有值有明确结论，构建配置中不再出现 `TBD`；若仍用临时 ID，明确标注“禁止发布” |
| 测试方式 | 人工评审检查表；T1-003 后用 `aapt dump badging` 核对实际值 |
| 证据/用例 | `TC-ENV-001`、评审记录 |
| 风险/备注 | 应用 ID 发布后不可改；minSdk 影响权限分支；目标设备未定会导致 M6 范围漂移 |

**建议默认值（待确认，不是既成事实）：**
- applicationId/namespace：`com.pocketccd.prototype`（临时）
- compileSdk：37；targetSdk：36；minSdk：29
- 目标设备：高性能/中端/低端各 1 台，Android 10–17
- 发布范围：先本地/侧载验证，Google Play 决策延后

---

## T1-002 — 仓库与工程规范初始化

| 字段 | 内容 |
|---|---|
| 优先级 | P0 |
| 状态 | `VERIFIED`（Git 初始化、提交、忽略规则均已检查） |
| 预估 | 0.5 天 |
| 前置条件 | 工作区无 Git 仓库；不得覆盖 `DEVELOPMENT_SPEC.md` |
| 涉及模块 | 仓库根目录、`.gitignore`、`.gitattributes`、`.editorconfig`、`README.md` |
| 交付物 | Git 仓库；`.gitignore`（排除 `.gradle/`、`build/`、`local.properties`、`.idea/`、`*.iml`、`captures/`、`test-evidence/`、`.research/`）；`.gitattributes`（LF/CRLF 规则）；`.editorconfig`；提交规范；README 索引 |
| 验收标准 | `git status` 干净；`git check-ignore` 能忽略 `local.properties`/`build/`；规范文件不含密钥；`DEVELOPMENT_SPEC.md` 未被修改 |
| 测试方式 | `git status`、`git check-ignore -v`、人工 review；`git diff -- DEVELOPMENT_SPEC.md` 为空 |
| 证据/用例 | `TC-ENV-003` |
| 实际结果 | Git `main` 初始化并提交（初始提交 `482b804`）；`git check-ignore -v` 确认忽略 `local.properties`、`app/build/.../app-debug.apk`、`.research/`；`git status` 干净；`DEVELOPMENT_SPEC.md` 未修改 |
| 风险/备注 | 不提交签名文件、`local.properties`、设备照片；不引入 Git LFS 除非确定需要 |

---

## T1-003 — Gradle / Compose 空工程与构建验证

| 字段 | 内容 |
|---|---|
| 优先级 | P0 |
| 状态 | `DONE`（构建/manifest/SDK 已验证；安装/启动因无设备 `BLOCKED`，未达 `VERIFIED`） |
| 预估 | 1–1.5 天 |
| 前置条件 | Android SDK Platform 37、Build Tools 36.0.0；JDK 21（Studio JBR）；Gradle 9.6.1；网络/代理可拉取缺失依赖；T1-002 完成 |
| 涉及模块 | `settings.gradle.kts`、根 `build.gradle.kts`、`app/build.gradle.kts`、`gradle/libs.versions.toml`、`gradle/wrapper/`、`app/src/main/`、`MainActivity` |
| 交付物 | 可构建的 `:app` 空工程；Compose 主题骨架；版本目录；`local.properties.example`；构建说明；`./gradlew` wrapper |
| 验收标准 | `./gradlew :app:assembleDebug` 成功；APK 产物存在；Manifest 无 `CAMERA`/`INTERNET`/存储权限；`compileSdk=37`、`targetSdk=36`、`minSdk=29`；依赖无 alpha/beta（CameraX 本阶段不引入）；应用可启动（需设备/模拟器） |
| 测试方式 | `./gradlew :app:assembleDebug`；`aapt dump badging`；`aapt dump permissions`；`adb install` + `am start`（环境具备时）；`./gradlew :app:dependencies` |
| 证据/用例 | `TC-ENV-002`、`TC-P1-001`、`TC-P1-017` |
| 实际结果 | 2026-10-17 `./gradlew :app:assembleDebug` **BUILD SUCCESSFUL**（首次 59s，wrapper 复跑 up-to-date）；APK `app/build/outputs/apk/debug/app-debug.apk`，29,570,230 bytes，SHA-256 `B77DB7171C99DDC10685DCB046512BD8BC48B2801679DAEDBDE47A702168912C`；`aapt` 确认 compileSdk 37 / targetSdk 36 / minSdk 29；无 CAMERA/INTERNET/存储权限（仅有 AndroidX 自定义签名权限）；`TC-ENV-004` `BLOCKED`（无设备） |
| 风险/备注 | 已把“构建成功”和“App 已验证”分开：安装/启动/UI 仍待 T1-015。首次构建出现 SDK platform `android-37.0` 与 `android-37.0-2` 重复目录警告，但不影响构建，记录在 `PROJECT_STATE.md`。 |

**已验证工具链（`[D]` D-004，2026-10-17 T1-003 构建通过）：**

```text
AGP              9.3.3
Gradle           9.6.1
JDK              21（Java target 17）
Kotlin           内建（AGP 9.3.3）
Compose 插件     2.3.21
Compose BOM      2026.08.00（→ Compose 1.12.0 / Material3 1.4.0）
compileSdk       37
targetSdk        36
minSdk           29
```

**备选（本次未使用）：** AGP 9.4.0（官方最新，最高 API 37，最低 Gradle 9.6.0；本机未缓存）；若未来升级，必须重新执行 `TC-ENV-002`，不能因为“版本更新”就假定可用。

**禁止：**
- 在阶段 1 引入 `androidx.camera:*`；
- 申请 `CAMERA` 权限；
- 为了“先跑起来”引入付费 SDK、统计 SDK、网络库；
- 提交 `local.properties` 或签名文件。

---

## T1-004 — 设计令牌与主题

| 字段 | 内容 |
|---|---|
| 优先级 | P0 |
| 状态 | `TODO` |
| 预估 | 1 天 |
| 前置条件 | T1-003 构建成功；视觉参考/原型截图（没有则用临时值并标注） |
| 涉及模块 | `ui/theme/`（Color、Type、Shape、Spacing、Motion、Theme） |
| 交付物 | `PocketCcdTheme`；颜色/表面/文字/强调/错误色；间距、圆角、控件尺寸、字体层级、动画时长、点击反馈、禁用/加载态令牌；`docs/design-tokens.md` |
| 验收标准 | 页面不出现散落硬编码颜色/尺寸；所有交互控件≥48dp；正文对比度≥4.5:1；字体放大 200% 不截断；令牌有“临时/待评审”标记 |
| 测试方式 | Compose Preview；对比度计算（人工/脚本）；`grep` 检查硬编码；T1-013 字体缩放测试 |
| 证据/用例 | `TC-P1-012`、`TC-P1-015`、`TC-P1-016`、设计评审记录 |
| 风险/备注 | 规范 §2.1 明确“未完成视觉原型前不要固定全部数值”；本任务只固定结构令牌，视觉值可替换 |

**建议令牌分组：**

| 组 | 例子 | 说明 |
|---|---|---|
| Color | `background`, `surface`, `surfaceElevated`, `textPrimary`, `textSecondary`, `accent`, `error` | 深色机身；避免紫色赛博朋克 |
| Spacing | 4/8/12/16/24/32dp | 统一边距 |
| Shape | 8/12/16/24dp | 适度圆角 |
| Type | display/title/body/label | 字号/行高/字重 |
| Motion | fast 120ms / base 200ms / slow 320ms | 克制；支持 reduce motion |
| Control | shutter 72dp、icon button 48dp、chip 32dp 高等 | 触摸目标与视觉尺寸分离 |

---

## T1-005 — 页面状态机与导航骨架

| 字段 | 内容 |
|---|---|
| 优先级 | P0 |
| 状态 | `DONE`（状态机 + 12 个单元测试通过；Compose UI/旋转测试待设备） |
| 预估 | 1 天 |
| 前置条件 | T1-003；D-007 |
| 涉及模块 | `navigation/`、`ui/AppRoot.kt`、`AppViewModel` |
| 交付物 | `AppDestination`（HOME/CAMERA/GALLERY/RESULT/SETTINGS）；`AppEvent`；转换守卫；`BackHandler`；`AnimatedContent` 骨架；状态机单元测试 |
| 验收标准 | 覆盖规范 §2.3 全部转换；非法转换被忽略；动画期间重复事件不重复导航；旋转后状态保持；返回行为符合预期；无 Navigation-Compose 依赖 |
| 测试方式 | 纯 Kotlin 单元测试（转换矩阵）；Compose UI 测试（点击/返回）；旋转手测 |
| 证据/用例 | `TC-P1-026`（已 PASS）；`TC-P1-002..011`（待设备） |
| 实际结果 | 2026-10-17 实现 `AppDestination`/`AppEvent`/`reduce`/`AppViewModel`/`PocketCcdApp`；`AppStateTest` 12 tests / 0 failures；`assembleDebug` 与 `testDebugUnitTest` 均 `BUILD SUCCESSFUL`；Compose UI/旋转仍待 T1-015 |
| 风险/备注 | `RESULT → CAMERA` 在真实相机阶段意味着重新绑定用例；阶段 1 只验证导航，不验证相机恢复 |

**状态机草案：**

```text
HOME
 ├─ OPEN_CAMERA → CAMERA
 ├─ OPEN_GALLERY → GALLERY
 └─ OPEN_SETTINGS → SETTINGS

CAMERA
 ├─ BACK → HOME
 ├─ OPEN_GALLERY → GALLERY
 └─ CAPTURE → CAPTURING → PROCESSING → RESULT
                         └─ FAILED → CAMERA(error)

GALLERY
 ├─ BACK → 来源页（HOME 或 CAMERA 或 RESULT）
 └─ OPEN_PHOTO → RESULT

RESULT
 ├─ BACK_TO_CAMERA → CAMERA
 └─ OPEN_GALLERY → GALLERY
```

> 阶段 1 的 `CAPTURING/PROCESSING/RESULT` 由 fake fixture 驱动；不调用相机。

---

## T1-006 — 首页 Home 静态 UI

| 字段 | 内容 |
|---|---|
| 优先级 | P0 |
| 状态 | `TODO` |
| 预估 | 1 天 |
| 前置条件 | T1-004、T1-005 |
| 涉及模块 | `ui/home/` |
| 交付物 | 品牌标识、视觉主体、进入相机 CTA、图库入口、设置入口、下拉提示；Compose Preview；截图 |
| 验收标准 | 打开首页不启动相机、不请求权限；360×800/412×915/横屏/大屏无溢出；CTA 可点击且有语义标签；图库/设置入口可达；下拉提示不遮挡系统栏 |
| 测试方式 | Compose UI 测试；截图评审；`adb shell dumpsys media.camera`/日志确认无相机初始化（阶段 1 可用代码检查替代） |
| 证据/用例 | `TC-P1-001`、`TC-P1-012`、`TC-P1-013`、`TC-P1-020` |
| 风险/备注 | “精致”只能通过评审清单判断；不得用自动测试宣称审美通过 |

---

## T1-007 — 下拉进入相机手势与转场

| 字段 | 内容 |
|---|---|
| 优先级 | P0 |
| 状态 | `TODO` |
| 预估 | 1.5 天 |
| 前置条件 | T1-006；EXP-09（可先用手势阈值假设） |
| 涉及模块 | `ui/home/`、`navigation/` |
| 交付物 | 垂直拖拽检测；距离/速度阈值；取消/回弹；动画；防重复触发；无障碍替代按钮；阈值文档 |
| 验收标准 | 拖拽超过阈值且速度足够才触发；未超过阈值回弹不导航；图库滚动/系统返回不误触；动画期间重复手势不叠加；TalkBack 可通过按钮进入相机；阈值有记录 |
| 测试方式 | Compose 手势测试（`performTouchInput`）；真机/模拟器手测；慢动作录屏；`TC-P1-004..006` |
| 证据/用例 | `TC-P1-004`、`TC-P1-005`、`TC-P1-006`、`TC-P1-024` |
| 风险/备注 | 下拉手势与系统通知栏/返回手势可能冲突；必须提供按钮替代，不能把手势作为唯一入口 |

**初始假设（`[H]`，待 EXP-09）：** 阈值 72dp 或屏幕高度 20%，速度阈值 800dp/s；以实际手感为准。

---

## T1-008 — 拍摄页 Camera 静态 UI

| 字段 | 内容 |
|---|---|
| 优先级 | P0 |
| 状态 | `TODO` |
| 预估 | 1.5 天 |
| 前置条件 | T1-004、T1-005；D-019（不接相机） |
| 涉及模块 | `ui/camera/`、`ui/components/` |
| 交付物 | 取景占位（静态图/纯色，4:3，与后续输出策略一致）；中央快门；滤镜 chip；闪光灯状态；前后摄切换；图库入口；返回；状态区（CAPTURING/PROCESSING/ERROR） |
| 验收标准 | 无 CameraX/Camera2 类、无 `CAMERA` 权限；竖屏/横屏布局合理；快门/图标≥48dp；禁用态/加载态可见；安全区不被刘海/手势条遮挡；状态区不遮挡取景 |
| 测试方式 | Compose UI 测试；`grep`/依赖检查；`aapt dump permissions`；截图 |
| 证据/用例 | `TC-P1-003`、`TC-P1-008`、`TC-P1-017`、`TC-P1-020` |
| 风险/备注 | 取景占位比例必须与后续成片 4:3 策略一致，避免阶段 2 重做布局 |

---

## T1-009 — 图库 Gallery 静态 UI

| 字段 | 内容 |
|---|---|
| 优先级 | P1 |
| 状态 | `TODO` |
| 预估 | 1.5 天 |
| 前置条件 | T1-004、T1-005；fake repository |
| 涉及模块 | `ui/gallery/`、`domain/`（fake records） |
| 交付物 | 时间倒序网格；缩略图占位；状态徽标（处理中/完成/失败）；空态/加载/错误；单张详情预览；重试入口（仅失败态） |
| 验收标准 | 无媒体权限；不加载全尺寸图；网格 key 稳定；空态有引导；失败态有原因/重试；排序正确；滚动不 ANR |
| 测试方式 | Compose UI 测试；fake repository 单元测试；大数据量（200 条 fake）滚动手测 |
| 证据/用例 | `TC-P1-002`、`TC-P1-019`、`TC-P1-020` |
| 风险/备注 | 阶段 1 不实现真实 MediaStore；不要为了“看起来有照片”申请存储权限 |

---

## T1-010 — 成片页 Result 静态 UI

| 字段 | 内容 |
|---|---|
| 优先级 | P1 |
| 状态 | `TODO` |
| 预估 | 1 天 |
| 前置条件 | T1-005；打包测试图 |
| 涉及模块 | `ui/result/` |
| 交付物 | 成片展示；滤镜名/版本；保存状态（保存中/成功/失败）；返回拍摄；进入图库；失败重试 |
| 验收标准 | 保存完成前不显示“保存成功”；失败态有明确动作；返回/图库路径可达；图片不因缩放变形；长按/双击行为（若定义）有测试 |
| 测试方式 | Compose UI 测试；状态快照；截图 |
| 证据/用例 | `TC-P1-009`、`TC-P1-010`、`TC-P1-019` |
| 风险/备注 | 阶段 1 的“保存成功”是 fake 状态；必须标注 demo 数据 |

---

## T1-011 — 设置页 Settings 静态 UI

| 字段 | 内容 |
|---|---|
| 优先级 | P1 |
| 状态 | `TODO` |
| 预估 | 0.5 天 |
| 前置条件 | T1-004、T1-005 |
| 涉及模块 | `ui/settings/`、`BuildConfig` |
| 交付物 | 保存位置说明；默认滤镜；质量选项占位；版本信息；隐私说明；Debug 诊断入口（仅 debug） |
| 验收标准 | Release 构建不显示 Debug 入口；无网络请求；无权限；文案可理解；版本号来自 `BuildConfig` |
| 测试方式 | Compose UI 测试；`assembleRelease`（未签名也可）检查 BuildConfig；`aapt dump permissions` |
| 证据/用例 | `TC-P1-018`、`TC-P1-017` |
| 风险/备注 | 质量选项在阶段 1 只是占位，不得暗示已实现 |

---

## T1-012 — 状态矩阵与错误/空/加载态

| 字段 | 内容 |
|---|---|
| 优先级 | P0 |
| 状态 | `TODO` |
| 预估 | 1 天 |
| 前置条件 | T1-006..T1-011 |
| 涉及模块 | `ui/components/`、`diagnostics/`（debug 状态切换） |
| 交付物 | 页面状态矩阵；统一 Loading/Empty/Error 组件；Debug 菜单强制状态；错误文案表 |
| 验收标准 | 每个页面至少覆盖 loading/empty/error/正常；每个错误有用户文案和动作；无死路；Debug 状态切换不影响 Release；状态矩阵文档完整 |
| 测试方式 | Compose UI 测试逐状态；手测 Debug 菜单；文案评审 |
| 证据/用例 | `TC-P1-019`、`TC-P1-018` |
| 风险/备注 | `PROCESSING_ERROR` 是导航态还是照片状态必须在 D-013/T1-005 中定清，不能两套枚举混用 |

---

## T1-013 — 自适应布局与无障碍

| 字段 | 内容 |
|---|---|
| 优先级 | P0 |
| 状态 | `TODO` |
| 预估 | 1.5 天 |
| 前置条件 | T1-012；D-022 |
| 涉及模块 | `ui/` 全部 |
| 交付物 | `WindowSizeClass`/`BoxWithConstraints` 适配；横屏/大屏布局；字体缩放；TalkBack 标签/顺序；对比度修正；reduce motion 支持 |
| 验收标准 | 360dp/412dp/≥600dp 宽度可用；横屏不拉伸；字体 200% 不截断；所有图标有 contentDescription；焦点顺序合理；正文对比度≥4.5:1；触控目标≥48dp |
| 测试方式 | Compose 语义测试；Accessibility Scanner（真机/模拟器）；200% 字体截图；TalkBack 手测 |
| 证据/用例 | `TC-P1-012`、`TC-P1-013`、`TC-P1-014`、`TC-P1-015`、`TC-P1-016` |
| 风险/备注 | Android 16 target 36 大屏会忽略方向锁；不能靠“锁竖屏”规避 |

---

## T1-014 — 视觉基线截图与评审

| 字段 | 内容 |
|---|---|
| 优先级 | P1 |
| 状态 | `TODO` |
| 预估 | 0.5–1 天 |
| 前置条件 | T1-013；可运行 APK/模拟器 |
| 涉及模块 | `test-evidence/`（不提交大文件到 Git 时可放本地）、`docs/design-review.md` |
| 交付物 | 360×800、412×915、横屏、≥600dp、字体 100%/200% 截图；设计评审清单；已知偏差 |
| 验收标准 | 截图来自实际构建；评审清单逐项签字；无 P0 视觉缺陷；偏差记录在 `PROJECT_STATE.md` |
| 测试方式 | 手工截图（`adb exec-out screencap`）；设计评审会议；不宣称像素级自动通过 |
| 证据/用例 | `TC-P1-020` |
| 风险/备注 | 没有视觉参考图时，评审只能判断一致性和可用性，不能判断“像不像目标产品” |

---

## T1-015 — 创建模拟器/连接测试设备

| 字段 | 内容 |
|---|---|
| 优先级 | P0 |
| 状态 | `BLOCKED`（本机无 AVD、无 system image、无设备） |
| 预估 | 0.5–1 天（下载/配置） |
| 前置条件 | Android SDK；网络/代理；至少 8GB 磁盘 |
| 涉及模块 | 测试环境（不属于 App 代码） |
| 交付物 | 1 个 API 36/37 AVD 或 1 台真机；`adb devices` 可见；设备信息登记 |
| 验收标准 | 可安装并启动 APK；可截图；可运行 Compose UI 测试；设备型号/API/RAM 记录 |
| 测试方式 | `adb devices`、`adb install`、`adb shell getprop`、`adb exec-out screencap` |
| 证据/用例 | `TC-ENV-004` |
| 风险/备注 | 模拟器不能验证相机/ISP/色彩；相机测试必须真机。无 system image 时需通过 Studio SDK Manager 安装 |

---

## T1-016 — 原型性能与稳定性烟雾测试

| 字段 | 内容 |
|---|---|
| 优先级 | P1 |
| 状态 | `TODO` |
| 预估 | 0.5 天 |
| 前置条件 | T1-015；T1-014 |
| 涉及模块 | `diagnostics/`、构建配置 |
| 交付物 | 冷启动、Home 首帧、页面切换 jank 的基线记录；无崩溃/ANR 记录 |
| 验收标准 | 连续 20 次页面往返无崩溃；冷启动中位数记录；不承诺固定毫秒数，只建立基线 |
| 测试方式 | `adb shell am start -W` ×5；Perfetto/JankStats（可选）；`adb logcat` 检查崩溃 |
| 证据/用例 | `TC-P1-021`、`TC-P1-022` |
| 风险/备注 | 阶段 1 没有相机/大图，性能数据不能外推到阶段 2+ |

---

## M1 依赖关系

```text
T1-001（产品标识，BLOCKED）──┐
T1-002（Git/规范）───────────┼→ T1-003（空工程/构建）
T1-003 ─┬→ T1-004（设计令牌）─┬→ T1-006（首页）→ T1-007（手势）
        ├→ T1-005（状态机）───┼→ T1-008（拍摄页）
        │                     ├→ T1-009（图库）
        │                     ├→ T1-010（成片页）
        │                     └→ T1-011（设置页）
        └→ T1-015（设备/模拟器）→ T1-014（截图评审）
T1-006..T1-011 → T1-012（状态矩阵）→ T1-013（自适应/无障碍）→ T1-014
T1-014 + T1-015 → T1-016（性能烟雾）→ M1 退出评审
```

> 注：T1-001 的最终值可在 T1-003 之后补齐；T1-003 使用临时 ID，不得发布。

---

## M1 退出清单

- [ ] T1-001 决策完成（或明确延期，且临时值标记为禁止发布）
- [x] T1-002 Git/规范完成
- [x] T1-003 构建成功（`assembleDebug`）
- [ ] T1-003 安装/启动验证（待 T1-015 设备/模拟器）
- [ ] T1-004 设计令牌完成
- [x] T1-005 状态机完成（12 单元测试通过；UI/旋转待设备）
- [ ] T1-006..T1-011 五个页面完成
- [ ] T1-012 状态矩阵完成
- [ ] T1-013 自适应/无障碍完成
- [ ] T1-014 视觉基线评审完成
- [ ] T1-015 设备/模拟器可用
- [ ] T1-016 性能烟雾完成
- [ ] 所有 `VERIFIED` 任务有证据；未执行项标 `NOT_RUN`
- [ ] `PROJECT_STATE.md` / `TEST_PLAN.md` 已更新

---

# M2 — 相机基础链路（后备任务）

**进入条件：** M1 退出；EXP-01/EXP-02 有初步结论；D-005/D-006 确认。

| ID | 任务 | 前置 | 模块 | 交付物 | 验收标准 | 测试方式 | 状态 |
|---|---|---|---|---|---|---|---|
| T2-001 | 相机权限流程 | T1-003；D-015 | `camera/permission`、`ui/camera` | 权限请求/解释/永久拒绝引导；状态流 | 未授权不打开相机；拒绝有解释；永久拒绝可去设置；旋转/后台不重复弹窗 | 真机权限矩阵测试 `TC-CAM-001..004` | `TODO` |
| T2-002 | CameraX 预览与生命周期 | T2-001；D-006 | `camera/CameraController`、`ui/camera` | `PreviewView` + `ProcessCameraProvider`；绑定/解绑；前后台 | 预览比例正确；旋转不拉伸；后台释放；返回恢复；无泄漏 | 真机 `TC-CAM-005..010`；LeakCanary/日志 | `TODO` |
| T2-003 | 高分辨率采集与分辨率策略 | T2-002；EXP-01；D-003 | `camera/CaptureCoordinator` | `ImageCapture` 配置；`ResolutionSelector`；JPEG 输出；尺寸/方向记录 | 输出 4:3、≥12MP、长边≥4000、无上采样；实际尺寸=采集尺寸；连续拍摄稳定 | 真机拍 20 张，读 JPEG 尺寸；`TC-CAM-011..014` | `TODO` |
| T2-004 | 临时文件、EXIF、尺寸校验 | T2-003 | `storage/ProcessingCache`、`imaging/ImageDecoder` | 临时文件命名/清理；EXIF 读取；方向归一；尺寸/格式校验 | 不覆盖已有文件；方向正确；像素旋转与 EXIF 不双重旋转；损坏文件有错误码 | 单元测试 + 真机 `TC-CAM-015..018` | `TODO` |
| T2-005 | 拍摄错误模型与恢复 | T2-001..004；D-013 | `domain/error`、`camera` | 错误码映射；重试/取消；相机资源回收 | 每个错误有码/阶段/文案；失败后相机可再次打开；临时文件可控 | 故障注入 `TC-CAM-019..022` | `TODO` |
| T2-006 | 镜头/闪光灯/旋转能力 | T2-002 | `camera/CameraCapabilities` | 前/后摄、闪光灯、屏幕闪光、旋转能力探测 | 不支持的能力隐藏/禁用；切换不崩溃；闪光灯模式生效/有记录 | 真机 `TC-CAM-023..028` | `TODO` |
| T2-007 | 预览-成片构图一致性 | T2-002/003；EXP-01 | `camera`、`ui/camera` | 视口/裁切策略；对照报告 | 预览与成片的构图/裁切差异在声明范围内；方向一致；有记录 | 同场景对照拍摄 `TC-CAM-029` | `TODO` |
| T2-008 | 设备能力探针 | T2-002 | `diagnostics/DeviceCapabilities` | 相机 ID、硬件级别、支持的尺寸/闪光灯/扩展 | 能导出能力 JSON；不采集照片/个人信息 | 真机 `TC-CAM-030` | `TODO` |

**M2 退出条件：** 能在至少 1 台真机完成“预览→拍照→保存普通 JPEG→系统图库可见”；输出尺寸可证明来自采集而非预览截图；有设备测试记录。

---

# M3 — 图像处理内核（后备任务）

**进入条件：** M2 有稳定测试样本；EXP-03/04/06 结论；D-009/010/011/012 确认。

| ID | 任务 | 前置 | 模块 | 交付物 | 验收标准 | 测试方式 | 状态 |
|---|---|---|---|---|---|---|---|
| T3-001 | 解码/编码与像素缓冲抽象 | M2；EXP-03 | `imaging/ImageDecoder`、`JpegEncoder`、`PixelBuffer` | 统一解码/编码接口；尺寸/色彩空间元数据；错误码 | 固定输入可重复解码；输出尺寸正确；OOM 有降级；编码质量可配置 | JVM/仪器测试 `TC-IMG-001..005` | `TODO` |
| T3-002 | sRGB/线性色彩管线 | T3-001；EXP-04 | `imaging/ColorTransform` | sRGB↔线性转换；ICC 处理；矩阵 | 已知色块转换误差在容差内；不做隐式 P3；输出 ICC 正确 | 色块图 + ΔE2000 `TC-IMG-006..009` | `TODO` |
| T3-003 | 曲线/矩阵/饱和度/对比度 | T3-002 | `imaging/ToneCurve`、`ColorTransform` | 参数化曲线/矩阵；边界校验 | 固定参数输出确定性；曲线单调/端点正确；非法参数拒绝 | 单元测试 + golden `TC-IMG-010..014` | `TODO` |
| T3-004 | 高光/阴影/局部对比度 | T3-003 | `imaging/HighlightShadow`、`LocalContrast` | 可开关模块；参数范围 | 高光不异常溢出；阴影不塑料感；关闭时 bit-exact 通过 | 测试图 + 视觉评审 `TC-IMG-015..018` | `TODO` |
| T3-005 | 锐化/颗粒（可选） | T3-003；EXP-07 | `imaging/Sharpening`、`Grain` | 亮度锐化；种子化颗粒；开关 | 无色边；颗粒可复现（同种子）；关闭时无副作用 | golden + 视觉评审 `TC-IMG-019..022` | `TODO` |
| T3-006 | 3D LUT 加载与插值实验 | T3-002；EXP-05 | `imaging/Lut` | `.cube`/二进制加载；三线性插值；越界策略 | 已知 LUT 输出误差在容差内；网格尺寸/内存记录 | 单元测试 + 对比 `TC-IMG-023..025` | `TODO` |
| T3-007 | 图像处理测试集、golden、基准 | T3-001..006 | `test/`、`benchmark/` | 固定测试图；golden；耗时/内存报告 | 固定输入固定参数可复现；GPU 有容差；测试集覆盖 10 类场景 | 自动测试 + 基准 `TC-IMG-026..032` | `TODO` |

**M3 退出条件：** 处理器可重复、输入输出尺寸/色彩空间明确；有性能基准；有一个可运行的基础滤镜链（不含最终风格）。

---

# M4 — 三个滤镜（后备任务）

**进入条件：** M3 稳定；EXP-05/08/11 结论；参考图许可明确。

| ID | 任务 | 前置 | 模块 | 交付物 | 验收标准 | 测试方式 | 状态 |
|---|---|---|---|---|---|---|---|
| T4-001 | 滤镜 schema/加载/版本 | M3；D-012 | `filters/FilterDefinition`、`FilterRepository` | JSON schema；加载/校验；版本；解析快照 | 非法参数被拒绝；版本可追溯；加载失败有错误码 | 单元测试 `TC-FLT-001..004` | `TODO` |
| T4-002 | DAYLIGHT | T4-001；参考样片 | `filters/presets/daylight` | 参数；前后对照；耗时；限制 | 固定测试集有对照；盲评优于无处理；不偏色/不塑料 | `TC-FLT-005..008` | `TODO` |
| T4-003 | FLASH（色调风格） | T4-001；参考样片 | `filters/presets/flash` | 参数；前后对照；限制（不模拟物理闪光） | 仅色彩/色调变化；不宣称补光；肤色可接受 | `TC-FLT-009..012` | `TODO` |
| T4-004 | NIGHT | T4-001；参考样片 | `filters/presets/night` | 参数；前后对照；噪声/颗粒策略 | 暗部不统一加彩噪；高光不溢出；细节可接受 | `TC-FLT-013..016` | `TODO` |
| T4-005 | 滤镜对照评审与已知限制 | T4-002..004；EXP-11 | `docs/filter-specifications.md` | 对照报告；盲评记录；已知限制 | 每个滤镜有参数/样片/测试输入/对照/耗时/限制/版本 | 评审 `TC-FLT-017` | `TODO` |

**M4 退出条件：** 三个滤镜可切换、可复现、有对照和限制说明；不得只凭开发者主观评价通过。

---

# M5 — 图库 / 成片 / 存储（后备任务）

**进入条件：** M2/M3 输出稳定；D-008/D-014 确认；EXP-10 结论。

| ID | 任务 | 前置 | 模块 | 交付物 | 验收标准 | 测试方式 | 状态 |
|---|---|---|---|---|---|---|---|
| T5-001 | Room `PhotoRecord` schema | M3；D-008 | `data/database`、`data/model` | Entity/DAO/迁移；字段（尺寸、URI、状态、快照、时间、错误） | 升级迁移可测；查询按时间倒序；不存图片字节 | 单元测试/Room 测试 `TC-STO-001..003` | `TODO` |
| T5-002 | 状态机/事务/幂等 | T5-001；C-03 | `domain/PhotoStatus`、`storage/PhotoRepository` | 状态转换表；事务；幂等键；对账 | 非法转换拒绝；重复写入不产生重复记录；崩溃后状态可恢复 | 单元测试 + 故障注入 `TC-STO-004..008` | `TODO` |
| T5-003 | MediaStoreWriter + `IS_PENDING` | T5-002 | `storage/MediaStoreWriter` | 插入/写入/校验/发布；失败清理 | 写入中不可见；成功后系统图库可见；失败不留 pending；URI 可读 | 真机 `TC-STO-009..012` | `TODO` |
| T5-004 | 缩略图与图库数据源 | T5-001/003 | `gallery`、`storage/ThumbnailCache` | 256px 缩略图；分页；缓存 | 200 张滚动不 OOM；缩略图与成片一致；失败占位 | 真机/模拟器 `TC-STO-013..015` | `TODO` |
| T5-005 | 图库/成片页真实数据 | T5-003/004 | `ui/gallery`、`ui/result` | 真实记录；状态徽标；失败重试 | 跨重启保留；状态正确；无重复照片 | 真机 `TC-STO-016..019` | `TODO` |
| T5-006 | 重试/重新处理/清理 | T5-002/003；D-014 | `storage/ProcessingCache`、`domain/Retry` | 重试；原图保留窗口；清理任务 | 重试不覆盖成功文件；过期清理安全；无孤儿临时文件 | 故障注入 `TC-STO-020..023` | `TODO` |
| T5-007 | 启动恢复与一致性校验 | T5-002..006 | `storage/Reconciler` | 启动对账；pending 修复；摘要 | 崩溃点可恢复；不把失败当成功；日志有错误码 | 崩溃矩阵 `TC-STO-024..027` | `TODO` |

**M5 退出条件：** 拍摄—处理—保存—查看闭环；跨应用重启保留；失败可诊断/恢复；系统图库可见。

---

# M6 — 兼容性与性能（后备任务）

| ID | 任务 | 前置 | 模块 | 交付物 | 验收标准 | 测试方式 | 状态 |
|---|---|---|---|---|---|---|---|
| T6-001 | 设备兼容性矩阵 | M5；D-025 | `docs/compatibility.md` | 3 档设备记录；相机/存储/权限矩阵 | 登记设备通过；未登记设备标注未验证 | 真机矩阵 `TC-COMPAT-001..020` | `TODO` |
| T6-002 | 性能/内存基准 | M5；D-021 | `benchmark/`、`docs/performance.md` | 冷启动/采集/处理/峰值内存；连续拍摄 | 达到暂行预算或记录偏差；无 OOM/ANR | 真机基准 `TC-PERF-001..008` | `TODO` |
| T6-003 | 故障注入/低存储/后台/锁屏 | M5 | `diagnostics/`、测试脚本 | 故障矩阵；恢复报告 | 低存储有提示；后台/锁屏不崩溃；恢复正确 | `TC-COMPAT-021..030` | `TODO` |
| T6-004 | Release 构建/混淆/签名验证 | M5 | `app/build.gradle.kts`、`proguard-rules.pro` | Release APK/AAB；映射文件；签名验证 | Release 可安装；关键流程不因 R8 崩溃；无调试入口 | `assembleRelease` + 真机 `TC-REL-001..005` | `TODO` |
| T6-005 | 已知限制与兼容性文档 | T6-001..004 | `docs/compatibility.md`、`PROJECT_STATE.md` | 已知限制；不支持设备；性能结论 | 文档与实现一致；无夸大 | 文档评审 | `TODO` |

**M6 退出条件：** 关键功能在声明设备范围内通过；性能结论有记录；已知限制写入文档。

---

# M7 — 发布准备（后备任务）

| ID | 任务 | 前置 | 模块 | 交付物 | 验收标准 | 测试方式 | 状态 |
|---|---|---|---|---|---|---|---|
| T7-001 | 图标/品牌/商店素材 | M6；D-018 | `res/`、`docs/store/` | 图标/截图/描述 | 无品牌侵权；素材许可清晰；与实际功能一致 | 法务/设计评审 | `TODO` |
| T7-002 | 隐私/权限/数据安全审查 | M6；D-015/D-017 | `docs/privacy.md` | 隐私说明；权限清单；数据安全表 | 无未声明收集；无网络上传；权限与功能一致 | 清单评审 + 抓包 | `TODO` |
| T7-003 | 许可证/商标/素材审查 | M6；D-018 | `docs/licenses.md` | 依赖许可证；参考图许可；商标检索 | 无未授权 LUT/图片/代码；品牌表达合规 | 法务评审 | `TODO` |
| T7-004 | 发布候选验证与发布说明 | T7-001..003 | 发布包 | 可安装 Release；发布说明；已知问题 | 核心流程稳定；文档与实现一致 | 发布检查表 | `TODO` |

---

# 优先级映射（M0 / M2–M7）

M1 的优先级已写在各任务字段中。为避免表格过宽，其余里程碑的优先级集中记录如下；进入对应里程碑时再细化为 P0/P1/P2。

| 里程碑 | P0（必须按序完成） | P1（可并行/可延后） | P2（后续） |
|---|---|---|---|
| M0 | G-001..G-005 | — | — |
| M2 | T2-001、T2-002、T2-003、T2-004、T2-005 | T2-006、T2-007、T2-008 | — |
| M3 | T3-001、T3-002、T3-003 | T3-004、T3-005、T3-006、T3-007 | — |
| M4 | T4-001、T4-002、T4-003、T4-004 | T4-005 | — |
| M5 | T5-001、T5-002、T5-003、T5-007 | T5-004、T5-005、T5-006 | — |
| M6 | T6-001、T6-002、T6-004 | T6-003、T6-005 | — |
| M7 | T7-002、T7-003、T7-004 | T7-001 | — |

---

# 实验计划（EXP）

> 所有实验初始状态为 `NOT_RUN`。实验不是“可选项”：标为“阻塞”的实验未完成前，对应实现任务不得进入 `VERIFIED`。

| ID | 问题/假设 | 方法 | 指标/通过条件 | 产出 | 影响决策/任务 | 状态 |
|---|---|---|---|---|---|---|
| EXP-01 | CameraX 能否在目标设备上稳定输出 4:3、≥12MP JPEG，并与预览构图一致？ | 在 3 档真机用 `ImageCapture` 拍固定场景；记录支持尺寸、实际输出、预览/成片裁切 | 每台设备有可用 4:3 ≥12MP 输出；输出尺寸=采集尺寸；构图差异在声明范围 | 设备能力表；对照样片；结论 | T2-003/T2-007；D-005 | `NOT_RUN` |
| EXP-02 | 是否存在必须用 Camera2 的缺口？ | 对照需求清单：高分辨率、闪光灯、手动曝光、RAW、焦距；检查 CameraX/Camera2Interop 能力 | 若 CameraX 全覆盖 → 不引入 Camera2；若有缺口，记录最小接入面 | 缺口清单；风险 | D-005；阶段 2 架构 | `NOT_RUN` |
| EXP-03 | Android JPEG 编码器 vs 可控编码器的画质/伪影/性能差异 | 同一输入用 q=88/92/95 与 libjpeg-turbo 自定义量化表编码；盲评+指标+耗时 | 明确哪种满足“可辨识风格”；记录代际损失；有数据支持选择 | 编码器对比报告；样本 | D-010；T3-001 | `NOT_RUN` |
| EXP-04 | sRGB/8-bit 管线是否足够？P3/ICC/线性化在目标设备上的影响 | 色块图 + 真实样片；比较 sRGB vs P3、gamma vs linear 处理 | 颜色误差/视觉差异在可接受范围；明确是否必须 P3 | 色彩报告；决策 | D-011；T3-002 | `NOT_RUN` |
| EXP-05 | 3D LUT、RGB 曲线、混合管线哪种更合适？ | 同一组参考样片，分别拟合曲线/LUT/混合；盲评+ΔE/SSIM+参数可解释性 | 选择在画质/性能/可维护性综合最优；不以单一指标决定 | 对比报告；推荐 | D-012；T3-003/T3-006；T4 | `NOT_RUN` |
| EXP-06 | CPU 是否能在预算内处理 12MP？GPU 是否值得？ | CPU 参考实现 vs GPU（OpenGL ES）实现；测耗时/峰值内存/画质差异 | CPU 达到暂行预算则 MVP 不上 GPU；否则评估 GPU 收益 | 基准报告 | D-009；D-021；T3-001 | `NOT_RUN` |
| EXP-07 | 颗粒/噪声模型是否必要？能否确定性复现？ | 固定种子生成颗粒；对比参考夜景；检查同种子重复输出 | 同种子 bit-exact；视觉上不“统一加彩噪” | 噪声报告；参数 | T3-005；T4-004 | `NOT_RUN` |
| EXP-08 | 参考样片是否可合法使用？能提取哪些稳定特征？ | 收集 Canon A610/Sony W80/Fuji F30 样片；记录来源/许可/EXIF；做特征统计 | 每张图有许可/来源；特征可量化（曲线/色块/噪声） | `research/reference-images.md` | D-018；T4-002..004 | `NOT_RUN` |
| EXP-09 | 下拉手势阈值/速度/冲突 | 原型上测试 5 种阈值/速度；记录误触/成功率/主观评分 | 选定阈值；误触率可接受；有按钮替代 | 手势参数 | T1-007；D-022 | `NOT_RUN` |
| EXP-10 | MediaStore + Room 崩溃一致性 | 在写入各阶段注入崩溃/断电模拟；重启对账 | 不出现“成功但不可读”；不重复照片；pending 可清理 | 状态机/对账方案 | D-008；T5-002/T5-003 | `NOT_RUN` |
| EXP-11 | 滤镜验收用什么指标？ | 盲评 + ΔE2000 + SSIM + 直方图/曲线；测试评审者一致性 | 形成可重复的验收表；不依赖单人主观 | 验收方法 | T4-005 | `NOT_RUN` |
| EXP-12 | 是否需要 WorkManager/后台处理？ | 处理 12MP 时切后台/锁屏/杀进程；观察恢复 | 若前台可完成且恢复可靠 → 不引入 WorkManager；否则评估 | 后台策略 | T5-006；D-008 | `NOT_RUN` |

---

# 任务状态与证据规则（补充）

## 任务完成定义（DoD）

一个任务只有同时满足以下条件才能从 `DONE` 进入 `VERIFIED`：

1. 交付物存在且可定位；
2. 验收标准逐条有结论（通过/失败/阻塞）；
3. 测试命令、构建哈希、设备信息、原始输出已记录；
4. 没有未说明的偏差；
5. 相关文档（`PROJECT_STATE.md`/`TEST_PLAN.md`/`DECISION_LOG.md`）已同步；
6. 代码任务通过 review（至少自查清单），无密钥/隐私数据。

## 禁止事项

- 把 `TODO`/`DONE` 写成“已完成”；
- 用模拟器相机结果证明真机相机兼容性；
- 用文件大小证明分辨率；
- 用单张主观满意的照片证明滤镜通过；
- 在没有参考图许可的情况下把图片放进仓库；
- 在阶段 1 引入相机/网络/付费依赖。

---

# 附：阶段 1 任务清单（速查）

| ID | 任务 | 状态 | 关键产出 |
|---|---|---|---|
| T1-001 | 产品标识与平台基线 | `BLOCKED` | D-003/D-020/D-025 定稿 |
| T1-002 | Git/工程规范 | `VERIFIED` | `.gitignore`/`.editorconfig`/README |
| T1-003 | Gradle/Compose 空工程 | `DONE` | 可构建 APK；安装待设备 |
| T1-004 | 设计令牌/主题 | `TODO` | `ui/theme` + 令牌文档 |
| T1-005 | 页面状态机 | `DONE` | 状态机 + 导航骨架；12 单元测试通过 |
| T1-006 | 首页 | `TODO` | Home 静态 UI |
| T1-007 | 下拉手势 | `TODO` | 手势 + 转场 |
| T1-008 | 拍摄页 | `TODO` | Camera 静态 UI |
| T1-009 | 图库 | `TODO` | Gallery 静态 UI |
| T1-010 | 成片页 | `TODO` | Result 静态 UI |
| T1-011 | 设置页 | `TODO` | Settings 静态 UI |
| T1-012 | 状态矩阵 | `TODO` | Loading/Empty/Error |
| T1-013 | 自适应/无障碍 | `TODO` | 多尺寸 + TalkBack |
| T1-014 | 视觉基线 | `TODO` | 截图 + 评审 |
| T1-015 | 模拟器/设备 | `BLOCKED` | AVD/真机 |
| T1-016 | 性能烟雾 | `TODO` | 启动/切换基线 |
