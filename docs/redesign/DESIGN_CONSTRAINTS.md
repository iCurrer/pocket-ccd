# Pocket CCD — 视觉改版技术约束

**基线：** `3166831` / 0.8.8，2026-11-03  
**适用对象：** 后续视觉实现者、评审者与测试者。  
**约束原则：** 视觉可以重建，业务事实、时序和数据所有权不能重建。

## 1. 权限与依赖边界

### 1.1 当前权限

- `android.permission.CAMERA`
- 依赖库带入的普通权限不属于产品功能；
- 没有 `INTERNET`、位置、麦克风、通讯录或读写媒体权限；
- API 29+ 通过 MediaStore 发布本应用创建的图片，不需要存储权限。

### 1.2 当前 UI 依赖

- Jetpack Compose + Material 3；
- CameraX `Preview`、`ImageCapture`、`camera-view`；
- `io.github.kyant0:backdrop` 实现真实玻璃采样；
- Material Symbols Rounded 的本地矢量资源；
- 系统 sans/mono 字体，不打包字体。

### 1.3 视觉改版禁止

- 不新增网络、账户、分析或 UI 框架依赖；
- 不为视觉效果引入远程图片或在线内容；
- 不新增产品功能；
- 不删除业务组件；
- 不修改导航；
- 不修改相机、显影、图像处理、照片存储、收藏或对账逻辑；
- 不以视觉状态覆盖、伪造或绕过真实状态。

## 2. AppRoot 与页面挂载

`PocketCcdRoute()` 是应用外壳，持有相机句柄和相机 UI 本地状态。`AppViewModel` 持有导航、拍摄和照片数据。

### 2.1 相机挂载规则

```text
captureVisible =
    destination == CAMERA
    || (destination == DETAIL && detailOrigin == CAMERA)

cameraBound = captureVisible && hasCameraPermission
```

- `DETAIL` 从快门进入时，底部 `CameraScreen` 必须继续挂载，等待异步 `takePicture`；
- `DETAIL` 从相册进入时不能挂载相机，避免点亮相机；
- 离开 `captureVisible` 时，句柄清空、`CameraState` 回到 Pending、CameraX 解绑；
- 纸面层需要吃点击，避免点击穿透到下层取景器；
- 详情/取景期间保持屏幕常亮，离开解除。

### 2.2 相机打开顺序

1. 首次请求 CAMERA 权限；
2. 相机页进入横屏；
3. 方向变化触发 `cameraNonce++`；
4. `CameraPreview` 绑定生命周期；
5. 新会话产出 `CameraHandle`；
6. ViewModel 接收支持尺寸、闪光灯能力；
7. UI 回填 flash、zoom、EV；
8. PhotoStore 与相册对账不参与相机绑定。

任何视觉重构不能打乱此生命周期。

## 3. CameraX 约束

### 3.1 Preview

- `PreviewView.ScaleType.FIT_CENTER`；
- 预览流固定 4:3：`RATIO_4_3_FALLBACK_AUTO_STRATEGY`；
- 取景窗是 4:3，横屏时贴合画面，竖屏时按 3:4 取景窗显示；
- 预览必须保持原色，只允许纯 UI 暗角、网格、对焦框和状态覆盖；
- 不得在预览上应用胶片 LUT、`ColorMatrix` 近似色或实时 Fotufilm。

### 3.2 ImageCapture

- `CAPTURE_MODE_MAXIMIZE_QUALITY`；
- `targetRotation = Surface.ROTATION_0`；
- 下发 pack 目标尺寸时使用 `CLOSEST_HIGHER_THEN_LOWER`；
- `supportedSizes` 由实际 `attachedSurfaceResolution` 探测；
- 首次绑定的尺寸策略与轮询不能因 Compose 布局改变；
- 回调在进程级后台单线程执行，字节复制和 EXIF 解析不回到主线程；
- `ImageProxy.close()` 必须始终发生。

### 3.3 采集尺寸与裁切

