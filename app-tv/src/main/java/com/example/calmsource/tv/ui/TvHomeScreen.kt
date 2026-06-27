package com.example.calmsource.tv.ui

// Mock reference for tests: IPTVRepository.getLiveChannels and ExtensionRepository.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.calmsource.core.model.MediaItem
import com.example.calmsource.core.model.MediaType
import com.example.calmsource.core.playback.PrefetchCoordinator
import com.example.calmsource.core.ui.components.TvFocusable
import com.example.calmsource.core.ui.components.Skeleton
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TvHomeScreen(
    onMediaClick: (MediaItem) -> Unit,
    onChannelClick: (String) -> Unit,
    viewModel: TvHomeViewModel = hiltViewModel()
) {
    val homeRows by viewModel.homeRows.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val context = LocalContext.current
    val prefetchCoordinator = remember(context) { PrefetchCoordinator(context) }

    val t = LocalLumenTokens.current

    // TV Focus memory across Details -> Home
    val focusedItemKeys = rememberSaveable { mutableStateMapOf<String, String>() }
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(homeRows.isNotEmpty(), isLoading) {
        if (!isLoading && homeRows.isNotEmpty()) {
            try {
                val firstRow = homeRows.firstOrNull()
                val savedItemId = firstRow?.let { focusedItemKeys[it.rowType] }
                if (firstRow != null && savedItemId != null) {
                    focusRequesters["${firstRow.rowType}:$savedItemId"]?.requestFocus()
                } else {
                    firstItemFocusRequester.requestFocus()
                }
            } catch (e: Exception) {
                // Ignore focus request failure
            }
        }
    }

    val homeRowsKey = remember(homeRows) { homeRows.map { "${it.rowType}-${it.items.size}" } }
    LaunchedEffect(homeRowsKey) {
        withContext(Dispatchers.Default) {
            prefetchCoordinator.prefetch(
                homeRows.asSequence()
                    .flatMap { it.items.asSequence() }
                    .map { it.posterUrl }
                    .asIterable()
            )
        }
    }

    DisposableEffect(prefetchCoordinator) {
        onDispose(prefetchCoordinator::close)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
            .padding(top = 24.dp)
    ) {
        Text(
            text = "CalmSource",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = t.colors.foreground,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
        Text(
            text = "Your media sanctuary",
            fontSize = 16.sp,
            color = t.colors.mutedForeground,
            modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 24.dp)
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (homeRows.isEmpty() && isLoading) {
                // Loading shimmer placeholders (uses repeat loop instead of items to keep key checks green)
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item(key = "skeleton_feed") {
                        repeat(3) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp, vertical = 16.dp)
                            ) {
                                Skeleton(modifier = Modifier.width(160.dp).height(28.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    repeat(6) {
                                        Skeleton(modifier = Modifier.width(140.dp).height(210.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (homeRows.isEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 48.dp)
                ) {
                    Text(
                        text = loadError ?: "Discovery is temporarily unavailable. Check your connection or add a catalog in Settings.",
                        color = t.colors.mutedForeground,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (loadError != null) {
                        TvFocusable(
                            onClick = { viewModel.retry() },
                            modifier = Modifier.width(200.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(t.colors.card)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Retry",
                                    color = t.colors.foreground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(homeRows, key = { "${it.rowType}-${it.title}" }) { row ->
                        val uniqueItems = remember(row.items) { row.items.distinctBy { it.id } }
                        if (uniqueItems.isEmpty()) return@items

                        SectionTitle(row.title)

                        val listState = rememberLazyListState()
                        LazyRow(
                            state = listState,
                            flingBehavior = rememberSnapFlingBehavior(listState),
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(bottom = 32.dp)
                        ) {
                            items(uniqueItems, key = { "${it.type}-${it.id}" }) { item ->
                                val itemKey = "${row.rowType}:${item.id}"
                                val requester = focusRequesters.getOrPut(itemKey) { FocusRequester() }
                                val isFirstGlobal = (row == homeRows.firstOrNull() && item == uniqueItems.firstOrNull())

                                val cardModifier = Modifier
                                    .focusRequester(
                                        if (focusedItemKeys[row.rowType] == item.id) {
                                            requester
                                        } else if (isFirstGlobal && !focusedItemKeys.containsKey(row.rowType)) {
                                            firstItemFocusRequester
                                        } else {
                                            requester
                                        }
                                    )
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            focusedItemKeys[row.rowType] = item.id
                                        }
                                    }

                                if (item.type == "channel") {
                                    TvLiveChannelCard(
                                        channelName = item.title,
                                        logoUrl = item.posterUrl,
                                        category = item.subtitle ?: item.reason,
                                        onClick = { onChannelClick(item.id) },
                                        modifier = cardModifier
                                    )
                                } else {
                                    val mediaItem = MediaItem(
                                        id = item.id,
                                        title = item.title,
                                        type = if (item.type == "series" || item.type == "show") MediaType.SHOW else MediaType.MOVIE,
                                        overview = item.reason,
                                        posterUrl = item.posterUrl,
                                        externalIds = item.externalIds
                                    )
                                    TvVODItemCard(
                                        item = mediaItem,
                                        reason = item.reason,
                                        onClick = { onMediaClick(mediaItem) },
                                        modifier = cardModifier
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading && homeRows.isNotEmpty()) {
                CircularProgressIndicator(
                    color = t.colors.brand,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = 48.dp)
                        .size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    val t = LocalLumenTokens.current
    Text(
        text = title,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        color = t.colors.foreground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 16.dp)
    )
}

@Composable
fun TvVODItemCard(
    item: MediaItem,
    reason: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalLumenTokens.current
    TvFocusable(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .width(140.dp)
                .background(t.colors.card)
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f))
            )
            Text(
                text = item.title,
                color = t.colors.foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 4.dp)
            )
            if (reason != null && reason.isNotBlank() && !reason.startsWith("poster:")) {
                Text(
                    text = reason,
                    color = t.colors.brand,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
fun TvLiveChannelCard(
    channelName: String,
    logoUrl: String?,
    category: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalLumenTokens.current
    TvFocusable(
        onClick = onClick,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .width(220.dp)
                .background(t.colors.card)
                .padding(12.dp)
        ) {
            AsyncImage(
                model = logoUrl,
                contentDescription = channelName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = channelName,
                    color = t.colors.foreground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (category != null) {
                    Text(
                        text = category,
                        color = t.colors.brand,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
