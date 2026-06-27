package com.example.calmsource.tv.ui

import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import com.example.calmsource.core.playback.ui.PlayerOverlayMotion
import com.example.calmsource.core.playback.ui.TrackLanguageFormatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.calmsource.core.model.PlaybackError
import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.PlayerState
import com.example.calmsource.feature.iptv.IPTVRepository
import com.example.calmsource.core.model.TrackType
import com.example.calmsource.core.model.IPTVChannel
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import com.example.calmsource.core.playback.PlaybackManager
import com.example.calmsource.core.playback.LiveChannelRecovery
import com.example.calmsource.core.playback.isResolvedPlaybackUrlInvalid
import com.example.calmsource.core.playback.mergeFallbackIdentityPreservingPseudoUrl
import com.example.calmsource.core.playback.resolvePlaybackFallbacks
import com.example.calmsource.core.playback.resolvePlaybackRequest
import com.example.calmsource.core.playback.selectAutoLiveFallbackCandidates
import android.util.Log
import com.example.calmsource.core.discoveryengine.providers.ProviderManager
import com.example.calmsource.core.model.ResourcePlaybackState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun TvPlayerScreen(
    request: PlaybackRequest,
    fallbackSources: List<PlaybackSource> = emptyList(),
    playBestIntent: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val initialRequest = remember { request }
    
    // MediaSession for HDMI-CEC, Bluetooth media keys, lock screen controls,
    // and Android TV Assistant voice commands.
    var mediaSession by remember { mutableStateOf<androidx.media3.session.MediaSession?>(null) }

    val playbackManager = remember(initialRequest.source.id) {
        PlaybackManager(
            context = context,
            coroutineScope = coroutineScope,
            resourceStateSink = { state ->
                ProviderManager.setPlaybackState(state.toResourcePlaybackState())
            },
            lowMemoryModeSink = { enabled ->
                ProviderManager.setLowMemoryMode(enabled)
            }
        ).apply {
            onPlayerAboutToBeReleased = {
                mediaSession?.release()
                mediaSession = null
            }
        }
    }
    
    val uiState by playbackManager.uiState.collectAsStateWithLifecycle()
    val progressState by playbackManager.progressState.collectAsStateWithLifecycle()
    val audioTracks by playbackManager.audioTracks.collectAsStateWithLifecycle()
    val subtitleTracks by playbackManager.subtitleTracks.collectAsStateWithLifecycle()
    val showMultipleAudio = remember(audioTracks) { audioTracks.size > 1 }
    val showSubtitlePicker = remember(subtitleTracks) { subtitleTracks.isNotEmpty() }
    val subtitlesOff = remember(subtitleTracks) { subtitleTracks.none { it.isSelected } }
    
    var showControls by remember { mutableStateOf(true) }
    var showChannelSwitcher by remember { mutableStateOf(false) }
    var showTrackSelector by remember { mutableStateOf(false) }
    var trackSelectorType by remember { mutableStateOf(TrackType.AUDIO) }
    var activeRequest by remember { mutableStateOf(request) }
    var successRecorded by remember(activeRequest.source.id) { mutableStateOf(false) }
    var failureRecorded by remember(activeRequest.source.id) { mutableStateOf(false) }
    val isLive = activeRequest.source.metadata?.isLive == true
    val fallbackPromptState by playbackManager.fallbackPromptState.collectAsStateWithLifecycle()

    val iptvChannelsFlow: kotlinx.coroutines.flow.Flow<List<IPTVChannel>> = remember(isLive) {
        if (isLive) {
            IPTVRepository.channels.map { list -> list.filterNot { it.isVod } }
        } else {
            flowOf(emptyList())
        }
    }
    val iptvChannelsFlowCollected by iptvChannelsFlow.collectAsStateWithLifecycle(
        initialValue = emptyList<IPTVChannel>()
    )
    val channels = remember(iptvChannelsFlowCollected) { iptvChannelsFlowCollected }

    val iptvPlaybackError by com.example.calmsource.feature.iptv.IPTVRepository.playbackResolutionError.collectAsStateWithLifecycle(initialValue = null)

    var channelSwitchFails by remember { mutableIntStateOf(0) }

    val activeRequestState = rememberUpdatedState(activeRequest)
    val channelsState = rememberUpdatedState(channels)
    val uiStateLatest = rememberUpdatedState(uiState)

    LaunchedEffect(uiState.error, activeRequest.source.id) {
        if (uiState.error != null) {
            showTrackSelector = false
        }
        // Respect the user's auto-fallback policy: only auto-advance to the next live channel
        // when fallback is enabled. OFF / ASK_BEFORE_FALLBACK leave the user in control.
        val maxLiveSwitches = LiveChannelRecovery.maxAutoSwitchCount(
            com.example.calmsource.core.playback.FallbackPreferences.policy
        )
        val isLive = activeRequest.source.metadata?.isLive == true
        if (uiState.error != null && isLive && channels.isNotEmpty() && channelSwitchFails < maxLiveSwitches) {
            delay(1500)
            // Only switch channels once the stream-level fallback chain for THIS source has settled;
            // otherwise we hijack an in-progress m3u8/extension fallback (bug #2).
            val latest = uiStateLatest.value
            if (latest.isTransitioningSource || latest.error == null) return@LaunchedEffect
            if (activeRequestState.value.source.id != activeRequest.source.id) return@LaunchedEffect
            val bestCandidate = LiveChannelRecovery.suggestNextChannel(
                currentChannelId = activeRequest.source.id,
                channels = channelsState.value,
                healthScoreFor = { sourceId ->
                    com.example.calmsource.core.database.SourceHealthRepository
                        .getSourceHealth(sourceId, readonly = true)?.healthScore ?: 100
                }
            ) ?: return@LaunchedEffect
            channelSwitchFails++
            activeRequest = com.example.calmsource.feature.iptv.IPTVRepository.buildLivePlaybackRequest(bestCandidate)
        }
    }

    LaunchedEffect(uiState.playerState) {
        if (uiState.playerState == PlayerState.PLAYING) {
            delay(5000)
            channelSwitchFails = 0
        }
    }

    LaunchedEffect(uiState.error) {
        val error = uiState.error
        if (error is PlaybackError.TerminalError) {
            android.widget.Toast.makeText(context, error.message, android.widget.Toast.LENGTH_LONG).show()
            onBack()
        }
    }

    LaunchedEffect(uiState.playerState, activeRequest.source.id) {
        // Require sustained PLAYING before recording success — READY can precede the first frame
        // (bug #6), matching PlaybackManager's own 5s success debounce.
        if (activeRequest.source.type == PlaybackSourceType.IPTV &&
            uiState.playerState == PlayerState.PLAYING && !successRecorded
        ) {
            delay(5000)
            if (uiStateLatest.value.playerState == PlayerState.PLAYING && !successRecorded) {
                IPTVRepository.recordPlaybackSuccess(activeRequest.source.id)
                successRecorded = true
            }
        }
    }

    LaunchedEffect(uiState.error, uiState.isTransitioningSource, activeRequest.source.id) {
        val error = uiState.error
        // Defer failure recording until the fallback chain settles (bug #6).
        if (activeRequest.source.type == PlaybackSourceType.IPTV && error != null &&
            !uiState.isTransitioningSource && !failureRecorded
        ) {
            val errorCategory = when (error) {
                is PlaybackError.Network -> "NETWORK"
                is PlaybackError.Timeout -> "TIMEOUT"
                is PlaybackError.UnsupportedFormat -> "UNSUPPORTED_FORMAT"
                is PlaybackError.PermissionRequired -> "PERMISSION_REQUIRED"
                is PlaybackError.SourceUnavailable -> "SOURCE_UNAVAILABLE"
                is PlaybackError.DecoderError -> "DECODER_ERROR"
                is PlaybackError.Drm -> "DRM"
                is PlaybackError.ServerRefused -> "SERVER_REFUSED"
                is PlaybackError.CleartextNotPermitted -> "CLEARTEXT_NOT_PERMITTED"
                is PlaybackError.TerminalError -> "TERMINAL_ERROR"
                else -> "UNKNOWN"
            }
            IPTVRepository.recordPlaybackFailure(activeRequest.source.id, errorCategory)
            failureRecorded = true
        }
    }
 
    var userInteractionTrigger by remember { mutableStateOf(0) }
    val playPauseFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val channelSwitcherFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val trackSelectorFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val progressBarFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val screenFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(showChannelSwitcher) {
        if (showChannelSwitcher) {
            delay(100)
            try {
                channelSwitcherFocusRequester.requestFocus()
            } catch (e: Exception) {
                Log.w("TvPlayerScreen", "Failed to request channel switcher focus", e)
            }
        }
    }

    LaunchedEffect(showTrackSelector) {
        if (showTrackSelector) {
            delay(100)
            try {
                trackSelectorFocusRequester.requestFocus()
            } catch (e: Exception) {
                Log.w("TvPlayerScreen", "Failed to request track selector focus", e)
            }
        }
    }

    LaunchedEffect(uiState.playerState, uiState.source) {
        if (uiState.playerState == PlayerState.PLAYING) {
            uiState.source?.let { newSource ->
                if (newSource.id != activeRequest.source.id) {
                    activeRequest = mergeFallbackIdentityPreservingPseudoUrl(activeRequest, newSource)
                }
            }
        }
    }

    // Auto-hide controls (only while playing)
    LaunchedEffect(showControls, userInteractionTrigger, uiState.playerState) {
        if (showControls && uiState.playerState == PlayerState.PLAYING) {
            delay(5_000)
            if (!showChannelSwitcher && !showTrackSelector) showControls = false
        }
    }

    LaunchedEffect(showControls, showChannelSwitcher, showTrackSelector) {
        if (showControls) {
            if (!showChannelSwitcher && !showTrackSelector) {
                delay(100)
                try {
                    playPauseFocusRequester.requestFocus()
                } catch (e: Exception) {
                    Log.w("TvPlayerScreen", "Failed to request play/pause focus", e)
                }
            }
        } else {
            try {
                screenFocusRequester.requestFocus()
            } catch (e: Exception) {
                Log.w("TvPlayerScreen", "Failed to request screen focus", e)
            }
        }
    }
 
    val fallbackSourcesState = rememberUpdatedState(fallbackSources)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, playbackManager) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                // Pause (not release) on background so returning resumes instantly; full release is
                // reserved for onDispose (navigation away) (bug #24).
                playbackManager.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playbackManager.release()
        }
    }

    LaunchedEffect(activeRequest, lifecycleOwner) {
        com.example.calmsource.feature.iptv.IPTVRepository.cancelBackgroundWork()
        playbackManager.clearError()
        // Drop any resolution error left over from a previously-failed channel so it can't bleed
        // into this request's UI (see playbackResolutionError being a shared StateFlow).
        com.example.calmsource.feature.iptv.IPTVRepository.clearPlaybackResolutionError()
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            try {
                // Resume in place if the player is already initialised for this exact request
                // (returning from background where we only paused) instead of re-preparing (bug #24).
                val active = uiStateLatest.value
                val resumableStates = setOf(
                    PlayerState.PLAYING, PlayerState.PAUSED, PlayerState.READY, PlayerState.BUFFERING
                )
                if (active.source?.id == activeRequest.source.id && active.playerState in resumableStates) {
                    playbackManager.play()
                    kotlinx.coroutines.awaitCancellation()
                }

                delay(300)

                suspend fun resolveXtream(source: PlaybackSource): String? =
                    com.example.calmsource.feature.iptv.IptvXtreamPlaybackResolver.resolveSourceUrl(source)

                // Alt/fallback sources must not overwrite the primary source's resolution error.
                suspend fun resolveXtreamFallback(source: PlaybackSource): String? =
                    com.example.calmsource.feature.iptv.IptvXtreamPlaybackResolver.resolveSourceUrl(source, surfaceError = false)

                suspend fun resolveMagnet(source: PlaybackSource): String? {
                    val connectedAccount = com.example.calmsource.feature.debrid.DebridRepository.accounts.value.firstOrNull { it.isConnected } ?: return null
                    val client = com.example.calmsource.feature.debrid.DebridRepository.getClient(connectedAccount.providerType) ?: return null
                    val tokens = com.example.calmsource.feature.debrid.DebridRepository.tokenStore.getTokens(connectedAccount.providerType) ?: return null
                    val infoHash = "magnet:\\?xt=urn:btih:([a-zA-Z0-9]+)".toRegex()
                        .find(source.rawUrl)?.groupValues?.get(1) ?: ""
                    if (infoHash.isBlank()) return null
                    com.example.calmsource.feature.debrid.DebridRepository.cachedResolvedLink(infoHash)?.let { return it }
                    val debridReq = com.example.calmsource.core.model.DebridResolveRequest(
                        infoHash = infoHash, magnetUrl = source.rawUrl
                    )
                    return try {
                        client.resolveLink(debridReq, tokens).url?.also { resolved ->
                            com.example.calmsource.feature.debrid.DebridRepository.putResolvedLink(infoHash, resolved)
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }

                suspend fun resolveIptv(source: PlaybackSource): String? {
                    val channel = IPTVRepository.findPlaybackChannel(source.id.substringBefore("-alt-"))
                        ?: return null
                    return IPTVRepository.resolvePlaybackUrl(channel)
                }

                suspend fun resolveIptvFallback(source: PlaybackSource): String? {
                    val channel = IPTVRepository.findPlaybackChannel(source.id.substringBefore("-alt-"))
                        ?: return null
                    val result = IPTVRepository.resolvePlaybackUrlOrError(channel)
                    return result.url.takeIf { url ->
                        result.error == null &&
                            !url.contains("REDACTED", ignoreCase = true) &&
                            !url.startsWith("xtream://", ignoreCase = true)
                    }
                }

                val resolvedRequest = resolvePlaybackRequest(
                    activeRequest,
                    ::resolveXtream,
                    ::resolveMagnet,
                    ::resolveIptv
                )
                val isInvalidUrl = isResolvedPlaybackUrlInvalid(resolvedRequest)

                if (isInvalidUrl) {
                    if (resolvedRequest == null && activeRequest.source.rawUrl.startsWith("magnet:", ignoreCase = true)) {
                        playbackManager.setError(PlaybackError.PermissionRequired(message = "A Debrid account must be connected to play torrent (magnet) streams."))
                    } else {
                        val errorMsg = com.example.calmsource.feature.iptv.IPTVRepository.playbackResolutionError.value ?: "Could not resolve this channel for playback."
                        playbackManager.setError(PlaybackError.SourceUnavailable(message = errorMsg))
                    }
                } else {
                    val validRequest = checkNotNull(resolvedRequest)
                    val candidateFallbacks = selectAutoLiveFallbackCandidates(
                        explicitFallbacks = fallbackSourcesState.value,
                        currentSourceId = activeRequest.source.id,
                        findChannel = com.example.calmsource.feature.iptv.IPTVRepository::findChannel,
                        buildLiveFallbackSources = com.example.calmsource.feature.iptv.IPTVRepository::buildLivePlaybackFallbackSources
                    )
                    val skipIds = playbackManager.consumeBestMatchSkipSet(candidateFallbacks)
                    val resolvedFallbacks = resolvePlaybackFallbacks(
                        candidateFallbacks,
                        skipIds,
                        ::resolveXtreamFallback,
                        ::resolveMagnet,
                        ::resolveIptvFallback
                    )
                    playbackManager.prepareBest(validRequest, resolvedFallbacks, playBest = playBestIntent)
                }
                kotlinx.coroutines.awaitCancellation()
            } catch (e: android.os.NetworkOnMainThreadException) {
                throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val errMsg = e.message.orEmpty()
                val redactedMsg = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(errMsg)
                playbackManager.setError(
                    PlaybackError.Unknown(
                        message = "Unexpected error preparing stream: $redactedMsg"
                    )
                )
                kotlinx.coroutines.awaitCancellation()
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (showTrackSelector) {
            showTrackSelector = false
        } else if (showChannelSwitcher) {
            showChannelSwitcher = false
        } else if (showControls) {
            showControls = false
        } else {
            onBack()
        }
    }
 
    val player by playbackManager.playerFlow.collectAsStateWithLifecycle()



    LaunchedEffect(player) {
        mediaSession?.release()
        mediaSession = player?.let { p ->
            androidx.media3.session.MediaSession.Builder(context, p).build()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaSession?.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.DirectionUp, Key.DirectionDown -> {
                            if (!showControls && !showChannelSwitcher && !showTrackSelector && uiState.error == null) {
                                showControls = true
                                true
                            } else false
                        }
                        Key.DirectionLeft -> {
                            if (!showControls && !showChannelSwitcher && !showTrackSelector &&
                                uiState.error == null && channels.isNotEmpty()
                            ) {
                                showChannelSwitcher = true
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (showTrackSelector) {
                                showTrackSelector = false
                                userInteractionTrigger++
                                true
                            } else if (showChannelSwitcher) {
                                showChannelSwitcher = false
                                true
                            } else false
                        }
                        Key.MediaPlayPause -> {
                            if (uiState.playerState == PlayerState.PLAYING) playbackManager.pause()
                            else playbackManager.play()
                            userInteractionTrigger++
                            true
                        }
                        Key.MediaRewind -> {
                            val newPos = (progressState.currentPositionMs - 10_000L).coerceAtLeast(0L)
                            playbackManager.seekTo(newPos)
                            userInteractionTrigger++
                            true
                        }
                        Key.MediaFastForward -> {
                            val target = progressState.currentPositionMs + 10_000L
                            val duration = progressState.durationMs
                            playbackManager.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
                            userInteractionTrigger++
                            true
                        }
                        Key.MediaStop -> {
                            playbackManager.pause()
                            true
                        }
                        Key.MediaNext -> {
                            val isLive = activeRequest.source.metadata?.isLive == true
                            if (isLive) {
                                val currentIdx = channels.indexOfFirst { it.id == activeRequest.source.id }
                                val nextIdx = if (currentIdx >= 0) (currentIdx + 1) % channels.size else 0
                                if (channels.isNotEmpty()) {
                                    activeRequest = com.example.calmsource.feature.iptv.IPTVRepository.buildLivePlaybackRequest(channels[nextIdx])
                                }
                            } else {
                                val target = progressState.currentPositionMs + 10_000L
                                val duration = progressState.durationMs
                                playbackManager.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
                            }
                            userInteractionTrigger++
                            true
                        }
                        Key.MediaPrevious -> {
                            val isLive = activeRequest.source.metadata?.isLive == true
                            if (isLive) {
                                val currentIdx = channels.indexOfFirst { it.id == activeRequest.source.id }
                                val prevIdx = if (currentIdx >= 0) (currentIdx - 1 + channels.size) % channels.size else 0
                                if (channels.isNotEmpty()) {
                                    activeRequest = com.example.calmsource.feature.iptv.IPTVRepository.buildLivePlaybackRequest(channels[prevIdx])
                                }
                            } else {
                                val newPos = (progressState.currentPositionMs - 10_000L).coerceAtLeast(0L)
                                playbackManager.seekTo(newPos)
                            }
                            userInteractionTrigger++
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusRequester(screenFocusRequester)
            .focusable()
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    keepScreenOn = true
                }.also { view ->
                    playbackManager.setPlayerView(view)
                }
            },
            update = { view ->
                if (view.player != player) {
                    view.player = player
                }
            },
            onRelease = { view ->
                view.player = null
                playbackManager.setPlayerView(null)
            },
            modifier = Modifier.fillMaxSize()
        )
 
        val fallbackMsg = uiState.fallbackMessage
        val isTransitioning = uiState.isTransitioningSource || !fallbackMsg.isNullOrBlank()

        // Loading Indicator
        if (!isTransitioning && (uiState.playerState == PlayerState.BUFFERING || uiState.playerState == PlayerState.PREPARING)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 34.dp, vertical = 26.dp)
            ) {
                androidx.compose.material3.CircularProgressIndicator(color = TvColors.BorderFocused)
                Text(
                    text = if (uiState.playerState == PlayerState.PREPARING) "Loading stream..." else "Buffering...",
                    color = Color.White,
                    fontSize = 18.sp
                )
            }
        }
 
        // Fallback Transition Overlay
        if (isTransitioning) {
            val displayMsg = if (!fallbackMsg.isNullOrBlank()) fallbackMsg else "Trying alternative track..."
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(color = TvColors.BorderFocused)
                    Text(
                        text = displayMsg,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    uiState.sessionDiagnostics.activeBackend?.let { backend ->
                        Text(
                            text = "Backend: $backend",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Error State
        val currentError = uiState.error
        if (currentError != null && (uiState.isTerminal || !isTransitioning)) {
            TvErrorOverlay(
                error = currentError,
                resolutionError = iptvPlaybackError,
                isTerminal = uiState.isTerminal,
                onTryNext = if (!uiState.isTerminal && fallbackPromptState) {
                    { playbackManager.onUserSelectTryNextBest() }
                } else null,
                onChooseAnother = {
                    playbackManager.onUserSelectChooseAnother()
                    onBack()
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }
 
        // Overlay Controls
        AnimatedVisibility(
            visible = showControls && uiState.error == null,
            enter = PlayerOverlayMotion.fadeIn,
            exit = PlayerOverlayMotion.fadeOut,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.72f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.92f)
                            )
                        )
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 48.dp, vertical = 36.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (activeRequest.source.metadata?.isLive == true) "LIVE TV"
                                else activeRequest.source.type.name.replace('_', ' '),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Press Back to hide controls",
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 14.sp
                    )
                }

                // Bottom Info & Progress
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 40.dp)
                ) {
                    Text(
                        text = uiState.source?.title ?: activeRequest.source.title,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Playback control buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isLive = uiState.source?.metadata?.isLive == true || activeRequest.source.metadata?.isLive == true

                        if (isLive) {
                            TvFocusCard(
                                onClick = {
                                    userInteractionTrigger++
                                    showChannelSwitcher = true
                                }
                            ) { isFocused ->
                                Text(
                                    text = "Channels",
                                    color = if (isFocused) TvColors.TextMain else TvColors.TextSub,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                        
                        if (!isLive) {
                            TvFocusCard(
                                onClick = {
                                    userInteractionTrigger++
                                    val currentPos = progressState.currentPositionMs
                                    val newPos = (currentPos - 10000L).coerceAtLeast(0L)
                                    playbackManager.seekTo(newPos)
                                }
                            ) { isFocused ->
                                Text(
                                    text = "-10s",
                                    color = if (isFocused) TvColors.TextMain else TvColors.TextSub,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        val isPlaying = uiState.playerState == PlayerState.PLAYING
                        TvFocusCard(
                            onClick = {
                                userInteractionTrigger++
                                if (isPlaying) {
                                    playbackManager.pause()
                                } else {
                                    playbackManager.play()
                                }
                            },
                            modifier = Modifier.focusRequester(playPauseFocusRequester)
                        ) { isFocused ->
                            Text(
                                text = if (isPlaying) "Pause" else "Play",
                                color = if (isFocused) TvColors.TextMain else TvColors.TextSub,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        // Audio Track button
                        if (showMultipleAudio) {
                            TvFocusCard(
                                onClick = {
                                    userInteractionTrigger++
                                    trackSelectorType = TrackType.AUDIO
                                    showTrackSelector = true
                                }
                            ) { isFocused ->
                                Text(
                                    text = "Audio",
                                    color = if (isFocused) TvColors.TextMain else TvColors.TextSub,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        // Subtitle button
                        if (showSubtitlePicker) {
                            TvFocusCard(
                                onClick = {
                                    userInteractionTrigger++
                                    trackSelectorType = TrackType.SUBTITLE
                                    showTrackSelector = true
                                }
                            ) { isFocused ->
                                Text(
                                    text = "Subs",
                                    color = if (isFocused) TvColors.TextMain else TvColors.TextSub,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        if (!isLive) {
                            TvFocusCard(
                                onClick = {
                                    userInteractionTrigger++
                                    val currentPos = progressState.currentPositionMs
                                    val duration = progressState.durationMs
                                    val newPos = (currentPos + 10000L).coerceAtMost(if (duration > 0) duration else Long.MAX_VALUE)
                                    playbackManager.seekTo(newPos)
                                }
                            ) { isFocused ->
                                Text(
                                    text = "+10s",
                                    color = if (isFocused) TvColors.TextMain else TvColors.TextSub,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (uiState.source?.metadata?.isLive == true || activeRequest.source.metadata?.isLive == true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.Red))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "LIVE", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        TvProgressBar(
                            progressState = progressState,
                            playbackManager = playbackManager,
                            focusRequester = progressBarFocusRequester,
                            onUserInteraction = { userInteractionTrigger++ }
                        )
                    }
                }
            }
        }
 
        // Channel Switcher Overlay
        AnimatedVisibility(
            visible = showChannelSwitcher && uiState.error == null,
            enter = PlayerOverlayMotion.fadeIn,
            exit = PlayerOverlayMotion.fadeOut,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(380.dp)
                    .background(Color(0xED090B12))
                    .padding(24.dp)
            ) {
                Text(
                    text = "Live Channels",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${channels.size} available",
                    color = TvColors.TextSub,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(18.dp))
                if (channels.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No live channels available",
                                color = TvColors.TextSub,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TvFocusCard(
                                modifier = Modifier.focusRequester(channelSwitcherFocusRequester),
                                onClick = { showChannelSwitcher = false }
                            ) { isFocused ->
                                Text(
                                    text = "Close",
                                    color = if (isFocused) TvColors.TextMain else Color.White,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                        TvFocusCard(
                            onClick = {
                                activeRequest = com.example.calmsource.feature.iptv.IPTVRepository.buildLivePlaybackRequest(channel)
                                showChannelSwitcher = false
                            },
                            modifier = if (index == 0) {
                                Modifier.focusRequester(channelSwitcherFocusRequester).fillMaxWidth()
                            } else {
                                Modifier.fillMaxWidth()
                            }
                        ) { isFocused ->
                            Text(
                                text = if (channel.id == activeRequest.source.id) "Playing  ${channel.name}" else channel.name,
                                color = if (channel.id == activeRequest.source.id) Color.Cyan else if (isFocused) Color.White else Color.Gray,
                                fontWeight = if (channel.id == activeRequest.source.id) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(12.dp),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                }
            }
        }

        // Track Selector Overlay
        AnimatedVisibility(
            visible = showTrackSelector && uiState.error == null,
            enter = PlayerOverlayMotion.fadeIn,
            exit = PlayerOverlayMotion.fadeOut,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(380.dp)
                    .background(Color(0xED090B12))
                    .padding(24.dp)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                            showTrackSelector = false
                            true
                        } else false
                    }
            ) {
                Text(
                    text = if (trackSelectorType == TrackType.AUDIO) "Audio Track" else "Subtitles",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(18.dp))

                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (trackSelectorType == TrackType.AUDIO) {
                        itemsIndexed(audioTracks, key = { _, track -> track.id }) { index, track ->
                            TvTrackRow(
                                label = TrackLanguageFormatter.trackLabel(track.name, track.language),
                                subtitle = TrackLanguageFormatter.displayLanguage(track.language),
                                selected = track.isSelected,
                                onClick = {
                                    playbackManager.selectAudioTrack(track.id)
                                    showTrackSelector = false
                                    userInteractionTrigger++
                                },
                                modifier = if (index == 0) {
                                    Modifier.focusRequester(trackSelectorFocusRequester).fillMaxWidth()
                                } else {
                                    Modifier.fillMaxWidth()
                                }
                            )
                        }
                    } else {
                        item(key = "subtitles-off") {
                            TvTrackRow(
                                label = "Off",
                                subtitle = null,
                                selected = subtitlesOff,
                                onClick = {
                                    playbackManager.disableSubtitles()
                                    showTrackSelector = false
                                    userInteractionTrigger++
                                },
                                modifier = Modifier.focusRequester(trackSelectorFocusRequester).fillMaxWidth()
                            )
                        }
                        items(subtitleTracks, key = { it.id }) { track ->
                            TvTrackRow(
                                label = TrackLanguageFormatter.trackLabel(track.name, track.language),
                                subtitle = TrackLanguageFormatter.displayLanguage(track.language),
                                selected = track.isSelected,
                                onClick = {
                                    playbackManager.selectSubtitleTrack(track.id)
                                    showTrackSelector = false
                                    userInteractionTrigger++
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvTrackRow(
    label: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvFocusCard(
        onClick = onClick,
        modifier = modifier
    ) { isFocused ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    color = if (isFocused || selected) Color.White else Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                subtitle?.let {
                    Text(it, color = TvColors.TextSub, fontSize = 12.sp)
                }
            }
            if (selected) {
                Text("✓", color = TvColors.BorderFocused, fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun TvErrorOverlay(
    error: PlaybackError,
    resolutionError: String? = null,
    isTerminal: Boolean = false,
    onTryNext: (() -> Unit)?,
    onChooseAnother: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    
    LaunchedEffect(error, onTryNext != null) {
        delay(100)
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            Log.w("TvPlayerScreen", "Failed to request error dialog focus", e)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 500.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(72.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (isTerminal) "All Sources Failed" else "Playback Failed",
                color = TvColors.TextMain,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val explanation = when (error) {
                is PlaybackError.Network -> "A network connection error occurred. Please check your internet connection."
                is PlaybackError.Timeout -> "The stream request timed out. The server might be busy or offline."
                is PlaybackError.UnsupportedFormat -> "This stream format is not supported by your device."
                is PlaybackError.PermissionRequired -> error.message
                is PlaybackError.CleartextNotPermitted -> "Cleartext HTTP is blocked. Enable insecure HTTP in Settings or use an HTTPS stream."
                is PlaybackError.Drm -> "This stream requires DRM that could not be unlocked on this device."
                is PlaybackError.SourceUnavailable -> error.message.ifBlank { "The backup source is currently unavailable or offline." }
                is PlaybackError.DecoderError -> "A media decoding error occurred on your device."
                is PlaybackError.ServerRefused -> "The stream server refused the connection."
                is PlaybackError.Unknown -> "An unknown playback error occurred."
                is PlaybackError.TerminalError -> "Automatic fallback exhausted. All available sources failed. Try another title or check your connection."
                else -> error.message
            }
            
            Text(
                text = explanation,
                color = TvColors.TextSub,
                fontSize = 16.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            if (!resolutionError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = resolutionError,
                    color = Color(0xFFF59E0B),
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onTryNext != null) {
                    TvFocusCard(
                        onClick = onTryNext,
                        modifier = Modifier.focusRequester(focusRequester)
                    ) { isFocused ->
                        Text(
                            text = "Try next best source",
                            color = if (isFocused) TvColors.TextMain else TvColors.TextSub,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                
                TvFocusCard(
                    onClick = onChooseAnother,
                    modifier = if (onTryNext == null) Modifier.focusRequester(focusRequester) else Modifier
                ) { isFocused ->
                    Text(
                        text = "Choose another source",
                        color = if (isFocused) TvColors.TextMain else TvColors.TextSub,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
 
@Composable
fun TvProgressBar(
    progressState: com.example.calmsource.core.model.PlaybackProgressState,
    playbackManager: PlaybackManager,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
    onUserInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isProgressBarFocused by remember { mutableStateOf(false) }
    val buffered = if (progressState.durationMs > 0) {
        progressState.bufferedPositionMs.toFloat() / progressState.durationMs.toFloat()
    } else 0f
    val playedFraction = if (progressState.durationMs > 0) {
        progressState.currentPositionMs.toFloat() / progressState.durationMs.toFloat()
    } else 0f

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Gray.copy(alpha = 0.5f))
                .focusRequester(focusRequester)
                .onFocusChanged { isProgressBarFocused = it.isFocused || it.hasFocus }
                .focusable()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionRight -> {
                                onUserInteraction()
                                val newPos = (progressState.currentPositionMs + 10_000L)
                                    .coerceAtMost(progressState.durationMs)
                                playbackManager.seekTo(newPos)
                                true
                            }
                            Key.DirectionLeft -> {
                                onUserInteraction()
                                val newPos = (progressState.currentPositionMs - 10_000L).coerceAtLeast(0L)
                                playbackManager.seekTo(newPos)
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            // Focus border indicator
            if (isProgressBarFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 2.dp,
                            color = TvColors.BorderFocused,
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(buffered.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.35f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(playedFraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(TvColors.BorderFocused)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${formatTime(progressState.currentPositionMs)} / ${formatTime(progressState.durationMs)}",
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun PlayerState.toResourcePlaybackState(): ResourcePlaybackState {
    return when (this) {
        PlayerState.IDLE -> ResourcePlaybackState.IDLE
        PlayerState.PREPARING -> ResourcePlaybackState.BUFFERING
        PlayerState.BUFFERING -> ResourcePlaybackState.BUFFERING
        PlayerState.READY -> ResourcePlaybackState.READY_PAUSED
        PlayerState.PLAYING -> ResourcePlaybackState.READY_PLAYING
        PlayerState.PAUSED -> ResourcePlaybackState.READY_PAUSED
        PlayerState.ENDED -> ResourcePlaybackState.ENDED
        PlayerState.FAILED -> ResourcePlaybackState.ERROR
    }
}
