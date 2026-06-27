package com.example.calmsource.core.playback

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StreamRaceManagerTest {
    @Test
    fun `feature flag off returns sequential fallback without probing`() = runTest {
        var probeCount = 0
        val manager = StreamRaceManager(
            probe = StreamProbe {
                probeCount++
                StreamProbeResult.Ready(handshakeMs = 1, firstByteMs = 2, readyAtMs = 10)
            }
        )

        val result = manager.race(
            StreamRaceRequest(
                candidates = listOf(source("s1"), source("s2")),
                enabled = false
            )
        )

        assertTrue(result is StreamRaceResult.SequentialFallback)
        assertEquals(StreamRaceSkipReason.FEATURE_DISABLED, (result as StreamRaceResult.SequentialFallback).reason)
        assertEquals(0, probeCount)
    }

    @Test
    fun `low memory mode skips racing`() = runTest {
        var probeCount = 0
        val manager = StreamRaceManager(
            probe = StreamProbe {
                probeCount++
                StreamProbeResult.Ready(handshakeMs = 1, firstByteMs = 2, readyAtMs = 10)
            }
        )

        val result = manager.race(
            StreamRaceRequest(
                candidates = listOf(source("s1"), source("s2")),
                lowMemoryMode = true,
                enabled = true
            )
        )

        assertTrue(result is StreamRaceResult.SequentialFallback)
        assertEquals(StreamRaceSkipReason.LOW_MEMORY_MODE, (result as StreamRaceResult.SequentialFallback).reason)
        assertEquals(0, probeCount)
    }

    @Test
    fun `racing filters failed sources and limits probes to top three ranked candidates`() = runTest {
        val probed = mutableSetOf<String>()
        val scores = mapOf("s1" to 30.0, "s2" to 90.0, "s3" to 100.0, "s4" to 60.0, "s5" to 10.0)
        val manager = StreamRaceManager(
            probe = StreamProbe { source ->
                probed += source.id
                StreamProbeResult.Failed("offline")
            },
            ranker = StreamRaceRanker { source -> scores.getValue(source.id) }
        )

        val result = manager.race(
            StreamRaceRequest(
                candidates = listOf(source("s1"), source("s2"), source("s3"), source("s4"), source("s5")),
                failedSourceIds = setOf("s3"),
                enabled = true
            )
        )

        assertTrue(result is StreamRaceResult.SequentialFallback)
        assertEquals(StreamRaceSkipReason.ALL_PROBES_FAILED, (result as StreamRaceResult.SequentialFallback).reason)
        assertEquals(setOf("s2", "s4", "s1"), probed)
        assertFalse("failed source should not be probed", "s3" in probed)
        assertFalse("fourth-ranked source should not be probed", "s5" in probed)
    }

    @Test
    fun `first ready source wins and unfinished losers are cancelled`() = runTest {
        val released = mutableSetOf<String>()
        val manager = StreamRaceManager(
            probe = StreamProbe { source ->
                try {
                    if (source.id == "s1") {
                        delay(10)
                        StreamProbeResult.Ready(handshakeMs = 10, firstByteMs = 12, readyAtMs = 1_000)
                    } else {
                        awaitCancellation()
                    }
                } finally {
                    released += source.id
                }
            },
            ranker = StreamRaceRanker { source -> if (source.id == "s1") 10.0 else 5.0 }
        )

        val result = manager.race(
            StreamRaceRequest(
                candidates = listOf(source("s1"), source("s2"), source("s3")),
                enabled = true
            )
        )

        assertTrue(result is StreamRaceResult.Winner)
        assertEquals("s1", (result as StreamRaceResult.Winner).source.id)
        assertEquals(setOf("s1", "s2", "s3"), released)
    }

    @Test
    fun `tie window picks higher ranked ready source`() = runTest {
        val manager = StreamRaceManager(
            probe = StreamProbe { source ->
                when (source.id) {
                    "low" -> StreamProbeResult.Ready(handshakeMs = 8, firstByteMs = 10, readyAtMs = 1_000)
                    "high" -> StreamProbeResult.Ready(handshakeMs = 9, firstByteMs = 11, readyAtMs = 1_025)
                    else -> StreamProbeResult.Failed("offline")
                }
            },
            ranker = StreamRaceRanker { source -> if (source.id == "high") 50.0 else 10.0 }
        )

        val result = manager.race(
            StreamRaceRequest(
                candidates = listOf(source("low"), source("high")),
                enabled = true
            )
        )

        assertTrue(result is StreamRaceResult.Winner)
        assertEquals("high", (result as StreamRaceResult.Winner).source.id)
    }

    @Test
    fun `all probe failures fall back to sequential mode and emit failure telemetry`() = runTest {
        val events = mutableListOf<StreamRaceTelemetryEvent>()
        val manager = StreamRaceManager(
            probe = StreamProbe {
                StreamProbeResult.Failed(reason = "timeout", handshakeMs = 20, firstByteMs = 0)
            },
            telemetrySink = StreamRaceTelemetrySink { event -> events += event }
        )

        val result = manager.race(
            StreamRaceRequest(
                candidates = listOf(source("s1"), source("s2")),
                enabled = true
            )
        )

        assertTrue(result is StreamRaceResult.SequentialFallback)
        assertEquals(StreamRaceSkipReason.ALL_PROBES_FAILED, (result as StreamRaceResult.SequentialFallback).reason)
        assertEquals(2, events.size)
        assertTrue(events.all { it.status == StreamRaceProbeStatus.FAILURE })
        assertTrue(events.all { it.reason == "timeout" })
    }

    @Test
    fun `rate limited probes are denied before the prober runs`() = runTest {
        val probed = mutableSetOf<String>()
        val events = mutableListOf<StreamRaceTelemetryEvent>()
        val manager = StreamRaceManager(
            probe = StreamProbe { source ->
                probed += source.id
                StreamProbeResult.Ready(handshakeMs = 5, firstByteMs = 8, readyAtMs = 2_000)
            },
            ranker = StreamRaceRanker { source -> if (source.id == "s1") 100.0 else 10.0 },
            permit = StreamRacePermit { source -> source.id != "s1" },
            telemetrySink = StreamRaceTelemetrySink { event -> events += event }
        )

        val result = manager.race(
            StreamRaceRequest(
                candidates = listOf(source("s1"), source("s2"), source("s3")),
                enabled = true
            )
        )

        assertTrue(result is StreamRaceResult.Winner)
        assertFalse("denied source should not reach the probe", "s1" in probed)
        assertTrue(events.any { it.sourceId == "s1" && it.status == StreamRaceProbeStatus.DENIED })
    }

    @Test
    fun `hasSelectedTracks returns true when at least one track is selected`() {
        val mockedTextUtils = org.mockito.Mockito.mockStatic(android.text.TextUtils::class.java)
        mockedTextUtils.`when`<Boolean> { android.text.TextUtils.isEmpty(org.mockito.kotlin.anyOrNull()) }
            .thenAnswer { invocation ->
                val str = invocation.getArgument<CharSequence?>(0)
                str.isNullOrEmpty()
            }
        try {
            val mockPlayer = org.mockito.kotlin.mock<androidx.media3.common.Player>()
            val format = androidx.media3.common.Format.Builder().build()
            val trackGroup = androidx.media3.common.TrackGroup(format)
            val group = androidx.media3.common.Tracks.Group(
                trackGroup,
                /* adaptiveSupported = */ false,
                /* trackSupport = */ intArrayOf(4),
                /* trackSelected = */ booleanArrayOf(true)
            )
            val tracks = androidx.media3.common.Tracks(com.google.common.collect.ImmutableList.of(group))

            org.mockito.kotlin.whenever(mockPlayer.currentTracks).thenReturn(tracks)

            assertTrue(mockPlayer.hasSelectedTracks())
        } finally {
            mockedTextUtils.close()
        }
    }

    @Test
    fun `hasSelectedTracks returns false when no tracks are selected`() {
        val mockedTextUtils = org.mockito.Mockito.mockStatic(android.text.TextUtils::class.java)
        mockedTextUtils.`when`<Boolean> { android.text.TextUtils.isEmpty(org.mockito.kotlin.anyOrNull()) }
            .thenAnswer { invocation ->
                val str = invocation.getArgument<CharSequence?>(0)
                str.isNullOrEmpty()
            }
        try {
            val mockPlayer = org.mockito.kotlin.mock<androidx.media3.common.Player>()
            val format = androidx.media3.common.Format.Builder().build()
            val trackGroup = androidx.media3.common.TrackGroup(format)
            val group = androidx.media3.common.Tracks.Group(
                trackGroup,
                /* adaptiveSupported = */ false,
                /* trackSupport = */ intArrayOf(4),
                /* trackSelected = */ booleanArrayOf(false)
            )
            val tracks = androidx.media3.common.Tracks(com.google.common.collect.ImmutableList.of(group))

            org.mockito.kotlin.whenever(mockPlayer.currentTracks).thenReturn(tracks)

            assertFalse(mockPlayer.hasSelectedTracks())
        } finally {
            mockedTextUtils.close()
        }
    }

    private fun source(id: String): PlaybackSource {
        return PlaybackSource(
            id = id,
            type = PlaybackSourceType.EXTENSION,
            title = "Source $id",
            rawUrl = "https://example.com/$id.m3u8"
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class Media3StreamProbeTest {
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val testScope = TestScope(testDispatcher)

    private lateinit var mockContext: Context
    private lateinit var mockAppContext: Context
    private lateinit var mockPlayer: ExoPlayer
    private lateinit var mockedUri: MockedStatic<android.net.Uri>
    private lateinit var mockedTextUtils: MockedStatic<android.text.TextUtils>
    private lateinit var mockedLog: MockedStatic<android.util.Log>
    private lateinit var mockedBuilderConstruction: org.mockito.MockedConstruction<ExoPlayer.Builder>

    private fun resetSupportedMimeTypes() {
        testSupportedVideoMimeTypes = setOf("video/avc")
    }

    @Before
    fun setup() {
        resetSupportedMimeTypes()
        Dispatchers.setMain(testDispatcher)
        mockContext = mock()
        mockAppContext = mock()
        mockPlayer = mock()

        whenever(mockContext.applicationContext).thenReturn(mockAppContext)

        mockedUri = Mockito.mockStatic(android.net.Uri::class.java)
        mockedUri.`when`<android.net.Uri> { android.net.Uri.parse(any()) }.thenAnswer { invocation ->
            val url = invocation.getArgument<String>(0)
            val mUri = mock<android.net.Uri>()
            whenever(mUri.toString()).thenReturn(url)
            mUri
        }

        mockedTextUtils = Mockito.mockStatic(android.text.TextUtils::class.java)
        mockedTextUtils.`when`<Boolean> { android.text.TextUtils.isEmpty(any()) }
            .thenAnswer { invocation ->
                val str = invocation.getArgument<CharSequence?>(0)
                str.isNullOrEmpty()
            }

        mockedLog = Mockito.mockStatic(android.util.Log::class.java)

        mockedBuilderConstruction = Mockito.mockConstruction(
            ExoPlayer.Builder::class.java
        ) { builderMock, _ ->
            whenever(builderMock.setMediaSourceFactory(any())).thenReturn(builderMock)
            whenever(builderMock.setRenderersFactory(any())).thenReturn(builderMock)
            whenever(builderMock.setLoadControl(any())).thenReturn(builderMock)
            whenever(builderMock.build()).thenReturn(mockPlayer)
        }
    }

    @After
    fun teardown() {
        if (::mockedUri.isInitialized) mockedUri.close()
        if (::mockedTextUtils.isInitialized) mockedTextUtils.close()
        if (::mockedLog.isInitialized) mockedLog.close()
        if (::mockedBuilderConstruction.isInitialized) mockedBuilderConstruction.close()
        Dispatchers.resetMain()
    }

    private fun mockTracks(selected: Boolean): androidx.media3.common.Tracks {
        val format = androidx.media3.common.Format.Builder().build()
        val trackGroup = androidx.media3.common.TrackGroup(format)
        val group = androidx.media3.common.Tracks.Group(
            trackGroup,
            /* adaptiveSupported = */ false,
            /* trackSupport = */ intArrayOf(4),
            /* trackSelected = */ booleanArrayOf(selected)
        )
        return androidx.media3.common.Tracks(com.google.common.collect.ImmutableList.of(group))
    }

    private fun mockEmptyTracks(): androidx.media3.common.Tracks {
        return androidx.media3.common.Tracks(com.google.common.collect.ImmutableList.of())
    }

    private fun source(id: String): PlaybackSource {
        return PlaybackSource(
            id = id,
            type = PlaybackSourceType.EXTENSION,
            title = "Source $id",
            rawUrl = "https://example.com/$id.m3u8"
        )
    }

    @Test
    fun `when STATE_READY is reached but tracks are not selected player listener waits`() = testScope.runTest {
        val probe = Media3StreamProbe(mockContext, timeoutMs = 60_000L)
        val initialTracks = mockTracks(selected = false)
        whenever(mockPlayer.currentTracks).thenReturn(initialTracks)

        val deferred = async {
            probe.probe(source("s1"))
        }
        runCurrent()

        val listenerCaptor = argumentCaptor<Player.Listener>()
        verify(mockPlayer).addListener(listenerCaptor.capture())
        val listener = listenerCaptor.firstValue

        // Fire STATE_READY
        listener.onPlaybackStateChanged(Player.STATE_READY)
        runCurrent()

        // Verify that the probe has not completed yet
        assertFalse(deferred.isCompleted)
        assertTrue(deferred.isActive)

        // Clean up coroutine
        deferred.cancel()
    }

    @Test
    fun `when tracks are selected after STATE_READY is reached probe resumes as Ready`() = testScope.runTest {
        val probe = Media3StreamProbe(mockContext, timeoutMs = 60_000L)
        val initialTracks = mockTracks(selected = false)
        whenever(mockPlayer.currentTracks).thenReturn(initialTracks)

        val deferred = async {
            probe.probe(source("s1"))
        }
        runCurrent()

        val listenerCaptor = argumentCaptor<Player.Listener>()
        verify(mockPlayer).addListener(listenerCaptor.capture())
        val listener = listenerCaptor.firstValue

        // Fire STATE_READY
        listener.onPlaybackStateChanged(Player.STATE_READY)
        runCurrent()

        // Still active
        assertFalse(deferred.isCompleted)

        // Fire onTracksChanged with a selected track
        val selectedTracks = mockTracks(selected = true)
        listener.onTracksChanged(selectedTracks)
        runCurrent()

        // Should be completed as Ready
        assertTrue(deferred.isCompleted)
        val result = deferred.getCompleted()
        assertTrue(result is StreamProbeResult.Ready)
    }

    @Test
    fun `when tracks are empty after STATE_READY is reached probe resumes as Failed no_active_tracks`() = testScope.runTest {
        val probe = Media3StreamProbe(mockContext, timeoutMs = 60_000L)
        val initialTracks = mockTracks(selected = false)
        whenever(mockPlayer.currentTracks).thenReturn(initialTracks)

        val deferred = async {
            probe.probe(source("s1"))
        }
        runCurrent()

        val listenerCaptor = argumentCaptor<Player.Listener>()
        verify(mockPlayer).addListener(listenerCaptor.capture())
        val listener = listenerCaptor.firstValue

        // Fire STATE_READY
        listener.onPlaybackStateChanged(Player.STATE_READY)
        runCurrent()

        // Still active
        assertFalse(deferred.isCompleted)

        // Fire onTracksChanged with empty tracks
        val emptyTracks = mockEmptyTracks()
        listener.onTracksChanged(emptyTracks)
        runCurrent()

        // Should be completed as Failed("no_active_tracks")
        assertTrue(deferred.isCompleted)
        val result = deferred.getCompleted()
        assertTrue(result is StreamProbeResult.Failed)
        assertEquals("no_active_tracks", (result as StreamProbeResult.Failed).reason)
    }

    private fun mockVideoTracks(selected: Boolean, mimeType: String?): androidx.media3.common.Tracks {
        val format = androidx.media3.common.Format.Builder()
            .setSampleMimeType(mimeType)
            .build()
        val trackGroup = androidx.media3.common.TrackGroup(format)
        val group = androidx.media3.common.Tracks.Group(
            trackGroup,
            /* adaptiveSupported = */ false,
            /* trackSupport = */ intArrayOf(4),
            /* trackSelected = */ booleanArrayOf(selected)
        )
        return androidx.media3.common.Tracks(com.google.common.collect.ImmutableList.of(group))
    }

    @Test
    fun `hasSupportedVideoTrack returns false when video track is unsupported`() {
        val mockPlayer = org.mockito.kotlin.mock<androidx.media3.common.Player>()
        val mockTracks = mockVideoTracks(selected = true, mimeType = "video/unsupported-mime-type")
        org.mockito.kotlin.whenever(mockPlayer.currentTracks).thenReturn(mockTracks)

        val mockCodecInfo = org.mockito.kotlin.mock<android.media.MediaCodecInfo>()
        org.mockito.kotlin.whenever(mockCodecInfo.isEncoder).thenReturn(false)
        org.mockito.kotlin.whenever(mockCodecInfo.supportedTypes).thenReturn(arrayOf("video/avc"))

        val mockedCodecList = Mockito.mockConstruction(
            android.media.MediaCodecList::class.java
        ) { mockList, _ ->
            org.mockito.kotlin.whenever(mockList.codecInfos).thenReturn(arrayOf(mockCodecInfo))
        }
        try {
            assertFalse(hasSupportedVideoTrack(mockPlayer))
        } finally {
            mockedCodecList.close()
        }
    }

    @Test
    fun `hasSupportedVideoTrack returns true when video track is supported`() {
        val mockPlayer = org.mockito.kotlin.mock<androidx.media3.common.Player>()
        val mockTracks = mockVideoTracks(selected = true, mimeType = "video/avc")
        org.mockito.kotlin.whenever(mockPlayer.currentTracks).thenReturn(mockTracks)

        val mockCodecInfo = org.mockito.kotlin.mock<android.media.MediaCodecInfo>()
        org.mockito.kotlin.whenever(mockCodecInfo.isEncoder).thenReturn(false)
        org.mockito.kotlin.whenever(mockCodecInfo.supportedTypes).thenReturn(arrayOf("video/avc"))

        val mockedCodecList = Mockito.mockConstruction(
            android.media.MediaCodecList::class.java
        ) { mockList, _ ->
            org.mockito.kotlin.whenever(mockList.codecInfos).thenReturn(arrayOf(mockCodecInfo))
        }
        try {
            assertTrue(hasSupportedVideoTrack(mockPlayer))
        } finally {
            mockedCodecList.close()
        }
    }

    @Test
    fun `when tracks are selected but video track is unsupported probe resumes as Failed unsupported_video_track`() = testScope.runTest {
        val probe = Media3StreamProbe(mockContext, timeoutMs = 60_000L)
        val initialTracks = mockTracks(selected = false)
        whenever(mockPlayer.currentTracks).thenReturn(initialTracks)

        val deferred = async {
            probe.probe(source("s1"))
        }
        runCurrent()

        val listenerCaptor = argumentCaptor<Player.Listener>()
        verify(mockPlayer).addListener(listenerCaptor.capture())
        val listener = listenerCaptor.firstValue

        // Fire STATE_READY
        listener.onPlaybackStateChanged(Player.STATE_READY)
        runCurrent()

        // Still active
        assertFalse(deferred.isCompleted)

        // Mock player currentTracks to return unsupported video track group
        val unsupportedTracks = mockVideoTracks(selected = true, mimeType = "video/unsupported")
        whenever(mockPlayer.currentTracks).thenReturn(unsupportedTracks)

        // Mock MediaCodecList to return at least one supported codec (e.g., video/avc)
        val mockCodecInfo = org.mockito.kotlin.mock<android.media.MediaCodecInfo>()
        org.mockito.kotlin.whenever(mockCodecInfo.isEncoder).thenReturn(false)
        org.mockito.kotlin.whenever(mockCodecInfo.supportedTypes).thenReturn(arrayOf("video/avc"))

        val mockedCodecList = Mockito.mockConstruction(
            android.media.MediaCodecList::class.java
        ) { mockList, _ ->
            whenever(mockList.codecInfos).thenReturn(arrayOf(mockCodecInfo))
        }

        try {
            listener.onTracksChanged(unsupportedTracks)
            runCurrent()
        } finally {
            mockedCodecList.close()
        }

        // Should be completed as Failed("unsupported_video_track")
        assertTrue(deferred.isCompleted)
        val result = deferred.getCompleted()
        assertTrue(result is StreamProbeResult.Failed)
        assertEquals("unsupported_video_track", (result as StreamProbeResult.Failed).reason)
    }

    @Test
    fun `when rawUrl is empty or blank probe throws IllegalArgumentException immediately and returns Failed`() = testScope.runTest {
        val probe = Media3StreamProbe(mockContext, timeoutMs = 60_000L)
        val source = PlaybackSource(
            id = "empty1",
            type = PlaybackSourceType.EXTENSION,
            title = "Empty Source",
            rawUrl = "   "
        )
        val result = probe.probe(source)
        assertTrue(result is StreamProbeResult.Failed)
        assertEquals("IllegalArgumentException", (result as StreamProbeResult.Failed).reason)
        // Verify that player was never created or prepare was never called
        org.mockito.Mockito.verify(mockPlayer, org.mockito.Mockito.never()).prepare()
    }

    @Test
    fun `when rawUrl is xtream pseudo-URL probe does not reject it before prepare`() = testScope.runTest {
        val probe = Media3StreamProbe(mockContext, timeoutMs = 60_000L)
        val source = PlaybackSource(
            id = "xtream1",
            type = PlaybackSourceType.IPTV,
            title = "Xtream Source",
            rawUrl = "xtream://stream_id/123"
        )
        val result = probe.probe(source)
        assertTrue(result is StreamProbeResult.Failed)
        assertEquals("unresolved_xtream_url", (result as StreamProbeResult.Failed).reason)
        org.mockito.Mockito.verify(mockPlayer, org.mockito.Mockito.never()).prepare()
    }
}

