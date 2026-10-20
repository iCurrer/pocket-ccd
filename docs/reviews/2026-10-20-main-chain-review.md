# 主链路代码审阅：相机 → 冲洗 → 落盘 → 对账

**审阅时间：** 2026-10-20 22:30（Asia/Shanghai）
**审阅范围（HEAD `21697ee`）：**

| 文件 | 行数 |
|---|---|
| `camera/CameraPreview.kt` | 598 |
| `camera/PhotoStore.kt` | 461 |
| `camera/GalleryReconciler.kt` + 单测 | 70 + 124 |
| `navigation/AppViewModel.kt`、`AppState.kt` | 352 + 91 |
| `ui/AppRoot.kt` | 460 |
| `fotufilm/FilmEngine.kt`、`FilmPack.kt`、`FotufilmNative.kt`、`LinearColor.kt`、`FilmStock.kt` | 1393 |
| `ui/film/FilmCaptureScreen.kt`、`ui/gallery/PhotoWallScreen.kt`、`ui/result/ResultScreen.kt`、`ui/film/FilmControls.kt` | 约 1500 |
| `native/fotufilm_jni.cpp`（只读边界与 halo） | 394 |

**证据标记：** `[F]` 已核实事实 ｜ `[I]` 工程推断 ｜ `[H]` 待真机验证。

## 本次实际执行过的验证

| 动作 | 结果 |
|---|---|
| `:app:assembleDebug` | `BUILD SUCCESSFUL`（HEAD `21697ee`）`[F]` |
| `:app:testDebugUnitTest --rerun-tasks` | `BUILD SUCCESSFUL in 35s`；`AppStateTest` 16/16、`GalleryReconcilerTest` 11/11，0 失败 `[F]` |
| `adb devices` | **空，没有连接任何真机** `[F]` — 所有运行时行为只能标 `[H]` |

> 本机 Gradle 用户目录在工作区外，只读沙箱下 wrapper 无法取锁；以上构建是在一次性放宽沙箱后执行的。

---

## 0. 结论摘要

**三条会**永久**丢照片的路径（P0），共同根因是「用缺失证明删除」：**

1. **升级/首启竞态**：`syncToGallery()` 与 `ON_RESUME` 触发的对账并发，历史照片在补传进相册**之前**就被判成"用户已删除"，私有副本被删，且补传标记照样置位 → 永不恢复。
2. **空游标 = 清库**：`PhotoStore` 的注释明写"查询失败/返回空游标 → 整批不删"，代码只挡住了 `null`；查询成功但返回 0 行时会把整个照片库判死。
3. **补传失败不记账**：`markPublished()` 只在拍摄路径调用，补传路径没调用，违反 D-047 的安全阀③。

**两条会改路线图的发现：**

4. **B-11（8.4–9.1 s）大概率是测量口径问题**：8.4–9.1 s 来自 debug 包的 app 内实测，2.0–3.2 s 预算来自非 debuggable 的 `benchmark` 构建；项目自己的基准确认 debug 编码阶段慢 6.7–10.2×。**先重测再优化。**
5. **halo 重叠不是注释说的 30%，实测约 1.94× 内核工作量**（halo 55–62 / strip 128）。`TILE_BUDGET_BYTES` 从未真正生效（实际占 49.5 MB / 256 MB），把 `stripRows` 提到 256–384 可再省约 30% 内核时间。

**产品体验上最刺眼的是 7 件：** 照片墙点不进单张、闪光灯重绑后静默失效、冷启动要绑两次相机、照片墙在主线程解码（缩略图缺失时解 12MP）、「换卷」磁贴只重置计数不换胶卷、**0.6× 光学档位永远打不出去**、**成片页参数网格永远是「—」**。

**第二轮补审（相机生命周期专项）又找到 1 条卡死路径 + 5 条 P1**，见文末〈第二轮〉——其中最要紧的是：快门回调没有超时兜底，一旦丢失就永久卡在「正在冲洗」；以及 `ON_RESUME` 的会话探针几乎必然误判，导致每次回前台都整轮重绑。

---

## P0

### P0-1 升级首启：补传与对账并发，历史照片在补传前被删光

**位置** `PhotoStore.kt:207-221`（`syncToGallery`）、`PhotoStore.kt:256-306`（`reconcileWithGallery`）、`PhotoStore.kt:271-279`（`published` 默认 true）、`AppViewModel.kt:101-107`（init 里 `launch(IO){ syncToGallery() }`）、`AppRoot.kt:129-142`（`LifecycleEventObserver` → `ON_RESUME` → `reconcileGallery()`）

**机理** `[F]`

