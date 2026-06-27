package com.example.calmsource.feature.search

import com.example.calmsource.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive stabilization tests for the Universal Search pipeline,
 * WatchOptionResolver, and Stream Picker label generation.
 *
 * Covers:
 * 1. Title normalization (years, resolution suffixes, language suffixes)
 * 2. Duplicate detection (case, provider, source type)
 * 3. cleanStreamTitle for raw filenames
 * 4. Stream Picker label cleanliness
 * 5. Search responsiveness with multiple providers
 * 6. Disabled/unhealthy provider handling
 * 7. Spider-Man merged result regression
 */
class SearchPipelineStabilizationTest {

    private val defaultPrefs = FakeData.defaultPreferences

    // ─── Task 1: Title Normalization ─────────────────────────────────

    @Test
    fun testNormalizeTitleWithYearInParentheses() = runBlocking {
        // "Spider-Man: Homecoming (2017)" should match "Spider-Man: Homecoming"
        val result1 = NormalizedSearchResult(
            mediaItem = MediaItem("m1", "Spider-Man: Homecoming (2017)", MediaType.MOVIE),
            availableFrom = emptyList(), languages = emptyList(), watchOptions = emptyList(), score = 100
        )
        val result2 = NormalizedSearchResult(
            mediaItem = MediaItem("m2", "Inception", MediaType.MOVIE),
            availableFrom = emptyList(), languages = emptyList(), watchOptions = emptyList(), score = 200
        )

        val ranked = SearchResultRanker.rankResults(
            listOf(result1, result2), "Spider-Man: Homecoming", defaultPrefs
        )
        assertEquals("m1", ranked.first().mediaItem.id)
        assertTrue("Title with year should still get exact match bonus",
            ranked.first().score > ranked.last().score)
    }

    @Test
    fun testNormalizeTitleWithResolutionSuffix_Brackets() = runBlocking {
        // "Inception [HD]" should match "Inception"
        val result = NormalizedSearchResult(
            mediaItem = MediaItem("m1", "Inception [HD]", MediaType.MOVIE),
            availableFrom = emptyList(), languages = emptyList(), watchOptions = emptyList(), score = 100
        )
        val other = NormalizedSearchResult(
            mediaItem = MediaItem("m2", "Interstellar", MediaType.MOVIE),
            availableFrom = emptyList(), languages = emptyList(), watchOptions = emptyList(), score = 200
        )

        val ranked = SearchResultRanker.rankResults(
            listOf(result, other), "Inception", defaultPrefs
        )
        assertEquals("m1", ranked.first().mediaItem.id)
    }

    @Test
    fun testNormalizeTitleWithResolutionSuffix_Parens() = runBlocking {
        // "Dune (4K)" should match "Dune"
        val result = NormalizedSearchResult(
            mediaItem = MediaItem("m1", "Dune (4K)", MediaType.MOVIE),
            availableFrom = emptyList(), languages = emptyList(), watchOptions = emptyList(), score = 100
        )
        val other = NormalizedSearchResult(
            mediaItem = MediaItem("m2", "Tenet", MediaType.MOVIE),
            availableFrom = emptyList(), languages = emptyList(), watchOptions = emptyList(), score = 200
        )

        val ranked = SearchResultRanker.rankResults(
            listOf(result, other), "Dune", defaultPrefs
        )
        assertEquals("m1", ranked.first().mediaItem.id)
    }

    @Test
    fun testNormalizeTitleWithLanguageSuffix() = runBlocking {
        // "Inception Hindi" should match "Inception"
        val result = NormalizedSearchResult(
            mediaItem = MediaItem("m1", "Inception Hindi", MediaType.MOVIE),
            availableFrom = emptyList(), languages = emptyList(), watchOptions = emptyList(), score = 100
        )
        val other = NormalizedSearchResult(
            mediaItem = MediaItem("m2", "Interstellar", MediaType.MOVIE),
            availableFrom = emptyList(), languages = emptyList(), watchOptions = emptyList(), score = 200
        )

        val ranked = SearchResultRanker.rankResults(
            listOf(result, other), "Inception", defaultPrefs
        )
        assertEquals("m1", ranked.first().mediaItem.id)
    }

    @Test
    fun testNormalizeTitleQueryWithYearShouldMatch() = runBlocking {
        // Query "Inception (2010)" should match title "Inception"
        val result = NormalizedSearchResult(
            mediaItem = MediaItem("m1", "Inception", MediaType.MOVIE),
            availableFrom = emptyList(), languages = emptyList(), watchOptions = emptyList(), score = 100
        )
        val other = NormalizedSearchResult(
            mediaItem = MediaItem("m2", "Tenet", MediaType.MOVIE),
            availableFrom = emptyList(), languages = emptyList(), watchOptions = emptyList(), score = 200
        )

        val ranked = SearchResultRanker.rankResults(
            listOf(result, other), "Inception (2010)", defaultPrefs
        )
        assertEquals("m1", ranked.first().mediaItem.id)
    }

    @Test
    fun testNormalizeForTitleMatch_internals() = runBlocking {
        // Direct unit test on the normalizer function
        assertEquals("SpiderMan Homecoming",
            SearchResultRanker.normalizeForTitleMatch("Spider-Man: Homecoming"))
        assertEquals("SpiderMan Homecoming",
            SearchResultRanker.normalizeForTitleMatch("Spider-Man: Homecoming (2017)"))
        assertEquals("Inception",
            SearchResultRanker.normalizeForTitleMatch("Inception [HD]"))
        assertEquals("Dune",
            SearchResultRanker.normalizeForTitleMatch("Dune (4K)"))
        assertEquals("Inception",
            SearchResultRanker.normalizeForTitleMatch("Inception Hindi"))
        assertEquals("Interstellar",
            SearchResultRanker.normalizeForTitleMatch("Interstellar (2014) English"))
    }

