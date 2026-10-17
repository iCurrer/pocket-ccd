# Pocket CCD — 项目状态

**快照时间：** 2026-10-17 20:42（Asia/Shanghai）
**当前阶段：** M1 进行中（首页/拍摄页静态 UI 已完成代码；其余页面与手势细化未完成）  
**规范版本：** `DEVELOPMENT_SPEC.md` V1.0  
**工作区：** `D:\Android\pocket ccd`  
**Git：** `main` 分支已初始化并提交（初始提交 `482b804`，后续文档提交会更新 HEAD）  
**App 代码：** Compose 空壳 + 导航状态机 + V2 玩具材质令牌 + 首页 + 拍摄页 + 照片墙 + 成片页 + 设置页（`MainActivity` + `AppRoot` + 五个页面 + `ToyCameraBody`/`ToyScreen`/`ToyRoundButton`）
**已执行 App 构建/测试：** `assembleDebug` 与 `assembleRelease` 均已通过；`AppStateTest` 12/12 通过；安装/启动/UI/相机未执行

---

## 1. 一句话状态

规范已读完并完成工程审查；已建立任务板、决策记录、测试计划和官方文档研究记录。**已创建可构建的 Compose 空壳并通过 `assembleDebug`；尚未安装/启动 App，尚未运行任何 UI、相机、图像或设备测试。** 下一步是准备设备/模拟器（T1-015），然后进入 T1-004 设计令牌与 T1-005 状态机。

---

## 2. 工作区盘点

### 2.1 审查前

| 文件 | 说明 |
|---|---|
| `DEVELOPMENT_SPEC.md` | 唯一的项目文件；38035 bytes；V1.0；1327 行；SHA-256 `178B856E42A96240D6490DB8EDB31AD44207EFE0889D2E2136E6741A66C72381` |

没有代码、没有 Gradle 工程、没有 Git 仓库、没有测试证据。`[F]` 目录列表 + `git status`。

### 2.2 当前（本次工程准备产出）

| 文件 | 作用 | 状态 |
|---|---|---|
| `DEVELOPMENT_SPEC.md` | 产品/技术规范基线，未修改 | 保持原样 |
| `README.md` | 项目入口、文档索引、边界说明 | 本次新建 |
| `SPEC_REVIEW.md` | 规范矛盾/缺失/不可验证项审查 | 本次新建 |
| `TASK_BOARD.md` | 任务拆分、验收、测试、实验计划 | 本次新建 |
| `DECISION_LOG.md` | 技术决策记录（ACCEPTED/PROPOSED/OPEN） | 本次新建 |
| `TEST_PLAN.md` | 测试层级、用例、证据、性能协议 | 本次新建 |
| `PROJECT_STATE.md` | 本文件 | 本次新建 |
| `research/android-official-docs-2026-10-17.md` | 官方文档版本、来源、核查结论 | 本次新建 |
| `app/`、`gradle/`、`build.gradle.kts`、`settings.gradle.kts` | Compose 空壳工程；可构建 `app-debug.apk` | 本次新建；T1-003 构建通过 |
| `.gitignore`、`.gitattributes`、`.editorconfig` | 仓库规范 | 本次新建 |
| `.research/` | 官方文档抓取缓存（Markdown 快照） | 临时研究材料；已在 `.gitignore` 中排除 |

> `.research/` 中的内容是从官方页面抓取的原始文本快照，仅用于核查；正式结论以 `research/android-official-docs-2026-10-17.md` 和官方链接为准。

---

## 3. 已完成事项

