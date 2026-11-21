# Pocket CCD — 组件审计

**基线：** `3166831` / 0.8.8  
**原则：** 以下只列当前仓库真实存在的组件。页面私有 Composable 也标明，避免把局部实现误认为公共设计系统。

## 1. 分类方法

| 类型 | 含义 | 改版权限 |
|---|---|---|
| `T` 主题令牌 | 颜色、字体、间距、形状、材质参数 | 可重构视觉值；字号与触控底线不能退步 |
| `C` 公共视觉组件 | 多页面复用的玻璃、图标、按钮、导航 | 可重做外观和内部视觉，公开语义尽量保持不变 |
| `S` 页面组件 | 单页私有，但形成可复用的视觉模块 | 可按页面重做；若抽出公共件需避免新增业务依赖 |
| `B` 行为耦合组件 | 与 CameraX、存储、导航、状态流直接连接 | 只改视觉外壳；行为与状态契约保持 |
| `P` 受保护组件 | 决定拍照、方向、归档、合规或权限的组件 | 不作为纯视觉重构对象 |

## 2. Theme / Palette / Typography

### 2.1 `GlassPalette`

- **文件：** `ui/theme/Theme.kt`
- **类型：** `T`
- **当前职责：** 固定深色页面的背景、玻璃填充、强填充、影像压暗、边框、高光、主/次文字、强调色、错误色、双光斑、遮罩。
- **当前值：**
  - 背景来自 Material scheme `#0B0C0F`；
  - fill 为白 6%，fillStrong 为白 10%；
  - veil 为黑 48%，border 为白 14%，highlight 为白 12%；
  - onGlass 为白 95%，onGlassMuted 为其 62%；
  - accent 为 `#A8C7FA`；
  - scrim 为黑 55%。
- **改版建议：** 替换为 Darkroom Instrument 的碳黑、石墨、烟熏玻璃、象牙白和琥珀色；错误仍保留独立红色语义。

### 2.2 `PocketCcdTheme` / `LocalGlassPalette` / `MaterialTheme.glass`

- **文件：** `ui/theme/Theme.kt`
- **类型：** `T`
- **当前职责：** 只提供一套固定深色主题；不跟随 wallpaper dynamic color；同时设置 M3 fallback typography/shapes。
- **可改：** 色板、M3 fallback scheme、字体/形状映射。
- **不可改：** 不重新引入动态取色；页面不应绕过 token 直接读 `MaterialTheme.colorScheme`。

### 2.3 `GlassShape`

- **文件：** `ui/theme/Tokens.kt`
- **类型：** `T`
- **当前值：**
  - `pill = 50%`
  - `chip = 18dp`
  - `card = 24dp`
  - `panel = 32dp`
  - `image = 20dp`
- **可改：** 全部视觉值。建议建立更克制的“仪器面板 / 相纸画框”形状层级。

### 2.4 `GlassSpace`

- **文件：** `ui/theme/Tokens.kt`
- **类型：** `T`
- **当前值：** `page 20dp`、`inner 16dp`、`gap 12dp`、`tight 8dp`、`dockReserve 108dp`。
- **可改：** 页边距和间距梯度；必须同步检查列表底部为 TabBar 的让位。

### 2.5 `GlassType` / `PocketCcdTypography`

- **文件：** `ui/theme/Type.kt`
- **类型：** `T`
- **当前字阶：**

| Token | Size / line | 用途 |
|---|---:|---|
| `title` | 24 / 30sp | 页面标题 |
| `cardTitle` | 16 / 22sp | 卡片标题 |
| `body` | 14 / 20sp | 正文 |
| `caption` | 12 / 17sp | 说明 |
| `label` | 12 / 16sp | 按钮、chip |
| `numeric` | 12 / 16sp Mono | 参数 |
| `numericLarge` | 16 / 20sp Mono | 大读数 |
| `badge` | 11 / 14sp Mono | 小标签、导航文字 |

- **当前约束：** 11sp 是信息文字地板；以 `NoBareFontSizeTest` 防裸字号。
- **可改：** 系统 Sans + Mono 的尺度、字重和行高；不新增字体资产或依赖。
- **保留：** 数字读数和 EXIF 使用等宽；正文中文不降到 11sp 以下。

## 3. 玻璃与通用控件

### 3.1 `GlassBackdrop`

