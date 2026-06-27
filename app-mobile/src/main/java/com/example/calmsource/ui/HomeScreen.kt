package com.example.calmsource.ui

import com.example.calmsource.core.ui.theme.LumenTokens

// Mock reference for tests: IPTVRepository.getLiveChannels and ExtensionRepository.extensions

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import com.example.calmsource.core.model.MediaItem
import com.example.calmsource.core.model.MediaType
import com.example.calmsource.core.playback.PrefetchCoordinator
import com.example.calmsource.core.ui.components.*
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onMediaClick: (MediaItem) -> Unit,
    onChannelClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val homeRows by viewModel.homeRows.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val context = LocalContext.current
    val prefetchCoordinator = remember(context) { PrefetchCoordinator(context) }

    val t = LocalLumenTokens.current

    // Navigation and tab states
    var selectedTab by remember { mutableStateOf("Home") }
    var selectedMood by remember { mutableStateOf<String?>(null) }

    val tabs = remember {
        listOf(
            TabItem("Home", "Home", Icons.Default.Home),
            TabItem("Films", "Films", Icons.Default.PlayArrow),
            TabItem("Series", "Series", Icons.Default.List),
            TabItem("Live", "Live", Icons.Default.Favorite)
        )
    }

    val moods = remember {
        listOf("Action", "Comedy", "Drama", "Sci-Fi", "Thriller", "Horror", "Documentary")
    }

    // Filter homeRows dynamically based on selected tab and selected mood
    val filteredRows = remember(homeRows, selectedTab, selectedMood) {
        homeRows.map { row ->
            val filteredItems = when (selectedTab) {
                "Films" -> row.items.filter { it.type == "movie" }
                "Series" -> row.items.filter { it.type == "series" || it.type == "show" }
                "Live" -> row.items.filter { it.type == "channel" }
                else -> row.items
            }.let { items ->
                val mood = selectedMood
                if (mood != null && row.rowType != "continue_watching") {
                    items.filter {
                        it.reason.contains(mood, ignoreCase = true) ||
                        it.subtitle?.contains(mood, ignoreCase = true) == true
                    }
                } else items
            }
            row.copy(items = filteredItems.toImmutableList())
        }.filter { it.items.isNotEmpty() }
    }

    // Extract VOD items from homeRows for the Hero Banner
    val featuredItems = remember(homeRows) {
        homeRows.asSequence()
            .filter { it.rowType != "continue_watching" && it.rowType != "live_tv" }
            .flatMap { it.items.asSequence() }
            .filter { it.type != "channel" && !it.posterUrl.isNullOrBlank() }
            .distinctBy { it.id }
            .take(5)
            .toList()
    }

    var heroIndex by remember { mutableStateOf(0) }
    var isPressed by remember { mutableStateOf(false) }

    // Detect system reduced motion scale
    val isReducedMotion = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f
            ) == 0f
        } catch (e: Exception) {
            false
        }
    }

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
            externalIds = item.externalIds
        )
    }

    var backdropLuminance by remember(featuredItem?.id) { mutableStateOf(0.5f) }

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
            // Shimmer skeletons for loading state
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "skeleton_top") {
                    LumenSkeleton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(LumenTokens.Layout.heroHeightMobile)
                    )
                    Spacer(modifier = Modifier.height(LumenTokens.Space.xxl))
                }
                item(key = "skeleton_rows") {
                    repeat(2) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = LumenTokens.Space.xxl, vertical = LumenTokens.Space.md)
                        ) {
                            LumenSkeleton(modifier = Modifier.width(LumenTokens.Layout.epgMinBlockWidthTv).height(LumenTokens.Space.xxl))
                            Spacer(modifier = Modifier.height(LumenTokens.Space.md))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.md)
                            ) {
                                repeat(5) {
                                    LumenSkeleton(
                                        modifier = Modifier
                                            .width(LumenTokens.Layout.epgMinBlockWidth)
                                            .height(LumenTokens.Layout.heroStripHeight)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (loadError != null && homeRows.isEmpty()) {
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
                    icon = androidx.compose.material.icons.Icons.Default.Home,
                    ctaText = "Go to Settings",
                    onCtaClick = onSettingsClick
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LumenTokens.Layout.bottomNavPadding)
            ) {
                // 1. Hero Banner
                if (featuredItem != null && featuredMediaItem != null) {
                    item(key = "hero") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(LumenTokens.Layout.heroHeightLg)
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
                            Hero(
                                backdropUrl = featuredItem.posterUrl,
                                title = featuredItem.title,
                                tagline = featuredItem.reason,
                                modifier = Modifier.fillMaxSize(),
                                actions = {
                                    AdaptiveButton(
                                        text = "Play",
                                        onClick = { onMediaClick(featuredMediaItem) },
                                        backdropLuminance = backdropLuminance
                                    )
                                    Spacer(modifier = Modifier.width(LumenTokens.Space.md))
                                    AdaptiveButton(
                                        text = "More Info",
                                        onClick = { onMediaClick(featuredMediaItem) },
                                        backdropLuminance = backdropLuminance
                                    )
                                }
                            )

                            // Silent sampler using AsyncImage
                            AsyncImage(
                                model = featuredItem.posterUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(1.dp),
                                onSuccess = { state ->
                                    val drawable = state.result.drawable
                                    if (drawable is android.graphics.drawable.BitmapDrawable) {
                                        val bitmap = drawable.bitmap
                                        Palette.from(bitmap).generate { palette ->
                                            val dominantColor = palette?.getDominantColor(0xFF000000.toInt()) ?: 0xFF000000.toInt()
                                            val r = android.graphics.Color.red(dominantColor) / 255f
                                            val g = android.graphics.Color.green(dominantColor) / 255f
                                            val b = android.graphics.Color.blue(dominantColor) / 255f
                                            Handler(Looper.getMainLooper()).post {
                                                backdropLuminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // 2. GlassTabBar
                item(key = "tab_bar") {
                    GlassTabBar(
                        items = tabs,
                        selected = selectedTab,
                        onSelect = {
                            selectedTab = it
                            selectedMood = null // Reset mood filter on tab switch
                        },
                        modifier = Modifier.padding(vertical = LumenTokens.Space.md)
                    )
                }

                // 3. Moods chip row above content rows (only if Home or movie/series tabs active)
                if (selectedTab != "Live") {
                    item(key = "moods") {
                        ChipRow(
                            items = moods,
                            selected = selectedMood,
                            onSelect = { selectedMood = if (selectedMood == it) null else it },
                            modifier = Modifier.padding(bottom = LumenTokens.Space.lg)
                        )
                    }
                }

                // Render dynamic rows
                if (filteredRows.isEmpty()) {
                    item(key = "empty_content") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(LumenTokens.Layout.iconXl),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No items match selected filters.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = t.colors.mutedForeground
                            )
                        }
                    }
                } else {
                    items(filteredRows, key = { it.rowType }) { row ->
                        val uniqueItems = remember(row.items) { row.items.distinctBy { it.id } }
                        if (uniqueItems.isEmpty()) return@items

                        // Group rows by design requirements
                        when {
                            row.rowType == "continue_watching" -> {
                                SectionHeader(row.title)
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = LumenTokens.Space.xxl),
                                    horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.md),
                                    modifier = Modifier.padding(bottom = LumenTokens.Space.xxl)
                                ) {
                                    items(uniqueItems, key = { it.id }) { item ->
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

                                        PosterCard(
                                            imageUrl = item.posterUrl,
                                            orientation = PosterOrientation.Landscape,
                                            progress = progress,
                                            onClick = { onMediaClick(mediaItem) },
                                            modifier = Modifier.width(LumenTokens.Layout.tileWidthMd)
                                        )
                                    }
                                }
                            }
                            row.rowType == "top_rated" -> {
                                SectionHeader("Top 10")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = LumenTokens.Space.xxl),
                                    horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.lg),
                                    modifier = Modifier.padding(bottom = LumenTokens.Space.xxl)
                                ) {
                                    itemsIndexed(uniqueItems.take(10), key = { _, item -> item.id }) { index, item ->
                                        val mediaItem = MediaItem(
                                            id = item.id,
                                            title = item.title,
                                            type = if (item.type == "series" || item.type == "show") MediaType.SHOW else MediaType.MOVIE,
                                            overview = item.reason,
                                            posterUrl = item.posterUrl,
                                            externalIds = item.externalIds
                                        )

                                        Box(modifier = Modifier.width(LumenTokens.Layout.posterTileWidth).padding(start = LumenTokens.Space.xxl)) {
                                            Text(
                                                text = (index + 1).toString(),
                                                style = androidx.compose.ui.text.TextStyle(
                                                    fontSize = 110.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = t.colors.border.copy(alpha = 0.35f)
                                                ),
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .offset(x = (-LumenTokens.Space.xxl), y = LumenTokens.Radius.md)
                                            )
                                            PosterCard(
                                                imageUrl = item.posterUrl,
                                                onClick = { onMediaClick(mediaItem) },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                            row.rowType == "live_tv" -> {
                                SectionHeader("Live Channels")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = LumenTokens.Space.xxl),
                                    horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.md),
                                    modifier = Modifier.padding(bottom = LumenTokens.Space.xxl)
                                ) {
                                    items(uniqueItems, key = { item -> item.id }) { item ->
                                        LumenCard(
                                            modifier = Modifier
                                                .size(LumenTokens.Layout.epgMinBlockWidth)
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
                                                        .size(LumenTokens.Layout.playerControlSize)
                                                        .clip(LumenTokens.Shape.sm)
                                                        .background(LumenTokens.Color.textPrimary.copy(alpha = 0.05f))
                                                )
                                                Spacer(modifier = Modifier.height(LumenTokens.Space.sm2))
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
                            row.rowType == "leaving_soon" || row.title.contains("Leaving Soon", ignoreCase = true) -> {
                                SectionHeader(row.title)
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = LumenTokens.Space.xxl),
                                    horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.md),
                                    modifier = Modifier.padding(bottom = LumenTokens.Space.xxl)
                                ) {
                                    items(uniqueItems, key = { item -> item.id }) { item ->
                                        val mediaItem = MediaItem(
                                            id = item.id,
                                            title = item.title,
                                            type = if (item.type == "series" || item.type == "show") MediaType.SHOW else MediaType.MOVIE,
                                            overview = item.reason,
                                            posterUrl = item.posterUrl,
                                            externalIds = item.externalIds
                                        )
                                        Column(modifier = Modifier.width(LumenTokens.Layout.epgMinBlockWidth)) {
                                            PosterCard(
                                                imageUrl = item.posterUrl,
                                                onClick = { onMediaClick(mediaItem) }
                                            )
                                            Spacer(modifier = Modifier.height(LumenTokens.Space.sm))
                                            Text(
                                                text = "Leaves Mar 12",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = t.colors.mutedForeground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                // Default RowSection item listing
                                RowSection(
                                    title = row.title,
                                    modifier = Modifier.padding(bottom = LumenTokens.Space.md)
                                ) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = LumenTokens.Space.xxl),
                                        horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.md),
                                        modifier = Modifier.padding(bottom = LumenTokens.Space.lg)
                                    ) {
                                        items(uniqueItems, key = { item -> item.id }) { item ->
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
                                                onClick = { onMediaClick(mediaItem) },
                                                modifier = Modifier.width(LumenTokens.Layout.epgMinBlockWidth)
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
private fun SectionHeader(title: String) {
    val t = LocalLumenTokens.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = t.colors.foreground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = LumenTokens.Space.xxl, vertical = LumenTokens.Space.md)
    )
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
            .width(LumenTokens.Layout.epgMinBlockWidth)
            .clickable(onClick = onClick)
    ) {
        PosterCard(
            imageUrl = item.posterUrl,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = item.title,
            color = t.colors.foreground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = LumenTokens.Space.sm2)
        )
        if (reason != null && reason.isNotBlank() && !reason.startsWith("poster:")) {
            Text(
                text = reason,
                color = t.colors.brand,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
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
            .width(LumenTokens.Layout.posterTileWidth)
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            AsyncImage(
                model = logoUrl,
                contentDescription = channelName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(LumenTokens.Layout.iconXl)
                    .clip(LumenTokens.Shape.md)
                    .background(LumenTokens.Color.textPrimary.copy(alpha = 0.05f))
            )
            Spacer(modifier = Modifier.height(LumenTokens.Space.sm2))
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
