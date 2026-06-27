/**
 * Media details and stream picker screen for the CalmSource Android TV app.
 *
 * Shows media metadata (poster, title, rating, overview) and a prioritized
 * list of watch options resolved from all available sources. Watch options
 * are sorted by [SearchEngine.calculateScore] based on user preferences.
 *
 * Layout (TV-optimized two-column):
 * - **Left column**: Poster, title, rating, overview, quick-play buttons
 * - **Right column**: Scrollable list of all watch options with score,
 *   resolution, source type, file size, codec, and seed count
 *
 * All interactive elements use [TvFocusCard] for D-pad navigation.
 *
 * Navigation: Reached from [TvHomeScreen] or [TvSearchScreen].
 * Navigates to [TvPlayerScreen] when a watch option is selected.
 *
 * @see TvWatchOptionItem for individual source list item rendering
 */
package com.example.calmsource.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusGroup
import androidx.compose.material3.Text
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

/**
 * TV media details screen composable displaying metadata and prioritized watch options.
 *
 * Resolves all available [StreamSource] entries for the given [mediaItem], maps
 * them to [WatchOption] instances, and sorts them by [SearchEngine.calculateScore].
 * Presents a two-column layout optimized
 * for lean-back viewing with D-pad navigation.
 *
 * @param mediaItem The [MediaItem] to display details for.
 * @param onBack Callback invoked when the user presses back.
 * @param onPlayOption Callback invoked when the user selects a watch option;
 *   receives the chosen [WatchOption] and navigates to [TvPlayerScreen].
 */
