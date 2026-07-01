package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.*

import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.example.calmsource.core.ui.R as CoreUiR
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import com.example.calmsource.core.playback.PlaybackManager
import com.example.calmsource.core.playback.PlaybackRequestSaver
import com.example.calmsource.core.playback.PlaybackUrlCache
import com.example.calmsource.core.playback.LiveChannelRecovery
import com.example.calmsource.core.playback.liveChannelBaseId
import com.example.calmsource.core.playback.isResolvedPlaybackUrlInvalid
import com.example.calmsource.core.playback.requiresPlaybackUrlResolution
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
import com.example.calmsource.feature.player.PlayerChrome
import com.example.calmsource.feature.player.PlayerActions
import com.example.calmsource.feature.player.buildPlayerChromeState
import com.example.calmsource.core.ui.components.*

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
    val t = LocalLumenTokens.current
    val initialRequest = remember { request }
    
    // Low ram check
    val isLowRam = remember(context) {
        val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        activityManager?.isLowRamDevice == true
    }

    // MediaSession
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
    
    var showChannelSwitcher by remember { mutableStateOf(false) }
    PlaybackUrlCache.put(request.source.id, request.source.rawUrl)
    var activeRequest by remember(request.source.id) { mutableStateOf(request) }
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
    val currentIndex = remember(activeRequest, channels) {
        channels.indexOfFirst { it.id == liveChannelBaseId(activeRequest.source.id) }
    }

    fun switchChannel(offset: Int) {
        if (channels.isEmpty() || currentIndex == -1) return
        val nextIndex = (currentIndex + offset + channels.size) % channels.size
        activeRequest = IPTVRepository.buildLivePlaybackRequest(channels[nextIndex])
    }

    val iptvPlaybackError by com.example.calmsource.feature.iptv.IPTVRepository.playbackResolutionError.collectAsStateWithLifecycle(initialValue = null)

    var channelSwitchFails by remember { mutableIntStateOf(0) }

    val activeRequestState = rememberUpdatedState(activeRequest)
    val channelsState = rememberUpdatedState(channels)
    val uiStateLatest = rememberUpdatedState(uiState)

    LaunchedEffect(uiState.error, activeRequest.source.id) {
        val policy = com.example.calmsource.core.playback.FallbackPreferences.policy
        val maxLiveSwitches = LiveChannelRecovery.maxAutoSwitchCount(policy)
        val isLive = activeRequest.source.metadata?.isLive == true
        if (uiState.error != null && isLive && channels.isNotEmpty() && channelSwitchFails < maxLiveSwitches) {
            delay(LiveChannelRecovery.AUTO_SWITCH_SETTLE_MS)
            val latest = uiStateLatest.value
            if (!LiveChannelRecovery.shouldAttemptLiveChannelAutoSwitch(latest, policy)) return@LaunchedEffect
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

    LaunchedEffect(uiState.playerState, activeRequest.source.id) {
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
 
    val retryTrigger = remember { mutableIntStateOf(0) }
    
    val channelSwitcherFocusRequester = remember { FocusRequester() }

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

    LaunchedEffect(uiState.playerState, uiState.source) {
        if (uiState.playerState == PlayerState.PLAYING) {
            uiState.source?.let { newSource ->
                if (newSource.id != activeRequest.source.id) {
                    activeRequest = mergeFallbackIdentityPreservingPseudoUrl(activeRequest, newSource)
                }
            }
        }
    }

    val fallbackSourcesState = rememberUpdatedState(fallbackSources)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, playbackManager) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                playbackManager.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playbackManager.release()
        }
    }

    LaunchedEffect(activeRequest, lifecycleOwner, retryTrigger.intValue) {
        com.example.calmsource.feature.iptv.IPTVRepository.cancelBackgroundWork()
        playbackManager.clearError()
        com.example.calmsource.feature.iptv.IPTVRepository.clearPlaybackResolutionError()
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            try {
                val active = uiStateLatest.value
                val resumableStates = setOf(
                    PlayerState.PLAYING, PlayerState.PAUSED, PlayerState.READY, PlayerState.BUFFERING
                )
                val canResumeWithoutResolve = active.source?.id == activeRequest.source.id &&
                    active.playerState in resumableStates &&
                    !requiresPlaybackUrlResolution(activeRequest.source.rawUrl)
                if (canResumeWithoutResolve) {
                    playbackManager.play()
                    kotlinx.coroutines.awaitCancellation()
                }

                delay(300)

                suspend fun resolveXtream(source: PlaybackSource): String? =
                    com.example.calmsource.feature.iptv.IptvXtreamPlaybackResolver.resolveSourceUrl(source)

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

    var showChrome by rememberSaveable { mutableStateOf(true) }

    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            showChannelSwitcher -> showChannelSwitcher = false
            showChrome -> showChrome = false
            else -> onBack()
        }
    }

    val playerTitle = uiState.source?.title ?: activeRequest.source.title
    val playerChromeState = buildPlayerChromeState(
        title = playerTitle,
        exoState = uiState.playerState,
        progress = progressState,
        audioTracks = audioTracks,
        subtitleTracks = subtitleTracks,
        isLive = isLive,
        hasNext = isLive && currentIndex != -1 && channels.size > 1,
        hasPrev = isLive && currentIndex != -1 && channels.size > 1,
    )
    val playerChromeActions = PlayerActions(
        onPlayPause = {
            if (uiState.playerState == PlayerState.PLAYING) playbackManager.pause()
            else playbackManager.play()
        },
        onSeekTo = playbackManager::seekTo,
        onSeekRelative = { delta ->
            val duration = progressState.durationMs
            val target = progressState.currentPositionMs + delta
            playbackManager.seekTo(
                when {
                    duration > 0L && delta > 0 -> target.coerceAtMost(duration)
                    else -> target.coerceAtLeast(0L)
                },
            )
        },
        onSelectAudio = { playbackManager.selectAudioTrack(it.id) },
        onSelectSubtitle = { track ->
            if (track.id == "off") playbackManager.disableSubtitles()
            else playbackManager.selectSubtitleTrack(track.id)
        },
        onNext = { switchChannel(1) },
        onPrev = { switchChannel(-1) },
        onClose = onBack,
    )
 
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
            mediaSession = null
        }
    }

    val playerFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(showChannelSwitcher) {
        if (!showChannelSwitcher) {
            kotlinx.coroutines.delay(100)
            runCatching { playerFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LumenTokens.Color.bg)
            .focusRequester(playerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (!showChrome) {
                            showChrome = true
                            true
                        } else {
                            if (uiState.playerState == PlayerState.PLAYING) playbackManager.pause()
                            else playbackManager.play()
                            true
                        }
                    }
                    else -> false
                }
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (!showChannelSwitcher && uiState.error == null && isLive && channels.isNotEmpty()) {
                                showChannelSwitcher = true
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (showChannelSwitcher) {
                                showChannelSwitcher = false
                                true
                            } else false
                        }
                        Key.MediaPlayPause -> {
                            if (uiState.playerState == PlayerState.PLAYING) playbackManager.pause()
                            else playbackManager.play()
                            true
                        }
                        Key.MediaRewind -> {
                            val newPos = (progressState.currentPositionMs - 10_000L).coerceAtLeast(0L)
                            playbackManager.seekTo(newPos)
                            true
                        }
                        Key.MediaFastForward -> {
                            val target = progressState.currentPositionMs + 10_000L
                            val duration = progressState.durationMs
                            playbackManager.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
                            true
                        }
                        Key.MediaStop -> {
                            playbackManager.pause()
                            true
                        }
                        Key.MediaNext -> {
                            if (isLive) switchChannel(1)
                            else {
                                val target = progressState.currentPositionMs + 10_000L
                                val duration = progressState.durationMs
                                playbackManager.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
                            }
                            true
                        }
                        Key.MediaPrevious -> {
                            if (isLive) switchChannel(-1)
                            else playbackManager.seekTo((progressState.currentPositionMs - 10_000L).coerceAtLeast(0L))
                            true
                        }
                        else -> false
                    }
                } else false
            }
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

        if (isTransitioning) {
            val displayMsg = if (!fallbackMsg.isNullOrBlank()) fallbackMsg else "Trying alternative track..."
            LumenBufferingOverlay(
                isBuffering = true,
                text = displayMsg,
                modifier = Modifier.fillMaxSize(),
            )
        }

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
                onRetry = { retryTrigger.intValue++ },
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (uiState.error == null) {
            PlayerChrome(
                state = playerChromeState,
                actions = playerChromeActions,
                isTv = true,
                modifier = Modifier.fillMaxSize(),
                chromeVisible = showChrome,
                onChromeVisibleChange = { showChrome = it },
            )
            if (isLive) {
                TvFocusable(
                    onClick = { showChannelSwitcher = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(LumenLegacySpace.xl),
                ) {
                    Text(
                        text = "Channels",
                        color = t.colors.foreground,
                        fontWeight = FontWeight.Bold,
                        fontSize = LumenType.size16,
                        modifier = Modifier.padding(horizontal = LumenLegacySpace.lg, vertical = LumenLegacySpace.sm2),
                    )
                }
            }
        }

        // Channel Switcher Sidebar
        AnimatedVisibility(
            visible = showChannelSwitcher && uiState.error == null,
            enter = PlayerOverlayMotion.fadeIn,
            exit = PlayerOverlayMotion.fadeOut,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(LumenLayout.panelWidthTv)
                    .background(t.colors.card.copy(alpha = 0.95f))
                    .border(1.dp, t.colors.border)
                    .padding(LumenLegacySpace.xxl)
            ) {
                Text(
                    text = "Live Channels",
                    color = t.colors.foreground,
                    fontSize = LumenType.size24,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${channels.size} available",
                    color = t.colors.mutedForeground,
                    fontSize = LumenType.size14
                )
                Spacer(modifier = Modifier.height(LumenLayout.iconMd))
                if (channels.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No live channels available",
                                color = t.colors.mutedForeground,
                                fontSize = LumenType.size16
                            )
                            Spacer(modifier = Modifier.height(LumenLegacySpace.lg))
                            TvFocusable(
                                modifier = Modifier.focusRequester(channelSwitcherFocusRequester),
                                onClick = { showChannelSwitcher = false }
                            ) {
                                Text(
                                    text = "Close",
                                    color = t.colors.foreground,
                                    modifier = Modifier.padding(LumenLegacySpace.md)
                                )
                            }
                        }
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
                        modifier = Modifier.weight(1f)
                    ) {
                        itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                            TvFocusable(
                                onClick = {
                                    activeRequest = com.example.calmsource.feature.iptv.IPTVRepository.buildLivePlaybackRequest(channel)
                                    showChannelSwitcher = false
                                },
                                modifier = if (index == 0) {
                                    Modifier.focusRequester(channelSwitcherFocusRequester).fillMaxWidth()
                                } else {
                                    Modifier.fillMaxWidth()
                                }
                            ) {
                                Text(
                                    text = if (channel.id == liveChannelBaseId(activeRequest.source.id)) {
                                        "Playing  ${channel.name}"
                                    } else {
                                        channel.name
                                    },
                                    color = if (channel.id == liveChannelBaseId(activeRequest.source.id)) {
                                        t.colors.brandGlow
                                    } else {
                                        t.colors.foreground
                                    },
                                    fontWeight = if (channel.id == liveChannelBaseId(activeRequest.source.id)) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    modifier = Modifier.padding(LumenLegacySpace.md),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
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
    val t = LocalLumenTokens.current
    TvFocusable(
        onClick = onClick,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(LumenLegacySpace.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    color = t.colors.foreground,
                    fontWeight = FontWeight.Bold
                )
                subtitle?.let {
                    Text(it, color = t.colors.mutedForeground, fontSize = LumenType.size12)
                }
            }
            if (selected) {
                Text("✓", color = t.colors.brand, fontSize = LumenType.size18)
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
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalLumenTokens.current
    val focusRequester = remember { FocusRequester() }
    
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
            .background(LumenTokens.Color.bg.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = LumenLayout.playerSheetMaxWidth)
        ) {
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

            LumenErrorState(
                title = if (isTerminal) {
                    stringResource(CoreUiR.string.player_all_sources_failed)
                } else {
                    stringResource(CoreUiR.string.player_playback_failed)
                },
                body = explanation + (if (!resolutionError.isNullOrBlank()) "\n$resolutionError" else ""),
                onRetry = onRetry,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(LumenLegacySpace.xxxl))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onTryNext != null) {
                    TvFocusable(
                        onClick = onTryNext,
                        modifier = Modifier.focusRequester(focusRequester)
                    ) {
                        Text(
                            text = "Try next best source",
                            color = t.colors.foreground,
                            fontWeight = FontWeight.Bold,
                            fontSize = LumenType.size14,
                            modifier = Modifier.padding(horizontal = LumenLegacySpace.lg, vertical = LumenLegacySpace.sm2)
                        )
                    }
                }
                
                TvFocusable(
                    onClick = onChooseAnother,
                    modifier = if (onTryNext == null) Modifier.focusRequester(focusRequester) else Modifier
                ) {
                    Text(
                        text = "Choose another source",
                        color = t.colors.foreground,
                        fontWeight = FontWeight.Bold,
                        fontSize = LumenType.size14,
                        modifier = Modifier.padding(horizontal = LumenLegacySpace.lg, vertical = LumenLegacySpace.sm2)
                    )
                }
            }
        }
    }
}
 
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun TvProgressBar(
    progressState: com.example.calmsource.core.model.PlaybackProgressState,
    playbackManager: PlaybackManager,
    focusRequester: FocusRequester,
    onUserInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalLumenTokens.current
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
                .height(LumenLegacySpace.sm2)
                .clip(LumenTokens.Shape.md)
                .background(t.colors.muted.copy(alpha = 0.5f))
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
            if (isProgressBarFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = LumenLegacySpace.xxs,
                            color = t.colors.brand,
                            shape = LumenTokens.Shape.md
                        )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(buffered.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(t.colors.foreground.copy(alpha = 0.35f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(playedFraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(t.colors.brand)
            )
        }
        Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))
        Text(
            text = "${formatTime(progressState.currentPositionMs)} / ${formatTime(progressState.durationMs)}",
            color = t.colors.foreground,
            fontSize = LumenType.size14
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
