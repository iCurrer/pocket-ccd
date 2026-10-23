# Pocket CCD — 视觉方向 C「白纸档案」实现规格

**日期：** 2026-10-23 ｜ **状态：** `ACCEPTED`（用户选择 C）｜ **决策：** D-063
**基线：** `main` @ `1ed3ff1`（v0.4.1）｜ **实现层：** 原生 Kotlin + Jetpack Compose，零新依赖
**取代关系：** 本方向对成片/照片墙/胶卷库/设置四个页面取代 D-038 的"全 App 暗色"；
取景页保留暗色，但改为"黑闸门 + 纸白刻字"，不再用香槟金。

---

## 0. 一句话

**在黑暗里拍，在纸上归档。** 取景器是一只不透光的黑闸门，只刻纸白线；按下快门后，
照片变成一张相纸、一张接触印相、一张索引卡。彩色照片永远落在中性纸面上，
所以胶片颜色是被纸衬出来的，不是被暖色滤出来的。

- 不做主题切换：不是"亮色模式"，是两种材质（闸门 / 相纸）按页面分工。
- 不新增首页、TabBar、相机逻辑、虚构数据、第三方 UI 库、字体资源。
- 相机启动仍直接全屏取景；预览永远原色；胶片只作用于成片。

---

## 1. 色彩令牌

### 1.1 纸面（成片 / 照片墙 / 胶卷库 / 设置 / 声明）

| 令牌 | 值 | 用途 |
|---|---|---|
| `appBg` | `#ECEAE4` | 相纸底（冷中性纸，不是米黄） |
| `sheetBg` | `#F6F4EE` | 抬起纸面 / 浮层 / 查看器 caption 纸 |
| `textPrimary` | `#16181A` | 墨色主文字 |
| `textSecondary` | `#4A4E52` | 说明文字（纸面上 ≈ 7.5:1） |
| `textTertiary` | `#61676C` | 标签 / 单位（≈ 5:1，不得更低） |
| `accent` | `#4F6B7A` | 石板蓝：选中态、索引页签、链接 |
| `onAccent` | `#FFFFFF` | 石板蓝上的文字 |
| `hairline` | `#16181A` @ 12% | 规则线 / 分隔线 |
| `hairlineStrong` | `#16181A` @ 20% | 输入框 / 焦点线 |
| `controlFill` | `#16181A` @ 5% | 次级按钮底 / 按下覆盖 |
| `chipFill` | `#16181A` @ 8% | chip / 索引页签底 |
| `error` | `#B23A2A` | 仅错误 |
| `windowBg` | `#0A0B0C` | 查看器黑底灯箱 |
| 金属类令牌 | 纸色占位 | 纸面页不使用金属渐变；字段保留只为 spec 完整 |

规则：纸面页**没有阴影**（`shadow` 一律 0），层级只靠 1px 规则线、纸色明度差
（`#F6F4EE` vs `#ECEAE4`）与留白。禁止渐变、玻璃、发光、彩色边框。

### 1.2 黑闸门（取景页）

| 令牌 | 值 | 用途 |
|---|---|---|
| `appBg` | `#0A0B0C` | 闸门底 |
| `viewfinderBg` | `#0A0B0C` | 取景窗兜底 |
| `textPrimary` | `#EDEAE3` | 纸白刻字 |
| `textSecondary` | `#EDEAE3` @ 70% | 次级刻字 |
| `textTertiary` | `#EDEAE3` @ 52% | 标签 |
| `accent` | `#EDEAE3` | 激活态（白 12% 底 + 纸白字） |
| `onAccent` | `#16181A` | 白底上的深字 |
| `hairline` / `hairlineStrong` | 白 10% / 16% | 刻线 / 分隔 |
| `controlFill` | 白 5% | 圆形控件底 / 按下覆盖 |
| `shutterRing` | 纸白金属的细微明暗（`#EDEAE3→#8B8880→#EDEAE3→#5F5C56→#D9D6CC`） | 快门环：保留物理感，但去金色 |
| `shutterFace` | 暖白纸面 `#F6F4EE→#E8E4DA→#D6D1C4` | 快门面 |
| `shutterDot` | `#16181A` | 快门红点改成**墨点**（红不再承担强调） |
| `error` | `#B84A3A` | 仅错误 |

