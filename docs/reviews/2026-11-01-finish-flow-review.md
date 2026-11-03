# 成片流程逻辑复核（2026-11-01）

**范围：** 快门 → 相机回调 → 解码/裁切 → Fotufilm 冲洗 → 旋转/落盘 → 系统相册 → 成片页 / 照片墙 / 卷计数 → 对账。
**对象：** 工作树 `45c6e84`（v0.6.2）+ 未提交改动（D-067 色彩链、成片页动作行、TabBar 等，`git diff` 已逐段看过）。
**方法：** 只读代码 + 文档对照；未跑构建、未上真机。
**标记：** `F` = 代码事实可推出；`H` = 推断，需真机/实验；`I` = 仅信息。

**一句话结论：** 单张照片能从快门走到系统相册，但"一卷 36 张"的计数模型、成片页与照片墙两套单张详情、以及"保存成功"的定义这三处是互相矛盾的；色彩/分块链路另外有若干"注释承诺 ≠ 实测"的问题。

---

## 一、结构性问题（P1）

### P1-1 「卷」没有边界：换胶卷不换卷、换卷重置所有胶卷、切回旧卷计数复活

**证据**
- `navigation/AppViewModel.kt:79-86`：`rollShots` = 现存照片中 `capturedAt > rollStartedAt` **且** `stockId == 当前选中卷` 的张数。
- `navigation/AppViewModel.kt:150-157`：`selectStock()` 只改 `_selectedStockId`；只有 `newRoll()`（相机页「换卷」）推进 `rollStartedAt`。
- `ui/film/films/FilmLibraryScreen.kt:215-230`：胶卷列表行点按只调 `onSelect(stock.id)`；`onNewRoll` 参数（`:133`）在库内**从未被调用**，库里的 CURRENT 卡也没有可点的换卷动作。
- `README.md:46`：把「真机连切三卷：`5/36 → 0/36 → 5/36`」当作"D-046 口径完成"的证据。

**为什么不成立 `[F]`**
物理上相机一次只能装一支卷。"装卷"（在胶卷库点另一支卷）没有结束上一支卷，所以：A 卷拍 36 张 → 装 B 卷 → 计数 0 → B 也拍 36 张；中间从未按过「换卷」，但已经拍了 72 张。再切回 A 卷，计数又回到 36。反过来说，按「换卷」会把**所有**胶卷的计数一起归零。`README.md:46` 引用的实测结果正是这个矛盾的现场记录，不是验证通过。

**后果** 卷号、`P-05`、进度条、满卷判断全部建立在一个不存在的"卷"上。

**建议** 装卷 = 换卷：`selectStock()` 必须同时 `startNewRoll()`（或至少把 `rollStartedAt` 按 stock 分开存）；胶卷库 CURRENT 卡要能开新卷（`onNewRoll` 已经在参数里，只是没人调）。

---

### P1-2 计数与片号是"存活照片数"，不是"曝光次数"——删相册照片会让卷倒退

**证据**
- `navigation/AppViewModel.kt:255-259`、`:270-282`：`_roll` 由 `photoStore.list()`（目录里现存文件）重建；对账删除后整体重读。
- `navigation/AppViewModel.kt:79-86`：`rollShots` 从 `_roll` 数出来。
- `ui/AppRoot.kt:598`：成片页 `frameIndex = rollShots`；`ui/result/ResultScreen.kt:294-299`、`:506-507` 用它拼 `05 / 36` 与 `P-05`。
- `ui/gallery/PhotoWallScreen.kt:141-149`、`ui/gallery/GalleryLogic.kt:89-100`：照片墙/查看器又是另一套"当前可见列表的连续序号"。

**为什么不成立 `[F]`**
成片页的 `P-05` 声称是"本卷第几张"，但它的输入是"当前还存在的照片数"。用户在系统相册删 8 张再回来，计数器会从 20 掉到 12，快门重新可用（能拍到 36 张以上）；同理，删除或对账之后同一张照片的"第几张"可能变小。照片墙的 `FRAME 05` 与成片页的 `P-05` 又是两个口径，同一张照片可以在两处显示不同号。

**后果** 卷计数会倒退、满卷可被绕过、片号会跳号/回退；"第几次曝光"这个事实根本没被持久化。

