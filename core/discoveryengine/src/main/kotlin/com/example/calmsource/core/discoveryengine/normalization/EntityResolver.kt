package com.example.calmsource.core.discoveryengine.normalization

import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineDao
import com.example.calmsource.core.discoveryengine.database.MediaItemEntity

object EntityResolver {

    /**
     * Attempts to resolve an incoming media item's canonical ID from the database by matching:
     * 1. IMDb ID
     * 2. TMDB ID
     * 3. Normalized Title + Year
     * Returns the existing item's ID if found, or null if it's a new unique item.
     */
    fun resolveMediaItemId(
        dao: DiscoveryEngineDao,
        title: String,
        year: Int?,
        director: String?,
        externalIds: Map<String, String>
    ): String? {
        // 1. Check IMDb ID
        val imdbId = externalIds["imdb"] ?: externalIds["IMDb"]
        if (!imdbId.isNullOrEmpty()) {
            val matched = dao.getMediaItemByExternalId(imdbId)
                ?: dao.getMediaIdByExternalId(imdbId)?.let { dao.getMediaItem(it) }
            if (matched != null) return matched.id
        }

        // 2. Check TMDB ID
        val tmdbId = externalIds["tmdb"] ?: externalIds["TMDb"]
        if (!tmdbId.isNullOrEmpty()) {
            val matched = dao.getMediaItemByExternalId(tmdbId)
                ?: dao.getMediaIdByExternalId(tmdbId)?.let { dao.getMediaItem(it) }
            if (matched != null) return matched.id
        }

        // 3. Check Normalized Title + Year
        if (year != null && year > 0) {
            val normalizedTitle = MetadataNormalizer.normalizeTitle(title)
            if (normalizedTitle.isNotEmpty()) {
                val matched = dao.getMediaItemByNormalizedTitleAndYear(normalizedTitle, year)
                if (matched != null) return matched.id
            }
        }

        return null
    }
}
