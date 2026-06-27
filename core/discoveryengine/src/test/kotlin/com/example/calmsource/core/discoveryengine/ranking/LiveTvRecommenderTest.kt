package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.ChannelEntity
import com.example.calmsource.core.discoveryengine.database.EpgProgramEntity
import com.example.calmsource.core.discoveryengine.database.UserChannelStateEntity
import com.example.calmsource.core.discoveryengine.database.WatchEventEntity
import com.example.calmsource.core.discoveryengine.models.TasteProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTvRecommenderTest {

    @Test
    fun testLiveTvRecommenderBoosts() {
        val tasteProfile = TasteProfile(
            profileId = "p-1",
            genreAffinities = mapOf("sports" to 1.0),
            languageAffinities = emptyMap(),
            sourceAffinities = emptyMap()
        )

        val channelSports = ChannelEntity("c-sports", "Sports Channel", null, "url", "Sports", "provider", null, 0L)
        val channelNews = ChannelEntity("c-news", "News Channel", null, "url", "News", "provider", null, 0L)

        // User watched sports channel at 14:00 UTC (14 hours)
        val watchEvent = WatchEventEntity(
            profileId = "p-1",
            itemId = "c-sports",
            itemType = "channel",
            timestamp = 14L * 60L * 60L * 1000L, // 14 hours
            progressMs = 0L,
            durationMs = 0L,
            eventType = "progress"
        )

        // Epg programs
        val epgSports = EpgProgramEntity("epg-1", "c-sports", "Live Soccer", "", "Sports", 0L, 0L, "en", null, 0L)
        val epgNews = EpgProgramEntity("epg-2", "c-news", "News Program", "", "News", 0L, 0L, "en", null, 0L)

        // Current time: 14:30 (hour 14)
        val currentTime = 14L * 60L * 60L * 1000L + 30L * 60L * 1000L

        val results = LiveTvRecommender.recommend(
            tasteProfile = tasteProfile,
            channels = listOf(channelSports, channelNews),
            userChannelStates = emptyMap(),
            watchEvents = listOf(watchEvent),
            currentEpg = mapOf("c-sports" to epgSports, "c-news" to epgNews),
            currentTimeMs = currentTime,
            limit = 10
        )

        assertEquals(2, results.size)
        // Sports Channel should rank first due to:
        // - Base: 5.0
        // - Time of day boost: 2.0 (watched around hour 14)
        // - EPG category matches "sports" user affinity (1.0 * 15.0) = 15.0
        // - Total score: 5.0 + 2.0 + 15.0 = 22.0
        // News Channel score: 5.0 + 0 + 0 = 5.0
        assertEquals("c-sports", results[0].id)
        assertEquals(22.0, results[0].score, 0.01)
        assertEquals("c-news", results[1].id)
        assertEquals(5.0, results[1].score, 0.01)
    }

    @Test
    fun compoundEpgCategoriesMatchPreferenceTokens() {
        val tasteProfile = TasteProfile(
            profileId = "p-1",
            genreAffinities = mapOf("sports" to 1.0, "live" to 1.0),
            languageAffinities = emptyMap(),
            sourceAffinities = emptyMap()
        )

        val channelSports = ChannelEntity("c-sports", "Match Channel", null, "url", "Sports", "provider", null, 0L)
        val channelMovie = ChannelEntity("c-movie", "Movie Channel", null, "url", "Movies", "provider", null, 0L)
        val epgSports = EpgProgramEntity("epg-1", "c-sports", "Live Soccer", "", "Sports - Live", 0L, 0L, "en", null, 0L)
        val epgMovie = EpgProgramEntity("epg-2", "c-movie", "Feature Presentation", "", "Movie", 0L, 0L, "en", null, 0L)

        val results = LiveTvRecommender.recommend(
            tasteProfile = tasteProfile,
            channels = listOf(channelMovie, channelSports),
            userChannelStates = emptyMap(),
            watchEvents = emptyList(),
            currentEpg = mapOf("c-sports" to epgSports, "c-movie" to epgMovie),
            currentTimeMs = 0L,
            limit = 10
        )

        assertEquals("c-sports", results[0].id)
        assertEquals(20.0, results[0].score, 0.01)
        assertEquals("c-movie", results[1].id)
        assertEquals(5.0, results[1].score, 0.01)
    }
}
