package com.example.calmsource.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import com.example.calmsource.core.ui.components.TvFocusable
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.components.LumenCard
import com.example.calmsource.core.ui.components.AdaptiveButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TvGuideScreen(
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
            Text("No channels available for guide.", color = t.colors.mutedForeground, fontSize = 16.sp)
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

    // Capture the currently focused program for the details synopsis panel
    var focusedProgram by remember { mutableStateOf<Program?>(null) }
    var focusedChannel by remember { mutableStateOf<Channel?>(null) }

    val scope = rememberCoroutineScope()
    val horizontalScrollState = rememberScrollState()

    // Request initial focus on the first program card
    val initialFocusRequester = remember { FocusRequester() }
    var isFirstFocusTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(channels) {
        if (channels.isNotEmpty() && !isFirstFocusTriggered) {
            kotlinx.coroutines.delay(200)
            try {
                initialFocusRequester.requestFocus()
                isFirstFocusTriggered = true
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background)
    ) {
        // Top Bar: Pinned "Jump to now" button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Guide (EPG)",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = t.colors.foreground
            )

            var isJumpFocused by remember { mutableStateOf(false) }

            TvFocusable(
                onClick = {
                    scope.launch {
                        horizontalScrollState.animateScrollTo(0)
                    }
                },
                modifier = Modifier.onFocusChanged { isJumpFocused = it.isFocused }
            ) {
                AdaptiveButton(
                    text = "Jump to now",
                    onClick = {
                        scope.launch {
                            horizontalScrollState.animateScrollTo(0)
                        }
                    },
                    backdropLuminance = if (isJumpFocused) 1f else 0f
                )
            }
        }

        // Synopsis / Selected Program Panel below ruler
        LumenCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                if (focusedProgram != null) {
                    Text(
                        text = "${focusedChannel?.name ?: ""} — ${focusedProgram?.title ?: ""}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = t.colors.foreground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${timeFormatter.format(Date(focusedProgram!!.startTimeMs))} - ${timeFormatter.format(Date(focusedProgram!!.endTimeMs))}",
                        fontSize = 11.sp,
                        color = t.colors.brand
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = focusedProgram?.description ?: "No description available.",
                        fontSize = 11.sp,
                        color = t.colors.mutedForeground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "Highlight a program card to view details",
                        fontSize = 12.sp,
                        color = t.colors.mutedForeground
                    )
                }
            }
        }

        // Grid Area (vertical scroll for channels, horizontal scroll for EPG timeline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Sticky Left Channel list
            LazyColumn(
                modifier = Modifier
                    .width(68.dp)
                    .fillMaxHeight()
            ) {
                // Ruler alignment spacer
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .background(t.colors.muted)
                            .border(0.5.dp, t.colors.border)
                    )
                }

                itemsIndexed(channels, key = { _, ch -> "sticky-ch-${ch.id}" }) { _, channel ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .border(0.5.dp, t.colors.border)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(t.colors.muted)
                        )
                    }
                }
            }

            // Right scrollable EPG Timeline
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(horizontalScrollState)
            ) {
                // Time Ruler Row
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .background(t.colors.muted)
                        .border(0.5.dp, t.colors.border),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ticks.forEach { tickTime ->
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .fillMaxHeight()
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = timeFormatter.format(Date(tickTime)),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = t.colors.foreground
                            )
                        }
                    }
                }

                // Program Rows
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    itemsIndexed(channels, key = { _, ch -> "guide-row-${ch.id}" }) { channelIndex, channel ->
                        val nowNext = nowNextMap[channel.id]
                        Row(
                            modifier = Modifier
                                .height(68.dp)
                                .border(0.5.dp, t.colors.border),
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    Text("No program information", color = t.colors.mutedForeground, fontSize = 11.sp)
                                }
                            } else {
                                programsList.forEachIndexed { progIndex, program ->
                                    val pStart = maxOf(program.startTimeMs, startOfCurrentHour)
                                    val pEnd = minOf(program.endTimeMs, endOfTimeline)
                                    val durationMinutes = (pEnd - pStart) / 60000.0
                                    val blockWidth = (durationMinutes * 4.0).coerceAtLeast(140.0).dp

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

                                    val isFirstCard = channelIndex == 0 && progIndex == 0
                                    var isCardFocused by remember { mutableStateOf(false) }

                                    TvFocusable(
                                        onClick = { onChannelSelect(channel, uiProgram) },
                                        modifier = Modifier
                                            .width(blockWidth)
                                            .fillMaxHeight()
                                            .padding(4.dp)
                                            .run {
                                                if (isFirstCard) focusRequester(initialFocusRequester) else this
                                            }
                                            .onFocusChanged {
                                                isCardFocused = it.isFocused
                                                if (it.isFocused) {
                                                    focusedProgram = uiProgram
                                                    focusedChannel = channel
                                                }
                                            }
                                    ) {
                                        LumenCard(
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        if (isCurrent) t.colors.brand.copy(alpha = 0.15f)
                                                        else Color.Transparent
                                                    )
                                                    .padding(8.dp),
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = program.title,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = t.colors.foreground,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${timeFormatter.format(Date(program.startTimeMs))} - ${timeFormatter.format(Date(program.endTimeMs))}",
                                                    fontSize = 9.5.sp,
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
}
