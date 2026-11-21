# Pocket CCD UI Redesign — 项目审计

**审计日期：** 2026-11-03  
**代码基线：** `3166831`（`main`，版本 0.8.8 / versionCode 21）  
**审计目标：** 在不改变相机、冲洗、存储、收藏、导航和产品功能的前提下，为 “Darkroom Instrument” 视觉改版建立事实基线和修改边界。

## 1. 结论摘要

Pocket CCD 目前是一套以 CameraX 实时原色预览为入口、以 Fotufilm 物理显影为成片差异、以本地双副本存储和相册对账为数据底座的胶片相机 App。当前界面是固定冷蓝强调色的深色玻璃拟态体系；它不是白纸档案、黑闸门或玩具机身体系。旧文档中这些命名仍大量存在，但相关 `ui/film/` 组件已在 D-069 后被整体替换。

本次视觉改版可以安全调整的重点是：

- 所有页面的层级、排版、间距、圆角、玻璃材质、图标尺寸与状态表现；
- 相机取景窗、工具区、底栏、抽屉和反馈层的视觉组织；
- 相册、详情、胶卷库、发现、设置的卡片与列表呈现；
- 现行冷蓝强调色替换为 Darkroom Instrument 的暖金属/琥珀色体系。

必须保持不变的是：

- CameraX `Preview` + `ImageCapture` 的绑定与参数；
- 拍摄页强制横屏、其它页竖屏及方向变更后的相机重绑；
- 原色预览，不在预览像素上叠加胶片滤镜；
- 快门、闪光灯、真实变焦、真实 EV、九宫格和点按对焦的行为；
- 快门后 `Capturing → Developing → Saving → Done/Failed` 状态流；
- Fotufilm 尺寸/裁切/旋转纪律、真实 EXIF、双副本写入与相册对账；
- `PhotoStore`、`FavoritesStore`、`ImprintPrefs` 的存储语义；
- `AppDestination` / `AppEvent` / `reduce` 的导航结果；
- 照片、收藏、胶卷文件、相册写入和合规声明不得被视觉层伪造或绕过。

## 2. 审计范围与证据

已逐文件核对：

- 根文档：`README.md`、`DECISION_LOG.md`、`TASK_BOARD.md`、`PROJECT_STATE.md`；
- UI：`app/src/main/java/com/pocketccd/prototype/ui/` 全部 Kotlin 文件；
- 导航：`navigation/AppState.kt`、`AppViewModel.kt`；
- 相机：`camera/` 全部 Kotlin 文件，并对照 `AndroidManifest.xml` 与 `MainActivity.kt`；
- 产品/设计/交互/验收文档：`docs/` 下的 UI 规格、交互规格、设计令牌、纸面/玻璃方案、密度评审、主链路评审、成片流程评审、交接文档、相机规则、设备适配和许可清单；
- 当前生成截图：`app/build/screenshots/` 中 2026-11-03 12:59 的 Robolectric 页面截图。

证据等级：

- **[F] 已核实：** 直接来自当前源码、当前决策或当前生成截图；
- **[I] 工程推断：** 由多个已核实事实推导，尚未上真机；
- **[H] 待确认：** 依赖真机、设备能力、外部状态或产品选择。

本审计未运行构建、未安装到真机、未执行 Accessibility Scanner。截图只证明 Robolectric/Compose 的静态布局，不证明真实传感器、真实玻璃模糊、性能或触感。

## 3. 当前产品与界面基线

### 3.1 产品行为

- 启动直接进入 `CAMERA`，没有首页。
- `CAMERA` 强制 `SENSOR_LANDSCAPE`；打开详情、相册、胶卷、发现、设置时切到竖屏。
- 其它页面底部有五项玻璃导航：相册、胶卷、拍摄、发现、设置。
- 详情页是压栈页；查看器是有独立关闭出口的模态层。
- 快门后立即进入详情页，详情页展示真实冲洗阶段；相机的异步采集不能在跳页时被解绑。
- 一“卷”的 36 张容量已被删除。当前只显示“这一支胶卷在本机现存多少张照片”和全库总量。

### 3.2 当前视觉语言

- 全 App 固定深色，不跟随系统壁纸取色。
- 冷蓝 Material 3 种子色，主强调色 `#A8C7FA`。
- Kyant Backdrop 负责玻璃真实背景采样；API 29/30 降级为着色加边缘高光。
- 页面背景由深底和两颗低透明色光组成；TabBar 是当前唯一真正采样页面内容做模糊/折射的控件。
- 摄影页以 4:3 取景窗为核心，竖屏居中、横屏左右侧工具排列。
- 相册是 3 列 3:4 缩略图；胶卷库是当前卷卡加行列表；详情是竖向大图、参数卡和底部动作条。

### 3.3 当前主要视觉问题

以下问题来自当前代码或当前截图，属于本次改版应解决的视觉/呈现问题，不等同于业务逻辑缺陷：

