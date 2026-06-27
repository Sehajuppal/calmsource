package com.example.calmsource.core.discoveryengine.providers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BreakerState {
    CLOSED,
    OPEN
}

data class BreakerStatus(
    val providerId: String,
    val state: BreakerState = BreakerState.CLOSED,
    val consecutiveFailures: Int = 0,
    val openedAtMs: Long? = null,
    val cooldownUntilMs: Long? = null,
    val openCount: Int = 0
)

class ProviderUnavailableException(
    val providerId: String,
    val cooldownUntilMs: Long?
) : IllegalStateException("Provider $providerId is unavailable until $cooldownUntilMs")

class ProviderCircuitBreaker(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD
) {
    private val lock = Any()
    private val statuses = linkedMapOf<String, BreakerStatus>()
    private val _state = MutableStateFlow<Map<String, BreakerStatus>>(emptyMap())
    val state: StateFlow<Map<String, BreakerStatus>> = _state.asStateFlow()

    fun recordSuccess(providerId: String) {
        synchronized(lock) {
            val current = statuses[providerId]
            statuses[providerId] = BreakerStatus(
                providerId = providerId,
                openCount = 0  // Reset on success so recovered extensions don't get permanent 30-min lockouts
            )
            publishLocked()
        }
    }

    fun recordFailure(providerId: String): BreakerStatus {
        synchronized(lock) {
            val current = refreshLocked(providerId)
            if (current.state == BreakerState.OPEN) return current

            val failures = current.consecutiveFailures + 1
            val next = if (failures >= failureThreshold) {
                val openedAt = clockMs()
                val openCount = current.openCount + 1
                val cooldownMs = cooldownForOpenCount(openCount)
                BreakerStatus(
                    providerId = providerId,
                    state = BreakerState.OPEN,
                    consecutiveFailures = failures,
                    openedAtMs = openedAt,
                    cooldownUntilMs = openedAt + cooldownMs,
                    openCount = openCount
                )
            } else {
                current.copy(consecutiveFailures = failures)
            }
            statuses[providerId] = next
            publishLocked()
            return next
        }
    }

    fun isOpen(providerId: String): Boolean {
        return synchronized(lock) {
            refreshLocked(providerId).state == BreakerState.OPEN
        }
    }

    fun requireAvailable(providerId: String) {
        val status = synchronized(lock) { refreshLocked(providerId) }
        if (status.state == BreakerState.OPEN) {
            throw ProviderUnavailableException(providerId, status.cooldownUntilMs)
        }
    }

    fun reset(providerId: String) {
        synchronized(lock) {
            statuses[providerId] = BreakerStatus(providerId = providerId)
            publishLocked()
        }
    }

    fun resetAll() {
        synchronized(lock) {
            statuses.clear()
            publishLocked()
        }
    }

    fun snapshot(): Map<String, BreakerStatus> {
        return synchronized(lock) {
            statuses.keys.forEach(::refreshLocked)
            statuses.toMap()
        }
    }

    private fun refreshLocked(providerId: String): BreakerStatus {
        val current = statuses[providerId] ?: BreakerStatus(providerId = providerId)
        val cooldownUntil = current.cooldownUntilMs
        val refreshed = if (
            current.state == BreakerState.OPEN &&
            cooldownUntil != null &&
            clockMs() >= cooldownUntil
        ) {
            current.copy(
                state = BreakerState.CLOSED,
                consecutiveFailures = 0,
                openedAtMs = null,
                cooldownUntilMs = null
            )
        } else {
            current
        }
        statuses[providerId] = refreshed
        publishLocked()
        return refreshed
    }

    private fun publishLocked() {
        _state.value = statuses.toMap()
    }

    private fun cooldownForOpenCount(openCount: Int): Long {
        return when (openCount) {
            1 -> 5.minutesMs
            2 -> 15.minutesMs
            else -> 30.minutesMs
        }
    }

    private val Int.minutesMs: Long
        get() = this * 60L * 1000L

    companion object {
        const val DEFAULT_FAILURE_THRESHOLD = 3
    }
}
