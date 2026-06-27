package com.example.calmsource.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debrid_accounts")
class DebridAccountEntity {
    @PrimaryKey
    var id: String = ""
    var providerType: String = ""
    var providerName: String = ""
    var isConnected: Boolean = false
    var email: String? = null
    var username: String? = null
    var health: String = ""
}
