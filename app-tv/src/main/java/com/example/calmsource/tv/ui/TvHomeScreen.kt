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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.components.Hero
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.calmsource.core.ui.R as CoreUiR
import com.example.calmsource.core.ui.components.PreviewSamplerCard
import com.example.calmsource.core.model.MediaItem
import com.example.calmsource.core.model.MediaType
import com.example.calmsource.core.playback.PrefetchCoordinator
import com.example.calmsource.core.ui.components.GlassmorphicCard
import com.example.calmsource.core.ui.components.TvFocusable
import com.example.calmsource.core.ui.components.LumenSkeleton
import com.example.calmsource.core.ui.components.LumenEmptyState
import com.example.calmsource.core.ui.components.LumenErrorState
import com.example.calmsource.core.ui.components.LumenHorizontalRowFade
import com.example.calmsource.core.ui.components.HomeSectionHeader
import kotlinx.collections.immutable.toImmutableList
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

    val displayRows = remember(homeRows) {
        homeRows.map { row ->
            val items = if (row.rowType == "live_tv") {
                row.items
            } else {
                row.items.filter { it.type != "channel" }
            }
            row.copy(items = items.toImmutableList())
        }.filter { it.items.isNotEmpty() }
    }

    val hasTopShelf = remember(displayRows) {
        displayRows.any { row ->
            row.rowType != "continue_watching" &&
                row.rowType != "live_tv" &&
                row.items.any { it.type != "channel" && !it.posterUrl.isNullOrBlank() }
        }
    }

    var activeBackdropUrl by remember { mutableStateOf<String?>(null) }

    val homeRowsKey = remember(displayRows) { displayRows.map { "${it.rowType}-${it.items.size}" } }
    LaunchedEffect(homeRowsKey) {
        withContext(Dispatchers.Default) {
            prefetchCoordinator.prefetch(
                displayRows.asSequence()
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
            if (displayRows.isEmpty() && isLoading) {
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
            } else if (displayRows.isEmpty() && loadError != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LumenErrorState(
                        title = stringResource(CoreUiR.string.error_load_feed),
                        body = loadError ?: stringResource(CoreUiR.string.error_unknown),
                        onRetry = { viewModel.retry() }
                    )
                }
            } else if (displayRows.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LumenEmptyState(
                        title = stringResource(CoreUiR.string.empty_nothing_to_browse),
                        body = stringResource(CoreUiR.string.empty_connect_provider),
                        icon = Icons.Default.Home,
                        ctaText = stringResource(CoreUiR.string.cta_go_to_settings),
                        onCtaClick = onSettingsClick
                    )
                }
            } else {
                val featuredItem = remember(displayRows) {
                    displayRows.asSequence()
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

                val spotlightSource = remember(spotlight?.id, displayRows) {
                    displayRows.asSequence()
                        .flatMap { row -> row.items.asSequence().map { item -> row to item } }
                        .firstOrNull { (_, item) -> item.id == spotlight?.id }
                }

                val navigableRowKeys = remember(displayRows) {
                    displayRows.mapNotNull { row ->
                        if (row.items.isEmpty()) null else "${row.rowType}-${row.title}"
                    }
                }
                val rowEntryFocusRequesters = remember(navigableRowKeys) {
                    navigableRowKeys.associateWith { FocusRequester() }
                }
                val heroFocusRequester = remember { FocusRequester() }
                val rowTargetProviders = remember { mutableMapOf<String, (Int?) -> FocusRequester?>() }

                LazyColumn(
                    state = homeListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LumenTokens.Space.sectionGapTv),
                ) {
                    if (spotlight != null) {
                        item(key = "top_shelf") {
                            TvTopShelf(
                                item = spotlight!!,
                                eyebrow = spotlightSource?.first?.title ?: stringResource(CoreUiR.string.home_featured),
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

                    items(displayRows, key = { it.rowType + "-" + it.title }) { row ->
                        val rowKey = "${row.rowType}-${row.title}"
                        val uniqueItems = remember(row.items) { row.items.distinctBy { it.id } }
                        if (uniqueItems.isEmpty()) return@items
                        val rowIndex = navigableRowKeys.indexOf(rowKey)
                        val rowEntryRequester = rowEntryFocusRequesters[rowKey]

                        Column(
                            modifier = Modifier,
                        ) {
                        HomeSectionHeader(
                            title = if (row.rowType == "quick_preview") {
                                stringResource(CoreUiR.string.home_quick_preview)
                            } else {
                                row.title
                            },
                            isTv = true
                        )

                        val listState = rememberLazyListState()
                        TvFocusScope(
                            memory = focusMemory,
                            scopeId = "home/row/${row.rowType}",
                            itemIds = uniqueItems.map { it.id },
                            listState = listState,
                        ) { restorer ->
                            rowTargetProviders[rowKey] = { targetIndex ->
                                val ids = uniqueItems.map { it.id }
                                val lastFocusedId = restorer.targetId(ids)
                                val targetId = if (targetIndex != null && targetIndex in ids.indices) {
                                    ids[targetIndex]
                                } else {
                                    lastFocusedId
                                }
                                targetId?.let { restorer.requester(it) }
                            }
                            LumenHorizontalRowFade(
                                modifier = Modifier.padding(bottom = LumenLegacySpace.xxxl),
                            ) {
                                LazyRow(
                                    state = restorer.listState,
                                    flingBehavior = rememberSnapFlingBehavior(restorer.listState),
                                    contentPadding = PaddingValues(horizontal = LumenLayout.iconXl),
                                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg),
                                    modifier = if (rowEntryRequester != null) Modifier.focusRequester(rowEntryRequester) else Modifier
                                ) {
                                    itemsIndexed(uniqueItems, key = { _, item -> "${item.type}-${item.id}" }) { index, item ->
                                    val cardModifier = restorer.itemModifier(item.id)
                                        .tvHomeVerticalRowNav(
                                            onMoveDown = {
                                                val nextKey = navigableRowKeys.getOrNull(rowIndex + 1)
                                                val target = nextKey?.let { rowTargetProviders[it]?.invoke(index) } ?: rowEntryFocusRequesters[nextKey]
                                                target?.let { runCatching { it.requestFocus() }.isSuccess } ?: false
                                            },
                                            onMoveUp = {
                                                if (rowIndex == 0) {
                                                    runCatching { heroFocusRequester.requestFocus() }.isSuccess
                                                } else {
                                                    val prevKey = navigableRowKeys.getOrNull(rowIndex - 1)
                                                    val target = prevKey?.let { rowTargetProviders[it]?.invoke(index) } ?: rowEntryFocusRequesters[prevKey]
                                                    target?.let { runCatching { it.requestFocus() }.isSuccess } ?: false
                                                }
                                            }
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
                                    } else if (row.rowType == "quick_preview") {
                                        val mediaItem = MediaItem(
                                            id = item.id,
                                            title = item.title,
                                            type = if (item.type == "series" || item.type == "show") MediaType.SHOW else MediaType.MOVIE,
                                            overview = sanitizeHomeBlurb(item.reason),
                                            posterUrl = item.posterUrl,
                                            backdropUrl = item.backdropUrl,
                                            externalIds = item.externalIds,
                                        )
                                        PreviewSamplerCard(
                                            imageUrl = item.backdropUrl ?: item.posterUrl,
                                            title = item.title,
                                            contentLabel = context.getString(CoreUiR.string.home_preview_play, item.title),
                                            onClick = { onMediaClick(mediaItem) },
                                            modifier = cardModifier.width(172.dp),
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
                                        val progress = if (item.reason.startsWith("Resume watching (")) {
                                            item.reason.substringAfter("(").substringBefore("%").toFloatOrNull()?.div(100f)
                                        } else null

                                        val cardWidth = if (row.rowType == "continue_watching") LumenLayout.tileWidthMd else LumenLayout.epgMinBlockWidthTv

                                        TvVODItemCard(
                                            item = mediaItem,
                                            reason = item.subtitle,
                                            progress = progress,
                                            landscape = row.rowType == "continue_watching",
                                            onClick = {
                                                val resumeAt = item.resumePositionMs
                                                if (resumeAt != null && resumeAt > 0L) {
                                                    onResumeClick(mediaItem, resumeAt)
                                                } else {
                                                    onMediaClick(mediaItem)
                                                }
                                            },
                                            onFocused = {
                                                spotlight = mediaItem
                                            },
                                            modifier = cardModifier.width(cardWidth),
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

            if (isLoading && displayRows.isNotEmpty()) {
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
    Column(
        modifier = modifier.width(if (landscape) LumenTokens.Tile.landscapeMobileW else LumenLayout.epgMinBlockWidthTv)
    ) {
        var isCardActive by remember { mutableStateOf(false) }

        GlassmorphicCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (landscape) LumenTokens.AspectRatio.landscape else LumenTokens.AspectRatio.poster)
                .onFocusChanged { isCardActive = it.isFocused },
            applyGlassFill = false,
            onFocused = onFocused,
        ) { isActive ->
            AsyncImage(
                model = if (landscape) item.backdropUrl ?: item.posterUrl else item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(LumenTokens.Color.textPrimary.copy(alpha = 0.05f)),
            )

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
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            color = if (isCardActive) t.colors.brandGlow else t.colors.foreground,
            style = lumenCaptionStyle(),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!reason.isNullOrBlank() && !reason.startsWith("poster:") && !reason.startsWith("Resume watching")) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = reason,
                color = t.colors.mutedForeground,
                style = LumenType.Meta.toTextStyle(lumenTextScale()),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
            modifier = Modifier
                .padding(LumenLegacySpace.md)
                .semantics(mergeDescendants = true) {
                    contentDescription = buildString {
                        append(channelName)
                        if (!category.isNullOrBlank()) append(", Category: $category")
                    }
                },
        ) {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
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
                    color = if (isActive) t.colors.brandGlow else Color.White,
                    style = lumenCaptionStyle(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!nowTitle.isNullOrBlank()) {
                    Text(
                        text = nowTitle,
                        color = if (isActive) Color.White.copy(alpha = 0.9f) else LumenTokens.Color.brand,
                        style = LumenType.Meta.toTextStyle(lumenTextScale()),
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    progress?.let {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { it.coerceIn(0f, 1f) },
                            color = t.colors.brandGlow,
                            trackColor = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = LumenLegacySpace.sm),
                        )
                    }
                    nextTitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = stringResource(CoreUiR.string.live_next_program, it),
                            color = if (isActive) Color.White.copy(alpha = 0.7f) else t.colors.mutedForeground,
                            style = LumenType.Meta.toTextStyle(lumenTextScale()),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else if (category != null) {
                    Text(
                        text = category,
                        color = if (isActive) Color.White.copy(alpha = 0.7f) else t.colors.mutedForeground,
                        style = LumenType.Meta.toTextStyle(lumenTextScale()),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