1. `AppViewModel.init` 在 IO 上异步启动 `syncToGallery()`：逐张 `query` → `insert` → 复制 3 MB → `IS_PENDING=0`。
2. `AppRoot` 的 `DisposableEffect` 把观察者加到**已经 RESUMED** 的 lifecycle 上，`LifecycleRegistry` 会立刻补发 `ON_CREATE/ON_START/ON_RESUME` → `reconcileGallery()` 几乎同时开跑。
3. 对账读到的索引里只有"已补传完的那几张"。其余历史文件：`published = id !in unpublished` → **默认为 true**；`capturedAt` 是几个月前 → 早已过 2 分钟等待期；名字不在索引 → **判为已删除** → `full.delete()` + `thumb.delete()`。
4. `syncToGallery` 继续跑，源文件已被删 → `publishToGallery` 里 `inputStream()` 抛 `FileNotFoundException`，被 `runCatching` 吞成 `null` → 不计入 `added`，但**循环结束后仍然 `KEY_BACKFILL_DONE = true`** → 永不重试。

**期望 vs 实际** 期望补传完成前不执行对账；实际两条路径无任何顺序约束。`[F]`

**触发条件** 私有目录里有 ≥1 张老照片，且 `gallery_backfill_done_v1` 为 false —— 也就是**从"只写私有目录"的旧版本升级上来的第一次启动**。dev 自己这台机器不会中（P-034 时已把标记置位），所以这是一个**只在真实用户升级时才会响的雷**。`[I]`

**修复** 在 `reconcileWithGallery()` 开头加：

```kotlin
if (!prefs.getBoolean(KEY_BACKFILL_DONE, false)) {
    Log.i(TAG, "对账跳过：相册补齐还没做完")
    return ReconcileReport(removed = 0, kept = files.size, queryOk = false)
}
```

更稳的做法是补传期间写一个 `backfilling` 标记（进程被杀也要能恢复），并对账只在标记清除后运行。

---

### P0-2 空游标被当成"用户把相册删空了"，整库删除；安全阀①没有实现

**位置** `PhotoStore.kt:250-251`（注释承诺）、`PhotoStore.kt:261-265`（实际只挡 `null`）、`PhotoStore.kt:318-345`（`readGalleryIndex` 对 0 行也返回非 null）、`GalleryReconciler.kt:64-69`（判定式）、`GalleryReconcilerTest.kt:114-123`（单测把错误行为断言成正确）

**证据** `[F]`

```kotlin
// PhotoStore.kt:250-251
 * 1. **查询失败/返回空游标 → 整批不删**。MediaStore 抖一下不能变成清空照片库。

// PhotoStore.kt:261-265
val index = readGalleryIndex()
    ?: run { … return ReconcileReport(0, files.size, queryOk = false) }
```

`readGalleryIndex()` 只在 `runCatching` 抛异常或 `cursor == null` 时返回 null；**`cursor` 非 null 但 0 行**时返回 `GalleryIndex(emptySet(), emptySet())`，对账据此把每张 `published` 且过期的照片判死。`GalleryReconcilerTest:114-123` 甚至明确断言"空索引 → 全删合法"，注释把责任推给 PhotoStore 的 `null` 分支——而那个分支根本不覆盖空结果。文档、测试、实现三层互相推诿，净效果就是清库。

**触发条件** MediaProvider 重建索引/启动风暴/卷重挂载，或者本应用条目 `owner_package_name` 记录丢失（我们没有 `READ_MEDIA_IMAGES`，可见性完全依赖 owner 记录）。概率不高，后果是**照片墙 + 私有副本一起消失且不可恢复**。`[I]`

**修复** 把"空结果"并入安全阀①：`if (index.names.isEmpty()) return ReconcileReport(0, files.size, queryOk = false)`；同时改掉那条单测（它现在保护的是错误行为）。

---

### P0-3 补传失败的照片不记入 `gallery_missing_ids`，下一轮对账即删唯一副本

**位置** `PhotoStore.kt:212-219`（补传循环）、对照 `PhotoStore.kt:110-112`（拍摄路径调用 `markPublished`）、`PhotoStore.kt:352-359`（`markPublished` 实现）

**证据** `[F]` 拍摄路径：`markPublished(id, galleryUri != null)`。补传路径：

```kotlin
for (file in files.orEmpty()) {
    if (existsInGallery(file.name)) continue
    val capturedAt = parseId(file.name)?.third ?: file.lastModified()
    if (publishToGallery(file, file.name, capturedAt) != null) added++   // ← 失败什么都不记
}
```

补传失败（空间不足、`insert` 返回 null、`openOutputStream` 失败）后，该文件既不在相册、也没有 `published=false` 记录 → "名字不在 + 时间不在 + 已过等待期" → 下一轮对账把它删掉，而它是**唯一一份**。这正好违反 D-047 列出的安全阀③（"发布到相册失败的照片不删"）。`[F]`

**修复** 循环里每张都写结果：`markPublished(parseId(file.name)?.first ?: continue, published != null)`。

---

## P1

### P1-4 照片墙在主线程解码；缩略图缺失时直接解 12MP 原图

