package com.example.calmsource.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.calmsource.core.model.Channel
import com.example.calmsource.core.model.Program
import androidx.compose.ui.res.stringResource
import com.example.calmsource.core.ui.R as CoreUiR
import com.example.calmsource.core.ui.components.LumenEmptyState
import com.example.calmsource.feature.epg.EpgGrid
import com.example.calmsource.feature.epg.mapGuideToEpgGrid
import com.example.calmsource.feature.iptv.EpgNowNext
import com.example.calmsource.feature.iptv.LiveGuideUiState
import java.util.Calendar

@Composable
fun GuideScreen(
    uiState: LiveGuideUiState,
    nowNextMap: Map<String, EpgNowNext>,
    onChannelSelect: (Channel, Program?) -> Unit,
) {
    val channels = uiState.filteredChannels

    if (channels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LumenEmptyState(
                title = stringResource(CoreUiR.string.guide_schedule_unavailable),
                body = stringResource(CoreUiR.string.guide_schedule_unavailable_body),
                icon = Icons.AutoMirrored.Filled.List,
            )
        }
        return
    }

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

    EpgGrid(
        channels = epgChannels,
        programmes = programmes,
        windowStartEpochMs = startOfCurrentHour,
        windowEndEpochMs = endOfTimeline,
        onProgrammeClick = { programme ->
            val channel = channels.firstOrNull { it.id == programme.channelId } ?: return@EpgGrid
            onChannelSelect(
                channel,
                Program(
                    id = programme.id,
                    channelId = programme.channelId,
                    title = programme.title,
                    description = programme.description,
                    startTimeMs = programme.startEpochMs,
                    endTimeMs = programme.endEpochMs,
                ),
            )
        },
        modifier = Modifier.fillMaxSize(),
    )
}
