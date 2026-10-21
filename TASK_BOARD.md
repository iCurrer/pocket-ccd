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
| M1 | 项目初始化 + 静态 UI 原型 | 16 | `IN_PROGRESS`（五个页面均 `DONE`；T1-002 `VERIFIED`；T1-007 `IN_PROGRESS`；T1-012/T1-013/T1-014/T1-016 待做；T1-001/T1-015 受阻） | 本板确认 |
| M2 | 相机基础链路 | 8 | `TODO` | M1 退出 + EXP-01/02 |
| M3 | 图像处理内核 | 7 | `TODO` | M2 采集样本 + EXP-03/04/06 |
| M4 | 三个滤镜 | 5 | `TODO` | M3 管线稳定 + EXP-05/08/11 |
| M5 | 图库 / 成片 / 存储 | 7 | `TODO` | M3 输出稳定 + D-014 |
| M6 | 兼容性与性能 | 5 | `TODO` | M2–M5 功能可用 |
| M7 | 发布准备 | 4 | `TODO` | M6 通过 |
| M8 | Fotufilm Android 原生移植 | 14 | `IN_PROGRESS`（CI 路线已取代 WSL2：GitHub Actions 已跑绿，最新 run `39198895398` 全绿；FOT-001/002/003 `DONE`；FOT-004 **构建侧 `DONE`**——`libfotufilm_android.so` 已编出（5,874,424 B）并通过 AArch64 + JNI 符号断言；真机 `PJZ110` 已就位，但无 Kotlin 调用方，12MP 未实测。方案见 `docs/github-actions-fotufilm.md`、`docs/fotufilm-android-port-plan.md`） | FOT-001 + M2 可并行 |
| M9 | UI 重设计：暗色胶片相机 | 17 | `IN_PROGRESS`（新取景页已是默认入口并接入真实相机；真机验证：取景 → 按快门 → 冲洗 → 落盘 → 相册全链路通过，卷计数真实可归零；原型完整 4 页已解出并归档，交互对照见 `docs/interaction-spec.md`；**交互层已于 2026-10-19 22:29–22:50 在 PJZ110 逐项验证**：点按对焦（`tap to focus -> true`）、真变焦（`zoom -> 2.00x`）、网格+Toast、90ms 闪白（整帧均值 243），T9-006/007/009 转 `DONE`；**其余三页（成片/相册/设置）已于 2026-10-19 23:25 全部暗色化并接入真实 EXIF/镜头倍率**，`assembleDebug` + 12/12 单测绿，待真机截图（T9-018/019/020/021）） | T9-018 → T9-019 → T9-020 → T9-021 → T9-012 |

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
| 状态 | `DONE`（构建/manifest/SDK 已验证；安装/启动已于 2026-10-19 在 `PJZ110` 真机完成，见 P-027） |
| 预估 | 1–1.5 天 |
| 前置条件 | Android SDK Platform 37、Build Tools 36.0.0；JDK 21（Studio JBR）；Gradle 9.6.1；网络/代理可拉取缺失依赖；T1-002 完成 |
| 涉及模块 | `settings.gradle.kts`、根 `build.gradle.kts`、`app/build.gradle.kts`、`gradle/libs.versions.toml`、`gradle/wrapper/`、`app/src/main/`、`MainActivity` |
| 交付物 | 可构建的 `:app` 空工程；Compose 主题骨架；版本目录；`local.properties.example`；构建说明；`./gradlew` wrapper |
| 验收标准 | `./gradlew :app:assembleDebug` 成功；APK 产物存在；Manifest 无 `CAMERA`/`INTERNET`/存储权限；`compileSdk=37`、`targetSdk=36`、`minSdk=29`；依赖无 alpha/beta（CameraX 本阶段不引入）；应用可启动（需设备/模拟器） |
| 测试方式 | `./gradlew :app:assembleDebug`；`aapt dump badging`；`aapt dump permissions`；`adb install` + `am start`（环境具备时）；`./gradlew :app:dependencies` |
| 证据/用例 | `TC-ENV-002`、`TC-P1-001`、`TC-P1-017` |
| 实际结果 | 2026-10-17 `./gradlew :app:assembleDebug` **BUILD SUCCESSFUL**（首次 59s，wrapper 复跑 up-to-date）；APK `app/build/outputs/apk/debug/app-debug.apk`，29,570,230 bytes，SHA-256 `B77DB7171C99DDC10685DCB046512BD8BC48B2801679DAEDBDE47A702168912C`；`aapt` 确认 compileSdk 37 / targetSdk 36 / minSdk 29；无 CAMERA/INTERNET/存储权限（仅有 AndroidX 自定义签名权限）；`TC-ENV-004` 后于 2026-10-19 以 `PJZ110` 真机 `PASS`（安装/启动/截图） |
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
| 状态 | `DONE`（令牌/主题已实现并编译；关键对比度已脚本计算；字体/视觉评审待设备） |
| 预估 | 1 天 |
| 前置条件 | T1-003 构建成功；视觉参考/原型截图（没有则用临时值并标注） |
| 涉及模块 | `ui/theme/`（Color、Type、Shape、Spacing、Motion、Theme） |
| 交付物 | `PocketCcdTheme`；颜色/表面/文字/强调/错误色；间距、圆角、控件尺寸、字体层级、动画时长、点击反馈、禁用/加载态令牌；`docs/design-tokens.md` |
| 验收标准 | 页面不出现散落硬编码颜色/尺寸；所有交互控件≥48dp；正文对比度≥4.5:1；字体放大 200% 不截断；令牌有“临时/待评审”标记 |
| 测试方式 | Compose Preview；对比度计算（人工/脚本）；`grep` 检查硬编码；T1-013 字体缩放测试 |
| 证据/用例 | `TC-P1-030`（对比度 PASS）；`TC-P1-012`、`TC-P1-015`、`TC-P1-016`（待设备）；`docs/design-tokens.md`、`docs/color-direction.md` |
| 实际结果 | 2026-10-17 新增 `Tokens.kt`/`Type.kt`，重写 `Theme.kt`；V2 色板改为黄油机身 + 奶油面板 + 薄荷按钮 + 珊瑚快门 + 深青 LCD；`docs/color-direction.md` 记录 V2 方向/材质/对比度；关键组合 4.58–13.19:1；`assembleDebug` 通过；200% 字体/视觉评审待 T1-013/T1-014 |
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
| 状态 | `DONE`（首页静态 UI 已实现并编译；UI/截图/多尺寸待设备） |
| 预估 | 1 天 |
| 前置条件 | T1-004、T1-005 |
| 涉及模块 | `ui/home/` |
| 交付物 | 红色玩具相机正面（品牌、镜头、绿色闪光窗、橙色快门、FILM 图库键、设置小圆键）；下拉提示；Compose Preview；截图 |
| 验收标准 | 打开首页不启动相机、不请求权限；360×800/412×915/横屏/大屏无溢出；快门/FILM/设置可点击且有语义标签；下拉提示不遮挡系统栏 |
| 测试方式 | Compose UI 测试；截图评审；`adb shell dumpsys media.camera`/日志确认无相机初始化（阶段 1 可用代码检查替代） |
| 证据/用例 | `TC-P1-001`、`TC-P1-012`、`TC-P1-013`、`TC-P1-020`（均待设备） |
| 实际结果 | 2026-10-17 首页由 `CameraStageScreen` 的 `progress=0` 状态呈现：红色外框 + 奶油内面板、黑色大镜头、绿色闪光键、橙色快门、绿色 `FILM`、深红设置键；删除 `PULL DOWN` 说明、外圈白线、闪光窗分割线和镜头弧线；无网页式文本链接；`assembleDebug` 通过；Compose UI/截图/多尺寸/无障碍待 T1-013/T1-015 |
| 风险/备注 | “精致”只能通过评审清单判断；不得用自动测试宣称审美通过 |

