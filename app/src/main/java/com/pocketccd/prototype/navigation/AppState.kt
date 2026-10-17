package com.pocketccd.prototype.navigation

/** Top-level destinations. Photo-processing states are intentionally not modeled here. */
enum class AppDestination {
    HOME,
    CAMERA,
    GALLERY,
    RESULT,
    SETTINGS,
}

sealed interface AppEvent {
    data object OpenCamera : AppEvent
    data object OpenGallery : AppEvent
    data object OpenSettings : AppEvent
    data object OpenResult : AppEvent
    data object NavigateBack : AppEvent
}

/**
 * UI navigation state. `galleryOrigin` and `resultOrigin` keep the "back" target
 * explicit instead of relying on an animation or a hidden back stack.
 */
data class AppUiState(
    val destination: AppDestination = AppDestination.HOME,
    val galleryOrigin: AppDestination = AppDestination.HOME,
    val resultOrigin: AppDestination = AppDestination.CAMERA,
)

fun reduce(state: AppUiState, event: AppEvent): AppUiState = when (event) {
    AppEvent.OpenCamera -> when (state.destination) {
        AppDestination.HOME, AppDestination.RESULT -> state.copy(destination = AppDestination.CAMERA)
        else -> state
    }

    AppEvent.OpenGallery -> when (state.destination) {
        AppDestination.GALLERY -> state
        AppDestination.HOME, AppDestination.CAMERA, AppDestination.RESULT -> state.copy(
            destination = AppDestination.GALLERY,
            galleryOrigin = state.destination,
        )
        AppDestination.SETTINGS -> state
    }

    AppEvent.OpenSettings -> when (state.destination) {
        AppDestination.HOME -> state.copy(destination = AppDestination.SETTINGS)
        else -> state
    }

    AppEvent.OpenResult -> when (state.destination) {
        AppDestination.CAMERA, AppDestination.GALLERY -> state.copy(
            destination = AppDestination.RESULT,
            resultOrigin = state.destination,
        )
        else -> state
    }

    AppEvent.NavigateBack -> when (state.destination) {
        AppDestination.HOME -> state
        AppDestination.CAMERA, AppDestination.SETTINGS -> state.copy(destination = AppDestination.HOME)
        AppDestination.GALLERY -> state.copy(destination = state.galleryOrigin)
        AppDestination.RESULT -> state.copy(destination = state.resultOrigin)
    }
}