1. **冷蓝默认 Material 感偏重。** 强调色、选中态和开关轨道形成统一的系统蓝，没有暗房仪器的材料辨识度。
2. **玻璃层级过度依赖圆角胶囊。** 顶栏、筛选、按钮、底栏大量使用圆角容器，页面结构容易读成“玻璃卡片库”。
3. **相机横竖两套布局不共享同一几何语言。** 竖屏为顶部控件、底部 dock；横屏为左右工具列。功能一致，但扫描路径与视觉权重不同。
4. **横屏状态反馈覆盖不完整。** 当前横屏代码只在工具区显示贴花状态；`notice`、`blockReason` 以及通过竖屏 dock 打开的目的地抽屉没有完整对应入口。
5. **当前运行时没有传入贴花状态文案。** `CameraScreen` 有 `imprintLabel` 参数，但 `AppRoot` 调用处未传 `imprint.label()`；截图测试单独传了值。真实界面的“135 半格 · 日期”状态需要真机确认。
6. **照片比例仍按竖构图假设显示。** 相册格固定 `3:4 + Crop`，详情图框在 `photo != null` 时固定 `3:4 + Crop`。拍摄页现已强制横屏并产出 4:3 横图，真实成片在网格和详情中可能被明显裁掉。此项为视觉呈现问题，改版时应改为依据真实宽高比/方向呈现。
7. **胶卷库标题文案与数据范围不一致。** 标题写 `${cards.size} 支可用`，但 `cards` 同时包含 `supported = false` 的卷。截图里也能看到灰卷仍被计入“可用”。
8. **相册页存在未使用的回调。** `GalleryScreen` 接收 `onToggleFavorite` 与 `onOpenDetail`，但网格只调用 `onOpenViewer`；收藏和详情入口实际在查看器/详情页。
9. **加载态不统一。** 查看器初次解码时与解码失败都落到“这张照片读不出来”，没有独立加载指示；胶卷库没有“正在扫描”状态；只有相机、详情和空态有较为完整的显式状态。
10. **旧设计文档仍是多数文件的默认入口。** 当前代码对应 D-069 之后的玻璃体系；`film-dark-ui-spec.md`、`paper-archive-*.md` 和旧截图只能追溯历史，不能作为改版源文件。

## 4. 架构与状态地图

```text
MainActivity
  └─ PocketCcdRoute (AppRoot.kt)
      ├─ AppViewModel
      │   ├─ AppUiState: destination / detailOrigin
      │   ├─ CaptureState: Idle / Capturing / Developing / Saving / Done / Failed
      │   ├─ CameraUiState: stocks / activeStock / camera / canShoot / programmedSize
      │   ├─ roll: 现有 SavedPhoto
      │   ├─ filmCards: 每支胶卷支持情况、现有张数、最近缩略图
      │   └─ storageBytes
      ├─ CameraScreen + CameraPreview
      │   └─ CameraHandle: JPEG / focus / zoom / EV / flash / session probe
      ├─ GlassBackdrop
      │   ├─ content: 相册 / 胶卷 / 发现 / 设置 / 详情
      │   └─ overlay: 底部 TabBar + PhotoViewer
      └─ PhotoStore / FavoritesStore / ImprintPrefs
```

关键页面入口：

| 页面 | 文件 | 上游状态 |
|---|---|---|
| `CAMERA` | `ui/camera/CameraScreen.kt` | `AppViewModel.cameraUi`、`capture`、`filmCards`；`AppRoot` 的硬件句柄与变焦/EV/闪光/网格状态 |
| `GALLERY` | `ui/gallery/GalleryScreen.kt` | `roll` 转成的 `PhotoWallItem`、`FavoritesStore` |
| 查看器模态 | `ui/gallery/PhotoViewer.kt` | 筛选后的 `ViewerRequest`、收藏集合 |
| `DETAIL` | `ui/detail/PhotoDetailScreen.kt` | `CaptureState` 或按 id 找到的 `SavedPhoto`、`PhotoExif`、收藏、相册发布状态 |
| `FILMS` | `ui/films/FilmLibraryScreen.kt` | `AppViewModel.filmCards`、`activeStock.id` |
| `DISCOVER` | `ui/discover/DiscoverScreen.kt` | `roll.size`、收藏数、`filmCards.size`、`storageBytes` |
| `SETTINGS` | `ui/settings/SettingsScreen.kt` | 张数、胶卷数、版本、`ImprintSettings`、debug 自测入口 |

## 5. 审计发现：改版前必须处理或明确接受的事项

### 5.1 文档漂移

