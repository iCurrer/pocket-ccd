package com.pocketccd.prototype.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D-039 removed HOME: the app starts on the viewfinder and CAMERA is the root.
 * These cases pin that down, plus the four overlay destinations that can still
 * be entered from it.
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
    fun settingsCannotOpenGallery() {
        val settings = reduce(AppUiState(), AppEvent.OpenSettings)
        val next = reduce(settings, AppEvent.OpenGallery)
        assertEquals(AppDestination.SETTINGS, next.destination)
    }

    @Test
    fun openSettingsFromGalleryIsIgnored() {
        val gallery = reduce(AppUiState(), AppEvent.OpenGallery)
        val next = reduce(gallery, AppEvent.OpenSettings)
        assertEquals(AppDestination.GALLERY, next.destination)
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
        // 库里顶栏右侧就是相册入口（原型 films.html 的 image-multiple 按钮），
        // 所以这里必须能进相册；返回要回到库，而不是掉回取景器。
        val films = reduce(AppUiState(), AppEvent.OpenFilms)
        val gallery = reduce(films, AppEvent.OpenGallery)
        assertEquals(AppDestination.GALLERY, gallery.destination)

        assertEquals(AppDestination.FILMS, reduce(gallery, AppEvent.NavigateBack).destination)
    }

    @Test
    fun filmsIgnoresSettings() {
        val films = reduce(AppUiState(), AppEvent.OpenFilms)
        assertEquals(AppDestination.FILMS, reduce(films, AppEvent.OpenSettings).destination)
    }

    @Test
    fun openFilmsOnlyFromCamera() {
        val gallery = reduce(AppUiState(), AppEvent.OpenGallery)
        assertEquals(AppDestination.GALLERY, reduce(gallery, AppEvent.OpenFilms).destination)

        val result = reduce(AppUiState(), AppEvent.OpenResult)
        assertEquals(AppDestination.RESULT, reduce(result, AppEvent.OpenFilms).destination)
    }
}