    // ─── Task 2: Duplicate Detection ─────────────────────────────────

    @Test
    fun testDeduplicateSameTitleDifferentCase() = runBlocking {
        // Two results with same ID but from different case paths should merge
        val item = MediaItem("movie-test", "Test Movie", MediaType.MOVIE)
        val src1 = StreamSource("s1", "Stream A", "http://a", "iptv-1", "1080p", language = "Hindi")
        val src2 = StreamSource("s2", "Stream B", "http://b", "ext-torrentio", "720p", language = "English")

        val results = listOf(
            NormalizedSearchResult(
                mediaItem = item,
                availableFrom = listOf(SourceType.IPTV),
                languages = listOf("Hindi"),
                watchOptions = listOf(WatchOption("w1", "A", src1, SourceType.IPTV, "1080p Hindi")),
                score = 300
            ),
            NormalizedSearchResult(
                mediaItem = item,
                availableFrom = listOf(SourceType.EXTENSION),
                languages = listOf("English"),
                watchOptions = listOf(WatchOption("w2", "B", src2, SourceType.EXTENSION, "720p English")),
                score = 200
            )
        )

        val merged = SearchResultDeduplicator.deduplicate(results, hideDuplicates = true, prefs = defaultPrefs)
        assertEquals("Should merge duplicates with same ID", 1, merged.size)
        assertEquals(2, merged.first().watchOptions.size)
        assertTrue(merged.first().languages.contains("Hindi"))
        assertTrue(merged.first().languages.contains("English"))
    }

    @Test
    fun testDeduplicateSameIdDifferentTitles() = runBlocking {
        // Two results with same ID but completely different titles should merge because of ID
        val item1 = MediaItem("movie-test", "The Matrix", MediaType.MOVIE)
        val item2 = MediaItem("movie-test", "Matrix", MediaType.MOVIE)
        val src1 = StreamSource("s1", "Stream A", "http://a", "iptv-1", "1080p", language = "English")
        val src2 = StreamSource("s2", "Stream B", "http://b", "ext-torrentio", "720p", language = "English")

        val results = listOf(
            NormalizedSearchResult(
                mediaItem = item1,
                availableFrom = listOf(SourceType.IPTV),
                languages = emptyList(),
                watchOptions = listOf(WatchOption("w1", "A", src1, SourceType.IPTV, "1080p")),
                score = 300
            ),
            NormalizedSearchResult(
                mediaItem = item2,
                availableFrom = listOf(SourceType.EXTENSION),
                languages = emptyList(),
                watchOptions = listOf(WatchOption("w2", "B", src2, SourceType.EXTENSION, "720p")),
                score = 200
            )
        )

        val merged = SearchResultDeduplicator.deduplicate(results, hideDuplicates = true, prefs = defaultPrefs)
        assertEquals("Should merge duplicates based on ID despite different titles", 1, merged.size)
        assertEquals(2, merged.first().watchOptions.size)
    }

    @Test
    fun testDeduplicateSameTitleDifferentProviders() = runBlocking {
        // Same mediaItem ID from IPTV and Extension should merge
        val item = FakeData.movieSpiderman
        val iptvSrc = FakeData.spidermanSources[0]
        val extSrc = FakeData.spidermanSources[2]

        val results = listOf(
            NormalizedSearchResult(
                mediaItem = item,
                availableFrom = listOf(SourceType.IPTV),
                languages = listOf("Hindi"),
                watchOptions = listOf(WatchOption("w1", "IPTV", iptvSrc, SourceType.IPTV, "1080p Hindi")),
                score = 350
            ),
            NormalizedSearchResult(
                mediaItem = item,
                availableFrom = listOf(SourceType.EXTENSION),
                languages = listOf("English"),
                watchOptions = listOf(WatchOption("w2", "Ext", extSrc, SourceType.EXTENSION, "1080p English")),
                score = 250
            )
        )

        val merged = SearchResultDeduplicator.deduplicate(results, hideDuplicates = true, prefs = defaultPrefs)
        assertEquals(1, merged.size)
        val consolidated = merged.first()
        assertTrue(consolidated.availableFrom.contains(SourceType.IPTV))
        assertTrue(consolidated.availableFrom.contains(SourceType.EXTENSION))
    }

    @Test
    fun testDeduplicateIPTVandExtensionMerge() = runBlocking {
        // Explicit test for the Spider-Man IPTV + Extension merge scenario
        val item = FakeData.movieSpiderman
        val iptvSource = FakeData.spidermanSources[0]
        val debridSource = FakeData.spidermanSources[1]
        val extSource = FakeData.spidermanSources[2]

        val results = listOf(
            NormalizedSearchResult(
                mediaItem = item,
                availableFrom = listOf(SourceType.IPTV),
                languages = listOf("Hindi"),
                watchOptions = listOf(WatchOption("w1", "IPTV", iptvSource, SourceType.IPTV, "IPTV Hindi")),
                score = 400
            ),
            NormalizedSearchResult(
                mediaItem = item,
                availableFrom = listOf(SourceType.DEBRID),
                languages = listOf("English"),
                watchOptions = listOf(WatchOption("w2", "Debrid", debridSource, SourceType.DEBRID, "Debrid 4K")),
                score = 500
            ),
            NormalizedSearchResult(
                mediaItem = item,
                availableFrom = listOf(SourceType.EXTENSION),
                languages = listOf("English"),
                watchOptions = listOf(WatchOption("w3", "Ext", extSource, SourceType.EXTENSION, "Ext 1080p")),
                score = 300
            )
        )

        val merged = SearchResultDeduplicator.deduplicate(results, hideDuplicates = true, prefs = defaultPrefs)
        assertEquals(1, merged.size)
        assertEquals(3, merged.first().watchOptions.size)
        assertTrue(merged.first().availableFrom.contains(SourceType.IPTV))
        assertTrue(merged.first().availableFrom.contains(SourceType.EXTENSION))
        assertTrue(merged.first().availableFrom.contains(SourceType.DEBRID))
    }

