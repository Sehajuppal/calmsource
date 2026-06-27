package com.example.calmsource.core.database.dao

import androidx.room.*
import com.example.calmsource.core.database.entity.ProviderHealthScoreEntity
import com.example.calmsource.core.database.entity.SourceHealthEntity

@Dao
interface HealthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSourceHealth(entity: SourceHealthEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSourceHealth(entities: List<SourceHealthEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertProviderHealth(entity: ProviderHealthScoreEntity)

    @Query("SELECT * FROM source_health WHERE sourceId = :sourceId")
    fun getSourceHealth(sourceId: String): SourceHealthEntity?

    @Query("SELECT * FROM provider_health_scores WHERE providerId = :providerId")
    fun getProviderHealth(providerId: String): ProviderHealthScoreEntity?

    @Query("DELETE FROM source_health")
    fun clearSourceHealth()

    @Query("DELETE FROM provider_health_scores")
    fun clearProviderHealth()

    @Query("UPDATE source_health SET userHidden = :hidden WHERE sourceId = :sourceId")
    fun markSourceHidden(sourceId: String, hidden: Boolean)

    @Query("SELECT * FROM source_health")
    fun getAllSourceHealth(): List<SourceHealthEntity>

    @Query("DELETE FROM source_health WHERE lastSuccessTime < :cutoffTime AND lastFailureTime < :cutoffTime")
    fun pruneStaleSourceHealth(cutoffTime: Long)
}
