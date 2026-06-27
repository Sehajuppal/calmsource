package com.example.calmsource.core.discoveryengine

import com.example.calmsource.core.discoveryengine.models.*
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DiscoveryEngineModelsTest {

    @Test
    fun testMediaItemSerialization() {
        val mediaItem = MediaItem(
            id = "movie-1",
            type = "movie",
            title = "Spider-Man",
            overview = "Spider-Man overview",
            posterUrl = "http://poster.jpg",
            rating = 8.5,
            releaseYear = 2002,
            genres = listOf("Action", "Sci-Fi"),
            externalIds = mapOf("imdb" to "tt0145487")
        )

        val jsonString = Json.encodeToString(MediaItem.serializer(), mediaItem)
        assertNotNull(jsonString)

        val deserialized = Json.decodeFromString(MediaItem.serializer(), jsonString)
        assertEquals(mediaItem.id, deserialized.id)
        assertEquals(mediaItem.title, deserialized.title)
        assertEquals(mediaItem.type, deserialized.type)
        assertEquals(mediaItem.genres, deserialized.genres)
        assertEquals(mediaItem.externalIds["imdb"], deserialized.externalIds["imdb"])
    }

    @Test
    fun testSearchResultCreation() {
        val breakdown = ScoreBreakdown(ftsScore = 10.0, exactPrefixBoost = 5.0)
        val result = SearchResult(
            id = "movie-1",
            type = "movie",
            title = "Spider-Man",
            subtitle = "Action",
            posterUrl = "http://poster.jpg",
            score = 15.0,
            source = "stremio-addon",
            scoreBreakdown = breakdown
        )

        assertEquals("movie-1", result.id)
        assertEquals(15.0, result.score, 0.001)
        assertEquals(10.0, result.scoreBreakdown.ftsScore, 0.001)
    }

    @Test
    fun testWatchEventSerialization() {
        val event = WatchEvent(
            profileId = "profile-1",
            itemId = "item-123",
            itemType = "movie",
            timestamp = 1717960000L,
            progressMs = 5000L,
            durationMs = 120000L,
            eventType = "progress"
        )
        val jsonString = Json.encodeToString(WatchEvent.serializer(), event)
        val deserialized = Json.decodeFromString(WatchEvent.serializer(), jsonString)
        assertEquals(event.profileId, deserialized.profileId)
        assertEquals(event.progressMs, deserialized.progressMs)
    }
}