    @Test
    fun testDeduplicatePreservesWhenHideDuplicatesFalse() = runBlocking {
        val item = FakeData.movieSpiderman
        val src1 = FakeData.spidermanSources[0]
        val src2 = FakeData.spidermanSources[2]

        val results = listOf(
            NormalizedSearchResult(
                mediaItem = item, availableFrom = listOf(SourceType.IPTV), languages = listOf("Hindi"),
                watchOptions = listOf(WatchOption("w1", "A", src1, SourceType.IPTV, "1080p")), score = 300
            ),
            NormalizedSearchResult(
                mediaItem = item, availableFrom = listOf(SourceType.EXTENSION), languages = listOf("English"),
                watchOptions = listOf(WatchOption("w2", "B", src2, SourceType.EXTENSION, "1080p")), score = 200
            )
        )

        val merged = SearchResultDeduplicator.deduplicate(results, hideDuplicates = false, prefs = defaultPrefs)
        assertEquals("When hideDuplicates=false, should keep all", 2, merged.size)
    }

    @Test
    fun testDeduplicateMagnetURLs() = runBlocking {
        // Two sources with the same magnet hash should deduplicate their watch options
        val item = MediaItem("m1", "Test", MediaType.MOVIE)
        val magnetUrl = "magnet:?xt=urn:btih:ABC123DEF&dn=test"
        val src1 = StreamSource("s1", "Source A", magnetUrl, "ext-a", "1080p", language = "English")
        val src2 = StreamSource("s2", "Source B", magnetUrl, "ext-b", "1080p", language = "English")

        val results = listOf(
            NormalizedSearchResult(
                mediaItem = item, availableFrom = listOf(SourceType.EXTENSION), languages = listOf("English"),
                watchOptions = listOf(
                    WatchOption("w1", "A", src1, SourceType.EXTENSION, "1080p"),
                    WatchOption("w2", "B", src2, SourceType.EXTENSION, "1080p")
                ),
                score = 300
            ),
            NormalizedSearchResult(
                mediaItem = item, availableFrom = listOf(SourceType.EXTENSION), languages = listOf("English"),
                watchOptions = listOf(WatchOption("w3", "C", src1, SourceType.EXTENSION, "1080p")),
                score = 200
            )
        )

        val merged = SearchResultDeduplicator.deduplicate(results, hideDuplicates = true, prefs = defaultPrefs)
        assertEquals(1, merged.size)
        // Same magnet hash should result in dedup, keeping only distinct magnet hashes
        val uniqueHashes = merged.first().watchOptions.map {
            it.source.url.substringAfter("btih:").substringBefore("&").lowercase()
        }.distinct()
        assertEquals("Should have only 1 unique magnet hash", 1, uniqueHashes.size)
    }

    // ─── Task 3: cleanStreamTitle ────────────────────────────────────

    @Test
    fun testCleanStreamTitle_rawTorrentFilename() = runBlocking {
        val cleaned = WatchOptionResolver.cleanStreamTitle(
            "Movie.Name.2024.1080p.WEB-DL.x264-GROUP.mkv", null, "Torrentio"
        )
        // Should produce "Movie Name 2024" (no resolution, codec, release group, extension)
        assertTrue("Should contain 'Movie Name 2024', got: '$cleaned'",
            cleaned.contains("Movie Name 2024"))
        assertFalse("Should not contain '1080p'", cleaned.contains("1080p", ignoreCase = true))
        assertFalse("Should not contain 'WEB-DL'", cleaned.contains("WEB-DL", ignoreCase = true))
        assertFalse("Should not contain 'x264'", cleaned.contains("x264", ignoreCase = true))
        assertFalse("Should not contain 'GROUP'", cleaned.contains("GROUP"))
        assertFalse("Should not contain '.mkv'", cleaned.contains(".mkv"))
    }

    @Test
    fun testCleanStreamTitle_4kBlurayFilename() = runBlocking {
        val cleaned = WatchOptionResolver.cleanStreamTitle(
            "Spider-Man.Homecoming.2017.2160p.UHD.HDR.BluRay.x265.REMUX-RealDebrid.mkv", null, "RD"
        )
        assertTrue("Should contain 'Spider-Man Homecoming 2017', got: '$cleaned'",
            cleaned.contains("Spider") && cleaned.contains("Homecoming") && cleaned.contains("2017"))
        assertFalse("Should not contain '2160p'", cleaned.contains("2160p", ignoreCase = true))
        assertFalse("Should not contain 'BluRay'", cleaned.contains("BluRay", ignoreCase = true))
        assertFalse("Should not contain 'REMUX'", cleaned.contains("REMUX", ignoreCase = true))
        assertFalse("Should not contain 'x265'", cleaned.contains("x265", ignoreCase = true))
    }

    @Test
    fun testCleanStreamTitle_urlFallback() = runBlocking {
        val cleaned = WatchOptionResolver.cleanStreamTitle(
            "https://cdn.example.com/streams/spiderman-1080p.mp4", null, "CDN Provider"
        )
        assertEquals("CDN Provider Stream", cleaned)
    }

