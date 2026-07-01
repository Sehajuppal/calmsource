// feature/epg/src/main/kotlin/com/calmsource/feature/epg/EpgGrid.kt
//
// 2D-virtualized EPG (Electronic Programme Guide) grid.
//
// Layout:
//   ┌────────────┬───────────────────────────────────────────────┐
//   │            │  06:00   06:30   07:00   07:30   08:00  ...   │  ← sticky TIME RULER (top)
//   ├────────────┼───────────────────────────────────────────────┤
//   │ BBC One    │ [News           ][Breakfast       ][...]      │
//   │ BBC Two    │ [Cartoons ][Schools      ][...]               │
//   │ ITV1       │ [GMB                              ][This M.]  │  ← rows scroll vertically
//   │ ...        │  ...                                          │
//   └────────────┴───────────────────────────────────────────────┘
//      ↑ sticky CHANNEL LANE (left), scrolls vertically only
//
// Both axes share scroll state — horizontal scrollState is reused by the ruler
// AND by every row, so they stay aligned without re-emitting events.
//
// Time-to-pixel mapping:
//   pxPerMinute is fixed (default 8.dp/min ⇒ 240.dp per 30-min slot).
//   Programme x = ((startEpochMs - windowStartMs) / 60000) * pxPerMinute
//   Programme w = ((endEpochMs   - startEpochMs)  / 60000) * pxPerMinute
//
// Current-time indicator is drawn as an overlay line at the computed x; it
// re-evaluates every minute via a LaunchedEffect tick.
//
// Programmes are passed pre-sorted per channel (the repository already does this).
// We DO virtualize channels vertically (LazyColumn) and rely on row-level
// horizontal clipping; programmes outside the visible window are still composed
// but stay cheap because each is just a Box+Text. If channels exceed a few
// hundred, swap LazyRow per-row for a custom Layout that culls by x range.

package com.example.calmsource.feature.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.theme.LumenTokens
import com.example.calmsource.core.ui.theme.LumenType
import kotlinx.coroutines.delay
import kotlin.math.max

data class EpgChannel(val id: String, val name: String, val logoUrl: String? = null)
data class EpgProgramme(
    val id: String,
    val channelId: String,
    val title: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val description: String? = null,
)

private const val MS_PER_MIN = 60_000L

@Composable
fun EpgGrid(
    channels: List<EpgChannel>,
    programmes: Map<String, List<EpgProgramme>>, // keyed by channelId, pre-sorted
    windowStartEpochMs: Long,
    windowEndEpochMs: Long,
    onProgrammeClick: (EpgProgramme) -> Unit,
    modifier: Modifier = Modifier,
    horizontalScrollState: ScrollState? = null,
    pxPerMinute: Dp = 8.dp,
    rowHeight: Dp = 72.dp,
    laneWidth: Dp = 140.dp,
    rulerHeight: Dp = 36.dp,
) {
    val hScroll = horizontalScrollState ?: rememberScrollState()
    val vScroll = rememberLazyListState()
    val totalMinutes = max(1, ((windowEndEpochMs - windowStartEpochMs) / MS_PER_MIN).toInt())
    val gridWidth = pxPerMinute * totalMinutes

    // Re-tick once a minute to refresh the now-line position.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(60_000)
        }
    }

    Column(modifier.fillMaxSize().background(LumenTokens.Color.bg)) {
        // ── Sticky top: time ruler, offset by laneWidth so it sits over the grid only.
        Row(Modifier.fillMaxWidth().height(rulerHeight)) {
            Box(Modifier.width(laneWidth).fillMaxHeight().background(LumenTokens.Color.surface))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(LumenTokens.Color.surface)
                    .horizontalScroll(hScroll, enabled = false) // driven by rows below
            ) {
                TimeRuler(
                    windowStartEpochMs = windowStartEpochMs,
                    totalMinutes = totalMinutes,
                    pxPerMinute = pxPerMinute,
                    modifier = Modifier.width(gridWidth).fillMaxHeight(),
                )
            }
        }

        Box(Modifier.weight(1f)) {
            LazyColumn(state = vScroll, modifier = Modifier.fillMaxSize()) {
                items(channels, key = { it.id }) { ch ->
                    EpgRow(
                        channel = ch,
                        programmes = programmes[ch.id].orEmpty(),
                        windowStartEpochMs = windowStartEpochMs,
                        windowEndEpochMs = windowEndEpochMs,
                        pxPerMinute = pxPerMinute,
                        gridWidth = gridWidth,
                        rowHeight = rowHeight,
                        laneWidth = laneWidth,
                        hScroll = hScroll,
                        nowMs = nowMs,
                        onProgrammeClick = onProgrammeClick,
                    )
                }
            }
            // Current-time vertical line — drawn on top of all rows but right of the lane.
            NowLine(
                nowMs = nowMs,
                windowStartEpochMs = windowStartEpochMs,
                windowEndEpochMs = windowEndEpochMs,
                pxPerMinute = pxPerMinute,
                laneWidth = laneWidth,
                hScrollState = hScroll,
            )
        }
    }
}

