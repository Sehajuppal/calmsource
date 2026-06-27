package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineDao
import com.example.calmsource.core.discoveryengine.database.UserItemStateEntity
import com.example.calmsource.core.discoveryengine.models.RecommendationItem
import com.example.calmsource.core.discoveryengine.models.ScoreBreakdown
import com.example.calmsource.core.discoveryengine.models.SearchResult
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class QualityEvaluationTest {

    private lateinit var mockDao: DiscoveryEngineDao

    @Before
    fun setUp() {
        mockDao = mock(DiscoveryEngineDao::class.java)
    }

    @Test
    fun testEvaluateSearchRankingQuality() {
        val profileId = "prof-1"
        val query = "Inception"

        // Candidates matching the search
        val rawResults = listOf(
            mapOf("id" to "m-1", "type" to "movie", "title" to "The Matrix", "normalized_title" to "the matrix", "overview" to "", "genres" to "Sci-Fi", "cast_director" to ""),
            mapOf("id" to "m-2", "type" to "movie", "title" to "Inception", "normalized_title" to "inception", "overview" to "", "genres" to "Sci-Fi", "cast_director" to ""),
            mapOf("id" to "m-3", "type" to "movie", "title" to "Inception 2", "normalized_title" to "inception 2", "overview" to "", "genres" to "Sci-Fi", "cast_director" to "")
        )

        // Mock user states: Inception is a favorite (+30 boost)
        val inceptionState = UserItemStateEntity(
            profileId = profileId,
            itemId = "m-2",
            isFavorite = true,
            isHidden = false,
            lastWatchedAt = null,
            progressMs = 0L,
            durationMs = 0L,
            watchCount = 0,
            isCompleted = false
        )
        `when`(mockDao.getUserItemStatesForProfile(profileId)).thenReturn(listOf(inceptionState))

        // Rank search results
        val ranked = SearchRanker.rankSearchResults(mockDao, profileId, query, rawResults, 10)

        // Calculate Reciprocal Rank (RR) of target item "m-2" (Inception)
        // Since it matches the query exactly and is a favorite, it should rank at #1 (index 0)
        val rank = ranked.indexOfFirst { it.id == "m-2" } + 1
        val reciprocalRank = if (rank > 0) 1.0 / rank else 0.0

        println("=== Search Quality Evaluation ===")
        println("Query: '$query', Target Item Rank: $rank, Reciprocal Rank: $reciprocalRank")
        ranked.forEachIndexed { idx, res ->
            println("  Rank ${idx + 1}: ${res.title} (Score: ${res.score})")
        }
        println("=================================")

        // Assert optimal ranking (Inception should rank at rank 1)
        assertEquals(1, rank)
        assertEquals(1.0, reciprocalRank, 0.001)
    }

    @Test
    fun testEvaluateSimpsonDiversityIndexCalculation() {
        // Verify the Simpson's Index of Diversity formula: 1 - sum((n / N) ^ 2)
        // A set with 3 identical genres should have low diversity
        val uniformItems = listOf(
            RecommendationItem("1", "movie", "Sci-Fi Movie 1", 10.0, "", ScoreBreakdown()),
            RecommendationItem("2", "movie", "Sci-Fi Movie 2", 9.5, "", ScoreBreakdown()),
            RecommendationItem("3", "movie", "Sci-Fi Movie 3", 9.0, "", ScoreBreakdown())
        )
        val uniformGenres = mapOf("1" to listOf("Sci-Fi"), "2" to listOf("Sci-Fi"), "3" to listOf("Sci-Fi"))
        val uniformDiversity = calculateSimpsonDiversityIndex(uniformItems, uniformGenres)
        assertEquals(0.0, uniformDiversity, 0.001)

        // A set with 3 different genres should have high diversity
        val diverseItems = listOf(
            RecommendationItem("4", "movie", "Sci-Fi Movie", 10.0, "", ScoreBreakdown()),
            RecommendationItem("5", "movie", "Drama Movie", 9.5, "", ScoreBreakdown()),
            RecommendationItem("6", "movie", "Action Movie", 9.0, "", ScoreBreakdown())
        )
        val diverseGenres = mapOf("4" to listOf("Sci-Fi"), "5" to listOf("Drama"), "6" to listOf("Action"))
        val diverseDiversity = calculateSimpsonDiversityIndex(diverseItems, diverseGenres)
        assertTrue("Diverse genres should have high diversity", diverseDiversity > 0.5)
    }

    private fun calculateSimpsonDiversityIndex(items: List<RecommendationItem>, genresMap: Map<String, List<String>>): Double {
        val totalGenres = items.flatMap { genresMap[it.id] ?: emptyList() }
        val n = totalGenres.size
        if (n <= 1) return 0.0

        val counts = totalGenres.groupingBy { it }.eachCount()
        val sumSquared = counts.values.sumOf { count ->
            (count.toDouble() / n) * (count.toDouble() / n)
        }
        return 1.0 - sumSquared
    }

    private fun assertEquals(expected: Int, actual: Int) {
        org.junit.Assert.assertEquals(expected.toLong(), actual.toLong())
    }

    private fun assertEquals(expected: Double, actual: Double, delta: Double) {
        org.junit.Assert.assertEquals(expected, actual, delta)
    }
}