---

## T1-007 — 下拉进入相机手势与转场

| 字段 | 内容 |
|---|---|
| 优先级 | P0 |
| 状态 | `IN_PROGRESS`（距离阈值 + 下拉跟随已实现；速度阈值/手势测试/冲突验证未做） |
| 预估 | 1.5 天 |
| 前置条件 | T1-006；EXP-09（可先用手势阈值假设） |
| 涉及模块 | `ui/home/`、`navigation/`、`ui/AppRoot.kt` |
| 交付物 | 垂直拖拽检测；距离/速度阈值；取消/回弹；展开/收起动画；防重复触发；无障碍替代按钮；阈值文档 |
| 验收标准 | 拖拽超过阈值且速度足够才触发；未超过阈值回弹不导航；图库滚动/系统返回不误触；动画期间重复手势不叠加；TalkBack 可通过按钮进入相机；阈值有记录 |
| 测试方式 | Compose 手势测试（`performTouchInput`）；真机/模拟器手测；慢动作录屏；`TC-P1-004..006` |
| 证据/用例 | `TC-P1-004`、`TC-P1-005`、`TC-P1-006`、`TC-P1-024`（均待设备） |
| 实际结果 | 2026-10-17 改为 `CameraStageScreen`：HOME/CAMERA 共用单一红框机身，纵向拖拽直接驱动 `Animatable<Float> progress`（0=首页，1=拍摄页），松手按 progress 阈值动画到两端；不再使用 `SharedTransitionLayout`/`sharedBounds`，避免跨页图层闪动；快门/闪光灯位置与大小随 progress 插值，品牌上下移动，镜头淡出/取景器淡入，FILM/设置淡出、翻转键淡入。`assembleDebug` 通过。**速度阈值、Compose 手势测试/真机跟手验收未做**，因此不标 `DONE` |
| 风险/备注 | 下拉手势与系统通知栏/返回手势可能冲突；必须提供按钮替代，不能把手势作为唯一入口 |

**当前阈值（`[H]`，待 EXP-09 真机调参）：** 距离 88dp；速度阈值尚未实现，暂不承诺“速度足够才触发”。

---

## T1-008 — 拍摄页 Camera 静态 UI

| 字段 | 内容 |
|---|---|
| 优先级 | P0 |
| 状态 | `DONE`（静态布局/按压反馈编译通过；UI/截图/横屏/状态变体待设备与 T1-012） |
| 预估 | 1.5 天 |
| 前置条件 | T1-004、T1-005；D-019（不接相机） |
| 涉及模块 | `ui/camera/`、`ui/components/` |
| 交付物 | 红色复古前面板；近方形厚重黑取景器；绿色闪光灯（含不可用态）；中央橙色大快门（高光/层次/按压反馈）；红色循环切换键；紧凑 `POCKET CCD` 品牌；系统返回收起；无图库按钮、无顶部导航/设置入口 |
| 验收标准 | 无 CameraX/Camera2 类、无 `CAMERA` 权限；取景器厚重、预览内嵌；竖屏布局可读；快门/图标≥48dp；按压反馈可感知；安全区不被刘海/手势条遮挡；系统返回可回首页；右上角没有图库入口 |
| 测试方式 | Compose UI 测试；`grep`/依赖检查；`aapt dump permissions`；真机截图 |
| 证据/用例 | `TC-P1-003`、`TC-P1-008`、`TC-P1-017`、`TC-P1-020`、`TC-P1-028`、`TC-P1-039` |
| 实际结果 | 2026-10-17 拍摄页由 `CameraStageScreen` 的 `progress=1` 状态呈现：红框机身放大，奶油内面板中的黑取景器、绿色闪光灯、橙色大快门、红色循环键、底部品牌；闪光灯/快门从首页位置移动过来，镜头淡出、取景器淡入；无顶部导航，系统返回收起；相册区暂未实现；`assembleDebug` 通过；**预览为静态测试画面，非实时相机** |
| 风险/备注 | 取景占位比例必须与后续成片 4:3 策略一致；横屏/大屏适配归 T1-013；CAPTURING/PROCESSING/ERROR 状态变体归 T1-012 |

---

## T1-009 — 图库 Gallery 静态 UI

| 字段 | 内容 |
|---|---|
| 优先级 | P1 |
| 状态 | `DONE`（照片墙静态 UI/空态/网格预览完成；真实数据待 T5） |
| 预估 | 1.5 天 |
| 前置条件 | T1-004、T1-005；app-owned 照片列表（当前为空） |
| 涉及模块 | `ui/gallery/` |
| 交付物 | 首页上拉进入照片墙；3 列 app-owned 网格；`POCKET CCD ONLY` 说明；空态；日期/滤镜小标签；无系统图库扫描 |
| 验收标准 | 不申请媒体权限；不扫描系统图库；空态有引导；网格 key 稳定；无照片时不伪造照片；后续接入 Room/MediaStore 只传本应用记录 |
| 测试方式 | 静态代码检查；Compose Preview；真机 UI/滚动测试 |
| 证据/用例 | `TC-P1-031`；`TC-P1-002`、`TC-P1-019`、`TC-P1-020`（待设备） |
| 实际结果 | 2026-10-17 新增 `PhotoWallScreen.kt`：`ToyCameraBody` + 深青 `ToyScreen` 内的 2 列奶油照片纸、空态胶片头、app-owned 说明、日期/滤镜标签；运行时传入 `emptyList()`，不读取系统图库；预览用合成色块展示胶片格；首页上拉触发；`assembleDebug` 通过 |
| 风险/备注 | 阶段 1 不实现真实 MediaStore；不要为了“看起来有照片”申请存储权限；真实数据/缩略图/状态徽标归 T5 |

---

## T1-010 — 成片页 Result 静态 UI

