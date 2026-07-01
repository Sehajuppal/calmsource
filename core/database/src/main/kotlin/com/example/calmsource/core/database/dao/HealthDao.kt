package com.example.calmsource.core.database.dao

import androidx.room.*
import com.example.calmsource.core.database.entity.ProviderHealthScoreEntity
import com.example.calmsource.core.database.entity.SourceHealthEntity

@Dao
interface HealthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSourceHealth(entity: SourceHealthEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSourceHealth(entities: List<SourceHealthEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertProviderHealth(entity: ProviderHealthScoreEntity): Long

    @Query("SELECT * FROM source_health WHERE sourceId = :sourceId")
    fun getSourceHealth(sourceId: String): SourceHealthEntity?

    @Query("SELECT * FROM source_health WHERE sourceId IN (:sourceIds)")
    fun getSourceHealths(sourceIds: List<String>): List<SourceHealthEntity>

    @Query("SELECT * FROM provider_health_scores WHERE providerId = :providerId")
    fun getProviderHealth(providerId: String): ProviderHealthScoreEntity?

    @Query("DELETE FROM source_health")
    fun clearSourceHealth(): Int

    @Query("DELETE FROM provider_health_scores")
    fun clearProviderHealth(): Int

    @Query("UPDATE source_health SET userHidden = :hidden WHERE sourceId = :sourceId")
    fun markSourceHidden(sourceId: String, hidden: Boolean): Int

    @Query("SELECT * FROM source_health")
    fun getAllSourceHealth(): List<SourceHealthEntity>

    @Query("DELETE FROM source_health WHERE lastSuccessTime < :cutoffTime AND lastFailureTime < :cutoffTime")
    fun pruneStaleSourceHealth(cutoffTime: Long): Int
}
