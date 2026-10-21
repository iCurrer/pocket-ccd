# 变更规格：UI 信息密度与字号地板

**状态：** 待实施 ｜ **写给：** 实现方（开发 AI）｜ **日期：** 2026-10-21
**依据：** [`2026-10-20-typography-and-density-audit.md`](2026-10-20-typography-and-density-audit.md)（量化数据都在那份里，本文只给执行指令）
**基线：** `main` @ `cc78d17`（本单由 `21697ee` 的调研产出，行号已逐条核对仍成立），`assembleDebug` + `testDebugUnitTest` **28/28** 绿

---

## 0. 一句话目标

**把屏幕上承载信息的文字从「25 处 6.5–9.5sp」抬到「最低 11sp」，并把取景页 10 条 chrome 文字砍到 5 条以内。** 不改设计语言、不换配色、不动业务逻辑。

## 1. 动工前需要产品拍板的 4 件事

若产品没回复，**按下列默认值执行**，不要停工等待：

| # | 问题 | 默认决策 |
|---|---|---|
| 1 | 取景页的 `LIVE` / `12.6MP` / `4:3` 三个 chip 是否可删（它们对应 D-040「chip 只写能读到的值」） | **删**。D-040 解决的是"不许写假的 f/2.8"，本单解决的是"真实但无用"；需补一条决策记录（见 §7） |
| 2 | 计数保留 `1 / 36 EXP` 还是 `余 N 张` | **保留 `1 / 36 EXP`**，删 `余 N 张`（胶片计数器的隐喻更重要，且与 36 格刻度同源） |
| 3 | 成片页 `9107ms · 24 strips` 删掉还是挪走 | **从成片页删除**，不挪（设置页已有「开发者」弹窗，需要时再加，不在本单范围） |
| 4 | 变焦档位下方的等效焦距（`15mm`，现 6.5sp） | **抬到 11sp**；若产品对 0.6× 档另有结论（隐藏 <1×），随该结论一起处理，本单只改字号 |

## 2. 硬约束（违反即返工）

1. **不许低于 11sp 承载信息。** 唯一例外是"印刷在胶卷筒/包装上的仿真字"（`FilmCanister` 内部标签），可用 9sp，且必须带注释 `// micro-print: 装饰，不承载信息`。
2. **文字颜色只能用 `textPrimary` / `textSecondary` / `textTertiary` / `accent` / `onAccent` / `error`。** 现有 `inkMuted` / `inkFaint` / `inkGhost` 只允许用于图标 tint 与分隔线。

   **两处例外**——这两处换成上面那套会直接不可读，不要"统一"：

   - **`FilmLibraryScreen` 的 `Metal` 反色浅底面板**（≥300 行的「已装卷」卡）。它是全页唯一浅底，
     文字用页面私有的 `Metal.inkBody` / `Metal.inkMono` / `Metal.inkFaint`（深色）。
     `textSecondary` 是**白 60%**，落在浅金属底上等于消失。**只抬字号，颜色继续用 `Metal.*`**，
     并保证与面板底色（`#EDEFF1`→`#8D9298` 渐变，按最浅处算）对比度 ≥4.5:1。
   - **取景器内的文字**（`AF LOCK` / 卷名缩写 / 倍率）。它压在真实画面上，不适用"合成到 appBg"的算法。
     统一用 `ink`(75%) 或 `inkStrong`(90%)，**不要**用 `textTertiary`(50%) —— 白天亮画面下 50% 白等于没有。
     逐处截图确认亮画面下可读。
3. **不许为了容纳放大后的文字而缩小字号。** 溢出处理优先级见 §5。
4. **不改**：配色主题、导航状态机、相机/冲洗/落盘/对账任何逻辑、图标绘制、48dp 触摸目标、文件结构、依赖。
5. **不顺手修**上一轮报告里的 P0（对账删照片、快门回调无超时等）——那是另一张单，混在一起无法 review。
6. 每个 Phase 单独可编译、可截图，按顺序提交。

---

## 3. Phase 1：字号令牌与文字颜色（其他 Phase 的前提）

### 3.1 `app/src/main/java/com/pocketccd/prototype/ui/film/FilmTheme.kt`

**改 `FilmType`（第 70-78 行）为：**