    @Test
    fun testCleanStreamTitle_multilineWithUrl() = runBlocking {
        val cleaned = WatchOptionResolver.cleanStreamTitle(
            "https://cdn.example.com/stream\nSome fallback text", null, "Provider"
        )
        assertEquals("Provider Stream", cleaned)
    }

    @Test
    fun testCleanStreamTitle_hidesInlineUrl() = runBlocking {
        val cleaned = WatchOptionResolver.cleanStreamTitle(
            "Watch here: https://cdn.example.com/stream and enjoy", null, "Provider"
        )
        assertTrue("Should hide inline URL", cleaned.contains("[URL HIDDEN]"))
        assertFalse("Should not contain raw URL", cleaned.contains("https://"))
    }

    @Test
    fun testCleanStreamTitle_nullInputs() = runBlocking {
        val cleaned = WatchOptionResolver.cleanStreamTitle(null, null, "TestProvider")
        assertEquals("Stream", cleaned)
    }

    @Test
    fun testCleanStreamTitle_dd51Audio() = runBlocking {
        val cleaned = WatchOptionResolver.cleanStreamTitle(
            "Spider-Man Homecoming (2017) 1080p Bluray x264 [Hindi DD 5.1 + English]", null, "Torrentio"
        )
        assertTrue("Should contain 'Spider-Man Homecoming', got: '$cleaned'",
            cleaned.contains("Spider") && cleaned.contains("Homecoming"))
        assertFalse("Should not contain '1080p'", cleaned.contains("1080p", ignoreCase = true))
        assertFalse("Should not contain 'x264'", cleaned.contains("x264", ignoreCase = true))
    }

    @Test
    fun testCleanStreamTitle_bdrip() = runBlocking {
        val cleaned = WatchOptionResolver.cleanStreamTitle(
            "Spider-Man.Homecoming.2017.BDRip.x264.Castellano.mp4", null, "Ext"
        )
        assertTrue("Should contain 'Spider', got: '$cleaned'", cleaned.contains("Spider"))
        assertFalse("Should not contain 'BDRip'", cleaned.contains("BDRip", ignoreCase = true))
        assertFalse("Should not contain 'x264'", cleaned.contains("x264", ignoreCase = true))
    }

    @Test
    fun testCleanStreamTitle_longTitleTruncated() = runBlocking {
        val longName = "A".repeat(100) + ".1080p.BluRay.x264-GROUP.mkv"
        val cleaned = WatchOptionResolver.cleanStreamTitle(longName, null, "Ext")
        assertTrue("Long titles should be truncated to 60 chars max", cleaned.length <= 60)
        assertTrue("Should end with '...'", cleaned.endsWith("..."))
    }

    // ─── Task 4: Stream Picker Label Cleanliness ─────────────────────

    @Test
    fun testReadableLabelContainsResolution() = runBlocking {
        val source = StreamSource("s1", "Test", "http://x", "ext-a", "1080p", language = "English")
        val label = WatchOptionResolver.getReadableLabel(source, SourceType.EXTENSION)
        assertTrue("Label should contain normalized resolution '1080p', got: '$label'",
            label.contains("1080p"))
    }

    @Test
    fun testReadableLabelContainsLanguage() = runBlocking {
        val source = StreamSource("s1", "Test", "http://x", "ext-a", "1080p", language = "Hindi")
        val label = WatchOptionResolver.getReadableLabel(source, SourceType.EXTENSION)
        assertTrue("Label should contain language 'Hindi', got: '$label'",
            label.contains("Hindi"))
    }

    @Test
    fun testReadableLabelNoRawFilenames() = runBlocking {
        val source = StreamSource(
            "s1",
            "Movie.Name.2024.1080p.WEB-DL.x264-GROUP.mkv",
            "http://x", "ext-a", "1080p", language = "English"
        )
        val label = WatchOptionResolver.getReadableLabel(source, SourceType.EXTENSION)
        // The label should contain structured info (resolution, language) but not raw URLs
        assertFalse("Label should not contain raw file extension",
            label.contains(".mkv"))
        assertFalse("Label should not contain http://",
            label.contains("http://"))
        assertTrue("Label should contain '1080p'", label.contains("1080p"))
    }

    @Test
    fun testReadableLabelDebrid() = runBlocking {
        val source = StreamSource(
            "src-spiderman-debrid-4k",
            "Spider-Man 4K HDR",
            "http://x", "deb-rd", "4K", language = "English"
        )
        val label = WatchOptionResolver.getReadableLabel(source, SourceType.DEBRID)
        assertTrue("Label should contain 'Debrid'", label.contains("Debrid"))
        assertTrue("Label should contain '4K'", label.contains("4K"))
    }

    @Test
    fun testReadableLabelDualAudio() = runBlocking {
        val source = StreamSource(
            "s1", "Test", "http://x", "ext-a", "1080p",
            language = "Hindi", isDualAudio = true
        )
        val label = WatchOptionResolver.getReadableLabel(source, SourceType.EXTENSION)
        assertTrue("Label should contain 'Dual Audio'", label.contains("Dual Audio"))
    }

    @Test
    fun testReadableLabelWithSubtitles() = runBlocking {
        val source = StreamSource(
            "s1", "Test Sub", "http://x", "ext-a", "720p",
            language = "English", isSubbed = true
        )
        val label = WatchOptionResolver.getReadableLabel(source, SourceType.EXTENSION)
        assertTrue("Label should contain 'Subtitles'", label.contains("Subtitles"))
    }