**建议** 在按下快门那一刻分配并持久化曝光序号（写进文件名或元数据）；计数用序号，不用存活文件数。照片墙如果继续用"列表序号"，必须在文案上换一个词，别叫 FRAME。

---

### P1-3 冲洗中的那 2–4 秒里，"这张属于哪卷/第几张/卷名"用的是错的来源

**证据**
- `camera/CaptureState.kt:17-26`：`Capturing/Developing/Saving` **各自携带拍摄时的 `FilmStock`**。
- `navigation/AppViewModel.kt:202`：`Capturing(stock)` 用的是快门瞬间的卷；`:209-222` 冲洗完成才调 `save()`，`capturedAt = System.currentTimeMillis()` 在 `save()` 调用处才求值（冲洗结束后）。
- `ui/AppRoot.kt:594-598`：成片页卷名取 `(capture as? Done)?.photo?.stockLabel ?: stock?.label`（`stock` = 当前选中卷），片号取实时的 `rollShots`。
- `ui/film/FilmCaptureScreen.kt:386-390`：「换卷」键不受 `canShoot` 约束，冲洗期间可点（确认框 `:411-437`）；`AppViewModel.kt:150-157` 换卷只推 `rollStartedAt`。

**为什么不成立 `[F]`**
`CaptureState` 已经带着正确快照，UI 却回头读"当前状态"，等于把 3 秒内发生的切卷/换卷污染进这张照片的归属。最直接的一条：冲洗中途回相机按「换卷」，`rollStartedAt` 被推到此刻，而这张照片的 `capturedAt` 在冲洗**结束**时才取，必然大于新的 `rollStartedAt` → 它被算进新卷，成片页显示 `01 / 36`。

**后果** 换卷/装卷操作与在途照片的归属互相踩；`frameIndex`、卷名、卷计数都可能对不上这张照片。

**建议** 快门瞬间固定 `(stock, capturedAt, exposureIndex)` 并随 `CapturedFrame`/`CaptureState` 一路带到落盘与成片页；UI 不再读实时 `rollShots/activeStock` 来回答"这张"的问题。

---

### P1-4 成片页与照片墙是两套"单张详情"：EXIF 只存在于刚拍完那一屏

**证据**
- `ui/AppRoot.kt:298`：`AppEvent.OpenResult` 的**唯一**发出点是快门按下之后；`navigation/AppState.kt:75-81` 虽然允许从 `GALLERY` 进 `RESULT`，但没有任何调用点（`ui/gallery/PhotoWallScreen.kt:201-210` 点开走的是内嵌 `PhotoViewer`）。
- `ui/result/ResultScreen.kt:127`：`onOpenPhotoWall` 参数存在、`ui/AppRoot.kt:584` 也传了，但函数体内**从未使用**；动作行第三项"相册"走的是 `onMore` → `Intent.ACTION_VIEW`（`ui/AppRoot.kt:635-646`），不是应用内照片墙。
- `ui/gallery/PhotoViewer.kt:74-76`：查看器明确"不显示 EXIF/尺寸/曝光"。

**为什么不成立 `[F]`**
文档把成片页定义成"Photo Detail / 看完整成片 + 真实 EXIF + 真实片号"，但它在运行时只能由"刚刚拍完"这一条路径进入。用户在照片墙点开任意一张历史照片，看到的是另一套详情页：有卷名、日期、列表序号，没有 EXIF、没有 `P-05`、没有分享/保存。同一张照片在两处能得到的信息不一致，且"成片页"对历史照片不可达。

**后果** README 里"新拍成片参数网格显示真实值、历史照片仍 `—`"（`README.md:48`）这句话，在 UI 上其实没有"历史照片的成片页"可看；"相册"这个标签在两个层级指向两个不同的东西。

**建议** 二选一：让 `PhotoViewer` 承载成片页的详情（EXIF/片号/动作），或让 `RESULT` 接受一个 `photoId` 从照片墙进入。顺手把 `onOpenPhotoWall` 死参数与"相册→ACTION_VIEW"改掉。

---

### P1-5 "已保存"与"能分享/能给别人看"不是一个状态，失败后没有出口