- `PROJECT_STATE.md` 的主体仍停留在 2026-10-17 的工程准备阶段；当前实现的依据应以 README、D-069 之后决策、源码和当前截图为准。
- `DECISION_LOG.md` 的决策总览没有完整列入 D-069 之后的新决策，正文却已继续追加。改版文档应直接引用具体 D 编号，不依赖总览表。
- `AndroidManifest.xml` 顶部注释仍写“不写系统相册”；当前 `PhotoStore` 明确写系统相册副本。视觉改版不修逻辑，但文档不得沿用旧注释。
- `TASK_BOARD.md` 的 M9 与 G 系列记录大多是旧纸面/玻璃迭代，且大量“代码完成、真机未验”；它不能替代当前真机验收。
- `docs/design-tokens.md` 是已废弃的玩具相机令牌，不是当前 `ui/theme/Tokens.kt` 的说明。

### 5.2 相机页真机待确认

- 真实 `PreviewView` 与横屏工具列之间的覆盖、遮挡和对焦坐标；
- 真实玻璃在 SurfaceView 上方不可采样时，降级静态玻璃是否仍能保证亮画面可读；
- `sensorLandscape` 下四种物理旋转的布局与拍摄方向；
- `blockReason`、换卷提示、贴花状态在横屏中的可发现性；
- 从横屏相机切到竖屏相册再回来的转向抖动、闪光/变焦/EV 恢复；
- 320dp 窄屏/800dp 平板的真实触控与视觉密度。

### 5.3 相册与详情真机待确认

- 横版成片在当前 3:4 网格/详情框中的裁切程度；
- 真实全尺寸 JPEG 与 1440 长边缩略图在大图查看时的清晰度；
- 系统字体放大后的过滤器横向滚动、详情动作条与参数卡；
- 亮照片、黑照片、损坏缩略图、缺失原图下的玻璃可读性；
- 查看器打开/关闭时 TabBar 显隐和返回手势；
- 50+ 照片、快速滚动时的缓存和首帧表现。

### 5.4 页面文案/呈现待修正

- 胶卷库的“可用”数量应只统计 `supported`，或改成中性的“已安装”；
- 相机页横屏应补齐已存在状态的可视化位置；
- 相册/详情的图片框应跟随真实图片宽高比，视觉上不应用固定 3:4 裁切；
- 查看器应区分“正在读取”和“读取失败”；
- 若继续保留相机内“135 半格 · 日期”提示，必须在 `AppRoot` 真实传入状态；
- 发现页是否保留标题“发现”而内容只是本机统计，属于产品命名待确认，不应在视觉层擅自扩展成内容社区。

## 6. 后续视觉改版的主要修改对象

优先级从高到低：

| 优先级 | 文件 | 原因 |
|---|---|---|
| P0 | `ui/theme/Theme.kt`、`Tokens.kt`、`Type.kt` | 决定全 App 的色板、间距、形状、字体与材质规则 |
| P0 | `ui/glass/Glass.kt`、`GlassBackdrop.kt`、`GlassNavBar.kt`、`GlassIcons.kt` | 决定玻璃的“仪器感”与全局组件语法 |
| P0 | `ui/camera/CameraScreen.kt` | 首屏、横屏排版、取景框、所有相机控件和状态反馈 |
| P1 | `ui/gallery/GalleryScreen.kt`、`PhotoViewer.kt`、`GalleryImages.kt` | 相册网格、缩略图、全屏查看器和模态层级 |
| P1 | `ui/detail/PhotoDetailScreen.kt` | 成片展示、EXIF、冲洗/写入/失败状态、动作条 |
| P1 | `ui/films/FilmLibraryScreen.kt` | 当前卷、候选卷、支持/不可用状态与缩略图 |
| P1 | `ui/settings/SettingsScreen.kt` | 设置分组、真实开关、许可页 |
| P2 | `ui/discover/DiscoverScreen.kt` | 本机档案统计的视觉改造 |
| P2 | `ui/AppRoot.kt` | 只改壳层、遮罩、通用转场和视觉接线；生命周期/导航语义保持不变 |

`navigation/AppState.kt`、`navigation/AppViewModel.kt` 与 `camera/*` 不应作为本轮视觉文件修改对象；若改版真的需要其新增数据字段，应单独立项并补测试。

## 7. 验收基线

改版至少应满足：

1. 六个页面与查看器在 320dp、360dp、411dp、横屏 804×361dp 和 800dp 宽屏上无遮挡、无截断；
2. “至少 48dp 热区”保持，并补测横向控件间距；
3. 200% 字体下不靠缩字号解决溢出；
4. 真实亮画面、黑白照片、空相册、权限拒绝、相机不可用、冲洗中、写入中、失败均有可识别呈现；
5. 拍照 → 显影 → 写入 → 详情/相册 → 系统相册链路未回归；
6. 闪光灯、网格、真实变焦、EV、点按对焦在重绑后状态一致；
7. 收藏在重启后仍存在；相册筛选和查看器顺序不变；
8. 不出现虚构的胶卷参数、容量、冲洗百分比、网络内容或未接入动作；
9. API 29/30 静态玻璃降级仍可读；
10. 至少一次真实设备逐页截图和一次完整拍摄链路验收后才可标记视觉版“已通过”。

本审计文档不代表上述验收已通过。
