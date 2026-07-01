package com.example.calmsource.core.playback.support

import android.app.ActivityManager
import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import com.example.calmsource.core.database.SourceHealthRepository
import com.example.calmsource.core.model.AutoFallbackPolicy
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.playback.FallbackPreferences
import com.example.calmsource.core.playback.diagnostics.PlaybackDiagnosticsRecorder
import com.example.calmsource.core.playback.PlaybackManager
import com.example.calmsource.core.playback.StreamRacePreferences
import com.example.calmsource.core.playback.VlcPlayerBackend
import com.example.calmsource.core.playback.session.DefaultStreamRaceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class PlaybackManagerTestHarness(
    val scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(scheduler),
    val testScope: TestScope = TestScope(dispatcher),
) {
    lateinit var context: Context
        private set
    lateinit var mockPlayer: ExoPlayer
        private set
    lateinit var manager: PlaybackManager
        private set
    lateinit var playerListener: Player.Listener
        private set

    private var mockedSystemClock: MockedStatic<android.os.SystemClock>? = null
    private var mockedUri: MockedStatic<android.net.Uri>? = null
    private var savedStreamRacingPref = false
    private var savedVlcOverride: Boolean? = null

    private var mocksOwnedByHarness = true

    fun setUp(options: HarnessOptions = HarnessOptions(), installStaticMocks: Boolean = true) {
        Dispatchers.setMain(dispatcher)
        context = mockContext(options.highMemoryDevice)
        mockPlayer = mock()
        FallbackPreferences.policy = options.fallbackPolicy
        savedStreamRacingPref = StreamRacePreferences.enableStreamRacing
        StreamRacePreferences.enableStreamRacing = options.streamRacing
        savedVlcOverride = VlcPlayerBackend.isAvailableOverride
        VlcPlayerBackend.isAvailableOverride = options.vlcAvailable

        if (installStaticMocks) {
            mockedSystemClock?.close()
            mockedUri?.close()
            mockedSystemClock = Mockito.mockStatic(android.os.SystemClock::class.java).also { static ->
                static.`when`<Long> { android.os.SystemClock.elapsedRealtime() }.thenReturn(0L)
            }
            mockedUri = Mockito.mockStatic(android.net.Uri::class.java).also { static ->
                static.`when`<android.net.Uri> { android.net.Uri.parse(org.mockito.kotlin.any()) }.thenAnswer { invocation ->
                    val url = invocation.getArgument<String>(0)
                    val mUri = mock<android.net.Uri>()
                    whenever(mUri.toString()).thenReturn(url)
                    mUri
                }
            }
            mocksOwnedByHarness = true
        } else {
            mocksOwnedByHarness = false
        }

        whenever(mockPlayer.playWhenReady).thenReturn(true)
        whenever(mockPlayer.trackSelectionParameters).thenReturn(TrackSelectionParameters.DEFAULT)

        SourceHealthRepository.dispatcher = dispatcher
        kotlinx.coroutines.runBlocking {
            SourceHealthRepository.clearSourceHealth()
            SourceHealthRepository.clearProviderHealth()
        }
    }

    fun replaceContext(highMemoryDevice: Boolean, streamRacing: Boolean) {
        context = mockContext(highMemoryDevice)
        StreamRacePreferences.enableStreamRacing = streamRacing
    }

    fun createManager(
        coroutineScope: CoroutineScope,
        options: HarnessOptions = HarnessOptions(),
    ) {
        FallbackPreferences.policy = options.fallbackPolicy
        VlcPlayerBackend.isAvailableOverride = options.vlcAvailable
        manager = PlaybackManager(context, coroutineScope, userMemoryRepositoryFactory = { mock() })
        manager.playerCreator = { _, _, _, _ -> mockPlayer }
        manager.streamRaceFactory = DefaultStreamRaceFactory
        manager.progressUpdatesEnabled = options.progressUpdates
        manager.allowVlcInRecovery = options.allowVlcInRecovery
        options.vlcBackendFactory?.let { manager.vlcBackendFactory = it }

        val playerField = PlaybackManager::class.java.getDeclaredField("player")
        playerField.isAccessible = true
        playerField.set(manager, mockPlayer)

        val playerFlowField = PlaybackManager::class.java.getDeclaredField("_playerFlow")
        playerFlowField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = playerFlowField.get(manager) as MutableStateFlow<ExoPlayer?>
        flow.value = mockPlayer

        val listenerField = PlaybackManager::class.java.getDeclaredField("playerListener")
        listenerField.isAccessible = true
        playerListener = listenerField.get(manager) as Player.Listener
    }

    fun tearDown() {
        if (::manager.isInitialized) {
            manager.release()
        }
        SourceHealthRepository.dispatcher = Dispatchers.IO
        kotlinx.coroutines.runBlocking {
            SourceHealthRepository.clearSourceHealth()
            SourceHealthRepository.clearProviderHealth()
        }
        if (mocksOwnedByHarness) {
            mockedSystemClock?.close()
            mockedUri?.close()
            mockedSystemClock = null
            mockedUri = null
        }
        StreamRacePreferences.enableStreamRacing = savedStreamRacingPref
        VlcPlayerBackend.isAvailableOverride = savedVlcOverride
        PlaybackDiagnosticsRecorder.clearForTests()
        com.example.calmsource.core.database.DatabaseProvider.resetForTesting()
        Dispatchers.resetMain()
    }

    fun runFallbackTasks() {
        scheduler.advanceUntilIdle()
        scheduler.advanceTimeBy(100)
        scheduler.advanceUntilIdle()
    }

    fun fireUnsupportedFormatError() {
        playerListener.onPlayerError(
            PlaybackException(
                "Unsupported container",
                null,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            )
        )
        scheduler.advanceUntilIdle()
    }

    fun fireNetworkError() {
        playerListener.onPlayerError(
            PlaybackException(
                "Network error",
                null,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            )
        )
        scheduler.advanceUntilIdle()
    }

    fun fireDecoderError() {
        playerListener.onPlayerError(
            PlaybackException(
                "Decoder init failed",
                null,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            )
        )
        scheduler.advanceUntilIdle()
    }

    fun mimeRetryIndex(): Int {
        val field = PlaybackManager::class.java.getDeclaredField("mimeRetryIndex")
        field.isAccessible = true
        return field.getInt(manager)
    }

    fun injectPlayerAfterRelease() {
        val playerField = PlaybackManager::class.java.getDeclaredField("player")
        playerField.isAccessible = true
        playerField.set(manager, mockPlayer)

        val playerFlowField = PlaybackManager::class.java.getDeclaredField("_playerFlow")
        playerFlowField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = playerFlowField.get(manager) as MutableStateFlow<ExoPlayer?>
        flow.value = mockPlayer
    }

    private fun mockContext(highMemoryDevice: Boolean): Context {
        val ctx = mock<Context>()
        whenever(ctx.applicationContext).thenReturn(ctx)
        val activityManager = mock<ActivityManager>()
        if (highMemoryDevice) {
            whenever(activityManager.memoryClass).thenReturn(512)
            whenever(activityManager.isLowRamDevice).thenReturn(false)
        } else {
            whenever(activityManager.memoryClass).thenReturn(256)
            whenever(activityManager.isLowRamDevice).thenReturn(false)
        }
        whenever(ctx.getSystemService(ActivityManager::class.java)).thenReturn(activityManager)
        return ctx
    }
}

internal data class HarnessOptions(
    val highMemoryDevice: Boolean = false,
    val streamRacing: Boolean = false,
    val fallbackPolicy: AutoFallbackPolicy = AutoFallbackPolicy.AUTO_FALLBACK_ONCE,
    val progressUpdates: Boolean = false,
    val vlcAvailable: Boolean = false,
    val allowVlcInRecovery: Boolean? = null,
    val vlcBackendFactory: (() -> com.example.calmsource.core.playback.PlayerBackend)? = null,
)

internal fun extensionlessSource(
    id: String,
    title: String = id,
): PlaybackSource {
    return PlaybackSource(
        id = id,
        type = com.example.calmsource.core.model.PlaybackSourceType.IPTV,
        title = title,
        rawUrl = "https://example.com/$id",
    )
}

internal fun liveSource(
    id: String,
    title: String = id,
): PlaybackSource {
    return PlaybackSource(
        id = id,
        type = com.example.calmsource.core.model.PlaybackSourceType.IPTV,
        title = title,
        rawUrl = "https://example.com/$id.m3u8",
        metadata = com.example.calmsource.core.model.PlaybackItemMetadata(
            title = title,
            isLive = true,
        ),
    )
}
