package com.example.calmsource.core.discoveryengine.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "provider_failure_log",
    indices = [
        Index("providerId"),
        Index("occurredAt")
    ]
)
data class ProviderFailureLogEntity(
    @PrimaryKey(autoGenerate = true) val failureId: Long = 0,
    val providerId: String,
    val requestType: String,
    val mediaId: String?,
    val errorCode: String,
    val errorMessage: String?,
    val occurredAt: Long,
    val retryAfter: Long?
)

@Entity(
    tableName = "provider_usage_log",
    indices = [
        Index("providerId"),
        Index("createdAt")
    ]
)
data class ProviderUsageLogEntity(
    @PrimaryKey(autoGenerate = true) val usageId: Long = 0,
    val providerId: String,
    val requestType: String,
    val mediaId: String?,
    val cacheHit: Boolean,
    val durationMs: Long,
    val success: Boolean,
    val createdAt: Long
)
