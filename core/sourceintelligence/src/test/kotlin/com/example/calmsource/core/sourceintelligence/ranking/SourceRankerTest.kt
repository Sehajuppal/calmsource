package com.example.calmsource.core.sourceintelligence.ranking

import com.example.calmsource.core.model.SortingPreference
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.sourceintelligence.models.ParsedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRankerTest {

    private val ranker = SourceRanker()

    private fun source(
        title: String,
        quality: String? = null,
        sizeBytes: Long? = null,
        seeders: Int? = null,
        rawUrl: String = "https://example.com/stream"
    ) = ParsedSource(
        id = "src-$title",
        type = PlaybackSourceType.EXTENSION,
        title = title,
        quality = quality,
        sizeBytes = sizeBytes,
        seeders = seeders,
        origin = "test",
        rawUrl = rawUrl
    )

    @Test
    fun `empty list returns empty`() {
        assertTrue(ranker.rank(emptyList()).isEmpty())
    }

    @Test
    fun `single source returns that source`() {
        val s = source("Movie A", "1080p")
        val result = ranker.rank(listOf(s))
        assertEquals(1, result.size)
        assertEquals("Movie A", result.first().title)
    }

    @Test
    fun `4K ranks above 1080p`() {
        val s1 = source("Movie A", "4K", rawUrl = "https://example.com/4k")
        val s2 = source("Movie B", "1080p", rawUrl = "https://example.com/fhd")
        val result = ranker.rank(listOf(s1, s2), strategy = SortingPreference.HIGHEST_QUALITY)
        assertEquals("Movie A", result.first().title)
    }

    @Test
    fun `1080p ranks above 720p`() {
        val a = source("Movie A", "1080p", rawUrl = "https://example.com/fhd")
        val b = source("Movie B", "720p", rawUrl = "https://example.com/hd")
        val result = ranker.rank(listOf(a, b))
        assertEquals("Movie A", result.first().title)
    }

    @Test
    fun `REMUX ranks above BluRay in highest quality mode`() {
        val remux = source("Movie A REMUX 4K", "REMUX", rawUrl = "https://example.com/remux")
        val br = source("Movie B BluRay 1080p", "BluRay", rawUrl = "https://example.com/bluray")
        val result = ranker.rank(
            listOf(remux, br),
            strategy = SortingPreference.HIGHEST_QUALITY
        )
        assertEquals("Movie A REMUX 4K", result.first().title)
    }

    @Test
    fun `BluRay ranks above WEB-DL`() {
        val br = source("Movie A", "BluRay", rawUrl = "https://example.com/br")
        val web = source("Movie B", "WEB-DL", rawUrl = "https://example.com/web")
        val result = ranker.rank(listOf(br, web))
        assertEquals("Movie A", result.first().title)
    }

    @Test
    fun `seeders break ties when scores are equal`() {
        val seeded = source("Movie A", "1080p", seeders = 150)
        val unseeded = source("Movie B", "1080p", seeders = 0)
        val result = ranker.rank(listOf(seeded, unseeded))
        assertEquals("Movie A", result.first().title)
    }

    @Test
    fun `size breaks ties when scores and seeders are equal`() {
        val big = source("Movie A", "1080p", sizeBytes = 4L * 1024 * 1024 * 1024)
        val small = source("Movie B", "1080p", sizeBytes = 2L * 1024 * 1024 * 1024)
        val result = ranker.rank(listOf(big, small))
        assertEquals("Movie A", result.first().title)
    }

    @Test
    fun `Dolby Vision source ranks higher than SDR with same resolution`() {
        val dv = source("Movie DV", "4K Dolby Vision", rawUrl = "https://example.com/dv")
        val sdr = source("Movie SDR", "4K", rawUrl = "https://example.com/sdr")
        val result = ranker.rank(listOf(dv, sdr))
        assertEquals("Movie DV", result.first().title)
    }

    @Test
    fun `Atmos source ranks higher than stereo with same resolution`() {
        val atmos = source("Movie Atmos", "4K Atmos", rawUrl = "https://example.com/atmos")
        val stereo = source("Movie Stereo", "4K", rawUrl = "https://example.com/stereo")
        val result = ranker.rank(listOf(atmos, stereo))
        assertEquals("Movie Atmos", result.first().title)
    }

    @Test
    fun `HEVC source ranks higher than H264 with same resolution`() {
        val hevc = source("Movie HEVC", "4K HEVC", rawUrl = "https://example.com/hevc")
        val h264 = source("Movie H264", "4K x264", rawUrl = "https://example.com/h264")
        val result = ranker.rank(listOf(hevc, h264))
        assertEquals("Movie HEVC", result.first().title)
    }

    @Test
    fun `huge file size is penalized`() {
        val huge = source("Movie Huge", "1080p", sizeBytes = 30L * 1024 * 1024 * 1024)
        val normal = source("Movie Normal", "720p", sizeBytes = 2L * 1024 * 1024 * 1024)
        val result = ranker.rank(listOf(huge, normal))
        // Huge size penalty may drop it below 720p depending on score calculation
        assertEquals("Movie Normal", result.first().title)
    }

    @Test
    fun `input order does not affect output order`() {
        val a = source("Movie A", "1080p")
        val b = source("Movie B", "4K")
        val c = source("Movie C", "720p")
        val resultAsc = ranker.rank(listOf(a, b, c)).map { it.title }
        val resultDesc = ranker.rank(listOf(c, b, a)).map { it.title }
        assertEquals(resultAsc, resultDesc)
    }

    @Test
    fun `all sources with unknown resolution still produce deterministic order`() {
        val sources = listOf(
            source("Movie C"),
            source("Movie A"),
            source("Movie B")
        )
        val result = ranker.rank(sources)
        assertEquals(3, result.size)
        // Order is deterministic by seeders then size (all zero), so original order is stable
        assertEquals("Movie C", result[0].title)
        assertEquals("Movie A", result[1].title)
        assertEquals("Movie B", result[2].title)
    }

    @Test
    fun `many sources do not crash`() {
        val sources = (1..200).map { i ->
            source("Movie $i", if (i % 2 == 0) "4K" else "1080p", seeders = i)
        }
        val result = ranker.rank(sources)
        assertEquals(200, result.size)
    }
}
