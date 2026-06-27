package com.example.calmsource.core.sourceintelligence

import com.example.calmsource.core.sourceintelligence.models.RawSourceInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamPickerIntegrationTest {

    @Test
    fun testLabelGeneration_HindiEnglish4KDolbyVisionCached() {
        val input = RawSourceInput(
            rawFilename = "Spider-Man.No.Way.Home.2021.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR10.HEVC.Hindi.English.Cached.mkv",
            rawTitle = "Spider-Man No Way Home",
            rawUrl = "https://example.com/stream"
        )
        
        val result = SourceIntelligence.process(input)
        
        val primaryLabel = result.displayLabel.primaryLabel
        assertTrue("Expected label to contain 'Dual Audio', 'Hindi + English', or 'English + Hindi', got: $primaryLabel",
            primaryLabel.contains("Hindi + English") || primaryLabel.contains("English + Hindi") || primaryLabel.contains("Dual Audio"))
        assertTrue("Expected label to contain '4K', got: $primaryLabel", primaryLabel.contains("4K"))
        assertTrue("Expected label to contain 'Dolby Vision', got: $primaryLabel", primaryLabel.contains("Dolby Vision"))
        assertTrue("Expected label to contain 'Cached', got: $primaryLabel", primaryLabel.contains("Cached"))
    }

    @Test
    fun testLabelGeneration_LowDataSD() {
        val input = RawSourceInput(
            rawFilename = "Some.Show.S01E01.480p.x264-GROUP.mkv",
            rawTitle = "Some Show - Episode 1",
            rawUrl = "https://example.com/stream"
        )
        
        val result = SourceIntelligence.process(input)
        
        val primaryLabel = result.displayLabel.primaryLabel
        assertTrue("Expected label to contain 'SD', got: $primaryLabel", primaryLabel.contains("SD"))
    }

    @Test
    fun testLabelGeneration_Remux() {
        val input = RawSourceInput(
            rawFilename = "Dune.2021.1080p.BluRay.REMUX.AVC.DTS-HD.MA.TrueHD.7.1.Atmos-FGT.mkv",
            rawTitle = "Dune",
            rawUrl = "https://example.com/stream"
        )
        
        val result = SourceIntelligence.process(input)
        
        val primaryLabel = result.displayLabel.primaryLabel
        assertTrue("Expected label to contain '1080p', got: $primaryLabel", primaryLabel.contains("1080p"))
        assertTrue("Expected label to contain 'Remux', got: $primaryLabel", primaryLabel.contains("Remux"))
    }

    @Test
    fun testLabelGeneration_MalformedSeparatorsFixed() {
        val input = RawSourceInput(
            rawFilename = "Some.Show.1080p.ENG.mkv",
            rawTitle = "Some Show",
            rawUrl = "https://example.com/stream"
        )
        
        val result = SourceIntelligence.process(input)
        val primaryLabel = result.displayLabel.primaryLabel
        assertTrue("Expected label to use ' • ' as separator, got: $primaryLabel", primaryLabel.contains(" • "))
    }
}
