package com.example.calmsource.core.playback

import androidx.media3.common.Player
import com.example.calmsource.core.database.SourceHealthRepository
import com.example.calmsource.core.model.AutoFallbackPolicy
import com.example.calmsource.core.model.PlaybackError
import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.PlayerState
import com.example.calmsource.core.playback.support.HarnessOptions
import com.example.calmsource.core.playback.support.PlaybackManagerTestHarness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Integration-style tests for health recording, fallback candidate selection, and policy
 * enforcement. Uses [PlaybackManagerTestHarness] with progress polling disabled so coroutine
 * tests complete deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackFallbackTest {
    private val harness = PlaybackManagerTestHarness()

    private val source1 = PlaybackSource("src1", PlaybackSourceType.EXTENSION, "Source 1", "https://example.com/src1.m3u8")
    private val source2 = PlaybackSource("src2", PlaybackSourceType.EXTENSION, "Source 2", "https://example.com/src2.m3u8")
    private val source3 = PlaybackSource("src3", PlaybackSourceType.EXTENSION, "Source 3", "https://example.com/src3.m3u8")

    @Before
    fun setup() {
        harness.setUp(HarnessOptions(fallbackPolicy = AutoFallbackPolicy.ASK_BEFORE_FALLBACK, progressUpdates = false))
    }

    @After
    fun tearDown() {
        harness.tearDown()
    }

    private fun initManager(policy: AutoFallbackPolicy = FallbackPreferences.policy) {
        harness.createManager(
            harness.testScope.backgroundScope,
            HarnessOptions(fallbackPolicy = policy, progressUpdates = false),
        )
    }

    @Test
    fun `playback success is recorded to repository after 5 seconds in PLAYING state`() = harness.testScope.runTest {
        initManager()
        harness.manager.prepare(PlaybackRequest(source = source1, playWhenReady = true))
        harness.scheduler.advanceUntilIdle()

        harness.playerListener.onPlaybackStateChanged(Player.STATE_READY)
        harness.scheduler.advanceUntilIdle()
        assertEquals(PlayerState.PLAYING, harness.manager.uiState.value.playerState)

        var health = SourceHealthRepository.getSourceHealth(source1.safeSourceId)
        assertNull(health)

        advanceTimeBy(5001)
        harness.scheduler.advanceUntilIdle()

        health = SourceHealthRepository.getSourceHealth(source1.safeSourceId)
        assertNotNull(health)
        assertEquals(100, health?.healthScore)
        assertTrue(health?.lastSuccessTime ?: 0L > 0L)
        assertEquals(0, health?.failureCount)

        harness.manager.release()
    }

    @Test
    fun `playback failure is recorded to repository when ExoPlayer fires error`() = harness.testScope.runTest {
        initManager(AutoFallbackPolicy.OFF)
        harness.manager.prepare(PlaybackRequest(source = source1, playWhenReady = true))
        harness.scheduler.advanceUntilIdle()

        harness.fireNetworkError()

        assertEquals(PlayerState.FAILED, harness.manager.uiState.value.playerState)
        assertTrue(harness.manager.uiState.value.error is PlaybackError.Network)

        val health = SourceHealthRepository.getSourceHealth(source1.safeSourceId)
        assertNotNull(health)
        assertEquals(80, health?.healthScore)
        assertEquals(1, health?.failureCount)
        assertEquals("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", health?.lastErrorCategory)

        harness.manager.release()
    }

    @Test
    fun `playback timeout is triggered and recorded after 15 seconds in PREPARING state`() = harness.testScope.runTest {
        initManager()
        harness.manager.prepare(PlaybackRequest(source = source1, playWhenReady = true))
        harness.scheduler.advanceUntilIdle()

        assertEquals(PlayerState.PREPARING, harness.manager.uiState.value.playerState)
        assertNull(SourceHealthRepository.getSourceHealth(source1.safeSourceId))

        advanceTimeBy(14000)
        harness.scheduler.advanceUntilIdle()
        assertEquals(PlayerState.PREPARING, harness.manager.uiState.value.playerState)

        advanceTimeBy(1100)
        harness.scheduler.advanceUntilIdle()

        assertEquals(PlayerState.FAILED, harness.manager.uiState.value.playerState)
        assertTrue(harness.manager.uiState.value.error is PlaybackError.Timeout)

        val health = SourceHealthRepository.getSourceHealth(source1.safeSourceId)
        assertNotNull(health)
        assertEquals(80, health?.healthScore)
        assertEquals(1, health?.failureCount)
        assertEquals("PLAYBACK_TIMEOUT", health?.lastErrorCategory)

        harness.manager.release()
    }

    @Test
    fun `failed source is downranked and next best candidate by health is selected`() = harness.testScope.runTest {
        initManager(AutoFallbackPolicy.AUTO_FALLBACK_LIMITED)

        SourceHealthRepository.recordFailure(source1.safeSourceId, "p", PlaybackSourceType.IPTV)
        SourceHealthRepository.recordFailure(source2.safeSourceId, "p", PlaybackSourceType.IPTV)
        SourceHealthRepository.recordFailure(source2.safeSourceId, "p", PlaybackSourceType.IPTV)
        SourceHealthRepository.recordSuccess(source3.safeSourceId, "p", PlaybackSourceType.IPTV)
        harness.scheduler.advanceUntilIdle()

        harness.manager.prepare(
            request = PlaybackRequest(source = source2, playWhenReady = true),
            fallbackCandidates = listOf(source1, source2, source3)
        )
        harness.scheduler.advanceUntilIdle()

        harness.fireNetworkError()
        harness.runFallbackTasks()

        assertEquals(source3.id, harness.manager.uiState.value.source?.id)
        assertEquals(PlayerState.PREPARING, harness.manager.uiState.value.playerState)

        harness.manager.release()
    }

    @Test
    fun `auto fallback once policy limits attempts to 1`() = harness.testScope.runTest {
        initManager(AutoFallbackPolicy.AUTO_FALLBACK_ONCE)

        harness.manager.prepare(
            request = PlaybackRequest(source = source1, playWhenReady = true),
            fallbackCandidates = listOf(source1, source2, source3)
        )
        harness.scheduler.advanceUntilIdle()

        harness.fireNetworkError()
        harness.runFallbackTasks()

        val secondSource = harness.manager.uiState.value.source
        assertNotNull(secondSource)
        assertTrue(secondSource?.id != source1.id)

        harness.fireNetworkError()
        harness.runFallbackTasks()

        assertEquals(PlayerState.FAILED, harness.manager.uiState.value.playerState)
        assertEquals(secondSource?.id, harness.manager.uiState.value.source?.id)

        harness.manager.release()
    }

    @Test
    fun `auto fallback until playable policy limits attempts to number of candidates or max 5`() = harness.testScope.runTest {
        initManager(AutoFallbackPolicy.AUTO_FALLBACK_LIMITED)

        harness.manager.prepare(
            request = PlaybackRequest(source = source1, playWhenReady = true),
            fallbackCandidates = listOf(source1, source2)
        )
        harness.scheduler.advanceUntilIdle()

        harness.fireNetworkError()
        harness.runFallbackTasks()
        assertEquals(source2.id, harness.manager.uiState.value.source?.id)

        harness.fireNetworkError()
        harness.runFallbackTasks()

        assertEquals(PlayerState.FAILED, harness.manager.uiState.value.playerState)
        assertEquals(source2.id, harness.manager.uiState.value.source?.id)

        harness.manager.release()
    }

    @Test
    fun `ask before fallback policy displays prompt and triggers fallback on user selection`() = harness.testScope.runTest {
        initManager(AutoFallbackPolicy.ASK_BEFORE_FALLBACK)

        harness.manager.prepare(
            request = PlaybackRequest(source = source1, playWhenReady = true),
            fallbackCandidates = listOf(source1, source2)
        )
        harness.scheduler.advanceUntilIdle()

        harness.fireNetworkError()
        harness.runFallbackTasks()

        assertEquals(PlayerState.FAILED, harness.manager.uiState.value.playerState)
        assertTrue(harness.manager.fallbackPromptState.value)

        harness.manager.onUserSelectTryNextBest()
        harness.runFallbackTasks()

        assertFalse(harness.manager.fallbackPromptState.value)
        assertEquals(source2.id, harness.manager.uiState.value.source?.id)
        assertEquals(PlayerState.PREPARING, harness.manager.uiState.value.playerState)

        harness.manager.release()
    }

    @Test
    fun `playerFlow emits null upon release`() = harness.testScope.runTest {
        initManager()
        harness.manager.prepare(
            request = PlaybackRequest(source = source1, playWhenReady = true),
            fallbackCandidates = emptyList()
        )
        harness.scheduler.advanceUntilIdle()

        assertNotNull(harness.manager.playerFlow.value)
        harness.manager.release()
        harness.scheduler.advanceUntilIdle()
        assertNull(harness.manager.playerFlow.value)
    }

    @Test
    fun `stateTrackingJob restarts on prepare after being released`() = harness.testScope.runTest {
        initManager()
        harness.manager.prepare(
            request = PlaybackRequest(source = source1, playWhenReady = true),
            fallbackCandidates = emptyList()
        )
        harness.scheduler.advanceUntilIdle()

        val jobField = PlaybackManager::class.java.getDeclaredField("stateTrackingJob")
        jobField.isAccessible = true
        var job = jobField.get(harness.manager) as kotlinx.coroutines.Job?
        assertNotNull(job)
        assertTrue(job!!.isActive)

        harness.manager.release()
        harness.scheduler.advanceUntilIdle()
        assertNull(jobField.get(harness.manager))

        harness.injectPlayerAfterRelease()
        harness.manager.prepare(
            request = PlaybackRequest(source = source1, playWhenReady = true),
            fallbackCandidates = emptyList()
        )
        harness.scheduler.advanceUntilIdle()

        job = jobField.get(harness.manager) as kotlinx.coroutines.Job?
        assertNotNull(job)
        assertTrue(job!!.isActive)

        harness.manager.release()
    }

    @Test
    fun `executeFallback after release does not re-prepare player`() = harness.testScope.runTest {
        initManager(AutoFallbackPolicy.AUTO_FALLBACK_ONCE)

        harness.manager.prepare(
            request = PlaybackRequest(source = source1, playWhenReady = true),
            fallbackCandidates = listOf(source1, source2)
        )
        harness.scheduler.advanceUntilIdle()

        harness.fireNetworkError()
        harness.manager.release()
        harness.runFallbackTasks()

        assertEquals(PlayerState.IDLE, harness.manager.uiState.value.playerState)
        assertNull(harness.manager.player)
    }

    @Test
    fun `isFallbackAllowed returns false when candidates are empty even for ASK_BEFORE_FALLBACK`() {
        val manager = FallbackManager()
        manager.reset(listOf(source1))
        manager.markFailed(source1.id)

        val allowed = manager.isFallbackAllowed(AutoFallbackPolicy.ASK_BEFORE_FALLBACK)
        assertFalse("Fallback should not be allowed if no candidates remain", allowed)
    }
}
