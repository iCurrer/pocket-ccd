# 交互设计对照：原型 4 页 → Compose 实现

**来源：** `C:\Users\13783\Downloads\未命名原型-演示.html`（1.9 MB，2026-10-19 19:47）
该文件把整份原型的 4 个页面以 JSON 内嵌在 `data-mb-code-navigation` 属性里，已解出到
`docs/reference/pages/`：

| 文件 | 字节 | 页面 | 我们的现状 |
|---|---|---|---|
| `home.html` | 35,585 | 取景（主页面） | 已实现：`ui/film/FilmCaptureScreen.kt` |
| `gallery.html` | 33,457 | 胶片相册（16 张、按卷分组、全屏查看器） | 旧 `ui/gallery/PhotoWallScreen.kt`，未换肤 |
| `films.html` | 28,238 | 胶卷库（6 款、装卷） | 旧设置页里的一段列表 |
| `roll.html` | 20,730 | 冲扫进度（虚构 LAB 订单） | **无对应，且不应有** |

> 原型里 `home.html` 的页内链接指向 `films.html` / `gallery.html`（modao 直链需登录，无法直接抓）。
> 这份 `未命名原型-演示.html` 是完整版，四页齐全且带跨页状态传递。

---

## 1. 整个 demo 只有 6 种交互原语

原型没有动画库、没有手势识别（`grep` 全仓无 `touchstart`/`touchmove`/`swipe`），
所有交互都由这 6 种原语拼出来：

| # | 原语 | 原型实现 | Compose 对应 | 成本 |
|---|---|---|---|---|
| 1 | **开关** toggle | `classList.toggle('on')` | `MutableState<Boolean>` + `animateColorAsState` | 极低 |
| 2 | **覆盖面板** sheet | `.hidden` 切换，遮罩 `bg-black/65 backdrop-blur-[2px]` 点击关闭 | `ModalBottomSheet` 或自绘 `AnimatedVisibility` + `Box(scrim)` | 低 |
| 3 | **轻提示** toast | 固定文案，2.0–2.2 s 后淡出 | `Snackbar` 无样式适配 → 自绘 `AnimatedVisibility` | 低 |
| 4 | **快门闪白** | `opacity 0.85 → 0`，90 ms | `animateFloatAsState` + `Box(White)` | 极低 |
| 5 | **点按对焦环** | JS 取点击坐标改 `left/top`，CSS `@keyframes` 播一次 | `pointerInput` 记 `Offset` + `Animatable` | 中 |
| 6 | **页面跳转 + 状态传递** | `href` 跳转 + `localStorage['film-active']` | `AppEvent` + `ViewModel` | 低 |

**注意 2 号原语**：原型的面板**没有下滑手势关闭**，只有"点遮罩"和"点关闭钮"。
`ModalBottomSheet` 自带拖拽关闭，属于我们额外送的，不算复刻偏差。

---

## 2. 逐页交互清单

### 2.1 取景页 `home.html`（已实现，此处只列差异）

| 交互 | 原型行为 | 我们的实现 | 状态 |
|---|---|---|---|
| 闪光 toggle | 换图标 + toast「闪光灯已开启 · 自动输出」 | `handle.setFlash()` + toast「闪光灯已开启/关闭」 | **已做**（文案去掉"自动输出"，无此功能） |
| 网格 toggle | 显示 9 宫格 + toast | `gridOn` state + 九宫格 + toast | **已做** |
| 变焦 1x / 2x | `sceneLayer.style.transform = scale(1.28)`，**假变焦**（只缩放画面，不换镜头） | `CameraControl.setZoomRatio`，真变焦 + toast「变焦 2.0x」 | **已做**（真机 `zoom -> 2.00x (max=20.00)`） |
| 点按取景器 | 焦点环出现在点击处，播一次 0.5 s 动画；环一直留着，永远写 `AF LOCK` | `MeteringPointFactory` + `startFocusAndMetering`，环 2.2 s 后淡出，标签显示真实结果 | **已做**（真机 `tap to focus -> true`） |
| 快门 | 闪白 90 ms → toast「已记录 · 第 N 张」→ 420 ms 防连点 | 闪白 90 ms（画在**成片页之上**）→ 真实拍摄 → 冲洗 8.4–9.1 s → 落盘 → 成片页 | **已做**（时长差 20 倍，见 B-11） |
| 卷名胶囊 | 跳到 `films.html` | 循环切下一卷 | 行为不同，待定（T9-016） |
| 设置钮 tune | 开 `settingsSheet` | 跳 `SETTINGS` 目的地（旧页面） | 待改面板 |
| 风格 chip `STD` | 开 `styleSheet` | 无 | 待 D-041 |
| 相册缩略图 | 显示上一张 + 角标张数 | 图标 + 角标（真实张数） | 已做 |

