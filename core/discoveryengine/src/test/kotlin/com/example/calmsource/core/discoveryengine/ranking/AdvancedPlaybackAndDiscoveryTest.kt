package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.*
import com.example.calmsource.core.discoveryengine.models.*
import com.example.calmsource.core.discoveryengine.normalization.EntityResolver
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class AdvancedPlaybackAndDiscoveryTest {

    @Test
    fun testEntityResolutionDuplicateMerging() {
        val mockDao = mock(DiscoveryEngineDao::class.java)

        // 1. Resolve by IMDb ID match
        `when`(mockDao.getMediaItemByExternalId("tt12345")).thenReturn(
            MediaItemEntity(
                id = "m-canonical-imdb", type = "movie", title = "Inception", overview = null,
                posterUrl = null, rating = 8.8, releaseYear = 2010, genres = "", cast = "",
                director = "Christopher Nolan", language = "en", durationMs = 0L, externalId = "tt12345",
                externalIdsJson = "{}", source = "stremio", seriesId = null, seasonNumber = null,
                episodeNumber = null, normalizedTitle = "inception", updatedAt = 0L
            )
        )
        
        val resolvedId1 = EntityResolver.resolveMediaItemId(
            dao = mockDao,
            title = "Inception (2010)",
            year = 2010,
            director = "Christopher Nolan",
            externalIds = mapOf("imdb" to "tt12345")
        )
        assertEquals("m-canonical-imdb", resolvedId1)

        // 2. Resolve by normalized title + year match
        `when`(mockDao.getMediaItemByExternalId(anyString())).thenReturn(null)
        `when`(mockDao.getMediaItemByNormalizedTitleAndYear("inception", 2010)).thenReturn(
            MediaItemEntity(
                id = "m-canonical-title-year", type = "movie", title = "Inception", overview = null,
                posterUrl = null, rating = 8.8, releaseYear = 2010, genres = "", cast = "",
                director = "Christopher Nolan", language = "en", durationMs = 0L, externalId = null,
                externalIdsJson = "{}", source = "stremio", seriesId = null, seasonNumber = null,
                episodeNumber = null, normalizedTitle = "inception", updatedAt = 0L
            )
        )

        val resolvedId2 = EntityResolver.resolveMediaItemId(
            dao = mockDao,
            title = "Inception",
            year = 2010,
            director = null,
            externalIds = emptyMap()
        )
        assertEquals("m-canonical-title-year", resolvedId2)
    }

    @Test
    fun testStreamQualityRanking() {
        val stream1 = MediaStreamEntity(
            id = "s-1", mediaId = "m-1", title = "Inception 4K HDR HEVC English", url = "url-1",
            resolution = "2160p", codec = "HEVC", quality = "UHD", sizeInBytes = 8000000000L,
            language = "en", isSubbed = false, isDubbed = false, source = "source-1", updatedAt = 0L
        )
        val stream2 = MediaStreamEntity(
            id = "s-2", mediaId = "m-1", title = "Inception 1080p Punjabi Audio x264", url = "url-2",
            resolution = "1080p", codec = "h264", quality = "FHD", sizeInBytes = 2000000000L,
            language = "pa", isSubbed = true, isDubbed = false, source = "source-1", updatedAt = 0L
        )
        val stream3 = MediaStreamEntity(
            id = "s-3", mediaId = "m-1", title = "Inception 720p Spanish Audio (Failed)", url = "url-3",
            resolution = "720p", codec = "h264", quality = "HD", sizeInBytes = 500000000L,
            language = "es", isSubbed = false, isDubbed = false, source = "source-1", updatedAt = 0L
        )

        val ranked = StreamRanker.rankWithSignals(
            streams = listOf(stream1, stream2, stream3),
            preferredAudio = listOf("pa"),
            preferredSub = listOf("en"),
            streamSuccessCount = { streamId -> if (streamId == "s-2") 2 else 0 },
            streamFailureCount = { streamId -> if (streamId == "s-3") 2 else 0 }
        )

        // Stream 2 should rank first because:
        // Punjabi matches preferred language, it has success history (+20), and FHD resolution is good.
        // Stream 3 should rank last because of failures.
        assertEquals(3, ranked.size)
        assertEquals("s-2", ranked[0].id)
        assertEquals("s-1", ranked[1].id)
        assertEquals("s-3", ranked[2].id)
    }

    @Test
    fun testAvailabilityAwareRecommendationsAndFeedbackExclusion() {
        val mockDao = mock(DiscoveryEngineDao::class.java)
        val profileId = "p-1"

        // Mock profile and feedback
        val profile = ProfileEntity(
            id = profileId, name = "John", avatarUrl = null, createdAt = 12345L,
            preferredAudioLanguages = "en", preferredSubtitleLanguages = "en"
        )
        `when`(mockDao.getProfile(profileId)).thenReturn(profile)

        val feedbacks = listOf(
            UserFeedbackEntity(profileId, "m-not-interested", "not_interested", 0L)
        )
        `when`(mockDao.getUserFeedbacksForProfile(profileId)).thenReturn(feedbacks)

        val rawResults = listOf(
            mapOf("id" to "m-playable", "type" to "movie", "title" to "Playable Movie", "normalized_title" to "playablemovie"),
            mapOf("id" to "m-not-interested", "type" to "movie", "title" to "Not Interested Movie", "normalized_title" to "notinterestedmovie")
        )

        val mediaItems = listOf(
            MediaItemEntity(
                id = "m-playable", type = "movie", title = "Playable Movie", overview = null,
                posterUrl = null, rating = 8.0, releaseYear = 2020, genres = "", cast = "",
                director = null, language = "en", durationMs = 0L, externalId = null,
                externalIdsJson = "{}", source = "stremio", seriesId = null, seasonNumber = null,
                episodeNumber = null, normalizedTitle = "playablemovie", updatedAt = 0L
            ),
            MediaItemEntity(
                id = "m-not-interested", type = "movie", title = "Not Interested Movie", overview = null,
                posterUrl = null, rating = 9.0, releaseYear = 2021, genres = "", cast = "",
                director = null, language = "en", durationMs = 0L, externalId = null,
                externalIdsJson = "{}", source = "stremio", seriesId = null, seasonNumber = null,
                episodeNumber = null, normalizedTitle = "notinterestedmovie", updatedAt = 0L
            )
        )
        `when`(mockDao.getMediaItemsByIds(listOf("m-playable", "m-not-interested"))).thenReturn(mediaItems)
        `when`(mockDao.getChannelsByIds(anyList())).thenReturn(emptyList())
        `when`(mockDao.getEmbeddingsByIds(anyList())).thenReturn(emptyList())
        `when`(mockDao.getUserItemStatesForProfile(profileId)).thenReturn(emptyList())
        `when`(mockDao.getUserChannelStatesForProfile(profileId)).thenReturn(emptyList())

        // Mock stream availability (batch query used by refactored SearchRanker)
        val streamPlayable = MediaStreamEntity(
            id = "s-play", mediaId = "m-playable", title = "Playable stream 1080p", url = "url",
            resolution = "1080p", codec = "h264", quality = "FHD", sizeInBytes = 0L,
            language = "en", isSubbed = false, isDubbed = false, source = "stremio", updatedAt = 0L
        )
        `when`(mockDao.getStreamsForMediaItems(anyList())).thenReturn(listOf(streamPlayable))
        `when`(mockDao.getPlaybackSuccessCounts(anyList())).thenReturn(emptyList())
        `when`(mockDao.getPlaybackFailureCounts(anyList())).thenReturn(emptyList())

        // Execute search ranking
        val ranked = SearchRanker.rankSearchResults(mockDao, profileId, "Movie", rawResults)

        // 1. Feedback exclusion: "m-not-interested" should be completely filtered out
        val containsNotInterested = ranked.any { it.id == "m-not-interested" }
        assertFalse(containsNotInterested)

        // 2. Availability score boost: "m-playable" gets boosts (+15 addon_has_stream, +1 streamCount, +15 preferredQuality, +20 preferredLanguage)
        val result = ranked.find { it.id == "m-playable" }
        assertNotNull(result)
        assertTrue(result!!.scoreBreakdown.availabilityScore > 0.0)
    }

    @Test
    fun testNextEpisodeFinderStreamAvailability() {
        val mockDao = mock(DiscoveryEngineDao::class.java)
        val profileId = "p-1"
        val seriesId = "s-series"

        // 1. Mock series and episodes
        val seriesItem = MediaItemEntity(
            id = seriesId, type = "series", title = "My Show", overview = null,
            posterUrl = null, rating = 8.0, releaseYear = 2020, genres = "", cast = "",
            director = null, language = "en", durationMs = 0L, externalId = null,
            externalIdsJson = "{}", source = "stremio", seriesId = null, seasonNumber = null,
            episodeNumber = null, normalizedTitle = "myshow", updatedAt = 0L
        )
        `when`(mockDao.getMediaItem(seriesId)).thenReturn(seriesItem)

        val episode1 = MediaItemEntity(
            id = "ep-1", type = "episode", title = "My Show S01E01", overview = null,
            posterUrl = null, rating = 8.0, releaseYear = 2020, genres = "", cast = "",
            director = null, language = "en", durationMs = 0L, externalId = null,
            externalIdsJson = "{}", source = "stremio", seriesId = seriesId, seasonNumber = 1,
            episodeNumber = 1, normalizedTitle = "myshows01e01", updatedAt = 0L
        )
        val episode2 = MediaItemEntity(
            id = "ep-2", type = "episode", title = "My Show S01E02", overview = null,
            posterUrl = null, rating = 8.0, releaseYear = 2020, genres = "", cast = "",
            director = null, language = "en", durationMs = 0L, externalId = null,
            externalIdsJson = "{}", source = "stremio", seriesId = seriesId, seasonNumber = 1,
            episodeNumber = 2, normalizedTitle = "myshows01e02", updatedAt = 0L
        )
        val episode3 = MediaItemEntity(
            id = "ep-3", type = "episode", title = "My Show S01E03", overview = null,
            posterUrl = null, rating = 8.0, releaseYear = 2020, genres = "", cast = "",
            director = null, language = "en", durationMs = 0L, externalId = null,
            externalIdsJson = "{}", source = "stremio", seriesId = seriesId, seasonNumber = 1,
            episodeNumber = 3, normalizedTitle = "myshows01e03", updatedAt = 0L
        )
        `when`(mockDao.getEpisodesForSeries(seriesId)).thenReturn(listOf(episode1, episode2, episode3))

        // Mock watch events - user has completed episode 1
        val watchEvent = WatchEventEntity(
            profileId = profileId, itemId = "ep-1", itemType = "episode", timestamp = 1000L,
            progressMs = 90L, durationMs = 100L, eventType = "stop" // completed (>85%)
        )
        `when`(mockDao.getLatestWatchEventsForProfile(profileId)).thenReturn(listOf(watchEvent))

        // Mock streams:
        // ep-2 has NO streams!
        // ep-3 HAS streams!
        `when`(mockDao.getStreamsForMediaItem("ep-2")).thenReturn(emptyList())
        val ep3Stream = MediaStreamEntity(
            id = "s-ep3", mediaId = "ep-3", title = "Episode 3 Stream 1080p", url = "url",
            resolution = "1080p", codec = "h264", quality = "FHD", sizeInBytes = 0L,
            language = "en", isSubbed = false, isDubbed = false, source = "stremio", updatedAt = 0L
        )
        `when`(mockDao.getStreamsForMediaItem("ep-3")).thenReturn(listOf(ep3Stream))

        // Execute finder
        val nextEpisodeResult = SmartNextEpisodeFinder.findNextEpisode(mockDao, profileId, seriesId)

        // The finder should bypass S01E02 because it has no streams and fall back to S01E03!
        assertEquals(RecommendationType.NEXT_EPISODE, nextEpisodeResult.recommendationType)
        assertEquals("ep-3", nextEpisodeResult.targetEpisodeId)
        assertEquals(1, nextEpisodeResult.seasonNumber)
        assertEquals(3, nextEpisodeResult.episodeNumber)
        assertTrue(nextEpisodeResult.isAvailable)
    }
}
