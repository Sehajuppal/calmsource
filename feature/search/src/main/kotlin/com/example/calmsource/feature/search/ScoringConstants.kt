package com.example.calmsource.feature.search

import com.example.calmsource.core.sourceintelligence.ranking.StreamScoringConstants

/**
 * Named constants for the Universal Search ranking algorithm.
 *
 * Shared stream-quality weights live in [StreamScoringConstants]; this object keeps
 * search-specific post-merge boosts and re-exports shared weights for existing tests.
 */
object ScoringConstants {

    // --- Language matching (Category 1) ---
    const val PRIMARY_LANGUAGE_BONUS = StreamScoringConstants.PRIMARY_LANGUAGE_BONUS
    const val SECONDARY_LANGUAGE_BONUS = StreamScoringConstants.SECONDARY_LANGUAGE_BONUS
    const val FOREIGN_LANGUAGE_PENALTY = StreamScoringConstants.FOREIGN_LANGUAGE_PENALTY

    // --- Audio preferences (Category 2) ---
    const val DUAL_AUDIO_BONUS = StreamScoringConstants.DUAL_AUDIO_BONUS
    const val DUBBED_AUDIO_BONUS = StreamScoringConstants.DUBBED_AUDIO_BONUS
    const val SUBTITLE_MATCH_BONUS = StreamScoringConstants.SUBTITLE_MATCH_BONUS

    // --- Source type preferences (Category 3) ---
    const val IPTV_EXACT_MATCH_BONUS = StreamScoringConstants.IPTV_EXACT_MATCH_BONUS
    const val DEBRID_CACHED_BONUS = StreamScoringConstants.DEBRID_CACHED_BONUS
    const val DEBRID_PREFERRED_CACHED_BONUS = StreamScoringConstants.DEBRID_PREFERRED_CACHED_BONUS

    // --- Resolution scoring (Category 4) ---
    const val RESOLUTION_4K = StreamScoringConstants.RESOLUTION_4K_BEST_MATCH
    const val RESOLUTION_1080P = StreamScoringConstants.RESOLUTION_1080P_BEST_MATCH
    const val RESOLUTION_720P = StreamScoringConstants.RESOLUTION_720P
    const val RESOLUTION_SD = StreamScoringConstants.RESOLUTION_SD
    const val LOW_QUALITY_PENALTY = StreamScoringConstants.LOW_QUALITY_PENALTY
    const val HIGH_QUALITY_PREFERENCE_BONUS = StreamScoringConstants.HIGH_QUALITY_PREFERENCE_BONUS
    const val RESOLUTION_UNKNOWN = StreamScoringConstants.RESOLUTION_UNKNOWN

    // --- Seeds / availability (Category 5) ---
    const val MAX_SEEDS_BONUS = StreamScoringConstants.MAX_SEEDS_BONUS
    const val SEED_DIVISOR = StreamScoringConstants.SEED_DIVISOR

    // --- Provider health (Category 6) ---
    const val PROVIDER_HEALTHY_BONUS = StreamScoringConstants.PROVIDER_HEALTHY_BONUS
    const val PROVIDER_SLOW_PENALTY = StreamScoringConstants.PROVIDER_SLOW_PENALTY
    const val PROVIDER_FAILED_PENALTY = StreamScoringConstants.PROVIDER_FAILED_PENALTY
    const val PROVIDER_UNHEALTHY_PENALTY = StreamScoringConstants.PROVIDER_UNHEALTHY_PENALTY

    // --- Source health and recent playback ---
    const val SOURCE_BLOCKED_PENALTY = StreamScoringConstants.SOURCE_BLOCKED_PENALTY
    const val SOURCE_POOR_PENALTY = StreamScoringConstants.SOURCE_POOR_PENALTY
    const val SOURCE_UNSTABLE_PENALTY = StreamScoringConstants.SOURCE_UNSTABLE_PENALTY
    const val SOURCE_EXCELLENT_BOOST = StreamScoringConstants.SOURCE_EXCELLENT_BOOST
    const val SOURCE_RECENT_SUCCESS_BOOST = StreamScoringConstants.SOURCE_RECENT_SUCCESS_BOOST

    // --- Post-source ranking (Category 7-8) ---
    const val EXACT_TITLE_MATCH_BONUS = 1000
    const val FAVORITES_BONUS = 500
    const val HISTORY_BONUS = 300
    const val RECENT_QUERY_BONUS = 150

    // --- Default scores for pseudo-sources ---
    const val LIVE_CHANNEL_DEFAULT_SCORE = 200
    const val EPG_PROGRAM_DEFAULT_SCORE = 150
}
