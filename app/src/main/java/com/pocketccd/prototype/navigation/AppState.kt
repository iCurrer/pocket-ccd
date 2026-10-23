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

    /**
     * 发现页（D-063 1:1 版面新增，B 方案）。
     *
     * 目前只展示真实统计（照片数 / 胶卷数 / 本卷进度）；内容由后续智能体接入，
     * 这里先保留独立目的地，避免底部 Tab 变成一个死按钮。
     */
    DISCOVER,
}

sealed interface AppEvent {
    /** Result screen → back to shooting. Also used after a capture. */
    data object OpenCamera : AppEvent

    data object OpenGallery : AppEvent
    data object OpenSettings : AppEvent
    data object OpenResult : AppEvent
    data object OpenFilms : AppEvent
    data object OpenDiscover : AppEvent
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

    // 底部 Tab 是平级切换：从任何纸面页都能去另外三个 Tab（相机页仍只从取景页进）。
    AppEvent.OpenGallery -> when (state.destination) {
        AppDestination.GALLERY -> state
        else -> state.copy(
            destination = AppDestination.GALLERY,
            galleryOrigin = state.destination,
        )
    }

    AppEvent.OpenSettings -> when (state.destination) {
        AppDestination.SETTINGS -> state
        else -> state.copy(destination = AppDestination.SETTINGS)
    }

    AppEvent.OpenResult -> when (state.destination) {
        AppDestination.CAMERA, AppDestination.GALLERY -> state.copy(
            destination = AppDestination.RESULT,
            resultOrigin = state.destination,
        )
        else -> state
    }

    AppEvent.OpenFilms -> when (state.destination) {
        AppDestination.FILMS -> state
        else -> state.copy(destination = AppDestination.FILMS)
    }

    AppEvent.OpenDiscover -> when (state.destination) {
        AppDestination.DISCOVER -> state
        else -> state.copy(destination = AppDestination.DISCOVER)
    }

    AppEvent.NavigateBack -> when (state.destination) {
        // CAMERA is the root: [BackHandler] stays disabled so the system can
        // finish the activity instead of looping inside the app.
        AppDestination.CAMERA -> state
        // D-064 修正：底部 Tab 是平级目的地，返回统一回相机，而不是回上一个 Tab。
        // 否则从设置切到相册再按返回，会回到设置，用户永远退不出纸面页。
        else -> state.copy(destination = AppDestination.CAMERA)
    }
}