| ID | 事项 | 证据 | 状态 |
|---|---|---|---|
| P-001 | 阅读 `DEVELOPMENT_SPEC.md` 全文 | 本会话读取记录 | `DONE` |
| P-002 | 检查工作区，确认无既有代码/文件可覆盖 | 目录列表；审查前仅 1 个文件 | `DONE` |
| P-003 | 盘点本机 Android 工具链 | SDK/Studio/JBR/Gradle/缓存/设备命令输出 | `DONE` |
| P-004 | 核查官方 Android/CameraX/Camera2/Compose/AGP/Play 文档 | `research/android-official-docs-2026-10-17.md` | `DONE` |
| P-005 | 识别规范矛盾、缺失、不可验证项 | `SPEC_REVIEW.md` | `DONE` |
| P-006 | 建立技术决策记录 | `DECISION_LOG.md` | `DONE` |
| P-007 | 拆分任务并建立任务板 | `TASK_BOARD.md` | `DONE` |
| P-008 | 建立测试计划与实验计划 | `TEST_PLAN.md`、`TASK_BOARD.md` | `DONE` |
| P-009 | 建立项目状态文档 | 本文件 | `DONE` |
| P-010 | Git 仓库初始化与基线提交 | `main` 分支；初始提交 `482b804` | `DONE` |
| P-011 | Gradle/Compose 空壳构建 | `assembleDebug` 成功；APK/哈希见第 5 节 | `DONE`（构建通过；安装未执行） |
| P-012 | 导航状态机与骨架 | `navigation/`、`ui/AppRoot.kt`；`AppStateTest` 12/12 | `DONE`（逻辑测试通过；UI/旋转待设备） |
| P-013 | 设计令牌 + 首页静态 UI | `ui/theme/Tokens.kt`、`Type.kt`、`Theme.kt`、`ui/home/HomeScreen.kt`、`docs/design-tokens.md`；构建通过 | `DONE`（视觉/截图/多尺寸待设备） |
| P-014 | 拍摄页静态 UI + 下拉/展开原型 | `ui/camera/CameraScreen.kt`、`ui/components/CameraShutterButton.kt`、`AppRoot` 转场、首页下拉；构建通过 | `DONE`（T1-007 手势仍 `IN_PROGRESS`；UI/截图待设备） |
| P-015 | 玩具相机配色研究与实现 | `docs/color-direction.md`；V2 黄油/奶油/薄荷/珊瑚/深青色板；对比度脚本计算 | `DONE`（真机视觉评审待做） |
| P-016 | 照片墙 + 上拉手势 + 相机页精简 | `PhotoWallScreen.kt`；首页上拉；移除拍摄页图库按钮；共享机身转场优化 | `DONE`（真机 UI/动画待验证） |
| P-017 | 成片页静态 UI + 状态变体 | `ResultScreen.kt`；4:3 测试图；SAVED/FAILED 预览；运行时不伪造保存 | `DONE`（设备 UI 待验证） |
| P-018 | 设置页静态 UI + Debug 诊断 | `SettingsScreen.kt`；保存位置/默认滤镜/质量占位/版本/隐私；`BuildConfig` 门控诊断 | `DONE`（设备 UI/Release 构建待验证） |
| P-019 | V2 玩具相机材质重构 | `ToyCameraBody`/`ToyScreen`/`ToyRoundButton`；首页/照片墙/成片/设置改为机身/屏幕结构；拍摄页另走红色变体 | `DONE`（真机视觉待验证） |
| P-020 | 拍摄页红色复古前面板 | 全屏红塑料、厚重黑取景器、绿色闪光灯、橙色快门、红色循环键、紧凑品牌 | `DONE`（预览仍为静态画面） |
| P-021 | 首页统一红色主题 | 红色机身面板、黑色大镜头、绿色闪光窗、橙色快门、FILM 键、设置键 | `DONE`（真机视觉待验证） |

**注意：** 文档类 `DONE` 指交付物存在；P-011 是实际构建通过，但不是 App 功能验证。P-004 的“核查”指查阅官方页面并记录版本/来源，不是对依赖组合的构建验证（构建验证见 P-011）。

---

## 4. 当前未完成 / 未验证

