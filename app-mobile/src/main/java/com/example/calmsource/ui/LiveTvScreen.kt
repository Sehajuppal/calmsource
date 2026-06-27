/**
 * Live TV guide screen for the CalmSource mobile app.
 *
 * Displays all available IPTV channels grouped by category with EPG
 * (Electronic Program Guide) information. Channels are loaded from
 * [IPTVRepository] and matched against EPG data for current/next
 * program display.
 *
 * Layout:
 * - **Category filter tabs** (scrollable [ScrollableTabRow]) for filtering
 *   channels by group/category
 * - **Channel list** ([LazyColumn]) with logo, name, current program with
 *   progress bar, and next program preview
 * - Tap a channel to start playback of the current program
 *
 * Navigation: Accessible via bottom navigation bar "Live TV" tab.
 *
 * @see LiveChannelGuideItem for individual channel row rendering
 */
package com.example.calmsource.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.example.calmsource.core.model.Channel
import com.example.calmsource.core.model.Program
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calmsource.feature.iptv.IPTVRepository
import com.example.calmsource.feature.iptv.IptvChannelFacets
import com.example.calmsource.feature.iptv.IptvContentSection
import com.example.calmsource.feature.iptv.IptvLiveGuideFilters
import com.example.calmsource.feature.iptv.IptvLiveGuideSort
import com.example.calmsource.feature.iptv.IptvLiveGuideView
import com.example.calmsource.feature.iptv.LiveGuideViewModel
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.repository.RoomUserMemoryRepository
import com.example.calmsource.core.database.repository.FallbackUserMemoryRepository
import com.example.calmsource.core.model.toUserMemoryReference
import kotlinx.coroutines.launch

