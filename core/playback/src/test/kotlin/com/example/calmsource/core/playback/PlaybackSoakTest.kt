package com.example.calmsource.core.playback

import androidx.media3.common.PlaybackException
import com.example.calmsource.core.model.AutoFallbackPolicy
import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.PlayerState
import com.example.calmsource.core.playback.diagnostics.PlaybackDiagnosticsRecorder
import com.example.calmsource.core.playback.support.HarnessOptions
import com.example.calmsource.core.playback.support.PlaybackManagerTestHarness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Longer chained scenarios that approximate device soak patterns: rapid session churn,
 * fallback exhaustion, and recovery event timelines.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSoakTest {
    private val harness = PlaybackManagerTestHarness()

    private val source1 = PlaybackSource("src1", PlaybackSourceType.IPTV, "Source 1", "https://example.com/src1.m3u8")
    private val source2 = PlaybackSource("src2", PlaybackSourceType.IPTV, "Source 2", "https://example.com/src2.m3u8")
    private val source3 = PlaybackSource("src3", PlaybackSourceType.IPTV, "Source 3", "https://example.com/src3.m3u8")

    @Before
    fun setup() {
        harness.setUp(HarnessOptions(fallbackPolicy = AutoFallbackPolicy.AUTO_FALLBACK_LIMITED, vlcAvailable = false))
        PlaybackDiagnosticsRecorder.clearForTests()
        VlcPlayerBackend.isAvailableOverride = false
    }

    @After
    fun teardown() {
        harness.tearDown()
    }

    @Test
    fun soakRapidPrepareChurnKeepsLatestSessionEvents() = harness.testScope.runTest {
        harness.createManager(harness.testScope.backgroundScope, HarnessOptions(progressUpdates = false))
        repeat(5) { index ->
            val source = PlaybackSource("zap-$index", PlaybackSourceType.IPTV, "Zap $index", "https://example.com/zap$index.m3u8")
            harness.manager.prepare(PlaybackRequest(source = source))
            harness.scheduler.advanceUntilIdle()
        }
        val events = PlaybackDiagnosticsRecorder.recentEvents()
        assertTrue(events.count { it.kind == "prepare" } >= 5)
        assertEquals("zap-4", harness.manager.uiState.value.sessionDiagnostics.sourceId)
    }

    @Test
    fun soakFallbackChainRecordsTerminalTimeline() = harness.testScope.runTest {
        harness.createManager(harness.testScope.backgroundScope, HarnessOptions(progressUpdates = false))
        harness.manager.prepare(
            PlaybackRequest(source = source1, playWhenReady = true),
            fallbackCandidates = listOf(source1, source2, source3),
        )
        harness.scheduler.advanceUntilIdle()

        repeat(3) {
            harness.playerListener.onPlayerError(
                PlaybackException(
                    "fail",
                    null,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                )
            )
            harness.runFallbackTasks()
            advanceTimeBy(16_000)
            harness.scheduler.advanceUntilIdle()
        }

        val kinds = PlaybackDiagnosticsRecorder.recentEvents().map { it.kind }
        assertTrue(kinds.contains("prepare"))
        assertTrue(kinds.any { it == "auto_fallback" || it == "fail_in_place" || it == "terminal" })
        assertTrue(
            harness.manager.uiState.value.playerState == PlayerState.FAILED ||
                harness.manager.uiState.value.isTransitioningSource
        )
    }
}
