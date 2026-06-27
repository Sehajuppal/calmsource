package com.example.calmsource.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
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

import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun HomeScreen(
    onMediaClick: (MediaItem) -> Unit,
    onChannelClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val homeRows by viewModel.homeRows.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val context = LocalContext.current
    val prefetchCoordinator = remember(context) { PrefetchCoordinator(context) }

    val homeRowsKey = remember(homeRows) { homeRows.map { "${it.rowType}-${it.items.size}" } }
    LaunchedEffect(homeRowsKey) {
        // Run the URL flattening + prefetch dispatch on Dispatchers.Default
        // so the main thread is not blocked by per-row processing.
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(16.dp)
    ) {
        item(key = "header") {
            Text("CalmSource", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = AppColors.TextMain)
            Text(
                "Your media sanctuary",
                fontSize = 14.sp,
                color = AppColors.TextSub,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        // Discovery rows are populated from IPTVRepository.getLiveChannels and ExtensionRepository.extensions.
        if (isLoading) {
            item(key = "loading") {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Primary)
                }
            }
        } else if (homeRows.isEmpty()) {
            item(key = "empty") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(bottom = 28.dp)
                ) {
                    Text(
                        loadError
                            ?: "Discovery is temporarily unavailable. Check your connection or add a catalog in Settings.",
                        color = AppColors.TextSub
                    )
                    if (loadError != null) {
                        PremiumButton(
                            text = "Retry",
                            onClick = { viewModel.retry() }
                        )
                    }
                }
            }
        } else {
            items(homeRows, key = { it.rowType }) { row ->
                SectionTitle(row.title)

                // distinctBy allocates a new list on every recomposition; cache it
                // per row so scrolling or state changes don't reallocate.
                val uniqueItems = remember(row.items) { row.items.distinctBy { it.id } }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 28.dp)
                ) {
                    items(uniqueItems, key = { "${it.type}-${it.id}" }) { item ->
                        if (item.type == "channel") {
                            // Render Live TV Card
                            LiveChannelCard(
                                channelName = item.title,
                                logoUrl = item.posterUrl,
                                category = item.subtitle ?: item.reason,
                                onClick = { onChannelClick(item.id) }
                            )
                        } else {
                            // Render VOD Card
                            val mediaItem = MediaItem(
                                id = item.id,
                                title = item.title,
                                type = if (item.type == "series") MediaType.SHOW else MediaType.MOVIE,
                                overview = item.reason,
                                posterUrl = item.posterUrl,
                                externalIds = item.externalIds
                            )
                            VODItemCard(
                                item = mediaItem,
                                reason = item.reason,
                                onClick = { onMediaClick(mediaItem) }
                            )
                        }
                    }
                }
            }
        }
        
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppColors.TextMain,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
fun VODItemCard(item: MediaItem, reason: String? = null, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = "Poster for ${item.title}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x1AFFFFFF))
        )
        Text(
            item.title,
            color = AppColors.TextMain,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (reason != null && reason.isNotBlank() && !reason.startsWith("poster:")) {
            Text(
                reason,
                color = AppColors.Primary,
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
    GlassCard(modifier = Modifier.width(160.dp), onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = "Logo for $channelName",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0x1AFFFFFF))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                channelName,
                color = AppColors.TextMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (category != null) {
                Text(
                    category,
                    color = AppColors.Primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
