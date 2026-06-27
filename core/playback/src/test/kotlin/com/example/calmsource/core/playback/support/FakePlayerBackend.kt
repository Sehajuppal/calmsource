package com.example.calmsource.core.playback.support

import android.content.Context
import com.example.calmsource.core.model.PlaybackError
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlayerState
import com.example.calmsource.core.playback.PlayerBackend
import com.example.calmsource.core.playback.PlayerBackendState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controllable [PlayerBackend] for collector and VLC-handoff chaos tests.
 */
internal open class FakePlayerBackend(
    initialState: PlayerBackendState = PlayerBackendState(PlayerState.IDLE),
) : PlayerBackend {
    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<PlayerBackendState> = _state.asStateFlow()

    override var playerView: androidx.media3.ui.PlayerView? = null
    override val isSurfaceRequired: Boolean = false

    var prepareCount: Int = 0
        private set

    fun emit(state: PlayerBackendState) {
        _state.value = state
    }

    fun emitFirstFrame(playerState: PlayerState = PlayerState.PLAYING) {
        emit(_state.value.copy(playerState = playerState, firstFrameRendered = true))
    }

    fun emitFailed(error: PlaybackError, rawErrorCode: String? = null) {
        emit(
            PlayerBackendState(
                playerState = PlayerState.FAILED,
                error = error,
                rawErrorCode = rawErrorCode,
            )
        )
    }

    override fun prepare(context: Context, source: PlaybackSource, resumePositionMs: Long) {
        prepareCount++
        _state.value = _state.value.copy(playerState = PlayerState.PREPARING, error = null)
    }

    override fun play() {
        _state.value = _state.value.copy(playerState = PlayerState.PLAYING)
    }

    override fun pause() {
        _state.value = _state.value.copy(playerState = PlayerState.PAUSED)
    }

    override fun seekTo(positionMs: Long) = Unit

    override fun stop() {
        _state.value = _state.value.copy(playerState = PlayerState.IDLE, error = null)
    }

    override fun release() {
        _state.value = PlayerBackendState(PlayerState.IDLE)
    }

    override fun currentPositionMs(): Long = 0L
    override fun durationMs(): Long = 0L
    override fun isPlaying(): Boolean = _state.value.playerState == PlayerState.PLAYING
    override fun forceNextFormatRetry() = Unit
}