**证据**
- `camera/PhotoStore.kt:227-261`：`publishToGallery()` 声明为"任何一步失败都只降级成照片墙里有、相册里没有，绝不抛"。
- `ui/AppRoot.kt:614-646`：分享与"相册/更多"**只认** `photo.galleryUri`；为 null 时只弹 `Toast("成片还没写入系统相册")`，没有重试/没有 FileProvider 兜底。
- `ui/result/ResultScreen.kt:580-585`：状态行只写"已保存 · Ns"，不区分只在本机还是也进了系统相册。

**为什么不成立 `[F]`**
照片确实"保存成功"（本地文件在、照片墙能看到），但应用对外的两条动作永久失效，用户唯一的出路是重拍。而 `galleryUri` 只在"刚拍完"这一屏有值（`PhotoStore.kt:46-49` 注释明说从目录重建的记录没有它），所以重新进入（虽然现在进不来，见 P1-4）也不可能恢复分享能力。

**建议** 动作行按 `galleryUri == null` 给出真正的"重新写入相册"动作；分享改用 FileProvider/MediaStore 任一可用来源；状态行写清"已存本机 / 已存相册"。

---

### P1-6 `save()` 后半段失败会被报成"冲洗失败"，但照片已经落盘并进了相册

**证据**
- `camera/PhotoStore.kt:118-138`：顺序是 full 落盘 → `writeExif` → `publishToGallery` → `markPublished` → **缩略图**。
- `camera/PhotoStore.kt:153-156`：`finally` 只回收 `upright`；`:135-138` 的 `thumbBitmap` 只在成功路径回收。
- `navigation/AppViewModel.kt:210-239`：`save()` 抛任何异常 → `CaptureState.Failed`。

**为什么不成立 `[F]`**
缩略图阶段（压缩/写盘/rename）失败时，全尺寸与相册副本已经存在，但状态机认为这张没拍成：照片墙要等下次 `refreshRoll`/重启才出现，用户看到"冲洗失败"点重试 → 生成第二张几乎一样的照片；同时泄漏一张 1440 长边的位图。

**建议** 缩略图失败降级（成片页回退采样 full），或在 `finally` 里清理已发布条目并回收 `thumbBitmap`；无论如何不要让"缩略图失败"等同于"照片没拍成"。

---

## 二、成像链路的逻辑断点（P1/P2）

### P2-7 分块冲洗不可逐像素复现，且 `originY`/边缘延拓两份注释互相矛盾

**证据**
- `fotufilm/FilmEngine.kt:221-232`：每条带子调用 `nativeProcessStrip`，`origin=(0,0)`、`interiorTop=halo` **恒定**；从不把该带在整帧里的行位置传给内核。
- `native/fotufilm_jni.cpp:363-369`：注释写"输入块位于全帧的 `(0, originY)`"，且"调用方负责把 halo 之外的输入行读成边缘延拓 —— kernel 会真的去读这些像素"。
- `fotufilm/FilmEngine.kt:202-203`：注释写"图像边界处直接截断……native 那边本来就把输入块的边界当作图像边界做延拓"——与上一条直接相反。
- `fotufilm/FilmEngine.kt:53-54`、`:192-193`：声称"两次运行可以逐像素比对 / 同一张图两次冲洗逐像素相同"。
- `docs/fotufilm-device-benchmark.md:103-130`：实测只有**首条带子**与整帧一致，之后每条带子的每一行都不同（约 109 处/行），已登记 FOT-015。

**为什么不成立 `[F]`（注释互斥）/`[H]`（可见量级）**
三处说法不可能同时为真。若内核按块内局部坐标推进颗粒，则除第一条外所有带子都从同一相位开始，颗粒会以 `stripRows` 为周期重复；若内核真的按 `originY` 定位，则现在恒传 0 等于每条带子都自称在帧顶。无论哪种，`stripRows`（128 这个"内存/速度旋钮"）都会变成成像参数，同一张照片换档位/换内存预算结果就不同，"逐像素可复现"的契约不成立；自测 parity 的容差也已经承认被颗粒占满（见 P2-8）。

**建议** 先做一次对照实验：把 `originY=tileTop` 传下去（输出画布需相应放大或让 native 支持帧坐标），与整帧结果逐像素比；同一进程内 A→B→A 两次渲染同一 pack 也比一次。

### P2-8 halo 取"各阶段半径的最大值"，而真实 12MP 尺度从未被验证

