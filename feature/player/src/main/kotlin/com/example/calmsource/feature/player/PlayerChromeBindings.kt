package com.example.calmsource.feature.player

import com.example.calmsource.core.model.PlaybackAudioTrack
import com.example.calmsource.core.model.PlaybackProgressState
import com.example.calmsource.core.model.PlaybackSubtitleTrack
import com.example.calmsource.core.model.PlayerState as ExoPlayerState
import com.example.calmsource.core.playback.ui.TrackLanguageFormatter

fun buildPlayerChromeState(
    title: String,
    exoState: ExoPlayerState,
    progress: PlaybackProgressState,
    audioTracks: List<PlaybackAudioTrack>,
    subtitleTracks: List<PlaybackSubtitleTrack>,
    isLive: Boolean,
    hasNext: Boolean = false,
    hasPrev: Boolean = false,
    subtitleCue: String? = null,
): PlayerState {
    val isPlaying = exoState == ExoPlayerState.PLAYING
    val isBuffering = exoState == ExoPlayerState.BUFFERING || exoState == ExoPlayerState.PREPARING
    return PlayerState(
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        positionMs = progress.currentPositionMs,
        bufferedMs = progress.bufferedPositionMs,
        durationMs = if (isLive) 0L else progress.durationMs.coerceAtLeast(0L),
        title = title,
        subtitleCue = subtitleCue,
        audioTracks = audioTracks.map { track ->
            TrackOption(
                id = track.id,
                label = TrackLanguageFormatter.trackLabel(track.name, track.language),
                language = track.language,
                selected = track.isSelected,
            )
        },
        subtitleTracks = buildSubtitleTrackOptions(subtitleTracks),
        qualityOptions = emptyList(),
        hasNext = hasNext,
        hasPrev = hasPrev,
    )
}

private fun buildSubtitleTrackOptions(tracks: List<PlaybackSubtitleTrack>): List<TrackOption> {
    if (tracks.isEmpty()) return emptyList()
    val mapped = tracks.map { track ->
        TrackOption(
            id = track.id,
            label = TrackLanguageFormatter.trackLabel(track.name, track.language),
            language = track.language,
            selected = track.isSelected,
        )
    }
    val offSelected = tracks.none { it.isSelected }
    return listOf(TrackOption(id = "off", label = "Off", selected = offSelected)) + mapped
}
