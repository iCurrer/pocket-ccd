# Pocket CCD — 页面清单与视觉改版边界

**基线：** `3166831` / 0.8.8（2026-11-03）  
**范围：** `CAMERA`、`GALLERY`、`DETAIL`、`FILMS`、`DISCOVER`、`SETTINGS`，以及 `GALLERY` 内的全屏查看器。  
**说明：** “可改变视觉”仅指布局、层级、材质、色板、字体、形状、图标和反馈呈现；“不允许改变业务”指数据源、状态转换、存储与相机行为。

## 1. 全局导航壳

**文件：**

- `ui/AppRoot.kt`
- `navigation/AppState.kt`
- `ui/glass/GlassBackdrop.kt`
- `ui/glass/GlassNavBar.kt`

**职责：** 承载相机与上层页面；决定相机何时挂载；为页面提供玻璃背景；绘制底部 TabBar、全屏查看器与快门闪白。

**当前结构：**

- `CAMERA` 是根页面，本身不在 `GlassBackdrop` 中。
- 非相机页面位于 `GlassBackdrop.content`。
- TabBar 与查看器位于 `GlassBackdrop.overlay`，确保查看器覆盖 TabBar。
- TabBar 五项：相册、胶卷、拍摄、发现、设置。
- 详情页不显示 TabBar；查看器打开时任何页面都不显示 TabBar。
- 快门闪白位于整棵 UI 树最上层。

**可改变视觉：**

- 页面进入/退出动效；
- TabBar 的玻璃、尺寸、字阶、选中态和指示器；
- 页面底色、背景光、遮罩、圆角和阴影；
- 闪白透明度与时长，但必须保持全屏最上层。

**不允许改变业务：**

- `AppDestination` 枚举和 `reduce()` 的导航结果；
- 相机是根、返回相机、详情按来源返回；
- `DETAIL` 与查看器隐藏 TabBar；
- 查看器由 `AppRoot` 持有，不能重新藏进相册页；
- 详情从快门进入期间相机保持挂载、从相册进入不绑相机的规则；
- 页面触摸拦截，避免点击穿透到取景器。

---

## 2. `CAMERA`

**文件：**

- `ui/camera/CameraScreen.kt`
- `ui/AppRoot.kt`
- `camera/CameraPreview.kt`
- `ui/glass/Glass.kt`
- `ui/glass/GlassIcons.kt`

**页面职责：** 提供 4:3 实时取景、构图辅助、点按对焦、闪光灯、九宫格、真实光学/数码变焦、EV 补偿、快门、胶片选择、相册入口和其他页面入口。

### 2.1 当前布局结构

**竖屏（`width <= height`）：**

1. 顶部：胶卷芯片、EV 图标、九宫格图标、设置图标；
2. 中央：3:4 圆角取景窗，白 18% 描边和四角 L 形刻线；
3. 取景窗内：原色 CameraX 预览、静态暗角、可选九宫格、点按对焦层、对焦回执；
4. 右侧：变焦圆钮纵向轨道；
5. 底部：可选提示、不可拍原因、贴花状态、玻璃 dock，dock 内为相册缩略图、快门、闪光灯；
6. EV 面板从底部中央弹出；
7. 上滑 dock 打开目的地抽屉：相册、胶卷、发现、设置。

**横屏（强制拍摄方向）：**

1. 中央：4:3 横向取景窗，是页面主视觉；
2. 左上：胶卷/张数芯片；
3. 右上：EV、九宫格、设置；
4. 左中：相册缩略图、闪光灯；
5. 右中：快门、变焦轨；
6. 右下：仅显示贴花状态（如果传入）；
7. 当前横屏没有 dock，因此没有上滑目的地抽屉入口。

### 2.2 实际显示的数据

- `activeStock.label`：当前胶卷名；
- `activeCard.shots`：该胶卷现存照片数；只与胶卷芯片合并显示，不夸张为本卷容量；
- `blockReason`：为何不能拍；
- `stockSwitchedNotice`：设备分辨率不支持用户所选胶卷时的自动换卷说明；
- `flashOn` / `flashAvailable`；
- `gridOn`；
- `zoom`；
- `lensStops.zoomRatio`：只用于生成可点倍率；当前 UI 没有显示等效毫米数；
- `exposureProfile` 与当前 EV index；
- 最近一张该胶卷的 `latestThumbnail`；
- 对焦状态：对焦中、`AF LOCK`、`AF FAIL`；
- `imprintLabel` 参数存在，但真实 `AppRoot` 调用当前未传入，运行时是否出现待确认；
- 不显示虚构 ISO、光圈、快门时间或像素/画幅 chip。