| 事项 | 状态 | 说明 |
|---|---|---|
| Android 工程初始化 | `DONE` | T1-002/T1-003；可构建空壳 |
| App 安装/启动 | `BLOCKED` | 无 AVD/真机（T1-015） |
| Compose 主题/设计令牌 | `DONE` | T1-004；`docs/design-tokens.md`；对比度/字体待设备 |
| 页面状态机/导航 | `DONE` | T1-005；`AppStateTest` 12/12；UI/旋转待设备 |
| 五个静态页面 | `DONE` | 首页/拍摄页/照片墙/成片页/设置页均已完成静态 UI；设备 UI 待验 |
| 下拉/上拉手势/转场 | `IN_PROGRESS` | 下拉 88dp 相机、上拉 88dp 照片墙；共享机身转场已优化；速度阈值/手势测试未做 |
| 自适应/无障碍 | `NOT_STARTED` | T1-013 |
| 截图基线/视觉评审 | `NOT_STARTED` | T1-014 |
| AVD/真机 | `BLOCKED` | 无 AVD、无 system image、无设备 |
| Gradle 构建验证 | `PASS` | 2026-10-17 `assembleDebug` + `assembleRelease` 成功；APK/哈希见第 5 节 |
| Release 安装/UI | `NOT_RUN` | 未签名 Release APK 已生成，未安装 |
| 相机预览/拍摄 | `NOT_STARTED` | 阶段 2；当前明确不做 |
| 图像处理/滤镜 | `NOT_STARTED` | 阶段 3/4 |
| MediaStore/Room | `NOT_STARTED` | 阶段 5 |
| 性能/兼容性 | `NOT_STARTED` | 阶段 6 |
| 应用 ID/应用名 | `OPEN` | D-020；需产品确认 |
| minSdk 最终值 | `OPEN` | D-003/D-025；当前建议 29 |
| 原图保留策略 | `OPEN` | D-014；影响“重新处理” |
| 目标设备清单 | `OPEN` | D-025；阶段 2 前必须登记 |
| 视觉参考/原型图 | `OPEN` | 无参考图；阶段 1 只能做一致性评审 |
| 参考样片许可 | `OPEN` | 阶段 4 前完成 EXP-08 |

---

## 5. 本机环境事实

> 这些是 2026-10-17 的命令输出，不是推测。`[F]`

| 项目 | 值 |
|---|---|
| 工作目录 | `D:\Android\pocket ccd` |
| 操作系统 | Windows 10.0.26300 |
| Android Studio | 2026.1.2（`AI-261.25134.95.2612.15822958`） |
| Studio JBR | OpenJDK 21.0.10（`D:\software\Android\Android Studio\jbr`） |
| Android SDK | `D:\software\Android\Sdk` |
| SDK Platform | `android-35`、`android-36`、`android-36.1`、`android-37.0`、`android-37.0-2` |
| Build Tools | `34.0.0`、`36.0.0` |
| Platform Tools / adb | 37.0.0（`D:\software\Android\Sdk\platform-tools\adb.exe`） |
| Emulator | 36.6.11.0 |
| AVD | 无 |
| system image | 无 |
| cmdline-tools / sdkmanager / avdmanager | 无 |
| 已连接设备 | 无（`adb devices` 空） |
| Gradle 缓存 | 9.6.1、9.5.0、8.10.2 |
| AGP 缓存 | 9.2.0、9.3.2、9.3.3 |
| Kotlin 缓存 | 2.3.21、2.4.10 |
| Compose BOM 缓存 | 2026.06.01、2026.08.00；2026.08.00 的官方 POM 映射为 Compose 1.12.0 / Material3 1.4.0 |
| CameraX 缓存 | 1.6.2 |
| Room 缓存 | 2.8.4、2.8.5 |
| ExifInterface 缓存 | 1.4.2 |
| `java`/`gradle`/`adb` in PATH | 否 |
| 系统代理 | `127.0.0.1:7897`（Java/Node 可用；Windows schannel curl 失败） |
| Git | `main` 分支；初始提交 `482b804`；`.gitattributes` 强制 LF/CRLF 规则 |
| Gradle wrapper | 9.6.1；`./gradlew :app:assembleDebug` 已通过 |
| 构建警告 | SDK 目录同时存在 `android-37.0` 与 `android-37.0-2`，AGP 报 inconsistent location；未阻断构建 |

