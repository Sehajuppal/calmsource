package com.example.calmsource.core.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.MainThread
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import io.ktor.client.plugins.ResponseException
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.SourceHealthRepository
import com.example.calmsource.core.database.repository.RoomUserMemoryRepository
import com.example.calmsource.core.database.repository.UserMemoryRepository
import com.example.calmsource.core.database.repository.UserPreferencesRepository
import com.example.calmsource.core.model.AutoFallbackPolicy
import com.example.calmsource.core.model.PlaybackAudioTrack
import com.example.calmsource.core.model.PlaybackError
import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.PlaybackSubtitleTrack
import com.example.calmsource.core.model.PlaybackSessionDiagnostics
import com.example.calmsource.core.model.PlayerState
import com.example.calmsource.core.model.PlayerUiState
import com.example.calmsource.core.playback.diagnostics.PlaybackDiagnosticsRecorder
import com.example.calmsource.core.playback.diagnostics.inferDisplayPhase
import com.example.calmsource.core.playback.diagnostics.sanitizedEvent
import com.example.calmsource.core.playback.recovery.PlaybackRecoveryEngine
import com.example.calmsource.core.playback.recovery.RecoveryAction
import com.example.calmsource.core.playback.recovery.RecoveryContext
import com.example.calmsource.core.playback.recovery.RecoveryDecision
import com.example.calmsource.core.playback.session.DefaultStreamRaceFactory
import com.example.calmsource.core.playback.session.PlaybackSessionStore
import com.example.calmsource.core.playback.session.SessionPhase
import com.example.calmsource.core.playback.session.StreamRaceFactory
import com.example.calmsource.core.playback.watchdog.PlaybackWatchdogController
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Process-lifetime I/O scope for memory persistence writes.
 * Intentionally not cancelable — in-flight writes must complete even after
 * [PlaybackManager.release] to avoid data loss on app exit.
 */
typealias PlaybackProfile = PlaybackResourceProfile

/**
 * Process-lifetime I/O scope for memory persistence writes.
 * Intentionally not cancelable — in-flight writes must complete even after
 * [PlaybackManager.release] to avoid data loss on app exit.
 */
private val memoryPersistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Manages ExoPlayer lifecycle and state.
 *
 * Thread safety contract: All public methods that interact with [player] MUST be called
 * from the main (UI) thread. ExoPlayer itself enforces main-thread access; the @MainThread
 * annotations make this contract explicit at call sites.
 *
 * The [coroutineScope] should use [Dispatchers.Main] or [Dispatchers.Main.immediate] to
 * ensure coroutine-based player interactions remain on the main thread.
 */