**位置** `PhotoWallScreen.kt:218-220`；`PhotoStore.kt:397`

```kotlin
val bitmap = remember(item.imagePath) {
    item.imagePath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
}   // ← remember 块在 composition 里执行 = 主线程，无 inSampleSize、无缓存
```

`PhotoStore.list()` 在缩略图缺失时把**全尺寸文件**交给照片墙：

```kotlin
thumbnailFile = File(thumbDir, file.name).takeIf { it.isFile } ?: file
```

**后果** `[I]` 1440×1080 ARGB ≈ 6.2 MB/格，12 格滚动 = 持续主线程解码（掉帧）；缩略图缺失时 4096×3072 ≈ 48 MB/格，必 OOM。项目在 `FilmLibraryScreen.kt:846-853` 里已经写对了（`inSampleSize = 4` + 注释说明），照片墙却留了这条通路。

**修复** `produceState`/`LaunchedEffect` + `Dispatchers.IO`、按格子像素尺寸算 `inSampleSize`、加 `LruCache`；`list()` 不要在缩略图缺失时回退全尺寸——照片墙本来就有空占位分支（`PhotoWallScreen.kt:228-240`）。

### P1-5 闪光灯在相机重绑后静默失效（图标还亮着）

**位置** `AppRoot.kt:201`（唯一一处 `setFlash`）、`AppRoot.kt:271-272`（`flashOn` 状态）、`AppRoot.kt:175-177`（对照：zoom 有回填）、`CameraPreview.kt:307-330`（每次绑定新建 `ImageCapture`）

**机理** `[F]` `flashOn` 活在 `PocketCcdApp` 里、跨页面存活；`setFlash` 只在用户点按那一刻调一次。而离开 CAMERA/RESULT 去相册/设置/胶卷库会 `onDispose → unbindAll`，回来重建 `ImageCapture`（默认 `FLASH_MODE_OFF`），**没有任何地方把 `flashOn` 重新写进硬件**。zoom 有回填（`if (zoomRatio > 1f) bound.requestZoom(zoomRatio)`），闪光灯没有。

**后果** 用户开闪光灯 → 翻一次相册 → 回来图标仍显示"开" → 夜景按下快门没有闪光。`[F]`

**修复** 把 `flashOn` 提到 `PocketCcdRoute`（和 `zoomRatio` 同一层），在 `onReady` 里与 `requestZoom` 并列 `bound.setFlash(flashOn)`。

### P1-6 照片墙点不进单张：看不到大图，也删不掉

**位置** `PhotoWallScreen.kt:204-260`（`PhotoTile` 没有任何 `clickable`）；`AppState.kt:67-73`（`OpenResult` 支持从 GALLERY 进入，但全仓唯一触发点是 `AppRoot.kt:198` 的快门）

**证据** `[F]` `grep OpenResult` 只有快门一处；照片墙的格子不可点，页面是死胡同。

**产品影响** 用户拍完必然想点开一张看大图/删掉废片。现在两条都做不到（App 内删除在 README 里标"未开始"）。`OpenResult` 从 GALLERY 进入的分支是现成的，接上格子点击即可；删除必须同时删 MediaStore 条目（README 已警告否则制造反向分叉）。

### P1-7 相册"删除到回收站"被当成永久删除

**位置** `PhotoStore.kt:281-300`；`GalleryReconciler.kt:64-69`；README〈故障排查〉"相册里还原一张照片，照片墙不长回来"（已知不对称 D-047）

**机理** `[I]` Android 11+ 的删除默认进回收站（`IS_TRASHED=1`），默认 `query` **不返回** trashed 条目 → 名字与 `DATE_TAKEN` 双双消失 → 私有副本被删。而还原只存在于系统相册一侧，本 App"只对账删、不对账补" → 照片墙永久少一张。

**比 README 记的更严重**：README 把这条写成"还原不长回来"的已知不对称，但实际路径是**用户只是删到回收站、还没决定销毁**，唯一副本就已经没了。

**修复** 查询时带上 `QUERY_ARG_MATCH_TRASHED`（或显式读 `IS_TRASHED`）并把 trashed 条目算作"还活着"；真要支持还原，需要反向补一条私有副本。

**待真机确认** trashed 条目在默认 query 里的可见性（`[H]`）。

### P1-8 B-11 大概率是测量口径问题，先别优化

**位置** `README.md:50`、`PROJECT_STATE.md:246`（B-11）、`docs/fotufilm-device-benchmark.md:17/43-56`

**证据** `[F]`

