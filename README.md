# Pocket CCD

一个以早期消费级数码相机成像风格为核心的 Android 相机应用。当前处于 **M1 静态 UI 原型**的准备阶段：Compose 空壳已可构建，尚未实现相机功能。

---

## 当前状态（2026-10-19）

| 项目 | 状态 |
|---|---|
| 产品/技术规范 | `DEVELOPMENT_SPEC.md` V1.0 已存在，未修改 |
| 规范审查 | 已完成，见 `SPEC_REVIEW.md` |
| 官方文档核查 | 已完成，见 `research/android-official-docs-2026-10-17.md` |
| 任务拆分 | 已完成，见 `TASK_BOARD.md` |
| 技术决策 | 已建立，见 `DECISION_LOG.md` |
| 测试计划 | 已建立，见 `TEST_PLAN.md` |
| Android 工程 | 已创建 Compose 空壳（T1-003） |
| 导航骨架 | 已实现状态机 + 五个页面；12 个单元测试通过（T1-005） |
| 设计令牌 | 已建立 `ui/theme/Tokens.kt` + `docs/design-tokens.md`（T1-004） |
| 配色/材质 | 红色外框 + 奶油内面板 + 黑镜头/黑取景器 + 绿闪光/橙快门；见 `docs/color-direction.md` |
| 机身组件 | `ToyCameraBody`/`ToyScreen`/`ToyRoundButton`；五页统一红框机身（P-019/P-023） |
| 首页/拍摄 | 单一持久 `CameraStageScreen`：下拉跟手放大红框机身，快门/闪光灯移动，镜头→取景器，FILM/设置淡出、翻转键淡入（T1-006/T1-008）；预览为静态，未接相机 |
| 照片墙 | 机身深青屏幕内的 2 列奶油照片纸 + 空态胶片头（T1-009）；真实数据待 T5 |
| 成片页 | 相机吐出的奶油相纸打印 + 状态变体 + 两个物理圆键（T1-010）；运行时 demo 不伪造保存 |
| 设置页 | 红框机身内 App 式设置：24sp 标题、分组卡片、15/12sp 行、44dp 滤镜分段控件（T1-011） |
| 下拉/上拉手势 | 下拉 88dp 进入相机、上拉 88dp 进入照片墙；共享机身转场（T1-007，`IN_PROGRESS`） |
| M8 Fotufilm | CI run `39198895398` 全绿；12MP packs + arm64 AOT + `libfotufilm_android.so` 5,874,424 B；真机已就位，**12MP 实测未执行**（缺 Kotlin 调用方） |
| 构建 | `assembleDebug` 通过（2026-10-19）；Release 旧包未重打；Release 未签名、未安装 |
| 安装/启动/真机测试 | `PJZ110` (arm64-v8a, API37) 已安装/启动/截图；下拉进入拍摄页；相机/FOT 12MP 未测 |

> 构建通过不等于 App 已验证。页面、相机、滤镜、性能均未验证；未执行的测试均标 `NOT_RUN`/`BLOCKED`。

---

## 文档索引

| 文件 | 用途 | 什么时候读 |
|---|---|---|
| [`DEVELOPMENT_SPEC.md`](DEVELOPMENT_SPEC.md) | 产品意图、技术方向、阶段规范 | 首先读，作为基线 |
| [`SPEC_REVIEW.md`](SPEC_REVIEW.md) | 规范中的矛盾、缺失、不可验证项 | 开工前；评审需求时 |
| [`DECISION_LOG.md`](DECISION_LOG.md) | 技术决策及其理由/状态 | 选型、争议、复审时 |
| [`TASK_BOARD.md`](TASK_BOARD.md) | 任务、前置、交付物、验收、测试、实验 | 每天开发前 |
| [`TEST_PLAN.md`](TEST_PLAN.md) | 测试层级、用例、证据、性能协议 | 写测试/验收时 |
| [`PROJECT_STATE.md`](PROJECT_STATE.md) | 当前进度、环境、阻塞、风险、下一步 | 每次阶段切换/接手时 |
| [`research/android-official-docs-2026-10-17.md`](research/android-official-docs-2026-10-17.md) | 官方文档版本与来源 | 升级依赖/核对事实时 |
| [`docs/design-tokens.md`](docs/design-tokens.md) | 设计令牌（临时值） | 写页面前；视觉评审时 |
| [`docs/color-direction.md`](docs/color-direction.md) | 玩具相机配色研究、对比度与选型 | 调整配色时 |
| [`docs/red-camera-research.md`](docs/red-camera-research.md) | 红色玩具相机调研、降刺激选色、系统栏/转场修正 | 打磨红色主题时 |
| [`docs/fotufilm-android-port-plan.md`](docs/fotufilm-android-port-plan.md) | Fotufilm 原生 Android 移植方案、阶段、任务、风险 | 胶卷引擎/相机链路 |
| [`docs/fotufilm-license-inventory.md`](docs/fotufilm-license-inventory.md) | Fotufilm/第三方/CC BY-SA/商标合规清单 | 引入胶片数据前 |
| [`docs/fotufilm-windows-build.md`](docs/fotufilm-windows-build.md) | Windows-only 的 WSL2 构建步骤 | 本地备选 |
| [`docs/github-actions-fotufilm.md`](docs/github-actions-fotufilm.md) | GitHub Actions 构建/触发/artifact | 开始 FOT-002 主路径 |