#### 2.1.1 落地结果与三处必要的偏离

真机（`PJZ110`）2026-10-19 22:29–22:50 逐项验证，全部有截图与 logcat 证据：

| 项 | 证据 |
|---|---|
| 点按对焦 | `PocketCcdCamera: tap to focus (760.0,1226.0) -> true`；`docs/screenshots/2026-10-19-focus-ring.png` |
| 真实变焦 | `PocketCcdCamera: zoom -> 2.00x (max=20.00)`；`2026-10-19-zoom-toast-final.png` |
| 网格 + toast | `2026-10-19-grid-on.png` |
| 快门闪白 | `2026-10-19-shutter-flash.png`，整帧均值 243（成片页基准 195） |

三处偏离，都是原型照抄会出问题的地方：

1. **焦点环必须淡出**。原型把环永久留在最后一次点击的位置，换个角度它就是画面里一个没意义的框。
2. **`AF LOCK` 不能永远显示**。原型只挪光圈、从不真的对焦；我们调了 `startFocusAndMetering`，所以标签分三态：对焦中 `AF`、成功 `AF LOCK`、失败 `AF FAIL`。
3. **闪白必须画在最上层**。实测把闪白放进取景页内部时，按下快门立刻导航，它被成片页完全盖住 —— 整帧均值只从 195 变成 206，等于没闪。移到 `PocketCcdApp` 最外层后是 243。


### 2.2 色彩风格面板 `styleSheet`

5 个风格：`STD` 标准 / `P400` 柔和 / `E100` 浓郁 / `HP5` 黑白 / `CINE` 电影蓝调。
每项 = 色卡 + 名称 + 一句描述 + 勾选圆点。

- **原型行为**：选中后给取景层换 `tone-*` class（CSS `filter`），**实时**改变画面；面板立刻关闭 + toast。
- **冲突**：D-036 已 `ACCEPTED` —— 取景走原色 `Preview`，只有成片跑 Fotufilm 引擎；引擎单帧 8.4–9.1 s，物理上不可能实时。
- **可落地方案**：预览层叠一层 `ColorMatrix` 近似（几乎零成本），并明确标注"预览为近似示意，成片以冲洗结果为准"。这就是 D-041 待确认的那条。

### 2.3 相机设置面板 `settingsSheet`

4 个开关 + 2 个跳转按钮（胶卷库 / 胶片相册）。

| 开关 | 原型 | 能否照做 |
|---|---|---|
| 机械快门声 | 纯装饰 | **能做**（`ToneGenerator`/短音效），但"机械"是假的，文案应写"快门音" |
| 九宫格参考线 | 与顶部网格按钮双向同步（`setGridFromSwitch`） | 能做，需保证两个入口同一份 state |
| 保留 DNG 原片 | 纯装饰 | **不能做**：项目只出 JPEG，D-002 禁止 |
| 胶片边框水印 | 纯装饰 | 能做（成片加边框），需实现真功能 |

面板头部还有个 `FILM OS · 1.4` 版本号 —— 可用 `BuildConfig.VERSION_NAME` 替真。

### 2.4 胶片相册 `gallery.html`（16 张，最复杂的一页）

结构：标题 `胶片相册 / ARCHIVE · 2026` → 筛选 chips → 按卷分组的 section（每组带卷名、日期、张数）→
末尾"冲扫中 72%"卡片。

