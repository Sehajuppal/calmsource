package com.example.calmsource.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
            .background(TvColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Library", color = TvColors.TextMain, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Text(
                "Continue watching, favorites, history, and recent channels",
                color = TvColors.TextSub,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TvFocusCard(
                        modifier = Modifier.weight(1f),
                        onClick = { onSearch(item.query) }
                    ) { focused ->
                        Text(
                            item.query,
                            color = if (focused) TvColors.TextMain else TvColors.TextSub,
                            fontSize = 17.sp,
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                color = TvColors.TextMain,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (hasItems) {
            TvFocusCard(modifier = Modifier.width(110.dp), onClick = onClear) { focused ->
                Text(
                    "Clear",
                    color = if (focused) TvColors.TextMain else TvColors.BorderFocused,
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TvFocusCard(modifier = Modifier.weight(1f), onClick = onOpen) { focused ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    reference.title,
                    color = TvColors.TextMain,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        color = if (focused) TvColors.TextMain else TvColors.TextSub,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (progress != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(5.dp)
                            .background(TvColors.Surface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(5.dp)
                                .background(TvColors.BorderFocused)
                        )
                    }
                }
            }
        }
        TvRemoveAction(onRemove)
    }
}

@Composable
private fun TvRemoveAction(onClick: () -> Unit) {
    TvFocusCard(modifier = Modifier.width(110.dp), onClick = onClick) { focused ->
        Text(
            "Remove",
            color = if (focused) TvColors.TextMain else TvColors.TextSub,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            maxLines = 1
        )
    }
}

@Composable
private fun TvEmptyMemory(message: String) {
    Text(message, color = TvColors.TextSub, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
}

private fun progressLabel(progressMs: Long, durationMs: Long): String {
    if (durationMs <= 0L) return "Resume playback"
    return "${((progressMs * 100L) / durationMs).coerceIn(0L, 100L)}% watched"
}

private fun UserMemoryContentType.displayName(): String {
    return name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
