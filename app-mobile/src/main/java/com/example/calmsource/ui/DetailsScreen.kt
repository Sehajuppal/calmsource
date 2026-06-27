/**
 * Media details and stream picker screen for the CalmSource mobile app.
 *
 * Shows media metadata (poster/backdrop, title, rating, overview) and a
 * prioritized list of watch options resolved from all available sources.
 * Watch options are sorted by [SearchEngine.calculateScore] based on user
 * preferences.
 *
 * Layout (top to bottom):
 * 1. **Back button header** — navigates to the previous screen
 * 2. **Backdrop / Poster area** — full-width media artwork
 * 3. **Title & Meta** — title, release year, star rating, overview text
 * 4. **Play Best Match** — one-tap playback of the highest-scored source
 * 5. **Alternative Watch Options** — filtered shortcuts (Debrid, IPTV,
 *    Dual Audio, Hindi, English)
 * 6. **Advanced Manual Sources** — collapsible list of all available sources
 *    with score, size, codec, and seed count details
 *
 * Navigation: Reached from [HomeScreen] or [SearchScreen].
 * Navigates to [PlayerScreen] when a watch option is selected.
 *
 * @see WatchOptionButton for alternative option buttons
 * @see ManualSourceItem for advanced source list items
 */
package com.example.calmsource.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.example.calmsource.core.discoveryengine.DiscoveryEngine
import com.example.calmsource.core.discoveryengine.models.MediaItem as DiscoveryMediaItem
import com.example.calmsource.core.discoveryengine.models.RecommendationItem as DiscoveryRecommendationItem
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
 * Media details screen composable displaying metadata and prioritized watch options.
 *
 * Resolves all available [StreamSource] entries for the given [mediaItem], maps
 * them to [WatchOption] instances, and sorts them by [SearchEngine.calculateScore].
 * The top-scored option is presented as
 * a "Play Best Match" primary action, with alternative shortcuts and an
 * expandable advanced source list below.
 *
 * @param mediaItem The [MediaItem] to display details for (title, poster, overview, rating).
 * @param onBack Callback invoked when the user taps the back button.
 * @param onPlayOption Callback invoked when the user selects any watch option;
 *   receives the chosen [WatchOption] and typically navigates to [PlayerScreen].
 */
