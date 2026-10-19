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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketccd.prototype.camera.CameraHandle
import com.pocketccd.prototype.camera.CameraPreview
import com.pocketccd.prototype.camera.CameraUiState
import com.pocketccd.prototype.camera.CaptureState
import com.pocketccd.prototype.camera.SavedPhoto
import com.pocketccd.prototype.navigation.AppDestination
import com.pocketccd.prototype.navigation.AppEvent
import com.pocketccd.prototype.navigation.AppUiState
import com.pocketccd.prototype.navigation.AppViewModel
import com.pocketccd.prototype.ui.gallery.PhotoWallItem
import com.pocketccd.prototype.ui.gallery.PhotoWallScreen
import com.pocketccd.prototype.ui.result.ResultScreen
import com.pocketccd.prototype.ui.result.ResultStatus
import com.pocketccd.prototype.ui.settings.SettingsScreen
import com.pocketccd.prototype.ui.stage.CameraStageScreen
import com.pocketccd.prototype.ui.stage.CameraViewfinder
import com.pocketccd.prototype.ui.theme.PocketCcdColors
import com.pocketccd.prototype.ui.theme.PocketCcdTheme

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

    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var handle by remember { mutableStateOf<CameraHandle?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) viewModel.onCameraError("没有相机权限")
    }

    val destination = state.destination
    val inCamera = destination == AppDestination.CAMERA
    // 成片页也要保持相机绑定：快门一按下就导航，而 takePicture 是异步的，
    // 此时若销毁 PreviewView 会连带 unbindAll，把刚发起的采集掐断。
    // 相机页在 RESULT 期间继续挂着（被成片页完全遮住），返回时也省一次重绑。
    val stageMounted = destination == AppDestination.HOME ||
        destination == AppDestination.CAMERA ||
        destination == AppDestination.RESULT
    val cameraBound = (destination == AppDestination.CAMERA ||
        destination == AppDestination.RESULT) && hasPermission

    // 进拍摄页才要权限：首页不该弹系统对话框。
    // 每次都重新查一次权限：用户可能刚去系统设置里打开再切回来，
    // remember 的初值不会自动跟着变。
    LaunchedEffect(inCamera) {
        if (inCamera) {
            hasPermission = context.hasCameraPermission()
            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // 收起拍摄界面就解绑，不在后台占着摄像头（会亮指示灯、也会耗电）。
    LaunchedEffect(cameraBound) {
        if (!cameraBound) {
            handle = null
            viewModel.onCameraReleased()
        }
    }

    PocketCcdApp(
        state = state,
        onEvent = viewModel::onEvent,
        cameraUi = cameraUi,
        capture = capture,
        roll = roll,
        stageMounted = stageMounted,
        cameraLive = handle != null,
        previewError = previewError,
        cameraViewfinder = if (cameraBound) {
            { modifier ->
                val stock = cameraUi.activeStock
                CameraPreview(
                    lensFacing = androidx.camera.core.CameraSelector.LENS_FACING_BACK,
                    targetSize = stock?.let { Size(it.width, it.height) },
                    modifier = modifier,
                    onReady = { bound ->
                        handle = bound
                        if (bound != null) {
                            previewError = null
                            viewModel.onCameraBound(bound)
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
            if (bound != null && cameraUi.canShoot) {
                // 顺序不能反：capture() 会同步把状态置为 Capturing，
                // 先调它再导航，成片页第一帧就是"正在冲洗"，不会闪一下上一张。
                viewModel.capture(bound::takeJpeg)
                viewModel.onEvent(AppEvent.OpenResult)
            }
        },
        onFlashChange = { on -> handle?.setFlash(on) },
        onCycleStock = viewModel::nextStock,
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
    stageMounted: Boolean = true,
    cameraLive: Boolean = false,
    previewError: String? = null,
    cameraViewfinder: (@Composable (Modifier) -> Unit)? = null,
    onShutter: () -> Unit = {},
    onFlashChange: (Boolean) -> Unit = {},
    onCycleStock: () -> Unit = {},
    onRetry: () -> Unit = {},
    onRefreshStocks: () -> Unit = {},
    onRefreshRoll: () -> Unit = {},
) {
    val view = LocalView.current
    val isRedPage = state.destination == AppDestination.HOME ||
        state.destination == AppDestination.CAMERA
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(activity.window, view)
        // Red pages use light system icons; cream/mint pages use dark icons.
        controller.isAppearanceLightStatusBars = !isRedPage
        controller.isAppearanceLightNavigationBars = !isRedPage
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isRedPage) {
            PocketCcdColors.RetroRed
        } else {
            PocketCcdColors.Background
        },
    ) {
        BackHandler(enabled = state.destination != AppDestination.HOME) {
            onEvent(AppEvent.NavigateBack)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            if (stageMounted) {
                // One persistent body for home + shooting: the pull gesture
                // drives the same frame, shutter, flash and lens/viewfinder
                // instead of cross-fading two page layers.
                //
                // 成片页期间它继续挂载（被 ResultScreen 完全遮住），这样刚按下的
                // 快门不会被 PreviewView 的销毁连带 unbind 掉。
                CameraStageScreen(
                    expanded = state.destination != AppDestination.HOME,
                    onExpand = { onEvent(AppEvent.OpenCamera) },
                    onCollapse = { onEvent(AppEvent.NavigateBack) },
                    onOpenGallery = { onEvent(AppEvent.OpenGallery) },
                    onOpenSettings = { onEvent(AppEvent.OpenSettings) },
                    onShutter = onShutter,
                    stockLabel = cameraUi.activeStock?.label,
                    cameraLive = cameraLive && previewError == null,
                    canShoot = cameraUi.canShoot,
                    blockReason = previewError ?: cameraUi.blockReason,
                    onCycleStock = onCycleStock,
                    onFlashChange = onFlashChange,
                    viewfinder = cameraViewfinder ?: { CameraViewfinder(modifier = it) },
                )
            }

            if (state.destination != AppDestination.HOME &&
                state.destination != AppDestination.CAMERA
            ) {
                AnimatedContent(
                    targetState = state.destination,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(160))
                    },
                    label = "app-overlay-destination",
                ) { destination ->
                    when (destination) {
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
                            // 没有真实成片时保留 DEMO 标记，避免把测试帧冒充成照片。
                            demo = capture !is CaptureState.Done,
                            photo = (capture as? CaptureState.Done)?.photo,
                            failureMessage = (capture as? CaptureState.Failed)?.message,
                            // 优先用这张照片自己的卷名：用户可能在冲洗期间切了卷，
                            // 那时 activeStock 已经不是拍它时用的那卷了。
                            stockLabel = (capture as? CaptureState.Done)?.photo?.stockLabel
                                ?: cameraUi.activeStock?.label,
                        )

                        AppDestination.SETTINGS -> SettingsScreen(
                            onBack = { onEvent(AppEvent.NavigateBack) },
                            stockCount = cameraUi.stocks.size,
                            onRefreshStocks = onRefreshStocks,
                        )

                        AppDestination.HOME,
                        AppDestination.CAMERA,
                        -> Unit
                    }
                }
            }
        }
    }
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
    filter = stockLabel,
    dateLabel = formatDate(capturedAt),
    colors = listOf(PocketCcdColors.BackgroundElevated, PocketCcdColors.Outline),
    imagePath = thumbnailFile.absolutePath,
)

private fun formatDate(epochMillis: Long): String {
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    val month = calendar.get(java.util.Calendar.MONTH) + 1
    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
    return "%02d.%02d".format(month, day)
}

@Preview(showBackground = true, backgroundColor = 0xFFD9382E)
@Composable
private fun HomePreview() {
    PocketCcdTheme {
        PocketCcdApp(state = AppUiState(), onEvent = {})
    }
}
