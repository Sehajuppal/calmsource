package com.example.calmsource.core.playback

import androidx.media3.common.Player
import com.example.calmsource.core.model.AutoFallbackPolicy
import com.example.calmsource.core.model.PlaybackError
import com.example.calmsource.core.model.PlaybackItemMetadata
import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.PlayerState
import com.example.calmsource.core.playback.session.FakeStreamRaceFactory
import com.example.calmsource.core.playback.support.FakePlayerBackend
import com.example.calmsource.core.playback.support.HarnessOptions
import com.example.calmsource.core.playback.support.PlaybackManagerTestHarness
import com.example.calmsource.core.playback.support.extensionlessSource
import com.example.calmsource.core.playback.support.liveSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.whenever

/**
 * Chaos/regression tests for the playback state machine:
 *  - fallback chain must reach FAILED instead of hanging (#7),
 *  - startup timeouts must not reset the circuit breaker (#1),
 *  - backend BUFFERING must not wipe failure errors (#2),
 *  - stale async work must respect session ids (#4),
 *  - recovery chain MIME → VLC → source fallback behaves under stress.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackChaosTest {
    private val harness = PlaybackManagerTestHarness()

    private val source1 = PlaybackSource("src1", PlaybackSourceType.IPTV, "Source 1", "https://example.com/src1.m3u8")
    private val source2 = PlaybackSource("src2", PlaybackSourceType.IPTV, "Source 2", "https://example.com/src2.m3u8")

    @Before
    fun setup() {
        harness.setUp(
            HarnessOptions(
                fallbackPolicy = AutoFallbackPolicy.AUTO_FALLBACK_ONCE,
                vlcAvailable = false,
            )
        )
        VlcPlayerBackend.isAvailableOverride = false
    }

    @After
    fun teardown() {
        harness.tearDown()
    }

    private fun initManager(options: HarnessOptions = HarnessOptions(progressUpdates = false)) {
        harness.createManager(harness.testScope.backgroundScope, options)
    }

    @Test
    fun testFallbackTimeoutHang() = harness.testScope.runTest {
        initManager()
        harness.manager.prepare(
            request = PlaybackRequest(source = source1, playWhenReady = true),
            fallbackCandidates = listOf(source1, source2)
        )
        harness.scheduler.advanceUntilIdle()
        assertEquals(PlayerState.PREPARING, harness.manager.uiState.value.playerState)

        advanceTimeBy(15_001)
        harness.scheduler.advanceUntilIdle()
        assertEquals(PlayerState.PREPARING, harness.manager.uiState.value.playerState)
        assertEquals(source2.id, harness.manager.uiState.value.source?.id)

        advanceTimeBy(15_001)
        harness.scheduler.advanceUntilIdle()
        assertEquals(
            "Second source timeout must reach FAILED (no infinite PREPARING hang)",
            PlayerState.FAILED,
            harness.manager.uiState.value.playerState
        )
        assertTrue(harness.manager.uiState.value.error is PlaybackError.Timeout)
    }

    @Test
    fun `startup timeouts accumulate the circuit breaker instead of resetting it`() = harness.testScope.runTest {
        initManager(HarnessOptions(fallbackPolicy = AutoFallbackPolicy.AUTO_FALLBACK_LIMITED, progressUpdates = false))
        val candidates = (1..6).map {
            PlaybackSource("c$it", PlaybackSourceType.IPTV, "C$it", "https://example.com/c$it.m3u8")
        }
        harness.manager.prepare(
            request = PlaybackRequest(source = candidates.first(), playWhenReady = true),
            fallbackCandidates = candidates
        )
        harness.scheduler.advanceUntilIdle()

        repeat(5) {
            advanceTimeBy(15_001)
            harness.scheduler.advanceUntilIdle()
        }

        val state = harness.manager.uiState.value
        assertEquals(PlayerState.FAILED, state.playerState)
        assertTrue("Five consecutive timeouts should trip the terminal breaker", state.isTerminal)
        assertTrue(state.error is PlaybackError.TerminalError)
    }

    @Test
    fun `backend buffering emission does not clear the failure error`() = harness.testScope.runTest {
        initManager(HarnessOptions(fallbackPolicy = AutoFallbackPolicy.OFF, progressUpdates = false))
        harness.manager.prepare(PlaybackRequest(source = source1, playWhenReady = true))
        harness.scheduler.advanceUntilIdle()

        advanceTimeBy(15_001)
        harness.scheduler.advanceUntilIdle()
        assertEquals(PlayerState.FAILED, harness.manager.uiState.value.playerState)
        assertNotNull(harness.manager.uiState.value.error)

        whenever(harness.mockPlayer.playbackState).thenReturn(Player.STATE_BUFFERING)
        harness.playerListener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        harness.scheduler.advanceUntilIdle()

        assertNotNull(
            "A null-error BUFFERING emission must not wipe the failure error (#2)",
            harness.manager.uiState.value.error
        )
        assertTrue(harness.manager.uiState.value.error is PlaybackError.Timeout)
    }

    @Test
    fun `duplicate backend FAILED emission does not double-count the circuit breaker`() = harness.testScope.runTest {
        initManager(HarnessOptions(fallbackPolicy = AutoFallbackPolicy.OFF, progressUpdates = false))
        harness.manager.prepare(PlaybackRequest(source = source1, playWhenReady = true))
        harness.scheduler.advanceUntilIdle()

        advanceTimeBy(15_001)
        harness.scheduler.advanceUntilIdle()

        val countField = PlaybackManager::class.java.getDeclaredField("consecutiveFallbackCount")
        countField.isAccessible = true
        assertEquals(1, countField.getInt(harness.manager))

        val backend = harness.manager.currentBackend ?: error("expected active backend")
        val stateField = backend.javaClass.getDeclaredField("_state")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(backend) as MutableStateFlow<PlayerBackendState>
        val handledError = stateFlow.value.error ?: error("expected backend failure error")

        stateFlow.value = stateFlow.value.copy(playerState = PlayerState.FAILED, error = handledError)
        harness.scheduler.advanceUntilIdle()

        assertEquals(1, countField.getInt(harness.manager))
        assertEquals(source1.id, harness.manager.uiState.value.source?.id)
    }

    @Test
    fun `stopEngine does not reset consecutive fallback count`() = harness.testScope.runTest {
        initManager(HarnessOptions(fallbackPolicy = AutoFallbackPolicy.OFF, progressUpdates = false))
        harness.manager.prepare(PlaybackRequest(source = source1, playWhenReady = true))
        harness.scheduler.advanceUntilIdle()
        advanceTimeBy(15_001)
        harness.scheduler.advanceUntilIdle()

        val countField = PlaybackManager::class.java.getDeclaredField("consecutiveFallbackCount")
        countField.isAccessible = true
        val countAfterTimeout = countField.getInt(harness.manager)
        assertTrue(countAfterTimeout >= 1)

        harness.manager.stopEngine(resetCircuitBreaker = false)
        assertEquals(countAfterTimeout, countField.getInt(harness.manager))

        harness.manager.stop()
        assertEquals(0, countField.getInt(harness.manager))
    }

    @Test
    fun `stale stream race completion is ignored after release`() = harness.testScope.runTest {
        harness.replaceContext(highMemoryDevice = true, streamRacing = true)
        initManager(HarnessOptions(highMemoryDevice = true, streamRacing = true, progressUpdates = false))

        val channelA = PlaybackSource("race-a", PlaybackSourceType.IPTV, "A", "https://example.com/a.m3u8")
        val channelB = PlaybackSource("race-b", PlaybackSourceType.IPTV, "B", "https://example.com/b.m3u8")

        harness.manager.prepare(
            request = PlaybackRequest(source = channelA, playWhenReady = true),
            fallbackCandidates = listOf(channelA, channelB)
        )
        harness.manager.release()
        advanceTimeBy(8_500)
        harness.scheduler.advanceUntilIdle()

        assertEquals(PlayerState.IDLE, harness.manager.uiState.value.playerState)
        assertEquals(null, harness.manager.currentBackend)
    }

    @Test
    fun `stale stream race winner is ignored after a newer prepare`() = harness.testScope.runTest {
        harness.replaceContext(highMemoryDevice = true, streamRacing = true)
        initManager(HarnessOptions(highMemoryDevice = true, streamRacing = true, progressUpdates = false))

        val channelA = PlaybackSource("race-a", PlaybackSourceType.IPTV, "A", "https://example.com/a.m3u8")
        val channelB = PlaybackSource("race-b", PlaybackSourceType.IPTV, "B", "https://example.com/b.m3u8")
        val channelC = PlaybackSource("zap-c", PlaybackSourceType.IPTV, "C", "https://example.com/c.m3u8")

        harness.manager.streamRaceFactory = FakeStreamRaceFactory(delayMs = 5_000) {
            StreamRaceResult.Winner(
                source = channelA,
                attemptedSourceIds = listOf(channelA.id, channelB.id),
                telemetry = emptyList(),
            )
        }

        harness.manager.prepare(
            request = PlaybackRequest(source = channelA, playWhenReady = true),
            fallbackCandidates = listOf(channelA, channelB)
        )
        harness.manager.prepare(PlaybackRequest(source = channelC, playWhenReady = true))
        advanceTimeBy(5_001)
        harness.scheduler.advanceUntilIdle()

        assertEquals(channelC.id, harness.manager.uiState.value.source?.id)
    }

    // ─── PR-4 chaos expansion ───

    @Test
    fun `unsupported format retries the next mime hint before failing`() = harness.testScope.runTest {
        val stream = extensionlessSource("ext-1")
        initManager(HarnessOptions(fallbackPolicy = AutoFallbackPolicy.OFF, progressUpdates = false))

        harness.manager.prepare(PlaybackRequest(source = stream, playWhenReady = true))
        harness.scheduler.advanceUntilIdle()
        assertEquals(0, harness.mimeRetryIndex())

        harness.fireUnsupportedFormatError()
        assertEquals(1, harness.mimeRetryIndex())
        assertEquals(PlayerState.PREPARING, harness.manager.uiState.value.playerState)
    }

    @Test
    fun `mime retries exhausted switches to injectable vlc backend`() = harness.testScope.runTest {
        val stream = extensionlessSource("ext-vlc")
        val fakeBackend = FakePlayerBackend()
        initManager(
            HarnessOptions(
                fallbackPolicy = AutoFallbackPolicy.OFF,
                progressUpdates = false,
                vlcAvailable = true,
                allowVlcInRecovery = true,
                vlcBackendFactory = { fakeBackend },
            )
        )

        harness.manager.prepare(PlaybackRequest(source = stream, playWhenReady = true))
        harness.scheduler.advanceUntilIdle()

        repeat(4) { harness.fireUnsupportedFormatError() }
        assertEquals(4, harness.mimeRetryIndex())

        harness.fireUnsupportedFormatError()
        assertTrue(harness.manager.currentBackend === fakeBackend)
        assertEquals(1, fakeBackend.prepareCount)
    }

    @Test
    fun `vlc backend failure advances to the next fallback source`() = harness.testScope.runTest {
        val stream = extensionlessSource("ext-vlc-fail")
        val fakeBackend = FakePlayerBackend()
        initManager(
            HarnessOptions(
                fallbackPolicy = AutoFallbackPolicy.AUTO_FALLBACK_ONCE,
                progressUpdates = false,
                vlcAvailable = true,
                allowVlcInRecovery = true,
                vlcBackendFactory = { fakeBackend },
            )
        )

        harness.manager.prepare(
            request = PlaybackRequest(source = stream, playWhenReady = true),
            fallbackCandidates = listOf(stream, source2)
        )
        harness.scheduler.advanceUntilIdle()

        repeat(4) { harness.fireUnsupportedFormatError() }
        harness.fireUnsupportedFormatError()
        assertTrue(harness.manager.currentBackend === fakeBackend)

        fakeBackend.emitFailed(
            PlaybackError.Network(cause = Exception("VLC network failure")),
            rawErrorCode = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"
        )
        harness.runFallbackTasks()

        assertEquals(source2.id, harness.manager.uiState.value.source?.id)
        assertEquals(PlayerState.PREPARING, harness.manager.uiState.value.playerState)
    }

    @Test
    fun `decoder error with remaining candidates skips vlc and auto-falls back`() = harness.testScope.runTest {
        val hevcSource = PlaybackSource(
            id = "hevc-1",
            type = PlaybackSourceType.IPTV,
            title = "HEVC",
            rawUrl = "https://example.com/hevc.m3u8",
            metadata = PlaybackItemMetadata(title = "HEVC", videoCodec = "hevc"),
        )
        val h264Source = PlaybackSource(
            id = "h264-1",
            type = PlaybackSourceType.IPTV,
            title = "H264",
            rawUrl = "https://example.com/h264.m3u8",
            metadata = PlaybackItemMetadata(title = "H264", videoCodec = "h264"),
        )
        initManager(
            HarnessOptions(
                fallbackPolicy = AutoFallbackPolicy.AUTO_FALLBACK_ONCE,
                progressUpdates = false,
                vlcAvailable = true,
                allowVlcInRecovery = true,
            )
        )

        harness.manager.prepare(
            request = PlaybackRequest(source = hevcSource, playWhenReady = true),
            fallbackCandidates = listOf(hevcSource, h264Source)
        )
        harness.scheduler.advanceUntilIdle()

        harness.fireDecoderError()
        harness.runFallbackTasks()

        assertEquals(h264Source.id, harness.manager.uiState.value.source?.id)
        assertFalse(harness.manager.currentBackend is FakePlayerBackend)
        assertFalse(harness.manager.currentBackend is VlcPlayerBackend)
    }

    @Test
    fun `ask before fallback preserves failure error through buffering emissions`() = harness.testScope.runTest {
        initManager(HarnessOptions(fallbackPolicy = AutoFallbackPolicy.ASK_BEFORE_FALLBACK, progressUpdates = false))
        harness.manager.prepare(
            request = PlaybackRequest(source = source1, playWhenReady = true),
            fallbackCandidates = listOf(source1, source2)
        )
        harness.scheduler.advanceUntilIdle()

        harness.fireNetworkError()
        assertEquals(PlayerState.FAILED, harness.manager.uiState.value.playerState)
        assertTrue(harness.manager.fallbackPromptState.value)
        val failureError = harness.manager.uiState.value.error
        assertNotNull(failureError)

        whenever(harness.mockPlayer.playbackState).thenReturn(Player.STATE_BUFFERING)
        harness.playerListener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        harness.scheduler.advanceUntilIdle()

        assertEquals(failureError, harness.manager.uiState.value.error)
        assertTrue(harness.manager.fallbackPromptState.value)
    }

    @Test
    fun `fast zap during fallback transition keeps the newer source`() = harness.testScope.runTest {
        initManager(HarnessOptions(fallbackPolicy = AutoFallbackPolicy.AUTO_FALLBACK_ONCE, progressUpdates = false))
        val zapTarget = PlaybackSource("zap-new", PlaybackSourceType.IPTV, "Zap", "https://example.com/zap.m3u8")

        harness.manager.prepare(
            request = PlaybackRequest(source = source1, playWhenReady = true),
            fallbackCandidates = listOf(source1, source2)
        )
        harness.scheduler.advanceUntilIdle()

        harness.fireNetworkError()
        harness.manager.prepare(PlaybackRequest(source = zapTarget, playWhenReady = true))
        harness.scheduler.advanceUntilIdle()

        assertEquals(zapTarget.id, harness.manager.uiState.value.source?.id)
    }

    @Test
    fun `release during fallback transition does not re-prepare`() = harness.testScope.runTest {
        initManager(HarnessOptions(fallbackPolicy = AutoFallbackPolicy.AUTO_FALLBACK_ONCE, progressUpdates = false))

        harness.manager.prepare(
            request = PlaybackRequest(source = source1, playWhenReady = true),
            fallbackCandidates = listOf(source1, source2)
        )
        harness.scheduler.advanceUntilIdle()

        harness.playerListener.onPlayerError(
            androidx.media3.common.PlaybackException(
                "Network error",
                null,
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            )
        )
        harness.manager.release()
        harness.runFallbackTasks()

        assertEquals(PlayerState.IDLE, harness.manager.uiState.value.playerState)
        assertEquals(null, harness.manager.currentBackend)
    }

    @Test
    fun `live streams use a longer startup timeout than vod`() = harness.testScope.runTest {
        val live = liveSource("live-1")
        initManager(HarnessOptions(fallbackPolicy = AutoFallbackPolicy.OFF, progressUpdates = false))

        harness.manager.prepare(PlaybackRequest(source = live, playWhenReady = true))
        harness.scheduler.advanceUntilIdle()
        assertEquals(PlayerState.PREPARING, harness.manager.uiState.value.playerState)

        advanceTimeBy(15_001)
        harness.scheduler.advanceUntilIdle()
        assertEquals("VOD-length timeout must not fire for live metadata", PlayerState.PREPARING, harness.manager.uiState.value.playerState)

        advanceTimeBy(15_001)
        harness.scheduler.advanceUntilIdle()
        assertEquals(PlayerState.FAILED, harness.manager.uiState.value.playerState)
        assertTrue(harness.manager.uiState.value.error is PlaybackError.Timeout)
    }

    @Test
    fun `fake backend state emissions drive the ui collector`() = harness.testScope.runTest {
        val fakeBackend = FakePlayerBackend()
        initManager(
            HarnessOptions(
                fallbackPolicy = AutoFallbackPolicy.OFF,
                progressUpdates = false,
                vlcAvailable = true,
                allowVlcInRecovery = true,
                vlcBackendFactory = { fakeBackend },
            )
        )

        val stream = extensionlessSource("collector")
        harness.manager.prepare(PlaybackRequest(source = stream, playWhenReady = true))
        harness.scheduler.advanceUntilIdle()
        repeat(4) { harness.fireUnsupportedFormatError() }
        harness.fireUnsupportedFormatError()
        assertTrue(harness.manager.currentBackend === fakeBackend)

        fakeBackend.emit(PlayerBackendState(playerState = PlayerState.BUFFERING))
        harness.scheduler.advanceUntilIdle()
        assertEquals(PlayerState.BUFFERING, harness.manager.uiState.value.playerState)

        fakeBackend.emitFailed(PlaybackError.Timeout(cause = Exception("stall")))
        harness.scheduler.advanceUntilIdle()
        assertEquals(PlayerState.FAILED, harness.manager.uiState.value.playerState)
        assertTrue(harness.manager.uiState.value.error is PlaybackError.Timeout)
    }

    @Test
    fun `vlc first frame signal cancels freeze watchdog and refreshes tracks`() = harness.testScope.runTest {
        val fakeBackend = object : FakePlayerBackend() {
            override fun availableAudioTracks() = listOf(
                com.example.calmsource.core.model.PlaybackAudioTrack(
                    id = "1",
                    name = "English",
                    language = "en",
                    channels = 2,
                    isSelected = true,
                )
            )
        }
        initManager(
            HarnessOptions(
                fallbackPolicy = AutoFallbackPolicy.OFF,
                progressUpdates = false,
                vlcAvailable = true,
                allowVlcInRecovery = true,
                vlcBackendFactory = { fakeBackend },
            )
        )

        val stream = extensionlessSource("vlc-frame")
        harness.manager.prepare(PlaybackRequest(source = stream, playWhenReady = true))
        harness.scheduler.advanceUntilIdle()
        repeat(4) { harness.fireUnsupportedFormatError() }
        harness.fireUnsupportedFormatError()
        assertTrue(harness.manager.currentBackend === fakeBackend)

        fakeBackend.emit(PlayerBackendState(playerState = PlayerState.BUFFERING))
        harness.scheduler.advanceUntilIdle()
        advanceTimeBy(4_001)
        harness.scheduler.advanceUntilIdle()
        assertEquals(PlayerState.BUFFERING, harness.manager.uiState.value.playerState)

        fakeBackend.emitFirstFrame()
        harness.scheduler.advanceUntilIdle()
        advanceTimeBy(4_001)
        harness.scheduler.advanceUntilIdle()

        assertEquals(PlayerState.PLAYING, harness.manager.uiState.value.playerState)
        assertEquals(1, harness.manager.audioTracks.value.size)
    }

    @Test
    fun `vlc buffering emission preserves failure error`() = harness.testScope.runTest {
        val fakeBackend = FakePlayerBackend()
        initManager(
            HarnessOptions(
                fallbackPolicy = AutoFallbackPolicy.OFF,
                progressUpdates = false,
                vlcAvailable = true,
                allowVlcInRecovery = true,
                vlcBackendFactory = { fakeBackend },
            )
        )

        val stream = extensionlessSource("vlc-error")
        harness.manager.prepare(PlaybackRequest(source = stream, playWhenReady = true))
        harness.scheduler.advanceUntilIdle()
        repeat(4) { harness.fireUnsupportedFormatError() }
        harness.fireUnsupportedFormatError()

        fakeBackend.emitFailed(PlaybackError.Network(cause = Exception("offline")))
        harness.scheduler.advanceUntilIdle()
        val failureError = harness.manager.uiState.value.error
        assertNotNull(failureError)

        fakeBackend.emit(PlayerBackendState(playerState = PlayerState.BUFFERING, error = failureError))
        harness.scheduler.advanceUntilIdle()

        assertEquals(failureError, harness.manager.uiState.value.error)
    }

    @Test
    fun `vlc buffering loop trips breaker before first frame`() = harness.testScope.runTest {
        val fakeBackend = FakePlayerBackend()
        initManager(
            HarnessOptions(
                fallbackPolicy = AutoFallbackPolicy.OFF,
                progressUpdates = false,
                vlcAvailable = true,
                allowVlcInRecovery = true,
                vlcBackendFactory = { fakeBackend },
            )
        )

        val stream = extensionlessSource("vlc-loop")
        harness.manager.prepare(PlaybackRequest(source = stream, playWhenReady = true))
        harness.scheduler.advanceUntilIdle()
        repeat(4) { harness.fireUnsupportedFormatError() }
        harness.fireUnsupportedFormatError()

        repeat(3) { index ->
            fakeBackend.emit(
                PlayerBackendState(
                    playerState = PlayerState.BUFFERING,
                    playbackSpeed = 1f + index * 0.01f,
                )
            )
            harness.scheduler.advanceUntilIdle()
        }

        assertEquals(PlayerState.FAILED, harness.manager.uiState.value.playerState)
        assertTrue(harness.manager.uiState.value.error is PlaybackError.Timeout)
    }
}