@Composable
fun TvDetailsScreen(
    mediaItem: MediaItem,
    startPositionMs: Long = 0L,
    onBack: () -> Unit,
    onPlayOption: (PlaybackRequest, List<PlaybackSource>, Boolean) -> Unit,
    onOpenMedia: (MediaItem) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    
    val installedExtensions by ExtensionRepository.extensions.collectAsState()
    val activeExtensions = installedExtensions.filter { it.isEnabled && it.health != ExtensionHealth.NEEDS_CONFIGURATION && it.health != ExtensionHealth.INVALID_MANIFEST }
    val extensionQueryKey = activeExtensions.map { it.id to it.url }
    var currentMediaItem by remember(mediaItem.id) { mutableStateOf(mediaItem) }
    var streamSearchUiState by remember { mutableStateOf(StreamSearchUiState()) }
    val watchOptions = streamSearchUiState.watchOptions
    val subtitlesList = streamSearchUiState.subtitles
    
    var stremioMeta by remember(mediaItem.id) { mutableStateOf<com.example.calmsource.core.model.StremioMeta?>(null) }
    var isLoadingMeta by remember(mediaItem.id) { mutableStateOf(false) }
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

    LaunchedEffect(mediaItem.id, extensionQueryKey) {
        isLoadingMeta = true
        try {
            val metadata = ExtensionRepository.refreshMediaMetadata(mediaItem, activeExtensions)
            currentMediaItem = metadata.mediaItem
            stremioMeta = metadata.primaryMeta
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
    // Gate Room access on databaseReady so we never build the database on the main thread before
    // the deferred warmup completes; use the in-memory fallback until the DB has been built on IO.
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

    var sortingPreference by remember { mutableStateOf(SortingPreference.BEST_MATCH) }
    // Sort watch options by search score
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
                
                // Build fallback candidate sources sorted by score, excluding the selected one.
                // Cap to the top 5 before navigating so we don't ship a huge fallback chain (and a
                // long resolve loop) into the player.
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

        // 1. Initial local watch options
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

            // 4. Fetch subtitles from active extensions in parallel
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
        androidx.compose.ui.window.Dialog(onDismissRequest = { showUnavailableDialog = false }) {
            Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(TvColors.Surface).padding(24.dp)) {
                Column {
                    Text("Source Unavailable", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TvColors.TextMain)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("The selected stream is missing a valid URL. Please choose another option.", fontSize = 14.sp, color = TvColors.TextSub)
                    Spacer(modifier = Modifier.height(16.dp))
                    TvFocusCard(onClick = { showUnavailableDialog = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("OK", color = TvColors.TextMain, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showBlockedDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showBlockedDialog = false }) {
            Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(TvColors.Surface).padding(24.dp)) {
                Column {
                    Text("Configuration Required", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TvColors.TextMain)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("This source requires configuration or authentication. Please update your settings.", fontSize = 14.sp, color = TvColors.TextSub)
                    Spacer(modifier = Modifier.height(16.dp))
                    TvFocusCard(onClick = { showBlockedDialog = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("OK", color = TvColors.TextMain, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Key options
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Simple Back focus card
        item {
            TvFocusCard(
                onClick = onBack,
                modifier = Modifier.wrapContentSize().padding(bottom = 8.dp)
            ) {
                Text(text = "← Back to Guide", color = TvColors.TextMain, fontSize = 14.sp)
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Poster
                AsyncImage(
                    model = currentMediaItem.posterUrl,
                    contentDescription = "Poster for ${currentMediaItem.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(160.dp, 240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x1AFFFFFF))
                )

                // VOD Info
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
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = TvColors.TextMain
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(text = currentMediaItem.releaseDate ?: "", color = TvColors.TextSub, fontSize = 14.sp)
                        currentMediaItem.rating?.let {
                            Text(text = "IMDb: ★ $it", color = Color(0xFFFBBF24), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        stremioMeta?.rtRating?.let {
                            Text(text = "RT: 🍅 $it", color = Color(0xFFEF4444), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        stremioMeta?.metascore?.let {
                            Text(text = "Metascore: Ⓜ️ $it", color = Color(0xFF10B981), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        if (stremioMeta?.type == "anime") {
                            stremioMeta?.simklRating?.let {
                                Text(text = "SIMKL: ★ $it", color = Color(0xFF3B82F6), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            stremioMeta?.malRating?.let {
                                Text(text = "MAL: ★ $it", color = Color(0xFF8B5CF6), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    stremioMeta?.studios?.let { studios ->
                        if (studios.isNotEmpty()) {
                            Text(
                                text = "Studios: ${studios.joinToString(", ")}",
                                fontSize = 14.sp,
                                color = TvColors.TextSub,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                    Text(
                        text = currentMediaItem.overview ?: "",
                        color = TvColors.TextSub,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    TvFocusCard(
                        onClick = {
                            val wasFavorite = isFavorite
                            memoryScope.launch {
                                runCatching {
                                    memoryRepository.toggleFavorite(
                                        com.example.calmsource.feature.iptv.IPTVRepository
                                            .findChannel(currentMediaItem.id)
                                            ?.toUserMemoryReference()
                                            ?: currentMediaItem.toUserMemoryReference()
                                    )
                                }
                                // Record taste signals only when adding (not removing) a favorite.
                                if (!wasFavorite) {
                                    recordTasteSignals(memoryRepository, currentMediaItem, stremioMeta)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) { focused ->
                        Text(
                            text = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                            color = if (focused) TvColors.Background else TvColors.BorderFocused,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    if (similarItems.isNotEmpty()) {
                        Text(
                            text = "More Like This",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TvColors.TextMain,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
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
                                TvFocusCard(
                                    onClick = { onOpenMedia(similarMedia) },
                                    modifier = Modifier.width(120.dp)
                                ) { _ ->
                                    AsyncImage(
                                        model = similarMedia.posterUrl,
                                        contentDescription = "Poster for ${similarMedia.title}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(170.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x1AFFFFFF))
                                    )
                                    Text(
                                        text = similarMedia.title,
                                        color = TvColors.TextMain,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (mediaItem.type == MediaType.SHOW) {
                        if (isLoadingMeta) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(color = TvColors.BorderFocused)
                            }
                        } else {
                            if (seasons.isNotEmpty()) {
                                Text(
                                    text = "Seasons",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TvColors.TextMain,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                ) {
                                    items(seasons, key = { it }) { season ->
                                        val isSelected = selectedSeason == season
                                        TvFocusCard(
                                            onClick = { selectedSeason = season },
                                            modifier = Modifier.width(110.dp)
                                        ) { isFocused ->
                                            Text(
                                                text = seasonDisplayLabel(season),
                                                color = if (isFocused) TvColors.Background 
                                                       else if (isSelected) TvColors.BorderFocused 
                                                       else TvColors.TextMain,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (episodesForSelectedSeason.isNotEmpty()) {
                                Text(
                                    text = "Episodes",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TvColors.TextMain,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                                ) {
                                    items(episodesForSelectedSeason, key = { it.id ?: "${it.season}-${it.episode}" }) { video ->
                                        val isSelected = selectedEpisode?.episode == video.episode
                                        TvFocusCard(
                                            onClick = { selectedEpisode = video },
                                            modifier = Modifier.width(140.dp)
                                        ) { isFocused ->
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(
                                                    text = "Ep ${video.episode}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isFocused) TvColors.Background 
                                                           else if (isSelected) TvColors.BorderFocused 
                                                           else TvColors.TextMain,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = video.title ?: "Episode ${video.episode}",
                                                    color = if (isFocused) TvColors.Background.copy(alpha = 0.8f) 
                                                           else TvColors.TextSub,
                                                    fontSize = 11.sp,
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

                    if (subtitlesList.isNotEmpty()) {
                        val langs = subtitlesList.map { it.lang }.distinct().joinToString(", ")
                        Text(
                            text = "Subtitles Available: $langs",
                            color = Color(0xFF34D399),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (extensionErrors.isNotEmpty() || streamSearchUiState.failedExtensions.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1AEF4444))
                                .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Extension Warning",
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                extensionErrors.forEach { error ->
                                    Text(text = error, color = TvColors.TextMain, fontSize = 12.sp)
                                }
                                if (streamSearchUiState.failedExtensions.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Failed extensions: ${streamSearchUiState.failedExtensions.joinToString(", ")}",
                                        color = Color(0xFFEF4444),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 1. Play Best Match vs Highest Quality Card
                    if (!isLoadingSources && sortedOptions.isNotEmpty()) {
                        val scoresMap = remember(sortedOptionsWithScores) {
                            sortedOptionsWithScores.associate { it.first.id to it.second }
                        }
                        val bestMatchOption = selectBestMatch(sortedOptions, scoresMap) ?: sortedOptions.first()
                        val highestQualityOption = selectHighestQuality(sortedOptions, scoresMap) ?: sortedOptions.first()
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            TvFocusCard(
                                onClick = { handlePlayOption(bestMatchOption, true) },
                                modifier = Modifier.weight(1f).focusRequester(playBestFocusRequester)
                            ) { isFocused ->
                                Text(
                                    text = "Play Best Match (${bestMatchOption.source.resolution})",
                                    color = if (isFocused) TvColors.Background else TvColors.TextMain,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(12.dp).align(Alignment.CenterHorizontally)
                                )
                            }
                            if (highestQualityOption.id != bestMatchOption.id) {
                                TvFocusCard(
                                    onClick = { handlePlayOption(highestQualityOption, true) },
                                    modifier = Modifier.weight(1f)
                                ) { isFocused ->
                                    Text(
                                        text = "Highest Quality (${highestQualityOption.source.resolution})",
                                        color = if (isFocused) TvColors.Background else TvColors.TextMain,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(12.dp).align(Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        }
                    } else if (!isLoadingSources) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "No playable sources found.", color = TvColors.TextSub)
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator(color = TvColors.BorderFocused)
                        }
                    }
                }
            }
        }

        // Watch options D-pad lists
        item {
            Text(
                text = "Stream Pickers",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = TvColors.TextMain,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).focusGroup()
            ) {
                val iptvRes = remember(iptvOption) { iptvOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) } }
                if (iptvOption != null && iptvRes != null) {
                    TvWatchOptionCard(
                        title = "IPTV Option",
                        resolution = iptvRes.displayLabel.primaryLabel,
                        health = sourceHealths[iptvOption.id],
                        onClick = { handlePlayOption(iptvOption, false) },
                        modifier = Modifier.weight(1f)
                    )
                }

                val extRes = remember(extensionOption) { extensionOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) } }
                if (extensionOption != null && extRes != null) {
                    TvWatchOptionCard(
                        title = "Extension Option",
                        resolution = extRes.displayLabel.primaryLabel,
                        health = sourceHealths[extensionOption.id],
                        onClick = { handlePlayOption(extensionOption, false) },
                        modifier = Modifier.weight(1f)
                    )
                }

                val primaryLangRes = remember(primaryLangOption) { primaryLangOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) } }
                if (primaryLangOption != null && primaryLangRes != null) {
                    TvWatchOptionCard(
                        title = "Primary Language",
                        resolution = primaryLangRes.displayLabel.primaryLabel,
                        health = sourceHealths[primaryLangOption.id],
                        onClick = { handlePlayOption(primaryLangOption, false) },
                        modifier = Modifier.weight(1f)
                    )
                }

                val dualRes = remember(dualAudioOption) { dualAudioOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) } }
                if (dualAudioOption != null && dualRes != null) {
                    TvWatchOptionCard(
                        title = "Dual-Audio",
                        resolution = dualRes.displayLabel.primaryLabel,
                        health = sourceHealths[dualAudioOption.id],
                        onClick = { handlePlayOption(dualAudioOption, false) },
                        modifier = Modifier.weight(1f)
                    )
                }

                val lowDataRes = remember(lowDataOption) { lowDataOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) } }
                if (lowDataOption != null && lowDataRes != null) {
                    TvWatchOptionCard(
                        title = "Low-Data",
                        resolution = lowDataRes.displayLabel.primaryLabel,
                        health = sourceHealths[lowDataOption.id],
                        onClick = { handlePlayOption(lowDataOption, false) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            // Advanced Toggle Focus Card
            TvFocusCard(
                onClick = { isAdvancedExpanded = !isAdvancedExpanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .focusRequester(advancedToggleFocusRequester)
            ) { isFocused ->
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Advanced - Manual Stream Sources (${sortedOptions.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TvColors.TextMain
                    )
                    Text(
                        text = if (isAdvancedExpanded) "Collapse ▲" else "Expand ▼",
                        fontSize = 14.sp,
                        color = if (isFocused) TvColors.TextMain else TvColors.TextSub
                    )
                }
            }

        }

        if (isAdvancedExpanded) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .focusGroup()
                ) {
                    Text(
                        text = "Sort Strategy:",
                        color = TvColors.TextSub,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TvFocusCard(
                        onClick = { sortingPreference = SortingPreference.BEST_MATCH },
                        modifier = Modifier.wrapContentSize()
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (sortingPreference == SortingPreference.BEST_MATCH) TvColors.BorderFocused
                                    else if (isFocused) Color(0x3DFFFFFF)
                                    else Color(0x1AFFFFFF)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Best Match",
                                color = if (sortingPreference == SortingPreference.BEST_MATCH) TvColors.Background else TvColors.TextMain,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    TvFocusCard(
                        onClick = { sortingPreference = SortingPreference.HIGHEST_QUALITY },
                        modifier = Modifier.wrapContentSize()
                    ) { isFocused ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (sortingPreference == SortingPreference.HIGHEST_QUALITY) TvColors.BorderFocused
                                    else if (isFocused) Color(0x3DFFFFFF)
                                    else Color(0x1AFFFFFF)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Highest Quality",
                                color = if (sortingPreference == SortingPreference.HIGHEST_QUALITY) TvColors.Background else TvColors.TextMain,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            itemsIndexed(sortedOptionsWithScores, key = { _, pair -> pair.first.id }) { index, (option, score) ->
                TvManualSourceItem(
                    option = option,
                    score = score,
                    health = sourceHealths[option.id],
                    modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                    onClick = { handlePlayOption(option, false) }
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * A card component displaying a summary of a specific watch option.
 *
 * Used in the "Stream Pickers" section to show quick-access buttons for 
 * categorized sources (e.g., Debrid, Hindi, English).
 *
 * @param title The category or type label of the source.
 * @param resolution The resolution of the video source.
 * @param onClick Callback invoked when the card is selected.
 * @param modifier Optional modifier for styling.
 */
@Composable
fun TvWatchOptionCard(
    title: String,
    resolution: String,
    health: SourceHealth?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvFocusCard(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = title, fontWeight = FontWeight.Bold, color = TvColors.TextMain, fontSize = 14.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(text = resolution, color = TvColors.TextSub, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (health != null) {
                    val tier = health.reliabilityTier
                    val color = when (tier) {
                        SourceReliabilityTier.EXCELLENT, SourceReliabilityTier.GOOD -> Color(0xFF10B981)
                        SourceReliabilityTier.UNSTABLE, SourceReliabilityTier.POOR -> Color(0xFFF59E0B)
                        SourceReliabilityTier.BLOCKED -> Color(0xFFEF4444)
                        else -> Color(0xFF10B981)
                    }
                    val labelText = when (tier) {
                        SourceReliabilityTier.EXCELLENT, SourceReliabilityTier.GOOD -> "Reliable"
                        SourceReliabilityTier.UNSTABLE, SourceReliabilityTier.POOR -> "Unstable"
                        SourceReliabilityTier.BLOCKED -> "Failed recently"
                        else -> "Reliable"
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = labelText, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * Detailed manual source card in the TV details watch options list.
 *
 * Shows the source name, resolution, source type badge, file size,
 * codec, language, seed count, and computed score. Wrapped in [TvFocusCard]
 * for D-pad navigation.
 *
 * @param option The [WatchOption] to render details for.
 * @param onClick Callback invoked when the card is selected to start playback.
 */
@Composable
fun TvManualSourceItem(
    option: WatchOption,
    score: Int,
    health: SourceHealth?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val result = remember(option) { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(option.toRawSourceInput()) }

    TvFocusCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) { isFocused ->
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = result.displayLabel.primaryLabel, color = TvColors.TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                
                val parsedInfo = remember(option.source) {
                    StreamParserUtil.smartParseAll(option.source.rawTitle ?: option.source.name, option.source.extensionId)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    TvSourceBadge(type = option.type)
                    
                    val extensionName = parsedInfo.sourceExtensionName ?: option.source.sourceExtensionName
                    if (!extensionName.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x3D8B5CF6))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = extensionName,
                                color = Color(0xFFA78BFA),
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
                            .background(Color(0x1AFFFFFF))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "[$quality] [$sizeStr]",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // HDR format badge
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

                    // Video codec badge
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

                    // Audio codec badge
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
                        Text(text = result.displayLabel.secondaryLabel, color = TvColors.TextSub, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    
                    // Quiet health label next to stream options
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
                
                // Show detailed health metrics in the advanced panel
                if (health != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(text = "Failures: ${health.failureCount}", fontSize = 12.sp, color = TvColors.TextSub)
                        Text(text = "Startup: ${health.averageStartupTime}ms", fontSize = 12.sp, color = TvColors.TextSub)
                        Text(text = "Buffering: ${String.format("%.1f", health.averageBufferingSeverity)}", fontSize = 12.sp, color = TvColors.TextSub)
                    }
                }
            }
            Text(
                text = "Score: $score",
                color = TvColors.BorderFocused,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
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

/**
 * Persists lightweight taste signals (genre + content type) when a user favorites an item, so the
 * previously-orphaned [UserPreferenceSignalType.GENRE]/[UserPreferenceSignalType.CONTENT_TYPE]
 * signals reflect real engagement and can inform future personalization. Best-effort.
 */
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
