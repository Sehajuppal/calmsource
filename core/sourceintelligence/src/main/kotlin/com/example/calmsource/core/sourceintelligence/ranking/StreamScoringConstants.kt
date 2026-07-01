package com.example.calmsource.core.sourceintelligence.ranking

/**
 * Named weights for the unified [StreamScoringEngine].
 *
 * Search and details screens share these constants so ranking behavior stays consistent.
 */
object StreamScoringConstants {

    // --- Language matching ---
    const val PRIMARY_LANGUAGE_BONUS = 200
    const val SECONDARY_LANGUAGE_BONUS = 100
    const val PROFILE_LANGUAGE_BONUS = 30
    const val PROFILE_LANGUAGE_FUZZY_BONUS = 20
    const val SUBTITLE_MATCH_BONUS = 40
    const val FOREIGN_LANGUAGE_PENALTY = -50

    // --- Audio preferences ---
    const val DUAL_AUDIO_BONUS = 80
    const val DUBBED_AUDIO_BONUS = 60

    // --- Source type ---
    const val IPTV_EXACT_MATCH_BONUS = 150
    const val DEBRID_CACHED_BONUS = 150
    const val DEBRID_PREFERRED_CACHED_BONUS = 120

    // --- Resolution (Best Match baseline) ---
    /** 4K in the 2–15 GB sweet spot — intentionally above [RESOLUTION_1080P_BEST_MATCH]. */
    const val RESOLUTION_4K_REASONABLE_BEST_MATCH = 100
    const val RESOLUTION_4K_MID_BEST_MATCH = 85
    const val RESOLUTION_4K_LIGHT_BEST_MATCH = 75
    const val RESOLUTION_4K_UNKNOWN_SIZE_BEST_MATCH = 90
    const val RESOLUTION_1080P_BEST_MATCH = 80
    /** @deprecated Use [RESOLUTION_4K_REASONABLE_BEST_MATCH] — kept for search constant alias. */
    const val RESOLUTION_4K_BEST_MATCH = RESOLUTION_4K_REASONABLE_BEST_MATCH
    const val RESOLUTION_720P = 40
    const val RESOLUTION_SD = 10
    const val RESOLUTION_UNKNOWN = 10
    const val LOW_QUALITY_PENALTY = -150
    const val HIGH_QUALITY_PREFERENCE_BONUS = 50

    // --- Highest Quality strategy ---
    const val RESOLUTION_4K_HQ = 120
    const val RESOLUTION_1080P_HQ = 60
    const val HDR_DV_BONUS = 50
    const val HDR10_PLUS_BONUS = 30
    const val HDR10_BONUS = 10
    const val ATMOS_HQ_BONUS = 30
    const val DTS_HD_BONUS = 20
    const val AV1_HQ_BONUS = 30
    const val HEVC_HQ_BONUS = 20
    const val SIZE_40GB_BONUS = 100
    const val SIZE_20GB_BONUS = 50
    const val SIZE_10GB_BONUS = 20

    // --- Best Match strategy ---
    const val HUGE_FILE_PENALTY = -150
    const val SWEET_SPOT_SIZE_BONUS = 30
    const val MID_SIZE_BONUS = 15
    const val SEEDS_HIGH_BONUS = 20
    const val SEEDS_MID_BONUS = 10
    const val CODEC_ATMOS_BEST_MATCH_BONUS = 10
    const val CODEC_HEVC_AV1_BEST_MATCH_BONUS = 10
    const val REMUX_PENALTY = -40
    const val HUGE_SIZE_SOFT_PENALTY = -20

    // --- Seeds / availability ---
    const val MAX_SEEDS_BONUS = 30
    const val SEED_DIVISOR = 10

    // --- Provider health ---
    const val PROVIDER_HEALTHY_BONUS = 50
    const val PROVIDER_SLOW_PENALTY = -50
    const val PROVIDER_FAILED_PENALTY = -200
    const val PROVIDER_UNHEALTHY_PENALTY = -150

    // --- Source health & playback history ---
    const val SOURCE_BLOCKED_PENALTY = -1000
    const val SOURCE_POOR_PENALTY = -200
    const val SOURCE_UNSTABLE_PENALTY = -100
    const val SOURCE_EXCELLENT_BOOST = 50
    const val SOURCE_RECENT_SUCCESS_BOOST = 100
    const val STREAM_SUCCESS_BONUS = 20
    const val STREAM_SUCCESS_MAX = 40
    const val STREAM_FAILURE_PENALTY = 50
    const val STREAM_FAILURE_MAX = 150
    const val SOURCE_HISTORY_POSITIVE = 10
    const val SOURCE_HISTORY_NEGATIVE = -20

    // --- Playback history decay ---
    const val PLAYBACK_DECAY_HALF_LIFE_DAYS = 14.0

    // --- Low-bandwidth / data-saver hints ---
    const val LOW_BANDWIDTH_4K_PENALTY = -35
    const val LOW_BANDWIDTH_COMPACT_BONUS = 25

    // --- Device playback caps ---
    const val DEVICE_OVER_RESOLUTION_PENALTY = -45
    const val DEVICE_HDR_PENALTY = -25
    const val DEVICE_ATMOS_PENALTY = -15

    // --- Media-level availability (search + recommendations) ---
    const val MEDIA_AVAILABILITY_PROVIDER_CACHE_MAX = 20.0
    const val MEDIA_AVAILABILITY_TOP_STREAM_SCALE = 0.35
    const val MEDIA_AVAILABILITY_TOP_STREAM_CAP = 85.0
    const val MEDIA_AVAILABILITY_STREAM_COUNT_PER = 2.0
    const val MEDIA_AVAILABILITY_STREAM_COUNT_CAP = 10.0
    const val MEDIA_AVAILABILITY_CHANNEL_DEFAULT = 20.0
    const val MEDIA_AVAILABILITY_NORMALIZE_DIVISOR = 100.0
}