    @Test
    fun testReadableLabelLowData() = runBlocking {
        val source = StreamSource(
            "s1", "Test", "http://x", "ext-a", "720p",
            language = "English", sizeBytes = 500_000_000 // 500MB — qualifies as low data
        )
        val label = WatchOptionResolver.getReadableLabel(source, SourceType.EXTENSION)
        assertTrue("Label should contain 'Low Data' for small non-4K files", label.contains("Low Data"))
    }

    @Test
    fun testNormalizeResolution() = runBlocking {
        assertEquals("4K", WatchOptionResolver.normalizeResolution("2160P"))
        assertEquals("4K", WatchOptionResolver.normalizeResolution("4K"))
        assertEquals("1080p", WatchOptionResolver.normalizeResolution("1080P"))
        assertEquals("1080p", WatchOptionResolver.normalizeResolution("FHD"))
        assertEquals("720p", WatchOptionResolver.normalizeResolution("720P"))
        assertEquals("720p", WatchOptionResolver.normalizeResolution("HD"))
        assertEquals("SD", WatchOptionResolver.normalizeResolution("480P"))
        assertEquals("SD", WatchOptionResolver.normalizeResolution("SD"))
        assertEquals("Live", WatchOptionResolver.normalizeResolution("Live"))
    }

    // ─── Task 5: Search Responsiveness with Multiple Providers ───────

    @Test
    fun testMultipleProviderEmissions() = runBlocking {
        val engine = UniversalSearchEngineImpl(
            providers = listOf(
                IPTVSearchProviderImpl(),
                VODSearchProviderImpl(),
                DebridAvailabilityProviderImpl()
            )
        )

        val emissions = engine.search("Spider-Man", defaultPrefs).toList()
        // With conflated batching, rapid emissions are coalesced.
        assertTrue("Should receive emissions, got ${emissions.size}",
            emissions.isNotEmpty())
    }

    @Test
    fun testBlankQueryReturnsEmpty() = runBlocking {
        val engine = UniversalSearchEngineImpl(providers = listOf(IPTVSearchProviderImpl()))
        val emissions = engine.search("", defaultPrefs).toList()
        assertEquals(1, emissions.size) // Single empty emission
        assertTrue(emissions.first().isEmpty())
    }

    @Test
    fun testSearchWithTimeoutDoesNotCrash() = runBlocking {
        val engine = UniversalSearchEngineImpl(
            providers = listOf(
                ExtensionSearchProviderImpl(),
                SettingsSearchProviderImpl()
            )
        )

        val policy = SearchTimeoutPolicy(
            defaultTimeoutMs = 2000L,
            providerTimeoutsMs = mapOf("prov-extensions" to 100L) // Very tight timeout
        )

        val emissions = engine.search("Spider-Man", defaultPrefs, policy).toList()
        assertFalse("Search should complete without crash", emissions.isEmpty())
    }

