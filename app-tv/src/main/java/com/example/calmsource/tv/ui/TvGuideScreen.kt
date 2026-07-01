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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calmsource.core.model.Channel
import com.example.calmsource.core.model.Program
import com.example.calmsource.core.ui.components.AdaptiveButton
import com.example.calmsource.core.ui.components.LumenCard
import com.example.calmsource.core.ui.components.LumenEmptyState
import com.example.calmsource.core.ui.components.TvFocusable
import com.example.calmsource.core.ui.theme.*
import com.example.calmsource.feature.epg.EpgGrid
import com.example.calmsource.feature.epg.mapGuideToEpgGrid
import com.example.calmsource.feature.iptv.EpgNowNext
import com.example.calmsource.feature.iptv.LiveGuideUiState
import androidx.compose.material.icons.Icons
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

private const val EPG_PX_PER_MINUTE = 8

@Composable
fun TvGuideScreen(
    uiState: LiveGuideUiState,
    nowNextMap: Map<String, EpgNowNext>,
    onChannelSelect: (Channel, Program?) -> Unit,
) {
    val t = LocalLumenTokens.current
    val channels = uiState.filteredChannels

    if (channels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LumenEmptyState(
                title = "Schedule unavailable",
                body = "No channels are loaded or match the active filters.",
                icon = ImageVector.vectorResource(id = com.example.calmsource.core.ui.R.drawable.ic_list),
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
    val endOfTimeline = startOfCurrentHour + 24 * 60 * 60 * 1000L

    val (epgChannels, programmes) = remember(channels, nowNextMap, startOfCurrentHour, endOfTimeline) {
        mapGuideToEpgGrid(channels, nowNextMap, startOfCurrentHour, endOfTimeline)
    }

    var focusedProgram by remember { mutableStateOf<Program?>(null) }
    var focusedChannel by remember { mutableStateOf<Channel?>(null) }

    LaunchedEffect(channels, nowNextMap) {
        if (focusedProgram != null) return@LaunchedEffect
        val channel = channels.firstOrNull() ?: return@LaunchedEffect
        val current = nowNextMap[channel.id]?.currentProgram ?: return@LaunchedEffect
        focusedChannel = channel
        focusedProgram = Program(
            id = current.id,
            channelId = channel.id,
            title = current.title,
            description = current.description,
            startTimeMs = current.startTimeMs,
            endTimeMs = current.endTimeMs,
        )
    }

    val horizontalScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.colors.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LumenLegacySpace.lg, vertical = LumenLegacySpace.sm2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Live Guide (EPG)",
                fontSize = LumenType.size20,
                fontWeight = FontWeight.Bold,
                color = t.colors.foreground,
            )

            AdaptiveButton(
                text = "Jump to now",
                onClick = {
                    val nowMinutes = max(0, ((System.currentTimeMillis() - startOfCurrentHour) / 60_000).toInt())
                    scope.launch {
                        val px = with(density) { (LumenLayout.epgPxPerMinute * nowMinutes).roundToPx() }
                        horizontalScrollState.animateScrollTo(px)
                    }
                },
                backdropLuminance = 0f,
                modifier = Modifier
                    .padding(horizontal = LumenLegacySpace.lg, vertical = LumenLegacySpace.sm2)
            )
        }

        LumenCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(LumenLayout.inputWidthSm)
                .padding(horizontal = LumenLegacySpace.lg, vertical = LumenLegacySpace.sm2),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(LumenLegacySpace.sm2),
                verticalArrangement = Arrangement.Center,
            ) {
                val program = focusedProgram
                if (program != null) {
                    Text(
                        text = "${focusedChannel?.name.orEmpty()} — ${program.title}",
                        fontSize = LumenType.size14,
                        fontWeight = FontWeight.Bold,
                        color = t.colors.foreground,
                    )
                    Spacer(modifier = Modifier.height(LumenLegacySpace.xxs))
                    Text(
                        text = "${timeFormatter.format(Date(program.startTimeMs))} - ${timeFormatter.format(Date(program.endTimeMs))}",
                        fontSize = LumenType.size11,
                        color = t.colors.brand,
                    )
                    Spacer(modifier = Modifier.height(LumenLegacySpace.xxs))
                    Text(
                        text = program.description ?: "No description available.",
                        fontSize = LumenType.size11,
                        color = t.colors.mutedForeground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = "Select a program to view details",
                        fontSize = LumenType.size12,
                        color = t.colors.mutedForeground,
                    )
                }
            }
        }

        EpgGrid(
            channels = epgChannels,
            programmes = programmes,
            windowStartEpochMs = startOfCurrentHour,
            windowEndEpochMs = endOfTimeline,
            horizontalScrollState = horizontalScrollState,
            onProgrammeClick = { programme ->
                val channel = channels.firstOrNull { it.id == programme.channelId } ?: return@EpgGrid
                val uiProgram = Program(
                    id = programme.id,
                    channelId = programme.channelId,
                    title = programme.title,
                    description = programme.description,
                    startTimeMs = programme.startEpochMs,
                    endTimeMs = programme.endEpochMs,
                )
                focusedProgram = uiProgram
                focusedChannel = channel
                onChannelSelect(channel, uiProgram)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}