**证据**
- `native/fotufilm_jni.cpp:327-348`：`nativeMaxHalo` 对 14 个半径 + config 里 3 个 diffusion 半径**逐项取 max**。
- `fotufilm/FilmEngine.kt:148-160`：halo 直接用于条带切分。
- `fotufilm/FilmEngine.kt:362`：`FULL_FRAME_PIXEL_LIMIT = 4_000_000`，而真实 pack 是 12.58MP → 整帧参考路径对真实 pack **永远跑不了**。
- `fotufilm/FilmSelfTest.kt:96-103`：parity 只能选最小的 pack，即 96×64、halo=1 的引擎夹具。

**为什么不成立 `[F]`（max 是事实；12MP 无验证是事实）**
多级串联卷积的输出依赖范围约等于各阶段半径之和，取 max 会系统性偏小；而唯一能验证分块的探针只能在 halo=1 的夹具上跑，"最大半径 vs 半径之和"在这个夹具上几乎无差别。于是"分块 = 整帧"只在**最不敏感**的尺寸上被证明过；真实尺度上是否有接缝，目前没有证据。

**建议** 打印上游 14 个半径与 3 个 diffusion 半径，求和后与 `nativeMaxHalo` 对照；或用 benchmark 构建临时抬高 `FULL_FRAME_PIXEL_LIMIT`，对一张 12MP 合成图做整帧 vs 分块。

### P2-9 少数机型会"转两次"：解码时按 EXIF 转正，落盘时又按 sensor 角转

**证据**
- `camera/CameraPreview.kt:636-644`：`decodeJpeg` 情况 2（采集尺寸是 target 的转置）先 `applyExifOrientation` 把图转正，返回尺寸恰为 pack 尺寸。
- `camera/PhotoStore.kt:116`、`:159-165`：冲洗后按 `rotationDegrees`（`CameraPreview.kt:336-349`，`(sensor - display) % 360`）再转一次。
- `camera/CameraPreview.kt:653-657`：情况 3 失败消息只比较 `rawWidth/rawHeight >= target`，没有转置分支。

**为什么不成立 `[F]`（路径）+ `H`（机型）**
EXIF 旋转语义就是"转到正立"，`rotationDegrees` 也是"转到正立"；两条都用就是叠加。命中情况 2 的机型会多转 90/180°。另外，若采集的是 `3072×4096` 且 EXIF=NORMAL（项目自己声明 ROTATION_0 下不写 EXIF），情况 2 转不出目标尺寸 → 落到情况 3 → 因为 `3072 < 4096` 直接报"这台设备的采集尺寸只有 3072×4096，请换一卷尺寸更小的胶卷"——而转置后正是 4096×3072，这台设备其实能拍。

**建议** 情况 2 里既然已经转到正立，就把 `rotationDegrees` 归零（或直接把 EXIF 角从 sensor 角里扣掉）；错误消息加转置判断。

### P2-10 成片页的"相机保活"被底部 TabBar 打穿

**证据**
- `ui/AppRoot.kt:139-143`：注释与代码明确"RESULT 期间相机继续挂着，免得销毁 PreviewView 把刚发起的采集掐断"。
- `ui/AppRoot.kt:437-441`、`:691-709`：`showBottomNav` 对 RESULT 为 true（TabBar 在成片页可见），点任何 Tab → 目的地变化。
- `ui/AppRoot.kt:155-160`：`cameraBound=false` → `handle=null` + `onCameraReleased()`；`camera/CameraPreview.kt:542-550` 的 `onDispose` 会 `unbindAll()`。

**为什么不成立 `[H]`**
"保活"只在用户停留在 RESULT/CAMERA 时成立。快门后 0.3–1 秒内（`takePicture` 回调还没回来）点「胶卷/设置/发现」，会解绑正在采集的会话，`takePicture` 回调丢失 → 5 秒超时 → "相机没有响应"失败。这正是 `CameraPreview.kt:49-56` 里描述的病症。

**建议** `_capture.isBusy` 期间禁用 Tab 离开成片页（或延迟解绑到采集回调结束）。验证：按快门后 0.5s 内点「胶卷」，看是否出现 `CaptureTimeoutException`。

### P2-11 首绑不约束 + 回相册清空 supportedSizes → 每次回取景重绑两次；attached 轮询超时后本次页面内无自愈

