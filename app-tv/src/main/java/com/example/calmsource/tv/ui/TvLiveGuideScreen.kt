/**
 * TV Live Guide screen for the CalmSource Android TV app.
 *
 * Displays all available IPTV channels in a two-column lean-back layout
 * with EPG (Electronic Program Guide) information. Channels are loaded
 * from [IPTVRepository] and matched against EPG data.
 *
 * Layout (two-column, TV-optimized):
 * - **Left column**: Scrollable list of channel cards with D-pad focus
 *   support; focus changes auto-select the channel for detail display
 * - **Right column**: Detailed program information for the focused
 *   channel showing current program with progress bar, next program,
 *   and channel metadata
 *
 * Navigation: Accessible via TV top navigation "Live" tab.
 * Selecting a channel starts live playback.
 */
package com.example.calmsource.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.example.calmsource.core.model.Channel
import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.Program
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calmsource.feature.iptv.IPTVRepository
import com.example.calmsource.feature.iptv.IptvLiveGuideFilters
import com.example.calmsource.feature.iptv.IptvLiveGuideSort
import com.example.calmsource.feature.iptv.IptvLiveGuideView
import com.example.calmsource.feature.iptv.LiveGuideViewModel
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.repository.RoomUserMemoryRepository
import com.example.calmsource.core.model.toUserMemoryReference
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * TV Live Guide composable displaying channels with EPG program data.
 *
 * Loads channels from [IPTVRepository], maps them to [Channel] instances,
 * and presents a two-column layout: a focusable channel list on the left and
 * a detail panel on the right. The detail panel auto-updates as D-pad focus
 * moves between channel items.
 *
 * @param onChannelSelect Callback invoked when the user presses D-pad Center
 *   on a channel; receives the selected [Channel] and the currently airing
 *   [Program] (if any).
 */
