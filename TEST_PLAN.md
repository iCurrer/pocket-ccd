# Pocket CCD — 测试计划

**建立日期：** 2026-10-17  
**适用版本：** 规范 V1.0 / 工程准备阶段  
**当前测试阶段：** M1（静态 UI 原型）+ M8 构建侧；已执行 Debug 安装/启动，未执行相机/FOT 真机 12MP
**配套文件：** `TASK_BOARD.md`、`SPEC_REVIEW.md`、`DECISION_LOG.md`、`PROJECT_STATE.md`

---

## 1. 目的与范围

本计划定义：

- 测试层级、环境、证据格式和状态规则；
- 阶段 1（工程初始化 + 静态 UI 原型）的可执行测试用例；
- 后续相机、成像、存储、性能、兼容性测试的用例目录；
- 什么情况下可以声称“通过”，什么情况下只能写“未执行/阻塞”。

**不在本计划范围内：**
- 完整相机功能、图像算法正确性的最终结论（阶段 2+）；
- 商业发布合规的法律意见；
- 付费设备农场、云测试、照片上传。

---

## 2. 测试原则

1. **未执行不得标通过。** 只有实际运行并记录证据的用例才能标 `PASS`。
2. **模拟器不等于真机。** 模拟器可用于 UI/状态/布局；相机预览、闪光灯、ISP、色彩、性能必须真机。
3. **不用单一指标证明画质。** 文件大小不能证明分辨率；单张好看不能证明滤镜；主观评价不能单独作为通过依据。
4. **固定输入、固定参数、固定版本。** 成像测试必须保存测试图、滤镜参数、处理器版本和构建哈希。
5. **隐私优先。** 测试图使用合成图、无版权/已授权样片或用户明确提供的非敏感图片；不上传、不把照片内容写入日志。
6. **失败也是结果。** 失败用例要记录实际行为、日志、设备和复现步骤，不能只写“失败”。

### 2.1 状态

| 状态 | 含义 |
|---|---|
| `NOT_RUN` | 尚未执行 |
| `RUNNING` | 执行中 |
| `PASS` | 实际执行并通过，证据完整 |
| `FAIL` | 实际执行未通过，有缺陷记录 |
| `BLOCKED` | 因环境/设备/依赖无法执行，需注明原因 |

### 2.2 证据标记

`[F]` 已核实事实、`[I]` 工程推断、`[H]` 待验证假设、`[D]` 决策。

---

## 3. 测试环境

### 3.1 当前环境（2026-10-17 盘点）

| 项目 | 现状 | 状态 |
|---|---|---|
| 操作系统 | Windows 10.0.26300 | `[F]` |
| Android Studio | 2026.1.2（AI-261.25134.95.2612.15822958） | `[F]` |
| JBR | OpenJDK 21.0.10 | `[F]` |
| Android SDK Platform | 35、36、36.1、37.0 | `[F]` |
| Build Tools | 34.0.0、36.0.0 | `[F]` |
| Platform Tools / adb | 37.0.0 | `[F]` |
| Gradle | 9.6.1、9.5.0、8.10.2 已缓存 | `[F]` |
| 依赖缓存 | AGP 9.2.0/9.3.2/9.3.3；Kotlin 2.3.21/2.4.10；Compose BOM 2026.06.01/2026.08.00；CameraX 1.6.2；Room 2.8.5 | `[F]` |
| AVD / system image | 无 | `[F]` |
| 已连接设备 | `PJZ110`（OnePlus/OP5D0DL1），arm64-v8a，Android API 37 | `[F]` 2026-10-19 |
| `sdkmanager`/`avdmanager` | 未安装（无 cmdline-tools） | `[F]` |
| Git | 工作区尚未初始化 | `[F]` |
| 网络 | 系统代理 127.0.0.1:7897；Java/Node 可访问官方文档；Windows schannel curl 失败 | `[F]` |

**结论：** 已能做静态检查、文档检查、Gradle 构建，并已在 `PJZ110` 真机上安装/启动 Debug APK、截图、下拉进入拍摄页；**相机/FOT 12MP/Compose UI 测试仍未执行**。

### 3.2 阶段 1 目标环境

| 环境 | 用途 | 要求 |
|---|---|---|
| 本机 Gradle/JDK | 编译、单元测试 | JDK 21；Gradle 9.6.1；SDK 37 |
| 模拟器（API 36/37） | Compose UI、布局、字体缩放、截图 | 需安装 system image；至少 1 个 AVD |
| 真机（阶段 2+） | 相机/闪光灯/色彩/性能/存储 | 至少 1 台；阶段 6 扩展到 3 档 |

### 3.3 设备登记模板

| 字段 | 示例 | 必填 |
|---|---|---|
| 设备代号 | `mid-01` | 是 |
| 型号 | 厂商 + 型号 | 是 |
| SoC / RAM | 例如 Snapdragon xxx / 8GB | 是 |
| Android 版本 / API | 例如 Android 16 / 36 | 是 |
| 屏幕 | 尺寸、分辨率、刷新率、宽高比 | 是 |
| 相机 | 主摄/前摄、是否支持闪光灯、硬件级别 | 是 |
| 测试角色 | 高性能/中端/低端 | 是 |
| 备注 | 已知厂商问题 | 否 |

> 设备型号必须在实际测试开始前登记；不得用“某台手机”代替。

---

## 4. 测试层级

