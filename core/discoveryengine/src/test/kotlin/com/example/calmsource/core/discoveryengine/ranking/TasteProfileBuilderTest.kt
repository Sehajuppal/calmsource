package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito.*

class TasteProfileBuilderTest {

    @Test
    fun testBuildTasteProfile() {
        val mockDao = mock(DiscoveryEngineDao::class.java)
        val profileId = "p-1"

        // Mock states: user watched m-1 (Sci-Fi, en, watchCount = 2, isFavorite = false)
        // and user favorited m-2 (Drama, en, watchCount = 0, isFavorite = true)
        val states = listOf(
            UserItemStateEntity(profileId, "m-1", isFavorite = false, isHidden = false, lastWatchedAt = 0L, progressMs = 0L, durationMs = 0L, watchCount = 2, isCompleted = false),
            UserItemStateEntity(profileId, "m-2", isFavorite = true, isHidden = false, lastWatchedAt = 0L, progressMs = 0L, durationMs = 0L, watchCount = 0, isCompleted = false)
        )
        `when`(mockDao.getUserItemStatesForProfile(profileId)).thenReturn(states)
        `when`(mockDao.getUserChannelStatesForProfile(profileId)).thenReturn(emptyList())

        // Mock media items
        val media1 = MediaItemEntity("m-1", "movie", "Interstellar", "Space movie", null, 8.6, 2014, "Sci-Fi,Adventure", "", null, "en", 1000L, null, "{}", "stremio", null, null, null, "interstellar", 0L)
        val media2 = MediaItemEntity("m-2", "movie", "The Godfather", "Drama movie", null, 9.2, 1972, "Drama,Crime", "", null, "en", 1000L, null, "{}", "stremio", null, null, null, "the godfather", 0L)
        `when`(mockDao.getMediaItemsByIds(listOf("m-1", "m-2"))).thenReturn(listOf(media1, media2))

        val profile = TasteProfileBuilder.buildTasteProfile(mockDao, profileId)

        assertNotNull(profile)
        assertEquals(profileId, profile.profileId)

        // Weights:
        // m-1 weight: 2.0 (watchCount = 2) -> genres: "sci-fi" (2.0), "adventure" (2.0)
        // m-2 weight: 3.0 (isFavorite = true) -> genres: "drama" (3.0), "crime" (3.0)
        // Max weight for genres: 3.0 (Drama/Crime)
        // Affinity for drama should be 1.0 (3.0 / 3.0)
        // Affinity for sci-fi should be 0.666 (2.0 / 3.0)
        assertEquals(1.0, profile.genreAffinities["drama"]!!, 0.01)
        assertEquals(0.666, profile.genreAffinities["sci-fi"]!!, 0.01)

        // Languages:
        // en weight = 2.0 (from m-1) + 3.0 (from m-2) = 5.0 -> normalized to 1.0
        assertEquals(1.0, profile.languageAffinities["en"]!!, 0.01)
    }
}