---

## 证据标记

本项目所有研究结论必须区分：

- `[F]` **已核实事实**：官方文档明确写出，或本机命令输出明确显示；
- `[I]` **工程推断**：由事实推导，尚未实验证明；
- `[H]` **待验证假设**：必须通过原型、基准或真机测试确认；
- `[D]` **决策**：见 `DECISION_LOG.md`，可能是 `ACCEPTED`/`PROPOSED`/`OPEN`。

**规则：未实际执行的构建、测试、真机拍摄，不得标记为“通过”。**

---

## 当前建议基线（不是最终决定）

| 项 | 建议 |
|---|---|
| 语言/UI | Kotlin + Jetpack Compose |
| 构建 | AGP 9.3.3 + Gradle 9.6.1 + JDK 21（Java target 17） |
| SDK | compileSdk 37 / targetSdk 36 / minSdk 29（待产品确认） |
| Compose | BOM 2026.08.00（已构建验证 → Compose 1.12.0 / Material3 1.4.0） |
| 相机 | CameraX 1.6.2（阶段 2；Camera2 仅按缺口接入） |
| 存储 | MediaStore 成片 + Room 元数据 + 应用私有临时文件 |
| 处理 | CPU 参考实现优先；GPU 仅在基准证明必要时引入 |
| 上传/付费服务 | 不引入 |

详见 `DECISION_LOG.md` D-003 / D-004 / D-005 / D-008 / D-009。

---

## 本地构建

前置：

- Android SDK：`D:\software\Android\Sdk`（复制 `local.properties.example` 为 `local.properties` 并修改路径；该文件不提交）
- JDK：`D:\software\Android\Android Studio\jbr`（JDK 21）
- Gradle：项目自带 wrapper（9.6.1）

```powershell
$env:JAVA_HOME='D:\software\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。  
当前单测：`AppStateTest` 12/12（状态机转换、非法转换、重复事件）。

**国内网络：** `settings.gradle.kts` 优先使用阿里云 Maven 镜像（gradle-plugin/google/public），官方 Google/Maven Central 作为兜底；Gradle wrapper 使用腾讯云 `gradle-9.6.1-bin.zip`。如果某个依赖仍解析失败，再按错误信息单独调整，不要一次性堆叠过多镜像。

> 当前构建使用临时包名 `com.pocketccd.prototype`，禁止发布。

## 下一步

1. 阅读 `SPEC_REVIEW.md` 第 7 节，确认应用 ID、minSdk、目标设备、原图保留策略；
2. T1-002/T1-003/T1-004/T1-005/T1-006/T1-008/T1-009/T1-010/T1-011 已完成；T1-007 仍需速度阈值与手势测试；
3. 下一步 T1-012 状态矩阵 / T1-013 自适应；准备 AVD/真机（T1-015）做 UI/截图/多尺寸验收；
4. 新方向 M8：Fotufilm Android 原生移植，主路径走 GitHub Actions Linux，见 `docs/github-actions-fotufilm.md`；先做 FOT-002/003 Phase 0；
5. 执行 `TEST_PLAN.md` 的 `TC-P1-*` 并记录证据。

---

## 边界

- 当前**不**实现完整相机、图像处理、图库持久化；
- 当前**不**引入付费服务、账号、云同步、分析 SDK；
- 当前**不**上传任何照片或个人数据；
- 不使用真实相机品牌 Logo/机型名作为产品名；
- 不把未验证的 CCD 风格结论写成事实。
