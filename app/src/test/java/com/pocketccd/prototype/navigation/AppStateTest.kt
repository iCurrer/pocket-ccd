package com.pocketccd.prototype.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D-039 removed HOME: the app starts on the viewfinder and CAMERA is the root.
 * D-063 1:1 版面又加了底部 Tab（相册/胶卷/发现/设置），纸面页之间是**平级切换**。
 * D-071 把 RESULT 改名 DETAIL 并给了它返回起点：从相册点开的一张，返回就是相册。
 */
class AppStateTest {

    @Test
    fun appStartsOnCamera() {
        assertEquals(AppDestination.CAMERA, AppUiState().destination)
    }

    @Test
    fun cameraOpensGalleryAndBackReturnsCamera() {
        val gallery = reduce(AppUiState(), AppEvent.OpenGallery)
        assertEquals(AppDestination.GALLERY, gallery.destination)
        assertEquals(AppDestination.CAMERA, gallery.galleryOrigin)

        val back = reduce(gallery, AppEvent.NavigateBack)
        assertEquals(AppDestination.CAMERA, back.destination)
    }

    @Test
    fun cameraOpensDetailAndBackReturnsCamera() {
        val detail = reduce(AppUiState(), AppEvent.OpenDetail)
        assertEquals(AppDestination.DETAIL, detail.destination)
        assertEquals(AppDestination.CAMERA, detail.detailOrigin)

        val back = reduce(detail, AppEvent.NavigateBack)
        assertEquals(AppDestination.CAMERA, back.destination)
    }

    @Test
    fun galleryOpensDetailAndBackReturnsGallery() {
        // D-071：详情页是压栈页，返回回起点。从相册翻一张按返回若掉回取景器，
        // 用户就得重新进相册、重新找那一张 —— 之前就是这个行为。
        val gallery = reduce(AppUiState(), AppEvent.OpenGallery)
        val detail = reduce(gallery, AppEvent.OpenDetail)
        assertEquals(AppDestination.DETAIL, detail.destination)
        assertEquals(AppDestination.GALLERY, detail.detailOrigin)

        val back = reduce(detail, AppEvent.NavigateBack)
        assertEquals(AppDestination.GALLERY, back.destination)
    }

    @Test
    fun detailOpensGalleryAndBackReturnsCamera() {
        val detail = reduce(AppUiState(), AppEvent.OpenDetail)
        val gallery = reduce(detail, AppEvent.OpenGallery)
        assertEquals(AppDestination.DETAIL, gallery.galleryOrigin)

        // D-064：底部 Tab 是平级目的地，从 Tab 进来之后的返回统一回相机。
        val back = reduce(gallery, AppEvent.NavigateBack)
        assertEquals(AppDestination.CAMERA, back.destination)
    }

    @Test
    fun detailReturnsToCamera() {
        val detail = reduce(AppUiState(), AppEvent.OpenDetail)
        val backToCamera = reduce(detail, AppEvent.OpenCamera)
        assertEquals(AppDestination.CAMERA, backToCamera.destination)
    }

    @Test
    fun settingsOpensFromCameraAndBackReturnsCamera() {
        val settings = reduce(AppUiState(), AppEvent.OpenSettings)
        assertEquals(AppDestination.SETTINGS, settings.destination)

        val back = reduce(settings, AppEvent.NavigateBack)
        assertEquals(AppDestination.CAMERA, back.destination)
    }

    @Test
    fun settingsTabCanOpenGallery() {
        val settings = reduce(AppUiState(), AppEvent.OpenSettings)
        val next = reduce(settings, AppEvent.OpenGallery)
        assertEquals(AppDestination.GALLERY, next.destination)
    }

    @Test
    fun galleryTabCanOpenSettings() {
        val gallery = reduce(AppUiState(), AppEvent.OpenGallery)
        val next = reduce(gallery, AppEvent.OpenSettings)
        assertEquals(AppDestination.SETTINGS, next.destination)
    }

    @Test
    fun duplicateGalleryEventIsIgnored() {
        val gallery = reduce(AppUiState(), AppEvent.OpenGallery)
        val next = reduce(gallery, AppEvent.OpenGallery)
        assertEquals(gallery, next)
    }

    @Test
    fun cameraIgnoresOpenCamera() {
        val state = AppUiState()
        assertEquals(state, reduce(state, AppEvent.OpenCamera))
    }

    @Test
    fun everyPaperPageCanReturnToCamera() {
        // 底部 Tab 的「拍摄」从任何纸面页都要走通，否则相册/设置页没有回取景器的出口。
        val pages = listOf(
            AppEvent.OpenGallery,
            AppEvent.OpenFilms,
            AppEvent.OpenDiscover,
            AppEvent.OpenSettings,
        )
        for (event in pages) {
            val page = reduce(AppUiState(), event)
            assertEquals(event.toString(), AppDestination.CAMERA, reduce(page, AppEvent.OpenCamera).destination)
        }
    }