- 8.4–9.1 s 记的是"成片实测 9107 ms / 8443 ms"，来自 app 内 `PocketCcdCapture` 的 `developed … in Xms`（`AppViewModel.kt:270-276`，即 `RenderResult.totalMillis`，纯引擎）。
- 预算 2.0–3.2 s 来自 `FilmSelfTest`，构建类型明写 **`benchmark`（非 debuggable）**。
- 同一份基准文档 §3.1：**debug 构建的编码阶段 1,931–2,523 ms vs benchmark 189–377 ms，6.7–10.2×**，并且明确写着"任何性能结论必须出自 benchmark 构建"。
- 仓库里所有真机拍摄记录用的都是 `app-debug.apk`（README 快速开始 / TASK_BOARD P-027..P-031）。

8.4 s ÷ 5.3 ≈ 1.6 s 量级；benchmark 引擎 2.0–3.2 s + 解码/JPEG 编码/相册复制（release 下约 1–1.5 s）≈ 3.5–4.5 s，仍在预算边缘但不再是 4 倍超标。`[I]`

**建议** 在动手改算法/降尺寸之前：装 `assembleBenchmark` 的包，用真实相机连拍 3 张，记录 `developed …ms` 与从快门到成片页的墙钟；同时把 `decodeJpeg`、`save()`（JPEG 压缩 + 缩略图 + 相册复制）分别计时。**如果确认是 debug 包，B-11 应改判为"测量口径错误"并关闭，而不是排期优化。** `[I]`

### P1-9 halo 重叠约 1.94×，不是注释写的 30%

**位置** `FilmEngine.kt:30-33`（注释）、`FilmEngine.kt:196-282`（循环）、`docs/fotufilm-device-benchmark.md:32`（halo 55–62、tile 238–252 行、24 条带）

**计算** `[F]` 每条带输出 128 行，上下各留 halo 55–62 → 实际送入内核 238–252 行。24 × 249 ≈ 5,976 行，而整帧只有 3,072 行 → **1.86–1.97×**。重叠率 = `2*halo/stripRows` = 86%–97%，注释写的"约 30%"错了约 3 倍。内核占 80–85% 时间，于是这一项约等于**总耗时的 45%**。

**而且这段开销可以白拿回来**：`estimatedTileBytes` 在 halo=62 / strip=128 时只有 `4096×252×4×12 ≈ 49.5 MB`，对 256 MB 的 `TILE_BUDGET_BYTES` 完全不构成约束（`FilmEngine.kt:153-158` 的降档循环对真实 pack 从不触发）。把 `stripRows` 提到 384 → tile 508 行 ≈ 100 MB；内核行数 8×508 = 4,064（−32%），预计内核时间 −25~30%，`fill/copy` 同步减少。

**建议** 在真机上对 `stripRows ∈ {128, 256, 384}` 各测一次（`[H]`）。注意 Halide 对 tile 高度可能有向量化偏好，不要一次改到位。顺带把那条注释改成实测值。

### P1-10 冷启动/从相册返回要绑两次相机

**位置** `CameraPreview.kt:286`（`LaunchedEffect(lensFacing, targetSize, rebindNonce)`）、`AppViewModel.kt:155-157/302-327`（`onCameraReleased → Pending → supportedSizes 空 → programmedSize null`）、`AppRoot.kt:116-121`

**机理** `[F]`

1. 首绑 `targetSize = null`（还没回读到真实尺寸）→ 绑定 → 轮询 `attachedSurfaceResolution` → `onReady`。
2. `onCameraBound` → `CameraState.Bound` → `buildCameraUi` 算出 `programmedSize = Size(pack.w, pack.h)`。
3. `CameraPreview` 的 `LaunchedEffect` key 从 `null` 变成 `Size(...)` → **effect 重启 → `unbindAll()` + 重新 `bindToLifecycle()`**。

即使下发的尺寸与已 attach 的尺寸**完全相同**也会重绑，因为 key 比的是对象而不是等价性。每次从相册/设置/胶卷库返回同样走一遍（`onCameraReleased` 把 `supportedSizes` 清空 → `programmedSize` 回到 null）。

**后果** `[I]` 取景器多黑一下、多 200–500 ms 首帧延迟（`[H]` 具体值）。D-039 的产品承诺是"打开就是取景器"，这一下正好砸在体验最敏感的入口上。

**附带**：两次绑定之间 `handle` 被置空，但 `cameraUi.canShoot` 仍为 true（它只看 `CameraState`），快门看起来可点却静默无响应约几百毫秒。闪光灯会在这一下之后失效（见 P1-5）。

**修复** 允许"请求尺寸 == 已 attach 尺寸"时跳过重绑（或把 key 换成 `programmedSize` 的等价类，例如 null 与等于 attached 的值视为同一档）；`onCameraReleased` 不必立刻清空 `supportedSizes`。

---

## P2 / P3

