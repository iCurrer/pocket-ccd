package com.pocketccd.prototype.navigation

/**
 * Top-level destinations.
 *
 * `HOME` was removed on 2026-10-19 (D-039): the app now opens straight into the
 * viewfinder, the way a film camera does. There is no intermediate "camera
 * body" page any more, so `CAMERA` is the root — and back from it belongs to
 * the system, not to a state transition.
 */
enum class AppDestination {
    CAMERA,
    GALLERY,
    RESULT,
    SETTINGS,

    /**
     * 胶卷库（换卷页，T9-016）。设备上实际有几卷取决于推了几个 pack，
     * 所以这页的数量永远是运行时读出来的，不写死。
     */
    FILMS,
}

sealed interface AppEvent {
    /** Result screen → back to shooting. Also used after a capture. */
    data object OpenCamera : AppEvent

    data object OpenGallery : AppEvent
    data object OpenSettings : AppEvent
    data object OpenResult : AppEvent
    data object OpenFilms : AppEvent
    data object NavigateBack : AppEvent
}

/**
 * UI navigation state. `galleryOrigin` and `resultOrigin` keep the "back" target
 * explicit instead of relying on an animation or a hidden back stack.
 */
data class AppUiState(
    val destination: AppDestination = AppDestination.CAMERA,
    val galleryOrigin: AppDestination = AppDestination.CAMERA,
    val resultOrigin: AppDestination = AppDestination.CAMERA,
)

fun reduce(state: AppUiState, event: AppEvent): AppUiState = when (event) {
    AppEvent.OpenCamera -> when (state.destination) {
        // Already shooting: ignore. Only RESULT has somewhere to come back from.
        AppDestination.RESULT -> state.copy(destination = AppDestination.CAMERA)
        else -> state
    }

    AppEvent.OpenGallery -> when (state.destination) {
        AppDestination.GALLERY -> state
        AppDestination.CAMERA, AppDestination.RESULT, AppDestination.FILMS -> state.copy(
            destination = AppDestination.GALLERY,
            galleryOrigin = state.destination,
        )
        AppDestination.SETTINGS -> state
    }

    AppEvent.OpenSettings -> when (state.destination) {
        // Settings is reachable from the viewfinder's top bar, not from home.
        AppDestination.CAMERA -> state.copy(destination = AppDestination.SETTINGS)
        else -> state
    }

    AppEvent.OpenResult -> when (state.destination) {
        AppDestination.CAMERA, AppDestination.GALLERY -> state.copy(
            destination = AppDestination.RESULT,
            resultOrigin = state.destination,
        )
        else -> state
    }

    // 只从取景页进：换卷是"拍之前"的动作，从相册或成片页进去没有意义。
    AppEvent.OpenFilms -> when (state.destination) {
        AppDestination.CAMERA -> state.copy(destination = AppDestination.FILMS)
        else -> state
    }

    AppEvent.NavigateBack -> when (state.destination) {
        // CAMERA is the root: [BackHandler] stays disabled so the system can
        // finish the activity instead of looping inside the app.
        AppDestination.CAMERA -> state
        AppDestination.SETTINGS -> state.copy(destination = AppDestination.CAMERA)
        AppDestination.GALLERY -> state.copy(destination = state.galleryOrigin)
        AppDestination.RESULT -> state.copy(destination = state.resultOrigin)
        // 装卷/不装卷都回取景器：换完卷就该继续拍。
        AppDestination.FILMS -> state.copy(destination = AppDestination.CAMERA)
    }
}
