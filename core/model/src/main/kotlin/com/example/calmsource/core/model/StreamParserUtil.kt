package com.example.calmsource.core.model

object StreamParserUtil {
    private val SIZE_REGEX = Regex("(?i)(\\d+(?:[\\.,]\\d+)?)\\s*(GB|MB|KB|GIB|MIB|KIB|G|M)\\b")
    private val QUALITY_8K = Regex("\\b(4320p|8k)\\b", RegexOption.IGNORE_CASE)
    private val QUALITY_4K = Regex("\\b(2160p|4k|uhd)\\b", RegexOption.IGNORE_CASE)
    private val QUALITY_2K = Regex("\\b(1440p|2k|qhd)\\b", RegexOption.IGNORE_CASE)
    private val QUALITY_1080P = Regex("\\b(1080p|1080i|fhd)\\b", RegexOption.IGNORE_CASE)
    private val QUALITY_720P = Regex("\\b(720p|hd)\\b", RegexOption.IGNORE_CASE)

    // Video codecs
    private val CODEC_HEVC = Regex("\\b(HEVC|x265|h\\.?265|H[Ee][Vv][Cc])\\b", RegexOption.IGNORE_CASE)
    private val CODEC_AVC  = Regex("\\b(AVC|x264|h\\.?264)\\b", RegexOption.IGNORE_CASE)
    private val CODEC_AV1  = Regex("\\b(AV1)\\b", RegexOption.IGNORE_CASE)

    // Audio codecs
    private val AUDIO_ATMOS   = Regex("\\b(Atmos|TrueHD)\\b", RegexOption.IGNORE_CASE)
    private val AUDIO_DTS     = Regex("\\b(DTS[\\-:]?HD[\\-:]?[MX]|DTS[\\-:]?X)\\b", RegexOption.IGNORE_CASE)
    private val AUDIO_EAC3    = Regex("\\b(E\\-?AC3|DD[\\+]|Dolby\\s?Digital[\\+]?)\\b", RegexOption.IGNORE_CASE)
    private val AUDIO_AAC     = Regex("\\b(AAC)\\b", RegexOption.IGNORE_CASE)
    private val AUDIO_FLAC    = Regex("\\b(FLAC)\\b", RegexOption.IGNORE_CASE)

    // HDR formats
    private val HDR_DV     = Regex("\\b(DV|DoVi|Dolby\\s*Vision)\\b", RegexOption.IGNORE_CASE)
    private val HDR_10P    = Regex("\\b(HDR10\\+|HDR10plus\\b)", RegexOption.IGNORE_CASE)
    private val HDR_HDR    = Regex("\\b(HDR(10)?)\\b", RegexOption.IGNORE_CASE)

    // Seeds
    private val SEEDS_REGEX = Regex("(?:[👥👤]|\\bseeds?:?|\\bS:)\\s*(\\d+)", RegexOption.IGNORE_CASE)

    /**
     * Parses the file size from a text string and returns the size in GB (Double).
     */
    fun parseFileSizeGb(text: String?): Double {
        if (text == null) return 0.0
        val matches = SIZE_REGEX.findAll(text)
        for (match in matches) {
            val valueStr = match.groupValues[1]
            val unit = match.groupValues[2]
            if (unit.equals("G", ignoreCase = true) || unit.equals("M", ignoreCase = true)) {
                if (!valueStr.contains('.') && !valueStr.contains(',') && valueStr.length in 1..2) {
                    continue
                }
            }
            val value = valueStr.replace(',', '.').toDoubleOrNull() ?: continue
            val unitUpper = unit.uppercase()
            return when (unitUpper) {
                "GB", "GIB", "G" -> value
                "MB", "MIB", "M" -> value / 1024.0
                "KB", "KIB" -> value / (1024.0 * 1024.0)
                else -> value
            }
        }
        return 0.0
    }

    /**
     * Parses the file size from a text string and returns exact bytes, or null.
     */
    fun parseSizeBytes(text: String?): Long? {
        if (text == null) return null
        val matches = SIZE_REGEX.findAll(text)
        for (match in matches) {
            val valueStr = match.groupValues[1]
            val unit = match.groupValues[2]
            if (unit.equals("G", ignoreCase = true) || unit.equals("M", ignoreCase = true)) {
                if (!valueStr.contains('.') && !valueStr.contains(',') && valueStr.length in 1..2) {
                    continue
                }
            }
            val value = valueStr.replace(',', '.').toDoubleOrNull() ?: continue
            val unitUpper = unit.uppercase()
            val bytes = when (unitUpper) {
                "GB", "GIB", "G" -> value * 1024.0 * 1024.0 * 1024.0
                "MB", "MIB", "M" -> value * 1024.0 * 1024.0
                "KB", "KIB" -> value * 1024.0
                else -> continue
            }
            return bytes.toLong()
        }
        return null
    }