| # | 级别 | 问题 | 位置 |
|---|---|---|---|
| 1 | P2 | **单测在保护错误行为**：`空相册索引会删掉全部已发布的旧照片` 断言"空索引 → 全删"，并把安全责任推给没有实现它的 `PhotoStore`；`GRACE_MILLIS` 用例拿被测常量自身当断言值（把 120_000 改成 2_000 依然全绿），且缺 `capturedAt == now - GRACE` 这条边界 | `GalleryReconcilerTest.kt:114-123`、`:53-58` |
| 2 | P2 | 「换卷」磁贴**只重置计数、不换胶卷**，且没有二次确认：紧挨快门，误触一次本卷 `N/36` 直接归零，而真正的换卷在胶卷库里 | `FilmCaptureScreen.kt:343`、`FilmControls.kt:182-195` |
| 3 | P2 | 卷计数是"存活照片数"而不是"已曝光张数"：在系统相册删 8 张，计数器从 20 掉回 12，能继续多拍；满卷提示不准时 | `AppViewModel.kt:67-74`、`:248-250` |
| 4 | P2 | 换胶卷不换卷：`selectStock` 不动 `rollStartedAt`，一"卷"可拍 A 36 张 + B 36 张，与 D-040「一卷 = 一支胶卷 + 一次装卷」不符 | `AppViewModel.kt:133-140`、`:68-69` |
| 5 | P2 | 计数依赖墙钟：时钟回拨 → 新拍 `capturedAt < startedAt` → 计数不动、36 张上限失效 | `PhotoStore.kt:368-382`、`AppViewModel.kt:69` |
| 6 | P2 | `syncToGallery` 补传失败不记账（见 P0-3）；`existsInGallery` 用 `RELATIVE_PATH LIKE`，用户把照片移到别的相册后会**重复补一张** | `PhotoStore.kt:229-238` |
| 7 | P2 | `nativeAbiVersion` 从未校验：换 `.so` 不会在构建期报错，9 个同名符号就能被调用，参数语义一改就是静默错图/崩溃 | `FotufilmNative.kt:36-40`（唯一读取点是 `FilmSelfTest.kt`） |
| 8 | P2 | FSWP 版本接受 `1` 和 `2`，但解析只实现 v2 布局 → v1 pack 会被当 v2 读，返回成功、结果全错 | `native/fotufilm_jni.cpp:94-96` |
| 9 | P2 | `FilmEngine.render(destination = null)` 把渲染结果丢进局部变量后只返回统计（48 MB + 数秒白做）；`FilmPack` 在 `nativePackInfo`/`nativeMaxHalo` 抛异常时句柄泄漏（每次 1.8 MB） | `FilmEngine.kt:43-49`、`FilmPack.kt:62-84` |
| 10 | P2 | 首绑 `attachedSurfaceResolution` 轮询 40×25 ms 超时 → `supportedSizes` 为空 → 快门永久置灰，直到离开页面 | `CameraPreview.kt:343-353`、`AppViewModel.kt:333-337` |
| 11 | P2 | `writeAtomically` 无 `fsync`、`renameTo` 失败时用 `copyTo`（非原子）、失败不清理 `.tmp` 孤儿 | `PhotoStore.kt:425-436` |
| 12 | P3 | **死代码 864 行**：`CameraStageScreen.kt`(681) + `ToyCameraBody.kt`(183) 全仓无引用（只剩自己的 Preview），是 M9 之前红色玩具机时代的残留 | `ui/stage/`、`ui/components/` |
| 13 | P3 | 文档漂移：`interaction-spec` 声称快门有「已记录 · 第 N 张」toast 与 420 ms 防连点"已做"——代码里**没有**这个 toast（只有变焦/闪光/网格用 toast），防连点改由 `capturing` 旗标 + 快门置灰实现 | `docs/interaction-spec.md:48`、`FilmCaptureScreen.kt:121-134` |
| 14 | P3 | `list()` 把 `width/height/renderMillis/strips` 填 0：将来若从照片墙打开旧照片，成片页会显示 `0ms · 0 strips` 并按 4:3 兜底 | `PhotoStore.kt:391-403`、`ResultScreen.kt:253-257/508-513` |
| 15 | P3 | `readPhotoExif()` 在 composition 里同步读文件（`remember{}`）——主线程 IO，虽然只读 EXIF 头 | `ResultScreen.kt:106-108` |
| 16 | P3 | 对账第二次请求被静默丢弃：进照片墙触发的对账若撞上 `reconciling` 直接 return，墙会继续显示已删照片直到下次 resume | `AppViewModel.kt:244` |

---

## 第二轮：相机生命周期专项

> 同一次审阅的第二遍，专盯 `takeJpeg` / 重绑 / 权限 / 变焦 / 方向。以下每条都已对照代码行复核；CameraX 的时序行为标 `[H]`。

### P0-4 快门回调没有超时兜底：一旦丢失就永久卡在「正在冲洗」

**位置** `CameraPreview.kt:105-130`（`takeJpeg`）、`AppViewModel.kt:172-217`（`capturing` 只在 `finally` 复位）、`AndroidManifest.xml:29-36`（`MainActivity` 无 `configChanges`、无 `screenOrientation`）

**证据** `[F]`

