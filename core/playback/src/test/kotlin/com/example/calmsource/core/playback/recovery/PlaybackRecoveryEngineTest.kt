package com.example.calmsource.core.playback.recovery

import com.example.calmsource.core.model.AutoFallbackPolicy
import com.example.calmsource.core.model.PlaybackError
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoveryEngineTest {

    private val source1 = PlaybackSource("s1", PlaybackSourceType.IPTV, "S1", "https://example.com/1.m3u8")
    private val source2 = PlaybackSource("s2", PlaybackSourceType.IPTV, "S2", "https://example.com/2.m3u8")

    private fun context(
        error: PlaybackError,
        policy: AutoFallbackPolicy = AutoFallbackPolicy.AUTO_FALLBACK_LIMITED,
        consecutiveFailureCount: Int = 0,
        mimeRetryIndex: Int = 0,
        mimeRetrySequenceSize: Int = 0,
        vlcAlreadyAttemptedForSource: Boolean = false,
        remainingCandidates: List<PlaybackSource> = listOf(source1, source2),
        fallbackAllowed: Boolean = true,
        safeProfileRetryEnabled: Boolean = false,
        safeProfileAlreadyRetried: Boolean = false,
        vlcRuntimeAvailable: Boolean = true,
        allowVlcInThisEnvironment: Boolean = true,
    ) = RecoveryContext(
        error = error,
        currentSource = source1,
        policy = policy,
        consecutiveFailureCount = consecutiveFailureCount,
        mimeRetryIndex = mimeRetryIndex,
        mimeRetrySequenceSize = mimeRetrySequenceSize,
        vlcAlreadyAttemptedForSource = vlcAlreadyAttemptedForSource,
        remainingCandidates = remainingCandidates,
        fallbackAllowed = fallbackAllowed,
        safeProfileRetryEnabled = safeProfileRetryEnabled,
        safeProfileAlreadyRetried = safeProfileAlreadyRetried,
        vlcRuntimeAvailable = vlcRuntimeAvailable,
        allowVlcInThisEnvironment = allowVlcInThisEnvironment,
    )

    @Test
    fun `unsupported format with remaining mime hints chooses TryMimeRetry`() {
        val decision = PlaybackRecoveryEngine.decide(
            context(
                error = PlaybackError.UnsupportedFormat(),
                mimeRetryIndex = 0,
                mimeRetrySequenceSize = 3,
            )
        )
        assertEquals(RecoveryAction.TryMimeRetry, decision.action)
        assertFalse(decision.recordSourceFailure)
    }

    @Test
    fun `timeout with vlc available chooses SwitchToVlc`() {
        val decision = PlaybackRecoveryEngine.decide(
            context(error = PlaybackError.Timeout())
        )
        assertEquals(RecoveryAction.SwitchToVlc, decision.action)
        assertFalse(decision.recordSourceFailure)
    }

    @Test
    fun `timeout skips vlc when already attempted for source`() {
        val decision = PlaybackRecoveryEngine.decide(
            context(
                error = PlaybackError.Timeout(),
                vlcAlreadyAttemptedForSource = true,
            )
        )
        assertTrue(decision.action is RecoveryAction.AutoFallback)
        assertTrue(decision.recordSourceFailure)
    }

    @Test
    fun `decoder error with safe profile enabled chooses RetrySafeProfile`() {
        val decision = PlaybackRecoveryEngine.decide(
            context(
                error = PlaybackError.DecoderError(),
                safeProfileRetryEnabled = true,
                vlcRuntimeAvailable = false,
            )
        )
        assertEquals(RecoveryAction.RetrySafeProfile, decision.action)
        assertFalse(decision.recordSourceFailure)
    }

    @Test
    fun `decoder error with remaining candidates chooses vlc for audio codec failures`() {
        val audioEx = androidx.media3.common.PlaybackException(
            "Audio track init failed",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        )
        val decision = PlaybackRecoveryEngine.decide(
            context(
                error = PlaybackError.DecoderError(cause = audioEx),
                remainingCandidates = listOf(source2),
                vlcRuntimeAvailable = true,
            )
        )
        assertEquals(RecoveryAction.SwitchToVlc, decision.action)
    }

    @Test
    fun `decoder error with remaining candidates skips vlc for video codec failures`() {
        val decision = PlaybackRecoveryEngine.decide(
            context(
                error = PlaybackError.DecoderError(),
                remainingCandidates = listOf(source2),
                vlcRuntimeAvailable = true,
            )
        )
        assertTrue(decision.action is RecoveryAction.AutoFallback)
    }

    @Test
    fun `decoder error with no remaining candidates chooses vlc`() {
        val decision = PlaybackRecoveryEngine.decide(
            context(
                error = PlaybackError.DecoderError(),
                remainingCandidates = emptyList(),
            )
        )
        assertEquals(RecoveryAction.SwitchToVlc, decision.action)
    }

    @Test
    fun `four prior failures chooses AutoFallback on fifth`() {
        val decision = PlaybackRecoveryEngine.decide(
            context(
                error = PlaybackError.Timeout(),
                consecutiveFailureCount = 3,
                vlcAlreadyAttemptedForSource = true,
            )
        )
        assertTrue(decision.action is RecoveryAction.AutoFallback)
        assertTrue(decision.recordSourceFailure)
    }

    @Test
    fun `fifth consecutive failure chooses Terminal`() {
        val decision = PlaybackRecoveryEngine.decide(
            context(
                error = PlaybackError.Timeout(),
                consecutiveFailureCount = 4,
                vlcAlreadyAttemptedForSource = true,
            )
        )
        assertTrue(decision.action is RecoveryAction.Terminal)
        assertTrue(decision.recordSourceFailure)
    }

    @Test
    fun `ask before fallback chooses ShowFallbackPrompt`() {
        val error = PlaybackError.Network()
        val decision = PlaybackRecoveryEngine.decide(
            context(
                error = error,
                policy = AutoFallbackPolicy.ASK_BEFORE_FALLBACK,
                vlcAlreadyAttemptedForSource = true,
            )
        )
        assertEquals(RecoveryAction.ShowFallbackPrompt(error), decision.action)
        assertTrue(decision.recordSourceFailure)
    }

    @Test
    fun `auto fallback limited uses offline message for timeout`() {
        val decision = PlaybackRecoveryEngine.decide(
            context(
                error = PlaybackError.Timeout(),
                vlcAlreadyAttemptedForSource = true,
            )
        )
        val action = decision.action as RecoveryAction.AutoFallback
        assertEquals("Source offline. Trying alternative track...", action.message)
    }

    @Test
    fun `auto fallback limited uses incompatible message for decoder error`() {
        val decision = PlaybackRecoveryEngine.decide(
            context(
                error = PlaybackError.DecoderError(),
                vlcAlreadyAttemptedForSource = true,
                remainingCandidates = listOf(source2),
            )
        )
        val action = decision.action as RecoveryAction.AutoFallback
        assertEquals("Source incompatible. Trying alternative track...", action.message)
    }

    @Test
    fun `fallback not allowed enriches unsupported format with remaining candidates`() {
        val remaining = listOf(source2)
        val decision = PlaybackRecoveryEngine.decide(
            context(
                error = PlaybackError.UnsupportedFormat(),
                mimeRetryIndex = 2,
                mimeRetrySequenceSize = 2,
                fallbackAllowed = false,
                vlcAlreadyAttemptedForSource = true,
                remainingCandidates = remaining,
            )
        )
        val action = decision.action as RecoveryAction.FailInPlace
        val enriched = action.error as PlaybackError.UnsupportedFormat
        assertEquals(remaining, enriched.retryableSources)
    }

    @Test
    fun `no active source returns FailWithoutSource`() {
        val error = PlaybackError.Timeout()
        val decision = PlaybackRecoveryEngine.decideWithoutSource(error)
        assertEquals(RecoveryAction.FailWithoutSource(error), decision.action)
    }

    @Test
    fun `mock environment disables vlc even when runtime available`() {
        val decision = PlaybackRecoveryEngine.decide(
            context(
                error = PlaybackError.Timeout(),
                allowVlcInThisEnvironment = false,
            )
        )
        assertTrue(decision.action is RecoveryAction.AutoFallback)
    }
}
