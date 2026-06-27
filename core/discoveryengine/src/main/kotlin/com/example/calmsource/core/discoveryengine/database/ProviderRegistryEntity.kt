package com.example.calmsource.core.discoveryengine.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "provider_registry",
    indices = [
        Index("type"),
        Index("priority")
    ]
)
data class ProviderRegistryEntity(
    @PrimaryKey val providerId: String,
    val name: String,
    val type: String,
    val kind: String,
    val endpointUrl: String?,
    val isEnabled: Boolean,
    val isSystemProvider: Boolean,
    val isUserInstalled: Boolean,
    val priority: Int,
    val supportsCatalog: Boolean,
    val supportsMeta: Boolean,
    val supportsStream: Boolean,
    val supportsSubtitles: Boolean,
    val supportsSearch: Boolean,
    val supportsRatings: Boolean,
    val supportsSimilar: Boolean,
    val supportsArtwork: Boolean,
    val supportsAvailability: Boolean,
    val privacySensitive: Boolean,
    val lastSuccessAt: Long?,
    val lastFailureAt: Long?,
    val failureCount: Int,
    val reliabilityScore: Double,
    val createdAt: Long,
    val updatedAt: Long
)
