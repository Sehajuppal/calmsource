package com.example.calmsource.ui

import com.example.calmsource.core.ui.theme.*
import com.example.calmsource.core.ui.theme.LocalReducedMotion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Mock reference for tests: IPTVRepository.getLiveChannels and ExtensionRepository.extensions


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import coil.compose.AsyncImage
import com.example.calmsource.core.model.MediaItem
import com.example.calmsource.core.model.MediaType
import com.example.calmsource.core.playback.PrefetchCoordinator
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.ui.graphics.Brush
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.example.calmsource.core.ui.R as CoreUiR
import com.example.calmsource.core.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    onMediaClick: (MediaItem) -> Unit,
    onChannelClick: (String) -> Unit,
    onPlayClick: (MediaItem) -> Unit = {},
    onResumeClick: (MediaItem, Long) -> Unit = { item, _ -> onMediaClick(item) },
    onSettingsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedPosterKey: String? = null,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val homeRows by viewModel.homeRows.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val context = LocalContext.current
    val prefetchCoordinator = remember(context) { PrefetchCoordinator(context) }

    val t = LocalLumenTokens.current

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

    val featuredItems = remember(homeRows) {
        homeRows.asSequence()
            .filter { it.rowType != "continue_watching" && it.rowType != "live_tv" }
            .flatMap { it.items.asSequence() }
            .filter { it.type != "channel" && (!it.backdropUrl.isNullOrBlank() || !it.posterUrl.isNullOrBlank()) }
            .sortedByDescending { !it.backdropUrl.isNullOrBlank() }
            .distinctBy { it.id }
            .take(5)
            .toList()
    }

    var heroIndex by remember { mutableStateOf(0) }
    var isPressed by remember { mutableStateOf(false) }

    // Respect system reduced motion
    val isReducedMotion = rememberReducedMotion()
    val sharedBoundsTransform = rememberLumenSharedBoundsTransform()

    // Auto-rotate Hero Banner every 7000ms if not pressed and motion is not reduced
    if (featuredItems.isNotEmpty()) {
        val currentIsPressed by rememberUpdatedState(isPressed)
        LaunchedEffect(Unit) {
            if (!isReducedMotion) {
                while (isActive) {
                    delay(7000L)
                    if (!currentIsPressed) {
                        heroIndex = (heroIndex + 1) % featuredItems.size
                    }
                }
            }
        }
    }

    val featuredItem = featuredItems.getOrNull(heroIndex % maxOf(featuredItems.size, 1))
    val featuredMediaItem = featuredItem?.let { item ->
        MediaItem(
            id = item.id,
            title = item.title,
            type = if (item.type == "series" || item.type == "show") MediaType.SHOW else MediaType.MOVIE,
            overview = item.reason,
            posterUrl = item.posterUrl,
            backdropUrl = item.backdropUrl,
            externalIds = item.externalIds
        )
    }

    val heroArtUrl = featuredItem?.backdropUrl ?: featuredItem?.posterUrl
    var backdropLuminance by remember(heroArtUrl) { mutableStateOf(0.5f) }

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
        if (isLoading && homeRows.isEmpty()) {
            HomeFeedSkeleton(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LumenLayout.bottomNavPadding),
            )
        } else if (loadError != null && homeRows.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LumenErrorState(
                    title = stringResource(CoreUiR.string.error_load_feed),
                    body = loadError ?: stringResource(CoreUiR.string.error_load_feed_body),
                    onRetry = { viewModel.retry() }
                )
            }
        } else if (homeRows.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LumenEmptyState(
                    title = stringResource(CoreUiR.string.empty_nothing_to_browse),
                    body = stringResource(CoreUiR.string.empty_connect_provider),
                    icon = androidx.compose.material.icons.Icons.Default.Home,
                    ctaText = stringResource(CoreUiR.string.cta_go_to_settings),
                    onCtaClick = onSettingsClick
                )
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isLoading && homeRows.isNotEmpty(),
                onRefresh = { viewModel.retry() },
                modifier = Modifier.fillMaxSize(),
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LumenLayout.bottomNavPadding)
            ) {
                // 1. Hero Banner
                if (featuredItem != null && featuredMediaItem != null) {
                    item(key = "hero") {
                        val heroSharedModifier =
                            if (
                                sharedTransitionScope != null &&
                                animatedVisibilityScope != null &&
                                sharedPosterKey != null &&
                                featuredItem?.id == sharedPosterKey
                            ) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedBounds(
                                        rememberSharedContentState(key = "poster-$sharedPosterKey"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        resizeMode = scaleToBounds(),
                                        boundsTransform = sharedBoundsTransform,
                                    )
                                }
                            } else {
                                Modifier
                            }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(LumenLayout.heroHeightLg)
                                .then(heroSharedModifier)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            isPressed = true
                                            tryAwaitRelease()
                                            isPressed = false
                                        }
                                    )
                                }
                        ) {
                            androidx.compose.animation.Crossfade(
                                targetState = featuredItem,
                                animationSpec = androidx.compose.animation.core.tween(
                                    durationMillis = if (isReducedMotion) 0 else LumenTokens.Duration.cinematic,
                                    easing = LumenTokens.Easing.AppleOut,
                                ),
                                label = "hero-crossfade"
                            ) { targetFeaturedItem ->
                                val targetFeaturedMediaItem = targetFeaturedItem?.let { item ->
                                    MediaItem(
                                        id = item.id,
                                        title = item.title,
                                        type = if (item.type == "series" || item.type == "show") MediaType.SHOW else MediaType.MOVIE,
                                        overview = item.reason,
                                        posterUrl = item.posterUrl,
                                        backdropUrl = item.backdropUrl,
                                        externalIds = item.externalIds
                                    )
                                }
                                if (targetFeaturedItem != null && targetFeaturedMediaItem != null) {
                                    Hero(
                                        backdropUrl = targetFeaturedItem.backdropUrl ?: targetFeaturedItem.posterUrl,
                                        posterUrl = targetFeaturedItem.posterUrl,
                                        title = targetFeaturedItem.title,
                                        tagline = targetFeaturedItem.reason,
                                        modifier = Modifier.fillMaxSize(),
                                        onBackdropLuminance = { backdropLuminance = it },
                                        actions = {
                                            AdaptiveButton(
                                                text = stringResource(CoreUiR.string.cta_play),
                                                onClick = { onPlayClick(targetFeaturedMediaItem) },
                                                backdropLuminance = backdropLuminance
                                            )
                                            Spacer(modifier = Modifier.width(LumenTokens.Space.s5))
                                            GhostButton(
                                                text = stringResource(CoreUiR.string.cta_more_info),
                                                onClick = { onMediaClick(targetFeaturedMediaItem) }
                                            )
                                        }
                                    )
                                }
                            }
                            IconButton(
                                onClick = onProfileClick,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .statusBarsPadding()
                                    .padding(top = LumenTokens.Space.s5, end = LumenTokens.Space.s5)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = stringResource(CoreUiR.string.settings_switch_profile),
                                    tint = t.colors.foreground,
                                    modifier = Modifier.size(LumenLayout.iconXl)
                                )
                            }

                        }
                    }
                }

                itemsIndexed(displayRows, key = { _, row -> row.rowType }) { rowIndex, row ->
                        val uniqueItems = remember(row.items) { row.items.distinctBy { it.id } }
                        if (uniqueItems.isEmpty()) return@itemsIndexed
                        val isFirstSection = rowIndex == 0

                        Column {
                            when {
                                row.rowType == "continue_watching" -> {
                                    HomeSectionHeader(row.title, isTv = false, isFirstSection = isFirstSection)
                                    HomeScrollableRow(spacing = LumenTokens.Space.s5) {
                                        uniqueItems.forEach { item ->
                                            val progress = if (item.reason.startsWith("Resume watching (")) {
                                                item.reason.substringAfter("(").substringBefore("%").toFloatOrNull()?.div(100f)
                                            } else null

                                            val mediaItem = MediaItem(
                                                id = item.id,
                                                title = item.title,
                                                type = if (item.type == "series" || item.type == "show") MediaType.SHOW else MediaType.MOVIE,
                                                overview = item.reason,
                                                posterUrl = item.posterUrl,
                                                externalIds = item.externalIds
                                            )

                                            val resumeAt = item.resumePositionMs
                                            PosterCard(
                                                imageUrl = item.posterUrl,
                                                orientation = PosterOrientation.Landscape,
                                                progress = progress,
                                                contentLabel = item.title,
                                                onClick = {
                                                    if (resumeAt != null && resumeAt > 0L) {
                                                        onResumeClick(mediaItem, resumeAt)
                                                    } else {
                                                        onMediaClick(mediaItem)
                                                    }
                                                },
                                                modifier = Modifier.width(LumenLayout.tileWidthMd)
                                            )
                                        }
                                    }
                                }
                                row.rowType == "top_rated" -> {
                                    HomeSectionHeader(
                                        stringResource(CoreUiR.string.home_top_10),
                                        isTv = false,
                                        isFirstSection = isFirstSection,
                                    )
                                    HomeScrollableRow(spacing = LumenTokens.Space.md) {
                                        uniqueItems.take(10).forEachIndexed { index, item ->
                                            val mediaItem = MediaItem(
                                                id = item.id,
                                                title = item.title,
                                                type = if (item.type == "series" || item.type == "show") MediaType.SHOW else MediaType.MOVIE,
                                                overview = item.reason,
                                                posterUrl = item.posterUrl,
                                                externalIds = item.externalIds
                                            )

                                            Box(modifier = Modifier.width(LumenLayout.posterTileWidth).padding(start = LumenTokens.Space.sm)) {
                                                PosterCard(
                                                    imageUrl = item.posterUrl,
                                                    contentLabel = item.title,
                                                    onClick = { onMediaClick(mediaItem) },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Text(
                                                    text = (index + 1).toString(),
                                                    style = LumenType.rankNumeralStyle().copy(
                                                        fontWeight = FontWeight.Black,
                                                        color = t.colors.foreground,
                                                    ),
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .offset(x = (-LumenTokens.Space.s3), y = LumenTokens.Space.xs)
                                                )
                                            }
                                        }
                                    }
                                }
                                row.rowType == "live_tv" -> {
                                    HomeSectionHeader(
                                        stringResource(CoreUiR.string.home_live_channels),
                                        isTv = false,
                                        isFirstSection = isFirstSection,
                                    )
                                    HomeScrollableRow(spacing = LumenTokens.Space.s5) {
                                        uniqueItems.forEach { item ->
                                            LumenCard(
                                                modifier = Modifier
                                                    .size(LumenLayout.epgMinBlockWidth)
                                                    .clickable { onChannelClick(item.id) }
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    AsyncImage(
                                                        model = item.posterUrl,
                                                        contentDescription = item.title,
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier
                                                            .size(LumenLayout.playerControlSize)
                                                            .clip(LumenTokens.Shape.sm)
                                                            .background(LumenTokens.Color.textPrimary.copy(alpha = 0.05f))
                                                    )
                                                    Spacer(modifier = Modifier.height(LumenTokens.Space.sm))
                                                    Text(
                                                        text = item.title,
                                                        color = t.colors.foreground,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                row.rowType == "quick_preview" -> {
                                    HomeSectionHeader(
                                        stringResource(CoreUiR.string.home_quick_preview),
                                        isTv = false,
                                        isFirstSection = isFirstSection,
                                    )
                                    HomeScrollableRow(spacing = LumenTokens.Space.s5) {
                                        uniqueItems.forEach { item ->
                                            val mediaItem = MediaItem(
                                                id = item.id,
                                                title = item.title,
                                                type = if (item.type == "series" || item.type == "show") MediaType.SHOW else MediaType.MOVIE,
                                                overview = item.reason,
                                                posterUrl = item.posterUrl,
                                                backdropUrl = item.backdropUrl,
                                                externalIds = item.externalIds,
                                            )
                                            PreviewSamplerCard(
                                                imageUrl = item.backdropUrl ?: item.posterUrl,
                                                title = item.title,
                                                contentLabel = context.getString(CoreUiR.string.home_preview_play, item.title),
                                                onClick = { onMediaClick(mediaItem) },
                                                modifier = Modifier.width(120.dp),
                                            )
                                        }
                                    }
                                }
                                row.rowType == "leaving_soon" || row.title.contains("Leaving Soon", ignoreCase = true) -> {
                                    HomeSectionHeader(
                                        row.title,
                                        isTv = false,
                                        isFirstSection = isFirstSection,
                                    )
                                    HomeScrollableRow(spacing = LumenTokens.Space.s5) {
                                        uniqueItems.forEach { item ->
                                            val mediaItem = MediaItem(
                                                id = item.id,
                                                title = item.title,
                                                type = if (item.type == "series" || item.type == "show") MediaType.SHOW else MediaType.MOVIE,
                                                overview = item.reason,
                                                posterUrl = item.posterUrl,
                                                externalIds = item.externalIds
                                            )
                                            Column(
                                                modifier = Modifier
                                                    .width(LumenLayout.epgMinBlockWidth)
                                                    .clip(LumenTokens.Shape.md)
                                                    .clickable { onMediaClick(mediaItem) }
                                                    .padding(4.dp)
                                            ) {
                                                PosterCard(
                                                    imageUrl = item.posterUrl,
                                                    contentLabel = item.title,
                                                    enabled = false
                                                )
                                                Spacer(modifier = Modifier.height(LumenTokens.Space.s3))
                                                val leavingLabel = item.subtitle?.takeIf { it.isNotBlank() }
                                                    ?: item.reason.takeIf { it.isNotBlank() && !it.startsWith("poster:") }
                                                if (!leavingLabel.isNullOrBlank()) {
                                                    Text(
                                                        text = leavingLabel,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = t.colors.mutedForeground,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    HomeSectionHeader(
                                        row.title,
                                        isTv = false,
                                        isFirstSection = isFirstSection,
                                    )
                                    HomeScrollableRow(spacing = LumenTokens.Space.s5) {
                                        uniqueItems.forEach { item ->
                                            val mediaItem = MediaItem(
                                                id = item.id,
                                                title = item.title,
                                                type = if (item.type == "series" || item.type == "show") MediaType.SHOW else MediaType.MOVIE,
                                                overview = item.reason,
                                                posterUrl = item.posterUrl,
                                                externalIds = item.externalIds
                                            )
                                            PosterCard(
                                                imageUrl = item.posterUrl,
                                                contentLabel = item.title,
                                                onClick = { onMediaClick(mediaItem) },
                                                modifier = Modifier.width(LumenLayout.epgMinBlockWidth)
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
    }
}

@Composable
fun VODItemCard(
    item: MediaItem,
    reason: String? = null,
    onClick: () -> Unit
) {
    val t = LocalLumenTokens.current
    Column(
        modifier = Modifier
            .width(LumenLayout.epgMinBlockWidth)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(item.title)
                    if (reason != null && reason.isNotBlank() && !reason.startsWith("poster:")) {
                        append(", ")
                        append(reason)
                    }
                }
            }
    ) {
        PosterCard(
            imageUrl = item.posterUrl,
            contentLabel = item.title,
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = item.title,
            color = t.colors.foreground,
            style = LumenType.Body.toTextStyle().copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = LumenTokens.Space.sm)
        )
        if (reason != null && reason.isNotBlank() && !reason.startsWith("poster:")) {
            Text(
                text = reason,
                color = t.colors.brand,
                style = LumenType.Eyebrow.toTextStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LiveChannelCard(
    channelName: String,
    logoUrl: String?,
    category: String?,
    onClick: () -> Unit
) {
    val t = LocalLumenTokens.current
    LumenCard(
        modifier = Modifier
            .width(LumenLayout.posterTileWidth)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(channelName)
                    if (!category.isNullOrBlank()) append(", Category: $category")
                }
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            AsyncImage(
                model = logoUrl,
                contentDescription = null, // Set to null
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(LumenLayout.iconXl)
                    .clip(LumenTokens.Shape.md)
                    .background(LumenTokens.Color.textPrimary.copy(alpha = 0.05f))
            )
            Spacer(modifier = Modifier.height(LumenTokens.Space.sm))
            Text(
                text = channelName,
                color = t.colors.foreground,
                style = LumenType.Body.toTextStyle().copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (category != null) {
                Text(
                    text = category,
                    color = t.colors.brand,
                    style = LumenType.Meta.toTextStyle(),
                )
            }
        }
    }
}

@Composable
private fun HomeScrollableRow(
    spacing: androidx.compose.ui.unit.Dp,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = LumenTokens.Space.lg)
            .padding(bottom = LumenTokens.Space.lg),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}
