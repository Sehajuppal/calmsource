package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.MediaItemEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SimilarityFinderTest {

    @Test
    fun testFindSimilar() {
        val target = MediaItemEntity("m-target", "movie", "Interstellar", "Space movie", null, 8.6, 2014, "Sci-Fi,Adventure", "Matthew McConaughey,Anne Hathaway", "Christopher Nolan", "en", 1000L, null, "{}", "stremio", null, null, null, "interstellar", 0L)

        val similarCandidate = MediaItemEntity("m-1", "movie", "Inception", "Dream movie", null, 8.8, 2010, "Sci-Fi,Adventure", "Leonardo DiCaprio,Ellen Page", "Christopher Nolan", "en", 1000L, null, "{}", "stremio", null, null, null, "inception", 0L)
        val differentCandidate = MediaItemEntity("m-2", "movie", "The Notebook", "Romance movie", null, 7.8, 2004, "Romance,Drama", "Ryan Gosling", "Nick Cassavetes", "en", 1000L, null, "{}", "stremio", null, null, null, "the notebook", 0L)

        val results = SimilarityFinder.findSimilar(
            target = target,
            candidates = listOf(similarCandidate, differentCandidate),
            limit = 5
        )

        assertEquals(2, results.size)
        assertEquals("m-1", results[0].id)
    }

    @Test
    fun providerSimilarIdsBoostMatchingCandidate() {
        val target = MediaItemEntity("m-target", "movie", "Target", null, null, 5.0, 2020, "Drama", "", null, "en", null, null, "{}", null, null, null, null, "target", 0L)
        val localMatch = MediaItemEntity("m-local", "movie", "Local Match", null, null, 5.0, 2020, "Drama", "", null, "en", null, null, "{}", null, null, null, null, "local match", 0L)
        val providerMatch = MediaItemEntity("m-provider", "movie", "Provider Match", null, null, 5.0, 2020, "Drama", "", null, "en", null, null, "{}", null, null, null, null, "provider match", 0L)

        val results = SimilarityFinder.findSimilar(
            target = target,
            candidates = listOf(localMatch, providerMatch),
            providerSimilarIds = setOf("m-provider")
        )

        assertEquals("m-provider", results.first().id)
    }

    @Test
    fun eraProximityBoostsRecentTitles() {
        val target = MediaItemEntity("m-target", "movie", "Target", null, null, 5.0, 2020, "Drama", "", null, "en", null, null, "{}", null, null, null, null, "target", 0L)
        val closeYear = MediaItemEntity("m-close", "movie", "Close Year", null, null, 5.0, 2018, "Drama", "", null, "en", null, null, "{}", null, null, null, null, "close year", 0L)
        val farYear = MediaItemEntity("m-far", "movie", "Far Year", null, null, 5.0, 1990, "Drama", "", null, "en", null, null, "{}", null, null, null, null, "far year", 0L)

        val results = SimilarityFinder.findSimilar(
            target = target,
            candidates = listOf(farYear, closeYear),
            limit = 5
        )

        assertEquals("m-close", results.first().id)
    }

    @Test
    fun weightedGenreBoostsNicheOverlaps() {
        val target = MediaItemEntity("m-target", "movie", "Target", null, null, 5.0, 2020, "Drama,Cyberpunk", "", null, "en", null, null, "{}", null, null, null, null, "target", 0L)
        
        val commonGenreMatch = MediaItemEntity("m-common", "movie", "Drama Movie", null, null, 5.0, 2020, "Drama,Comedy", "", null, "en", null, null, "{}", null, null, null, null, "drama movie", 0L)
        val nicheGenreMatch = MediaItemEntity("m-niche", "movie", "Cyberpunk Movie", null, null, 5.0, 2020, "Cyberpunk,Sci-Fi", "", null, "en", null, null, "{}", null, null, null, null, "cyberpunk movie", 0L)
        
        val extra1 = MediaItemEntity("m-extra1", "movie", "Extra 1", null, null, 5.0, 2020, "Drama", "", null, "en", null, null, "{}", null, null, null, null, "extra 1", 0L)
        val extra2 = MediaItemEntity("m-extra2", "movie", "Extra 2", null, null, 5.0, 2020, "Drama", "", null, "en", null, null, "{}", null, null, null, null, "extra 2", 0L)
        val extra3 = MediaItemEntity("m-extra3", "movie", "Extra 3", null, null, 5.0, 2020, "Drama", "", null, "en", null, null, "{}", null, null, null, null, "extra 3", 0L)

        val results = SimilarityFinder.findSimilar(
            target = target,
            candidates = listOf(commonGenreMatch, nicheGenreMatch, extra1, extra2, extra3),
            limit = 5
        )

        assertEquals("m-niche", results.first().id)
    }

    @Test
    fun globalSimilarIdsBoostMatchingCandidate() {
        val target = MediaItemEntity("m-target", "movie", "Target", null, null, 5.0, 2020, "Drama", "", null, "en", null, null, "{}", null, null, null, null, "target", 0L)
        val localMatch = MediaItemEntity("m-local", "movie", "Local Match", null, null, 5.0, 2020, "Drama", "", null, "en", null, null, "{}", null, null, null, null, "local match", 0L)
        val globalMatch = MediaItemEntity("m-global", "movie", "Global Match", null, null, 5.0, 2020, "Drama", "", null, "en", null, null, "{}", null, null, null, null, "global match", 0L)

        val results = SimilarityFinder.findSimilar(
            target = target,
            candidates = listOf(localMatch, globalMatch),
            globalSimilarIds = setOf("m-global")
        )

        assertEquals("m-global", results.first().id)
        assertEquals(true, results.first().scoreBreakdown.reasons.contains("Suggested by global similarity engine"))
    }
}
