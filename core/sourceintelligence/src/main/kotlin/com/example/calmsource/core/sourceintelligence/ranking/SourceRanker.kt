package com.example.calmsource.core.sourceintelligence.ranking

import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.SortingPreference
import com.example.calmsource.core.model.StreamSource
import com.example.calmsource.core.model.UserPreferences
import com.example.calmsource.core.sourceintelligence.models.ParsedSource

/**
 * Ranks [ParsedSource] lists using the unified [StreamScoringEngine].
 */
class SourceRanker {

    fun rank(
        sources: List<ParsedSource>,
        strategy: SortingPreference = SortingPreference.BEST_MATCH,
        prefs: UserPreferences = UserPreferences(),
    ): List<ParsedSource> {
        if (sources.isEmpty()) return emptyList()

        return sources.map { parsed ->
            parsed to StreamScoringEngine.score(
                StreamScoringInput(
                    source = parsed.toStreamSource(),
                    strategy = strategy,
                    prefs = prefs,
                )
            )
        }.sortedWith(
            compareByDescending<Pair<ParsedSource, Double>> { it.second }
                .thenByDescending { it.first.seeders ?: 0 }
                .thenByDescending { it.first.sizeBytes ?: 0L }
        ).map { it.first }
    }

    private fun ParsedSource.toStreamSource(): StreamSource {
        val sizeStr = sizeBytes?.let { bytes ->
            val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            String.format(java.util.Locale.US, "%.2f GB", gb)
        }
        return StreamSource(
            id = id,
            name = title,
            url = getRawUrlUnsafe().orEmpty(),
            extensionId = origin,
            resolution = quality.orEmpty(),
            videoCodec = null,
            audioCodec = null,
            sizeBytes = sizeBytes,
            seeds = seeders,
            language = "",
            isSubbed = false,
            isDubbed = false,
            isDualAudio = false,
            headers = emptyMap(),
            rawTitle = listOfNotNull(title, quality, sizeStr).joinToString(" ").ifBlank { title }
        )
    }
}