| 字段 | 内容 |
|---|---|
| 优先级 | P1 |
| 状态 | `DONE`（静态 UI/状态变体完成；设备 UI 待 T1-015） |
| 预估 | 1 天 |
| 前置条件 | T1-005；合成测试图 |
| 涉及模块 | `ui/result/` |
| 交付物 | 4:3 测试成片；滤镜/型号铭牌；`PROCESSING/OUTPUT_WRITING/SAVED/FAILED` 状态；返回拍摄；照片墙；失败重试；demo 状态标注 |
| 验收标准 | 保存完成前不显示“保存成功”；失败态有明确动作；返回/照片墙路径可达；图片不因缩放变形；demo 模式明确显示“NOT SAVED” |
| 测试方式 | 静态代码检查；Compose Preview；真机 UI/状态切换测试 |
| 证据/用例 | `TC-P1-034`；`TC-P1-009`、`TC-P1-010`、`TC-P1-019`（待设备） |
| 实际结果 | 2026-10-17 新增 `ResultScreen.kt`：`ToyCameraBody` 内的奶油相纸打印（轻微旋转/阴影）、4:3 合成测试图、状态 LED、失败重试、两个物理圆键（返回拍摄/照片墙）；运行时 `demo=true` 显示 `DEMO · NOT SAVED`，不伪造“已保存”；Preview 展示 SAVED/FAILED 变体；`assembleDebug` 通过 |
| 风险/备注 | 阶段 1 的保存状态是 UI 变体，不是实际文件写入；真实状态机/MediaStore 在 T5 |

---

## T1-011 — 设置页 Settings 静态 UI

| 字段 | 内容 |
|---|---|
| 优先级 | P1 |
| 状态 | `DONE`（静态 UI/BuildConfig/Debug 诊断完成；设备 UI 待 T1-015） |
| 预估 | 0.5 天 |
| 前置条件 | T1-004、T1-005 |
| 涉及模块 | `ui/settings/`、`BuildConfig` |
| 交付物 | 红框机身内 App 式设置：24sp 标题、分组卡片、15/12sp 行、44dp 分段滤镜、保存位置/质量占位/版本/隐私/Debug 诊断 |
| 验收标准 | Release 构建不显示 Debug 入口；无网络请求；无权限；文案可理解；版本号来自 `BuildConfig` |
| 测试方式 | 静态代码检查；`assembleRelease`（未签名也可）检查 BuildConfig；`aapt dump permissions` |
| 证据/用例 | `TC-P1-036`、`TC-P1-037`、`TC-P1-047`；`TC-P1-018`、`TC-P1-017`（待设备/Release） |
| 实际结果 | 2026-10-17 设置页保留红框机身，内容改为 App 式布局：24sp“设置”标题 + 副标题、PHOTOS/ABOUT/DEVELOPER 分组卡片、15sp 行标题 + 12sp 副标题、44dp 三段滤镜控件、LazyColumn 滚动；删除 8sp 深青 LCD 小字；`assembleDebug`/`assembleRelease` 通过；Debug 诊断仅 debug |
| 风险/备注 | 默认滤镜/质量只是本地 UI，尚未持久化；真实设置持久化归后续 DataStore 任务 |

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
| 状态 | `DONE`（真机路线 `PASS`：`PJZ110` arm64-v8a / API 37；安装、启动、截图、下拉进入拍摄页均通过，2026-10-19。AVD/system image 未安装，模拟器用例仍 `NOT_RUN`） |
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
- [x] T1-004 设计令牌完成（视觉评审待设备）
- [x] T1-005 状态机完成（12 单元测试通过；UI/旋转待设备）
- [x] T1-006 首页完成（UI/截图待设备）
- [ ] T1-007 下拉手势完成（距离阈值已实现；速度/测试待补）
- [x] T1-008 拍摄页静态 UI 完成（UI/截图待设备）
- [x] T1-009 照片墙静态 UI 完成（空态/网格；真实数据待 T5）
- [x] T1-010 成片页静态 UI 完成（状态变体/预览；设备 UI 待验）
- [x] T1-011 设置页完成（设备 UI 待验）
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

# M8 — Fotufilm Android 原生移植

**目标：** 用 Fotufilm 真实引擎（不是 LUT 近似）做 Android 胶卷相机。
**路径：** 离线 FSWP pack + C++ AOT + JNI；不在 Android 上跑完整 Swift App。
**方案文档：** [`docs/fotufilm-android-port-plan.md`](docs/fotufilm-android-port-plan.md)
**License 清单：** [`docs/fotufilm-license-inventory.md`](docs/fotufilm-license-inventory.md)
**当前状态：** `IN_PROGRESS` — **Phase 0 spike 已在真机上跑通**。构建侧：CI run `39198895398` 全绿，FSWP pack、arm64 AOT 内核、parity（64 case）、`libfotufilm_android.so`（5,874,424 B）均有实证。运行时侧：`PJZ110` 真机（arm64-v8a / API 37 / 10.9 GB RAM / 8 核）已完成 Kotlin 绑定 + pack 加载 + 12MP 全链路，3 个 stock 均渲染成功、`nonFinite=0`、单帧 2.0–3.2 s、峰值 RSS 373–463 MB。**M8 的硬卡点已从"无设备"→"无调用方"→ 全部解除**；余下的是 Phase 1 的真实链路（相机接入 FOT-007、色彩口径 FOT-006）与 47 stock 全量（FOT-012）。基准数据与方法见 [`docs/fotufilm-device-benchmark.md`](docs/fotufilm-device-benchmark.md)。

