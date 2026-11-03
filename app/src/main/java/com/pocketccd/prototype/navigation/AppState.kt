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

    /**
     * 单张详情（D-071）。
     *
     * 旧名是 `RESULT`，只能从快门路径进入 —— 于是"从相册点开一张历史照片"没有任何
     * 参数、动作、EXIF 可看（复审 P1-4）。现在**两条入口共用这一屏**：刚拍完的那张与
     * 相册里翻出来的那张，走同一个目的地、同一个 `PhotoDetailScreen`。
     *
     * 名字跟着改：这一屏不再是"这次拍摄的结果"，而是"这一张照片的详情"。
     */
    DETAIL,

    SETTINGS,

    /**
     * 胶卷库（换卷页，T9-016）。设备上实际有几卷取决于推了几个 pack，
     * 所以这页的数量永远是运行时读出来的，不写死。
     */
    FILMS,

    /**
     * 发现页。目前只展示真实统计（照片数 / 收藏数 / 胶卷数 / 磁盘占用），
     * 内容由后续智能体接入，这里先保留独立目的地，避免底部 Tab 变成一个死按钮。
     */
    DISCOVER,
}

sealed interface AppEvent {
    /** 回取景页。相机是根，从任何页面都该走得通。 */
    data object OpenCamera : AppEvent

    data object OpenGallery : AppEvent
    data object OpenSettings : AppEvent
    data object OpenDetail : AppEvent
    data object OpenFilms : AppEvent
    data object OpenDiscover : AppEvent
    data object NavigateBack : AppEvent
}

/**
 * UI navigation state。
 *
 * [detailOrigin] 记录**这一次详情是从哪进来的**：从相册点开就该退回相册，
 * 从快门进来才退回取景页。之前的版本记了 `resultOrigin` 却从不读它，
 * 于是在相册里翻完一张按返回会直接掉回取景器（还要顺带重绑一次相机）。
 */
data class AppUiState(
    val destination: AppDestination = AppDestination.CAMERA,
    val galleryOrigin: AppDestination = AppDestination.CAMERA,
    val detailOrigin: AppDestination = AppDestination.CAMERA,
)

fun reduce(state: AppUiState, event: AppEvent): AppUiState = when (event) {
    // 相机是根：从任何页面都能回去（底部 Tab 的「拍摄」走的也是这条路）。
    AppEvent.OpenCamera -> when (state.destination) {
        AppDestination.CAMERA -> state
        else -> state.copy(destination = AppDestination.CAMERA)
    }

    // 底部 Tab 是平级切换：从任何纸面页都能去另外三个 Tab。
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

    // 详情页只从"相机"（刚按完快门）和"相册"（点开一张）进入。
    // 其它页面没有"一张照片"这个上下文，忽略事件比乱猜一张更有礼貌。
    AppEvent.OpenDetail -> when (state.destination) {
        AppDestination.CAMERA, AppDestination.GALLERY -> state.copy(
            destination = AppDestination.DETAIL,
            detailOrigin = state.destination,
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

        // 详情页是**压栈**页，不是 Tab：退回它是从哪来的（D-071）。
        // 从相册点开就回相册，看完继续翻；从快门进来就回取景器，接着拍。
        AppDestination.DETAIL -> state.copy(destination = state.detailOrigin)

        // 底部 Tab 是平级目的地，返回统一回相机，而不是回上一个 Tab（D-064）。
        // 否则从设置切到相册再按返回，会回到设置，用户永远退不出纸面页。
        else -> state.copy(destination = AppDestination.CAMERA)
    }
}

/**
 * 底部 TabBar 是否出现。
 *
 * 两个例外都是"盖在最上面、有自己的出口"的页面：
 * - **详情页**（压栈页）：出口是左上角返回；
 * - **全屏查看器**（模态）：出口是右上角关闭 / 系统返回，它必须盖住 TabBar。
 *
 * 这条判断放在这里而不是散在 UI 里，是因为它曾经散着：查看器的打开状态一度藏在
 * `GalleryScreen` 内部，外层不知道，于是 TabBar 一直画在查看器上面（D-077）。
 */
internal fun showBottomTabs(
    destination: AppDestination,
    viewerOpen: Boolean,
): Boolean = destination != AppDestination.DETAIL && !viewerOpen
