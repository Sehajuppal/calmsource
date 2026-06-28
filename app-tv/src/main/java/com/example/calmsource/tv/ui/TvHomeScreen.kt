package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.*

// Mock reference for tests: IPTVRepository.getLiveChannels and ExtensionRepository.extensions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.example.calmsource.core.ui.tv.rememberTvFocusMemory
import com.example.calmsource.core.ui.tv.TvFocusScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                val featuredItem = remember(homeRows) {
                    homeRows.asSequence()
                        .filter { it.rowType != "continue_watching" && it.rowType != "live_tv" }
                        .flatMap { it.items.asSequence() }
                        .firstOrNull { it.type != "channel" && !it.posterUrl.isNullOrBlank() }
                }
                val initialSpotlight = remember(featuredItem) {
                    featuredItem?.let {
                        MediaItem(
                            id = it.id,
                            title = it.title,
                            type = if (it.type == "series" || it.type == "show") MediaType.SHOW else MediaType.MOVIE,
                            overview = it.reason,
                            posterUrl = it.posterUrl,
                            externalIds = it.externalIds,
                        )
                    }
                }
                var spotlight by remember { mutableStateOf<MediaItem?>(null) }
                LaunchedEffect(initialSpotlight?.id) {
                    if (spotlight == null) spotlight = initialSpotlight
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LumenTokens.Space.sectionGapTv),
                ) {
                    if (spotlight != null) {
                        item(key = "top_shelf") {
                            TvTopShelf(
                                item = spotlight!!,
                                eyebrow = "Selected for you",
                                onOpen = { spotlight?.let(onMediaClick) },
                            )
                        }
                    }

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
                                            landscape = row.rowType == "continue_watching",
                                            progress = item.reason
                                                .takeIf { row.rowType == "continue_watching" }
                                                ?.substringAfter("(", "")
                                                ?.substringBefore("%")
                                                ?.toFloatOrNull()
                                                ?.div(100f),
                                            onFocused = { spotlight = mediaItem },
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
        fontSize = LumenType.size24,
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
    landscape: Boolean = false,
    progress: Float? = null,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalLumenTokens.current
    var isFocused by remember { mutableStateOf(false) }
    TvFocusable(
        onClick = onClick,
        modifier = modifier.onFocusChanged { isFocused = it.isFocused },
        onFocused = onFocused,
    ) {
        Box(
            modifier = Modifier
                .width(if (landscape) LumenTokens.Tile.landscapeMobileW else LumenLayout.epgMinBlockWidthTv)
                .aspectRatio(if (landscape) LumenTokens.AspectRatio.landscape else LumenTokens.AspectRatio.poster)
                .clip(LumenTokens.Shape.sm)
                .background(t.colors.card)
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(LumenTokens.Color.textPrimary.copy(alpha = 0.05f))
            )

            AnimatedVisibility(
                visible = isFocused,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, t.colors.background.copy(alpha = 0.96f)),
                            ),
                        )
                        .padding(
                            start = LumenTokens.Space.s5,
                            end = LumenTokens.Space.s5,
                            top = LumenTokens.Space.s9,
                            bottom = LumenTokens.Space.s5,
                        ),
                ) {
                    Text(
                        text = item.title,
                        color = t.colors.foreground,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!reason.isNullOrBlank() && !reason.startsWith("poster:") && !reason.startsWith("Resume watching")) {
                        Text(
                            text = reason,
                            color = t.colors.mutedForeground,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (progress != null) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(LumenTokens.Space.s2)
                        .align(Alignment.BottomCenter),
                    color = t.colors.brand,
                    trackColor = Color.White.copy(alpha = 0.2f),
                )
            }
        }
    }
}

@Composable
private fun TvTopShelf(
    item: MediaItem,
    eyebrow: String,
    onOpen: () -> Unit,
) {
    val t = LocalLumenTokens.current
    Crossfade(
        targetState = item,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = LumenTokens.Duration.cinematic,
            easing = LumenTokens.Easing.AppleOut,
        ),
        label = "home-spotlight",
    ) { spotlightItem ->
      Box(
          modifier = Modifier
              .fillMaxWidth()
              .height(LumenLayout.detailsHeroHeight),
      ) {
        AsyncImage(
            model = spotlightItem.backdropUrl ?: spotlightItem.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to t.colors.background,
                        0.48f to t.colors.background.copy(alpha = 0.78f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to t.colors.background,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(LumenLayout.width480)
                .padding(start = LumenTokens.Space.sidePaddingTv),
        ) {
            Text(
                text = eyebrow.uppercase(),
                color = t.colors.brand,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(LumenTokens.Space.s4))
            Text(
                text = spotlightItem.title,
                color = t.colors.foreground,
                style = MaterialTheme.typography.displayMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!spotlightItem.overview.isNullOrBlank()) {
                Spacer(Modifier.height(LumenTokens.Space.s4))
                Text(
                    text = spotlightItem.overview.orEmpty(),
                    color = t.colors.mutedForeground,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(LumenTokens.Space.s6))
            TvFocusable(onClick = onOpen) {
                Row(
                    modifier = Modifier
                        .clip(LumenTokens.Shape.pill)
                        .background(t.colors.foreground)
                        .border(
                            width = LumenTokens.Focus.ringStroke,
                            color = Color.White.copy(alpha = 0.24f),
                            shape = LumenTokens.Shape.pill,
                        )
                        .padding(horizontal = LumenTokens.Space.s7, vertical = LumenTokens.Space.s5),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.s4),
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = t.colors.background)
                    Text(
                        text = "View details",
                        color = t.colors.background,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
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
                    fontSize = LumenType.size14,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (category != null) {
                    Text(
                        text = category,
                        color = t.colors.brand,
                        fontSize = LumenType.size11,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
