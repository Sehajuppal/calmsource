package com.example.calmsource.feature.extensions

import com.example.calmsource.core.model.ExtensionManifest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ExtensionManifestCacheTest {

    @Test
    fun `Verify cache timestamp and refresh behavior`() = runTest {
        val cache = ExtensionManifestCache(ttlMillis = 1000L)
        val manifest = ExtensionManifest(id = "test", name = "Test")
        val url = "https://example.com/manifest.json"

        cache.put(url, manifest, currentTime = 100L)
        
        // Within TTL
        val cached1 = cache.get(url, currentTime = 500L)
        assertNotNull("Should return manifest within TTL", cached1)
        
        // Past TTL
        val cached2 = cache.get(url, currentTime = 1200L)
        assertNull("Should return null past TTL to trigger refresh", cached2)
    }

    @Test
    fun `Verify stale or failed cache behavior`() = runTest {
        val cache = ExtensionManifestCache(ttlMillis = 1000L, staleMillis = 5000L)
        val manifest = ExtensionManifest(id = "test", name = "Test")
        val url = "https://example.com/manifest.json"

        cache.put(url, manifest, currentTime = 100L)
        
        // Past TTL, but within stale limit
        val cached1 = cache.get(url, currentTime = 1200L)
        assertNull("get() should return null past TTL", cached1)
        
        val stale1 = cache.getStale(url, currentTime = 1200L)
        assertNotNull("getStale() should return manifest if within stale limit", stale1)
        
        // Past stale limit
        val stale2 = cache.getStale(url, currentTime = 6000L)
        assertNull("getStale() should return null past stale limit", stale2)
    }

    @Test
    fun `Verify cache eviction when max entries exceeded`() = runTest {
        val cache = ExtensionManifestCache(ttlMillis = 10000L, maxEntries = 3)
        
        cache.put("https://a.com/manifest.json", ExtensionManifest(id = "a", name = "A"), currentTime = 100L)
        cache.put("https://b.com/manifest.json", ExtensionManifest(id = "b", name = "B"), currentTime = 200L)
        cache.put("https://c.com/manifest.json", ExtensionManifest(id = "c", name = "C"), currentTime = 300L)
        
        assertEquals(3, cache.size())
        
        // Adding a 4th entry should evict the oldest (a)
        cache.put("https://d.com/manifest.json", ExtensionManifest(id = "d", name = "D"), currentTime = 400L)
        
        assertEquals(3, cache.size())
        assertNull("Oldest entry 'a' should be evicted", cache.get("https://a.com/manifest.json", currentTime = 400L))
        assertNotNull("Entry 'b' should still exist", cache.get("https://b.com/manifest.json", currentTime = 400L))
        assertNotNull("Entry 'd' should exist", cache.get("https://d.com/manifest.json", currentTime = 400L))
    }

    @Test
    fun `Verify stale getStale evicts entries past stale limit`() = runTest {
        val cache = ExtensionManifestCache(ttlMillis = 1000L, staleMillis = 5000L)
        val manifest = ExtensionManifest(id = "test", name = "Test")
        val url = "https://example.com/manifest.json"

        cache.put(url, manifest, currentTime = 100L)
        assertEquals(1, cache.size())
        
        // Accessing past stale limit should evict
        val stale = cache.getStale(url, currentTime = 6000L)
        assertNull(stale)
        assertEquals(0, cache.size())
    }
}
