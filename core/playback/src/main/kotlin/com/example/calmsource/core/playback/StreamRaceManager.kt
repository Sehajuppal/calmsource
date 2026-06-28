package com.example.calmsource.core.playback

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.util.Log
import androidx.annotation.OptIn
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.calmsource.core.model.PlaybackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs

private val Context.streamRacingStore by preferencesDataStore(
    name = "playback_stream_racing"
)

/**
 * Default-off control for Mission 27 stream racing.
 *
 * The in-memory [enableStreamRacing] flag is the hot-path source of truth read by the
 * racing contract; it is backed by DataStore so the user's choice survives process restarts.
 * Call [warmBestEffort] at startup to hydrate the flag, and [setEnabledBestEffort] from
 * settings toggles to persist changes.
 */
object StreamRacePreferences {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val enabledKey = booleanPreferencesKey("enable_stream_racing")

    @Volatile
    var enableStreamRacing: Boolean = false

    fun shutdown() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancelChildren()
    }

    fun warmBestEffort(context: Context) {
        val appContext = context.applicationContext ?: context
        scope.launch {
            runCatching {
                enableStreamRacing = readEnabled(appContext)
            }.onFailure { throwable ->
                runCatching {
                    Log.w("StreamRacePreferences", "Failed to load stream racing preference", throwable)
                }
            }
        }
    }

    /** Blocking hydrate for [Application.onCreate] so the first playback sees the saved toggle. */
    fun warmBlockingBestEffort(context: Context) {
        val appContext = context.applicationContext ?: context
        runBlocking(Dispatchers.IO) {
            runCatching {
                enableStreamRacing = readEnabled(appContext)
            }.onFailure { throwable ->
                runCatching {
                    Log.w("StreamRacePreferences", "Failed to load stream racing preference", throwable)
                }
            }
        }
    }

    fun setEnabledBestEffort(context: Context, enabled: Boolean) {
        enableStreamRacing = enabled
        val appContext = context.applicationContext ?: context
        scope.launch {
            runCatching {
                appContext.streamRacingStore.edit { preferences ->
                    preferences[enabledKey] = enabled
                }
            }.onFailure { throwable ->
                runCatching {
                    Log.w("StreamRacePreferences", "Failed to save stream racing preference", throwable)
                }
            }
        }
    }

    suspend fun readEnabled(context: Context): Boolean {
        return try {
            context.streamRacingStore.data.first()[enabledKey] ?: false
        } catch (e: Exception) {
            false
        }
    }
}

data class StreamRaceRequest(
    val candidates: List<PlaybackSource>,
    val failedSourceIds: Set<String> = emptySet(),
    val lowMemoryMode: Boolean = false,
    val enabled: Boolean = StreamRacePreferences.enableStreamRacing,
    val maxProbes: Int = StreamRaceManager.MAX_PROBES
)

sealed interface StreamRaceResult {
    data class Winner(
        val source: PlaybackSource,
        val attemptedSourceIds: List<String>,
        val telemetry: List<StreamRaceTelemetryEvent>
    ) : StreamRaceResult

    data class SequentialFallback(
        val reason: StreamRaceSkipReason,
        val candidates: List<PlaybackSource>
    ) : StreamRaceResult
}

enum class StreamRaceSkipReason {
    FEATURE_DISABLED,
    LOW_MEMORY_MODE,
    NOT_ENOUGH_CANDIDATES,
    NO_PROBES_ALLOWED,
    ALL_PROBES_FAILED
}

sealed interface StreamProbeResult {
    data class Ready(
        val handshakeMs: Long,
        val firstByteMs: Long,
        val readyAtMs: Long? = null
    ) : StreamProbeResult

    data class Failed(
        val reason: String,
        val handshakeMs: Long = 0L,
        val firstByteMs: Long = 0L
    ) : StreamProbeResult
}

enum class StreamRaceProbeStatus {
    WIN,
    LOSS,
    FAILURE,
    CANCELLED,
    DENIED
}

