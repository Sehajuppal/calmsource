package com.example.calmsource.tv.ui

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
import com.example.calmsource.core.ui.theme.*

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
    val activeExtensions = installedExtensions.filter { it.isEnabled && it.health != ExtensionHealth.NEEDS_CONFIGURATION && it.health != ExtensionHealth.INVALID_MANIFEST }
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
            val isMagnetBlocked = option.source.url.startsWith("magnet:", ignoreCase = true) &&
                !com.example.calmsource.feature.debrid.DebridRepository.listAccounts().any { it.isConnected }

            if (provider?.health == ExtensionHealth.NEEDS_CONFIGURATION || isDebridBlocked || isMagnetBlocked) {
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
            Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(t.colors.card).padding(24.dp)) {
                Column {
                    Text("Source Unavailable", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("The selected stream is missing a valid URL. Please choose another option.", fontSize = 14.sp, color = t.colors.mutedForeground)
                    Spacer(modifier = Modifier.height(16.dp))
                    TvFocusable(onClick = { showUnavailableDialog = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("OK", color = t.colors.foreground, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }

    if (showBlockedDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showBlockedDialog = false }) {
            Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(t.colors.card).padding(24.dp)) {
                Column {
                    Text("Configuration Required", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("This source requires configuration or authentication. Please update your settings.", fontSize = 14.sp, color = t.colors.mutedForeground)
                    Spacer(modifier = Modifier.height(16.dp))
                    TvFocusable(onClick = { showBlockedDialog = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("OK", color = t.colors.foreground, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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
                    .padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                LumenSkeleton(modifier = Modifier.width(150.dp).height(36.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LumenSkeleton(modifier = Modifier.size(180.dp, 270.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LumenSkeleton(modifier = Modifier.fillMaxWidth().height(48.dp))
                        LumenSkeleton(modifier = Modifier.width(200.dp).height(24.dp))
                        LumenSkeleton(modifier = Modifier.fillMaxWidth().height(100.dp))
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
                .padding(horizontal = 48.dp),
            contentPadding = PaddingValues(top = 40.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            item(key = "details_row") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Left column: Poster
                    AsyncImage(
                        model = currentMediaItem.posterUrl,
                        contentDescription = "Poster for ${currentMediaItem.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(180.dp, 270.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1AFFFFFF))
                    )

                    // Right column: VOD Info + metadata + actions
                    Column(modifier = Modifier.weight(1f)) {
                        if (!stremioMeta?.logo.isNullOrEmpty()) {
                            AsyncImage(
                                model = stremioMeta?.logo,
                                contentDescription = currentMediaItem.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .height(80.dp)
                            )
                        } else {
                            Text(
                                text = currentMediaItem.title,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = t.colors.foreground
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            val year = currentMediaItem.releaseDate ?: stremioMeta?.releaseInfo ?: ""
                            Text(text = year, color = t.colors.mutedForeground, fontSize = 14.sp)
                            
                            currentMediaItem.rating?.let {
                                Text(text = "IMDb: ★ $it", color = Color(0xFFFBBF24), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            stremioMeta?.rtRating?.let {
                                Text(text = "RT: 🍅 $it", color = Color(0xFFEF4444), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            stremioMeta?.metascore?.let {
                                Text(text = "Metascore: Ⓜ️ $it", color = Color(0xFF10B981), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        val tagline = stremioMeta?.description?.substringBefore(".") ?: ""
                        if (tagline.isNotBlank() && tagline.length > 5) {
                            Text(
                                text = tagline,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = t.colors.mutedForeground,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        Text(
                            text = currentMediaItem.overview ?: stremioMeta?.description ?: "",
                            color = t.colors.mutedForeground,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Action Buttons: Play (Adaptive Contrast) & Favorite
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            if (bestMatch != null) {
                                val isLightBackdrop = backdropLuminance > 0.55f
                                val controlBg = if (isLightBackdrop) Color(0xCC0B0B10) else Color(0xCCFAFAFA)
                                val controlFg = if (isLightBackdrop) Color(0xFFFAFAFA) else Color(0xFF0B0B10)

                                TvFocusable(
                                    onClick = { handlePlayOption(bestMatch, true) },
                                    modifier = Modifier.focusRequester(playBestFocusRequester)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .background(controlBg, RoundedCornerShape(999.dp))
                                            .padding(horizontal = 24.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = "Play Best Match",
                                            color = controlFg,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
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
                                    text = if (isFavorite) "✓ Watchlist" else "+ Watchlist",
                                    color = t.colors.foreground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
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
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Seasons", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(seasons) { season ->
                                    val isSelected = selectedSeason == season
                                    TvFocusable(
                                        onClick = { selectedSeason = season }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (isSelected) t.colors.brand else t.colors.muted,
                                                    RoundedCornerShape(999.dp)
                                                )
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = seasonDisplayLabel(season),
                                                color = if (isSelected) t.colors.brandForeground else t.colors.foreground,
                                                fontSize = 14.sp,
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
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Episodes", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                            LazyRow(
                                state = currentSeasonScrollState,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(episodesForSelectedSeason, key = { it.id ?: "${it.season}:${it.episode}" }) { video ->
                                    val isSelected = selectedEpisode?.episode == video.episode
                                    val epId = video.id ?: "${mediaItem.id}:${video.season ?: 1}:${video.episode ?: 1}"
                                    val progress = progressMap[epId]

                                    TvFocusable(
                                        onClick = { selectedEpisode = video },
                                        modifier = Modifier.width(200.dp)
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
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 8.dp)
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
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("More Like This", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = t.colors.foreground)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                                    modifier = Modifier.width(140.dp)
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
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 6.dp)
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
                    text = "Alternative Watch Options",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.colors.foreground,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            item(key = "alternative_options") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Advanced - Manual Sources (${sortedOptions.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = t.colors.foreground,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (isAdvancedExpanded) "Collapse ▲" else "Expand ▼",
                            color = t.colors.brand,
                            fontSize = 14.sp,
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Text("Show Raw Details", color = t.colors.foreground, fontSize = 14.sp)
                        Switch(checked = showRawDetails, onCheckedChange = { showRawDetails = it })
                    }
                }

                item(key = "sort_strategies") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        TvFocusable(
                            onClick = { sortingPreference = SortingPreference.BEST_MATCH }
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (sortingPreference == SortingPreference.BEST_MATCH) t.colors.brand else t.colors.muted,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Best Match", color = t.colors.foreground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        TvFocusable(
                            onClick = { sortingPreference = SortingPreference.HIGHEST_QUALITY }
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (sortingPreference == SortingPreference.HIGHEST_QUALITY) t.colors.brand else t.colors.muted,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Highest Quality", color = t.colors.foreground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                itemsIndexed(sortedOptionsWithScores, key = { _, pair -> pair.first.id }) { index, (option, score) ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, fontWeight = FontWeight.Bold, color = t.colors.foreground, fontSize = 14.sp)
        Text(text = resolution, color = t.colors.mutedForeground, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
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
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (showRawDetails) com.example.calmsource.core.network.UrlRedactor.redactFilename(option.source.name) else result.displayLabel.primaryLabel,
                    color = t.colors.foreground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                val parsedInfo = remember(option.source) {
                    StreamParserUtil.smartParseAll(option.source.rawTitle ?: option.source.name, option.source.extensionId)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    TvSourceBadge(type = option.type)
                    
                    val extensionName = parsedInfo.sourceExtensionName ?: option.source.sourceExtensionName
                    if (!extensionName.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(t.colors.brand.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = extensionName,
                                color = t.colors.brandGlow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val quality = parsedInfo.quality
                    val sizeStr = WatchOptionResolver.formatFileSize(parsedInfo.fileSizeBytes)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(t.colors.muted)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "[$quality] [$sizeStr]",
                            color = t.colors.foreground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val hdrBadge = parsedInfo.hdrFormat
                    if (hdrBadge != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x3DFBBF24))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "[$hdrBadge]",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val codecBadge = parsedInfo.videoCodec ?: option.source.videoCodec
                    if (!codecBadge.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x3D22D3EE))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "[$codecBadge]",
                                color = Color(0xFF22D3EE),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val audioBadge = parsedInfo.audioCodec ?: option.source.audioCodec
                    if (!audioBadge.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x3DA78BFA))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "[$audioBadge]",
                                color = Color(0xFFA78BFA),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (result.displayLabel.secondaryLabel.isNotEmpty()) {
                        Text(text = result.displayLabel.secondaryLabel, color = t.colors.mutedForeground, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    
                    val tier = health?.reliabilityTier ?: SourceReliabilityTier.EXCELLENT
                    val (labelText, labelColor) = when (tier) {
                        SourceReliabilityTier.EXCELLENT, SourceReliabilityTier.GOOD -> "Reliable" to Color(0xFF10B981)
                        SourceReliabilityTier.UNSTABLE, SourceReliabilityTier.POOR -> "Unstable" to Color(0xFFF59E0B)
                        SourceReliabilityTier.BLOCKED -> "Failed recently" to Color(0xFFEF4444)
                        else -> "Reliable" to Color(0xFF10B981)
                    }
                    Text(
                        text = labelText,
                        color = labelColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val parsedSeeds = parsedInfo.seeds ?: option.source.seeds
                    if (parsedSeeds != null) {
                        Text(text = "Seeds: $parsedSeeds", color = Color(0xFF34D399), fontSize = 12.sp)
                    }
                }
                
                if (health != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(text = "Failures: ${health.failureCount}", fontSize = 12.sp, color = t.colors.mutedForeground)
                        Text(text = "Startup: ${health.averageStartupTime}ms", fontSize = 12.sp, color = t.colors.mutedForeground)
                        Text(text = "Buffering: ${String.format("%.1f", health.averageBufferingSeverity)}", fontSize = 12.sp, color = t.colors.mutedForeground)
                    }
                }
            }
            Text(
                text = "Score: $score",
                color = t.colors.brand,
                fontSize = 14.sp,
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
        SourceType.DEBRID -> Triple("DEBRID", Color(0x3D10B981), Color(0xFF34D399))
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
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