- 设备采集尺寸 **大于或等于** pack：允许中心裁切到 pack 尺寸；
- 设备采集尺寸等于 pack：直接使用；
- 设备采集尺寸小于 pack：明确失败；
- 任何情况下不得缩放；
- 视觉层不能答应“帮你裁一点/补一点”而改变这一规则。

### 3.4 快门超时

- `takeJpeg()` 5 秒超时；
- 超时抛 `CaptureTimeoutException`，释放 busy 状态并请求重绑；
- 视觉层可以改变错误表达，但不能取消超时、自动重复拍摄或改变失败语义。

## 4. 强制方向

### 4.1 目的地方向

| 目的地 | 方向 |
|---|---|
| `CAMERA` | `SCREEN_ORIENTATION_SENSOR_LANDSCAPE` |
| `GALLERY` | `SCREEN_ORIENTATION_PORTRAIT` |
| `DETAIL` | `SCREEN_ORIENTATION_PORTRAIT` |
| `FILMS` | `SCREEN_ORIENTATION_PORTRAIT` |
| `DISCOVER` | `SCREEN_ORIENTATION_PORTRAIT` |
| `SETTINGS` | `SCREEN_ORIENTATION_PORTRAIT` |

### 4.2 为什么不能“顺便改回全竖屏”

- 拍照页当前按横版相机布局；
- `ImageCapture.targetRotation` 仅在绑定时取一次；
- 不重绑就切换方向会产生不报错的 90° 错图；
- 相册缩略图/详情应按真实图片比例展示，不能以“强制方向”为理由裁切照片。

视觉改版可优化横竖布局与转场，但不能只改 manifest 或移除重绑。

## 5. 闪光灯、变焦、EV、网格

### 5.1 闪光灯

- `CameraHandle.hasFlash` 来自 CameraX；
- `flashOn` 是 UI 层状态；
- 每次新 `CameraHandle` 都调用 `setFlash(flashOn)`；
- 无闪光灯时按钮禁用，不允许视觉显示为开启。

### 5.2 变焦

- 真实档来自 `physicalCameraInfos` + `intrinsicZoomRatio`；
- 只保留 ≥0.95× 的档，`requestZoom` 下界为 1×；
- 探不到物理镜头时退回 1×/2×；
- 显示倍率不代表系统一定切换到了该物理镜头；
- 不得用 UI transform 缩放预览伪装变焦；
- 重绑后重新请求界面记住的倍率。

### 5.3 EV

- 范围、步长和当前 index 来自 `ExposureState`；
- 不支持时隐藏整个入口；
- 每次改变真实 index 一档或一步；
- 重绑后回填；
- 可改读数、刻度、面板和拨轮，不可写死 `±2` 或伪造连续滑杆。

### 5.4 九宫格

- 纯 UI overlay；
- 状态可在重建中保存；
- 不可进入成片像素；
- 状态改变不触发相机重绑。

## 6. 快门后的 Fotufilm 显影流程

### 6.1 状态机

```text
Idle
  └─ capture() → Capturing(stock)
       └─ takeJpeg()
            └─ Developing(stock)
                 └─ save() 开始 → Saving(stock)
                      └─ Done(photo) | Failed(message, stock)
```

### 6.2 状态顺序

- `capture()` 必须同步把状态置为 Capturing；
- 先设置状态，后导航；
- 详情页首帧不能短暂显示上一张 Done；
- 同一时刻只允许一张在途照片；
- 失败重试是新的拍摄，不覆盖上一张。

### 6.3 图像数据流

```text
ImageCapture JPEG bytes
  → readPhotoExif(bytes)
  → decodeJpeg(bytes, pack size)
       equal → use
       larger → center crop
       transposed + EXIF → rotate to pack orientation
  → FilmEngine.render()
  → post-render rotation by corrected sensor/display angle
  → imprint
  → full JPEG quality 95
  → EXIF write
  → MediaStore publish
  → 1440-long-edge thumbnail quality 90
  → SavedPhoto
```

