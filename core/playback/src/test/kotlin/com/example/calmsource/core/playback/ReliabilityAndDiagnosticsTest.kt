package com.example.calmsource.core.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.PlayerState
import com.example.calmsource.core.model.PlaybackError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class ReliabilityAndDiagnosticsTest {
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val testScope = TestScope(testDispatcher)

    private lateinit var mockContext: Context
    private lateinit var mockPlayer: ExoPlayer
    private lateinit var playbackManager: PlaybackManager

    private val testSource = PlaybackSource("test-src", PlaybackSourceType.IPTV, "Test Source", "https://example.com/test.m3u8")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockContext = Mockito.mock(Context::class.java)
        mockPlayer = Mockito.mock(ExoPlayer::class.java)

        // Disable VLC fallback in unit tests to prevent surface errors
        VlcPlayerBackend.isAvailableOverride = false

        playbackManager = PlaybackManager(mockContext, testScope.backgroundScope)

        // Inject the mockPlayer using reflection
        val playerField = PlaybackManager::class.java.getDeclaredField("player")
        playerField.isAccessible = true
        playerField.set(playbackManager, mockPlayer)

        val playerFlowField = PlaybackManager::class.java.getDeclaredField("_playerFlow")
        playerFlowField.isAccessible = true
        val flow = playerFlowField.get(playbackManager) as MutableStateFlow<ExoPlayer?>
        flow.value = mockPlayer
    }

    @After
    fun teardown() {
        playbackManager.release()
        VlcPlayerBackend.isAvailableOverride = null
        TunnelingPreferences.mode = TunnelingMode.OFF // Reset to ensure test isolation
        Dispatchers.resetMain()
    }

    @Test
    fun testPlaybackErrorAbortsOnFatalHttpCodes() {
        // Instantiate the private CustomLoadErrorHandlingPolicy via reflection
        val policyClass = Class.forName("com.example.calmsource.core.playback.CustomLoadErrorHandlingPolicy")
        val constructor = policyClass.getDeclaredConstructor()
        constructor.isAccessible = true
        val policy = constructor.newInstance() as LoadErrorHandlingPolicy

        val mockLoadEventInfo = Mockito.mock(LoadEventInfo::class.java)
        val mockMediaLoadData = Mockito.mock(MediaLoadData::class.java)

        val fatalCodes = listOf(400, 401, 403, 404, 405, 410, 451)
        for (code in fatalCodes) {
            val mockUri = Mockito.mock(Uri::class.java)
            val dataSpec = DataSpec.Builder().setUri(mockUri).build()
            val exception = HttpDataSource.InvalidResponseCodeException(
                code,
                "Error $code",
                null,
                emptyMap(),
                dataSpec,
                ByteArray(0)
            )
            val mockErrorInfo = LoadErrorHandlingPolicy.LoadErrorInfo(
                mockLoadEventInfo,
                mockMediaLoadData,
                exception,
                1
            )

            val delay = policy.getRetryDelayMsFor(mockErrorInfo)
            assertEquals("HTTP $code should immediately abort and return C.TIME_UNSET", C.TIME_UNSET, delay)
        }

        // Transient server errors should still allow ExoPlayer to retry.
        val mockUri500 = Mockito.mock(Uri::class.java)
        val dataSpec500 = DataSpec.Builder().setUri(mockUri500).build()
        val exception500 = HttpDataSource.InvalidResponseCodeException(
            500,
            "Error 500",
            null,
            emptyMap(),
            dataSpec500,
            ByteArray(0)
        )
        val mockErrorInfo500 = LoadErrorHandlingPolicy.LoadErrorInfo(
            mockLoadEventInfo,
            mockMediaLoadData,
            exception500,
            1
        )
        val delay500 = policy.getRetryDelayMsFor(mockErrorInfo500)
        assertNotEquals("HTTP 500 should allow retry", C.TIME_UNSET, delay500)

        // Check a transient code still allows ExoPlayer to retry.
        val mockUri = Mockito.mock(Uri::class.java)
        val dataSpec503 = DataSpec.Builder().setUri(mockUri).build()
        val exception503 = HttpDataSource.InvalidResponseCodeException(
            503,
            "Service Unavailable",
            null,
            emptyMap(),
            dataSpec503,
            ByteArray(0)
        )
        val mockErrorInfo503 = LoadErrorHandlingPolicy.LoadErrorInfo(
            mockLoadEventInfo,
            mockMediaLoadData,
            exception503,
            1
        )
        val delay503 = policy.getRetryDelayMsFor(mockErrorInfo503)
        assert(delay503 != C.TIME_UNSET) { "HTTP 503 should be retryable" }
    }

    @Test
    fun testWatchdogTriggersOnStall() = testScope.runTest {
        // Stub ExoPlayer currentTracks to return video track
        val mockTracks = Mockito.mock(Tracks::class.java)
        Mockito.`when`(mockTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)).thenReturn(true)
        Mockito.`when`(mockPlayer.currentTracks).thenReturn(mockTracks)
        Mockito.`when`(mockPlayer.playWhenReady).thenReturn(true)
        Mockito.`when`(mockPlayer.playbackState).thenReturn(Player.STATE_READY)

        // Set active source and request so handleFailure does not exit early
        playbackManager.prepare(
            request = PlaybackRequest(source = testSource, playWhenReady = true),
            fallbackCandidates = emptyList()
        )

        // Get playerListener using reflection
        val listenerField = PlaybackManager::class.java.getDeclaredField("playerListener")
        listenerField.isAccessible = true
        val listener = listenerField.get(playbackManager) as Player.Listener

        // Trigger STATE_READY with video and playWhenReady true
        listener.onPlaybackStateChanged(Player.STATE_READY)

        // Advance 3.9 seconds, shouldn't trigger yet
        testScheduler.advanceTimeBy(3900)
        assertEquals(PlayerState.PLAYING, playbackManager.uiState.value.playerState)

        // Advance past 4 seconds
        testScheduler.advanceTimeBy(200)

        // Verify it failed because of VIDEO_FREEZE (DecoderError)
        assertEquals(PlayerState.FAILED, playbackManager.uiState.value.playerState)
        val error = playbackManager.uiState.value.error
        assert(error is PlaybackError.DecoderError) { "Should trigger DecoderError but got $error" }
    }

    @Test
    fun testWatchdogCancelsOnRenderedFirstFrame() = testScope.runTest {
        // Stub ExoPlayer currentTracks to return video track
        val mockTracks = Mockito.mock(Tracks::class.java)
        Mockito.`when`(mockTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)).thenReturn(true)
        Mockito.`when`(mockPlayer.currentTracks).thenReturn(mockTracks)
        Mockito.`when`(mockPlayer.playWhenReady).thenReturn(true)
        Mockito.`when`(mockPlayer.playbackState).thenReturn(Player.STATE_READY)

        // Set active source and request
        playbackManager.prepare(
            request = PlaybackRequest(source = testSource, playWhenReady = true),
            fallbackCandidates = emptyList()
        )

        val listenerField = PlaybackManager::class.java.getDeclaredField("playerListener")
        listenerField.isAccessible = true
        val listener = listenerField.get(playbackManager) as Player.Listener

        // Trigger STATE_READY with video and playWhenReady true
        listener.onPlaybackStateChanged(Player.STATE_READY)

        // Advance 2 seconds
        testScheduler.advanceTimeBy(2000)
        assertEquals(PlayerState.PLAYING, playbackManager.uiState.value.playerState)

        // First frame is rendered
        listener.onRenderedFirstFrame()

        // Advance past 4 seconds
        testScheduler.advanceTimeBy(3000)

        // Player state should still be PLAYING, not FAILED
        assertEquals(PlayerState.PLAYING, playbackManager.uiState.value.playerState)
    }

    @Test
    fun testTunnelingPreferencesUpdatesSettings() {
        // Verify setting TunnelingPreferences updates the memory state
        TunnelingPreferences.setModeBestEffort(mockContext, TunnelingMode.ON)
        assertEquals(TunnelingMode.ON, TunnelingPreferences.mode)

        TunnelingPreferences.setModeBestEffort(mockContext, TunnelingMode.OFF)
        assertEquals(TunnelingMode.OFF, TunnelingPreferences.mode)

        TunnelingPreferences.setModeBestEffort(mockContext, TunnelingMode.AUTO)
        assertEquals(TunnelingMode.AUTO, TunnelingPreferences.mode)
    }
}
