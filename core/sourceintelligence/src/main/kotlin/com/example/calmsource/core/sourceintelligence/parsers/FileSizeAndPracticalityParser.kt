package com.example.calmsource.core.sourceintelligence.parsers

import com.example.calmsource.core.sourceintelligence.models.RawSourceInput
import com.example.calmsource.core.sourceintelligence.models.SourceRankingFeatures
import java.util.regex.Pattern

/**
 * Parses file size from raw titles/filenames and calculates practicality scores.
 */
object FileSizeAndPracticalityParser {

    const val HUGE_SIZE_BYTES: Long = 20L * 1024 * 1024 * 1024
    private const val HIGH_BANDWIDTH_BYTES: Long = 5L * 1024 * 1024 * 1024
    private const val LOW_DATA_BYTES: Long = 1L * 1024 * 1024 * 1024

    private val sizeRegex = Pattern.compile("(?i)(\\d+(?:[\\.,]\\d+)?)\\s*(GB|MB|KB|GIB|MIB|KIB)")

    fun parseFileSize(text: String?): com.example.calmsource.core.sourceintelligence.models.SourceSizeInfo {
        return com.example.calmsource.core.sourceintelligence.models.SourceSizeInfo(bytes = extractSizeBytes(text))
    }

    fun parse(
        input: RawSourceInput, 
        lowDataModeEnabled: Boolean = false, 
        existingFeatures: SourceRankingFeatures? = null
    ): SourceRankingFeatures {
        val sizeBytes = extractSizeBytes(input.rawFilename) ?: extractSizeBytes(input.rawTitle) ?: 0L
        
        val isHugeSize = sizeBytes > HUGE_SIZE_BYTES
        val isLowDataSuitable = sizeBytes > 0 && sizeBytes <= LOW_DATA_BYTES
        val requiresHighBandwidth = isHugeSize || sizeBytes > HIGH_BANDWIDTH_BYTES
        
        var practicalScore = 50 // Base score

        if (isLowDataSuitable) {
            practicalScore += 20
        }
        if (requiresHighBandwidth) {
            practicalScore -= 20
        }
        
        // Flag huge sources when low-data mode is enabled
        if (lowDataModeEnabled && isHugeSize) {
            practicalScore -= 50
        } else if (lowDataModeEnabled && requiresHighBandwidth) {
            practicalScore -= 30
        } else if (lowDataModeEnabled && isLowDataSuitable) {
            practicalScore += 30
        }

        // Clamp practicalScore between 0 and 100
        practicalScore = practicalScore.coerceIn(0, 100)

        val baseFeatures = existingFeatures ?: SourceRankingFeatures()
        
        return baseFeatures.copy(
            sizeBytes = if (sizeBytes > 0) sizeBytes else baseFeatures.sizeBytes,
            isLowDataSuitable = isLowDataSuitable,
            requiresHighBandwidth = requiresHighBandwidth,
            isHugeSize = isHugeSize,
            practicalScore = practicalScore
        )
    }

    private fun extractSizeBytes(text: String?): Long? {
        if (text == null) return null
        val matcher = sizeRegex.matcher(text)
        if (matcher.find()) {
            val value = matcher.group(1)?.replace(',', '.')?.toDoubleOrNull() ?: return null
            val unit = matcher.group(2)?.uppercase() ?: return null
            val multiplier: Long = when (unit) {
                "GB", "GIB" -> 1024L * 1024 * 1024
                "MB", "MIB" -> 1024L * 1024
                "KB", "KIB" -> 1024L
                else -> 1L
            }
            return (value * multiplier).toLong()
        }
        return null
    }
}
