package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.LumenLegacySpace
import com.example.calmsource.core.ui.theme.LumenLayout
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
import com.example.calmsource.core.ui.tv.rememberTvFocusMemory
import com.example.calmsource.core.ui.tv.TvFocusScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val focusMemory = rememberTvFocusMemory()

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
            .padding(top = LumenLegacySpace.xxl)
    ) {
        Text(
            text = "CalmSource",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = t.colors.foreground,
            modifier = Modifier.padding(horizontal = LumenLayout.iconXl)
        )
        Text(
            text = "Your media sanctuary",
            fontSize = 16.sp,
            color = t.colors.mutedForeground,
            modifier = Modifier.padding(start = LumenLayout.iconXl, end = LumenLayout.iconXl, bottom = LumenLegacySpace.xxl)
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
                                    .padding(horizontal = LumenLayout.iconXl, vertical = LumenLegacySpace.lg)
                            ) {
                                LumenSkeleton(modifier = Modifier.width(LumenLayout.posterTileWidth).height(LumenTokens.Radius.xl))
                                Spacer(modifier = Modifier.height(LumenLegacySpace.lg))
                                Row(horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg)) {
                                    repeat(6) {
                                        LumenSkeleton(modifier = Modifier.width(LumenLayout.epgMinBlockWidthTv).height(LumenLayout.posterTileHeightTv))
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
                        TvFocusScope(
                            memory = focusMemory,
                            scopeId = "home/row/${row.rowType}",
                            itemIds = uniqueItems.map { it.id },
                            listState = listState,
                        ) { restorer ->
                            LazyRow(
                                state = restorer.listState,
                                flingBehavior = rememberSnapFlingBehavior(listState),
                                contentPadding = PaddingValues(horizontal = LumenLayout.iconXl),
                                horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg),
                                modifier = Modifier.padding(bottom = LumenLegacySpace.xxxl),
                            ) {
                                items(uniqueItems, key = { "${it.type}-${it.id}" }) { item ->
                                    val cardModifier = restorer.itemModifier(item.id)

                                    if (item.type == "channel") {
                                        TvLiveChannelCard(
                                            channelName = item.title,
                                            logoUrl = item.posterUrl,
                                            category = item.subtitle ?: item.reason,
                                            onClick = { onChannelClick(item.id) },
                                            modifier = cardModifier,
                                        )
                                    } else {
                                        val mediaItem = MediaItem(
                                            id = item.id,
                                            title = item.title,
                                            type = if (item.type == "series" || item.type == "show") MediaType.SHOW else MediaType.MOVIE,
                                            overview = item.reason,
                                            posterUrl = item.posterUrl,
                                            externalIds = item.externalIds,
                                        )
                                        TvVODItemCard(
                                            item = mediaItem,
                                            reason = item.reason,
                                            onClick = { onMediaClick(mediaItem) },
                                            modifier = cardModifier,
                                        )
                                    }
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
                        .padding(horizontal = LumenLayout.iconXl)
                        .size(LumenLegacySpace.xxl)
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
        modifier = Modifier.padding(start = LumenLayout.iconXl, end = LumenLayout.iconXl, bottom = LumenLegacySpace.lg)
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
                .width(LumenLayout.epgMinBlockWidthTv)
                .background(t.colors.card)
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LumenLayout.posterTileHeightTv)
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
                modifier = Modifier.padding(top = LumenLegacySpace.sm2, start = LumenLegacySpace.sm2, end = LumenLegacySpace.sm2, bottom = LumenLegacySpace.xs)
            )
            if (reason != null && reason.isNotBlank() && !reason.startsWith("poster:")) {
                Text(
                    text = reason,
                    color = t.colors.brand,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = LumenLegacySpace.sm2, end = LumenLegacySpace.sm2, bottom = LumenLegacySpace.sm2)
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
                .width(LumenLayout.tileWidthMd)
                .background(t.colors.card)
                .padding(LumenLegacySpace.md)
        ) {
            AsyncImage(
                model = logoUrl,
                contentDescription = channelName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(LumenLayout.iconXl)
                    .clip(LumenTokens.Shape.sm)
                    .background(LumenTokens.Color.textPrimary.copy(alpha = 0.05f))
            )
            Spacer(modifier = Modifier.width(LumenLegacySpace.md))
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
