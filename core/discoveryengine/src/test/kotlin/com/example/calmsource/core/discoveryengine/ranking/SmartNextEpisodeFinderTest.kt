package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineDao
import com.example.calmsource.core.discoveryengine.database.MediaItemEntity
import com.example.calmsource.core.discoveryengine.database.WatchEventEntity
import com.example.calmsource.core.discoveryengine.models.RecommendationType
import com.example.calmsource.core.discoveryengine.normalization.EpisodeParser
import com.example.calmsource.core.discoveryengine.normalization.ShowExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.*

class SmartNextEpisodeFinderTest {

    @Test
    fun testEpisodeParserMessyFilenames() {
        // 1. S01E04
        val res1 = EpisodeParser.parse("Breaking.Bad.S01E04.1080p")
        assertNotNull(res1)
        assertEquals(1, res1!!.season)
        assertEquals(4, res1.episode)

        // 2. 1x04
        val res2 = EpisodeParser.parse("Stranger.Things.3x08.HEVC")
        assertNotNull(res2)
        assertEquals(3, res2!!.season)
        assertEquals(8, res2.episode)

        // 3. Season 1 Episode 4
        val res3 = EpisodeParser.parse("Planet Earth Season 2 Ep 3 4K")
        assertNotNull(res3)
        assertEquals(2, res3!!.season)
        assertEquals(3, res3.episode)

        // 4. Episode 4 / Ep 4
        val res4 = EpisodeParser.parse("Breaking Bad Ep 12")
        assertNotNull(res4)
        assertEquals(1, res4!!.season) // Defaults to season 1
        assertEquals(12, res4.episode)

        // 5. 104 / 1204
        val res5 = EpisodeParser.parse("The.Simpsons.305.1080p")
        assertNotNull(res5)
        assertEquals(3, res5!!.season)
        assertEquals(5, res5.episode)

        // 6. Year exclusion safety checks
        val resYear1 = EpisodeParser.parse("Inception.2010.BluRay")
        assertNull(resYear1) // 2010 is a year, should be excluded

        val resYear2 = EpisodeParser.parse("Show.S01E02.2018")
        assertNotNull(resYear2)
        assertEquals(1, resYear2!!.season)
        assertEquals(2, resYear2.episode) // Matches S01E02, ignores 2018
    }

    @Test
    fun testShowExtractor() {
        assertEquals("breaking bad", ShowExtractor.extractShowName("Breaking Bad - S01E01 - Pilot"))
        assertEquals("stranger things", ShowExtractor.extractShowName("Stranger Things 3x08 HEVC"))
        assertEquals("planet earth", ShowExtractor.extractShowName("Planet Earth Season 2 Episode 3"))
    }

    @Test
    fun testUnfinishedEpisodeReturnsSame() {
        val mockDao = mock(DiscoveryEngineDao::class.java)
        val seriesId = "s-bb"
        val profileId = "p-adult"

        // Mock database episodes
        val episodes = listOf(
            createMockEpisode("e-bb-1", seriesId, 1, 1),
            createMockEpisode("e-bb-2", seriesId, 1, 2)
        )
        `when`(mockDao.getEpisodesForSeries(seriesId)).thenReturn(episodes)

        // Mock watch events (progress is 400ms out of 1000ms = 40% < 85%)
        val watchEvents = listOf(
            createMockWatchEvent(profileId, "e-bb-1", 400L, 1000L, 123456789L)
        )
        `when`(mockDao.getLatestWatchEventsForProfile(profileId)).thenReturn(watchEvents)

        val result = SmartNextEpisodeFinder.findNextEpisode(mockDao, profileId, seriesId)

        assertEquals(RecommendationType.CONTINUE_EPISODE, result.recommendationType)
        assertEquals("e-bb-1", result.targetEpisodeId)
        assertEquals(1, result.seasonNumber)
        assertEquals(1, result.episodeNumber)
        assertEquals(0.4, result.progress!!, 0.01)
    }

    @Test
    fun testCompletedEpisodeReturnsNext() {
        val mockDao = mock(DiscoveryEngineDao::class.java)
        val seriesId = "s-bb"
        val profileId = "p-adult"

        val episodes = listOf(
            createMockEpisode("e-bb-1", seriesId, 1, 1),
            createMockEpisode("e-bb-2", seriesId, 1, 2)
        )
        `when`(mockDao.getEpisodesForSeries(seriesId)).thenReturn(episodes)

        // Mock watch events (progress is 900ms out of 1000ms = 90% >= 85%)
        val watchEvents = listOf(
            createMockWatchEvent(profileId, "e-bb-1", 900L, 1000L, 123456789L)
        )
        `when`(mockDao.getLatestWatchEventsForProfile(profileId)).thenReturn(watchEvents)

        val result = SmartNextEpisodeFinder.findNextEpisode(mockDao, profileId, seriesId)

        assertEquals(RecommendationType.NEXT_EPISODE, result.recommendationType)
        assertEquals("e-bb-2", result.targetEpisodeId)
        assertEquals(1, result.seasonNumber)
        assertEquals(2, result.episodeNumber)
        assertEquals(0.0, result.progress!!, 0.01)
    }