| 层级 | 工具 | 覆盖 | 阶段 1 是否可用 |
|---|---|---|---|
| 静态检查 | `./gradlew lint`、`grep`、Manifest 检查 | 权限、硬编码、依赖、禁用 API | 构建后可用 |
| 单元测试 | JUnit4 + coroutines-test | 状态机、令牌、fake repository、错误映射 | 构建后可用 |
| Compose UI 测试 | `ui-test-junit4` + `createComposeRule` | 页面、导航、手势、语义 | 需模拟器/设备 |
| 仪器测试 | AndroidX Test | View 集成、权限、存储 | 需设备 |
| 手工真机 | 人眼/手测 | 相机、闪光灯、色彩、手感、TalkBack | 需真机 |
| 性能 | `am start -W`、Perfetto、JankStats、`dumpsys meminfo` | 启动、帧、耗时、内存 | 需设备 |
| 视觉评审 | 截图 + 检查表 | 布局、层级、对比度、一致性 | 需可运行 APK |

---

## 5. 阶段 1 测试用例

> 当前所有 `TC-P1-*` 均为 `NOT_RUN`；只有实际执行后才可改状态。  
> `前置` 中的“T1-xxx”指 `TASK_BOARD.md` 的任务。

### 5.1 导航与页面

| ID | 用例/步骤 | 预期结果 | 层级 | 自动化 | 状态 |
|---|---|---|---|---|---|
| TC-P1-001 | 冷启动 App，停留在首页 10s；检查权限弹窗、日志、相机初始化 | 不请求 CAMERA；不出现权限弹窗；无相机初始化；首页正常显示 | 手工 + 日志 | 否 | `PASS`（2026-10-19，`PJZ110`；截图 `docs/screenshots/2026-10-19-home.png`；未抓完整 logcat） |
| TC-P1-002 | 首页点击 `PHOTO WALL` | 进入照片墙；返回回到 Home；无崩溃 | UI | 是 | `NOT_RUN` |
| TC-P1-003 | 首页点击“进入相机”按钮 | 进入 Camera 静态页；无权限请求；返回 Home | UI | 是 | `NOT_RUN` |
| TC-P1-004 | 首页从顶部向下拖拽超过阈值并释放 | 进入 Camera；动画完成后状态为 CAMERA | UI + 手工 | 部分 | `NOT_RUN` |
| TC-P1-005 | 首页向下拖拽小于阈值后释放 | 回弹到 Home；不导航 | UI | 是 | `NOT_RUN` |
| TC-P1-006 | 在转场动画期间快速重复下拉/点击 | 不重复入栈；最终只在一个 Camera 页面 | UI + 手工 | 部分 | `NOT_RUN` |
| TC-P1-007 | Camera 页按返回/返回手势 | 回到 Home；相机占位不残留 | UI + 手工 | 是 | `NOT_RUN` |
| TC-P1-008 | Camera 页按系统返回 | 回到 Home；相机页无图库按钮/顶部导航，不出现图库路径 | UI + 静态 | 部分 | `NOT_RUN` |
| TC-P1-009 | Result 页点击“返回拍摄” | 进入 Camera；无黑屏/重复页面 | UI | 是 | `NOT_RUN` |
| TC-P1-010 | Result 页点击“图库” | 进入 Gallery；返回路径合理 | UI | 是 | `NOT_RUN` |
| TC-P1-024 | 使用系统预测性返回手势从 Camera/Result 返回 | 不与应用手势冲突；返回目标正确；无闪烁/双导航 | 手工 | 否 | `NOT_RUN` |

### 5.2 状态、恢复与交互

