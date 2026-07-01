package com.example.calmsource.core.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "continue_watching",
    primaryKeys = ["profileId", "itemKey"],
    indices = [Index(value = ["profileId", "updatedAt"])]
)
data class ContinueWatchingEntity(
    val profileId: String = "default",
    val itemKey: String,
    val contentType: String,
    val title: String,
    val subtitle: String?,
    val providerId: String?,
    val sourceId: String?,
    val progressMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "favorites",
    primaryKeys = ["profileId", "itemKey"],
    indices = [Index(value = ["profileId", "updatedAt"])]
)
data class FavoriteEntity(
    val profileId: String = "default",
    val itemKey: String,
    val contentType: String,
    val title: String,
    val subtitle: String?,
    val providerId: String?,
    val sourceId: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "watch_history",
    primaryKeys = ["profileId", "itemKey"],
    indices = [Index(value = ["profileId", "lastWatchedAt"])]
)
data class WatchHistoryEntity(
    val profileId: String = "default",
    val itemKey: String,
    val contentType: String,
    val title: String,
    val subtitle: String?,
    val providerId: String?,
    val sourceId: String?,
    val firstWatchedAt: Long,
    val lastWatchedAt: Long,
    val watchCount: Long,
    val progressMs: Long,
    val durationMs: Long
)

@Entity(
    tableName = "recent_channels",
    primaryKeys = ["profileId", "itemKey"],
    indices = [Index(value = ["profileId", "lastWatchedAt"])]
)
data class RecentChannelEntity(
    val profileId: String = "default",
    val itemKey: String,
    val contentType: String,
    val title: String,
    val subtitle: String?,
    val providerId: String?,
    val sourceId: String?,
    val lastWatchedAt: Long,
    val watchCount: Long
)

@Entity(
    tableName = "search_history",
    primaryKeys = ["profileId", "normalizedQuery"],
    indices = [Index(value = ["profileId", "lastSearchedAt"])]
)
data class SearchHistoryEntity(
    val profileId: String = "default",
    val normalizedQuery: String,
    val query: String,
    val lastSearchedAt: Long,
    val searchCount: Long
)

@Entity(
    tableName = "preference_signals",
    primaryKeys = ["profileId", "signalType", "signalKey"],
    indices = [Index(value = ["profileId", "lastSignaledAt"])]
)
data class PreferenceSignalEntity(
    val profileId: String = "default",
    val signalType: String,
    val signalKey: String,
    val count: Long,
    val lastSignaledAt: Long
)

