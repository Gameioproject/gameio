package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.screens.home.delegates.HomeVideoPreviewDelegate
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test

class HomeVideoPreviewTest {
    private val audio = mockk<AmbientAudioManager>(relaxed = true)
    private val previews = HomeVideoPreviewDelegate(audio)

    @Test fun `late callbacks cannot activate or cancel another game's trailer`() {
        previews.startVideoPreviewLoading(1, "first")
        val stale = previews.state.value.videoPreviewRequest!!
        previews.startVideoPreviewLoading(2, "second")
        val current = previews.state.value.videoPreviewRequest!!
        previews.activateVideoPreview(stale)
        previews.cancelVideoPreviewLoading(stale)
        assertEquals(current, previews.state.value.videoPreviewRequest)
        assertTrue(previews.state.value.isVideoPreviewLoading)
        assertFalse(previews.state.value.isVideoPreviewActive)
        previews.activateVideoPreview(current)
        assertTrue(previews.state.value.isVideoPreviewActive)
    }

    @Test fun `revisiting the same game creates a distinct playback request`() {
        previews.startVideoPreviewLoading(1, "same")
        val stale = previews.state.value.videoPreviewRequest!!
        previews.deactivateVideoPreview()
        previews.startVideoPreviewLoading(1, "same")
        previews.activateVideoPreview(stale)
        assertFalse(previews.state.value.isVideoPreviewActive)
        assertNotEquals(stale, previews.state.value.videoPreviewRequest)
    }

    @Test fun `playback error restores cover and clears active state`() {
        previews.startVideoPreviewLoading(1, "video")
        val request = previews.state.value.videoPreviewRequest!!
        previews.activateVideoPreview(request)
        previews.cancelVideoPreviewLoading(request)
        assertFalse(previews.state.value.isVideoPreviewActive)
        assertFalse(previews.state.value.isVideoPreviewLoading)
        assertNull(previews.state.value.videoPreviewRequest)
        previews.activateVideoPreview(request)
        assertFalse(previews.state.value.isVideoPreviewActive)
        verify(exactly = 1) { audio.fadeOut() }
        verify(exactly = 1) { audio.fadeIn() }
    }

    @Test fun `muted preview preserves audio preference through a new selection`() {
        previews.updateFromPreferences(true, true, 3)
        previews.startVideoPreviewLoading(1, "video")
        previews.activateVideoPreview(previews.state.value.videoPreviewRequest!!)
        previews.startVideoPreviewLoading(2, "next")
        assertFalse(previews.state.value.isVideoPreviewActive)
        assertTrue(previews.state.value.isVideoPreviewLoading)
        assertTrue(previews.state.value.muteVideoPreview)
        verify(exactly = 0) { audio.fadeOut() }
    }
}
