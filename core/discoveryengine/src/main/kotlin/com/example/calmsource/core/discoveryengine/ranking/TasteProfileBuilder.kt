package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineDao
import com.example.calmsource.core.discoveryengine.models.TasteProfile
import com.example.calmsource.core.model.UserPreferenceSignal
import com.example.calmsource.core.model.UserPreferenceSignalType

object TasteProfileBuilder {

    /**
     * Dynamically builds a normalized TasteProfile for a profile by analyzing favorites,
     * watch events, and preference/click signals, applying exponential recency decay.
     */
    fun buildTasteProfile(
        dao: DiscoveryEngineDao,
        profileId: String,
        preferenceSignals: List<UserPreferenceSignal> = emptyList(),
        now: Long = System.currentTimeMillis()
    ): TasteProfile {
        // Fetch all item states for this profile
        val itemStates = dao.getUserItemStatesForProfile(profileId)
        val channelStates = dao.getUserChannelStatesForProfile(profileId)
        
        val itemIds = itemStates.map { it.itemId }
        val channelIds = channelStates.map { it.channelId }
        
        val mediaItems = dao.getMediaItemsByIds(itemIds).associateBy { it.id }
        val channels = dao.getChannelsByIds(channelIds).associateBy { it.id }

        val genreScores = mutableMapOf<String, Double>()
        val langScores = mutableMapOf<String, Double>()
        val sourceScores = mutableMapOf<String, Double>()

        // Process media items
        itemStates.forEach { state ->
            val media = mediaItems[state.itemId] ?: return@forEach
            
            val decayFactor = if (state.lastWatchedAt != null && state.lastWatchedAt > 0) {
                val deltaMs = now - state.lastWatchedAt
                val deltaDays = deltaMs.toDouble() / (1000.0 * 60.0 * 60.0 * 24.0)
                kotlin.math.exp(-0.0231 * kotlin.math.max(0.0, deltaDays))
            } else {
                1.0
            }

            // Score weight: favorites count for 3.0, watch count adds 1.0 per watch, with recency decay
            val weight = ((if (state.isFavorite) 3.0 else 0.0) + (state.watchCount * 1.0)) * decayFactor
            if (weight == 0.0) return@forEach

            // Genres
            val genres = media.genres.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            genres.forEach { genre ->
                genreScores[genre] = (genreScores[genre] ?: 0.0) + weight
            }

            // Language
            val lang = media.language?.trim()?.lowercase() ?: "unknown"
            if (lang.isNotEmpty()) {
                langScores[lang] = (langScores[lang] ?: 0.0) + weight
            }

            // Source (stremio, local, etc)
            val source = media.source?.trim()?.lowercase() ?: "stremio"
            if (source.isNotEmpty()) {
                sourceScores[source] = (sourceScores[source] ?: 0.0) + weight
            }
        }

        // Process channel items
        channelStates.forEach { state ->
            val channel = channels[state.channelId] ?: return@forEach

            val decayFactor = if (state.lastWatchedAt != null && state.lastWatchedAt > 0) {
                val deltaMs = now - state.lastWatchedAt
                val deltaDays = deltaMs.toDouble() / (1000.0 * 60.0 * 60.0 * 24.0)
                kotlin.math.exp(-0.0231 * kotlin.math.max(0.0, deltaDays))
            } else {
                1.0
            }

            val weight = ((if (state.isFavorite) 3.0 else 0.0) + (state.watchCount * 1.0)) * decayFactor
            if (weight == 0.0) return@forEach

            // EPG channels don't have movie genres, but they have category
            val category = channel.category?.trim()?.lowercase() ?: "live"
            if (category.isNotEmpty()) {
                genreScores[category] = (genreScores[category] ?: 0.0) + weight
            }

            // Source (providerId)
            val source = channel.providerId.trim().lowercase()
            if (source.isNotEmpty()) {
                sourceScores[source] = (sourceScores[source] ?: 0.0) + weight
            }
        }

        // Process preference signals (click multipliers)
        preferenceSignals.forEach { signal ->
            val decayFactor = if (signal.lastSignaledAt > 0) {
                val deltaMs = now - signal.lastSignaledAt
                val deltaDays = deltaMs.toDouble() / (1000.0 * 60.0 * 60.0 * 24.0)
                kotlin.math.exp(-0.0231 * kotlin.math.max(0.0, deltaDays))
            } else {
                1.0
            }
            val weight = 0.5 * signal.count * decayFactor
            if (weight <= 0.0) return@forEach

            val key = signal.signalKey.trim().lowercase()
            if (key.isEmpty()) return@forEach

            when (signal.signalType) {
                UserPreferenceSignalType.GENRE -> {
                    genreScores[key] = (genreScores[key] ?: 0.0) + weight
                }
                UserPreferenceSignalType.PROVIDER, UserPreferenceSignalType.SOURCE -> {
                    sourceScores[key] = (sourceScores[key] ?: 0.0) + weight
                }
                else -> { /* Ignore other types */ }
            }
        }

        // Cold start fallback to seed genres from settings
        if (genreScores.isEmpty()) {
            val seedGenresString = dao.getSetting("seed_genres_$profileId")
            if (!seedGenresString.isNullOrEmpty()) {
                val seeds = seedGenresString.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                seeds.forEach { genre ->
                    genreScores[genre] = 1.0
                }
            }
        }

        // Helper to normalize map values to [0.0, 1.0]
        fun normalize(scores: Map<String, Double>): Map<String, Double> {
            val maxVal = scores.values.maxOrNull() ?: return emptyMap()
            if (maxVal == 0.0) return emptyMap()
            return scores.mapValues { it.value / maxVal }
        }

        return TasteProfile(
            profileId = profileId,
            genreAffinities = normalize(genreScores),
            languageAffinities = normalize(langScores),
            sourceAffinities = normalize(sourceScores)
        )
    }
}