- **文件：** `ui/glass/GlassBackdrop.kt`
- **类型：** `C / B`
- **当前职责：** 提供页面底色、背景光；通过 `layerBackdrop` 录制 content；把 TabBar 和查看器放入 overlay。
- **行为约束：**
  - 只有 overlay 内的玻璃能真实采样 content；
  - 页面内部组件拿 `null` backdrop，走静态玻璃；
  - `captureContent=false` 时不做全屏离屏录制；
  - 相机预览是独立 Surface，不能进入真实模糊。
- **可改视觉：** 背景光位置、颜色、强度；内容/overlay 容器透明度。
- **不可改：** content/overlay 分层、录制开关语义。

### 3.2 `GlassSurface`

- **文件：** `ui/glass/Glass.kt`
- **类型：** `C`
- **当前职责：** 通用玻璃面；支持 `overPhoto`、可交互、onClick、PaddingValues、模糊/折射参数。
- **当前材质：** Kyant backdrop + vibrancy/blur/lens + directional highlight + shadow + inner shadow；无 backdrop 时退化为填充/高光。
- **可改：** 形状、透明度、边缘高光、内阴影、按压行为、模糊半径。
- **保留：** 不能自行伪造无来源背景模糊；需要有 API 降级路径。

### 3.3 `GlassIconButton`

- **文件：** `ui/glass/Glass.kt`
- **类型：** `C`
- **当前尺寸：** 默认 48dp，宽高由同一个 `size` 决定；元素固定圆形。
- **当前状态：** active、enabled、overPhoto；无禁用按压反馈。
- **语义：** `label` 为必填，写 `contentDescription`，role 为 Button。
- **可改：** 视觉可以不再是通栏玻璃圆钮，例如仪表键、刻度键或边框刻度；必须保持 48dp 热区和可读语义。

### 3.4 `GlassChip`

- **文件：** `ui/glass/Glass.kt`
- **类型：** `C`
- **当前用途：** 胶卷芯片、变焦、筛选、空态重扫。
- **当前状态：** selected、overPhoto、前后图标。
- **当前视觉：** chip 18dp，选中时为 accent 30% 叠加，文字仍为白色/次白色。
- **可改：** 方向明确的标签轨、刻度和选中指示；避免所有 chip 继续同一胶囊。

### 3.5 `GlassButton`

- **文件：** `ui/glass/Glass.kt`
- **类型：** `C`
- **当前职责：** 主/次文字按钮，固定 48dp 高，横向 22dp 内边距。
- **可改：** 主次层级、底色、边框、按压高度。
- **保留：** 主操作唯一、禁用态不响应。

### 3.6 `GlassDivider` / `GlassScrim` / `GlassContent`

- **文件：** `ui/glass/Glass.kt`
- **类型：** `C`
- **职责：**
  - 1dp 分隔线；
  - 全屏 scrim；
  - 为子树提供玻璃文字色。
- **可改：** 颜色和厚度。仍应保持颜色对比和触摸行为的独立性。

### 3.7 `GlassIcon` / `GlassIconKind`

- **文件：** `ui/glass/GlassIcons.kt`
- **类型：** `C`
- **实际图标枚举：** Gallery、Film、Aperture、Sparkle、Gear、Flash、FlashOff、Grid、Close、Back、Chevron、Share、Heart、Refresh、Info、Warning、Check、Zoom、Exposure、Add、Remove。
- **来源：** Material Symbols Rounded 矢量；Heart/Sparkle/Aperture 有填充变体。
- **可改：** 尺寸、tint 和视觉组合。若替换图标源需遵守许可且不能新增臃肿依赖。
- **保留：** 装饰图标自身 `contentDescription=null`，语义由外层按钮/文本提供。

### 3.8 `GlassTab` / `GlassNavBar`

- **文件：** `ui/glass/GlassNavBar.kt`
- **类型：** `C / B`
- **当前职责：** 五项底部导航；24dp 图标、11sp 标签、选中强调色与 14×3dp 指示点。
- **当前尺寸：** GlassSurface `panel`、页面左右 20dp、底部 10dp。
- **可改：** 导航外观、密度、选中指示；可更像暗房仪器的功能条。
- **不可改：** 五项顺序、Camera 是中心/可返回根、选中逻辑，以及查看器/详情隐藏。

## 4. 快门和相机控制

### 4.1 `CameraScreen`

- **文件：** `ui/camera/CameraScreen.kt`
- **类型：** `B`
- **职责：** 相机页组合根和全部私有视觉组件。
- **可改：** 整个视觉布局。
- **不可改：** 从 `AppRoot` 传入的相机句柄/状态和所有回调语义。

### 4.2 `ShutterKey`

