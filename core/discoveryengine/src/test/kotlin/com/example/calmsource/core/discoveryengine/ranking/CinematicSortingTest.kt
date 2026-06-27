package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.MediaStreamEntity
import com.example.calmsource.core.model.SortingPreference
import com.example.calmsource.core.model.StreamParserUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CinematicSortingTest {

    @Test
    fun testSizeRegexNoFalsePositives() {
        val size = StreamParserUtil.parseFileSizeGb("12 Monkey")
        assertEquals(0.0, size, 0.001)

        val actualSize = StreamParserUtil.parseFileSizeGb("Inception 2010 1080p 1.5 GB")
        assertEquals(1.5, actualSize, 0.001)

        // Additional false positive checks requested:
        // '12 Monkey' (already checked above), '5G Movie', 'Fast 2 Furious'
        val size5G = StreamParserUtil.parseFileSizeGb("5G Movie")
        assertEquals("5G Movie should not be parsed as 5 GB size", 0.0, size5G, 0.001)

        val sizeFast2Furious = StreamParserUtil.parseFileSizeGb("Fast 2 Furious")
        assertEquals("Fast 2 Furious should not be parsed as containing file size", 0.0, sizeFast2Furious, 0.001)
    }

    @Test
    fun testHdrRegexCaseInsensitiveAndPlus() {
        val format1 = StreamParserUtil.parseHdrFormat("Inception.hdr10+.mkv")
        assertEquals("HDR10+", format1)

        val format2 = StreamParserUtil.parseHdrFormat("Inception.hdr10plus.mkv")
        assertEquals("HDR10+", format2)

        val format3 = StreamParserUtil.parseHdrFormat("Inception.HDR10.mkv")
        assertEquals("HDR10", format3)
    }

    @Test
    fun testHdrDvMultipleSpaces() {
        val format = StreamParserUtil.parseHdrFormat("Inception Dolby      Vision 4K.mkv")
        assertEquals("DV", format)
    }

    @Test
    fun testSeedsRegexMultipleEmojisAndPrefix() {
        val seeds1 = StreamParserUtil.parseSeeds("👥 145")
        assertEquals(145, seeds1)

        val seeds2 = StreamParserUtil.parseSeeds("👤 85")
        assertEquals(85, seeds2)

        val seeds3 = StreamParserUtil.parseSeeds("S: 99")
        assertEquals(99, seeds3)
    }

    @Test
    fun testAdversarialStreamNameEdgeCases() {
        // Nested brackets: e.g. [Torrentio] (Dolby Vision) Movie [4K] [Remux]
        val format = StreamParserUtil.parseHdrFormat("[Torrentio] (Dolby Vision) Movie [4K] [Remux]")
        assertEquals("DV", format)

        // Multiple spaces in Dolby Vision
        val formatSpaces = StreamParserUtil.parseHdrFormat("Movie Dolby        Vision 1080p")
        assertEquals("DV", formatSpaces)

        // Seeders: s: 120, S:120, 👤 12, 👥 500
        assertEquals(120, StreamParserUtil.parseSeeds("s: 120"))
        assertEquals(120, StreamParserUtil.parseSeeds("S:120"))
        assertEquals(12, StreamParserUtil.parseSeeds("👤 12"))
        assertEquals(500, StreamParserUtil.parseSeeds("👥 500"))
    }

    @Test
    fun testStreamRankerBestMatchStrategy() {
        // Under BEST_MATCH:
        // Exclude files >20GB (heavy penalty like -150)
        // Sweet spot size (2-8GB gets +30)
        // 1080p gets +80, 4K gets +60 if under 15GB
        
        val heavy4K = MediaStreamEntity(
            id = "heavy-4k",
            mediaId = "m1",
            title = "Movie 4K Remux 👥 100",
            url = "http://heavy",
            resolution = "4K",
            codec = "HEVC",
            quality = "UHD",
            sizeInBytes = 25L * 1024 * 1024 * 1024,
            language = "English",
            isSubbed = false,
            isDubbed = false,
            source = "ext-torrentio",
            updatedAt = System.currentTimeMillis()
        )

        val sweet1080p = MediaStreamEntity(
            id = "sweet-1080p",
            mediaId = "m1",
            title = "Movie 1080p HEVC 👥 100",
            url = "http://sweet",
            resolution = "1080p",
            codec = "HEVC",
            quality = "FHD",
            sizeInBytes = 5L * 1024 * 1024 * 1024,
            language = "English",
            isSubbed = false,
            isDubbed = false,
            source = "ext-torrentio",
            updatedAt = System.currentTimeMillis()
        )

        val ranked = StreamRanker.rankWithSignals(
            streams = listOf(heavy4K, sweet1080p),
            preferredAudio = emptyList(),
            preferredSub = emptyList(),
            streamSuccessCount = { 0 },
            streamFailureCount = { 0 },
            strategy = SortingPreference.BEST_MATCH
        )

        // Sweet 1080p should rank first because heavy 4K (>20GB) gets penalized by -150.
        assertEquals("sweet-1080p", ranked[0].id)
    }

    @Test
    fun testStreamRankerHighestQualityStrategy() {
        // Under HIGHEST_QUALITY:
        // Prioritize 4K (+120) and 1080p (+60)
        // Heavy sizes (>=40GB gets +100, >=20GB gets +50)
        
        val heavy4K = MediaStreamEntity(
            id = "heavy-4k",
            mediaId = "m1",
            title = "Movie 4K Remux 👥 100",
            url = "http://heavy",
            resolution = "4K",
            codec = "HEVC",
            quality = "UHD",
            sizeInBytes = 45L * 1024 * 1024 * 1024,
            language = "English",
            isSubbed = false,
            isDubbed = false,
            source = "ext-torrentio",
            updatedAt = System.currentTimeMillis()
        )

        val sweet1080p = MediaStreamEntity(
            id = "sweet-1080p",
            mediaId = "m1",
            title = "Movie 1080p HEVC 👥 100",
            url = "http://sweet",
            resolution = "1080p",
            codec = "HEVC",
            quality = "FHD",
            sizeInBytes = 5L * 1024 * 1024 * 1024,
            language = "English",
            isSubbed = false,
            isDubbed = false,
            source = "ext-torrentio",
            updatedAt = System.currentTimeMillis()
        )

        val ranked = StreamRanker.rankWithSignals(
            streams = listOf(sweet1080p, heavy4K),
            preferredAudio = emptyList(),
            preferredSub = emptyList(),
            streamSuccessCount = { 0 },
            streamFailureCount = { 0 },
            strategy = SortingPreference.HIGHEST_QUALITY
        )

        // Heavy 4K should rank first under HIGHEST_QUALITY
        assertEquals("heavy-4k", ranked[0].id)
    }

    @Test
    fun testAdversarialSortingStrategiesVerification() {
        // 1. Verify that BEST_MATCH sorting strategy penalizes and filters out files larger than 20GB and heavy remuxes
        val hugeFile = MediaStreamEntity(
            id = "huge-file",
            mediaId = "m1",
            title = "Movie 1080p HEVC 👥 100 25GB",
            url = "http://huge",
            resolution = "1080p",
            codec = "HEVC",
            quality = "FHD",
            sizeInBytes = 25L * 1024 * 1024 * 1024,
            language = "English",
            isSubbed = false,
            isDubbed = false,
            source = "ext-torrentio",
            updatedAt = System.currentTimeMillis()
        )

        val heavyRemux = MediaStreamEntity(
            id = "heavy-remux",
            mediaId = "m1",
            title = "Movie 1080p REMUX HEVC 👥 100 8GB",
            url = "http://remux",
            resolution = "1080p",
            codec = "HEVC",
            quality = "REMUX",
            sizeInBytes = 8L * 1024 * 1024 * 1024,
            language = "English",
            isSubbed = false,
            isDubbed = false,
            source = "ext-torrentio",
            updatedAt = System.currentTimeMillis()
        )

        val standard1080p = MediaStreamEntity(
            id = "standard-1080p",
            mediaId = "m1",
            title = "Movie 1080p HEVC 👥 100 5GB",
            url = "http://standard",
            resolution = "1080p",
            codec = "HEVC",
            quality = "FHD",
            sizeInBytes = 5L * 1024 * 1024 * 1024,
            language = "English",
            isSubbed = false,
            isDubbed = false,
            source = "ext-torrentio",
            updatedAt = System.currentTimeMillis()
        )

        val rankedBestMatch = StreamRanker.rankWithSignals(
            streams = listOf(hugeFile, heavyRemux, standard1080p),
            preferredAudio = emptyList(),
            preferredSub = emptyList(),
            streamSuccessCount = { 0 },
            streamFailureCount = { 0 },
            strategy = SortingPreference.BEST_MATCH
        )

        // Standard should be first
        assertEquals("standard-1080p", rankedBestMatch[0].id)
        // heavy-remux should rank second (penalized -40 for remux, but huge-file is penalized -150 for >20GB)
        assertEquals("heavy-remux", rankedBestMatch[1].id)
        assertEquals("huge-file", rankedBestMatch[2].id)

        // 2. Verify that HIGHEST_QUALITY ranks 4K HDR Dolby Vision and heavy remuxes at the top
        val rankedHighestQuality = StreamRanker.rankWithSignals(
            streams = listOf(standard1080p, heavyRemux, hugeFile),
            preferredAudio = emptyList(),
            preferredSub = emptyList(),
            streamSuccessCount = { 0 },
            streamFailureCount = { 0 },
            strategy = SortingPreference.HIGHEST_QUALITY
        )

        // hugeFile has 25GB (>=20GB gets +50) and 1080p (+60) = 110
        // heavyRemux has 8GB (no size bonus), 1080p (+60), REMUX (wait, does remux get bonus? No, but no size bonus) = 60
        // standard1080p has 5GB (no size bonus), 1080p (+60) = 60
        // Let's create a 4K HDR Dolby Vision stream:
        val dv4K = MediaStreamEntity(
            id = "dv-4k",
            mediaId = "m1",
            title = "Movie 4K Dolby Vision HDR10 HEVC Atmos 45GB",
            url = "http://dv4k",
            resolution = "4K",
            codec = "HEVC",
            quality = "UHD",
            sizeInBytes = 45L * 1024 * 1024 * 1024,
            language = "English",
            isSubbed = false,
            isDubbed = false,
            source = "ext-torrentio",
            updatedAt = System.currentTimeMillis()
        )

        val rankedHQWithDV = StreamRanker.rankWithSignals(
            streams = listOf(standard1080p, dv4K, hugeFile),
            preferredAudio = emptyList(),
            preferredSub = emptyList(),
            streamSuccessCount = { 0 },
            streamFailureCount = { 0 },
            strategy = SortingPreference.HIGHEST_QUALITY
        )

        // dv4k should be ranked first under HIGHEST_QUALITY
        assertEquals("dv-4k", rankedHQWithDV[0].id)
    }
}
