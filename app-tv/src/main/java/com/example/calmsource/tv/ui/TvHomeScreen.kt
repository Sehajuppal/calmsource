package com.example.calmsource.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.calmsource.core.model.MediaItem
import com.example.calmsource.core.model.MediaType
import com.example.calmsource.core.playback.PrefetchCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

import androidx.hilt.navigation.compose.hiltViewModel

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

    val firstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(homeRows.isNotEmpty(), isLoading) {
        if (!isLoading && homeRows.isNotEmpty()) {
            try {
                firstItemFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus request failure
            }
        }
    }

    val homeRowsKey = remember(homeRows) { homeRows.map { "${it.rowType}-${it.items.size}" } }
    LaunchedEffect(homeRowsKey) {
        // Run the URL flattening + prefetch dispatch on Dispatchers.Default
        // so the main thread is not blocked by per-row processing on a Fire
        // TV Stick with many home rows.
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
            .background(TvColors.Background)
            .padding(24.dp)
    ) {
        Text("CalmSource", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = TvColors.TextMain)
        Text(
            "Your media sanctuary",
            fontSize = 16.sp,
            color = TvColors.TextSub,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Discovery rows are populated from IPTVRepository.getLiveChannels and ExtensionRepository.extensions.
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (homeRows.isEmpty() && isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TvColors.BorderFocused)
                }
            } else if (homeRows.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        loadError
                            ?: "Discovery is temporarily unavailable. Check your connection or add a catalog in Settings.",
                        color = TvColors.TextSub
                    )
                    if (loadError != null) {
                        TvFocusCard(
                            modifier = Modifier.width(200.dp),
                            onClick = { viewModel.retry() }
                        ) {
                            Text(
                                "Retry",
                                color = TvColors.TextMain,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(homeRows, key = { "${it.rowType}-${it.title}" }) { row ->
                        val uniqueItems = remember(row.items) { row.items.distinctBy { it.id } }
                        if (uniqueItems.isEmpty()) return@items

                        SectionTitle(row.title)

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(bottom = 32.dp)
                        ) {
                            items(uniqueItems, key = { "${it.type}-${it.id}" }) { item ->
                                val isFirst = (row == homeRows.firstOrNull() && item == uniqueItems.firstOrNull())
                                val cardModifier = if (isFirst) Modifier.focusRequester(firstItemFocusRequester) else Modifier

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
                                        type = if (item.type == "series") MediaType.SHOW else MediaType.MOVIE,
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
                    color = TvColors.BorderFocused,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        color = TvColors.TextMain,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

@Composable
fun TvVODItemCard(
    item: MediaItem,
    reason: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvFocusCard(
        modifier = modifier.width(140.dp),
        onClick = onClick
    ) {
        Column {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = "Poster for ${item.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x1AFFFFFF))
            )
            Text(
                item.title,
                color = TvColors.TextMain,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp)
            )
            if (reason != null && reason.isNotBlank() && !reason.startsWith("poster:")) {
                Text(
                    reason,
                    color = TvColors.BorderFocused,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
    TvFocusCard(
        modifier = modifier.width(200.dp),
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            AsyncImage(
                model = logoUrl,
                contentDescription = "Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0x1AFFFFFF))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    channelName,
                    color = TvColors.TextMain,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (category != null) {
                    Text(
                        category,
                        color = TvColors.BorderFocused,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