- **文件：** `ui/camera/CameraScreen.kt`
- **类型：** `S / B`
- **当前结构：** 74dp 圆形外框、2dp 外环、60dp 白盘、6dp 中心点；禁用时降透明。
- **反馈：** `HapticFeedbackType.LongPress`，随后调用 `onShutter`。
- **可改：** 中心位置、同心环、机械快门造型和按压反馈。
- **保留：** 禁用时仍可见；不可拍原因应可读；点击只触发一次拍摄回调。

### 4.3 `GalleryKey`

- **文件：** `ui/camera/CameraScreen.kt`
- **类型：** `S / B`
- **职责：** 52dp 最近成片缩略图或相册占位；打开相册。
- **可改：** 仪器窗口、contact strip、缩略图外框。
- **保留：** 只有真实最近缩略图才显示图片，不可用假照片填充。

### 4.4 `ZoomRail`

- **文件：** `ui/camera/CameraScreen.kt`
- **类型：** `S / B`
- **职责：** 让用户选择真实 `LensStop` 倍率；无足够镜头时退 1×/2×。
- **当前视觉：** 44dp 圆形 GlassIconButton 纵向排列，只显示倍率文字。
- **可改：** 刻度尺、拨轮、横向刻度、侧边键。
- **保留：** 只显示 ≥1× 的档，选中按真实倍率，不允许点击无效果档。

### 4.5 `EvPanel` 与 EV 入口

- **文件：** `ui/camera/CameraScreen.kt`
- **类型：** `S / B`
- **当前视觉：** 底部玻璃卡，减少/读数/增加/归零。
- **数据：** 硬件范围与步长，不做虚构 ±2。
- **可改：** 面板形态、刻度、读数窗口、归零按钮。
- **保留：** 不支持 EV 时不显示入口；按钮逐档改变真实 index；显示范围真实。

### 4.6 `FocusReticle`

- **文件：** `ui/camera/CameraScreen.kt`
- **类型：** `S / B`
- **职责：** 显示 Working / Locked / Failed，2.2 秒后消失。
- **可改：** 尺标、四角框、动画和颜色。
- **保留：** 真实 `startFocusAndMetering` 回调；不能永远显示 AF LOCK。

### 4.7 `GridOverlay`

- **文件：** `ui/camera/CameraScreen.kt`
- **类型：** `S`
- **职责：** 在预览上画 2 横 2 竖三分线。
- **可改：** 线宽、透明度、是否使用可读遮罩。
- **保留：** 纯 UI 叠加，不进入照片像素。

### 4.8 `DestinationSwitcher` / `SwitcherItem`

- **文件：** `ui/camera/CameraScreen.kt`
- **类型：** `S / B`
- **当前职责：** 竖屏 dock 上滑后的四项目的地抽屉。
- **可改：** 抽屉形态、抓手、图标和遮罩。
- **保留：** 点击四项仍调用既有 navigation events。横屏目前没有该入口，是否保留属于待确认交互设计。

### 4.9 `PermissionState`

- **文件：** `ui/camera/CameraScreen.kt`
- **类型：** `S / B`
- **职责：** 相机不可用/无权限的玻璃卡，包含去应用设置按钮。
- **可改：** 错误层级、说明、图标、按钮。
- **保留：** 拒绝权限不能黑屏；必须可跳应用设置。

### 4.10 `ShutterFlash`

- **文件：** `ui/AppRoot.kt`
- **类型：** `P / C`
- **职责：** 全屏白色从 82% 淡到 0，持续 320ms。
- **可改：** 透明度、时长、色调。但必须保持树最上层，否则会被详情页遮住。

## 5. 胶卷列表组件

### 5.1 `FilmCard`

- **文件：** `ui/films/FilmLibraryScreen.kt`
- **类型：** `S / B`
- **字段：** `id`、`label`、`frameLabel`、`supported`、`reason`、`shots`、`latestThumbnail`。
- **约束：** 数据由 ViewModel 组装，UI 不直接依赖 CameraX `Size`。

### 5.2 `FilmLibraryScreen`

- **文件：** `ui/films/FilmLibraryScreen.kt`
- **类型：** `S / B`
- **职责：** 页头、当前卷卡、候选列表和重扫入口。

### 5.3 `CurrentStockCard`

- **文件：** `ui/films/FilmLibraryScreen.kt`
- **类型：** `S`
- **职责：** 当前卷大卡，展示真实张数、pack 尺寸、最近缩略图，整卡可回相机。
- **可改：** 仪器铭牌、胶片盒、当前卷灯光等。
- **保留：** 仅真实缩略图或胶卷筒，不画虚构样片。