@UnstableApi
class PlaybackManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val resourceStateSink: (PlayerState) -> Unit = {},
    private val lowMemoryModeSink: (Boolean) -> Unit = {},
    private val userMemoryRepositoryFactory: ((Context) -> UserMemoryRepository)? = null
) : ExoPlayerHost {
    init {
        TunnelingBlacklist.warmBestEffort(context)
        TunnelingPreferences.warmBestEffort(context)
        VlcPlayerBackend.restoreInitFailedState(context)
        StreamRacePreferences.warmBestEffort(context)
    }

    var onPlayerAboutToBeReleased: (() -> Unit)? = null

    internal var currentBackend: PlayerBackend? = null
        private set
    private var backendStateCollectionJob: Job? = null
    private var hasRenderedFirstFrame = false
    private var consecutiveFallbackCount = 0
    private var consecutiveBufferingCount = 0
    private val vlcAttemptedSourceIds = mutableSetOf<String>()
    private var isReleased = false
    private var lastSourceHeaders: Map<String, String> = emptyMap()
    private var activeMediaItem: MediaItem? = null
    @Volatile private var handlingFailure = false
    private var pendingFailure: Pair<PlaybackError, String>? = null

    private val _playerFlow = MutableStateFlow<ExoPlayer?>(null)
    val playerFlow: StateFlow<ExoPlayer?> = _playerFlow.asStateFlow()

    var player: ExoPlayer? = null
        private set(value) {
            field = value
            _playerFlow.value = value
        }

    internal var playerCreator: (Context, DefaultRenderersFactory, PlaybackSource, PlaybackProfile) -> ExoPlayer = { ctx, rf, src, profile ->
        val builder = ExoPlayer.Builder(ctx.applicationContext ?: ctx, rf)
            .setLoadControl(PlaybackProfileManager.loadControl(profile))

        val speedControl = PlaybackProfileManager.livePlaybackSpeedControl(profile)
        if (speedControl != null) {
            builder.setLivePlaybackSpeedControl(speedControl)
        }

        builder.setVideoChangeFrameRateStrategy(
            FrameRateMatchingPolicy.media3Strategy(FrameRateMatchingPreferences.mode)
        )

        val sourceHeaders = src.headers
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        val resolvingDataSourceFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(ctx, httpDataSourceFactory)
        ) { dataSpec ->
            if (sourceHeaders.isNotEmpty()) {
                dataSpec.withRequestHeaders(sourceHeaders)
            } else {
                dataSpec
            }
        }

        builder.setMediaSourceFactory(
            DefaultMediaSourceFactory(ctx)
                .setDataSourceFactory(resolvingDataSourceFactory)
                .setLoadErrorHandlingPolicy(CustomLoadErrorHandlingPolicy())
        )

        builder.build()
    }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _progressState = MutableStateFlow(com.example.calmsource.core.model.PlaybackProgressState())
    val progressState: StateFlow<com.example.calmsource.core.model.PlaybackProgressState> = _progressState.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<PlaybackAudioTrack>>(emptyList())
    val audioTracks: StateFlow<List<PlaybackAudioTrack>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<PlaybackSubtitleTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<PlaybackSubtitleTrack>> = _subtitleTracks.asStateFlow()

    private var progressJob: Job? = null

    // Fallback tracking
    val fallbackManager = FallbackManager()
    private val _fallbackPromptState = MutableStateFlow(false)
    val fallbackPromptState: StateFlow<Boolean> = _fallbackPromptState.asStateFlow()

    private var fallbackJob: Job? = null

    /**
     * Monotonic playback session store. Async work captures [PlaybackSessionStore.begin] ids and
     * aborts when a newer prepare/release supersedes it (#4).
     */
    internal val sessionStore = PlaybackSessionStore()

    /** Injectable for unit tests; production uses [DefaultStreamRaceFactory]. */
    internal var streamRaceFactory: StreamRaceFactory = DefaultStreamRaceFactory

    /** When false, skips the 1s progress polling loop so unit tests can finish deterministically. */
    internal var progressUpdatesEnabled: Boolean = true

    /**
     * Overrides mock-context VLC suppression in recovery decisions. Null keeps the production rule
     * (VLC disabled when [Context] class name contains "mock").
     */
    internal var allowVlcInRecovery: Boolean? = null

    /** Injectable VLC backend for unit tests; production uses [VlcPlayerBackend]. */
    internal var vlcBackendFactory: () -> PlayerBackend = { VlcPlayerBackend() }

    private val watchdogController: PlaybackWatchdogController by lazy {
        PlaybackWatchdogController(
            scope = coroutineScope,
            currentSessionId = { sessionStore.currentId() },
            isSessionCurrent = { sessionId -> isSessionCurrent(sessionId) },
            onFailure = { error, code -> reportFailure(error, code) },
            onRecordSuccess = { source -> recordPlaybackSuccess(source) },
            startupTimeoutMs = { startupTimeoutMs() },
            bufferingStallDelayMs = { bufferingStallDelayMs() },
            currentPlayerState = { uiState.value.playerState },
            currentSource = { uiState.value.source },
            hasRenderedFirstFrame = { hasRenderedFirstFrame },
            shouldScheduleFreezeWatchdog = {
                player?.playbackState == Player.STATE_READY &&
                    player?.playWhenReady == true &&
                    player?.currentTracks?.isTypeSelected(C.TRACK_TYPE_VIDEO) == true &&
                    !hasRenderedFirstFrame
            },
            isFreezeWatchdogStillValid = {
                !hasRenderedFirstFrame &&
                    player?.playWhenReady == true &&
                    player?.playbackState == Player.STATE_READY
            },
        )
    }

    private var lastHandledBackendError: PlaybackError? = null

    private var stateTrackingJob: Job? = null
    private var activeRequest: PlaybackRequest? = null
    private var activeCrashRecord: com.example.calmsource.core.playback.PlaybackCrashMarkerRecord? = null

    private val audioManager = PlaybackAudioManager(
        context = context,
        onPauseRequired = { abandonFocus -> pauseInternal(abandonFocus = abandonFocus) },
        onResumeRequired = { play() },
        isPlayingProvider = { uiState.value.playerState == PlayerState.PLAYING }
    )

    private val watchMemoryManager: PlaybackWatchMemoryManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val appContext = context.applicationContext ?: context
        val repo = userMemoryRepositoryFactory?.invoke(appContext)
            ?: run {
                try {
                    RoomUserMemoryRepository(DatabaseProvider.getDatabase(appContext))
                } catch (_: Exception) {
                    com.example.calmsource.core.database.repository.FallbackUserMemoryRepository()
                }
            }
        PlaybackWatchMemoryManager(appContext, repo)
    }

    // ExoPlayerHost implementation
    override var hostedPlayer: ExoPlayer?
        get() = player
        set(value) {
            player = value
        }

    override val hostedPlayerView: androidx.media3.ui.PlayerView?
        get() = playerView

    override val hostedActiveMediaItem: MediaItem?
        get() = activeMediaItem

    override val hostedPlayWhenReady: Boolean
        get() = activeRequest?.playWhenReady ?: true

    override val hostedPlayerListener: Player.Listener
        get() = playerListener

    override fun buildMediaItemForBackend(source: PlaybackSource): MediaItem {
        return buildMediaItem(source)
    }

    override fun mapPlaybackError(error: PlaybackException): PlaybackError {
        return mapError(error)
    }

    override fun onForceFormatRetry() {
        forceNextFormatRetry()
    }

    var isActive = false
        private set

    // Surface and Deferred Preparation
    var isSurfaceRequired: Boolean = false
    var playerView: androidx.media3.ui.PlayerView? = null
        private set

    private data class PendingPrepare(
        val request: PlaybackRequest,
        val fallbackCandidates: List<PlaybackSource>,
        val isFallbackAttempt: Boolean
    )
    private var pendingPrepare: PendingPrepare? = null

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            checkAndRunPendingPrepare()
        }
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
        override fun surfaceDestroyed(holder: SurfaceHolder) {}
    }

    private fun checkAndRunPendingPrepare() {
        val pending = pendingPrepare ?: return
        pendingPrepare = null
        prepare(pending.request, pending.fallbackCandidates, pending.isFallbackAttempt)
    }

    // Playback Profile and Tunneling
    private var activeResourceProfile: PlaybackResourceProfile? = null
    private var activeTunnelingDecision: TunnelingDecision? = null
    private var profileHistory = PlaybackProfileHistory()
    private val fallbackSafeRetriedSourceIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    // MIME Format Fallback Retry
    private var mimeRetrySequence: List<String?> = emptyList()
    private var mimeRetryIndex = 0

    private fun bufferingStallDelayMs(): Long {
        val isLive = activeRequest?.source?.metadata?.isLive == true
        return if (isLive) 30_000L else 15_000L
    }

    private fun startupTimeoutMs(): Long {
        val isLive = activeRequest?.source?.metadata?.isLive == true
        return if (isLive) 30_000L else 15_000L
    }

    /**
     * Cancels every playback watchdog/timeout so a stale timer from a previous source or backend
     * cannot fire a spurious failure after a backend switch (VLC), MIME retry, source fallback or
     * release (#5).
     */
    private fun isSessionCurrent(sessionId: Long): Boolean =
        !isReleased && sessionStore.isCurrent(sessionId)

    private fun cancelWatchdogs() {
        watchdogController.cancelAll()
    }

    private fun isDecoderInitializationOrCapabilitiesFailure(error: PlaybackException): Boolean {
        if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES
        ) {
            return true
        }
        var cause = error.cause
        while (cause != null) {
            val name = cause.javaClass.name
            if (name.contains("DecoderInitializationException") || name.contains("DecoderQueryException")) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun getFailingCodec(error: PlaybackException, currentSource: PlaybackSource?): String? {
        val isAudioFailure = error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED

        if (isAudioFailure) {
            player?.audioFormat?.sampleMimeType?.let { return it }
        } else {
            player?.videoFormat?.sampleMimeType?.let { return it }
            player?.audioFormat?.sampleMimeType?.let { return it }
        }

        var cause = error.cause
        while (cause != null) {
            if (cause.javaClass.name.contains("DecoderInitializationException")) {
                try {
                    val mimeTypeField = cause.javaClass.getField("mimeType")
                    val mimeType = mimeTypeField.get(cause) as? String
                    if (mimeType != null) return mimeType
                } catch (_: Exception) {}
            }
            cause = cause.cause
        }

        val vCodec = currentSource?.metadata?.videoCodec
        if (!vCodec.isNullOrEmpty()) return vCodec
        val aCodec = currentSource?.metadata?.audioCodec
        if (!aCodec.isNullOrEmpty()) return aCodec

        val msg = error.message?.lowercase() ?: ""
        when {
            msg.contains("hevc") || msg.contains("h265") || msg.contains("h.265") || msg.contains("video/hevc") -> return "hevc"
            msg.contains("h264") || msg.contains("h.264") || msg.contains("avc") || msg.contains("video/avc") -> return "h264"
            msg.contains("vp9") || msg.contains("video/x-vnd.on2.vp9") -> return "vp9"
            msg.contains("av1") || msg.contains("video/av01") -> return "av1"
        }

        return null
    }

    private fun playbackExceptionFrom(error: PlaybackError): PlaybackException? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is PlaybackException) return cause
            cause = cause.cause
        }
        return null
    }

    private fun recordPlaybackSuccess(source: PlaybackSource) {
        // IPTV health + channel sorting is recorded by player screens via IPTVRepository.
        if (source.type == PlaybackSourceType.IPTV) return
        val providerId = source.id.substringBefore("-", "")
            .ifBlank { source.type.name.lowercase() }
        coroutineScope.launch {
            SourceHealthRepository.recordSuccess(
                sourceId = source.safeSourceId,
                providerId = providerId,
                sourceType = source.type
            )
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            val backend = currentBackend
            if (backend is ExoPlayerBackend) {
                onBackendFirstFrameRendered(backend)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val backend = currentBackend
            val newPlayerState = when (playbackState) {
                Player.STATE_IDLE -> {
                    val currentErr = (backend as? ExoPlayerBackend)?.state?.value?.error
                    if (currentErr != null) PlayerState.FAILED else PlayerState.IDLE
                }
                Player.STATE_BUFFERING -> PlayerState.BUFFERING
                Player.STATE_READY -> if (player?.playWhenReady == true) PlayerState.PLAYING else PlayerState.READY
                Player.STATE_ENDED -> PlayerState.ENDED
                else -> PlayerState.IDLE
            }
            if (backend is ExoPlayerBackend) {
                backend.updateState(newPlayerState)
            } else {
                updateState { state -> state.copy(playerState = newPlayerState) }
            }
            if (playbackState == Player.STATE_ENDED) {
                watchMemoryManager.onEnded(currentPositionMs(), currentDurationMs())
                activeCrashRecord?.let { PlaybackCrashMarker.clearAsync(context, it.sessionId) }
            }
            if (playbackState == Player.STATE_READY) {
                watchdogController.scheduleFreezeWatchdogIfNeeded()
                watchdogController.cancelBufferingWatchdog()
                consecutiveBufferingCount = 0
                // Intentionally NOT resetting consecutiveFallbackCount here.
                // The counter is a guard against "ready-but-no-frame" failures
                // caught by the 4s first-frame watchdog. Resetting on STATE_READY
                // would let 3 such failures slip past the breaker.
                updateState { it.copy(fallbackMessage = null, isTransitioningSource = false) }
            } else if (playbackState == Player.STATE_BUFFERING) {
                if (!hasRenderedFirstFrame) {
                    consecutiveBufferingCount++
                    if (consecutiveBufferingCount >= 3) {
                        reportFailure(
                            PlaybackError.Timeout(
                                cause = Exception("Stuck in buffering loop before rendering first frame")
                            ),
                            "BUFFERING_LOOP"
                        )
                        return
                    }
                }
                watchdogController.startBufferingWatchdog()
            } else {
                watchdogController.cancelFreezeWatchdog()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val backend = currentBackend
            if (player?.playbackState == Player.STATE_READY) {
                val newPlayerState = if (playWhenReady) PlayerState.PLAYING else PlayerState.PAUSED
                if (backend is ExoPlayerBackend) {
                    backend.updateState(newPlayerState)
                } else {
                    updateState { state -> state.copy(playerState = newPlayerState) }
                }
            }
            if (!playWhenReady) {
                watchMemoryManager.onPause(currentPositionMs(), currentDurationMs())
            }
            if (playWhenReady) {
                watchdogController.scheduleFreezeWatchdogIfNeeded()
            } else {
                watchdogController.cancelFreezeWatchdog()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                watchMemoryManager.onPlaying(currentPositionMs(), currentDurationMs())
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                cancelWatchdogs()
                updateState {
                    it.copy(
                        playerState = PlayerState.BUFFERING,
                        error = null,
                        fallbackMessage = null,
                        isTransitioningSource = false
                    )
                }
                (currentBackend as? ExoPlayerBackend)?.updateState(PlayerState.BUFFERING)
                player?.apply {
                    val wasPlaying = this.playWhenReady
                    seekToDefaultPosition()
                    prepare()
                    playWhenReady = wasPlaying
                }
                watchdogController.startBufferingWatchdog()
                return
            }
            reportFailure(mapError(error), error.errorCodeName)
        }

        override fun onVolumeChanged(volume: Float) {
            val backend = currentBackend
            if (backend is ExoPlayerBackend) {
                backend.updateMuted(volume == 0f)
            }
            updateState { it.copy(volume = volume, isMuted = volume == 0f) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            val backend = currentBackend
            if (backend is ExoPlayerBackend) {
                backend.updateSpeed(playbackParameters.speed)
            }
            updateState { it.copy(playbackSpeed = playbackParameters.speed) }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            val diagnostics = com.example.calmsource.core.model.PlaybackDiagnostics(
                videoResolution = "${videoSize.width}x${videoSize.height}",
                bufferHealthMs = 0L
            )
            updateState { it.copy(diagnostics = diagnostics) }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val audioTracks = mutableListOf<PlaybackAudioTrack>()
            val subtitleTracks = mutableListOf<PlaybackSubtitleTrack>()
            var audioIdx = 0
            var subIdx = 0
            for (group in tracks.groups) {
                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val isSelected = group.isTrackSelected(i)
                            audioTracks.add(
                                PlaybackAudioTrack(
                                    id = format.id ?: "audio_${audioIdx}",
                                    name = format.label ?: format.language ?: "Track ${audioIdx + 1}",
                                    language = format.language,
                                    channels = format.channelCount,
                                    isSelected = isSelected
                                )
                            )
                            audioIdx++
                        }
                    }
                    C.TRACK_TYPE_TEXT -> {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val isSelected = group.isTrackSelected(i)
                            subtitleTracks.add(
                                PlaybackSubtitleTrack(
                                    id = format.id ?: "sub_${subIdx}",
                                    name = format.label ?: format.language ?: "Track ${subIdx + 1}",
                                    language = format.language,
                                    isSelected = isSelected,
                                    isSafeToLoad = true
                                )
                            )
                            subIdx++
                        }
                    }
                }
            }
            _audioTracks.value = audioTracks
            _subtitleTracks.value = subtitleTracks
        }
    }

    @MainThread
    fun create() {
        // Left empty because ExoPlayer is created dynamically in ensurePlayerFor
        TunnelingPreferences.warmBestEffort(context)
        FrameRateMatchingPreferences.warmBestEffort(context)
    }

    private fun ensurePlayerFor(source: PlaybackSource, profileHistory: PlaybackProfileHistory): Boolean {
        val profile = PlaybackProfileManager.profileFor(context, source, profileHistory)
        val tunnelingDecision = TunnelingPolicy.decisionFor(context, source, profile)

        val oldProfile = activeResourceProfile
        val oldTunnelingDecision = activeTunnelingDecision

        activeResourceProfile = profile
        activeTunnelingDecision = tunnelingDecision

        val headersChanged = lastSourceHeaders != source.headers
        val needsRecreate = player == null || headersChanged || (oldProfile != null && (
                oldProfile.compatibilityKey != profile.compatibilityKey ||
                // activeTunnelingDecision.compatibilityKey
                oldTunnelingDecision?.compatibilityKey != tunnelingDecision.compatibilityKey
        ))
        if (headersChanged) lastSourceHeaders = source.headers

        if (needsRecreate) {
            player?.removeListener(playerListener)
            onPlayerAboutToBeReleased?.invoke()
            player?.release()
            player = null
        }

        if (player == null) {
            val renderersFactory = DefaultRenderersFactory(context)
            TunnelingPolicy.applyToRenderersFactory(renderersFactory, tunnelingDecision)

            player = playerCreator(context, renderersFactory, source, profile).apply {
                addListener(playerListener)
            }

            profile.maxVideoHeight?.let { maxHeight ->
                val p = player ?: return@let
                p.trackSelectionParameters = p.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(maxHeight, Int.MAX_VALUE)
                    .build()
            }

            // Apply user language preferences for audio and subtitle tracks
            val prefs = UserPreferencesRepository.preferences.value
            player?.let { p ->
                p.trackSelectionParameters = p.trackSelectionParameters
                    .buildUpon()
                    .setPreferredAudioLanguage(prefs.primaryLanguage.lowercase(Locale.ROOT))
                    .setPreferredTextLanguage(prefs.subtitleLanguage.lowercase(Locale.ROOT))
                    .build()
            }

            playerView?.player = player

            lowMemoryModeSink(profile.lowMemoryMode)
            return true
        }

        lowMemoryModeSink(profile.lowMemoryMode)
        return false
    }

    @MainThread
    fun setPlayerView(view: androidx.media3.ui.PlayerView?) {
        if (playerView != view) {
            playerView?.player = null
        }
        val oldSurfaceView = playerView?.videoSurfaceView as? SurfaceView
        oldSurfaceView?.holder?.removeCallback(surfaceCallback)

        playerView = view
        currentBackend?.playerView = view
        if (view != null) {
            if (player != null && view.player != player) {
                view.player = player
            }
            val newSurfaceView = view.videoSurfaceView as? SurfaceView
            newSurfaceView?.holder?.addCallback(surfaceCallback)

            if (pendingPrepare != null && newSurfaceView?.holder?.surface?.isValid == true) {
                checkAndRunPendingPrepare()
            }
        }
    }

    suspend fun consumeBestMatchSkipSet(candidates: List<PlaybackSource>): Set<String> {
        val recovery = PlaybackCrashMarker.consumeRecoveryMarker(context) ?: return emptySet()
        val skipIds = mutableSetOf<String>()
        for (candidate in candidates) {
            if (PlaybackCrashMarker.matchesRecovery(candidate, recovery)) {
                skipIds.add(candidate.id)
            }
        }
        return skipIds
    }

    @MainThread
    fun prepareBest(
        request: PlaybackRequest,
        fallbackCandidates: List<PlaybackSource> = emptyList(),
        playBest: Boolean = true
    ) {
        prepare(
            request = request.copy(playWhenReady = playBest),
            fallbackCandidates = fallbackCandidates,
            isFallbackAttempt = false
        )
    }

    @MainThread
    fun prepare(
        request: PlaybackRequest,
        fallbackCandidates: List<PlaybackSource> = emptyList(),
        isFallbackAttempt: Boolean = false,
        skipStreamRace: Boolean = false
    ) {
        isReleased = false
        cancelWatchdogs()
        val allCandidates = listOf(request.source) + fallbackCandidates
        val deviceProfile = PlaybackProfileManager.deviceProfile(context)
        val lowMemoryMode = deviceProfile.lowRamDevice ||
            deviceProfile.memoryClassMb <= PlaybackProfileManager.LOW_MEMORY_CLASS_MB
        val canRace = allCandidates.size >= 2 && !isFallbackAttempt && !skipStreamRace && !lowMemoryMode
        val useFullRacing = canRace && StreamRacePreferences.enableStreamRacing
        val useLiteRacing = canRace && !useFullRacing
        val willRace = useFullRacing || useLiteRacing
        val sessionId = sessionStore.begin(
            request = request,
            candidates = allCandidates,
            phase = if (willRace) SessionPhase.Racing else SessionPhase.Preparing,
        )
        if (willRace) {
            coroutineScope.launch(Dispatchers.Main) {
                if (!isSessionCurrent(sessionId)) return@launch
                updateState { it.copy(playerState = PlayerState.PREPARING) }
                val raceCandidates = if (useLiteRacing) allCandidates.take(3) else allCandidates
                val raceRequest = StreamRaceRequest(
                    candidates = raceCandidates,
                    lowMemoryMode = lowMemoryMode,
                    enabled = true,
                    maxProbes = if (useLiteRacing) 3 else StreamRaceManager.MAX_PROBES
                )
                val result = streamRaceFactory.race(context, raceRequest)
                // A newer prepare() (fast zap), fallback or release() ran while the race was in
                // flight — drop this winner so we don't prepare the wrong stream (#4).
                if (!isSessionCurrent(sessionId)) return@launch
                if (result is StreamRaceResult.Winner) {
                    val winner = result.source
                    publishSessionDiagnostics(
                        eventKind = "race_winner",
                        eventDetail = "Stream race selected candidate",
                        eventSourceId = winner.id,
                    )
                    val remainingCandidates = allCandidates.filter { it.id != winner.id }
                    prepare(
                        request = request.copy(source = winner),
                        fallbackCandidates = remainingCandidates,
                        isFallbackAttempt = false,
                        skipStreamRace = true
                    )
                } else {
                    prepare(
                        request = request,
                        fallbackCandidates = fallbackCandidates,
                        isFallbackAttempt = false,
                        skipStreamRace = true
                    )
                }
            }
            return
        }

        isActive = true
        hasRenderedFirstFrame = false

        val previousRequest = activeRequest
        val sameTrackingItem = previousRequest != null &&
            isSameTrackingItem(previousRequest, request)
        val effectiveRequest = if (sameTrackingItem) {
            request.copy(
                startPositionMs = maxOf(
                    previousRequest.startPositionMs,
                    request.startPositionMs
                ),
                userMemoryReference = request.userMemoryReference
                    ?: previousRequest.userMemoryReference
            )
        } else {
            request
        }
        val source = effectiveRequest.source
        val previousSource = previousRequest?.source
        val sourceChanged = previousSource == null || previousSource.id != source.id || previousSource.rawUrl != source.rawUrl

        if (!isFallbackAttempt && sourceChanged) {
            watchMemoryManager.onRelease(currentPositionMs(), currentDurationMs())
            watchMemoryManager.begin(effectiveRequest)
        } else if (previousRequest == null) {
            watchMemoryManager.begin(effectiveRequest)
        }
        activeRequest = effectiveRequest

        consecutiveBufferingCount = 0
        if (!isFallbackAttempt) {
            updateState { it.copy(isTransitioningSource = false) }
        }

        if (sourceChanged) {
            watchdogController.onSourceChanged()
        }
        if (!isFallbackAttempt) {
            consecutiveFallbackCount = 0
            isSurfaceRequired = false
            currentBackend?.release()
            currentBackend = null
            profileHistory = PlaybackProfileHistory()
            fallbackSafeRetriedSourceIds.clear()
            vlcAttemptedSourceIds.clear()
            fallbackManager.reset(fallbackCandidates)
            _fallbackPromptState.value = false
            _audioTracks.value = emptyList()
            _subtitleTracks.value = emptyList()
            fallbackJob?.cancel()
            fallbackJob = null
        }

        if (!isFallbackAttempt || sourceChanged) {
            mimeRetrySequence = StreamFormatFallback.buildMimeRetrySequence(
                source.rawUrl,
                source.metadata?.containerFormat
            )
            mimeRetryIndex = 0
        }

        // 1. Surface validation (deferred preparation if surface required but invalid)
        if (isSurfaceRequired) {
            val surfaceView = playerView?.videoSurfaceView as? SurfaceView
            val surfaceHolder = surfaceView?.holder
            val hasValidSurface = surfaceHolder?.surface?.isValid == true
            if (!hasValidSurface) {
                pendingPrepare = PendingPrepare(request, fallbackCandidates, isFallbackAttempt)
                surfaceHolder?.addCallback(surfaceCallback)
                return
            }
        }
        pendingPrepare = null

        publishSessionDiagnostics(
            eventKind = if (isFallbackAttempt) "fallback_prepare" else "prepare",
            eventDetail = if (isFallbackAttempt) "Preparing fallback source" else "Preparing source",
            eventSourceId = source.id,
        )

        // 2. Write playback crash marker
        activeCrashRecord = PlaybackCrashMarker.markStartedBestEffort(context, source)

        // 3. Trim image cache
        ImageCacheController.trimForPlayback(context)

        // 4. Ensure Player and active profile
        val wasPlayerCreated = ensurePlayerFor(source, profileHistory)

        val mediaItem = try {
            buildMediaItem(source).also { activeMediaItem = it }
        } catch (e: PlaybackException) {
            activeMediaItem = null
            if (wasPlayerCreated) {
                player?.removeListener(playerListener)
                onPlayerAboutToBeReleased?.invoke()
                player?.release()
                player = null
            }
            updateState { it.copy(source = source) }
            handleFailure(mapError(e), e.errorCodeName)
            return
        } catch (e: SecurityException) {
            activeMediaItem = null
            if (wasPlayerCreated) {
                player?.removeListener(playerListener)
                onPlayerAboutToBeReleased?.invoke()
                player?.release()
                player = null
            }
            updateState { it.copy(source = source) }
            handleFailure(PlaybackError.PermissionRequired(cause = e, message = e.message ?: "Unsafe scheme rejected"), "SECURITY_VIOLATION")
            return
        } catch (e: Exception) {
            activeMediaItem = null
            if (wasPlayerCreated) {
                player?.removeListener(playerListener)
                onPlayerAboutToBeReleased?.invoke()
                player?.release()
                player = null
            }
            updateState { it.copy(source = source) }
            val sanitizedCause = Exception(PlaybackSanitizer.sanitize(e.message))
            handleFailure(PlaybackError.SourceUnavailable(cause = sanitizedCause), "SOURCE_UNAVAILABLE")
            return
        }

        val resumePosition = if (uiState.value.source?.id == source.id && progressState.value.currentPositionMs > 0) {
            progressState.value.currentPositionMs.coerceAtLeast(effectiveRequest.startPositionMs)
        } else {
            effectiveRequest.startPositionMs
        }

        updateState { it.copy(source = source, error = null) }

        val backend = currentBackend as? ExoPlayerBackend ?: ExoPlayerBackend(this).also {
            currentBackend?.release()
            currentBackend = it
            isSurfaceRequired = false
            observeBackendState(it)
        }
        backend.prepare(context, source, resumePosition)
        if (activeRequest?.playWhenReady != false) play()
        startProgressUpdates()
        watchdogController.startBufferingWatchdog()

        if (stateTrackingJob == null || stateTrackingJob?.isActive != true) {
            stateTrackingJob = coroutineScope.launch(Dispatchers.Main) {
                uiState.collect { state ->
                    watchdogController.onUiStateChanged(state.playerState)
                }
            }
        }
    }

    @MainThread
    private fun abandonAudioFocus() {
        audioManager.abandonAudioFocus()
    }

    fun play() {
        audioManager.requestFocusAndRegister()
        currentBackend?.play()
    }

    @MainThread
    fun pause() = pauseInternal(abandonFocus = true)

    /**
     * Pauses playback. When [abandonFocus] is false we keep our audio-focus request registered so
     * the framework still delivers AUDIOFOCUS_GAIN after a transient interruption, enabling
     * auto-resume (bug #29).
     */
    @MainThread
    private fun pauseInternal(abandonFocus: Boolean) {
        currentBackend?.pause()
        if (abandonFocus) abandonAudioFocus()
        watchMemoryManager.onPause(currentPositionMs(), currentDurationMs())
    }

    @MainThread
    fun seekTo(positionMs: Long) {
        currentBackend?.seekTo(positionMs)
    }

    @MainThread
    fun selectAudioTrack(trackId: String) {
        // Route through the active backend so this is not a silent no-op when VLC is active and the
        // ExoPlayer `player` is null (#11).
        currentBackend?.selectAudioTrack(trackId)
        refreshTracksFromBackend(currentBackend)
    }

    @MainThread
    fun selectSubtitleTrack(trackId: String) {
        currentBackend?.selectSubtitleTrack(trackId)
        refreshTracksFromBackend(currentBackend)
    }

    @MainThread
    fun disableSubtitles() {
        currentBackend?.disableSubtitles()
        refreshTracksFromBackend(currentBackend)
    }

    /**
     * Pulls the current track lists from a non-Exo backend (VLC) into the exposed StateFlows so the
     * audio/subtitle pickers reflect VLC's tracks. The ExoPlayer backend keeps publishing tracks
     * via [Player.Listener.onTracksChanged], so this is a no-op for it.
     */
    private fun refreshTracksFromBackend(backend: PlayerBackend?) {
        if (backend == null || backend is ExoPlayerBackend) return
        val audio = runCatching { backend.availableAudioTracks() }.getOrDefault(emptyList())
        val subtitles = runCatching { backend.availableSubtitleTracks() }.getOrDefault(emptyList())
        if (audio.isNotEmpty() || _audioTracks.value.isNotEmpty()) _audioTracks.value = audio
        if (subtitles.isNotEmpty() || _subtitleTracks.value.isNotEmpty()) _subtitleTracks.value = subtitles
    }

    private fun onBackendFirstFrameRendered(backend: PlayerBackend) {
        hasRenderedFirstFrame = true
        watchdogController.cancelFreezeWatchdog()
        watchdogController.cancelBufferingWatchdog()
        consecutiveFallbackCount = 0
        consecutiveBufferingCount = 0
        refreshTracksFromBackend(backend)
        publishSessionDiagnostics(
            eventKind = "stable_playback",
            eventDetail = "First frame rendered",
            eventSourceId = uiState.value.source?.id,
        )
        updateState { it.copy(fallbackMessage = null, isTransitioningSource = false) }
    }

    @MainThread
    fun stop() {
        stopEngine(resetCircuitBreaker = true)
    }

    /**
     * Stops the active engine without tearing down [release] state. Internal recovery paths use
     * [reportFailure] instead of [stop] so the consecutive-failure breaker is preserved (#1).
     */
    @MainThread
    internal fun stopEngine(resetCircuitBreaker: Boolean = false) {
        watchMemoryManager.onPause(currentPositionMs(), currentDurationMs())
        currentBackend?.stop()
        abandonAudioFocus()
        stopProgressUpdates()
        watchdogController.cancelBufferingWatchdog()
        if (resetCircuitBreaker) {
            consecutiveFallbackCount = 0
        }
        activeCrashRecord?.let { PlaybackCrashMarker.clearAsync(context, it.sessionId) }
        scheduleImageCacheRestore()
    }

    @MainThread
    fun release() {
        if (isReleased) return
        isReleased = true
        // Bump the session id so any in-flight stream race aborts instead of resurrecting a
        // released session (#4), drop any deferred prepare so a re-attached surface can't run a
        // prepare for this released session (#6), and clear the failure-dedup guard.
        sessionStore.invalidate()
        pendingPrepare = null
        lastHandledBackendError = null
        onPlayerAboutToBeReleased?.invoke()
        isActive = false
        watchMemoryManager.onRelease(currentPositionMs(), currentDurationMs())
        stopProgressUpdates()
        currentBackend?.release()
        currentBackend = null
        fallbackJob?.cancel()
        fallbackJob = null
        stateTrackingJob?.cancel()
        stateTrackingJob = null
        backendStateCollectionJob?.cancel()
        backendStateCollectionJob = null
        watchdogController.cancelAll()
        consecutiveFallbackCount = 0
        _fallbackPromptState.value = false
        activeRequest = null
        _audioTracks.value = emptyList()
        _subtitleTracks.value = emptyList()
        activeMediaItem = null

        audioManager.release()

        playerView?.player = null
        setPlayerView(null)

        activeCrashRecord?.let { PlaybackCrashMarker.clearAsync(context, it.sessionId) }
        scheduleImageCacheRestore()

        updateState { it.copy(playerState = PlayerState.IDLE) }
    }

    private fun scheduleImageCacheRestore() {
        ImageCacheController.scheduleRestoreAfterPlayback(context)
    }

    fun onUserSelectTryNextBest() {
        _fallbackPromptState.value = false
        val originalError = uiState.value.error
        if (originalError == null) {
            runCatching { Log.w("PlaybackManager", "User triggered fallback but no error was present; using Unknown error as placeholder") }
        }
        executeFallback(originalError ?: PlaybackError.Unknown())
    }

    fun onUserSelectChooseAnother() {
        _fallbackPromptState.value = false
    }

    @MainThread
    fun clearError() {
        updateState { it.copy(error = null) }
    }

    @MainThread
    fun clearFallbackMessage() {
        updateState { it.copy(fallbackMessage = null) }
    }

    @MainThread
    fun setError(error: PlaybackError) {
        updateState { it.copy(playerState = PlayerState.FAILED, error = error) }
    }

    /**
     * Single entry point for failures detected by watchdogs/timeouts (startup timeout, buffering
     * loop, buffering stall, video freeze). Unlike the old `stop() + handleFailure()` pattern it
     * does NOT reset the circuit breaker (#1) and it pushes the error onto the active backend so
     * the engine's own state emissions cannot overwrite FAILED+error with a null-error
     * BUFFERING/IDLE (#2). For ExoPlayer the backend-state collector mirrors FAILED+error to the UI
     * and routes to [handleFailure] exactly once.
     */
    private fun reportFailure(error: PlaybackError, rawErrorCode: String) {
        cancelWatchdogs()
        val backend = currentBackend
        if (backend is ExoPlayerBackend) {
            backend.updateError(error, rawErrorCode)
            if (error !is PlaybackError.DecoderError) {
                backend.stop()
            }
        } else {
            handleFailure(error, rawErrorCode)
        }
    }

    private fun handleFailure(error: PlaybackError, rawErrorCode: String) {
        if (handlingFailure) {
            // Queue concurrent errors instead of silently dropping them.
            // Recovery actions (e.g. switchToVlcBackend) can trigger secondary
            // errors that would otherwise be lost.
            pendingFailure = error to rawErrorCode
            return
        }
        handlingFailure = true
        try {
            val currentSource = uiState.value.source
            if (currentSource == null) {
                applyRecoveryDecision(
                    PlaybackRecoveryEngine.decideWithoutSource(error),
                    rawErrorCode = rawErrorCode,
                    currentSource = null
                )
                return
            }

            // Codec blocklisting runs after recovery decides VLC is not the next step.
            val policy = FallbackPreferences.policy
            val recoveryContext = RecoveryContext(
                error = error,
                currentSource = currentSource,
                policy = policy,
                consecutiveFailureCount = consecutiveFallbackCount,
                mimeRetryIndex = mimeRetryIndex,
                mimeRetrySequenceSize = mimeRetrySequence.size,
                vlcAlreadyAttemptedForSource = currentSource.id in vlcAttemptedSourceIds,
                remainingCandidates = fallbackManager.getRemainingCandidates(),
                fallbackAllowed = fallbackManager.isFallbackAllowed(policy),
                safeProfileRetryEnabled = FallbackPreferences.enableFallbackSafeProfileOnDecoderError,
                safeProfileAlreadyRetried = fallbackSafeRetriedSourceIds.contains(currentSource.id),
                vlcRuntimeAvailable = VlcPlayerBackend.isAvailable,
                allowVlcInThisEnvironment = allowVlcInRecovery
                    ?: !context.javaClass.name.contains("mock", ignoreCase = true),
            )
            val decision = PlaybackRecoveryEngine.decide(recoveryContext)
            if (error is PlaybackError.DecoderError &&
                decision.action !is RecoveryAction.SwitchToVlc
            ) {
                val playbackEx = playbackExceptionFrom(error)
                if (playbackEx != null) {
                    val failingCodec = getFailingCodec(playbackEx, currentSource)
                    if (failingCodec != null) {
                        runCatching { Log.i("PlaybackManager", "Pruning candidates with codec: $failingCodec") }
                        fallbackManager.pruneCandidatesByCodec(failingCodec)
                    }
                }
            }
            applyRecoveryDecision(
                decision,
                rawErrorCode = rawErrorCode,
                currentSource = currentSource
            )
        } finally {
            handlingFailure = false
            pendingFailure?.let { (pendingError, pendingCode) ->
                pendingFailure = null
                handleFailure(pendingError, pendingCode)
            }
        }
    }

    private fun applyRecoveryDecision(
        decision: RecoveryDecision,
        rawErrorCode: String,
        currentSource: PlaybackSource?,
    ) {
        if (decision.recordTunnelingFailure) {
            recordTunnelingFailureBestEffort()
        }
        if (decision.recordSourceFailure && currentSource != null) {
            recordSourceFailureBestEffort(currentSource, decision.action, rawErrorCode)
            fallbackManager.markFailed(currentSource.id)
            consecutiveFallbackCount++
        }

        val (eventKind, eventDetail) = decision.action.sanitizedEvent()
        publishSessionDiagnostics(
            eventKind = eventKind,
            eventDetail = eventDetail,
            eventSourceId = currentSource?.id,
        )

        when (val action = decision.action) {
            RecoveryAction.TryMimeRetry -> forceNextFormatRetry()
            RecoveryAction.SwitchToVlc -> {
                val src = currentSource ?: return
                switchToVlcBackend(src)
            }
            RecoveryAction.RetrySafeProfile -> {
                val src = currentSource ?: return
                retryWithSafeProfile(src)
            }
            is RecoveryAction.ShowFallbackPrompt -> {
                updateState {
                    it.copy(
                        playerState = PlayerState.FAILED,
                        error = action.error,
                        isTransitioningSource = false
                    )
                }
                _fallbackPromptState.value = true
            }
            is RecoveryAction.AutoFallback -> {
                updateState { it.copy(fallbackMessage = action.message) }
                executeFallback(action.error)
            }
            is RecoveryAction.Terminal -> {
                updateState {
                    it.copy(
                        playerState = PlayerState.FAILED,
                        error = action.error,
                        isTerminal = true,
                        isTransitioningSource = false
                    )
                }
                stopEngine(resetCircuitBreaker = false)
            }
            is RecoveryAction.FailInPlace -> {
                updateState {
                    it.copy(
                        playerState = PlayerState.FAILED,
                        error = action.error,
                        isTransitioningSource = false
                    )
                }
            }
            is RecoveryAction.FailWithoutSource -> {
                updateState {
                    it.copy(
                        playerState = PlayerState.FAILED,
                        error = action.error,
                        isTransitioningSource = false
                    )
                }
            }
        }
    }

    private fun recordTunnelingFailureBestEffort() {
        val key = activeTunnelingDecision?.key
        if (key != null && activeTunnelingDecision?.enabled == true) {
            TunnelingBlacklist.recordFailureBestEffort(context, key)
        }
    }

    private fun recordSourceFailureBestEffort(
        currentSource: PlaybackSource,
        action: RecoveryAction,
        rawErrorCode: String,
    ) {
        val providerId = currentSource.id.substringBefore("-", "")
            .ifBlank { currentSource.type.name.lowercase() }
        coroutineScope.launch {
            val underlyingError = when (action) {
                is RecoveryAction.ShowFallbackPrompt -> action.error
                is RecoveryAction.AutoFallback -> action.error
                is RecoveryAction.Terminal -> action.error
                is RecoveryAction.FailInPlace -> action.error
                else -> null
            }
            if (underlyingError is PlaybackError.Timeout) {
                SourceHealthRepository.recordSignal(
                    sourceId = currentSource.safeSourceId,
                    providerId = providerId,
                    sourceType = currentSource.type,
                    signal = com.example.calmsource.core.model.SourceHealthSignal.PLAYBACK_TIMEOUT
                )
            } else {
                SourceHealthRepository.recordFailure(
                    sourceId = currentSource.safeSourceId,
                    providerId = providerId,
                    sourceType = currentSource.type,
                    errorCategory = rawErrorCode
                )
            }
        }
    }

    private fun switchToVlcBackend(currentSource: PlaybackSource) {
        val backend = currentBackend
        cancelWatchdogs()
        vlcAttemptedSourceIds.add(currentSource.id)
        runCatching { Log.i("PlaybackManager", "ExoPlayer failed; falling back to VLC for ${currentSource.id}.") }
        if (backend is ExoPlayerBackend) {
            backend.releaseSafely(skipStop = true)
        } else {
            backend?.release()
        }
        player = null
        updateState {
            it.copy(
                playerState = PlayerState.PREPARING,
                error = null,
                fallbackMessage = null,
                isTransitioningSource = false,
            )
        }
        val vlcBackend = vlcBackendFactory()
        vlcBackend.playerView = this@PlaybackManager.playerView
        currentBackend = vlcBackend
        isSurfaceRequired = vlcBackend.isSurfaceRequired
        observeBackendState(vlcBackend)
        vlcBackend.prepare(context, currentSource, currentPositionMs())
        audioManager.requestFocusAndRegister()
        if (activeRequest?.playWhenReady != false) {
            vlcBackend.play()
        }
        startProgressUpdates()
        watchdogController.startBufferingWatchdog()
    }

    private fun retryWithSafeProfile(currentSource: PlaybackSource) {
        // PlaybackProfileKind.FALLBACK_SAFE_PROFILE for safer fallback parameters on decoder error.
        fallbackSafeRetriedSourceIds.add(currentSource.id)
        profileHistory = PlaybackProfileHistory(useFallbackSafeProfile = true)
        prepare(
            request = activeRequest ?: PlaybackRequest(source = currentSource),
            fallbackCandidates = emptyList(),
            isFallbackAttempt = true
        )
    }

    private fun executeFallback(originalError: PlaybackError) {
        val sessionId = sessionStore.currentId()
        updateState { it.copy(isTransitioningSource = true) }
        fallbackJob?.cancel()
        fallbackJob = coroutineScope.launch(Dispatchers.Main) {
            if (!isSessionCurrent(sessionId)) {
                updateState { it.copy(isTransitioningSource = false) }
                return@launch
            }
            val nextCandidate = fallbackManager.selectNextBestCandidate()
            if (!isSessionCurrent(sessionId)) {
                updateState { it.copy(isTransitioningSource = false) }
                return@launch
            }
            if (nextCandidate != null) {
                if (!isActive) {
                    updateState { it.copy(isTransitioningSource = false) }
                    return@launch
                }
                fallbackManager.incrementAttempts()
                publishSessionDiagnostics(
                    eventKind = "fallback_advance",
                    eventDetail = "Advancing to next candidate",
                    eventSourceId = nextCandidate.id,
                )
                val request = (activeRequest ?: PlaybackRequest(source = nextCandidate))
                    .forFallback(nextCandidate, currentPositionMs())
                prepare(
                    request = request,
                    fallbackCandidates = emptyList(),
                    isFallbackAttempt = true
                )
            } else {
                val finalError = if (originalError is PlaybackError.UnsupportedFormat) {
                    PlaybackError.UnsupportedFormat(
                        message = originalError.message,
                        cause = originalError.cause,
                        retryableSources = fallbackManager.getRemainingCandidates()
                    )
                } else {
                    originalError
                }
                updateState { it.copy(playerState = PlayerState.FAILED, error = finalError, isTransitioningSource = false) }
            }
        }
    }

    fun forceNextFormatRetry() {
        if (mimeRetryIndex < mimeRetrySequence.size) {
            mimeRetryIndex++
            val request = activeRequest
            if (request != null) {
                prepare(request, isFallbackAttempt = true)
            }
        }
    }

    @Throws(Exception::class)
    private fun buildMediaItem(source: PlaybackSource): MediaItem {
        val uri = source.rawUrl
        if (uri.startsWith("xtream://", ignoreCase = true)) {
            throw IllegalArgumentException("Cannot play unresolved Xtream URL")
        }
        if (uri.isBlank()) {
            throw IllegalArgumentException("URI is empty for source ${source.id}")
        }

        val schemeMatch = URI_SCHEME_REGEX.find(uri)
        val scheme = schemeMatch?.groupValues?.get(1)?.lowercase()
        val unsafeSchemes = listOf("file", "javascript", "data", "content", "ftp")
        if (scheme in unsafeSchemes) {
            throw SecurityException("Unsafe protocol scheme '$scheme' is blocked.")
        }

        if (scheme == "http") {
            val allowCleartext = com.example.calmsource.core.database.repository.UserPreferencesRepository.preferences.value.allowCleartextUserSources
            if (!allowCleartext && !source.allowInsecureHttp) {
                throw PlaybackException(
                    "Cleartext HTTP traffic is blocked by user preference",
                    null,
                    PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED
                )
            }
        }

        return try {
            val builder = MediaItem.Builder()
                .setUri(uri)
                .setMediaId(source.id)

            val currentMime = if (mimeRetryIndex > 0 && (mimeRetryIndex - 1) in mimeRetrySequence.indices) {
                mimeRetrySequence[mimeRetryIndex - 1]
            } else {
                null
            }

            if (currentMime != null) {
                builder.setMimeType(currentMime)
            } else {
                val cleanPath = uri.substringBefore('?').substringBefore('#').lowercase()
                val containerHint = source.metadata?.containerFormat
                    ?.trim()
                    ?.lowercase()
                    ?.removePrefix(".")
                when {
                    cleanPath.endsWith(".m3u8") -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
                    cleanPath.endsWith(".mpd") -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
                    cleanPath.endsWith(".mkv") -> builder.setMimeType(MimeTypes.VIDEO_MATROSKA)
                    cleanPath.endsWith(".webm") -> builder.setMimeType(MimeTypes.VIDEO_WEBM)
                    cleanPath.endsWith(".ts") -> builder.setMimeType(MimeTypes.VIDEO_MP2T)
                    cleanPath.endsWith(".mp4") -> builder.setMimeType(MimeTypes.VIDEO_MP4)
                    else -> StreamFormatFallback.mimeTypeForContainerHint(containerHint.orEmpty())?.let {
                        builder.setMimeType(it)
                    }
                }
            }

            val liveConfig = PlaybackProfileManager.liveConfiguration(activeResourceProfile ?: return builder.build())
            if (liveConfig != null) {
                builder.setLiveConfiguration(liveConfig)
            }

            source.drmConfiguration?.let { drm ->
                builder.setDrmConfiguration(
                    androidx.media3.common.MediaItem.DrmConfiguration.Builder(java.util.UUID.fromString(drm.scheme.uuid))
                        .setLicenseUri(drm.licenseUri)
                        .setLicenseRequestHeaders(drm.keyRequestHeaders)
                        .build()
                )
            }

            builder.build()
        } catch (e: Exception) {
            throw IllegalArgumentException("Unparseable URI for source ${source.id}")
        }
    }

    private fun mapError(error: PlaybackException): PlaybackError {
        val sanitizedMessage = "ExoPlayer error: ${error.errorCodeName}"
        val causeChain = when {
            error.cause != null -> Exception(sanitizedMessage, PlaybackSanitizer.sanitizeCause(error.cause!!))
            else -> Exception(sanitizedMessage)
        }
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> PlaybackError.Network(cause = causeChain)
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackError.Timeout(cause = causeChain)
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> PlaybackError.DecoderError(
                cause = Exception(sanitizedMessage, PlaybackSanitizer.sanitizeCause(error))
            )
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> PlaybackError.SourceUnavailable(cause = causeChain)
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> PlaybackError.ServerRefused(cause = causeChain)
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> PlaybackError.UnsupportedFormat(cause = causeChain)
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> PlaybackError.PermissionRequired(cause = causeChain)
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> PlaybackError.CleartextNotPermitted(cause = causeChain)
            PlaybackException.ERROR_CODE_REMOTE_ERROR -> PlaybackError.ServerRefused(cause = causeChain)
            PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
            PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
            PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION -> PlaybackError.Drm(cause = causeChain)
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> PlaybackError.DecoderError(
                cause = Exception(sanitizedMessage, PlaybackSanitizer.sanitizeCause(error))
            )
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                PlaybackError.Timeout(cause = Exception("Behind live window"))
            }
            else -> PlaybackError.Unknown(cause = causeChain)
        }
    }

    private fun startProgressUpdates() {
        if (!progressUpdatesEnabled) return
        progressJob?.cancel()
        progressJob = coroutineScope.launch(Dispatchers.Main) {
            while (true) {
                val backend = currentBackend
                if (backend == null) {
                    progressJob?.cancel()
                    break
                }
                _progressState.update {
                    it.copy(
                        currentPositionMs = backend.currentPositionMs().coerceAtLeast(0),
                        durationMs = backend.durationMs().coerceAtLeast(0),
                        bufferedPositionMs = backend.bufferedPositionMs().coerceAtLeast(0),
                    )
                }
                watchMemoryManager.onPeriodicCheckpoint(currentPositionMs(), currentDurationMs())
                delay(1000)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun currentPositionMs(): Long {
        return currentBackend?.currentPositionMs()
            ?.takeIf { it >= 0L }
            ?: progressState.value.currentPositionMs.coerceAtLeast(0L)
    }

    private fun currentDurationMs(): Long {
        return currentBackend?.durationMs()
            ?.takeIf { it > 0L }
            ?: progressState.value.durationMs.coerceAtLeast(0L)
    }

    private fun isSameTrackingItem(
        previous: PlaybackRequest,
        next: PlaybackRequest
    ): Boolean {
        val previousReference = previous.userMemoryReference
        val nextReference = next.userMemoryReference
        return if (previousReference != null || nextReference != null) {
            previousReference?.itemKey == nextReference?.itemKey &&
                previousReference?.contentType == nextReference?.contentType
        } else {
            previous.source.id == next.source.id
        }
    }

    private fun updateState(update: (PlayerUiState) -> PlayerUiState) {
        _uiState.update { oldState ->
            val newState = update(oldState)
            if (newState.playerState != oldState.playerState) {
                resourceStateSink(newState.playerState)
            }
            newState
        }
    }

    private fun activeBackendLabel(): String? = when (currentBackend) {
        is VlcPlayerBackend -> "VLC"
        null -> null
        else -> "ExoPlayer"
    }

    private fun publishSessionDiagnostics(
        eventKind: String? = null,
        eventDetail: String? = null,
        eventSourceId: String? = uiState.value.source?.id,
    ) {
        if (eventKind != null && eventDetail != null) {
            PlaybackDiagnosticsRecorder.record(eventKind, eventDetail, eventSourceId)
        }
        val session = sessionStore.active()
        val state = uiState.value
        val snapshot = PlaybackSessionDiagnostics(
            sessionId = session?.id ?: sessionStore.currentId(),
            phase = inferDisplayPhase(
                state.playerState,
                state.isTransitioningSource,
                session?.phase,
            ),
            activeBackend = activeBackendLabel(),
            consecutiveFailures = consecutiveFallbackCount,
            fallbackPolicy = FallbackPreferences.policy.name,
            sourceId = eventSourceId ?: activeRequest?.source?.id ?: state.source?.id,
            recentEvents = PlaybackDiagnosticsRecorder.recentEvents(),
        )
        PlaybackDiagnosticsRecorder.updateSnapshot(snapshot)
        updateState { it.copy(sessionDiagnostics = snapshot) }
    }

    private fun observeBackendState(backend: PlayerBackend) {
        backendStateCollectionJob?.cancel()
        lastHandledBackendError = null
        backendStateCollectionJob = coroutineScope.launch(Dispatchers.Main) {
            backend.state.collect { backendState ->
                updateState { uiState ->
                    uiState.copy(
                        playerState = backendState.playerState,
                        error = backendState.error,
                        playbackSpeed = backendState.playbackSpeed,
                        isMuted = backendState.isMuted
                    )
                }
                if (backendState.playerState == PlayerState.READY || backendState.playerState == PlayerState.PLAYING) {
                    watchdogController.cancelBufferingWatchdog()
                    consecutiveBufferingCount = 0
                }
                if (backend !is ExoPlayerBackend) {
                    if (backendState.playerState == PlayerState.BUFFERING && !hasRenderedFirstFrame) {
                        consecutiveBufferingCount++
                        if (consecutiveBufferingCount >= 3) {
                            reportFailure(
                                PlaybackError.Timeout(
                                    cause = Exception("Stuck in buffering loop before rendering first frame")
                                ),
                                "BUFFERING_LOOP"
                            )
                            return@collect
                        }
                        watchdogController.startBufferingWatchdog()
                    } else if (backendState.playerState == PlayerState.PLAYING ||
                        backendState.playerState == PlayerState.PAUSED
                    ) {
                        watchdogController.cancelFreezeWatchdog()
                    }
                    if (backendState.firstFrameRendered && !hasRenderedFirstFrame) {
                        // VLC signals the first frame via Vout rather than Exo's onRenderedFirstFrame (#9).
                        onBackendFirstFrameRendered(backend)
                    } else if (backendState.playerState == PlayerState.PLAYING && hasRenderedFirstFrame) {
                        // Refresh VLC track lists once playback is underway (#11).
                        refreshTracksFromBackend(backend)
                    }
                }
                if (backendState.error == null) {
                    lastHandledBackendError = null
                } else if (
                    backendState.playerState == PlayerState.FAILED ||
                    backendState.rawErrorCode != null
                ) {
                    if (currentBackend === backend && backendState.error !== lastHandledBackendError) {
                        // Dedup: the same failure can be re-emitted (e.g. stop() → IDLE mapped back
                        // to FAILED while the backend error is still set). Handle it once so the
                        // breaker isn't double-counted and the fallback chain isn't double-advanced.
                        lastHandledBackendError = backendState.error
                        val rawErrorCode = backendState.rawErrorCode ?: when (backendState.error) {
                            is PlaybackError.Network -> "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"
                            is PlaybackError.Timeout -> "PLAYBACK_TIMEOUT"
                            is PlaybackError.DecoderError -> "ERROR_CODE_DECODER_INIT_FAILED"
                            is PlaybackError.UnsupportedFormat -> "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED"
                            is PlaybackError.SourceUnavailable -> "ERROR_CODE_IO_UNSPECIFIED"
                            is PlaybackError.PermissionRequired -> "ERROR_CODE_IO_NO_PERMISSION"
                            is PlaybackError.CleartextNotPermitted -> "ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED"
                            is PlaybackError.ServerRefused -> "ERROR_CODE_IO_BAD_HTTP_STATUS"
                            is PlaybackError.Drm -> "ERROR_CODE_DRM_SYSTEM_ERROR"
                            else -> "UNKNOWN"
                        }
                        handleFailure(backendState.error, rawErrorCode)
                    }
                }
            }
        }
    }

    companion object {
        private val URL_REGEX = PlaybackSanitizer.URL_REGEX
        private val XTREAM_CREDENTIAL_PATH_REGEX = PlaybackSanitizer.XTREAM_CREDENTIAL_PATH_REGEX
        private val URI_SCHEME_REGEX = "^([a-zA-Z][a-zA-Z0-9+.-]*):".toRegex()
    }
}
