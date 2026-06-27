package com.example.calmsource.core.discoveryengine.providers

import kotlin.math.max
import kotlin.math.min

/**
 * Pure functions for the provider reliability score (0.0..1.0).
 *
 * The score is what [ProviderManager.getEnabledProviders] uses to auto-quarantine
 * a provider. A provider that hits `failureCount >= 8` with `reliability == 0.0`
 * is auto-quarantined for 30 minutes.
 */
object ReliabilityTracker {

    const val AUTO_QUARANTINE_FAILURE_COUNT = 8
    const val AUTO_QUARANTINE_DURATION_MS = 30L * 60 * 1000  // 30 minutes

    /** Apply a success: nudge reliability up, decrement failure count. */
    fun onSuccess(current: Double, currentFailureCount: Int): Pair<Double, Int> {
        val next = min(1.0, current + 0.05)
        val nextFailures = max(0, currentFailureCount - 1)
        return next to nextFailures
    }

    /** Apply a failure: nudge reliability down, increment failure count. */
    fun onFailure(current: Double, currentFailureCount: Int): Pair<Double, Int> {
        val next = max(0.0, current - 0.1)
        return next to (currentFailureCount + 1)
    }

    /**
     * Should the manager auto-quarantine this provider? A provider is
     * quarantined when its failure_count crosses the threshold and reliability
     * is at the floor. Lifting the quarantine requires a user action
     * (toggle, replace, or cache clear).
     */
    fun shouldQuarantine(reliability: Double, failureCount: Int): Boolean {
        return reliability <= 0.0 && failureCount >= AUTO_QUARANTINE_FAILURE_COUNT
    }
}