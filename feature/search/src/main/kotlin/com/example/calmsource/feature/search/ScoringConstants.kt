package com.example.calmsource.feature.search

/**
 * Named constants for the Universal Search ranking algorithm.
 *
 * These constants control how [SearchResultRanker.calculateSourceScore] scores
 * individual [StreamSource]s based on user preferences. Higher values indicate
 * a stronger positive signal; negative values indicate penalties.
 *
 * Scoring categories (in evaluation order):
 * 1. **Language matching** — strongest signal, determines if content is watchable
 * 2. **Audio preferences** — dual audio, dubbing, subtitle bonuses
 * 3. **Source type** — IPTV exact match and debrid cached bonuses
 * 4. **Resolution** — quality tier scoring from SD to 4K
 * 5. **Seeds** — availability signal for torrent-style sources
 * 6. **Provider health** — penalizes slow or failed providers
 *
 * Post-source ranking ([SearchResultRanker.rankResults]):
 * 7. **Title match** — exact query match gets massive boost
 * 8. **Favorites/History** — user engagement signals
 *
 * @see SearchResultRanker for the scoring implementation
 * @see UserPreferences for user-configurable ranking behavior
 */
object ScoringConstants {

    // --- Language matching (Category 1) ---
    /** Bonus when source language matches user's primary language */
    const val PRIMARY_LANGUAGE_BONUS = 200
    /** Bonus when source language matches user's secondary language */
    const val SECONDARY_LANGUAGE_BONUS = 100
    /** Penalty when source language matches neither primary nor secondary */
    const val FOREIGN_LANGUAGE_PENALTY = -50

    // --- Audio preferences (Category 2) ---
    /** Bonus for dual audio sources when user prefers dual audio */
    const val DUAL_AUDIO_BONUS = 80
    /** Bonus for dubbed sources when user prefers dubbed audio */
    const val DUBBED_AUDIO_BONUS = 60
    /** Bonus for subtitled sources matching user's subtitle language */
    const val SUBTITLE_MATCH_BONUS = 40

    // --- Source type preferences (Category 3) ---
    /** Bonus for IPTV sources when user prefers IPTV exact match */
    const val IPTV_EXACT_MATCH_BONUS = 150
    /** Bonus for debrid sources that are cached (instant playback) */
    const val DEBRID_CACHED_BONUS = 150
    /** Additional bonus for cached debrid when user prefers cached */
    const val DEBRID_PREFERRED_CACHED_BONUS = 120

    // --- Resolution scoring (Category 4) ---
    /** Score for 4K / 2160p sources */
    const val RESOLUTION_4K = 100
    /** Score for 1080p / FHD sources */
    const val RESOLUTION_1080P = 80
    /** Score for 720p / HD sources */
    const val RESOLUTION_720P = 40
    /** Score for SD / 480p sources (when not hidden) */
    const val RESOLUTION_SD = 10
    /** Penalty for low quality sources when user hides them */
    const val LOW_QUALITY_PENALTY = -150
    /** Bonus for high quality when user prefers highest quality */
    const val HIGH_QUALITY_PREFERENCE_BONUS = 50
    /** Score for unrecognized resolution strings */
    const val RESOLUTION_UNKNOWN = 10

    // --- Seeds / availability (Category 5) ---
    /** Maximum bonus from seed count (capped) */
    const val MAX_SEEDS_BONUS = 30
    /** Divisor for seed count to compute bonus: seeds / SEED_DIVISOR capped at MAX_SEEDS_BONUS */
    const val SEED_DIVISOR = 10

    // --- Provider health (Category 6) ---
    /** Bonus for healthy, responsive providers */
    const val PROVIDER_HEALTHY_BONUS = 50
    /** Penalty for slow providers */
    const val PROVIDER_SLOW_PENALTY = -50
    /** Penalty for failed/unreachable providers */
    const val PROVIDER_FAILED_PENALTY = -200
    /** Penalty for providers/extensions with poor health score (< 40) */
    const val PROVIDER_UNHEALTHY_PENALTY = -150

    // --- Source health and recent playback ---
    /** Penalty for blocked sources */
    const val SOURCE_BLOCKED_PENALTY = -1000
    /** Penalty for poor reliability sources */
    const val SOURCE_POOR_PENALTY = -200
    /** Penalty for unstable reliability sources */
    const val SOURCE_UNSTABLE_PENALTY = -100
    /** Bonus for excellent reliability sources */
    const val SOURCE_EXCELLENT_BOOST = 50
    /** Bonus for sources with successful playback in the last 24 hours */
    const val SOURCE_RECENT_SUCCESS_BOOST = 100

    // --- Post-source ranking (Category 7-8) ---
    /** Massive bonus for exact title match to search query */
    const val EXACT_TITLE_MATCH_BONUS = 1000
    /** Bonus for results that are in user's favorites */
    const val FAVORITES_BONUS = 500
    /** Bonus for results in user's watch history */
    const val HISTORY_BONUS = 300
    /** Bonus for results whose title matches one of the user's recent searches */
    const val RECENT_QUERY_BONUS = 150

    // --- Default scores for pseudo-sources ---
    /** Default score for live IPTV channel matches */
    const val LIVE_CHANNEL_DEFAULT_SCORE = 200
    /** Default score for EPG program matches */
    const val EPG_PROGRAM_DEFAULT_SCORE = 150
}
