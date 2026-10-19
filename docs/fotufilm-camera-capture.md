# FOT-007 · 相机接入（预览 → 快门 → 冲洗 → 保存）

> 状态：**代码与构建已完成，真机端到端待用户手测**。
> 本文只记录已经落到代码里的行为与实测过的构建事实；真机结果留待手测后回填。

## 1. 链路

```
CameraStageScreen（取景框 slot）
  └─ CameraPreview            CameraX Preview + ImageCapture
       ├─ PreviewView         FIT_CENTER，完整 4:3
       └─ CameraHandle        suspend takeJpeg() / setFlash()
            ↓ 快门
AppViewModel.capture()
  ├─ Capturing   同步置位，随后才导航到成片页
  ├─ takePicture JPEG → ByteArray（内存，不落盘）
  ├─ decodeJpeg  EXIF 处理 + 尺寸校验（不缩放）
  ├─ FilmEngine.render(strip 分块) → 成品 Bitmap
  ├─ PhotoStore.save()  全尺寸 JPEG + 1440 缩略图，临时文件 rename
  └─ Done/Failed → 成片页；同时插入照片墙
```

快门按下时**先**把状态置为 `Capturing`，**再**发导航事件。顺序反了会让成片页
第一帧闪一下上一张的旧结果。

## 2. D-037 是怎么被落地的

D-037 的原文是"pack 尺寸 = 采集尺寸"。要正确执行它，得先把两个概念拆开：

- **像素密度（pixels-per-mm）不能变**。pack 里的扩散 / 晕光 / 颗粒半径是
  「毫米长度 × 该帧的 pixels-per-mm」烘出来的常数。密度一变，等于换了光学系统：
  颗粒变粗或变细、晕光半径错位、MTF 曲线整体平移。
- **画面尺寸可以变**。只要密度不变，从同一帧里裁一块出来，空间参数照样成立 ——
  这跟换个裁切系数是一回事。

所以 D-037 真正禁止的是**缩放**，不是**裁切**。落地成四道闸：

1. **`ImageCapture.setTargetRotation(Surface.ROTATION_0)`**
   不固定的话，竖屏拍照时 CameraX 会输出 3072×4096，pack 的横向尺寸直接对不上。
2. **绑定后读实际采集尺寸**（`ImageCapture.attachedSurfaceResolution`），
   判断它**能不能服务**当前卷：相等或更大都算能，更小就置灰快门。
3. **解码时尺寸适配**（`decodeJpeg`）：相等 → 直接用；更大 → 中心裁切；
   更小 → 抛错，消息里同时给出"设备只能给多少"和"这卷需要多少"。
4. **引擎侧硬校验**（`FilmEngine.render` 的 `require`）：喂进去的帧必须精确等于
   pack 声明的尺寸。这一层不再做任何适配，适配已经在第 3 步做完。

**从不缩放** —— 缩放出来的图看起来是对的，但物理上是假的，比失败更糟。

### 2.1 第一次绑定必须"不约束尺寸"

`CameraPreview(targetSize = ...)` 传进去的尺寸会走 `ResolutionStrategy`，
CameraX 找不到精确匹配时会退到 `CLOSEST_HIGHER_THEN_LOWER` —— 也就是
**静默换成另一个尺寸，并把这个会话锁死**。

真机上出过一次：进相机页时拿"当前默认卷"的 4000×3000 去约束，PJZ110 实际
绑定成 4096×3072；此后三卷 12MP 全是"精确不匹配"，快门永久按不下去。

所以现在的顺序是：

```
首次绑定 targetSize = null      → CameraX 给该镜头最大 JPEG
读回 attachedSurfaceResolution  → 得到真实尺寸
判断能不能服务当前卷            → 能才把尺寸下发（programmedSize）
```

下发时填的是 **pack 的尺寸**，不是设备实际尺寸：CameraX 若有这一档就直接给精确的，
没有也会给最接近的更高档，多出来的部分由 `decodeJpeg` 裁掉。所以即使尺寸被替换，
也不会再出现"锁死"——替换掉的尺寸只要更大就仍然可拍。

### 2.2 多机型：为什么答案是裁切，而不是缩放

不同设备的最大 JPEG 尺寸并不统一，而且**看起来更大的那个反而是更差的基准**：

| 设备最大 JPEG | 对 `4000×3000` 的 pack | 对 `4096×3072` 的 pack |
|---|---|---|
| 4096×3072（PJZ110 / OnePlus 13，实测） | 中心裁切，丢 1.5% 线性视野 | 精确 |
| 4000×3000（12MP 名义档，很常见） | 精确 | **太小，不可拍** |
| 4080×3060（部分华为 / 荣耀机型常见档） | 中心裁切 | **太小，不可拍** |
| 4608×3456（16MP） | 中心裁切，丢 13% | 中心裁切，丢 11% |
| 3264×2448（8MP 老机） | **太小，不可拍** | **太小，不可拍** |

