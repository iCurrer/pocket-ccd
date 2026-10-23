package com.pocketccd.prototype.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D-039 removed HOME: the app starts on the viewfinder and CAMERA is the root.
 * D-063 1:1 版面又加了底部 4 Tab（相册/胶卷/发现/设置），所以纸面页之间现在是
 * **平级切换**：从任何纸面页都能去另外三个 Tab；相机页仍然只从取景页进。
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
    fun cameraOpensResultAndBackReturnsCamera() {
        val result = reduce(AppUiState(), AppEvent.OpenResult)
        assertEquals(AppDestination.RESULT, result.destination)
        assertEquals(AppDestination.CAMERA, result.resultOrigin)

        val back = reduce(result, AppEvent.NavigateBack)
        assertEquals(AppDestination.CAMERA, back.destination)
    }

    @Test
    fun galleryOpensResultAndBackReturnsGallery() {
        val gallery = reduce(AppUiState(), AppEvent.OpenGallery)
        val result = reduce(gallery, AppEvent.OpenResult)
        assertEquals(AppDestination.GALLERY, result.resultOrigin)

        val back = reduce(result, AppEvent.NavigateBack)
        assertEquals(AppDestination.GALLERY, back.destination)
    }

    @Test
    fun resultOpensGalleryAndBackReturnsResult() {
        val result = reduce(AppUiState(), AppEvent.OpenResult)
        val gallery = reduce(result, AppEvent.OpenGallery)
        assertEquals(AppDestination.RESULT, gallery.galleryOrigin)

        val back = reduce(gallery, AppEvent.NavigateBack)
        assertEquals(AppDestination.RESULT, back.destination)
    }

    @Test
    fun resultReturnsToCamera() {
        val result = reduce(AppUiState(), AppEvent.OpenResult)
        val backToCamera = reduce(result, AppEvent.OpenCamera)
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
    fun filmsOpensGalleryAndBackReturnsToFilms() {
        // 库里顶部右侧是相册入口；返回要回到库，而不是掉回取景器。
        val films = reduce(AppUiState(), AppEvent.OpenFilms)
        val gallery = reduce(films, AppEvent.OpenGallery)
        assertEquals(AppDestination.GALLERY, gallery.destination)

        assertEquals(AppDestination.FILMS, reduce(gallery, AppEvent.NavigateBack).destination)
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

        val result = reduce(AppUiState(), AppEvent.OpenResult)
        assertEquals(AppDestination.FILMS, reduce(result, AppEvent.OpenFilms).destination)
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
}
