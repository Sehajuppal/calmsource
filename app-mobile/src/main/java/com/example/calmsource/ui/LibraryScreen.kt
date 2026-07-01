package com.example.calmsource.ui

import com.example.calmsource.core.ui.theme.*

import com.example.calmsource.core.data.rememberActiveProfileId
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import com.example.calmsource.core.ui.components.LumenCard
import com.example.calmsource.core.ui.components.LumenEmptyState
import com.example.calmsource.core.ui.components.PosterCard
import com.example.calmsource.core.ui.components.PosterOrientation
import com.example.calmsource.core.ui.components.LumenHorizontalRowFade
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.repository.RoomUserMemoryRepository
import com.example.calmsource.core.database.repository.FallbackUserMemoryRepository
import com.example.calmsource.core.model.UserMemoryContentType
import com.example.calmsource.core.model.UserMemoryReference
import androidx.compose.ui.res.stringResource
import com.example.calmsource.core.ui.R as CoreUiR
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.calmsource.core.discoveryengine.DiscoveryEngine
import com.example.calmsource.core.model.ContinueWatchingItem
import com.example.calmsource.feature.iptv.IPTVRepository
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Composable
fun LibraryScreen(
    onOpenMedia: (UserMemoryReference, Long) -> Unit,
    onOpenChannel: (UserMemoryReference) -> Unit,
    onSearch: (String) -> Unit,
    onBrowse: () -> Unit = {},
    onOpenLive: () -> Unit = onBrowse,
    onOpenSettings: () -> Unit = {},
) {
    val t = LocalLumenTokens.current
    val context = LocalContext.current.applicationContext
    // Gate Room access on databaseReady so we never build the database on the main thread before
    // the deferred warmup completes; use the in-memory fallback until the DB has been built on IO.
    val dbReady by DatabaseProvider.databaseReady.collectAsState()
    val repository = remember(context, dbReady) {
        if (!dbReady) {
            FallbackUserMemoryRepository()
        } else runCatching {
            RoomUserMemoryRepository(DatabaseProvider.getDatabase(context))
        }.getOrElse { e ->
            runCatching {
                android.util.Log.e("LibraryScreen", "Failed to initialize RoomUserMemoryRepository", e)
            }
            FallbackUserMemoryRepository()
        }
    }
    val profileId = rememberActiveProfileId()
    val scope = rememberCoroutineScope()
    val continueWatching by remember(repository, profileId) { repository.observeContinueWatching(profileId) }.collectAsState(initial = emptyList())
    val favorites by remember(repository, profileId) { repository.observeFavorites(profileId) }.collectAsState(initial = emptyList())
    val history by remember(repository, profileId) { repository.observeWatchHistory(profileId) }.collectAsState(initial = emptyList())
    val recentChannels by remember(repository, profileId) { repository.observeRecentChannels(profileId) }.collectAsState(initial = emptyList())
    val lastChannel by remember(repository, profileId) { repository.observeLastWatchedChannel(profileId) }.collectAsState(initial = null)
    val searchHistory by remember(repository, profileId) { repository.observeSearchHistory(profileId) }.collectAsState(initial = emptyList())
    val resumePositions = remember(continueWatching) {
        continueWatching.associate { it.reference.itemKey to it.progressMs }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
            .statusBarsPadding()
            .padding(horizontal = LumenTokens.Space.md),
        verticalArrangement = Arrangement.spacedBy(LumenTokens.Radius.sm),
        contentPadding = PaddingValues(bottom = LumenLayout.bottomNavPadding),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(CoreUiR.string.library_title),
                        style = LumenType.H1.toTextStyle(),
                        color = t.colors.foreground,
                        modifier = Modifier.padding(top = LumenTokens.Space.md, bottom = LumenTokens.Space.sm),
                    )
                    Text(
                        text = stringResource(CoreUiR.string.library_subtitle),
                        color = t.colors.mutedForeground,
                        modifier = Modifier.padding(bottom = LumenTokens.Space.s5),
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.padding(top = LumenTokens.Space.md),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(CoreUiR.string.nav_settings),
                        tint = t.colors.foreground,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm),
            ) {
                LibraryStat(
                    value = continueWatching.size.toString(),
                    label = stringResource(CoreUiR.string.library_in_progress),
                    modifier = Modifier.weight(1f),
                )
                LibraryStat(
                    value = favorites.size.toString(),
                    label = stringResource(CoreUiR.string.library_saved),
                    modifier = Modifier.weight(1f),
                )
                LibraryStat(
                    value = recentChannels.size.toString(),
                    label = stringResource(CoreUiR.string.library_recent_live),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            LibrarySectionHeader(
                title = stringResource(CoreUiR.string.library_continue_watching),
                hasItems = continueWatching.isNotEmpty(),
                onClear = { scope.launch { repository.clearContinueWatching(profileId) } }
            )
        }
        if (continueWatching.isEmpty()) {
            item {
                EmptyLibraryRow(
                    title = stringResource(CoreUiR.string.library_empty_in_progress_title),
                    body = stringResource(CoreUiR.string.library_empty_in_progress_body),
                    ctaText = stringResource(CoreUiR.string.cta_browse_home),
                    onCtaClick = onBrowse,
                )
            }
        } else {
            item {
                LumenHorizontalRowFade(modifier = Modifier.padding(bottom = LumenTokens.Space.s5)) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = LumenTokens.Space.md),
                        horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.s5),
                    ) {
                        items(continueWatching, key = { "continue-${it.reference.itemKey}" }) { item ->
                            ContinueWatchingPosterCard(
                                item = item,
                                onClick = { onOpenMedia(item.reference, item.progressMs) },
                                modifier = Modifier.width(LumenLayout.tileWidthMd),
                            )
                        }
                    }
                }
            }
        }

        item {
            LibrarySectionHeader(
                title = stringResource(CoreUiR.string.library_favorites),
                hasItems = favorites.isNotEmpty(),
                onClear = { scope.launch { repository.clearFavorites(profileId) } }
            )
        }
        if (favorites.isEmpty()) {
            item {
                EmptyLibraryRow(
                    title = stringResource(CoreUiR.string.library_empty_favorites_title),
                    body = stringResource(CoreUiR.string.library_empty_favorites_body),
                    ctaText = stringResource(CoreUiR.string.cta_browse_home),
                    onCtaClick = onBrowse,
                )
            }
        } else {
            items(favorites, key = { "favorite-${it.reference.itemKey}" }) { item ->
                MemoryRow(
                    reference = item.reference,
                    subtitle = item.reference.contentType.displayName(),
                    onClick = {
                        if (item.reference.contentType == UserMemoryContentType.LIVE_CHANNEL) {
                            onOpenChannel(item.reference)
                        } else {
                            onOpenMedia(item.reference, 0L)
                        }
                    },
                    onRemove = { scope.launch { repository.removeFavorite(item.reference.itemKey, profileId) } }
                )
            }
        }

        item {
            LibrarySectionHeader(
                title = stringResource(CoreUiR.string.library_watch_history),
                hasItems = history.isNotEmpty(),
                onClear = { scope.launch { repository.clearWatchHistory(profileId) } }
            )
        }
        if (history.isEmpty()) {
            item {
                EmptyLibraryRow(
                    title = stringResource(CoreUiR.string.library_no_history_title),
                    body = stringResource(CoreUiR.string.library_no_history_body),
                    ctaText = stringResource(CoreUiR.string.cta_browse_home),
                    onCtaClick = onBrowse,
                )
            }
        } else {
            items(history, key = { "history-${it.reference.itemKey}" }) { item ->
                MemoryRow(
                    reference = item.reference,
                    subtitle = "Watched ${item.watchCount} time${if (item.watchCount == 1L) "" else "s"}",
                    onClick = {
                        onOpenMedia(
                            item.reference,
                            resumePositions[item.reference.itemKey] ?: 0L
                        )
                    },
                    onRemove = { scope.launch { repository.removeWatchHistory(item.reference.itemKey, profileId) } }
                )
            }
        }

        item {
            LibrarySectionHeader(
                title = stringResource(CoreUiR.string.library_recent_channels),
                hasItems = recentChannels.isNotEmpty(),
                onClear = { scope.launch { repository.clearRecentChannels(profileId) } }
            )
        }
        if (recentChannels.isEmpty()) {
            item {
                EmptyLibraryRow(
                    title = stringResource(CoreUiR.string.library_no_recent_channels_title),
                    body = stringResource(CoreUiR.string.library_no_recent_channels_body),
                    ctaText = stringResource(CoreUiR.string.cta_open_live_tv),
                    onCtaClick = onOpenLive,
                )
            }
        } else {
            items(recentChannels, key = { "recent-${it.reference.itemKey}" }) { item ->
                val isLast = lastChannel?.reference?.itemKey == item.reference.itemKey
                MemoryRow(
                    reference = item.reference,
                    subtitle = if (isLast) "Last watched channel" else item.reference.subtitle,
                    onClick = { onOpenChannel(item.reference) },
                    onRemove = { scope.launch { repository.removeRecentChannel(item.reference.itemKey, profileId) } }
                )
            }
        }

        item {
            LibrarySectionHeader(
                title = stringResource(CoreUiR.string.library_search_history),
                hasItems = searchHistory.isNotEmpty(),
                onClear = { scope.launch { repository.clearSearchHistory(profileId) } }
            )
        }
        if (searchHistory.isEmpty()) {
            item {
                EmptyLibraryRow(
                    title = stringResource(CoreUiR.string.library_no_search_history_title),
                    body = stringResource(CoreUiR.string.library_no_search_history_body),
                    ctaText = stringResource(CoreUiR.string.cta_search_catalog),
                    onCtaClick = { onSearch("") },
                )
            }
        } else {
            items(searchHistory, key = { "search-${it.query.lowercase()}" }) { item ->
                SearchHistoryRow(
                    query = item.query,
                    onClick = { onSearch(item.query) },
                    onRemove = { scope.launch { repository.removeSearch(item.query, profileId) } }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(LumenTokens.Space.lg)) }
    }
}