（表中"常见档"是按公开的传感器阵列规格归纳的，不是逐机型实测枚举；新机型上手前
仍应跑一次 `dumpsys media.camera` 或用 `adb logcat -s PocketCcdCamera` 确认。）

两个结论：

1. **pack 基准应该尽量低，但不能低于目标机群的最低档**。`4000×3000` 是最低的
   常见 12MP 档，覆盖面比 `4096×3072` 大得多 —— 后者会把所有正好 4000×3000 的
   机型直接排除，而那批机器不少。
2. **裁切是唯一能"一份 pack 服务多种设备"的正确手段**。想再往下兼容（比如 8MP 机），
   只能另产一套更小尺寸的 pack，绝不能在运行时缩放。

### 2.3 裁切带来的取景偏差（已知限制）

裁切发生在**冲洗前的解码阶段**，取景器显示的仍是相机完整视野。所以裁切比例大时，
"看到的构图"会与"拍到的画面"不一致 —— 这与 FOT-009「预览不冒充成片」在同一方向上
冲突（FOT-017 是方向，这里是范围）。

小比例裁切（< 5%，例如 4096×3072 → 4000×3000 只丢 1.5% 线性）肉眼不可见，可以忽略。
比例更大时应当在取景器上叠加裁切框，或直接把预览按裁切后的 FOV 缩放 —— 这部分
需要改 `PreviewView` 的呈现方式，归入 FOT-009。

## 3. 三个与 CameraX API 有关的事实（都不是猜的，是编译/反编译确认的）

| 事实 | 依据 |
|---|---|
| `CameraInfo` **没有** `getSupportedResolutions` | 反编译 `camera-core-1.6.2` 的 `CameraInfo.class`，只有 `getSupportedFrameRateRanges` 等，没有任何分辨率查询方法 |
| 能拿到的是 `UseCase.getAttachedSurfaceResolution()`（deprecated 但可用） | 同上，`UseCase.class` 里唯一 public 的尺寸接口 |
| 它是异步出现的 | 绑定后要轮询等待 attach；这里 25ms × 40 次（1s）为上限，超时则退回 `targetSize` 继续走，让 `decodeJpeg` 用真实像素去判 |

`camera-view` 的 `PreviewView.ScaleType` 用 `FIT_CENTER` 而不是 `FILL_CENTER`：
取景框比 4:3 高，填满会裁掉左右，造成"看到的构图"与"拍到的画面"不一致。

## 4. EXIF 的取舍

`decodeJpeg` 的顺序是**先看像素尺寸、后看 EXIF**：

- 解码出来已经是期望尺寸 → 直接返回，不理会 EXIF；
- 尺寸不符 → 才按 EXIF 方向旋转补救，补救完再判一次；
- 仍不符 → 抛异常。

原因：不同机型在 `ROTATION_0` 下写不写 orientation tag 并不一致。跟着 EXIF 转，
可能把一个本来就正确的 4000×3000 转成 3000×4000。

## 5. pack 与相机分辨率的匹配

`FilmStockCatalog.scan()` 扫描 `Android/data/<pkg>/files/film/*.fswp`，逐个打开读
头部尺寸后立即 `close()`。相机绑定完成后 `AppViewModel.onCameraBound()` 会自动
切到**第一个尺寸精确匹配**的卷；一个都不匹配时快门禁用。

目录由 App 自己 `mkdirs()`。`adb shell mkdir` 建出来的目录属主是 shell、
模式 `drwxrws---`，App 透过 FUSE 列不出内容且不报错（这个坑在 FOT-004 已经踩过一次，
表现为 `packCount=0`）。

### 5.1 引擎夹具不许当胶卷

CI 的 parity 用一个 `example-negative-400__96x64.fswp` 跑 64 个 case。它一旦被
push 进 `film/`，文件名按字典序排第一，就成了"默认卷"——相机被约束到 96×64、
退成 1920×1440 绑定，随后没有任何 12MP 卷能匹配。

两道防线：

- `scan()` 有 `MIN_FRAME_PIXELS = 1_000_000` 地板，低于它的直接跳过并打日志
  （`FilmStockCatalog: 跳过 xxx：96×64 是引擎夹具，不是可拍胶卷`）；
- 即使混进来，2.1 的"首次不约束"也保证它不会把相机带偏。

夹具仍然可以留在设备上做引擎验证，但**不要放进 `files/film/`**。

## 6. 已知限制（不是 bug，是排期）