    @Test
    fun testSeasonFinaleReturnsNextSeasonEpisodeOne() {
        val mockDao = mock(DiscoveryEngineDao::class.java)
        val seriesId = "s-bb"
        val profileId = "p-adult"

        val episodes = listOf(
            createMockEpisode("e-bb-s1e1", seriesId, 1, 1),
            createMockEpisode("e-bb-s1e2", seriesId, 1, 2), // Season 1 Finale
            createMockEpisode("e-bb-s2e1", seriesId, 2, 1)  // Season 2 Premiere
        )
        `when`(mockDao.getEpisodesForSeries(seriesId)).thenReturn(episodes)

        // Watched Season 1 Finale (95% complete)
        val watchEvents = listOf(
            createMockWatchEvent(profileId, "e-bb-s1e1", 1000L, 1000L, 100L),
            createMockWatchEvent(profileId, "e-bb-s1e2", 950L, 1000L, 200L) // Latest watched
        )
        `when`(mockDao.getLatestWatchEventsForProfile(profileId)).thenReturn(watchEvents)

        val result = SmartNextEpisodeFinder.findNextEpisode(mockDao, profileId, seriesId)

        assertEquals(RecommendationType.NEXT_SEASON, result.recommendationType)
        assertEquals("e-bb-s2e1", result.targetEpisodeId)
        assertEquals(2, result.seasonNumber)
        assertEquals(1, result.episodeNumber)
        assertEquals(0.0, result.progress!!, 0.01)
    }

    @Test
    fun testFinalAvailableEpisodeReturnsCaughtUp() {
        val mockDao = mock(DiscoveryEngineDao::class.java)
        val seriesId = "s-bb"
        val profileId = "p-adult"

        val episodes = listOf(
            createMockEpisode("e-bb-1", seriesId, 1, 1),
            createMockEpisode("e-bb-2", seriesId, 1, 2)
        )
        `when`(mockDao.getEpisodesForSeries(seriesId)).thenReturn(episodes)

        // Watched final available episode (e-bb-2) (99% complete)
        val watchEvents = listOf(
            createMockWatchEvent(profileId, "e-bb-1", 1000L, 1000L, 100L),
            createMockWatchEvent(profileId, "e-bb-2", 990L, 1000L, 300L) // Latest watched
        )
        `when`(mockDao.getLatestWatchEventsForProfile(profileId)).thenReturn(watchEvents)

        val result = SmartNextEpisodeFinder.findNextEpisode(mockDao, profileId, seriesId)

        assertEquals(RecommendationType.CAUGHT_UP, result.recommendationType)
        assertNull(result.targetEpisodeId)
        assertEquals(1, result.seasonNumber)
        assertEquals(2, result.episodeNumber)
        assertEquals(1.0, result.progress!!, 0.01)
    }

    @Test
    fun testTwoProfilesGetDifferentNextEpisodes() {
        val mockDao = mock(DiscoveryEngineDao::class.java)
        val seriesId = "s-bb"

        val episodes = listOf(
            createMockEpisode("e-bb-1", seriesId, 1, 1),
            createMockEpisode("e-bb-2", seriesId, 1, 2)
        )
        `when`(mockDao.getEpisodesForSeries(seriesId)).thenReturn(episodes)

        // Profile A watched S1E1 to 90% (completed) -> should get S1E2 next
        val watchEventsA = listOf(
            createMockWatchEvent("profile-a", "e-bb-1", 900L, 1000L, 123456789L)
        )
        `when`(mockDao.getLatestWatchEventsForProfile("profile-a")).thenReturn(watchEventsA)

        val resultA = SmartNextEpisodeFinder.findNextEpisode(mockDao, "profile-a", seriesId)
        assertEquals(RecommendationType.NEXT_EPISODE, resultA.recommendationType)
        assertEquals("e-bb-2", resultA.targetEpisodeId)

        // Profile B watched S1E1 to 30% (unfinished) -> should continue S1E1
        val watchEventsB = listOf(
            createMockWatchEvent("profile-b", "e-bb-1", 300L, 1000L, 123456789L)
        )
        `when`(mockDao.getLatestWatchEventsForProfile("profile-b")).thenReturn(watchEventsB)

        val resultB = SmartNextEpisodeFinder.findNextEpisode(mockDao, "profile-b", seriesId)
        assertEquals(RecommendationType.CONTINUE_EPISODE, resultB.recommendationType)
        assertEquals("e-bb-1", resultB.targetEpisodeId)
    }

    private fun createMockEpisode(
        id: String,
        seriesId: String,
        season: Int,
        episode: Int
    ): MediaItemEntity {
        return MediaItemEntity(
            id = id,
            type = "episode",
            title = "Episode Title",
            overview = "Overview",
            posterUrl = null,
            rating = 8.0,
            releaseYear = 2020,
            genres = "",
            cast = "",
            director = null,
            language = "en",
            durationMs = 1000L,
            externalId = null,
            externalIdsJson = "{}",
            source = "stremio",
            seriesId = seriesId,
            seasonNumber = season,
            episodeNumber = episode,
            normalizedTitle = "episode title",
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun createMockWatchEvent(
        profileId: String,
        itemId: String,
        progress: Long,
        duration: Long,
        timestamp: Long
    ): WatchEventEntity {
        return WatchEventEntity(
            profileId = profileId,
            itemId = itemId,
            itemType = "episode",
            timestamp = timestamp,
            progressMs = progress,
            durationMs = duration,
            eventType = "progress"
        )
    }
}