    /**
     * Parses the resolution quality tag from a text string (e.g. 4K, 1080p, 720p, SD).
     */
    fun parseQuality(text: String?): String {
        if (text == null || text.length >= 500) return "Auto"
        val lower = text.lowercase()
        return try {
            when {
                QUALITY_4K.containsMatchIn(lower) || QUALITY_8K.containsMatchIn(lower) -> "4K"
                QUALITY_1080P.containsMatchIn(lower) || QUALITY_2K.containsMatchIn(lower) -> "1080p"
                QUALITY_720P.containsMatchIn(lower) -> "720p"
                else -> "Auto"
            }
        } catch (e: Exception) {
            "Auto"
        }
    }

    /**
     * Detects the video codec from a stream title string.
     * Returns "HEVC", "AVC", "AV1", or null.
     */
    fun parseVideoCodec(text: String?): String? {
        if (text == null) return null
        val lower = text
        return when {
            CODEC_AV1.containsMatchIn(lower) -> "AV1"
            CODEC_HEVC.containsMatchIn(lower) -> "HEVC"
            CODEC_AVC.containsMatchIn(lower) -> "H264"
            else -> null
        }
    }

    /**
     * Detects the audio codec/format from a stream title string.
     * Returns "Atmos", "DTS-HD", "E-AC3", "AAC", "FLAC", or null.
     */
    fun parseAudioCodec(text: String?): String? {
        if (text == null) return null
        return when {
            AUDIO_ATMOS.containsMatchIn(text) -> "Atmos"
            AUDIO_DTS.containsMatchIn(text) -> "DTS-HD"
            AUDIO_EAC3.containsMatchIn(text) -> "E-AC3"
            AUDIO_AAC.containsMatchIn(text) -> "AAC"
            AUDIO_FLAC.containsMatchIn(text) -> "FLAC"
            else -> null
        }
    }

    /**
     * Detects the HDR format from a stream title string.
     * Returns "DV", "HDR10+", "HDR10", or null.
     */
    fun parseHdrFormat(text: String?): String? {
        if (text == null) return null
        return when {
            HDR_DV.containsMatchIn(text) -> "DV"
            HDR_10P.containsMatchIn(text) -> "HDR10+"
            HDR_HDR.containsMatchIn(text) -> "HDR10"
            else -> null
        }
    }

    /**
     * Extracts seeder count from a stream title (e.g. "👥 145" or "seeds: 85").
     */
    fun parseSeeds(text: String?): Int? {
        if (text == null) return null
        val match = SEEDS_REGEX.find(text) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    /**
     * Extracts the extension provider name from a stream name string.
     */
    fun parseSourceExtensionName(name: String?, providerName: String): String {
        val raw = name ?: return providerName
        return when {
            raw.contains("\n") -> raw.substringBefore("\n").trim()
            raw.contains("]") -> raw.substringAfter("[").substringBefore("]").trim()
            raw.contains(")") -> raw.substringAfter("(").substringBefore(")").trim()
            else -> raw.split(" ").firstOrNull()?.trim() ?: providerName
        }.ifBlank { providerName }
    }

    /**
     * Runs all parsers against the input text and returns structured info.
     */
    fun smartParseAll(text: String?, providerName: String): StreamParsedInfo {
        val sizeBytes = parseSizeBytes(text)
        return StreamParsedInfo(
            fileSizeBytes = sizeBytes,
            fileSizeGb = if (sizeBytes != null) sizeBytes / (1024.0 * 1024.0 * 1024.0) else 0.0,
            quality = parseQuality(text),
            videoCodec = parseVideoCodec(text),
            audioCodec = parseAudioCodec(text),
            hdrFormat = parseHdrFormat(text),
            seeds = parseSeeds(text),
            sourceExtensionName = parseSourceExtensionName(text, providerName),
            cleanTitle = text?.replace(Regex("\\s+"), " ")?.trim() ?: providerName
        )
    }
}

/**
 * Structured result from [StreamParserUtil.smartParseAll], bundling every
 * extractable attribute from a raw Stremio stream title string.
 */
data class StreamParsedInfo(
    val fileSizeBytes: Long?,
    val fileSizeGb: Double,
    val quality: String,
    val videoCodec: String?,
    val audioCodec: String?,
    val hdrFormat: String?,
    val seeds: Int?,
    val sourceExtensionName: String?,
    val cleanTitle: String
)