@Composable
private fun TimeRuler(
    windowStartEpochMs: Long,
    totalMinutes: Int,
    pxPerMinute: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        // Tick every 30 minutes.
        val slot = 30
        val count = totalMinutes / slot
        for (i in 0..count) {
            val minutes = i * slot
            val x = pxPerMinute * minutes
            val tickMs = windowStartEpochMs + minutes * MS_PER_MIN
            Box(Modifier.offset(x = x).fillMaxHeight()) {
                Text(
                    text = formatHm(tickMs),
                    style = LumenType.Meta.toTextStyle(),
                    color = LumenTokens.Color.textSecondary,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EpgRow(
    channel: EpgChannel,
    programmes: List<EpgProgramme>,
    windowStartEpochMs: Long,
    windowEndEpochMs: Long,
    pxPerMinute: Dp,
    gridWidth: Dp,
    rowHeight: Dp,
    laneWidth: Dp,
    hScroll: androidx.compose.foundation.ScrollState,
    nowMs: Long,
    onProgrammeClick: (EpgProgramme) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(rowHeight)) {
        // Sticky channel lane (left).
        Box(
            Modifier
                .width(laneWidth)
                .fillMaxHeight()
                .background(LumenTokens.Color.surface)
                .border(0.5.dp, LumenTokens.Color.borderSubtle),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = channel.name,
                style = LumenType.RowTitle.toTextStyle(),
                color = LumenTokens.Color.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        // Scrollable programmes lane.
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(hScroll)
        ) {
            Box(Modifier.width(gridWidth).fillMaxHeight()) {
                for (p in programmes) {
                    // Cull anything entirely outside the window.
                    if (p.endEpochMs <= windowStartEpochMs || p.startEpochMs >= windowEndEpochMs) continue
                    val start = max(p.startEpochMs, windowStartEpochMs)
                    val end = minOf(p.endEpochMs, windowEndEpochMs)
                    val xMin = ((start - windowStartEpochMs) / MS_PER_MIN).toInt()
                    val wMin = max(1, ((end - start) / MS_PER_MIN).toInt())
                    val isLive = nowMs in p.startEpochMs..p.endEpochMs
                    ProgrammeBlock(
                        programme = p,
                        modifier = Modifier
                            .offset(x = pxPerMinute * xMin)
                            .width(pxPerMinute * wMin)
                            .fillMaxHeight()
                            .padding(2.dp),
                        isLive = isLive,
                        onClick = { onProgrammeClick(p) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgrammeBlock(
    programme: EpgProgramme,
    modifier: Modifier,
    isLive: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (isLive) LumenTokens.Color.brand.copy(alpha = 0.18f) else LumenTokens.Color.surfaceMuted
    val borderC = if (isLive) LumenTokens.Color.brand else LumenTokens.Color.borderSubtle
    Box(
        modifier
            .clip(LumenTokens.Shape.sm)
            .background(bg)
            .border(1.dp, borderC, LumenTokens.Shape.sm)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column {
            Text(
                text = programme.title,
                style = LumenType.Body.toTextStyle(),
                color = LumenTokens.Color.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatHm(programme.startEpochMs)} – ${formatHm(programme.endEpochMs)}",
                style = LumenType.Meta.toTextStyle(),
                color = LumenTokens.Color.textSecondary,
            )
        }
    }
}

@Composable
private fun NowLine(
    nowMs: Long,
    windowStartEpochMs: Long,
    windowEndEpochMs: Long,
    pxPerMinute: Dp,
    laneWidth: Dp,
    hScrollState: ScrollState,
) {
    if (nowMs < windowStartEpochMs || nowMs > windowEndEpochMs) return
    val minutesFromStart = ((nowMs - windowStartEpochMs) / MS_PER_MIN).toInt()
    val xInGrid = pxPerMinute * minutesFromStart
    // Account for the horizontal scroll offset of the grid lane.
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .offset(x = laneWidth + xInGrid)
                // Read hScrollState.value inside the layout-phase lambda to avoid
                // recomposition on every scroll pixel.
                .offset { androidx.compose.ui.unit.IntOffset(-hScrollState.value, 0) }
                .width(2.dp)
                .fillMaxHeight()
                .background(LumenTokens.Color.brand)
        )
    }
}

private fun formatHm(epochMs: Long): String {
    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(epochMs))
}
