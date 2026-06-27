package com.example.calmsource.core.discoveryengine.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProviderTelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFailure(entity: ProviderFailureLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUsage(entity: ProviderUsageLogEntity): Long

    @Query("SELECT * FROM provider_failure_log ORDER BY occurredAt DESC LIMIT :limit")
    fun getRecentFailures(limit: Int): List<ProviderFailureLogEntity>

    @Query("SELECT * FROM provider_failure_log WHERE providerId = :providerId ORDER BY occurredAt DESC LIMIT :limit")
    fun getFailuresForProvider(providerId: String, limit: Int): List<ProviderFailureLogEntity>

    @Query("DELETE FROM provider_failure_log WHERE occurredAt < :cutoff")
    fun pruneFailures(cutoff: Long): Int

    @Query("DELETE FROM provider_failure_log")
    fun clearAllFailures()

    @Query("SELECT * FROM provider_usage_log ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentUsage(limit: Int): List<ProviderUsageLogEntity>

    @Query("DELETE FROM provider_usage_log WHERE createdAt < :cutoff")
    fun pruneUsage(cutoff: Long): Int

    @Query("DELETE FROM provider_usage_log")
    fun clearAllUsage()
}
