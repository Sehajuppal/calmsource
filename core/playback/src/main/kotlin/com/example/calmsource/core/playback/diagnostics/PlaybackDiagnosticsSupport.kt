package com.example.calmsource.core.playback.diagnostics

import com.example.calmsource.core.model.PlayerState
import com.example.calmsource.core.playback.recovery.RecoveryAction
import com.example.calmsource.core.playback.session.SessionPhase

fun RecoveryAction.sanitizedEvent(): Pair<String, String> = when (this) {
    RecoveryAction.TryMimeRetry -> "mime_retry" to "Trying alternate container"
    RecoveryAction.SwitchToVlc -> "vlc_switch" to "Switching to VLC backend"
    RecoveryAction.RetrySafeProfile -> "safe_profile" to "Retrying with safe decoder profile"
    is RecoveryAction.ShowFallbackPrompt -> "fallback_prompt" to "Waiting for user approval"
    is RecoveryAction.AutoFallback -> "auto_fallback" to message
    is RecoveryAction.Terminal -> "terminal" to error.message
    is RecoveryAction.FailInPlace -> "fail_in_place" to error.message
    is RecoveryAction.FailWithoutSource -> "no_source" to error.message
}

fun inferDisplayPhase(
    playerState: PlayerState,
    isTransitioningSource: Boolean,
    storedPhase: SessionPhase?,
): String {
    if (isTransitioningSource) return SessionPhase.Recovering.name
    if (storedPhase == SessionPhase.Racing && playerState == PlayerState.PREPARING) {
        return SessionPhase.Racing.name
    }
    return when (playerState) {
        PlayerState.IDLE, PlayerState.ENDED -> SessionPhase.Idle.name
        PlayerState.PREPARING -> SessionPhase.Preparing.name
        PlayerState.BUFFERING, PlayerState.PLAYING, PlayerState.PAUSED, PlayerState.READY ->
            SessionPhase.Playing.name
        PlayerState.FAILED -> SessionPhase.Failed.name
    }
}