```kotlin
// CameraPreview.kt:105-130
suspend fun takeJpeg(): CapturedFrame = suspendCancellableCoroutine { continuation ->
    val rotation = rotationDegrees()
    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) { … }
        override fun onError(exception: ImageCaptureException) { … }
    })
}
```

只有 `onCaptureSuccess` / `onError` 两条路径会恢复协程，**没有 `withTimeout`，没有 `invokeOnCancellation`**。而 `capturing` 这个 `AtomicBoolean` 只在协程的 `finally` 里复位、`_capture` 只在异常路径才会离开 `Capturing`。

**触发** `[I]` 快门按下后、`onCaptureSuccess` 之前（典型 100–300 ms）发生下列任一：

- 切到相册 / 设置 / 胶卷库 → `cameraBound=false` → `CameraPreview` 被销毁 → `unbindAll()`，正在进行的 `takePicture` 被掐断；
- 按 Home / 锁屏 → `ON_STOP` → CameraX 关闭会话；
- **旋转屏幕** —— `MainActivity` 没有 `configChanges` 也没有锁定方向，转屏会销毁并重建 Activity，`DisposableEffect` 解绑，而 `viewModelScope` 存活、协程还挂在**旧句柄**上。

**后果** `capturing` 永久为 true → 快门永久置灰、底部永久显示「正在冲洗上一张」、成片页永久 PROCESSING，**只能杀进程恢复**。`[F]`（机制）/ `[H]`（CameraX 是否真的不回调）

**置信度说明** CameraX 在会话关闭时通常会补一个 `ERROR_CAMERA_CLOSED`，所以「必现」不成立；但「丢失回调时无任何兜底」是代码事实，而这条路径的失败代价是整个 App 卡死。**无论 CameraX 表现如何都值得加兜底**：

```kotlin
suspend fun takeJpeg(): CapturedFrame = withTimeout(15_000) {
    suspendCancellableCoroutine { … }   // + 在 onError 之外，finally 里也 resumeWithException
}
// 另加状态自愈：_capture 处于 busy 超过 30s 时强制重置为 Failed
```

真机验证路径：**按快门后 1 秒内点「相册」→ 回取景页 → 再按快门**，抓 `adb logcat -s PocketCcdCamera PocketCcdCapture`，看是否出现第二次 `capture` 或永久忙。

### P1-11 `ON_RESUME` 的会话探针几乎必然误判，导致每次回前台都整轮重绑

**位置** `AppRoot.kt:130-138`、`CameraPreview.kt:150-151`、对照 `CameraPreview.kt:343-351`

**机理** `[I]` `bindToLifecycle` 在 `ON_START` 时异步重新打开相机会话；`ON_RESUME` 紧接着同步触发我们的观察者，此时 `attachedSurfaceResolution` 还没就绪 → `isAttached()` 抛出 → **判为「连接已断开」** → `cameraNonce++` → 整轮 `unbindAll + bind`。

**自相矛盾**：`CameraPreview` 自己在绑定时都要轮询最多 **40×25 ms = 1 s** 才敢读 `attachedSurfaceResolution`（`:343-351`），同一个探针在 `ON_RESUME` 却只探一次就下结论。

**后果** 每次从别的 App 切回来：footer 闪一下「相机连接已断开，正在重连」，取景器黑 0.5–1 s，并且这段时间 `previewError != null` → **快门被禁用**。对一个「打开就是取景器」的产品，这是每天几十次的体验伤。

**修复** 探针改成"失败后延迟 200 ms 重试 2–3 次再下结论"；或者干脆不在 `ON_RESUME` 探，改由 `takePicture` 的异常驱动重绑（反正 `onError` 已经回来了）。

**真机确认** 切后台再回来，看 `camera bound` 是否每次都打两遍。`[H]`

### P1-12 首次绑定失败没有自愈，只能离开页面

**位置** `AppRoot.kt:179-183`（`onError` 只写 `previewError`）、`AppRoot.kt:133-138`（自愈要求 `handle != null`）、`CameraPreview.kt:404-406`

**证据** `[F]` 绑定失败 → `onError` → `previewError` + `onCameraError`；但唯一的自愈入口是 `ON_RESUME` 里那句 `if (bound != null && !bound.isAttached())`，**`bound` 为 null 时什么都不做**；`LaunchedEffect(lensFacing, targetSize, rebindNonce)` 的 key 也都没变 → 不会重试。

**后果** 相机被别的 App 占着、或系统开机后相机服务还没起来时首次进入 → 永久停在「正在准备取景器 / 错误信息」，`previewError` 一直挡着快门，用户只能切到胶卷库再回来。

**修复** `onError` 里自增 `cameraNonce`（带 1–2 次退避上限，避免死循环重绑）。

### P1-13 0.6× 光学档位永远打不出去 —— 而且放开它会违反 D-044

