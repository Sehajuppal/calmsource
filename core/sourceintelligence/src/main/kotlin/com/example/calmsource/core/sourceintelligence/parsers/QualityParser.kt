package com.example.calmsource.core.sourceintelligence.parsers

import com.example.calmsource.core.sourceintelligence.models.SourceHdrFormat
import com.example.calmsource.core.sourceintelligence.models.SourceQuality
import com.example.calmsource.core.sourceintelligence.models.SourceReleaseType
import com.example.calmsource.core.sourceintelligence.models.SourceResolution
import com.example.calmsource.core.sourceintelligence.models.SourceVideoCodec

/**
 * Parses raw source metadata (e.g. filename, title) into structured quality enums.
 *
 * All regex patterns are hoisted to companion object to avoid recompiling on every
 * scoring call. The SourceIntelligence pipeline invokes these parsers for every
 * source it sees — previously each call allocated dozens of Regex objects.
 */
object QualityParser {

    private val RES_8K = Regex("\\b(4320p|8k)\\b")
    private val RES_4K = Regex("\\b(2160p|4k|uhd)\\b")
    private val RES_QHD = Regex("\\b(1440p|2k|qhd)\\b")
    private val RES_FHD = Regex("\\b(1080p|1080i|fhd)\\b")
    private val RES_HD = Regex("\\b(720p|hd)\\b")
    private val RES_SD = Regex("\\b(480p|sd)\\b")

    private val QUAL_REMUX = Regex("\\b(remux)\\b")
    private val QUAL_BLURAY = Regex("\\b(bluray|bdrip|brrip|bd)\\b")
    private val QUAL_WEB = Regex("\\b(web-dl|webrip|web)\\b")
    private val QUAL_HDTV = Regex("\\b(hdtv)\\b")
    private val QUAL_DVD = Regex("\\b(dvd|dvdrip)\\b")
    private val QUAL_SCR = Regex("\\b(scr|screener)\\b")
    private val QUAL_TC = Regex("\\b(tc|telecinema)\\b")
    private val QUAL_TS = Regex("\\b(ts|telesync|hd-ts)\\b")
    private val QUAL_CAM = Regex("\\b(cam|camrip|hd-cam)\\b")

    private val HDR_DOVI = Regex("\\b(dolby\\s?vision|dovi)\\b")
    private val HDR_DV_SHORT = Regex("\\bdv\\b")
    private val HDR_DV_KEYWORDS = Regex("\\b(2160p|4k|uhd|hevc|h\\.?265|x265|hdr\\d*)\\b")
    private val HDR10_PLUS = Regex("\\b(hdr10\\+|hdr10plus)(?:\\b|[^a-zA-Z0-9]|$)")
    private val HDR10 = Regex("\\b(hdr10|hdr)\\b")
    private val HLG = Regex("\\b(hlg)\\b")
    private val SDR = Regex("\\b(sdr)\\b")

    private val CODEC_AV1 = Regex("\\b(av1)\\b")
    private val CODEC_H265 = Regex("\\b(hevc|h\\.?265|x265)\\b")
    private val CODEC_H264 = Regex("\\b(avc|h\\.?264|x264)\\b")
    private val CODEC_VP9 = Regex("\\b(vp9)\\b")
    private val CODEC_MPEG2 = Regex("\\b(mpeg2)\\b")
    private val CODEC_XVID = Regex("\\b(xvid)\\b")

    private val REL_EXT = Regex("\\b(extended|ext)\\b")
    private val REL_DC = Regex("(\\bdirectors[\\s\\.\\-\\_]cut\\b|(?:(?<=^)|(?<=[\\s\\.\\-\\_\\[\\(]))dc(?=$|[\\s\\.\\-\\_\\]\\)]))")
    private val REL_UNRATED = Regex("\\b(unrated)\\b")
    private val REL_IMAX = Regex("\\b(imax)\\b")
    private val REL_THEATRICAL = Regex("\\b(theatrical)\\b")

    /**
     * Parses the video resolution from the given string.
     */
    fun parseResolution(input: String): SourceResolution {
        val lower = input.lowercase()
        return when {
            RES_8K.containsMatchIn(lower) -> SourceResolution.UHD_8K
            RES_4K.containsMatchIn(lower) -> SourceResolution.UHD_4K
            RES_QHD.containsMatchIn(lower) -> SourceResolution.QHD
            RES_FHD.containsMatchIn(lower) -> SourceResolution.FHD
            RES_HD.containsMatchIn(lower) -> SourceResolution.HD
            RES_SD.containsMatchIn(lower) -> SourceResolution.SD
            else -> SourceResolution.UNKNOWN
        }
    }

    /**
     * Parses the release quality type from the given string.
     */
    fun parseQuality(input: String): SourceQuality {
        val lower = input.lowercase()
        return when {
            QUAL_REMUX.containsMatchIn(lower) -> SourceQuality.REMUX
            QUAL_BLURAY.containsMatchIn(lower) -> SourceQuality.BLURAY
            QUAL_WEB.containsMatchIn(lower) -> SourceQuality.WEB
            QUAL_HDTV.containsMatchIn(lower) -> SourceQuality.HDTV
            QUAL_DVD.containsMatchIn(lower) -> SourceQuality.DVD
            QUAL_SCR.containsMatchIn(lower) -> SourceQuality.SCR
            QUAL_TC.containsMatchIn(lower) -> SourceQuality.TELECINEMA
            QUAL_TS.containsMatchIn(lower) -> SourceQuality.TELESYNC
            QUAL_CAM.containsMatchIn(lower) -> SourceQuality.CAM
            else -> SourceQuality.UNKNOWN
        }
    }