| ID | 用例/步骤 | 预期结果 | 层级 | 自动化 | 状态 |
|---|---|---|---|---|---|
| TC-P1-011 | 在 Home/Gallery/Camera 旋转屏幕；检查状态 | 页面不重置到错误位置；滚动/选中状态合理保留；无崩溃 | UI + 手工 | 部分 | `NOT_RUN` |
| TC-P1-012 | 系统字体调到 200%；遍历五个页面 | 无截断/重叠；可滚动到所有内容；按钮可点 | 手工 + 截图 | 否 | `NOT_RUN` |
| TC-P1-013 | 横屏、≥600dp 宽度、分屏/自由窗口 | 布局可用；不拉伸；不遮挡系统栏；Camera 页控制区可达 | 手工 + 截图 | 否 | `NOT_RUN` |
| TC-P1-019 | 依次触发每个页面的 loading/empty/error/正常态 | 每个状态有明确 UI；无死路；错误有动作 | UI | 是 | `NOT_RUN` |
| TC-P1-023 | 在 Camera/Gallery 按 Home 键后从最近任务恢复；开发者选项“不保留活动” | 恢复到合理页面；不崩溃；不丢失关键状态 | 手工 | 否 | `NOT_RUN` |
| TC-P1-025 | 静态检查阶段 1 代码/依赖 | 无 CameraX/Camera2 类；无相机权限；无相机初始化 | 静态 | 是 | `PASS`（2026-10-17 复检：`ui/camera` 无 CameraX/Camera2 import；Manifest 无 CAMERA） |
| TC-P1-026 | 运行 `AppStateTest` 覆盖 HOME/CAMERA/GALLERY/RESULT/SETTINGS 转换、非法转换、重复事件、返回 | 12 个测试全部通过；非法转换不改变状态 | 单元 | 是 | `PASS`（2026-10-17，12/12，0 failures） |
| TC-P1-028 | 检查拍摄页是否符合“机身内取景框/非全屏”静态约束 | 12dp 机身外边距、16dp 取景框内留白、4:3；快门 88dp 触摸区；无 CameraX；无预览截图逻辑 | 静态 | 是 | `PASS`（2026-10-17；布局数值来自代码/令牌，未做视觉截图） |
| TC-P1-029 | 检查首页→拍摄页的共享元素转场代码路径 | `SharedTransitionLayout` 包裹 `AnimatedContent`；首页机身与拍摄页机身使用同一 key `camera-body`；编译通过 | 静态 | 是 | `PASS`（2026-10-17；运行时连续性未验证） |
| TC-P1-030 | 计算 V2 玩具色板关键前景/背景对比度 | 主文字/机刻字/LCD/珊瑚底文字均≥4.5:1 | 静态 | 是 | `PASS`（2026-10-17 脚本：13.19/8.56/7.30/4.73/4.58/10.24/5.51/8.95） |
| TC-P1-031 | 检查照片墙只接收 app-owned 记录、无系统图库扫描 | 运行时 `photos = emptyList()`；无媒体权限/MediaStore 查询；空态有说明；网格 key 稳定 | 静态 | 是 | `PASS`（2026-10-17；网格预览为合成色块） |
| TC-P1-032 | 首页向上滑动进入照片墙 | 上拉超过 88dp 进入 `PHOTO WALL`；未达阈值回弹；反向返回首页 | UI + 手工 | 部分 | `NOT_RUN`（真机可测，未执行；已测的是下拉进入拍摄页） |
| TC-P1-033 | 拍摄页右上角无图库按钮 | 顶栏只有收起 + 居中 DC-01；无 gallery 点击路径 | 静态 | 是 | `PASS`（2026-10-17） |
| TC-P1-034 | 检查 Result 页状态变体与 demo 诚实性 | 4:3 测试图；SAVED/FAILED 预览；运行时 `DEMO · NOT SAVED`，不显示真实“已保存” | 静态 | 是 | `PASS`（2026-10-17；设备 UI 未跑） |
| TC-P1-035 | 检查 Result 页无相机/存储依赖 | 只有状态与回调；无 CameraX/MediaStore/文件写入 | 静态 | 是 | `PASS`（2026-10-17） |
| TC-P1-036 | 检查 Settings 页内容与依赖 | 保存位置/默认滤镜/质量占位/版本/隐私/Debug 诊断；无网络/权限 | 静态 | 是 | `PASS`（2026-10-17；BuildConfig 版本号） |
| TC-P1-037 | 检查 Debug 诊断入口仅 debug | `if (BuildConfig.DEBUG)` 门控；Release 不进入该分支 | 静态 | 是 | `PASS`（2026-10-17；Release APK 构建通过，未在设备上打开 Release UI） |
| TC-P1-038 | 检查 V2 玩具机身结构 | 首页/照片墙/成片/设置使用 `ToyCameraBody`/`ToyScreen`/物理按钮；首页无文本链接行 | 静态 | 是 | `PASS`（2026-10-17；运行时视觉未验） |
| TC-P1-039 | 检查红色拍摄页结构 | 红色全屏、厚重黑取景器、绿色闪光灯（不可用态）、橙色快门、红色循环键、紧凑品牌、无顶部导航；预览为静态测试画面 | 静态 | 是 | `PASS`（2026-10-17；非实时相机） |
| TC-P1-040 | 计算红色页面关键对比度 | 品牌 logo/取景器/LCD 文字达到对应阈值；小字不在红底上用奶油色 | 静态 | 是 | `PASS`（2026-10-17 脚本：奶油/红 3.86 仅 logo；奶油/深红 6.02；深字/绿 9.17；深字/橙 7.00） |
| TC-P1-041 | 检查首页红色统一主题 | 首页与拍摄页同一红塑料/黑镜头/绿闪光/橙快门语言；无网页文本链接；快门/FILM/设置可达 | 静态 | 是 | `PASS`（2026-10-17；真机视觉未验） |
| TC-P1-042 | 检查系统栏与页面同色/图标对比 | HOME/CAMERA 的 `Surface` 用 `RetroRed`；其他页用浅薄荷；状态栏/导航栏图标随之切换 | 静态 | 是 | `PASS`（2026-10-17；真机未验） |
| TC-P1-043 | 检查共享转场 overlay 防矩形闪角 | `sharedBounds` 有 36dp `OverlayClip` + `zIndexInOverlay=1f` + `Enter/Exit None`；红页底 non-cream | 静态 | 是 | `PASS`（2026-10-17；真机慢放未验） |
| TC-P1-044 | 检查首页品牌区无多余线条 | 已删 `PULL DOWN`、外圈白线、闪光窗分割线、镜头弧线；品牌 16sp/1.5sp | 静态 | 是 | `PASS`（2026-10-17） |
| TC-P1-045 | 检查首页/拍摄页单一持久 Stage | HOME/CAMERA 不为两个 `AnimatedContent` 页面；`CameraStageScreen` 共用机身；拖拽 progress 驱动 | 静态 | 是 | `PASS`（2026-10-17；真机跟手未验） |
| TC-P1-046 | 检查红框机身全页统一 | `ToyCameraBody` 外框为红、内面板奶油；首页/拍摄/照片墙/成片/设置同一外框 | 静态 | 是 | `PASS`（2026-10-17；真机视觉未验） |
| TC-P1-047 | 检查设置页内容 App 化 | 24sp 标题、15/12sp 行、44dp 分段控件、分组卡片、LazyColumn；无 8sp LCD 小字 | 静态 | 是 | `PASS`（2026-10-17；真机视觉未验） |