| 交互 | 细节 |
|---|---|
| 筛选 | 5 个 tab：`全部` / `收藏` / 3 个卷；选中态高亮 |
| 分组过滤 | 组内一张不剩 → 整组隐藏（`is-hidden`）；收藏为空 → 显示空态插画 |
| 打开查看器 | 点任意缩略图 → 全屏 viewer，显示卷名、日期、元数据、片号 `01`、索引 `1 / 16` |
| 切换 | `‹ ›` 按钮 **首尾循环**；键盘 `←` `→`；`Esc` 关闭 |
| 收藏 | 心形切换 + toast；**只存在内存 Set 里，刷新即丢** |

**冲突点**：「冲扫中 72% · 已扫描 18/36 · 预计 10.20 交付」——我们本地冲洗 9 秒出片，不存在"冲扫中"。

### 2.5 胶卷库 `films.html`

6 款胶卷、顶部「已装卷」大卡（卷名 / ISO / 画幅 / 剩余张数 / 进度条 / 剩余帧点阵）、
分类 chips（全部 / 彩色负片 / 黑白 / 电影卷 / C-41）、每款带「装卷」按钮。

**唯一真正跨页的状态传递**：点「装卷」→ 写 `localStorage['film-active']`
（`{name, iso, meta, remaining, exposed}`）→ 回到 `home.html` 时 `loadActiveRoll()` 读取，
把卷名、ISO、剩余张数、进度条、角标全部刷新。

我们的等价物：`AppViewModel.nextStock()` + `PhotoStore.rollStartedAt()`。**但语义不同**：
原型换卷会把 `remaining` 重置成该卷总张数；我们换卷只重置计数、不区分卷型容量。

### 2.6 冲扫进度 `roll.html` —— 建议不复刻

内容：`订单 GL-1024`、`72%`、`预计 10.20 交付`、`已用 18h 42m`、5 个冲扫节点时间轴、
`C-41 / 6000×4000 / DNG + JPG`、`下载扫描件`、`加急`。

这是一个**不存在的线下冲扫服务**：没有订单号、没有 LAB、没有交付日期、没有加急通道，
规格里的 `6000×4000` 和 `DNG` 也与实际（`4096×3072`、仅 JPEG）不符。

照做等于在 App 里编造一条业务线，直接撞 D-002。**建议整页舍弃**，
它的视觉价值（时间轴、进度环）可以借给"本次冲洗耗时"这种真实数据，但那是另一个设计题。

---

## 3. 原型没有、但真机必须补的交互

原型是静态演示，所有异常路径都被省略了。以下必须由我们补：

| 场景 | 现状 | 需求 |
|---|---|---|
| 相机权限被拒 | 只有一行文案 | "去设置"按钮 + 重新检查（切回前台时重查） |
| 相机启动失败 / 被占用 | 有 `blockReason` 文案 | 重试按钮 |
| 冲洗中（8–9 s） | 跳成片页显示进度 | 需要真实的进度反馈，否则像卡死 |
| 本卷拍满 | 已实现禁快门 + 提示 | — |
| 删除照片 | 无 | 查看器内删除 + 二次确认 |
| 分享 / 导出 | 无 | 系统分享 |
| 无障碍 | 未测 | 所有 chip/圆钮热区 ≥48dp、TalkBack 可读 |

---

## 4. 复刻顺序建议

1. **成片页 / 相册 / 设置页换肤**（T9-011）——先把视觉断层补上，这是"正式替换"的前置；
2. **设置面板 + 风格面板**（T9-008）——两个 sheet 是复刻成本最低、观感提升最大的一块；
3. **相册查看器**（筛选 / 首尾循环 / 收藏）——`gallery.html` 里唯一有信息架构的部分；
4. **胶卷库装卷流程**（T9-016）——把"切换胶卷"从循环点击升级成可选列表；
5. **焦点环 + 闪白 + Toast**（T9-006 / T9-009）；
6. 冲扫进度页：**不复刻**，见 §2.6。

---

## 5. 参考

- 原型页面：`docs/reference/pages/{home,gallery,films,roll}.html`
- 单页设计基线：`docs/reference/film-dark-home-2026-10-19.html`
- 视觉规格：`docs/film-dark-ui-spec.md`
- 相关决策：D-002（不伪造）、D-036（预览原色）、D-039（废除首页）、D-040（真实数据 + 36 张）、D-041（预览色调，OPEN）、D-042（冲扫页不复刻）
