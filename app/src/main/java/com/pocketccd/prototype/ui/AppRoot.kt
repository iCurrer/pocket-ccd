package com.pocketccd.prototype.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketccd.prototype.navigation.AppDestination
import com.pocketccd.prototype.navigation.AppEvent
import com.pocketccd.prototype.navigation.AppUiState
import com.pocketccd.prototype.navigation.AppViewModel
import com.pocketccd.prototype.ui.gallery.PhotoWallScreen
import com.pocketccd.prototype.ui.result.ResultScreen
import com.pocketccd.prototype.ui.result.ResultStatus
import com.pocketccd.prototype.ui.settings.SettingsScreen
import com.pocketccd.prototype.ui.stage.CameraStageScreen
import com.pocketccd.prototype.ui.theme.PocketCcdColors
import com.pocketccd.prototype.ui.theme.PocketCcdTheme

@Composable
fun PocketCcdRoute(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PocketCcdApp(state = state, onEvent = viewModel::onEvent)
}

@Composable
fun PocketCcdApp(
    state: AppUiState,
    onEvent: (AppEvent) -> Unit,
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
            if (state.destination == AppDestination.HOME ||
                state.destination == AppDestination.CAMERA
            ) {
                // One persistent body for home + shooting: the pull gesture
                // drives the same frame, shutter, flash and lens/viewfinder
                // instead of cross-fading two page layers.
                CameraStageScreen(
                    expanded = state.destination == AppDestination.CAMERA,
                    onExpand = { onEvent(AppEvent.OpenCamera) },
                    onCollapse = { onEvent(AppEvent.NavigateBack) },
                    onOpenGallery = { onEvent(AppEvent.OpenGallery) },
                    onOpenSettings = { onEvent(AppEvent.OpenSettings) },
                    onShutter = { onEvent(AppEvent.OpenResult) },
                )
            } else {
                AnimatedContent(
                    targetState = state.destination,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(160))
                    },
                    label = "app-overlay-destination",
                ) { destination ->
                    when (destination) {
                        AppDestination.GALLERY -> PhotoWallScreen(
                            onBack = { onEvent(AppEvent.NavigateBack) },
                            // Runtime has no captures yet; only app-owned records
                            // will be supplied here once the repository exists.
                            photos = emptyList(),
                        )

                        AppDestination.RESULT -> ResultScreen(
                            status = ResultStatus.SAVED,
                            onBackToCamera = { onEvent(AppEvent.OpenCamera) },
                            onOpenPhotoWall = { onEvent(AppEvent.OpenGallery) },
                            onRetry = { /* T1-012 wires real retry states */ },
                            demo = true,
                        )

                        AppDestination.SETTINGS -> SettingsScreen(
                            onBack = { onEvent(AppEvent.NavigateBack) },
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

@Preview(showBackground = true, backgroundColor = 0xFFD9382E)
@Composable
private fun HomePreview() {
    PocketCcdTheme {
        PocketCcdApp(state = AppUiState(), onEvent = {})
    }
}
