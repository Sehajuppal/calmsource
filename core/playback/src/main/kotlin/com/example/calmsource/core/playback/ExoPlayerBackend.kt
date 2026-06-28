package com.example.calmsource.core.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import com.example.calmsource.core.model.PlaybackError
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Callback interface that [ExoPlayerBackend] uses to access shared state
 * managed by [PlaybackManager]. Keeps the backend decoupled from the
 * orchestrator so it can be tested in isolation.
 */
internal interface ExoPlayerHost {
    /** The current [androidx.media3.exoplayer.ExoPlayer] instance (read/write). Setting to null must update [PlaybackManager.playerFlow]. */
    var hostedPlayer: androidx.media3.exoplayer.ExoPlayer?
    /** The [androidx.media3.ui.PlayerView] used for surface output. */
    val hostedPlayerView: androidx.media3.ui.PlayerView?
    /** The currently cached [MediaItem], built by a prior [buildMediaItemForBackend] call. */
    val hostedActiveMediaItem: MediaItem?
    /** Whether the active request wants to auto-play. */
    val hostedPlayWhenReady: Boolean
    /** The [Player.Listener] managed by [PlaybackManager] (added/removed by the backend). */
    val hostedPlayerListener: Player.Listener
    /** Build a [MediaItem] for the given source. May throw. */
    @Throws(PlaybackException::class, SecurityException::class)
    fun buildMediaItemForBackend(source: PlaybackSource): MediaItem
    /** Map a [PlaybackException] to the domain [PlaybackError]. */
    fun mapPlaybackError(error: PlaybackException): PlaybackError
    /** Delegate the MIME-format retry to the orchestrator. */
    fun onForceFormatRetry()
}

/**
 * [PlayerBackend] implementation backed by ExoPlayer.
 *
 * Extracted from [PlaybackManager]'s private inner class to allow independent
 * unit testing and reduce class size. The backend does NOT own the ExoPlayer
 * instance — it accesses it through the [ExoPlayerHost] interface which is
 * implemented by [PlaybackManager].
 */
@UnstableApi
internal class ExoPlayerBackend(
    private val host: ExoPlayerHost,
) : PlayerBackend {
    private val _state = MutableStateFlow(PlayerBackendState(PlayerState.IDLE))
    override val state: StateFlow<PlayerBackendState> = _state.asStateFlow()

    override var playerView: androidx.media3.ui.PlayerView?
        get() = host.hostedPlayerView
        set(_) { /* Surface lifecycle managed by PlaybackManager */ }

    override val isSurfaceRequired: Boolean = false

    fun updateState(playerState: PlayerState) {
        _state.value = _state.value.copy(playerState = playerState)
    }

    fun updateError(error: PlaybackError, rawErrorCode: String) {
        _state.value = _state.value.copy(playerState = PlayerState.FAILED, error = error, rawErrorCode = rawErrorCode)
    }

    fun updateSpeed(speed: Float) {
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    fun updateMuted(isMuted: Boolean) {
        _state.value = _state.value.copy(isMuted = isMuted)
    }

    override fun prepare(context: Context, source: PlaybackSource, resumePositionMs: Long) {
        _state.value = _state.value.copy(error = null)
        val mediaItem = try {
            host.hostedActiveMediaItem ?: host.buildMediaItemForBackend(source)
        } catch (e: PlaybackException) {
            val playbackError = host.mapPlaybackError(e)
            _state.value = _state.value.copy(playerState = PlayerState.FAILED, error = playbackError, rawErrorCode = e.errorCodeName)
            return
        } catch (e: SecurityException) {
            val pe = PlaybackError.PermissionRequired(cause = e, message = e.message ?: "Unsafe scheme rejected")
            _state.value = _state.value.copy(playerState = PlayerState.FAILED, error = pe, rawErrorCode = "ERROR_CODE_PERMISSION_DENIED")
            return
        } catch (e: Exception) {
            val sanitizedCause = Exception(PlaybackSanitizer.sanitize(e.message))
            val pe = PlaybackError.SourceUnavailable(cause = sanitizedCause)
            _state.value = _state.value.copy(playerState = PlayerState.FAILED, error = pe, rawErrorCode = "ERROR_CODE_UNSPECIFIED")
            return
        }

        host.hostedPlayer?.apply {
            val playbackState = playbackState
            // Re-prepare when the explicit MIME hint changes even if the URL (mediaId) is the
            // same, otherwise a MIME-retry while the player is still READY/BUFFERING is silently
            // dropped and the alternate container is never actually tried (#10).
            val mimeChanged = currentMediaItem?.localConfiguration?.mimeType !=
                    mediaItem.localConfiguration?.mimeType
            val needsPrepare = (currentMediaItem?.mediaId != mediaItem.mediaId) ||
                    mimeChanged ||
                    playbackState == Player.STATE_IDLE ||
                    playbackState == Player.STATE_ENDED
            if (needsPrepare) {
                _state.value = _state.value.copy(playerState = PlayerState.PREPARING)
                setMediaItem(mediaItem, resumePositionMs)
                prepare()
            }
            playWhenReady = host.hostedPlayWhenReady
        }
    }

    override fun play() {
        host.hostedPlayer?.playWhenReady = true
    }

    override fun pause() {
        host.hostedPlayer?.playWhenReady = false
    }

    override fun seekTo(positionMs: Long) {
        host.hostedPlayer?.seekTo(positionMs)
    }

    override fun stop() {
        host.hostedPlayer?.stop()
    }

    override fun release() {
        releaseSafely(skipStop = false)
    }

    fun releaseSafely(skipStop: Boolean = false) {
        val p = host.hostedPlayer ?: return
        p.removeListener(host.hostedPlayerListener)
        if (!skipStop) {
            runCatching { p.stop() }
        }
        runCatching { p.release() }
        host.hostedPlayer = null
    }

    override fun currentPositionMs(): Long {
        return host.hostedPlayer?.currentPosition ?: 0L
    }

    override fun durationMs(): Long {
        return host.hostedPlayer?.duration ?: 0L
    }

    override fun bufferedPositionMs(): Long {
        return host.hostedPlayer?.bufferedPosition?.coerceAtLeast(0L) ?: 0L
    }

    override fun isPlaying(): Boolean {
        return host.hostedPlayer?.isPlaying ?: false
    }

    override fun forceNextFormatRetry() {
        host.onForceFormatRetry()
    }

    override fun selectAudioTrack(trackId: String) {
        val p = host.hostedPlayer ?: return
        val tracks = p.currentTracks
        var idx = 0
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val id = format.id ?: "audio_$idx"
                if (id == trackId) {
                    p.trackSelectionParameters = p.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .addOverride(TrackSelectionOverride(group.mediaTrackGroup, i))
                        .build()
                    return
                }
                idx++
            }
        }
    }

    override fun selectSubtitleTrack(trackId: String) {
        val p = host.hostedPlayer ?: return
        val tracks = p.currentTracks
        var idx = 0
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_TEXT) continue
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val id = format.id ?: "sub_$idx"
                if (id == trackId) {
                    p.trackSelectionParameters = p.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .addOverride(TrackSelectionOverride(group.mediaTrackGroup, i))
                        .build()
                    return
                }
                idx++
            }
        }
    }

    override fun disableSubtitles() {
        val p = host.hostedPlayer ?: return
        p.trackSelectionParameters = p.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }
}