### 2.3 用户可进行的操作

- 点按取景窗对焦/测光；
- 按快门；
- 开/关闪光灯；
- 开/关九宫格；
- 切换真实可用变焦档；
- 打开 EV 面板、增减曝光、归零；
- 点胶卷芯片进入胶卷库；
- 点相册缩略图进入相册；
- 点设置进入设置页；
- 在竖屏从 dock 上滑打开目的地抽屉；
- 在抽屉中点相册/胶卷/发现/设置；
- 权限空态中打开应用设置。

### 2.4 交互状态

- 相机 `Pending`：可显示“正在启动相机”；
- 相机 `Bound`：可拍；
- 相机 `Unavailable`：显示错误原因；
- 权限未授予：权限空态与“打开应用设置”；
- 快门忙：`CaptureState.Capturing/Developing/Saving` 时禁用；
- 设备分辨率不服务当前胶卷：快门禁用并显示原因；
- 无闪光灯：闪光灯按钮禁用；
- EV 不支持：EV 控件整体不绘制；
- 变焦物理档不足两档：退回 1×/2× 数码档；
- 对焦失败/成功/处理中；
- 目的地抽屉与 EV 面板互斥；
- 横屏的 `notice`/`blockReason` 表达不完整，属于待改版视觉与状态呈现问题。

### 2.5 依赖的 ViewModel / State

- `AppViewModel.cameraUi`: `activeStock`、`canShoot`、`blockReason`、`programmedSize`、`CameraState`；
- `AppViewModel.capture`: 是否忙及失败信息；
- `AppViewModel.filmCards`: 当前卷张数与最近缩略图；
- `AppViewModel.stockSwitchedNotice`；
- `AppRoot` 本地状态：`handle`、`hasPermission`、`zoomRatio`、`lensStops`、`exposureProfile`、`evIndex`、`flashOn`、`flashAvailable`、`gridOn`、`cameraNonce`、`previewError`；
- `CameraScreen` 内部状态：`focus`、`evOpen`、`switcherOpen`。

### 2.6 当前使用的公共组件

- `GlassChip`
- `GlassIconButton`
- `GlassSurface`
- `GlassButton`
- `GlassIcon`
- `GlassShape`
- `GlassSpace`
- `GlassType`
- `CameraPreview` / `CameraHandle`

### 2.7 可以改变的视觉部分

- 取景窗比例可以继续忠实呈现 4:3 拍摄结果，但外观、圆角、边框、角标和留白可重做；
- 胶卷名、张数、设置、EV、九宫格、变焦、闪光、相册、快门的位置和层级；
- 控制区从“玻璃圆钮 + 玻璃 dock”改为仪表化刻度、机械按键或窄条，只要触控语义不变；
- 对焦框、九宫格、EV 面板、目的地抽屉、错误和提示的呈现；
- 横屏必须获得与竖屏同等的状态可见性；
- 暗角可调整，但必须是纯 UI 蒙层，不能改动预览像素。

### 2.8 不允许改变的业务部分

- `PreviewView` 的 `FIT_CENTER`、4:3 预览流和原色内容；
- `ImageCapture` 的 `MAXIMIZE_QUALITY`、`ROTATION_0` 和目标尺寸协商；
- 首绑、`attachedSurfaceResolution`、尺寸能力判断和失败提示；
- 相机绑定/解绑/重绑时机；
- 方向锁与方向变更后重绑；
- `takeJpeg` 的 5 秒超时、后台回调、EXIF 采样与真实旋转角；
- 闪光灯、变焦、EV 对真实 `CameraControl` 的调用；
- 快门先同步进入 `Capturing` 再导航；
- 拍摄时快照贴花设置；
- 预览不加滤镜、九宫格不烧进成片。

---

## 3. `GALLERY`

**文件：**

- `ui/gallery/GalleryScreen.kt`
- `ui/gallery/GalleryLogic.kt`
- `ui/gallery/GalleryImages.kt`
- `ui/AppRoot.kt`