**工作区权限说明：** 本次开始时，DSH 的 Windows 沙箱无法为工作区授予写权限（`SetNamedSecurityInfoW ... grantWrite`）。已按 DSH 技能运行一次诊断修复，为当前用户补充了工作区目录的完全控制条目；恢复脚本与报告位于 `D:\Android\pocket-ccd-acl-report`。文件内容和所有者未被修改。`[F]`

### 5.1 构建证据（2026-10-17）

| 项目 | 结果 |
|---|---|
| 构建命令 | `JAVA_HOME=D:\software\Android\Android Studio\jbr; ./gradlew :app:assembleDebug` |
| T1-003 首次构建 | `BUILD SUCCESSFUL in 59s`（36 tasks executed） |
| T1-003 Wrapper 复跑 | `BUILD SUCCESSFUL in 1s`（36 tasks up-to-date） |
| T1-005 构建 + 单测 | `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL in 40s`（42 tasks） |
| 状态机单测 | `AppStateTest`：12 tests / 0 failures / 0 errors |
| 实际解析 | Kotlin stdlib 2.3.21；Compose UI 1.12.0；Material3 1.4.0（BOM 2026.08.00） |
| T1-003 APK | 29,570,230 bytes；SHA-256 `B77DB7171C99DDC10685DCB046512BD8BC48B2801679DAEDBDE47A702168912C` |
| T1-005 重建 APK | 29,658,967 bytes；SHA-256 `508A109F9890D5C48871EBB73E08405C18241FECEA222AD18E15F0D6E771088B` |
| T1-004/T1-006 构建 | `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL in 12s`；42 tasks（16 executed） |
| T1-004/T1-006 当前 APK | 30,844,690 bytes；SHA-256 `F7943F180D64A30F5B9C62E5828A01470DCEB1A3E71D3B0BC29CA8EF62837574` |
| T1-007/T1-008/T1-009 构建 + 单测 | `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL in 5s`；42 tasks（18 executed） |
| T1-010 构建 + 单测 | `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL in 11s`；42 tasks（18 executed） |
| T1-010 当前 APK | 30,857,502 bytes；SHA-256 `743A44AE230286FBC26FE8B15FB99A8BF48BC3EF7E8F975368E7464165D78C20` |
| T1-011 构建 + 单测 | `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL in 34s`；44 tasks（12 executed） |
| T1-011 当前 APK | 30,865,377 bytes；SHA-256 `CC4BF7E8C6102646F6003AC19035D7881290FC5AB4AB54F8194A6DC2C90FE760` |
| V2 玩具材质重构构建 | `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL in 4s`；42+ tasks |
| V2 当前 APK | 30,865,377 bytes；SHA-256 `A9BD98091019EAF6D7D98C01C71B8A2316C83114E1CF9F5E22FA6D8FD734FD69` |
| 红色拍摄页构建 | `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL in 9s`；44 tasks（7 executed） |
| 红色拍摄页当前 APK | 30,865,797 bytes；SHA-256 `3D3DB517EEAFE18AFB554F8E5DE4D6E520A3701BFF77021BEEDCB86353D00953` |
| 红色拍摄页 Release 构建 | `assembleRelease` → `BUILD SUCCESSFUL in 14s`；`app-release-unsigned.apk` 23,462,826 bytes；SHA-256 `990A9A9AF781B894E0DB024D2EA484A759772C7A0408A0E6A3CE96A67B94FB00` |
| 红色首页构建 | `assembleDebug testDebugUnitTest` → `BUILD SUCCESSFUL in 6s`；44 tasks（8 executed） |
| 红色首页当前 APK | 30,865,797 bytes；SHA-256 `7000DF74EC63D5A479D012B5A0AB0D74CDE8646AA858BD69A88B002CD3812E47`（随代码变化，不作为固定指纹） |
| 红色首页 Release 构建 | `assembleRelease` → `BUILD SUCCESSFUL in 7s`；`app-release-unsigned.apk` 23,446,442 bytes；SHA-256 `4639CC2AFA7A3AC39A040C40E24F8CD10E24DF1CE54A3D09C943DD3E3DC2D224`；未签名/未安装 |
| SDK | compileSdk 37 / targetSdk 36 / minSdk 29 |
| 权限 | 无 CAMERA/INTERNET/存储/麦克风/位置；仅有 AndroidX 自定义签名权限 `com.pocketccd.prototype.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` |
| 未执行 | `adb install`、启动、UI 截图、Compose UI 测试、相机/图像/性能测试（无设备） |

