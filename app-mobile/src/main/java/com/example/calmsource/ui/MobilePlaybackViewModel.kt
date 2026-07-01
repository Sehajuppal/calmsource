package com.example.calmsource.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calmsource.core.discoveryengine.providers.ProviderManager
import com.example.calmsource.core.model.PlayerState
import com.example.calmsource.core.model.ResourcePlaybackState
import com.example.calmsource.core.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class MobilePlaybackViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val _isMinimized = MutableStateFlow(false)
    val isMinimized: StateFlow<Boolean> = _isMinimized.asStateFlow()

    private val _playerRoute = MutableStateFlow<com.example.calmsource.MobileScreen.Player?>(null)
    val playerRoute: StateFlow<com.example.calmsource.MobileScreen.Player?> = _playerRoute.asStateFlow()

    fun rememberPlayerRoute(route: com.example.calmsource.MobileScreen.Player) {
        _playerRoute.value = route
    }

    private var playbackManager: PlaybackManager? = null
    private val managerScope = CoroutineScope(viewModelScope.coroutineContext)

    fun obtainManager(): PlaybackManager {
        val existing = playbackManager
        if (existing != null) return existing
        return PlaybackManager(
            context = appContext,
            coroutineScope = managerScope,
            resourceStateSink = { state ->
                ProviderManager.setPlaybackState(state.toResourcePlaybackState())
            },
            lowMemoryModeSink = { enabled ->
                ProviderManager.setLowMemoryMode(enabled)
            },
        ).also { playbackManager = it }
    }

    fun minimize() {
        _isMinimized.value = true
        playbackManager?.setPlayerView(null)
    }

    fun expand() {
        _isMinimized.value = false
    }

    fun stopSession() {
        playbackManager?.release()
        playbackManager = null
        _isMinimized.value = false
        _playerRoute.value = null
        ProviderManager.setPlaybackState(ResourcePlaybackState.IDLE)
    }

    fun togglePlayPause() {
        val manager = playbackManager ?: return
        val state = manager.uiState.value.playerState
        if (state == PlayerState.PLAYING) manager.pause() else manager.play()
    }

    override fun onCleared() {
        stopSession()
        super.onCleared()
    }
}

private fun PlayerState.toResourcePlaybackState(): ResourcePlaybackState = when (this) {
    PlayerState.IDLE -> ResourcePlaybackState.IDLE
    PlayerState.PREPARING -> ResourcePlaybackState.BUFFERING
    PlayerState.BUFFERING -> ResourcePlaybackState.BUFFERING
    PlayerState.READY -> ResourcePlaybackState.READY_PAUSED
    PlayerState.PLAYING -> ResourcePlaybackState.READY_PLAYING
    PlayerState.PAUSED -> ResourcePlaybackState.READY_PAUSED
    PlayerState.ENDED -> ResourcePlaybackState.ENDED
    PlayerState.FAILED -> ResourcePlaybackState.ERROR
}