**页面职责：** 展示本应用现存成片；按真实胶卷筛选；打开查看器和详情；提供刷新与相册对账触发。

### 3.1 当前布局结构

1. 顶部标题“相册”，副标题为总张数与收藏张数；
2. 右上刷新圆钮；
3. 横向滚动的筛选 chip：全部、收藏、真实出现过的胶卷名；
4. 3 列 `LazyVerticalGrid`，格子固定 3:4、14dp 圆角、5dp 间隔；
5. 收藏照片右上显示心形角标；
6. 底部预留 108dp 给悬浮 TabBar；
7. 无照片或筛选为空时显示居中空态。

### 3.2 实际显示的数据

- 全部现存成片数；
- 当前现存收藏数；
- 真实出现过的胶卷名；
- 缩略图；
- 收藏状态；
- 图片读不出时的占位图标；
- 不显示虚构日期分组、冲扫进度、LAB 或假数据。

### 3.3 用户可进行的操作

- 切换筛选；
- 刷新相册并触发系统相册对账；
- 点缩略图打开全屏查看器；
- 在“收藏为空”空态中点“看全部”；
- 在无照片空态中点“去拍摄”；
- 通过底部 TabBar 切换页面；
- 收藏切换与进入详情实际通过查看器完成，网格本身不直接收藏。

### 3.4 交互状态

- 有照片；
- 无照片；
- 有照片但当前筛选为空；
- 缩略图缺失或损坏；
- 图片异步解码中（当前无独立 loading 视觉，直接先显示占位）；
- 查看器打开；
- 系统相册对账可能移除照片并刷新列表。

### 3.5 依赖的 ViewModel / State

- `AppViewModel.roll`；
- `SavedPhoto` → `PhotoWallItem`；
- `FavoritesStore` 提供的收藏 id 集合；
- `filter` 使用 `remember`，当前不是 `rememberSaveable`；
- `ViewerRequest` 由 `AppRoot` 持有；
- 进入 `GALLERY` 时触发 `reconcileGallery()`。

### 3.6 当前使用的公共组件

- `GlassChip`
- `GlassIconButton`
- `GlassButton`
- `GlassIcon`
- `rememberThumbnail`
- `GalleryBitmapCache`
- `decodeSampledBitmap`

### 3.7 可以改变的视觉部分

- 页头与统计层级；
- 筛选 chip 到索引轨、刻度轨或简化文字页签都可；
- 网格列数/间距/圆角/留白可调，但必须按真实照片方向正确展示；
- 收藏角标、空态、加载态和错误态；
- TabBar 让位与列表底部留白；
- 横向滚动的可发现性。

### 3.8 不允许改变的业务部分

- 筛选集合与顺序：全部、收藏、真实卷名；
- `GalleryFilter`、`applyGalleryFilter()`、`toRollGroups()` 语义；
- 按胶卷分组、卷内时间倒序、组间最新照片倒序；
- 收藏持久化；
- 缩略图 IO 解码、`inSampleSize`、LRU 上限；
- 查看器列表与网格顺序同源；
- 点击打开查看器，不在网格内删除照片。

---

## 4. 全屏查看器（`GALLERY` 模态）

**文件：**

- `ui/gallery/PhotoViewer.kt`
- `ui/AppRoot.kt`
- `ui/gallery/GalleryLogic.kt`

**职责：** 全屏查看筛选后的照片、左右循环切换、收藏、进入详情、关闭。

### 4.1 当前布局结构

1. 黑色全屏底；
2. `HorizontalPager` 填充全屏，`ContentScale.Fit`；
3. 顶部左侧关闭圆钮，右侧 `01 / N` 计数胶囊；
4. 底部玻璃信息面板：胶卷名、时间、收藏按钮、详情按钮、分隔线、操作提示；
5. 导航栏 inset；
6. 无 TabBar。

### 4.2 实际显示的数据

- 当前照片全图或缩略图回退；
- 胶卷名；
- 拍摄时间；
- 当前序号/筛选后总数；
- 收藏状态；
- 图片读取失败时仅显示“这张照片读不出来”。

### 4.3 用户可进行的操作