| 限制 | 归属 |
|---|---|
| 预览是普通画面，不做实时滤镜 | 设计如此（D-036） |
| 冲洗 2–3 秒没有进度条，只有状态点 | FOT-009 |
| 取景框是 4:3 横向构图，竖屏下上下留边 | FOT-009（需要重新设计取景框比例） |
| 色彩口径仍是线性 + sRGB 基色，未做 Rec.2020 | FOT-006 |
| 照片墙缩略图同步解码，条数多了会卡顿 | FOT-011 |
| 现役 pack 是 4000×3000，PJZ110 只给 4096×3072，因此在该机上不可拍 | FOT-016（需重产 pack） |

## 7. 手动测试清单

前置：设备已安装 APK，且 `files/film/` 下有**尺寸等于本机最大 JPEG** 的
`*.fswp`。PJZ110 是 `4096×3072`；换机时先按下面第 3 条看日志确认，再决定 push
哪一套 pack。

1. 打开 App → 首页 → **下拉**进入拍摄页
2. 首次会弹相机权限 → 允许
3. 观察取景框：**应该是真实画面**（不是静态渐变占位），下方卷名小圆点为绿色。
   同时抓一行日志确认实际采集尺寸：
   `adb logcat -s PocketCcdCamera` → `camera bound: ... attached=4096x3072 ...`
4. 点卷名可切卷；若没有与 `attached` 精确相等的卷，快门会置灰，并在快门与
   "POCKET CCD" 之间显示 `相机不支持 4000×3000，最大 4096×3072`
5. 按快门 → 跳到成片页，状态 `PROCESSING` → `WRITING FILE` → `SAVED`
6. 成片应显示为 4:3 照片，下方有 `4096×3072 · Xms · N strips` 诊断行
7. 点 `PHOTO WALL` → 照片墙出现刚拍的那张（缩略图）
8. 返回拍摄页再拍一张，验证可以连续拍
9. 关掉 App 重开 → 照片墙仍有历史照片（落盘在 `files/Pictures/pocket-ccd/`）

抓日志：

```bash
adb logcat -c
adb logcat | grep -iE "pocketccd|CameraX|Fotufilm|AndroidRuntime"
```

文件落地检查：

```bash
adb shell ls -l /sdcard/Android/data/com.pocketccd.prototype/files/Pictures/pocket-ccd/full/
```

## 8. 失败时的判读

| 现象 | 最可能原因 |
|---|---|
| 取景框仍是静态渐变 | 权限没给；或 `CameraPreview` 绑定失败（`blockReason` 会显示原因） |
| 快门灰着，提示"正在启动相机" | 绑定尚未完成，等 1 秒 |
| 提示"没有可用的胶卷" | `files/film/` 空或文件读不出（用设置页的"胶卷"行点一下重扫） |
| 提示"相机不支持 W×H" | 设备采集分辨率没有该尺寸 → 需要按提示的尺寸重新产 pack（CI 的 `pack_size` 输入） |
| `采集尺寸 A 与 pack 的 B 不一致` | 上面那条的运行时表现，说明前置判断超时退回了 `targetSize` |
| `原生内核不可用` | `.so` 没打进 APK（检查 `lib/arm64-v8a/libfotufilm_android.so`） |
| 成片全黑/全白 | 内核输出异常，需要看 `nonFinite` 与 `min/max/mean`（成片页目前只显示耗时） |

## 9. 成片方向：为什么必须"冲洗完再转"（FOT-017，已修）

`setTargetRotation(Surface.ROTATION_0)` 让 JPEG 像素恒为**传感器自然方向**
（4096×3072），从而与 pack 精确对齐（D-037）。代价是竖持拍摄时成片侧躺 90°，
与 FOT-009「预览不冒充成片」冲突。

**修法：冲洗不动，写完再转。** 方向补偿分两步落在两处：

| 阶段 | 位置 | 做什么 |
|---|---|---|
| 按下快门瞬间 | `CameraHandle.takeJpeg` | 读出此时该转多少度，随 JPEG 一起返回（`CapturedFrame.rotationDegrees`） |
| 冲洗完成后 | `PhotoStore.save` | 按该角度旋转成品位图，再从**旋转后**的图生成缩略图 |

关键约束与理由：

- **角度必须在按快门那一刻取**。冲洗要 2–3 秒，用户拍完就转手机是常事，
  等冲洗时再读 `display.rotation` 会拿到错的姿势。
- **不能冲洗前转**。pack 的扩散/晕光/颗粒半径已按 4096×3072 烘成像素数，
  先转成 3072×4096 会直接对不上。旋转发生在效果烘焙之后，像素只是换摆法，
  颗粒与晕光的尺度不受影响。
