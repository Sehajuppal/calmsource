package com.example.calmsource.core.sourceintelligence.ranking

import com.example.calmsource.core.sourceintelligence.SourceIntelligence
import com.example.calmsource.core.sourceintelligence.models.ParsedSource
import com.example.calmsource.core.sourceintelligence.models.RawSourceInput

/**
 * Evaluates ParsedSource objects and ranks them based on heuristics.
 */
class SourceRanker {

    /**
     * Ranks a list of sources according to quality, seeders, and performance heuristics.
     *
     * Processes all sources in a single batch call to [SourceIntelligence.processAll],
     * avoiding redundant per-source parsing.
     */
    fun rank(sources: List<ParsedSource>): List<ParsedSource> {
        if (sources.isEmpty()) return emptyList()

        // Build inputs for all sources in one pass.
        val inputs = sources.map { source ->
            val sizeStr = source.sizeBytes?.let { bytes ->
                val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                String.format(java.util.Locale.US, "%.2f GB", gb)
            } ?: ""
            RawSourceInput(
                rawFilename = null,
                rawTitle = listOfNotNull(source.title, source.quality, sizeStr.takeIf { it.isNotEmpty() }).joinToString(" "),
                rawUrl = source.getRawUrlUnsafe()
            )
        }

        // Single batch call — deduplicates identical inputs internally.
        val results = SourceIntelligence.processAll(inputs)

        // Zip scores back to their sources.
        val scored = sources.zip(results) { source, intelligence ->
            val features = intelligence.rankingFeatures
            var score = 0

            // Seeders heuristic
            val s = source.seeders ?: 0
            if (s > 100) score += 30
            else if (s > 20) score += 10

            score += features.practicalScore - 50
            if (features.isRemux) score += 40
            if (features.isDolbyVision) score += 25
            if (features.isAtmos) score += 20
            if (features.isHevc) score += 10
            if (features.isHugeSize) score -= 20
            score += when {
                features.resolutionHeight >= 2160 -> 100
                features.resolutionHeight >= 1080 -> 80
                features.resolutionHeight >= 720 -> 50
                else -> 0
            }

            ScoredSource(source, score)
        }

        return scored.sortedWith(
            compareByDescending<ScoredSource> { it.score }
                .thenByDescending { it.source.seeders ?: 0 }
                .thenByDescending { it.source.sizeBytes ?: 0L }
        ).map { it.source }
    }

    private data class ScoredSource(
        val source: ParsedSource,
        val score: Int
    )
}