### 5.4 `FilmRow`

- **文件：** `ui/films/FilmLibraryScreen.kt`
- **类型：** `S / B`
- **职责：** 候选胶卷行；支持时可点选，不支持时灰化并给出原因。
- **可改：** 行高、缩略图、选中勾、支持/禁用样式。

### 5.5 `StockFrame` / `CanisterGlyph`

- **文件：** `ui/films/FilmLibraryScreen.kt`
- **类型：** `S`
- **职责：** 真实缩略图框或胶卷筒轮廓。
- **可改：** 画布比例、边缘、材质、胶卷筒描画。
- **保留：** 没拍过的卷不得显示伪造成像。

## 6. 照片缩略图

### 6.1 `Thumb`

- **文件：** `ui/gallery/GalleryScreen.kt`
- **类型：** `S`
- **当前职责：** 3:4 圆角格、真实缩略图、收藏心形角标、读取失败占位。
- **当前限制：** `ContentScale.Crop` + 固定 3:4，对横版成片的裁切风险高。
- **可改：** 方形/原始方向画框、contact sheet、边缘标签。
- **保留：** 点击打开查看器、收藏角标只表示收藏。

### 6.2 `rememberThumbnail`

- **文件：** `ui/gallery/GalleryScreen.kt`
- **类型：** `C / B`
- **职责：** IO 线程采样解码 120dp 网格图并查询/写入 LRU。
- **可改：** 目标尺寸和占位视觉。
- **不可改：** 必须离开主线程，必须按目标尺寸采样。

### 6.3 `GalleryBitmapCache`

- **文件：** `ui/gallery/GalleryImages.kt`
- **类型：** `B`
- **当前策略：** 仅缓存缩略图；上限为堆 1/8，夹在 8–32MB。
- **保护：** 不要为大图增加同类无界缓存。

### 6.4 `decodeSampledBitmap`

- **文件：** `ui/gallery/GalleryImages.kt`
- **类型：** `B`
- **职责：** 先读 bounds，再按 `inSampleSize` 解码，异常/损坏返回 null。
- **保护：** 不能为视觉改版改成全尺寸解码或主线程解码。

## 7. 照片查看器

### 7.1 `PhotoViewer`

- **文件：** `ui/gallery/PhotoViewer.kt`
- **类型：** `S / B`
- **职责：** 黑底全屏、水平分页、循环、顶部关闭/计数、底部玻璃信息条、收藏和详情入口。
- **可改：** 灯箱背景、计数、信息条、操作布局、加载/损坏态。
- **不可改：** 模态层级、冻结列表、循环索引、全图优先回退、打开时隐藏 TabBar。

### 7.2 `ViewerPage`

- **文件：** `ui/gallery/PhotoViewer.kt`
- **类型：** `S`
- **职责：** 用 `ContentScale.Fit` 展示一页；读取失败显示文本。
- **可改：** 失败视觉并加入独立加载态；显示可随真实图片比例调整留白。

### 7.3 Viewer 环形分页逻辑

- **文件：** `ui/gallery/GalleryLogic.kt`
- **类型：** `B / P`
- **实际函数：** `circularIndex()`、`loopPageCount()`、`loopStartPage()`。
- **保护：** 不能视觉改版时更换成会失去首尾循环的分页实现。

## 8. 详情与 EXIF 展示

### 8.1 `PhotoDetailScreen`

- **文件：** `ui/detail/PhotoDetailScreen.kt`
- **类型：** `S / B`
- **职责：** 四态详情、大图、EXIF、发布状态、动作条。

### 8.2 `PrintBox`

- **文件：** `ui/detail/PhotoDetailScreen.kt`
- **类型：** `S`
- **当前视觉：** 有图时 3:4、无图时 4:3；玻璃背景。
- **可改：** 暗房工作台、相纸画框、按真实方向布局。
- **保护：** 有图/显影/失败三种内容的语义不能混。

### 8.3 `DevelopingRing`

- **文件：** `ui/detail/PhotoDetailScreen.kt`
- **类型：** `S`
- **职责：** 循环弧线 + 等待秒数；不是百分比。
- **可改：** 周期指示、机械表盘或暗房计时器。
- **保留：** 不显示伪造进度百分比。

### 8.4 `InfoCard` / `InfoRow`