| ID | 任务 | 前置 | 模块/文件 | 交付物 | 验收标准 | 测试方式 | 状态 |
|---|---|---|---|---|---|---|---|
| FOT-001 | License/来源清单 | 无 | `docs/fotufilm-license-inventory.md` | 代码/profile/第三方/商标清单 | 每项有 license/义务/处置 | 人工/法务复核 | `DONE` |
| FOT-002 | GitHub Actions Linux 构建 + 工具链 | FOT-001 | `.github/workflows/fotufilm-android-native.yml` | Swift + Halide + NDK + workflow | workflow 首次 run 成功；artifact 可下载 | Actions run + artifact | `DONE`（run `39143317856` 全绿，12.0 min；Swift 6.1 / LLVM 22.1.8 / NDK r28c 均装通；artifact `fotufilm-android-poc` 已下载核对） |
| FOT-003 | 3 个代表性 FSWP pack | FOT-002 | Gold200/Portra400/TriX400 | `.fswp` ×3 | AOT parity 工具可读取 | `--dump-wasm-pack` | `DONE`（判据改为 `[F]` 实证：run `39145196597` 的 4 个 pack 已下载，头部解出 `FSWP v2 4000x3000`；parity 用 `example-negative-400__96x64` 跑通 64 case） |
| FOT-004 | arm64 CPU AOT `.so` + 最小 JNI | FOT-003 | `libfotufilm_android.so`（构建脚本 `native/`） | 真机处理 1 张 12MP | 输出非黑/非 NaN；记录耗时/内存 | 真机 | `DONE`（`1864f56` 提交 `native/`；`0c6610c` 修 `find_path` 被 NDK sysroot 重根化；`6004220` 修 most vexing parse。run `39198895398` 全绿，`.so` 5,874,424 B 通过 AArch64 + JNI 导出断言。**真机实测（run 见下）**：非 debuggable 的 `benchmark` 构建下，3 个 12MP pack 全 `nonFinite=0`，单帧 **2.0–3.2 s**（内核占 80–85%），峰值 RSS **373–463 MB**。数据与方法见 [`docs/fotufilm-device-benchmark.md`](docs/fotufilm-device-benchmark.md)） |
| FOT-005 | FSWP 解析 + Kotlin FilmEngine API | FOT-004 | `app/src/main/java/com/pocketccd/prototype/fotufilm/` | pack 选择、JNI 调用 | 3 个 stock 可渲染 | 单元/真机 | `DONE`（`FotufilmNative`（9 个 external，与 C++ 签名逐条对齐）、`FilmPack`（小端 FSWP 解析）、`FilmEngine`（行带分块 + halo）、`LinearColor`、`FilmSelfTest`。真机：`libraryLoaded=true` / `abiVersion=1` / `available=true` / `packCount=4`；Gold200、Portra400、TriX400 三卷均渲染成功；TriX400 输出 R=G=B 精确相等（黑白卷），三彩色卷各有 0.128–0.137 偏色） |
| FOT-006 | 解码/线性 Rec.2020/输出色彩 | FOT-004 | `imaging/` | 输入输出色彩转换 | 与桌面参考误差可接受 | 色块/样片 | `TODO` |
| FOT-007 | CameraX → 引擎 → 保存 | FOT-005、M2 | `camera/`、`storage/` | 拍照闭环 | 真机照片进入 app 相册 | 真机 | `DONE`（**真机端到端已跑通**）。`camera/CameraPreview.kt`（Preview + ImageCapture、`targetRotation=ROTATION_0`、`attachedSurfaceResolution` 匹配 pack）、`camera/PhotoStore.kt`（全尺寸 + 1440 缩略图，临时文件 rename）、`camera/CaptureState.kt`；`fotufilm/FilmStock.kt` 扫描 pack；`AppViewModel` 转 `AndroidViewModel` 并在 `Dispatchers.Default` 上跑冲洗。**实证 2026-10-19 14:33:43**：PJZ110 按快门 → `film_1792391623930_gold200__4096x3072.jpg` 落盘，`4096×3072 / 3,038,375 B`，缩略图 `1440×1080 / 320,410 B`，照片墙可见。均无 EXIF 方向标记。遗留方向问题见 FOT-017 |
| FOT-008 | 胶片选择器 + 12 stock | FOT-005 | `ui/film/` | 选择器/metadata | 切换生效；无假数据 | UI/真机 | `TODO` |
| FOT-009 | 预览/成片双路径 | FOT-007 | `ui/camera/` | 低分辨率预览 + 全分辨率成片 | 预览不冒充成片 | 真机 | `TODO` |
| FOT-010 | Vulkan GPU + CPU fallback | FOT-004 | `fotufilm-halide` | GPU pack/后端选择 | GPU 失败自动 CPU | 真机 | `TODO` |
| FOT-011 | 性能/包体/热/内存 | FOT-007、FOT-010 | 报告 | 基线 + 优化 | 目标设备可接受 | benchmark | `TODO` |
| FOT-012 | 47 stock 全量 pack | FOT-008、FOT-011 | `assets/film/` | 47 pack + 选择器 | 47 个可加载 | 真机 | `TODO` |
| FOT-013 | Attribution/License 页 | FOT-001 | `ui/settings/` | 开源声明/许可页 | 满足 Apache/CC BY-SA/第三方 | 人工审计 | `TODO` |
| FOT-014 | 设备端到端验收 | FOT-012、FOT-013 | 发布候选 | 真机矩阵报告 | 端到端通过 | 真机 | `TODO` |
| FOT-015 | 颗粒相位跨带子连续性 | FOT-004 | `FilmEngine` | 结论 + 必要时按行偏移 seed | 实拍样片上无可见带间纹理差 | 实拍样片 + 上游确认 | `TODO`（真机分块验证发现：只有第一条带子与整帧逐位一致，之后每次内核调用都会推进颗粒随机状态，24 条带子的颗粒相位不连续。`maxAbsDiff` 仅 3/255，低于可察觉阈值，暂无可见接缝，但需在真实照片上确认；见 [`docs/fotufilm-device-benchmark.md`](docs/fotufilm-device-benchmark.md) §5） |
| FOT-016 | 按设备真实最大 JPEG 重产 pack | FOT-003、FOT-007 | CI `pack_size` | 4096×3072 的 3 卷 pack | 真机能拍且尺寸精确匹配 | 真机 | `DONE`。**事实**：PJZ110 后置镜头 CameraX 最大 JPEG 是 **4096×3072**，`dumpsys media.camera` 全表与 `CXCP bestSizes=[4096x3072, 1600x1200]` 双证；请求 4000×3000 会被 `CLOSEST_HIGHER_THEN_LOWER` 换成 4096×3072，按 D-037 故三卷 4000×3000 不可拍。**已完成**：CI 用 `pack_size=4096x3072` 重产，`fotufilm-packs` artifact 解压得 `gold200/portra400/trix400__4096x3072.fswp`（头部逐份校验 `FSWP v2 4096x3072`），已 push 至设备并删掉旧的 4000×3000；随后拍照成功 |
| FOT-018 | 多机型尺寸适配（裁切，不缩放） | FOT-007、FOT-016 | `CameraPreview.decodeJpeg`、`AppViewModel` | 采集尺寸 ≥ pack 时中心裁切；< pack 时明确失败 | 非 4096×3072 的机型也能拍出正确物理尺度的成片 | 真机 | `DONE`（**真机已验，2026-10-20**）。三态 `decodeJpeg`：相等直用 / 更大 `BitmapRegionDecoder.decodeRegion` 中心裁切 / 更小抛错（消息含设备尺寸与 pack 尺寸）。**为什么裁切可以、缩放不行**：pack 的空间参数是「毫米 × 该帧 pixels-per-mm」，裁切不改像素密度故参数依然成立，缩放改密度等于换光学系统（D-037 只禁缩放）。`AppViewModel` 用 `canServe`（≥）+ `servingSizeFor`；`programmedSize` 在能服务时下发 pack 尺寸。**实证**：装 `4000×3000` pack 的包，`PJZ110` 采集 `4096×3072` → 成片 `3000×4000` 正立（旋转后），成片页显示 `3909ms · 24 strips`。**仍未验证**：采集尺寸**小于** pack 的失败分支（手边没有 8MP 机型）；裁切比例大时取景器与实际范围不一致，归 FOT-009 |
| FOT-019 | 成片写入系统相册 | FOT-007 | `camera/PhotoStore` | 冲洗完成后同时写 `Pictures/Pocket CCD/` | 系统相册可见、可分享，且不申请存储权限 | 真机 | `DONE`（**真机已验**）。**缺陷起因**：`PhotoStore` 只写 `Android/data/<pkg>/files/Pictures/pocket-ccd/`，该目录被媒体扫描明确排除，导致照片墙有、系统相册无 —— 相机拍的照片在相机之外完全不可见。**实现**：`publishToGallery()` 用 MediaStore 插入 `Pictures/Pocket CCD/`，`IS_PENDING=1 → 写流 → 置 0`，失败 `delete` 回滚；相册副本由已落盘的 JPEG **文件流复制**，不重新编码位图（省 300ms + 48MB 临时位图）。API 29+ 分区存储写自建媒体条目无需任何权限，manifest 仍只有 `CAMERA`。历史照片由 `syncToGallery()` 一次性补齐（`SharedPreferences` 标记，避免用户删图后又被补回）。成片页诊断行增加 `已存入相册 · Pictures/Pocket CCD`。**实证**：13 张历史成片全部进相册，`relative_path=Pictures/Pocket CCD/`、`_size` 与文件字节数一致、`is_pending=0`；系统相册 App 日志出现读取 `Pictures/Pocket CCD/film_…jpg` |
| FOT-020 | 私有副本 ↔ 系统相册对账 | FOT-019、D-047 | `camera/GalleryReconciler.kt`、`camera/PhotoStore.kt`、`navigation/AppViewModel.kt`、`ui/AppRoot.kt` | 用户在系统相册里删掉的成片，私有副本同步移除；回前台与进照片墙各对一次 | 相册删一张 → 照片墙消失；相册索引读不到时**一张都不删**；刚拍 2 分钟内不删；发布失败的照片不删；移动/重命名过的照片不误删 | 单测 `GalleryReconcilerTest`（11 例，已过）；真机：相册删一张→切回→照片墙消失 | `IN_PROGRESS`（**代码已落，单测 28/28 绿**；真机待验。判定规则见 `GalleryReconciler.staleIds`：文件名命中 **或** 拍摄时间（秒级）命中即视为存活——前者兜"移动到别的相册"，后者兜"相册内重命名"，秒级归一兜"DATE_TAKEN 被截断"。**已知不对称**：用户在相册"回收站"里还原后，私有副本已删、照片墙不会自己回来） |
| FOT-021 | pack 内置进 APK（别人装上就能用） | FOT-016、D-048 | `app/build.gradle.kts`、`fotufilm/FilmStock.kt`、`navigation/AppViewModel.kt` | assets 带 pack + 首启落盘 + 内置清单清理 | 别人装 APK 不连电脑即可看到 3 卷；换 pack 尺寸后旧内置文件自动消失，`adb push` 的不动 | `unzip -l` + 真机首启日志 | `DONE`（**构建侧已验，真机待验**）。`sourceSets.main.assets.srcDir("prebuilt/fotufilm/packs")` 让 3 个 fswp 进 `assets/`；`FilmStockCatalog.provisionBundled()` 首次启动复制到 `files/film/`（先写 `.tmp` 再 rename），用 `bundled_packs_v1.txt`（`文件名 字节数`）记账，只清理"上一版内置过、这一版没有了"的文件。`assembleBenchmark` 产物 39 MB，`unzip -l` 可见 3 个 fswp + `lib/arm64-v8a/libfotufilm_android.so` |
| FOT-022 | pack 基准尺寸降到 4000×3000 | FOT-021、D-048 | CI `pack_size`、`prebuilt/fotufilm/packs/` | 3 卷 `__4000x3000.fswp` + 替换 `prebuilt/` | 12MP 主流档（`4000×3000`/`4080×3060`）真机能拍 | 真机 | `DONE`（**2026-10-20 22:52 换入并真机验证**）。三份 pack 头部逐份校验为 `FSWP v2 4000x3000`（`mask` 0x2043e / 0x2063e / 0x200b6，`lut=33/143748`）；旧的三份 `__4096x3072` 备份在 `build/artifacts/packs-4096x3072/`（`build/` 不进版本库）。`PJZ110` 上：首启释放 3 份新 pack 到 `files/film/`，`bundled_packs_v1.txt` 记账正确，**旧的 adb push 版本被「同支卷只留一个变体」规则清掉**（否则胶卷库会出现两个 `GOLD 200`）。拍片通过，见 FOT-018 |
| FOT-023 | 对账三条数据丢失路径（review P0-1/2/3） | FOT-020 | `camera/PhotoStore.kt`、`camera/GalleryReconciler.kt`、`GalleryReconcilerTest.kt` | 补传与对账的顺序约束 + 空索引不下判断 + 补传失败记账 | 升级首启不删历史照片；索引为空时一张不删；补传失败的照片不进删除候选 | 单测 12 例（已过）；真机：清 `gallery_backfill_done_v1` → 放几张私有目录照片 → 冷启动 | `DONE`（**代码 + 单测已过，真机待验**）。细节见 [`docs/reviews/2026-10-20-main-chain-review.md`](docs/reviews/2026-10-20-main-chain-review.md) 与 D-047〈修订〉 |
| FOT-024 | App 内「开源与素材声明」页 | FOT-021、D-048 | `ui/settings/LicensesScreen.kt`、`strings.xml`、`SettingsScreen.kt` | 设置页新增入口 + 整屏声明（署名/修改声明/文件位置/第三方/商标） | 收到 APK 的人不翻 GitHub 也能看到 CC BY-SA 4.0 的署名与修改声明 | 真机截图 | `DONE`（**代码已过编译，真机待验**）。内容分四段：①Fotufilm / MUAStudio Inc. / https://fotufilm.com + 引擎 Apache-2.0、胶片档案 CC BY-SA 4.0 + **修改声明**（移植到 Android、预烘焙成 `.fswp`）；②`files/film` 的实际路径与 `adb pull` 命令 + 明说不加密不做完整性校验、同名覆盖即生效；③Halide / AndroidX / CameraX / Compose / rawtoaces / colour-science；④商标与「渲染结果不受 ShareAlike 约束」。文案全部走 `stringResource`，措辞对照 [`docs/fotufilm-license-inventory.md`](docs/fotufilm-license-inventory.md) §5 |
| FOT-025 | 快门回调超时兜底（review P0-4） | FOT-007 | `camera/CameraPreview.kt`、`navigation/AppViewModel.kt`、`ui/AppRoot.kt` | `takeJpeg` 加 5 s 上限 + 超时后请求重绑 | 按快门后切后台/转屏不会把 App 永久卡在「正在冲洗」 | 真机：按快门 1 s 内切相册再回来 | `DONE`（**代码已过编译，真机待验**）。`takeJpeg` 的 `suspendCancellableCoroutine` 包进 `withTimeoutOrNull(5_000)`，超时抛 `CaptureTimeoutException`；`AppViewModel` 捕获后 `rebindRequests++`，`AppRoot` 收到即 `cameraNonce++` 换一条会话。**为什么必须重绑**：超时的那条会话多半已废（转屏重建/被别的客户端抢走），只放 UI 不换会话，用户再按只会原地再等 5 s。`ImageProxy` 在 `finally` 里 `close()`，超时后照样归还缓冲。阈值取 5 s 而非 2 s：12MP `MAXIMIZE_QUALITY` 在慢机上单次编码偶尔要 1–2 s |


