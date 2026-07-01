package com.example.calmsource.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "epg_programs",
    indices = [
        Index("channelId", "startTimeMs"),
        Index("startTimeMs", "endTimeMs"),
        Index("endTimeMs")
    ]
)
class EPGProgramEntity {
    @PrimaryKey
    var id: String = ""
    var channelId: String = ""
    var title: String = ""
    var description: String? = null
    var startTimeMs: Long = 0
    var endTimeMs: Long = 0
    var subtitle: String? = null
    var category: String? = null
    var language: String? = null
    var episodeNum: String? = null
}
