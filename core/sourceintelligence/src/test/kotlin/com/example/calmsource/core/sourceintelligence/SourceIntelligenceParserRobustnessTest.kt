package com.example.calmsource.core.sourceintelligence

import com.example.calmsource.core.sourceintelligence.models.RawSourceInput
import com.example.calmsource.core.sourceintelligence.models.SourceAudioChannelLayout
import com.example.calmsource.core.sourceintelligence.models.SourceAudioFormat
import com.example.calmsource.core.sourceintelligence.models.SourceHdrFormat
import com.example.calmsource.core.sourceintelligence.models.SourceLanguageInfo
import com.example.calmsource.core.sourceintelligence.models.SourceQuality
import com.example.calmsource.core.sourceintelligence.models.SourceResolution
import com.example.calmsource.core.sourceintelligence.models.SourceVideoCodec
import com.example.calmsource.core.sourceintelligence.parsers.FileSizeAndPracticalityParser
import com.example.calmsource.core.sourceintelligence.parsers.LanguageAndAudioParser
import com.example.calmsource.core.sourceintelligence.parsers.QualityParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceIntelligenceParserRobustnessTest {

    private val languageAudioParser = LanguageAndAudioParser
    private val fileSizeParser = FileSizeAndPracticalityParser

    @Test
    fun `test simple 1080p English fixture`() {
        val filename = SanitizedTestFixtures.SIMPLE_1080P_ENGLISH

        // Language & Audio
        val lang = languageAudioParser.parseLanguage(filename)
        // Note: the filename only has English if we look at standard tags or 'English'
        // Actually, SIMPLE_1080P_ENGLISH doesn't explicitly have the word "English" in the filename right now, wait
        // The fixture is: Movie.Name.2023.1080p.BluRay.x264-MockGroup.mkv
        // So language should be UNKNOWN or empty list because it's implicit
        assertTrue(lang.languages.isEmpty())

        // Quality
        assertEquals(SourceResolution.FHD, QualityParser.parseResolution(filename))
        assertEquals(SourceQuality.BLURAY, QualityParser.parseQuality(filename))
        assertEquals(SourceVideoCodec.H264, QualityParser.parseVideoCodec(filename))
    }

    @Test
    fun `test HDR 4K Dolby Vision Atmos fixture`() {
        val filename = SanitizedTestFixtures.HDR_4K_DV_ATMOS
        // Movie.Name.2023.2160p.WEB-DL.DV.HDR10+.DDP5.1.Atmos.x265-MockGroup.mkv

        assertEquals(SourceResolution.UHD_4K, QualityParser.parseResolution(filename))
        assertEquals(SourceQuality.WEB, QualityParser.parseQuality(filename))
        assertEquals(SourceHdrFormat.DOLBY_VISION, QualityParser.parseHdrFormat(filename))
        assertEquals(SourceVideoCodec.H265, QualityParser.parseVideoCodec(filename))

        assertEquals(SourceAudioFormat.EAC3, languageAudioParser.parseAudioFormat(filename))
        assertEquals(SourceAudioChannelLayout.ATMOS, languageAudioParser.parseAudioChannelLayout(filename))
    }

    @Test
    fun `test Hindi-English Dual Audio fixture`() {
        val filename = SanitizedTestFixtures.HINDI_ENGLISH_DUAL
        // Movie.Name.2023.1080p.WEB-DL.Dual-Audio.Hindi.English.AAC.2.0.x264-MockGroup.mp4

        val lang = languageAudioParser.parseLanguage(filename)
        assertTrue(lang.isDualAudio)
        assertTrue(lang.languages.contains("Hindi"))
        assertTrue(lang.languages.contains("English"))

        assertEquals(SourceAudioFormat.AAC, languageAudioParser.parseAudioFormat(filename))
        assertEquals(SourceAudioChannelLayout.STEREO, languageAudioParser.parseAudioChannelLayout(filename))
    }

    @Test
    fun `test CAM TS Low Quality fixture`() {
        val filename = SanitizedTestFixtures.CAM_TS_LOW_QUALITY
        // Movie.Name.2023.CAMRip.TS.XviD.MP3-MockGroup.avi

        val quality = QualityParser.parseQuality(filename)
        assertTrue(quality == SourceQuality.CAM || quality == SourceQuality.TELESYNC)
        assertTrue(QualityParser.isLowQuality(quality))

        assertEquals(SourceVideoCodec.XVID, QualityParser.parseVideoCodec(filename))
        assertEquals(SourceAudioFormat.MP3, languageAudioParser.parseAudioFormat(filename))
    }

    @Test
    fun `test Huge REMUX fixture`() {
        val filename = SanitizedTestFixtures.HUGE_REMUX
        // Movie.Name.2023.2160p.BluRay.REMUX.HEVC.DTS-HD.MA.TrueHD.7.1.Atmos-MockGroup.mkv

        assertEquals(SourceQuality.REMUX, QualityParser.parseQuality(filename))
        assertEquals(SourceResolution.UHD_4K, QualityParser.parseResolution(filename))
        assertEquals(SourceVideoCodec.H265, QualityParser.parseVideoCodec(filename)) // HEVC maps to H265

        // File size parser test requires a raw input
        val input = RawSourceInput(filename, "Movie Name 2023 60GB", null)
        val features = fileSizeParser.parse(input)
        assertTrue(features.isHugeSize)
        assertEquals(60L * 1024 * 1024 * 1024, features.sizeBytes)
    }

    @Test
    fun `test malformed and weird filenames do not crash`() {
        val filename = SanitizedTestFixtures.MALFORMED_FILENAME
        // [ www.MockSite.com ] - Movie Name (2023) [1080p] {x264} (Multi_Audio) @MockUser

        assertEquals(SourceResolution.FHD, QualityParser.parseResolution(filename))
        assertEquals(SourceVideoCodec.H264, QualityParser.parseVideoCodec(filename))

        val lang = languageAudioParser.parseLanguage(filename)
        assertTrue(lang.isMultiAudio)
    }

    @Test
    fun `test edge cases with empty and null values`() {
        // Parsers should gracefully handle empty strings without exceptions
        assertEquals(SourceResolution.UNKNOWN, QualityParser.parseResolution(""))
        assertEquals(SourceQuality.UNKNOWN, QualityParser.parseQuality("   "))
        
        val langInfo = languageAudioParser.parseLanguage(null, null)
        assertEquals(SourceLanguageInfo.UNKNOWN, langInfo)
        
        val audioFmt = languageAudioParser.parseAudioFormat("    ", "")
        assertEquals(SourceAudioFormat.UNKNOWN, audioFmt)
        
        val features = fileSizeParser.parse(RawSourceInput(null, null, null))
        assertEquals(0L, features.sizeBytes)
        assertFalse(features.isHugeSize)

        // Process should not crash
        val result = SourceIntelligence.process(RawSourceInput("", "", null))
        assertEquals(SourceResolution.UNKNOWN, result.parsedMetadata.resolution)
    }

    @Test
    fun `test file size extraction robustness`() {
        // Various size formats
        assertEquals(1536L * 1024 * 1024, fileSizeParser.parse(RawSourceInput("Movie.1.5GB.mkv", null, null)).sizeBytes)
        assertEquals(500L * 1024 * 1024, fileSizeParser.parse(RawSourceInput("Movie.500MB.mkv", null, null)).sizeBytes)
        assertEquals(1024L, fileSizeParser.parse(RawSourceInput("Movie.1KB.mkv", null, null)).sizeBytes)
        
        // Ensure no crash on giant numbers
        val features = fileSizeParser.parse(RawSourceInput("Movie.999999999999GB.mkv", null, null))
        assertTrue(features.sizeBytes > 0)
    }

    @Test
    fun `test conflicting metadata lowers confidence`() {
        val input = RawSourceInput("Movie.1080p.4K.CAM.BluRay.mkv", null, null)
        val result = SourceIntelligence.process(input)
        assertTrue("Confidence should be lowered due to conflicting resolutions and qualities", result.confidence.score < 0.9f)
        assertTrue("Reasons should include conflicting metadata", result.confidence.reasons.any { it.contains("Conflicting") })
    }

    @Test
    fun `test very long source name handles gracefully`() {
        val longName = "Movie.Name." + "VeryLong.".repeat(100) + "1080p.mkv"
        val input = RawSourceInput(longName, null, null)
        val result = SourceIntelligence.process(input)
        assertEquals(SourceResolution.FHD, result.parsedMetadata.resolution)
    }

    @Test
    fun `test deterministic parser output`() {
        val input = RawSourceInput("Movie.1080p.BluRay.mkv", null, null)
        val result1 = SourceIntelligence.process(input)
        val result2 = SourceIntelligence.process(input)
        assertEquals(result1.parsedMetadata, result2.parsedMetadata)
        assertEquals(result1.displayLabel, result2.displayLabel)
        assertEquals(result1.confidence, result2.confidence)
        assertEquals(result1.rankingFeatures, result2.rankingFeatures)
    }

    @Test
    fun `test provider-agnostic parsing`() {
        val input1 = RawSourceInput("ProviderA_Movie_1080p", null, null)
        val input2 = RawSourceInput("ProviderB_Movie_1080p", null, null)
        val result1 = SourceIntelligence.process(input1)
        val result2 = SourceIntelligence.process(input2)
        assertEquals(result1.parsedMetadata.resolution, result2.parsedMetadata.resolution)
    }

    @Test
    fun `test no raw filename in default display model`() {
        val rawFilename = "Secret.Private.Tracker.Movie.1080p.mkv"
        val input = RawSourceInput(rawFilename, null, null)
        val result = SourceIntelligence.process(input)
        assertFalse("Display label should not contain raw filename", result.displayLabel.primaryLabel.contains("Secret"))
        assertFalse("Display label should not contain raw filename", result.displayLabel.secondaryLabel.contains("Secret"))
    }
}
