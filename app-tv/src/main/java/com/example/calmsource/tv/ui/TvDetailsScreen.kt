package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.*

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusGroup
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import com.example.calmsource.core.discoveryengine.DiscoveryEngine
import com.example.calmsource.core.discoveryengine.models.MediaItem as DiscoveryMediaItem
import com.example.calmsource.core.model.*
import com.example.calmsource.core.model.isResourceSupported
import com.example.calmsource.core.sourceintelligence.models.toRawSourceInput
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

@Composable
fun TvDetailsScreen(
    mediaItem: MediaItem,
    startPositionMs: Long = 0L,
    onBack: () -> Unit,
    onPlayOption: (PlaybackRequest, List<PlaybackSource>, Boolean) -> Unit,
    onOpenMedia: (MediaItem) -> Unit = {}
) {
    val t = LocalLumenTokens.current
    var showRawDetails by remember { mutableStateOf(false) }
    
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
    var isLoadingMeta by remember(mediaItem.id) { mutableStateOf(false) }
    var metadataError by remember(mediaItem.id) { mutableStateOf<String?>(null) }
    val retryTrigger = remember { mutableStateOf(0) }
    var similarItems by remember(mediaItem.id) {
        mutableStateOf<List<com.example.calmsource.core.discoveryengine.models.RecommendationItem>>(emptyList())
    }

    LaunchedEffect(mediaItem.id) {
        similarItems = runCatching {
            withContext(Dispatchers.IO) {
                DiscoveryEngine.getMoreLikeThis(profileId = "default", itemId = mediaItem.id)
            }
        }.getOrDefault(emptyList())
    }

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
    
    val dbReady by DatabaseProvider.databaseReady.collectAsState()
    val memoryRepository = remember(context, dbReady) {
        if (!dbReady) {
            FallbackUserMemoryRepository()
        } else runCatching {
            RoomUserMemoryRepository(DatabaseProvider.getDatabase(context))
        }.getOrElse { e ->
            runCatching {
                android.util.Log.e("TvDetailsScreen", "Failed to initialize RoomUserMemoryRepository", e)
            }
            FallbackUserMemoryRepository()
        }
    }
    val memoryReference = remember(mediaItem.id, mediaItem.title, mediaItem.type) {
        com.example.calmsource.feature.iptv.IPTVRepository.findChannel(mediaItem.id)
            ?.toUserMemoryReference()
            ?: mediaItem.toUserMemoryReference()
    }
    val isFavorite by memoryRepository.observeIsFavorite(memoryReference.itemKey)
        .collectAsState(initial = false)
    val memoryScope = rememberCoroutineScope()

    val continueWatchingItems by memoryRepository.observeContinueWatching().collectAsState(initial = emptyList())
    val progressMap = remember(continueWatchingItems) {
        continueWatchingItems.associate { it.reference.itemKey to (it.progressMs.toFloat() / it.durationMs.coerceAtLeast(1L)) }
    }

    var sortingPreference by remember { mutableStateOf(SortingPreference.BEST_MATCH) }
    val preferences by UserPreferencesRepository.preferences.collectAsState(initial = UserPreferences())
    var sortedOptionsWithScores by remember { mutableStateOf<List<Pair<WatchOption, Int>>>(emptyList()) }
    var sortedOptions by remember { mutableStateOf<List<WatchOption>>(emptyList()) }

    val watchOptionsList = watchOptions.toList()
    LaunchedEffect(watchOptionsList, preferences, sortingPreference) {
        val calculated = withContext(Dispatchers.Default) {
            watchOptionsList.map { option ->
                option to WatchOptionResolver.calculateScore(option.source, sortingPreference).toInt()
            }.sortedByDescending { it.second }
        }
        sortedOptionsWithScores = calculated
        sortedOptions = calculated.map { it.first }
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



    val bestMatch = sortedOptions.firstOrNull()

    if (showUnavailableDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showUnavailableDialog = false }) {
            Box(modifier = Modifier.clip(LumenTokens.Shape.md).background(t.colors.card).padding(LumenLegacySpace.xxl)) {
                Column {
                    Text("Source Unavailable", fontSize = LumenType.size20, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                    Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))
                    Text("The selected stream is missing a valid URL. Please choose another option.", fontSize = LumenType.size14, color = t.colors.mutedForeground)
                    Spacer(modifier = Modifier.height(LumenLegacySpace.lg))
                    TvFocusable(onClick = { showUnavailableDialog = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("OK", color = t.colors.foreground, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = LumenLegacySpace.lg, vertical = LumenLegacySpace.sm2))
                    }
                }
            }
        }
    }

    if (showBlockedDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showBlockedDialog = false }) {
            Box(modifier = Modifier.clip(LumenTokens.Shape.md).background(t.colors.card).padding(LumenLegacySpace.xxl)) {
                Column {
                    Text("Configuration Required", fontSize = LumenType.size20, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                    Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))
                    Text("This source requires configuration or authentication. Please update your settings.", fontSize = LumenType.size14, color = t.colors.mutedForeground)
                    Spacer(modifier = Modifier.height(LumenLegacySpace.lg))
                    TvFocusable(onClick = { showBlockedDialog = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("OK", color = t.colors.foreground, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = LumenLegacySpace.lg, vertical = LumenLegacySpace.sm2))
                    }
                }
            }
        }
    }

    val iptvOption = sortedOptions.firstOrNull { it.type == SourceType.IPTV }
    val extensionOption = sortedOptions.firstOrNull { it.type == SourceType.EXTENSION }
    val dualAudioOption = sortedOptions.firstOrNull { it.source.isDualAudio }
    val primaryLangOption = sortedOptions.firstOrNull { it.source.language.equals("Hindi", ignoreCase = true) && !it.source.isDualAudio }
        ?: sortedOptions.firstOrNull { it.source.language.equals("English", ignoreCase = true) }
    val lowDataOption = remember(sortedOptions) {
        sortedOptions.firstOrNull { 
            val r = com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput())
            r.rankingFeatures.isLowDataSuitable
        }
    }

    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var isAdvancedExpandedInitialized by remember { mutableStateOf(false) }

    val playBestFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }
    val advancedToggleFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isLoadingSources) {
        if (!isLoadingSources && sortedOptions.isNotEmpty()) {
            try {
                playBestFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus request failures
            }
        }
    }

    LaunchedEffect(isAdvancedExpanded) {
        if (isAdvancedExpanded) {
            isAdvancedExpandedInitialized = true
            if (sortedOptions.isNotEmpty()) {
                try {
                    firstItemFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Ignore focus request failures
                }
            }
        } else {
            if (isAdvancedExpandedInitialized) {
                try {
                    advancedToggleFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Ignore focus request failures
                }
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = isAdvancedExpanded) {
        isAdvancedExpanded = false
    }

    // Cached states for each season to keep scroll position
    val seasonScrollStates = remember { mutableMapOf<Int, LazyListState>() }
    val currentSeasonScrollState = seasonScrollStates.getOrPut(selectedSeason) { LazyListState() }

    // Color extraction for play button
    var backdropLuminance by remember(currentMediaItem.id) { mutableStateOf(0.5f) }

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
                    title = "Failed to load details",
                    body = metadataError ?: "Unknown error",
                    onRetry = { retryTrigger.value++ }
                )
            }
        } else if (isLoadingMeta && stremioMeta == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(LumenLayout.iconXl),
                verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.xxl)
            ) {
                LumenSkeleton(modifier = Modifier.width(LumenLayout.skeletonTitleWidth).height(LumenLayout.offsetLg))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.xxxl),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LumenSkeleton(modifier = Modifier.size(LumenLayout.heroStripHeight, LumenLayout.posterHeightTv))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)
                    ) {
                        LumenSkeleton(modifier = Modifier.fillMaxWidth().height(LumenLayout.iconXl))
                        LumenSkeleton(modifier = Modifier.width(LumenLayout.channelPanelWidth).height(LumenLegacySpace.xxl))
                        LumenSkeleton(modifier = Modifier.fillMaxWidth().height(LumenLayout.inputWidthSm))
                    }
                }
            }
        } else {
            // TV static backdrop background with vertical gradient scrim
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = currentMediaItem.backdropUrl ?: currentMediaItem.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = LumenLayout.iconXl),
            contentPadding = PaddingValues(top = LumenLegacySpace.xxxxl, bottom = LumenLegacySpace.xxxxl),
            verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)
        ) {
            // Back focus card
            item(key = "back_header") {
                TvFocusable(
                    onClick = onBack,
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(
                        text = "← Back to Home",
                        color = t.colors.foreground,
                        fontSize = LumenType.size14,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = LumenLegacySpace.lg, vertical = LumenLegacySpace.sm2)
                    )
                }
            }

            item(key = "details_row") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.xxxl),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Left column: Poster
                    AsyncImage(
                        model = currentMediaItem.posterUrl,
                        contentDescription = "Poster for ${currentMediaItem.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(LumenLayout.heroStripHeight, LumenLayout.posterHeightTv)
                            .clip(LumenTokens.Shape.md)
                            .background(LumenTokens.Color.glass)
                    )

                    // Right column: VOD Info + metadata + actions
                    Column(modifier = Modifier.weight(1f)) {
                        if (!stremioMeta?.logo.isNullOrEmpty()) {
                            AsyncImage(
                                model = stremioMeta?.logo,
                                contentDescription = currentMediaItem.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .padding(vertical = LumenLegacySpace.sm2)
                                    .height(LumenLayout.bottomNavPadding)
                            )
                        } else {
                            Text(
                                text = currentMediaItem.title,
                                fontSize = LumenType.size36,
                                fontWeight = FontWeight.Bold,
                                color = t.colors.foreground
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = LumenLegacySpace.sm2)
                        ) {
                            val year = currentMediaItem.releaseDate ?: stremioMeta?.releaseInfo ?: ""
                            Text(text = year, color = t.colors.mutedForeground, fontSize = LumenType.size14)
                            
                            currentMediaItem.rating?.let {
                                Text(text = "IMDb: ★ $it", color = LumenExtendedColors.ratingGold, fontSize = LumenType.size14, fontWeight = FontWeight.Bold)
                            }
                            stremioMeta?.rtRating?.let {
                                Text(text = "RT: 🍅 $it", color = LumenExtendedColors.errorBright, fontSize = LumenType.size14, fontWeight = FontWeight.Bold)
                            }
                            stremioMeta?.metascore?.let {
                                Text(text = "Metascore: Ⓜ️ $it", color = LumenExtendedColors.statusHealthy, fontSize = LumenType.size14, fontWeight = FontWeight.Bold)
                            }
                        }

                        val tagline = stremioMeta?.description?.substringBefore(".") ?: ""
                        if (tagline.isNotBlank() && tagline.length > 5) {
                            Text(
                                text = tagline,
                                fontSize = LumenType.size16,
                                fontWeight = FontWeight.Medium,
                                color = t.colors.mutedForeground,
                                modifier = Modifier.padding(bottom = LumenLegacySpace.sm2)
                            )
                        }

                        Text(
                            text = currentMediaItem.overview ?: stremioMeta?.description ?: "",
                            color = t.colors.mutedForeground,
                            fontSize = LumenType.size14,
                            lineHeight = LumenType.size20,
                            modifier = Modifier.padding(bottom = LumenLegacySpace.md)
                        )

                        // Action Buttons: Play (Adaptive Contrast) & Favorite
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = LumenLegacySpace.lg)
                        ) {
                            if (bestMatch != null) {
                                val isLightBackdrop = backdropLuminance > 0.55f
                                val controlBg = if (isLightBackdrop) LumenExtendedColors.controlScrimDark else LumenExtendedColors.controlScrimLight
                                val controlFg = if (isLightBackdrop) LumenTokens.Color.textPrimary else LumenTokens.Color.bg

                                TvFocusable(
                                    onClick = { handlePlayOption(bestMatch, true) },
                                    modifier = Modifier.focusRequester(playBestFocusRequester)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .background(controlBg, LumenTokens.Shape.pill)
                                            .padding(horizontal = LumenLegacySpace.xxl, vertical = LumenLegacySpace.md)
                                    ) {
                                        Text(
                                            text = if (startPositionMs > 0L) "Resume" else "Play",
                                            color = controlFg,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = LumenType.size15
                                        )
                                    }
                                }
                            }

                            TvFocusable(
                                onClick = {
                                    val wasFavorite = isFavorite
                                    memoryScope.launch {
                                        runCatching {
                                            memoryRepository.toggleFavorite(memoryReference)
                                        }
                                        if (!wasFavorite) {
                                            recordTasteSignals(memoryRepository, currentMediaItem, stremioMeta)
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    text = if (isFavorite) "✓ My List" else "+ My List",
                                    color = t.colors.foreground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = LumenType.size15,
                                    modifier = Modifier.padding(horizontal = LumenLegacySpace.xl, vertical = LumenLegacySpace.md)
                                )
                            }
                        }
                    }
                }
            }

            // TV Seasons & Episodes horizontal section
            if (mediaItem.type == MediaType.SHOW) {
                item(key = "seasons_row") {
                    if (seasons.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2)) {
                            Text("Seasons", fontSize = LumenType.size20, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm)) {
                                items(seasons) { season ->
                                    val isSelected = selectedSeason == season
                                    TvFocusable(
                                        onClick = { selectedSeason = season }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (isSelected) t.colors.brand else t.colors.muted,
                                                    LumenTokens.Shape.pill
                                                )
                                                .padding(horizontal = LumenLegacySpace.lg, vertical = LumenLegacySpace.sm2)
                                        ) {
                                            Text(
                                                text = seasonDisplayLabel(season),
                                                color = if (isSelected) t.colors.brandForeground else t.colors.foreground,
                                                fontSize = LumenType.size14,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "episodes_row") {
                    if (episodesForSelectedSeason.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)) {
                            Text("Episodes", fontSize = LumenType.size20, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                            LazyRow(
                                state = currentSeasonScrollState,
                                horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(episodesForSelectedSeason, key = { it.id ?: "${it.season}:${it.episode}" }) { video ->
                                    val isSelected = selectedEpisode?.episode == video.episode
                                    val epId = video.id ?: "${mediaItem.id}:${video.season ?: 1}:${video.episode ?: 1}"
                                    val progress = progressMap[epId]

                                    TvFocusable(
                                        onClick = { selectedEpisode = video },
                                        modifier = Modifier.width(LumenLayout.channelPanelWidth)
                                    ) {
                                        Column {
                                            PosterCard(
                                                imageUrl = currentMediaItem.backdropUrl ?: currentMediaItem.posterUrl,
                                                orientation = PosterOrientation.Landscape,
                                                progress = progress,
                                                onClick = { selectedEpisode = video },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Text(
                                                text = "Episode ${video.episode}: ${video.title ?: ""}",
                                                color = if (isSelected) t.colors.brand else t.colors.foreground,
                                                fontSize = LumenType.size13,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = LumenLegacySpace.sm2)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // More Like This
            if (similarItems.isNotEmpty()) {
                item(key = "similar_items_row") {
                    Column(verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)) {
                        Text("More Like This", fontSize = LumenType.size20, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)) {
                            items(similarItems, key = { "similar-${it.id}" }) { item ->
                                val similarMedia = MediaItem(
                                    id = item.id,
                                    title = item.title,
                                    type = if (item.type == "series") MediaType.SHOW else MediaType.MOVIE,
                                    overview = item.reason,
                                    posterUrl = item.posterUrl,
                                    externalIds = item.externalIds
                                )
                                TvFocusable(
                                    onClick = { onOpenMedia(similarMedia) },
                                    modifier = Modifier.width(LumenLayout.epgMinBlockWidthTv)
                                ) {
                                    Column {
                                        PosterCard(
                                            imageUrl = similarMedia.posterUrl,
                                            onClick = { onOpenMedia(similarMedia) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = similarMedia.title,
                                            color = t.colors.foreground,
                                            fontSize = LumenType.size13,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = LumenLegacySpace.sm)
                                        )
                                    }
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
                    fontSize = LumenType.size20,
                    fontWeight = FontWeight.Bold,
                    color = t.colors.foreground,
                    modifier = Modifier.padding(top = LumenLegacySpace.md)
                )
            }

            item(key = "alternative_options") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val iptvRes = remember(iptvOption) { iptvOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) } }
                    if (iptvOption != null && iptvRes != null) {
                        TvFocusable(
                            onClick = { handlePlayOption(iptvOption, false) },
                            modifier = Modifier.weight(1f)
                        ) {
                            TvWatchOptionContent(title = "IPTV Option", resolution = iptvRes.displayLabel.primaryLabel)
                        }
                    }

                    if (extensionOption != null) {
                        TvFocusable(
                            onClick = { handlePlayOption(extensionOption, false) },
                            modifier = Modifier.weight(1f)
                        ) {
                            TvWatchOptionContent(title = "Extension Option", resolution = extensionOption.source.resolution)
                        }
                    }

                    if (primaryLangOption != null) {
                        val primaryLangRes = remember(primaryLangOption) { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(primaryLangOption.toRawSourceInput()) }
                        TvFocusable(
                            onClick = { handlePlayOption(primaryLangOption, false) },
                            modifier = Modifier.weight(1f)
                        ) {
                            TvWatchOptionContent(title = "Primary Lang", resolution = primaryLangRes.displayLabel.primaryLabel)
                        }
                    }

                    if (dualAudioOption != null) {
                        val dualRes = remember(dualAudioOption) { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(dualAudioOption.toRawSourceInput()) }
                        TvFocusable(
                            onClick = { handlePlayOption(dualAudioOption, false) },
                            modifier = Modifier.weight(1f)
                        ) {
                            TvWatchOptionContent(title = "Dual-Audio", resolution = dualRes.displayLabel.primaryLabel)
                        }
                    }
                }
            }

            // Advanced options panel
            item(key = "advanced_toggle") {
                TvFocusable(
                    onClick = { isAdvancedExpanded = !isAdvancedExpanded },
                    modifier = Modifier.fillMaxWidth().focusRequester(advancedToggleFocusRequester)
                ) {
                    Row(
                        modifier = Modifier.padding(LumenLegacySpace.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Source Controls · ${sortedOptions.size}",
                            fontSize = LumenType.size16,
                            fontWeight = FontWeight.Bold,
                            color = t.colors.foreground,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (isAdvancedExpanded) "Collapse ▲" else "Expand ▼",
                            color = t.colors.brand,
                            fontSize = LumenType.size14,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (isAdvancedExpanded) {
                item(key = "show_raw_row") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = LumenLegacySpace.lg)
                    ) {
                        Text("Show technical details", color = t.colors.foreground, fontSize = LumenType.size14)
                        Switch(checked = showRawDetails, onCheckedChange = { showRawDetails = it })
                    }
                }

                item(key = "sort_strategies") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = LumenLegacySpace.lg)
                    ) {
                        TvFocusable(
                            onClick = { sortingPreference = SortingPreference.BEST_MATCH }
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (sortingPreference == SortingPreference.BEST_MATCH) t.colors.brand else t.colors.muted,
                                        LumenTokens.Shape.sm
                                    )
                                    .padding(horizontal = LumenLegacySpace.lg, vertical = LumenLegacySpace.sm2)
                            ) {
                                Text("Best Match", color = t.colors.foreground, fontSize = LumenType.size13, fontWeight = FontWeight.Bold)
                            }
                        }
                        TvFocusable(
                            onClick = { sortingPreference = SortingPreference.HIGHEST_QUALITY }
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (sortingPreference == SortingPreference.HIGHEST_QUALITY) t.colors.brand else t.colors.muted,
                                        LumenTokens.Shape.sm
                                    )
                                    .padding(horizontal = LumenLegacySpace.lg, vertical = LumenLegacySpace.sm2)
                            ) {
                                Text("Highest Quality", color = t.colors.foreground, fontSize = LumenType.size13, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                itemsIndexed(sortedOptionsWithScores, key = { _, pair -> pair.first.id }) { index, (option, score) ->
                    Box(modifier = Modifier.padding(horizontal = LumenLegacySpace.lg)) {
                        TvManualSourceItem(
                            option = option,
                            score = score,
                            health = sourceHealths[option.id],
                            showRawDetails = showRawDetails,
                            modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                            onClick = { handlePlayOption(option, false) }
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun TvWatchOptionContent(
    title: String,
    resolution: String
) {
    val t = LocalLumenTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(LumenLegacySpace.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, fontWeight = FontWeight.Bold, color = t.colors.foreground, fontSize = LumenType.size14)
        Text(text = resolution, color = t.colors.mutedForeground, fontSize = LumenType.size12, modifier = Modifier.padding(top = LumenLegacySpace.xs))
    }
}

@Composable
fun TvManualSourceItem(
    option: WatchOption,
    score: Int,
    health: SourceHealth?,
    showRawDetails: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val t = LocalLumenTokens.current
    val result = remember(option) { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(option.toRawSourceInput()) }

    TvFocusable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(LumenLegacySpace.lg)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (showRawDetails) com.example.calmsource.core.network.UrlRedactor.redactFilename(option.source.name) else result.displayLabel.primaryLabel,
                    color = t.colors.foreground,
                    fontSize = LumenType.size14,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                val parsedInfo = remember(option.source) {
                    StreamParserUtil.smartParseAll(option.source.rawTitle ?: option.source.name, option.source.extensionId)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = LumenLegacySpace.sm)
                ) {
                    TvSourceBadge(type = option.type)
                    
                    val extensionName = parsedInfo.sourceExtensionName ?: option.source.sourceExtensionName
                    if (!extensionName.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(LumenTokens.Shape.md)
                                .background(t.colors.brand.copy(alpha = 0.2f))
                                .padding(horizontal = LumenLegacySpace.sm, vertical = LumenLegacySpace.xxs)
                        ) {
                            Text(
                                text = extensionName,
                                color = t.colors.brandGlow,
                                fontSize = LumenType.size11,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val quality = parsedInfo.quality
                    val sizeStr = WatchOptionResolver.formatFileSize(parsedInfo.fileSizeBytes)
                    Box(
                        modifier = Modifier
                            .clip(LumenTokens.Shape.md)
                            .background(t.colors.muted)
                            .padding(horizontal = LumenLegacySpace.sm, vertical = LumenLegacySpace.xxs)
                    ) {
                        Text(
                            text = "[$quality] [$sizeStr]",
                            color = t.colors.foreground,
                            fontSize = LumenType.size11,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val hdrBadge = parsedInfo.hdrFormat
                    if (hdrBadge != null) {
                        Box(
                            modifier = Modifier
                                .clip(LumenTokens.Shape.md)
                                .background(LumenExtendedColors.ratingGold.copy(alpha = 0.24f))
                                .padding(horizontal = LumenLegacySpace.sm, vertical = LumenLegacySpace.xxs)
                        ) {
                            Text(
                                text = "[$hdrBadge]",
                                color = LumenExtendedColors.ratingGold,
                                fontSize = LumenType.size11,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val codecBadge = parsedInfo.videoCodec ?: option.source.videoCodec
                    if (!codecBadge.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(LumenTokens.Shape.md)
                                .background(LumenExtendedColors.cyan.copy(alpha = 0.24f))
                                .padding(horizontal = LumenLegacySpace.sm, vertical = LumenLegacySpace.xxs)
                        ) {
                            Text(
                                text = "[$codecBadge]",
                                color = LumenExtendedColors.cyan,
                                fontSize = LumenType.size11,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val audioBadge = parsedInfo.audioCodec ?: option.source.audioCodec
                    if (!audioBadge.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(LumenTokens.Shape.md)
                                .background(LumenExtendedColors.violet.copy(alpha = 0.24f))
                                .padding(horizontal = LumenLegacySpace.sm, vertical = LumenLegacySpace.xxs)
                        ) {
                            Text(
                                text = "[$audioBadge]",
                                color = LumenExtendedColors.violet,
                                fontSize = LumenType.size11,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (result.displayLabel.secondaryLabel.isNotEmpty()) {
                        Text(text = result.displayLabel.secondaryLabel, color = t.colors.mutedForeground, fontSize = LumenType.size12, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        fontSize = LumenType.size12,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val parsedSeeds = parsedInfo.seeds ?: option.source.seeds
                    if (parsedSeeds != null) {
                        Text(text = "Seeds: $parsedSeeds", color = LumenTokens.Color.success, fontSize = LumenType.size12)
                    }
                }
                
                if (health != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md),
                        modifier = Modifier.padding(top = LumenLegacySpace.xs)
                    ) {
                        Text(text = "Failures: ${health.failureCount}", fontSize = LumenType.size12, color = t.colors.mutedForeground)
                        Text(text = "Startup: ${health.averageStartupTime}ms", fontSize = LumenType.size12, color = t.colors.mutedForeground)
                        Text(text = "Buffering: ${String.format("%.1f", health.averageBufferingSeverity)}", fontSize = LumenType.size12, color = t.colors.mutedForeground)
                    }
                }
            }
            Text(
                text = "Score: $score",
                color = t.colors.brand,
                fontSize = LumenType.size14,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TvSourceBadge(type: SourceType, modifier: Modifier = Modifier) {
    val t = LocalLumenTokens.current
    val (label, bg, fg) = when (type) {
        SourceType.IPTV -> Triple("IPTV", t.colors.brand.copy(alpha = 0.2f), t.colors.brandGlow)
        SourceType.EXTENSION -> Triple("ADDON", t.colors.muted, t.colors.foreground)
        SourceType.DEBRID -> Triple("DEBRID", LumenExtendedColors.debridTint, LumenTokens.Color.success)
    }
    Box(
        modifier = modifier
            .clip(LumenTokens.Shape.md)
            .background(bg)
            .padding(horizontal = LumenLegacySpace.sm, vertical = LumenLegacySpace.xxs)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = LumenType.size11,
            fontWeight = FontWeight.Black
        )
    }
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
    stremioMeta: com.example.calmsource.core.model.StremioMeta?
) {
    runCatching {
        stremioMeta?.genres?.forEach { genre ->
            val key = genre.trim().lowercase()
            if (key.isNotBlank()) {
                memoryRepository.incrementPreferenceSignal(
                    signalType = com.example.calmsource.core.model.UserPreferenceSignalType.GENRE,
                    signalKey = key
                )
            }
        }
        memoryRepository.incrementPreferenceSignal(
            signalType = com.example.calmsource.core.model.UserPreferenceSignalType.CONTENT_TYPE,
            signalKey = if (mediaItem.type == MediaType.SHOW) "series" else "movie"
        )
    }
}

fun selectBestMatch(options: List<WatchOption>, scores: Map<String, Int>): WatchOption? {
    val sizeCap = 15L * 1024 * 1024 * 1024L
    val cappedOptions = options.filter { option ->
        val size = option.source.sizeBytes
        size == null || size < sizeCap
    }
    val candidates = if (cappedOptions.isNotEmpty()) cappedOptions else options

    return candidates.maxByOrNull { option ->
        var score = scores[option.id] ?: 0
        if (option.source.resolution == "1080p") {
            score += 50
        }
        val seeds = option.source.seeds ?: 0
        if (seeds > 50) score += 20
        score
    }
}

fun selectHighestQuality(options: List<WatchOption>, scores: Map<String, Int>): WatchOption? {
    return options.maxByOrNull { option ->
        var score = scores[option.id] ?: 0
        if (option.source.resolution == "4K") {
            score += 100
        }
        val size = option.source.sizeBytes ?: 0L
        if (size >= 40L * 1024 * 1024 * 1024L) {
            score += 100
        } else if (size >= 15L * 1024 * 1024 * 1024L) {
            score += 50
        }
        score
    }
}
