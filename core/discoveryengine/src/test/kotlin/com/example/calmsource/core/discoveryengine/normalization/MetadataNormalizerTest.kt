package com.example.calmsource.core.discoveryengine.normalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataNormalizerTest {

    @Test
    fun testNormalizeTitle() {
        assertEquals("inception 2010", MetadataNormalizer.normalizeTitle("Inception (2010)"))
        assertEquals("spider man into the spider verse", MetadataNormalizer.normalizeTitle("Spider-Man: Into the Spider-Verse"))
        assertEquals("cafe", MetadataNormalizer.normalizeTitle("Café"))
        assertEquals("the lord of the rings", MetadataNormalizer.normalizeTitle("The Lord... of the Rings!!!"))
        assertEquals("", MetadataNormalizer.normalizeTitle("   "))
    }

    @Test
    fun testNormalizeChannelName() {
        assertEquals("hbo", MetadataNormalizer.normalizeChannelName("US: HBO HD (Backup)"))
        assertEquals("canal sport", MetadataNormalizer.normalizeChannelName("FR | CANAL+ SPORT FHD"))
        assertEquals("action", MetadataNormalizer.normalizeChannelName("[ES] Action 1080p"))
        assertEquals("sky news", MetadataNormalizer.normalizeChannelName("uk- sky news raw"))
    }

    @Test
    fun testGenerateTitleAliases() {
        val aliases = MetadataNormalizer.generateTitleAliases("Spider-Man: Into the Spider-Verse")
        assertTrue(aliases.contains("spider man into the spider verse"))
        assertTrue(aliases.contains("spidermanintothespiderverse"))
        assertTrue(aliases.contains("spider man"))
        assertTrue(aliases.contains("into the spider verse"))
    }

    @Test
    fun testGenerateChannelAliases() {
        val aliases = MetadataNormalizer.generateChannelAliases("canal+ sport")
        assertTrue(aliases.contains("canal sport"))
        assertTrue(aliases.contains("canalsport"))
        assertTrue(aliases.contains("canal plus sport"))
        assertTrue(aliases.contains("canalplussport"))
    }

    @Test
    fun testRemoveStreamNoise() {
        val stream1 = "[AIO Streams] Inception.2010.1080p.BluRay.x264.DTS-HD.MA.5.1-FGT"
        assertEquals("inception 2010", MetadataNormalizer.removeStreamNoise(stream1))

        val stream2 = "Spider-Man Into the Spider-Verse (2018) Multi-Audio Dual 1080p HEVC x265"
        assertEquals("spider man into the spider verse 2018", MetadataNormalizer.removeStreamNoise(stream2))
    }
}