@Composable
private fun LibrarySectionHeader(
    title: String,
    hasItems: Boolean,
    onClear: () -> Unit
) {
    val t = LocalLumenTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = t.colors.foreground,
            fontSize = LumenType.size20,
            fontWeight = FontWeight.SemiBold
        )
        if (hasItems) {
            TextButton(onClick = onClear) {
                Text("Clear", color = t.colors.brand)
            }
        }
    }
}

@Composable
private fun MemoryRow(
    reference: UserMemoryReference,
    subtitle: String? = null,
    progress: Float? = null,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val t = LocalLumenTokens.current
    LumenCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemoryArtwork(reference)
            Spacer(modifier = Modifier.size(LumenTokens.Space.s5))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reference.title,
                    color = t.colors.foreground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = t.colors.mutedForeground,
                        fontSize = LumenType.size12,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = t.colors.brand,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = LumenTokens.Space.sm)
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove ${reference.title}",
                    tint = t.colors.mutedForeground
                )
            }
        }
    }
}

@Composable
private fun LibraryStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val t = LocalLumenTokens.current
    Column(
        modifier = modifier
            .clip(LumenTokens.Shape.md)
            .background(t.colors.card)
            .padding(LumenTokens.Space.s5),
    ) {
        Text(
            text = value,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            color = t.colors.foreground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = t.colors.mutedForeground,
            maxLines = 1,
        )
    }
}

