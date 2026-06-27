package com.example.calmsource.core.discoveryengine.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderRegistryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: ProviderRegistryEntity)

    @Query("SELECT * FROM provider_registry ORDER BY priority ASC, name ASC")
    fun getAll(): List<ProviderRegistryEntity>

    @Query("SELECT * FROM provider_registry WHERE isEnabled = 1 ORDER BY priority ASC, name ASC")
    fun getEnabled(): List<ProviderRegistryEntity>

    @Query("SELECT * FROM provider_registry WHERE providerId = :providerId LIMIT 1")
    fun get(providerId: String): ProviderRegistryEntity?

    @Query("SELECT * FROM provider_registry ORDER BY priority ASC, name ASC")
    fun observeAll(): Flow<List<ProviderRegistryEntity>>

    @Query("UPDATE provider_registry SET isEnabled = :enabled, updatedAt = :updatedAt WHERE providerId = :providerId")
    fun setEnabled(providerId: String, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE provider_registry SET priority = :priority, updatedAt = :updatedAt WHERE providerId = :providerId")
    fun setPriority(providerId: String, priority: Int, updatedAt: Long)

    @Query(
        """
        UPDATE provider_registry
        SET reliabilityScore = :reliabilityScore,
            failureCount = :failureCount,
            lastSuccessAt = :lastSuccessAt,
            lastFailureAt = :lastFailureAt,
            updatedAt = :updatedAt
        WHERE providerId = :providerId
        """
    )
    fun updateReliability(
        providerId: String,
        reliabilityScore: Double,
        failureCount: Int,
        lastSuccessAt: Long?,
        lastFailureAt: Long?,
        updatedAt: Long
    )

    @Query("UPDATE provider_registry SET endpointUrl = :endpointUrl, updatedAt = :updatedAt WHERE providerId = :providerId")
    fun updateEndpoint(providerId: String, endpointUrl: String, updatedAt: Long)

    @Query("DELETE FROM provider_registry WHERE providerId = :providerId")
    fun delete(providerId: String)
}
