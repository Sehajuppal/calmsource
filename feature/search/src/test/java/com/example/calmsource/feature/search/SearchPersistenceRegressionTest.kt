package com.example.calmsource.feature.search

import com.example.calmsource.core.model.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.example.calmsource.feature.extensions.ExtensionRepository

class SearchPersistenceRegressionTest {

    @Before
    fun setUp() {
        SearchResultRanker.invalidateLookupCaches()
    }

    @After
    fun tearDown() = runBlocking {
        ExtensionRepository.confirmInstall(
            ExtensionManifest(id = "ext-torrentio", name = "Torrentio Addon", description = "Torrent stream provider", version = "1.0.0", resources = listOf("stream"), types = listOf("movie", "series")),
            "https://torrentio.strem.io/manifest.json"
        )
        ExtensionRepository.confirmInstall(
            ExtensionManifest(id = "ext-aiostreams", name = "AIOStreams Aggregator", description = "Stream aggregator provider", version = "1.0.0", resources = listOf("stream"), types = listOf("movie", "series")),
            "https://aiostreams.net/manifest.json"
        )
        ExtensionRepository.confirmInstall(
            ExtensionManifest(id = "ext-slow", name = "Slow Catalog Addon", description = "Slow catalog provider", version = "1.0.0", resources = listOf("catalog", "stream"), types = listOf("movie", "series")),
            "https://slowaddon.org/manifest.json"
        )
        ExtensionRepository.confirmInstall(
            ExtensionManifest(id = "ext-failed", name = "Failed Scraper Engine", description = "Failed scraper provider", version = "1.0.0", resources = listOf("stream"), types = listOf("movie", "series")),
            "https://failedaddon.com/manifest.json"
        )
        ExtensionRepository.toggleExtension("ext-torrentio", true)
        ExtensionRepository.toggleExtension("ext-aiostreams", true)
        ExtensionRepository.toggleExtension("ext-slow", true)
        ExtensionRepository.toggleExtension("ext-failed", true)
        ExtensionRepository.updatePriority("ext-torrentio", 10)
        ExtensionRepository.updatePriority("ext-aiostreams", 20)
        ExtensionRepository.updatePriority("ext-slow", 30)
        ExtensionRepository.updatePriority("ext-failed", 40)
        ExtensionRepository.updateHealth("ext-slow", ExtensionHealth.SLOW)
        ExtensionRepository.updateHealth("ext-failed", ExtensionHealth.FAILED)
        SearchResultRanker.invalidateLookupCaches()
    }

    @Test
    fun testPersistedProviderPrioritiesAffectRanking() = runBlocking {
        // Add fake extensions to repository so they have priorities
        val manifest1 = ExtensionManifest(
            id = "ext-torrentio", name = "Torrentio", version = "1.0.0", description = "A"
        )
        val manifest2 = ExtensionManifest(
            id = "ext-aiostreams", name = "AIOStreams", version = "1.0.0", description = "B"
        )
        ExtensionRepository.confirmInstall(manifest1, "http://torrentio.test")
        ExtensionRepository.confirmInstall(manifest2, "http://aio.test")
        
        // Let state flow propagate
        kotlinx.coroutines.delay(10)

        val prefs = FakeData.defaultPreferences
        
        // Let's create two mock sources with different extensions
        val sourceExt1 = StreamSource("src1", "Src 1", "", "ext-torrentio", "1080p", language = "English", videoCodec = "AVC", audioCodec = "AAC")
        val sourceExt2 = StreamSource("src2", "Src 2", "", "ext-aiostreams", "1080p", language = "English", videoCodec = "AVC", audioCodec = "AAC")

        // Priority 10 for ext-torrentio, Priority 20 for ext-aiostreams
        val score1 = SearchResultRanker.calculateSourceScore(sourceExt1, prefs)
        val score2 = SearchResultRanker.calculateSourceScore(sourceExt2, prefs)

        assertTrue("Torrentio (priority 10) should outrank AIOStreams (priority 20). Score1: $score1, Score2: $score2", score1 > score2)
    }

    @Test
    fun testPersistedDisabledExtensionsDoNotAppear() = runBlocking {
        // Disable torrentio
        ExtensionRepository.toggleExtension("ext-torrentio", false)

        val engine = UniversalSearchEngineImpl(providers = listOf(ExtensionSearchProviderImpl()))
        val flowList = engine.search("Spider-Man", FakeData.defaultPreferences).toList()
        
        val mergedGroups = flowList.last()
        val extGroup = mergedGroups.firstOrNull { it.groupType == SearchGroupType.EXTENSION_RESULTS }
        
        if (extGroup != null) {
            val hasTorrentio = extGroup.results.any { res ->
                res.watchOptions.any { it.source.extensionId == "ext-torrentio" }
            }
            assertFalse("Disabled extension (ext-torrentio) should not appear in search results", hasTorrentio)
        }

        // Re-enable for other tests
        ExtensionRepository.toggleExtension("ext-torrentio", true)
    }

    @Test
    fun testPersistedUnhealthyProvidersAreDownranked() = runBlocking {
        val manifestSlow = ExtensionManifest(
            id = "ext-slow", name = "SlowExt", version = "1.0", description = "S"
        )
        val manifestFailed = ExtensionManifest(
            id = "ext-failed", name = "FailedExt", version = "1.0", description = "F"
        )
        ExtensionRepository.confirmInstall(manifestSlow, "http://slow.test")
        ExtensionRepository.confirmInstall(manifestFailed, "http://failed.test")
        
        // Let state flow propagate
        kotlinx.coroutines.delay(10)

        val extSlow = ExtensionRepository.extensions.value.first { it.id == "ext-slow" }
        val extFailed = ExtensionRepository.extensions.value.first { it.id == "ext-failed" }
        
        ExtensionRepository.updateHealth(extSlow.id, ExtensionHealth.SLOW)
        ExtensionRepository.updateHealth(extFailed.id, ExtensionHealth.FAILED)

        // Let state flow propagate
        kotlinx.coroutines.delay(10)

        val prefs = FakeData.defaultPreferences
        val healthySource = StreamSource("src1", "Healthy", "", "ext-torrentio", "1080p", language = "English", videoCodec = "AVC", audioCodec = "AAC")
        val slowSource = StreamSource("src2", "Slow", "", "ext-slow", "1080p", language = "English", videoCodec = "AVC", audioCodec = "AAC")
        val failedSource = StreamSource("src3", "Failed", "", "ext-failed", "1080p", language = "English", videoCodec = "AVC", audioCodec = "AAC")

        val scoreHealthy = SearchResultRanker.calculateSourceScore(healthySource, prefs)
        val scoreSlow = SearchResultRanker.calculateSourceScore(slowSource, prefs)
        val scoreFailed = SearchResultRanker.calculateSourceScore(failedSource, prefs)

        assertTrue("Healthy should outrank slow ($scoreHealthy > $scoreSlow)", scoreHealthy > scoreSlow)
        assertTrue("Slow should outrank failed ($scoreSlow > $scoreFailed)", scoreSlow > scoreFailed)
    }
}