@Composable
private fun MemoryArtwork(reference: UserMemoryReference) {
    val t = LocalLumenTokens.current
    val accent = when (reference.contentType) {
        UserMemoryContentType.LIVE_CHANNEL -> LumenTokens.Color.cyan
        UserMemoryContentType.SHOW, UserMemoryContentType.EPISODE -> LumenTokens.Color.violet
        UserMemoryContentType.MOVIE, UserMemoryContentType.VOD -> t.colors.brandGlow
    }
    Box(
        modifier = Modifier
            .size(LumenLayout.avatarLg)
            .clip(LumenTokens.Shape.md)
            .background(Brush.linearGradient(listOf(accent, t.colors.card))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = reference.title.firstOrNull()?.uppercase() ?: "•",
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            color = t.colors.foreground,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun SearchHistoryRow(
    query: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val t = LocalLumenTokens.current
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = LumenTokens.Radius.sm)
            .semantics(mergeDescendants = true) {
                contentDescription = context.getString(CoreUiR.string.search_for_query, query)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(query, color = t.colors.foreground, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Remove search", tint = t.colors.mutedForeground)
        }
    }
}

@Composable
private fun EmptyLibraryRow(
    title: String,
    body: String,
    ctaText: String? = null,
    onCtaClick: (() -> Unit)? = null,
) {
    LumenEmptyState(
        title = title,
        body = body,
        ctaText = ctaText,
        onCtaClick = onCtaClick,
        modifier = Modifier.padding(bottom = LumenTokens.Space.s5),
    )
}

private fun progressLabel(progressMs: Long, durationMs: Long): String {
    if (durationMs <= 0L) return "Resume playback"
    val percent = ((progressMs * 100L) / durationMs).coerceIn(0L, 100L)
    return "$percent% watched"
}

private fun UserMemoryContentType.displayName(): String {
    return name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun ContinueWatchingPosterCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var posterUrl by remember(item.reference.itemKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.reference) {
        posterUrl = withContext(Dispatchers.IO) { resolveMemoryPosterUrl(item.reference) }
    }
    val progress = if (item.durationMs > 0L) {
        (item.progressMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
    } else {
        null
    }
    PosterCard(
        imageUrl = posterUrl,
        contentLabel = item.reference.title,
        orientation = PosterOrientation.Landscape,
        progress = progress,
        onClick = onClick,
        modifier = modifier,
    )
}

private suspend fun resolveMemoryPosterUrl(reference: UserMemoryReference): String? {
    return when (reference.contentType) {
        UserMemoryContentType.LIVE_CHANNEL, UserMemoryContentType.VOD -> {
            reference.sourceId?.let { IPTVRepository.findChannel(it)?.tvgLogo }
        }
        else -> {
            val mediaId = reference.sourceId ?: return null
            DiscoveryEngine.lookupMediaPosterUrl(mediaId)
        }
    }
}