```kotlin
@Immutable
data class FilmType(
    val sheetTitle: TextUnit = 16.sp,   // 页面标题（不变）
    val optionTitle: TextUnit = 15.sp,  // 行标题 / 强调信息：13 → 15
    val value: TextUnit = 14.sp,        // 新增：参数值、设置项右侧值、计数
    val body: TextUnit = 13.sp,         // 说明文案：12 → 13
    val mono: TextUnit = 11.sp,         // 等宽标签：10 → 11
    val monoSmall: TextUnit = 11.sp,    // 等宽小标签：9 → 11（不得低于 11）
    val label: TextUnit = 11.sp,        // 通用标签：10 → 11
    /** 只允许用于胶卷筒/包装上的仿真印刷字，不得承载信息。 */
    val microPrint: TextUnit = 9.sp,
)
```

**给 `FilmPalette` 增加两个语义文字色（第 21-46 行的 data class 与第 88-133 行的实例都要加）：**

```kotlin
/** 说明文字（≥13sp）。白 60% ≈ 7.2:1 */
val textSecondary: Color,
/** 标签/单位（≥11sp）。白 50% ≈ 5.3:1 */
val textTertiary: Color,
```

实例值：

```kotlin
textSecondary = Color(0xFFFFFFFF).copy(alpha = 0.60f),
textTertiary = Color(0xFFFFFFFF).copy(alpha = 0.50f),
```

**在 `FilmType` 的 KDoc 里写死这条规则**（防止下一轮又退回 7sp）：
`字号下限 11sp；只有 microPrint 可低于它，且只用于装饰性印刷仿真。`

### 3.2 把 25 处裸 `fontSize` 全部换成令牌

`grep -n "fontSize\s*=\s*[0-9.]" app/src/main/java -r` 应只剩 `FilmTheme.kt` 与 `Type.kt`（旧主题）两处定义。

