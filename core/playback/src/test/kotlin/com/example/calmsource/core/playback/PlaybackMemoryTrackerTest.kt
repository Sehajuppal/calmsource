package com.example.calmsource.core.playback

import com.example.calmsource.core.model.PlaybackItemMetadata
import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.UserMemoryContentType
import com.example.calmsource.core.model.UserMemoryReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackMemoryTrackerTest {

    @Test
    fun `VOD tracking starts only after actual playback and checkpoints periodically`() {
        val tracker = PlaybackMemoryTracker(clock = { 1234L })
        tracker.begin(vodRequest(startPositionMs = 5_000L))

        assertTrue(tracker.onPause(10_000L, 100_000L).isEmpty())

        tracker.onPlaying(5_000L, 100_000L)
        assertTrue(tracker.onPeriodicCheckpoint(19_999L, 100_000L).isEmpty())

        val periodic = tracker.onPeriodicCheckpoint(20_000L, 100_000L)
            .single() as PlaybackMemoryWrite.VodCheckpoint
        assertEquals(20_000L, periodic.progressMs)
        assertEquals(100_000L, periodic.durationMs)
        assertEquals(1234L, periodic.watchedAt)
        assertTrue(periodic.recordHistory)
        assertFalse(periodic.completed)

        val pause = tracker.onPause(25_000L, 100_000L)
            .single() as PlaybackMemoryWrite.VodCheckpoint
        assertFalse(pause.recordHistory)
        assertFalse(pause.completed)

        assertTrue(tracker.onRelease(25_000L, 100_000L).isEmpty())
        assertTrue(tracker.onRelease(25_000L, 100_000L).isEmpty())
    }

    @Test
    fun `completed VOD removes progress once and cannot be re-added by later lifecycle events`() {
        val tracker = PlaybackMemoryTracker(clock = { 99L })
        tracker.begin(vodRequest())
        tracker.onPlaying(0L, 100_000L)
        tracker.onPeriodicCheckpoint(15_000L, 100_000L)

        val completed = tracker.onPeriodicCheckpoint(95_000L, 100_000L)
            .single() as PlaybackMemoryWrite.VodCheckpoint
        assertTrue(completed.completed)
        assertFalse(completed.recordHistory)

        assertTrue(tracker.onPause(40_000L, 100_000L).isEmpty())
        assertTrue(tracker.onEnded(100_000L, 100_000L).isEmpty())
        assertTrue(tracker.onRelease(100_000L, 100_000L).isEmpty())
    }

    @Test
    fun `ended VOD is persisted as complete even when reported position trails duration`() {
        val tracker = PlaybackMemoryTracker()
        tracker.begin(vodRequest())
        tracker.onPlaying(0L, 100_000L)

        val completed = tracker.onEnded(87_000L, 100_000L)
            .single() as PlaybackMemoryWrite.VodCheckpoint
        assertEquals(100_000L, completed.progressMs)
        assertTrue(completed.completed)
        assertTrue(completed.recordHistory)
    }

    @Test
    fun `live playback records recent channel once only after playing`() {
        val tracker = PlaybackMemoryTracker(clock = { 44L })
        val request = liveRequest()
        tracker.begin(request)

        assertTrue(tracker.onPause(0L, 0L).isEmpty())

        val started = tracker.onPlaying(0L, 0L)
            .single() as PlaybackMemoryWrite.LiveStarted
        assertEquals(request.userMemoryReference, started.reference)
        assertEquals(44L, started.watchedAt)

        assertTrue(tracker.onPlaying(1_000L, 0L).isEmpty())
        assertTrue(tracker.onPeriodicCheckpoint(30_000L, 0L).isEmpty())
        assertTrue(tracker.onPause(30_000L, 0L).isEmpty())
        assertTrue(tracker.onRelease(30_000L, 0L).isEmpty())
    }

    @Test
    fun `live metadata can never create VOD progress from a mismatched reference`() {
        val tracker = PlaybackMemoryTracker()
        tracker.begin(
            vodRequest().copy(
                source = source(isLive = true)
            )
        )

        tracker.onPlaying(0L, 0L)
        assertTrue(tracker.onPeriodicCheckpoint(30_000L, 0L).isEmpty())
        assertTrue(tracker.onPause(30_000L, 0L).isEmpty())
        assertTrue(tracker.onEnded(30_000L, 0L).isEmpty())
    }

    @Test
    fun `fallback preserves request identity and furthest start position`() {
        val original = vodRequest(startPositionMs = 30_000L)
        val fallback = original.forFallback(
            source = source(id = "fallback"),
            currentPositionMs = 45_000L
        )

        assertEquals("fallback", fallback.source.id)
        assertEquals(45_000L, fallback.startPositionMs)
        assertEquals(original.userMemoryReference, fallback.userMemoryReference)
        assertTrue(fallback.playWhenReady)

        val beforeStart = original.forFallback(
            source = source(id = "fallback-2"),
            currentPositionMs = 10_000L
        )
        assertEquals(30_000L, beforeStart.startPositionMs)
    }

    @Test
    fun `tracking actions contain request identity but no source URL headers or tokens`() {
        val tracker = PlaybackMemoryTracker()
        val request = vodRequest().copy(
            source = source(
                rawUrl = "https://private.example/movie.m3u8?token=top-secret",
                headers = mapOf("Authorization" to "Bearer top-secret")
            )
        )
        tracker.begin(request)
        tracker.onPlaying(0L, 100_000L)

        val write = tracker.onPause(20_000L, 100_000L).single()
        assertTrue(write.toString().contains("movie-123"))
        assertFalse(write.toString().contains("private.example"))
        assertFalse(write.toString().contains("top-secret"))
        assertFalse(write.toString().contains("Authorization"))
    }

    @Test
    fun `resume position never moves behind the request start position`() {
        val tracker = PlaybackMemoryTracker()
        tracker.begin(vodRequest(startPositionMs = 30_000L))
        tracker.onPlaying(30_000L, 100_000L)

        val write = tracker.onPause(5_000L, 100_000L)
            .single() as PlaybackMemoryWrite.VodCheckpoint
        assertEquals(30_000L, write.progressMs)
    }

    private fun vodRequest(startPositionMs: Long = 0L): PlaybackRequest {
        return PlaybackRequest(
            source = source(),
            startPositionMs = startPositionMs,
            userMemoryReference = UserMemoryReference(
                itemKey = "movie-123",
                contentType = UserMemoryContentType.MOVIE,
                title = "A Movie",
                providerId = "provider",
                sourceId = "catalog-source"
            )
        )
    }

    private fun liveRequest(): PlaybackRequest {
        return PlaybackRequest(
            source = source(isLive = true),
            userMemoryReference = UserMemoryReference(
                itemKey = "channel-7",
                contentType = UserMemoryContentType.LIVE_CHANNEL,
                title = "Channel 7",
                providerId = "iptv-provider",
                sourceId = "channel-source"
            )
        )
    }

    private fun source(
        id: String = "primary",
        rawUrl: String = "https://example.invalid/video.m3u8",
        headers: Map<String, String> = emptyMap(),
        isLive: Boolean = false
    ): PlaybackSource {
        return PlaybackSource(
            id = id,
            type = PlaybackSourceType.EXTENSION,
            title = "Playback source",
            rawUrl = rawUrl,
            metadata = PlaybackItemMetadata(
                title = "Playback item",
                durationMs = if (isLive) null else 100_000L,
                isLive = isLive
            ),
            headers = headers
        )
    }
}
