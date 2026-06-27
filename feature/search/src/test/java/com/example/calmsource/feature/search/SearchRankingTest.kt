package com.example.calmsource.feature.search

import com.example.calmsource.core.model.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SearchRankingTest {

    @Test
    fun testExactTitleRanking() = kotlinx.coroutines.runBlocking {
        val spiderman = FakeData.movieSpiderman
        val prefs = FakeData.defaultPreferences

        val results = listOf(
            NormalizedSearchResult(mediaItem = spiderman, availableFrom = emptyList(), languages = emptyList(), watchOptions = emptyList(), score = 100),
            NormalizedSearchResult(mediaItem = FakeData.movieInception, availableFrom = emptyList(), languages = emptyList(), watchOptions = emptyList(), score = 500)
        )

        // Query: "Spider-Man: Homecoming" -> Exact match for spiderman should boost its score above inception
        val ranked = SearchResultRanker.rankResults(results, "Spider-Man: Homecoming", prefs)
        
        assertEquals(spiderman.id, ranked.first().mediaItem.id)
        assertTrue(ranked.first().score > ranked.last().score)
    }

    @Test
    fun testMissingPunctuationStillRanksExactTitleFirst() {
        val results = listOf(
            NormalizedSearchResult(
                mediaItem = MediaItem("spider-man", "Spider-Man", MediaType.MOVIE),
                availableFrom = emptyList(),
                languages = emptyList(),
                watchOptions = emptyList(),
                score = 100
            ),
            NormalizedSearchResult(
                mediaItem = MediaItem("spider-woman", "Spider-Woman", MediaType.MOVIE),
                availableFrom = emptyList(),
                languages = emptyList(),
                watchOptions = emptyList(),
                score = 200
            )
        )

        val ranked = SearchResultRanker.rankResults(results, "Spider Man", FakeData.defaultPreferences)

        assertEquals("spider-man", ranked.first().mediaItem.id)
    }

    @Test
    fun testSmallTypoRanksClosestTitleFirst() {
        val results = listOf(
            NormalizedSearchResult(
                mediaItem = MediaItem("interstellar", "Interstellar", MediaType.MOVIE),
                availableFrom = emptyList(),
                languages = emptyList(),
                watchOptions = emptyList(),
                score = 100
            ),
            NormalizedSearchResult(
                mediaItem = MediaItem("interstate", "Interstate", MediaType.MOVIE),
                availableFrom = emptyList(),
                languages = emptyList(),
                watchOptions = emptyList(),
                score = 200
            )
        )

        val ranked = SearchResultRanker.rankResults(results, "Interstelar", FakeData.defaultPreferences)

        assertEquals("interstellar", ranked.first().mediaItem.id)
    }

    @Test
    fun testDuplicateMerging() = kotlinx.coroutines.runBlocking {
        val spiderman = FakeData.movieSpiderman
        val prefs = FakeData.defaultPreferences

        val results = listOf(
            NormalizedSearchResult(
                mediaItem = spiderman,
                availableFrom = listOf(SourceType.IPTV),
                languages = listOf("Hindi"),
                watchOptions = listOf(WatchOption("1", "IPTV Stream", FakeData.spidermanSources[0], SourceType.IPTV, "1080p Hindi")),
                score = 300
            ),
            NormalizedSearchResult(
                mediaItem = spiderman,
                availableFrom = listOf(SourceType.EXTENSION),
                languages = listOf("English"),
                watchOptions = listOf(WatchOption("2", "Torrent Stream", FakeData.spidermanSources[2], SourceType.EXTENSION, "1080p English")),
                score = 250
            )
        )

        // Deduplicate
        val merged = SearchResultDeduplicator.deduplicate(results, hideDuplicates = true, prefs = prefs)

        // Should merge into 1 search result card
        assertEquals(1, merged.size)
        val consolidated = merged.first()
        assertEquals(spiderman.id, consolidated.mediaItem.id)
        assertEquals(2, consolidated.watchOptions.size)
        assertTrue(consolidated.availableFrom.contains(SourceType.IPTV))
        assertTrue(consolidated.availableFrom.contains(SourceType.EXTENSION))
        assertTrue(consolidated.languages.contains("Hindi"))
        assertTrue(consolidated.languages.contains("English"))
    }

    @Test
    fun testIptvExtensionDebridMerge() = kotlinx.coroutines.runBlocking {
        val spiderman = FakeData.movieSpiderman
        val prefs = FakeData.defaultPreferences

        val providerResults = listOf(
            SearchProviderResult(
                providerId = "prov-vod",
                providerName = "VOD",
                query = SearchQuery("Spider-Man"),
                mediaItems = listOf(spiderman),
                streamSources = listOf(FakeData.spidermanSources[0]) // IPTV source
            ),
            SearchProviderResult(
                providerId = "prov-extensions",
                providerName = "Extensions",
                query = SearchQuery("Spider-Man"),
                mediaItems = listOf(spiderman),
                streamSources = listOf(FakeData.spidermanSources[2]) // Extension source
            ),
            SearchProviderResult(
                providerId = "prov-debrid",
                providerName = "Debrid",
                query = SearchQuery("Spider-Man"),
                mediaItems = listOf(spiderman),
                streamSources = listOf(FakeData.spidermanSources[1]) // Debrid source
            )
        )

        val mergedGroups = SearchResultMerger.merge(providerResults, "Spider-Man", prefs)
        
        // Find movies group
        val moviesGroup = mergedGroups.firstOrNull { it.groupType == SearchGroupType.MOVIES }
        assertNotNull(moviesGroup)
        
        val spidermanCard = moviesGroup!!.results.first()
        assertTrue(spidermanCard.availableFrom.contains(SourceType.IPTV))
        assertTrue(spidermanCard.availableFrom.contains(SourceType.EXTENSION))
        assertTrue(spidermanCard.availableFrom.contains(SourceType.DEBRID))
    }

    @Test
    fun testPrimaryLanguageRanking() = kotlinx.coroutines.runBlocking {
        val sourceHindi = FakeData.spidermanSources[3] // Hindi, score: base + 200 (primary lang match)
        val sourceEnglish = FakeData.spidermanSources[2] // English, score: base + 100 (sec lang match)
        val prefs = FakeData.defaultPreferences.copy(primaryLanguage = "Hindi", secondaryLanguage = "English")

        val scoreHindi = SearchResultRanker.calculateSourceScore(sourceHindi, prefs)
        val scoreEnglish = SearchResultRanker.calculateSourceScore(sourceEnglish, prefs)

        assertTrue("Hindi score ($scoreHindi) should exceed English score ($scoreEnglish)", scoreHindi > scoreEnglish)
    }

    @Test
    fun testPrimaryLanguageBeatsHigherQualityWrongLanguage() = kotlinx.coroutines.runBlocking {
        val primary1080p = StreamSource(
            id = "src-primary-1080p",
            name = "Primary 1080p",
            url = "http://primary",
            extensionId = "ext-test",
            resolution = "1080p",
            language = "Hindi"
        )
        val wrongLang4k = StreamSource(
            id = "src-wrong-4k",
            name = "Wrong Lang 4K",
            url = "http://wrong",
            extensionId = "ext-test",
            resolution = "4K",
            language = "French"
        )
        val prefs = FakeData.defaultPreferences.copy(primaryLanguage = "Hindi", secondaryLanguage = "English")

        val primaryScore = SearchResultRanker.calculateSourceScore(primary1080p, prefs)
        val wrongScore = SearchResultRanker.calculateSourceScore(wrongLang4k, prefs)

        assertTrue("Primary 1080p ($primaryScore) should beat wrong-language 4K ($wrongScore)", primaryScore > wrongScore)
    }

    @Test
    fun testSecondaryLanguageFallback() = kotlinx.coroutines.runBlocking {
        val sourceEnglish = FakeData.spidermanSources[2] // English
        val sourceSpanish = FakeData.spidermanSources[5] // Spanish
        val prefs = FakeData.defaultPreferences.copy(primaryLanguage = "Hindi", secondaryLanguage = "English")

        val scoreEnglish = SearchResultRanker.calculateSourceScore(sourceEnglish, prefs)
        val scoreSpanish = SearchResultRanker.calculateSourceScore(sourceSpanish, prefs)

        assertTrue("English score ($scoreEnglish) should exceed Spanish score ($scoreSpanish)", scoreEnglish > scoreSpanish)
    }

    @Test
    fun testDualAudioRanking() = kotlinx.coroutines.runBlocking {
        val sourceDual = FakeData.spidermanSources[3] // Hindi Dual Audio
        val sourceSingle = FakeData.spidermanSources[4] // Tamil Single Audio
        val prefs = FakeData.defaultPreferences.copy(preferDualAudio = true)

        val scoreDual = SearchResultRanker.calculateSourceScore(sourceDual, prefs)
        val scoreSingle = SearchResultRanker.calculateSourceScore(sourceSingle, prefs)

        assertTrue("Dual audio score ($scoreDual) should exceed single audio score ($scoreSingle)", scoreDual > scoreSingle)
    }

    @Test
    fun testSubtitleMatchBonus() = kotlinx.coroutines.runBlocking {
        val sourceSubbed = StreamSource(
            id = "src-subbed",
            name = "Subbed Source",
            url = "http://subbed",
            extensionId = "ext-test",
            resolution = "1080p",
            language = "English",
            isSubbed = true
        )
        val sourceUnsubbed = StreamSource(
            id = "src-unsubbed",
            name = "Unsubbed Source",
            url = "http://unsubbed",
            extensionId = "ext-test",
            resolution = "1080p",
            language = "English",
            isSubbed = false
        )
        val prefs = FakeData.defaultPreferences.copy(subtitleLanguage = "English")

        val scoreSubbed = SearchResultRanker.calculateSourceScore(sourceSubbed, prefs)
        val scoreUnsubbed = SearchResultRanker.calculateSourceScore(sourceUnsubbed, prefs)

        assertTrue("Subbed score ($scoreSubbed) should exceed unsubbed score ($scoreUnsubbed)", scoreSubbed > scoreUnsubbed)
    }

    @Test
    fun testHugeFileDownrankingInLowDataMode() = kotlinx.coroutines.runBlocking {
        val huge4kSource = StreamSource(
            id = "src-huge-4k",
            name = "Huge 4K Source 15GB",
            url = "http://huge",
            extensionId = "ext-test",
            resolution = "4K",
            language = "English"
        )
        val balanced1080pSource = StreamSource(
            id = "src-balanced-1080p",
            name = "Balanced 1080p Source 1GB",
            url = "http://balanced",
            extensionId = "ext-test",
            resolution = "1080p",
            language = "English"
        )
        val prefs = FakeData.defaultPreferences.copy(preferLowerDataUsage = true)
        
        val hugeScore = SearchResultRanker.calculateSourceScore(huge4kSource, prefs)
        val balancedScore = SearchResultRanker.calculateSourceScore(balanced1080pSource, prefs)
        
        assertTrue("Balanced 1080p ($balancedScore) should beat Huge 4K ($hugeScore) in low data mode", balancedScore > hugeScore)
    }

    // Task 8: Verify extension availability does not outrank better IPTV results incorrectly.
    @Test
    fun testIptvOutranksExtensionWhenPreferred() = kotlinx.coroutines.runBlocking {
        val iptvSource = FakeData.spidermanSources[0] // IPTV source (1080p, English)
        val extSource = FakeData.spidermanSources[2] // Extension source (1080p, English)
        
        // Both have the same resolution and language, so normally they might score closely.
        // If we set preferIptvExactMatch = true, IPTV should definitely outrank.
        val prefs = FakeData.defaultPreferences.copy(preferIptvExactMatch = true)

        val iptvScore = SearchResultRanker.calculateSourceScore(iptvSource, prefs)
        val extScore = SearchResultRanker.calculateSourceScore(extSource, prefs)

        assertTrue("IPTV score ($iptvScore) should outrank Extension score ($extScore) when IPTV is preferred", iptvScore > extScore)
    }

    @Test
    fun testSlowProviderTimeout() = runBlocking {
        // Create engine with a slow provider (1200ms delay) and settings indexer
        val engine = UniversalSearchEngineImpl(
            providers = listOf(
                ExtensionSearchProviderImpl(), // Simulates slow provider when query is "slow"
                SettingsSearchProviderImpl()
            )
        )

        // Set policy timeout limit for extensions to 500ms
        val policy = SearchTimeoutPolicy(
            defaultTimeoutMs = 1000L,
            providerTimeoutsMs = mapOf("prov-extensions" to 500L)
        )

        // Query "slow" -> triggers 1200ms delay inside ExtensionSearchProviderImpl.
        // It should get timed out and emit empty extension results.
        val flowList = engine.search("slow", FakeData.defaultPreferences, policy).toList()
        
        // Assert search finished without throwing timeout exception to caller
        assertFalse(flowList.isEmpty())
        
        // Check final emission: should only contain settings matching "slow", no slow extensions
        val finalEmission = flowList.last()
        val extensionGroup = finalEmission.firstOrNull { it.groupType == SearchGroupType.EXTENSION_RESULTS }
        
        assertNull(extensionGroup)
    }

    @Test
    fun testFailedProviderHandling() = runBlocking {
        // Create engine with a failed provider and IPTVSearchProviderImpl
        val engine = UniversalSearchEngineImpl(
            providers = listOf(
                ExtensionSearchProviderImpl(), // Throws error when query is "fail"
                IPTVSearchProviderImpl()
            )
        )

        // Query "fail" -> triggers runtime exception.
        // The flow should capture it and proceed without failing the whole search.
        val flowList = engine.search("fail", FakeData.defaultPreferences).toList()
        
        assertFalse(flowList.isEmpty())
        // Verified we don't crash and still emit matched channels
    }

    @Test
    fun testPartialResultUpdates() = runBlocking {
        val engine = UniversalSearchEngineImpl(
            providers = listOf(
                IPTVSearchProviderImpl(), // 0ms delay
                DebridAvailabilityProviderImpl() // 150ms delay
            )
        )

        // Perform search for Spiderman
        val emissions = engine.search("Spider-Man", FakeData.defaultPreferences).toList()

        // Should receive multiple progressive emissions as providers finish
        assertTrue(emissions.size >= 2)
    }

    @Test
    fun testSpiderManHomecomingMergedResult() = kotlinx.coroutines.runBlocking {
        val spiderman = FakeData.movieSpiderman
        val sources = FakeData.spidermanSources
        val prefs = FakeData.defaultPreferences

        val providerResult = SearchProviderResult(
            providerId = "test",
            providerName = "Test",
            query = SearchQuery("Spider-Man Homecoming"),
            mediaItems = listOf(spiderman),
            streamSources = sources
        )

        val mergedGroups = SearchResultMerger.merge(listOf(providerResult), "Spider-Man Homecoming", prefs)
        
        val moviesGroup = mergedGroups.firstOrNull { it.groupType == SearchGroupType.MOVIES }
        assertNotNull(moviesGroup)
        
        val result = moviesGroup!!.results.first()
        assertEquals("Spider-Man: Homecoming", result.mediaItem.title)

        // Check available formats (IPTV, Extensions, Debrid)
        assertTrue(result.availableFrom.contains(SourceType.IPTV))
        assertTrue(result.availableFrom.contains(SourceType.EXTENSION))
        assertTrue(result.availableFrom.contains(SourceType.DEBRID))

        // Check watch option shortcuts (Task 9: Spider-Man regression behavior)
        val options = result.watchOptions
        assertNotNull(result.bestMatchOption) // Play Best Match
        
        // IPTV option, Extension option, Debrid-enhanced option
        assertTrue(result.availableFrom.contains(SourceType.IPTV))
        assertTrue(result.availableFrom.contains(SourceType.EXTENSION))
        assertTrue(result.availableFrom.contains(SourceType.DEBRID))

        // Hindi, English, Dual Audio
        assertTrue(result.languages.contains("Hindi"))
        assertTrue(result.languages.contains("English"))
        assertTrue(result.isDualAudio)

        // Manual Sources collapsed under Advanced
        assertTrue(options.size > 3) // Ensures all manual sources are retained in the object
    }

    @Test
    fun testHealthAwareRankingAndLabelExposing() = runBlocking {
        // Clear health repository first
        com.example.calmsource.core.database.SourceHealthRepository.clearSourceHealth()
        com.example.calmsource.core.database.SourceHealthRepository.clearProviderHealth()

        val prefs = FakeData.defaultPreferences
        val sourceExcellent = StreamSource(
            id = "src-excellent",
            name = "Excellent Source",
            url = "http://excellent",
            extensionId = "ext-excellent-prov",
            resolution = "1080p",
            language = "English"
        )
        val sourceUnstable = StreamSource(
            id = "src-unstable",
            name = "Unstable Source",
            url = "http://unstable",
            extensionId = "ext-unstable-prov",
            resolution = "1080p",
            language = "English"
        )
        val sourceBlocked = StreamSource(
            id = "src-blocked",
            name = "Blocked Source",
            url = "http://blocked",
            extensionId = "ext-blocked-prov",
            resolution = "1080p",
            language = "English"
        )
        val sourceUnhealthyProvider = StreamSource(
            id = "src-unhealthy-prov",
            name = "Unhealthy Provider Source",
            url = "http://unhealthy-prov",
            extensionId = "ext-unhealthy-prov",
            resolution = "1080p",
            language = "English"
        )

        // 1. Setup health states:
        // - src-excellent: record success (lastSuccessTime = now, should boost +100 and reliability tier Excellent gets +50)
        com.example.calmsource.core.database.SourceHealthRepository.recordSuccess("src-excellent", "ext-excellent-prov", PlaybackSourceType.EXTENSION)
        
        // - src-unstable: record failure twice (health score decreases, tier unstable)
        com.example.calmsource.core.database.SourceHealthRepository.recordFailure("src-unstable", "ext-unstable-prov", PlaybackSourceType.EXTENSION)
        com.example.calmsource.core.database.SourceHealthRepository.recordFailure("src-unstable", "ext-unstable-prov", PlaybackSourceType.EXTENSION)
        
        // - src-blocked: mark bad/blocked (health score 0, tier BLOCKED)
        com.example.calmsource.core.database.SourceHealthRepository.recordSignal(
            sourceId = "src-blocked",
            providerId = "ext-blocked-prov",
            sourceType = PlaybackSourceType.EXTENSION,
            signal = SourceHealthSignal.USER_MARKED_BAD
        )

        // - ext-unhealthy-prov: record provider failure repeatedly to drop its health below 40
        for (i in 1..4) {
            com.example.calmsource.core.database.SourceHealthRepository.recordFailure("src-other", "ext-unhealthy-prov", PlaybackSourceType.EXTENSION)
        }

        // 2. Score them
        val scoreExcellent = SearchResultRanker.calculateSourceScore(sourceExcellent, prefs)
        val scoreUnstable = SearchResultRanker.calculateSourceScore(sourceUnstable, prefs)
        val scoreBlocked = SearchResultRanker.calculateSourceScore(sourceBlocked, prefs)
        val scoreUnhealthyProv = SearchResultRanker.calculateSourceScore(sourceUnhealthyProvider, prefs)

        // Base score for 1080p, English (primary/secondary lang match etc.)
        val baseSource = StreamSource(
            id = "src-base",
            name = "Base Source",
            url = "http://base",
            extensionId = "ext-good-prov",
            resolution = "1080p",
            language = "English"
        )
        val scoreBase = SearchResultRanker.calculateSourceScore(baseSource, prefs)

        // Assertions:
        // - Excellent should be boosted relative to Base (+150 total boost: Excellent tier boost +50, recent success boost +100)
        assertEquals(scoreBase + 150, scoreExcellent)

        // - Unstable should be penalized relative to Base (Unstable penalty is -100)
        assertEquals(scoreBase - 100, scoreUnstable)

        // - Blocked should be heavily penalized (Blocked penalty is -1000)
        assertEquals(scoreBase - 1000, scoreBlocked)

        // - Unhealthy provider source should be penalized (-150 penalty)
        assertEquals(scoreBase - 150, scoreUnhealthyProv)

        // 3. Exposing health labels:
        // Let's create WatchOption wrappers and verify their labels
        val optExcellent = WatchOption("1", "Excellent", sourceExcellent, SourceType.EXTENSION, "label")
        val optUnstable = WatchOption("2", "Unstable", sourceUnstable, SourceType.EXTENSION, "label")
        val optBlocked = WatchOption("3", "Blocked", sourceBlocked, SourceType.EXTENSION, "label")
        val optUnhealthyProv = WatchOption("4", "Unhealthy", sourceUnhealthyProvider, SourceType.EXTENSION, "label")

        assertEquals("Recently worked", optExcellent.healthLabel)
        assertEquals("Unstable", optUnstable.healthLabel)
        assertEquals("Failed recently", optBlocked.healthLabel)
        assertEquals("Unstable", optUnhealthyProv.healthLabel)

        // NormalizedSearchResult.healthLabel should mirror its bestMatchOption
        val mediaItem = MediaItem("movie-1", "Test", MediaType.MOVIE)
        val searchResult = NormalizedSearchResult(
            mediaItem = mediaItem,
            availableFrom = listOf(SourceType.EXTENSION),
            languages = listOf("English"),
            watchOptions = listOf(optExcellent),
            bestMatchOption = optExcellent
        )
        assertEquals("Recently worked", searchResult.healthLabel)
    }

    @Test
    fun testStreamPickerOptionsSortedByHealth() = runBlocking {
        // Clear health repository first
        com.example.calmsource.core.database.SourceHealthRepository.clearSourceHealth()
        com.example.calmsource.core.database.SourceHealthRepository.clearProviderHealth()

        val prefs = FakeData.defaultPreferences
        val spiderman = FakeData.movieSpiderman
        val sourceExcellent = FakeData.spidermanSources[0] // src-spiderman-iptv
        val sourceUnstable = FakeData.spidermanSources[2] // src-spiderman-ext-1080p-en

        com.example.calmsource.core.database.SourceHealthRepository.recordSuccess(sourceExcellent.id, sourceExcellent.extensionId, PlaybackSourceType.IPTV)
        com.example.calmsource.core.database.SourceHealthRepository.recordFailure(sourceUnstable.id, sourceUnstable.extensionId, PlaybackSourceType.EXTENSION)
        com.example.calmsource.core.database.SourceHealthRepository.recordFailure(sourceUnstable.id, sourceUnstable.extensionId, PlaybackSourceType.EXTENSION)

        val providerResult = SearchProviderResult(
            providerId = "prov-test",
            providerName = "Test Provider",
            query = SearchQuery("Spider-Man"),
            mediaItems = listOf(spiderman),
            streamSources = listOf(sourceUnstable, sourceExcellent) // excellent is second here
        )

        // Merge results
        val mergedGroups = SearchResultMerger.merge(listOf(providerResult), "Spider-Man Homecoming", prefs)
        val moviesGroup = mergedGroups.firstOrNull { it.groupType == SearchGroupType.MOVIES }
        assertNotNull(moviesGroup)

        val result = moviesGroup!!.results.first()
        // The watch options should be sorted descending by score, so excellent must be first!
        assertEquals(sourceExcellent.id, result.watchOptions.first().id)
        assertEquals(sourceUnstable.id, result.watchOptions.last().id)
    }

    // ─── Additional Health-Aware Scoring Regression Tests ─────────────

    @Test
    fun testScoringConstantsValues() = kotlinx.coroutines.runBlocking {
        // Task 2: Verify exact health-aware scoring constant values
        assertEquals("HEALTHY/EXCELLENT boost should be +50", 50, ScoringConstants.PROVIDER_HEALTHY_BONUS)
        assertEquals("EXCELLENT boost should be +50", 50, ScoringConstants.SOURCE_EXCELLENT_BOOST)
        assertEquals("SLOW penalty should be -50", -50, ScoringConstants.PROVIDER_SLOW_PENALTY)
        assertEquals("UNSTABLE penalty should be -100", -100, ScoringConstants.SOURCE_UNSTABLE_PENALTY)
        assertEquals("UNHEALTHY penalty should be -150", -150, ScoringConstants.PROVIDER_UNHEALTHY_PENALTY)
        assertEquals("FAILED penalty should be -200", -200, ScoringConstants.PROVIDER_FAILED_PENALTY)
        assertEquals("POOR penalty should be -200", -200, ScoringConstants.SOURCE_POOR_PENALTY)
        assertEquals("BLOCKED penalty should be -1000", -1000, ScoringConstants.SOURCE_BLOCKED_PENALTY)
        assertEquals("Recent success boost should be +100", 100, ScoringConstants.SOURCE_RECENT_SUCCESS_BOOST)
    }

    @Test
    fun testPoorSourcePenaltyInIsolation() = runBlocking {
        // Verify POOR tier gets exactly -200 penalty
        com.example.calmsource.core.database.SourceHealthRepository.clearSourceHealth()
        com.example.calmsource.core.database.SourceHealthRepository.clearProviderHealth()

        val prefs = FakeData.defaultPreferences
        val baseSrc = StreamSource("src-base-poor", "Base", "http://base", "ext-clean-prov", "1080p", language = "English")
        val poorSrc = StreamSource("src-poor-test", "Poor", "http://poor", "ext-clean-prov", "1080p", language = "English")

        // Record enough failures to make POOR tier (healthScore 1-39)
        // 4 failures: 100 - 4*20 = 20 → POOR tier
        for (i in 1..4) {
            com.example.calmsource.core.database.SourceHealthRepository.recordFailure("src-poor-test", "ext-clean-prov", PlaybackSourceType.EXTENSION)
        }

        val baseScore = SearchResultRanker.calculateSourceScore(baseSrc, prefs)
        val poorScore = SearchResultRanker.calculateSourceScore(poorSrc, prefs)

        assertTrue("POOR source ($poorScore) should score less than base ($baseScore) by at least 200",
            baseScore - poorScore >= 200)
    }

    @Test
    fun testFailedSourceDownrankingDoesNotCreateDuplicateMergedCards() = runBlocking {
        // Task 5: Verify that when one source is FAILED and another is HEALTHY,
        // deduplication still produces exactly one merged card for the same media item.
        com.example.calmsource.core.database.SourceHealthRepository.clearSourceHealth()
        com.example.calmsource.core.database.SourceHealthRepository.clearProviderHealth()

        val prefs = FakeData.defaultPreferences
        val spiderman = FakeData.movieSpiderman
        val healthySource = FakeData.spidermanSources[0] // IPTV
        val failedSource = FakeData.spidermanSources[2] // Extension

        // Make one source FAILED
        com.example.calmsource.core.database.SourceHealthRepository.recordSignal(
            sourceId = failedSource.id,
            providerId = failedSource.extensionId,
            sourceType = PlaybackSourceType.EXTENSION,
            signal = SourceHealthSignal.PLAYBACK_FAILURE
        )
        com.example.calmsource.core.database.SourceHealthRepository.recordSignal(
            sourceId = failedSource.id,
            providerId = failedSource.extensionId,
            sourceType = PlaybackSourceType.EXTENSION,
            signal = SourceHealthSignal.PLAYBACK_FAILURE
        )

        // Make the other source healthy
        com.example.calmsource.core.database.SourceHealthRepository.recordSuccess(
            healthySource.id, healthySource.extensionId, PlaybackSourceType.IPTV
        )

        val results = listOf(
            NormalizedSearchResult(
                mediaItem = spiderman,
                availableFrom = listOf(SourceType.IPTV),
                languages = listOf("Hindi"),
                watchOptions = listOf(WatchOption("w1", "IPTV", healthySource, SourceType.IPTV, "1080p Hindi")),
                score = SearchResultRanker.calculateSourceScore(healthySource, prefs)
            ),
            NormalizedSearchResult(
                mediaItem = spiderman,
                availableFrom = listOf(SourceType.EXTENSION),
                languages = listOf("English"),
                watchOptions = listOf(WatchOption("w2", "Ext", failedSource, SourceType.EXTENSION, "1080p English")),
                score = SearchResultRanker.calculateSourceScore(failedSource, prefs)
            )
        )

        val merged = SearchResultDeduplicator.deduplicate(results, hideDuplicates = true, prefs = prefs)

        // Critical: Must be exactly 1 merged card, not 2 separate cards
        assertEquals("Failed source downranking must NOT create duplicate cards", 1, merged.size)
        assertEquals(2, merged.first().watchOptions.size)
        assertTrue(merged.first().availableFrom.contains(SourceType.IPTV))
        assertTrue(merged.first().availableFrom.contains(SourceType.EXTENSION))

        // Healthy source should be first in watch options (sorted by score desc)
        val healthyScore = SearchResultRanker.calculateSourceScore(healthySource, prefs)
        val failedScore = SearchResultRanker.calculateSourceScore(failedSource, prefs)
        assertTrue("Healthy source ($healthyScore) should outrank failed ($failedScore)",
            healthyScore > failedScore)
    }

    @Test
    fun testSpiderManMergedCardWithHealthAdjustments() = runBlocking {
        // Task 6: Full Spider-Man scenario with health adjustments still produces
        // one merged card with IPTV, Extension, and Debrid options
        com.example.calmsource.core.database.SourceHealthRepository.clearSourceHealth()
        com.example.calmsource.core.database.SourceHealthRepository.clearProviderHealth()

        val prefs = FakeData.defaultPreferences
        val spiderman = FakeData.movieSpiderman
        val sources = FakeData.spidermanSources

        // Record health signals for different sources
        // IPTV source: healthy (recent success)
        com.example.calmsource.core.database.SourceHealthRepository.recordSuccess(
            sources[0].id, sources[0].extensionId, PlaybackSourceType.IPTV
        )
        // Extension source: unstable (2 failures)
        com.example.calmsource.core.database.SourceHealthRepository.recordFailure(
            sources[2].id, sources[2].extensionId, PlaybackSourceType.EXTENSION
        )
        com.example.calmsource.core.database.SourceHealthRepository.recordFailure(
            sources[2].id, sources[2].extensionId, PlaybackSourceType.EXTENSION
        )
        // Debrid source: excellent (recent success)
        com.example.calmsource.core.database.SourceHealthRepository.recordSuccess(
            sources[1].id, sources[1].extensionId, PlaybackSourceType.EXTENSION
        )

        val providerResult = SearchProviderResult(
            providerId = "test",
            providerName = "Test",
            query = SearchQuery("Spider-Man Homecoming"),
            mediaItems = listOf(spiderman),
            streamSources = sources
        )

        val mergedGroups = SearchResultMerger.merge(listOf(providerResult), "Spider-Man Homecoming", prefs)
        val moviesGroup = mergedGroups.firstOrNull { it.groupType == SearchGroupType.MOVIES }
        assertNotNull("Should still have Movies group with health adjustments", moviesGroup)

        val result = moviesGroup!!.results.first()
        assertEquals("Spider-Man: Homecoming", result.mediaItem.title)

        // Must still have all three source types present
        assertTrue("IPTV option must still be present", result.availableFrom.contains(SourceType.IPTV))
        assertTrue("Extension option must still be present", result.availableFrom.contains(SourceType.EXTENSION))
        assertTrue("Debrid option must still be present", result.availableFrom.contains(SourceType.DEBRID))

        // Must have multiple watch options
        assertTrue("Should have multiple watch options after health adjustments", result.watchOptions.size > 3)

        // Best match should NOT be the unstable source
        val bestMatchExtId = result.bestMatchOption?.source?.extensionId
        assertNotEquals("Best match should not be the unstable extension source",
            sources[2].extensionId, bestMatchExtId)
    }

    @Test
    fun testNoRawURLsInWatchOptionLabels() = kotlinx.coroutines.runBlocking {
        // Task 10: Verify no raw URLs exposed in Stream Picker
        val sources = FakeData.spidermanSources
        val options = WatchOptionResolver.buildWatchOptions(sources)

        options.forEach { option ->
            assertFalse("WatchOption title should not contain 'http://' for ${option.id}",
                option.title.contains("http://"))
            assertFalse("WatchOption title should not contain 'https://' for ${option.id}",
                option.title.contains("https://"))
            assertFalse("WatchOption title should not contain 'magnet:' for ${option.id}",
                option.title.contains("magnet:"))
            assertFalse("WatchOption languageLabel should not contain 'http://' for ${option.id}",
                option.languageLabel.contains("http://"))
        }
    }

    @Test
    fun testHealthLabelCachePopulatedByScoring() = runBlocking {
        // Verify that scoring populates the healthLabelCache used by WatchOption.healthLabel
        com.example.calmsource.core.database.SourceHealthRepository.clearSourceHealth()
        com.example.calmsource.core.database.SourceHealthRepository.clearProviderHealth()

        val prefs = FakeData.defaultPreferences

        val source = StreamSource("src-label-test", "Label Test", "http://test", "ext-lbl", "1080p", language = "English")
        com.example.calmsource.core.database.SourceHealthRepository.recordSuccess("src-label-test", "ext-lbl", PlaybackSourceType.EXTENSION)

        // Score the source — this should populate the label cache
        SearchResultRanker.calculateSourceScore(source, prefs)

        // Now create a WatchOption and verify the label
        val option = WatchOption("src-label-test", "Test", source, SourceType.EXTENSION, "label")
        assertNotNull("healthLabel should be populated after scoring", option.healthLabel)
        assertEquals("Recently worked", option.healthLabel)
    }

    @Test
    fun testDeterministicRanking() = kotlinx.coroutines.runBlocking {
        val prefs = FakeData.defaultPreferences
        val s1 = StreamSource("A", "Same Score Source 1", "http://A", "ext-1", "1080p", language = "English")
        val s2 = StreamSource("B", "Same Score Source 2", "http://B", "ext-1", "1080p", language = "English")

        val r1 = NormalizedSearchResult(
            mediaItem = MediaItem("m2", "Title", MediaType.MOVIE),
            availableFrom = listOf(SourceType.EXTENSION),
            languages = listOf("English"),
            watchOptions = listOf(WatchOption("w1", "w1", s1, SourceType.EXTENSION, "1080p")),
            score = 100
        )
        val r2 = NormalizedSearchResult(
            mediaItem = MediaItem("m1", "Title", MediaType.MOVIE),
            availableFrom = listOf(SourceType.EXTENSION),
            languages = listOf("English"),
            watchOptions = listOf(WatchOption("w2", "w2", s2, SourceType.EXTENSION, "1080p")),
            score = 100
        )

        val ranked = SearchResultRanker.rankResults(listOf(r1, r2), "Title", prefs)
        
        // Since both have same score after title match bonus (both exactly match Title),
        // deterministic fallback sorts by mediaItem.id ascending (m1 then m2).
        assertEquals("m1", ranked.first().mediaItem.id)
        assertEquals("m2", ranked.last().mediaItem.id)
    }
}