@Composable
fun DetailsScreen(
    mediaItem: MediaItem,
    startPositionMs: Long = 0L,
    onBack: () -> Unit,
    onPlayOption: (PlaybackRequest, List<PlaybackSource>, Boolean) -> Unit,
    onOpenMedia: (MediaItem) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var showRawDetails by remember { mutableStateOf(false) }

    BackHandler(enabled = isAdvancedExpanded) {
        isAdvancedExpanded = false
    }

    val installedExtensions by ExtensionRepository.extensions.collectAsState()
    val activeExtensions = installedExtensions.filter { it.isEnabled && it.health != ExtensionHealth.NEEDS_CONFIGURATION && it.health != ExtensionHealth.INVALID_MANIFEST }
    val extensionQueryKey = activeExtensions.map { it.id to it.url }
    var currentMediaItem by remember(mediaItem.id) { mutableStateOf(mediaItem) }
    var streamSearchUiState by remember { mutableStateOf(StreamSearchUiState()) }
    val watchOptions = streamSearchUiState.watchOptions
    val subtitlesList = streamSearchUiState.subtitles
    
    var stremioMeta by remember(mediaItem.id) { mutableStateOf<com.example.calmsource.core.model.StremioMeta?>(null) }
    var similarItems by remember(mediaItem.id) { mutableStateOf<List<DiscoveryRecommendationItem>>(emptyList()) }

    LaunchedEffect(mediaItem.id) {
        similarItems = runCatching {
            withContext(Dispatchers.IO) {
                DiscoveryEngine.getMoreLikeThis(profileId = "default", itemId = mediaItem.id)
            }
        }.getOrDefault(emptyList())
    }
    var isLoadingMeta by remember(mediaItem.id) { mutableStateOf(false) }

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
            title = { Text("Configuration Required") },
            text = { Text("This source requires configuration or authentication. Please update your settings.") },
            confirmButton = {
                TextButton(onClick = { showBlockedDialog = false }) { Text("OK") }
            }
        )
    }

    // Specific options required for "Spider-Man: Homecoming" test cases
    val iptvOption = sortedOptions.firstOrNull { it.type == SourceType.IPTV }
    val debridEnhancedOption = sortedOptions.firstOrNull { it.type == SourceType.DEBRID }
    val hindiOption = sortedOptions.firstOrNull { it.source.language.equals("Hindi", ignoreCase = true) && !it.source.isDualAudio }
    val englishOption = sortedOptions.firstOrNull { it.source.language.equals("English", ignoreCase = true) }
    val dualAudioOption = sortedOptions.firstOrNull { it.source.isDualAudio }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        // Back Button Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppColors.TextMain)
            }
            Text(
                text = "Back",
                color = AppColors.TextMain,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            item {
                // Backdrop / Poster area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x1AFFFFFF))
                ) {
                    AsyncImage(
                        model = currentMediaItem.backdropUrl ?: currentMediaItem.posterUrl,
                        contentDescription = "Backdrop for ${currentMediaItem.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Title & Meta Info
                if (!stremioMeta?.logo.isNullOrEmpty()) {
                    AsyncImage(
                        model = stremioMeta?.logo,
                        contentDescription = currentMediaItem.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .height(60.dp)
                    )
                } else {
                    Text(
                        text = currentMediaItem.title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextMain,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = currentMediaItem.releaseDate?.substringBefore("-") ?: "",
                        color = AppColors.TextSub,
                        fontSize = 14.sp
                    )
                    currentMediaItem.rating?.let {
                        Text(
                            text = "IMDb: ★ $it",
                            color = Color(0xFFFBBF24),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    stremioMeta?.rtRating?.let {
                        Text(
                            text = "RT: 🍅 $it",
                            color = Color(0xFFEF4444),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    stremioMeta?.metascore?.let {
                        Text(
                            text = "Metascore: Ⓜ️ $it",
                            color = Color(0xFF10B981),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (stremioMeta?.type == "anime") {
                        stremioMeta?.simklRating?.let {
                            Text(
                                text = "SIMKL: ★ $it",
                                color = Color(0xFF3B82F6),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        stremioMeta?.malRating?.let {
                            Text(
                                text = "MAL: ★ $it",
                                color = Color(0xFF8B5CF6),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                stremioMeta?.studios?.let { studios ->
                    if (studios.isNotEmpty()) {
                        Text(
                            text = "Studios: ${studios.joinToString(", ")}",
                            fontSize = 14.sp,
                            color = AppColors.TextSub,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                Text(
                    text = currentMediaItem.overview ?: "No description available.",
                    fontSize = 14.sp,
                    color = AppColors.TextSub,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                TextButton(
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
                    }
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = AppColors.Primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                        color = AppColors.Primary
                    )
                }

                if (similarItems.isNotEmpty()) {
                    Text(
                        text = "More Like This",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextMain,
                        modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
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
                            VODItemCard(
                                item = similarMedia,
                                onClick = { onOpenMedia(similarMedia) }
                            )
                        }
                    }
                }

                if (mediaItem.type == MediaType.SHOW) {
                    if (isLoadingMeta) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AppColors.Primary)
                        }
                    } else {
                        if (seasons.isNotEmpty()) {
                            Text(
                                text = "Seasons",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextMain,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                items(seasons) { season ->
                                    FilterChip(
                                        selected = selectedSeason == season,
                                        onClick = { selectedSeason = season },
                                        label = { Text(seasonDisplayLabel(season)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AppColors.Primary.copy(alpha = 0.24f),
                                            selectedLabelColor = AppColors.Primary
                                        )
                                    )
                                }
                            }
                        }

                        if (episodesForSelectedSeason.isNotEmpty()) {
                            Text(
                                text = "Episodes",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextMain,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                            ) {
                                items(episodesForSelectedSeason) { video ->
                                    val isSelected = selectedEpisode?.episode == video.episode
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) AppColors.Primary.copy(alpha = 0.24f) else AppColors.Surface
                                        ),
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, AppColors.Primary) else null,
                                        modifier = Modifier
                                            .width(180.dp)
                                            .clickable { selectedEpisode = video }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Episode ${video.episode}",
                                                fontWeight = FontWeight.Bold,
                                                color = AppColors.TextMain,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = video.title ?: "Episode ${video.episode}",
                                                color = AppColors.TextSub,
                                                fontSize = 12.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 4.dp)
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
                        fontSize = 13.sp,
                        color = Color(0xFF34D399),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (extensionErrors.isNotEmpty() || streamSearchUiState.failedExtensions.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x1AEF4444)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Extension Warning",
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            extensionErrors.forEach { error ->
                                Text(text = error, color = AppColors.TextMain, fontSize = 12.sp)
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
                }

                // 1. Main Action: Play Best Match vs Highest Quality
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumButton(
                            text = "Play Best Match (${bestMatchOption.source.resolution})",
                            onClick = { handlePlayOption(bestMatchOption, true) },
                            modifier = Modifier.weight(1f)
                        )
                        if (highestQualityOption.id != bestMatchOption.id) {
                            PremiumButton(
                                text = "Highest Quality (${highestQualityOption.source.resolution})",
                                onClick = { handlePlayOption(highestQualityOption, true) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else if (!isLoadingSources) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "No playable sources found.", color = AppColors.TextSub)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppColors.Primary)
                    }
                }

                // 2. Watch Option Shortcuts (Dynamic language filters)
                Text(
                    text = "Alternative Watch Options",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextMain,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    val iptvRes = remember(iptvOption) { iptvOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) } }
                    if (iptvOption != null && iptvRes != null) {
                        WatchOptionButton(
                            label = "IPTV Option (${iptvRes.displayLabel.primaryLabel})",
                            onClick = { handlePlayOption(iptvOption, false) }
                        )
                    }

                    val extOption = remember(sortedOptions) { sortedOptions.firstOrNull { it.type == SourceType.EXTENSION } }
                    val extRes = remember(extOption) { extOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) } }
                    if (extOption != null && extRes != null) {
                        WatchOptionButton(
                            label = "Extension Option (${extRes.displayLabel.primaryLabel})",
                            onClick = { handlePlayOption(extOption, false) }
                        )
                    }

                    val primaryLangRes = remember(hindiOption) { hindiOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) } }
                    if (hindiOption != null && primaryLangRes != null) {
                        WatchOptionButton(
                            label = "Primary Language (${primaryLangRes.displayLabel.primaryLabel})",
                            onClick = { handlePlayOption(hindiOption, false) }
                        )
                    }

                    val dualRes = remember(dualAudioOption) { dualAudioOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) } }
                    if (dualAudioOption != null && dualRes != null) {
                        WatchOptionButton(
                            label = "Dual-Audio (${dualRes.displayLabel.primaryLabel})",
                            onClick = { handlePlayOption(dualAudioOption, false) }
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
                        WatchOptionButton(
                            label = "Low-Data Option (${lowDataRes.displayLabel.primaryLabel})",
                            onClick = { handlePlayOption(lowDataOption, false) }
                        )
                    }
                }

                // 3. Collapsible Advanced/Manual sources
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = "Advanced - Manual Sources (${sortedOptions.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextMain,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (isAdvancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isAdvancedExpanded) "Collapse sources" else "Expand sources",
                        tint = AppColors.TextMain
                    )
                }
            }

            if (isAdvancedExpanded) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "Show Raw Details",
                            color = AppColors.TextMain,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = showRawDetails,
                            onCheckedChange = { showRawDetails = it }
                        )
                    }
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = "Sort Strategy:",
                            color = AppColors.TextSub,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (sortingPreference == SortingPreference.BEST_MATCH) AppColors.Primary else Color(0x1AFFFFFF))
                                .clickable { sortingPreference = SortingPreference.BEST_MATCH }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Best Match",
                                color = if (sortingPreference == SortingPreference.BEST_MATCH) Color.White else AppColors.TextSub,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (sortingPreference == SortingPreference.HIGHEST_QUALITY) AppColors.Primary else Color(0x1AFFFFFF))
                                .clickable { sortingPreference = SortingPreference.HIGHEST_QUALITY }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Highest Quality",
                                color = if (sortingPreference == SortingPreference.HIGHEST_QUALITY) Color.White else AppColors.TextSub,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                items(sortedOptionsWithScores, key = { it.first.id }) { (option, score) ->
                    Box(modifier = Modifier.padding(bottom = 10.dp)) {
                        ManualSourceItem(
                            option = option,
                            score = score,
                            health = sourceHealths[option.id],
                            showRawDetails = showRawDetails,
                            onClick = { handlePlayOption(option, false) }
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }
}

/**
 * Full-width button for an alternative watch option shortcut.
 *
 * Displayed in the "Alternative Watch Options" section of [DetailsScreen],
 * styled with [AppColors.Surface] background and rounded corners.
 *
 * @param label Descriptive text for this option (e.g., "Play via Debrid (4K)").
 * @param onClick Callback invoked when the button is tapped.
 */
@Composable
fun WatchOptionButton(
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Text(
            text = label,
            color = AppColors.TextMain,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Detailed manual source card in the "Advanced - Manual Sources" section.
 *
 * Shows the source name, resolution badge, source type badge, file size,
 * optional seed count, video codec, language, and the computed
 * [SearchEngine.calculateScore] for the source. Wrapped in a [GlassCard].
 *
 * @param option The [WatchOption] to render details for.
 * @param onClick Callback invoked when the card is tapped to start playback.
 */
@Composable
fun ManualSourceItem(
    option: WatchOption,
    score: Int,
    health: SourceHealth?,
    showRawDetails: Boolean,
    onClick: () -> Unit
) {
    val result = remember(option) { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(option.toRawSourceInput()) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth(),
        onClick = onClick
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (showRawDetails) com.example.calmsource.core.network.UrlRedactor.redactFilename(option.source.name) else result.displayLabel.primaryLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextMain,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (showRawDetails) {
                Text(
                    text = com.example.calmsource.core.network.UrlRedactor.redactUrl(option.source.url),
                    fontSize = 11.sp,
                    color = AppColors.TextSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (result.displayLabel.secondaryLabel.isNotEmpty()) {
                Text(
                    text = result.displayLabel.secondaryLabel,
                    fontSize = 12.sp,
                    color = AppColors.TextSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SourceBadge(type = option.type)
                
                val parsedInfo = remember(option.source) {
                    StreamParserUtil.smartParseAll(option.source.rawTitle ?: option.source.name, option.source.extensionId)
                }

                val extensionName = parsedInfo.sourceExtensionName ?: option.source.sourceExtensionName
                if (!extensionName.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x3D8B5CF6))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = extensionName,
                            color = Color(0xFFA78BFA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                val quality = parsedInfo.quality
                val sizeStr = WatchOptionResolver.formatFileSize(parsedInfo.fileSizeBytes)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x1AFFFFFF))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "[$quality] [$sizeStr]",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // HDR format badge
                val hdrBadge = parsedInfo.hdrFormat
                if (hdrBadge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x3DFBBF24))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "[$hdrBadge]",
                            color = Color(0xFFFBBF24),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Video codec badge
                val codecBadge = parsedInfo.videoCodec ?: option.source.videoCodec
                if (!codecBadge.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x3D22D3EE))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "[$codecBadge]",
                            color = Color(0xFF22D3EE),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Audio codec badge
                val audioBadge = parsedInfo.audioCodec ?: option.source.audioCodec
                if (!audioBadge.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x3DA78BFA))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "[$audioBadge]",
                            color = Color(0xFFA78BFA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                
                val parsedSeeds = parsedInfo.seeds ?: option.source.seeds
                if (parsedSeeds != null) {
                    Text(
                        text = "Seeds: $parsedSeeds",
                        fontSize = 11.sp,
                        color = Color(0xFF34D399)
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Score: $score",
                    fontSize = 11.sp,
                    color = AppColors.Secondary,
                    fontWeight = FontWeight.Bold
                )
            }
            // Show detailed health metrics in the advanced panel
            if (health != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text(text = "Failures: ${health.failureCount}", fontSize = 10.sp, color = AppColors.TextSub)
                    Text(text = "Startup: ${health.averageStartupTime}ms", fontSize = 10.sp, color = AppColors.TextSub)
                    Text(text = "Buffering: ${String.format("%.1f", health.averageBufferingSeverity)}", fontSize = 10.sp, color = AppColors.TextSub)
                }
            }
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
    stremioMeta: StremioMeta?
) {
    runCatching {
        stremioMeta?.genres?.forEach { genre ->
            val key = genre.trim().lowercase()
            if (key.isNotBlank()) {
                memoryRepository.incrementPreferenceSignal(
                    signalType = UserPreferenceSignalType.GENRE,
                    signalKey = key
                )
            }
        }
        memoryRepository.incrementPreferenceSignal(
            signalType = UserPreferenceSignalType.CONTENT_TYPE,
            signalKey = if (mediaItem.type == MediaType.SHOW) "series" else "movie"
        )
    }
}

/**
 * Selects the best-balanced stream for reliable playback on typical TV hardware.
 *
 * Strategy: filter out unreasonably large files (>20GB) that would choke
 * TV hardware, then score remaining candidates by a balance of:
 * - Resolution: 1080p preferred (+50), 4K at reasonable size (+30)
 * - Codec efficiency: HEVC/AV1 deliver better quality-per-byte (+20)
 * - Seed count: >50 seeds means faster connections (+20)
 * - Size optimality: 2-8GB is sweet spot (+30), 8-15GB acceptable (+15)
 */
fun selectBestMatch(options: List<WatchOption>, scores: Map<String, Int>): WatchOption? {
    val sizeCap = 20L * 1024 * 1024 * 1024L  // 20GB cap
    val cappedOptions = options.filter { option ->
        val size = option.source.sizeBytes
        size == null || size < sizeCap
    }
    val candidates = if (cappedOptions.isNotEmpty()) cappedOptions else options

    return candidates.maxByOrNull { option ->
        var score = scores[option.id] ?: 0

        // Resolution bonus: prefer 1080p for compatibility, but reward
        // reasonable-size 4K over oversized 1080p
        when (option.source.resolution) {
            "1080p" -> score += 50
            "4K" -> score += 30
            "720p" -> score += 10
        }

        // Codec efficiency: HEVC at 8GB > H264 at 15GB
        when (option.source.videoCodec?.uppercase()) {
            "AV1", "HEVC" -> score += 20
            "H264", "AVC" -> score += 5
        }

        // Seeders = connection reliability
        val seeds = option.source.seeds ?: 0
        if (seeds > 50) score += 20
        else if (seeds > 10) score += 10

        // Size sweet spot: too small = low quality, too large = buffering risk
        val sizeBytes = option.source.sizeBytes ?: 0L
        when {
            sizeBytes in 2_000_000_000L..8_000_000_000L -> score += 30  // 2-8GB sweet spot
            sizeBytes in 8_000_000_001L..15_000_000_000L -> score += 15 // 8-15GB acceptable
            sizeBytes in 1L..1_999_999_999L -> score -= 10               // <2GB likely low quality
        }

        score
    }
}

/**
 * Selects the absolute highest quality stream for premium home theater setups.
 *
 * No size cap. Scores by:
 * - 4K resolution (+100), 1080p (+50)
 * - Dolby Vision (+50), HDR10+ (+30)
 * - Studio audio: Atmos/TrueHD (+30), DTS-HD/DTS:X (+20)
 * - Large file size indicating high bitrate (>40GB = +100)
 * - Codec: AV1 (+30), HEVC (+20)
 */
fun selectHighestQuality(options: List<WatchOption>, scores: Map<String, Int>): WatchOption? {
    return options.maxByOrNull { option ->
        var score = scores[option.id] ?: 0

        // Resolution: 4K is mandatory for "highest quality"
        when (option.source.resolution) {
            "4K" -> score += 100
            "1080p" -> score += 50
        }

        // File size = bitrate indicator
        val sizeBytes = option.source.sizeBytes ?: 0L
        when {
            sizeBytes >= 40_000_000_000L -> score += 100  // 40GB+ remux
            sizeBytes >= 20_000_000_000L -> score += 50   // 20-40GB encode
            sizeBytes >= 10_000_000_000L -> score += 20   // 10-20GB encode
        }

        // HDR format
        when (option.source.videoCodec?.uppercase()) {
            // Detect HDR from the stream name — check source metadata
            else -> {}
        }
        // Use the source name for HDR detection since we don't have a dedicated field
        val nameLower = option.source.name.lowercase()
        val sourceTitleLower = option.title.lowercase()
        val combined = "$nameLower $sourceTitleLower"
        when {
            combined.contains("dolby vision") || combined.contains("dv") -> score += 50
            combined.contains("hdr10+") || combined.contains("hdr10plus") -> score += 30
            combined.contains("hdr10") || combined.contains("hdr") -> score += 10
        }

        // Audio quality
        when {
            combined.contains("atmos") || combined.contains("truehd") -> score += 30
            combined.contains("dts-hd") || combined.contains("dts:x") || combined.contains("dtsx") -> score += 20
            combined.contains("e-ac3") || combined.contains("dd+") || combined.contains("dolby digital plus") -> score += 10
        }

        // Codec quality
        when (option.source.videoCodec?.uppercase()) {
            "AV1" -> score += 30
            "HEVC" -> score += 20
        }

        score
    }
}
