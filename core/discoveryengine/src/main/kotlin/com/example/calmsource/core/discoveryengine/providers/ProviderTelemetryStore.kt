package com.example.calmsource.core.discoveryengine.providers

import com.example.calmsource.core.discoveryengine.database.ProviderFailureLogEntity
import com.example.calmsource.core.discoveryengine.database.ProviderTelemetryDao
import com.example.calmsource.core.discoveryengine.database.ProviderUsageLogEntity

/**
 * Failure and usage logging. Stores are written to once per provider call —
 * developers can inspect them in Advanced settings "View failures" and the
 * reliability score is derived from them.
 */
class ProviderTelemetryStore(private val dao: ProviderTelemetryDao) {

    private val now: Long get() = System.currentTimeMillis()

    fun logFailure(
        providerId: String,
        requestType: String,
        mediaId: String?,
        errorCode: String,
        message: String? = null,
        retryAfterMs: Long? = null
    ): Long = dao.insertFailure(
        ProviderFailureLogEntity(
            providerId = providerId,
            requestType = requestType,
            mediaId = mediaId,
            errorCode = errorCode,
            errorMessage = message,
            occurredAt = now,
            retryAfter = retryAfterMs
        )
    )

    fun logUsage(
        providerId: String,
        requestType: String,
        mediaId: String?,
        cacheHit: Boolean,
        durationMs: Long,
        success: Boolean
    ): Long = dao.insertUsage(
        ProviderUsageLogEntity(
            providerId = providerId,
            requestType = requestType,
            mediaId = mediaId,
            cacheHit = cacheHit,
            durationMs = durationMs,
            success = success,
            createdAt = now
        )
    )

    fun getRecentFailures(limit: Int = 100): List<FailureLogEntry> =
        dao.getRecentFailures(limit).map { it.toEntry() }

    fun getFailuresForProvider(providerId: String, limit: Int = 50): List<FailureLogEntry> =
        dao.getFailuresForProvider(providerId, limit).map { it.toEntry() }

    fun pruneOldFailures(ttl: Long = ProviderTtl.FAILURE_LOG_DEFAULT): Int =
        dao.pruneFailures(now - ttl)

    fun clearAllFailures(): Unit = dao.clearAllFailures()

    fun getRecentUsage(limit: Int = 100): List<UsageLogEntry> =
        dao.getRecentUsage(limit).map { it.toEntry() }

    fun pruneOldUsage(ttl: Long = 7L * 24 * 60 * 60 * 1000): Int =
        dao.pruneUsage(now - ttl)

    fun clearAllUsage(): Unit = dao.clearAllUsage()
}

data class FailureLogEntry(
    val failureId: Long,
    val providerId: String,
    val requestType: String,
    val mediaId: String?,
    val errorCode: String,
    val message: String?,
    val occurredAt: Long,
    val retryAfterMs: Long?
)

data class UsageLogEntry(
    val usageId: Long,
    val providerId: String,
    val requestType: String,
    val mediaId: String?,
    val cacheHit: Boolean,
    val durationMs: Long,
    val success: Boolean,
    val createdAt: Long
)

private fun ProviderFailureLogEntity.toEntry() = FailureLogEntry(
    failureId = failureId,
    providerId = providerId,
    requestType = requestType,
    mediaId = mediaId,
    errorCode = errorCode,
    message = errorMessage,
    occurredAt = occurredAt,
    retryAfterMs = retryAfter
)

private fun ProviderUsageLogEntity.toEntry() = UsageLogEntry(
    usageId = usageId,
    providerId = providerId,
    requestType = requestType,
    mediaId = mediaId,
    cacheHit = cacheHit,
    durationMs = durationMs,
    success = success,
    createdAt = createdAt
)