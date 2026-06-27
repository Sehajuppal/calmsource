package com.example.calmsource.feature.search

import com.example.calmsource.core.model.MediaType
import com.example.calmsource.core.model.SearchGroupType
import com.example.calmsource.core.model.SearchResultGroup

/**
 * URL-free search result used by both app UIs.
 *
 * Provider results can include sensitive playback URLs, so this display model keeps only
 * metadata and a boolean indicating whether the result already has a playable option.
 */
data class SearchDisplayResult(
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val sourceLabel: String,
    val hasPlayableSource: Boolean = false,
    val score: Double = 0.0,
    val externalIds: Map<String, String> = emptyMap()
)

fun List<SearchResultGroup>.toSearchDisplayResults(): List<SearchDisplayResult> {
    return asSequence()
        .filterNot { it.groupType == SearchGroupType.SETTINGS }
        .flatMap { it.results.asSequence() }
        .map { result ->
            val sourceId = result.bestMatchOption?.source?.extensionId
            val type = when {
                sourceId == "iptv-live" || sourceId == "epg-live" -> "channel"
                result.mediaItem.type == MediaType.SHOW -> "series"
                else -> "movie"
            }
            val sourceLabel = result.availableFrom
                .joinToString(" + ") { source ->
                    source.name.lowercase().replaceFirstChar(Char::uppercase)
                }
                .ifBlank { "Catalog" }

            SearchDisplayResult(
                id = result.mediaItem.id,
                type = type,
                title = result.mediaItem.title,
                subtitle = result.mediaItem.overview,
                posterUrl = result.mediaItem.posterUrl,
                sourceLabel = sourceLabel,
                hasPlayableSource = result.watchOptions.isNotEmpty(),
                score = result.score.toDouble(),
                externalIds = result.mediaItem.externalIds
            )
        }
        .distinctBy { "${it.type}:${it.id}" }
        .toList()
}
