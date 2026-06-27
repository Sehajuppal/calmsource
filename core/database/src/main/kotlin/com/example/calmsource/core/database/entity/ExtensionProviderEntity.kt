package com.example.calmsource.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "extension_providers", indices = [Index("url")])
class ExtensionProviderEntity {
    @PrimaryKey
    var id: String = ""
    var name: String = ""
    var url: String = ""
    var isEnabled: Boolean = false
    var health: String = ""
    var priority: Int = 0
    var manifestJson: String = ""
    var permissionsCsv: String = ""
}
