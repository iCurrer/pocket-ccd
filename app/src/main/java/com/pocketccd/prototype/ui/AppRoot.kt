package com.pocketccd.prototype.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketccd.prototype.camera.CameraHandle
import com.pocketccd.prototype.camera.CameraPreview
import com.pocketccd.prototype.camera.CameraUiState
import com.pocketccd.prototype.camera.CaptureState
import com.pocketccd.prototype.camera.LensStop
import com.pocketccd.prototype.camera.SavedPhoto
import com.pocketccd.prototype.navigation.AppDestination
import com.pocketccd.prototype.navigation.AppEvent
import com.pocketccd.prototype.navigation.AppUiState
import com.pocketccd.prototype.navigation.AppViewModel
import com.pocketccd.prototype.ui.film.FilmCaptureScreen
import com.pocketccd.prototype.ui.film.FilmDarkSpec
import com.pocketccd.prototype.ui.film.FilmTheme
import com.pocketccd.prototype.ui.film.ShutterFlash
import com.pocketccd.prototype.ui.film.films.FilmLibraryScreen
import com.pocketccd.prototype.ui.film.films.StockUsage
import com.pocketccd.prototype.ui.gallery.PhotoWallItem
import com.pocketccd.prototype.ui.gallery.PhotoWallScreen
import com.pocketccd.prototype.ui.result.ResultScreen
import com.pocketccd.prototype.ui.result.ResultStatus
import com.pocketccd.prototype.ui.settings.SettingsScreen

/**
 * 把 ViewModel 接到 UI 上，并持有相机句柄。
 *
 * 句柄刻意留在这一层而不是 ViewModel 里：它绑定 `LifecycleOwner`，
 * 一旦页面销毁就必须作废，放进 ViewModel 会让它活过 Activity。
 */
