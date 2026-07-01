package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.MediaItemEntity
import com.example.calmsource.core.discoveryengine.database.UserItemStateEntity
import com.example.calmsource.core.discoveryengine.models.RecommendationItem
import com.example.calmsource.core.discoveryengine.models.ScoreBreakdown
import com.example.calmsource.core.discoveryengine.models.TasteProfile
import com.example.calmsource.core.discoveryengine.providers.EnrichmentFeatures
import java.util.Calendar
import kotlin.math.log10

object HybridRecommendationRanker {
    private const val UNAVAILABLE_SCORE_MULTIPLIER = 0.7

    /**
     * Ranks candidate media items using a hybrid scoring algorithm combining TMDb ratings
     * with the user's taste profile affinities.
     */
    fun rank(
        tasteProfile: TasteProfile,
        candidates: List<MediaItemEntity>,
        userItemStates: Map<String, UserItemStateEntity>,
        limit: Int = 15,
        currentYear: Int = Calendar.getInstance().get(Calendar.YEAR),
        enrichmentFeatures: (String) -> EnrichmentFeatures = { EnrichmentFeatures(mediaId = it) }
    ): List<RecommendationItem> {
        val scoredCandidates = candidates.mapNotNull { media ->
            val state = userItemStates[media.id]
            
            // Skip hidden items and already completed items
            if (state != null && (state.isHidden || state.isCompleted)) {
                return@mapNotNull null
            }

            val reasons = mutableListOf<String>()

            // 1. Base Quality Score (TMDb rating * 2.0)
            val rating = media.rating ?: 0.0
            val baseQualityScore = rating * 2.0
            if (rating > 0.0) {
                reasons.add("TMDb Rating: $rating")
            }

            // 2. Genre Affinity Boost
            val genres = media.genres.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val matchingAffinities = genres
                .mapNotNull { genre -> tasteProfile.genreAffinities[genre]?.takeIf { it > 0.0 } }
                .sortedDescending()
            val genreBoost = if (matchingAffinities.isNotEmpty()) {
                val primaryAnchor = matchingAffinities.first()
                val secondaryAdditive = matchingAffinities.drop(1).mapIndexed { index, affinity ->
                    affinity * (1.0 / (index + 2.0))
                }.sum()
                (primaryAnchor * 12.0 + secondaryAdditive * 3.0).coerceAtMost(15.0)
            } else {
                0.0
            }
            if (genreBoost > 0.0) {
                reasons.add("Matches your favorite genres")
            }

            // 3. Language Affinity Boost
            val lang = media.language?.trim()?.lowercase() ?: "unknown"
            val langAffinity = tasteProfile.languageAffinities[lang] ?: 0.0
            val langBoost = langAffinity * 10.0
            if (langBoost > 0.0) {
                reasons.add("In your preferred language")
            }

            val features = enrichmentFeatures(media.id)
            val providerRatingBonus = if (features.ratingCount > 0) {
                (features.averageRating / 10.0).coerceIn(0.0, 1.0) * 0.5
            } else {
                0.0
            }
            if (providerRatingBonus > 0.0) {
                reasons.add("Provider enrichment match")
            }

            val releaseYear = media.releaseYear ?: currentYear
            val ageYears = (currentYear - releaseYear).coerceAtLeast(0)
            val freshnessBoost = 6.0 / (1.0 + log10(1.0 + ageYears))
            if (freshnessBoost >= 5.0) {
                reasons.add("Recent release")
            }

            val availabilitySignal = features.availabilityScore.coerceIn(0.0, 1.0)
            val availabilityMultiplier = if (availabilitySignal == 0.0) {
                UNAVAILABLE_SCORE_MULTIPLIER
            } else {
                UNAVAILABLE_SCORE_MULTIPLIER + (availabilitySignal * (1.0 - UNAVAILABLE_SCORE_MULTIPLIER))
            }
            if (availabilitySignal > 0.0) {
                reasons.add("Playable streams available")
            }

            val rawScore = baseQualityScore + genreBoost + langBoost + providerRatingBonus
            val availabilityPenalty = rawScore * (1.0 - availabilityMultiplier)
            val totalScore = rawScore * availabilityMultiplier + freshnessBoost

            val breakdown = ScoreBreakdown(
                ftsScore = 0.0,
                exactPrefixBoost = 0.0,
                aliasBoost = 0.0,
                qualityBoost = baseQualityScore + providerRatingBonus + freshnessBoost,
                profileBoost = genreBoost + langBoost,
                liveNowBoost = 0.0,
                availabilityScore = availabilitySignal,
                penalty = availabilityPenalty,
                reasons = reasons
            )

            val itemVector = com.example.calmsource.core.discoveryengine.normalization.Vectorizer.vectorize(
                title = media.title,
                overview = media.overview,
                genres = genres,
                cast = media.cast.split(",").filter { it.isNotEmpty() },
                director = media.director,
                language = media.language,
                source = media.source
            )

            val recItem = RecommendationItem(
                id = media.id,
                type = media.type,
                title = media.title,
                score = totalScore,
                reason = reasons.firstOrNull() ?: "Recommended for you",
                scoreBreakdown = breakdown,
                genres = genres,
            )
            recItem to itemVector
        }

        if (scoredCandidates.isEmpty()) return emptyList()

        val selected = mutableListOf<RecommendationItem>()
        val remaining = scoredCandidates.toMutableList()
        val lambda = 0.6

        while (selected.size < limit && remaining.isNotEmpty()) {
            if (selected.isEmpty()) {
                val bestPair = remaining.maxByOrNull { it.first.score } ?: break
                selected.add(bestPair.first)
                remaining.remove(bestPair)
            } else {
                var bestMmrScore = -Double.MAX_VALUE
                var bestPair: Pair<RecommendationItem, FloatArray>? = null

                for (pair in remaining) {
                    val (item, itemVec) = pair
                    var maxSimToSelected = 0.0
                    for (selItem in selected) {
                        val selVec = scoredCandidates.firstOrNull { it.first.id == selItem.id }?.second
                        if (selVec != null) {
                            val sim = com.example.calmsource.core.discoveryengine.normalization.Vectorizer.cosineSimilarity(itemVec, selVec)
                            if (sim > maxSimToSelected) {
                                maxSimToSelected = sim
                            }
                        }
                    }

                    // MMR Score formula with scaled similarity penalty
                    val mmrScore = lambda * item.score - (1.0 - lambda) * maxSimToSelected * 35.0

                    if (mmrScore > bestMmrScore) {
                        bestMmrScore = mmrScore
                        bestPair = pair
                    }
                }

                if (bestPair != null) {
                    selected.add(bestPair.first)
                    remaining.remove(bestPair)
                } else {
                    break
                }
            }
        }

        return selected
    }
}