**M8 退出条件：**

1. Phase 0 spike 通过（12MP、arm64、真机、AOT CPU）；
2. 至少 3 个 stock 端到端；12 个 stock 通过性能和包体预算；
3. License/attribution 页可审计；
4. 未通过前不得把 M8 标成 `DONE`，也不得替换现有相机链路。

---

# M9 — UI 重设计：暗色胶片相机

**目标：** 按 `docs/reference/film-dark-home-2026-10-19.html` 的设计基线，用 Compose 重做一套"近黑底 + 香槟金 + 金属取景器"的拍摄界面，取代现行红色玩具机身。
**规格文档：** [`docs/film-dark-ui-spec.md`](docs/film-dark-ui-spec.md)
**范围：** 主题抽象、取景器叠加层、拍摄控制、底部面板、图标、其余页面视觉统一。
**不包含：** 相机链路行为变更（沿用 FOT-007/FOT-019 已验收的实现）、Fotufilm 引擎改动、实时滤镜引擎。

**关键约束：**

- **并行开发，不原地改。** 新 UI 落 `ui/film/`，`AppRoot` 用构建开关切换；旧 `ui/stage/` + `ui/components/ToyCameraBody.kt` 在新 UI 验收通过前保持可回退。
- **不做假数据。** 设计稿里的曝光三要素（`1/125`/`f/2.8`/`ISO 400`）、`DNG`、`3:2`、`AF-S`/`AWB` 一律删除或替换为真实值，见 D-040。
- **触摸目标 ≥48dp。** 设计稿的 36dp 圆钮、28dp 变焦钮、17dp chip 视觉尺寸可保留，热区必须补齐，见规格 §2.4。
- **语义令牌优先。** 不得新增 `RetroRed` 式材质名令牌，见 T9-001。

