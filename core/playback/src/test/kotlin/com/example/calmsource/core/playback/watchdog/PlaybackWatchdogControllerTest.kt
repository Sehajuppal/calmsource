package com.example.calmsource.core.playback.watchdog

import com.example.calmsource.core.model.PlaybackError
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackWatchdogControllerTest {

    private val source = PlaybackSource("s1", PlaybackSourceType.IPTV, "S1", "https://example.com/s.m3u8")
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startup timeout reports failure when session remains current`() = testScope.runTest {
        var sessionId = 1L
        val failures = mutableListOf<String>()
        val controller = controller(
            sessionId = { sessionId },
            isSessionCurrent = { id -> id == sessionId },
            onFailure = { _, code -> failures += code },
            startupTimeoutMs = { 15_000L },
            playerState = { PlayerState.PREPARING },
        )

        controller.onUiStateChanged(PlayerState.PREPARING)
        advanceTimeBy(15_001)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("PLAYBACK_TIMEOUT"), failures)
    }

    @Test
    fun `startup timeout is ignored after session superseded`() = testScope.runTest {
        var sessionId = 1L
        val failures = mutableListOf<String>()
        val controller = controller(
            sessionId = { sessionId },
            isSessionCurrent = { id -> id == sessionId },
            onFailure = { _, code -> failures += code },
            startupTimeoutMs = { 15_000L },
            playerState = { PlayerState.PREPARING },
        )

        controller.onUiStateChanged(PlayerState.PREPARING)
        sessionId = 2
        advanceTimeBy(15_001)
        testScheduler.advanceUntilIdle()

        assertTrue(failures.isEmpty())
    }

    @Test
    fun `success debounce records health after five seconds playing`() = testScope.runTest {
        var recorded: PlaybackSource? = null
        val controller = controller(
            onRecordSuccess = { recorded = it },
            playerState = { PlayerState.PLAYING },
        )

        controller.onUiStateChanged(PlayerState.PLAYING)
        advanceTimeBy(5_001)
        testScheduler.advanceUntilIdle()

        assertEquals(source, recorded)
        assertTrue(controller.successRecorded)
    }

    @Test
    fun `buffering stall fires when still buffering without first frame`() = testScope.runTest {
        val failures = mutableListOf<String>()
        var hasFirstFrame = false
        val controller = controller(
            onFailure = { _, code -> failures += code },
            playerState = { PlayerState.BUFFERING },
            hasRenderedFirstFrame = { hasFirstFrame },
            bufferingStallDelayMs = { 15_000L },
        )

        controller.startBufferingWatchdog()
        advanceTimeBy(15_001)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("BUFFERING_STALL"), failures)

        hasFirstFrame = true
        failures.clear()
        controller.startBufferingWatchdog()
        advanceTimeBy(15_001)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("MIDSTREAM_BUFFERING_STALL"), failures)
    }

    @Test
    fun `cancelAll disarms pending startup timeout`() = testScope.runTest {
        val failures = mutableListOf<String>()
        val controller = controller(
            onFailure = { _, code -> failures += code },
            startupTimeoutMs = { 15_000L },
            playerState = { PlayerState.BUFFERING },
        )

        controller.onUiStateChanged(PlayerState.BUFFERING)
        controller.cancelAll()
        advanceTimeBy(15_001)
        testScheduler.advanceUntilIdle()

        assertTrue(failures.isEmpty())
    }

    @Test
    fun `freeze watchdog fires when first frame never renders`() = testScope.runTest {
        val failures = mutableListOf<String>()
        val controller = controller(
            onFailure = { _, code -> failures += code },
            shouldScheduleFreezeWatchdog = { true },
            isFreezeWatchdogStillValid = { true },
        )

        controller.scheduleFreezeWatchdogIfNeeded()
        advanceTimeBy(4_001)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("VIDEO_FREEZE"), failures)

        failures.clear()
        val ignored = controller(
            onFailure = { _, code -> failures += code },
            shouldScheduleFreezeWatchdog = { true },
            isFreezeWatchdogStillValid = { false },
        )
        ignored.scheduleFreezeWatchdogIfNeeded()
        advanceTimeBy(4_001)
        testScheduler.advanceUntilIdle()
        assertTrue(failures.isEmpty())
    }

    private fun controller(
        sessionId: () -> Long = { 1L },
        isSessionCurrent: (Long) -> Boolean = { true },
        onFailure: (PlaybackError, String) -> Unit = { _, _ -> },
        onRecordSuccess: (PlaybackSource) -> Unit = { },
        startupTimeoutMs: () -> Long = { 15_000L },
        bufferingStallDelayMs: () -> Long = { 15_000L },
        playerState: () -> PlayerState = { PlayerState.IDLE },
        hasRenderedFirstFrame: () -> Boolean = { false },
        shouldScheduleFreezeWatchdog: () -> Boolean = { false },
        isFreezeWatchdogStillValid: () -> Boolean = { false },
    ): PlaybackWatchdogController {
        return PlaybackWatchdogController(
            scope = testScope.backgroundScope,
            currentSessionId = sessionId,
            isSessionCurrent = isSessionCurrent,
            onFailure = onFailure,
            onRecordSuccess = onRecordSuccess,
            startupTimeoutMs = startupTimeoutMs,
            bufferingStallDelayMs = bufferingStallDelayMs,
            currentPlayerState = playerState,
            currentSource = { source },
            hasRenderedFirstFrame = hasRenderedFirstFrame,
            shouldScheduleFreezeWatchdog = shouldScheduleFreezeWatchdog,
            isFreezeWatchdogStillValid = isFreezeWatchdogStillValid,
        )
    }
}