**位置** `CameraPreview.kt:203-209`、`LensInfo.kt:88-92`、`FilmCaptureScreen.kt:239-249`、README:29（一加 13 应为 `0.6× 15mm / 1× 23mm / 3.2× 73mm`）

**证据** `[F]`

```kotlin
fun requestZoom(requested: Float): Float {
    val max = maxZoomRatio()
    val applied = requested.coerceIn(1f, max)     // ← 下界写死 1f
```

`readLensStops` 会保留 `intrinsicZoomRatio < 1` 的超广角档（`ratio in 0.3f..15f`），于是 UI 上画得出 `0.6×`，点下去请求被夹到 `1.0×`，Toast 显示「变焦 1.0x」，画面不变。

**但这不只是个 clamp 写错的问题**：`setZoomRatio(0.6)` 走逻辑多摄时会**切到超广角物理镜头**，而 D-044 的全部论证前提是「像素密度恒等于主摄，pack 的扩散/晕光/颗粒半径才成立」。超广角是另一块传感器（1/2.75" 级别），pixels-per-mm 与主摄差一截 —— 放开 0.6× 等于**打开一条会用错 pack 空间参数的路径**。

**所以这里需要一次产品决策，二选一**：

| 方案 | 做法 | 代价 |
|---|---|---|
| **A（推荐）** | `readLensStops` 里过滤掉 `zoomRatio < 1f`，UI 不画 0.6× 档 | 少一个卖点；但守住 D-044 的物理一致性 |
| B | 放开 clamp，同时在 UI 上把 <1× 标成「超广角（颗粒尺度不保证）」 | 需要为超广角另产一套 pack，或接受成像偏差并写进文档 |

现在这种「画得出来、按了没用、Toast 还报 1.0x」是**三种做法里最差的一种**：既没守住物理前提，也没兑现 UI 承诺。

### P1-14 成片 JPEG 没有 EXIF，成片页参数网格永远是「—」

**位置** `PhotoStore.kt:106/118`（`Bitmap.compress` 落盘）、`PhotoExif.kt:63-77`、`ResultScreen.kt:446-454`；全仓 `grep saveAttributes` = 0 命中

**证据** `[F]` 管线是 `JPEG → decodeJpeg(ARGB) → 引擎 → Bitmap.compress(JPEG)`。**`Bitmap.compress` 不写任何 EXIF**，之后也没有任何一步把焦距/光圈/快门/ISO 补回去。于是 `readPhotoExif()` 恒返回空 `PhotoExif()`，`exposureLine()` 为 null、`isoLabel()` 为 null → 「EXPOSURE」格永远只显示 `—`。

**这与文档冲突**：README 的当前状态表写「成片页：大图 + **真实 EXIF 参数网格**」，D-043 的口径是「参数网格全部来自 `readPhotoExif`，读不到的那一项显示 `—`」。现在不是"个别项读不到"，是**整格恒空**；而成片页从未在真机上跑过（T9-018 标"待真机"），所以没人发现。

**修复** 相机交来的原始 JPEG **是带 EXIF 的**（焦距/光圈/快门/ISO 都有）。在 `takeJpeg` 里用 `ExifInterface(ByteArrayInputStream(bytes))` 把四项读出来随 `CapturedFrame` 带下去，`SavedPhoto` 加字段，`ResultScreen` 直接用，不再依赖输出文件。

### P1-15 去系统设置授权后回来，仍然显示「需要相机权限」

**位置** `AppRoot.kt:106-113`（注释承诺"每次回到前台都重查一次"）、`AppRoot.kt:128-142`（`ON_RESUME` 里并不查权限）

**证据** `[F]`

```kotlin
LaunchedEffect(captureVisible) {          // key 是 Boolean，回前台时它没变
    if (captureVisible) { hasPermission = context.hasCameraPermission() … }
}
```

`captureVisible = CAMERA || RESULT`。用户点「允许」进系统设置 → 切回来：Activity 走了 `ON_STOP/ON_START/ON_RESUME`，但 `captureVisible` 从头到尾是 `true`，`LaunchedEffect` **不会重跑**，`ON_RESUME` 观察者也只做对账和会话探针 → `hasPermission` 停在 `false`，取景器继续显示「需要相机权限」。

而且 UI **没有「去设置」入口**：权限被永久拒绝后，点占位文字没有任何响应（`permissionLauncher.launch` 会静默返回 false）。

**修复** `ON_RESUME` 里重查 `hasPermission`；永久拒绝时在占位区加一个 `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` 按钮。

### 第二轮的 P2 / P3

