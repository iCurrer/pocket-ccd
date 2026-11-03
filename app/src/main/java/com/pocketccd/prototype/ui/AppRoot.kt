package com.pocketccd.prototype.ui

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.ui.platform.LocalConfiguration
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketccd.prototype.BuildConfig
import com.pocketccd.prototype.camera.CameraHandle
import com.pocketccd.prototype.camera.CameraPreview
import com.pocketccd.prototype.camera.CaptureState
import com.pocketccd.prototype.camera.ExposureProfile
import com.pocketccd.prototype.camera.FavoritesStore
import com.pocketccd.prototype.camera.LensStop
import com.pocketccd.prototype.camera.ImprintPrefs
import com.pocketccd.prototype.camera.PhotoStore
import com.pocketccd.prototype.camera.SavedPhoto
import com.pocketccd.prototype.fotufilm.FilmSelfTestActivity
import com.pocketccd.prototype.navigation.AppDestination
import com.pocketccd.prototype.navigation.AppEvent
import com.pocketccd.prototype.navigation.AppViewModel
import com.pocketccd.prototype.navigation.showBottomTabs
import com.pocketccd.prototype.ui.camera.CameraScreen
import com.pocketccd.prototype.ui.detail.DetailStatus
import com.pocketccd.prototype.ui.detail.PhotoDetailScreen
import com.pocketccd.prototype.ui.discover.DiscoverScreen
import com.pocketccd.prototype.ui.films.FilmLibraryScreen
import com.pocketccd.prototype.ui.gallery.GalleryScreen
import com.pocketccd.prototype.ui.gallery.PhotoViewer
import com.pocketccd.prototype.ui.gallery.ViewerRequest
import com.pocketccd.prototype.ui.gallery.PhotoWallItem
import com.pocketccd.prototype.ui.glass.GlassBackdrop
import com.pocketccd.prototype.ui.glass.LocalGlassBackdrop
import com.pocketccd.prototype.ui.glass.GlassNavBar
import com.pocketccd.prototype.ui.glass.GlassTab
import com.pocketccd.prototype.ui.settings.SettingsScreen
import com.pocketccd.prototype.ui.theme.PocketCcdTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 应用外壳：把 [AppViewModel] 接到这一套玻璃界面上，并持有相机句柄。
 *
 * ## 这一层负责什么
 *
 * 1. **相机会话的生命周期**。句柄刻意留在 Composable 而不是 ViewModel 里：它绑
 *    `LifecycleOwner`，页面一销毁就必须作废，放进 ViewModel 会让它活过 Activity。
 * 2. **硬件状态的重绑复原**。变焦 / EV / 闪光灯 / 九宫格都记在这一层，因为去一趟相册
 *    回来相机会重绑，硬件会回到 1× / 0EV / OFF；界面若自己记着 2×，就会出现
 *    "按钮亮着、画面是 1×"。
 * 3. **页面切换**。五个目的地 + 一个压栈的详情页，没有导航库、没有回退栈 ——
 *    `reduce` 是纯函数，返回目标由 `detailOrigin` 显式记住（D-071）。
 *
 * ## 取景页为什么在详情页期间仍然挂着
 *
 * `takePicture` 是异步的。按快门 → 立刻导航到详情页 → 若此时把 `PreviewView` 从
 * 组合里拿掉，CameraX 会 `unbindAll`，**刚发起的采集直接被掐断**，用户拿到一张
 * 失败页。所以 `captureVisible` 把 `DETAIL` 也算在内：取景页继续挂在最底下（被
 * 详情页完全遮住），返回时也省一次重绑。
 *
 * 代价是详情页下方那一层仍然能收到触摸。因此纸面层挂了一个空 `clickable`
 * （见 `swallowTaps`）把点击吃掉，否则用户在详情页上点一下会顺带对焦取景器。
 */