- **文件：** `ui/detail/PhotoDetailScreen.kt`
- **类型：** `S`
- **职责：** 展示时间、曝光、ISO、焦距、机身、文件大小、相册发布状态。
- **可改：** 参数栅格、单位显示、标签与数字排版。
- **保留：** 值来自真实 EXIF/文件/Build；缺失为 `—`。

### 8.5 `ActionCard` / `ActionItem`

- **文件：** `ui/detail/PhotoDetailScreen.kt`
- **类型：** `S / B`
- **职责：** 收藏、分享、在相册打开。
- **可改：** 底部操作条形态、图标、状态反馈。
- **保留：** 系统相册未写入时不可打开；分享走系统分享。

## 9. 设置行和开关

### 9.1 `SettingsScreen`

- **文件：** `ui/settings/SettingsScreen.kt`
- **类型：** `S / B`
- **职责：** 三个常驻分组加可选诊断分组。

### 9.2 `Group`

- **文件：** `ui/settings/SettingsScreen.kt`
- **类型：** `S`
- **当前视觉：** 小标题 + GlassSurface 卡片。
- **可改：** 分组标题、分隔线、仪器面板。

### 9.3 `LinkRow` / `StaticRow`

- **文件：** `ui/settings/SettingsScreen.kt`
- **类型：** `S / B`
- **职责：** 可点/只读设置行。
- **可改：** 行高、对齐、数值列、箭头/状态。
- **保留：** 可点行必须有明确点击语义；数值真实。

### 9.4 `ToggleRow`

- **文件：** `ui/settings/SettingsScreen.kt`
- **类型：** `S / B`
- **当前实现：** 整行可点，使用 Material 3 `Switch` 与自定义颜色。
- **可改：** 自绘仪器拨杆/压板开关，只要整行仍可点、状态清楚。
- **保留：** 调用同一个 `onToggle`；开关状态由真实 `ImprintSettings` 决定。

### 9.5 `LicensesScreen` / `LicenseSection`

- **文件：** `ui/settings/SettingsScreen.kt`
- **类型：** `S / P`
- **职责：** 显示 `strings.xml` 中完整许可文本；独立 BackHandler。
- **可改：** 排版、标题层级、正文可读性。
- **不可改：** 合规文案不得删减或为视觉缩短。

## 10. 空态、错误态、加载态

### 10.1 空态

实际存在：

- `EmptyGallery()`：无照片；玻璃图标、标题、说明、去拍摄。
- `EmptyFavorites()`：筛选后无照片；收藏图标、说明、看全部。
- `EmptyLibrary()`：无胶卷；说明 .fswp 文件缺失、重扫入口。
- `PermissionState()`：无权限/相机不可用；图标、原因、去设置。
- 详情失败态：`PrintBox` 内显示错误图标与失败原因，下方重试。

### 10.2 错误态

实际存在：

- 相机不可拍/无权限；
- 相机绑定失败、超时、会话断开提示；
- 详情冲洗失败；
- 图片解码失败显示占位或“这张照片读不出来”；
- 系统相册未写入状态。

### 10.3 加载态

实际存在：

- 相机 `Pending`；
- 详情 `Processing` / `Writing` + 秒表 + 旋转弧；
- 缩略图异步加载只显示空占位，没有显式 spinner；
- Photos/Videos viewer 初次解码没有独立 loading，失败和 loading 当前共用空文本；
- 胶卷扫描/刷新没有 loading 视觉；
- 相册刷新没有 loading 视觉。

### 10.4 待建统一状态组件

当前没有通用 `EmptyState`、`ErrorState`、`LoadingState` 公共 Composable。建议在视觉改版中建立一个三层状态组件：

- 图标/状态灯；
- 明确标题；
- 一句原因与一个可执行主操作。

应继续使用页面局部实现承载状态，不必为了“组件化”改业务状态枚举。

## 11. 实际不存在的组件

以下名称不应写入实现计划为“现有组件”：

- 通用 `GlassSwitch`
- 通用 `PhotoGrid`
- 通用 `PhotoCard`
- 通用 `ExifPanel`
- 通用 `CameraControlBar`
- 通用 `ThemeSpec` / `FilmTheme` / `PaperComponents`
- 通用 `GlassDialog` / `GlassBottomSheet`
- `FilmCanister` 公共组件
- `PhotoViewerController`
- 独立 `DiscoverFeed`
- 独立 `ResultScreen`

其中 `ThemeSpec`、`FilmTheme`、`PaperComponents` 已随旧 `ui/film/` 包移除；`ResultScreen` 已改为 `PhotoDetailScreen`。改版不应尝试恢复旧组件名。