@Composable
fun PocketCcdRoute(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val capture by viewModel.capture.collectAsStateWithLifecycle()
    val cameraUi by viewModel.cameraUi.collectAsStateWithLifecycle()
    val roll by viewModel.roll.collectAsStateWithLifecycle()
    val rollShots by viewModel.rollShots.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var handle by remember { mutableStateOf<CameraHandle?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    // 变焦比留在这里而不是取景页：相机重绑（去相册再回来）会把硬件变焦重置为 1x，
    // 界面若自己记着 2x，就会出现"按钮亮着但画面是 1x"。
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    // 自增即强制重绑相机。会话被系统/其他 App 抢走后，这是唯一能恢复的手段。
    var cameraNonce by remember { mutableIntStateOf(0) }
    // 该机的真实光学档位（D-044）。空列表 = 探不到，取景页会退回数码档。
    var lensStops by remember { mutableStateOf<List<LensStop>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) viewModel.onCameraError("需要相机权限才能取景")
    }

    val destination = state.destination
    // 成片页也要保持相机绑定：快门一按下就导航，而 takePicture 是异步的，
    // 此时若销毁 PreviewView 会连带 unbindAll，把刚发起的采集掐断。
    // 相机页在 RESULT 期间继续挂着（被成片页完全遮住），返回时也省一次重绑。
    val captureVisible = destination == AppDestination.CAMERA ||
        destination == AppDestination.RESULT
    val cameraBound = captureVisible && hasPermission

    // D-039 废除首页后，App 一启动就是取景器，权限也只能在这时申请。
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

    // 回到前台时验一次会话：相机可能已被系统或其他 App 拿走。
    // 不探这一次，界面会一直亮着 LIVE，按快门只会得到 "Not bound to a valid Camera"。
    //
    // 顺便和系统相册对一次账：用户最典型的路径就是「切去相册删几张，再切回来」，
    // 不在这里收，照片墙会一直显示已经删掉的照片。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reconcileGallery()
                val bound = handle
                if (bound != null && !bound.isAttached()) {
                    previewError = "相机连接已断开，正在重连"
                    cameraNonce++
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 进照片墙之前再对一次：从相册切回来但没经过 ON_RESUME 的路径（比如分屏、
    // 从成片页返回）也能拿到最新结果。
    LaunchedEffect(destination) {
        if (destination == AppDestination.GALLERY) viewModel.reconcileGallery()
    }

    PocketCcdApp(
        state = state,
        onEvent = viewModel::onEvent,
        cameraUi = cameraUi,
        capture = capture,
        roll = roll,
        rollShots = rollShots,
        cameraLive = handle != null,
        previewError = previewError,
        permissionGranted = hasPermission,
        cameraViewfinder = if (cameraBound) {
            { modifier ->
                CameraPreview(
                    lensFacing = androidx.camera.core.CameraSelector.LENS_FACING_BACK,
                    // 用 programmedSize 而不是 activeStock 的尺寸：未验证的尺寸会把
                    // CameraX 锁死在"最接近的更高档"上（4000×3000 → 4096×3072）。
                    targetSize = cameraUi.programmedSize,
                    modifier = modifier,
                    rebindNonce = cameraNonce,
                    onReady = { bound ->
                        handle = bound
                        if (bound != null) {
                            previewError = null
                            viewModel.onCameraBound(bound)
                            lensStops = bound.lensStops
                            // 新会话的变焦是 1x，把界面记住的那一档补回去。
                            if (zoomRatio > 1f) bound.requestZoom(zoomRatio)
                        }
                    },
                    onError = { message ->
                        previewError = message
                        viewModel.onCameraError(message)
                    },
                )
            }
        } else {
            null
        },
        onShutter = {
            val bound = handle
            if (bound != null && !bound.isAttached()) {
                // 与其让用户按下去、等 9 秒拿到一张失败页，不如立刻重连并说明原因。
                previewError = "相机连接已断开，正在重连"
                cameraNonce++
            } else if (bound != null && cameraUi.canShoot) {
                // 顺序不能反：capture() 会同步把状态置为 Capturing，
                // 先调它再导航，成片页第一帧就是"正在冲洗"，不会闪一下上一张。
                viewModel.capture(bound::takeJpeg)
                viewModel.onEvent(AppEvent.OpenResult)
            }
        },
        onFlashChange = { on -> handle?.setFlash(on) },
        onTapToFocus = { x, y, onResult ->
            val bound = handle
            if (bound == null) onResult(false) else bound.focusAt(x, y, onResult)
        },
        onZoomChange = { requested ->
            val bound = handle
            // 以相机返回的实际倍率为准：请求 2x 但机型只支持 1x 时，
            // 界面就停在 1x，不会出现"按钮亮着画面没变"。
            val applied = bound?.requestZoom(requested) ?: requested
            zoomRatio = applied
            applied
        },
        zoom = zoomRatio,
        lensStops = lensStops,
        onNewRoll = viewModel::newRoll,
        onSelectStock = viewModel::selectStock,
        onRetry = viewModel::retry,
        onRefreshStocks = viewModel::refreshStocks,
        onRefreshRoll = viewModel::refreshRoll,
    )
}

@Composable
fun PocketCcdApp(
    state: AppUiState,
    onEvent: (AppEvent) -> Unit,
    cameraUi: CameraUiState = CameraUiState(),
    capture: CaptureState = CaptureState.Idle,
    roll: List<SavedPhoto> = emptyList(),
    rollShots: Int = 0,
    cameraLive: Boolean = false,
    previewError: String? = null,
    permissionGranted: Boolean = true,
    cameraViewfinder: (@Composable (Modifier) -> Unit)? = null,
    onShutter: () -> Unit = {},
    onFlashChange: (Boolean) -> Unit = {},
    /** 装卷。胶卷库点某一卷时调用，装完由调用方负责回到取景页。 */
    onSelectStock: (String) -> Unit = {},
    onNewRoll: () -> Unit = {},
    onRetry: () -> Unit = {},
    onRefreshStocks: () -> Unit = {},
    onRefreshRoll: () -> Unit = {},
    /** 点按对焦：坐标是取景器 View 像素。 */
    onTapToFocus: (Float, Float, (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onZoomChange: (Float) -> Float = { it },
    zoom: Float = 1f,
    /** 该机真实光学档位（D-044）。空 = 探不到，退回数码档。 */
    lensStops: List<LensStop> = emptyList(),
) {
    val view = LocalView.current
    val destination = state.destination
    val shooting = destination == AppDestination.CAMERA
    val stock = cameraUi.activeStock

    // 胶卷库每行的画格用「该卷最新一张真实成片」。按 stockId 归组，
    // 没有成片的卷不会出现在这张表里 —— 库里退回画胶卷筒，而不是编一张示意图。
    val stockUsage = remember(roll) {
        roll.groupBy { it.stockId }.mapValues { (_, photos) ->
            val latest = photos.maxByOrNull { it.capturedAt }
            StockUsage(
                shots = photos.size,
                latestThumbnail = latest?.thumbnailFile?.takeIf { it.isFile },
                lastCapturedAt = latest?.capturedAt,
            )
        }
    }

    // Flash and grid live here, not inside the screen: they survive the
    // viewfinder being covered by the result page.
    var flashOn by remember { mutableStateOf(false) }
    var gridOn by remember { mutableStateOf(false) }
    // 快门闪白的触发器。放在这一层是因为它必须在**成片页之上**：
    // 按下快门会立刻导航，闪白若画在取景页内部会被成片页完全盖住
    // （真机实测整帧均值只从 195 变成 206，等于没闪）。
    var shutterFlash by remember { mutableIntStateOf(0) }

    // 四个页面全部改成近黑底（T9-018/019/020），系统栏图标固定走浅色。
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    FilmTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            // 四个页面共用同一个底色，AnimatedContent 淡入淡出时不会透出第二层颜色。
            color = FilmDarkSpec.palette.appBg,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            // CAMERA 是根：返回交给系统 finish，而不是在 App 内循环。
            BackHandler(enabled = !shooting) {
                onEvent(AppEvent.NavigateBack)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                if (destination == AppDestination.CAMERA || destination == AppDestination.RESULT) {
                    // 成片页期间它继续挂载（被 ResultScreen 完全遮住），这样刚按下的
                    // 快门不会被 PreviewView 的销毁连带 unbind 掉。
                    val rollFull = rollShots >= ROLL_CAPACITY
                    FilmCaptureScreen(
                        stockName = stock?.label ?: "无胶卷",
                        stockMeta = stock?.let { "${it.label} · ${it.sizeLabel}" } ?: "未找到胶卷",
                        shotsTaken = rollShots,
                        rollCapacity = ROLL_CAPACITY,
                        libraryCount = roll.size,
                        megapixels = megapixelLabel(cameraUi.programmedSize),
                        flashOn = flashOn,
                        gridOn = gridOn,
                        // 一卷拍满就真的停：容量若只是装饰，计数器就成了摆设（D-002）。
                        canShoot = cameraUi.canShoot && previewError == null && !rollFull,
                        blockReason = when {
                            rollFull -> "本卷已拍满 · 点「换卷」继续"
                            else -> previewError ?: cameraUi.blockReason
                        },
                        onShutter = {
                            // 先触发闪白，再走真实快门。两者同一帧发起，
                            // 闪白 90ms 正好盖住成片页淡入的前半段。
                            shutterFlash++
                            onShutter()
                        },
                        onToggleFlash = {
                            flashOn = !flashOn
                            onFlashChange(flashOn)
                        },
                        onToggleGrid = { gridOn = !gridOn },
                        onOpenGallery = { onEvent(AppEvent.OpenGallery) },
                        onOpenFilms = { onEvent(AppEvent.OpenFilms) },
                        onNewRoll = onNewRoll,
                        onOpenSettings = { onEvent(AppEvent.OpenSettings) },
                        cameraLive = cameraLive,
                        permissionGranted = permissionGranted,
                        viewfinder = cameraViewfinder,
                        onTapToFocus = onTapToFocus,
                        onZoomChange = onZoomChange,
                        zoom = zoom,
                        lensStops = lensStops,
                    )
                }

                if (destination != AppDestination.CAMERA) {
                    AnimatedContent(
                        targetState = destination,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(160))
                        },
                        label = "app-overlay-destination",
                    ) { target ->
                        when (target) {
                            AppDestination.GALLERY -> {
                                // 进照片墙顺手重扫一次目录：可能刚保存过，也可能是
                                // 用户从电脑上补推了文件。
                                LaunchedEffect(Unit) { onRefreshRoll() }
                                PhotoWallScreen(
                                    onBack = { onEvent(AppEvent.NavigateBack) },
                                    photos = roll.map { it.toWallItem() },
                                )
                            }

                            AppDestination.RESULT -> ResultScreen(
                                status = capture.toResultStatus(),
                                onBackToCamera = { onEvent(AppEvent.OpenCamera) },
                                onOpenPhotoWall = { onEvent(AppEvent.OpenGallery) },
                                onRetry = onRetry,
                                // 只有"根本没有拍过"才算 DEMO。冲洗中/写入中也属于真实成片
                                // 流程，之前写成 `!is Done`，于是每张照片冲洗的那 3 秒里
                                // 都在屏幕上自称 DEMO · NOT SAVED。
                                demo = capture is CaptureState.Idle,
                                photo = (capture as? CaptureState.Done)?.photo,
                                failureMessage = (capture as? CaptureState.Failed)?.message,
                                // 优先用这张照片自己的卷名：用户可能在冲洗期间切了卷，
                                // 那时 activeStock 已经不是拍它时用的那卷了。
                                stockLabel = (capture as? CaptureState.Done)?.photo?.stockLabel
                                    ?: stock?.label,
                                // 本卷已拍到第几张。rollShots 按 rollStartedAt 过滤，
                                // 换卷即归零，所以它同时就是这张的片号。
                                frameIndex = rollShots,
                                rollCapacity = ROLL_CAPACITY,
                            )

                            AppDestination.SETTINGS -> SettingsScreen(
                                onBack = { onEvent(AppEvent.NavigateBack) },
                                stockCount = cameraUi.stocks.size,
                                onRefreshStocks = onRefreshStocks,
                            )

                            AppDestination.FILMS -> {
                                // 进库先重扫：用户可能刚用 adb 推了新卷进来。
                                LaunchedEffect(Unit) { onRefreshStocks() }
                                FilmLibraryScreen(
                                    stocks = cameraUi.stocks,
                                    selectedStockId = stock?.id,
                                    shotsTaken = rollShots,
                                    rollCapacity = ROLL_CAPACITY,
                                    onBack = { onEvent(AppEvent.NavigateBack) },
                                    onSelect = onSelectStock,
                                    onNewRoll = onNewRoll,
                                    onRefresh = onRefreshStocks,
                                    onOpenGallery = { onEvent(AppEvent.OpenGallery) },
                                    usage = stockUsage,
                                )
                            }

                            AppDestination.CAMERA -> Unit
                        }
                    }
                }
            }

            // 最上层：快门闪白盖住整屏（含状态栏、导航栏），成片页淡入时也在它下面。
            ShutterFlash(trigger = shutterFlash)
            }
        }
    }
}