@Composable
fun PocketCcdRoute(viewModel: AppViewModel = viewModel()) {
    val context = LocalContext.current

    // 只有一套固定色板（D-074 去掉了"跟随壁纸取色"）。
    // 相机 App 的界面是照片的衬底，强调色跟着壁纸变会让同一张照片在不同桌面下
    // 看起来不一样；而且它只覆盖选中态/强调文字，收益远小于"不可预期"的代价。
    PocketCcdTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val capture by viewModel.capture.collectAsStateWithLifecycle()
        val cameraUi by viewModel.cameraUi.collectAsStateWithLifecycle()
        val roll by viewModel.roll.collectAsStateWithLifecycle()
        val cards by viewModel.filmCards.collectAsStateWithLifecycle()
        val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()
        val notice by viewModel.stockSwitchedNotice.collectAsStateWithLifecycle()

        val photoStore = remember { PhotoStore(context) }
        val favorites = remember { FavoritesStore(context) }
        var favoriteIds by remember { mutableStateOf(favorites.ids()) }

        // 片边 / 日期后背（D-081）：状态提到这里，因为设置页改、拍摄页显示、
        // 快门时快照进 viewModel —— 三处必须是同一份值，否则会出现
        // "设置里关着、成片却带片边"这种最难受的 bug。
        val imprintPrefs = remember { ImprintPrefs(context) }
        var imprint by remember { mutableStateOf(imprintPrefs.load()) }

        var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
        var handle by remember { mutableStateOf<CameraHandle?>(null) }
        var previewError by remember { mutableStateOf<String?>(null) }

        var zoomRatio by remember { mutableFloatStateOf(1f) }
        var cameraNonce by remember { mutableIntStateOf(0) }
        var lensStops by remember { mutableStateOf<List<LensStop>>(emptyList()) }
        var exposureProfile by remember { mutableStateOf<ExposureProfile?>(null) }
        var evIndex by remember { mutableIntStateOf(0) }
        var flashOn by remember { mutableStateOf(false) }
        var flashAvailable by remember { mutableStateOf(true) }
        // rememberSaveable：转屏/分屏重建后格子不该自己消失。
        var gridOn by rememberSaveable { mutableStateOf(false) }

        var bindFailures by remember { mutableIntStateOf(0) }
        var resumeProbe by remember { mutableIntStateOf(0) }
        var shutterFlash by remember { mutableIntStateOf(0) }

        // 非空 = 详情页看的是相册里翻出来的那张；null = 看刚拍的那张。
        var detailPhotoId by remember { mutableStateOf<String?>(null) }

        // 非空 = 全屏查看器开着。**由外层持有**（而不是 GalleryScreen 内部）：
        // 只有外层知道该不该画 TabBar，状态藏在页面里就会出现"TabBar 压在查看器上"（D-077）。
        var viewerRequest by remember { mutableStateOf<ViewerRequest?>(null) }

        val scope = rememberCoroutineScope()

        // ---- 方向：拍摄页强制横版，其它页竖版（D-083）----
        // 片边是横的、照片现在也是横的，取景页横过来才不用"竖照片塞横片基"。
        // manifest 里已经不再锁竖屏；这里按目的地改 requestedOrientation。
        val activity = remember(context) { context.findActivityOrNull() }
        val configuration = LocalConfiguration.current
        LaunchedEffect(state.destination) {
            val wanted = if (state.destination == AppDestination.CAMERA) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            if (activity != null && activity.requestedOrientation != wanted) {
                activity.requestedOrientation = wanted
            }
        }
        // 方向一变必须重绑相机：ImageCapture 的 targetRotation 只在绑定那一刻取一次，
        // 不重绑，横过来拍出来的照片方向就是错的（而且不会报错，只是悄悄歪 90°）。
        LaunchedEffect(configuration.orientation) {
            if (state.destination == AppDestination.CAMERA) cameraNonce++
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            hasPermission = granted
            if (!granted) viewModel.onCameraError("需要相机权限才能取景")
        }

        val destination = state.destination
        // 取景页挂载条件：
        // - 就在取景页：当然要挂；
        // - 在详情页 **且是从快门进来的**：异步采集还没落地，此时销毁 PreviewView
        //   会 unbindAll 把它掐断。从相册点开的那张则不该点亮摄像头 —— 看了一眼老照片
        //   就让指示灯亮起来，是这一行第二个条件的全部理由。
        val captureVisible = destination == AppDestination.CAMERA ||
            (destination == AppDestination.DETAIL &&
                state.detailOrigin == AppDestination.CAMERA)
        val cameraBound = captureVisible && hasPermission

        // 取景/详情期间别让屏幕睡着：相机 App 的常识（等一个瞬间构图时屏幕一黑，
        // 就得重新点亮再重新等）。离开相机就撤销，不在别的页面白耗电。
        val view = LocalView.current
        DisposableEffect(captureVisible) {
            view.keepScreenOn = captureVisible
            onDispose { view.keepScreenOn = false }
        }

        // 每次回到前台都重查一次：用户可能刚从系统设置里打开权限再切回来。
        LaunchedEffect(captureVisible) {
            if (captureVisible) {
                hasPermission = context.hasCameraPermission()
                if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // 收起取景界面就解绑，不在后台占着摄像头（会亮指示灯、也会耗电）。
        LaunchedEffect(cameraBound) {
            if (!cameraBound) {
                handle = null
                viewModel.onCameraReleased()
            }
        }

        // 快门超时后换一条会话（复审 P0-4）。超时的那条多半已经废了，
        // 只把 UI 放出"正在冲洗"而不重绑，用户再按一次快门只会原地再等 5 秒。
        val rebindTick by viewModel.rebindRequests.collectAsStateWithLifecycle()
        LaunchedEffect(rebindTick) {
            if (rebindTick > 0) {
                previewError = "相机没有响应，正在重连"
                cameraNonce++
            }
        }

        // 换卷提示是一次性的：留 6 秒后交回 ViewModel 清空，否则它会一直挂在取景页底部。
        LaunchedEffect(notice) {
            if (notice != null) {
                delay(6_000)
                viewModel.consumeStockNotice()
            }
        }

        // 回到前台：对一次相册账 + 重查权限 + 发一个会话探针信号。
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    // 用户最典型的路径就是「切去相册删几张，再切回来」，
                    // 不在这里收，照片墙会一直显示已经删掉的照片。
                    viewModel.reconcileGallery()
                    hasPermission = context.hasCameraPermission()
                    resumeProbe++
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // 探针不能只探一次 —— ON_RESUME 紧跟 ON_START，CameraX 此时常常还没把 surface
        // attach 回来，一次失败就整轮重绑会让「切回来」永远黑一下。最多等 3×250ms。
        LaunchedEffect(resumeProbe) {
            if (resumeProbe == 0) return@LaunchedEffect
            if (handle == null) return@LaunchedEffect
            repeat(3) {
                delay(250)
                if (handle?.isAttached() == true) return@LaunchedEffect
            }
            previewError = "相机连接已断开，正在重连"
            cameraNonce++
        }

        // 权限被永久拒绝后系统弹窗不会再出现（launch 会静默返回 false），
        // 唯一出口是应用详情页的「权限」开关。
        val openAppSettings: () -> Unit = {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            }
        }

        // 进相册之前再对一次：从相册切回来但没经过 ON_RESUME 的路径
        // （分屏、从详情页返回）也能拿到最新结果。
        LaunchedEffect(destination) {
            if (destination == AppDestination.GALLERY) viewModel.reconcileGallery()
            // 换页就关掉查看器：它是模态，不该跨页面活着（比如从详情页返回后
            // 再进相册，不该突然又弹出一张刚才看过的照片）。
            viewerRequest = null
        }

        val photos = remember(roll) { roll.map { it.toWallItem() } }
        val activeStock = cameraUi.activeStock
        val activeCard = cards.firstOrNull { it.id == activeStock?.id }

        // 详情页的数据来源：相册来的**只**按 id 查现存成片，快门来的才读 capture。
        // 不能写成 `id?.let{…} ?: capture.Done.photo`：照片刚在系统相册里被删掉时，
        // 那个回退会悄悄把"上一张刚拍的"当成"你点开的那张"显示出来。
        val detailPhoto: SavedPhoto? = if (detailPhotoId != null) {
            roll.firstOrNull { it.id == detailPhotoId }
        } else {
            (capture as? CaptureState.Done)?.photo
        }
        val detailStatus = if (detailPhotoId != null) {
            // 从相册翻出来的照片永远是已完成态：冲洗在拍下那一刻就结束了。
            DetailStatus.Saved
        } else {
            capture.toDetailStatus()
        }

        // 「已写入相册」的判据归数据层（见 rememberPublished）。这里在组合期读一次：
        // 一次详情只查一次记账，不跟着每帧刷新。
        val detailPublished = detailPhoto
            ?.let { photo -> remember(photo.id) { photoStore.isPublished(photo.id) } }
            ?: false

        val closeDetail: () -> Unit = {
            detailPhotoId = null
            viewModel.onEvent(AppEvent.NavigateBack)
        }

        val sharePhoto: (SavedPhoto) -> Unit = { photo ->
            runCatching {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    photo.fullFile,
                )
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                        "分享这张照片",
                    ),
                )
            }.onFailure { toast(context, "没有能分享这张照片的应用") }
        }

        Box(Modifier.fillMaxSize()) {
            // ---- 底层：取景页 ----
            // 详情页期间必须继续挂着（见类注释）。被纸面层完全遮住时它不参与显示，
            // 但 CameraX 的绑定还活着，异步采集不会被 unbindAll 掐断。
            if (captureVisible) {
                CameraScreen(
                    stockName = activeStock?.label ?: "没有胶卷",
                    shotsWithStock = activeCard?.shots ?: 0,
                    flashOn = flashOn,
                    flashAvailable = flashAvailable,
                    gridOn = gridOn,
                    // 快门要两个条件同时成立：胶卷/相机都就绪，**并且**手里真的有会话句柄。
                    // 重绑期间 cameraUi 还停在上一轮的 Bound（canShoot 仍为 true），
                    // 而 handle 已被置空 —— 这时快门亮着按下去只会静默无反应。
                    canShoot = cameraUi.canShoot && handle != null,
                    // 会话问题优先：相机没连上时，"正在冲洗上一张"之类的原因没有意义。
                    blockReason = previewError ?: when {
                        cameraUi.canShoot && handle == null -> "相机正在重连"
                        else -> cameraUi.blockReason
                    },
                    notice = notice,
                    permissionGranted = hasPermission,
                    lastThumbnail = activeCard?.latestThumbnail,
                    zoom = zoomRatio,
                    lensStops = lensStops,
                    exposureProfile = exposureProfile,
                    evIndex = evIndex,
                    viewfinder = if (cameraBound && activeStock != null) {
                        { modifier ->
                            CameraPreview(
                                lensFacing = androidx.camera.core.CameraSelector.LENS_FACING_BACK,
                                // 用 programmedSize 而不是 pack 尺寸：未验证的尺寸会把
                                // CameraX 锁死在"最接近的更高档"（4000×3000 → 4096×3072）。
                                //
                                // 这里还要等 activeStock：programmedSize 由它推出来，
                                // 提前挂载就会先"无约束绑一次"，等胶卷扫描完再按 pack
                                // 尺寸重绑一次 —— 冷启动每次都绑两次、中间预览黑一下。
                                targetSize = cameraUi.programmedSize,
                                modifier = modifier,
                                rebindNonce = cameraNonce,
                                onReady = { bound ->
                                    handle = bound
                                    if (bound != null) {
                                        previewError = null
                                        bindFailures = 0
                                        viewModel.onCameraBound(bound)
                                        lensStops = bound.lensStops
                                        flashAvailable = bound.hasFlash
                                        // 新会话的闪光灯/变焦/EV 都是"出厂值"，
                                        // 由这里把界面记住的那一档补回去。
                                        bound.setFlash(flashOn)
                                        if (zoomRatio > 1f) bound.requestZoom(zoomRatio)
                                        val profile = bound.exposureProfile()
                                        exposureProfile = profile
                                        if (profile != null && evIndex != profile.index) {
                                            evIndex = bound.requestExposure(evIndex) ?: profile.index
                                        }
                                    }
                                },
                                onError = { message ->
                                    previewError = message
                                    viewModel.onCameraError(message)
                                    // 首次绑定失败没人会来救：LaunchedEffect 的 key 没变，
                                    // 不会自己重试。相机被占 / 相机服务刚开机时等一两秒
                                    // 往往就好了，所以退避重绑，但限 2 次 ——
                                    // "没有后置镜头"这类错误重试一万次也没用。
                                    if (bindFailures < 2) {
                                        bindFailures++
                                        scope.launch {
                                            delay(1_200L * bindFailures)
                                            previewError = "相机没有响应，正在重试"
                                            cameraNonce++
                                        }
                                    }
                                },
                            )
                        }
                    } else {
                        null
                    },
                    onShutter = {
                        val bound = handle
                        if (bound != null && !bound.isAttached()) {
                            // 与其让用户按下去、等 5 秒拿到一张失败页，不如立刻重连并说明。
                            previewError = "相机连接已断开，正在重连"
                            cameraNonce++
                        } else if (bound != null && cameraUi.canShoot) {
                            // 顺序不能反：capture() 会**同步**把状态置为 Capturing，
                            // 先调它再导航，详情页第一帧就是"正在冲洗"，不会闪一下上一张。
                            detailPhotoId = null
                            shutterFlash++
                            // 贴花设置在这里**快照**：冲洗要 2–4 秒，这期间用户去设置页
                            // 改开关，不该影响已经按下快门的那一张。
                            viewModel.capture(bound::takeJpeg, imprint)
                            viewModel.onEvent(AppEvent.OpenDetail)
                        }
                    },
                    onToggleFlash = {
                        val next = !flashOn
                        flashOn = next
                        handle?.setFlash(next)
                    },
                    onToggleGrid = { gridOn = !gridOn },
                    onOpenGallery = { viewModel.onEvent(AppEvent.OpenGallery) },
                    onOpenFilms = { viewModel.onEvent(AppEvent.OpenFilms) },
                    // 上滑抽屉里的"发现"：相机页原本根本到不了这一页（D-079）。
                    onOpenDiscover = { viewModel.onEvent(AppEvent.OpenDiscover) },
                    onOpenSettings = { viewModel.onEvent(AppEvent.OpenSettings) },
                    onOpenAppSettings = openAppSettings,
                    onTapToFocus = { x, y, onResult ->
                        val bound = handle
                        if (bound == null) onResult(false) else bound.focusAt(x, y, onResult)
                    },
                    onZoomChange = { requested ->
                        // 以相机返回的实际倍率为准：请求 2× 但机型只给 1× 时就停在 1×，
                        // 不会出现"按钮亮着、画面没变"。
                        val applied = handle?.requestZoom(requested) ?: requested
                        zoomRatio = applied
                        applied
                    },
                    onEvChange = { requested ->
                        if (requested == evIndex) {
                            requested
                        } else {
                            // 和变焦同理：机型只给 ±1 时请求 +2 会停在 +1。
                            val applied = handle?.requestExposure(requested) ?: requested
                            evIndex = applied
                            applied
                        }
                    },
                )
            }

            // ---- 上层：纸面页 ----
            if (destination != AppDestination.CAMERA) {
                GlassBackdrop(
                    modifier = Modifier.swallowTaps(),
                    // 详情页没有 TabBar，查看器打开时也没有 —— 没人采样这张图层，
                    // 别白录一遍全屏。
                    captureContent = showBottomTabs(destination, viewerRequest != null),
                    // overlay 里的玻璃才能采样"内容图层"；放进 content 里就是自己采自己。
                    overlay = {
                        // 底部玻璃 Tab：只在四个平级页出现。
                        // 详情页是压栈页，它的出口是左上角返回（回起点），不是 Tab；
                        // 查看器是模态，它必须盖住 TabBar（D-077）。
                        if (showBottomTabs(destination, viewerRequest != null)) {
                            GlassNavBar(
                                active = destination.toTab(),
                                onSelect = { tab ->
                                    when (tab) {
                                        GlassTab.Camera -> viewModel.onEvent(AppEvent.OpenCamera)
                                        GlassTab.Gallery -> viewModel.onEvent(AppEvent.OpenGallery)
                                        GlassTab.Films -> viewModel.onEvent(AppEvent.OpenFilms)
                                        GlassTab.Discover -> viewModel.onEvent(AppEvent.OpenDiscover)
                                        GlassTab.Settings -> viewModel.onEvent(AppEvent.OpenSettings)
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = 10.dp),
                            )
                        }

                        // 查看器画在 overlay 里（TabBar 之上），并且**不采样页面背景** ——
                        // 它背后是照片，采样页面图层只会把相册网格糊到工具栏上。
                        viewerRequest?.let { request ->
                            CompositionLocalProvider(LocalGlassBackdrop provides null) {
                                PhotoViewer(
                                    photos = request.photos,
                                    initialIndex = request.index,
                                    favoriteIds = favoriteIds,
                                    onToggleFavorite = { item ->
                                        favoriteIds = favorites.toggle(item.id)
                                    },
                                    onOpenDetail = { item ->
                                        viewerRequest = null
                                        detailPhotoId = item.id
                                        viewModel.onEvent(AppEvent.OpenDetail)
                                    },
                                    onClose = { viewerRequest = null },
                                )
                            }
                        }
                    },
                ) {
                    when (destination) {
                        AppDestination.GALLERY -> GalleryScreen(
                            photos = photos,
                            favoriteIds = favoriteIds,
                            onToggleFavorite = { item ->
                                favoriteIds = favorites.toggle(item.id)
                            },
                            onOpenDetail = { item ->
                                detailPhotoId = item.id
                                viewModel.onEvent(AppEvent.OpenDetail)
                            },
                            onOpenCamera = { viewModel.onEvent(AppEvent.OpenCamera) },
                            onRefresh = {
                                viewModel.refreshRoll()
                                viewModel.reconcileGallery()
                            },
                            // 点开一张：交给外层画在第上一层，并按需收起 TabBar（D-077）。
                            onOpenViewer = { request -> viewerRequest = request },
                        )

                        AppDestination.FILMS -> FilmLibraryScreen(
                            cards = cards,
                            selectedId = activeStock?.id,
                            onSelect = viewModel::selectStock,
                            onRefresh = viewModel::refreshStocks,
                            onOpenCamera = { viewModel.onEvent(AppEvent.OpenCamera) },
                        )

                        AppDestination.DISCOVER -> DiscoverScreen(
                            photoCount = photos.size,
                            favoriteCount = photos.count { it.id in favoriteIds },
                            stockCount = cards.size,
                            storageBytes = storageBytes,
                        )

                        AppDestination.SETTINGS -> SettingsScreen(
                            stockCount = cards.size,
                            photoCount = photos.size,
                            versionName = BuildConfig.VERSION_NAME,
                            imprint = imprint,
                            onToggleFilmEdge = {
                                val next = imprint.copy(filmEdge = !imprint.filmEdge)
                                imprint = next
                                imprintPrefs.save(next)
                            },
                            onToggleDateBack = {
                                val next = imprint.copy(dateBack = !imprint.dateBack)
                                imprint = next
                                imprintPrefs.save(next)
                            },
                            onRefreshStocks = {
                                viewModel.refreshStocks()
                                toast(context, "正在重新扫描胶卷")
                            },
                            // 诊断入口只给 debug / benchmark 包（D-075）：发布包里
                            // FilmSelfTestActivity 自己也会 finish()，这一行干脆不出现。
                            onRunSelfTest = if (BuildConfig.DEBUG) {
                                {
                                    runCatching {
                                        context.startActivity(
                                            Intent(context, FilmSelfTestActivity::class.java),
                                        )
                                    }.onFailure { toast(context, "无法启动自测") }
                                }
                            } else {
                                null
                            },
                        )

                        AppDestination.DETAIL -> PhotoDetailScreen(
                            status = detailStatus,
                            photo = detailPhoto,
                            stockLabel = capture.stockLabelOrNull(),
                            failureMessage = (capture as? CaptureState.Failed)?.message,
                            isFavorite = detailPhoto != null && detailPhoto.id in favoriteIds,
                            galleryPublished = detailPublished,
                            onBack = closeDetail,
                            // 重试只对"刚拍失败"有意义：历史照片早就冲洗完了。
                            onRetry = viewModel::retry,
                            onToggleFavorite = {
                                detailPhoto?.let { favoriteIds = favorites.toggle(it.id) }
                            },
                            onShare = { detailPhoto?.let(sharePhoto) },
                            onOpenInGallery = {
                                val target = detailPhoto
                                if (target != null) {
                                    scope.launch {
                                        val uri = withContext(Dispatchers.IO) {
                                            photoStore.galleryUriOf(target.id)
                                        }
                                        if (uri == null) {
                                            toast(context, "系统相册里找不到这张了")
                                        } else {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW)
                                                        .setDataAndType(uri, "image/jpeg")
                                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                                )
                                            }.onFailure { toast(context, "没有能打开图片的应用") }
                                        }
                                    }
                                }
                            },
                        )

                        AppDestination.CAMERA -> Unit
                    }
                }
            }

            // ---- 最上层：快门闪白 ----
            // 必须画在整棵树的最外面：放进取景页会被详情页盖住（实测整帧均值只
            // 195→206，而在最外层是 243）。指针事件不经过它，所以不影响任何交互。
            ShutterFlash(trigger = shutterFlash)

            BackHandler(enabled = destination != AppDestination.CAMERA) {
                if (destination == AppDestination.DETAIL) {
                    closeDetail()
                } else {
                    viewModel.onEvent(AppEvent.NavigateBack)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 基础件
// ---------------------------------------------------------------------------

/**
 * 空白点击拦截层。
 *
 * 详情页期间取景页还挂在下面（理由见 [PocketCcdRoute]）。纸面层若只是"画了个不透明
 * 背景"，它在 Compose 里并不是触摸目标，点击会**穿过去**打在下面的取景层上，
 * 表现为"点详情页的空白处，取景器对焦了"。
 *
 * 这里用 `detectTapGestures` 而不是空 `clickable`：后者会给整页挂一个没有名字的可点节点，
 * TalkBack 会把"当前页面空白处"念成一个不知名的按钮；`detectTapGestures` 只吃掉手势，
 * 不产生任何无障碍节点。子元素（按钮、列表）命中优先级更高，不受影响。
 */
@Composable
private fun Modifier.swallowTaps(): Modifier =
    pointerInput(Unit) { detectTapGestures { } }

/**
 * 快门闪白。
 *
 * 白屏从 0.82 透明度在 320ms 内淡到 0 —— 它是"按下去了"的即时反馈，不是转场动画。
 * 用 [Animatable] + `graphicsLayer` 而不是在组合里读 `alpha`：后者每帧都会触发一次
 * 重组，闪一次白光要重组 20 次。
 */
@Composable
private fun ShutterFlash(trigger: Int) {
    if (trigger == 0) return
    val flash = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        flash.snapTo(0.82f)
        flash.animateTo(0f, tween(320))
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = flash.value }
            .background(Color.White),
    )
}