### 5.3 无障碍与视觉

| ID | 用例/步骤 | 预期结果 | 层级 | 自动化 | 状态 |
|---|---|---|---|---|---|
| TC-P1-014 | 开启 TalkBack，遍历五个页面 | 每个可交互元素有可理解标签；焦点顺序合理；无“未标记按钮” | 手工 | 否 | `NOT_RUN` |
| TC-P1-015 | 检查所有按钮/图标/手势替代入口的触摸区域 | ≥48dp；不重叠；边缘可点 | 手工 + 布局检查 | 部分 | `NOT_RUN` |
| TC-P1-016 | 用对比度工具检查正文/次要文字/按钮 | 正文≥4.5:1；大字号≥3:1；禁用态可辨识 | 手工 | 否 | `NOT_RUN` |
| TC-P1-020 | 在 360×800、412×915、横屏、≥600dp、字体 100%/200% 截图 | 截图来自实际构建；布局符合评审清单；无 P0 视觉缺陷 | 手工 + 评审 | 否 | `NOT_RUN` |

### 5.4 构建、权限与性能

| ID | 用例/步骤 | 预期结果 | 层级 | 自动化 | 状态 |
|---|---|---|---|---|---|
| TC-ENV-001 | 盘点 SDK/Studio/JDK/Gradle/设备 | 版本与路径记录完整；与文档一致 | 环境 | 是 | `PASS`（2026-10-17，仅环境盘点） |
| TC-ENV-002 | `./gradlew :app:assembleDebug` | 构建成功；APK 产物存在；无警告级阻塞 | 构建 | 是 | `PASS`（2026-10-17；有非阻塞 SDK 重复目录/strip 警告） |
| TC-ENV-003 | `git status`、`git check-ignore` | 仓库干净；构建产物/本地配置被忽略；规范文件未修改 | 仓库 | 是 | `PASS`（2026-10-17；提交后复检） |
| TC-ENV-004 | 创建 AVD 或连接真机；`adb devices` | 设备可见；可安装/启动/截图 | 环境 | 是 | `PASS`（2026-10-19，`PJZ110` arm64-v8a，API 37；安装/启动/截图通过） |
| TC-P1-017 | `aapt dump permissions` + Manifest 检查 | 无 CAMERA/INTERNET/存储/麦克风/位置权限 | 静态 | 是 | `PASS`（仅有 AndroidX 自定义签名权限 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`） |
| TC-P1-018 | `assembleRelease`（未签名可）+ 检查 Debug 入口 | Release 不显示诊断入口；无调试日志 | 构建 + UI | 部分 | `PASS`（构建；Release UI 未跑） |
| TC-P1-021 | `adb shell am start -W` 冷启动 ×5 | 记录中位数/P95；建立基线，不承诺固定值 | 性能 | 是 | `NOT_RUN` |
| TC-P1-022 | 页面往返 20 次；Perfetto/JankStats（可选） | 无崩溃/ANR；记录掉帧；不承诺固定帧率 | 性能 | 部分 | `NOT_RUN` |
| TC-P1-027 | `grep` 检查 `HomeScreen`/`Theme` 的硬编码颜色/尺寸 | 页面只使用令牌；仅 Preview 注解允许硬编码背景色 | 静态 | 是 | `PASS`（2026-10-17；仅 Preview 背景色例外） |

### 5.5 用例前置条件说明

| 用例组 | 前置 |
|---|---|
| TC-P1-001..010、019、023、024 | T1-003 构建成功；T1-015 设备/模拟器可用；T1-005/T1-006..011 完成 |
| TC-P1-004..006 | T1-007 完成；手势阈值已记录 |
| TC-P1-011..013、014..016、020 | T1-012/T1-013 完成；T1-015 可用 |
| TC-ENV-002 | T1-002/T1-003 完成 |
| TC-P1-017/018/025 | T1-003/T1-011 完成 |
| TC-P1-021/022 | T1-014/T1-015 完成 |

### 5.6 已执行证据（2026-10-17）

| 项目 | 结果 |
|---|---|
| 构建命令 | `$env:JAVA_HOME='D:\software\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug --console=plain` |
| T1-003 首次构建 | `BUILD SUCCESSFUL in 59s`（36 tasks executed） |
| T1-003 Wrapper 复跑 | `BUILD SUCCESSFUL in 1s`（36 tasks up-to-date） |
| T1-003 首次 APK | `app/build/outputs/apk/debug/app-debug.apk`；29,570,230 bytes；SHA-256 `B77DB7171C99DDC10685DCB046512BD8BC48B2801679DAEDBDE47A702168912C` |
| T1-005 构建 + 单测 | `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL in 40s`（42 tasks） |
| T1-005 单测结果 | `AppStateTest`：12 tests / 0 failures / 0 errors |
| T1-005 重建 APK | 29,658,967 bytes；SHA-256 `508A109F9890D5C48871EBB73E08405C18241FECEA222AD18E15F0D6E771088B` |
| T1-004/T1-006 构建 + 单测 | `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL in 12s`；42 tasks（16 executed） |
| T1-004/T1-006 当前 APK | 30,844,690 bytes；SHA-256 `F7943F180D64A30F5B9C62E5828A01470DCEB1A3E71D3B0BC29CA8EF62837574` |
| T1-007/T1-008 构建 + 单测 | `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL in 5s`；42 tasks（8 executed） |
| T1-007/T1-008/T1-009 当前 APK | 30,853,023 bytes；SHA-256 `F4C143A1288DBBEB5C338E57082E437186065B803D61762A15B61D3691D4B472` |
| T1-010 构建 + 单测 | `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL in 11s`；42 tasks（18 executed） |
| T1-010 当前 APK | 30,857,502 bytes；SHA-256 `743A44AE230286FBC26FE8B15FB99A8BF48BC3EF7E8F975368E7464165D78C20` |
| T1-011 构建 + 单测 | `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL in 34s`；44 tasks（12 executed） |
| T1-011 当前 APK | 30,865,377 bytes；SHA-256 `CC4BF7E8C6102646F6003AC19035D7881290FC5AB4AB54F8194A6DC2C90FE760` |
| 红色拍摄页 Release 构建 | `assembleRelease` → `BUILD SUCCESSFUL in 14s`；`app-release-unsigned.apk` 23,462,826 bytes；SHA-256 `990A9A9AF781B894E0DB024D2EA484A759772C7A0408A0E6A3CE96A67B94FB00`；未签名，未安装 |
| V2 玩具材质重构构建 | `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL in 4s` |
| V2 当前 APK | 30,865,377 bytes；SHA-256 `A9BD98091019EAF6D7D98C01C71B8A2316C83114E1CF9F5E22FA6D8FD734FD69` |
| 红色拍摄页构建 | `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL in 9s`；44 tasks（7 executed） |
| 红色拍摄页当前 APK | 30,865,797 bytes；SHA-256 `3D3DB517EEAFE18AFB554F8E5DE4D6E520A3701BFF77021BEEDCB86353D00953` |
| 红色首页构建（打磨后） | `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL in 996ms`（增量，全部 up-to-date）；44 tasks |
| 红色首页当前 APK | 30,865,797 bytes；SHA-256 `40EDEF5EBBEAA775F360952CCF94090B79472AB3A0EFBBAE470510B04668F2AB`（随代码变化，不作为固定指纹） |
| 红色首页 Release 构建 | `assembleRelease` → `BUILD SUCCESSFUL in 6s`；`app-release-unsigned.apk` 23,446,442 bytes；SHA-256 `7C952C5771EE25A04418F7E629DB019C8C3C7252AB921CEBF79CB9A239059332`；未签名，未安装 |
| Stage 构建 | `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL in 2s`；44 tasks（8 executed） |
| Stage 当前 APK | 30,865,856 bytes；SHA-256 `FA1D7F7A3A1DFA053684DF7C2B4B7B9CA0628CFBE69DE4528E906B65F6B092B9`（随代码变化，不作为固定指纹） |
| Stage Release 构建 | `assembleRelease` → `BUILD SUCCESSFUL in 5s`；`app-release-unsigned.apk` 23,511,978 bytes；SHA-256 `60165770A1573CDE1442925973B37C5C8997E21AD03596387C948AD425D3B56A`；未签名，未安装 |
| 设置页 App 化构建 | `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL in 3s`；44 tasks（8 executed） |
| 设置页当前 APK | 30,947,368 bytes；SHA-256 `F447373C0B89FB0575F1120EE8834F2C16F9ACBA88ED0B505B4442C19FEC863A`（随代码变化，不作为固定指纹） |
| 设置页 Release 构建 | `assembleRelease` → `BUILD SUCCESSFUL in 5s`；`app-release-unsigned.apk` 23,514,922 bytes；SHA-256 `CC1D58232FF039ADD3E9AD8116DA0B13DA9E38CBFBC740633502430CE4888E0E`；未签名，未安装 |
| `aapt dump badging` | package `com.pocketccd.prototype`；compileSdk 37；targetSdk 36；minSdk 29；label `Pocket CCD` |
| 权限 | 无 CAMERA/INTERNET/存储/麦克风/位置；仅有 AndroidX 自定义签名权限 `com.pocketccd.prototype.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` |
| 设备 | `adb devices` 为空；安装/启动/UI 测试 `BLOCKED` |
| 非阻塞警告 | SDK 目录存在 `android-37.0` 与 `android-37.0-2` 重复，AGP 提示 inconsistent location；`libandroidx.graphics.path.so` 未能 strip，按原样打包。均未阻断构建，记录待环境整理。 |

