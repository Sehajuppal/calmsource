package com.example.calmsource.feature.search

import com.example.calmsource.core.model.StreamSource
import com.example.calmsource.core.model.UserPreferences
import com.example.calmsource.core.model.DebridProviderType
import com.example.calmsource.core.database.SourceHealthRepository
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.feature.debrid.DebridRepository
import com.example.calmsource.feature.debrid.RealDebridFakeClient
import com.example.calmsource.feature.debrid.AllDebridFakeClient
import com.example.calmsource.feature.debrid.PremiumizeFakeClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RankerPriorityTest {

    @Before
    fun setup() = runBlocking {
        SourceHealthRepository.clearSourceHealth()
        SourceHealthRepository.clearProviderHealth()
        // Set up fake debrid clients so cache availability checks work
        DebridRepository.setClientsForTest(mapOf(
            DebridProviderType.REAL_DEBRID to RealDebridFakeClient(),
            DebridProviderType.ALL_DEBRID to AllDebridFakeClient(),
            DebridProviderType.PREMIUMIZE to PremiumizeFakeClient()
        ))
        DebridRepository.listAccounts().forEach {
            DebridRepository.disconnectAccount(it.id)
        }
        DebridRepository.addAccountWithApiKey(
            DebridProviderType.REAL_DEBRID, "RDUser", "rd@test.com", "RD_TOKEN_MOCK"
        )
    }

    @Test
    fun `Primary language beats higher quality wrong language`() = runBlocking {
        val prefs = UserPreferences(primaryLanguage = "Hindi", secondaryLanguage = "English")
        val source4kEnglish = StreamSource("src-1", "Movie.Name.2023.2160p.WEB-DL.English.mkv", "http://test1", "ext-1", "4K", language = "English")
        val source1080pHindi = StreamSource("src-2", "Movie.Name.2023.1080p.WEB-DL.Hindi.mkv", "http://test2", "ext-1", "1080p", language = "Hindi")

        val score4kEnglish = SearchResultRanker.calculateSourceScore(source4kEnglish, prefs)
        val score1080pHindi = SearchResultRanker.calculateSourceScore(source1080pHindi, prefs)

        assertTrue("1080p Hindi ($score1080pHindi) must beat 4k English ($score4kEnglish)", score1080pHindi > score4kEnglish)
    }

    @Test
    fun `Dual-audio preference is heavily weighted`() = runBlocking {
        val prefs = UserPreferences(preferDualAudio = true)
        val sourceSingle = StreamSource("src-1", "Movie.1080p.mkv", "http://test1", "ext-1", "1080p", language = "Hindi", isDualAudio = false)
        val sourceDual = StreamSource("src-2", "Movie.1080p.Dual.Audio.mkv", "http://test2", "ext-1", "1080p", language = "Hindi", isDualAudio = true)

        val scoreSingle = SearchResultRanker.calculateSourceScore(sourceSingle, prefs)
        val scoreDual = SearchResultRanker.calculateSourceScore(sourceDual, prefs)

        assertTrue("Dual Audio ($scoreDual) must heavily beat Single Audio ($scoreSingle)", scoreDual > scoreSingle + 50) // Assuming DUAL_AUDIO_BONUS is large
    }

    @Test
    fun `Do not let huge file size automatically beat a balanced 1080p source`() = runBlocking {
        val prefs = UserPreferences(preferHighestQuality = false)
        // Huge 4k remux ~ 60GB vs 1080p ~ 2GB
        val hugeSource = StreamSource("src-1", "Movie.Name.2023.2160p.BluRay.REMUX.60GB.mkv", "http://test1", "ext-1", "4K", language = "English")
        val balancedSource = StreamSource("src-2", "Movie.Name.2023.1080p.WEB-DL.2GB.mkv", "http://test2", "ext-1", "1080p", language = "English")

        val scoreHuge = SearchResultRanker.calculateSourceScore(hugeSource, prefs)
        val scoreBalanced = SearchResultRanker.calculateSourceScore(balancedSource, prefs)

        assertTrue("Balanced 1080p ($scoreBalanced) should beat huge 4k Remux ($scoreHuge) when preferHighestQuality is false", scoreBalanced >= scoreHuge)
    }

    @Test
    fun `Health-aware ranking downranks unhealthy sources`() = runBlocking {
        val prefs = UserPreferences()
        val healthySource = StreamSource("src-1", "Movie.1080p.mkv", "http://test1", "ext-1", "1080p", language = "English")
        val unhealthySource = StreamSource("src-2", "Movie.1080p.mkv", "http://test2", "ext-2", "1080p", language = "English")

        SourceHealthRepository.recordSuccess("src-1", "ext-1", PlaybackSourceType.EXTENSION)
        
        for (i in 1..5) {
            SourceHealthRepository.recordFailure("src-2", "ext-2", PlaybackSourceType.EXTENSION)
        }

        val scoreHealthy = SearchResultRanker.calculateSourceScore(healthySource, prefs)
        val scoreUnhealthy = SearchResultRanker.calculateSourceScore(unhealthySource, prefs)

        assertTrue("Healthy source ($scoreHealthy) must beat unhealthy source ($scoreUnhealthy)", scoreHealthy > scoreUnhealthy + 100)
    }

    @Test
    fun `Cached Debrid availability boosts ranking`() = runBlocking {
        val prefs = UserPreferences(preferCachedDebrid = true)
        val nonCached = StreamSource("src-1", "Movie.1080p.mkv", "http://test1", "deb-1", "1080p", language = "English")
        val cached = StreamSource("src-spiderman-debrid-4k", "Movie.1080p.mkv", "magnet:?xt=urn:btih:spiderman-4k-hash&dn=test", "deb-1", "1080p", language = "English")

        val scoreNonCached = SearchResultRanker.calculateSourceScore(nonCached, prefs)
        val scoreCached = SearchResultRanker.calculateSourceScore(cached, prefs)

        assertTrue("Cached debrid ($scoreCached) must beat non-cached ($scoreNonCached)", scoreCached > scoreNonCached)
    }

    @Test
    fun `Optional IPTV preference boosts exact IPTV VOD`() = runBlocking {
        val prefs = UserPreferences(preferIptvExactMatch = true)
        val iptvSource = StreamSource("src-1", "Movie.1080p.mkv", "http://test1", "iptv-1", "1080p", language = "English")
        val extSource = StreamSource("src-2", "Movie.1080p.mkv", "http://test2", "ext-1", "1080p", language = "English")

        val scoreIptv = SearchResultRanker.calculateSourceScore(iptvSource, prefs)
        val scoreExt = SearchResultRanker.calculateSourceScore(extSource, prefs)

        assertTrue("IPTV VOD ($scoreIptv) must beat extension source ($scoreExt) when preferred", scoreIptv > scoreExt)
    }
}
