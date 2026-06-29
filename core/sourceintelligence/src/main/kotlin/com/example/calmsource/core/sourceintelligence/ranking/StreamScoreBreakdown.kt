package com.example.calmsource.core.sourceintelligence.ranking

/**
 * Human-readable output from [StreamScoringEngine.scoreDetailed].
 */
data class StreamScoreBreakdown(
    val totalScore: Double,
    val reasons: List<String>,
) {
    /** Top positive/negative signals for compact UI display. */
    val topReasons: List<String>
        get() = reasons.take(4)
}

data class StreamScoredResult(
    val score: Double,
    val breakdown: StreamScoreBreakdown,
)
