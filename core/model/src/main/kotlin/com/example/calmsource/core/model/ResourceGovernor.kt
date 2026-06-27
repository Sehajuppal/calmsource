package com.example.calmsource.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ResourcePlaybackState {
    IDLE,
    BUFFERING,
    READY_PLAYING,
    READY_PAUSED,
    ENDED,
    ERROR
}

data class ResourceGovernorSnapshot(
    val playbackState: ResourcePlaybackState = ResourcePlaybackState.IDLE,
    val lowMemoryMode: Boolean = false,
    val shouldPauseBackgroundWork: Boolean = false
)

object ResourceGovernor {
    private val _snapshot = MutableStateFlow(ResourceGovernorSnapshot())
    val snapshot: StateFlow<ResourceGovernorSnapshot> = _snapshot.asStateFlow()

    fun setLowMemoryMode(enabled: Boolean) {
        update(lowMemoryMode = enabled)
    }

    fun setPlaybackActive(active: Boolean) {
        update(
            playbackState = if (active) {
                ResourcePlaybackState.READY_PLAYING
            } else {
                ResourcePlaybackState.READY_PAUSED
            }
        )
    }

    fun setPlaybackState(state: ResourcePlaybackState) {
        update(playbackState = state)
    }

    private fun update(
        playbackState: ResourcePlaybackState = _snapshot.value.playbackState,
        lowMemoryMode: Boolean = _snapshot.value.lowMemoryMode
    ) {
        val playbackWorkActive = playbackState == ResourcePlaybackState.BUFFERING ||
            playbackState == ResourcePlaybackState.READY_PLAYING
        _snapshot.value = ResourceGovernorSnapshot(
            playbackState = playbackState,
            lowMemoryMode = lowMemoryMode,
            shouldPauseBackgroundWork = lowMemoryMode || playbackWorkActive
        )
    }
}
