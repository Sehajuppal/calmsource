package com.example.calmsource.core.playback

import com.example.calmsource.core.model.PlaybackError
import com.example.calmsource.core.model.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VlcPlayerBackendParityTest {

    @Test
    fun `mergePlayerState clears failure error when recovering on buffering`() {
        val backend = VlcPlayerBackend()
        val failed = PlayerBackendState(
            playerState = PlayerState.FAILED,
            error = PlaybackError.Timeout(cause = Exception("startup timeout")),
            rawErrorCode = "PLAYBACK_TIMEOUT",
        )
        setState(backend, failed)

        invokeMerge(backend, PlayerState.BUFFERING, clearError = true)

        val state = backend.state.value
        assertEquals(PlayerState.BUFFERING, state.playerState)
        assertNull(state.error)
    }

    @Test
    fun `mergePlayerState preserves failure error when clearError is false`() {
        val backend = VlcPlayerBackend()
        val failed = PlayerBackendState(
            playerState = PlayerState.FAILED,
            error = PlaybackError.Timeout(cause = Exception("startup timeout")),
            rawErrorCode = "PLAYBACK_TIMEOUT",
        )
        setState(backend, failed)

        invokeMerge(backend, PlayerState.BUFFERING, clearError = false)

        val state = backend.state.value
        assertEquals(PlayerState.BUFFERING, state.playerState)
        assertNotNull(state.error)
        assertTrue(state.error is PlaybackError.Timeout)
        assertEquals("PLAYBACK_TIMEOUT", state.rawErrorCode)
    }

    @Test
    fun `mergePlayerState marks first frame without clearing playback state`() {
        val backend = VlcPlayerBackend()
        setState(
            backend,
            PlayerBackendState(
                playerState = PlayerState.BUFFERING,
                playbackSpeed = 1.25f,
                isMuted = true,
            )
        )

        invokeMerge(backend, PlayerState.BUFFERING, firstFrameRendered = true)

        val state = backend.state.value
        assertTrue(state.firstFrameRendered)
        assertEquals(1.25f, state.playbackSpeed)
        assertTrue(state.isMuted)
    }

    private fun setState(backend: VlcPlayerBackend, state: PlayerBackendState) {
        val field = VlcPlayerBackend::class.java.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(backend) as kotlinx.coroutines.flow.MutableStateFlow<PlayerBackendState>
        flow.value = state
    }

    private fun invokeMerge(
        backend: VlcPlayerBackend,
        playerState: PlayerState,
        firstFrameRendered: Boolean? = null,
        clearError: Boolean = false,
    ) {
        val method = VlcPlayerBackend::class.java.getDeclaredMethod(
            "mergePlayerState",
            PlayerState::class.java,
            Boolean::class.javaObjectType,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val merged = method.invoke(backend, playerState, firstFrameRendered, clearError) as PlayerBackendState
        setState(backend, merged)
    }
}
