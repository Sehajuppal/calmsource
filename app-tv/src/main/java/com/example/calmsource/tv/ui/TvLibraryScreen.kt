package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import kotlinx.coroutines.launch

@Composable
fun TvLibraryScreen(
    onOpenMedia: (UserMemoryReference, Long) -> Unit,
    onOpenChannel: (UserMemoryReference) -> Unit,
    onSearch: (String) -> Unit
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
                android.util.Log.e("TvLibraryScreen", "Failed to initialize RoomUserMemoryRepository", e)
            }
            FallbackUserMemoryRepository()
        }
    }
    val scope = rememberCoroutineScope()
    val continueWatching by repository.observeContinueWatching().collectAsState(initial = emptyList())
    val favorites by repository.observeFavorites().collectAsState(initial = emptyList())
    val history by repository.observeWatchHistory().collectAsState(initial = emptyList())
    val recentChannels by repository.observeRecentChannels().collectAsState(initial = emptyList())
    val lastChannel by repository.observeLastWatchedChannel().collectAsState(initial = null)
    val searches by repository.observeSearchHistory().collectAsState(initial = emptyList())
    val resumePositions = remember(continueWatching) {
        continueWatching.associate { it.reference.itemKey to it.progressMs }
    }
    val continueFocus = remember { FocusRequester() }
    val favoritesFocus = remember { FocusRequester() }
    val historyFocus = remember { FocusRequester() }
    val recentFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }

    LaunchedEffect(continueWatching.isNotEmpty()) {
        if (continueWatching.isNotEmpty()) {
            kotlinx.coroutines.delay(150)
            try {
                continueFocus.requestFocus()
            } catch (_: Exception) {
                // Focus may fail before the list is attached.
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
            .padding(LumenLegacySpace.xxl),
        verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.md)
    ) {
        item {
            Text("My Space", color = t.colors.foreground, fontSize = LumenType.size38, fontWeight = FontWeight.Bold)
            Text(
                "Everything you meant to come back to.",
                color = t.colors.mutedForeground,
                fontSize = LumenType.size16,
                modifier = Modifier.padding(bottom = LumenLegacySpace.md)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md),
            ) {
                TvLibraryStat("${continueWatching.size}", "In progress", Modifier.weight(1f))
                TvLibraryStat("${favorites.size}", "Saved", Modifier.weight(1f))
                TvLibraryStat("${recentChannels.size}", "Recent live", Modifier.weight(1f))
            }
        }

        item {
            TvLibraryHeader("Continue Watching", continueWatching.isNotEmpty(), continueFocus) {
                continueFocus.requestFocus()
                scope.launch { repository.clearContinueWatching() }
            }
        }
        if (continueWatching.isEmpty()) {
            item { TvEmptyMemory("Start a movie or show and it will appear here.") }
        } else {
            items(continueWatching, key = { "continue-${it.reference.itemKey}" }) { item ->
                TvMemoryRow(
                    reference = item.reference,
                    subtitle = progressLabel(item.progressMs, item.durationMs),
                    progress = if (item.durationMs > 0L) {
                        (item.progressMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
                    } else null,
                    onOpen = { onOpenMedia(item.reference, item.progressMs) },
                    onRemove = {
                        continueFocus.requestFocus()
                        scope.launch { repository.removeContinueWatching(item.reference.itemKey) }
                    }
                )
            }
        }

        item {
            TvLibraryHeader("Favorites", favorites.isNotEmpty(), favoritesFocus) {
                favoritesFocus.requestFocus()
                scope.launch { repository.clearFavorites() }
            }
        }
        if (favorites.isEmpty()) {
            item { TvEmptyMemory("Favorite a movie, show, VOD item, or channel to keep it here.") }
        } else {
            items(favorites, key = { "favorite-${it.reference.itemKey}" }) { item ->
                TvMemoryRow(
                    reference = item.reference,
                    subtitle = item.reference.contentType.displayName(),
                    onOpen = {
                        if (item.reference.contentType == UserMemoryContentType.LIVE_CHANNEL) {
                            onOpenChannel(item.reference)
                        } else {
                            onOpenMedia(item.reference, 0L)
                        }
                    },
                    onRemove = {
                        favoritesFocus.requestFocus()
                        scope.launch { repository.removeFavorite(item.reference.itemKey) }
                    }
                )
            }
        }

        item {
            TvLibraryHeader("Watch History", history.isNotEmpty(), historyFocus) {
                historyFocus.requestFocus()
                scope.launch { repository.clearWatchHistory() }
            }
        }
        if (history.isEmpty()) {
            item { TvEmptyMemory("Watched movies and shows will appear here.") }
        } else {
            items(history, key = { "history-${it.reference.itemKey}" }) { item ->
                TvMemoryRow(
                    reference = item.reference,
                    subtitle = "Watched ${item.watchCount} time${if (item.watchCount == 1L) "" else "s"}",
                    onOpen = {
                        onOpenMedia(
                            item.reference,
                            resumePositions[item.reference.itemKey] ?: 0L
                        )
                    },
                    onRemove = {
                        historyFocus.requestFocus()
                        scope.launch { repository.removeWatchHistory(item.reference.itemKey) }
                    }
                )
            }
        }

        item {
            TvLibraryHeader("Recent Channels", recentChannels.isNotEmpty(), recentFocus) {
                recentFocus.requestFocus()
                scope.launch { repository.clearRecentChannels() }
            }
        }
        if (recentChannels.isEmpty()) {
            item { TvEmptyMemory("Channels you watch will appear here.") }
        } else {
            items(recentChannels, key = { "recent-${it.reference.itemKey}" }) { item ->
                TvMemoryRow(
                    reference = item.reference,
                    subtitle = if (lastChannel?.reference?.itemKey == item.reference.itemKey) {
                        "Last watched channel"
                    } else {
                        item.reference.subtitle
                    },
                    onOpen = { onOpenChannel(item.reference) },
                    onRemove = {
                        recentFocus.requestFocus()
                        scope.launch { repository.removeRecentChannel(item.reference.itemKey) }
                    }
                )
            }
        }

        item {
            TvLibraryHeader("Search History", searches.isNotEmpty(), searchFocus) {
                searchFocus.requestFocus()
                scope.launch { repository.clearSearchHistory() }
            }
        }
        if (searches.isEmpty()) {
            item { TvEmptyMemory("Completed searches will appear here.") }
        } else {
            items(searches, key = { "search-${it.query.lowercase()}" }) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2)
                ) {
                    TvFocusCard(
                        modifier = Modifier.weight(1f),
                        onClick = { onSearch(item.query) }
                    ) { focused ->
                        Text(
                            item.query,
                            color = if (focused) t.colors.foreground else t.colors.mutedForeground,
                            fontSize = LumenType.size17,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    TvRemoveAction {
                        searchFocus.requestFocus()
                        scope.launch { repository.removeSearch(item.query) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvLibraryHeader(
    title: String,
    hasItems: Boolean,
    focusRequester: FocusRequester,
    onClear: () -> Unit
) {
    val t = LocalLumenTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TvFocusCard(
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            onClick = {}
        ) {
            Text(
                title,
                color = t.colors.foreground,
                fontSize = LumenType.size22,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (hasItems) {
            TvFocusCard(modifier = Modifier.width(LumenLayout.clearButtonWidthTv), onClick = onClear) { focused ->
                Text(
                    "Clear",
                    color = if (focused) t.colors.foreground else t.colors.brand,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun TvMemoryRow(
    reference: UserMemoryReference,
    subtitle: String? = null,
    progress: Float? = null,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    val t = LocalLumenTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TvFocusCard(modifier = Modifier.weight(1f), onClick = onOpen) { focused ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md),
            ) {
                TvMemoryArtwork(reference)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        reference.title,
                        color = t.colors.foreground,
                        fontSize = LumenType.size18,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            subtitle,
                            color = if (focused) t.colors.foreground else t.colors.mutedForeground,
                            fontSize = LumenType.size13,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (progress != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = LumenLegacySpace.sm2)
                                .height(LumenLayout.progressHeight)
                                .background(t.colors.surface)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(LumenLayout.progressHeight)
                                    .background(t.colors.brand)
                            )
                        }
                    }
                }
            }
        }
        TvRemoveAction(onRemove)
    }
}

@Composable
private fun TvLibraryStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val t = LocalLumenTokens.current
    Row(
        modifier = modifier
            .clip(LumenTokens.Shape.md)
            .background(t.colors.card)
            .padding(horizontal = LumenLegacySpace.lg, vertical = LumenLegacySpace.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.md),
    ) {
        Text(value, color = t.colors.foreground, fontSize = LumenType.size28, fontWeight = FontWeight.Bold)
        Text(label, color = t.colors.mutedForeground, fontSize = LumenType.size13, maxLines = 1)
    }
}

@Composable
private fun TvMemoryArtwork(reference: UserMemoryReference) {
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
            color = t.colors.foreground,
            fontSize = LumenType.size28,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun TvRemoveAction(onClick: () -> Unit) {
    val t = LocalLumenTokens.current
    TvFocusCard(modifier = Modifier.width(LumenLayout.clearButtonWidthTv), onClick = onClick) { focused ->
        Text(
            "Remove",
            color = if (focused) t.colors.foreground else t.colors.mutedForeground,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            maxLines = 1
        )
    }
}

@Composable
private fun TvEmptyMemory(message: String) {
    val t = LocalLumenTokens.current
    Text(message, color = t.colors.mutedForeground, fontSize = LumenType.size15, modifier = Modifier.padding(bottom = LumenLegacySpace.sm2))
}

private fun progressLabel(progressMs: Long, durationMs: Long): String {
    if (durationMs <= 0L) return "Resume playback"
    return "${((progressMs * 100L) / durationMs).coerceIn(0L, 100L)}% watched"
}

private fun UserMemoryContentType.displayName(): String {
    return name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