**结论：** T1-003 的“构建、SDK 配置、权限检查”已实际通过；T1-005 的状态机单元测试 12/12 通过；红色外框 + 持久 Stage + App 式设置页后的代码可编译，静态硬编码/无相机依赖/Result 状态变体/设置页 Debug 门控/机身结构/红色页面结构检查通过。T1-007 仅实现了上下 88dp 距离阈值和跟随，**速度阈值与手势测试未做**。T1-003/T1-004/T1-005/T1-006/T1-008/T1-010/T1-011 的“安装/启动/Compose UI/旋转/截图”未执行，所以只能标 `DONE`，不能标 `VERIFIED`。

---

## 6. 后续阶段测试用例目录

> 以下是**用例目录**，不是已执行结果。具体步骤在对应任务开始时展开；任务板中的 `TC-*` 引用均指此处。

### 6.1 相机（M2）

| ID 范围 | 覆盖 |
|---|---|
| `TC-CAM-001..004` | 首次授权、拒绝、永久拒绝、权限撤销、去设置 |
| `TC-CAM-005..010` | 预览首帧、比例、旋转、前后台、锁屏、资源释放 |
| `TC-CAM-011..014` | 高分辨率采集、4:3、尺寸/方向、连续拍摄、低存储 |
| `TC-CAM-015..018` | 临时文件、EXIF 方向、损坏文件、格式/尺寸校验 |
| `TC-CAM-019..022` | 相机被占用、初始化失败、拍摄失败、重试/取消 |
| `TC-CAM-023..028` | 前/后摄、闪光灯、屏幕闪光、旋转、镜头切换 |
| `TC-CAM-029` | 预览与成片构图/裁切一致性 |
| `TC-CAM-030` | 设备能力探针导出 |

