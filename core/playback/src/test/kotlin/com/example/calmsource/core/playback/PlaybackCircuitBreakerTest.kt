package com.example.calmsource.core.playback

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.calmsource.core.model.PlaybackError
import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCircuitBreakerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
    private val testScope = TestScope(testDispatcher)

    private lateinit var mockContext: Context
    private lateinit var mockPlayer: ExoPlayer
    private lateinit var playbackManager: PlaybackManager
    private lateinit var playerListener: Player.Listener

    private lateinit var mockedSystemClock: MockedStatic<android.os.SystemClock>
    private lateinit var mockedUri: MockedStatic<android.net.Uri>

    private val source1 = PlaybackSource("src1", PlaybackSourceType.IPTV, "Source 1", "https://example.com/src1.m3u8")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockContext = mock()
        mockPlayer = mock()

        // Mock SystemClock.elapsedRealtime() static method
        mockedSystemClock = Mockito.mockStatic(android.os.SystemClock::class.java)
        mockedSystemClock.`when`<Long> { android.os.SystemClock.elapsedRealtime() }.thenReturn(0L)

        // Mock Uri.parse() static method
        mockedUri = Mockito.mockStatic(android.net.Uri::class.java)
        mockedUri.`when`<android.net.Uri> { android.net.Uri.parse(org.mockito.kotlin.any()) }.thenAnswer { invocation ->
            val url = invocation.getArgument<String>(0)
            val mUri = mock<android.net.Uri>()
            whenever(mUri.toString()).thenReturn(url)
            mUri
        }

        whenever(mockPlayer.playWhenReady).thenReturn(true)
    }

    private fun initPlaybackManager(scope: CoroutineScope) {
        playbackManager = PlaybackManager(mockContext, scope, userMemoryRepositoryFactory = { mock() })
        playbackManager.playerCreator = { _, _, _, _ -> mockPlayer }

        // Inject the mock player
        val playerField = PlaybackManager::class.java.getDeclaredField("player")
        playerField.isAccessible = true
        playerField.set(playbackManager, mockPlayer)

        val playerFlowField = PlaybackManager::class.java.getDeclaredField("_playerFlow")
        playerFlowField.isAccessible = true
        val flow = playerFlowField.get(playbackManager) as kotlinx.coroutines.flow.MutableStateFlow<ExoPlayer?>
        flow.value = mockPlayer

        // Retrieve player listener
        val listenerField = PlaybackManager::class.java.getDeclaredField("playerListener")
        listenerField.isAccessible = true
        playerListener = listenerField.get(playbackManager) as Player.Listener
    }

    @After
    fun tearDown() {
        if (::playbackManager.isInitialized) {
            playbackManager.release()
        }
        if (::mockedSystemClock.isInitialized) {
            mockedSystemClock.close()
        }
        if (::mockedUri.isInitialized) {
            mockedUri.close()
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `buffering loop triggers repeated buffering entries trips circuit breaker`() = testScope.runTest {
        initPlaybackManager(backgroundScope)
        playbackManager.prepare(PlaybackRequest(source = source1, playWhenReady = true))
        testScheduler.advanceUntilIdle()

        // 1st Buffering event
        whenever(mockPlayer.playbackState).thenReturn(Player.STATE_BUFFERING)
        playerListener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        testScheduler.advanceUntilIdle()
        assertFalse(playbackManager.uiState.value.isTerminal)

        // 2nd Buffering event
        playerListener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        testScheduler.advanceUntilIdle()
        assertFalse(playbackManager.uiState.value.isTerminal)

        // 3rd Buffering event -> Should route through fallback instead of terminal failure
        playerListener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        testScheduler.advanceUntilIdle()

        val state = playbackManager.uiState.value
        assertEquals(PlayerState.FAILED, state.playerState)
        assertFalse(state.isTerminal)
        assertFalse(state.isTransitioningSource)
        assertTrue(state.error is PlaybackError.Timeout)
    }

    @Test
    fun `continuous buffering stall triggers after 30 seconds for vod`() = testScope.runTest {
        initPlaybackManager(backgroundScope)
        playbackManager.prepare(PlaybackRequest(source = source1, playWhenReady = true))
        testScheduler.advanceUntilIdle()

        whenever(mockPlayer.playbackState).thenReturn(Player.STATE_BUFFERING)
        playerListener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        testScheduler.advanceUntilIdle()

        // Verify that before 30 seconds the stall handler is NOT tripped
        advanceTimeBy(29_900)
        testScheduler.advanceUntilIdle()
        assertFalse(playbackManager.uiState.value.isTerminal)

        // Advance past 30 second VOD threshold
        advanceTimeBy(200)
        testScheduler.advanceUntilIdle()

        val state = playbackManager.uiState.value
        assertEquals(PlayerState.FAILED, state.playerState)
        assertFalse(state.isTerminal)
        assertFalse(state.isTransitioningSource)
        assertTrue(state.error is PlaybackError.Timeout)
    }
}
