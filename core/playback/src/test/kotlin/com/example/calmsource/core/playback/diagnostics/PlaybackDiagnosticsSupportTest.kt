package com.example.calmsource.core.playback.diagnostics

import com.example.calmsource.core.model.PlayerState
import com.example.calmsource.core.playback.recovery.RecoveryAction
import com.example.calmsource.core.playback.session.SessionPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackDiagnosticsSupportTest {

    @Test
    fun `sanitizedEvent maps recovery actions to stable labels`() {
        assertEquals("mime_retry" to "Trying alternate container", RecoveryAction.TryMimeRetry.sanitizedEvent())
        assertEquals("vlc_switch" to "Switching to VLC backend", RecoveryAction.SwitchToVlc.sanitizedEvent())
        assertEquals(
            "auto_fallback" to "Trying backup stream",
            RecoveryAction.AutoFallback(
                com.example.calmsource.core.model.PlaybackError.Network(),
                "Trying backup stream"
            ).sanitizedEvent()
        )
    }

    @Test
    fun `inferDisplayPhase prefers recovering while transitioning`() {
        assertEquals(
            SessionPhase.Recovering.name,
            inferDisplayPhase(PlayerState.FAILED, isTransitioningSource = true, storedPhase = SessionPhase.Playing)
        )
    }

    @Test
    fun `inferDisplayPhase keeps racing during prepare`() {
        assertEquals(
            SessionPhase.Racing.name,
            inferDisplayPhase(PlayerState.PREPARING, isTransitioningSource = false, storedPhase = SessionPhase.Racing)
        )
    }
}