**注意：** 构建成功不等于 App 已验证。安装/启动仍被 B-01 阻塞。

---

## 6. 官方文档核查结论（摘要）

完整来源、版本/日期见 [`research/android-official-docs-2026-10-17.md`](research/android-official-docs-2026-10-17.md)。

| 主题 | 结论 | 对本项目的影响 |
|---|---|---|
| API level | Android 17 = 37；Android 16 = 36；Android 15 = 35 | compileSdk 37 可用；targetSdk 36 满足 Play 2026 要求 |
| Play target | 2026-08-31 起新应用/更新 target 36+ | 不能再用 target 35 发布 |
| Compose | 1.12.1 / Material3 1.4.0（2026-10-07）；BOM 最新 2026.09.00 | Compose 1.12 要求 compileSdk 37 + AGP 9.2+ |
| AGP | 9.4 最高 API 37 / Gradle 9.6.0；8.13.2 最高 API 36 / Gradle 8.13 | T1-003 已用 AGP 9.3.3 + Gradle 9.6.1 构建通过 |
| AGP 9 Kotlin | 内建 Kotlin；`org.jetbrains.kotlin.android` 与新 DSL 不兼容 | 工程不能照搬旧模板 |
| CameraX | 1.6.2 stable（2026-08-26）；1.7.0-alpha03 | 阶段 2 用 1.6.2；不用 alpha |
| CameraX 底层 | 1.6.0 起 CameraPipe；修复 API 37 动态范围崩溃 | 目标 API 37 设备应使用 1.6.2+ |
| CameraX minSdk | 1.5.0 起默认 23 | 本项目 minSdk 29 不受影响 |
| Camera2 | API 21+，低层控制；官方推荐大多数应用用 CameraX | 不因“更专业”整体改用 Camera2 |
| MediaStore | Android 10+ 自有照片无需存储权限；`IS_PENDING` 可控制发布 | minSdk 29 可只申请 CAMERA |
| Android 16 | target 36 强制 edge-to-edge；大屏忽略方向/尺寸限制 | 不能锁竖屏；必须做自适应 |

**官方文档自身的不一致（需注意）：**
- CameraX 概览页仍写“支持 Android 5.0 (API 21)+”，但 1.5.0-rc01 release notes 写“默认 minSdk 从 21 移到 23”。`[F]` 以 release notes 的库行为为准；应用 minSdk 29 不受影响。
- AGP 8.13 页面仍作为稳定版示例，但 AGP 9.4 已发布；两者对 API 37/Compose 1.12 的支持不同。必须按项目实际组合验证，不能混用文档。

---

## 7. 当前阻塞与开放问题

### 7.1 阻塞

