package com.pocketccd.prototype.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketccd.prototype.navigation.AppDestination
import com.pocketccd.prototype.navigation.AppEvent
import com.pocketccd.prototype.navigation.AppUiState
import com.pocketccd.prototype.navigation.AppViewModel
import com.pocketccd.prototype.ui.camera.CameraScreen
import com.pocketccd.prototype.ui.gallery.PhotoWallScreen
import com.pocketccd.prototype.ui.home.HomeScreen
import com.pocketccd.prototype.ui.result.ResultScreen
import com.pocketccd.prototype.ui.result.ResultStatus
import com.pocketccd.prototype.ui.settings.SettingsScreen
import com.pocketccd.prototype.ui.theme.PocketCcdColors
import com.pocketccd.prototype.ui.theme.PocketCcdTheme

@Composable
fun PocketCcdRoute(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PocketCcdApp(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalSharedTransitionApi::class)
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
    SharedTransitionLayout {
        val sharedScope = this
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
            AnimatedContent(
                targetState = state.destination,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                // The camera body itself is a shared element; the surrounding
                // chrome only needs a quiet crossfade.
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                },
                label = "app-destination",
            ) { destination ->
                val animatedScope = this
                with(sharedScope) {
                    val cameraBodyModifier = Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "camera-body"),
                        animatedVisibilityScope = animatedScope,
                        enter = EnterTransition.None,
                        exit = ExitTransition.None,
                        boundsTransform = { _, _ ->
                            tween(
                                durationMillis = 320,
                                easing = FastOutSlowInEasing,
                            )
                        },
                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                        // The camera body is a rounded red slab. Clip the overlay
                        // during the morph so the full-screen page cannot flash
                        // square corners while the bounds are animating.
                        clipInOverlayDuringTransition = OverlayClip(
                            RoundedCornerShape(36.dp),
                        ),
                        zIndexInOverlay = 1f,
                    )
                    when (destination) {
                        AppDestination.HOME -> HomeScreen(
                            onOpenCamera = { onEvent(AppEvent.OpenCamera) },
                            onOpenGallery = { onEvent(AppEvent.OpenGallery) },
                            onOpenSettings = { onEvent(AppEvent.OpenSettings) },
                            cameraBodyModifier = cameraBodyModifier,
                        )

                        AppDestination.CAMERA -> CameraScreen(
                            onShutter = { onEvent(AppEvent.OpenResult) },
                            panelModifier = cameraBodyModifier,
                        )

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
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4EFE6)
@Composable
private fun HomePreview() {
    PocketCcdTheme {
        PocketCcdApp(state = AppUiState(), onEvent = {})
    }
}
