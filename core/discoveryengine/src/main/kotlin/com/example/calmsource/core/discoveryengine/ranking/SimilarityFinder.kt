package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.MediaItemEntity
import com.example.calmsource.core.discoveryengine.models.RecommendationItem
import com.example.calmsource.core.discoveryengine.models.ScoreBreakdown
import kotlin.math.abs

object SimilarityFinder {

    /**
     * Finds media items similar to the target item based on type, genres, cast/director,
     * and rating.
     */
    fun findSimilar(
        target: MediaItemEntity,
        candidates: List<MediaItemEntity>,
        limit: Int = 15,
        providerSimilarIds: Set<String> = emptySet(),
        globalSimilarIds: Set<String> = emptySet()
    ): List<RecommendationItem> {
        val targetGenres = target.genres.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val targetCast = target.cast.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val targetDirector = target.director?.trim()?.lowercase() ?: ""
        val targetRating = target.rating ?: 5.0

        // Calculate dynamic genre frequencies among all candidates to compute IDF-like weights
        val allCandidateGenres = candidates.flatMap {
            it.genres.split(",").map { g -> g.trim().lowercase() }.filter { g -> g.isNotEmpty() }
        }
        val genreFrequencies = allCandidateGenres.groupingBy { it }.eachCount()
        val totalCandidates = candidates.size.toDouble()

        // Weight helper function
        fun getGenreWeight(genre: String): Double {
            val freq = genreFrequencies[genre] ?: 0
            // IDF-like formula: ln(1.0 + totalCandidates / (1.0 + freq))
            return kotlin.math.ln(1.0 + totalCandidates / (1.0 + freq))
        }

        return candidates.mapNotNull { candidate ->
            // Skip the target item itself
            if (candidate.id == target.id) return@mapNotNull null
            // Ensure they are the same overall type (movie/series)
            if (candidate.type != target.type) return@mapNotNull null

            val reasons = mutableListOf<String>()

            // 1. Dynamic Weighted Genre Jaccard overlap
            val candGenres = candidate.genres.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            val intersection = targetGenres.intersect(candGenres)
            val union = targetGenres.union(candGenres)
            
            val weightedIntersection = intersection.sumOf { getGenreWeight(it) }
            val weightedUnion = union.sumOf { getGenreWeight(it) }
            
            val weightedJaccard = if (weightedUnion > 0.0) {
                weightedIntersection / weightedUnion
            } else {
                0.0
            }
            val genreScore = weightedJaccard * 40.0
            if (weightedJaccard > 0.0) {
                reasons.add("Similar genres: ${intersection.joinToString(", ")}")
            }

            // 2. Cast and Director overlap
            var castDirectorScore = 0.0
            val candDirector = candidate.director?.trim()?.lowercase() ?: ""
            if (targetDirector.isNotEmpty() && targetDirector == candDirector) {
                castDirectorScore += 10.0
                reasons.add("Directed by the same director: ${candidate.director}")
            }

            val candCast = candidate.cast.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            val castIntersection = targetCast.intersect(candCast)
            if (castIntersection.isNotEmpty()) {
                val castBoost = (castIntersection.size * 5.0).coerceAtMost(15.0)
                castDirectorScore += castBoost
                reasons.add("Stars same cast: ${castIntersection.joinToString(", ")}")
            }

            // 3. Rating Closeness
            val candRating = candidate.rating ?: 5.0
            val ratingDiff = abs(targetRating - candRating)
            val closeness = (1.0 - (ratingDiff / 10.0)).coerceIn(0.0, 1.0)
            val ratingScore = closeness * 10.0

            // 4. Era / Release Year Proximity (up to 5 points)
            val targetYear = target.releaseYear ?: 2020
            val candYear = candidate.releaseYear ?: 2020
            val yearDiff = abs(targetYear - candYear)
            val eraCloseness = (1.0 - (yearDiff.toDouble() / 30.0)).coerceIn(0.0, 1.0)
            val eraScore = eraCloseness * 5.0
            if (eraScore > 0.0) {
                reasons.add("Release year era similarity: +${String.format(java.util.Locale.US, "%.1f", eraScore)}")
            }

            val providerSimilarBoost = if (candidate.id in providerSimilarIds || candidate.externalId in providerSimilarIds) {
                reasons.add("Provider-suggested similar title")
                12.0
            } else {
                0.0
            }

            val globalSimilarBoost = if (candidate.id in globalSimilarIds || candidate.externalId in globalSimilarIds) {
                reasons.add("Suggested by global similarity engine")
                15.0
            } else {
                0.0
            }

            val totalScore = genreScore + castDirectorScore + ratingScore + providerSimilarBoost + globalSimilarBoost + eraScore

            val breakdown = ScoreBreakdown(
                ftsScore = 0.0,
                exactPrefixBoost = 0.0,
                aliasBoost = providerSimilarBoost + eraScore + globalSimilarBoost,
                qualityBoost = ratingScore,
                profileBoost = genreScore + castDirectorScore,
                liveNowBoost = 0.0,
                penalty = 0.0,
                reasons = reasons
            )

            RecommendationItem(
                id = candidate.id,
                type = candidate.type,
                title = candidate.title,
                score = totalScore,
                reason = reasons.firstOrNull() ?: "Similar content",
                scoreBreakdown = breakdown
            )
        }.sortedByDescending { it.score }.take(limit)
    }
}