规则：取景页**无金色、无香槟金、无红色强调**；所有刻线 1px；机身底盖为纯黑同族、
不出现"灰塑料板"；除快门/凹槽外不做金属渐变。

---

## 2. 字体与字阶

系统字体，**不打包字体资源**：

- 纸面页标题用 `FontFamily.Serif`（系统衬线，Noto Serif），正文 `SansSerif`，标签/数字 `Monospace`。
- 取景页全部 `SansSerif` + `Monospace`，不做衬线。
- 字号下限 11sp；`microPrint 9sp` 只允许出现在胶卷筒/包装仿真印刷上，且不承载信息。
- 禁止裸 `fontSize`（`NoBareFontSizeTest` 会失败）；一律取 `spec.type.*`。
- 数字用 `fontFeatureSettings = "tnum"`（等宽数字），避免计数跳动。

| 令牌 | 值 | 用途 |
|---|---|---|
| `display` | 32sp / 34sp 行高 | 纸面页唯一大标题（可为空） |
| `sheetTitle` | 24sp / 30sp 行高 | 页面标题（Serif） |
| `optionTitle` | 16sp | 行标题 / 卡片标题 / 印相卷名 |
| `value` | 14sp | 右侧值 / 参数值 / 计数 |
| `body` | 14sp / 22sp 行高 | 正文、说明 |
| `label` | 12sp / 16sp 行高 | 标签、页签、按钮 |
| `mono` | 12sp / 16sp 行高 | 元数据、帧号、日期、EXIF |
| `monoSmall` | 11sp | 密集 mono 标签 |
| `rail` | 13sp | 取景页档位轨 |
| `counterBig` | 30sp / 32sp 行高 | EXP 计数 |
| `microPrint` | 9sp | 仅装饰 |

---

## 3. 间距、圆角、边框、图标

- 间距 4dp 基：4 / 8 / 12 / 16 / 24 / 32 / 48。纸面页内容边距 **24dp**；
  取景页保留 v0.4.1 的 12/16/20 计算。
- 圆角：纸面页 **0**（面板、卡片、图片最多 2dp）；取景页保留 16dp 取景窗 / 12dp 实体键；
  圆形控件仍为 full。
- 边框：纸面页 1px 墨 12%；取景页 1px 白 10–16%。禁止渐变描边、外发光。
- 图标：继续用 `FilmIcons` 的 10 个手绘图标；24 网格、1.5–1.6dp、单色。
  纸面页墨色 `textSecondary`/`textPrimary`，取景页纸白。尺寸 16–18dp。
- 触控目标：全部 ≥48dp；视觉可以小。EV 刻度、变焦档位必须用 `minimumInteractiveComponentSize`
  或外扩热区补到 48dp。

---

## 4. 组件配方（Compose）