| 文件 | 行 | 现字号 | 语义 | 换成 |
|---|---|---|---|---|
| `FilmCaptureScreen.kt` | 489 | 13 | 占位标题 | `spec.type.optionTitle` |
| `FilmCaptureScreen.kt` | 499 | 11 | 占位说明 | `spec.type.body` |
| `SettingsScreen.kt` | 166 | 15 | 弹窗标题 | `spec.type.optionTitle` |
| `SettingsScreen.kt` | 178 | 11 | 弹窗正文 | `spec.type.body` |
| `SettingsScreen.kt` | 187 | 12 | 弹窗正文 | `spec.type.body` |
| `SettingsScreen.kt` | 223 | 13 | 顶栏标题 | `spec.type.optionTitle` |
| `SettingsScreen.kt` | 234 | 8 | 顶栏副标题 | `spec.type.label` + 颜色改 `textTertiary` |
| `SettingsScreen.kt` | 259 | 8 | 分组标题 | `spec.type.label` + 颜色改 `textTertiary` |
| `SettingsScreen.kt` | 303 | 13 | 行标题 | `spec.type.optionTitle` |
| `SettingsScreen.kt` | 314 | 8 | 行说明（**信息**） | `spec.type.body` + 颜色改 `textSecondary` |
| `SettingsScreen.kt` | 330 | 9 | 行右侧值 | `spec.type.value` + 颜色改 `textSecondary` |
| `ResultScreen.kt` | 213 | 13 | 页面标题 | `spec.type.optionTitle` |
| `ResultScreen.kt` | 574 `MonoSmall` | 8.5 | 副标题/状态 | `spec.type.label` |
| `ResultScreen.kt` | 580 `MonoTag` | 7.5 | 标签 | `spec.type.label` |
| `ResultScreen.kt` | 586 `MonoLabel` | 9 | 参数格标签 | `spec.type.label` |
| `ResultScreen.kt` | 592 `MonoValue` | 9.5 | 参数格值（**信息**） | `spec.type.value` |
| `PhotoWallScreen.kt` | 139/182/293 | 13 | 标题/组名 | `spec.type.optionTitle` |
| `PhotoWallScreen.kt` | 372 `MonoCaption` | 8 | 时间戳/计数 | `spec.type.label` |
| `FilmInteractions.kt` | 130 | 7 | `AF`/`AF LOCK` | `spec.type.label` + 颜色用 `textTertiary`（取景器内需保证对比：改为白 70% 再算一次，不得低于 4.5:1） |
| `FilmInteractions.kt` | 259 | 6.5 | 等效焦距 | `spec.type.label` + 颜色改 `textTertiary` |
| `LicensesScreen.kt` | 107 | 8 | 声明小标题 | `spec.type.label` + 颜色 `textTertiary` |
| `LicensesScreen.kt` | 125 | 10 | 声明正文 | `spec.type.body` + 颜色 `textSecondary` |
| `FilmLibraryScreen.kt` | 655-677 | 8 / 7 | **胶卷筒印刷标签** | 8sp → `spec.type.microPrint`，7sp → `spec.type.microPrint`（**装饰例外，加注释**） |
| `FilmLibraryScreen.kt` | 724 | 12.5 | 卷名 | `spec.type.optionTitle` |
| `FilmLibraryScreen.kt` | 736 | 9 | 张数 | `spec.type.label` |
| `FilmLibraryScreen.kt` | 744 | 9 | 文件名（**本单删除**，见 Phase 3） | — |
| `FilmLibraryScreen.kt` | 760 | 10 | 上次拍摄（**信息**） | `spec.type.body` + 颜色 `textSecondary` |
| `FilmLibraryScreen.kt` | 796 `BorderPill` | 8 | 尺寸/容量 chip | `spec.type.label` + 颜色 `textTertiary` |
| `FilmLibraryScreen.kt` | 815 `LoadPill` | 9 | 装卷状态 | `spec.type.label` |
| `FilmLibraryScreen.kt` | 889 | 7 | 待确认（读该处上下文） | 若承载信息 → `spec.type.label`；若装饰 → `microPrint` + 注释 |
| `FilmLibraryScreen.kt` | 150 | 13 | 卷卡说明 | `spec.type.body` |
| `FilmLibraryScreen.kt` | 211 | 15 | 页面标题「胶卷库」 | `spec.type.sheetTitle` |
| `FilmLibraryScreen.kt` | 218 | 8 | 副标题 `FILM STOCK` / `NO STOCK`（现 `inkGhost` 2.17:1） | `spec.type.label` + `textTertiary` |
| `FilmLibraryScreen.kt` | 300 | 8 | `LOADED · 已装卷` 区标题（`Metal.inkMono`） | `spec.type.label`，颜色保持 `Metal.inkMono` |
| `FilmLibraryScreen.kt` | 320 | 17 | 剩余张数大数字（`Metal` 面板） | 保留 17sp，抽出为 `spec.type.*` 外的页面常量并注释"反色面板标题字" |
| `FilmLibraryScreen.kt` | 333 | 10 | `stock.file.name`（已装卷大卡，**Phase 3 删除**） | — |
| `FilmLibraryScreen.kt` | 361 | 10 | 「剩余」二字（`Metal.inkBody`） | `spec.type.label`，颜色保持 `Metal.inkBody` |
| `FilmLibraryScreen.kt` | 366 | 15 | 剩余张数（`Metal` 面板） | 同 320，页面常量 |
| `FilmLibraryScreen.kt` | 373 | 10 | 「张」（`Metal.inkBody`） | `spec.type.label`，颜色保持 `Metal.inkBody` |
| `FilmLibraryScreen.kt` | 380 | 9 | `%02d / %02d` 已拍计数（`Metal.inkFaint`） | `spec.type.label`，颜色保持 `Metal.inkFaint` |
| `FilmLibraryScreen.kt` | 448 | 8 | `FULL` / `READY` 状态徽章 | `spec.type.label`；徽章内边距同时放宽，别裁字 |
| `FilmLibraryScreen.kt` | 466 | 8 | `MetalPill` 通用字号（尺寸/容量 chip） | `spec.type.label`；Phase 4 合并 pill 后只剩一个，高度按 11sp 重算 |
| `FilmLibraryScreen.kt` | 543 | 11 | 已达标，仅令牌化 | `spec.type.label` |
| `FilmLibraryScreen.kt` | 982 | 13 | 列表项标签 | `spec.type.optionTitle` |

> 上表 15 行是 2026-10-21 核对时补的——原清单只覆盖了 `FilmLibraryScreen` 的胶卷筒与列表卡，
> 漏了「已装卷」金属面板整块。**照原清单做，§8 第 2 条验收会失败**，因为 218/300/333/361/373/380/448/466
> 八处仍低于 11sp。

