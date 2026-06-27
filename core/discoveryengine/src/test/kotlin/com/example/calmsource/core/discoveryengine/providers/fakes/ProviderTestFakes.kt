package com.example.calmsource.core.discoveryengine.providers.fakes

import com.example.calmsource.core.discoveryengine.database.AddonAvailabilityEntity
import com.example.calmsource.core.discoveryengine.database.AvailabilityCacheEntity
import com.example.calmsource.core.discoveryengine.database.MetadataCacheEntity
import com.example.calmsource.core.discoveryengine.database.ProviderCacheDao
import com.example.calmsource.core.discoveryengine.database.ProviderFailureLogEntity
import com.example.calmsource.core.discoveryengine.database.ProviderRegistryDao
import com.example.calmsource.core.discoveryengine.database.ProviderRegistryEntity
import com.example.calmsource.core.discoveryengine.database.ProviderTelemetryDao
import com.example.calmsource.core.discoveryengine.database.ProviderUsageLogEntity
import com.example.calmsource.core.discoveryengine.database.RatingsCacheEntity
import com.example.calmsource.core.discoveryengine.database.SimilarCacheEntity
import com.example.calmsource.core.discoveryengine.database.SubtitlesCacheEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeProviderRegistryDao : ProviderRegistryDao {
    private val rows = linkedMapOf<String, ProviderRegistryEntity>()
    private val flow = MutableStateFlow<List<ProviderRegistryEntity>>(emptyList())

    override fun upsert(entity: ProviderRegistryEntity) {
        rows[entity.providerId] = entity
        emit()
    }

    override fun getAll(): List<ProviderRegistryEntity> = rows.values.sorted()

    override fun getEnabled(): List<ProviderRegistryEntity> = rows.values.filter { it.isEnabled }.sorted()

    override fun get(providerId: String): ProviderRegistryEntity? = rows[providerId]

    override fun observeAll(): Flow<List<ProviderRegistryEntity>> = flow

    override fun setEnabled(providerId: String, enabled: Boolean, updatedAt: Long) {
        rows[providerId]?.let { rows[providerId] = it.copy(isEnabled = enabled, updatedAt = updatedAt) }
        emit()
    }

    override fun setPriority(providerId: String, priority: Int, updatedAt: Long) {
        rows[providerId]?.let { rows[providerId] = it.copy(priority = priority, updatedAt = updatedAt) }
        emit()
    }

    override fun updateReliability(
        providerId: String,
        reliabilityScore: Double,
        failureCount: Int,
        lastSuccessAt: Long?,
        lastFailureAt: Long?,
        updatedAt: Long
    ) {
        rows[providerId]?.let {
            rows[providerId] = it.copy(
                reliabilityScore = reliabilityScore,
                failureCount = failureCount,
                lastSuccessAt = lastSuccessAt,
                lastFailureAt = lastFailureAt,
                updatedAt = updatedAt
            )
        }
        emit()
    }

    override fun updateEndpoint(providerId: String, endpointUrl: String, updatedAt: Long) {
        rows[providerId]?.let { rows[providerId] = it.copy(endpointUrl = endpointUrl, updatedAt = updatedAt) }
        emit()
    }

    override fun delete(providerId: String) {
        rows.remove(providerId)
        emit()
    }

    private fun emit() {
        flow.value = rows.values.sorted()
    }

    private fun Collection<ProviderRegistryEntity>.sorted(): List<ProviderRegistryEntity> =
        sortedWith(compareBy<ProviderRegistryEntity> { it.priority }.thenBy { it.name })
}

class FakeProviderCacheDao : ProviderCacheDao {
    val metadata = linkedMapOf<Pair<String, String>, MetadataCacheEntity>()
    val ratings = linkedMapOf<Pair<String, String>, RatingsCacheEntity>()
    val similar = linkedMapOf<Triple<String, String, String>, SimilarCacheEntity>()
    val subtitles = linkedMapOf<String, SubtitlesCacheEntity>()
    val availability = linkedMapOf<Triple<String, String, String>, AvailabilityCacheEntity>()
    val addonAvailability = linkedMapOf<Pair<String, String>, AddonAvailabilityEntity>()

    override fun upsertMetadata(entity: MetadataCacheEntity) {
        metadata[entity.mediaId to entity.providerId] = entity
    }

    override fun upsertRating(entity: RatingsCacheEntity) {
        ratings[entity.mediaId to entity.providerId] = entity
    }

    override fun upsertSimilar(entities: List<SimilarCacheEntity>) {
        entities.forEach { similar[Triple(it.mediaId, it.providerId, it.similarMediaId)] = it }
    }

    override fun upsertSubtitles(entities: List<SubtitlesCacheEntity>) {
        entities.forEach { subtitles[it.id] = it }
    }

