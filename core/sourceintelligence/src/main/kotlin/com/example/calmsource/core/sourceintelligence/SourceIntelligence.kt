package com.example.calmsource.core.sourceintelligence

import com.example.calmsource.core.sourceintelligence.models.*
import com.example.calmsource.core.sourceintelligence.parsers.QualityParser
import com.example.calmsource.core.sourceintelligence.parsers.LanguageAndAudioParser
import com.example.calmsource.core.sourceintelligence.parsers.FileSizeAndPracticalityParser

object SourceIntelligence {

    /** Cache entry TTL — entries older than this are re-computed on next access. */
    private const val CACHE_TTL_MS: Long = 5 * 60 * 1000L

    /** Maximum number of entries in the LRU cache. */
    private const val MAX_CACHE_SIZE = 1000

    /** Wraps a result with a creation timestamp so stale entries can be detected. */
    private data class CacheEntry(
        val result: SourceIntelligenceResult,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - createdAt > CACHE_TTL_MS
    }

    /**
     * Cache key that uses hashes of the raw URL/filename/title so the same host
     * with different paths produces distinct keys. Avoids storing the raw strings
     * in the key to prevent accidental leakage via logs or heap dumps.
     */
    private data class CacheKey(
        val urlHash: Int,
        val filenameHash: Int?,
        val titleHash: Int?,
        val preferredLanguages: List<String>,
        val lowDataModeEnabled: Boolean
    )