data class StreamRaceTelemetryEvent(
    val sourceId: String,
    val providerId: String,
    val status: StreamRaceProbeStatus,
    val handshakeMs: Long,
    val firstByteMs: Long,
    val rankScore: Double,
    val reason: String? = null,
    val recordedAtMs: Long = System.currentTimeMillis()
)

fun interface StreamRaceRanker {
    fun score(source: PlaybackSource): Double
}

fun interface StreamRacePermit {
    suspend fun acquire(source: PlaybackSource): Boolean
}

fun interface StreamProbe {
    suspend fun probe(source: PlaybackSource): StreamProbeResult
}

fun interface StreamRaceTelemetrySink {
    suspend fun record(event: StreamRaceTelemetryEvent)
}

class StreamRaceManager(
    private val probe: StreamProbe,
    private val ranker: StreamRaceRanker = StreamRaceRanker { 0.0 },
    private val permit: StreamRacePermit = StreamRacePermit { true },
    private val telemetrySink: StreamRaceTelemetrySink = StreamRaceTelemetrySink {},
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun race(request: StreamRaceRequest): StreamRaceResult = coroutineScope {
        if (!request.enabled) {
            return@coroutineScope StreamRaceResult.SequentialFallback(
                reason = StreamRaceSkipReason.FEATURE_DISABLED,
                candidates = request.candidates
            )
        }
        if (request.lowMemoryMode) {
            return@coroutineScope StreamRaceResult.SequentialFallback(
                reason = StreamRaceSkipReason.LOW_MEMORY_MODE,
                candidates = request.candidates
            )
        }

        val ranked = request.candidates
            .asSequence()
            .filterNot { it.id in request.failedSourceIds || it.safeSourceId in request.failedSourceIds }
            .distinctBy { it.id }
            .mapIndexed { index, source ->
                RankedCandidate(
                    source = source,
                    rankScore = ranker.score(source),
                    originalIndex = index
                )
            }
            .sortedWith(
                compareByDescending<RankedCandidate> { it.rankScore }
                    .thenBy { it.originalIndex }
            )
            .take(request.maxProbes.coerceIn(1, MAX_PROBES))
            .toList()

        if (ranked.size < 2) {
            return@coroutineScope StreamRaceResult.SequentialFallback(
                reason = StreamRaceSkipReason.NOT_ENOUGH_CANDIDATES,
                candidates = ranked.map { it.source }
            )
        }

        val pending = ranked.associateWith { candidate ->
            async {
                runProbe(candidate)
            }
        }.toMutableMap()
        val outcomes = mutableListOf<ProbeOutcome>()

        try {
            while (pending.isNotEmpty()) {
                val (_, outcome) = awaitNext(pending)
                outcomes += outcome

                if (outcome.isReady) {
                    val firstReadyAtMs = outcome.readyAtMs ?: clockMs()
                    val nearOutcomes = collectTieWindow(pending, firstReadyAtMs)
                    outcomes += nearOutcomes

                    val winner = (listOf(outcome) + nearOutcomes)
                        .filter { candidate ->
                            candidate.isReady &&
                                abs((candidate.readyAtMs ?: firstReadyAtMs) - firstReadyAtMs) < TIE_BREAK_WINDOW_MS
                        }
                        .sortedWith(
                            compareByDescending<ProbeOutcome> { it.rankScore }
                                .thenBy { it.readyAtMs ?: Long.MAX_VALUE }
                        )
                        .firstOrNull() ?: outcome

                    val cancelled = cancelPending(pending)
                    val telemetry = recordTelemetry(outcomes + cancelled, winner)
                    return@coroutineScope StreamRaceResult.Winner(
                        source = winner.source,
                        attemptedSourceIds = ranked.map { it.source.id },
                        telemetry = telemetry
                    )
                }
            }

            val telemetry = recordTelemetry(outcomes, winner = null)
            if (outcomes.none { it.status != StreamRaceProbeStatus.DENIED }) {
                return@coroutineScope StreamRaceResult.SequentialFallback(
                    reason = StreamRaceSkipReason.NO_PROBES_ALLOWED,
                    candidates = ranked.map { it.source }
                )
            }
            StreamRaceResult.SequentialFallback(
                reason = StreamRaceSkipReason.ALL_PROBES_FAILED,
                candidates = ranked.map { it.source }
            )
        } finally {
            if (pending.isNotEmpty()) {
                pending.values.forEach { it.cancel() }
                withTimeoutOrNull(LOSER_RELEASE_TIMEOUT_MS) {
                    pending.values.joinAll()
                }
                pending.clear()
            }
        }
    }

    private suspend fun runProbe(candidate: RankedCandidate): ProbeOutcome {
        val startedAtMs = clockMs()
        val source = candidate.source
        val allowed = permit.acquire(source)
        if (!allowed) {
            return ProbeOutcome(
                source = source,
                rankScore = candidate.rankScore,
                status = StreamRaceProbeStatus.DENIED,
                handshakeMs = 0L,
                firstByteMs = 0L,
                readyAtMs = null,
                reason = "rate_limited"
            )
        }

        return try {
            val probeResult = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                probe.probe(source)
            } ?: StreamProbeResult.Failed(
                reason = "timeout",
                handshakeMs = (clockMs() - startedAtMs).coerceAtLeast(0L),
                firstByteMs = 0L
            )
            when (val result = probeResult) {
                is StreamProbeResult.Ready -> ProbeOutcome(
                    source = source,
                    rankScore = candidate.rankScore,
                    status = StreamRaceProbeStatus.LOSS,
                    handshakeMs = result.handshakeMs,
                    firstByteMs = result.firstByteMs,
                    readyAtMs = result.readyAtMs ?: clockMs(),
                    reason = null
                )
                is StreamProbeResult.Failed -> ProbeOutcome(
                    source = source,
                    rankScore = candidate.rankScore,
                    status = StreamRaceProbeStatus.FAILURE,
                    handshakeMs = result.handshakeMs,
                    firstByteMs = result.firstByteMs,
                    readyAtMs = null,
                    reason = result.reason
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            ProbeOutcome(
                source = source,
                rankScore = candidate.rankScore,
                status = StreamRaceProbeStatus.FAILURE,
                handshakeMs = (clockMs() - startedAtMs).coerceAtLeast(0L),
                firstByteMs = 0L,
                readyAtMs = null,
                reason = t::class.simpleName ?: "probe_failed"
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun awaitNext(
        pending: MutableMap<RankedCandidate, Deferred<ProbeOutcome>>
    ): Pair<RankedCandidate, ProbeOutcome> {
        val selected = select<Pair<RankedCandidate, ProbeOutcome>> {
            pending.entries.toList().forEach { (candidate, deferred) ->
                deferred.onAwait { outcome -> candidate to outcome }
            }
        }
        pending.remove(selected.first)
        return selected
    }

    private suspend fun collectTieWindow(
        pending: MutableMap<RankedCandidate, Deferred<ProbeOutcome>>,
        firstReadyAtMs: Long
    ): List<ProbeOutcome> {
        val collected = mutableListOf<ProbeOutcome>()
        withTimeoutOrNull(TIE_BREAK_WINDOW_MS) {
            while (pending.isNotEmpty()) {
                val (_, outcome) = awaitNext(pending)
                collected += outcome
                if (
                    outcome.isReady &&
                    abs((outcome.readyAtMs ?: firstReadyAtMs) - firstReadyAtMs) > TIE_BREAK_WINDOW_MS
                ) {
                    return@withTimeoutOrNull
                }
            }
        }
        return collected
    }

    private suspend fun cancelPending(
        pending: MutableMap<RankedCandidate, Deferred<ProbeOutcome>>
    ): List<ProbeOutcome> {
        if (pending.isEmpty()) return emptyList()
        val cancelled = pending.keys.map { candidate ->
            ProbeOutcome(
                source = candidate.source,
                rankScore = candidate.rankScore,
                status = StreamRaceProbeStatus.CANCELLED,
                handshakeMs = 0L,
                firstByteMs = 0L,
                readyAtMs = null,
                reason = "loser_cancelled"
            )
        }
        pending.values.forEach { it.cancel() }
        withTimeoutOrNull(LOSER_RELEASE_TIMEOUT_MS) {
            pending.values.joinAll()
        }
        pending.clear()
        return cancelled
    }

    private suspend fun recordTelemetry(
        outcomes: List<ProbeOutcome>,
        winner: ProbeOutcome?
    ): List<StreamRaceTelemetryEvent> {
        val events = outcomes.map { outcome ->
            val status = when {
                winner != null && outcome.source.id == winner.source.id -> StreamRaceProbeStatus.WIN
                outcome.isReady -> StreamRaceProbeStatus.LOSS
                else -> outcome.status
            }
            StreamRaceTelemetryEvent(
                sourceId = outcome.source.safeSourceId,
                providerId = providerIdFor(outcome.source),
                status = status,
                handshakeMs = outcome.handshakeMs,
                firstByteMs = outcome.firstByteMs,
                rankScore = outcome.rankScore,
                reason = outcome.reason,
                recordedAtMs = clockMs()
            )
        }
        events.forEach { telemetrySink.record(it) }
        return events
    }

    private fun providerIdFor(source: PlaybackSource): String {
        return source.id.substringBefore('-', missingDelimiterValue = "")
            .ifBlank { source.type.name.lowercase() }
    }

    private data class RankedCandidate(
        val source: PlaybackSource,
        val rankScore: Double,
        val originalIndex: Int
    )

    private data class ProbeOutcome(
        val source: PlaybackSource,
        val rankScore: Double,
        val status: StreamRaceProbeStatus,
        val handshakeMs: Long,
        val firstByteMs: Long,
        val readyAtMs: Long?,
        val reason: String?
    ) {
        val isReady: Boolean
            get() = readyAtMs != null
    }

    companion object {
        const val MAX_PROBES = 3
        const val TIE_BREAK_WINDOW_MS = 50L
        const val LOSER_RELEASE_TIMEOUT_MS = 1_000L
        const val PROBE_TIMEOUT_MS = 8_500L
    }
}

internal fun Player.hasSelectedTracks(): Boolean {
    return currentTracks.groups.any { group ->
        (0 until group.length).any { trackIndex -> group.isTrackSelected(trackIndex) }
    }
}

@androidx.annotation.VisibleForTesting
internal var testSupportedVideoMimeTypes: Set<String>? = null

internal fun resetSupportedVideoMimeTypesForTest() {
    testSupportedVideoMimeTypes = null
}

private val supportedVideoMimeTypes: Set<String> by lazy {
    try {
        val list = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
        list.codecInfos
            .filter { !it.isEncoder }
            .flatMap { it.supportedTypes.toList() }
            .map { it.lowercase() }
            .toSet()
    } catch (e: Exception) {
        emptySet()
    }
}

private fun isVideoMimeTypeSupported(mimeType: String): Boolean {
    val codecs = testSupportedVideoMimeTypes ?: supportedVideoMimeTypes
    if (codecs.isEmpty()) return true
    return codecs.contains(mimeType.lowercase())
}


internal fun hasSupportedVideoTrack(player: Player): Boolean {
    return try {
        hasSupportedVideoTrack(player.currentTracks)
    } catch (e: Throwable) {
        true
    }
}

internal fun hasSupportedVideoTrack(tracks: androidx.media3.common.Tracks): Boolean {
    return try {
        val videoGroups = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }
        if (videoGroups.isEmpty()) {
            return true
        }
        videoGroups.any { group ->
            (0 until group.length).any { trackIndex ->
                val format = group.getTrackFormat(trackIndex)
                val mimeType = format.sampleMimeType
                mimeType == null || isVideoMimeTypeSupported(mimeType)
            }
        }
    } catch (e: Throwable) {
        true
    }
}

@OptIn(UnstableApi::class)
class Media3StreamProbe(
    private val context: Context,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) : StreamProbe {
    override suspend fun probe(source: PlaybackSource): StreamProbeResult {
        if (source.rawUrl.startsWith("xtream://", ignoreCase = true)) {
            return StreamProbeResult.Failed("unresolved_xtream_url")
        }
        return withContext(Dispatchers.Main.immediate) {
            val startedAtMs = System.currentTimeMillis()
            var firstBufferAtMs: Long? = null
            var tempPlayer: ExoPlayer? = null
            var listener: Player.Listener? = null

            try {
                withTimeout(timeoutMs) {
                    suspendCancellableCoroutine<StreamProbeResult> { continuation ->
                        val sourceHeaders = source.headers
                        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                        val resolvingDataSourceFactory = if (sourceHeaders.isNotEmpty()) {
                            ResolvingDataSource.Factory(
                                DefaultDataSource.Factory(context, httpDataSourceFactory)
                            ) { dataSpec ->
                                dataSpec.withRequestHeaders(sourceHeaders)
                            }
                        } else {
                            DefaultDataSource.Factory(context, httpDataSourceFactory)
                        }

                        val player = ExoPlayer.Builder(context.applicationContext)
                            .setMediaSourceFactory(
                                DefaultMediaSourceFactory(context)
                                    .setDataSourceFactory(resolvingDataSourceFactory)
                            )
                            .setRenderersFactory(
                                object : DefaultRenderersFactory(context) {
                                    override fun buildVideoRenderers(
                                        context: Context,
                                        extensionRendererMode: Int,
                                        mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                                        enableDecoderFallback: Boolean,
                                        eventHandler: android.os.Handler,
                                        eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
                                        allowedVideoJoiningTimeMs: Long,
                                        out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
                                    ) {
                                        // Do nothing to skip video renderers
                                    }

                                    override fun buildAudioRenderers(
                                        context: Context,
                                        extensionRendererMode: Int,
                                        mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                                        enableDecoderFallback: Boolean,
                                        audioSink: androidx.media3.exoplayer.audio.AudioSink,
                                        eventHandler: android.os.Handler,
                                        eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
                                        out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
                                    ) {
                                        // Do nothing to skip audio renderers
                                    }
                                }.setEnableDecoderFallback(true)
                            )
                            .setLoadControl(
                                PlaybackProfileManager.loadControl(
                                    PlaybackProfileManager.profileFor(context, source)
                                )
                            )
                            .build()
                        tempPlayer = player

                        listener = object : Player.Listener {
                            private var stateReadyReached = false
                            private var tracksChangedFired = false

                            override fun onPlaybackStateChanged(playbackState: Int) {
                                val now = System.currentTimeMillis()
                                if (playbackState == Player.STATE_BUFFERING && firstBufferAtMs == null) {
                                    firstBufferAtMs = now
                                }
                                if (playbackState == Player.STATE_READY) {
                                    stateReadyReached = true
                                    if (continuation.isActive) {
                                        val hasSelected = player.hasSelectedTracks()
                                        val isVideoOnly = player.currentTracks.groups.all { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }
                                        val hasSupportedVideo = hasSupportedVideoTrack(player.currentTracks)
                                        if ((hasSelected || isVideoOnly) && hasSupportedVideo) {
                                            continuation.resume(
                                                StreamProbeResult.Ready(
                                                    handshakeMs = (firstBufferAtMs ?: now) - startedAtMs,
                                                    firstByteMs = now - startedAtMs,
                                                    readyAtMs = now
                                                )
                                            )
                                        } else if (hasSelected) {
                                            continuation.resume(
                                                StreamProbeResult.Failed(
                                                    reason = "unsupported_video_track",
                                                    handshakeMs = (firstBufferAtMs ?: now) - startedAtMs,
                                                    firstByteMs = now - startedAtMs
                                                )
                                            )
                                        } else if (tracksChangedFired) {
                                            continuation.resume(
                                                StreamProbeResult.Failed(
                                                    reason = "no_active_tracks",
                                                    handshakeMs = (firstBufferAtMs ?: now) - startedAtMs,
                                                    firstByteMs = now - startedAtMs
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                                tracksChangedFired = true
                                val now = System.currentTimeMillis()
                                if (stateReadyReached && continuation.isActive) {
                                    val hasSelected = tracks.groups.any { group ->
                                        (0 until group.length).any { trackIndex -> group.isTrackSelected(trackIndex) }
                                    }
                                    val hasSupportedVideo = hasSupportedVideoTrack(tracks)
                                    if (hasSelected && hasSupportedVideo) {
                                        continuation.resume(
                                            StreamProbeResult.Ready(
                                                handshakeMs = (firstBufferAtMs ?: now) - startedAtMs,
                                                firstByteMs = now - startedAtMs,
                                                readyAtMs = now
                                            )
                                        )
                                    } else {
                                        val reason = if (!hasSelected) "no_active_tracks" else "unsupported_video_track"
                                        continuation.resume(
                                            StreamProbeResult.Failed(
                                                reason = reason,
                                                handshakeMs = (firstBufferAtMs ?: now) - startedAtMs,
                                                firstByteMs = now - startedAtMs
                                            )
                                        )
                                    }
                                }
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                if (continuation.isActive) {
                                    val now = System.currentTimeMillis()
                                    continuation.resume(
                                        StreamProbeResult.Failed(
                                            reason = error.errorCodeName,
                                            handshakeMs = (firstBufferAtMs ?: now) - startedAtMs,
                                            firstByteMs = now - startedAtMs
                                        )
                                    )
                                }
                            }
                        }

                        player.addListener(listener)
                        player.setMediaItem(buildProbeMediaItem(source))
                        player.prepare()
                        player.playWhenReady = false
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                StreamProbeResult.Failed(
                    reason = "timeout",
                    handshakeMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L),
                    firstByteMs = 0L
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                StreamProbeResult.Failed(
                    reason = e::class.simpleName ?: "probe_failed",
                    handshakeMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L),
                    firstByteMs = 0L
                )
            } finally {
                val playerToRelease = tempPlayer
                val listenerToRemove = listener
                if (playerToRelease != null) {
                    cleanupScope.launch {
                        runCatching {
                            if (listenerToRemove != null) {
                                playerToRelease.removeListener(listenerToRemove)
                            }
                            playerToRelease.release()
                        }.onFailure { error ->
                            runCatching {
                                Log.w("Media3StreamProbe", "Failed to release probe player", error)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun buildProbeMediaItem(source: PlaybackSource): MediaItem {
        val uri = source.rawUrl
        if (uri.isBlank()) {
            throw IllegalArgumentException("URI is empty for source ${source.id}")
        }
        val scheme = URI_SCHEME_REGEX
            .find(uri)
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
        if (scheme in setOf("file", "javascript", "data", "content", "ftp")) {
            throw SecurityException("Unsafe protocol scheme '$scheme' is blocked.")
        }

        if (scheme == "http") {
            // Mirror PlaybackManager.buildMediaItem: honour the global allowCleartextUserSources
            // preference in addition to the per-source flag, so racing doesn't reject (and thus
            // skip) an HTTP source that playback would actually allow (#15).
            val allowCleartext = com.example.calmsource.core.database.repository.UserPreferencesRepository
                .preferences.value.allowCleartextUserSources
            if (!allowCleartext && !source.allowInsecureHttp) {
                throw SecurityException("Cleartext HTTP traffic is blocked by user preference")
            }
        }

        val builder = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(source.safeSourceId)

        source.drmConfiguration?.let { drm ->
            builder.setDrmConfiguration(
                androidx.media3.common.MediaItem.DrmConfiguration.Builder(java.util.UUID.fromString(drm.scheme.uuid))
                    .setLicenseUri(drm.licenseUri)
                    .setLicenseRequestHeaders(drm.keyRequestHeaders)
                    .build()
            )
        }

        val cleanPath = uri.substringBefore('?').substringBefore('#').lowercase()
        when {
            cleanPath.endsWith(".m3u8") -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            cleanPath.endsWith(".mpd") -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
            cleanPath.endsWith(".ts") -> builder.setMimeType(MimeTypes.VIDEO_MP2T)
            cleanPath.endsWith(".mkv") -> builder.setMimeType(MimeTypes.VIDEO_MATROSKA)
            else -> StreamFormatFallback.mimeTypeForContainerHint(
                source.metadata?.containerFormat.orEmpty()
            )?.let { builder.setMimeType(it) }
        }
        return builder.build()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000L
        private val URI_SCHEME_REGEX = "^([a-zA-Z][a-zA-Z0-9+.-]*):".toRegex()
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
}
