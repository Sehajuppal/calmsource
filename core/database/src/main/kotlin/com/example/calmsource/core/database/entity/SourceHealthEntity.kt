package com.example.calmsource.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.example.calmsource.core.model.PlaybackSourceType

@Entity(
    tableName = "source_health",
    indices = [Index("providerId")]
)
class SourceHealthEntity {
    @PrimaryKey
    var sourceId: String = ""
    var providerId: String = ""
    var sourceType: PlaybackSourceType = PlaybackSourceType.UNKNOWN
    var failureCount: Int = 0
    var lastSuccessTime: Long = 0
    var lastFailureTime: Long = 0
    var averageStartupTime: Long = 0
    var averageBufferingSeverity: Float = 0f
    var lastErrorCategory: String = ""
    var healthScore: Int = 100
    var userHidden: Boolean = false
}
