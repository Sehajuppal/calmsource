package com.example.calmsource.core.sourceintelligence.parsers

import com.example.calmsource.core.sourceintelligence.models.SourceHdrFormat
import com.example.calmsource.core.sourceintelligence.models.SourceQuality
import com.example.calmsource.core.sourceintelligence.models.SourceReleaseType
import com.example.calmsource.core.sourceintelligence.models.SourceResolution
import com.example.calmsource.core.sourceintelligence.models.SourceVideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityParserTest {

    @Test
    fun testParseResolution() {
        assertEquals(SourceResolution.UHD_4K, QualityParser.parseResolution("Some.Movie.2023.2160p.WEB-DL"))
        assertEquals(SourceResolution.UHD_4K, QualityParser.parseResolution("Movie 4k release"))
        assertEquals(SourceResolution.UHD_4K, QualityParser.parseResolution("Movie.UHD.Remux"))
        assertEquals(SourceResolution.FHD, QualityParser.parseResolution("Another.Movie.1080p.x264"))
        assertEquals(SourceResolution.FHD, QualityParser.parseResolution("Another.Movie.FHD.x264"))
        assertEquals(SourceResolution.HD, QualityParser.parseResolution("Show.S01E01.720p.HDTV"))
        assertEquals(SourceResolution.HD, QualityParser.parseResolution("Show.S01E01.HD.HDTV"))
        assertEquals(SourceResolution.SD, QualityParser.parseResolution("Old.Movie.480p.DVD"))
        assertEquals(SourceResolution.SD, QualityParser.parseResolution("Old.Movie.SD.DVD"))
        assertEquals(SourceResolution.UNKNOWN, QualityParser.parseResolution("No.Resolution.Info.Here"))
    }

    @Test
    fun testParseQuality() {
        assertEquals(SourceQuality.WEB, QualityParser.parseQuality("Movie.2023.1080p.WEB-DL.x264"))
        assertEquals(SourceQuality.WEB, QualityParser.parseQuality("Movie.2023.WEBRip.x265"))
        assertEquals(SourceQuality.REMUX, QualityParser.parseQuality("Movie.2160p.REMUX.HEVC"))
        assertEquals(SourceQuality.CAM, QualityParser.parseQuality("Latest.Movie.CAMRip.XviD"))
        assertEquals(SourceQuality.CAM, QualityParser.parseQuality("Latest.Movie.CAM.XviD"))
        assertEquals(SourceQuality.TELESYNC, QualityParser.parseQuality("Movie.TS.x264"))
        assertEquals(SourceQuality.BLURAY, QualityParser.parseQuality("Movie.1080p.BluRay.x264"))
        assertEquals(SourceQuality.HDTV, QualityParser.parseQuality("Movie.1080p.HDTV.x264"))
        assertEquals(SourceQuality.UNKNOWN, QualityParser.parseQuality("Movie.2023.1080p.x264"))
    }

    @Test
    fun testParseHdrFormat() {
        assertEquals(SourceHdrFormat.DOLBY_VISION, QualityParser.parseHdrFormat("Movie.2160p.DV.HDR10.HEVC"))
        assertEquals(SourceHdrFormat.DOLBY_VISION, QualityParser.parseHdrFormat("Movie.2160p.DoVi.HEVC"))
        assertEquals(SourceHdrFormat.DOLBY_VISION, QualityParser.parseHdrFormat("Movie.2160p.Dolby Vision.HEVC"))
        assertEquals(SourceHdrFormat.HDR10_PLUS, QualityParser.parseHdrFormat("Movie.2160p.HDR10+.HEVC"))
        assertEquals(SourceHdrFormat.HDR10, QualityParser.parseHdrFormat("Movie.2160p.HDR.HEVC"))
        assertEquals(SourceHdrFormat.HLG, QualityParser.parseHdrFormat("Match.2160p.HLG.HEVC"))
        assertEquals(SourceHdrFormat.SDR, QualityParser.parseHdrFormat("Movie.1080p.SDR.Wait.No.SDR.Is.Parsed"))
        assertEquals(SourceHdrFormat.SDR, QualityParser.parseHdrFormat("Movie.1080p.SDR.HEVC"))
        
        // Test DV false positive prevention
        assertEquals(SourceHdrFormat.UNKNOWN, QualityParser.parseHdrFormat("Movie.DV.XviD"))
        assertEquals(SourceHdrFormat.UNKNOWN, QualityParser.parseHdrFormat("DV.Movie.1080p.x264"))
    }

    @Test
    fun testParseVideoCodec() {
        assertEquals(SourceVideoCodec.H265, QualityParser.parseVideoCodec("Movie.2160p.HEVC.REMUX"))
        assertEquals(SourceVideoCodec.H265, QualityParser.parseVideoCodec("Movie.1080p.x265"))
        assertEquals(SourceVideoCodec.H265, QualityParser.parseVideoCodec("Movie.1080p.H.265"))
        assertEquals(SourceVideoCodec.H264, QualityParser.parseVideoCodec("Movie.1080p.x264"))
        assertEquals(SourceVideoCodec.H264, QualityParser.parseVideoCodec("Movie.1080p.AVC"))
        assertEquals(SourceVideoCodec.AV1, QualityParser.parseVideoCodec("Movie.2160p.AV1.HDR"))
        assertEquals(SourceVideoCodec.VP9, QualityParser.parseVideoCodec("Movie.2160p.VP9.WEB"))
        assertEquals(SourceVideoCodec.UNKNOWN, QualityParser.parseVideoCodec("Movie.1080p.UnknownCodec"))
    }

    @Test
    fun testParseReleaseType() {
        assertEquals(SourceReleaseType.EXTENDED, QualityParser.parseReleaseType("Movie.2023.EXTENDED.1080p"))
        assertEquals(SourceReleaseType.DIRECTORS_CUT, QualityParser.parseReleaseType("Movie.Directors.Cut.1080p"))
        assertEquals(SourceReleaseType.IMAX, QualityParser.parseReleaseType("Movie.IMAX.2160p"))
        assertEquals(SourceReleaseType.UNRATED, QualityParser.parseReleaseType("Movie.UNRATED.1080p"))
        assertEquals(SourceReleaseType.UNKNOWN, QualityParser.parseReleaseType("Movie.1080p.WEB-DL"))
    }

    @Test
    fun testIsLowQuality() {
        assertTrue(QualityParser.isLowQuality(SourceQuality.CAM))
        assertTrue(QualityParser.isLowQuality(SourceQuality.TELESYNC))
        assertTrue(QualityParser.isLowQuality(SourceQuality.TELECINEMA))
        assertTrue(QualityParser.isLowQuality(SourceQuality.SCR))
        assertFalse(QualityParser.isLowQuality(SourceQuality.WEB))
        assertFalse(QualityParser.isLowQuality(SourceQuality.BLURAY))
        assertFalse(QualityParser.isLowQuality(SourceQuality.REMUX))
    }
}
