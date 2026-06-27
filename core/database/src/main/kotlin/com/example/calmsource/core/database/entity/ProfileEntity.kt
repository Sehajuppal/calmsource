package com.example.calmsource.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