| 组件 | 规格 |
|---|---|
| `PaperPageHeader` | 高 64dp；左/右 24dp；居中或左对齐的 Serif `sheetTitle`；下方 12sp mono 副题；底部 1px hairline；返回键 48dp 热区 / 16dp 图标 |
| `PaperRule` | 1px `hairline`，左右 24dp |
| `IndexTab` | 文字 12sp mono；选中 = 2px 墨色下划线 + `textPrimary`，未选中 = `textTertiary`；高 44dp、热区 48dp |
| `PrintFrame` | 图片外 6dp 纸白边 + 1px `hairline`；圆角 2dp；**无阴影**；图片按真实宽高比，`ContentScale.Fit` |
| `ContactSheetCell` | 3 列；列间距 12dp；图下方 11sp mono 帧号（`05`）；选中/收藏角标用石板蓝；无卡片背景 |
| `ColophonRow` | 高 ≥56dp；左 14sp 正文 / 右 14sp mono 值；底部 1px hairline；点击行整行可点 |
| `InkButton` | 主 = 墨底纸白字 14sp；次 = 1px 墨描边透明底；幽灵 = 下划线文字；高 48dp；圆角 2dp |
| `PaperSwitch` | 40×22 墨轨 + 纸白滑块；整行 48dp 可点；开关状态有文字值（开/关） |
| `PaperSheet` | 从底部上移 16dp + 淡入 200ms；圆角 0；顶部 1px hairline；纸色 `sheetBg`；遮罩墨 30% |
| `Lightbox` | 查看器：黑底 `windowBg` + 真实照片 + 底部纸面 caption（纸色条 + 元数据） |
| `PaperToast` | 纸底墨字胶囊（或下划线提示）；2200ms；`liveRegion` |

**8 态**：Default / Pressed / Focused / Selected / Disabled / Loading / Success / Error。
触屏没有 hover；焦点态给 TalkBack/键盘用 2px 石板蓝描边。禁用态 38% 不透明度、不响应。
成功/失败只用文案 + 图标 + 颜色三重表达，不能只靠颜色。

---

## 5. 逐页布局

### 5.1 取景页（黑闸门）— `FilmCaptureScreen.kt` / `FilmControls.kt` / `FilmInteractions.kt` / `FilmViewfinder.kt`

保留 v0.4.1 三段式几何，不改相机绑定：

1. 顶部：胶卷名改为**下划线式 tab**（12sp mono / 米白，底部 1px 白 20% 线；热区 48dp），
   不再用金色圆点胶囊。
2. 取景窗：16dp 圆角；四角刻线 16dp、1px、纸白 62%；准星 20dp；九宫格白 15%；
   画面外不再有任何金/红色。
3. 工具条 52dp：闪光 / 网格（左），变焦档位（中），EV / 设置（右）；
   激活态 = 白 12% 圆底 + 纸白图标/文字；未激活 = 白 46%。
4. 底盖：纯黑同族、无渐变、顶部 1px 白 10% 缝；EXP 计数 `00 / 36` 30sp mono 纸白；
   相册 / 快门 / 换卷：副键 76×50 墨黑实体键 + 1px 白 12% 边；快门 68（凹槽 80）
   纸白环 + 暖白面 + 墨点。
5. 36 格进度：2dp，已曝光纸白、未曝光白 12%。
6. 画面外文字 ≤5 条；`blockReason` 只在非空时出现。

**禁止**：给预览加色调、假参数、假快门、金色回潮、红点回潮。

### 5.2 成片页（相纸）— `ResultScreen.kt`

1. 纸面整页；顶部 64dp 页头：左返回（48dp），中/左 Serif「成片」+ mono 副题 `GOLD 200 · FRAME 05 / 36`
   （数据来自真实 `stockLabel` / `frameIndex` / `rollCapacity`）。
2. 主视觉：`PrintFrame`，图宽 88% 容器、6dp 纸白边 + 1px 墨线；下方 16sp Serif 卷名。
3. 元数据：三行 colophon（DATE / EXPOSURE / FILM），值为真实 EXIF；
   缺失时显示 `—`。不显示 ISO/光圈/快门 unless 真实 EXIF 已写入（当前只有真实四项）。
4. 显影态：纸面 + 打字机式状态：`显影中` / `写入中` + `已等待 03s`（真实墙钟）；
   **无进度百分比、无扫描光带、无假 LAB/订单**。阶段切换用 200ms 文字淡入，不表演。
5. 失败态：纸面错误说明 + 「重试」；空态（demo）只写"还没有拍过"+「回到取景」。
6. 操作：底部 `InkButton`，主操作在右（继续拍摄 / 查看成片 / 重试），次操作在左（重拍 / 回相册）。
7. 保留 `ResultStatus` 三态语义与失败重试逻辑，不改 `CaptureState`。

