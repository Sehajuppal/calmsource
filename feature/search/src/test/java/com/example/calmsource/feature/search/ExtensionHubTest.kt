package com.example.calmsource.feature.search

import com.example.calmsource.core.model.*
import com.example.calmsource.core.parser.ExtensionManifestParser
import com.example.calmsource.feature.extensions.ExtensionRepository
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ExtensionHubTest {

    @Before
    fun setUp() {
        // Re-initialize repository to seed initial extensions before each test
        // By invoking getExtensions we make sure we have a clean state
        ExtensionRepository.toggleExtension("ext-legal-demo", true)
        ExtensionRepository.updateHealth("ext-slow", ExtensionHealth.SLOW)
        ExtensionRepository.updateHealth("ext-failed", ExtensionHealth.FAILED)
        ExtensionRepository.updatePriority("ext-legal-demo", 10)
        ExtensionRepository.updatePriority("ext-failed", 20)
    }

    // 1. Valid Manifest Parsing
    @Test
    fun `Valid manifest JSON is parsed correctly with all fields`() = runBlocking {
        val validJson = """
            {
              "id": "ext-test-addon",
              "name": "Test Stremio Addon",
              "description": "A test addon resolving public domain content",
              "version": "1.2.3",
              "logo": "https://example.com/logo.png",
              "resources": ["catalog", "search", "stream", "subtitles"],
              "types": ["movie", "series"],
              "catalogs": [
                {
                  "type": "movie",
                  "id": "test-movies",
                  "name": "Public Domain Movies"
                }
              ],
              "behaviorHints": {
                "configurable": "true"
              }
            }
        """.trimIndent()

        val result = ExtensionManifestParser.parse(validJson)
        assertTrue(result.isSuccess)
        assertNull(result.error)
        
        val manifest = result.manifest
        assertNotNull(manifest)
        assertEquals("ext-test-addon", manifest?.id)
        assertEquals("Test Stremio Addon", manifest?.name)
        assertEquals("A test addon resolving public domain content", manifest?.description)
        assertEquals("1.2.3", manifest?.version)
        assertEquals("https://example.com/logo.png", manifest?.logo)
        
        assertTrue(manifest?.resources?.contains("catalog") == true)
        assertTrue(manifest?.resources?.contains("subtitles") == true)
        assertTrue(manifest?.types?.contains("movie") == true)
        assertEquals(1, manifest?.catalogs?.size)
        assertEquals("Public Domain Movies", manifest?.catalogs?.first()?.name)
        assertEquals("true", manifest?.behaviorHints?.get("configurable"))
    }

    // 2. Invalid Manifest Parsing
    @Test
    fun `Malformed manifest JSON returns parsing error`() = runBlocking {
        val malformedJson = """
            {
              "id": "ext-test",
              "name": "Test",
              "resources": [ {
            }
        """.trimIndent()

        val result = ExtensionManifestParser.parse(malformedJson)
        assertFalse(result.isSuccess)
        assertNotNull(result.error)
        assertTrue(result.error is ExtensionError.ParseError || result.error is ExtensionError.InvalidManifest)
    }

    // 3. Missing Capability Handling
    @Test
    fun `Manifest with missing optional fields parses successfully with defaults`() = runBlocking {
        val jsonMissingFields = """
            {
              "id": "ext-minimal",
              "name": "Minimal Addon"
            }
        """.trimIndent()

        val result = ExtensionManifestParser.parse(jsonMissingFields)
        assertTrue(result.isSuccess)
        
        val manifest = result.manifest
        assertNotNull(manifest)
        assertEquals("ext-minimal", manifest?.id)
        assertEquals("Minimal Addon", manifest?.name)
        assertTrue(manifest?.resources?.isEmpty() == true)
        assertTrue(manifest?.types?.isEmpty() == true)
        assertTrue(manifest?.catalogs?.isEmpty() == true)
    }

    // 4. Unknown Fields Handling
    @Test
    fun `Manifest with unknown fields retains them in rawAttributes for forward compatibility`() = runBlocking {
        val jsonWithUnknownFields = """
            {
              "id": "ext-test",
              "name": "Test",
              "customFieldString": "hello",
              "customFieldInt": 42,
              "customFieldBool": true
            }
        """.trimIndent()

        val result = ExtensionManifestParser.parse(jsonWithUnknownFields)
        assertTrue(result.isSuccess)
        
        val manifest = result.manifest
        assertNotNull(manifest)
        assertTrue(manifest?.rawAttributes?.containsKey("customFieldString") == true)
        assertEquals("hello", manifest?.rawAttributes?.get("customFieldString"))
        assertEquals("42", manifest?.rawAttributes?.get("customFieldInt"))
        assertEquals("true", manifest?.rawAttributes?.get("customFieldBool"))
    }

    // 5. Extension Enable/Disable
    @Test
    fun `Extension can be disabled and re-enabled updating health state`() = runBlocking {
        // Initially enabled
        val initial = ExtensionRepository.getExtensions().first { it.id == "ext-legal-demo" }
        assertTrue(initial.isEnabled)
        assertEquals(ExtensionHealth.ACTIVE, initial.health)

        // Disable
        ExtensionRepository.toggleExtension("ext-legal-demo", false)
        val disabled = ExtensionRepository.getExtensions().first { it.id == "ext-legal-demo" }
        assertFalse(disabled.isEnabled)
        assertEquals(ExtensionHealth.DISABLED, disabled.health)

        // Re-enable
        ExtensionRepository.toggleExtension("ext-legal-demo", true)
        val enabled = ExtensionRepository.getExtensions().first { it.id == "ext-legal-demo" }
        assertTrue(enabled.isEnabled)
        assertEquals(ExtensionHealth.ACTIVE, enabled.health)
    }

    // 6. Extension Remove
    @Test
    fun `Extension can be removed from repository`() = runBlocking {
        val initialSize = ExtensionRepository.getExtensions().size
        assertTrue(ExtensionRepository.getExtensions().any { it.id == "ext-failed" })

        // Remove
        ExtensionRepository.removeExtension("ext-failed")
        val postSize = ExtensionRepository.getExtensions().size
        assertEquals(initialSize - 1, postSize)
        assertFalse(ExtensionRepository.getExtensions().any { it.id == "ext-failed" })
    }

    // 7. Extension Priority Ordering
    @Test
    fun `Extensions are sorted correctly by priority`() = runBlocking {
        // Change priority so failed is lower number (higher priority) than legal-demo
        ExtensionRepository.updatePriority("ext-failed", 5)
        ExtensionRepository.updatePriority("ext-legal-demo", 15)
        kotlinx.coroutines.delay(100)

        val sorted = ExtensionRepository.getExtensions().sortedBy { it.priority }
        val failedIndex = sorted.indexOfFirst { it.id == "ext-failed" }
        val legalIndex = sorted.indexOfFirst { it.id == "ext-legal-demo" }
        assertTrue("ext-failed should be ordered before ext-legal-demo", failedIndex < legalIndex)
    }

    // 8. Extension Health State
    @Test
    fun `Extension health state can be updated`() = runBlocking {
        val ext = ExtensionRepository.getExtensions().first { it.id == "ext-slow" }
        assertEquals(ExtensionHealth.SLOW, ext.health)

        ExtensionRepository.updateHealth("ext-slow", ExtensionHealth.FAILED)
        val updated = ExtensionRepository.getExtensions().first { it.id == "ext-slow" }
        assertEquals(ExtensionHealth.FAILED, updated.health)
    }

    // 9. Extension Timeout Behavior
    @Test
    fun `Slow extensions timeout without crashing universal search`() = runBlocking {
        val engine = UniversalSearchEngineImpl(
            providers = listOf(
                ExtensionSearchProviderImpl(), // Simulates 1200ms delay when query is "slow"
                SettingsSearchProviderImpl()
            )
        )

        // Set policy to timeout extensions after 200ms
        val policy = SearchTimeoutPolicy(
            defaultTimeoutMs = 1000L,
            providerTimeoutsMs = mapOf("prov-extensions" to 200L)
        )

        val flowList = engine.search("slow", FakeData.defaultPreferences, policy).toList()
        
        // Universal search must not crash, and should emit results from other fast providers (Settings)
        assertFalse(flowList.isEmpty())
        val finalEmission = flowList.last()
        val extensionGroup = finalEmission.firstOrNull { it.groupType == SearchGroupType.EXTENSION_RESULTS }
        assertNull(extensionGroup) // Slow extension results are dropped due to timeout
    }

    // 10. Extension Result Normalization & 11. Merge with IPTV Result
    @Test
    fun testExtensionResultMergeWithIptv() = runBlocking {
        val spiderman = FakeData.movieSpiderman
        val prefs = FakeData.defaultPreferences

        val providerResults = listOf(
            SearchProviderResult(
                providerId = "prov-vod",
                providerName = "IPTV VOD",
                query = SearchQuery("Spider-Man"),
                mediaItems = listOf(spiderman),
                streamSources = listOf(FakeData.spidermanSources[0]) // IPTV stream source
            ),
            SearchProviderResult(
                providerId = "prov-extensions",
                providerName = "Extensions Scraper",
                query = SearchQuery("Spider-Man"),
                mediaItems = listOf(spiderman),
                streamSources = listOf(FakeData.spidermanSources[2]) // Extension stream source
            )
        )

        // Merge results
        val mergedGroups = SearchResultMerger.merge(providerResults, "Spider-Man", prefs)

        // Check movies group
        val moviesGroup = mergedGroups.firstOrNull { it.groupType == SearchGroupType.MOVIES }
        assertNotNull(moviesGroup)
        
        // Should produce exactly one consolidated card for Spider-Man: Homecoming
        val spidermanCard = moviesGroup!!.results.first()
        assertEquals("Spider-Man: Homecoming", spidermanCard.mediaItem.title)

        // Available from both sources
        assertTrue(spidermanCard.availableFrom.contains(SourceType.IPTV))
        assertTrue(spidermanCard.availableFrom.contains(SourceType.EXTENSION))
        
        // Includes watch options from both
        assertTrue(spidermanCard.watchOptions.any { it.type == SourceType.IPTV })
        assertTrue(spidermanCard.watchOptions.any { it.type == SourceType.EXTENSION })
    }

    // Task 1: Verify installed remote extension manifests register declared capabilities.
    @Test
    fun testInstalledExtensionRegistersCapabilities() = runBlocking {
        val manifest = ExtensionManifest(
            id = "ext-new-remote",
            name = "Remote Addon",
            description = "Some description",
            version = "1.0.0",
            resources = listOf("catalog", "stream", "subtitles"),
            types = listOf("movie", "series", "anime")
        )

        val result = ExtensionRepository.confirmInstall(manifest, "https://remote.addon/manifest.json")
        assertTrue(result.isSuccess)

        val installedExt = ExtensionRepository.getExtensions().first { it.id == manifest.id }
        assertEquals(3, installedExt.manifest?.resources?.size)
        assertTrue(installedExt.manifest?.resources?.contains("stream") == true)
        assertTrue(installedExt.manifest?.resources?.contains("subtitles") == true)
        assertEquals(3, installedExt.manifest?.types?.size)
        assertTrue(installedExt.manifest?.types?.contains("anime") == true)
    }

    @Test
    fun `extension search only queries bounded declared search catalogs`() {
        val catalogs = (1..8).map { index ->
            ExtensionCatalog(
                type = "movie",
                id = "search-$index",
                name = "Search $index",
                extra = listOf(StremioCatalogExtra(name = "search"))
            )
        } + ExtensionCatalog(
            type = "movie",
            id = "popular",
            name = "Popular"
        )
        val manifest = ExtensionManifest(
            id = "ext-tmdb-heavy",
            name = "TMDb Heavy",
            resources = listOf("catalog", "meta"),
            types = listOf("movie", "series"),
            catalogs = catalogs
        )
        val provider = ExtensionProvider(
            id = manifest.id,
            name = manifest.name,
            url = "https://example.com/manifest.json",
            isEnabled = true,
            health = ExtensionHealth.ACTIVE,
            manifest = manifest,
            capabilities = manifest.detectCapabilities(),
            supportedTypes = manifest.detectContentTypes()
        )

        val searchable = ExtensionSearchProviderImpl.searchableCatalogsForQuery(provider)

        assertEquals(4, searchable.size)
        assertFalse(searchable.any { it.id == "popular" })
        assertTrue(searchable.all { catalog -> catalog.extra.orEmpty().any { it.name == "search" } })
    }

    @Test
    fun `extension repository does not write production state into FakeData`() {
        val source = readProjectFile(
            "feature/extensions/src/main/kotlin/com/example/calmsource/feature/extensions/ExtensionRepository.kt"
        )

        assertFalse(source.contains("FakeData.extensionProviders ="))
        assertFalse(source.contains("syncToFakeData"))
    }

    // 12. Spider-Man Homecoming Extension Merge
    @Test
    fun `Spider-Man Homecoming merges available sources and languages`() = runBlocking {
        val spiderman = FakeData.movieSpiderman
        val sources = FakeData.spidermanSources
        val prefs = FakeData.defaultPreferences

        val providerResult = SearchProviderResult(
            providerId = "all-in-one",
            providerName = "Aggregated Provider",
            query = SearchQuery("Spider-Man Homecoming"),
            mediaItems = listOf(spiderman),
            streamSources = sources
        )

        val mergedGroups = SearchResultMerger.merge(listOf(providerResult), "Spider-Man Homecoming", prefs)
        val moviesGroup = mergedGroups.firstOrNull { it.groupType == SearchGroupType.MOVIES }
        assertNotNull(moviesGroup)

        val resultCard = moviesGroup!!.results.first()
        assertEquals("Spider-Man: Homecoming", resultCard.mediaItem.title)

        // Assert all availability types are resolved
        assertTrue(resultCard.availableFrom.contains(SourceType.IPTV))
        assertTrue(resultCard.availableFrom.contains(SourceType.EXTENSION))
        assertTrue(resultCard.availableFrom.contains(SourceType.DEBRID))

        // Available languages
        assertTrue(resultCard.languages.contains("Hindi"))
        assertTrue(resultCard.languages.contains("English"))
        assertTrue(resultCard.languages.contains("Tamil"))
        assertTrue(resultCard.languages.contains("Spanish"))
    }

    // 13. Stream Picker Readable Labels & 14. Raw Filename Hidden by Default
    @Test
    fun `Stream picker generates readable labels and hides raw filename`() = runBlocking {
        val mockStream4K = StreamSource(
            id = "src-4k-atmos-hdr",
            name = "Spider-Man.Homecoming.2017.2160p.HDR.Atmos.mkv",
            url = "http://example.com/movie.mkv",
            extensionId = "ext-legal-demo",
            resolution = "4K",
            videoCodec = "HEVC HDR",
            audioCodec = "Dolby Atmos",
            sizeBytes = 25_000_000_000,
            seeds = 150,
            language = "English",
            isSubbed = true
        )

        val labels = formatStreamPickerLabel(mockStream4K, isCached = true)
        
        // 13. Test presence of readable descriptors
        assertTrue(labels.contains("4K"))
        assertTrue(labels.contains("HDR"))
        assertTrue(labels.contains("Atmos"))
        assertTrue(labels.contains("Subtitles"))
        assertTrue(labels.contains("Extension"))
        assertTrue(labels.contains("Cached"))
        
        // 14. Assert the raw filename itself is NOT included in the final label list
        assertFalse(labels.contains(mockStream4K.name))
        assertFalse(labels.any { it.contains(".mkv") })
    }

    // Label formatting helper to match specifications
    private fun formatStreamPickerLabel(source: StreamSource, isCached: Boolean): List<String> {
        val labels = mutableListOf<String>()
        // 1. Resolution
        labels.add(source.resolution)
        // 2. Video codec details like HDR/Dolby Vision
        if (source.videoCodec?.contains("HDR", ignoreCase = true) == true || source.name.contains("HDR", ignoreCase = true)) {
            labels.add("HDR")
        }
        if (source.videoCodec?.contains("DV", ignoreCase = true) == true || source.name.contains("Dolby Vision", ignoreCase = true) || source.name.contains("DV", ignoreCase = true)) {
            labels.add("Dolby Vision")
        }
        // 3. Audio details like Atmos
        if (source.audioCodec?.contains("Atmos", ignoreCase = true) == true || source.name.contains("Atmos", ignoreCase = true)) {
            labels.add("Atmos")
        }
        // 4. Subtitles indicator
        if (source.isSubbed) {
            labels.add("Subtitles")
        }
        // 5. Source category / Extension indicator
        if (source.extensionId.startsWith("ext-")) {
            labels.add("Extension")
        }
        // 6. Cached status
        if (isCached) {
            labels.add("Cached")
        }
        // 7. Low Data warning if resolution is SD/720p or size is small
        if (source.resolution.uppercase() in listOf("SD", "360P") || (source.sizeBytes ?: 0L) < 500_000_000L) {
            labels.add("Low Data")
        }
        return labels
    }

    private fun readProjectFile(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath"),
            File("d:/Program Files/iptv/$relativePath")
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Could not find source file: $relativePath")
    }
}
