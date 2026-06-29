package com.example.calmsource.core.sourceintelligence.models

import com.example.calmsource.core.model.StreamSource
import com.example.calmsource.core.model.WatchOption

enum class SourceResolution(val height: Int) {
    UNKNOWN(0),
    SD(480),
    HD(720),
    FHD(1080),
    QHD(1440),
    UHD_4K(2160),
    UHD_8K(4320)
}

enum class SourceQuality {
    UNKNOWN,
    CAM,
    TELESYNC,
    TELECINEMA,
    SCR,
    DVD,
    HDTV,
    WEB,
    BLURAY,
    REMUX
}

enum class SourceHdrFormat {
    UNKNOWN,
    SDR,
    HDR10,
    HDR10_PLUS,
    DOLBY_VISION,
    HLG
}

enum class SourceVideoCodec {
    UNKNOWN,
    H264,
    H265,
    AV1,
    VP9,
    MPEG2,
    XVID
}

enum class SourceAudioFormat {
    UNKNOWN,
    AAC,
    AC3,
    EAC3,
    DTS,
    DTS_HD,
    DTS_X,
    TRUEHD,
    FLAC,
    MP3,
    OPUS
}

enum class SourceAudioChannelLayout {
    UNKNOWN,
    MONO,
    STEREO,
    SURROUND_5_1,
    SURROUND_7_1,
    ATMOS
}

enum class SourceReleaseType {
    UNKNOWN,
    THEATRICAL,
    EXTENDED,
    DIRECTORS_CUT,
    UNRATED,
    IMAX
}

data class SourceLanguageInfo(
    val languages: List<String> = emptyList(),
    val isDubbed: Boolean = false,
    val isDualAudio: Boolean = false,
    val isMultiAudio: Boolean = false
) {
    companion object {
        val UNKNOWN = SourceLanguageInfo()
    }
}

data class SourceSubtitleInfo(
    val isAvailable: Boolean = false,
    val languages: List<String> = emptyList(),
    val isHardcoded: Boolean = false,
    val isForced: Boolean = false
) {
    companion object {
        val UNKNOWN = SourceSubtitleInfo()
    }
}

data class SourceSizeInfo(
    val bytes: Long? = null
) {
    companion object {
        val UNKNOWN = SourceSizeInfo()
    }
}

/**
 * A confidence score for the parsed metadata.
 */
data class SourceConfidence(
    val score: Float, // 0.0 to 1.0
    val reasons: List<String> = emptyList()
) {
    companion object {
        val UNKNOWN = SourceConfidence(0f, listOf("Unknown"))
    }
}

/**
 * A physically separate model for raw unparsed strings so they don't leak into UI accidentally.
 * Private visibility internally so that the user cannot directly access `rawFilename` from `SourceIntelligenceResult`.
 */
class RawSourceMetadata internal constructor(
    internal val rawFilename: String?,
    internal val rawTitle: String?,
    internal val rawUrl: String?
)

/**
 * The fully parsed metadata object containing known/unknown enums.
 */
data class ParsedSourceMetadata(
    val quality: SourceQuality = SourceQuality.UNKNOWN,
    val resolution: SourceResolution = SourceResolution.UNKNOWN,
    val hdrFormat: SourceHdrFormat = SourceHdrFormat.UNKNOWN,
    val videoCodec: SourceVideoCodec = SourceVideoCodec.UNKNOWN,
    val audioFormat: SourceAudioFormat = SourceAudioFormat.UNKNOWN,
    val audioChannels: SourceAudioChannelLayout = SourceAudioChannelLayout.UNKNOWN,
    val languageInfo: SourceLanguageInfo = SourceLanguageInfo.UNKNOWN,
    val subtitleInfo: SourceSubtitleInfo = SourceSubtitleInfo.UNKNOWN,
    val releaseType: SourceReleaseType = SourceReleaseType.UNKNOWN,
    val sizeInfo: SourceSizeInfo = SourceSizeInfo.UNKNOWN,
    val group: String? = null
)

/**
 * The final display label containing clean, UI-ready strings.
 */
data class SourceDisplayLabel(
    val primaryLabel: String,
    val secondaryLabel: String,
    val tags: List<String> = emptyList()
)

/**
 * Extracted features used purely for ranking algorithms.
 */
data class SourceRankingFeatures(
    val isCached: Boolean = false,
    val isHevc: Boolean = false,
    val isAtmos: Boolean = false,
    val isDolbyVision: Boolean = false,
    val isRemux: Boolean = false,
    val sizeBytes: Long = 0L,
    val resolutionHeight: Int = 0,
    val seeds: Int = 0,
    val isLowDataSuitable: Boolean = false,
    val requiresHighBandwidth: Boolean = false,
    val isHugeSize: Boolean = false,
    val practicalScore: Int = 0
)

/**
 * The unified output of the SourceIntelligence module.
 * Exposes clean data, hides raw inputs physically.
 */
class SourceIntelligenceResult(
    private val rawMetadata: RawSourceMetadata,
    val parsedMetadata: ParsedSourceMetadata,
    val confidence: SourceConfidence,
    val displayLabel: SourceDisplayLabel,
    val rankingFeatures: SourceRankingFeatures
)

/**
 * Input DTO to pass into the parser layer.
 */
data class RawSourceInput(
    val rawFilename: String?,
    val rawTitle: String?,
    val rawUrl: String?
) {
    internal fun toRawSourceMetadata(): RawSourceMetadata {
        return RawSourceMetadata(rawFilename, rawTitle, rawUrl)
    }
}

/**
 * Mapping functions to convert StreamSource and WatchOption into RawSourceMetadata inputs for parsing.
 */
fun StreamSource.toRawSourceInput(): RawSourceInput {
    val sizeHint = sizeBytes?.takeIf { it > 0L }?.let { bytes ->
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        String.format(java.util.Locale.US, "%.2f GB", gb)
    }
    val combinedTitle = rawTitle ?: listOfNotNull(
        name.takeIf { it.isNotBlank() },
        resolution.takeIf { it.isNotBlank() },
        videoCodec,
        audioCodec,
        sizeHint
    ).joinToString(" ").ifBlank { null }
    return RawSourceInput(
        rawFilename = name,
        rawTitle = combinedTitle,
        rawUrl = url
    )
}

fun WatchOption.toRawSourceInput(): RawSourceInput {
    return this.source.toRawSourceInput().copy(
        rawTitle = this.title
    )
}