| ID | 阻塞 | 影响 | 解除方式 | 负责人 |
|---|---|---|---|---|
| B-01 | 无 AVD/system image/真机 | T1-015、所有 UI/设备测试无法执行 | 通过 Android Studio SDK Manager 安装 API 36/37 system image，或连接真机 | 开发/测试 |
| B-02 | 应用 ID/名称未定 | T1-001；发布配置无法最终化 | 产品确认 D-020 | 产品 |
| B-03 | minSdk 未最终确认 | 权限分支/测试范围 | 确认 D-003/D-025 | 产品+技术 |
| B-04 | 原图保留策略未定 | “重新处理”语义/存储设计 | 确认 D-014 | 产品 |
| B-05 | 目标设备清单未定 | M2/M6 验收范围 | 登记 3 档设备 | 测试 |
| B-06 | 视觉参考/原型图缺失 | “精致”无法对标 | 提供参考/设计稿，或接受一致性评审 | 设计 |
| B-07 | ~~Gradle 依赖下载未验证~~ | 已解除：T1-003 构建成功 | 记录构建警告；后续升级时重新验证 | 开发 |

### 7.2 开放决策

见 `DECISION_LOG.md` 的 `OPEN` 项：D-014（原图保留）、D-020（应用 ID/名称）、D-025（目标设备/minSdk）。  
`PROPOSED` 项不是既成事实；T1-003/T1-013/EXP-* 会验证或推翻。

---

## 8. 下一步执行计划（按顺序）

### 第 1 步：确认边界（待产品，T1-001）

- 阅读 `SPEC_REVIEW.md` 第 7 节；
- 确认 D-020（应用 ID/名称）、D-003/D-025（minSdk/目标设备）、D-014（原图保留）；
- 当前 T1-003 已使用临时 ID `com.pocketccd.prototype`，**禁止发布**，发布前必须替换。

### 第 2 步：初始化仓库与工程（已完成 2026-10-17）

- T1-002：`git init`、`.gitignore`、`.editorconfig`、`.gitattributes`、README；
- T1-003：创建 `:app` + Compose 空壳，使用 AGP 9.3.3 + Gradle 9.6.1 + JDK 21 + BOM 2026.08.00；
- `TC-ENV-002` 已 `PASS`；`TC-P1-017` 已 `PASS`；构建日志/APK 哈希见第 5 节；
- 未使用回退方案；AGP 9.2.0/8.13.2 仅保留为未来升级/回归备选。

### 第 3 步：准备测试环境（0.5–1 天，当前阻塞）

- T1-015：安装 API 36/37 system image，创建 AVD；或连接真机；
- 执行 `TC-ENV-004`，登记设备信息；
- 确认 `adb install`/`screencap` 可用。
- **在设备就绪前，T1-004/T1-005 可以先做，但 T1-006 之后的页面验收不能算完成。**

### 第 4 步：静态 UI 原型（5–7 天）

按依赖顺序：
1. T1-004 设计令牌（已完成）；
2. T1-005 状态机（已完成，12 单元测试通过）；
3. T1-006 首页（已完成静态 UI；UI/截图待设备）；
4. T1-007 下拉手势（`IN_PROGRESS`：距离阈值/跟随/转场已做；速度阈值与手势测试待补）；
5. T1-008 拍摄页（已完成静态 UI；图库按钮已移除；UI/截图/横屏待设备）；
6. T1-009 照片墙（已完成静态 UI/空态；真实 app-owned 数据待 T5）；
7. T1-010 成片页（已完成静态 UI；设备 UI 待设备）；
8. T1-011 设置页（已完成静态 UI；设备 UI 待设备）；
9. T1-012 状态矩阵（下一步）；
10. T1-013 自适应/无障碍。

### 第 5 步：验收（1 天）

- 执行 `TC-P1-001..025`（设备相关先做）；
- T1-014 截图/视觉评审；
- T1-016 性能烟雾；
- 更新 `PROJECT_STATE.md`/`TASK_BOARD.md`/`TEST_PLAN.md`；
- 打 tag `phase-1-prototype`（Git 初始化后）。

### 第 6 步：阶段 2 之前（不立即执行）

- 先做 EXP-01/EXP-02（CameraX 高分辨率/构图、Camera2 缺口）；
- 再决定 T2-003 的采集参数和是否需要 Camera2 interop；
- 在真机上验证，不用模拟器相机下结论。

---

## 9. 风险登记（当前）

