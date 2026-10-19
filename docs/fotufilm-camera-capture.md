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

## 6. 已知限制（不是 bug，是排期）

| 限制 | 归属 |
|---|---|
| 预览是普通画面，不做实时滤镜 | 设计如此（D-036） |
| 冲洗 2–3 秒没有进度条，只有状态点 | FOT-009 |
| 取景框是 4:3 横向构图，竖屏下上下留边 | FOT-009（需要重新设计取景框比例） |
| 色彩口径仍是线性 + sRGB 基色，未做 Rec.2020 | FOT-006 |
| 照片墙缩略图同步解码，条数多了会卡顿 | FOT-011 |

## 7. 手动测试清单

前置：设备已安装 APK，且 `files/film/` 下有 `*__4000x3000.fswp`。

1. 打开 App → 首页 → **下拉**进入拍摄页
2. 首次会弹相机权限 → 允许
3. 观察取景框：**应该是真实画面**（不是静态渐变占位），下方卷名小圆点为绿色
4. 点卷名可切卷；若无 4000×3000 的卷，快门会置灰并显示
   `相机不支持 4000×3000，最大 W×H`
5. 按快门 → 跳到成片页，状态 `PROCESSING` → `WRITING FILE` → `SAVED`
6. 成片应显示为 4:3 照片，下方有 `4000×3000 · Xms · N strips` 诊断行
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
