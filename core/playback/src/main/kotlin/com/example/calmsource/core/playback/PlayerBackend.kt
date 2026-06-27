package com.example.calmsource.core.playback

import android.view.SurfaceView
import com.example.calmsource.core.model.PlaybackAudioTrack
import com.example.calmsource.core.model.PlaybackError
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSubtitleTrack
import com.example.calmsource.core.model.PlayerState
import kotlinx.coroutines.flow.StateFlow

/**
 * Internal abstraction over a media player engine (ExoPlayer or VLC).
 * Allows [PlaybackManager] to swap backends transparently when one
 * engine cannot handle a given container format.
 */
internal interface PlayerBackend {
    /** Current player state, mirrored to [com.example.calmsource.core.model.PlayerUiState]. */
    val state: StateFlow<PlayerBackendState>

    /** The [androidx.media3.ui.PlayerView] used for surface output (may be null during setup). */
    var playerView: androidx.media3.ui.PlayerView?

    /** Whether the backend requires a valid SurfaceView before [prepare] can be called. */
    val isSurfaceRequired: Boolean

    /** Create/recreate the underlying engine for [source]. */
    fun prepare(context: android.content.Context, source: PlaybackSource, resumePositionMs: Long)

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun stop()
    fun release()

    /** Current playback position in milliseconds, or 0 if unknown. */
    fun currentPositionMs(): Long

    /** Media duration in milliseconds, or 0 if unknown. */
    fun durationMs(): Long

    /** Whether content is currently playing (not paused). */
    fun isPlaying(): Boolean

    /** Force the next format retry attempt on container failures (ExoPlayer-only; VLC is no-op). */
    fun forceNextFormatRetry()

    /**
     * Select the audio track identified by [trackId]. Routed through the backend so VLC (where the
     * ExoPlayer `player` is null) is not a silent no-op (#11). Default no-op for backends that do
     * not support track switching.
     */
    fun selectAudioTrack(trackId: String) {}

    /** Select the subtitle/text track identified by [trackId]. */
    fun selectSubtitleTrack(trackId: String) {}

    /** Disable subtitle/text rendering. */
    fun disableSubtitles() {}

    /** Audio tracks currently exposed by the backend (used to populate the picker for VLC). */
    fun availableAudioTracks(): List<PlaybackAudioTrack> = emptyList()

    /** Subtitle tracks currently exposed by the backend (used to populate the picker for VLC). */
    fun availableSubtitleTracks(): List<PlaybackSubtitleTrack> = emptyList()
}

/**
 * Minimal player state exposed by a [PlayerBackend] to [PlaybackManager].
 */
internal data class PlayerBackendState(
    val playerState: PlayerState,
    val error: PlaybackError? = null,
    val playbackSpeed: Float = 1f,
    val isMuted: Boolean = false,
    val rawErrorCode: String? = null,
    /** True once the backend has rendered at least one video frame (VLC Vout / Exo first frame). */
    val firstFrameRendered: Boolean = false,
)