### 6.2 图像处理（M3）

| ID 范围 | 覆盖 |
|---|---|
| `TC-IMG-001..005` | 解码/编码、尺寸、OOM、错误码、固定输入可重复 |
| `TC-IMG-006..009` | sRGB/线性、ICC、P3 输入、输出色彩 |
| `TC-IMG-010..014` | 曲线、矩阵、饱和度、对比度、参数边界 |
| `TC-IMG-015..018` | 高光、阴影、局部对比度、关闭时无副作用 |
| `TC-IMG-019..022` | 锐化、颗粒、种子复现、无色边 |
| `TC-IMG-023..025` | LUT 加载、插值、越界、内存 |
| `TC-IMG-026..032` | 10 类场景测试集、golden、性能/内存 |

### 6.3 滤镜（M4）

| ID 范围 | 覆盖 |
|---|---|
| `TC-FLT-001..004` | schema、版本、非法参数、加载失败 |
| `TC-FLT-005..008` | DAYLIGHT 对照/盲评/限制 |
| `TC-FLT-009..012` | FLASH 色调/肤色/不宣称补光 |
| `TC-FLT-013..016` | NIGHT 暗部/高光/噪声/细节 |
| `TC-FLT-017` | 三滤镜验收表与已知限制 |

### 6.4 存储与图库（M5）

| ID 范围 | 覆盖 |
|---|---|
| `TC-STO-001..003` | Room schema/迁移/查询/不存图片字节 |
| `TC-STO-004..008` | 状态转换、事务、幂等、非法转换 |
| `TC-STO-009..012` | MediaStore 插入、`IS_PENDING`、发布、失败清理 |
| `TC-STO-013..015` | 缩略图、分页、200 张滚动、失败占位 |
| `TC-STO-016..019` | 跨重启保留、状态徽标、无重复照片 |
| `TC-STO-020..023` | 重试、原图保留、清理、孤儿文件 |
| `TC-STO-024..027` | 启动对账、崩溃点恢复、失败不当成功 |

### 6.5 兼容性与性能（M6）

| ID 范围 | 覆盖 |
|---|---|
| `TC-COMPAT-001..020` | 3 档设备 × 权限/相机/存储/旋转/低存储 |
| `TC-COMPAT-021..030` | 后台、锁屏、杀进程、低内存、相机占用、系统回收 |
| `TC-PERF-001..008` | 冷启动、预览首帧、采集、处理、峰值内存、连续拍摄、UI 流畅度 |
| `TC-REL-001..005` | Release 安装、R8、签名、升级、隐私/权限 |

### 6.6 Fotufilm 原生移植（M8）

| ID | 用例 | 预期 | 状态 |
|---|---|---|---|
| `TC-FOT-001` | License/来源审计 | 代码 Apache-2.0、profile CC BY-SA、第三方 notices、商标声明齐全 | `DONE`（清单已建） |
| `TC-FOT-002` | FSWP pack 生成 | 3 个 stock pack 可被 AOT parity 工具读取 | `NOT_RUN` |
| `TC-FOT-003` | arm64 AOT 加载 | `libfotufilm_android.so` 在真机 `dlopen` 成功 | `NOT_RUN` |
| `TC-FOT-004` | 12MP 单张处理 | 输出非黑/非 NaN；耗时/内存/pack/so 大小记录 | `NOT_RUN` |
| `TC-FOT-005` | 3 stock 色彩对比 | 与桌面参考的 ΔE/视觉差异在可接受范围 | `NOT_RUN` |
| `TC-FOT-006` | CameraX→引擎→相册 | 真机拍照后全分辨率处理并保存 | `NOT_RUN` |
| `TC-FOT-007` | Vulkan/CPU fallback | GPU 不可用时自动 CPU，输出一致 | `NOT_RUN` |
| `TC-FOT-008` | 47 stock 加载/包体 | 全部可加载；APK/资产在预算内 | `NOT_RUN` |
| `TC-FOT-009` | License 页/attribution | App 内可查看 Fotufilm/CC BY-SA/第三方声明 | `NOT_RUN` |

> 这些用例在 Phase 0/spike 前全部是 `NOT_RUN`；不能因为计划存在就标 `PASS`。

---

## 7. 性能与内存测试协议

### 7.1 设备与条件

- 至少 1 台高性能、1 台中端、1 台低端/低内存；登记型号/SoC/RAM/API。
- 测试前设备冷却，电量≥50%，关闭省电模式，固定亮度/刷新率（如可能）。
- 同一场景、同一光源、同一滤镜参数、同一构建哈希。
- 每项至少 5 次，报告 median 与 P95，不只报最好一次。

### 7.2 指标定义