- 左右滑动切换；
- 首尾循环；
- 关闭；
- 收藏/取消收藏；
- 进入统一详情页；
- 系统返回关闭查看器。

### 4.4 交互状态

- 正常图片；
- 原图缺失时回退缩略图；
- 图片解码中（当前显示为读取失败文本，缺少独立 loading）；
- 解码失败；
- 收藏/未收藏；
- 列表在打开时冻结，收藏变化不抽走当前页；
- 照片列表被清空时自动关闭。

### 4.5 依赖的 ViewModel / State

- `ViewerRequest(photos, index)`；
- `FavoritesStore`；
- `circularIndex()`、`loopStartPage()`、`loopPageCount()`；
- `rememberViewerBitmap()` 按显示尺寸在 IO 解码。

### 4.6 当前使用的公共组件

- `GlassSurface`
- `GlassIconButton`
- `GlassDivider`
- `GlassIcon`

### 4.7 可以改变的视觉部分

- 黑底灯箱、顶部工具栏、计数刻度、底部信息面板；
- 关闭/收藏/详情动作的位置与形态；
- 加载、回退、损坏状态；
- 系统 inset 和照片四周留白。

### 4.8 不允许改变的业务部分

- 模态层位于 TabBar 之上；
- 打开时冻结筛选列表；
- 左右首尾循环；
- 大图优先、缩略图回退；
- 查看器大图不在内存缓存；
- 进入详情时关闭查看器；
- 返回先关闭查看器。

---

## 5. `DETAIL`

**文件：**

- `ui/detail/PhotoDetailScreen.kt`
- `ui/AppRoot.kt`
- `camera/PhotoExif.kt`
- `camera/PhotoStore.kt`

**职责：** 同时承载“刚拍完正在冲洗/写入/完成/失败”和“从相册打开的历史照片”。显示成片、真实 EXIF、文件信息和收藏/分享/在系统相册打开动作。

### 5.1 当前布局结构

1. 顶部返回按钮、胶卷名、状态副标题；
2. 可滚动的竖向内容区；
3. 大图框：有照片时 `3:4 Crop`，无照片/处理中时 `4:3`；
4. 处理中：旋转进度环、真实阶段文字、已等待秒数；
5. 完成后：拍摄参数玻璃卡；
6. 失败：错误原因与重试按钮；
7. 完成态底部固定玻璃动作条：收藏、分享、在相册打开；
8. 无 TabBar。

### 5.2 实际显示的数据

- 胶卷名；
- 状态：显影中、写入中、已完成、冲洗失败；
- 已等待秒数；
- 缩略图成片；
- 拍摄时间；
- 真实 EXIF：快门、光圈、ISO、物理焦距；
- 真实设备 `Build.MODEL`；
- 文件大小；
- 是否已写入系统相册；
- 收藏状态；
- 处理中的失败原因。

### 5.3 用户可进行的操作

- 返回来源页；
- 失败时重试；
- 收藏/取消收藏；
- 系统分享；
- 在系统相册打开；
- 滚动查看照片和参数。

### 5.4 交互状态

- `Processing`
- `Writing`
- `Saved`
- `Failed`
- 照片缩略图缺失；
- 系统相册未发布：按钮禁用、说明原因；
- EXIF 不完整：对应值显示 `—`；
- 历史照片与刚拍照片共用布局。

### 5.5 依赖的 ViewModel / State

- `DetailStatus`
- `CaptureState` → `DetailStatus`
- `SavedPhoto`
- `PhotoExif`
- `PhotoStore.isPublished()`
- `PhotoStore.galleryUriOf()`
- `FavoritesStore`

### 5.6 当前使用的公共组件

- `GlassSurface`
- `GlassButton`
- `GlassIconButton`
- `GlassIcon`
- `GlassDivider`
- `GlassType`
- `rememberThumbnail`
- `InfoCard`、`InfoRow`、`ActionCard`、`ActionItem`、`PrintBox`、`DevelopingRing`（页面私有）

### 5.7 可以改变的视觉部分

- 成片画框与状态区；
- 参数读数布局、单位、标签层级；
- 冲洗中/写入中的非百分比视觉；
- 错误和重试状态；
- 固定动作条、图标和标签；
- 应按真实照片方向选择画框比例，避免横版成片被固定 3:4 裁切。

### 5.8 不允许改变的业务部分