@Composable
fun TvLiveGuideScreen(
    onChannelSelect: (Channel, Program?) -> Unit,
    onOpenSetup: () -> Unit
) {
    val viewModel: LiveGuideViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current.applicationContext
    val dbReady by DatabaseProvider.databaseReady.collectAsState()
    val memoryRepository = remember(context, dbReady) {
        if (!dbReady) {
            com.example.calmsource.core.database.repository.FallbackUserMemoryRepository()
        } else runCatching {
            RoomUserMemoryRepository(DatabaseProvider.getDatabase(context))
        }.getOrElse { e ->
            runCatching {
                android.util.Log.e("TvLiveGuideScreen", "Failed to initialize RoomUserMemoryRepository", e)
            }
            com.example.calmsource.core.database.repository.FallbackUserMemoryRepository()
        }
    }
    val favorites by memoryRepository.observeFavorites().collectAsState(initial = emptyList())
    val favoriteKeys = remember(favorites) { favorites.mapTo(hashSetOf()) { it.reference.itemKey } }
    val recentItems by memoryRepository.observeRecentChannels().collectAsState(initial = emptyList())
    val recentOrder = remember(recentItems) {
        recentItems.mapIndexed { index, item -> item.reference.itemKey to index }.toMap()
    }
    val memoryScope = rememberCoroutineScope()
    val optimizationPreferences by IPTVRepository.optimizationPreferences.collectAsState()

    LaunchedEffect(favoriteKeys, recentOrder) {
        viewModel.updateMemoryHints(favoriteKeys, recentOrder)
    }

    if (uiState.isLoading || (uiState.isSyncing && uiState.allChannels.isEmpty())) {
        Box(modifier = Modifier.fillMaxSize().background(TvColors.Background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.CircularProgressIndicator(color = TvColors.BorderFocused)
                Text(
                    text = "Syncing Live TV...",
                    color = TvColors.TextSub,
                    fontSize = 16.sp,
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

    if (uiState.allChannels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(TvColors.Background), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = "No live channels yet.", color = TvColors.TextSub, fontSize = 16.sp)
                Text(
                    text = "Connect an M3U or Xtream provider to build your Live TV guide.",
                    color = TvColors.TextSub,
                    fontSize = 14.sp
                )
                TvFocusCard(onClick = onOpenSetup) { isFocused ->
                    Text(text = "Open IPTV setup", color = if (isFocused) TvColors.TextMain else TvColors.TextSub, fontSize = 14.sp)
                }
            }
        }
        return
    }

    val categories = uiState.categories
    val activeCategory = uiState.selectedCategory
    val syncWarnings = uiState.syncWarnings
    val safeChannels = uiState.filteredChannels
    val searchQuery = uiState.searchQuery
    val sortMode = uiState.sortMode
    val selectedView = uiState.selectedView
    val selectedLanguage = uiState.selectedLanguage
    val selectedCountry = uiState.selectedCountry
    val reloadToken = uiState.reloadToken

    if (safeChannels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(TvColors.Background), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.isSyncing || uiState.isEnrichingFacets) {
                    androidx.compose.material3.CircularProgressIndicator(color = TvColors.BorderFocused)
                    Text(text = "Syncing Live TV...", color = TvColors.TextSub, fontSize = 16.sp)
                } else {
                    Text(text = "No channels match your filters.", color = TvColors.TextSub, fontSize = 16.sp)
                    TvFocusCard(onClick = { viewModel.clearFilters() }) { isFocused ->
                        Text(
                            text = "Clear filters",
                            color = if (isFocused) TvColors.TextMain else TvColors.TextSub,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
        return
    }

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

    fun clearFilters() {
        viewModel.clearFilters()
    }

    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30000L)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val selectedChannelState = remember {
        mutableStateOf(safeChannels.firstOrNull() ?: Channel("", "No channels match", null, "", "General"))
    }
    // Keep the focused channel stable across category/search/filter changes; only fall back to the
    // first entry when the previously-selected channel is no longer in the filtered set (bug #14).
    LaunchedEffect(safeChannels) {
        val current = selectedChannelState.value
        if (current.id.isBlank() || safeChannels.none { it.id == current.id }) {
            selectedChannelState.value =
                safeChannels.firstOrNull() ?: Channel("", "No channels match", null, "", "General")
        }
    }
    val selectedChannel = selectedChannelState.value
    val listState = rememberLazyListState()
    val firstChannelFocusRequester = remember { FocusRequester() }

    val visibleEpgChannelIds by remember(safeChannels) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (safeChannels.isEmpty()) return@derivedStateOf emptyList<String>()
            val visible = layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) {
                return@derivedStateOf safeChannels.take(40).map { it.id }
            }
            val buffer = 10
            val first = (visible.first().index - buffer).coerceAtLeast(0)
            val last = (visible.last().index + buffer).coerceAtMost(safeChannels.lastIndex)
            safeChannels.subList(first, last + 1).map { it.id }
        }
    }

    var nowNextMap by remember { mutableStateOf(emptyMap<String, com.example.calmsource.feature.iptv.EpgNowNext>()) }
    val safeChannelIds = remember(safeChannels) { safeChannels.map { it.id } }

    LaunchedEffect(safeChannelIds) {
        val validIds = safeChannelIds.toSet()
        nowNextMap = nowNextMap.filterKeys { it in validIds }
    }

    LaunchedEffect(visibleEpgChannelIds, reloadToken, currentTimeMs) {
        if (visibleEpgChannelIds.isEmpty()) return@LaunchedEffect
        val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            IPTVRepository.getNowNextForChannels(visibleEpgChannelIds, currentTimeMs)
        }
        nowNextMap = nowNextMap + loaded
    }

    LaunchedEffect(selectedChannel.id, currentTimeMs, reloadToken) {
        if (selectedChannel.id.isBlank()) return@LaunchedEffect
        if (selectedChannel.id !in nowNextMap) {
            val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                IPTVRepository.getNowNextForChannel(selectedChannel.id, currentTimeMs)
            }
            if (loaded != null) {
                nowNextMap = nowNextMap + (selectedChannel.id to loaded)
            }
        }
    }

    LaunchedEffect(safeChannels.firstOrNull()?.id) {
        if (safeChannels.isNotEmpty()) {
            kotlinx.coroutines.delay(150)
            try {
                firstChannelFocusRequester.requestFocus()
            } catch (_: Exception) {
                // Focus may fail before the list is attached.
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Left Pane: Channels list (focusable)
        Column(
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, start = 8.dp)
            ) {
                Text(
                    text = "Channels",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TvColors.TextMain
                )
                TvFocusCard(
                    onClick = onOpenSetup,
                    modifier = Modifier.wrapContentSize()
                ) { focused ->
                    Text(
                        text = "Setup ⚙",
                        color = if (focused) TvColors.TextMain else TvColors.BorderFocused,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            TvTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search channels...", color = TvColors.TextSub, fontSize = 14.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            syncWarnings.forEach { warning ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFEF3C7))
                        .padding(10.dp)
                ) {
                    Text(
                        text = warning,
                        color = Color(0xFFD97706),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (uiState.isEnrichingFacets) {
                Text(
                    text = "Enriching guide…",
                    color = TvColors.TextSub,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
                )
            }

            var filtersExpanded by remember { mutableStateOf(false) }

            TvFocusCard(
                onClick = { filtersExpanded = !filtersExpanded },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) { focused ->
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (filtersExpanded) "Collapse Filters ▴" else "Expand Filters ▾",
                        color = if (focused) TvColors.TextMain else TvColors.BorderFocused,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val activeFilterCount = (if (searchQuery.isNotEmpty()) 1 else 0) +
                            (if (selectedView != IptvLiveGuideView.LIVE) 1 else 0) +
                            (if (selectedLanguage != IptvLiveGuideFilters.ALL_LANGUAGES) 1 else 0) +
                            (if (selectedCountry != IptvLiveGuideFilters.ALL_REGIONS) 1 else 0) +
                            (if (sortMode != IptvLiveGuideSort.RECOMMENDED) 1 else 0) +
                            (if (uiState.selectedCategory != "All") 1 else 0)
                    if (activeFilterCount > 0) {
                        Text(
                            text = "$activeFilterCount active",
                            color = TvColors.TextSub,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            AnimatedVisibility(visible = filtersExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // View filters
                    Text("View:", color = TvColors.TextSub, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(IptvLiveGuideView.entries, key = { it.name }) { view ->
                            TvFocusCard(
                                onClick = { viewModel.setSelectedView(view) }
                            ) { focused ->
                                Text(
                                    text = view.label,
                                    color = if (selectedView == view || focused) TvColors.TextMain else TvColors.TextSub,
                                    fontWeight = if (selectedView == view) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Sort modes
                    Text("Sort:", color = TvColors.TextSub, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(IptvLiveGuideSort.entries, key = { it.name }) { mode ->
                            TvFocusCard(
                                onClick = { viewModel.setSortMode(mode) }
                            ) { focused ->
                                Text(
                                    text = when (mode) {
                                        IptvLiveGuideSort.RECOMMENDED -> "Recommended"
                                        IptvLiveGuideSort.POPULAR -> "Popular"
                                        IptvLiveGuideSort.NAME -> "Name"
                                        IptvLiveGuideSort.CATEGORY -> "Category"
                                        IptvLiveGuideSort.LANGUAGE -> "Language"
                                        IptvLiveGuideSort.RECENT -> "Recent"
                                    },
                                    color = if (sortMode == mode || focused) TvColors.TextMain else TvColors.TextSub,
                                    fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Languages
                    if (languages.isNotEmpty()) {
                        Text("Language:", color = TvColors.TextSub, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                TvFocusCard(
                                    onClick = { viewModel.setSelectedLanguage(IptvLiveGuideFilters.ALL_LANGUAGES) }
                                ) { focused ->
                                    Text(
                                        text = "All",
                                        color = if (selectedLanguage == IptvLiveGuideFilters.ALL_LANGUAGES || focused) TvColors.TextMain else TvColors.TextSub,
                                        fontWeight = if (selectedLanguage == IptvLiveGuideFilters.ALL_LANGUAGES) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            items(languages, key = { it }) { lang ->
                                TvFocusCard(
                                    onClick = { viewModel.setSelectedLanguage(lang) }
                                ) { focused ->
                                    Text(
                                        text = lang,
                                        color = if (selectedLanguage == lang || focused) TvColors.TextMain else TvColors.TextSub,
                                        fontWeight = if (selectedLanguage == lang) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Regions
                    if (countries.isNotEmpty()) {
                        Text("Region:", color = TvColors.TextSub, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                TvFocusCard(
                                    onClick = { viewModel.setSelectedCountry(IptvLiveGuideFilters.ALL_REGIONS) }
                                ) { focused ->
                                    Text(
                                        text = "All",
                                        color = if (selectedCountry == IptvLiveGuideFilters.ALL_REGIONS || focused) TvColors.TextMain else TvColors.TextSub,
                                        fontWeight = if (selectedCountry == IptvLiveGuideFilters.ALL_REGIONS) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            items(countries, key = { it }) { country ->
                                TvFocusCard(
                                    onClick = { viewModel.setSelectedCountry(country) }
                                ) { focused ->
                                    Text(
                                        text = country,
                                        color = if (selectedCountry == country || focused) TvColors.TextMain else TvColors.TextSub,
                                        fontWeight = if (selectedCountry == country) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Reset button
                    TvFocusCard(
                        onClick = ::clearFilters,
                        modifier = Modifier.fillMaxWidth()
                    ) { focused ->
                        Text(
                            text = "Clear filters",
                            color = if (focused) TvColors.TextMain else TvColors.BorderFocused,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }

            if (categories.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    items(categories, key = { it }) { category ->
                        TvFocusCard(
                            modifier = Modifier.widthIn(min = 86.dp, max = 170.dp),
                            onClick = { viewModel.setSelectedCategory(category) }
                        ) { focused ->
                            Text(
                                text = category,
                                color = if (activeCategory == category || focused) TvColors.TextMain else TvColors.TextSub,
                                fontSize = 12.sp,
                                fontWeight = if (activeCategory == category) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                if (activeCategory != "All") {
                    TvFocusCard(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val categoryToHide = activeCategory
                            memoryScope.launch {
                                IPTVRepository.setLiveChannelGroupHidden(categoryToHide, hidden = true)
                                viewModel.bumpReloadToken()
                            }
                        }
                    ) { focused ->
                        Text(
                            text = "Hide category",
                            color = if (focused) TvColors.TextMain else TvColors.BorderFocused,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
                TvFocusCard(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        memoryScope.launch {
                            IPTVRepository.restoreHiddenIptvChannels()
                            viewModel.bumpReloadToken()
                        }
                    }
                ) { focused ->
                    Text(
                        text = "Reset hidden",
                        color = if (focused) TvColors.TextMain else TvColors.BorderFocused,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                itemsIndexed(safeChannels, key = { _, channel -> channel.id }) { index, channel ->
                    val iptvChannel = iptvChannelById[channel.id]
                    val memoryReference = iptvChannel?.toUserMemoryReference()
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TvFocusCard(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (index == 0) {
                                        Modifier.focusRequester(firstChannelFocusRequester)
                                    } else {
                                        Modifier
                                    }
                                ),
                            onClick = {
                                val activeProg = nowNextMap[channel.id]?.currentProgram?.let {
                                    Program(
                                        id = it.id,
                                        channelId = channel.id,
                                        title = it.title,
                                        description = it.description,
                                        startTimeMs = it.startTimeMs,
                                        endTimeMs = it.endTimeMs
                                    )
                                }
                                onChannelSelect(channel, activeProg)
                            },
                            onFocusChanged = { focused ->
                                if (focused) selectedChannelState.value = channel
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AsyncImage(
                                    model = channel.logoUrl,
                                    contentDescription = "Logo for ${channel.name}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0x1AFFFFFF))
                                )
                                Column {
                                    Text(
                                        text = channel.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TvColors.TextMain,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = channel.category ?: "General",
                                        fontSize = 12.sp,
                                        color = TvColors.BorderFocused,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (memoryReference != null) {
                            TvFocusCard(
                                modifier = Modifier.width(58.dp),
                                onClick = {
                                    memoryScope.launch {
                                        runCatching {
                                            memoryRepository.toggleFavorite(memoryReference)
                                        }
                                    }
                                }
                            ) { favoriteFocused ->
                                Text(
                                    text = if (memoryReference.itemKey in favoriteKeys) "Fav" else "+Fav",
                                    color = if (favoriteFocused) TvColors.TextMain else TvColors.BorderFocused,
                                    fontSize = 12.sp,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                        TvFocusCard(
                            modifier = Modifier.width(58.dp),
                            onClick = {
                                memoryScope.launch {
                                    IPTVRepository.setLiveChannelHidden(channel.id, hidden = true)
                                    viewModel.bumpReloadToken()
                                }
                            }
                        ) { hideFocused ->
                            Text(
                                text = "Hide",
                                color = if (hideFocused) TvColors.TextMain else TvColors.TextSub,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        }

        // Right Pane: Selected Channel Programs Guide
        TvLiveGuideRightPane(
            selectedChannelState = selectedChannelState,
            nowNextMap = nowNextMap,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TvLiveGuideRightPane(
    selectedChannelState: State<Channel>,
    nowNextMap: Map<String, com.example.calmsource.feature.iptv.EpgNowNext>,
    modifier: Modifier = Modifier
) {
    val selectedChannel = selectedChannelState.value
    val selectedNowNext = nowNextMap[selectedChannel.id]
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(TvColors.Surface)
            .padding(16.dp)
    ) {
        Text(
            text = "${selectedChannel.name} Schedule",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TvColors.TextMain,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (selectedNowNext?.currentProgram == null && selectedNowNext?.nextProgram == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No program info available", color = TvColors.TextSub)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                selectedNowNext.currentProgram?.let { currentProg ->
                    item(key = "current_${currentProg.id}") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1F8B5CF6))
                                .padding(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = currentProg.title,
                                    color = TvColors.BorderFocused,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${timeFormatter.format(Date(currentProg.startTimeMs))} - ${timeFormatter.format(Date(currentProg.endTimeMs))}",
                                    color = TvColors.TextSub,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = currentProg.description ?: "No description.",
                                color = TvColors.TextSub,
                                fontSize = 12.sp,
                                maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            // Progress Bar
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = selectedNowNext.progressPercentage,
                                    color = TvColors.BorderFocused,
                                    trackColor = Color(0x1AFFFFFF),
                                    modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                                )
                            }
                            Text(
                                text = "★ AIRING NOW - Press OK on channel to view",
                                color = TvColors.BorderFocused,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                selectedNowNext.nextProgram?.let { nextProg ->
                    item(key = "next_${nextProg.id}") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Transparent)
                                .padding(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = nextProg.title,
                                    color = TvColors.TextMain,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${timeFormatter.format(Date(nextProg.startTimeMs))} - ${timeFormatter.format(Date(nextProg.endTimeMs))}",
                                    color = TvColors.TextSub,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = nextProg.description ?: "No description.",
                                color = TvColors.TextSub,
                                fontSize = 12.sp,
                                maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

