package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.*
import com.example.calmsource.core.discoveryengine.models.RecommendationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.*

class SearchRankerTest {

    @Test
    fun testSearchRankingAndScoring() {
        val mockDao = mock(DiscoveryEngineDao::class.java)
        val profileId = "p-adult"
        val query = "Inception"

        val rawResults = listOf(
            mapOf("id" to "m-inception", "type" to "movie", "title" to "Inception", "normalized_title" to "inception"),
            mapOf("id" to "m-interstellar", "type" to "movie", "title" to "Interstellar", "normalized_title" to "interstellar"),
            mapOf("id" to "m-inc-copy", "type" to "movie", "title" to "Inception Copy", "normalized_title" to "inception copy"),
            mapOf("id" to "m-hidden", "type" to "movie", "title" to "Hidden Inception", "normalized_title" to "hidden inception")
        )

        // Mock DB lookups
        val mediaItems = listOf(
            createMockMediaItem(
                "m-inception",
                "movie",
                "Inception",
                2010,
                8.8,
                """{"imdb":"tt1375666","stremio":"catalog:inception"}"""
            ),
            createMockMediaItem("m-interstellar", "movie", "Interstellar", 2014, 8.6),
            createMockMediaItem("m-inc-copy", "movie", "Inception Copy", 2021, 6.0),
            createMockMediaItem("m-hidden", "movie", "Hidden Inception", 2023, 7.0)
        )
        `when`(mockDao.getMediaItemsByIds(anyList())).thenReturn(mediaItems)
        `when`(mockDao.getChannelsByIds(anyList())).thenReturn(emptyList())

        // Mock User States (m-inception is favorite, m-hidden is hidden)
        val userStates = listOf(
            UserItemStateEntity(profileId, "m-inception", isFavorite = true, isHidden = false, lastWatchedAt = null, progressMs = 0L, durationMs = 0L, watchCount = 0, isCompleted = false),
            UserItemStateEntity(profileId, "m-hidden", isFavorite = false, isHidden = true, lastWatchedAt = null, progressMs = 0L, durationMs = 0L, watchCount = 0, isCompleted = false)
        )
        `when`(mockDao.getUserItemStatesForProfile(profileId)).thenReturn(userStates)
        `when`(mockDao.getUserChannelStatesForProfile(profileId)).thenReturn(emptyList())
        `when`(mockDao.getLatestWatchEventsForProfile(profileId)).thenReturn(emptyList())

        val ranked = SearchRanker.rankSearchResults(mockDao, profileId, query, rawResults)

        // Verify that:
        // 1. The hidden item is completely filtered out
        val hiddenMatch = ranked.any { it.id == "m-hidden" }
        assertEquals(false, hiddenMatch)

        // 2. The remaining items are sorted by score
        assertTrue(ranked.size >= 2)
        
        // Inception should be #1 (exact match + favorite boost + high rating)
        assertEquals("m-inception", ranked[0].id)
        assertTrue(ranked[0].score > ranked[1].score)
        assertEquals("tt1375666", ranked[0].externalIds["imdb"])
        assertEquals("catalog:inception", ranked[0].externalIds["stremio"])
        
        // Inception Copy should be #2 (prefix match)
        assertEquals("m-inc-copy", ranked[1].id)
    }

    private fun createMockMediaItem(
        id: String,
        type: String,
        title: String,
        year: Int,
        rating: Double,
        externalIdsJson: String = "{}"
    ): MediaItemEntity {
        return MediaItemEntity(
            id = id,
            type = type,
            title = title,
            overview = "Overview of $title",
            posterUrl = "url",
            rating = rating,
            releaseYear = year,
            genres = "Action,Sci-Fi",
            cast = "Leonardo DiCaprio",
            director = "Christopher Nolan",
            language = "en",
            durationMs = 7000000L,
            externalId = null,
            externalIdsJson = externalIdsJson,
            source = "stremio",
            seriesId = null,
            seasonNumber = null,
            episodeNumber = null,
            normalizedTitle = title.lowercase(),
            updatedAt = System.currentTimeMillis()
        )
    }
}