    /** Thread-safe LRU cache. Uses [LinkedHashMap] with manual [@Synchronized] guards
     *  so that eviction ([removeEldestEntry]) runs under the same lock as reads/writes,
     *  avoiding the known concurrency pitfall of [synchronizedMap]. */
    private val cache = object : LinkedHashMap<CacheKey, CacheEntry>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, CacheEntry>?): Boolean =
            size > MAX_CACHE_SIZE
    }

    @Synchronized
    private fun cacheGet(key: CacheKey): CacheEntry? = cache[key]

    @Synchronized
    private fun cachePut(key: CacheKey, entry: CacheEntry) {
        cache[key] = entry
        // removeEldestEntry is called by LinkedHashMap after put,
        // but it may not be triggered if the key already existed.
        if (cache.size > MAX_CACHE_SIZE) {
            val eldest = cache.entries.firstOrNull()
            if (eldest != null) cache.remove(eldest.key)
        }
    }

    fun process(
        input: RawSourceInput,
        preferredLanguages: List<String> = emptyList(),
        lowDataModeEnabled: Boolean = false
    ): SourceIntelligenceResult {
        val cacheKey = CacheKey(
            urlHash = (input.rawUrl ?: "").hashCode(),
            filenameHash = input.rawFilename?.hashCode(),
            titleHash = input.rawTitle?.hashCode(),
            preferredLanguages = preferredLanguages,
            lowDataModeEnabled = lowDataModeEnabled
        )

        cacheGet(cacheKey)?.let { entry ->
            if (!entry.isExpired()) return entry.result
        }

        val result = computeResult(input, preferredLanguages, lowDataModeEnabled)

        cachePut(cacheKey, CacheEntry(result))
        return result
    }

    /**
     * Batch API: processes multiple inputs in a single pass, deduplicating
     * identical inputs before parsing to avoid redundant work.
     *
     * @return a list of results in the same order as [inputs] (duplicates share the same result).
     */
    fun processAll(
        inputs: List<RawSourceInput>,
        preferredLanguages: List<String> = emptyList(),
        lowDataModeEnabled: Boolean = false
    ): List<SourceIntelligenceResult> {
        if (inputs.isEmpty()) return emptyList()

        // Deduplicate by identity — identical inputs produce the same result.
        val seen = LinkedHashMap<RawSourceInput, SourceIntelligenceResult>(inputs.size)
        val deduped = inputs.filter { input ->
            if (input in seen) false else true.also { seen[input] = computeResult(input, preferredLanguages, lowDataModeEnabled) }
        }

        // Fast path — all were unique, map in declaration order.
        if (deduped.size == inputs.size) return deduped.map { seen.getValue(it) }

        // Slow path — re-insert duplicates at their original positions.
        return inputs.map { seen.getValue(it) }
    }

    /** Core parse + score pipeline, independent of caching. */
    private fun computeResult(
        input: RawSourceInput,
        preferredLanguages: List<String> = emptyList(),
        lowDataModeEnabled: Boolean = false
    ): SourceIntelligenceResult {
        val rawMetadata = input.toRawSourceMetadata()
        val combinedInput = "${input.rawFilename.orEmpty()} ${input.rawTitle.orEmpty()}".trim()

        // Parsing
        val resolution = QualityParser.parseResolution(combinedInput)
        val quality = QualityParser.parseQuality(combinedInput)
        val hdrFormat = QualityParser.parseHdrFormat(combinedInput)
        val videoCodec = QualityParser.parseVideoCodec(combinedInput)
        val releaseType = QualityParser.parseReleaseType(combinedInput)

        val audioFormat = LanguageAndAudioParser.parseAudioFormat(combinedInput)
        val audioChannels = LanguageAndAudioParser.parseAudioChannelLayout(combinedInput)
        val languageInfo = LanguageAndAudioParser.parseLanguage(combinedInput)
        val subtitleInfo = LanguageAndAudioParser.parseSubtitle(combinedInput)

        val sizeInfo = FileSizeAndPracticalityParser.parseFileSize(combinedInput)

        val parsedMetadata = ParsedSourceMetadata(
            quality = quality,
            resolution = resolution,
            hdrFormat = hdrFormat,
            videoCodec = videoCodec,
            audioFormat = audioFormat,
            audioChannels = audioChannels,
            languageInfo = languageInfo,
            subtitleInfo = subtitleInfo,
            releaseType = releaseType,
            sizeInfo = sizeInfo
        )

        // Generate Labels
        val displayLabel = generateDisplayLabel(parsedMetadata, input, preferredLanguages)

        // Generate Ranking Features
        val baseFeatures = SourceRankingFeatures(
            isCached = input.rawFilename?.contains("cached", ignoreCase = true) == true || input.rawTitle?.contains("cached", ignoreCase = true) == true,
            isHevc = videoCodec == SourceVideoCodec.H265,
            isAtmos = audioChannels == SourceAudioChannelLayout.ATMOS || audioFormat == SourceAudioFormat.TRUEHD,
            isDolbyVision = hdrFormat == SourceHdrFormat.DOLBY_VISION,
            isRemux = quality == SourceQuality.REMUX,
            sizeBytes = sizeInfo.bytes ?: 0L,
            resolutionHeight = resolution.height,
            seeds = 0,
            isLowDataSuitable = sizeInfo.bytes != null && sizeInfo.bytes < 1_500_000_000L && resolution.height < 2160,
            requiresHighBandwidth = resolution.height >= 2160 || (sizeInfo.bytes ?: 0L) > 5L * 1024 * 1024 * 1024,
            isHugeSize = (sizeInfo.bytes ?: 0L) > FileSizeAndPracticalityParser.HUGE_SIZE_BYTES,
            practicalScore = 0
        )
        val rankingFeatures = FileSizeAndPracticalityParser.parse(input, existingFeatures = baseFeatures, lowDataModeEnabled = lowDataModeEnabled)

        val confidence = run {
            var score = 0.9f
            val reasons = mutableListOf("Basic extraction")
            val allRes = QualityParser.parseAllResolutions(combinedInput)
            if (allRes.size > 1) {
                score -= 0.3f
                reasons.add("Conflicting resolutions detected")
            }
            val allQualities = QualityParser.parseAllQualities(combinedInput)
            if (allQualities.size > 1) {
                score -= 0.3f
                reasons.add("Conflicting qualities detected")
            }
            SourceConfidence(score.coerceAtLeast(0.1f), reasons)
        }

        return SourceIntelligenceResult(
            rawMetadata = rawMetadata,
            parsedMetadata = parsedMetadata,
            confidence = confidence,
            displayLabel = displayLabel,
            rankingFeatures = rankingFeatures
        )
    }

    private fun generateDisplayLabel(parsed: ParsedSourceMetadata, input: RawSourceInput, preferredLanguages: List<String> = emptyList()): SourceDisplayLabel {
        val parts = mutableListOf<String>()

        val langParts = mutableListOf<String>()
        if (parsed.languageInfo.languages.isNotEmpty()) {
            val sortedLangs = parsed.languageInfo.languages.sortedBy { lang ->
                val index = preferredLanguages.indexOfFirst { it.equals(lang, ignoreCase = true) }
                if (index != -1) index else Int.MAX_VALUE
            }
            langParts.addAll(sortedLangs)
        }
        if (langParts.isEmpty() && (parsed.languageInfo.isDubbed || parsed.languageInfo.isDualAudio)) {
            if (parsed.languageInfo.isDualAudio) langParts.add("Dual Audio")
            if (parsed.languageInfo.isDubbed) langParts.add("Dubbed")
        }

        val primaryLanguageLabel = if (langParts.isNotEmpty()) langParts.joinToString(" + ") else "Unknown Lang"

        val resolutionLabel = when (parsed.resolution) {
            SourceResolution.UHD_8K -> "8K"
            SourceResolution.UHD_4K -> "4K"
            SourceResolution.QHD -> "1440p"
            SourceResolution.FHD -> "1080p"
            SourceResolution.HD -> "720p"
            SourceResolution.SD -> "SD"
            SourceResolution.UNKNOWN -> "Auto"
        }

        if (primaryLanguageLabel != "Unknown Lang") {
            parts.add(primaryLanguageLabel)
        }
        parts.add(resolutionLabel)

        if (parsed.hdrFormat == SourceHdrFormat.DOLBY_VISION) parts.add("Dolby Vision")
        else if (parsed.hdrFormat == SourceHdrFormat.HDR10 || parsed.hdrFormat == SourceHdrFormat.HDR10_PLUS) parts.add("HDR")

        if (parsed.quality == SourceQuality.REMUX) parts.add("Remux")
        if (parsed.quality == SourceQuality.BLURAY) parts.add("BluRay")

        if (input.rawTitle?.contains("cached", ignoreCase = true) == true || input.rawFilename?.contains("cached", ignoreCase = true) == true) {
            parts.add("Cached")
        }

        val primaryLabel = parts.joinToString(" • ")

        val secParts = mutableListOf<String>()
        if (parsed.sizeInfo.bytes != null) {
            val bytes = parsed.sizeInfo.bytes
            if (bytes < 1024 * 1024 * 1024) {
                secParts.add(String.format("%.0f MB", bytes.toDouble() / (1024 * 1024)))
            } else {
                secParts.add(String.format("%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024)))
            }
        }
        if (parsed.videoCodec != SourceVideoCodec.UNKNOWN) secParts.add(parsed.videoCodec.name)

        return SourceDisplayLabel(
            primaryLabel = primaryLabel,
            secondaryLabel = secParts.joinToString(" • "),
            tags = parts
        )
    }
}
