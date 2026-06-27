package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.LumenTokens

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
import com.example.calmsource.core.ui.components.LumenSkeleton
import com.example.calmsource.core.ui.components.LumenEmptyState
import com.example.calmsource.core.ui.components.LumenErrorState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TvHomeScreen(
    onMediaClick: (MediaItem) -> Unit,
    onChannelClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
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
            .padding(top = LumenTokens.Space.xxl)
    ) {
        Text(
            text = "CalmSource",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = t.colors.foreground,
            modifier = Modifier.padding(horizontal = LumenTokens.Layout.iconXl)
        )
        Text(
            text = "Your media sanctuary",
            fontSize = 16.sp,
            color = t.colors.mutedForeground,
            modifier = Modifier.padding(start = LumenTokens.Layout.iconXl, end = LumenTokens.Layout.iconXl, bottom = LumenTokens.Space.xxl)
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (homeRows.isEmpty() && isLoading) {
                // Loading shimmer placeholders
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item(key = "skeleton_feed") {
                        repeat(3) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = LumenTokens.Layout.iconXl, vertical = LumenTokens.Space.lg)
                            ) {
                                LumenSkeleton(modifier = Modifier.width(LumenTokens.Layout.posterTileWidth).height(LumenTokens.Radius.xl))
                                Spacer(modifier = Modifier.height(LumenTokens.Space.lg))
                                Row(horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.lg)) {
                                    repeat(6) {
                                        LumenSkeleton(modifier = Modifier.width(LumenTokens.Layout.epgMinBlockWidthTv).height(LumenTokens.Layout.posterTileHeightTv))
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (homeRows.isEmpty() && loadError != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LumenErrorState(
                        title = "Failed to load feed",
                        body = loadError ?: "Unknown error",
                        onRetry = { viewModel.retry() }
                    )
                }
            } else if (homeRows.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LumenEmptyState(
                        title = "Nothing to browse yet",
                        body = "Connect a catalog provider in settings to begin.",
                        icon = Icons.Default.Home,
                        ctaText = "Go to Settings",
                        onCtaClick = onSettingsClick
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(homeRows, key = { "${it.rowType}-${it.title}-${it.items.size}-${it.hashCode()}" }) { row ->
                        val uniqueItems = remember(row.items) { row.items.distinctBy { it.id } }
                        if (uniqueItems.isEmpty()) return@items

                        SectionTitle(row.title)

                        val listState = rememberLazyListState()
                        LazyRow(
                            state = listState,
                            flingBehavior = rememberSnapFlingBehavior(listState),
                            contentPadding = PaddingValues(horizontal = LumenTokens.Layout.iconXl),
                            horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.lg),
                            modifier = Modifier.padding(bottom = LumenTokens.Space.xxxl)
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
                        .padding(horizontal = LumenTokens.Layout.iconXl)
                        .size(LumenTokens.Space.xxl)
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
        modifier = Modifier.padding(start = LumenTokens.Layout.iconXl, end = LumenTokens.Layout.iconXl, bottom = LumenTokens.Space.lg)
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
                .width(LumenTokens.Layout.epgMinBlockWidthTv)
                .background(t.colors.card)
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LumenTokens.Layout.posterTileHeightTv)
                    .clip(LumenTokens.Shape.sm)
                    .background(LumenTokens.Color.textPrimary.copy(alpha = 0.05f))
            )
            Text(
                text = item.title,
                color = t.colors.foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = LumenTokens.Space.sm2, start = LumenTokens.Space.sm2, end = LumenTokens.Space.sm2, bottom = LumenTokens.Space.xs)
            )
            if (reason != null && reason.isNotBlank() && !reason.startsWith("poster:")) {
                Text(
                    text = reason,
                    color = t.colors.brand,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = LumenTokens.Space.sm2, end = LumenTokens.Space.sm2, bottom = LumenTokens.Space.sm2)
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
                .width(LumenTokens.Layout.tileWidthMd)
                .background(t.colors.card)
                .padding(LumenTokens.Space.md)
        ) {
            AsyncImage(
                model = logoUrl,
                contentDescription = channelName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(LumenTokens.Layout.iconXl)
                    .clip(LumenTokens.Shape.sm)
                    .background(LumenTokens.Color.textPrimary.copy(alpha = 0.05f))
            )
            Spacer(modifier = Modifier.width(LumenTokens.Space.md))
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