| # | 级别 | 问题 | 位置 |
|---|---|---|---|
| 1 | P2 | `retry()` 复用 `lastTaker`：`onCameraReleased` 从不清空、`onReady` 也不更新它 → RESULT→相册→返回→「重试」必然报 `Not bound to a valid Camera`（英文原文直接显示给用户） | `AppViewModel.kt:85-86/155-157/219-226`、`AppRoot.kt:174` |
| 2 | P2 | `takePicture` 的回调跑在 **主线程 executor** 上，`onCaptureSuccess` 里做 3–5 MB 的 `ByteArray` 分配 + 拷贝，正好落在快门闪白动画那一帧 | `CameraPreview.kt:109-117/390` |
| 3 | P2 | 旋转屏幕会重建 Activity（Manifest 无 `configChanges`、无 `screenOrientation`）：`flashOn`/`gridOn`/`zoomRatio`/`previewError` 全部重置；冲洗中途旋转会撞上 P0-4；横屏没有任何布局设计 | `AndroidManifest.xml:29-36`、`AppRoot.kt:80-89/271-276` |
| 4 | P2 | `hasFlash` 传到了 `CameraState.Bound` 却没有用于禁用闪光按钮：没有闪光灯的设备点下去仍弹「闪光灯已开启」，而 `setFlash` 内部静默降级 | `CaptureState.kt:35-38`、`CameraPreview.kt:132-138` |
| 5 | P3 | `readLensStops` 先升序再 `take(4)`：五档时丢掉最长焦（10×），而最短那档（0.6×）又打不出去 | `LensInfo.kt:78-83` |
| 6 | P3 | 缩略图 `compress` 抛异常时 `thumbBitmap` 未被 recycle（外层 `finally` 只回收 `upright`） | `PhotoStore.kt:116-119/134-137` |
| 7 | P3 | `MainActivity` 注释仍写 "App launches on the red camera home"，是 D-039 之前的口径 | `MainActivity.kt:16` |

---

## 建议的修复顺序

1. **P0-1 / P0-2 / P0-3**（三条数据丢失路径）—— 都是几行到十几行的改动，且能顺带把 `GalleryReconcilerTest` 那条错误断言改对。**在真机上把"升级路径"当正式用例跑一次**：清 `gallery_backfill_done_v1` → 放几张私有目录照片 → 冷启动 → 看照片还在不在。
2. **P0-4**（快门回调兜底）—— 十几行：`withTimeout` + `invokeOnCancellation` + busy 超时自愈。它不丢数据，但症状是「App 卡死、只能杀进程」，而且和旋转屏幕/切后台直接相关。顺手一起做 P1-12（绑定失败自愈）与 P1-11（探针重试），三处都在同一段生命周期代码里。
3. **P1-14 / P1-13**（成片 EXIF、0.6× 档位）—— 两者都是「UI 承诺了做不到的事」，其中 0.6× 需要你先拍板 A/B 方案（我推荐 A：隐藏 <1× 档，守住 D-044 的像素密度前提）。EXIF 只是把相机原始 JPEG 的四项带下来，纯增量。
4. **P1-8**（B-11 重测）—— 只需装一次 `benchmark` 包。它决定要不要为性能排期，可能是本轮最高性价比的一条。
5. **P1-5 / P1-10 / P1-6 / P1-15**（闪光灯回填、去掉重复绑定、照片墙可点开、权限重查）—— 产品每天都会碰到的体验缺口。
6. **P1-4 / P1-9**（照片墙解码、stripRows）—— 需要真机量化，可以和上一轮基准一起做。
7. 两张 P2 表里的产品规则/测试债（换卷语义、卷计数口径、`GalleryReconcilerTest`），建议和 D-040/D-046 一起收口。

## 我没能覆盖的

- **没有任何真机**：所有运行时结论（P0-1 的实际时序、P0-4 的回调行为、P1-11 的误判频率、P1-5 的可见现象、P1-9 的收益、trash 语义、DATE_TAKEN 是否落库）都标了 `[H]`。
- **P1-11 / P0-4 需要的第一手证据**：切后台再回来看 `camera bound` 是否重复打；快门后 1 秒内切相册再回来，看是否永久停留在「正在冲洗」。
- **上游 Halide 头文件不在仓库**（`FotufilmResolvedFrameParams.h` 等），所以 `nativeMaxHalo` 取"各阶段半径最大值"是否等于"级联支撑半径之和"无法证实；`nativeProcessStrip` 的 `originY` 语义在两份注释里互相矛盾（`fotufilm_jni.cpp:363-369` 说"输入块位于全帧 (0, originY)"，`FotufilmNative.kt:84-89` 说"位于输出画布"），而调用方恒传 `originY = 0`——如果 kernel 用绝对行坐标做颗粒相位，这正好能解释 FOT-015「每条带子的颗粒相位都不同，只有第一条与整帧一致」。**这条需要上游源码，或一次 `originY = tileTop` 的对照实验**（注意改错会让 native 直接返回 `ERROR_INVALID_ARGUMENT`，见 `fotufilm_jni.cpp:198-201`）。
- 未审：`FilmLibraryScreen` 的装卷交互细节、`FilmSelfTest` 全量、`SettingsScreen`、CI workflow。
