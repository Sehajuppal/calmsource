package com.example.calmsource.core.sourceintelligence.parsers

import com.example.calmsource.core.sourceintelligence.models.SourceAudioChannelLayout
import com.example.calmsource.core.sourceintelligence.models.SourceAudioFormat
import com.example.calmsource.core.sourceintelligence.models.SourceLanguageInfo
import com.example.calmsource.core.sourceintelligence.models.SourceSubtitleInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageAndAudioParserTest {

    private val parser = LanguageAndAudioParser

    @Test
    fun testParseLanguage() {
        val result1 = parser.parseLanguage("Movie.2023.1080p.Dual.Audio.ENG.HIN.mkv")
        assertTrue(result1.isDualAudio)
        assertTrue(result1.languages.contains("English"))
        assertTrue(result1.languages.contains("Hindi"))
        
        val result2 = parser.parseLanguage("Movie.Multi.Audio.Dubbed.tam.tel.mkv")
        assertTrue(result2.isMultiAudio)
        assertTrue(result2.isDubbed)
        assertTrue(result2.languages.contains("Tamil"))
        assertTrue(result2.languages.contains("Telugu"))
        
        val result3 = parser.parseLanguage("Unknown.Movie.mkv")
        assertEquals(SourceLanguageInfo.UNKNOWN, result3)

        val resultFalsePositives1 = parser.parseLanguage("It.2017.1080p.mkv")
        assertFalse("Should not detect Italian for 'It'", resultFalsePositives1.languages.contains("Italian"))

        val resultFalsePositives2 = parser.parseLanguage("Say.Hi.To.My.Little.Friend.2023.mkv")
        assertFalse("Should not detect Hindi for 'Hi'", resultFalsePositives2.languages.contains("Hindi"))
    }

    @Test
    fun testSubtitleAvailabilityAndForced() {
        val sub1 = parser.parseSubtitle("Movie.1080p.HC.ENG.SUB.mkv")
        assertTrue(sub1.isAvailable)
        assertTrue(sub1.isHardcoded)
        assertFalse(sub1.isForced)

        val sub2 = parser.parseSubtitle("Movie.mkv", "Movie Title (CC)")
        assertTrue(sub2.isAvailable)
        assertFalse(sub2.isHardcoded)
        assertFalse(sub2.isForced)

        val sub3 = parser.parseSubtitle("Movie.mkv")
        assertEquals(SourceSubtitleInfo.UNKNOWN, sub3)
        assertFalse(sub3.isAvailable)

        val sub4 = parser.parseSubtitle("Movie.1080p.forced.mkv")
        assertTrue(sub4.isAvailable)
        assertTrue(sub4.isForced)
        assertFalse(sub4.isHardcoded)
        
        val sub5 = parser.parseSubtitle("Movie.1080p.subs.mkv")
        assertTrue(sub5.isAvailable)
        assertFalse(sub5.isForced)
        assertFalse(sub5.isHardcoded)
        assertTrue(sub5.languages.isEmpty()) // No overpromise
    }

    @Test
    fun testParseAudioFormat() {
        assertEquals(SourceAudioFormat.TRUEHD, parser.parseAudioFormat("Movie.TrueHD.7.1.mkv"))
        assertEquals(SourceAudioFormat.EAC3, parser.parseAudioFormat("Movie.DDP.5.1.mkv"))
        assertEquals(SourceAudioFormat.AAC, parser.parseAudioFormat("Movie.AAC.2.0.mkv"))
        assertEquals(SourceAudioFormat.DTS_X, parser.parseAudioFormat("Movie.DTS-X.mkv"))
        assertEquals(SourceAudioFormat.UNKNOWN, parser.parseAudioFormat("Movie.mkv"))
    }

    @Test
    fun testParseAudioChannelLayout() {
        assertEquals(SourceAudioChannelLayout.ATMOS, parser.parseAudioChannelLayout("Movie.TrueHD.Atmos.mkv"))
        assertEquals(SourceAudioChannelLayout.SURROUND_7_1, parser.parseAudioChannelLayout("Movie.7.1.mkv"))
        assertEquals(SourceAudioChannelLayout.SURROUND_5_1, parser.parseAudioChannelLayout("Movie.5.1.mkv"))
        assertEquals(SourceAudioChannelLayout.STEREO, parser.parseAudioChannelLayout("Movie.2.0.mkv"))
        assertEquals(SourceAudioChannelLayout.UNKNOWN, parser.parseAudioChannelLayout("Movie.mkv"))
    }
    
    @Test
    fun testEdgeCasesAndNulls() {
        assertEquals(SourceLanguageInfo.UNKNOWN, parser.parseLanguage(null, null))
        assertEquals(SourceSubtitleInfo.UNKNOWN, parser.parseSubtitle(null, null))
        assertEquals(SourceAudioFormat.UNKNOWN, parser.parseAudioFormat(null, null))
        assertEquals(SourceAudioChannelLayout.UNKNOWN, parser.parseAudioChannelLayout(null, null))
        
        assertEquals(SourceAudioFormat.DTS_HD, parser.parseAudioFormat("movie.dts-hd.ma.mkv"))
        assertEquals(SourceAudioFormat.AC3, parser.parseAudioFormat("movie.ac3.mkv"))
    }

    @Test
    fun testItalianAndHindiDetection() {
        // Valid filenames where "it" and "hi" should be detected
        assertTrue(parser.parseLanguage("Movie.1080p.it.mkv").languages.contains("Italian"))
        assertTrue(parser.parseLanguage("Movie.1080p.hi.mkv").languages.contains("Hindi"))
        assertTrue(parser.parseLanguage("Movie.2022.it.mkv").languages.contains("Italian"))
        assertTrue(parser.parseLanguage("Movie.2022.hi.mkv").languages.contains("Hindi"))
        assertTrue(parser.parseLanguage("Movie.(it).mkv").languages.contains("Italian"))
        assertTrue(parser.parseLanguage("Movie.(hi).mkv").languages.contains("Hindi"))
        assertTrue(parser.parseLanguage("Movie.[it].mkv").languages.contains("Italian"))
        assertTrue(parser.parseLanguage("Movie.[hi].mkv").languages.contains("Hindi"))

        // False positive filenames where "it" and "hi" should NOT be detected
        assertFalse(parser.parseLanguage("It.2017.mkv").languages.contains("Italian"))
        assertFalse(parser.parseLanguage("Say.Hi.To.My.Little.Friend.2023.mkv").languages.contains("Hindi"))
    }

    @Test
    fun testChallengerBoundaryLanguageInputs() {
        // Titles starting/ending with "it"
        assertFalse(parser.parseLanguage("it.2023.1080p.mkv").languages.contains("Italian"))
        assertTrue(parser.parseLanguage("Movie.2023.it").languages.contains("Italian"))
        assertFalse(parser.parseLanguage("Movie.it").languages.contains("Italian"))
        
        // Titles containing words like "white", "spirit", "history", "punjabi"
        assertFalse(parser.parseLanguage("White.Collar.S01E01.1080p.mkv").languages.contains("Italian"))
        assertFalse(parser.parseLanguage("Spirit.Untamed.2021.1080p.mkv").languages.contains("Italian"))
        assertFalse(parser.parseLanguage("History.Channel.The.Food.That.Built.America.S01.1080p.mkv").languages.contains("Hindi"))
        
        // Punjabi boundary checks
        assertFalse(parser.parseLanguage("Movie.2023.1080p.mkv").languages.contains("Punjabi"))
        
        // Test that getLanguageFullName parses "pa" to "punjabi"
        assertEquals("punjabi", parser.getLanguageFullName("pa"))
    }
}