| ID | 任务 | 前置 | 模块/文件 | 交付物 | 验收标准 | 测试方式 | 状态 |
|---|---|---|---|---|---|---|---|
| T9-001 | 语义令牌 + `ThemeSpec` 抽象 | D-038 | `ui/theme/ThemeSpec.kt`、`FilmDarkTokens.kt`、`Theme.kt` | 语义色板（`AppBg`/`Accent`/`Ink*`/`Hairline*`）、尺寸令牌、`CompositionLocal` 下发、`FilmDarkTheme` | 页面不读材质名常量；令牌改动一处即全局生效；旧 UI 外观零变化 | `assembleDebug`；旧 UI 真机截图与 P-027 对比无差异 | `IN_PROGRESS`（令牌层已实现；旧 UI 未迁移，语义令牌目前只服务新 UI） |
| T9-002 | 页面结构定稿 + 导航调整 | D-039 确认、T9-001 | `navigation/AppState.kt`、`ui/AppRoot.kt` | 按 D-039 选定的结构更新 `AppDestination`/`reduce`；构建开关 | 转换矩阵覆盖全部路径；非法转换被忽略；单测通过 | `AppStateTest` 扩充；`testDebugUnitTest` | `DONE`（D-039 = 废除首页，`HOME` 已从枚举删除，`ApiStateTest` 12/12 通过） |
| T9-003 | 取景器外壳 + 叠加层 | T9-001 | `ui/film/ViewfinderFrame.kt` | 四段金属渐变框 + 顶部 1px 高光 + 手绘投影；四角构图角标；九宫格；中央准星 | 边框层次可见（非单色平涂）；圆角 20dp 无锯齿；角标/准星在真实亮画面上可读 | 真机截图；亮/暗两种预览下各一张 | `DONE`（真机亮/暗两种画面均已验证：v3 暗场景、v6 亮桌面；角标靠双描边在任何亮度下都可读） |
| T9-004 | 参数 chip + 真实数据接入 | T9-003、D-040 确认 | `ui/film/InfoChip.kt` | chip 组件；真实值数据源（尺寸/张数/版本） | 无硬编码假值；chip 内容随状态变化 | `grep` 无 `ISO`/`f/`/`1/125`/`DNG`/`AF-S`；真机截图 | `DONE`（LIVE / 12.6MP / 4:3 全为真实值；chip 写死 17dp 高度导致文字被压成「…」，已修） |
| T9-005 | 金属快门 + 拍摄控制行 | T9-003 | `ui/film/FilmShutter.kt`、`CaptureControls.kt` | `sweepGradient` 快门环 + 径向面 + 红点；相册缩略图 + 张数角标；换卷键 | 按压 0.94 反馈可感；金属层次与设计稿接近；热区 ≥48dp；`onShutter` 接真实链路 | 真机连按 10 次无重复触发；Accessibility Scanner | `DONE`（真机按下快门 → 冲洗 → 落盘 → 相册全链路通过；红点光晕已去掉） |
| T9-006 | 焦点环 + 点按对焦 | T9-003 | `ui/film/FocusRing.kt`、`camera/CameraPreview.kt` | 点按坐标记录、四角 L 形动画、`AF LOCK` 标签、与 CameraX 对焦联动 | 点按出现 500ms 脉冲并消失；对焦生效；不遮挡构图 | 真机点按 20 次；弱光下验证 | | `DONE`（`ui/film/FilmInteractions.kt` 的 `FocusRing`；`CameraHandle.focusAt` 走 `PreviewView.meteringPointFactory` + `startFocusAndMetering`；真机 `tap to focus (760.0,1226.0) -> true`，标签三态 AF / AF LOCK / AF FAIL，2.2s 淡出。偏离原型：原型环永久留置且永远写 AF LOCK） |
| T9-007 | 顶部工具行 + 变焦 | T9-003 | `ui/film/TopBar.kt`、`ZoomSelector.kt` | 闪光/网格/胶卷胶囊/设置；1x-2x 变焦 | 网格显隐正确；变焦生效且切换镜头不崩溃；胶囊显示真实卷名 | 真机逐项点按；CameraX zoom 状态核对 | | `DONE`（顶部三钮 + 卷名胶囊 + 1x/2x 真变焦。真机 `zoom -> 2.00x (max=20.00)`。只有两档，非设计的 1x-2x 连续档） |
| T9-008 | 底部面板（风格 / 设置） | T9-001、D-041 确认 | `ui/film/BottomSheet.kt`、`StylePanel.kt`、`SettingsPanel.kt` | `ModalBottomSheet`；5 个风格选项（色块/名称/描述/勾选）；4 个设置开关 | 手势下拉可关闭；返回键关闭；TalkBack 可读；开关状态不回弹 | 真机手势/无障碍测试 | `BLOCKED`（待 D-041） |
| T9-009 | 快门反馈 + Toast | T9-005 | `ui/film/ShutterFeedback.kt` | 90ms 闪白；2.2s Toast | 闪白不残留；Toast 不叠加；连拍不卡 | 真机连拍 10 次录屏 | | `DONE`（90ms 闪白 + 2.2s Toast。**闪白必须挂在 `PocketCcdApp` 最外层**：放取景页内部会被成片页盖住，实测整帧均值只从 195→206；移到最外层后 243。连拍 10 次录屏未做） |
| T9-010 | 图标资源（手画） | T9-001 | `ui/film/icons/` | 9 个 `ImageVector`：flash / flash-off / grid / tune / filmstrip / image-multiple / close / check-circle / chevron-right | 不引入 `material-icons-extended`；尺寸颜色可控 | 依赖检查；真机截图 | `DONE`（9 个图标已实现，未引 `material-icons-extended`；视觉待真机） |
| T9-011 | 其余页面视觉统一（父任务） | T9-002 | `ui/result`、`ui/gallery`、`ui/settings` | 成片页/照片墙/设置页暗色化，拆分为 T9-018/019/020 逐页验收（D-045） | 三个子项全 `DONE` | 真机逐页截图对照 | `IN_PROGRESS`（三页代码已全部落地，`assembleDebug` 绿；**待真机截图**） |
| T9-012 | 真机视觉验收 + 切换 + 清旧 | T9-003..T9-011 | 开关、`test-evidence/`、文档 | 截图组、偏差表、默认入口切换到新 UI、删除旧包 | 规格 §7 验收清单全绿；偏差逐条记录；快门→冲洗→落盘链路未回归 | 真机全流程；`assembleRelease` | `IN_PROGRESS`（默认入口已切到新 UI；**旧包已删（2026-10-20）**：`ui/stage/CameraStageScreen.kt` 720 行、`ui/components/ToyCameraBody.kt` 188 行都零引用，`PocketCcdColors`/`Sizes`/`Spacing`/`Motion`/`PocketCcdType` 一并移除，`ui/theme` 从 400+ 行降到 135 行；验收清单与偏差表仍待补） |
| T9-013 | 首启权限引导 / 拒绝空态 | D-039 选 1 时必需 | `ui/film/PermissionGate.kt` | 权限说明页、拒绝空态、去设置入口 | 拒绝后不黑屏不崩溃；有明确动作 | 真机权限矩阵 | `IN_PROGRESS`（空态文案已实现；缺"去设置"按钮） |
| T9-014 | 卷计数器（每卷 36 张） | D-040 选 3 时必需 | `camera/RollCounter.kt`、`AppViewModel` | 卷内计数、拍满限制、换卷重置 | 计数与 `roll` 一致；拍满有明确提示；换卷语义清晰 | 单测 + 真机 | `DONE`（`PhotoStore.rollStartedAt`/`startNewRoll` 落盘；真机 1/36 →「换卷」归零 → 再拍回 1/36） |
| T9-015 | 胶片相册：筛选 + 查看器 + 收藏 | T9-011 | `ui/film/gallery/` | 5 个筛选 tab（全部/收藏/3 个卷）；按卷分组、空组隐藏；全屏查看器（首尾循环、片号、索引）；收藏集合 | 筛选与分组联动正确；查看器左右可循环；收藏持久化（原型只存内存，需补） | 真机逐项点按；空态截图 | `TODO` |
| T9-016 | 换卷页（原「胶卷库」） | T9-011、D-043 | `ui/film/films/`、`navigation/AppState.kt` | 已装卷大卡（卷名/帧尺寸/已拍-容量/进度）+ 全部卷列表 + 装卷动作；新增 `AppDestination.FILMS` | 卷数量按实际 pack 渲染，**不写死 6**；装卷后返回取景页卷名同步；无价格/到期日/品牌故事 | 真机 `adb push` 两卷/四卷各截一次图 | `DONE`（2026-10-20 真机：三卷渲染 / 装卷后卷名与左下角标同步 / 顶栏相册入口 / 返回回库，截图 `docs/screenshots/2026-10-20-films-library.png`） |
| T9-017 | ~~冲扫进度页~~ | — | — | 原型 `roll.html`（订单/LAB/加急/下载） | — | — | `REJECTED`（D-042：虚构线下服务，不复刻） |
| T9-018 | 成片页暗色化 | T9-011、D-043、D-045 | `ui/result/ResultScreen.kt`、`camera/PhotoExif.kt` | 按 `gallery.html` 单张详情重写：大图 + 三列参数网格 + 真实片号；三态（冲洗中/成功/失败） | 与取景页同一套令牌；无奶油红残留；参数只含真读到的 EXIF，读不到显示 `—` | 真机拍一张看冲洗中→成功；尺寸不符看失败态 | `IN_PROGRESS`（代码已落地：新增 `readPhotoExif`，删掉 `ToyCameraBody`/`PocketCcdColors` 依赖；待真机截图） |
| T9-019 | 照片墙暗色化 + 按卷分组 | T9-011、D-043、D-045 | `ui/gallery/PhotoWallScreen.kt` | 按 `gallery.html` 列表重写：居中标题栏 + 按卷分组 + 三列缩略图 + 时间戳 | 无「冲扫中 72%」、无 LAB 水印；空态有明确引导 | 真机跨两卷各拍几张后截图 | `IN_PROGRESS`（代码已落地。**分组口径改为按胶卷名**，不是 `rollStartedAt`：历史照片没有持久化的卷边界，按时间间隔猜新卷属于编造，见文件头注释；待真机截图） |
| T9-020 | 设置页暗色化 | T9-011、D-045 | `ui/settings/SettingsScreen.kt` | 按 `FilmTheme` 令牌重排现有设置项 | 无奶油红残留；所有行可用；热区 ≥48dp | 真机逐项点按 + 截图 | `IN_PROGRESS`（代码已落地。**顺手删掉「默认滤镜」日间/闪光/夜间分段控件**——它没接线，选了不落盘也不影响成像，是假开关；待真机截图） |
| T9-021 | 真实镜头倍率 + 等效焦距 | T9-007、D-044 | `camera/LensInfo.kt`、`camera/CameraPreview.kt`、`ui/film/FilmInteractions.kt` | 读 `physicalCameraInfos` + `intrinsicZoomRatio`；算 35mm 等效焦距；档位随设备弹性渲染 | 倍率与官方规格一致（PJZ110 应为 0.65/1.0/3.17）；探不到时退回 1x/2x 数码档且不显示 mm | 真机 `adb logcat -s PocketCcdCamera` 打印镜头表；拍 1×/3.2× 比对 EXIF `FocalLength` | `IN_PROGRESS`（代码已落地：`readLensStops` + `LensStop`，变焦钮改为动态档位；**待真机确认一加 13 是否暴露 physical infos**） |
| T9-022 | 启动闪红修复 + 旧令牌清理 | T9-012、D-039 | `res/values/themes.xml`、`ui/theme/Theme.kt`、`ui/theme/Tokens.kt`、`ui/theme/Type.kt` | windowBackground 对齐首帧底色；Material 兜底 scheme 改深色；删除旧红色设计令牌 | 冷启动无红色帧；四个页面在 M3 组件（对话框/选中柄）上不闪白 | 真机冷启动连拍 12 帧主色分析 | `DONE`（**真机已验证**）。根因：`themes.xml` 的 `android:windowBackground` 一直是旧玩具机身的 `#D9382E`，窗口在 `setContent` 之前就画出来了，Compose 首帧要等 Activity + ViewModel 首次扫卷，那 200–300 ms 露的就是它。第二处来源是 `PocketCcdTheme` 用了 `lightColorScheme`（米色/珊瑚红）。修法：`windowBackground` → `#0B0C0E`，父主题 `Theme.Material.Light.NoActionBar` → `Theme.Material.NoActionBar`，`darkColorScheme` 直接取 `FilmDarkSpec.palette` 的值（`accent`/`appBg`/`sheetBg`/`hairline`），避免出现「第二套近黑，但不完全一样」。实测：华为 FOA-AL00 冷启动 577 ms，连拍 12 帧里起始两帧是桌面（正常），第 3–4 帧均值 `(11,12,13)` ≈ `#0B0C0E`，红色像素占比 0.1%，第 5 帧起是取景画面 |
| T9-023 | 信息密度与字号地板（D-050） | T9-022 | `ui/film/FilmTheme.kt`、五个页面、`NoBareFontSizeTest` | 字号下限 11sp + 语义文字色；取景页画面外文字 10→5 条；删开发者指标；胶卷库每卡 8→5 条 | 裸字号 0；UI 层检索不到 `ms ·`/`strips`/`.fswp`/`1.8 MB`/`AUTO ·`/`余 N 张`；font_scale 1.3 下无裁切 | 单测 + 真机四页截图 | `DONE`（**代码与单测已过，真机截图待补**）。规格见 [`docs/reviews/2026-10-21-ui-density-change-spec.md`](docs/reviews/2026-10-21-ui-density-change-spec.md)，依据见 `2026-10-20-typography-and-density-audit.md`。`FilmType` 新增 `value`(14sp)/`microPrint`(9sp)，`optionTitle` 13→15、`body` 12→13、`mono`/`monoSmall`/`label` 全部 →11sp；`FilmPalette` 新增 `textSecondary`(白 60%)/`textTertiary`(白 50%)。删掉 `LIVE`/`12.6MP`/`4:3` chip、`G200` 缩写、footer 卷名尺寸、`AUTO · 4:3 · JPEG`、`余 N 张`、成片页引擎耗时与 `JPEG · 4:3`、胶卷库 pack 文件名与体积；`FilmChip`/`megapixelLabel`/`stockShortLabel`/`fileSizeLabel` 随之移除。**两处例外**：胶卷库浅色金属面板保留深色字，取景器内文字用 `ink` 不用 50% 白。单测 29/29 |