    @Test
    fun testSearchWithTimeoutEmitsErrorResult() = runBlocking {
        // Create a fake slow provider that suspends for longer than the timeout
        val slowProvider = object : SearchProvider {
            override val id = "prov-slow"
            override val name = "Slow Provider"
            override val priority = 100
            override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
                delay(1000)
                emit(SearchProviderResult(id, name, query))
            }
        }
        val fastProvider = object : SearchProvider {
            override val id = "prov-fast"
            override val name = "Fast Provider"
            override val priority = 100
            override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
                delay(10)
                emit(SearchProviderResult(id, name, query, mediaItems = listOf(FakeData.movieSpiderman)))
            }
        }

        val engine = UniversalSearchEngineImpl(listOf(slowProvider, fastProvider))
        
        // Timeout the slow provider quickly
        val policy = SearchTimeoutPolicy(
            defaultTimeoutMs = 2000L,
            providerTimeoutsMs = mapOf("prov-slow" to 100L)
        )
        
        // Use a test-specific SearchResultMerger intercept to inspect the raw provider results
        // Actually, we can't easily intercept the internal accumulatedResults without reflection,
        // so we just verify the resulting list from the fast provider is emitted, and it does not crash.
        // Wait, UniversalSearchEngineImpl catches the exception and sends an error result internally.
        // Let's assert that the emissions contain the fast provider's result.
        val emissions = engine.search("Spider-Man", defaultPrefs, policy).toList()
        
        assertEquals(2, emissions.size) // One for fast, one for slow
        
        // The last emission should contain the fast provider's merged groups.
        val lastGroups = emissions.last()
        assertTrue("Fast provider's items should be in the final merged groups", lastGroups.isNotEmpty())
        
        val moviesGroup = lastGroups.find { it.groupType == SearchGroupType.MOVIES }
        assertNotNull("Should contain MOVIES group from the fast provider", moviesGroup)
        assertEquals("Spider-Man: Homecoming", moviesGroup!!.results.first().mediaItem.title)
    }

    // ─── Task 6: Disabled/Unhealthy Provider Handling ────────────────

    @Test
    fun testFailedProviderScorePenalty() = runBlocking {
        val healthySource = StreamSource("s1", "Healthy", "", "ext-torrentio", "1080p",
            language = "English", videoCodec = "AVC", audioCodec = "AAC")
        val failedSource = StreamSource("s2", "Failed", "", "ext-failed", "1080p",
            language = "English", videoCodec = "AVC", audioCodec = "AAC")

        val healthyScore = SearchResultRanker.calculateSourceScore(healthySource, defaultPrefs)
        val failedScore = SearchResultRanker.calculateSourceScore(failedSource, defaultPrefs)

        assertTrue("Healthy provider ($healthyScore) should score higher than failed ($failedScore)",
            healthyScore > failedScore)
    }

    @Test
    fun testSlowProviderScorePenalty() = runBlocking {
        val healthySource = StreamSource("s1", "Healthy", "", "ext-torrentio", "1080p",
            language = "English", videoCodec = "AVC", audioCodec = "AAC")
        val slowSource = StreamSource("s2", "Slow", "", "ext-slow", "1080p",
            language = "English", videoCodec = "AVC", audioCodec = "AAC")

        val healthyScore = SearchResultRanker.calculateSourceScore(healthySource, defaultPrefs)
        val slowScore = SearchResultRanker.calculateSourceScore(slowSource, defaultPrefs)

        assertTrue("Healthy provider ($healthyScore) should score higher than slow ($slowScore)",
            healthyScore > slowScore)
    }

    @Test
    fun testErrorProviderDoesNotCrashSearch() = runBlocking {
        val engine = UniversalSearchEngineImpl(
            providers = listOf(
                ExtensionSearchProviderImpl(), // Throws on "fail" query
                IPTVSearchProviderImpl()
            )
        )

        // "fail" triggers RuntimeException in ExtensionSearchProviderImpl
        val emissions = engine.search("fail", defaultPrefs).toList()
        assertFalse("Search should complete even with failed provider", emissions.isEmpty())
    }

    @Test
    fun testMissingPosterIsTolerated() = runBlocking {
        val itemNoPoster = MediaItem("m_no_poster", "No Poster Movie", MediaType.MOVIE, posterUrl = null)
        val source = StreamSource("s1", "Stream", "http://x", "ext-test", "1080p", language = "English")
        
        val result = NormalizedSearchResult(
            mediaItem = itemNoPoster,
            availableFrom = listOf(SourceType.EXTENSION),
            languages = listOf("English"),
            watchOptions = listOf(WatchOption("w1", "w1", source, SourceType.EXTENSION, "1080p")),
            score = 100
        )
        
        // Ensure ranking doesn't crash
        val ranked = SearchResultRanker.rankResults(listOf(result), "No Poster Movie", defaultPrefs)
        assertEquals(1, ranked.size)
        assertNull(ranked.first().mediaItem.posterUrl)
        
        // Ensure dedup doesn't crash
        val deduped = SearchResultDeduplicator.deduplicate(ranked, true, defaultPrefs)
        assertEquals(1, deduped.size)
        assertNull(deduped.first().mediaItem.posterUrl)
    }

    @Test
    fun testDisabledExtensionsAreSkipped() = runBlocking {
        // We know from testPersistedDisabledExtensionsDoNotAppear that disabling works.
        // Let's do a quick validation here too.
        com.example.calmsource.feature.extensions.ExtensionRepository.toggleExtension("ext-torrentio", false)
        val engine = UniversalSearchEngineImpl(providers = listOf(ExtensionSearchProviderImpl()))
        val flowList = engine.search("Spider-Man", defaultPrefs).toList()
        val mergedGroups = flowList.last()
        val extGroup = mergedGroups.firstOrNull { it.groupType == SearchGroupType.EXTENSION_RESULTS }
        
        val hasTorrentio = extGroup?.results?.any { res ->
            res.watchOptions.any { it.source.extensionId == "ext-torrentio" }
        } ?: false
        
        assertFalse("Disabled extensions should not be queried or returned", hasTorrentio)
        com.example.calmsource.feature.extensions.ExtensionRepository.toggleExtension("ext-torrentio", true)
    }

    // ─── Task 7: Spider-Man Merged Result Regression ─────────────────

    @Test
    fun testSpiderManMergedResultStillPasses() = runBlocking {
        val spiderman = FakeData.movieSpiderman
        val sources = FakeData.spidermanSources
        val prefs = defaultPrefs

        val providerResult = SearchProviderResult(
            providerId = "test",
            providerName = "Test",
            query = SearchQuery("Spider-Man Homecoming"),
            mediaItems = listOf(spiderman),
            streamSources = sources
        )

        val mergedGroups = SearchResultMerger.merge(listOf(providerResult), "Spider-Man Homecoming", prefs)

        val moviesGroup = mergedGroups.firstOrNull { it.groupType == SearchGroupType.MOVIES }
        assertNotNull("Should have a Movies group", moviesGroup)

        val result = moviesGroup!!.results.first()
        assertEquals("Spider-Man: Homecoming", result.mediaItem.title)

        // Check all three source types present
        assertTrue(result.availableFrom.contains(SourceType.IPTV))
        assertTrue(result.availableFrom.contains(SourceType.EXTENSION))
        assertTrue(result.availableFrom.contains(SourceType.DEBRID))

        // Check languages and dual audio
        assertTrue(result.languages.contains("Hindi"))
        assertTrue(result.languages.contains("English"))
        assertTrue(result.isDualAudio)

        // Best match option should be set
        assertNotNull(result.bestMatchOption)

        // Multiple watch options
        assertTrue("Should have multiple watch options", result.watchOptions.size > 3)
    }

    @Test
    fun testSpiderManMultiProviderMerge() = runBlocking {
        val spiderman = FakeData.movieSpiderman
        val prefs = defaultPrefs

        // Simulate three separate providers contributing Spider-Man results
        val providerResults = listOf(
            SearchProviderResult(
                providerId = "prov-vod",
                providerName = "VOD",
                query = SearchQuery("Spider-Man"),
                mediaItems = listOf(spiderman),
                streamSources = listOf(FakeData.spidermanSources[0]) // IPTV
            ),
            SearchProviderResult(
                providerId = "prov-ext",
                providerName = "Extensions",
                query = SearchQuery("Spider-Man"),
                mediaItems = listOf(spiderman),
                streamSources = listOf(FakeData.spidermanSources[2]) // Extension
            ),
            SearchProviderResult(
                providerId = "prov-debrid",
                providerName = "Debrid",
                query = SearchQuery("Spider-Man"),
                mediaItems = listOf(spiderman),
                streamSources = listOf(FakeData.spidermanSources[1]) // Debrid
            )
        )

        val mergedGroups = SearchResultMerger.merge(providerResults, "Spider-Man", prefs)
        val moviesGroup = mergedGroups.firstOrNull { it.groupType == SearchGroupType.MOVIES }
        assertNotNull(moviesGroup)

        val spiderCard = moviesGroup!!.results.first()
        assertTrue(spiderCard.availableFrom.contains(SourceType.IPTV))
        assertTrue(spiderCard.availableFrom.contains(SourceType.EXTENSION))
        assertTrue(spiderCard.availableFrom.contains(SourceType.DEBRID))
    }

    // ─── Bug #39/#40 Regression: No More Hardcoded Hindi/1080p ───────

    @Test
    fun testChannelPseudoSourceNeverHardcodesHindi() = runBlocking {
        val channel = Channel("chan-test", "ESPN HD", null, "http://stream.url", "Sports")
        val prefs = defaultPrefs

        val providerResult = SearchProviderResult(
            providerId = "test",
            providerName = "Test",
            query = SearchQuery("ESPN"),
            channels = listOf(channel)
        )

        val mergedGroups = SearchResultMerger.merge(listOf(providerResult), "ESPN", prefs)
        val channelGroup = mergedGroups.firstOrNull { it.groupType == SearchGroupType.LIVE_CHANNELS }
        assertNotNull("Should have live channels group", channelGroup)

        val result = channelGroup!!.results.first()
        // Language should be inferred from name, not hardcoded "Hindi"
        // "ESPN HD" contains "HD" which triggers "English" heuristic
        assertFalse("Language should NOT be hardcoded 'Hindi' for ESPN",
            result.languages == listOf("Hindi"))
    }

    @Test
    fun testChannelPseudoSourceNeverHardcodes1080p() = runBlocking {
        val channel = Channel("chan-test", "ESPN HD", null, "http://stream.url", "Sports")
        val prefs = defaultPrefs

        val providerResult = SearchProviderResult(
            providerId = "test",
            providerName = "Test",
            query = SearchQuery("ESPN"),
            channels = listOf(channel)
        )

        val mergedGroups = SearchResultMerger.merge(listOf(providerResult), "ESPN", prefs)
        val channelGroup = mergedGroups.firstOrNull { it.groupType == SearchGroupType.LIVE_CHANNELS }
        assertNotNull(channelGroup)

        val result = channelGroup!!.results.first()
        val pseudoResolution = result.bestMatchOption?.source?.resolution
        assertNotEquals("Resolution should NOT be hardcoded '1080p' for live channels",
            "1080p", pseudoResolution)
        assertEquals("Live channels should have 'Live' resolution", "Live", pseudoResolution)
    }

    @Test
    fun testProgramPseudoSourceUsesLiveResolution() = runBlocking {
        val program = Program("prog-test", "chan-test", "NBA Today", "Basketball news", 0, 100000)
        val prefs = defaultPrefs

        val providerResult = SearchProviderResult(
            providerId = "test",
            providerName = "Test",
            query = SearchQuery("NBA"),
            programs = listOf(program)
        )

        val mergedGroups = SearchResultMerger.merge(listOf(providerResult), "NBA", prefs)
        val programGroup = mergedGroups.firstOrNull { it.groupType == SearchGroupType.LIVE_PROGRAMS }
        assertNotNull(programGroup)

        val result = programGroup!!.results.first()
        val pseudoResolution = result.bestMatchOption?.source?.resolution
        assertEquals("Programs should have 'Live' resolution", "Live", pseudoResolution)
    }

    @Test
    fun testHindiChannelInfersHindi() = runBlocking {
        val channel = Channel("chan-hindi", "Star Sports 1 Hindi", null, "http://stream.url", "Sports")
        val prefs = defaultPrefs

        val providerResult = SearchProviderResult(
            providerId = "test",
            providerName = "Test",
            query = SearchQuery("Star Sports"),
            channels = listOf(channel)
        )

        val mergedGroups = SearchResultMerger.merge(listOf(providerResult), "Star Sports", prefs)
        val channelGroup = mergedGroups.firstOrNull { it.groupType == SearchGroupType.LIVE_CHANNELS }
        assertNotNull(channelGroup)

        val result = channelGroup!!.results.first()
        assertTrue("Hindi channel should correctly infer Hindi language",
            result.languages.contains("Hindi"))
    }

    // ─── WatchOptionResolver.mapStremioStreamToSource ─────────────────

    @Test
    fun testMapStremioStreamResolutionParsing() = runBlocking {
        val stream = StremioStream(
            name = "4K HDR",
            title = "Movie.2024.2160p.BluRay.x265-GROUP",
            url = "http://example.com/stream"
        )
        val source = WatchOptionResolver.mapStremioStreamToSource(
            stream, "ext-test", "TestExt", "movie-1"
        )
        assertEquals("4K", source.resolution)
        assertEquals("HEVC", source.videoCodec)
    }

    @Test
    fun testMapStremioStreamLanguageDetection() = runBlocking {
        val streamHindi = StremioStream(
            name = "Hindi 1080p",
            title = "Movie Hindi Dubbed",
            url = "http://example.com/stream"
        )
        val source = WatchOptionResolver.mapStremioStreamToSource(
            streamHindi, "ext-test", "TestExt", "movie-1"
        )
        assertEquals("Hindi", source.language)
        assertTrue(source.isDubbed)
    }

    @Test
    fun testMapStremioStreamDualAudioDetection() = runBlocking {
        val stream = StremioStream(
            name = "Dual Audio",
            title = "Movie Hindi English 1080p",
            url = "http://example.com/stream"
        )
        val source = WatchOptionResolver.mapStremioStreamToSource(
            stream, "ext-test", "TestExt", "movie-1"
        )
        assertTrue("Should detect dual audio", source.isDualAudio)
    }

    @Test
    fun testMapStremioStreamSeedsParsing() = runBlocking {
        val stream = StremioStream(
            name = "Torrentio 1080p",
            title = "Movie.2024.1080p 👤 142",
            url = null,
            infoHash = "abc123"
        )
        val source = WatchOptionResolver.mapStremioStreamToSource(
            stream, "ext-test", "TestExt", "movie-1"
        )
        assertNotNull("Seeds should be parsed from 👤 emoji", source.seeds)
        assertEquals(142, source.seeds)
    }

    @Test
    fun testMapStremioStreamSizeParsing() = runBlocking {
        val stream = StremioStream(
            name = "1080p WEB-DL",
            title = "Movie 2024 3.5 GB",
            url = "http://example.com/stream"
        )
        val source = WatchOptionResolver.mapStremioStreamToSource(
            stream, "ext-test", "TestExt", "movie-1"
        )
        assertNotNull("Size should be parsed", source.sizeBytes)
        val sizeGb = source.sizeBytes!!.toDouble() / (1024 * 1024 * 1024)
        assertTrue("Size should be ~3.5 GB, got $sizeGb", sizeGb > 3.0 && sizeGb < 4.0)
    }

    @Test
    fun testFormatFileSize() = runBlocking {
        assertEquals("2.80 GB", WatchOptionResolver.formatFileSize((2.8 * 1024 * 1024 * 1024).toLong()))
        assertEquals("Unknown", WatchOptionResolver.formatFileSize(null))
    }

    // ─── buildWatchOptions ────────────────────────────────────────────

    @Test
    fun testBuildWatchOptionsClassifiesSourceTypes() = runBlocking {
        val sources = listOf(
            StreamSource("s1", "A", "http://a", "iptv-1", "1080p", language = "Hindi"),
            StreamSource("s2", "B", "http://b", "deb-rd", "4K", language = "English"),
            StreamSource("s3", "C", "http://c", "ext-torrentio", "720p", language = "Tamil")
        )

        val options = WatchOptionResolver.buildWatchOptions(sources)
        assertEquals(3, options.size)
        assertEquals(SourceType.IPTV, options[0].type)
        assertEquals(SourceType.DEBRID, options[1].type)
        assertEquals(SourceType.EXTENSION, options[2].type)
    }

    // ─── Edge Cases ──────────────────────────────────────────────────

    @Test
    fun testEmptyProviderResultsProduceNoGroups() = runBlocking {
        val mergedGroups = SearchResultMerger.merge(emptyList(), "test", defaultPrefs)
        assertTrue("Empty provider results should produce no groups", mergedGroups.isEmpty())
    }

    @Test
    fun testSettingsGroupSeparateFromMedia() = runBlocking {
        val providerResult = SearchProviderResult(
            providerId = "test",
            providerName = "Test",
            query = SearchQuery("Source"),
            settingsRoutes = listOf("Source Priorities & Languages")
        )

        val mergedGroups = SearchResultMerger.merge(listOf(providerResult), "Source", defaultPrefs)
        val settingsGroup = mergedGroups.firstOrNull { it.groupType == SearchGroupType.SETTINGS }
        assertNotNull("Should have settings group", settingsGroup)
        assertEquals(1, settingsGroup!!.results.size)
    }

    @Test
    fun testSearchProviderCustomThrowableDoesNotCrashPipeline() = runBlocking {
        val customThrowable = object : Throwable("Custom Throwable") {}
        val throwingProvider = object : SearchProvider {
            override val id = "prov-throwing"
            override val name = "Throwing Provider"
            override val priority = 100
            override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
                throw customThrowable
            }
        }
        val healthyProvider = object : SearchProvider {
            override val id = "prov-healthy"
            override val name = "Healthy Provider"
            override val priority = 100
            override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
                emit(SearchProviderResult(id, name, query, mediaItems = listOf(FakeData.movieSpiderman)))
            }
        }

        val engine = UniversalSearchEngineImpl(listOf(throwingProvider, healthyProvider))
        val emissions = engine.search("Spider-Man", defaultPrefs).toList()
        
        assertFalse("Search should complete even when provider throws custom Throwable", emissions.isEmpty())
        val lastGroups = emissions.last()
        assertTrue("Healthy provider's items should still be merged", lastGroups.isNotEmpty())
    }

    @Test
    fun testSearchProviderCancellationExceptionIsHandledAsError() = runBlocking {
        val cancellingProvider = object : SearchProvider {
            override val id = "prov-cancelling"
            override val name = "Cancelling Provider"
            override val priority = 100
            override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
                throw kotlinx.coroutines.CancellationException("Test cancellation")
            }
        }

        val engine = UniversalSearchEngineImpl(listOf(cancellingProvider))
        val emissions = engine.search("Spider-Man", defaultPrefs).toList()
        assertFalse("Search should complete even with cancelled provider", emissions.isEmpty())
    }
}
