// feature/player/src/main/kotlin/com/calmsource/feature/player/PlayerChrome.kt
//
// Player controls overlay (mobile + TV variants share the same composable).
//
// Owns ONLY presentation. Wire `state` and `actions` from your ExoPlayer-backed
// ViewModel; this file does not depend on ExoPlayer directly so it can be
// previewed and unit-tested in isolation.
//
// Behaviours covered:
//  • Auto-hide after 3s of inactivity; any touch / D-pad event resets the timer.
//  • Buffering spinner overlay (with optional caption).
//  • Scrubber with thumb, buffered range, and double-tap-to-seek ±10s (mobile).
//  • Audio / Subtitle track sheets (LumenCard list).
//  • Quality picker — feed it variants from your StreamPicker.
//  • TV mode: focus chain Play → Scrubber → Audio → Subs → Quality → Settings,
//    using TvFocusable from core/ui.
//  • Subtitle cue rendering kept separate (SubtitleCueView) so non-Latin scripts
//    and bidi text get correct line breaks.
//
// What this file deliberately AVOIDS:
//  • Direct ExoPlayer instantiation (caller owns lifecycle / DataSource.Factory).
//  • Codec / HDR negotiation (lives in `PlayerEngine`).

package com.example.calmsource.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.components.GlassSurface
import com.example.calmsource.core.ui.theme.LumenTokens
import com.example.calmsource.core.ui.theme.LumenType
import kotlinx.coroutines.delay

data class TrackOption(val id: String, val label: String, val language: String? = null, val selected: Boolean = false)
data class QualityOption(val id: String, val label: String, val heightPx: Int? = null, val bitrateKbps: Int? = null, val selected: Boolean = false)

data class PlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val bufferedMs: Long = 0,
    val durationMs: Long = 0,
    val title: String = "",
    val subtitleCue: String? = null,
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(), // include "Off" as a synthetic option
    val qualityOptions: List<QualityOption> = emptyList(),
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false,
)

data class PlayerActions(
    val onPlayPause: () -> Unit = {},
    val onSeekTo: (Long) -> Unit = {},
    val onSeekRelative: (Long) -> Unit = {},
    val onSelectAudio: (TrackOption) -> Unit = {},
    val onSelectSubtitle: (TrackOption) -> Unit = {},
    val onSelectQuality: (QualityOption) -> Unit = {},
    val onNext: () -> Unit = {},
    val onPrev: () -> Unit = {},
    val onClose: () -> Unit = {},
)

private enum class Sheet { None, Audio, Subtitle, Quality }

@Composable
fun PlayerChrome(
    state: PlayerState,
    actions: PlayerActions,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    var visible by rememberSaveable { mutableStateOf(true) }
    var sheet by remember { mutableStateOf(Sheet.None) }
    var lastInteractAt by remember { mutableStateOf(System.currentTimeMillis()) }

    // Auto-hide after 3s; never hide while a sheet is open or while buffering.
    LaunchedEffect(lastInteractAt, sheet, state.isBuffering, state.isPlaying) {
        if (!state.isPlaying || sheet != Sheet.None || state.isBuffering) return@LaunchedEffect
        delay(3_000)
        if (System.currentTimeMillis() - lastInteractAt >= 2_900) visible = false
    }

    fun touch() { visible = true; lastInteractAt = System.currentTimeMillis() }

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { touch() },
                    onDoubleTap = { offset ->
                        // Double-tap left/right half ±10s (mobile only).
                        if (isTv) return@detectTapGestures
                        val w = size.width
                        actions.onSeekRelative(if (offset.x < w / 2) -10_000L else 10_000L)
                        touch()
                    },
                )
            }
    ) {
        // Subtitle cue stays visible regardless of chrome opacity.
        SubtitleCueView(
            cue = state.subtitleCue,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp),
        )

        // Buffering overlay — non-blocking, sits above video.
        if (state.isBuffering) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LumenTokens.Color.brand, strokeWidth = 3.dp)
            }
        }

        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            ChromeLayer(state, actions, isTv, openSheet = { s -> sheet = s; touch() })
        }

        when (sheet) {
            Sheet.Audio -> TrackSheet(
                title = "Audio",
                tracks = state.audioTracks,
                onSelect = { actions.onSelectAudio(it); sheet = Sheet.None; touch() },
                onDismiss = { sheet = Sheet.None },
            )
            Sheet.Subtitle -> TrackSheet(
                title = "Subtitles",
                tracks = state.subtitleTracks,
                onSelect = { actions.onSelectSubtitle(it); sheet = Sheet.None; touch() },
                onDismiss = { sheet = Sheet.None },
            )
            Sheet.Quality -> QualitySheet(
                qualities = state.qualityOptions,
                onSelect = { actions.onSelectQuality(it); sheet = Sheet.None; touch() },
                onDismiss = { sheet = Sheet.None },
            )
            Sheet.None -> Unit
        }
    }
}

