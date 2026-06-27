package com.example.calmsource.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_telemetry",
    indices = [
        Index("profileId"),
        Index("eventType"),
        Index("timestamp")
    ]
)
data class UserTelemetryEntity(
    @PrimaryKey(autoGenerate = true) val telemetryId: Long = 0,
    val profileId: String,
    val eventType: String,
    val eventData: String,
    val timestamp: Long
)
