package com.example.calmsource.ui

import com.example.calmsource.core.ui.theme.LumenTokens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.components.LumenCard
import com.example.calmsource.core.ui.components.LumenEmptyState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GuideScreen(
    uiState: LiveGuideUiState,
    nowNextMap: Map<String, EpgNowNext>,
    onChannelSelect: (Channel, Program?) -> Unit
) {
    val t = LocalLumenTokens.current
    val channels = uiState.filteredChannels

    if (channels.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LumenEmptyState(
                title = "Schedule unavailable",
                body = "No channels are loaded or match the active filters.",
                icon = androidx.compose.material.icons.Icons.Default.List
            )
        }
        return
    }

    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val startOfCurrentHour = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    
    val endOfTimeline = startOfCurrentHour + 24 * 60 * 60 * 1000L // 24 hours timeline
    val ticks = remember(startOfCurrentHour) {
        List(48) { index -> // 30-min ticks
            startOfCurrentHour + index * 30 * 60 * 1000L
        }
    }

    // Sync vertical scroll between left channel header list and right timeline grid
    val lazyColumnState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
    ) {
        // Sticky Channel Header Column
        Column(
            modifier = Modifier
                .width(LumenTokens.Layout.epgTimeColumnWidth)
                .fillMaxHeight()
        ) {
            // Spacer to align with time ruler
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LumenTokens.Layout.epgRowHeight)
                    .border(LumenTokens.Layout.hairline, t.colors.border)
                    .background(t.colors.muted)
            )

            LazyColumn(
                state = lazyColumnState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(channels, key = { "header-${it.id}" }) { channel ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(LumenTokens.Layout.epgBlockHeight)
                            .border(LumenTokens.Layout.hairline, t.colors.border)
                            .padding(LumenTokens.Space.sm2),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(LumenTokens.Layout.avatarMd)
                                .clip(CircleShape)
                                .background(t.colors.muted)
                        )
                    }
                }
            }
        }

        // Scrollable Timeline Column
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(horizontalScrollState)
        ) {
            // Time Ruler Header
            Row(
                modifier = Modifier
                    .height(LumenTokens.Layout.epgRowHeight)
                    .background(t.colors.muted)
                    .border(LumenTokens.Layout.hairline, t.colors.border),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ticks.forEach { tickTime ->
                    Box(
                        modifier = Modifier
                            .width(LumenTokens.Layout.epgMinBlockWidth)
                            .fillMaxHeight()
                            .padding(horizontal = LumenTokens.Space.sm2),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = timeFormatter.format(Date(tickTime)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = t.colors.foreground
                        )
                    }
                }
            }

            // Timeline Grid Rows
            LazyColumn(
                state = lazyColumnState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(channels, key = { "timeline-${it.id}" }) { channel ->
                    val nowNext = nowNextMap[channel.id]
                    Row(
                        modifier = Modifier
                            .height(LumenTokens.Layout.epgBlockHeight)
                            .border(LumenTokens.Layout.hairline, t.colors.border),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentProgram = nowNext?.currentProgram
                        val nextProgram = nowNext?.nextProgram

                        val programsList = remember(currentProgram, nextProgram) {
                            buildList {
                                if (currentProgram != null) add(currentProgram)
                                if (nextProgram != null) add(nextProgram)
                            }
                        }

                        if (programsList.isEmpty()) {
                            // Empty timeline block
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = LumenTokens.Space.lg)
                            ) {
                                Text("No program info", color = t.colors.mutedForeground, fontSize = 12.sp)
                            }
                        } else {
                            programsList.forEach { program ->
                                val pStart = maxOf(program.startTimeMs, startOfCurrentHour)
                                val pEnd = minOf(program.endTimeMs, endOfTimeline)
                                val durationMinutes = (pEnd - pStart) / 60000.0
                                val blockWidth = (durationMinutes * 4.0).dp
                                    .coerceAtLeast(LumenTokens.Layout.epgMinBlockWidth)

                                val isCurrent = System.currentTimeMillis() in program.startTimeMs..program.endTimeMs

                                val uiProgram = remember(program, channel) {
                                    Program(
                                        id = program.id,
                                        channelId = channel.id,
                                        title = program.title,
                                        description = program.description,
                                        startTimeMs = program.startTimeMs,
                                        endTimeMs = program.endTimeMs
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(blockWidth)
                                        .fillMaxHeight()
                                        .padding(LumenTokens.Space.xs)
                                ) {
                                    LumenCard(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable { onChannelSelect(channel, uiProgram) }
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    if (isCurrent) t.colors.brand.copy(alpha = 0.15f)
                                                    else Color.Transparent
                                                )
                                                .padding(LumenTokens.Space.sm2),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = program.title,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = t.colors.foreground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(LumenTokens.Space.xxs))
                                            Text(
                                                text = "${timeFormatter.format(Date(program.startTimeMs))} - ${timeFormatter.format(Date(program.endTimeMs))}",
                                                fontSize = 10.sp,
                                                color = t.colors.mutedForeground,
                                                maxLines = 1
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
