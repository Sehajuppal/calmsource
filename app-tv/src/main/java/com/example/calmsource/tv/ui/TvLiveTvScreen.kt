package com.example.calmsource.tv.ui

// Filter options: "Popular", "Clear filters", "sectionById", "onOpenSetup"

import com.example.calmsource.core.ui.theme.*

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.calmsource.core.ui.R as CoreUiR
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
                text = stringResource(CoreUiR.string.live_categories),
                fontSize = LumenType.size18,
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
                                        isCategoryFocused && isSelected -> t.colors.brandGlow
                                        isCategoryFocused -> t.colors.muted
                                        isSelected -> t.colors.brand
                                        else -> Color.Transparent
                                    }
                                )
                                .padding(horizontal = LumenLegacySpace.md, vertical = LumenLegacySpace.sm2)
                        ) {
                            Text(
                                text = category,
                                color = when {
                                    isCategoryFocused && isSelected -> t.colors.background
                                    isCategoryFocused -> t.colors.foreground
                                    isSelected -> t.colors.brandForeground
                                    else -> t.colors.foreground
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = LumenType.size14
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
                        title = stringResource(CoreUiR.string.live_no_channels_category_tv),
                        body = stringResource(CoreUiR.string.live_connect_playlists),
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
                                        text = if (currentProgram != null) {
                                            stringResource(CoreUiR.string.live_airing_now)
                                        } else {
                                            stringResource(CoreUiR.string.live_no_epg)
                                        },
                                        fontSize = LumenType.size12,
                                        letterSpacing = LumenType.size1_4,
                                        fontWeight = FontWeight.Bold,
                                        color = t.colors.mutedForeground
                                    )
                                    Spacer(modifier = Modifier.height(LumenLegacySpace.xxs))
                                    Text(
                                        text = currentProgram?.title ?: stringResource(CoreUiR.string.live_no_program_data),
                                        fontSize = LumenType.size11_5,
                                        color = t.colors.foreground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(LumenLegacySpace.xs))
                                    Text(
                                        text = channel.name,
                                        fontSize = LumenType.size12,
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
