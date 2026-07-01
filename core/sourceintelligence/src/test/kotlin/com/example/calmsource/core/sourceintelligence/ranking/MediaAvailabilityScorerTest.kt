package com.example.calmsource.core.sourceintelligence.ranking

import com.example.calmsource.core.model.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAvailabilityScorerTest {

    private fun stream(
        id: String,
        title: String,
        resolution: String,
        sizeBytes: Long?,
    ) = StreamSource(
        id = id,
        name = title,
        url = "https://example.com/$id",
        extensionId = "ext-torrentio",
        resolution = resolution,
        videoCodec = "HEVC",
        audioCodec = null,
        sizeBytes = sizeBytes,
        seeds = 100,
        language = "English",
        isSubbed = false,
        isDubbed = false,
        isDualAudio = false,
        headers = emptyMap(),
        rawTitle = title,
    )

    @Test
    fun addingReasonable4KRaisesAvailabilityAbove1080Only() {
        val light4K = stream(
            id = "4k",
            title = "Movie 4K AV1 👥 100",
            resolution = "4K",
            sizeBytes = 12L * 1024 * 1024 * 1024,
        )
        val sweet1080 = stream(
            id = "1080",
            title = "Movie 1080p HEVC 👥 100",
            resolution = "1080p",
            sizeBytes = 5L * 1024 * 1024 * 1024,
        )

        val only1080 = MediaAvailabilityScorer.scoreFromStreams(listOf(sweet1080))
        val withBoth = MediaAvailabilityScorer.scoreFromStreams(listOf(sweet1080, light4K))

        assertTrue(withBoth.additiveScore >= only1080.additiveScore)
        assertTrue(withBoth.normalizedSignal >= only1080.normalizedSignal)
    }

    @Test
    fun emptyStreamsFallsBackToProviderCacheSignal() {
        val result = MediaAvailabilityScorer.scoreFromStreams(
            streams = emptyList(),
            providerCacheAvailability = 0.5,
        )
        assertEquals(10.0, result.additiveScore, 0.01)
        assertEquals(0.1, result.normalizedSignal, 0.01)
    }

    @Test
    fun channelAvailabilityIsStable() {
        val result = MediaAvailabilityScorer.channelAvailability()
        assertEquals(StreamScoringConstants.MEDIA_AVAILABILITY_CHANNEL_DEFAULT, result.additiveScore, 0.01)
    }
}
