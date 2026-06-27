package com.example.calmsource.core.playback

import com.example.calmsource.core.model.AutoFallbackPolicy
import com.example.calmsource.core.model.IPTVChannel

/**
 * Shared live-TV channel auto-switch policy used by mobile and TV player screens.
 *
 * Channel switching runs only after stream-level fallbacks have settled; callers must gate on
 * [com.example.calmsource.core.model.PlaybackUiState.isTransitioningSource] before invoking
 * [suggestNextChannel].
 */
object LiveChannelRecovery {
    /** Do not auto-zap into the POOR/BLOCKED reliability tiers. */
    private const val MIN_AUTO_ZAP_HEALTH_SCORE = 40

    fun maxAutoSwitchCount(policy: AutoFallbackPolicy): Int = when (policy) {
        AutoFallbackPolicy.OFF, AutoFallbackPolicy.ASK_BEFORE_FALLBACK -> 0
        AutoFallbackPolicy.AUTO_FALLBACK_ONCE -> 1
        AutoFallbackPolicy.AUTO_FALLBACK_LIMITED -> 3
    }

    /**
     * Picks the healthiest of the next [candidateOffsets] channels after [currentChannelId].
     * Returns null when the current channel is not in the guide (e.g. alt-container fallback ids)
     * so callers do not jump to an arbitrary channel.
     */
    suspend fun suggestNextChannel(
        currentChannelId: String,
        channels: List<IPTVChannel>,
        healthScoreFor: suspend (String) -> Int,
        candidateOffsets: IntRange = 1..3,
    ): IPTVChannel? {
        if (channels.isEmpty()) return null
        val normalizedId = currentChannelId.substringBefore("-alt-")
        val idx = channels.indexOfFirst { it.id == normalizedId || it.id == currentChannelId }
        if (idx == -1) return null
        val candidates = candidateOffsets.asSequence()
            .map { offset -> channels[(idx + offset) % channels.size] }
            .filterNot { candidate ->
                candidate.id == normalizedId || candidate.id == currentChannelId
            }
            .distinctBy { it.id }
            .toList()
        val scoredCandidates = buildList {
            for (candidate in candidates) {
                add(candidate to healthScoreFor(candidate.safeSourceId))
            }
        }
        return scoredCandidates
            .maxByOrNull { (_, score) -> score }
            ?.takeIf { (_, score) -> score >= MIN_AUTO_ZAP_HEALTH_SCORE }
            ?.first
    }
}
