package com.example.calmsource.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "epg_sources", indices = [Index("providerId")])
class EPGSourceEntity {
    @PrimaryKey
    var id: String = ""
    var providerId: String = ""
    var name: String = ""
    var url: String = ""
    var lastSyncMs: Long = 0
}
