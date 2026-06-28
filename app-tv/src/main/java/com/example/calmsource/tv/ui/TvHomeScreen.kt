package com.example.calmsource.tv.ui

import com.example.calmsource.core.ui.theme.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode

// Mock reference for tests: IPTVRepository.getLiveChannels and ExtensionRepository.extensions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.components.Hero
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
import com.example.calmsource.core.ui.components.GlassmorphicCard
import com.example.calmsource.core.ui.components.TvFocusable
import com.example.calmsource.core.ui.components.LumenSkeleton
import com.example.calmsource.core.ui.components.LumenEmptyState
import com.example.calmsource.core.ui.components.LumenErrorState
import com.example.calmsource.core.ui.components.LumenHorizontalRowFade
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TvHomeScreen(
    onMediaClick: (MediaItem) -> Unit,
    onResumeClick: (MediaItem, Long) -> Unit = { item, _ -> onMediaClick(item) },
    onChannelClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    onOpenSidebar: () -> Unit = {},
    viewModel: TvHomeViewModel = hiltViewModel()
) {
    val homeRows by viewModel.homeRows.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val context = LocalContext.current
    val prefetchCoordinator = remember(context) { PrefetchCoordinator(context) }

    val t = LocalLumenTokens.current

    val focusMemory = rememberTvFocusMemory()
    val homeListState = rememberLazyListState()
    val reducedMotion = LocalReducedMotion.current
    val hasTopShelf = remember(homeRows) {
        homeRows.any { row ->
            row.rowType != "continue_watching" &&
                row.rowType != "live_tv" &&
                row.items.any { it.type != "channel" && !it.posterUrl.isNullOrBlank() }
        }
    }

    var activeBackdropUrl by remember { mutableStateOf<String?>(null) }

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
        // Immersive background layer
        Crossfade(
            targetState = activeBackdropUrl,
            animationSpec = if (reducedMotion) tween(0) else tween(500),
            label = "ImmersiveTvBackdrop"
        ) { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(36.dp),
                    colorFilter = ColorFilter.tint(
                        Color.Black.copy(alpha = 0.68f),
                        BlendMode.SrcOver
                    )
                )
            }
        }

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
                        .flatMap { it.items.asSequence() }
                        .firstOrNull { it.type != "channel" && !it.posterUrl.isNullOrBlank() }
                }
                val initialSpotlight = remember(featuredItem) {
                    featuredItem?.let {
                        MediaItem(
                            id = it.id,
                            title = it.title,
                            type = if (it.type == "series" || it.type == "show") MediaType.SHOW else MediaType.MOVIE,
                            overview = sanitizeHomeBlurb(it.reason),
                            posterUrl = it.posterUrl,
                            backdropUrl = it.backdropUrl,
                            externalIds = it.externalIds,
                        )
                    }
                }
                var spotlight by remember { mutableStateOf<MediaItem?>(null) }
                LaunchedEffect(initialSpotlight?.id) {
                    if (spotlight == null) spotlight = initialSpotlight
                }
                LaunchedEffect(spotlight) {
                    kotlinx.coroutines.delay(200L)
                    activeBackdropUrl = spotlight?.backdropUrl
                }

                val spotlightSource = remember(spotlight?.id, homeRows) {
                    homeRows.asSequence()
                        .flatMap { row -> row.items.asSequence().map { item -> row to item } }
                        .firstOrNull { (_, item) -> item.id == spotlight?.id }
                }

                val navigableRowKeys = remember(homeRows) {
                    homeRows.mapNotNull { row ->
                        if (row.items.isEmpty()) null else "${row.rowType}-${row.title}"
                    }
                }
                val rowEntryFocusRequesters = remember(navigableRowKeys) {
                    navigableRowKeys.associateWith { FocusRequester() }
                }
                val heroFocusRequester = remember { FocusRequester() }

                LazyColumn(
                    state = homeListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LumenTokens.Space.sectionGapTv),
                ) {
                    if (spotlight != null) {
                        item(key = "top_shelf") {
                            TvTopShelf(
                                item = spotlight!!,
                                eyebrow = spotlightSource?.first?.title ?: "Featured",
                                onOpen = {
                                    spotlight?.let { mediaItem ->
                                        val resumeAt = spotlightSource?.second?.resumePositionMs
                                        if (resumeAt != null && resumeAt > 0L) {
                                            onResumeClick(mediaItem, resumeAt)
                                        } else {
                                            onMediaClick(mediaItem)
                                        }
                                    }
                                },
                                onOpenSidebar = onOpenSidebar,
                                heroFocusRequester = heroFocusRequester,
                                firstRowRequester = rowEntryFocusRequesters[navigableRowKeys.firstOrNull()],
                            )
                        }
                    }

                    items(homeRows, key = { it.rowType + "-" + it.title }) { row ->
                        val rowKey = "${row.rowType}-${row.title}"
                        val uniqueItems = remember(row.items) { row.items.distinctBy { it.id } }
                        if (uniqueItems.isEmpty()) return@items
                        val rowIndex = navigableRowKeys.indexOf(rowKey)
                        val rowEntryRequester = rowEntryFocusRequesters[rowKey]

                        Column(
                            modifier = Modifier,
                        ) {
                        SectionTitle(row.title)

                        val listState = rememberLazyListState()
                        TvFocusScope(
                            memory = focusMemory,
                            scopeId = "home/row/${row.rowType}",
                            itemIds = uniqueItems.map { it.id },
                            listState = listState,
                        ) { restorer ->
                            LumenHorizontalRowFade(
                                modifier = Modifier.padding(bottom = LumenLegacySpace.xxxl),
                            ) {
                                LazyRow(
                                    state = restorer.listState,
                                    flingBehavior = rememberSnapFlingBehavior(restorer.listState),
                                    contentPadding = PaddingValues(horizontal = LumenLayout.iconXl),
                                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg),
                                ) {
                                    itemsIndexed(uniqueItems, key = { _, item -> "${item.type}-${item.id}" }) { index, item ->
                                    val cardModifier = restorer.itemModifier(item.id)
                                        .then(
                                            if (index == 0 && rowEntryRequester != null) {
                                                Modifier.focusRequester(rowEntryRequester)
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .tvHomeVerticalRowNav(
                                            rowIndex = rowIndex,
                                            rowKeys = navigableRowKeys,
                                            rowEntryRequesters = rowEntryFocusRequesters,
                                            upTarget = if (rowIndex == 0) heroFocusRequester else null,
                                        )
                                        .then(
                                            if (index == 0) {
                                                Modifier.openTvSidebarOnLeftKey(onOpenSidebar)
                                            } else {
                                                Modifier
                                            },
                                        )

                                    if (item.type == "channel") {
                                        TvLiveChannelCard(
                                            channelName = item.title,
                                            logoUrl = item.posterUrl,
                                            category = item.subtitle,
                                            nowTitle = item.liveNowTitle,
                                            nextTitle = item.liveNextTitle,
                                            progress = item.liveProgress,
                                            onClick = { onChannelClick(item.id) },
                                            modifier = cardModifier,
                                        )
                                    } else {
                                        val mediaItem = MediaItem(
                                            id = item.id,
                                            title = item.title,
                                            type = if (item.type == "series" || item.type == "show") MediaType.SHOW else MediaType.MOVIE,
                                            overview = sanitizeHomeBlurb(item.reason),
                                            posterUrl = item.posterUrl,
                                            backdropUrl = item.backdropUrl,
                                            externalIds = item.externalIds,
                                        )
                                        val isLeavingSoon = row.rowType == "leaving_soon" ||
                                            row.title.contains("Leaving Soon", ignoreCase = true)
                                        TvVODItemCard(
                                            item = mediaItem,
                                            reason = if (isLeavingSoon) item.subtitle ?: item.reason else item.reason,
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
    GlassmorphicCard(
        onClick = onClick,
        modifier = modifier
            .width(if (landscape) LumenTokens.Tile.landscapeMobileW else LumenLayout.epgMinBlockWidthTv)
            .aspectRatio(if (landscape) LumenTokens.AspectRatio.landscape else LumenTokens.AspectRatio.poster),
        applyGlassFill = false,
        onFocused = onFocused,
    ) { isActive ->
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .background(LumenTokens.Color.textPrimary.copy(alpha = 0.05f)),
        )

        AnimatedVisibility(
            visible = isActive,
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

@Composable
private fun TvTopShelf(
    item: MediaItem,
    eyebrow: String,
    onOpen: () -> Unit,
    onOpenSidebar: () -> Unit,
    heroFocusRequester: FocusRequester,
    firstRowRequester: FocusRequester?,
) {
    val configuration = LocalConfiguration.current
    val reducedMotion = LocalReducedMotion.current
    val heroHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp * 0.5f).dp.coerceIn(
            260.dp,
            340.dp,
        )
    }

    Crossfade(
        targetState = item,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = if (reducedMotion) 0 else LumenTokens.Duration.cinematic,
            easing = LumenTokens.Easing.AppleOut,
        ),
        label = "home-spotlight",
    ) { spotlightItem ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight),
        ) {
            Hero(
                backdropUrl = spotlightItem.backdropUrl,
                posterUrl = spotlightItem.posterUrl,
                title = spotlightItem.title,
                tagline = sanitizeHomeBlurb(spotlightItem.overview),
                metadata = eyebrow.uppercase(),
                modifier = Modifier.fillMaxSize(),
                actions = {
                    TvHeroDetailsButton(
                        onClick = onOpen,
                        modifier = Modifier
                            .focusRequester(heroFocusRequester)
                            .tvHomeHeroVerticalNav(firstRowRequester)
                            .openTvSidebarOnLeftKey(onOpenSidebar),
                    )
                },
            )
        }
    }
}

@Composable
fun TvLiveChannelCard(
    channelName: String,
    logoUrl: String?,
    category: String?,
    nowTitle: String? = null,
    nextTitle: String? = null,
    progress: Float? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
) {
    val t = LocalLumenTokens.current
    GlassmorphicCard(
        modifier = modifier.width(LumenLayout.tileWidthMd),
        onClick = onClick,
        onFocused = onFocused,
    ) { isActive ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(LumenLegacySpace.md),
        ) {
            AsyncImage(
                model = logoUrl,
                contentDescription = channelName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(LumenLayout.iconXl)
                    .clip(LumenTokens.Shape.sm)
                    .background(LumenTokens.Color.textPrimary.copy(alpha = 0.05f)),
            )
            Spacer(modifier = Modifier.width(LumenLegacySpace.md))
            Column {
                Text(
                    text = channelName,
                    color = if (isActive) Color.Black else Color.White,
                    fontSize = LumenType.size14,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!nowTitle.isNullOrBlank()) {
                    Text(
                        text = nowTitle,
                        color = if (isActive) Color.Black.copy(alpha = 0.65f) else LumenTokens.Color.brand,
                        fontSize = LumenType.size11,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    progress?.let {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { it.coerceIn(0f, 1f) },
                            color = if (isActive) t.colors.background else t.colors.brand,
                            trackColor = if (isActive) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.18f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = LumenLegacySpace.sm),
                        )
                    }
                    nextTitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = "Next: $it",
                            color = if (isActive) Color.Black.copy(alpha = 0.55f) else t.colors.mutedForeground,
                            fontSize = LumenType.size11,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else if (category != null) {
                    Text(
                        text = category,
                        color = if (isActive) Color.Black.copy(alpha = 0.65f) else t.colors.mutedForeground,
                        fontSize = LumenType.size11,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