**删 chip 的连锁（Phase 2 一并清理，否则留一堆死参数）**：`FilmCaptureScreen.megapixels`
只被 `FilmChip(megapixels)` 一处消费。删掉那个 chip 后，以下三处全部成为死代码，必须一起删：
`AppRoot.kt:322` 的 `megapixels = megapixelLabel(cameraUi.programmedSize)`、
`AppRoot.kt:441` 的 `private fun megapixelLabel(size: Size?)`、`FilmPreviewActivity.kt:53` 的
`megapixels = "12.6MP"`。删完 `FilmCaptureScreen` 的参数表也要去掉 `megapixels`。

其余 `fontSize = spec.type.*` 的调用点不用改。

### 3.3 颜色替换：所有 `inkGhost` / `inkFaint` 文字

全局扫 `Text(` 的 `color =`，按下表替换（**只改文字，不改图标 tint**）：

| 现颜色 | 对比度 | 改成 | 新对比度 |
|---|---|---|---|
| `inkGhost`（文字） | 2.17:1 | `textTertiary` | 5.3:1 |
| `inkFaint`（≥13sp 信息文字） | 3.15:1 | `textSecondary` | 7.2:1 |
| `inkFaint`（<13sp 标签） | 3.15:1 | `textTertiary` | 5.3:1 |
| `inkMuted`（正文） | 4.50:1 | `textSecondary` | 7.2:1 |
| `inkMuted`（标签） | 4.50:1 | `textTertiary` | 5.3:1 |

**取景器内的文字是例外**：它压在真实画面上，不适用上面的合成计算。`AF LOCK`、`G200`、`1×` 这些在 `FilmChip`/drawing 里的白字保持现值或更高，逐处截图确认在亮画面下可读。

---

## 4. Phase 2：取景页降噪（`FilmCaptureScreen.kt`）

目标：**画面之外可见文字 10 条 → ≤ 5 条**。

| 行 | 现状 | 动作 |
|---|---|---|
| 258-268 | `LIVE`/`STANDBY` + `12.6MP` + `4:3` 三个 chip | **整行删除**。理由：STANDBY 已由 `ViewfinderPlaceholder`（215-220）表达；MP 与画幅用户不可选 |
| 270-278 | 取景器右下 `G200` chip | **删除**（卷名在顶部 pill 已有） |
| 299-306 | footer 左 `GOLD 200 · 4096×3072` | **删除卷名与尺寸**，该位置改放 `blockReason`（仅在非空时渲染） |
| 307-313 | `1 / 36 EXP` | **保留**，字号跟 `spec.type.monoSmall`（自动变 11sp） |
| 354-374 | 左 `blockReason ?: "AUTO · 4:3 · JPEG"`；右 `余 N 张` | **删除整行**（blockReason 已上移到 299 位置；`余 N 张` 与 `1/36` 重复） |
| 376-397 | 36 格刻度 + 进度 | **保留**（最有胶片感的一处进度表达） |
| 143-174 | flash / grid / 卷名 pill / settings | 保留不动 |
| 316-350 | 相册 tile / 快门 / 换卷 tile | 保留不动（仅文字标签随令牌变大） |

**改完后取景页应当只剩**：顶部 4 个控件、`1 / 36 EXP`、进度条、快门区两个按钮标签、以及仅在出错时出现的 `blockReason`。

**注意**：`FilmActionTile` 的 `label`（`相册`/`换卷`）现用 `spec.type.label`（10→11sp），tile 高度不变、文字变大，确认不裁切。

---

## 5. Phase 3：删除开发者指标（4 处）

| 文件:行 | 内容 | 动作 |
|---|---|---|
| `ResultScreen.kt:508-513` | `${renderMillis}ms · ${strips} strips` | 删除该 `if (photo != null) { Text(...) }` 整块 |
| `FilmLibraryScreen.kt:741-749` | `stock.file.name`（`gold200__4096x3072.fswp`） | 删除该 `Text` 与它上面的 `Spacer(height 5.dp)`；若卡片因此变矮，靠已有的 `heightIn(min=108.dp)` 吸收 |
| `FilmLibraryScreen.kt:777-783` | `fileSizeLabel(...)`（`1.8 MB`） | 删除该 `Text`；同一 Row 保留 `LoadPill`，`Arrangement.SpaceBetween` 改 `Arrangement.End` |
| `res/values/strings.xml` | `files/film · 点击重新扫描`、`JPEG · 当前为原型固定值` | 分别改成 `点击重新扫描`、`光栅 JPEG`（不写"原型"自白）；**若产品要求全删副标题，直接删这两条 string 与引用** |