@Composable
fun LiveTvScreen(
    onChannelSelect: (Channel, Program?) -> Unit,
    onOpenSetup: () -> Unit
) {
    val viewModel: LiveGuideViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current.applicationContext
    val dbReady by DatabaseProvider.databaseReady.collectAsState()
    val memoryRepository = remember(context, dbReady) {
        if (!dbReady) {
            FallbackUserMemoryRepository()
        } else runCatching {
            RoomUserMemoryRepository(DatabaseProvider.getDatabase(context))
        }.getOrElse { e ->
            runCatching {
                android.util.Log.e("LiveTvScreen", "Failed to initialize RoomUserMemoryRepository", e)
            }
            FallbackUserMemoryRepository()
        }
    }
    val favoriteItems by memoryRepository.observeFavorites().collectAsState(initial = emptyList())
    val recentItems by memoryRepository.observeRecentChannels().collectAsState(initial = emptyList())
    val favoriteKeys = remember(favoriteItems) { favoriteItems.mapTo(hashSetOf()) { it.reference.itemKey } }
    val recentOrder = remember(recentItems) {
        recentItems.mapIndexed { index, item -> item.reference.itemKey to index }.toMap()
    }
    val memoryScope = rememberCoroutineScope()

    LaunchedEffect(favoriteKeys, recentOrder) {
        viewModel.updateMemoryHints(favoriteKeys, recentOrder)
    }

    if (uiState.isLoading || (uiState.isSyncing && uiState.allChannels.isEmpty())) {
        Box(
            modifier = Modifier.fillMaxSize().background(AppColors.Background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AppColors.Primary)
                Text(
                    text = "Syncing Live TV...",
                    color = AppColors.TextSub,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
        return
    }

    val iptvChannelById = uiState.iptvChannelById
    val languageById = uiState.languageById
    val countryById = uiState.countryById
    val sectionById = uiState.sectionById
    val languages = uiState.languages
    val countries = uiState.countries
    val mappedChannels = uiState.allChannels

    if (mappedChannels.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("No live channels yet.", color = AppColors.TextMain, fontSize = 20.sp)
            Text(
                "Connect an M3U or Xtream provider to build your Live TV guide.",
                color = AppColors.TextSub,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
            Button(onClick = onOpenSetup) {
                Text("Open IPTV setup")
            }
        }
        return
    }

    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30000L)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val categories = uiState.categories
    LaunchedEffect(categories) {
        if (uiState.selectedCategory !in categories) {
            viewModel.setSelectedCategory("All")
        }
    }
    LaunchedEffect(languages) {
        if (uiState.selectedLanguage != IptvLiveGuideFilters.ALL_LANGUAGES && uiState.selectedLanguage !in languages) {
            viewModel.setSelectedLanguage(IptvLiveGuideFilters.ALL_LANGUAGES)
        }
    }
    LaunchedEffect(countries) {
        if (uiState.selectedCountry != IptvLiveGuideFilters.ALL_REGIONS && uiState.selectedCountry !in countries) {
            viewModel.setSelectedCountry(IptvLiveGuideFilters.ALL_REGIONS)
        }
    }
    val activeCategory = uiState.selectedCategory
    val syncWarnings = uiState.syncWarnings
    val filteredChannels = uiState.filteredChannels
    val channelQuery = uiState.searchQuery
    val selectedView = uiState.selectedView
    val selectedLanguage = uiState.selectedLanguage
    val selectedCountry = uiState.selectedCountry
    val sortMode = uiState.sortMode
    val reloadToken = uiState.reloadToken
    val listState = rememberLazyListState()
    val visibleEpgChannelIds by remember(filteredChannels) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (filteredChannels.isEmpty()) return@derivedStateOf emptyList<String>()
            val visible = layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) {
                return@derivedStateOf filteredChannels.take(40).map { it.id }
            }
            val buffer = 10
            val first = (visible.first().index - buffer).coerceAtLeast(0)
            val last = (visible.last().index + buffer).coerceAtMost(filteredChannels.lastIndex)
            filteredChannels.subList(first, last + 1).map { it.id }
        }
    }
    var nowNextMap by remember { mutableStateOf(emptyMap<String, com.example.calmsource.feature.iptv.EpgNowNext>()) }
    val filteredChannelIds = remember(filteredChannels) { filteredChannels.map { it.id } }
    LaunchedEffect(filteredChannelIds) {
        val validIds = filteredChannelIds.toSet()
        nowNextMap = nowNextMap.filterKeys { it in validIds }
    }
    LaunchedEffect(visibleEpgChannelIds, reloadToken, currentTimeMs) {
        if (visibleEpgChannelIds.isEmpty()) return@LaunchedEffect
        val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            IPTVRepository.getNowNextForChannels(visibleEpgChannelIds, currentTimeMs)
        }
        nowNextMap = nowNextMap + loaded
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(16.dp)
    ) {
        Text(
            text = "Live TV Guide",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextMain,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        syncWarnings.forEach { warning ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = warning,
                    color = Color(0xFFD97706),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (uiState.isEnrichingFacets) {
            Text(
                text = "Enriching guide…",
                color = AppColors.TextSub,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        OutlinedTextField(
            value = channelQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            label = { Text("Search channels") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp)
        ) {
            IptvLiveGuideView.entries.forEach { view ->
                FilterChip(
                    selected = selectedView == view,
                    onClick = { viewModel.setSelectedView(view) },
                    label = { Text(view.label) }
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 10.dp)
        ) {
            IptvLiveGuideSort.entries.forEach { mode ->
                FilterChip(
                    selected = sortMode == mode,
                    onClick = { viewModel.setSortMode(mode) },
                    label = {
                        Text(
                            when (mode) {
                                IptvLiveGuideSort.RECOMMENDED -> "Recommended"
                                IptvLiveGuideSort.POPULAR -> "Popular"
                                IptvLiveGuideSort.NAME -> "Name"
                                IptvLiveGuideSort.CATEGORY -> "Category"
                                IptvLiveGuideSort.LANGUAGE -> "Language"
                                IptvLiveGuideSort.RECENT -> "Recent"
                            }
                        )
                    }
                )
            }
        }
        if (languages.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp)
            ) {
                (listOf(IptvLiveGuideFilters.ALL_LANGUAGES) + languages).forEach { language ->
                    FilterChip(
                        selected = selectedLanguage == language,
                        onClick = { viewModel.setSelectedLanguage(language) },
                        label = { Text(language) }
                    )
                }
            }
        }
        if (countries.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp)
            ) {
                (listOf(IptvLiveGuideFilters.ALL_REGIONS) + countries).forEach { country ->
                    FilterChip(
                        selected = selectedCountry == country,
                        onClick = { viewModel.setSelectedCountry(country) },
                        label = { Text(country) }
                    )
                }
            }
        }

        // Category Tabs
        if (categories.size > 1) {
            PrimaryScrollableTabRow(
                selectedTabIndex = categories.indexOf(activeCategory).coerceAtLeast(0),
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                contentColor = AppColors.Primary,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                categories.forEach { category ->
                    Tab(
                        selected = activeCategory == category,
                        onClick = { viewModel.setSelectedCategory(category) },
                        text = { Text(text = category, color = if (activeCategory == category) AppColors.TextMain else AppColors.TextSub) }
                    )
                }
            }
        }

        if (mappedChannels.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                if (activeCategory != "All") {
                    OutlinedButton(
                        onClick = {
                            val categoryToHide = activeCategory
                            memoryScope.launch {
                                IPTVRepository.setLiveChannelGroupHidden(categoryToHide, hidden = true)
                                viewModel.bumpReloadToken()
                            }
                        }
                    ) {
                        Text(
                            text = "Hide category",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                TextButton(
                    onClick = {
                        memoryScope.launch {
                            IPTVRepository.restoreHiddenIptvChannels()
                            viewModel.bumpReloadToken()
                        }
                    }
                ) {
                    Text("Reset hidden")
                }
            }
        }

        // Live Channels Guide List
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 50.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            if (filteredChannels.isEmpty()) {
                item {
                    Column(modifier = Modifier.padding(vertical = 24.dp)) {
                        Text(
                            text = "No channels match these filters.",
                            color = AppColors.TextSub
                        )
                        TextButton(
                            onClick = {
                                viewModel.clearFilters()
                            }
                        ) {
                            Text("Clear filters")
                        }
                    }
                }
            }
            items(filteredChannels, key = { it.id }) { channel ->
                val nowNext = nowNextMap[channel.id]
                val currentProgram = nowNext?.currentProgram?.let {
                    Program(
                        id = it.id,
                        channelId = channel.id,
                        title = it.title,
                        description = it.description,
                        startTimeMs = it.startTimeMs,
                        endTimeMs = it.endTimeMs
                    )
                }
                val nextProgram = nowNext?.nextProgram?.let {
                    Program(
                        id = it.id,
                        channelId = channel.id,
                        title = it.title,
                        description = it.description,
                        startTimeMs = it.startTimeMs,
                        endTimeMs = it.endTimeMs
                    )
                }

                LiveChannelGuideItem(
                    channel = channel,
                    currentProgram = currentProgram,
                    nextProgram = nextProgram,
                    progressPercentage = nowNext?.progressPercentage ?: 0f,
                    isFavorite = iptvChannelById[channel.id]
                        ?.toUserMemoryReference()
                        ?.itemKey in favoriteKeys,
                    onFavoriteToggle = {
                        iptvChannelById[channel.id]?.let { iptvChannel ->
                            memoryScope.launch {
                                runCatching {
                                    memoryRepository.toggleFavorite(iptvChannel.toUserMemoryReference())
                                }
                            }
                        }
                    },
                    onHide = {
                        memoryScope.launch {
                            IPTVRepository.setLiveChannelHidden(channel.id, hidden = true)
                            viewModel.bumpReloadToken()
                        }
                    },
                    onClick = { onChannelSelect(channel, currentProgram) }
                )
            }
        }
    }
}

/**
 * Individual channel row in the Live TV guide list.
 *
 * Displays the channel logo, name, category, and EPG program information:
 * - **Current program**: title, description, time range, and a progress bar
 *   showing how much of the program has elapsed
 * - **Next program**: title and start time preview
 * - Falls back to "No EPG data available" when no program data exists
 *
 * Wrapped in a [GlassCard] for consistent visual styling.
 *
 * @param channel The [Channel] to display (name, logo, category).
 * @param currentProgram The currently airing [Program], or null if unavailable.
 * @param nextProgram The next scheduled [Program], or null if unavailable.
 * @param onClick Callback invoked when the channel row is tapped.
 */
@Composable
fun LiveChannelGuideItem(
    channel: Channel,
    currentProgram: Program?,
    nextProgram: Program?,
    progressPercentage: Float,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onHide: () -> Unit,
    onClick: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Channel Logo
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = "Logo for ${channel.name}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(Color(0x1AFFFFFF))
            )

            Column(modifier = Modifier.weight(1f)) {
                // Channel Name & Category
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = channel.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextMain,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = channel.category ?: "General",
                        fontSize = 11.sp,
                        color = AppColors.Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Currently Airing Program
                if (currentProgram != null) {

                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            text = "ON AIR: ${currentProgram.title}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.Secondary
                        )
                        Text(
                            text = currentProgram.description ?: "",
                            fontSize = 11.sp,
                            color = AppColors.TextSub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        // Progress bar for current program
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { progressPercentage.coerceIn(0f, 1f) },
                                color = AppColors.Secondary,
                                trackColor = Color(0x1AFFFFFF),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(1.5f.dp))
                            )
                            Text(
                                text = "${timeFormatter.format(Date(currentProgram.startTimeMs))} - ${timeFormatter.format(Date(currentProgram.endTimeMs))}",
                                fontSize = 9.sp,
                                color = AppColors.TextSub
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No program info available",
                        fontSize = 12.sp,
                        color = AppColors.TextSub,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // Up Next Program
                if (nextProgram != null) {
                    Text(
                        text = "Up Next: ${nextProgram.title} (${timeFormatter.format(Date(nextProgram.startTimeMs))})",
                        fontSize = 11.sp,
                        color = AppColors.TextSub,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove channel favorite" else "Favorite channel",
                        tint = AppColors.Primary
                    )
                }
                TextButton(onClick = onHide, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    Text(text = "Hide", fontSize = 11.sp, color = AppColors.TextSub)
                }
            }
        }
    }
}
