package com.example.calmsource.core.discoveryengine.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrichmentFeaturesTest {

    @Test
    fun streamRankAvailabilityCanOutrankProviderCache() {
        val features = EnrichmentFeatures(
            mediaId = "m-1",
            availabilityCount = 1,
            bestQuality = "720p",
            streamRankAvailability = 0.9,
        )
        assertEquals(0.9, features.availabilityScore, 0.01)
    }

    @Test
    fun providerCacheStillContributesWhenStreamSignalMissing() {
        val features = EnrichmentFeatures(
            mediaId = "m-1",
            availabilityCount = 5,
            bestQuality = "1080p",
            hasSubtitles = true,
        )
        assertTrue(features.availabilityScore >= 1.0)
    }
}
