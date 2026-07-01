package com.example.calmsource.core.sourceintelligence.ranking

import com.example.calmsource.core.model.UserPreferences

/**
 * Lightweight device hints for ranking — avoids pulling Android APIs into the engine.
 */
data class DeviceStreamProfile(
    /** Streams above this height may be penalized in Best Match on constrained devices. */
    val maxRecommendedHeight: Int = Int.MAX_VALUE,
    val penalizeHdr: Boolean = false,
) {
    companion object {
        val UNRESTRICTED = DeviceStreamProfile()

        fun forPlayback(isTelevision: Boolean, prefs: UserPreferences): DeviceStreamProfile {
            return when {
                prefs.preferLowerDataUsage -> DeviceStreamProfile(
                    maxRecommendedHeight = 720,
                    penalizeHdr = true,
                )
                isTelevision -> DeviceStreamProfile(maxRecommendedHeight = 2160)
                else -> DeviceStreamProfile(maxRecommendedHeight = 1080)
            }
        }
    }
}
