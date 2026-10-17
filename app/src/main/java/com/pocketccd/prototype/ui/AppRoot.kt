package com.pocketccd.prototype.ui

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketccd.prototype.R
import com.pocketccd.prototype.navigation.AppDestination
import com.pocketccd.prototype.navigation.AppEvent
import com.pocketccd.prototype.navigation.AppUiState
import com.pocketccd.prototype.navigation.AppViewModel
import com.pocketccd.prototype.ui.camera.CameraScreen
import com.pocketccd.prototype.ui.gallery.PhotoWallScreen
import com.pocketccd.prototype.ui.home.HomeScreen
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
    SharedTransitionLayout {
        val sharedScope = this
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
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
                    )
                    when (destination) {
                        AppDestination.HOME -> HomeScreen(
                            onOpenCamera = { onEvent(AppEvent.OpenCamera) },
                            onOpenGallery = { onEvent(AppEvent.OpenGallery) },
                            onOpenSettings = { onEvent(AppEvent.OpenSettings) },
                            cameraBodyModifier = cameraBodyModifier,
                        )

                        AppDestination.CAMERA -> CameraScreen(
                            onCollapse = { onEvent(AppEvent.NavigateBack) },
                            onShutter = { onEvent(AppEvent.OpenResult) },
                            panelModifier = cameraBodyModifier,
                        )

                        AppDestination.GALLERY -> PhotoWallScreen(
                            onBack = { onEvent(AppEvent.NavigateBack) },
                            // Runtime has no captures yet; only app-owned records
                            // will be supplied here once the repository exists.
                            photos = emptyList(),
                        )

                        AppDestination.RESULT -> ResultPlaceholder(
                            onBackToCamera = { onEvent(AppEvent.OpenCamera) },
                            onOpenGallery = { onEvent(AppEvent.OpenGallery) },
                            onBack = { onEvent(AppEvent.NavigateBack) },
                        )

                        AppDestination.SETTINGS -> SettingsPlaceholder(
                            onBack = { onEvent(AppEvent.NavigateBack) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultPlaceholder(
    onBackToCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onBack: () -> Unit,
) {
    PlaceholderScreen(
        title = stringResource(R.string.result_title),
        body = stringResource(R.string.result_placeholder),
    ) {
        Button(onClick = onBackToCamera) {
            Text(stringResource(R.string.result_back_to_camera))
        }
        OutlinedButton(onClick = onOpenGallery) {
            Text(stringResource(R.string.action_gallery))
        }
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.action_back))
        }
    }
}

@Composable
private fun SettingsPlaceholder(onBack: () -> Unit) {
    PlaceholderScreen(
        title = stringResource(R.string.settings_title),
        body = stringResource(R.string.settings_placeholder),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.action_back))
        }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    body: String,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        actions()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun HomePlaceholderPreview() {
    PocketCcdTheme {
        PocketCcdApp(state = AppUiState(), onEvent = {})
    }
}
