# Pocket CCD — 项目状态

**快照时间：** 2026-10-17 17:51（Asia/Shanghai）  
**当前阶段：** 开发前工程准备（M0 完成，M1 待启动）  
**规范版本：** `DEVELOPMENT_SPEC.md` V1.0  
**工作区：** `D:\Android\pocket ccd`  
**Git：** 尚未初始化  
**App 代码：** 无  
**已执行 App 构建/测试：** 无

---

## 1. 一句话状态

规范已读完并完成工程审查；已建立任务板、决策记录、测试计划和官方文档研究记录。**尚未创建 Android 工程、尚未构建 APK、尚未运行任何相机/图像/设备测试。** 下一阶段是 M1：项目初始化 + 静态 UI 原型。

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
| `.research/` | 官方文档抓取缓存（Markdown 快照） | 临时研究材料，不进入发布包；可保留供复查 |

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

**注意：** 以上 `DONE` 指文档交付物存在；不是 App 功能验证。P-004 的“核查”指查阅官方页面并记录版本/来源，不是对依赖组合的构建验证。

---

## 4. 当前未完成 / 未验证

| 事项 | 状态 | 说明 |
|---|---|---|
| Android 工程初始化 | `NOT_STARTED` | T1-002/T1-003 |
| Compose 主题/设计令牌 | `NOT_STARTED` | T1-004 |
| 页面状态机/导航 | `NOT_STARTED` | T1-005 |
| 五个静态页面 | `NOT_STARTED` | T1-006..T1-011 |
| 下拉手势/转场 | `NOT_STARTED` | T1-007 |
| 自适应/无障碍 | `NOT_STARTED` | T1-013 |
| 截图基线/视觉评审 | `NOT_STARTED` | T1-014 |
| AVD/真机 | `BLOCKED` | 无 AVD、无 system image、无设备 |
| Gradle 构建验证 | `NOT_RUN` | 无工程；工具链版本未实测 |
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
| Git | 工作区不是仓库 |

**工作区权限说明：** 本次开始时，DSH 的 Windows 沙箱无法为工作区授予写权限（`SetNamedSecurityInfoW ... grantWrite`）。已按 DSH 技能运行一次诊断修复，为当前用户补充了工作区目录的完全控制条目；恢复脚本与报告位于 `D:\Android\pocket-ccd-acl-report`。文件内容和所有者未被修改。`[F]`

---

## 6. 官方文档核查结论（摘要）

完整来源、版本/日期见 [`research/android-official-docs-2026-10-17.md`](research/android-official-docs-2026-10-17.md)。

| 主题 | 结论 | 对本项目的影响 |
|---|---|---|
| API level | Android 17 = 37；Android 16 = 36；Android 15 = 35 | compileSdk 37 可用；targetSdk 36 满足 Play 2026 要求 |
| Play target | 2026-08-31 起新应用/更新 target 36+ | 不能再用 target 35 发布 |
| Compose | 1.12.1 / Material3 1.4.0（2026-10-07）；BOM 最新 2026.09.00 | Compose 1.12 要求 compileSdk 37 + AGP 9.2+ |
| AGP | 9.4 最高 API 37 / Gradle 9.6.0；8.13.2 最高 API 36 / Gradle 8.13 | 本机缓存 AGP 9.3.3，建议作为候选 |
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
| B-07 | Gradle 依赖下载未验证 | 首构建可能失败 | T1-003 实测；必要时配置代理 | 开发 |

### 7.2 开放决策

见 `DECISION_LOG.md` 的 `OPEN` 项：D-014（原图保留）、D-020（应用 ID/名称）、D-025（目标设备/minSdk）。  
`PROPOSED` 项不是既成事实；T1-003/T1-013/EXP-* 会验证或推翻。

---

## 8. 下一步执行计划（按顺序）

### 第 1 步：确认边界（0.5 天）

- 阅读 `SPEC_REVIEW.md` 第 7 节；
- 确认 D-020（应用 ID/名称）、D-003/D-025（minSdk/目标设备）、D-014（原图保留）；
- 若无最终值，允许 T1-003 使用临时 ID，但标注“禁止发布”。