    @Test
    fun detailCannotBeOpenedFromSettingsOrFilms() {
        // 设置页 / 胶卷库里没有"当前这一张照片"的上下文，此时打开详情只会显示空白。
        val settings = reduce(AppUiState(), AppEvent.OpenSettings)
        assertEquals(settings, reduce(settings, AppEvent.OpenDetail))

        val films = reduce(AppUiState(), AppEvent.OpenFilms)
        assertEquals(films, reduce(films, AppEvent.OpenDetail))
    }

    @Test
    fun backOnCameraIsNoOpSoSystemFinishes() {
        val state = AppUiState()
        assertEquals(state, reduce(state, AppEvent.NavigateBack))
    }

    @Test
    fun cameraOpensFilmsAndBackReturnsCamera() {
        val films = reduce(AppUiState(), AppEvent.OpenFilms)
        assertEquals(AppDestination.FILMS, films.destination)

        val back = reduce(films, AppEvent.NavigateBack)
        assertEquals(AppDestination.CAMERA, back.destination)
    }

    @Test
    fun filmsOpensGalleryAndBackReturnsCamera() {
        // D-064 之后返回统一回相机，不回上一个 Tab。
        val films = reduce(AppUiState(), AppEvent.OpenFilms)
        val gallery = reduce(films, AppEvent.OpenGallery)
        assertEquals(AppDestination.GALLERY, gallery.destination)

        assertEquals(AppDestination.CAMERA, reduce(gallery, AppEvent.NavigateBack).destination)
    }

    @Test
    fun filmsTabCanOpenSettings() {
        val films = reduce(AppUiState(), AppEvent.OpenFilms)
        assertEquals(AppDestination.SETTINGS, reduce(films, AppEvent.OpenSettings).destination)
    }

    @Test
    fun paperTabsCanOpenFilms() {
        val gallery = reduce(AppUiState(), AppEvent.OpenGallery)
        assertEquals(AppDestination.FILMS, reduce(gallery, AppEvent.OpenFilms).destination)

        val detail = reduce(AppUiState(), AppEvent.OpenDetail)
        assertEquals(AppDestination.FILMS, reduce(detail, AppEvent.OpenFilms).destination)
    }

    @Test
    fun cameraOpensDiscoverAndBackReturnsCamera() {
        val discover = reduce(AppUiState(), AppEvent.OpenDiscover)
        assertEquals(AppDestination.DISCOVER, discover.destination)
        assertEquals(AppDestination.CAMERA, reduce(discover, AppEvent.NavigateBack).destination)
    }

    @Test
    fun discoverTabIsReachableFromPaperPages() {
        val gallery = reduce(AppUiState(), AppEvent.OpenGallery)
        assertEquals(AppDestination.DISCOVER, reduce(gallery, AppEvent.OpenDiscover).destination)

        val settings = reduce(AppUiState(), AppEvent.OpenSettings)
        assertEquals(AppDestination.DISCOVER, reduce(settings, AppEvent.OpenDiscover).destination)
    }

    @Test
    fun duplicateDiscoverEventIsIgnored() {
        val discover = reduce(AppUiState(), AppEvent.OpenDiscover)
        assertEquals(discover, reduce(discover, AppEvent.OpenDiscover))
    }

    /**
     * D-077：全屏查看器是模态，必须把 TabBar 挡掉。
     *
     * 这条曾经是个真 bug：查看器的打开状态藏在 `GalleryScreen` 内部，外层不知道，
     * TabBar 就一直画在查看器上面（用户报的"相册点开大图后导航栏和内容重叠"）。
     * 判断被提到 `showBottomTabs` 之后，这里把"哪些页面有 Tab"钉死。
     */
    @Test
    fun bottomTabsFollowDestinationAndViewerState() {
        // 四个平级页：有 Tab
        for (destination in listOf(
            AppDestination.GALLERY,
            AppDestination.FILMS,
            AppDestination.DISCOVER,
            AppDestination.SETTINGS,
        )) {
            assertEquals(
                "$destination 应该显示 TabBar",
                true,
                showBottomTabs(destination, viewerOpen = false),
            )
        }

        // 查看器打开：任何页面都不显示 TabBar
        for (destination in AppDestination.entries) {
            assertEquals(
                "$destination 在查看器打开时不该有 TabBar",
                false,
                showBottomTabs(destination, viewerOpen = true),
            )
        }

        // 详情页是压栈页：本来就没有 TabBar
        assertEquals(false, showBottomTabs(AppDestination.DETAIL, viewerOpen = false))
    }
}
