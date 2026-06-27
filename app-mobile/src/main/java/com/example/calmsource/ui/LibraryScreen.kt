package com.example.calmsource.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
fun LibraryScreen(
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
                android.util.Log.e("LibraryScreen", "Failed to initialize RoomUserMemoryRepository", e)
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
    val searchHistory by repository.observeSearchHistory().collectAsState(initial = emptyList())
    val resumePositions = remember(continueWatching) {
        continueWatching.associate { it.reference.itemKey to it.progressMs }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Library",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextMain,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            Text(
                text = "Your saved and recently watched items",
                color = AppColors.TextSub,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        item {
            LibrarySectionHeader(
                title = "Continue Watching",
                hasItems = continueWatching.isNotEmpty(),
                onClear = { scope.launch { repository.clearContinueWatching() } }
            )
        }
        if (continueWatching.isEmpty()) {
            item { EmptyLibraryRow("Start a movie or show and it will appear here.") }
        } else {
            items(continueWatching, key = { "continue-${it.reference.itemKey}" }) { item ->
                MemoryRow(
                    reference = item.reference,
                    subtitle = progressLabel(item.progressMs, item.durationMs),
                    progress = if (item.durationMs > 0L) {
                        (item.progressMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
                    } else {
                        null
                    },
                    onClick = { onOpenMedia(item.reference, item.progressMs) },
                    onRemove = { scope.launch { repository.removeContinueWatching(item.reference.itemKey) } }
                )
            }
        }

        item {
            LibrarySectionHeader(
                title = "Favorites",
                hasItems = favorites.isNotEmpty(),
                onClear = { scope.launch { repository.clearFavorites() } }
            )
        }
        if (favorites.isEmpty()) {
            item { EmptyLibraryRow("Favorite movies, shows, VOD, or channels to keep them here.") }
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
                    onRemove = { scope.launch { repository.removeFavorite(item.reference.itemKey) } }
                )
            }
        }

        item {
            LibrarySectionHeader(
                title = "Watch History",
                hasItems = history.isNotEmpty(),
                onClear = { scope.launch { repository.clearWatchHistory() } }
            )
        }
        if (history.isEmpty()) {
            item { EmptyLibraryRow("Watched movies and shows will appear here.") }
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
                    onRemove = { scope.launch { repository.removeWatchHistory(item.reference.itemKey) } }
                )
            }
        }

        item {
            LibrarySectionHeader(
                title = "Recent Channels",
                hasItems = recentChannels.isNotEmpty(),
                onClear = { scope.launch { repository.clearRecentChannels() } }
            )
        }
        if (recentChannels.isEmpty()) {
            item { EmptyLibraryRow("Channels you watch will appear here.") }
        } else {
            items(recentChannels, key = { "recent-${it.reference.itemKey}" }) { item ->
                val isLast = lastChannel?.reference?.itemKey == item.reference.itemKey
                MemoryRow(
                    reference = item.reference,
                    subtitle = if (isLast) "Last watched channel" else item.reference.subtitle,
                    onClick = { onOpenChannel(item.reference) },
                    onRemove = { scope.launch { repository.removeRecentChannel(item.reference.itemKey) } }
                )
            }
        }

        item {
            LibrarySectionHeader(
                title = "Search History",
                hasItems = searchHistory.isNotEmpty(),
                onClear = { scope.launch { repository.clearSearchHistory() } }
            )
        }
        if (searchHistory.isEmpty()) {
            item { EmptyLibraryRow("Completed searches will appear here.") }
        } else {
            items(searchHistory, key = { "search-${it.query.lowercase()}" }) { item ->
                SearchHistoryRow(
                    query = item.query,
                    onClick = { onSearch(item.query) },
                    onRemove = { scope.launch { repository.removeSearch(item.query) } }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun LibrarySectionHeader(
    title: String,
    hasItems: Boolean,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = AppColors.TextMain,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (hasItems) {
            TextButton(onClick = onClear) {
                Text("Clear", color = AppColors.Primary)
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
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reference.title,
                    color = AppColors.TextMain,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = AppColors.TextSub,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = AppColors.Primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove ${reference.title}",
                    tint = AppColors.TextSub
                )
            }
        }
    }
}

@Composable
private fun SearchHistoryRow(
    query: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(query, color = AppColors.TextMain, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Remove search", tint = AppColors.TextSub)
        }
    }
}

@Composable
private fun EmptyLibraryRow(message: String) {
    Text(
        text = message,
        color = AppColors.TextSub,
        modifier = Modifier.padding(bottom = 12.dp)
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
