package com.pocketccd.prototype.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppStateTest {

    @Test
    fun homeOpensCamera() {
        val next = reduce(AppUiState(), AppEvent.OpenCamera)
        assertEquals(AppDestination.CAMERA, next.destination)
    }

    @Test
    fun homeOpensGalleryAndBackReturnsHome() {
        val gallery = reduce(AppUiState(), AppEvent.OpenGallery)
        assertEquals(AppDestination.GALLERY, gallery.destination)
        assertEquals(AppDestination.HOME, gallery.galleryOrigin)

        val back = reduce(gallery, AppEvent.NavigateBack)
        assertEquals(AppDestination.HOME, back.destination)
    }

    @Test
    fun cameraOpensGalleryAndBackReturnsCamera() {
        val camera = reduce(AppUiState(), AppEvent.OpenCamera)
        val gallery = reduce(camera, AppEvent.OpenGallery)
        assertEquals(AppDestination.CAMERA, gallery.galleryOrigin)

        val back = reduce(gallery, AppEvent.NavigateBack)
        assertEquals(AppDestination.CAMERA, back.destination)
    }

    @Test
    fun cameraOpensResultAndBackReturnsCamera() {
        val camera = reduce(AppUiState(), AppEvent.OpenCamera)
        val result = reduce(camera, AppEvent.OpenResult)
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
        val camera = reduce(AppUiState(), AppEvent.OpenCamera)
        val result = reduce(camera, AppEvent.OpenResult)
        val gallery = reduce(result, AppEvent.OpenGallery)
        assertEquals(AppDestination.RESULT, gallery.galleryOrigin)

        val back = reduce(gallery, AppEvent.NavigateBack)
        assertEquals(AppDestination.RESULT, back.destination)
    }

    @Test
    fun resultOpensCamera() {
        val camera = reduce(AppUiState(), AppEvent.OpenCamera)
        val result = reduce(camera, AppEvent.OpenResult)
        val backToCamera = reduce(result, AppEvent.OpenCamera)
        assertEquals(AppDestination.CAMERA, backToCamera.destination)
    }

    @Test
    fun settingsOnlyOpensFromHomeAndBackReturnsHome() {
        val settings = reduce(AppUiState(), AppEvent.OpenSettings)
        assertEquals(AppDestination.SETTINGS, settings.destination)

        val back = reduce(settings, AppEvent.NavigateBack)
        assertEquals(AppDestination.HOME, back.destination)
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
    fun backOnHomeIsNoOp() {
        val state = AppUiState()
        assertEquals(state, reduce(state, AppEvent.NavigateBack))
    }
}
