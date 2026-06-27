package com.example.calmsource.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "iptv_providers")
class IPTVProviderEntity {
    @PrimaryKey
    var id: String = ""
    var name: String = ""
    var playlistUrl: String = ""
    var isEnabled: Boolean = false
    var health: String = ""
    var type: String = "M3U"
    var serverUrl: String = ""
    var username: String? = null
}
