package com.example.calmsource.core.playback

import android.os.Bundle
import androidx.compose.runtime.saveable.Saver
import com.example.calmsource.core.model.PlaybackItemMetadata
import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.UserMemoryContentType
import com.example.calmsource.core.model.UserMemoryReference

/**
 * Thread-safe static cache to hold sensitive raw URLs in memory.
 * This ensures they survive configuration changes (like screen rotation)
 * without being persisted to the Android Saved Instance State bundle on disk.
 */
object PlaybackUrlCache {
    private val cache = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, String>(20, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                return size > 20
            }
        }
    )

    fun get(id: String): String? = cache[id]

    fun put(id: String, rawUrl: String) {
        if (rawUrl.isNotEmpty() && rawUrl != id) {
            cache[id] = rawUrl
        }
    }
}

/**
 * Bundle-based Saver for [PlaybackRequest] used in [rememberSaveable].
 * Preserves playback state across configuration changes and process death
 * for both mobile and TV player screens.
 */
val PlaybackRequestSaver = Saver<PlaybackRequest, Bundle>(
    save = { request ->
        // Cache the rawUrl in memory keyed by id
        PlaybackUrlCache.put(request.source.id, request.source.rawUrl)
        Bundle().apply {
            putLong("startPositionMs", request.startPositionMs)
            putBoolean("playWhenReady", request.playWhenReady)
            putBundle("source", Bundle().apply {
                putString("id", request.source.id)
                putString("type", request.source.type.name)
                putString("title", request.source.title)
                putString("rawUrl", "") // SECURE: Do not write the raw URL to the persistent bundle
                putString("displayUrl", request.source.displayUrl)
                putBoolean("allowInsecureHttp", request.source.allowInsecureHttp)
                // Intentionally not persisting headers (may contain auth tokens)
                request.source.metadata?.let { meta ->
                    putBundle("metadata", Bundle().apply {
                        putString("title", meta.title)
                        meta.description?.let { putString("description", it) }
                        meta.posterUrl?.let { putString("posterUrl", it) }
                        meta.backdropUrl?.let { putString("backdropUrl", it) }
                        meta.durationMs?.let { putLong("durationMs", it) }
                        putBoolean("isLive", meta.isLive)
                        meta.seriesName?.let { putString("seriesName", it) }
                        meta.seasonNumber?.let { putInt("seasonNumber", it) }
                        meta.episodeNumber?.let { putInt("episodeNumber", it) }
                        meta.genre?.let { putString("genre", it) }
                        meta.containerFormat?.let { putString("containerFormat", it) }
                        meta.videoCodec?.let { putString("videoCodec", it) }
                        meta.audioCodec?.let { putString("audioCodec", it) }
                    })
                }
            })
            request.userMemoryReference?.let { ref ->
                putBundle("userMemoryReference", Bundle().apply {
                    putString("itemKey", ref.itemKey)
                    putString("contentType", ref.contentType.name)
                    putString("title", ref.title)
                    ref.subtitle?.let { putString("subtitle", it) }
                    ref.providerId?.let { putString("providerId", it) }
                    ref.sourceId?.let { putString("sourceId", it) }
                })
            }
            request.seriesContext?.let { series ->
                putBundle("seriesContext", Bundle().apply {
                    putString("seriesId", series.seriesId)
                    putString("seriesTitle", series.seriesTitle)
                    series.posterUrl?.let { putString("posterUrl", it) }
                    series.backdropUrl?.let { putString("backdropUrl", it) }
                })
            }
        }
    },
    restore = { bundle ->
        val startPositionMs = bundle.getLong("startPositionMs")
        val playWhenReady = bundle.getBoolean("playWhenReady")

        val sourceBundle = bundle.getBundle("source") ?: return@Saver null
        val id = sourceBundle.getString("id") ?: return@Saver null
        val type = runCatching { PlaybackSourceType.valueOf(sourceBundle.getString("type") ?: "UNKNOWN") }
            .getOrDefault(PlaybackSourceType.UNKNOWN)
        val title = sourceBundle.getString("title") ?: ""
        val displayUrl = sourceBundle.getString("displayUrl") ?: ""
        
        // Restore rawUrl from in-memory cache if present (e.g. rotation).
        // Otherwise, it remains empty, triggering re-resolution in the Player Screen.
        val rawUrl = PlaybackUrlCache.get(id) ?: ""
        val allowInsecureHttp = sourceBundle.getBoolean("allowInsecureHttp")

        val headersBundle = sourceBundle.getBundle("headers")
        val headers = mutableMapOf<String, String>()
        headersBundle?.keySet()?.forEach { k ->
            headersBundle.getString(k)?.let { v -> headers[k] = v }
        }

        val metaBundle = sourceBundle.getBundle("metadata")
        val metadata = if (metaBundle != null) {
            val durationMs = if (metaBundle.containsKey("durationMs")) metaBundle.getLong("durationMs") else null
            val seasonNumber = if (metaBundle.containsKey("seasonNumber")) metaBundle.getInt("seasonNumber") else null
            val episodeNumber = if (metaBundle.containsKey("episodeNumber")) metaBundle.getInt("episodeNumber") else null
            PlaybackItemMetadata(
                title = metaBundle.getString("title") ?: "",
                description = metaBundle.getString("description"),
                posterUrl = metaBundle.getString("posterUrl"),
                backdropUrl = metaBundle.getString("backdropUrl"),
                durationMs = durationMs,
                isLive = metaBundle.getBoolean("isLive"),
                seriesName = metaBundle.getString("seriesName"),
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                genre = metaBundle.getString("genre"),
                containerFormat = metaBundle.getString("containerFormat"),
                videoCodec = metaBundle.getString("videoCodec"),
                audioCodec = metaBundle.getString("audioCodec")
            )
        } else null

        val refBundle = bundle.getBundle("userMemoryReference")
        val userMemoryReference = if (refBundle != null) {
            val contentType = runCatching { UserMemoryContentType.valueOf(refBundle.getString("contentType") ?: "MOVIE") }
                .getOrDefault(UserMemoryContentType.MOVIE)
            UserMemoryReference(
                itemKey = refBundle.getString("itemKey") ?: "",
                contentType = contentType,
                title = refBundle.getString("title") ?: "",
                subtitle = refBundle.getString("subtitle"),
                providerId = refBundle.getString("providerId"),
                sourceId = refBundle.getString("sourceId")
            )
        } else null

        val seriesBundle = bundle.getBundle("seriesContext")
        val seriesContext = if (seriesBundle != null) {
            com.example.calmsource.core.model.SeriesPlaybackContext(
                seriesId = seriesBundle.getString("seriesId") ?: "",
                seriesTitle = seriesBundle.getString("seriesTitle") ?: "",
                posterUrl = seriesBundle.getString("posterUrl"),
                backdropUrl = seriesBundle.getString("backdropUrl"),
            )
        } else null

        PlaybackRequest(
            source = PlaybackSource(
                id = id,
                type = type,
                title = title,
                rawUrl = rawUrl,
                displayUrl = displayUrl,
                metadata = metadata,
                headers = headers,
                allowInsecureHttp = allowInsecureHttp
            ),
            startPositionMs = startPositionMs,
            playWhenReady = playWhenReady,
            userMemoryReference = userMemoryReference,
            seriesContext = seriesContext,
        )
    }
)