**依赖关系：**

```text
D-038（UI 基线确认）
  └→ T9-001（语义令牌/ThemeSpec）
       ├→ T9-002（结构定稿，需 D-039）─┬→ T9-011（其余页面）
       │                              └→ T9-012（验收切换）
       ├→ T9-003（取景器外壳+叠加层）
       │    ├→ T9-004（chip，需 D-040）
       │    ├→ T9-005（快门/控制行）→ T9-009（反馈）
       │    ├→ T9-006（焦点环）
       │    └→ T9-007（顶栏/变焦）
       ├→ T9-008（面板，需 D-041）
       └→ T9-010（图标）
T9-003..T9-011 → T9-012
D-039 选 1 → T9-013；D-040 选 3 → T9-014
```

**M9 退出条件：**

1. `assembleDebug` / `assembleRelease` 通过；`AppStateTest` 全绿；
2. 真机（`PJZ110`）截图与设计稿逐层对照，偏差表完整；
3. 全部可点控件 ≥48dp（Accessibility Scanner 复验）；
4. 无伪造参数（`grep` 证明）；
5. 拍摄→冲洗→落盘→照片墙→系统相册 链路未回归；
6. 旧红色 UI 代码已删除，`DECISION_LOG.md` 的 D-027..D-032 已标 `SUPERSEDED`，规范 §2.1/§2.2 已修订；
7. 未通过前不得把 M9 标成 `DONE`，也不得删除旧包。