### 5.3 照片墙（接触印相）— `PhotoWallScreen.kt` / `PhotoViewer.kt`

1. 纸面页；页头：Serif「胶片相册」+ mono `ARCHIVE`；右侧关闭/返回。
2. 筛选改为**索引页签**（`全部` / `★ 收藏` / 每个真实卷名），下划线选中；
   保留现有 `GalleryLogic` 的过滤/分组/空组隐藏逻辑，不改。
3. 分组头：`ROLL 02 / GOLD 200 / 10.20 / 05 / 36`（真实卷名、日期、张数）；
   下方 1px hairline。
4. 网格：3 列、列间距 12dp、行间距 20dp；每格 `ContactSheetCell`：
   图 + 图下 11sp mono 帧号（`01`…）；收藏用石板蓝小方块/星，不做金色。
5. 空态：纸面文案 + 明确动作（去取景 / 去胶卷库），不得只写"空空如也"。
6. 查看器：黑底 `windowBg` 灯箱 + `HorizontalPager` 首尾循环（保留）。
   顶部：返回 + `03 / 12` mono；底部纸面 caption：Serif 卷名 + mono 日期/帧号；
   `收藏` / `关闭` 用纸面 `InkButton`。保留收藏持久化与 BackHandler。
7. 缩略图/大图解码必须离开主线程（`produceState` + `Dispatchers.IO`），当前主线程 decode 要修。

### 5.4 胶卷库（索引卡）— `FilmLibraryScreen.kt`

1. 纸面页；页头：Serif「胶卷库」+ mono `FILM STOCK` + 右侧相册入口。
2. 「已装卷」从浅金属面板改为**纸面索引卡**：
   - 顶部黑色 `CURRENT` 黑带（纸白字 12sp mono）+ `READY` / `FULL` 状态；
   - 左：胶卷筒 Canvas（保留，标签改纸色）；右：卷名 16sp、尺寸/画幅 12sp mono、
     `剩余 31 张` 14sp、`05 / 36` 12sp mono、36 帧点阵列（已曝光墨、未曝光墨 15%）；
   - 操作：`继续拍摄`（墨底纸白）、`开新卷`（1px 墨描边）。
   - **不显示** `.fswp` 文件名、文件体积、内部尺寸串（D-050）。
3. 全部胶卷：索引卡列表，每卡 84×108 真实画格（无照片时画胶卷筒）、卷名、张数、
   上次拍摄日期、`装卷 / 已装卷`；卡间距 12dp，1px hairline 分隔，无阴影。
4. 装卷：保留 900ms 呼吸 + 已装卷卡淡入/上滑；装完留在库内，由「继续拍摄」回取景。
5. 空态：纸面 + 「重新扫描」按钮 + 真实原因。

### 5.5 设置 / 声明（版权页）— `SettingsScreen.kt` / `LicensesScreen.kt`

1. 纸面页；页头：Serif「设置」+ mono `SETTINGS`，关闭键在右。
2. 分组用 `01 保存与输出` / `02 关于` 的编号 + Serif 小标题 + hairline；
   行 = `ColophonRow`（左标题 14sp、副题 13sp `textSecondary`、右值 14sp mono）；
   开关 = `PaperSwitch`，整行 48dp 可点。
3. 修正事实文案：保存位置副标题不能写"仅本应用"——实际同时写系统相册
   （`Pictures/Pocket CCD/`），改为"本应用 + 系统相册"。
4. DEBUG 诊断保留，入口只在 debug；对话框用纸色 M3 light 主题。
5. 声明页：编号 01–04 + Serif 标题 + 1px 线 + 14/22sp 正文；
   合规文字一字不改（继续 `stringResource`）；**补 BackHandler 关闭覆盖层**。
6. 无卡片、无描边块、无金属。

---

## 6. 动效