---

## 6. Phase 4：胶卷库/成片页/照片墙的"少而大"

**胶卷库（`FilmLibraryScreen.kt`）**：每卡 8 条 → 5 条。

| 保留 | 删/并 |
|---|---|
| 卷名（→ `optionTitle` 15sp） | 文件名（Phase 3 删） |
| 张数（→ `label` 11sp，与卷名同行） | `1.8 MB`（Phase 3 删） |
| 上次拍摄（→ `body` 13sp + `textSecondary`） | 两个 BorderPill 合并为**一个**：`36 张 · 4096×3072` 太长，**只保留 `36 张`**（尺寸属于内部参数，删） |
| 装卷 / 已装卷 pill（→ `label` 11sp） | — |
| 画格缩略图 | — |

**成片页（`ResultScreen.kt`）**：参数网格保留三项，但字号按 §3.1 提升；`FRAME n / 36` 保留；`JPEG · 4:3` 这一行**删除**（固定值，无信息）。

**照片墙（`PhotoWallScreen.kt`）**：只改字号与颜色，不删元素；`ARCHIVE · 2026` 的**年份写死问题**不在本单（另开单）。

**设置页（`SettingsScreen.kt`）**：只改字号与颜色。副标题变成 13sp 后若撑高行，允许 `heightIn(min=64.dp)` 自然增长，**不要缩字**。

---

## 7. 溢出处理规则（放大必然会撞上，按此顺序）

1. **删掉该元素**（若它不是信息）
2. **缩短文案**（如 `上次拍摄 10.20` → `10.20`）
3. **调整间距/容器高度/列权重**
4. **换行**（给 `lineHeight` 与 `maxLines`）
5. ~~降字号~~ —— **禁止**。最低就是 11sp（装饰 9sp）

已知会撞的 3 处，预置处理办法：

| 位置 | 症状 | 处理 |
|---|---|---|
| `FilmLibraryScreen` 两个 BorderPill 并排 | 11sp 后横向溢出 | 只留一个 pill（见 Phase 4） |
| `ResultScreen` 参数网格 EXPOSURE 列 | 14sp 值可能换 3 行 | 列权重 1.5 → 1.8，或值允许 2 行并收紧 `lineHeight` |
| `SettingsScreen` 行副标题 | 13sp 后两行变三行 | 允许行高增长；文案超 2 行时截断（`maxLines=2` 已有） |

---

## 8. 验收标准（逐条勾，缺一条不算完成）

- [ ] `./gradlew :app:assembleDebug :app:testDebugUnitTest` 绿，27/27 通过
- [ ] 裸字号归零。命令（原版写的是 `grep "fontSize = N"`，但 `FilmTheme.kt` 用的是
      `val sheetTitle: TextUnit = 16.sp`，根本不含 `fontSize =`，那条命令永远空过）：

      ```bash
      grep -rn "\.sp" app/src/main/java/com/pocketccd/prototype/ui --include=*.kt \
        | grep -v "FilmTheme.kt" | grep -v "ui/theme/"
      # 期望：无输出
      ```

      `ui/theme/` 是 M3 兜底字型（`PocketCcdTypography`），不在本单范围；
      `microPrint` 走令牌所以不命中。
- [ ] `grep -rn "color = .*inkGhost\|color = .*inkFaint" app/src/main/java` 在 `Text(` 上 0 命中（图标 tint 不算）
- [ ] 全屏检索不到：`ms · `、`strips`、`.fswp`、`1.8 MB`、`AUTO ·`、`余 ` 这些字符串。
      **`files/film` 是唯一例外**：它必须留在 `licenses_*` 字符串里（CC BY-SA 4.0 要求披露素材位置与
      可替换性，FOT-024）。检索时排除 `res/values/strings.xml` 的 `licenses_` 前缀条目，
      否则就是在用"删内部路径"的理由删掉合规声明。
