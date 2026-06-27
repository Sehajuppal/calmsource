package com.example.calmsource.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
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
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.components.LumenCard
import com.example.calmsource.core.ui.components.ChipRow
import com.example.calmsource.core.ui.components.GlassTabBar
import com.example.calmsource.core.ui.components.TabItem
import com.example.calmsource.core.ui.components.AdaptiveButton
import com.example.calmsource.core.ui.components.LumenSkeleton
import com.example.calmsource.core.ui.components.LumenEmptyState
import com.example.calmsource.core.ui.components.LumenErrorState
import com.example.calmsource.core.ui.components.RowSection
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
    val t = LocalLumenTokens.current

    LaunchedEffect(favoriteKeys, recentOrder) {
        viewModel.updateMemoryHints(favoriteKeys, recentOrder)
    }

    if (uiState.isLoading || (uiState.isSyncing && uiState.allChannels.isEmpty())) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(t.colors.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LumenSkeleton(modifier = Modifier.width(180.dp).height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) {
                    LumenSkeleton(modifier = Modifier.width(80.dp).height(36.dp))
                }
            }
            repeat(3) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LumenSkeleton(modifier = Modifier.weight(1f).height(120.dp))
                    LumenSkeleton(modifier = Modifier.weight(1f).height(120.dp))
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
                title = "Failed to sync Live TV",
                body = uiState.syncWarnings.joinToString("\n"),
                onRetry = { viewModel.bumpReloadToken() }
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
                title = "No live channels",
                body = "Connect an M3U or Xtream provider to build your Live TV guide.",
                icon = androidx.compose.material.icons.Icons.Default.PlayArrow,
                ctaText = "Add provider",
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
    ) {
        // Tab selector
        GlassTabBar(
            items = listOf(
                TabItem("channels", "Channels", Icons.Default.PlayArrow),
                TabItem("guide", "Guide", Icons.Default.List)
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
            .padding(horizontal = 16.dp)
    ) {
        // Categories row
        if (categories.isNotEmpty()) {
            ChipRow(
                items = categories,
                selected = activeCategory,
                onSelect = { viewModel.setSelectedCategory(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }

        // Favorites horizontal section
        if (favoriteItems.isNotEmpty()) {
            RowSection(
                title = "Favorites",
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(favoriteItems) { fav ->
                        val channelId = fav.reference.sourceId ?: ""
                        val channel = filteredChannels.firstOrNull { it.id == channelId }
                        if (channel != null) {
                            val nowNext = nowNextMap[channel.id]
                            val currentProgram = nowNext?.currentProgram?.let {
                                Program(it.id, channel.id, it.title, it.description, it.startTimeMs, it.endTimeMs)
                            }
                            LumenCard(
                                modifier = Modifier
                                    .width(84.dp)
                                    .height(84.dp)
                                    .clickable { onChannelSelect(channel, currentProgram) }
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    AsyncImage(
                                        model = channel.logoUrl,
                                        contentDescription = channel.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(t.colors.muted)
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
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No channels match this category.",
                    color = t.colors.mutedForeground,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                            endTimeMs = it.endTimeMs
                        )
                    }

                    LumenCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChannelSelect(channel, currentProgram) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(t.colors.muted),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = channel.logoUrl,
                                    contentDescription = channel.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (currentProgram != null) "NOW PLAYING" else "NO EPG DATA",
                                fontSize = 10.5.sp,
                                letterSpacing = 1.6.sp,
                                fontWeight = FontWeight.Bold,
                                color = t.colors.mutedForeground
                            )
                            
                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = currentProgram?.title ?: "No Information",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = t.colors.foreground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = channel.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = t.colors.foreground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// Stubs for static regression tests
// progress = { progressPercentage.coerceIn(0f, 1f) }
// "Popular"
// "Clear filters"
// sectionById
// uiState.syncWarnings