| 指标 | 测量 | 注意 |
|---|---|---|
| 冷启动 | `adb shell am start -W` 的 TotalTime；清后台/强制停止后 | 首次安装 dex 优化另记 |
| 预览首帧 | 从进入 Camera 到首帧可见（日志/Perfetto） | 不含权限弹窗 |
| 快门到采集完成 | 点击快门 → `onImageSaved` 回调时间戳 | 记录分辨率/闪光灯 |
| 处理耗时 | 处理器入口/出口 `System.nanoTime`，CPU 时间另记 | 记录输入尺寸/滤镜 |
| 峰值内存 | `dumpsys meminfo` / `Debug.MemoryInfo` / Perfetto | 记录 Java/native/graphics |
| 连续拍摄 | 同场景 10 连拍，记录失败/耗时/发热 | 不中断、不 OOM |
| UI 流畅度 | JankStats/Perfetto 掉帧 | 只建立基线，不承诺固定帧率 |

### 7.3 暂行预算（`[H]`，不是承诺）

见 `DECISION_LOG.md` D-021。未测量前，任何“满足性能要求”的表述都只能用“目标”。

---

## 8. 视觉评审清单（阶段 1）

| 维度 | 检查项 | 通过标准 |
|---|---|---|
| 层级 | 品牌/主体/主 CTA/次级入口是否一眼可辨 | 3 秒内能找到“进入相机”和“图库” |
| 间距 | 边距/分组是否一致 | 使用令牌；无随意 13dp/17dp |
| 圆角 | 卡片/按钮/取景框是否统一 | 与令牌一致；无混用 |
| 对比 | 文字/图标/禁用态 | 正文≥4.5:1；大字号≥3:1 |
| 密度 | 小屏是否拥挤；大屏是否空洞 | 360dp 不溢出；≥600dp 有合理留白 |
| 动效 | 是否克制、可中断、无重复触发 | 转场≤320ms；无弹跳/闪烁 |
| 一致性 | 五个页面是否像同一产品 | 颜色/字体/控件一致 |
| 相机隐喻 | 是否像迷你数码相机而非专业控制台 | 控制数量少；快门突出 |
| 可访问性 | 字号/对比/TalkBack/触摸目标 | 见 TC-P1-012..016 |
| 内容真实 | demo 数据是否标注，是否误导为真实照片 | 截图/演示中标注“原型数据” |

> 视觉评审是人工签字流程；不把“看起来不错”写成自动化测试通过。

---

## 9. 故障注入矩阵（阶段 2+）

| 注入点 | 方法 | 预期 |
|---|---|---|
| 权限拒绝/永久拒绝 | 系统设置/`adb shell pm revoke` | 不崩溃；有解释/去设置 |
| 相机被占用 | 另一相机 App 占用 | 明确错误；可重试 |
| 相机初始化失败 | 模拟/厂商设备 | 错误码；资源清理 |
| 拍摄中切后台 | Home 键/锁屏 | 暂停/恢复；不泄漏 |
| 拍摄中杀进程 | `adb shell am force-stop` | 启动后状态可恢复/清理 |
| 低存储 | 填满磁盘/模拟 | 提前检查；失败不留半文件 |
| 处理中退出页面 | 返回/Home | 任务不丢失或明确取消 |
| 写入中崩溃 | 在 `IS_PENDING` 后杀进程 | 启动对账；不显示成功 |
| 数据库写失败 | 注入/模拟 | 文件/记录一致；可重试 |
| 临时文件损坏 | 截断/改字节 | 解码错误；不崩溃 |

---

## 10. 证据与记录格式

### 10.1 目录

```text
test-evidence/
└── 2026-10-17/
    ├── env-001_sdk-inventory.txt
    ├── p1-003_assembleDebug.log
    ├── p1-017_permissions.txt
    ├── p1-020_home_360x800.png
    └── p1-021_cold-start.txt
```

> 大截图/视频可放本地不提交 Git；提交文本日志与索引。禁止提交含人脸/定位/个人信息的照片。

### 10.2 每条证据必须包含

- 测试用例 ID；
- 日期时间；
- 构建哈希/版本（`git rev-parse HEAD` 或构建号）；
- 设备型号/API/RAM；
- 执行命令或操作步骤；
- 实际结果（通过/失败/阻塞）；
- 原始输出/截图路径；
- 失败时的复现步骤与日志片段（脱敏）。

### 10.3 结果表述

| 情况 | 允许写法 | 禁止写法 |
|---|---|---|
| 实际运行通过 | `PASS（证据：...）` | — |
| 代码审查判断 | `代码审查通过，未运行` | “已通过” |
| 未运行 | `NOT_RUN` | “应该没问题” |
| 环境缺失 | `BLOCKED（无设备）` | “模拟器应该一样” |
| 主观满意 | `视觉评审通过（评审人/日期）` | “画质已复刻” |

---

## 11. 阶段进入/退出标准

### 11.1 M1 进入

- `TASK_BOARD.md` 已确认；
- 工具链基线已选（D-004）；
- 工作区可写、Git 可初始化；
- 设备/模拟器计划已确定。

### 11.2 M1 退出

- `TC-ENV-002` 构建 `PASS`；
- `TC-ENV-004` 设备/模拟器可用；
- `TC-P1-001..025` 中所有 P0 用例 `PASS`，或明确 `BLOCKED` 并有负责人签字；
- 无 P0 缺陷；
- 截图基线 + 视觉评审记录；
- `PROJECT_STATE.md`/`TASK_BOARD.md`/`TEST_PLAN.md` 更新；
- 未执行项仍标 `NOT_RUN`。

### 11.3 后续阶段