- [ ] 取景页画面外可见文字 ≤ 5 条
- [ ] 四页真机截图（`PJZ110`）存档到 `docs/screenshots/2026-10-21-ui-density/`，文件名 `camera.png / films.png / result.png / settings.png`
- [ ] `adb shell settings put system font_scale 1.3` 下四页各截一张，**无裁切、无重叠、无省略号吃掉信息**；测完 `font_scale 1.0` 复原
- [ ] 每处保留文字在截图上放大 200% 仍可辨认（人工确认，写进提交说明）
- [ ] 提交说明里列出"删了哪些、改了哪些字号"，并同步 §9 文档

## 9. 必须同步的文档（这是项目规矩，不是可选项）

1. `docs/film-dark-ui-spec.md` 的字阶映射表（第 100-106 行）：把 `8px→9sp`、`9px→10sp`、`10px→10sp` 三行改成新令牌值，并在表下补一句：**「字号下限 11sp；microPrint(9sp) 仅限装饰性印刷仿真」**。
2. `DECISION_LOG.md` 追加一条（编号顺延）：
   - 标题：**信息密度上限：真实 ≠ 该显示**
   - 内容：D-002/D-043 规定了"不许虚构"，本决策补另一半——"能查到但与用户当前决策无关的数据不上屏"；据此删除取景页 `LIVE/12.6MP/4:3` chip、`AUTO · 4:3 · JPEG`、成片页引擎耗时、胶卷库 pack 文件名与体积。
   - 标注 `ACCEPTED`（若产品确认）或 `PROPOSED`（未确认）。
3. `README.md` 的〈取景页里哪些是真的〉一节：删掉"参数 chip 只写能读到的值：LIVE、12.6MP、4:3"，替换为一句说明"取景页只保留卷名、卷内计数与进度；其余参数移到成片页"。

## 10. 明确不做（防止范围蔓延）

- 不重做视觉、不换字体（继续用系统 sans + monospace）
- 不把自绘控件替换成 Material 组件
- 不改 `FilmMetrics` 的尺寸令牌（触摸目标、间距、圆角）
- 不改任何 `camera/`、`fotufilm/`、`navigation/` 逻辑（包括本单顺带发现的问题）
- 不新增依赖、不引入 lint 插件（若要加"禁止裸 fontSize"的守护，见附录）

## 附录：可选的守护测试（建议做，10 分钟）

在 `app/src/test/java/com/pocketccd/prototype/ui/` 新增：

```kotlin
@Test
fun `UI 层不许出现裸字号`() {
    val root = File("src/main/java/com/pocketccd/prototype/ui")
    val offenders = root.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
        f.readLines().withIndex()
            .filter { (_, line) -> Regex("""fontSize\s*=\s*[0-9.]+\s*\.sp""").containsMatchIn(line) }
            .map { (i, _) -> "${f.name}:${i + 1}" }
    }.toList()
    assertEquals(emptyList<String>(), offenders)
}
```

若单测工作目录不是 `app/`，用 `File(System.getProperty("user.dir"))` 打印实际路径后修正；这条测试的目的是让下一次"顺手写个 8sp"直接变红。

---

## 修订记录

**2026-10-21（实施前核对，`cc78d17`）：** 逐条比对代码行号，原表全部成立；补了 7 处会让执行跑偏的地方：

1. 基线 27/27 → **28/28**（`GalleryReconcilerTest` 11→12 例）。
2. §2 硬约束 2 加**两处例外**：`Metal` 反色浅底面板（白字浅底会消失）、取景器内文字（不用 50% 白）。
3. §3.2 表格补 **15 行**：原清单漏了整个「已装卷」金属面板，其中 8 处低于 11sp。
4. §3.2 补**删 chip 的连锁**：`megapixels` 参数链 3 处要一起删。
5. §8 第 2 条验收命令原版**永远空过**（`FilmTheme` 不含 `fontSize =`），改成按 `.sp` 检索。
6. §8 第 4 条**误伤合规声明**：`files/film` 必须留在 `licenses_*`。
7. 取景页 chip 取舍已由产品确认：**按原默认值全删**（画面外文字 10 → 5 条）。
   需同步补决策记录，口径是「D-040 只写真实值」**不变**，新增的是「真实 ≠ 该上屏」。