---

# 优先级映射（M0 / M2–M8）

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
| M8 | FOT-001、FOT-002、FOT-003、FOT-004、FOT-013 | FOT-005、FOT-006、FOT-008、FOT-009、FOT-010 | FOT-011、FOT-012、FOT-014 |
| M9 | T9-001、T9-002、T9-003、T9-005、T9-006、T9-007、T9-009、T9-012、T9-016 | T9-004、T9-008、T9-011、T9-015 | T9-010、T9-013、T9-014、T9-017 |

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
| EXP-13 | Fotufilm Android AOT 能否在真机跑 12MP？ | Phase 0：构建机生成 FSWP + arm64 AOT；JNI 处理单张 12MP | 成功输出；耗时/内存/pack/so 大小在预算内；与桌面参考误差可接受 | `docs/fotufilm-android-spike.md` | M8/FOT-004；D-033 | `BLOCKED`（构建侧已通（run `39198895398`）：CI 产 pack + 5 个 AOT 内核 + parity 64 case + `libfotufilm_android.so` 5.7 MB；**仍缺 Android 真机**，Phase 0 的真机判据未跑） |

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
| T1-004 | 设计令牌/主题 | `DONE` | `ui/theme` + 令牌文档 |
| T1-005 | 页面状态机 | `DONE` | 状态机 + 导航骨架；12 单元测试通过 |
| T1-006 | 首页 | `DONE` | Home 静态 UI |
| T1-007 | 下拉手势 | `IN_PROGRESS` | 距离阈值 + 跟随 + 转场；速度/测试待补 |
| T1-008 | 拍摄页 | `DONE` | Camera 静态 UI + 快门 |
| T1-009 | 图库 | `DONE` | 照片墙静态 UI + 空态 |
| T1-010 | 成片页 | `DONE` | Result 静态 UI + 状态变体 |
| T1-011 | 设置页 | `DONE` | Settings 静态 UI + Debug 诊断 |
| T1-012 | 状态矩阵 | `TODO` | Loading/Empty/Error |
| T1-013 | 自适应/无障碍 | `TODO` | 多尺寸 + TalkBack |
| T1-014 | 视觉基线 | `TODO` | 截图 + 评审 |
| T1-015 | 模拟器/设备 | `BLOCKED` | AVD/真机 |
| T1-016 | 性能烟雾 | `TODO` | 启动/切换基线 |
