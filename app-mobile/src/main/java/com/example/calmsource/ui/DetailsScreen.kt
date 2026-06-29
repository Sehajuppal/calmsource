package com.example.calmsource.ui

import com.example.calmsource.core.ui.theme.*

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedVisibilityScope
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
    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var showRawDetails by remember { mutableStateOf(false) }

    BackHandler(enabled = isAdvancedExpanded) {
        isAdvancedExpanded = false
    }

    val installedExtensions by ExtensionRepository.extensions.collectAsState()
    val activeExtensions = installedExtensions.filter {
        it.isEnabled &&
            it.health != ExtensionHealth.NEEDS_CONFIGURATION &&
            it.health != ExtensionHealth.INVALID_MANIFEST &&
            it.health != ExtensionHealth.FAILED &&
            it.health != ExtensionHealth.SLOW
    }
    val extensionQueryKey = activeExtensions.map { it.id to it.url }
    var currentMediaItem by remember(mediaItem.id) { mutableStateOf(mediaItem) }
    var streamSearchUiState by remember { mutableStateOf(StreamSearchUiState()) }
    val watchOptions = streamSearchUiState.watchOptions
    val subtitlesList = streamSearchUiState.subtitles
    
    var stremioMeta by remember(mediaItem.id) { mutableStateOf<com.example.calmsource.core.model.StremioMeta?>(null) }
    var similarItems by remember(mediaItem.id) { mutableStateOf<List<DiscoveryRecommendationItem>>(emptyList()) }

    val profileId = rememberActiveProfileId()

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
            metadataError = e.message ?: "Failed to load metadata"
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

    var showUnavailableDialog by remember { mutableStateOf(false) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    val isLoadingSources = streamSearchUiState.isLoading
    val extensionErrors = streamSearchUiState.errors
    val context = LocalContext.current.applicationContext

    LaunchedEffect(mediaItem.id) {
        com.example.calmsource.core.playback.StreamPrebufferer.preBufferStream(context, "default", mediaItem.id)
    }

    val dbReady by DatabaseProvider.databaseReady.collectAsState()
    val memoryRepository = remember(context, dbReady) {
        if (!dbReady) {
            FallbackUserMemoryRepository()
        } else runCatching {
            RoomUserMemoryRepository(DatabaseProvider.getDatabase(context))
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
            showUnavailableDialog = true
        } else {
            val provider = ExtensionRepository.getExtensions().find { it.id == option.source.extensionId }
            val isDebridBlocked = option.type == SourceType.DEBRID
                && !option.source.url.startsWith("http://", ignoreCase = true)
                && !option.source.url.startsWith("https://", ignoreCase = true)
                && !com.example.calmsource.feature.debrid.DebridRepository.listAccounts().any { it.isConnected }

            if (provider?.health == ExtensionHealth.NEEDS_CONFIGURATION || isDebridBlocked) {
                showBlockedDialog = true
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
                    }
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

    if (showUnavailableDialog) {
        AlertDialog(
            onDismissRequest = { showUnavailableDialog = false },
            title = { Text("Source Unavailable") },
            text = { Text("The selected stream is missing a valid URL. Please choose another option.") },
            confirmButton = {
                TextButton(onClick = { showUnavailableDialog = false }) { Text("OK") }
            }
        )
    }

    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = { Text(stringResource(CoreUiR.string.details_blocked_title)) },
            text = { Text(stringResource(CoreUiR.string.details_blocked_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showBlockedDialog = false
                    onOpenDebridSettings()
                }) {
                    Text(stringResource(CoreUiR.string.details_connect_debrid))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockedDialog = false }) {
                    Text(stringResource(CoreUiR.string.cta_dismiss))
                }
            }
        )
    }

    val iptvOption = sortedOptions.firstOrNull { it.type == SourceType.IPTV }
    val debridEnhancedOption = sortedOptions.firstOrNull { it.type == SourceType.DEBRID }
    val hindiOption = sortedOptions.firstOrNull { it.source.language.equals("Hindi", ignoreCase = true) && !it.source.isDualAudio }
    val englishOption = sortedOptions.firstOrNull { it.source.language.equals("English", ignoreCase = true) }
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
                    .padding(LumenLegacySpace.xxl),
                verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)
            ) {
                LumenSkeleton(modifier = Modifier.fillMaxWidth().height(LumenLayout.detailsSkeletonHero))
                LumenSkeleton(modifier = Modifier.width(LumenLayout.tileWidthMd).height(LumenLegacySpace.xxxl))
                Row(horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2)) {
                    LumenSkeleton(modifier = Modifier.width(LumenLayout.skeletonChipWidth).height(LumenLegacySpace.xxl))
                    LumenSkeleton(modifier = Modifier.width(LumenLayout.skeletonChipWidth).height(LumenLegacySpace.xxl))
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
                    bottom = LumenLegacySpace.xxxl + 88.dp,
                )
            ) {
                item(key = "title_block") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LumenLegacySpace.xxl)
                    ) {
                        // Title / Logo
                        if (!stremioMeta?.logo.isNullOrEmpty()) {
                            AsyncImage(
                                model = stremioMeta?.logo,
                                contentDescription = currentMediaItem.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .padding(vertical = LumenLegacySpace.md)
                                    .height(LumenLayout.avatarLg)
                            )
                        } else {
                            Text(
                                text = currentMediaItem.title,
                                style = LumenType.H1.toTextStyle(),
                                color = t.colors.foreground,
                                modifier = Modifier.padding(vertical = LumenLegacySpace.md)
                            )
                        }

                        // Meta Chips (Year · Runtime · Rating)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = LumenLegacySpace.md)
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
                                modifier = Modifier.padding(bottom = LumenLegacySpace.md)
                            )
                        }

                        // Genres (read-only labels)
                        stremioMeta?.genres?.let { genres ->
                            if (genres.isNotEmpty()) {
                                GenreLabelRow(
                                    genres = genres,
                                    modifier = Modifier.offset(x = (-LumenLegacySpace.xxl)),
                                )
                            }
                        }

                        // Stream status (play actions live in sticky bottom bar)
                        if (bestMatch == null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = LumenLegacySpace.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2)
                        ) {
                            if (isLoadingSources) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(LumenLayout.iconMd),
                                        color = t.colors.brand,
                                        strokeWidth = 2.dp,
                                    )
                                    Text(
                                        text = stringResource(CoreUiR.string.details_finding_streams),
                                        color = t.colors.mutedForeground,
                                        fontSize = LumenType.size14,
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm),
                                ) {
                                    Text(
                                        text = stringResource(CoreUiR.string.details_no_streams),
                                        color = t.colors.foreground,
                                        fontSize = LumenType.size14,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (extensionErrors.isNotEmpty()) {
                                        extensionErrors.take(2).forEach { err ->
                                            Text(
                                                text = err,
                                                color = LumenExtendedColors.errorBright,
                                                fontSize = LumenType.size12,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }

                        // Synopsis (Expandable)
                        var isExpanded by remember { mutableStateOf(false) }
                        val overviewText = currentMediaItem.overview ?: stremioMeta?.description ?: "No description available."
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = LumenLegacySpace.sm2)) {
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
                                    text = if (isExpanded) "Show Less" else "Read More",
                                    color = t.colors.brand,
                                    fontSize = LumenType.size14,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { isExpanded = !isExpanded }
                                        .padding(top = LumenLegacySpace.sm)
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
                                    .padding(vertical = LumenLegacySpace.sm2)
                            ) {
                                Text(
                                    text = "Seasons",
                                    fontSize = LumenType.size18,
                                    fontWeight = FontWeight.Bold,
                                    color = t.colors.foreground,
                                    modifier = Modifier.padding(start = LumenLegacySpace.xxl, end = LumenLegacySpace.xxl, bottom = LumenLegacySpace.md)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = LumenLegacySpace.xxl),
                                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
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
                                    .padding(vertical = LumenLegacySpace.md)
                            ) {
                                Text(
                                    text = "Episodes",
                                    fontSize = LumenType.size18,
                                    fontWeight = FontWeight.Bold,
                                    color = t.colors.foreground,
                                    modifier = Modifier.padding(start = LumenLegacySpace.xxl, end = LumenLegacySpace.xxl, bottom = LumenLegacySpace.md)
                                )
                                LazyRow(
                                    state = currentSeasonScrollState,
                                    contentPadding = PaddingValues(horizontal = LumenLegacySpace.xxl),
                                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md),
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
                            text = "Subtitles: $langs",
                                    fontSize = LumenType.size13,
                                    color = LumenExtendedColors.statusHealthy,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = LumenLegacySpace.xxl, vertical = LumenLegacySpace.md)
                        )
                    }
                }

                // More Like This
                if (similarItems.isNotEmpty()) {
                    item(key = "similar_items") {
                        RowSection(
                            title = "More Like This",
                            modifier = Modifier.padding(top = LumenLegacySpace.lg)
                        ) {
                            LumenHorizontalRowFade {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = LumenLegacySpace.xxl),
                                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)
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

                // Alternative options
                item(key = "alternative_options_header") {
                    Text(
                        text = "Ways to Watch",
                        fontSize = LumenType.size18,
                        fontWeight = FontWeight.SemiBold,
                        color = t.colors.foreground,
                        modifier = Modifier.padding(start = LumenLegacySpace.xxl, end = LumenLegacySpace.xxl, top = LumenLegacySpace.xl, bottom = LumenLegacySpace.md)
                    )
                }

                item(key = "alternative_options") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
                        modifier = Modifier.padding(start = LumenLegacySpace.xxl, end = LumenLegacySpace.xxl, bottom = LumenLegacySpace.xxl)
                    ) {
                        val iptvRes = remember(iptvOption) { iptvOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) } }
                        if (iptvOption != null && iptvRes != null) {
                            LumenGhostButton(
                                text = "IPTV Option (${iptvRes.displayLabel.primaryLabel})",
                                onClick = { handlePlayOption(iptvOption, false) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        val extOption = remember(sortedOptions) { sortedOptions.firstOrNull { it.type == SourceType.EXTENSION } }
                        val extRes = remember(extOption) { extOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) } }
                        if (extOption != null && extRes != null) {
                            LumenGhostButton(
                                text = "Extension Option (${extRes.displayLabel.primaryLabel})",
                                onClick = { handlePlayOption(extOption, false) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        val primaryLangRes = remember(hindiOption) { hindiOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) } }
                        if (hindiOption != null && primaryLangRes != null) {
                            LumenGhostButton(
                                text = "Primary Language (${primaryLangRes.displayLabel.primaryLabel})",
                                onClick = { handlePlayOption(hindiOption, false) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        val dualRes = remember(dualAudioOption) { dualAudioOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) } }
                        if (dualAudioOption != null && dualRes != null) {
                            LumenGhostButton(
                                text = "Dual-Audio (${dualRes.displayLabel.primaryLabel})",
                                onClick = { handlePlayOption(dualAudioOption, false) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        val lowDataOption = remember(sortedOptions) {
                            sortedOptions.firstOrNull { 
                                val r = com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput())
                                r.rankingFeatures.isLowDataSuitable
                            }
                        }
                        val lowDataRes = remember(lowDataOption) { lowDataOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) } }
                        if (lowDataOption != null && lowDataRes != null) {
                            LumenGhostButton(
                                text = "Low-Data Option (${lowDataRes.displayLabel.primaryLabel})",
                                onClick = { handlePlayOption(lowDataOption, false) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Collapsible Advanced Panel
                item(key = "advanced_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                            .padding(horizontal = LumenLegacySpace.xxl, vertical = LumenLegacySpace.md)
                    ) {
                        Text(
                            text = "Source Controls · ${sortedOptions.size}",
                            fontSize = LumenType.size16,
                            fontWeight = FontWeight.Bold,
                            color = t.colors.foreground,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isAdvancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isAdvancedExpanded) "Collapse sources" else "Expand sources",
                            tint = t.colors.foreground
                        )
                    }
                }

                if (isAdvancedExpanded) {
                    item(key = "show_raw_toggle") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = LumenLegacySpace.xxl, end = LumenLegacySpace.xxl, bottom = LumenLegacySpace.sm2)
                        ) {
                            Text(
                                text = "Show technical details",
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
                            horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = LumenLegacySpace.xxl, end = LumenLegacySpace.xxl, bottom = LumenLegacySpace.md)
                        ) {
                            Text(
                                text = "Sort Strategy:",
                                color = t.colors.mutedForeground,
                                fontSize = LumenType.size12,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(LumenTokens.Shape.lg)
                                    .background(if (sortingPreference == SortingPreference.BEST_MATCH) t.colors.brand else t.colors.muted)
                                    .clickable { sortingPreference = SortingPreference.BEST_MATCH }
                                    .padding(horizontal = LumenLegacySpace.md, vertical = LumenLegacySpace.sm)
                            ) {
                                Text(
                                    text = "Best Match",
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
                                    .padding(horizontal = LumenLegacySpace.md, vertical = LumenLegacySpace.sm)
                            ) {
                                Text(
                                    text = "Highest Quality",
                                    color = if (sortingPreference == SortingPreference.HIGHEST_QUALITY) t.colors.brandForeground else t.colors.mutedForeground,
                                    fontSize = LumenType.size11,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    items(sortedOptionsWithScores, key = { it.option.id }) { scored ->
                        Box(modifier = Modifier.padding(start = LumenLegacySpace.xxl, end = LumenLegacySpace.xxl, bottom = LumenTokens.Radius.sm)) {
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
                    .padding(LumenLegacySpace.md),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(LumenTokens.Shape.pill)
                        .background(t.colors.muted.copy(alpha = 0.9f)),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = t.colors.foreground,
                    )
                }
            }

            if (bestMatch != null && metadataError == null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, t.colors.background.copy(alpha = 0.98f)),
                            ),
                        )
                        .padding(horizontal = LumenLegacySpace.xxl, vertical = LumenLegacySpace.md),
                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
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

@Composable
private fun MetaChip(
    text: String,
    color: Color = LocalLumenTokens.current.colors.mutedForeground
) {
    val t = LocalLumenTokens.current
    Box(
        modifier = Modifier
            .clip(LumenTokens.Shape.xs)
            .background(t.colors.muted)
            .padding(horizontal = LumenLegacySpace.sm2, vertical = LumenLegacySpace.xs)
    ) {
        Text(
            text = text,
            fontSize = LumenType.size11,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun EpisodeRow(
    video: StremioVideo,
    backdropUrl: String?,
    isSelected: Boolean,
    progress: Float?,
    onClick: () -> Unit
) {
    val t = LocalLumenTokens.current
    Column(
        modifier = Modifier
            .width(LumenLayout.channelPanelWidth)
            .clickable(onClick = onClick)
    ) {
        PosterCard(
            imageUrl = video.displayImageUrl(backdropUrl),
            orientation = PosterOrientation.Landscape,
            progress = progress,
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = video.episodeDisplayLabel(video.season ?: 1),
            color = if (isSelected) t.colors.brand else t.colors.foreground,
            fontSize = LumenType.size13,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = LumenLegacySpace.sm2)
        )
    }
}

@Composable
fun ManualSourceItem(
    option: WatchOption,
    score: Int,
    scoreReasons: List<String> = emptyList(),
    health: SourceHealth?,
    showRawDetails: Boolean,
    onClick: () -> Unit
) {
    val t = LocalLumenTokens.current
    val result = remember(option) { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(option.toRawSourceInput()) }

    LumenCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.xs),
            modifier = Modifier.padding(LumenLegacySpace.md)
        ) {
            Text(
                text = if (showRawDetails) com.example.calmsource.core.network.UrlRedactor.redactFilename(option.source.name) else result.displayLabel.primaryLabel,
                fontSize = LumenType.size14,
                fontWeight = FontWeight.Bold,
                color = t.colors.foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (showRawDetails) {
                Text(
                    text = com.example.calmsource.core.network.UrlRedactor.redactUrl(option.source.url),
                    fontSize = LumenType.size11,
                    color = t.colors.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (result.displayLabel.secondaryLabel.isNotEmpty()) {
                Text(
                    text = result.displayLabel.secondaryLabel,
                    fontSize = LumenType.size12,
                    color = t.colors.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SourceBadge(kind = option.type.toBadgeKind())
                
                val parsedInfo = remember(option.source) {
                    StreamParserUtil.smartParseAll(option.source.rawTitle ?: option.source.name, option.source.extensionId)
                }

                val extensionName = parsedInfo.sourceExtensionName ?: option.source.sourceExtensionName
                if (!extensionName.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(LumenTokens.Shape.xs)
                            .background(t.colors.brand.copy(alpha = 0.2f))
                            .padding(horizontal = LumenLegacySpace.sm2, vertical = LumenLegacySpace.xs)
                    ) {
                        Text(
                            text = extensionName,
                            color = t.colors.brandGlow,
                            fontSize = LumenType.size10,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                val quality = parsedInfo.quality
                val sizeStr = WatchOptionResolver.formatFileSize(parsedInfo.fileSizeBytes)
                Box(
                    modifier = Modifier
                        .clip(LumenTokens.Shape.xs)
                        .background(t.colors.muted)
                        .padding(horizontal = LumenLegacySpace.sm2, vertical = LumenLegacySpace.xs)
                ) {
                    Text(
                        text = "[$quality] [$sizeStr]",
                        color = t.colors.foreground,
                        fontSize = LumenType.size10,
                        fontWeight = FontWeight.Bold
                    )
                }

                val hdrBadge = parsedInfo.hdrFormat
                if (hdrBadge != null) {
                    Box(
                        modifier = Modifier
                            .clip(LumenTokens.Shape.xs)
                            .background(LumenExtendedColors.ratingGold.copy(alpha = 0.24f))
                            .padding(horizontal = LumenLegacySpace.sm2, vertical = LumenLegacySpace.xs)
                    ) {
                        Text(
                            text = "[$hdrBadge]",
                            color = LumenExtendedColors.ratingGold,
                            fontSize = LumenType.size10,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val codecBadge = parsedInfo.videoCodec ?: option.source.videoCodec
                if (!codecBadge.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(LumenTokens.Shape.xs)
                            .background(LumenExtendedColors.cyan.copy(alpha = 0.24f))
                            .padding(horizontal = LumenLegacySpace.sm2, vertical = LumenLegacySpace.xs)
                    ) {
                        Text(
                            text = "[$codecBadge]",
                            color = LumenExtendedColors.cyan,
                            fontSize = LumenType.size10,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val audioBadge = parsedInfo.audioCodec ?: option.source.audioCodec
                if (!audioBadge.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(LumenTokens.Shape.xs)
                            .background(LumenExtendedColors.violet.copy(alpha = 0.24f))
                            .padding(horizontal = LumenLegacySpace.sm2, vertical = LumenLegacySpace.xs)
                    ) {
                        Text(
                            text = "[$audioBadge]",
                            color = LumenExtendedColors.violet,
                            fontSize = LumenType.size10,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                val tier = health?.reliabilityTier ?: SourceReliabilityTier.EXCELLENT
                val (labelText, labelColor) = when (tier) {
                    SourceReliabilityTier.EXCELLENT, SourceReliabilityTier.GOOD -> "Reliable" to LumenExtendedColors.statusHealthy
                    SourceReliabilityTier.UNSTABLE, SourceReliabilityTier.POOR -> "Unstable" to LumenTokens.Color.warning
                    SourceReliabilityTier.BLOCKED -> "Failed recently" to LumenExtendedColors.errorBright
                    else -> "Reliable" to LumenExtendedColors.statusHealthy
                }
                Text(
                    text = labelText,
                    color = labelColor,
                    fontSize = LumenType.size11,
                    fontWeight = FontWeight.Bold
                )
                
                val parsedSeeds = parsedInfo.seeds ?: option.source.seeds
                if (parsedSeeds != null) {
                    Text(
                        text = "Seeds: $parsedSeeds",
                        fontSize = LumenType.size11,
                        color = LumenTokens.Color.success
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Score: $score",
                    fontSize = LumenType.size11,
                    color = t.colors.brand,
                    fontWeight = FontWeight.Bold
                )
            }
            if (showRawDetails && scoreReasons.isNotEmpty()) {
                Text(
                    text = scoreReasons.joinToString(" · "),
                    fontSize = LumenType.size10,
                    color = t.colors.mutedForeground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (health != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md),
                    modifier = Modifier.fillMaxWidth().padding(top = LumenLegacySpace.xs)
                ) {
                    Text(text = "Failures: ${health.failureCount}", fontSize = LumenType.size10, color = t.colors.mutedForeground)
                    Text(text = "Startup: ${health.averageStartupTime}ms", fontSize = LumenType.size10, color = t.colors.mutedForeground)
                    Text(text = "Buffering: ${String.format("%.1f", health.averageBufferingSeverity)}", fontSize = LumenType.size10, color = t.colors.mutedForeground)
                }
            }
        }
    }
}

private fun SourceType.toBadgeKind(): SourceBadgeKind = when (this) {
    SourceType.IPTV -> SourceBadgeKind.IPTV
    SourceType.EXTENSION -> SourceBadgeKind.EXTENSION
    SourceType.DEBRID -> SourceBadgeKind.DEBRID
}

private fun MediaItem.toDiscoveryMediaItem(): DiscoveryMediaItem {
    return DiscoveryMediaItem(
        id = id,
        type = when (type) {
            MediaType.MOVIE -> "movie"
            MediaType.SHOW -> "series"
        },
        title = title,
        overview = overview,
        posterUrl = posterUrl,
        rating = rating,
        releaseYear = releaseDate?.take(4)?.toIntOrNull(),
        externalIds = externalIds,
        source = "details"
    )
}

private suspend fun recordTasteSignals(
    memoryRepository: com.example.calmsource.core.database.repository.UserMemoryRepository,
    mediaItem: MediaItem,
    stremioMeta: StremioMeta?,
    profileId: String,
) {
    runCatching {
        stremioMeta?.genres?.forEach { genre ->
            val key = genre.trim().lowercase()
            if (key.isNotBlank()) {
                memoryRepository.incrementPreferenceSignal(
                    signalType = UserPreferenceSignalType.GENRE,
                    signalKey = key,
                    profileId = profileId,
                )
            }
        }
        memoryRepository.incrementPreferenceSignal(
            signalType = UserPreferenceSignalType.CONTENT_TYPE,
            signalKey = if (mediaItem.type == MediaType.SHOW) "series" else "movie",
            profileId = profileId,
        )
    }
}

fun selectBestMatch(options: List<WatchOption>, scores: Map<String, Int>): WatchOption? {
    val sizeCap = 20L * 1024 * 1024 * 1024L  // 20GB cap
    val cappedOptions = options.filter { option ->
        val size = option.source.sizeBytes
        size == null || size < sizeCap
    }
    val candidates = if (cappedOptions.isNotEmpty()) cappedOptions else options

    return candidates.maxByOrNull { option ->
        var score = scores[option.id] ?: 0
        when (option.source.resolution) {
            "1080p" -> score += 50
            "4K" -> score += 30
            "720p" -> score += 10
        }
        when (option.source.videoCodec?.uppercase()) {
            "AV1", "HEVC" -> score += 20
            "H264", "AVC" -> score += 5
        }
        val seeds = option.source.seeds ?: 0
        if (seeds > 50) score += 20
        else if (seeds > 10) score += 10

        val sizeBytes = option.source.sizeBytes ?: 0L
        when {
            sizeBytes in 2_000_000_000L..8_000_000_000L -> score += 30
            sizeBytes in 8_000_000_001L..15_000_000_000L -> score += 15
            sizeBytes in 1L..1_999_999_999L -> score -= 10
        }
        score
    }
}

fun selectHighestQuality(options: List<WatchOption>, scores: Map<String, Int>): WatchOption? {
    return options.maxByOrNull { option ->
        var score = scores[option.id] ?: 0
        when (option.source.resolution) {
            "4K" -> score += 100
            "1080p" -> score += 50
        }
        val sizeBytes = option.source.sizeBytes ?: 0L
        when {
            sizeBytes >= 40_000_000_000L -> score += 100
            sizeBytes >= 20_000_000_000L -> score += 50
            sizeBytes >= 10_000_000_000L -> score += 20
        }
        val nameLower = option.source.name.lowercase()
        val sourceTitleLower = option.title.lowercase()
        val combined = "$nameLower $sourceTitleLower"
        when {
            combined.contains("dolby vision") || combined.contains("dv") -> score += 50
            combined.contains("hdr10+") || combined.contains("hdr10plus") -> score += 30
            combined.contains("hdr10") || combined.contains("hdr") -> score += 10
        }
        when {
            combined.contains("atmos") || combined.contains("truehd") -> score += 30
            combined.contains("dts-hd") || combined.contains("dts:x") || combined.contains("dtsx") -> score += 20
            combined.contains("e-ac3") || combined.contains("dd+") || combined.contains("dolby digital plus") -> score += 10
        }
        when (option.source.videoCodec?.uppercase()) {
            "AV1" -> score += 30
            "HEVC" -> score += 20
        }
        score
    }
}