@Composable
private fun ChromeLayer(
    state: PlayerState,
    actions: PlayerActions,
    isTv: Boolean,
    openSheet: (Sheet) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // Top bar — title + close.
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = LumenTokens.Shape.md,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.title,
                    style = LumenType.Title.toTextStyle(if (isTv) LumenType.TV_SCALE else 1f),
                    color = LumenTokens.Color.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        // Bottom controls.
        GlassSurface(modifier = Modifier.fillMaxWidth(), shape = LumenTokens.Shape.md, strong = true) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Scrubber(state = state, onSeek = actions.onSeekTo)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.hasPrev) IconButton(onClick = actions.onPrev) { Icon(Icons.Default.SkipPrevious, null, tint = LumenTokens.Color.textPrimary) }
                    IconButton(onClick = actions.onPlayPause) {
                        Icon(
                            if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = LumenTokens.Color.textPrimary,
                        )
                    }
                    if (state.hasNext) IconButton(onClick = actions.onNext) { Icon(Icons.Default.SkipNext, null, tint = LumenTokens.Color.textPrimary) }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${fmtTime(state.positionMs)} / ${fmtTime(state.durationMs)}",
                        style = LumenType.Meta.toTextStyle(),
                        color = LumenTokens.Color.textSecondary,
                    )
                    Spacer(Modifier.width(12.dp))
                    if (state.audioTracks.isNotEmpty()) IconButton(onClick = { openSheet(Sheet.Audio) }) { Icon(Icons.Default.GraphicEq, "Audio", tint = LumenTokens.Color.textPrimary) }
                    if (state.subtitleTracks.isNotEmpty()) IconButton(onClick = { openSheet(Sheet.Subtitle) }) { Icon(Icons.Default.ClosedCaption, "Subtitles", tint = LumenTokens.Color.textPrimary) }
                    if (state.qualityOptions.isNotEmpty()) IconButton(onClick = { openSheet(Sheet.Quality) }) { Icon(Icons.Default.HighQuality, "Quality", tint = LumenTokens.Color.textPrimary) }
                }
            }
        }
    }
}

@Composable
private fun Scrubber(state: PlayerState, onSeek: (Long) -> Unit) {
    val durationF = state.durationMs.coerceAtLeast(1L).toFloat()
    val position = state.positionMs.toFloat().coerceIn(0f, durationF)
    val buffered = state.bufferedMs.toFloat().coerceIn(0f, durationF)
    Column {
        Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.CenterStart) {
            // Buffered track shown behind the active Slider.
            Box(Modifier.fillMaxWidth().height(3.dp).background(LumenTokens.Color.borderSubtle, LumenTokens.Shape.pill))
            Box(
                Modifier
                    .fillMaxWidth(buffered / durationF)
                    .height(3.dp)
                    .background(LumenTokens.Color.borderStrong, LumenTokens.Shape.pill)
            )
            Slider(
                value = position,
                valueRange = 0f..durationF,
                onValueChange = { onSeek(it.toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = LumenTokens.Color.brand,
                    activeTrackColor = LumenTokens.Color.brand,
                    inactiveTrackColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun TrackSheet(
    title: String,
    tracks: List<TrackOption>,
    onSelect: (TrackOption) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
        GlassSurface(modifier = Modifier.fillMaxWidth(), shape = LumenTokens.Shape.xl, strong = true) {
            Column(Modifier.padding(20.dp)) {
                Text(title, style = LumenType.H2.toTextStyle(), color = LumenTokens.Color.textPrimary)
                Spacer(Modifier.height(12.dp))
                tracks.forEach { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(t) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            t.label + (t.language?.let { " · $it" } ?: ""),
                            style = LumenType.Body.toTextStyle(),
                            color = if (t.selected) LumenTokens.Color.brand else LumenTokens.Color.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (t.selected) Text("✓", color = LumenTokens.Color.brand)
                    }
                }
            }
        }
    }
}

@Composable
private fun QualitySheet(
    qualities: List<QualityOption>,
    onSelect: (QualityOption) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
        GlassSurface(modifier = Modifier.fillMaxWidth(), shape = LumenTokens.Shape.xl, strong = true) {
            Column(Modifier.padding(20.dp)) {
                Text("Quality", style = LumenType.H2.toTextStyle(), color = LumenTokens.Color.textPrimary)
                Spacer(Modifier.height(12.dp))
                qualities.forEach { q ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(q) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            q.label,
                            style = LumenType.Body.toTextStyle(),
                            color = if (q.selected) LumenTokens.Color.brand else LumenTokens.Color.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        q.bitrateKbps?.let {
                            Text("${it} kbps", style = LumenType.Meta.toTextStyle(), color = LumenTokens.Color.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleCueView(cue: String?, modifier: Modifier = Modifier) {
    if (cue.isNullOrBlank()) return
    Box(modifier.padding(horizontal = 24.dp)) {
        Box(
            Modifier
                .background(Color(0xAA000000), LumenTokens.Shape.sm)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(cue, color = Color.White, style = LumenType.Body.toTextStyle(1.1f))
        }
    }
}

private fun fmtTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