**证据**
- `camera/CameraPreview.kt:419-420`：`LaunchedEffect` 的 key 是 `targetSize` 对象本身，null 与"等于 attached 的尺寸"不视为同一档。
- `navigation/AppViewModel.kt:176-178`：`onCameraReleased()` 把 `CameraState` 打回 `Pending`，`buildCameraUi` 于是 `programmedSize=null`。
- `camera/CameraPreview.kt:476-486`：`attachedSurfaceResolution` 最多轮询 40×25ms；超时后 `attachedSize = targetSize`，`:527` 的 `supportedSizes = setOfNotNull(attachedSize)` 为空 → 快门永久置灰，而 key 没变不会重试。

**为什么不成立 `[F]`（机制）/`H`（是否必现）**
这是 2026-10-20 复审 P1-10、P2#10 的原样残留（`docs/reviews/2026-10-20-main-chain-review.md:200-216`、`:233`），当前代码没有等价性判断、也没有超时后的重试。

**建议** key 用"programmedSize 的等价类"；`onCameraReleased` 保留上一轮 `supportedSizes`；轮询失败后进入重绑/重试而不是留在空集合。

---

## 三、卷/相册/UI 的小逻辑矛盾（P2/P3）

### P3-12 胶卷库能装入"这台设备拍不了"的卷，相机绑定时又会静默改卷

`ui/film/films/FilmLibraryScreen.kt:215-230` 列出所有能解析的 pack，不过滤设备能力；`navigation/AppViewModel.kt:169-173` 在绑定后若当前卷不能被服务，会**自动切到**另一支能服务的卷——用户的选择被悄悄改掉，且界面上没有任何"因为这台设备拍不了所以换了卷"的说明。建议：装在库内就按 `supportedSizes` 置灰并说明原因；自动切卷必须提示。

### P3-13 `capturedAt` 是"落盘时间"，却被当作"拍摄时间"

`navigation/AppViewModel.kt:215`（`System.currentTimeMillis()` 在冲洗完成后才求值）→ `PhotoStore` 文件名/`id`、`DATE_TAKEN`、成片页"真实拍摄时间"（`ui/result/ResultScreen.kt:866-877`）、照片墙日期全部同源。与真实快门最多差 2–4s，跨分钟/跨天时用户可见；成片 JPEG 也没写 `DateTimeOriginal`。建议在 `takeJpeg` 成功时取时间戳随帧带回。

### P3-14 对账的三个不对称

1. **回收站也算删除**：MediaStore 默认查询不含 trashed 条目，对账会把私有副本删掉；用户从回收站恢复后照片墙不会回来（`README.md:541` 已承认"已知不对称"）。
2. **收藏不清理**：`camera/FavoritesStore.kt:66-68` 只增删 id，对账删照片后 id 永远留着。
3. **对账请求被丢弃**：`navigation/AppViewModel.kt:270-282` 用 `reconciling` 直接 return，进照片墙触发的对账若撞上回前台那次，已删照片会挂到下一次 resume（旧 review P2#16 未修）。

### P3-15 补齐相册只做一次，失败的永不重试

`camera/PhotoStore.kt:275-299`：补传失败的记入 `gallery_missing_ids`（防误删，正确），但 `KEY_BACKFILL_DONE` 仍然置位（`:296`）→ 这些照片永久只在本机，没有重试入口。

### P3-16 成片页的死路径 / 死参数 / 文档不一致

- `EMPTY`（"还没有拍过"）态在运行时不可达：RESULT 唯一入口是快门，而快门必然先置 `Capturing`（`ui/result/ResultScreen.kt:149-154`、`:883-926`；`ui/AppRoot.kt:298`）。
- `onOpenPhotoWall` 死参数（`ui/result/ResultScreen.kt:127`）；`galleryOrigin/resultOrigin` 只写不读（`navigation/AppState.kt:48-52`，D-065 已说明"仅保留字段"）。
- 当前动作行是 3 个（收藏/分享/相册）且页头无「更多」（`ui/result/ResultScreen.kt:728-763`、`:303-339`），与 `docs/paper-archive-1to1-layout.md:24,42,108`、`DECISION_LOG.md:1637`（D-066 第 6 条：保存/收藏/分享/更多接真实行为）不一致——这是工作树里未提交的改版，文档没跟上。

### P3-17 安全/健壮性小项（都是代码事实）