/**
 * D-040: the design's `24 / 36 EXP` had no backing model. Making the roll a real
 * 36-frame unit turns the counter, the progress bar and "change roll" into honest
 * UI. Flagged `PROPOSED` until the user signs off on the product rule.
 */
private const val ROLL_CAPACITY = 36

/** 真实采集尺寸，而不是设计稿写死的 `12`。 */
private fun megapixelLabel(size: Size?): String {
    if (size == null) return "—"
    val mp = size.width.toLong() * size.height / 1_000_000.0
    return "%.1fMP".format(mp)
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun CaptureState.toResultStatus(): ResultStatus = when (this) {
    CaptureState.Idle -> ResultStatus.SAVED
    is CaptureState.Capturing, is CaptureState.Developing -> ResultStatus.PROCESSING
    is CaptureState.Saving -> ResultStatus.OUTPUT_WRITING
    is CaptureState.Done -> ResultStatus.SAVED
    is CaptureState.Failed -> ResultStatus.FAILED
}

private fun SavedPhoto.toWallItem(): PhotoWallItem = PhotoWallItem(
    id = id,
    stockLabel = stockLabel,
    capturedAt = capturedAt,
    imagePath = thumbnailFile.absolutePath,
)

@Preview(showBackground = true, backgroundColor = 0xFF0B0C0E)
@Composable
private fun CameraPreviewEntry() {
    PocketCcdApp(state = AppUiState(), onEvent = {})
}