### 6.4 顺序不可交换

- 显影必须在 pack 尺寸上完成，然后才旋转；
- EXIF 写入必须在 full 文件落盘后、复制相册前；
- 相册复制必须从 final full 文件按字节复制；
- 缩略图必须从旋转、贴花后的最终图像生成；
- 贴花失败必须降级为未贴花保存，不能阻断成片；
- 缩略图失败不能把已落盘的全图报告成整次拍摄失败。

### 6.5 贴花快照

- `ImprintSettings` 在快门触发时快照；
- 冲洗过程中修改设置不影响在途照片；
- 重试复用上一次快照；
- 只应用一次，不存在二次导出路径。

## 7. PhotoStore 存储与相册发布

### 7.1 双副本

| 副本 | 路径/机制 | 角色 |
|---|---|---|
| 私有 full | `getExternalFilesDir(Pictures)/pocket-ccd/full/` | 照片列表权威来源 |
| 私有 thumb | 同根 `thumb/` | 列表/详情缩略图 |
| 系统相册 | MediaStore `Pictures/Pocket CCD/` | 外部分享、查看、备份 |

### 7.2 写入保证

- 使用 `.tmp → fsync → rename`；
- 相册写入使用 `IS_PENDING=1 → copy → IS_PENDING=0`；
- 失败删除 pending 条目；
- 缩略图与 full 分开存储；
- 文件命名包含快门时间和 stock id；
- 相册发布失败仍有私有副本，分享可走 FileProvider。

### 7.3 对账

- 用户在系统相册删除后，私有副本跟随删除；
- 补齐未完成时不对账；
- MediaStore 查询失败或空索引时一张都不删；
- 发布失败的照片不删；
- 刚拍 2 分钟内不删；
- 相册名称或时间戳任一匹配即认为存活；
- 对账删除不对称恢复；当前不在 App 内提供删除/从回收站恢复。

**视觉层禁止：**

- 把“已保存”直接等同为“已写入系统相册”；
- 在没有 MediaStore URI 时显示可点的“在相册打开”；
- 以视觉占位假装照片已同步。

## 8. FavoritesStore

- 收藏以照片 id 集合存于 `SharedPreferences`；
- id 来源于成片记录；
- 收藏判断与相册条目匹配；
- 若照片被对账删除，收藏 id 当前不会主动清理；
- 收藏切换返回新集合供 Compose 更新；
- 视觉改版可改变心形/按钮，但不可改成临时内存状态。

## 9. 真实 EXIF

### 9.1 支持的字段

- 物理焦距 `focalLengthMm`
- 光圈 `fNumber`
- 曝光时间 `exposureSeconds`
- ISO

### 9.2 数据来源

- 相机原始 JPEG 字节读取；
- 随 `CapturedFrame.exif` 传递；
- `PhotoStore` 写入 final JPEG；
- 详情页从文件重新读取。

### 9.3 视觉规则

- 缺项显示 `—`；
- 不补设计稿值；
- 不把相机倍率当物理焦距；
- 不把系统模型当光学参数；
- `Build.MODEL` 可以显示，但不可伪造为品牌营销名；
- 不要显示无真实来源的镜头型号。

## 10. Gallery 筛选和查看器

### 10.1 筛选

- `All`
- `Favorites`
- `Stock(label)`

选项由当前照片数据动态生成；没有照片的胶卷不生成筛选项。

### 10.2 顺序

- 按胶卷分组；
- 组间按最新照片时间倒序；
- 组内按照片时间倒序；
- 查看器接收同一筛选结果的扁平列表；
- 打开查看器时冻结该列表；
- 收藏变化不导致当前查看页被抽走。

### 10.3 查看器索引

- 大页数乘以真实条目数模拟循环；
- 打开时定位到用户点击照片；
- `circularIndex` 对负数和零长度安全；
- 查看器为空时自动关闭；
- 不添加捏合缩放，以免与水平滑动冲突。

### 10.4 解码