- 刚拍与历史照片共用同一详情目的地的语义；
- `CaptureState` → UI 状态映射；
- EXIF 四项读取与缺项 `—`；
- `Build.MODEL` 和文件大小来源；
- 收藏持久化；
- 分享 FileProvider；
- 系统相册 URI 查询；
- 重试重新按快门并复用上一次贴花设置；
- 不虚构照片序号或冲洗百分比。

---

## 6. `FILMS`

**文件：**

- `ui/films/FilmLibraryScreen.kt`
- `navigation/AppViewModel.kt`

**职责：** 展示当前胶卷和所有可解析胶卷包；显示设备支持情况、该胶卷现存张数和最近成片；允许换到另一支胶卷并返回相机。

### 6.1 当前布局结构

1. 页头“胶卷”，副标题为已安装数量与“点一下即装卷”；
2. 右上重新扫描；
3. `LazyColumn`；
4. 首项为当前胶卷大卡：卷名、pack 尺寸、现存张数、最近缩略图或胶卷筒图标；
5. 之后每支胶卷一行：缩略图/胶卷筒、卷名、尺寸与张数、支持原因、选中勾或进入箭头；
6. 底部预留 TabBar 空间；
7. 无卷时显示重新扫描空态。

### 6.2 实际显示的数据

- `FilmCard.label`；
- `FilmCard.frameLabel`：pack 真实帧尺寸；
- `FilmCard.supported` / `reason`；
- `FilmCard.shots`：现存照片数；
- `FilmCard.latestThumbnail`；
- 当前选中 id；
- 无历史照片时不伪造样片，显示胶卷筒图标。

### 6.3 用户可进行的操作

- 重新扫描 pack；
- 点支持且非当前的行选中该胶卷；
- 点当前卷大卡回相机；
- 通过 TabBar 去其它页面；
- 不可用卷不可选中。

### 6.4 交互状态

- 有卷；
- 无卷；
- 当前卷支持；
- 当前卷不可支持（数据异常/分辨率变化）；
- 不可支持卷灰字说明；
- 有/无最近缩略图；
- 当前扫描过程没有独立 loading 状态。

### 6.5 依赖的 ViewModel / State

- `AppViewModel.filmCards`
- `AppViewModel.cameraUi.activeStock`
- `AppViewModel.selectStock()`
- `AppViewModel.refreshStocks()`
- `FilmStockCatalog`
- `CameraState.Bound.supportedSizes`

### 6.6 当前使用的公共组件

- `GlassSurface`
- `GlassIconButton`
- `GlassIcon`
- `GlassChip`
- `rememberThumbnail`
- `FilmCard`、`CurrentStockCard`、`FilmRow`、`StockFrame`、`CanisterGlyph`、`EmptyLibrary`（页面内组件）

### 6.7 可以改变的视觉部分

- 当前卷与候选卷的层级；
- 胶片筒、样片画格和尺寸标签；
- 支持/不可用状态；
- 选中态与换卷反馈；
- 页头、空态和重新扫描动作；
- “可用数量”文案应修正或改为准确表达。

### 6.8 不允许改变的业务部分

- pack 扫描与内置 pack 落盘；
- 支持判断只看真实采集尺寸能否服务 pack；
- 选卷只是切换筛选，不重置任何计数、不删除照片；
- 张数来自现存成片；
- 最近缩略图来自真实成片；
- 不预测胶卷成像、不补 ISO/价格/工艺字段；
- 不支持卷不能通过视觉叠层伪造成可点。

---

## 7. `DISCOVER`

**文件：**

- `ui/discover/DiscoverScreen.kt`
- `ui/AppRoot.kt`

**职责：** 提供本机照片档案概览，并明确当前没有在线内容源。

### 7.1 当前布局结构

1. 页头“发现 / 本机档案概览”；
2. 大统计面板：现存照片总数；
3. 同一面板下方三列：收藏数、胶卷数、照片占用；
4. 说明卡：“为什么这里没有内容流”；
5. 底部预留 TabBar。

### 7.2 实际显示的数据

- `roll.size`：现存成片数；
- 现存照片中属于收藏集合的数量；
- `filmCards.size`：已安装/解析的胶卷数；
- `PhotoStore.usedBytes()`：仅私有 `full/` 照片总字节；
- 无网络权限和没有在线内容源的说明。