// ---------------------------------------------------------------------------
// 映射与工具
// ---------------------------------------------------------------------------

/** 冲洗中/已完成时这一张属于哪支卷。失败时也从 [CaptureState.Failed] 里取。 */
private fun CaptureState.stockLabelOrNull(): String? = when (this) {
    is CaptureState.Capturing -> stock.label
    is CaptureState.Developing -> stock.label
    is CaptureState.Saving -> stock.label
    is CaptureState.Failed -> stock?.label
    CaptureState.Idle, is CaptureState.Done -> null
}

private fun AppDestination.toTab(): GlassTab = when (this) {
    AppDestination.GALLERY -> GlassTab.Gallery
    AppDestination.FILMS -> GlassTab.Films
    AppDestination.DISCOVER -> GlassTab.Discover
    AppDestination.SETTINGS -> GlassTab.Settings
    // CAMERA 时纸面层根本不存在；DETAIL 不画 Tab。这两个分支不会被执行，
    // 但写出来能让编译器替我们守住"新增目的地必须在这里表态"。
    AppDestination.CAMERA, AppDestination.DETAIL -> GlassTab.Camera
}

private fun CaptureState.toDetailStatus(): DetailStatus = when (this) {
    // Idle 是"还没拍"，但详情页不可能在 Idle 时打开；真到了也按已完成处理，
    // 比显示一个假的"显影中"更诚实。
    CaptureState.Idle -> DetailStatus.Saved
    is CaptureState.Capturing, is CaptureState.Developing -> DetailStatus.Processing
    is CaptureState.Saving -> DetailStatus.Writing
    is CaptureState.Done -> DetailStatus.Saved
    is CaptureState.Failed -> DetailStatus.Failed
}

private fun SavedPhoto.toWallItem(): PhotoWallItem = PhotoWallItem(
    id = id,
    stockLabel = stockLabel,
    capturedAt = capturedAt,
    imagePath = thumbnailFile.absolutePath,
    // 网格只用缩略图；全尺寸路径留给查看器，别在列表里解 12MP。
    fullImagePath = fullFile.absolutePath,
)

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
/** 从 Compose 的 Context 往上找 Activity（orientation 只能设在它身上）。 */
private tailrec fun Context.findActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}