| ID | 风险 | 概率 | 影响 | 缓解 | 触发信号 |
|---|---|---|---|---|---|
| R-01 | 工具链组合（AGP 9.3.3/Compose BOM 2026.08.00）首次构建失败 | 低（已通过） | 阻塞 M1 | 已用 D-004 构建通过；升级时保留 AGP 9.2.0/8.13.2 回退 | `assembleDebug` 失败 |
| R-02 | 无测试设备/AVD 导致 UI 验收无法执行 | 高 | 阻塞 M1 退出 | 立即安装 system image/借真机 | `adb devices` 空 |
| R-03 | 依赖下载受网络/代理影响 | 中 | 构建慢/失败 | 用本地缓存；配置 Gradle 代理；锁定版本 | 依赖解析超时 |
| R-04 | “CCD 风格”无法用 Android JPEG 编码器达到预期 | 高 | 核心产品差异不足 | EXP-03 评估可控编码器；明确限制 | 盲评差距集中在 JPEG 伪影 |
| R-05 | 48MP/大图导致 OOM | 中 | 崩溃/差评 | 解码降采样到 12MP；内存预算；真机压力测试 | `OutOfMemoryError`/峰值超预算 |
| R-06 | 原图删除导致“重新处理”不可用 | 中 | 功能/预期落差 | 确认 D-014；UI 明确条件 | 产品要求永久重处理 |
| R-07 | Android 16 大屏方向行为导致相机布局错乱 | 中 | 体验/兼容性 | 不锁方向；自适应；大屏测试 | 折叠屏/平板拉伸 |
| R-08 | 参考素材/品牌名称侵权 | 低–中 | 发布受阻 | EXP-08 + 法务审查；不用品牌 Logo | 商店审核/投诉 |
| R-09 | 主观验收导致返工 | 高 | 进度 | 盲评+指标+评审签字 | “感觉不对”无记录 |
| R-10 | 过早引入 Camera2/GPU 导致复杂度失控 | 中 | 进度/稳定性 | 决策门槛；先 CameraX/CPU | 无基准就重构 |
| R-11 | SDK `android-37.0` 与 `android-37.0-2` 重复目录导致后续解析/platform 选择异常 | 中 | 构建环境不稳定 | 在 SDK Manager 中整理；暂记录警告 | 构建报 wrong platform / 重复包 |
| R-12 | V2 玩具色可能太童趣/太亮，或大面积黄油黄显廉价 | 中 | 视觉返工 | 先真机看一轮；可降低黄油饱和度/缩小面积，但不退回网页布局 | 用户反馈“太童趣/刺眼” |

---

## 10. 文件地图

```text
D:\Android\pocket ccd\
├── DEVELOPMENT_SPEC.md              # 原始规范（未修改）
├── README.md                        # 项目入口
├── PROJECT_STATE.md                 # 本文件
├── DECISION_LOG.md                  # 技术决策
├── TASK_BOARD.md                    # 任务/验收/实验
├── TEST_PLAN.md                     # 测试计划/用例/证据
├── SPEC_REVIEW.md                   # 规范审查
├── research/
│   └── android-official-docs-2026-10-17.md
├── docs/
│   ├── design-tokens.md             # 设计令牌（临时值）
│   └── color-direction.md           # 玩具相机配色研究
├── .research/                       # 官方文档抓取缓存（临时，已 gitignore）
├── gradle/
│   ├── libs.versions.toml           # 版本目录
│   └── wrapper/                     # Gradle 9.6.1 wrapper
├── app/                             # Compose 空壳（可构建）
│   ├── src/main/java/com/pocketccd/prototype/
│   │   ├── MainActivity.kt
│   │   ├── navigation/              # AppDestination/AppEvent/reduce/AppViewModel
│   │   └── ui/
│   │       ├── AppRoot.kt           # 导航 + 展开/收起转场 + 3 个占位页面
│   │       ├── home/HomeScreen.kt   # 首页静态 UI + 下拉/上拉手势
│   │       ├── camera/CameraScreen.kt
│   │       ├── gallery/PhotoWallScreen.kt
│   │       ├── result/ResultScreen.kt
│   │       ├── settings/SettingsScreen.kt
│   │       ├── components/CameraShutterButton.kt
│   │       ├── components/ToyCameraBody.kt
│   │       └── theme/               # Tokens/Type/Theme
│   └── build/outputs/apk/debug/app-debug.apk
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── local.properties                 # 本机 SDK 路径（已 gitignore）
```