| 阶段 | 退出最低要求 |
|---|---|
| M2 | 真机拍摄→保存 JPEG→系统图库可见；输出尺寸证明来自采集；设备记录 |
| M3 | 处理器可复现；输入/输出尺寸/色彩空间明确；有基准 |
| M4 | 三个滤镜有固定测试集/对照/耗时/限制/版本 |
| M5 | 跨重启保留；失败可恢复；系统图库可见 |
| M6 | 声明设备范围内关键功能通过；性能/兼容性报告 |
| M7 | Release 可安装；隐私/许可证审查；文档一致 |

---

## 12. 当前已执行/未执行清单

| 项目 | 状态 | 说明 |
|---|---|---|
| 环境盘点（SDK/Studio/JDK/Gradle/设备） | `PASS` | 2026-10-17 本机命令；仅环境事实，不是 App 测试 |
| Android 构建 | `PASS` | 2026-10-17 `assembleDebug` 成功；APK/哈希见 5.6 |
| 状态机单元测试 | `PASS` | `AppStateTest` 12/12；见 5.6 |
| 设计令牌/首页静态检查 | `PASS` | 页面使用令牌；仅 Preview 背景色例外；见 5.6 |
| 拍摄页静态检查 | `PASS` | 无 CameraX/Camera2；无 CAMERA；机身内取景框；见 `TC-P1-028` |
| 共享转场静态检查 | `PASS` | 同一 `camera-body` key；`SharedTransitionLayout`；见 `TC-P1-029` |
| 配色对比度检查 | `PASS` | 关键文字组合均≥4.5:1；见 `TC-P1-030` |
| 照片墙静态检查 | `PASS` | 运行时只传 app-owned 空列表；无系统图库扫描；见 `TC-P1-031` |
| 拍摄页无图库按钮检查 | `PASS` | 顶栏仅收起 + DC-01；见 `TC-P1-033` |
| 成片页静态检查 | `PASS` | 状态变体/demo 诚实性/无相机依赖；见 `TC-P1-034`、`TC-P1-035` |
| 设置页静态检查 | `PASS` | 内容/BuildConfig/Debug 门控；见 `TC-P1-036`、`TC-P1-037` |
| V2 玩具机身结构检查 | `PASS` | 首页/照片墙/成片/设置使用 `ToyCameraBody`/`ToyScreen`/物理按钮；见 `TC-P1-038` |
| 红色拍摄页结构检查 | `PASS` | 红底/黑取景器/绿闪光灯/橙快门/品牌；见 `TC-P1-039` |
| 红色拍摄页对比度检查 | `PASS` | 品牌仅 20sp 大字；小字不在红底用奶油色；见 `TC-P1-040` |
| 首页红色统一检查 | `PASS` | 与拍摄页同一红塑料/黑镜头/绿闪光/橙快门语言；见 `TC-P1-041` |
| 系统栏同色检查 | `PASS` | 红页 Surface/图标动态切换；见 `TC-P1-042` |
| 转场 overlay 裁剪检查 | `PASS` | 36dp OverlayClip 防矩形闪角；运行时未验；见 `TC-P1-043` |
| 品牌区线条精简检查 | `PASS` | 删除多余说明/边线/弧线；见 `TC-P1-044` |
| 持久 Stage 静态检查 | `PASS` | HOME/CAMERA 单一机身，无跨页 sharedBounds；见 `TC-P1-045` |
| 红框机身统一检查 | `PASS` | 五页外框红/内面板奶油；见 `TC-P1-046` |
| 设置页 App 化检查 | `PASS` | 24sp 标题/卡片/15+12sp 行/44dp 分段控件；见 `TC-P1-047` |
| Compose UI 测试 | `NOT_RUN` | 工程已存在，真机已就位但尚未接 `connectedAndroidTest` |
| 真机安装/启动 | `PASS` | 2026-10-19 `PJZ110` 安装/冷启动/截图（`TC-P1-001`、`TC-ENV-004`） |
| 相机预览/拍摄 | `NOT_RUN` | 阶段 1 不实现 |
| 图像处理/滤镜 | `NOT_RUN` | 阶段 3/4；M8 `.so` 已构建，无 Kotlin 调用方 |
| 性能/内存 | `NOT_RUN` | 真机已就位，12MP/冷启动基准未测 |
| 视觉评审 | `PARTIAL` | 首页/拍摄页有真机截图；其余三页无截图 |

**结论：** 截至目前，**构建链路、导航状态机逻辑、设计令牌的结构化使用、拍摄页静态约束**已实际验证；**首页与拍摄页已在 `PJZ110` 真机运行并截图，但相机/滤镜/性能没有任何实际验证**。不得在 `PROJECT_STATE.md` 或对外表述中写“App 已通过”“相机/滤镜/性能已通过”“UI 已完成验收”。

---

## 13. 风险与限制

| 风险 | 影响 | 缓解 |
|---|---|---|
| AVD/system image 未安装 | 模拟器用例无法执行（真机这类用例已可跑） | T1-015 安装 system image；或继续用 `PJZ110` 真机 |
| Gradle 依赖下载失败 | 无法构建 | 使用本地缓存；配置代理；记录错误 |
| 模拟器相机无效 | 相机结论错误 | 相机/色彩/性能只用真机 |
| 设备厂商差异 | 预览/色彩/闪光灯不一致 | 3 档设备 + 能力探针 + 已知限制 |
| 参考图许可不清 | 不能发布 | EXP-08 + 法务审查 |
| 主观验收 | 争议/返工 | 盲评 + 指标 + 评审签字 |
| 测试图含隐私 | 合规风险 | 合成/授权图；不上传；日志脱敏 |
| 过早承诺性能 | 用户预期落空 | 只写暂行目标；测量后更新 |