### 第 2 步：初始化仓库与工程（1–1.5 天）

- T1-002：`git init`、`.gitignore`、`.editorconfig`、README；
- T1-003：创建 `:app` + Compose 空工程，按 D-004 候选工具链构建；
- 执行 `TC-ENV-002`，记录构建日志/APK 路径；
- 若 AGP 9.3.3 组合失败：按 D-004 回退到 AGP 9.2.0/BOM 2026.06.01，仍失败再评估 AGP 8.13.2/Compose 1.11.4。

### 第 3 步：准备测试环境（0.5–1 天）

- T1-015：安装 API 36/37 system image，创建 AVD；或连接真机；
- 执行 `TC-ENV-004`，登记设备信息；
- 确认 `adb install`/`screencap` 可用。

### 第 4 步：静态 UI 原型（5–7 天）

按依赖顺序：
1. T1-004 设计令牌；
2. T1-005 状态机；
3. T1-006 首页；
4. T1-007 下拉手势；
5. T1-008 拍摄页；
6. T1-009 图库；
7. T1-010 成片页；
8. T1-011 设置页；
9. T1-012 状态矩阵；
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
| R-01 | 工具链组合（AGP 9.3.3/Compose BOM 2026.08.00）首次构建失败 | 中 | 阻塞 M1 | 保留 AGP 9.2.0/8.13.2 回退；记录错误 | `assembleDebug` 失败 |
| R-02 | 无测试设备/AVD 导致 UI 验收无法执行 | 高 | 阻塞 M1 退出 | 立即安装 system image/借真机 | `adb devices` 空 |
| R-03 | 依赖下载受网络/代理影响 | 中 | 构建慢/失败 | 用本地缓存；配置 Gradle 代理；锁定版本 | 依赖解析超时 |
| R-04 | “CCD 风格”无法用 Android JPEG 编码器达到预期 | 高 | 核心产品差异不足 | EXP-03 评估可控编码器；明确限制 | 盲评差距集中在 JPEG 伪影 |
| R-05 | 48MP/大图导致 OOM | 中 | 崩溃/差评 | 解码降采样到 12MP；内存预算；真机压力测试 | `OutOfMemoryError`/峰值超预算 |
| R-06 | 原图删除导致“重新处理”不可用 | 中 | 功能/预期落差 | 确认 D-014；UI 明确条件 | 产品要求永久重处理 |
| R-07 | Android 16 大屏方向行为导致相机布局错乱 | 中 | 体验/兼容性 | 不锁方向；自适应；大屏测试 | 折叠屏/平板拉伸 |
| R-08 | 参考素材/品牌名称侵权 | 低–中 | 发布受阻 | EXP-08 + 法务审查；不用品牌 Logo | 商店审核/投诉 |
| R-09 | 主观验收导致返工 | 高 | 进度 | 盲评+指标+评审签字 | “感觉不对”无记录 |
| R-10 | 过早引入 Camera2/GPU 导致复杂度失控 | 中 | 进度/稳定性 | 决策门槛；先 CameraX/CPU | 无基准就重构 |

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
├── .research/                       # 官方文档抓取缓存（临时）
└── app/                             # 尚未创建（T1-003）
```

---

## 11. 变更记录

| 日期 | 变更 | 说明 |
|---|---|---|
| 2026-10-17 | 建立工程准备文档集 | 未创建 App 代码；未执行构建/设备测试 |
| 2026-10-17 | 修复工作区 ACL 写入问题 | 仅主机权限；不涉及项目文件内容 |

---

## 12. 诚实声明

- 本文件中的 `PASS` 仅出现在环境盘点（`TC-ENV-001`）；没有任何 App 功能测试通过。
- 官方文档的版本/日期是 2026-10-17 抓取结果；依赖版本可能已更新，T1-003 必须再次核对。
- `PROPOSED` 决策是建议，不是既成事实；`OPEN` 项需要负责人确认。
- 未执行的测试在 `TEST_PLAN.md` 中保持 `NOT_RUN` 或 `BLOCKED`，不会因为文档完成而自动变成通过。
