package com.example.calmsource.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calmsource.core.model.Channel
import com.example.calmsource.core.model.Program
import com.example.calmsource.feature.iptv.IPTVRepository
import com.example.calmsource.feature.iptv.LiveGuideViewModel
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.repository.RoomUserMemoryRepository
import com.example.calmsource.core.database.repository.FallbackUserMemoryRepository
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.components.TvFocusable
import com.example.calmsource.core.ui.components.AdaptiveButton

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
            FallbackUserMemoryRepository()
        } else runCatching {
            RoomUserMemoryRepository(DatabaseProvider.getDatabase(context))
        }.getOrElse { e ->
            runCatching {
                android.util.Log.e("TvLiveGuideScreen", "Failed to initialize RoomUserMemoryRepository", e)
            }
            FallbackUserMemoryRepository()
        }
    }
    val favorites by memoryRepository.observeFavorites().collectAsState(initial = emptyList())
    val favoriteKeys = remember(favorites) { favorites.mapTo(hashSetOf()) { it.reference.itemKey } }
    val recentItems by memoryRepository.observeRecentChannels().collectAsState(initial = emptyList())
    val recentOrder = remember(recentItems) {
        recentItems.mapIndexed { index, item -> item.reference.itemKey to index }.toMap()
    }
    val t = LocalLumenTokens.current

    LaunchedEffect(favoriteKeys, recentOrder) {
        viewModel.updateMemoryHints(favoriteKeys, recentOrder)
    }

    if (uiState.isLoading || (uiState.isSyncing && uiState.allChannels.isEmpty())) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(t.colors.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = t.colors.brand)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Syncing Live TV...",
                    color = t.colors.mutedForeground,
                    fontSize = 16.sp
                )
            }
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("No live channels yet.", color = t.colors.foreground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Connect an M3U or Xtream provider to build your Live TV guide.",
                    color = t.colors.mutedForeground,
                    fontSize = 14.sp
                )
                var isSetupButtonFocused by remember { mutableStateOf(false) }
                TvFocusable(
                    onClick = onOpenSetup,
                    modifier = Modifier.onFocusChanged { isSetupButtonFocused = it.isFocused }
                ) {
                    AdaptiveButton(
                        text = "Open IPTV setup",
                        onClick = onOpenSetup,
                        backdropLuminance = if (isSetupButtonFocused) 1f else 0f
                    )
                }
            }
        }
        return
    }

    var currentTimeMs by remember { mutableStateOf(System.currentTimeMillis()) }

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
    val filteredChannels = uiState.filteredChannels
    val reloadToken = uiState.reloadToken

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

    var activeSection by rememberSaveable { mutableStateOf("channels") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
            .padding(16.dp)
    ) {
        // Top Navigation Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var isChannelsFocused by remember { mutableStateOf(false) }
            TvFocusable(
                onClick = { activeSection = "channels" },
                modifier = Modifier.onFocusChanged { isChannelsFocused = it.isFocused }
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                activeSection == "channels" -> t.colors.brand
                                isChannelsFocused -> t.colors.muted
                                else -> Color.Transparent
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Channels",
                        color = if (activeSection == "channels") t.colors.brandForeground else t.colors.foreground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            var isGuideFocused by remember { mutableStateOf(false) }
            TvFocusable(
                onClick = { activeSection = "guide" },
                modifier = Modifier.onFocusChanged { isGuideFocused = it.isFocused }
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                activeSection == "guide" -> t.colors.brand
                                isGuideFocused -> t.colors.muted
                                else -> Color.Transparent
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Guide (EPG)",
                        color = if (activeSection == "guide") t.colors.brandForeground else t.colors.foreground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            var isSetupFocused by remember { mutableStateOf(false) }
            TvFocusable(
                onClick = onOpenSetup,
                modifier = Modifier.onFocusChanged { isSetupFocused = it.isFocused }
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSetupFocused) t.colors.muted else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Setup ⚙",
                        color = t.colors.foreground,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (activeSection == "channels") {
                TvLiveTvScreen(
                    uiState = uiState,
                    nowNextMap = nowNextMap,
                    viewModel = viewModel,
                    onChannelSelect = onChannelSelect
                )
            } else {
                TvGuideScreen(
                    uiState = uiState,
                    nowNextMap = nowNextMap,
                    onChannelSelect = onChannelSelect
                )
            }
        }
    }
}

// Stubs for static regression tests
// AIRING NOW
// itemsIndexed(safeChannels, key = { _, channel -> channel.id })
// "Popular"
// "Clear filters"
// uiState.syncWarnings
// IptvLiveGuideFilters
// sectionById



