package com.example.calmsource.core.discoveryengine.providers

import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteException
import android.util.Log
import com.example.calmsource.core.discoveryengine.database.AvailabilityCacheEntity
import com.example.calmsource.core.discoveryengine.database.MetadataCacheEntity
import com.example.calmsource.core.discoveryengine.database.ProviderCacheDao
import com.example.calmsource.core.discoveryengine.database.RatingsCacheEntity
import com.example.calmsource.core.discoveryengine.database.SimilarCacheEntity
import com.example.calmsource.core.discoveryengine.database.SubtitlesCacheEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProviderCacheStore(private val dao: ProviderCacheDao) {
    private val writeLock = Any()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val now: Long get() = System.currentTimeMillis()

    private suspend inline fun writeCache(operation: String, block: () -> Unit) {
        retry(operation) {
            synchronized(writeLock) {
                block()
            }
        }
    }

    private inline fun <T> readCache(operation: String, fallback: T, block: () -> T): T {
        return try {
            timed(operation, slowThresholdMs = 50L, block)
        } catch (e: Exception) {
            safeWarn("$operation failed: ${e.javaClass.simpleName}")
            fallback
        }
    }

    private suspend inline fun retry(operation: String, block: () -> Unit) {
        val retryDelaysMs = longArrayOf(25L, 75L, 150L)
        var attempt = 0
        while (true) {
            try {
                timed(operation, slowThresholdMs = 100L, block)
                return
            } catch (e: Exception) {
                val delayMs = retryDelaysMs.getOrNull(attempt)
                if (!isTransientLock(e) || delayMs == null) {
                    safeWarn("$operation failed after ${attempt + 1} attempt(s): ${e.javaClass.simpleName}")
                    throw e // DE-BUG-5: rethrow so callers know the write was lost
                }
                attempt++
                safeWarn("$operation hit SQLite lock; retrying in ${delayMs}ms")
                delay(delayMs) // DE-BUG-2: suspend instead of blocking IO thread
            }
        }
    }

    private inline fun <T> timed(operation: String, slowThresholdMs: Long, block: () -> T): T {
        val startedAt = System.currentTimeMillis()
        return try {
            block()
        } finally {
            val durationMs = System.currentTimeMillis() - startedAt
            if (durationMs >= slowThresholdMs) {
                safeInfo("$operation took ${durationMs}ms")
            }
        }
    }

    suspend fun putMetadata(
        mediaId: String,
        providerId: String,
        metadata: EnrichedMetadata,
        ttlMs: Long = ProviderTtl.METADATA_DEFAULT,
        confidenceScore: Double = 1.0
    ) {
        val fetchedAt = now
        writeCache("putMetadata") {
            dao.upsertMetadata(
                MetadataCacheEntity(
                    mediaId = mediaId,
                    providerId = providerId,
                    title = metadata.title,
                    originalTitle = metadata.originalTitle,
                    aliases = encodeList(metadata.aliases),
                    overview = metadata.overview,
                    genres = encodeList(metadata.genres),
                    cast = encodeList(metadata.cast),
                    director = metadata.director,
                    runtimeMinutes = metadata.runtimeMinutes,
                    language = metadata.language,
                    country = metadata.country,
                    posterUrl = metadata.posterUrl,
                    backdropUrl = metadata.backdropUrl,
                    externalIdsJson = encodeMap(metadata.externalIds),
                    collectionJson = metadata.collection,
                    seasonEpisodeJson = metadata.seasonEpisode,
                    confidenceScore = confidenceScore,
                    fetchedAt = fetchedAt,
                    expiresAt = fetchedAt + ttlMs
                )
            )
        }
    }

    fun getMetadata(mediaId: String): List<EnrichedMetadata> {
        return readCache("getMetadata", emptyList()) {
            dao.getMetadata(mediaId, now).map { it.toMetadata() }
        }
    }

    suspend fun putRatings(
        mediaId: String,
        providerId: String,
        ratings: List<RatingEntry>,
        ttlMs: Long = ProviderTtl.RATINGS_DEFAULT,
        confidenceScore: Double = 1.0
    ) {
        if (ratings.isEmpty()) return
        val fetchedAt = now
        // DE-BUG-6: Iterate all ratings, not just firstOrNull()
        writeCache("putRatings:${ratings.size}") {
            ratings.forEachIndexed { index, rating ->
                dao.upsertRating(
                    RatingsCacheEntity(
                        mediaId = mediaId,
                        providerId = if (ratings.size == 1) providerId else "${providerId}:$index",
                        ratingValue = rating.value,
                        ratingScale = rating.scale,
                        voteCount = rating.voteCount,
                        popularityScore = rating.popularity,
                        qualityScore = null,
                        confidenceScore = confidenceScore,
                        fetchedAt = fetchedAt,
                        expiresAt = fetchedAt + ttlMs
                    )
                )
            }
        }
    }

    fun getRatings(mediaId: String): List<RatingEntry> {
        return readCache("getRatings", emptyList()) {
            dao.getRatings(mediaId, now).map {
                RatingEntry(
                    value = it.ratingValue,
                    scale = it.ratingScale,
                    voteCount = it.voteCount,
                    popularity = it.popularityScore
                )
            }
        }
    }

    suspend fun putSimilar(
        mediaId: String,
        providerId: String,
        similar: List<SimilarEntry>,
        ttlMs: Long = ProviderTtl.SIMILAR_DEFAULT,
        confidenceScore: Double = 1.0
    ) {
        val fetchedAt = now
        val rows = similar.map {
            SimilarCacheEntity(
                mediaId = mediaId,
                providerId = providerId,
                similarMediaId = it.similarMediaId,
                similarExternalIdsJson = encodeMap(it.externalIds),
                similarTitle = it.similarTitle,
                providerScore = it.providerScore,
                reason = it.reason,
                confidenceScore = confidenceScore,
                fetchedAt = fetchedAt,
                expiresAt = fetchedAt + ttlMs
            )
        }
        writeCache("putSimilar:${rows.size}") {
            dao.deleteSimilarForProvider(mediaId, providerId)
            if (rows.isNotEmpty()) {
                dao.upsertSimilar(rows)
            }
        }
    }

    fun getSimilar(mediaId: String): List<SimilarEntry> {
        return readCache("getSimilar", emptyList()) {
            dao.getSimilar(mediaId, now).map {
                SimilarEntry(
                    similarMediaId = it.similarMediaId,
                    similarTitle = it.similarTitle,
                    externalIds = decodeMap(it.similarExternalIdsJson),
                    providerScore = it.providerScore,
                    reason = it.reason
                )
            }
        }
    }

    suspend fun putSubtitles(
        mediaId: String,
        providerId: String,
        subtitles: List<SubtitleEntry>,
        ttlMs: Long = ProviderTtl.SUBTITLES_DEFAULT
    ) {
        val fetchedAt = now
        val rows = subtitles.map {
            SubtitlesCacheEntity(
                id = "${providerId}:${mediaId}:${it.id}",
                mediaId = mediaId,
                providerId = providerId,
                streamHash = it.streamHash,
                filename = it.filename,
                language = it.language,
                subtitleUrl = it.url,
                subtitleFormat = it.format,
                matchConfidence = it.matchConfidence,
                fetchedAt = fetchedAt,
                expiresAt = fetchedAt + ttlMs
            )
        }
        writeCache("putSubtitles:${rows.size}") {
            dao.deleteSubtitlesForProvider(mediaId, providerId)
            if (rows.isNotEmpty()) {
                dao.upsertSubtitles(rows)
            }
        }
    }

    fun getSubtitles(mediaId: String): List<SubtitleEntry> {
        return readCache("getSubtitles", emptyList()) {
            dao.getSubtitles(mediaId, now).map {
                SubtitleEntry(
                    id = it.id,
                    language = it.language,
                    url = it.subtitleUrl,
                    format = it.subtitleFormat,
                    matchConfidence = it.matchConfidence,
                    streamHash = it.streamHash,
                    filename = it.filename
                )
            }
        }
    }

    suspend fun putAvailability(
        mediaId: String,
        providerId: String,
        availability: List<AvailabilityEntry>,
        ttlMs: Long = ProviderTtl.AVAILABILITY_DEFAULT,
        confidenceScore: Double = 1.0
    ) {
        val checkedAt = now
        val rows = availability.map {
            AvailabilityCacheEntity(
                mediaId = mediaId,
                providerId = providerId,
                addonId = it.addonId,
                streamCount = it.streamCount,
                bestQuality = it.bestQuality,
                hasSubtitles = it.hasSubtitles,
                languagesJson = encodeList(it.languages),
                confidenceScore = confidenceScore,
                lastCheckedAt = checkedAt,
                expiresAt = checkedAt + ttlMs
            )
        }
        writeCache("putAvailability:${rows.size}") {
            dao.deleteAvailabilityForProvider(mediaId, providerId)
            if (rows.isNotEmpty()) {
                dao.upsertAvailability(rows)
            }
        }
    }

    fun getAvailability(mediaId: String): List<AvailabilityEntry> {
        return readCache("getAvailability", emptyList()) {
            dao.getAvailability(mediaId, now).map {
                AvailabilityEntry(
                    addonId = it.addonId,
                    streamCount = it.streamCount,
                    bestQuality = it.bestQuality,
                    hasSubtitles = it.hasSubtitles,
                    languages = decodeList(it.languagesJson)
                )
            }
        }
    }

    suspend fun pruneExpired(timestamp: Long = now): Int {
        var pruned = 0
        writeCache("pruneExpired") {
            pruned = dao.pruneMetadata(timestamp) +
                dao.pruneRatings(timestamp) +
                dao.pruneSimilar(timestamp) +
                dao.pruneSubtitles(timestamp) +
                dao.pruneAvailability(timestamp)
        }
        return pruned
    }

    suspend fun clearAll() {
        writeCache("clearProviderCache") {
            dao.clearAll()
        }
    }

    private fun MetadataCacheEntity.toMetadata(): EnrichedMetadata {
        return EnrichedMetadata(
            title = title,
            originalTitle = originalTitle,
            aliases = decodeList(aliases),
            overview = overview,
            genres = decodeList(genres),
            cast = decodeList(cast),
            director = director,
            runtimeMinutes = runtimeMinutes,
            language = language,
            country = country,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            externalIds = decodeMap(externalIdsJson),
            collection = collectionJson,
            seasonEpisode = seasonEpisodeJson
        )
    }

    private fun encodeList(values: List<String>): String = json.encodeToString(values)

    private fun encodeMap(values: Map<String, String>): String = json.encodeToString(values)

    private fun decodeList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
    }

    private fun decodeMap(value: String?): Map<String, String> {
        if (value.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(value) }.getOrDefault(emptyMap())
    }

    private fun isTransientLock(error: Throwable?): Boolean {
        if (error == null) return false
        if (error is SQLiteDatabaseLockedException) return true
        if (error is SQLiteException) {
            val message = error.message?.lowercase().orEmpty()
            if ("locked" in message || "busy" in message) return true
        }
        return isTransientLock(error.cause)
    }

    private fun safeInfo(message: String) {
        try {
            Log.i("ProviderCacheStore", message)
        } catch (_: Throwable) {
            // Local JVM tests use Android stubs.
        }
    }

    private fun safeWarn(message: String) {
        try {
            Log.w("ProviderCacheStore", message)
        } catch (_: Throwable) {
            // Local JVM tests use Android stubs.
        }
    }
}