### 7.3 用户可进行的操作

- 阅读统计；
- 滚动；
- 通过 TabBar 离开。

### 7.4 交互状态

- 有数据；
- 全零数据；
- 当前无 loading、error 或内容流状态；
- 统计随相册/收藏/胶卷状态变化。

### 7.5 依赖的 ViewModel / State

- `AppViewModel.roll`
- `FavoritesStore`
- `AppViewModel.filmCards`
- `AppViewModel.storageBytes`

### 7.6 当前使用的公共组件

- `GlassSurface`
- `GlassDivider`
- `GlassIcon`
- `Stat`（页面私有）

### 7.7 可以改变的视觉部分

- 统计卡、数字、单位、说明区和图标；
- 仪表盘式呈现；
- 零数据状态；
- 页面标题可保持“发现”，但视觉不能暗示存在未实现的在线内容。

### 7.8 不允许改变的业务部分

- 所有数字的真实来源；
- 只统计现存照片；
- 占用只统计全尺寸私有副本；
- 不新增网络请求、内容流、账户或假推荐；
- 不改变目的地和 Tab 位置。

---

## 8. `SETTINGS`

**文件：**

- `ui/settings/SettingsScreen.kt`
- `camera/ImprintSettings.kt`
- `camera/ImprintPrefs.kt`
- `ui/AppRoot.kt`
- `res/values/strings.xml`

**职责：** 提供真实可持久化的片边/日期后背开关、胶卷重扫、应用和照片统计、许可声明以及 debug/benchmark 的成像自测入口。

### 8.1 当前布局结构

1. 页头“设置 / 本机数据与声明”；
2. 纵向滚动分组；
3. “成像”组：片边开关、日期后背开关、固定半格画幅说明；
4. “胶卷”组：重新扫描、胶卷文件位置；
5. “关于”组：开源与素材声明、本机照片数、版本；
6. debug/benchmark 额外显示“诊断”：运行成像自测；
7. 无网络/本机存储说明卡；
8. 底部预留 TabBar；
9. “开源与素材声明”是全屏覆盖内容，内部有独立 BackHandler。

### 8.2 实际显示的数据

- `ImprintSettings.filmEdge` / `dateBack`；
- 片边固定为半格 135、24×18mm；
- 已安装胶卷数；
- 胶卷文件目录；
- 许可资源文案；
- 本机照片数；
- `BuildConfig.VERSION_NAME`；
- debug/benchmark 才显示的自测入口；
- 无虚假开关、账户、主题或动态取色。

### 8.3 用户可进行的操作

- 开关片边/日期后背，整行可点；
- 重新扫描胶卷；
- 打开开源与素材声明；
- 返回关闭声明；
- debug/benchmark 运行成像自测；
- 通过 TabBar 离开。

### 8.4 交互状态

- 开关开/关；
- 重扫即刻 Toast；
- 声明覆盖内容有独立返回；
- 无加载状态；
- 无错误状态；
- 发布包不显示诊断入口。

### 8.5 依赖的 ViewModel / State

- `ImprintSettings`
- `ImprintPrefs`
- `AppViewModel.filmCards.size`
- `AppViewModel.roll.size`
- `BuildConfig.VERSION_NAME`
- `BuildConfig.DEBUG`

### 8.6 当前使用的公共组件

- `GlassSurface`
- `GlassIconButton`
- `GlassIcon`
- `GlassDivider`
- Material 3 `Switch`
- `Group`、`LinkRow`、`StaticRow`、`ToggleRow`、`LicensesScreen`、`LicenseSection`（页面内组件）

### 8.7 可以改变的视觉部分

- 分组结构、仪器式开关、行高与层级；
- 设置项图标、数字/目录的呈现；
- 开源声明排版；
- 诊断分组呈现；
- 整个页面可以改得更像暗房设备配置，而不是 Material 设置模板。

### 8.8 不允许改变的业务部分

- 两个开关的实际写入与下次启动恢复；
- 贴花在快门时快照，失败不影响保存；
- 胶卷重扫；
- 真实统计与版本来源；
- debug-only 自测入口条件；
- 许可声明内容和入口不可删除；
- 页面不得出现未持久化或不影响成像的假开关。
