package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.MediaStreamEntity
import com.example.calmsource.core.model.SortingPreference
import com.example.calmsource.core.model.StreamParserUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CinematicSortingStressTest {

    @Test
    fun testExtremelyWeirdStreamNames() {
        // Test parsing of nested brackets
        val info = StreamParserUtil.smartParseAll("[[Movie]] [4K] [HDR] [Dolby Vision] [5.1]", "fallback")
        assertEquals("DV", info.hdrFormat)
        assertEquals("4K", info.quality)
        // Check clean title
        assertEquals("[[Movie]] [4K] [HDR] [Dolby Vision] [5.1]", info.cleanTitle)

        // Dolby Vision spacing
        val format1 = StreamParserUtil.parseHdrFormat("Dolby        Vision")
        assertEquals("DV", format1)

        val format2 = StreamParserUtil.parseHdrFormat("dolby   vision")
        assertEquals("DV", format2)

        val format3 = StreamParserUtil.parseHdrFormat("Dolby\nVision")
        assertEquals("DV", format3)
    }

    @Test
    fun testSeederFormats() {
        assertEquals(120, StreamParserUtil.parseSeeds("s: 120"))
        assertEquals(120, StreamParserUtil.parseSeeds("S:120"))
        assertEquals(12, StreamParserUtil.parseSeeds("👤 12"))
        assertEquals(500, StreamParserUtil.parseSeeds("👥 500"))
        assertEquals(5, StreamParserUtil.parseSeeds("👤👤 5"))
        assertEquals(12, StreamParserUtil.parseSeeds("👥👤 12"))
        assertEquals(100, StreamParserUtil.parseSeeds("seeds: 100"))
        assertEquals(42, StreamParserUtil.parseSeeds("seed: 42"))
    }

    @Test
    fun testFalsePositiveSizeParsing() {
        assertEquals(0.0, StreamParserUtil.parseFileSizeGb("12 Monkey"), 0.001)
        assertEquals(0.0, StreamParserUtil.parseFileSizeGb("Fast 2 Furious"), 0.001)
        assertEquals(0.0, StreamParserUtil.parseFileSizeGb("3D Movie"), 0.001)
        assertEquals(0.0, StreamParserUtil.parseFileSizeGb("20th Century Fox"), 0.001)

        // Note: Let's test the 5G Movie and AIOStreams 5G.
        // We expect these to fail because '5G' and '5G Movie' contain 'G' which is treated as GigaBytes.
        // Let's print out what they actually parse to, or test them.
        val size5G = StreamParserUtil.parseFileSizeGb("5G Movie")
        val sizeAio5G = StreamParserUtil.parseFileSizeGb("AIOStreams 5G")
        
        System.out.println("DEBUG: parseFileSizeGb('5G Movie') = $size5G")
        System.out.println("DEBUG: parseFileSizeGb('AIOStreams 5G') = $sizeAio5G")

        // We assert these are 0.0 to verify if the worker's implementation has this false positive bug.
        // If it fails, that is our empirical evidence.
        assertEquals(0.0, size5G, 0.001)
        assertEquals(0.0, sizeAio5G, 0.001)
    }

    @Test
    fun testVideoCodecCaseSensitivityBug() {
        // Let's test case sensitivity/lowercase issues for video codecs:
        // HEVC, AVC/H264, AV1
        val codec1 = StreamParserUtil.parseVideoCodec("Movie.2024.X265.mkv")
        val codec2 = StreamParserUtil.parseVideoCodec("Movie.2024.H264.mkv")
        val codec3 = StreamParserUtil.parseVideoCodec("Movie.2024.av1.mkv")

        System.out.println("DEBUG: parseVideoCodec('X265') = $codec1")
        System.out.println("DEBUG: parseVideoCodec('H264') = $codec2")
        System.out.println("DEBUG: parseVideoCodec('av1') = $codec3")

        // They should be parsed correctly (HEVC, H264, AV1).
        // If they return null, then it's a bug!
        assertEquals("HEVC", codec1)
        assertEquals("H264", codec2)
        assertEquals("AV1", codec3)
    }

    @Test
    fun testBestMatchVsHighestQualityStrategies() {
        val streamA = MediaStreamEntity(
            id = "stream-A-heavy-remux-4k",
            mediaId = "m1",
            title = "Movie 4K Remux HEVC DTS-HD Dolby Vision 👥 10",
            url = "http://heavy",
            resolution = "4K",
            codec = "HEVC",
            quality = "UHD",
            sizeInBytes = 25L * 1024 * 1024 * 1024, // 25 GB
            language = "English",
            isSubbed = false,
            isDubbed = false,
            source = "ext-torrentio",
            updatedAt = System.currentTimeMillis()
        )

        val streamB = MediaStreamEntity(
            id = "stream-B-sweet-1080p",
            mediaId = "m1",
            title = "Movie 1080p HEVC Atmos 👥 10",
            url = "http://sweet",
            resolution = "1080p",
            codec = "HEVC",
            quality = "FHD",
            sizeInBytes = 5L * 1024 * 1024 * 1024, // 5 GB
            language = "English",
            isSubbed = false,
            isDubbed = false,
            source = "ext-torrentio",
            updatedAt = System.currentTimeMillis()
        )

        val streamC = MediaStreamEntity(
            id = "stream-C-light-4k",
            mediaId = "m1",
            title = "Movie 4K AV1 HDR10 👥 10",
            url = "http://light-4k",
            resolution = "4K",
            codec = "AV1",
            quality = "UHD",
            sizeInBytes = 12L * 1024 * 1024 * 1024, // 12 GB
            language = "English",
            isSubbed = false,
            isDubbed = false,
            source = "ext-torrentio",
            updatedAt = System.currentTimeMillis()
        )

        // Test BEST_MATCH
        val rankedBestMatch = StreamRanker.rankWithSignals(
            streams = listOf(streamA, streamB, streamC),
            preferredAudio = emptyList(),
            preferredSub = emptyList(),
            streamSuccessCount = { 0 },
            streamFailureCount = { 0 },
            strategy = SortingPreference.BEST_MATCH
        )

        // Expected order under BEST_MATCH: Stream B (sweet-1080p) > Stream C (light-4k) > Stream A (heavy-remux-4k)
        assertEquals("stream-B-sweet-1080p", rankedBestMatch[0].id)
        assertEquals("stream-C-light-4k", rankedBestMatch[1].id)
        assertEquals("stream-A-heavy-remux-4k", rankedBestMatch[2].id)

        // Test HIGHEST_QUALITY
        val rankedHighestQuality = StreamRanker.rankWithSignals(
            streams = listOf(streamB, streamC, streamA),
            preferredAudio = emptyList(),
            preferredSub = emptyList(),
            streamSuccessCount = { 0 },
            streamFailureCount = { 0 },
            strategy = SortingPreference.HIGHEST_QUALITY
        )

        // Expected order under HIGHEST_QUALITY: Stream A (heavy-remux-4k) > Stream C (light-4k) > Stream B (sweet-1080p)
        assertEquals("stream-A-heavy-remux-4k", rankedHighestQuality[0].id)
        assertEquals("stream-C-light-4k", rankedHighestQuality[1].id)
        assertEquals("stream-B-sweet-1080p", rankedHighestQuality[2].id)
    }
}
