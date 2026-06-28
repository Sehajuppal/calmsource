package com.example.calmsource.tv.ui

// Filter options: "Popular", "Clear filters", "sectionById", "onOpenSetup"

import com.example.calmsource.core.ui.theme.LumenLegacySpace
import com.example.calmsource.core.ui.theme.LumenLayout
import com.example.calmsource.core.ui.theme.LumenTokens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.calmsource.core.model.Channel
import com.example.calmsource.core.model.Program
import com.example.calmsource.feature.iptv.EpgNowNext
import com.example.calmsource.feature.iptv.LiveGuideUiState
import com.example.calmsource.feature.iptv.LiveGuideViewModel
import com.example.calmsource.feature.iptv.IptvLiveGuideFilters
import com.example.calmsource.core.ui.components.TvFocusable
import com.example.calmsource.core.ui.components.LumenEmptyState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.components.LumenCard

@Composable
fun TvLiveTvScreen(
    uiState: LiveGuideUiState,
    nowNextMap: Map<String, EpgNowNext>,
    viewModel: LiveGuideViewModel,
    onChannelSelect: (Channel, Program?) -> Unit
) {
    val t = LocalLumenTokens.current
    val categories = uiState.categories
    val activeCategory = uiState.selectedCategory
    val filteredChannels = uiState.filteredChannels

    // D-pad focus memory
    var lastFocusedCategory by rememberSaveable { mutableStateOf("All") }
    var lastFocusedChannelId by rememberSaveable { mutableStateOf("") }

    val categoryFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val channelFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    // Restore category focus on mount
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        categoryFocusRequesters[lastFocusedCategory]?.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
    ) {
        // Left Pane: Category nav list (Two-pane)
        Column(
            modifier = Modifier
                .width(LumenLayout.channelPanelWidth)
                .fillMaxHeight()
                .border(LumenLayout.hairline, t.colors.border)
                .padding(vertical = LumenLegacySpace.lg, horizontal = LumenLegacySpace.sm2)
        ) {
            Text(
                text = "Categories",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = t.colors.foreground,
                modifier = Modifier.padding(bottom = LumenLegacySpace.lg, start = LumenLegacySpace.sm2)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.sm2),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(categories) { _, category ->
                    val requester = categoryFocusRequesters.getOrPut(category) { FocusRequester() }
                    val isSelected = category == activeCategory
                    var isCategoryFocused by remember { mutableStateOf(false) }

                    TvFocusable(
                        onClick = { viewModel.setSelectedCategory(category) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(requester)
                            .onFocusChanged {
                                isCategoryFocused = it.isFocused
                                if (it.isFocused) {
                                    viewModel.setSelectedCategory(category)
                                    lastFocusedCategory = category
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(LumenTokens.Shape.sm)
                                .background(
                                    when {
                                        isSelected -> t.colors.brand
                                        isCategoryFocused -> t.colors.muted
                                        else -> Color.Transparent
                                    }
                                )
                                .padding(horizontal = LumenLegacySpace.md, vertical = LumenLegacySpace.sm2)
                        ) {
                            Text(
                                text = category,
                                color = if (isSelected) t.colors.brandForeground else t.colors.foreground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Right Pane: Channel grid
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(LumenLegacySpace.lg)
        ) {
            if (filteredChannels.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LumenEmptyState(
                        title = "No channels in this category",
                        body = "Connect provider playlists to access content.",
                        icon = androidx.compose.material.icons.Icons.Default.PlayArrow
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg),
                    verticalArrangement = Arrangement.spacedBy(LumenLegacySpace.lg),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredChannels, key = { it.id }) { channel ->
                        val requester = channelFocusRequesters.getOrPut(channel.id) { FocusRequester() }
                        val nowNext = nowNextMap[channel.id]
                        val currentProgram = nowNext?.currentProgram?.let {
                            Program(
                                id = it.id,
                                channelId = channel.id,
                                title = it.title,
                                description = it.description,
                                startTimeMs = it.startTimeMs,
                                endTimeMs = it.endTimeMs
                            )
                        }
                        var isChannelFocused by remember { mutableStateOf(false) }

                        TvFocusable(
                            onClick = { onChannelSelect(channel, currentProgram) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(requester)
                                .onFocusChanged {
                                    isChannelFocused = it.isFocused
                                    if (it.isFocused) {
                                        lastFocusedChannelId = channel.id
                                    }
                                }
                        ) {
                            LumenCard(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(LumenLegacySpace.xs)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(LumenLayout.bottomNavPadding)
                                            .clip(LumenTokens.Shape.sm)
                                            .background(t.colors.muted),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = channel.logoUrl,
                                            contentDescription = channel.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.size(LumenLayout.playerControlSize)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(LumenLegacySpace.sm2))
                                    Text(
                                        text = if (currentProgram != null) "AIRING NOW" else "NO EPG INFO",
                                        fontSize = 12.sp,
                                        letterSpacing = 1.4.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = t.colors.mutedForeground
                                    )
                                    Spacer(modifier = Modifier.height(LumenLegacySpace.xxs))
                                    Text(
                                        text = currentProgram?.title ?: "No program data",
                                        fontSize = 11.5.sp,
                                        color = t.colors.foreground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(LumenLegacySpace.xs))
                                    Text(
                                        text = channel.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = t.colors.foreground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Restore channel focus if we saved it
                        LaunchedEffect(lastFocusedChannelId) {
                            if (lastFocusedChannelId == channel.id) {
                                requester.requestFocus()
                            }
                        }
                    }
                }
            }
        }
    }
}
