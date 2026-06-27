package com.example.calmsource.core.discoveryengine.providers

/**
 * TTL constants for provider caches, in milliseconds.
 *
 * Default values are conservative — enrichment is strictly optional.
 * The app works with stale or empty caches.
 */
object ProviderTtl {
    // Primary data: 30-90 days (default 60 days)
    val METADATA_DEFAULT = 60L * 24 * 60 * 60 * 1000L
    val METADATA_MIN = 30L * 24 * 60 * 60 * 1000L
    val METADATA_MAX = 90L * 24 * 60 * 60 * 1000L

    // Ratings: 7-30 days (default 14 days)
    val RATINGS_DEFAULT = 14L * 24 * 60 * 60 * 1000L
    val RATINGS_MIN = 7L * 24 * 60 * 60 * 1000L
    val RATINGS_MAX = 30L * 24 * 60 * 60 * 1000L

    // Similar titles: 7-30 days (default 14 days)
    val SIMILAR_DEFAULT = 14L * 24 * 60 * 60 * 1000L

    // Subtitles: 1-14 days (default 7 days)
    val SUBTITLES_DEFAULT = 7L * 24 * 60 * 60 * 1000L
    val SUBTITLES_MIN = 1L * 24 * 60 * 60 * 1000L
    val SUBTITLES_MAX = 14L * 24 * 60 * 60 * 1000L

    // Availability: 6-24 hours (default 12 hours)
    val AVAILABILITY_DEFAULT = 12L * 60 * 60 * 1000L
    val AVAILABILITY_MIN = 6L * 60 * 60 * 1000L
    val AVAILABILITY_MAX = 24L * 60 * 60 * 1000L

    // Artwork: 30-90 days (default 60 days)
    val ARTWORK_DEFAULT = 60L * 24 * 60 * 60 * 1000L

    // Failure log TTL: 1-7 days (default 3 days)
    val FAILURE_LOG_DEFAULT = 3L * 24 * 60 * 60 * 1000L
    val FAILURE_LOG_MIN = 1L * 24 * 60 * 60 * 1000L
    val FAILURE_LOG_MAX = 7L * 24 * 60 * 60 * 1000L

    /** Returns true if the given timestamp is in the past compared to now. */
    fun isExpired(fetchedAt: Long, ttl: Long): Boolean {
        return fetchedAt + ttl < System.currentTimeMillis()
    }

    /** Returns the default TTL for a given provider type. */
    fun defaultTtl(type: ProviderType): Long = when (type) {
        ProviderType.METADATA -> METADATA_DEFAULT
        ProviderType.RATING -> RATINGS_DEFAULT
        ProviderType.SIMILAR -> SIMILAR_DEFAULT
        ProviderType.SUBTITLE -> SUBTITLES_DEFAULT
        ProviderType.AVAILABILITY -> AVAILABILITY_DEFAULT
        ProviderType.ARTWORK -> ARTWORK_DEFAULT
        ProviderType.STREAM -> AVAILABILITY_DEFAULT  // streams cached like availability
        ProviderType.CATALOG -> RATINGS_DEFAULT // catalogs cached like ratings
    }
}