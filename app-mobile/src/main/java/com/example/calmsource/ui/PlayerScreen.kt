package com.example.calmsource.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.example.calmsource.core.model.PlaybackAudioTrack
import com.example.calmsource.core.model.PlaybackSubtitleTrack
import com.example.calmsource.core.model.TrackType
import com.example.calmsource.core.playback.ui.PlayerOverlayMotion
import com.example.calmsource.core.playback.ui.TrackLanguageFormatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.PlaybackError
import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlayerState
import com.example.calmsource.core.model.WatchOption
import com.example.calmsource.core.model.AutoFallbackPolicy
import androidx.compose.ui.unit.sp
import com.example.calmsource.core.playback.PlaybackManager
import com.example.calmsource.core.playback.LiveChannelRecovery
import com.example.calmsource.core.playback.isResolvedPlaybackUrlInvalid
import com.example.calmsource.core.playback.mergeFallbackIdentityPreservingPseudoUrl
import com.example.calmsource.core.playback.resolvePlaybackFallbacks
import com.example.calmsource.core.playback.resolvePlaybackRequest
import com.example.calmsource.core.playback.selectAutoLiveFallbackCandidates
import com.example.calmsource.core.playback.diagnostics.PlaybackDiagnosticsFormatter
import com.example.calmsource.core.discoveryengine.providers.ProviderManager
import com.example.calmsource.core.model.ResourcePlaybackState
import android.util.Log
import com.example.calmsource.feature.iptv.IPTVRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@ExperimentalMaterial3Api
@Composable
fun PlayerScreen(
    request: PlaybackRequest,
    fallbackSources: List<PlaybackSource> = emptyList(),
    playBestIntent: Boolean = false,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Instantiate PlaybackManager
    val initialRequest = remember { request }
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
        )
    }
    
    val uiState by playbackManager.uiState.collectAsStateWithLifecycle()
    val progressState by playbackManager.progressState.collectAsStateWithLifecycle()
    val audioTracks by playbackManager.audioTracks.collectAsStateWithLifecycle()
    val subtitleTracks by playbackManager.subtitleTracks.collectAsStateWithLifecycle()
    var showControls by remember { mutableStateOf(true) }
    var showAdvancedPanel by remember { mutableStateOf(false) }
    var showChannelSwitcher by remember { mutableStateOf(false) }
    var showTrackSelector by remember { mutableStateOf(false) }
    var trackSelectorType by remember { mutableStateOf(TrackType.AUDIO) }
    val showMultipleAudio = remember(audioTracks) { audioTracks.size > 1 }
    val showSubtitlePicker = remember(subtitleTracks) { subtitleTracks.isNotEmpty() }
    var userInteractionTrigger by remember { mutableIntStateOf(0) }
    
    // Double tap skip feedback state
    var showDoubleTapLeftFeedback by remember { mutableStateOf(false) }
    var showDoubleTapRightFeedback by remember { mutableStateOf(false) }
    
    var currentRequest by remember(request) { mutableStateOf(request) }
    var successRecorded by remember(currentRequest.source.id) { mutableStateOf(false) }
    var failureRecorded by remember(currentRequest.source.id) { mutableStateOf(false) }
    val isLive = currentRequest.source.metadata?.isLive == true

    var fallbackPolicy by remember { mutableStateOf(com.example.calmsource.core.playback.FallbackPreferences.policy) }
    val fallbackPromptState by playbackManager.fallbackPromptState.collectAsStateWithLifecycle()

    val iptvChannelsFlow = remember(isLive) {
        if (isLive) {
            IPTVRepository.channels.map { list -> list.filterNot { it.isVod } }
        } else {
            flowOf(emptyList())
        }
    }
    val iptvChannels by iptvChannelsFlow.collectAsStateWithLifecycle(emptyList())
    val iptvPlaybackError by IPTVRepository.playbackResolutionError.collectAsStateWithLifecycle()
    
    val currentIndex = remember(currentRequest, iptvChannels) {
        iptvChannels.indexOfFirst { it.id == currentRequest.source.id }
    }
    
    var channelSwitchFails by remember { mutableIntStateOf(0) }

    val currentRequestState = rememberUpdatedState(currentRequest)
    val iptvChannelsState = rememberUpdatedState(iptvChannels)
    val fallbackSourcesState = rememberUpdatedState(fallbackSources)
    val uiStateLatest = rememberUpdatedState(uiState)

    LaunchedEffect(uiState.error, currentRequest.source.id) {
        if (uiState.error != null) {
            showTrackSelector = false
        }
        // Respect the user's auto-fallback policy: only auto-advance to the next live channel
        // when fallback is enabled. OFF / ASK_BEFORE_FALLBACK leave the user in control.
        val maxLiveSwitches = LiveChannelRecovery.maxAutoSwitchCount(
            com.example.calmsource.core.playback.FallbackPreferences.policy
        )
        if (uiState.error != null && isLive && iptvChannels.isNotEmpty() && channelSwitchFails < maxLiveSwitches) {
            delay(1500)
            // Channel auto-switch must only run once the stream-level fallback chain for THIS
            // source has settled. While PlaybackManager is still trying alternate streams
            // (m3u8 alt, extension fallbacks) it sets isTransitioningSource=true; jumping to a
            // different channel here would hijack that in-progress recovery (bug #2).
            val latest = uiStateLatest.value
            if (latest.isTransitioningSource || latest.error == null) return@LaunchedEffect
            // Guard against stale effect overriding a manual channel switch
            if (currentRequestState.value.source.id != currentRequest.source.id) return@LaunchedEffect
            val channels = iptvChannelsState.value
            val bestCandidate = LiveChannelRecovery.suggestNextChannel(
                currentChannelId = currentRequest.source.id,
                channels = channels,
                healthScoreFor = { sourceId ->
                    com.example.calmsource.core.database.SourceHealthRepository
                        .getSourceHealth(sourceId, readonly = true)?.healthScore ?: 100
                }
            ) ?: return@LaunchedEffect
            channelSwitchFails++
            currentRequest = IPTVRepository.buildLivePlaybackRequest(bestCandidate)
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

    LaunchedEffect(uiState.playerState, currentRequest.source.id) {
        // Only record a success once playback is genuinely stable. READY can be reached before the
        // first frame (audio-only stall / black screen), so require sustained PLAYING — matching
        // PlaybackManager's own 5s success debounce (bug #6).
        if (currentRequest.source.type == PlaybackSourceType.IPTV &&
            uiState.playerState == PlayerState.PLAYING && !successRecorded
        ) {
            delay(5000)
            if (uiStateLatest.value.playerState == PlayerState.PLAYING && !successRecorded) {
                IPTVRepository.recordPlaybackSuccess(currentRequest.source.id)
                successRecorded = true
            }
        }
    }

    LaunchedEffect(uiState.error, uiState.isTransitioningSource, currentRequest.source.id) {
        val error = uiState.error
        // Defer failure recording until the stream-level fallback chain has settled. Recording on
        // the first transient error would penalise channels that recover via m3u8/extension
        // fallback while isTransitioningSource is still true (bug #6).
        if (currentRequest.source.type == PlaybackSourceType.IPTV && error != null &&
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
            IPTVRepository.recordPlaybackFailure(currentRequest.source.id, errorCategory)
            failureRecorded = true
        }
    }
 
    fun switchChannel(offset: Int) {
        if (iptvChannels.isEmpty() || currentIndex == -1) return
        val nextIndex = (currentIndex + offset + iptvChannels.size) % iptvChannels.size
        val channel = iptvChannels[nextIndex]
        currentRequest = IPTVRepository.buildLivePlaybackRequest(channel)
    }
 
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    LaunchedEffect(showControls, userInteractionTrigger, uiState.playerState) {
        if (showControls && uiState.playerState == PlayerState.PLAYING) {
            delay(5_000)
            if (!showAdvancedPanel && !showChannelSwitcher && !showTrackSelector) showControls = false
        }
    }

    LaunchedEffect(uiState.playerState, uiState.source) {
        if (uiState.playerState == PlayerState.PLAYING) {
            uiState.source?.let { newSource ->
                if (newSource.id != currentRequest.source.id) {
                    currentRequest = mergeFallbackIdentityPreservingPseudoUrl(currentRequest, newSource)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, playbackManager) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                // Pause (not release) when backgrounded so returning resumes instantly instead of
                // tearing down ExoPlayer and re-preparing from scratch (bug #24). Full release is
                // reserved for onDispose (navigation away / screen removed from composition).
                playbackManager.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playbackManager.release()
        }
    }

    LaunchedEffect(currentRequest, lifecycleOwner) {
        IPTVRepository.cancelBackgroundWork()
        playbackManager.clearError()
        // Drop any resolution error left over from a previously-failed channel so it can't bleed
        // into this request's UI (see playbackResolutionError being a shared StateFlow).
        IPTVRepository.clearPlaybackResolutionError()
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            try {
                // If the player is already initialised for this exact request (e.g. returning from
                // the background where we only paused), just resume instead of re-resolving and
                // re-preparing from scratch (bug #24).
                val active = uiStateLatest.value
                val resumableStates = setOf(
                    PlayerState.PLAYING, PlayerState.PAUSED, PlayerState.READY, PlayerState.BUFFERING
                )
                if (active.source?.id == currentRequest.source.id && active.playerState in resumableStates) {
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
                    } catch (e: CancellationException) {
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
                    currentRequest,
                    ::resolveXtream,
                    ::resolveMagnet,
                    ::resolveIptv
                )
                val isInvalidUrl = isResolvedPlaybackUrlInvalid(resolvedRequest)

                if (isInvalidUrl) {
                    if (resolvedRequest == null && currentRequest.source.rawUrl.startsWith("magnet:", ignoreCase = true)) {
                        playbackManager.setError(PlaybackError.PermissionRequired(message = "A Debrid account must be connected to play torrent (magnet) streams."))
                    } else {
                        val errorMsg = IPTVRepository.playbackResolutionError.value ?: "Could not resolve this channel for playback."
                        playbackManager.setError(PlaybackError.SourceUnavailable(message = errorMsg))
                    }
                } else {
                    val validRequest = checkNotNull(resolvedRequest)
                    val candidateFallbacks = selectAutoLiveFallbackCandidates(
                        explicitFallbacks = fallbackSourcesState.value,
                        currentSourceId = currentRequest.source.id,
                        findChannel = IPTVRepository::findChannel,
                        buildLiveFallbackSources = IPTVRepository::buildLivePlaybackFallbackSources
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Surface unexpected resolution/prepare errors instead of leaving the player stuck
                // with no feedback (bug #25; matches TV's handler).
                playbackManager.setError(
                    PlaybackError.Unknown(
                        message = "Unexpected error preparing stream: ${
                            com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(e.message ?: "Unknown error")
                        }"
                    )
                )
                kotlinx.coroutines.awaitCancellation()
            }
        }
    }
    
    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            showTrackSelector -> showTrackSelector = false
            showChannelSwitcher -> showChannelSwitcher = false
            showAdvancedPanel -> showAdvancedPanel = false
            showControls -> showControls = false
            else -> onBack()
        }
    }
 
    val player by playbackManager.playerFlow.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (isLive) return@detectTapGestures
                        // Double tap to seek (YouTube style)
                        if (!playbackManager.isActive) return@detectTapGestures
                        val halfWidth = size.width / 2
                        if (offset.x < halfWidth) {
                            playbackManager.seekTo(
                                (playbackManager.progressState.value.currentPositionMs - 10_000L).coerceAtLeast(0L)
                            )
                            showDoubleTapLeftFeedback = true
                            coroutineScope.launch {
                                delay(650)
                                showDoubleTapLeftFeedback = false
                            }
                        } else {
                            val current = playbackManager.progressState.value.currentPositionMs
                            val duration = playbackManager.progressState.value.durationMs
                            val target = current + 10_000L
                            playbackManager.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
                            showDoubleTapRightFeedback = true
                            coroutineScope.launch {
                                delay(650)
                                showDoubleTapRightFeedback = false
                            }
                        }
                    },
                    onTap = {
                        userInteractionTrigger++
                        showControls = !showControls
                        if (!showControls) {
                            showAdvancedPanel = false
                            showChannelSwitcher = false
                            showTrackSelector = false
                        }
                    }
                )
            }
    ) {
        // Video Player
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
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

        // Double Tap Skip Left Feedback Overlay
        AnimatedVisibility(
            visible = showDoubleTapLeftFeedback,
            enter = PlayerOverlayMotion.fadeIn,
            exit = PlayerOverlayMotion.fadeOut,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "◀◀",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "10 seconds",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Double Tap Skip Right Feedback Overlay
        AnimatedVisibility(
            visible = showDoubleTapRightFeedback,
            enter = PlayerOverlayMotion.fadeIn,
            exit = PlayerOverlayMotion.fadeOut,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "▶▶",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "10 seconds",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
 
        // Fallback Transition Overlay
        val fallbackMsg = uiState.fallbackMessage
        val isTransitioning = uiState.isTransitioningSource || !fallbackMsg.isNullOrBlank()
        if (isTransitioning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .then(
                        if (uiState.source != null) Modifier.clickable(enabled = false) {}
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 32.dp, vertical = 24.dp)
                ) {
                    // Animated transition indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Switching source...",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val displayMsg = if (!fallbackMsg.isNullOrBlank()) fallbackMsg else "Trying alternative track..."
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = androidx.compose.animation.fadeIn()
                    ) {
                        Text(
                            text = displayMsg,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }

                    Text(
                        text = "Your video will resume shortly",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Loading and Error States
        val currentError = uiState.error
        if (currentError != null && (uiState.isTerminal || !isTransitioning)) {
            ErrorOverlay(
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
        } else if (!isTransitioning && (uiState.playerState == PlayerState.BUFFERING || uiState.playerState == PlayerState.PREPARING)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 28.dp, vertical = 22.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = if (uiState.playerState == PlayerState.PREPARING) "Loading stream..." else "Buffering...",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
 
        // Controls Overlay
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
                                Color.Black.copy(alpha = 0.78f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = uiState.source?.title ?: currentRequest.source.metadata?.title ?: currentRequest.source.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isLive) "Live TV" else currentRequest.source.type.name.replace('_', ' '),
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    if (isLive) {
                        IconButton(
                            onClick = {
                                userInteractionTrigger++
                                showChannelSwitcher = true
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Channels", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                    if (showMultipleAudio) {
                        IconButton(
                            onClick = {
                                userInteractionTrigger++
                                trackSelectorType = TrackType.AUDIO
                                showTrackSelector = true
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Audiotrack, contentDescription = "Audio", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                    if (showSubtitlePicker) {
                        IconButton(
                            onClick = {
                                userInteractionTrigger++
                                trackSelectorType = TrackType.SUBTITLE
                                showTrackSelector = true
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Subtitles, contentDescription = "Subtitles", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                    IconButton(
                        onClick = {
                            userInteractionTrigger++
                            showAdvancedPanel = !showAdvancedPanel
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
 
                // Playback Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    if (isLive && currentIndex != -1) {
                        IconButton(onClick = { switchChannel(-1) }, modifier = Modifier.size(64.dp)) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Prev Channel", tint = Color.White, modifier = Modifier.fillMaxSize())
                        }
                    } else if (!isLive) {
                        IconButton(
                            onClick = {
                                userInteractionTrigger++
                                playbackManager.seekTo((progressState.currentPositionMs - 10_000L).coerceAtLeast(0L))
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.Black.copy(alpha = 0.42f), CircleShape)
                        ) {
                            Text("-10", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
 
                    IconButton(
                        onClick = {
                            userInteractionTrigger++
                            if (uiState.playerState == PlayerState.PLAYING) playbackManager.pause()
                            else playbackManager.play()
                        },
                        modifier = Modifier
                            .size(96.dp)
                            .background(Color.White.copy(alpha = 0.16f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (uiState.playerState == PlayerState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.playerState == PlayerState.PLAYING) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
 
                    if (isLive && currentIndex != -1) {
                        IconButton(onClick = { switchChannel(1) }, modifier = Modifier.size(64.dp)) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next Channel", tint = Color.White, modifier = Modifier.fillMaxSize())
                        }
                    } else if (!isLive) {
                        IconButton(
                            onClick = {
                                userInteractionTrigger++
                                val duration = progressState.durationMs
                                val target = progressState.currentPositionMs + 10_000L
                                playbackManager.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.Black.copy(alpha = 0.42f), CircleShape)
                        ) {
                            Text("+10", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
 
                // Bottom Progress Bar
                if (!isLive) {
                    MobilePlayerProgressBar(
                        progressState = progressState,
                        onSeek = { position ->
                            userInteractionTrigger++
                            playbackManager.seekTo(position)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(start = 24.dp, end = 24.dp, bottom = 48.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 32.dp, bottom = 48.dp)
                            .background(Color.Red, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = "LIVE", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
 
        // Channel Switcher
        if (showChannelSwitcher && isLive) {
            ModalBottomSheet(
                onDismissRequest = { showChannelSwitcher = false },
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) {
                ChannelSwitcherSheet(
                    channels = iptvChannels,
                    currentChannelId = currentRequest.source.id,
                    onChannelSelect = { channel ->
                        currentRequest = IPTVRepository.buildLivePlaybackRequest(channel)
                        showChannelSwitcher = false
                    }
                )
            }
        }
 
        // Advanced Panel
        AnimatedVisibility(
            visible = showAdvancedPanel && showControls,
            enter = PlayerOverlayMotion.fadeIn,
            exit = PlayerOverlayMotion.fadeOut,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Card(
                modifier = Modifier.width(320.dp).fillMaxHeight().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Advanced Options", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    Text("Source Information", style = MaterialTheme.typography.titleSmall)
                    Text(text = "Type: ${uiState.source?.type?.name ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                    if (uiState.source?.metadata?.isLive == true) {
                        Text(text = "Stream Type: Live", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                    Text("Session Diagnostics", style = MaterialTheme.typography.titleSmall)
                    val sessionDiag = uiState.sessionDiagnostics
                    PlaybackDiagnosticsFormatter.snapshotRows(sessionDiag).forEach { (label, value) ->
                        Text(text = "$label: $value", style = MaterialTheme.typography.bodySmall)
                    }
                    uiState.diagnostics.videoResolution?.let { resolution ->
                        Text(text = "Video: $resolution", style = MaterialTheme.typography.bodySmall)
                    }
                    if (sessionDiag.recentEvents.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Recovery timeline", style = MaterialTheme.typography.titleSmall)
                        sessionDiag.recentEvents.takeLast(5).forEach { event ->
                            Text(
                                text = PlaybackDiagnosticsFormatter.formatEvent(event),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider()
                    Text("Auto Fallback Policy", style = MaterialTheme.typography.titleSmall)
                    AutoFallbackPolicy.entries.forEach { policy ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    fallbackPolicy = policy 
                                    com.example.calmsource.core.playback.FallbackPreferences.setPolicyAndPersist(context, policy)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = fallbackPolicy == policy,
                                onClick = { 
                                    fallbackPolicy = policy 
                                    com.example.calmsource.core.playback.FallbackPreferences.setPolicyAndPersist(context, policy)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (policy) {
                                    AutoFallbackPolicy.OFF -> "Off"
                                    AutoFallbackPolicy.ASK_BEFORE_FALLBACK -> "Ask before fallback"
                                    AutoFallbackPolicy.AUTO_FALLBACK_ONCE -> "Auto fallback once"
                                    AutoFallbackPolicy.AUTO_FALLBACK_LIMITED -> "Auto fallback until playable"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        if (showTrackSelector) {
            ModalBottomSheet(
                onDismissRequest = { showTrackSelector = false },
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ) {
                PlayerTrackSelectorSheet(
                    trackType = trackSelectorType,
                    audioTracks = audioTracks,
                    subtitleTracks = subtitleTracks,
                    onAudioTrackSelect = { trackId ->
                        playbackManager.selectAudioTrack(trackId)
                        showTrackSelector = false
                    },
                    onSubtitleTrackSelect = { trackId ->
                        playbackManager.selectSubtitleTrack(trackId)
                        showTrackSelector = false
                    },
                    onDisableSubtitles = {
                        playbackManager.disableSubtitles()
                        showTrackSelector = false
                    }
                )
            }
        }
    }
}
 
@Composable
private fun MobilePlayerProgressBar(
    progressState: com.example.calmsource.core.model.PlaybackProgressState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        val progress = if (progressState.durationMs > 0) {
            progressState.currentPositionMs.toFloat() / progressState.durationMs.toFloat()
        } else 0f

        Slider(
            value = progress,
            onValueChange = { newProgress ->
                val newPosition = (newProgress * progressState.durationMs).toLong()
                onSeek(newPosition)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatTime(progressState.currentPositionMs), color = Color.White, style = MaterialTheme.typography.labelLarge)
            Text(text = formatTime(progressState.durationMs), color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun PlayerTrackSelectorSheet(
    trackType: TrackType,
    audioTracks: List<PlaybackAudioTrack>,
    subtitleTracks: List<PlaybackSubtitleTrack>,
    onAudioTrackSelect: (String) -> Unit,
    onSubtitleTrackSelect: (String) -> Unit,
    onDisableSubtitles: () -> Unit
) {
    val subtitlesOff = remember(subtitleTracks) { subtitleTracks.none { it.isSelected } }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            text = if (trackType == TrackType.AUDIO) "Audio track" else "Subtitles",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            if (trackType == TrackType.AUDIO) {
                items(audioTracks, key = { it.id }) { track ->
                    PlayerTrackListItem(
                        label = TrackLanguageFormatter.trackLabel(track.name, track.language),
                        subtitle = track.channels?.let { "$it ch" },
                        selected = track.isSelected,
                        onClick = { onAudioTrackSelect(track.id) }
                    )
                }
            } else {
                item(key = "subtitles-off") {
                    PlayerTrackListItem(
                        label = "Off",
                        subtitle = null,
                        selected = subtitlesOff,
                        onClick = onDisableSubtitles
                    )
                }
                items(subtitleTracks, key = { it.id }) { track ->
                    PlayerTrackListItem(
                        label = TrackLanguageFormatter.trackLabel(track.name, track.language),
                        subtitle = TrackLanguageFormatter.displayLanguage(track.language),
                        selected = track.isSelected,
                        onClick = { onSubtitleTrackSelect(track.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerTrackListItem(
    label: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        supportingContent = subtitle?.let {
            {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = if (selected) {
            {
                Text(
                    text = "✓",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        } else null,
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
        )
    )
}

@Composable
fun ChannelSwitcherSheet(
    channels: List<IPTVChannel>,
    currentChannelId: String,
    onChannelSelect: (IPTVChannel) -> Unit
) {
    val groups = remember(channels) { channels.groupBy { it.groupTitle ?: "General" } }
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        groups.forEach { (category, categoryChannels) ->
            item(key = "category-$category") {
                Text(text = category, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary)
            }
            items(categoryChannels, key = { it.id }) { channel ->
                ListItem(
                    headlineContent = { Text(channel.name) },
                    modifier = Modifier.clickable { onChannelSelect(channel) },
                    colors = ListItemDefaults.colors(
                        containerColor = if (channel.id == currentChannelId) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                    )
                )
            }
        }
    }
}
 
@Composable
fun ErrorOverlay(
    error: PlaybackError,
    resolutionError: String? = null,
    isTerminal: Boolean = false,
    onTryNext: (() -> Unit)?,
    onChooseAnother: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
            .padding(32.dp)
            .widthIn(max = 400.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (isTerminal) Icons.Default.Block else Icons.Default.Warning,
                contentDescription = "Error",
                tint = if (isTerminal) Color(0xFFEF4444) else Color(0xFFF59E0B),
                modifier = Modifier.size(if (isTerminal) 72.dp else 64.dp)
            )
            
            Text(
                text = if (isTerminal) "All Sources Failed" else "Playback Failed",
                color = AppColors.TextMain,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            val explanation = when (error) {
                is PlaybackError.TerminalError -> "Automatic fallback exhausted. All available sources failed to play. Please go back and select a different source manually."
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
                else -> error.message
            }
            
            Text(
                text = explanation,
                color = AppColors.TextSub,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            if (!resolutionError.isNullOrBlank()) {
                Text(
                    text = resolutionError,
                    color = AppColors.TextSub,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (onTryNext != null && !isTerminal) {
                PremiumButton(
                    text = "Try next best source",
                    onClick = onTryNext,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Button(
                onClick = onChooseAnother,
                colors = ButtonDefaults.buttonColors(containerColor = if (isTerminal) Color(0xFFEF4444) else AppColors.Surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = "Choose another source",
                    color = AppColors.TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
 
private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
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
