package com.example.calmsource.core.sourceintelligence.parsers

import com.example.calmsource.core.sourceintelligence.models.RawSourceInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSizeParserTest {

    private val parser = FileSizeAndPracticalityParser

    @Test
    fun `test parsing GB sizes`() {
        val input = RawSourceInput(rawFilename = "movie_1080p_2.5GB.mkv", rawTitle = null, rawUrl = null)
        val features = parser.parse(input)
        
        val expectedBytes = (2.5 * 1024 * 1024 * 1024).toLong()
        assertEquals(expectedBytes, features.sizeBytes)
        assertFalse(features.isLowDataSuitable)
        assertFalse(features.requiresHighBandwidth)
        assertFalse(features.isHugeSize)
        assertEquals(50, features.practicalScore)
    }

    @Test
    fun `test parsing GiB sizes`() {
        val input = RawSourceInput(rawFilename = "movie_1080p_2.5GiB.mkv", rawTitle = null, rawUrl = null)
        val features = parser.parse(input)
        
        val expectedBytes = (2.5 * 1024 * 1024 * 1024).toLong()
        assertEquals(expectedBytes, features.sizeBytes)
    }

    @Test
    fun `test parsing MB sizes`() {
        val input = RawSourceInput(rawFilename = "movie_720p_800MB.mp4", rawTitle = null, rawUrl = null)
        val features = parser.parse(input)
        
        val expectedBytes = (800.0 * 1024 * 1024).toLong()
        assertEquals(expectedBytes, features.sizeBytes)
        assertTrue(features.isLowDataSuitable)
        assertFalse(features.requiresHighBandwidth)
        assertFalse(features.isHugeSize)
        assertEquals(70, features.practicalScore) // 50 + 20
    }

    @Test
    fun `test parsing huge size over 20GB`() {
        val input = RawSourceInput(rawFilename = "remux_4k_22GB.mkv", rawTitle = null, rawUrl = null)
        val features = parser.parse(input)
        
        val expectedBytes = (22.0 * 1024 * 1024 * 1024).toLong()
        assertEquals(expectedBytes, features.sizeBytes)
        assertFalse(features.isLowDataSuitable)
        assertTrue(features.requiresHighBandwidth)
        assertTrue(features.isHugeSize)
        assertEquals(30, features.practicalScore) // 50 - 20
    }

    @Test
    fun `test low data mode with huge size`() {
        val input = RawSourceInput(rawFilename = "remux_4k_22GB.mkv", rawTitle = null, rawUrl = null)
        val features = parser.parse(input, lowDataModeEnabled = true)
        
        assertTrue(features.isHugeSize)
        // 50 - 20 (requiresHighBandwidth) - 50 (lowData & huge) = -20 -> coerced to 0
        assertEquals(0, features.practicalScore)
    }

    @Test
    fun `test high bandwidth size below huge threshold`() {
        val input = RawSourceInput(rawFilename = "remux_4k_12GB.mkv", rawTitle = null, rawUrl = null)
        val features = parser.parse(input)

        assertTrue(features.requiresHighBandwidth)
        assertFalse(features.isHugeSize)
        assertEquals(30, features.practicalScore)
    }

    @Test
    fun `test low data mode with small size`() {
        val input = RawSourceInput(rawFilename = "show_480p_200MB.mkv", rawTitle = null, rawUrl = null)
        val features = parser.parse(input, lowDataModeEnabled = true)
        
        assertTrue(features.isLowDataSuitable)
        // 50 + 20 (isLowDataSuitable) + 30 (lowData & isLowDataSuitable) = 100
        assertEquals(100, features.practicalScore)
    }

    @Test
    fun `test parsing from rawTitle when rawFilename has no size`() {
        val input = RawSourceInput(rawFilename = "movie.mkv", rawTitle = "Movie 2024 1080p 1.5 GiB", rawUrl = null)
        val features = parser.parse(input)
        
        val expectedBytes = (1.5 * 1024 * 1024 * 1024).toLong()
        assertEquals(expectedBytes, features.sizeBytes)
    }
    
    @Test
    fun `test unparsable size defaults to 0`() {
        val input = RawSourceInput(rawFilename = "movie_1080p.mkv", rawTitle = "Some movie", rawUrl = null)
        val features = parser.parse(input)
        
        assertEquals(0L, features.sizeBytes)
        assertFalse(features.isLowDataSuitable)
        assertFalse(features.requiresHighBandwidth)
        assertFalse(features.isHugeSize)
        assertEquals(50, features.practicalScore)
    }
}