---

## 11. 变更记录

| 日期 | 变更 | 说明 |
|---|---|---|
| 2026-10-17 | 建立工程准备文档集 | 未创建 App 代码；未执行构建/设备测试 |
| 2026-10-17 | 修复工作区 ACL 写入问题 | 仅主机权限；不涉及项目文件内容 |
| 2026-10-17 | 初始化 Git + Compose 空壳 | `assembleDebug` 通过；APK 已生成；安装/启动未执行 |
| 2026-10-17 | 导航状态机骨架 | 5 个占位页面；`AppStateTest` 12/12；UI/旋转待设备 |
| 2026-10-17 | 设计令牌 + 首页静态 UI | `docs/design-tokens.md`；`HomeScreen` 可编译；截图/多尺寸待设备 |
| 2026-10-17 | 拍摄页静态 UI + 下拉/展开原型 | `CameraScreen`/`CameraShutterButton`；机身内 4:3 取景框；88dp 下拉阈值；截图/手势测试待设备 |
| 2026-10-17 | 玩具相机配色调整 | 燕麦/灰绿/陶土；`docs/color-direction.md`；对比度实测；真机视觉待评 |
| 2026-10-17 | 照片墙 + 上拉手势 + 相机页精简 | 首页上拉进入照片墙；拍摄页移除图库按钮；共享机身转场优化 |
| 2026-10-17 | 成片页静态 UI | 4:3 测试图；状态变体；demo 不伪造保存；设备 UI 待验 |
| 2026-10-17 | 设置页静态 UI | 保存位置/默认滤镜/质量占位/版本/隐私；Debug 诊断仅 debug |
| 2026-10-17 | V2 玩具相机材质重构 | 黄油机身/奶油面板/薄荷按键/珊瑚快门/深青 LCD；五页统一机身结构 |
| 2026-10-17 | 拍摄页红色复古前面板 | 红塑料/黑取景器/绿闪光灯/橙快门；相册区按用户要求暂缓 |
| 2026-10-17 | 首页统一红色主题 | 红色机身面板/黑镜头/绿闪光窗/橙快门/FILM/设置；与拍摄页同一套红色材质 |

---

## 12. 诚实声明

- 本文件中的 `PASS` 出现在：环境盘点（`TC-ENV-001`）、Debug/Release 构建（`TC-ENV-002`、`TC-P1-018`）、Git/忽略规则（`TC-ENV-003`）、权限检查（`TC-P1-017`）、静态无相机检查（`TC-P1-025`）、导航状态机单测（`TC-P1-026`）、设计令牌静态检查（`TC-P1-027`）、拍摄页静态约束（`TC-P1-028`）、共享转场代码路径（`TC-P1-029`）、配色对比度（`TC-P1-030`）、照片墙/成片/设置静态检查（`TC-P1-031`、`TC-P1-034`、`TC-P1-035`、`TC-P1-036`、`TC-P1-037`）。**没有任何页面在设备上运行、没有相机/图像/性能功能被实际验证。**
- 构建通过只证明“空壳能编译打包”；安装、启动、UI 测试仍被无设备阻塞。
- 官方文档的版本/日期是 2026-10-17 抓取结果；依赖版本可能已更新，升级前需再次核对。
- `PROPOSED` 决策是建议，不是既成事实；`OPEN` 项需要负责人确认。
- 未执行的测试在 `TEST_PLAN.md` 中保持 `NOT_RUN` 或 `BLOCKED`，不会因为文档完成而自动变成通过。
