package com.example.calmsource.core.discoveryengine.providers

import com.example.calmsource.core.discoveryengine.database.ProviderRegistryDao
import com.example.calmsource.core.discoveryengine.database.ProviderRegistryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteException
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProviderRegistryStore(private val dao: ProviderRegistryDao) {

    private val writeMutex = Mutex()

    private suspend fun <T> runWithRetry(operationName: String, block: () -> T): T {
        writeMutex.withLock {
            var attempt = 0
            while (true) {
                try {
                    return block()
                } catch (e: Exception) {
                    if (!isTransientLock(e) || attempt >= 2) {
                        try {
                            Log.w("ProviderRegistryStore", "$operationName failed after ${attempt + 1} attempts: ${e.javaClass.simpleName}")
                        } catch (_: Throwable) {}
                        throw e
                    }
                    attempt++
                    val delayMs = when (attempt) {
                        1 -> 50L
                        else -> 100L
                    }
                    try {
                        Log.w("ProviderRegistryStore", "$operationName hit SQLite lock; retrying in ${delayMs}ms (attempt $attempt)")
                    } catch (_: Throwable) {}
                    delay(delayMs) // DE-BUG-2: suspend instead of blocking IO thread
                }
            }
        }
    }

    private fun isTransientLock(error: Throwable?): Boolean {
        if (error == null) return false
        val className = error.javaClass.simpleName
        if (className == "SQLiteDatabaseLockedException") return true
        if (error is SQLiteException) {
            val message = error.message?.lowercase().orEmpty()
            if ("locked" in message || "busy" in message) return true
        }
        return isTransientLock(error.cause)
    }

    suspend fun upsert(
        row: ProviderStatusRow,
        endpointUrl: String? = null,
        privacySensitive: Boolean = true
    ) {
        runWithRetry("upsert") {
            val existing = dao.get(row.providerId)
            val now = System.currentTimeMillis()
            dao.upsert(row.toEntity(
                endpointUrl = endpointUrl ?: existing?.endpointUrl,
                privacySensitive = privacySensitive,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            ))
        }
    }

    fun get(providerId: String): ProviderStatusRow? = dao.get(providerId)?.toStatusRow()

    fun getAll(): List<ProviderStatusRow> = dao.getAll().map { it.toStatusRow() }

    fun getEnabled(): List<ProviderStatusRow> = dao.getEnabled().map { it.toStatusRow() }

    fun observeAll(): Flow<List<ProviderStatusRow>> = dao.observeAll().map { rows ->
        rows.map { it.toStatusRow() }
    }

    suspend fun setEnabled(providerId: String, enabled: Boolean) {
        runWithRetry("setEnabled") {
            dao.setEnabled(providerId, enabled, System.currentTimeMillis())
        }
    }

    suspend fun setPriority(providerId: String, priority: Int) {
        runWithRetry("setPriority") {
            dao.setPriority(providerId, priority, System.currentTimeMillis())
        }
    }

    suspend fun updateReliability(
        providerId: String,
        reliabilityScore: Double,
        failureCount: Int,
        lastSuccessAt: Long?,
        lastFailureAt: Long?
    ) {
        runWithRetry("updateReliability") {
            dao.updateReliability(
                providerId = providerId,
                reliabilityScore = reliabilityScore,
                failureCount = failureCount,
                lastSuccessAt = lastSuccessAt,
                lastFailureAt = lastFailureAt,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun updateEndpoint(providerId: String, endpointUrl: String) {
        runWithRetry("updateEndpoint") {
            dao.updateEndpoint(providerId, endpointUrl, System.currentTimeMillis())
        }
    }

    suspend fun delete(providerId: String) {
        runWithRetry("delete") {
            dao.delete(providerId)
        }
    }
}

private fun ProviderStatusRow.toEntity(
    endpointUrl: String?,
    privacySensitive: Boolean,
    createdAt: Long,
    updatedAt: Long
): ProviderRegistryEntity {
    return ProviderRegistryEntity(
        providerId = providerId,
        name = name,
        type = type.name,
        kind = kind.name,
        endpointUrl = endpointUrl,
        isEnabled = isEnabled,
        isSystemProvider = isSystemProvider,
        isUserInstalled = isUserInstalled,
        priority = priority,
        supportsCatalog = ProviderType.CATALOG in capabilities,
        supportsMeta = ProviderType.METADATA in capabilities,
        supportsStream = ProviderType.STREAM in capabilities,
        supportsSubtitles = ProviderType.SUBTITLE in capabilities,
        supportsSearch = false,
        supportsRatings = ProviderType.RATING in capabilities,
        supportsSimilar = ProviderType.SIMILAR in capabilities,
        supportsArtwork = ProviderType.ARTWORK in capabilities,
        supportsAvailability = ProviderType.AVAILABILITY in capabilities,
        privacySensitive = privacySensitive,
        lastSuccessAt = lastSuccessAt,
        lastFailureAt = lastFailureAt,
        failureCount = failureCount,
        reliabilityScore = reliabilityScore,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

private fun ProviderRegistryEntity.toStatusRow(): ProviderStatusRow {
    val capabilities = buildSet {
        if (supportsCatalog) add(ProviderType.CATALOG)
        if (supportsMeta) add(ProviderType.METADATA)
        if (supportsStream) add(ProviderType.STREAM)
        if (supportsSubtitles) add(ProviderType.SUBTITLE)
        if (supportsRatings) add(ProviderType.RATING)
        if (supportsSimilar) add(ProviderType.SIMILAR)
        if (supportsArtwork) add(ProviderType.ARTWORK)
        if (supportsAvailability) add(ProviderType.AVAILABILITY)
    }
    val parsedType = runCatching { ProviderType.valueOf(type) }.getOrElse {
        capabilities.firstOrNull() ?: ProviderType.METADATA
    }
    val parsedKind = runCatching { ProviderKind.valueOf(kind) }.getOrDefault(ProviderKind.EXTERNAL_API)
    return ProviderStatusRow(
        providerId = providerId,
        name = name,
        type = parsedType,
        kind = parsedKind,
        isEnabled = isEnabled,
        isSystemProvider = isSystemProvider,
        isUserInstalled = isUserInstalled,
        priority = priority,
        reliabilityScore = reliabilityScore,
        failureCount = failureCount,
        lastSuccessAt = lastSuccessAt,
        lastFailureAt = lastFailureAt,
        capabilities = capabilities
    )
}
