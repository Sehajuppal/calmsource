/**
 * Public API facade for the CalmSource Universal Search system.
 *
 * This object provides a simplified interface for UI layers to perform searches
 * without needing to understand the internal pipeline architecture. It delegates to:
 * - [UniversalSearchEngineImpl] for concurrent multi-provider search execution
 * - [SearchResultRanker] for scoring individual sources
 * - [SearchResultMerger] for combining and grouping results
 *
 * Usage:
 * ```kotlin
 * // Simple score calculation
 * val score = SearchEngine.calculateScore(source, prefs)
 *
 * // Full reactive search
 * SearchEngine.search("spider-man", prefs).collect { results -> ... }
 * ```
 *
 * @see SearchResultPipeline for the scoring, deduplication, and grouping logic
 * @see UniversalSearchEngineImpl for the concurrent provider orchestration
 */
package com.example.calmsource.feature.search

import com.example.calmsource.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object SearchEngine {

    // Shared engine instance to avoid re-creating on every search call
    private val universalEngine by lazy { UniversalSearchEngineImpl() }
    
    /**
     * Calculates the ranking score for a single [StreamSource].
     *
     * Convenience wrapper around [SearchResultRanker.calculateSourceScore].
     *
     * @param source the stream source to score
     * @param prefs user preferences influencing the score
     * @return numeric quality score (higher is better)
     */
    suspend fun calculateScore(source: StreamSource, prefs: UserPreferences): Int {
        return SearchResultRanker.calculateSourceScore(source, prefs)
    }

    /**
     * Merges media items, stream sources, and channels into a flat list of
     * ranked [NormalizedSearchResult]s.
     *
     * This is a legacy compatibility shim that wraps the inputs in a single
     * [SearchProviderResult], runs the merge pipeline, and flattens groups
     * (excluding settings) into a single list.
     *
     * @param mediaItems catalog items to include
     * @param sources stream sources to associate with media items
     * @param channels live IPTV channels to include
     * @param query the search query for title-match boosting
     * @param prefs user preferences for scoring
     * @return flat, deduplicated, ranked list of results
     */
    suspend fun mergeAndRank(
        mediaItems: List<MediaItem>,
        sources: List<StreamSource>,
        channels: List<Channel>,
        query: String,
        prefs: UserPreferences
    ): List<NormalizedSearchResult> {
        val providerResult = SearchProviderResult(
            providerId = "legacy-compat",
            providerName = "Legacy Compatibility Linker",
            query = SearchQuery(query),
            mediaItems = mediaItems,
            streamSources = sources,
            channels = channels
        )
        val mergedGroups = SearchResultMerger.merge(
            providerResults = listOf(providerResult),
            query = query,
            prefs = prefs
        )
        // Flatten results for compat
        return mergedGroups
            .filter { it.groupType != SearchGroupType.SETTINGS }
            .flatMap { it.results }
            .distinctBy { it.mediaItem.id }
    }

    /**
     * Performs a full reactive search across all registered providers.
     *
     * Returns a [Flow] that emits progressively more complete result lists
     * as each provider responds, with settings groups filtered out.
     *
     * @param query the search query string
     * @param prefs user preferences for scoring and filtering
     * @return a flow emitting updated, flat result lists
     */
    fun search(query: String, prefs: UserPreferences): Flow<List<NormalizedSearchResult>> {
        return universalEngine.search(query, prefs).map { groups ->
            groups.filter { it.groupType != SearchGroupType.SETTINGS }
                .flatMap { it.results }
                .distinctBy { it.mediaItem.id }
        }
    }
}