- **NaN 不失败**：`FilmEngine.kt:258-260` 只计数；`LinearColor.kt:127-138` 把 NaN 静默编码成 0；`AppViewModel.kt:296-304` 只打日志；`FilmSelfTest` 的 verdict 不检查 Bitmap 路径的 `nonFinite`（`FilmSelfTest.kt:385-407`）→ 内核吐 NaN 时用户拿到 SAVED 的黑点照片、自测仍然 PASS。
- **ABI 版本从不校验**：`fotufilm/FotufilmNative.kt:36-40` 注释承诺用版本号防静默错配，实际只有 `FilmSelfTest` 读；`FilmEngine.kt:22-23` 把异常吞成"Halide 内核未就绪"。
- **FSWP v1 接受但只解析 v2**：`native/fotufilm_jni.cpp:94-96`（旧 review P2#8 未修）。
- **`inPreferredColorSpace` 未固定**：`camera/CameraPreview.kt:660-667`、`:686-692`；若相机给带 P3 ICC 的 JPEG，`LinearColor` 的 sRGB 解码表会拿错基色 `[H]`。
- **`encodeRgb` 无 scratch 时每像素分配**：`LinearColor.kt:160`（生产路径避开了，未来入口会踩）。
- **halo 极大时降档循环不收敛**：`FilmEngine.kt:154-158`，`estimatedTileBytes` 超预算后最低只降到 8 行，256MB 不是硬上界。
- **取景框公式可为负**：`ui/film/FilmCaptureScreen.kt:167-171`，`(maxHeight-340dp)*0.75` 在小窗口/分屏下为负；D-066 用锁竖屏绕过了正常路径，但没有 clamp。

---

## 四、文档 / 注释漂移（"说 A、做 B"）

| # | 文档说法 | 代码事实 |
|---|---|---|
| 1 | `README.md:59`、`docs/fotufilm-device-benchmark.md:137-138`：Rec.2020 口径"未开始 / 只做到线性+sRGB 基色" | 工作树 `fotufilm/LinearColor.kt:5-52` 已做 sRGB→Rec.2020、P3→sRGB、抖动（D-067，未提交） |
| 2 | `README.md:82-96,112-114`：管线图两端都是"linear / sRGB 编解码" | 现在是 Rec.2020 进、Display P3 出、三次矩阵 + 抖动 |
| 3 | `native/fotufilm_jni.cpp:6-13`：输入输出都写成同一套 "display-linear RGB" | 输入 Rec.2020、输出 P3（D-067 的证据链） |
| 4 | `README.md:46`：`5/36 → 0/36 → 5/36` 证明卷计数完成 | 这正是 P1-1 的矛盾现场 |
| 5 | `fotufilm/FilmEngine.kt:31-32`："128 行在 halo 几十像素时重叠率约 30%" | `2*halo/stripRows ≈ 86–97%`（旧 review P1-9，仍未改） |
| 6 | `fotufilm/FilmEngine.kt:53-54,192-193`："两次运行可以逐像素比对 / 同一张图两次冲洗逐像素相同" | `docs/fotufilm-device-benchmark.md:103-130` FOT-015：只有首条带一致，颗粒不可复现 |
| 7 | `fotufilm/FilmSelfTest.kt:439-440`：容差 3 是"给舍入留余量，不是给几何错误" | 同文档 `:128-130`：容差 3 已被颗粒相位占满，不再具备探测 3 色阶几何误差的能力 |
| 8 | `fotufilm/FilmStock.kt:10-12`、`camera/CameraPreview.kt:381-384`：采集尺寸"必须精确等于 pack" | D-037 修订：≥ pack 即可、中心裁切（`AppViewModel.kt:329-334`） |
| 9 | `AndroidManifest.xml:4-8`：不写系统相册、因此不需要存储权限 | `PhotoStore.publishToGallery()` 写 `Pictures/Pocket CCD/`（API 29+ 分区存储不需要权限，结论对、理由已过期） |
| 10 | `ui/result/ResultScreen.kt:137-142`：收藏"本轮不持久化"、分享/更多"待接入" | 收藏已持久化（`FavoritesStore`），分享/更多已接真实 Intent |
| 11 | `docs/paper-archive-1to1-layout.md:66`："装卷 900ms 动画" | `FilmLibraryScreen.kt:147` 注释明说已删掉模拟装卷 |
| 12 | `README.md:62`："保存/分享/更多/发现内容等回调默认空，待接入" | 已接入；且工作树又把「保存」「更多」删了（见 P3-16） |