- 网格缩略图：IO、按显示尺寸采样、LRU；
- 查看器：优先 full、失败回退 thumbnail、不缓存大图；
- 任何视觉改动不得把 `BitmapFactory.decodeFile` 放回 composition 主线程。

## 11. AppState 导航状态机

### 11.1 状态

```text
AppUiState(
  destination = CAMERA,
  galleryOrigin = CAMERA,
  detailOrigin = CAMERA
)
```

实际使用 `detailOrigin` 决定详情返回。`galleryOrigin` 当前仍存在但不是返回规则的主依据。

### 11.2 转换

- `OpenCamera`：任何页面回相机；
- 四个平级页面：相机/其它 Tab 可进入；
- `OpenDetail`：仅 CAMERA 或 GALLERY；
- `NavigateBack`：DETAIL 回 detailOrigin；其它非相机页回相机；相机由系统 finish；
- 查看器与详情隐藏 TabBar。

### 11.3 视觉改版的禁止项

- 不把它替换成 Navigation-Compose；
- 不增加无需求的回退栈；
- 不改变平级页返回相机；
- 不改变从相册打开历史照片不发相机权限请求的行为；
- 不将查看器状态藏进 `GalleryScreen`；
- 不改快门先 capture 后导航的顺序。

## 12. 真实数据与合规

不得在视觉改造中引入：

- 假 ISO、光圈、曝光时间、焦距；
- 假胶卷价格、到期日、故事、工艺分类；
- 假容量、假剩余张数、假片号；
- 假冲洗百分比、假 LAB、假订单、假扫描进度；
- 假网络内容或社区数据；
- 假开关、未持久化的设置、无操作的按钮；
- 用渐变或抽象图片冒充某卷的真实样片。

必须保留：

- `files/film/` 路径与可替换说明；
- Fotufilm、MUAStudio 署名、许可证和修改声明；
- Backdrop、Material Symbols、Halide 等第三方 notice；
- 设置→开源与素材声明的可发现入口；
- 不阻止用户取出或替换 CC BY-SA 胶卷文件。

## 13. 性能与资源约束

- 相机页长时间停留，重绘应低开销；
- 背景采样/玻璃只应在有可采样内容时启用；
- 页面内容不能同时存在多个高成本全屏离屏录制；
- 缩略图必须采样，不加载 12MP 到网格；
- 查看器大图不做多页缓存；
- 冲洗期间主线程不可承担 JPEG 拷贝、EXIF 解析和长时间像素工作；
- 相机页首帧、页面切换、玻璃效果不能以牺牲采集回调或快门反馈为代价；
- 200% 字体不能以缩小字号解决；
- 所有可点控件至少 48dp，这是当前实现仍需补测/修正的红线。

## 14. 待确认事项

以下没有合法依据在本轮自行决定：

1. Darkroom Instrument 对“发现”页的命名是否仍保留为本地档案；
2. 横屏相机是否需要与竖屏一样提供上滑目的地抽屉；
3. 真实成片横图在相册/详情中采用完整显示还是统一裁切；
4. 日期后背仅有开关，日期顺序和画幅 UI 是否维持当前单向固定；
5. API 29/30 下静态玻璃是否接受为同一品牌视觉的低保真版本；
6. 平板/折叠屏是否允许真正双栏，而不是仅限制内容最大宽度；
7. 改版是否需要补充应用内删除或相册回收站恢复；这会改变业务，不属于本次视觉范围。

## 15. 完成标准

视觉改版不能以“编译通过”或“截图好看”单独完成。最低要求：

- 所有页面源码审计后的业务语义不变；
- 现有测试仍通过；
- 当前业务状态截图不退化；
- 真机完成相机方向、拍照、显影、写入、相册、详情与返回链路；
- 真实亮景可读、API 29/30 降级可读；
- 320dp、360dp、411dp、横屏、800dp、200% 字体核对；
- 逐页真机截图和证据记录；
- 对任何无法验证的项明确标记 `[H]` 或“待确认”，不得写成已通过。