- **不能只转全尺寸**。缩略图必须从旋转后的图生成，否则「大图正、小图躺」。
- **用 `SENSOR_ORIENTATION` 而不是 CameraX 的 `sensorRotationDegrees`**。
  后者在 `targetRotation = ROTATION_0` 时返回 0（它认为已经正立），
  与本机实测「像素是传感器原始方向」矛盾。`SENSOR_ORIENTATION` 是硬件特性，
  不会被推导规则影响：

  ```
  后置：(sensor - displayRotation*90 + 360) % 360
  前置：(sensor + displayRotation*90) % 360    // 前置镜像，方向相加
  ```

  真机取值：`sensorOrientation=90`、竖持 `display=0` → **顺转 90°**。
  日志一行可查：`adb logcat -s PocketCcdCamera` →
  `capture rotation: sensor=90 display=0 -> 90°`。

实测（`PJZ110`，2026-10-19 14:40:57 PORTRA 400）：

| 文件 | 修复前 | 修复后 |
|---|---|---|
| `full/*.jpg` | 4096×3072，侧躺 | **3072×4096，正立** |
| `thumb/*.jpg` | 1440×1080，侧躺 | **1080×1440，正立** |

代价是多一份 ~50MB 的临时 ARGB 位图（12MP），旋转后原图由调用方立刻 recycle。
在 2.38GB 可用内存上实测未触发 GC 抖动，但这是**下一次内存优化要盯的点**。

历史遗留：修复前拍的那些照片仍是横的、且无 EXIF 标记，不会自动纠正。
文件名里的 `__4096x3072` 是 **pack 尺寸**（卷的标识），不是成品像素尺寸。

### 曾考虑但没采用的方案

1. **只写 EXIF 方向标记**：最省内存，但依赖下游读 EXIF，自家照片墙也得跟着改，
   且任何忽略 EXIF 的工具（部分相册、上传接口）看到的还是躺的。
2. **改用横向取景框**：等于改设计，承认这就是台横构图的机器。留给 FOT-009 重新设计
   取景框比例时再评估。


## 10. 成片写两份：私有目录 + 系统相册（FOT-019）

### 为什么必须写相册

早先 `PhotoStore` 只写 `Android/data/<pkg>/files/Pictures/pocket-ccd/`。这个选择
有两个好处：照片墙天然只显示本应用拍的照片、不申请任何存储权限。但它有一个
致命后果 —— **`Android/data` 被媒体扫描明确排除，系统相册永远看不到这些文件**。
相机拍出来的照片在相机之外完全不可见：不能分享、不能在微信里选图、不能备份。
对一台"拿出门拍"的玩具相机，这等于白拍。

### 现在的两条路径

| 目标 | 路径 | 角色 |
|---|---|---|
| 应用私有 | `Android/data/<pkg>/files/Pictures/pocket-ccd/{full,thumb}/` | 权威副本，照片墙唯一数据源 |
| 系统相册 | `Pictures/Pocket CCD/`（经 MediaStore） | 对外可见，分享 / 查看 / 备份都走这里 |

两张是**同一份 JPEG 字节**：相册副本由已经落盘的文件流复制，不重新编码位图
（12MP 再压一次要多花约 300ms 和一份 48MB 临时位图，而内容完全一样）。

### 权限：一个都不用申请

API 29+ 的分区存储允许应用向 MediaStore 插入**自己创建的**图片，不需要
`WRITE_EXTERNAL_STORAGE`。`AndroidManifest.xml` 里仍然只有 `CAMERA`。

### `IS_PENDING` 不能省

先 `IS_PENDING = 1` 写入、完成后再置 0；写入失败时 `delete` 掉这条记录。
否则相册里会出现半张图，或者留下一条永远 pending 的僵尸条目。

### 历史补齐只做一次

修复前拍的照片只在私有目录里，首次启动时由 `syncToGallery()` 补进相册。
**必须只做一次**（`SharedPreferences` 标记 `gallery_backfill_done_v1`）：
否则用户从系统相册删掉某张照片，下次启动又会被补回来，变成删不掉的鬼照片。

### 实测（PJZ110，2026-10-19 15:48）

```
$ adb shell "ls '/sdcard/Pictures/Pocket CCD/'"
film_1792391623930_gold200__4096x3072.jpg
…（共 13 张，与私有目录一一对应）

$ adb shell content query --uri content://media/external/images/media \
    --projection _id:_display_name:relative_path:_size
1000051828 film_1792391623930_gold200__4096x3072.jpg Pictures/Pocket CCD/ 3038375
…（13 条，_size 与文件字节数一致，is_pending=0）
```

系统相册 App 的日志里能看到它在读这个目录：
`AbsGalleryImageBitmapRequester … path:…/Pictures/Pocket CCD/film_…jpg`。

> 排查提示：`ls '/sdcard/Pictures/Pocket CCD/'` 在 adb 里必须整串加引号，
> 否则空格会把路径拆成两个参数，报 `No such file or directory` —— 很容易
> 误判成"照片没写进相册"。
