package com.example.calmsource.ui

import com.example.calmsource.core.ui.theme.*

import com.example.calmsource.core.data.rememberActiveProfileId
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.example.calmsource.core.ui.R as CoreUiR
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import com.example.calmsource.core.discoveryengine.DiscoveryEngine
import com.example.calmsource.core.discoveryengine.models.MediaItem as DiscoveryMediaItem
import com.example.calmsource.core.discoveryengine.models.RecommendationItem as DiscoveryRecommendationItem
import com.example.calmsource.core.model.*
import com.example.calmsource.core.model.isResourceSupported
import com.example.calmsource.core.database.SourceHealthRepository
import com.example.calmsource.core.sourceintelligence.models.toRawSourceInput
import com.example.calmsource.core.sourceintelligence.ranking.DeviceStreamProfile
import com.example.calmsource.core.sourceintelligence.ranking.StreamScoringSupport
import com.example.calmsource.core.sourceintelligence.ranking.ScoredWatchOption
import com.example.calmsource.core.sourceintelligence.ranking.WatchOptionScoring
import com.example.calmsource.feature.search.SearchEngine
import com.example.calmsource.core.database.repository.UserPreferencesRepository
import com.example.calmsource.feature.extensions.ExtensionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.repository.RoomUserMemoryRepository
import com.example.calmsource.core.database.repository.FallbackUserMemoryRepository
import com.example.calmsource.core.ui.components.*

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DetailsScreen(
    mediaItem: MediaItem,
    startPositionMs: Long = 0L,
    onBack: () -> Unit,
    onPlayOption: (PlaybackRequest, List<PlaybackSource>, Boolean) -> Unit,
    onOpenMedia: (MediaItem) -> Unit = {},
    onOpenDebridSettings: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedPosterKey: String? = null,
) {
    val t = LocalLumenTokens.current
    var isSourcesExpanded by remember { mutableStateOf(false) }
    var showRawDetails by remember { mutableStateOf(false) }

    BackHandler(enabled = isSourcesExpanded) {
        isSourcesExpanded = false
    }

    val installedExtensions by ExtensionRepository.extensions.collectAsState()
    val activeExtensions = remember(installedExtensions) {
        installedExtensions.filter {
            it.isEnabled &&
                it.health != ExtensionHealth.NEEDS_CONFIGURATION &&
                it.health != ExtensionHealth.INVALID_MANIFEST &&
                it.health != ExtensionHealth.FAILED &&
                it.health != ExtensionHealth.SLOW
        }
    }
    val extensionQueryKey = activeExtensions.map { it.id to it.url }
    var currentMediaItem by remember(mediaItem.id) { mutableStateOf(mediaItem) }
    var streamSearchUiState by remember { mutableStateOf(StreamSearchUiState()) }
    val watchOptions = streamSearchUiState.watchOptions
    val subtitlesList = streamSearchUiState.subtitles
    
    var stremioMeta by remember(mediaItem.id) { mutableStateOf<com.example.calmsource.core.model.StremioMeta?>(null) }
    var similarItems by remember(mediaItem.id) { mutableStateOf<List<DiscoveryRecommendationItem>>(emptyList()) }

    val profileId = rememberActiveProfileId()
    val appContext = LocalContext.current.applicationContext

    LaunchedEffect(mediaItem.id, profileId) {
        similarItems = runCatching {
            withContext(Dispatchers.IO) {
                DiscoveryEngine.getMoreLikeThis(profileId = profileId, itemId = mediaItem.id)
            }
        }.getOrDefault(emptyList())
    }

    var isLoadingMeta by remember(mediaItem.id) { mutableStateOf(false) }
    var metadataError by remember(mediaItem.id) { mutableStateOf<String?>(null) }
    val retryTrigger = remember { mutableStateOf(0) }

    DisposableEffect(mediaItem.id) {
        DiscoveryEngine.enrichItem(mediaItem.toDiscoveryMediaItem())
        onDispose { DiscoveryEngine.cancelPendingForMedia(mediaItem.id) }
    }

    LaunchedEffect(mediaItem.id, extensionQueryKey, retryTrigger.value) {
        isLoadingMeta = true
        metadataError = null
        try {
            val metadata = ExtensionRepository.refreshMediaMetadata(mediaItem, activeExtensions)
            currentMediaItem = metadata.mediaItem
            stremioMeta = metadata.primaryMeta
        } catch (e: Exception) {
            metadataError = e.message ?: appContext.getString(CoreUiR.string.error_unknown)
        } finally {
            isLoadingMeta = false
        }
    }

    val displayEpisodes = remember(stremioMeta) {
        stremioMeta?.videos.orEmpty().displayableEpisodes()
    }

    val seasons = remember(displayEpisodes) {
        displayEpisodes.displayableSeasons()
    }

    var selectedSeason by remember(seasons) { mutableIntStateOf(seasons.firstOrNull() ?: 1) }

    val episodesForSelectedSeason = remember(displayEpisodes, selectedSeason) {
        displayEpisodes
            .filter { it.season == selectedSeason }
    }

    var selectedEpisode by remember(episodesForSelectedSeason) {
        mutableStateOf(episodesForSelectedSeason.firstOrNull())
    }

    LaunchedEffect(selectedSeason, episodesForSelectedSeason) {
        if (selectedEpisode?.season != selectedSeason) {
            selectedEpisode = episodesForSelectedSeason.firstOrNull()
        }
    }

    val selectedEpisodeId = remember(mediaItem, selectedEpisode) {
        if (mediaItem.type == MediaType.SHOW) {
            selectedEpisode?.let { ep ->
                ep.id ?: "${mediaItem.id}:${ep.season ?: 1}:${ep.episode ?: 1}"
            } ?: "${mediaItem.id}:1:1"
        } else {
            mediaItem.id
        }
    }
    
    val sourceHealths = remember { mutableStateMapOf<String, SourceHealth>() }

    var detailsNotice by remember { mutableStateOf<DetailsNotice?>(null) }
    val isLoadingSources = streamSearchUiState.isLoading
    val extensionErrors = streamSearchUiState.errors

    LaunchedEffect(mediaItem.id) {
        com.example.calmsource.core.playback.StreamPrebufferer.preBufferStream(appContext, "default", mediaItem.id)
    }

    val dbReady by DatabaseProvider.databaseReady.collectAsState()
    val memoryRepository = remember(appContext, dbReady) {
        if (!dbReady) {
            FallbackUserMemoryRepository()
        } else runCatching {
            RoomUserMemoryRepository(DatabaseProvider.getDatabase(appContext))
        }.getOrElse { e ->
            runCatching {
                android.util.Log.e("DetailsScreen", "Failed to initialize RoomUserMemoryRepository", e)
            }
            FallbackUserMemoryRepository()
        }
    }
    val memoryReference = remember(mediaItem.id, mediaItem.title, mediaItem.type) {
        com.example.calmsource.feature.iptv.IPTVRepository.findChannel(mediaItem.id)
            ?.toUserMemoryReference()
            ?: mediaItem.toUserMemoryReference()
    }
    val isFavorite by remember(profileId, memoryReference.itemKey) {
        memoryRepository.observeIsFavorite(memoryReference.itemKey, profileId)
    }.collectAsState(initial = false)
    val memoryScope = rememberCoroutineScope()

    val continueWatchingItems by remember(profileId) {
        memoryRepository.observeContinueWatching(profileId)
    }.collectAsState(initial = emptyList())
    val progressMap = remember(continueWatchingItems) {
        continueWatchingItems.associate { it.reference.itemKey to (it.progressMs.toFloat() / it.durationMs.coerceAtLeast(1L)) }
    }

    var sortingPreference by remember { mutableStateOf(SortingPreference.BEST_MATCH) }
    val preferences by UserPreferencesRepository.preferences.collectAsState(initial = UserPreferences())
    var sortedOptionsWithScores by remember { mutableStateOf<List<ScoredWatchOption>>(emptyList()) }
    var sortedOptions by remember { mutableStateOf<List<WatchOption>>(emptyList()) }

    val watchOptionsList = remember(watchOptions, mediaItem.type, selectedEpisode) {
        val list = watchOptions.toList()
        val episode = selectedEpisode
        if (mediaItem.type == MediaType.SHOW && episode != null) {
            val s = episode.season
            val e = episode.episode
            val sZero = s.toString().padStart(2, '0')
            val eZero = e.toString().padStart(2, '0')
            val patterns = listOf(
                "s${sZero}e${eZero}",
                "s${s}e${e}",
                "${s}x${eZero}",
                "${s}x${e}",
                "season $s episode $e"
            )
            list.filter { option ->
                if (option.source.extensionId.startsWith("iptv-") || option.source.extensionId == "iptv") {
                    val nameLower = option.title.lowercase()
                    patterns.any { nameLower.contains(it) }
                } else {
                    true
                }
            }
        } else {
            list
        }
    }
    LaunchedEffect(watchOptionsList, preferences, sortingPreference, sourceHealths) {
        val calculated = withContext(Dispatchers.IO) {
            val extensions = ExtensionRepository.getExtensions().associateBy { it.id }
            val healthByOptionId = watchOptionsList.associate { option ->
                val healthKey = StreamScoringSupport.healthKeyForWatchOption(option)
                option.id to (
                    sourceHealths[option.id]
                        ?: SourceHealthRepository.getSourceHealth(healthKey, readonly = true)
                    )
            }
            val providerHealthByExtension = watchOptionsList
                .map { it.source.extensionId }
                .distinct()
                .associateWith { extensionId ->
                    SourceHealthRepository.getProviderHealth(extensionId, readonly = true)
                }
            WatchOptionScoring.scoreWatchOptionsDetailed(
                options = watchOptionsList,
                strategy = sortingPreference,
                prefs = preferences,
                deviceProfile = DeviceStreamProfile.forPlayback(isTelevision = false, prefs = preferences),
                signalsFor = { option ->
                    val extension = extensions[option.source.extensionId]
                    val providerHealth = when (extension?.health) {
                        ExtensionHealth.ACTIVE -> ProviderHealth.HEALTHY
                        ExtensionHealth.SLOW -> ProviderHealth.SLOW
                        ExtensionHealth.FAILED,
                        ExtensionHealth.DISABLED,
                        ExtensionHealth.INVALID_MANIFEST -> ProviderHealth.FAILED
                        else -> ProviderHealth.HEALTHY
                    }
                    StreamScoringSupport.signalsFromHealth(
                        sourceHealth = healthByOptionId[option.id],
                        providerHealth = providerHealth,
                        providerPriority = extension?.priority,
                        providerHealthScore = providerHealthByExtension[option.source.extensionId]?.healthScore,
                    )
                }
            )
        }
        sortedOptionsWithScores = calculated
        sortedOptions = calculated.map { it.option }
    }

    LaunchedEffect(watchOptions.toList()) {
        watchOptions.forEach { option ->
            if (!sourceHealths.containsKey(option.id)) {
                val healthKey = if (option.type == SourceType.IPTV) {
                    generateSafeSourceId(option.source.url)
                } else {
                    option.id
                }
                val health = com.example.calmsource.core.database.SourceHealthRepository.getSourceHealth(healthKey)
                if (health != null) {
                    sourceHealths[option.id] = health
                }
            }
        }
    }

    val handlePlayOption = { option: WatchOption, playBestIntent: Boolean ->
        if (option.source.url.isBlank()) {
            detailsNotice = DetailsNotice.SourceUnavailable
        } else {
            val provider = ExtensionRepository.getExtensions().find { it.id == option.source.extensionId }
            val isDebridBlocked = option.type == SourceType.DEBRID
                && !option.source.url.startsWith("http://", ignoreCase = true)
                && !option.source.url.startsWith("https://", ignoreCase = true)
                && !com.example.calmsource.feature.debrid.DebridRepository.listAccounts().any { it.isConnected }

            if (provider?.health == ExtensionHealth.NEEDS_CONFIGURATION || isDebridBlocked) {
                detailsNotice = DetailsNotice.SourceBlocked
            } else {
                val request = PlaybackRequest(
                    source = PlaybackSource(
                        id = option.id,
                        type = when (option.type) {
                            SourceType.IPTV -> PlaybackSourceType.IPTV
                            SourceType.EXTENSION -> PlaybackSourceType.EXTENSION
                            SourceType.DEBRID -> PlaybackSourceType.DEBRID_RESOLVED
                        },
                        title = WatchOptionResolver.cleanStreamTitle(option.title, null, option.type.name),
                        rawUrl = option.source.url,
                        metadata = PlaybackItemMetadata(
                            title = if (mediaItem.type == MediaType.SHOW && selectedEpisode != null) 
                                "${currentMediaItem.title} - S${selectedEpisode?.season}E${selectedEpisode?.episode}: ${selectedEpisode?.title}" 
                                else currentMediaItem.title,
                            posterUrl = currentMediaItem.posterUrl,
                            backdropUrl = currentMediaItem.backdropUrl,
                            isLive = option.type == SourceType.IPTV &&
                                    option.source.resolution.equals("Live", ignoreCase = true),
                            seriesName = if (mediaItem.type == MediaType.SHOW) currentMediaItem.title else null,
                            seasonNumber = selectedEpisode?.season,
                            episodeNumber = selectedEpisode?.episode,
                            containerFormat = option.source.name.substringAfterLast('.', "").takeIf { it.length in 2..5 },
                            videoCodec = option.source.videoCodec,
                            audioCodec = option.source.audioCodec
                        ),
                        headers = option.source.headers,
                        allowInsecureHttp = (option.type == SourceType.IPTV &&
                            option.source.url.startsWith("xtream://")) ||
                            ((option.type == SourceType.EXTENSION || option.type == SourceType.DEBRID) &&
                                option.source.url.startsWith("http://", ignoreCase = true)),
                        stableSourceId = stableSourceIdForWatchOption(
                            option.type,
                            option.source.url,
                            option.id
                        )
                    ),
                    startPositionMs = startPositionMs,
                    userMemoryReference = if (
                        option.type == SourceType.IPTV &&
                        option.source.resolution.equals("Live", ignoreCase = true)
                    ) {
                        com.example.calmsource.feature.iptv.IPTVRepository.findChannel(currentMediaItem.id)
                            ?.toUserMemoryReference()
                    } else {
                        UserMemoryReference(
                            itemKey = if (mediaItem.type == MediaType.SHOW) selectedEpisodeId else currentMediaItem.id,
                            contentType = if (mediaItem.type == MediaType.SHOW) UserMemoryContentType.SHOW else UserMemoryContentType.MOVIE,
                            title = if (mediaItem.type == MediaType.SHOW && selectedEpisode != null) 
                                "${currentMediaItem.title} - S${selectedEpisode?.season}E${selectedEpisode?.episode}" 
                                else currentMediaItem.title
                        )
                    },
                    seriesContext = if (mediaItem.type == MediaType.SHOW) {
                        com.example.calmsource.core.model.SeriesPlaybackContext(
                            seriesId = currentMediaItem.id,
                            seriesTitle = currentMediaItem.title,
                            posterUrl = currentMediaItem.posterUrl,
                            backdropUrl = currentMediaItem.backdropUrl,
                        )
                    } else {
                        null
                    },
                )
                
                val fallbackCandidates = sortedOptions.filter { it.id != option.id }.take(5).map { opt ->
                    PlaybackSource(
                        id = opt.id,
                        type = when (opt.type) {
                            SourceType.IPTV -> PlaybackSourceType.IPTV
                            SourceType.EXTENSION -> PlaybackSourceType.EXTENSION
                            SourceType.DEBRID -> PlaybackSourceType.DEBRID_RESOLVED
                        },
                        title = WatchOptionResolver.cleanStreamTitle(opt.title, null, opt.type.name),
                        rawUrl = opt.source.url,
                        metadata = PlaybackItemMetadata(
                            title = if (mediaItem.type == MediaType.SHOW && selectedEpisode != null) 
                                "${currentMediaItem.title} - S${selectedEpisode?.season}E${selectedEpisode?.episode}: ${selectedEpisode?.title}" 
                                else currentMediaItem.title,
                            posterUrl = currentMediaItem.posterUrl,
                            backdropUrl = currentMediaItem.backdropUrl,
                            isLive = opt.type == SourceType.IPTV &&
                                    opt.source.resolution.equals("Live", ignoreCase = true),
                            containerFormat = opt.source.name.substringAfterLast('.', "").takeIf { it.length in 2..5 },
                            videoCodec = opt.source.videoCodec,
                            audioCodec = opt.source.audioCodec
                        ),
                        headers = opt.source.headers,
                        allowInsecureHttp = (opt.type == SourceType.IPTV &&
                            opt.source.url.startsWith("xtream://")) ||
                            ((opt.type == SourceType.EXTENSION || opt.type == SourceType.DEBRID) &&
                                opt.source.url.startsWith("http://", ignoreCase = true)),
                        stableSourceId = stableSourceIdForWatchOption(
                            opt.type,
                            opt.source.url,
                            opt.id
                        )
                    )
                }
                
                onPlayOption(request, fallbackCandidates, playBestIntent)
            }
        }
    }

    LaunchedEffect(mediaItem.id, extensionQueryKey, selectedEpisodeId) {
        streamSearchUiState = StreamSearchUiState(isLoading = true)
        val localSources = com.example.calmsource.feature.iptv.IPTVRepository
            .findIptvStreamSources(
                mediaItem.id,
                mediaItem.title
            )
            .distinctBy { it.id }
        val localOptions = WatchOptionResolver.buildWatchOptions(localSources)
        streamSearchUiState = streamSearchUiState.copy(watchOptions = localOptions)

        val type = if (mediaItem.type == MediaType.SHOW) "series" else "movie"
        val streamId = selectedEpisodeId

        val streamsJob = launch {
            ExtensionRepository.lookupMediaStreams(mediaItem, activeExtensions, episodeId = selectedEpisodeId)
                .collect { extensionResolution ->
                    val extensionOptions = WatchOptionResolver.buildWatchOptions(extensionResolution.streamSources)
                    val extensionNewOptions = mutableListOf<WatchOption>()
                    extensionOptions.forEach { option ->
                        val matchesInfoHash = option.source.url.startsWith("magnet:") && (
                            streamSearchUiState.watchOptions.any {
                                it.source.url.startsWith("magnet:") &&
                                    it.source.url.substringAfter("btih:") == option.source.url.substringAfter("btih:")
                            } || extensionNewOptions.any {
                                it.source.url.startsWith("magnet:") &&
                                    it.source.url.substringAfter("btih:") == option.source.url.substringAfter("btih:")
                            }
                        )
                        val matchesUrl = option.source.url.isNotEmpty() && !option.source.url.startsWith("magnet:") && (
                            streamSearchUiState.watchOptions.any { it.source.url == option.source.url } ||
                                extensionNewOptions.any { it.source.url == option.source.url }
                        )
                        val matchesId = streamSearchUiState.watchOptions.any { it.id == option.id } || extensionNewOptions.any { it.id == option.id }
                        if (!matchesInfoHash && !matchesUrl && !matchesId) {
                            extensionNewOptions.add(option)
                        }
                    }
                    val updatedOptions = streamSearchUiState.watchOptions + extensionNewOptions
                    streamSearchUiState = streamSearchUiState.copy(
                        watchOptions = updatedOptions,
                        errors = extensionResolution.errors,
                        failedExtensions = extensionResolution.failedExtensions
                    )
                }
        }

        val subtitleJob = launch(Dispatchers.IO) {
            val extensionSemaphore = Semaphore(4)
            val sourceJobs = mutableListOf<kotlinx.coroutines.Job>()

            activeExtensions.forEach { provider ->
                sourceJobs += launch {
                    extensionSemaphore.withPermit {
                        val hasSubtitles = provider.capabilities.contains(ExtensionCapability.SubtitleProvider) && provider.manifest?.isResourceSupported("subtitles", type) == true
                        if (!hasSubtitles) return@withPermit
                        
                        val isDemo = provider.url.contains("legal-demo.com") ||
                                provider.url.contains("slowaddon.org") ||
                                provider.url.contains("failedaddon.com")
                        if (isDemo) return@withPermit

                        try {
                            val resolvedBase = com.example.calmsource.core.network.StremioAddonClient.resolveUrl(provider.url, provider.id).removeSuffix("/manifest.json")
                            val timeoutMs = 15_000L
                            val subRes = com.example.calmsource.core.network.StremioAddonClient.getSubtitles(resolvedBase, type, streamId, provider.id, timeoutMs)
                            if (subRes is com.example.calmsource.core.network.StremioResult.Success) {
                                withContext(Dispatchers.Main) {
                                    val currentSubs = streamSearchUiState.subtitles
                                    val newSubs = (subRes.data.subtitles ?: emptyList()).filter { sub ->
                                        currentSubs.none { it.id == sub.id || it.url == sub.url }
                                    }
                                    if (newSubs.isNotEmpty()) {
                                        streamSearchUiState = streamSearchUiState.copy(subtitles = currentSubs + newSubs)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                        }
                    }
                }
            }
            sourceJobs.joinAll()
        }

        joinAll(streamsJob, subtitleJob)
        streamSearchUiState = streamSearchUiState.copy(isLoading = false)
    }

    val bestMatch = sortedOptions.firstOrNull()

    val iptvOption = sortedOptions.firstOrNull { it.type == SourceType.IPTV }
    val hindiOption = sortedOptions.firstOrNull { it.source.language.equals("Hindi", ignoreCase = true) && !it.source.isDualAudio }
    val dualAudioOption = sortedOptions.firstOrNull { it.source.isDualAudio }

    // Scroll state for Parallax effect
    val lazyListState = rememberLazyListState()
    val parallaxOffset = remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex == 0) {
                lazyListState.firstVisibleItemScrollOffset / 2f
            } else {
                0f
            }
        }
    }

    val isReducedMotion = rememberReducedMotion()
    val sharedBoundsTransform = rememberLumenSharedBoundsTransform()
    val isSharedPosterTransition =
        sharedPosterKey != null && sharedPosterKey == currentMediaItem.id
    val backdropAlpha = remember(currentMediaItem.id, isSharedPosterTransition) {
        Animatable(if (isSharedPosterTransition && !isReducedMotion) 0f else 1f)
    }
    LaunchedEffect(currentMediaItem.id, isSharedPosterTransition, isReducedMotion) {
        if (isSharedPosterTransition && !isReducedMotion) {
            backdropAlpha.snapTo(0f)
            backdropAlpha.animateTo(
                targetValue = 1f,
                animationSpec = LumenDelightMotion.detailsBackdropFadeSpec(isReducedMotion),
            )
        } else {
            backdropAlpha.snapTo(1f)
        }
    }

    // Color extraction for the Adaptive Play Button
    var backdropLuminance by remember(currentMediaItem.id) { mutableStateOf(0.5f) }

    // Cached states for each season to keep scroll position
    val seasonScrollStates = remember { mutableMapOf<Int, LazyListState>() }
    val currentSeasonScrollState = seasonScrollStates.getOrPut(selectedSeason) { LazyListState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
    ) {
        if (metadataError != null && stremioMeta == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LumenErrorState(
                    title = stringResource(CoreUiR.string.error_load_details),
                    body = metadataError ?: stringResource(CoreUiR.string.error_load_feed_body),
                    onRetry = { retryTrigger.value++ }
                )
            }
        } else if (isLoadingMeta && stremioMeta == null) {
            // Loading Skeletons
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(LumenTokens.Space.lg),
                verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.md)
            ) {
                LumenSkeleton(modifier = Modifier.fillMaxWidth().height(LumenLayout.detailsSkeletonHero))
                LumenSkeleton(modifier = Modifier.width(LumenLayout.tileWidthMd).height(LumenTokens.Space.xl))
                Row(horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm)) {
                    LumenSkeleton(modifier = Modifier.width(LumenLayout.skeletonChipWidth).height(LumenTokens.Space.lg))
                    LumenSkeleton(modifier = Modifier.width(LumenLayout.skeletonChipWidth).height(LumenTokens.Space.lg))
                }
                LumenSkeleton(modifier = Modifier.fillMaxWidth().height(LumenLayout.epgMinBlockWidth))
            }
        } else {
            // Full-bleed Backdrop Hero with bottom-up gradient scrim
            val backdropSharedModifier =
                if (
                    sharedTransitionScope != null &&
                    animatedVisibilityScope != null &&
                    sharedPosterKey != null &&
                    sharedPosterKey == currentMediaItem.id
                ) {
                    with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            rememberSharedContentState(key = "poster-$sharedPosterKey"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            resizeMode = scaleToBounds(),
                            boundsTransform = sharedBoundsTransform,
                        )
                    }
                } else {
                    Modifier
                }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LumenLayout.detailsHeroHeight)
                    .then(backdropSharedModifier)
            ) {
                AsyncImage(
                    model = currentMediaItem.backdropUrl ?: currentMediaItem.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = backdropAlpha.value
                            translationY = parallaxOffset.value
                        },
                    onSuccess = { state ->
                        val drawable = state.result.drawable
                        if (drawable is android.graphics.drawable.BitmapDrawable) {
                            val bitmap = drawable.bitmap
                            Palette.from(bitmap).generate { palette ->
                                val dominantColor = palette?.getDominantColor(0xFF000000.toInt()) ?: 0xFF000000.toInt()
                                val r = android.graphics.Color.red(dominantColor) / 255f
                                val g = android.graphics.Color.green(dominantColor) / 255f
                                val b = android.graphics.Color.blue(dominantColor) / 255f
                                backdropLuminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
                            }
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(t.scrimGradient())
                )
            }

            // Scrollable Content
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = LumenLayout.detailsContentTop,
                    bottom = LumenTokens.Space.xl + 88.dp,
                )
            ) {
                item(key = "title_block") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LumenTokens.Space.lg)
                    ) {
                        // Title / Logo
                        if (!stremioMeta?.logo.isNullOrEmpty()) {
                            AsyncImage(
                                model = stremioMeta?.logo,
                                contentDescription = currentMediaItem.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .padding(vertical = LumenTokens.Space.s5)
                                    .height(LumenLayout.avatarLg)
                            )
                        } else {
                            Text(
                                text = currentMediaItem.title,
                                style = LumenType.H1.toTextStyle(),
                                color = t.colors.foreground,
                                modifier = Modifier.padding(vertical = LumenTokens.Space.s5)
                            )
                        }

                        // Meta Chips (Year · Runtime · Rating)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = LumenTokens.Space.s5)
                        ) {
                            val year = currentMediaItem.releaseDate?.substringBefore("-") ?: stremioMeta?.releaseInfo ?: ""
                            val rating = currentMediaItem.rating?.toString() ?: stremioMeta?.imdbRating
                            val duration = stremioMeta?.runtime

                            if (year.isNotBlank()) {
                                MetaChip(text = year)
                            }
                            if (!duration.isNullOrBlank()) {
                                MetaChip(text = duration)
                            }
                            if (!rating.isNullOrBlank()) {
                                MetaChip(text = "★ $rating", color = LumenExtendedColors.ratingGold)
                            }
                        }

                        // Editorial Tagline (1-2 lines)
                        val tagline = stremioMeta?.description?.substringBefore(".") ?: ""
                        if (tagline.isNotBlank() && tagline.length > 5) {
                            Text(
                                text = tagline,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = t.colors.mutedForeground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = LumenTokens.Space.s5)
                            )
                        }

                        // Genres (read-only labels)
                        stremioMeta?.genres?.let { genres ->
                            if (genres.isNotEmpty()) {
                                GenreLabelRow(
                                    genres = genres,
                                    modifier = Modifier.offset(x = (-LumenTokens.Space.lg)),
                                )
                            }
                        }

                        if (bestMatch == null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = LumenTokens.Space.s5),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm),
                            ) {
                                if (isLoadingSources) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(LumenLayout.iconMd),
                                        color = t.colors.brand,
                                        strokeWidth = 2.dp,
                                    )
                                    Text(
                                        text = stringResource(CoreUiR.string.details_finding_streams),
                                        color = t.colors.mutedForeground,
                                        style = LumenType.Body.toTextStyle(),
                                    )
                                } else {
                                    Text(
                                        text = stringResource(CoreUiR.string.details_no_streams),
                                        color = t.colors.mutedForeground,
                                        style = LumenType.Body.toTextStyle(),
                                    )
                                }
                            }
                        }

                        // Synopsis (Expandable)
                        var isExpanded by remember { mutableStateOf(false) }
                        val overviewText = currentMediaItem.overview ?: stremioMeta?.description
                            ?: stringResource(CoreUiR.string.details_no_description)
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = LumenTokens.Space.sm)) {
                            Text(
                                text = overviewText,
                                fontSize = LumenType.size14,
                                color = t.colors.mutedForeground,
                                lineHeight = LumenType.size20,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (overviewText.length > 120) {
                                Text(
                                    text = if (isExpanded) {
                                        stringResource(CoreUiR.string.details_show_less)
                                    } else {
                                        stringResource(CoreUiR.string.details_read_more)
                                    },
                                    color = t.colors.brand,
                                    fontSize = LumenType.size14,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { isExpanded = !isExpanded }
                                        .padding(top = LumenTokens.Space.s3)
                                )
                            }
                        }
                    }
                }

                // Seasons & Episodes
                if (mediaItem.type == MediaType.SHOW) {
                    item(key = "seasons_section") {
                        if (seasons.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = LumenTokens.Space.sm)
                            ) {
                                Text(
                                    text = stringResource(CoreUiR.string.details_seasons),
                                    fontSize = LumenType.size18,
                                    fontWeight = FontWeight.Bold,
                                    color = t.colors.foreground,
                                    modifier = Modifier.padding(start = LumenTokens.Space.lg, end = LumenTokens.Space.lg, bottom = LumenTokens.Space.s5)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = LumenTokens.Space.lg),
                                    horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(seasons) { season ->
                                        FilterChip(
                                            selected = selectedSeason == season,
                                            onClick = { selectedSeason = season },
                                            label = { Text(seasonDisplayLabel(season)) },
                                            shape = LumenTokens.Shape.pill,
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = t.colors.muted,
                                                selectedContainerColor = t.colors.brand,
                                                labelColor = t.colors.foreground,
                                                selectedLabelColor = t.colors.brandForeground
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "episodes_section") {
                        if (episodesForSelectedSeason.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = LumenTokens.Space.s5)
                            ) {
                                Text(
                                    text = stringResource(CoreUiR.string.details_episodes),
                                    fontSize = LumenType.size18,
                                    fontWeight = FontWeight.Bold,
                                    color = t.colors.foreground,
                                    modifier = Modifier.padding(start = LumenTokens.Space.lg, end = LumenTokens.Space.lg, bottom = LumenTokens.Space.s5)
                                )
                                LazyRow(
                                    state = currentSeasonScrollState,
                                    contentPadding = PaddingValues(horizontal = LumenTokens.Space.lg),
                                    horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.s5),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(episodesForSelectedSeason, key = { it.id ?: "${it.season}:${it.episode}" }) { video ->
                                        val isSelected = selectedEpisode?.episode == video.episode
                                        val epId = video.id ?: "${mediaItem.id}:${video.season ?: 1}:${video.episode ?: 1}"
                                        val progress = progressMap[epId]

                                        EpisodeRow(
                                            video = video,
                                            backdropUrl = currentMediaItem.backdropUrl ?: currentMediaItem.posterUrl,
                                            isSelected = isSelected,
                                            progress = progress,
                                            onClick = { selectedEpisode = video }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Subtitle Availability badge
                if (subtitlesList.isNotEmpty()) {
                    item(key = "subtitles") {
                        val langs = subtitlesList.map { it.lang }.distinct().joinToString(", ")
                        Text(
                            text = stringResource(CoreUiR.string.details_subtitles, langs),
                                    fontSize = LumenType.size13,
                                    color = LumenExtendedColors.statusHealthy,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = LumenTokens.Space.lg, vertical = LumenTokens.Space.s5)
                        )
                    }
                }

                // More Like This
                if (similarItems.isNotEmpty()) {
                    item(key = "similar_items") {
                        RowSection(
                            title = stringResource(CoreUiR.string.details_more_like_this),
                            modifier = Modifier.padding(top = LumenTokens.Space.md)
                        ) {
                            LumenHorizontalRowFade {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = LumenTokens.Space.lg),
                                    horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.s5)
                                ) {
                                    items(similarItems, key = { "similar-${it.id}" }) { item ->
                                        val similarMedia = MediaItem(
                                            id = item.id,
                                            title = item.title,
                                            type = if (item.type == "series") MediaType.SHOW else MediaType.MOVIE,
                                            overview = item.reason,
                                            posterUrl = item.posterUrl,
                                            externalIds = item.externalIds
                                        )
                                        PosterCard(
                                            imageUrl = similarMedia.posterUrl,
                                            contentLabel = similarMedia.title,
                                            onClick = { onOpenMedia(similarMedia) },
                                            modifier = Modifier.width(LumenLayout.epgMinBlockWidth)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "sources_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSourcesExpanded = !isSourcesExpanded }
                            .padding(horizontal = LumenTokens.Space.lg, vertical = LumenTokens.Space.s5),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(CoreUiR.string.details_sources_title, sortedOptions.size),
                                style = LumenType.Title.toTextStyle(),
                                color = t.colors.foreground,
                            )
                            if (!isSourcesExpanded && extensionErrors.isNotEmpty()) {
                                Text(
                                    text = stringResource(CoreUiR.string.details_extension_errors),
                                    style = LumenType.Caption.toTextStyle(),
                                    color = LumenExtendedColors.errorBright,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else if (!isSourcesExpanded && bestMatch == null && !isLoadingSources) {
                                Text(
                                    text = stringResource(CoreUiR.string.details_open_sources_hint),
                                    style = LumenType.Caption.toTextStyle(),
                                    color = t.colors.mutedForeground,
                                )
                            }
                        }
                        Icon(
                            imageVector = if (isSourcesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isSourcesExpanded) {
                                stringResource(CoreUiR.string.details_collapse_sources)
                            } else {
                                stringResource(CoreUiR.string.details_expand_sources)
                            },
                            tint = t.colors.foreground,
                        )
                    }
                }

                if (isSourcesExpanded) {
                    item(key = "sources_extension_errors") {
                        if (extensionErrors.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = LumenTokens.Space.lg, vertical = LumenTokens.Space.sm),
                                verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.xs),
                            ) {
                                Text(
                                    text = stringResource(CoreUiR.string.details_extension_errors),
                                    style = LumenType.Caption.toTextStyle(),
                                    color = LumenExtendedColors.errorBright,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                extensionErrors.take(3).forEach { err ->
                                    Text(
                                        text = err,
                                        style = LumenType.Caption.toTextStyle(),
                                        color = LumenExtendedColors.errorBright,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    item(key = "sources_alt_options") {
                        AlternativeWatchOptions(
                            iptvOption = iptvOption,
                            sortedOptions = sortedOptions,
                            hindiOption = hindiOption,
                            dualAudioOption = dualAudioOption,
                            onPlay = { handlePlayOption(it, false) },
                            modifier = Modifier.padding(
                                start = LumenTokens.Space.lg,
                                end = LumenTokens.Space.lg,
                                bottom = LumenTokens.Space.s5,
                            ),
                        )
                    }

                    item(key = "show_raw_toggle") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = LumenTokens.Space.lg, end = LumenTokens.Space.lg, bottom = LumenTokens.Space.sm)
                        ) {
                            Text(
                                text = stringResource(CoreUiR.string.details_show_technical),
                                color = t.colors.foreground,
                                fontSize = LumenType.size14,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = showRawDetails,
                                onCheckedChange = { showRawDetails = it }
                            )
                        }
                    }

                    item(key = "sort_strategies") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = LumenTokens.Space.lg, end = LumenTokens.Space.lg, bottom = LumenTokens.Space.s5)
                        ) {
                            Text(
                                text = stringResource(CoreUiR.string.details_sort_strategy),
                                color = t.colors.mutedForeground,
                                fontSize = LumenType.size12,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(LumenTokens.Shape.lg)
                                    .background(if (sortingPreference == SortingPreference.BEST_MATCH) t.colors.brand else t.colors.muted)
                                    .clickable { sortingPreference = SortingPreference.BEST_MATCH }
                                    .padding(horizontal = LumenTokens.Space.s5, vertical = LumenTokens.Space.s3)
                            ) {
                                Text(
                                    text = stringResource(CoreUiR.string.details_sort_best_match),
                                    color = if (sortingPreference == SortingPreference.BEST_MATCH) t.colors.brandForeground else t.colors.mutedForeground,
                                    fontSize = LumenType.size11,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(LumenTokens.Shape.lg)
                                    .background(if (sortingPreference == SortingPreference.HIGHEST_QUALITY) t.colors.brand else t.colors.muted)
                                    .clickable { sortingPreference = SortingPreference.HIGHEST_QUALITY }
                                    .padding(horizontal = LumenTokens.Space.s5, vertical = LumenTokens.Space.s3)
                            ) {
                                Text(
                                    text = stringResource(CoreUiR.string.details_sort_highest_quality),
                                    color = if (sortingPreference == SortingPreference.HIGHEST_QUALITY) t.colors.brandForeground else t.colors.mutedForeground,
                                    fontSize = LumenType.size11,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    items(sortedOptionsWithScores, key = { it.option.id }) { scored ->
                        Box(modifier = Modifier.padding(start = LumenTokens.Space.lg, end = LumenTokens.Space.lg, bottom = LumenTokens.Radius.sm)) {
                            ManualSourceItem(
                                option = scored.option,
                                score = scored.score,
                                scoreReasons = scored.breakdown.topReasons,
                                health = sourceHealths[scored.option.id],
                                showRawDetails = showRawDetails,
                                onClick = { handlePlayOption(scored.option, false) }
                            )
                        }
                    }

                    item(key = "advanced_bottom_spacer") {
                        Spacer(modifier = Modifier.height(LumenLayout.spacerMd))
                    }
                }
            }
        }

        val showDetailsChrome = !(isLoadingMeta && stremioMeta == null && metadataError == null)
        if (showDetailsChrome) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(LumenTokens.Space.s5),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(LumenTokens.Shape.pill)
                        .background(t.colors.muted.copy(alpha = 0.9f)),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(CoreUiR.string.cta_back),
                        tint = t.colors.foreground,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                when (val notice = detailsNotice) {
                    DetailsNotice.SourceUnavailable -> {
                        LumenInlineMessage(
                            message = stringResource(CoreUiR.string.details_source_unavailable),
                            onDismiss = { detailsNotice = null },
                            modifier = Modifier
                                .padding(horizontal = LumenTokens.Space.lg, vertical = LumenTokens.Space.sm),
                        )
                    }
                    DetailsNotice.SourceBlocked -> {
                        DetailsBlockedNotice(
                            title = stringResource(CoreUiR.string.details_blocked_title),
                            body = stringResource(CoreUiR.string.details_blocked_body),
                            actionLabel = stringResource(CoreUiR.string.details_connect_debrid),
                            onAction = {
                                detailsNotice = null
                                onOpenDebridSettings()
                            },
                            onDismiss = { detailsNotice = null },
                            modifier = Modifier
                                .padding(horizontal = LumenTokens.Space.lg, vertical = LumenTokens.Space.sm),
                        )
                    }
                    null -> Unit
                }

                if (bestMatch != null && metadataError == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, t.colors.background.copy(alpha = 0.98f)),
                            ),
                        )
                        .padding(horizontal = LumenTokens.Space.lg, vertical = LumenTokens.Space.s5),
                    horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AdaptiveButton(
                            text = if (startPositionMs > 0L) {
                                stringResource(CoreUiR.string.cta_resume)
                            } else {
                                stringResource(CoreUiR.string.cta_play)
                            },
                        onClick = { handlePlayOption(bestMatch, true) },
                        backdropLuminance = backdropLuminance,
                        modifier = Modifier.weight(1f),
                    )
                        LumenGhostButton(
                            text = if (isFavorite) {
                                stringResource(CoreUiR.string.details_my_list_saved)
                            } else {
                                stringResource(CoreUiR.string.details_my_list_add)
                            },
                        onClick = {
                            val wasFavorite = isFavorite
                            memoryScope.launch {
                                runCatching {
                                    memoryRepository.toggleFavorite(memoryReference, profileId = profileId)
                                }
                                if (!wasFavorite) {
                                    recordTasteSignals(memoryRepository, currentMediaItem, stremioMeta, profileId)
                                }
                            }
                        },
                    )
                }
                }
            }
        }
    }
}