    /**
     * Parses the HDR format from the given string.
     */
    fun parseHdrFormat(input: String): SourceHdrFormat {
        val lower = input.lowercase()
        return when {
            HDR_DOVI.containsMatchIn(lower) -> SourceHdrFormat.DOLBY_VISION
            HDR_DV_SHORT.containsMatchIn(lower) && HDR_DV_KEYWORDS.containsMatchIn(lower) -> SourceHdrFormat.DOLBY_VISION
            HDR10_PLUS.containsMatchIn(lower) -> SourceHdrFormat.HDR10_PLUS
            HDR10.containsMatchIn(lower) -> SourceHdrFormat.HDR10
            HLG.containsMatchIn(lower) -> SourceHdrFormat.HLG
            SDR.containsMatchIn(lower) -> SourceHdrFormat.SDR
            else -> SourceHdrFormat.UNKNOWN
        }
    }

    /**
     * Parses the video codec from the given string.
     */
    fun parseVideoCodec(input: String): SourceVideoCodec {
        val lower = input.lowercase()
        return when {
            CODEC_AV1.containsMatchIn(lower) -> SourceVideoCodec.AV1
            CODEC_H265.containsMatchIn(lower) -> SourceVideoCodec.H265
            CODEC_H264.containsMatchIn(lower) -> SourceVideoCodec.H264
            CODEC_VP9.containsMatchIn(lower) -> SourceVideoCodec.VP9
            CODEC_MPEG2.containsMatchIn(lower) -> SourceVideoCodec.MPEG2
            CODEC_XVID.containsMatchIn(lower) -> SourceVideoCodec.XVID
            else -> SourceVideoCodec.UNKNOWN
        }
    }

    /**
     * Parses the specific release type (extended, directors cut, etc).
     */
    fun parseReleaseType(input: String): SourceReleaseType {
        val lower = input.lowercase()
        return when {
            REL_EXT.containsMatchIn(lower) -> SourceReleaseType.EXTENDED
            REL_DC.containsMatchIn(lower) -> SourceReleaseType.DIRECTORS_CUT
            REL_UNRATED.containsMatchIn(lower) -> SourceReleaseType.UNRATED
            REL_IMAX.containsMatchIn(lower) -> SourceReleaseType.IMAX
            REL_THEATRICAL.containsMatchIn(lower) -> SourceReleaseType.THEATRICAL
            else -> SourceReleaseType.UNKNOWN
        }
    }

    /**
     * Utility method to determine if the parsed quality is considered low quality (e.g. CAM, TS).
     */
    fun isLowQuality(quality: SourceQuality): Boolean {
        return quality == SourceQuality.CAM || quality == SourceQuality.TELESYNC || quality == SourceQuality.TELECINEMA || quality == SourceQuality.SCR
    }

    fun parseAllResolutions(input: String): List<SourceResolution> {
        val lower = input.lowercase()
        val resolutions = mutableListOf<SourceResolution>()
        if (RES_8K.containsMatchIn(lower)) resolutions.add(SourceResolution.UHD_8K)
        if (RES_4K.containsMatchIn(lower)) resolutions.add(SourceResolution.UHD_4K)
        if (RES_QHD.containsMatchIn(lower)) resolutions.add(SourceResolution.QHD)
        if (RES_FHD.containsMatchIn(lower)) resolutions.add(SourceResolution.FHD)
        if (RES_HD.containsMatchIn(lower)) resolutions.add(SourceResolution.HD)
        if (RES_SD.containsMatchIn(lower)) resolutions.add(SourceResolution.SD)
        return resolutions.distinct()
    }

    fun parseAllQualities(input: String): List<SourceQuality> {
        val lower = input.lowercase()
        val qualities = mutableListOf<SourceQuality>()
        if (QUAL_REMUX.containsMatchIn(lower)) qualities.add(SourceQuality.REMUX)
        if (QUAL_BLURAY.containsMatchIn(lower)) qualities.add(SourceQuality.BLURAY)
        if (QUAL_WEB.containsMatchIn(lower)) qualities.add(SourceQuality.WEB)
        if (QUAL_HDTV.containsMatchIn(lower)) qualities.add(SourceQuality.HDTV)
        if (QUAL_DVD.containsMatchIn(lower)) qualities.add(SourceQuality.DVD)
        if (QUAL_SCR.containsMatchIn(lower)) qualities.add(SourceQuality.SCR)
        if (QUAL_TC.containsMatchIn(lower)) qualities.add(SourceQuality.TELECINEMA)
        if (QUAL_TS.containsMatchIn(lower)) qualities.add(SourceQuality.TELESYNC)
        if (QUAL_CAM.containsMatchIn(lower)) qualities.add(SourceQuality.CAM)
        return qualities.distinct()
    }
}
