package com.example.calmsource.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.calmsource.core.database.entity.UserTelemetryEntity

@Dao
interface UserTelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelemetry(entity: UserTelemetryEntity): Long

    @Query("SELECT * FROM user_telemetry WHERE profileId = :profileId ORDER BY timestamp DESC")
    suspend fun getTelemetryForProfile(profileId: String): List<UserTelemetryEntity>

    @Query("DELETE FROM user_telemetry WHERE profileId = :profileId")
    suspend fun clearTelemetryForProfile(profileId: String): Int
}
