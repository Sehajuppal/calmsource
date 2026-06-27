package com.example.calmsource.core.discoveryengine.models

import kotlinx.serialization.Serializable

@Serializable
enum class RecommendationType {
    CONTINUE_EPISODE,
    NEXT_EPISODE,
    NEXT_SEASON,
    CAUGHT_UP
}

@Serializable
data class NextEpisodeResult(
    val recommendationType: RecommendationType,
    val targetEpisodeId: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val progress: Double?, // null if caught up or not started, otherwise progress percentage (0.0 to 1.0)
    val reason: String,
    val confidenceScore: Double,
    val isAvailable: Boolean = true
)

