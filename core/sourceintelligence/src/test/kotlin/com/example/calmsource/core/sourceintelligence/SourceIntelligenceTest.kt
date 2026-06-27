package com.example.calmsource.core.sourceintelligence

import com.example.calmsource.core.sourceintelligence.models.RawSourceInput
import com.example.calmsource.core.sourceintelligence.models.SourceAudioChannelLayout
import com.example.calmsource.core.sourceintelligence.models.SourceAudioFormat
import com.example.calmsource.core.sourceintelligence.models.SourceLanguageInfo
import com.example.calmsource.core.sourceintelligence.models.SourceSubtitleInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceIntelligenceTest {

    @Test
    fun testPrimarySecondaryLanguageLabelOrdering() {
        val input = RawSourceInput("Movie.1080p.Dual.Audio.ENG.HIN.mkv", null, null)
        val preferredLanguages = listOf("Hindi", "English")
        
        val result = SourceIntelligence.process(input, preferredLanguages)
        
        // Ensure "Hindi + English" ordering based on preferences
        assertEquals("Hindi + English • 1080p", result.displayLabel.primaryLabel)
    }
    
    @Test
    fun testEnglishOnlyLabel() {
        val input = RawSourceInput("Movie.1080p.ENG.mkv", null, null)
        
        val result = SourceIntelligence.process(input, emptyList())
        
        // English-only label
        assertEquals("English • 1080p", result.displayLabel.primaryLabel)
    }
    
    @Test
    fun testUnknownLanguageSafeLabel() {
        val input = RawSourceInput("Movie.1080p.mkv", null, null)
        
        val result = SourceIntelligence.process(input, emptyList())
        
        // Unknown language should NOT fallback to English
        assertEquals("1080p", result.displayLabel.primaryLabel)
    }

    @Test
    fun testAtmosAnd51Label() {
        val input = RawSourceInput("Movie.1080p.TrueHD.Atmos.mkv", null, null)
        val result = SourceIntelligence.process(input)
        
        assertEquals(SourceAudioChannelLayout.ATMOS, result.parsedMetadata.audioChannels)
        assertTrue(result.rankingFeatures.isAtmos)
        
        val input2 = RawSourceInput("Movie.1080p.DD.5.1.mkv", null, null)
        val result2 = SourceIntelligence.process(input2)
        assertEquals(SourceAudioChannelLayout.SURROUND_5_1, result2.parsedMetadata.audioChannels)
    }

    @Test
    fun testLowDataLabelMB() {
        val input = RawSourceInput("Show.S01E01.720p.350MB.mkv", null, null)
        val result = SourceIntelligence.process(input)
        
        // Ensure secondaryLabel shows MB, not 0.xx GB
        assertEquals("350 MB", result.displayLabel.secondaryLabel)
    }

    @Test
    fun testLowDataLabelGB() {
        val input = RawSourceInput("Show.S01E01.1080p.1.5GB.mkv", null, null)
        val result = SourceIntelligence.process(input)
        
        // Ensure secondaryLabel shows 1.50 GB
        assertEquals("1.50 GB", result.displayLabel.secondaryLabel)
    }
}
