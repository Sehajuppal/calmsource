package com.example.calmsource.feature.extensions

import com.example.calmsource.core.model.ExtensionManifest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory cache for extension manifests to avoid redundant network fetching.
 *
 * Implements a time-to-live (TTL) cache.
 */
class ExtensionManifestCache(
    private val ttlMillis: Long = 3600_000L, // 1 hour
    private val staleMillis: Long = 86400_000L, // 24 hours
    private val maxEntries: Int = 100
) {
    private data class CacheEntry(
        val manifest: ExtensionManifest,
        val timestamp: Long
    )

    private val cache = mutableMapOf<String, CacheEntry>()
    private val mutex = Mutex()

    /**
     * Gets a manifest from cache if it is not expired.
     */
    suspend fun get(url: String, currentTime: Long = System.currentTimeMillis()): ExtensionManifest? = mutex.withLock {
        val entry = cache[cacheKey(url)] ?: return null
        if (currentTime - entry.timestamp > ttlMillis) {
            return null
        }
        return entry.manifest
    }
    
    suspend fun getStale(url: String, currentTime: Long = System.currentTimeMillis()): ExtensionManifest? = mutex.withLock {
        val key = cacheKey(url)
        val entry = cache[key] ?: return null
        if (currentTime - entry.timestamp > staleMillis) {
            cache.remove(key) // Evict entries that are too stale to ever use
            return null // Too stale to use
        }
        return entry.manifest
    }

    suspend fun put(url: String, manifest: ExtensionManifest, currentTime: Long = System.currentTimeMillis()) = mutex.withLock {
        cache[cacheKey(url)] = CacheEntry(manifest, currentTime)
        // Evict oldest entry if cache exceeds max size
        if (cache.size > maxEntries) {
            val oldest = cache.entries.minByOrNull { it.value.timestamp }
            if (oldest != null) {
                cache.remove(oldest.key)
            }
        }
    }

    suspend fun clear() = mutex.withLock {
        cache.clear()
    }

    /** Returns current cache size (for testing). */
    suspend fun size(): Int = mutex.withLock { cache.size }

    private fun cacheKey(url: String): String {
        return runCatching {
            val uri = java.net.URI(url.trim()).normalize()
            java.net.URI(
                uri.scheme?.lowercase(),
                uri.rawAuthority?.lowercase(),
                uri.rawPath?.trimEnd('/') ?: "",
                uri.rawQuery,
                null
            ).toString()
        }.getOrElse {
            url.trim().trimEnd('/')
        }
    }
}