---

## 五、2026-10-20 复审中"仍未关闭"的项（不重复展开）

| 旧编号 | 问题 | 现状 |
|---|---|---|
| P1-7 | 相册"删除到回收站"被当成永久删除 | 仍在，已写入 README 排查表当已知行为 |
| P1-9 | halo 30% 注释 / strip 太小（内核时间约 45% 花在重叠行） | 仍在（见上表 #5） |
| P1-10 | 冷启动/回相册绑两次相机 | 仍在（P2-11） |
| P2#3 | 卷计数是存活照片数 | 仍在（P1-2） |
| P2#4 | 换胶卷不换卷 | 仍在（P1-1） |
| P2#5 | 计数依赖墙钟，回拨后 36 上限失效 | 仍在（`capturedAt` 仍取墙钟） |
| P2#7 | `nativeAbiVersion` 从不校验 | 仍在 |
| P2#8 | FSWP v1 被当 v2 解析 | 仍在 |
| P2#9 | `render(destination=null)` 白渲染 48MB；`FilmPack` 句柄泄漏路径 | 仍在（`FilmEngine.kt:43-49`、`FilmPack.kt:62-84`） |
| P2#10 | 首绑轮询超时后快门永久置灰 | 仍在（P2-11） |
| P2#16 | 对账第二次请求被静默丢弃 | 仍在（P3-14.3） |

已确认修好的：P0-1/P0-2/P0-3（对账三阀 + 补传记账）、P0-4（快门超时）、P1-4（主线程解码）、P1-5（闪光灯重绑回填）、P1-6（查看器）、P1-11（resume 探针重试）、P1-12（绑定退避重试）、P1-13（0.6× 隐藏）、P1-14（EXIF 端到端）、P1-15（权限回读）。

---

## 六、复核过、没有问题的点（避免重复怀疑）

- `LinearColor` 的解码表（256 项，精确公式）与编码表（4097 项、插值误差约 0.005 码值、越界已挡）。
- `render` 的逐像素编码路径与整帧 `encodeInto` 在"同 seed + 同扫描序"下逐位等价；抖动步进每像素 3 步，顺序一致。
- 尺寸契约闭合：`decodeJpeg` 三种情况都返回 target 尺寸；`FilmEngine.render` 的 `require` 在生产路径不会被触发。
- 冲洗后旋转（`PhotoStore.save:116`）与 pack 空间参数不冲突；90° 整转不改像素密度。
- Bitmap 所有权链（develop 的 catch/finally、capture 的 finally、save 的 `upright === bitmap` / `thumbBitmap === upright`）没有重复回收。
- 条带几何算术自洽（tileTop/tileBottom/interiorTop、`setPixels` 的 offset/stride 对应正确），图像边界处不越界。
- JNI 9 个符号与 C++ 逐条对齐；`planeAddress` 有 direct + 容量校验；origin/interior 边界校验齐全。
- `FilmEngine.renderSynthetic`/`renderFullFrame` 目标画布尺寸不校验（P2-10）——这条是问题，但只在自测/未来入口触发。

---

## 七、建议的复查顺序

1. **先定"卷"的语义**（P1-1/P1-2）：装卷是否等于换卷？曝光序号要不要持久化？这一条不定，计数器、片号、满卷、对账后显示全都在漂。
2. **把成片页与查看器合成一个"单张详情"**（P1-4），顺手解决 EXIF 不可达、"相册"双语义、两个死参数。
3. **修 P1-6 的失败语义与 P1-5 的分享出口**：它们决定"拍到了"和"能用"是否同一件事。
4. **真机做三个成像实验**（P2-7/P2-8/P2-9）：`originY=tileTop` 对照、12MP 整帧 vs 分块、同一进程重复渲染逐像素比对；结果会一次性回答"颗粒重复/halo 不足/方向双转"。
5. **最后再批量修文档漂移**（第四节表），避免继续出现"按注释改代码把正确实现改坏"。

---

*本报告只读，未修改任何代码，未跑构建，未上真机。所有 `[H]` 项需要真机或实验确认。*
