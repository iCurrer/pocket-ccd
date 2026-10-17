# Pocket CCD

一个以早期消费级数码相机成像风格为核心的 Android 相机应用。当前处于**开发前工程准备**阶段，尚未创建 Android 工程，也未实现相机功能。

---

## 当前状态（2026-10-17）

| 项目 | 状态 |
|---|---|
| 产品/技术规范 | `DEVELOPMENT_SPEC.md` V1.0 已存在，未修改 |
| 规范审查 | 已完成，见 `SPEC_REVIEW.md` |
| 官方文档核查 | 已完成，见 `research/android-official-docs-2026-10-17.md` |
| 任务拆分 | 已完成，见 `TASK_BOARD.md` |
| 技术决策 | 已建立，见 `DECISION_LOG.md` |
| 测试计划 | 已建立，见 `TEST_PLAN.md` |
| Android 工程 | **尚未创建**（M1/T1-003） |
| 构建/真机测试 | **尚未执行** |

> 没有任何 App 功能被实际验证。文档中的 `PROPOSED` 是建议，`OPEN` 是待确认；未执行的测试均标 `NOT_RUN`/`BLOCKED`。

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
| Compose | BOM 2026.08.00（候选，待构建验证） |
| 相机 | CameraX 1.6.2（阶段 2；Camera2 仅按缺口接入） |
| 存储 | MediaStore 成片 + Room 元数据 + 应用私有临时文件 |
| 处理 | CPU 参考实现优先；GPU 仅在基准证明必要时引入 |
| 上传/付费服务 | 不引入 |

详见 `DECISION_LOG.md` D-003 / D-004 / D-005 / D-008 / D-009。

---

## 下一步

1. 阅读 `SPEC_REVIEW.md` 第 7 节，确认应用 ID、minSdk、目标设备、原图保留策略；
2. 执行 `TASK_BOARD.md` 的 T1-002/T1-003，建立可构建空工程；
3. 准备 AVD/真机（T1-015），否则 UI 验收无法执行；
4. 按 T1-004 → T1-013 完成静态 UI 原型；
5. 执行 `TEST_PLAN.md` 的 `TC-P1-*` 并记录证据。

---

## 边界

- 当前**不**实现完整相机、图像处理、图库持久化；
- 当前**不**引入付费服务、账号、云同步、分析 SDK；
- 当前**不**上传任何照片或个人数据；
- 不使用真实相机品牌 Logo/机型名作为产品名；
- 不把未验证的 CCD 风格结论写成事实。
