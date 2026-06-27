package com.example.calmsource.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.calmsource.core.model.PlaybackSourceType

@Entity(tableName = "provider_health_scores")
class ProviderHealthScoreEntity {
    @PrimaryKey
    var providerId: String = ""
    var sourceType: PlaybackSourceType = PlaybackSourceType.UNKNOWN
    var failureCount: Int = 0
    var successCount: Int = 0
    var lastFailureTime: Long = 0
    var lastSuccessTime: Long = 0
    var timeoutCount: Int = 0
    var healthScore: Int = 100
}