    override fun upsertAvailability(entities: List<AvailabilityCacheEntity>) {
        entities.forEach { availability[Triple(it.mediaId, it.providerId, it.addonId)] = it }
    }

    override fun upsertAddonAvailability(entities: List<AddonAvailabilityEntity>) {
        entities.forEach { addonAvailability[it.mediaId to it.addonId] = it }
    }

    override fun getMetadata(mediaId: String, now: Long): List<MetadataCacheEntity> =
        metadata.values.filter { it.mediaId == mediaId && it.expiresAt > now }

    override fun getRatings(mediaId: String, now: Long): List<RatingsCacheEntity> =
        ratings.values.filter { it.mediaId == mediaId && it.expiresAt > now }

    override fun getSimilar(mediaId: String, now: Long): List<SimilarCacheEntity> =
        similar.values.filter { it.mediaId == mediaId && it.expiresAt > now }.sortedByDescending { it.providerScore }

    override fun getSubtitles(mediaId: String, now: Long): List<SubtitlesCacheEntity> =
        subtitles.values.filter { it.mediaId == mediaId && it.expiresAt > now }.sortedByDescending { it.matchConfidence }

    override fun getAvailability(mediaId: String, now: Long): List<AvailabilityCacheEntity> =
        availability.values.filter { it.mediaId == mediaId && it.expiresAt > now }

    override fun deleteSimilarForProvider(mediaId: String, providerId: String) {
        similar.keys.filter { it.first == mediaId && it.second == providerId }.forEach { similar.remove(it) }
    }

    override fun deleteSubtitlesForProvider(mediaId: String, providerId: String) {
        subtitles.values.filter { it.mediaId == mediaId && it.providerId == providerId }.map { it.id }.forEach { subtitles.remove(it) }
    }

    override fun deleteAvailabilityForProvider(mediaId: String, providerId: String) {
        availability.keys.filter { it.first == mediaId && it.second == providerId }.forEach { availability.remove(it) }
    }

    override fun pruneMetadata(now: Long): Int = prune(metadata) { it.expiresAt < now }

    override fun pruneRatings(now: Long): Int = prune(ratings) { it.expiresAt < now }

    override fun pruneSimilar(now: Long): Int = prune(similar) { it.expiresAt < now }

    override fun pruneSubtitles(now: Long): Int = prune(subtitles) { it.expiresAt < now }

    override fun pruneAvailability(now: Long): Int = prune(availability) { it.expiresAt < now }

    override fun clearMetadata() {
        metadata.clear()
    }

    override fun clearRatings() {
        ratings.clear()
    }

    override fun clearSimilar() {
        similar.clear()
    }

    override fun clearSubtitles() {
        subtitles.clear()
    }

    override fun clearAvailability() {
        availability.clear()
    }

    override fun clearAddonAvailability() {
        addonAvailability.clear()
    }

    override fun clearAll() {
        clearMetadata()
        clearRatings()
        clearSimilar()
        clearSubtitles()
        clearAvailability()
        clearAddonAvailability()
    }

    private fun <K, V> prune(map: MutableMap<K, V>, shouldPrune: (V) -> Boolean): Int {
        val keys = map.filterValues(shouldPrune).keys.toList()
        keys.forEach { map.remove(it) }
        return keys.size
    }
}

class FakeProviderTelemetryDao : ProviderTelemetryDao {
    private val failures = mutableListOf<ProviderFailureLogEntity>()
    private val usages = mutableListOf<ProviderUsageLogEntity>()
    private var nextFailureId = 1L
    private var nextUsageId = 1L

    override fun insertFailure(entity: ProviderFailureLogEntity): Long {
        val id = nextFailureId++
        failures += entity.copy(failureId = id)
        return id
    }

    override fun insertUsage(entity: ProviderUsageLogEntity): Long {
        val id = nextUsageId++
        usages += entity.copy(usageId = id)
        return id
    }

    override fun getRecentFailures(limit: Int): List<ProviderFailureLogEntity> =
        failures.sortedByDescending { it.occurredAt }.take(limit)

    override fun getFailuresForProvider(providerId: String, limit: Int): List<ProviderFailureLogEntity> =
        failures.filter { it.providerId == providerId }.sortedByDescending { it.occurredAt }.take(limit)

    override fun pruneFailures(cutoff: Long): Int {
        val before = failures.size
        failures.removeAll { it.occurredAt < cutoff }
        return before - failures.size
    }

    override fun clearAllFailures() {
        failures.clear()
    }

    override fun getRecentUsage(limit: Int): List<ProviderUsageLogEntity> =
        usages.sortedByDescending { it.createdAt }.take(limit)

    override fun pruneUsage(cutoff: Long): Int {
        val before = usages.size
        usages.removeAll { it.createdAt < cutoff }
        return before - usages.size
    }

    override fun clearAllUsage() {
        usages.clear()
    }
}
