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

D-037 的要求是"pack 尺寸 = 采集尺寸"，因为 pack 里的扩散/晕光/颗粒半径已经被
烘成像素数，换尺寸等于换了光学系统。这里有三道闸：

1. **`ImageCapture.setTargetRotation(Surface.ROTATION_0)`**
   不固定的话，竖屏拍照时 CameraX 会输出 3000×4000，和 4000×3000 的 pack 直接对不上。
2. **绑定后读实际采集尺寸**（`ImageCapture.attachedSurfaceResolution`），
   用它和当前卷的 `width × height` 比对；不匹配就把快门置灰，并在取景框下方
   写出原因与"相机最大支持的尺寸"，方便决定该产出什么尺寸的 pack。
3. **`decodeJpeg` 硬校验**：拍完再验一次，不符直接抛
   `采集尺寸 A 与 pack 的 B 不一致`。**从不缩放** —— 缩放出来的图看起来是对的，
   但颗粒和晕光尺度全错，比失败更糟。

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
挑尺寸精确相等的卷              → matches 成立才把尺寸下发（programmedSize）
```

`programmedSize` 只在 `matches == true` 时非空 —— 也就是"这个尺寸已经被证明
被支持了"，不会再被替换。代价是匹配成功时会多一次同尺寸的重绑定，可以接受。

**推论**：pack 应该按"设备实际最大 JPEG"来产，而不是先定 12MP 再去要求设备。
PJZ110 实测最大 JPEG 是 **4096×3072**，没有 4000×3000。

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

## 9. 已知缺陷：成片方向与取景不一致（FOT-017）

`setTargetRotation(Surface.ROTATION_0)` 是为了让 JPEG 像素恒为**传感器横置方向**
（4096×3072），从而与 pack 精确对齐（D-037）。代价是竖持手机拍摄时，
成片相对取景框**侧躺 90°**。

实测（`PJZ110`，2026-10-19 14:33:43 那张 GOLD 200）：

| 文件 | 像素 | EXIF 方向 | 结果 |
|---|---|---|---|
| `full/*.jpg` | 4096×3072 | 无 | 场景侧倒 90° |
| `thumb/*.jpg` | 1440×1080 | 无 | 同样侧倒 |

这与 FOT-009「预览不冒充成片」冲突：用户在取景框里看到是正的，存下来是躺的。

### 三个修法（按代价排序）

1. **写 EXIF 方向标记**（最省内存）
   编码完 JPEG 后用 `ExifInterface` 把 `TAG_ORIENTATION` 设成 6，`saveAttributes()`。
   像素不动、不额外分配；但依赖看图的软件读 EXIF，自己家的照片墙读取时也要按方向转。
2. **渲染后旋转位图**（最可靠）
   冲洗仍按 4096×3072 跑（pack 的比例不能变），拿到成品 `Bitmap` 后 `Matrix`
   转 90° 再编码 → 3072×4096。代价是多一份 ~50MB 的临时位图，需立刻 recycle 原图。
3. **改用横向取景框**（改设计）
   承认这就是一台横向构图的 CCD，UI 引导用户横持；预览、成片、照片墙全部统一为横构图。

**推荐 2**：不依赖下游是否读 EXIF，且旋转发生在效果烘焙之后，颗粒/晕光的尺度不受影响。
选 1 的话必须同时改 `PhotoStore` 的缩略图与照片墙列表，否则会出现"大图正、小图躺"。
