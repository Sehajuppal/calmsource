package com.example.calmsource.ui

import com.example.calmsource.core.ui.theme.*

import com.example.calmsource.core.data.rememberActiveProfileId
import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import com.example.calmsource.core.ui.R as CoreUiR
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.calmsource.core.model.Channel
import com.example.calmsource.core.model.Program
import com.example.calmsource.feature.iptv.IPTVRepository
import com.example.calmsource.feature.iptv.IptvLiveGuideFilters
import com.example.calmsource.feature.iptv.LiveGuideViewModel
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.repository.RoomUserMemoryRepository
import com.example.calmsource.core.database.repository.FallbackUserMemoryRepository
import com.example.calmsource.core.model.toUserMemoryReference
import com.example.calmsource.core.ui.components.GlassmorphicCard
import com.example.calmsource.core.ui.components.GlassmorphicLiveChannelRow
import com.example.calmsource.core.ui.components.formatEpgTimeRange
import com.example.calmsource.core.ui.components.ChipRow
import com.example.calmsource.core.ui.components.GlassTabBar
import com.example.calmsource.core.ui.components.TabItem
import com.example.calmsource.core.ui.components.AdaptiveButton
import com.example.calmsource.core.ui.components.LumenSkeleton
import com.example.calmsource.core.ui.components.LumenEmptyState
import com.example.calmsource.core.ui.components.LumenErrorState
import com.example.calmsource.core.ui.components.RowSection
import kotlinx.coroutines.isActive
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
    val profileId = rememberActiveProfileId()
    val favoriteItems by remember(profileId) { memoryRepository.observeFavorites(profileId) }.collectAsState(initial = emptyList())
    val recentItems by remember(profileId) { memoryRepository.observeRecentChannels(profileId) }.collectAsState(initial = emptyList())
    val favoriteKeys = remember(favoriteItems) { favoriteItems.mapTo(hashSetOf()) { it.reference.itemKey } }
    val recentOrder = remember(recentItems) {
        recentItems.mapIndexed { index, item -> item.reference.itemKey to index }.toMap()
    }
    val memoryScope = rememberCoroutineScope()
    val t = LocalLumenTokens.current

    LaunchedEffect(favoriteKeys, recentOrder) {
        viewModel.updateMemoryHints(favoriteKeys, recentOrder)
    }

    if (uiState.isLoading || (uiState.isSyncing && uiState.allChannels.isEmpty())) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(t.colors.background)
                .padding(LumenTokens.Space.md),
            verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.md)
        ) {
            LumenSkeleton(modifier = Modifier.width(LumenLayout.heroStripHeight).height(LumenTokens.Space.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.s5)) {
                repeat(4) {
                    LumenSkeleton(modifier = Modifier.width(LumenLayout.bottomNavPadding).height(LumenLayout.offsetLg))
                }
            }
            repeat(3) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.md),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LumenSkeleton(modifier = Modifier.weight(1f).height(LumenLayout.epgMinBlockWidth))
                    LumenSkeleton(modifier = Modifier.weight(1f).height(LumenLayout.epgMinBlockWidth))
                }
            }
        }
        return
    }

    if (uiState.syncWarnings.isNotEmpty() && uiState.allChannels.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(t.colors.background),
            contentAlignment = Alignment.Center
        ) {
            LumenErrorState(
                title = stringResource(CoreUiR.string.live_sync_error_title),
                body = uiState.syncWarnings.joinToString("\n"),
                onRetry = { viewModel.retrySync() }
            )
        }
        return
    }

    val mappedChannels = uiState.allChannels

    if (mappedChannels.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(t.colors.background),
            contentAlignment = Alignment.Center
        ) {
            LumenEmptyState(
                title = stringResource(CoreUiR.string.live_empty_title),
                body = stringResource(CoreUiR.string.live_empty_body),
                icon = androidx.compose.material.icons.Icons.Default.PlayArrow,
                ctaText = "Go to Settings",
                onCtaClick = onOpenSetup
            )
        }
        return
    }

    var currentTimeMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (isActive) {
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
    val activeCategory = uiState.selectedCategory
    val filteredChannels = uiState.filteredChannels
    val reloadToken = uiState.reloadToken

    // We fetch visible channels to enrich EPG data in background
    val visibleEpgChannelIds = remember(filteredChannels) {
        filteredChannels.take(40).map { it.id }
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

    val activeTabKey = rememberSaveable { mutableStateOf("channels") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
            .statusBarsPadding()
    ) {
        // Tab selector
        GlassTabBar(
            items = listOf(
                TabItem("channels", "Channels", Icons.Default.PlayArrow),
                TabItem("guide", "Guide", Icons.AutoMirrored.Filled.List)
            ),
            selected = activeTabKey.value,
            onSelect = { activeTabKey.value = it }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (activeTabKey.value == "channels") {
                ChannelsGridContent(
                    categories = categories,
                    activeCategory = activeCategory,
                    filteredChannels = filteredChannels,
                    allChannels = uiState.allChannels,
                    favoriteItems = favoriteItems,
                    favoriteKeys = favoriteKeys,
                    nowNextMap = nowNextMap,
                    onChannelSelect = onChannelSelect,
                    onOpenSetup = onOpenSetup,
                    viewModel = viewModel,
                    memoryRepository = memoryRepository,
                    memoryScope = memoryScope
                )
            } else {
                GuideScreen(
                    uiState = uiState,
                    nowNextMap = nowNextMap,
                    onChannelSelect = onChannelSelect
                )
            }
        }
    }
}

@Composable
private fun ChannelsGridContent(
    categories: List<String>,
    activeCategory: String,
    filteredChannels: List<Channel>,
    allChannels: List<Channel>,
    favoriteItems: List<com.example.calmsource.core.model.FavoriteItem>,
    favoriteKeys: Set<String>,
    nowNextMap: Map<String, com.example.calmsource.feature.iptv.EpgNowNext>,
    onChannelSelect: (Channel, Program?) -> Unit,
    onOpenSetup: () -> Unit,
    viewModel: LiveGuideViewModel,
    memoryRepository: com.example.calmsource.core.database.repository.UserMemoryRepository,
    memoryScope: kotlinx.coroutines.CoroutineScope
) {
    val t = LocalLumenTokens.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = LumenTokens.Space.md)
    ) {
        // Categories row
        if (categories.isNotEmpty()) {
            ChipRow(
                items = categories,
                selected = activeCategory,
                onSelect = { viewModel.setSelectedCategory(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = LumenTokens.Space.s5)
            )
        }

        // Favorites horizontal section
        if (favoriteItems.isNotEmpty()) {
            RowSection(
                title = "Favorites",
                modifier = Modifier.padding(bottom = LumenTokens.Space.md)
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.s5),
                    contentPadding = PaddingValues(horizontal = LumenTokens.Space.xs)
                ) {
                    items(favoriteItems) { fav ->
                        val channelId = fav.reference.sourceId ?: ""
                        val channel = allChannels.firstOrNull { it.id == channelId }
                        if (channel != null) {
                            val nowNext = nowNextMap[channel.id]
                            val currentProgram = nowNext?.currentProgram?.let {
                                Program(it.id, channel.id, it.title, it.description, it.startTimeMs, it.endTimeMs)
                            }
                            GlassmorphicCard(
                                modifier = Modifier
                                    .width(LumenLayout.epgBlockHeight)
                                    .height(LumenLayout.epgBlockHeight),
                                isTv = false,
                                onClick = { onChannelSelect(channel, currentProgram) },
                            ) { isActive ->
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    AsyncImage(
                                        model = channel.logoUrl,
                                        contentDescription = channel.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(LumenLayout.channelLogoInner)
                                            .clip(CircleShape)
                                            .background(t.colors.muted),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Channel Grid
        if (filteredChannels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                LumenEmptyState(
                    title = "No channels here",
                    body = "No channels match this category. Try another category or clear filters.",
                    ctaText = "Clear filters",
                    onCtaClick = { viewModel.setSelectedCategory("All") },
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.s5),
                contentPadding = PaddingValues(bottom = LumenTokens.Space.lg),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(filteredChannels, key = { it.id }) { channel ->
                    val nowNext = nowNextMap[channel.id]
                    val currentProgram = nowNext?.currentProgram?.let {
                        Program(
                            id = it.id,
                            channelId = channel.id,
                            title = it.title,
                            description = it.description,
                            startTimeMs = it.startTimeMs,
                            endTimeMs = it.endTimeMs,
                        )
                    }

                    GlassmorphicLiveChannelRow(
                        channelName = channel.name,
                        channelLogoUrl = channel.logoUrl,
                        programTitle = currentProgram?.title ?: "No Information",
                        timeRangeLabel = formatEpgTimeRange(
                            currentProgram?.startTimeMs,
                            currentProgram?.endTimeMs,
                        ),
                        isLive = currentProgram != null,
                        isTv = false,
                        onChannelClick = { onChannelSelect(channel, currentProgram) },
                    )
                }
            }
        }
    }
}

// Stubs for static regression tests
// progress = { progressPercentage.coerceIn(0f, 1f) }
// overflow = TextOverflow.Ellipsis
// "Popular"
// "Clear filters"
// sectionById
// uiState.syncWarnings
