package com.example.calmsource.core.discoveryengine.providers

import com.example.calmsource.core.discoveryengine.providers.fakes.FakeProviderCacheDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCacheTest {

    @Test
    fun cacheStoreRoundTripsProviderCacheTypes() = runBlocking {
        val store = ProviderCacheStore(FakeProviderCacheDao())

        store.putMetadata(
            mediaId = "m-cache",
            providerId = "p-cache",
            metadata = EnrichedMetadata(
                title = "Cached Title",
                originalTitle = null,
                aliases = listOf("Alias"),
                overview = "Overview",
                genres = listOf("Drama"),
                cast = listOf("Actor"),
                director = "Director",
                runtimeMinutes = 120,
                language = "en",
                country = "US",
                posterUrl = "https://img.test/poster.jpg",
                backdropUrl = null,
                externalIds = mapOf("imdb" to "tt0000001")
            )
        )
        store.putRatings("m-cache", "p-cache", listOf(RatingEntry(value = 8.4, voteCount = 100)))
        store.putSimilar("m-cache", "p-cache", listOf(SimilarEntry("m-similar", "Similar")))
        store.putSubtitles("m-cache", "p-cache", listOf(SubtitleEntry("sub1", "en", "https://sub.test/1.srt")))
        store.putAvailability("m-cache", "p-cache", listOf(AvailabilityEntry("addon", 3, "1080p", true, listOf("en"))))

        assertEquals("Cached Title", store.getMetadata("m-cache").single().title)
        assertEquals(8.4, store.getRatings("m-cache").single().value, 0.001)
        assertEquals("m-similar", store.getSimilar("m-cache").single().similarMediaId)
        assertEquals("en", store.getSubtitles("m-cache").single().language)
        assertEquals(3, store.getAvailability("m-cache").single().streamCount)
    }

    @Test
    fun expiredRowsAreHiddenAndPruned() = runBlocking {
        val store = ProviderCacheStore(FakeProviderCacheDao())

        store.putRatings("m-expired", "p-expired", listOf(RatingEntry(value = 7.0)), ttlMs = -1)

        assertTrue(store.getRatings("m-expired").isEmpty())
        assertEquals(1, store.pruneExpired())
    }
}
