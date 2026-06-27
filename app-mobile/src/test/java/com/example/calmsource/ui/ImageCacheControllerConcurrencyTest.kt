package com.example.calmsource.ui

import android.content.Context
import com.example.calmsource.core.playback.ImageCacheController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class ImageCacheControllerConcurrencyTest {

    @Test
    fun testRestoreWithOldGenerationDoesNotOverrideActiveTrimState() {
        val mockContext = mock(Context::class.java)

        // 1. Trigger trim to start playback (generation G)
        val stateAfterTrim = ImageCacheController.trimForPlayback(mockContext)
        assertTrue("Trim should be active after trimForPlayback", stateAfterTrim.playbackTrimActive)
        assertTrue("Non-critical requests should be paused after trimForPlayback", stateAfterTrim.nonCriticalRequestsPaused)

        // Capture current state/generation
        val currentState = ImageCacheController.state.value
        assertTrue("Current state should have trim active", currentState.playbackTrimActive)

        // 2. Call restoreAfterPlayback with an old generation (e.g. 0L, since generation was incremented)
        // This simulates a delayed restore job that was scheduled before the most recent trim.
        ImageCacheController.restoreAfterPlayback(mockContext, generation = 0L)

        // 3. Verify that the state is NOT reset to untrimmed/unpaused
        val stateAfterOldRestore = ImageCacheController.state.value
        assertTrue(
            "State desynchronization bug: restore with an old generation cleared active trim state!",
            stateAfterOldRestore.playbackTrimActive
        )
        assertTrue(
            "State desynchronization bug: restore with an old generation unpaused non-critical requests!",
            stateAfterOldRestore.nonCriticalRequestsPaused
        )
    }
}