方向 C 是"安静档案"：不做弹跳表演，反馈快、显影稳。

| 交互 | 规格 |
|---|---|
| 按压 | scale 0.97，90ms tween（不用弹簧）；按下覆盖 5% 墨（纸面）/ 白（闸门）；KEYBOARD_TAP |
| 状态色 | 120ms `animateColorAsState` |
| 快门闪白 | 90ms 0.85→0，全屏最上层（保留现有） |
| 对焦环 | 4 角细括号 300ms 单次收束；AF / AF LOCK / AF FAIL 真实三态；2.2s 后淡出 |
| 变焦 | 文字档位 120ms 下划线/颜色；点击真实 `setZoomRatio`，Toast 实际倍率 |
| 换卷 | 长按 600ms + 触觉警告 + 整页纸面确认（显示本卷真实张数）；禁止直接归零 |
| 显影 | 只做阶段文字 + 真实秒数；阶段切换 200ms 淡入；无扫描带、无循环脉冲 |
| 页面转场 | fade 160ms + 8dp 上移；纸面页之间 120ms 交叉淡化 |
| 装卷 | 900ms 呼吸 + 卡片 240ms 淡入/180ms 淡出（保留） |
| 降级动效 | `Settings.Global.ANIMATOR_DURATION_SCALE == 0` 时关闭循环/位移，只保留淡入 |

---

## 7. 验收

- [ ] `:app:assembleDebug` + `:app:testDebugUnitTest` 全绿（当前 61 例）
- [ ] `NoBareFontSizeTest` / `InteractiveIconLabelsTest` 通过；无裸 `fontSize`
- [ ] 所有可点控件 ≥48dp；纸面文字对比度 ≥4.5:1（`textTertiary` 不低于 `#61676C`）
- [ ] 无金色/香槟金/红点残留；无阴影、无渐变、无玻璃出现在纸面页
- [ ] 无虚构数据：不新增 ISO / 光圈 / 快门 / 冲洗百分比 / 订单 / 卷容量
- [ ] 取景页仍是全屏、原色、可直接拍；相机/冲洗/落盘/EXIF/卷计数逻辑零改动
- [ ] 真机截图（PJZ110）四页归档；**没有真机图就不算视觉验收通过**

---

## 8. 首版实现记录（v0.5.0，2026-10-23）

| 项 | 结果 |
|---|---|
| 主题 | `FilmGateSpec`（取景）+ `FilmPaperSpec`（四页）+ `FilmMotion` / `FilmType` 字体族与行高 / 纸面 metrics |
| 组件 | `PaperComponents.kt`：页头、规则线、编号小节、索引页签、主次按钮、开关、空态 |
| 主题接线 | AppRoot 按目的地切 spec；M3 亮/暗兜底；系统栏图标随纸面/闸门/灯箱切换 |
| 取景页 | 下划线胶卷 tab、白 12% 激活、纯黑底盖、纸白环 + 墨点、Toast liveRegion、顶栏 48dp |
| 成片页 | 纸面 + PrintFrame（88% / 6dp 纸边 / 1px 墨线）、真实 EXIF 三行、打字机显影、IO 解码 |
| 照片墙 | 接触印相 3 列 + 帧号 + 索引页签；查看器黑底灯箱铺满系统栏 + 纸面 caption |
| 胶卷库 | 索引卡 + CURRENT 黑带；删内部参数；保留胶卷筒/36 帧点阵/900ms 装卷 |
| 设置/声明 | 版权页；声明 BackHandler；修正保存位置文案 |
| 验证 | `assembleDebug` + 61/61 单测；**真机未验** |
| 已知偏差 | 帧号为组内曝光顺序；卷容量 36 两处常量；相册空态返回来源页；无布尔设置故未上开关；返回图标用 X；200% 字体/横屏/多机型未验 |

> 真机验收前，本文件的状态是「代码完成」，不是「视觉通过」。四页真机截图归档后再改这句话。
