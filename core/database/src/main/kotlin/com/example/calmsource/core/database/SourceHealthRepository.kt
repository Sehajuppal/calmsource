package com.example.calmsource.core.database

import com.example.calmsource.core.database.entity.ProviderHealthScoreEntity
import com.example.calmsource.core.database.entity.SourceHealthEntity
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.ProviderHealthScore
import com.example.calmsource.core.model.SourceHealth
import com.example.calmsource.core.model.SourceHealthSignal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object SourceHealthRepository {

    private val lastDecayWriteTimeMs = AtomicLong(0L)

    private val sourceLockStripes = Array(64) { Mutex() }
    private val providerLockStripes = Array(64) { Mutex() }

    private fun getSourceLock(id: String): Mutex {
        val index = (id.hashCode() and 0x7FFFFFFF) % 64
        return sourceLockStripes[index]
    }

    private fun getProviderLock(id: String): Mutex {
        val index = (id.hashCode() and 0x7FFFFFFF) % 64
        return providerLockStripes[index]
    }

    private const val DECAY_WRITE_THROTTLE_MS = 60_000L

    @Volatile
    var dispatcher: CoroutineDispatcher = Dispatchers.IO

    private val fallbackDao: com.example.calmsource.core.database.dao.HealthDao by lazy {
        createInMemoryDao()
    }

    private val dao: com.example.calmsource.core.database.dao.HealthDao
        get() = DatabaseProvider.databaseOrNull()?.healthDao() ?: fallbackDao

    private fun createInMemoryDao(): com.example.calmsource.core.database.dao.HealthDao {
        return object : com.example.calmsource.core.database.dao.HealthDao {
            private val sourceHealthMap = java.util.concurrent.ConcurrentHashMap<String, SourceHealthEntity>()
            private val providerHealthMap = java.util.concurrent.ConcurrentHashMap<String, ProviderHealthScoreEntity>()

            override fun insertSourceHealth(entity: SourceHealthEntity): Long {
                sourceHealthMap[entity.sourceId] = entity
                return 1L
            }

            override fun insertSourceHealth(entities: List<SourceHealthEntity>): List<Long> {
                entities.forEach { entity ->
                    sourceHealthMap[entity.sourceId] = entity
                }
                return entities.map { 1L }
            }

            override fun insertProviderHealth(entity: ProviderHealthScoreEntity): Long {
                providerHealthMap[entity.providerId] = entity
                return 1L
            }

            override fun getSourceHealth(sourceId: String): SourceHealthEntity? {
                return sourceHealthMap[sourceId]
            }

            override fun getSourceHealths(sourceIds: List<String>): List<SourceHealthEntity> {
                return sourceIds.mapNotNull { sourceHealthMap[it] }
            }

            override fun getProviderHealth(providerId: String): ProviderHealthScoreEntity? {
                return providerHealthMap[providerId]
            }

            override fun clearSourceHealth(): Int {
                val size = sourceHealthMap.size
                sourceHealthMap.clear()
                return size
            }

            override fun clearProviderHealth(): Int {
                val size = providerHealthMap.size
                providerHealthMap.clear()
                return size
            }

            override fun markSourceHidden(sourceId: String, hidden: Boolean): Int {
                sourceHealthMap[sourceId]?.let {
                    it.userHidden = hidden
                    return 1
                }
                return 0
            }

            override fun getAllSourceHealth(): List<SourceHealthEntity> {
                return sourceHealthMap.values.toList()
            }

            override fun pruneStaleSourceHealth(cutoffTime: Long): Int {
                val keysToRemove = sourceHealthMap.entries
                    .filter { it.value.lastSuccessTime < cutoffTime && it.value.lastFailureTime < cutoffTime }
                    .map { it.key }
                keysToRemove.forEach { sourceHealthMap.remove(it) }
                return keysToRemove.size
            }
        }
    }

    suspend fun getSourceHealth(sourceId: String, currentTime: Long = System.currentTimeMillis(), readonly: Boolean = false): SourceHealth? = withContext(dispatcher) {
        val entity = dao.getSourceHealth(sourceId) ?: return@withContext null
        val domain = entity.toDomain().getUpdatedHealth(currentTime)
        if (!readonly && (domain.healthScore != entity.healthScore || domain.failureCount != entity.failureCount)) {
            dao.insertSourceHealth(domain.toEntity())
        }
        domain
    }

    suspend fun getSourceHealths(
        sourceIds: List<String>,
        currentTime: Long = System.currentTimeMillis(),
        readonly: Boolean = false,
    ): Map<String, SourceHealth?> = withContext(dispatcher) {
        if (sourceIds.isEmpty()) return@withContext emptyMap()
        val distinctIds = sourceIds.distinct()
        val entities = dao.getSourceHealths(distinctIds).associateBy { it.sourceId }
        distinctIds.associateWith { sourceId ->
            val entity = entities[sourceId] ?: return@associateWith null
            val domain = entity.toDomain().getUpdatedHealth(currentTime)
            if (!readonly && (domain.healthScore != entity.healthScore || domain.failureCount != entity.failureCount)) {
                dao.insertSourceHealth(domain.toEntity())
            }
            domain
        }
    }

    suspend fun getProviderHealth(providerId: String, currentTime: Long = System.currentTimeMillis(), readonly: Boolean = false): ProviderHealthScore? = withContext(dispatcher) {
        val entity = dao.getProviderHealth(providerId) ?: return@withContext null
        val domain = entity.toDomain().getUpdatedHealth(currentTime)
        if (!readonly && (domain.healthScore != entity.healthScore || domain.failureCount != entity.failureCount || domain.timeoutCount != entity.timeoutCount)) {
            dao.insertProviderHealth(domain.toEntity())
        }
        domain
    }

    suspend fun recordSignal(
        sourceId: String,
        providerId: String,
        sourceType: PlaybackSourceType,
        signal: SourceHealthSignal,
        timestamp: Long = System.currentTimeMillis(),
        startupTimeMs: Long = 0L,
        bufferingSeverity: Float = 0f,
        errorCategory: String = ""
    ) = withContext(dispatcher) {
        val sourceMutex = getSourceLock(sourceId)
        val providerMutex = getProviderLock(providerId)

        // Serialize signals per-entity to prevent lost-update races,
        // then wrap both writes in a single DB transaction for atomicity.
        sourceMutex.withLock {
            providerMutex.withLock {
                val db = DatabaseProvider.databaseOrNull()
                if (db != null) {
                    db.runInTransaction {
                        writeSourceSignal(
                            sourceId, providerId, sourceType,
                            signal, timestamp, startupTimeMs,
                            bufferingSeverity, errorCategory
                        )
                        writeProviderSignal(providerId, sourceType, signal, timestamp)
                    }
                } else {
                    // Fallback in-memory DAO: wrap both writes together
                    // for best-effort atomicity, though it's not truly
                    // transactional without Room.
                    writeSourceSignal(
                        sourceId, providerId, sourceType,
                        signal, timestamp, startupTimeMs,
                        bufferingSeverity, errorCategory
                    )
                    writeProviderSignal(providerId, sourceType, signal, timestamp)
                }
            }
        }
    }

    private fun writeSourceSignal(
        sourceId: String,
        providerId: String,
        sourceType: PlaybackSourceType,
        signal: SourceHealthSignal,
        timestamp: Long,
        startupTimeMs: Long,
        bufferingSeverity: Float,
        errorCategory: String
    ) {
        val existingSourceEntity = dao.getSourceHealth(sourceId)
        val sourceHealth = if (existingSourceEntity != null) {
            existingSourceEntity.toDomain().getUpdatedHealth(timestamp)
        } else {
            SourceHealth(sourceId = sourceId, providerId = providerId, sourceType = sourceType)
        }
        val updatedSourceHealth = sourceHealth.applySignal(
            signal = signal,
            timestamp = timestamp,
            startupTimeMs = startupTimeMs,
            bufferingSeverity = bufferingSeverity,
            errorCategory = errorCategory
        )
        dao.insertSourceHealth(updatedSourceHealth.toEntity())
    }

    private fun writeProviderSignal(
        providerId: String,
        sourceType: PlaybackSourceType,
        signal: SourceHealthSignal,
        timestamp: Long
    ) {
        val existingProviderEntity = dao.getProviderHealth(providerId)
        val providerHealth = if (existingProviderEntity != null) {
            existingProviderEntity.toDomain().getUpdatedHealth(timestamp)
        } else {
            ProviderHealthScore(providerId = providerId, sourceType = sourceType)
        }
        val updatedProviderHealth = providerHealth.applySignal(
            signal = signal,
            timestamp = timestamp
        )
        dao.insertProviderHealth(updatedProviderHealth.toEntity())
    }

    suspend fun recordSuccess(
        sourceId: String,
        providerId: String,
        sourceType: PlaybackSourceType,
        timestamp: Long = System.currentTimeMillis()
    ) {
        recordSignal(sourceId, providerId, sourceType, SourceHealthSignal.PLAYBACK_SUCCESS, timestamp)
    }

    suspend fun recordFailure(
        sourceId: String,
        providerId: String,
        sourceType: PlaybackSourceType,
        errorCategory: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        recordSignal(
            sourceId = sourceId,
            providerId = providerId,
            sourceType = sourceType,
            signal = SourceHealthSignal.PLAYBACK_FAILURE,
            timestamp = timestamp,
            errorCategory = errorCategory
        )
    }

    suspend fun clearSourceHealth() = withContext(dispatcher) {
        dao.clearSourceHealth()
    }

    suspend fun migrateSourceId(oldSourceId: String, newSourceId: String) = withContext(dispatcher) {
        if (oldSourceId == newSourceId) return@withContext
        val oldEntity = dao.getSourceHealth(oldSourceId) ?: return@withContext
        val newEntity = dao.getSourceHealth(newSourceId)
        val merged = (newEntity ?: SourceHealthEntity()).apply {
            sourceId = newSourceId
            providerId = newEntity?.providerId?.takeIf { it.isNotBlank() } ?: oldEntity.providerId
            sourceType = newEntity?.sourceType ?: oldEntity.sourceType
            failureCount = maxOf(oldEntity.failureCount, newEntity?.failureCount ?: 0)
            lastSuccessTime = maxOf(oldEntity.lastSuccessTime, newEntity?.lastSuccessTime ?: 0L)
            lastFailureTime = maxOf(oldEntity.lastFailureTime, newEntity?.lastFailureTime ?: 0L)
            averageStartupTime = when {
                newEntity == null -> oldEntity.averageStartupTime
                oldEntity.averageStartupTime <= 0L -> newEntity.averageStartupTime
                newEntity.averageStartupTime <= 0L -> oldEntity.averageStartupTime
                else -> (oldEntity.averageStartupTime + newEntity.averageStartupTime) / 2
            }
            averageBufferingSeverity = maxOf(oldEntity.averageBufferingSeverity, newEntity?.averageBufferingSeverity ?: 0f)
            lastErrorCategory = when {
                newEntity?.lastFailureTime ?: 0L >= oldEntity.lastFailureTime -> newEntity?.lastErrorCategory.orEmpty()
                else -> oldEntity.lastErrorCategory
            }
            healthScore = maxOf(oldEntity.healthScore, newEntity?.healthScore ?: 0)
            userHidden = (oldEntity.userHidden || (newEntity?.userHidden == true))
        }
        dao.insertSourceHealth(merged)
        // Legacy sourceId rows are orphaned until stale-health pruning; channels now use newSafeId.
    }

    suspend fun clearProviderHealth() = withContext(dispatcher) {
        dao.clearProviderHealth()
    }

    suspend fun markSourceHidden(sourceId: String, hidden: Boolean) = withContext(dispatcher) {
        dao.markSourceHidden(sourceId, hidden)
    }

    suspend fun setSourceHidden(
        sourceId: String,
        providerId: String,
        sourceType: PlaybackSourceType,
        hidden: Boolean,
        timestamp: Long = System.currentTimeMillis()
    ) = withContext(dispatcher) {
        val existing = dao.getSourceHealth(sourceId)
            ?.toDomain()
            ?.getUpdatedHealth(timestamp)
            ?: SourceHealth(
                sourceId = sourceId,
                providerId = providerId,
                sourceType = sourceType
            )

        val updated = if (hidden) {
            existing.applySignal(SourceHealthSignal.USER_MARKED_BAD, timestamp)
        } else {
            existing.copy(
                userHidden = false,
                healthScore = if (
                    existing.healthScore == 0 &&
                    existing.lastErrorCategory == SourceHealthSignal.USER_MARKED_BAD.name
                ) {
                    100
                } else {
                    existing.healthScore
                },
                lastErrorCategory = if (
                    existing.lastErrorCategory == SourceHealthSignal.USER_MARKED_BAD.name
                ) {
                    ""
                } else {
                    existing.lastErrorCategory
                }
            )
        }

        dao.insertSourceHealth(updated.toEntity())
    }

    suspend fun getAllSourceHealth(currentTime: Long = System.currentTimeMillis()): List<SourceHealth> = withContext(dispatcher) {
        val entities = dao.getAllSourceHealth()
        val decayedEntities = mutableListOf<SourceHealthEntity>()
        val domains = entities.map { entity ->
            val domain = entity.toDomain().getUpdatedHealth(currentTime)
            if (domain.healthScore != entity.healthScore || domain.failureCount != entity.failureCount) {
                decayedEntities.add(domain.toEntity())
            }
            domain
        }
        if (decayedEntities.isNotEmpty() && (currentTime - lastDecayWriteTimeMs.get() > DECAY_WRITE_THROTTLE_MS)) {
            lastDecayWriteTimeMs.set(currentTime)
            dao.insertSourceHealth(decayedEntities)
        }
        domains
    }

    /**
     * Prunes source health entries that have not seen any activity (success or failure)
     * since the cutoff time. Default retention period is 30 days.
     */
    suspend fun pruneStaleSourceHealth(
        retentionPeriodMs: Long = 30L * 24 * 3600_000L,
        currentTime: Long = System.currentTimeMillis()
    ) = withContext(dispatcher) {
        val cutoffTime = currentTime - retentionPeriodMs
        dao.pruneStaleSourceHealth(cutoffTime)
    }

    // Mappings between Room Entity and domain objects
    private fun SourceHealthEntity.toDomain(): SourceHealth {
        return SourceHealth(
            sourceId = this.sourceId,
            providerId = this.providerId,
            sourceType = this.sourceType,
            failureCount = this.failureCount,
            lastSuccessTime = this.lastSuccessTime,
            lastFailureTime = this.lastFailureTime,
            averageStartupTime = this.averageStartupTime,
            averageBufferingSeverity = this.averageBufferingSeverity,
            lastErrorCategory = this.lastErrorCategory,
            healthScore = this.healthScore,
            userHidden = this.userHidden
        )
    }

    private fun SourceHealth.toEntity(): SourceHealthEntity {
        val entity = SourceHealthEntity()
        entity.sourceId = this.sourceId
        entity.providerId = this.providerId
        entity.sourceType = this.sourceType
        entity.failureCount = this.failureCount
        entity.lastSuccessTime = this.lastSuccessTime
        entity.lastFailureTime = this.lastFailureTime
        entity.averageStartupTime = this.averageStartupTime
        entity.averageBufferingSeverity = this.averageBufferingSeverity
        entity.lastErrorCategory = this.lastErrorCategory
        entity.healthScore = this.healthScore
        entity.userHidden = this.userHidden
        return entity
    }

    private fun ProviderHealthScoreEntity.toDomain(): ProviderHealthScore {
        return ProviderHealthScore(
            providerId = this.providerId,
            sourceType = this.sourceType,
            failureCount = this.failureCount,
            successCount = this.successCount,
            lastFailureTime = this.lastFailureTime,
            lastSuccessTime = this.lastSuccessTime,
            timeoutCount = this.timeoutCount,
            healthScore = this.healthScore
        )
    }

    private fun ProviderHealthScore.toEntity(): ProviderHealthScoreEntity {
        val entity = ProviderHealthScoreEntity()
        entity.providerId = this.providerId
        entity.sourceType = this.sourceType
        entity.failureCount = this.failureCount
        entity.successCount = this.successCount
        entity.lastFailureTime = this.lastFailureTime
        entity.lastSuccessTime = this.lastSuccessTime
        entity.timeoutCount = this.timeoutCount
        entity.healthScore = this.healthScore
        return entity
    }
}
