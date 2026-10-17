package com.pocketccd.prototype.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
            label = "app-destination",
        ) { destination ->
            when (destination) {
                AppDestination.HOME -> HomePlaceholder(
                    onOpenCamera = { onEvent(AppEvent.OpenCamera) },
                    onOpenGallery = { onEvent(AppEvent.OpenGallery) },
                    onOpenSettings = { onEvent(AppEvent.OpenSettings) },
                )

                AppDestination.CAMERA -> CameraPlaceholder(
                    onFakeCapture = { onEvent(AppEvent.OpenResult) },
                    onOpenGallery = { onEvent(AppEvent.OpenGallery) },
                    onBack = { onEvent(AppEvent.NavigateBack) },
                )

                AppDestination.GALLERY -> GalleryPlaceholder(
                    onOpenSample = { onEvent(AppEvent.OpenResult) },
                    onBack = { onEvent(AppEvent.NavigateBack) },
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

@Composable
private fun HomePlaceholder(
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    PlaceholderScreen(
        title = stringResource(R.string.app_name),
        body = stringResource(R.string.home_subtitle),
    ) {
        Button(onClick = onOpenCamera) {
            Text(stringResource(R.string.home_open_camera))
        }
        OutlinedButton(onClick = onOpenGallery) {
            Text(stringResource(R.string.action_gallery))
        }
        TextButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.action_settings))
        }
    }
}

@Composable
private fun CameraPlaceholder(
    onFakeCapture: () -> Unit,
    onOpenGallery: () -> Unit,
    onBack: () -> Unit,
) {
    PlaceholderScreen(
        title = stringResource(R.string.camera_title),
        body = stringResource(R.string.camera_placeholder),
    ) {
        Button(onClick = onFakeCapture) {
            Text(stringResource(R.string.camera_fake_capture))
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
private fun GalleryPlaceholder(
    onOpenSample: () -> Unit,
    onBack: () -> Unit,
) {
    PlaceholderScreen(
        title = stringResource(R.string.gallery_title),
        body = stringResource(R.string.gallery_empty),
    ) {
        Button(onClick = onOpenSample) {
            Text(stringResource(R.string.gallery_open_sample))
        }
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.action_back))
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
