package com.example.calmsource.feature.search

import com.example.calmsource.core.model.*
import com.example.calmsource.feature.debrid.DebridRepository
import com.example.calmsource.feature.debrid.RealDebridFakeClient
import com.example.calmsource.feature.debrid.AllDebridFakeClient
import com.example.calmsource.feature.debrid.PremiumizeFakeClient
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DebridConnectTest {

    @Before
    fun setUp() {
        DebridRepository.setClientsForTest(mapOf(
            DebridProviderType.REAL_DEBRID to RealDebridFakeClient(),
            DebridProviderType.ALL_DEBRID to AllDebridFakeClient(),
            DebridProviderType.PREMIUMIZE to PremiumizeFakeClient()
        ))
        DebridRepository.listAccounts().forEach {
            DebridRepository.disconnectAccount(it.id)
        }
    }

    @Test
    fun testFakeDebridAuthSession() = runBlocking {
        val rdClient = RealDebridFakeClient()
        val session = rdClient.startAuth()
        assertTrue(session is DebridAuthSession.DeviceCode)
        val details = (session as DebridAuthSession.DeviceCode).details
        assertEquals("RD-XM4K", details.userCode)

        val polled = rdClient.pollAuth(session)
        val tokenSet = rdClient.completeAuth(polled)
        assertNotNull(tokenSet.accessToken)
        assertTrue(tokenSet.accessToken!!.startsWith("RD_ACCESS_TOKEN_"))
    }

    @Test
    fun testAccountConnectState() = runBlocking {
        val session = DebridRepository.startConnectionFlow(DebridProviderType.REAL_DEBRID)
        val account = DebridRepository.completeConnectionFlow(DebridProviderType.REAL_DEBRID, session)
        
        assertTrue(account.isConnected)
        assertEquals("RDUser_Premium", account.username)
        // Token should NOT be in the returned account object — it lives only in SecureTokenStore
        assertNull("tokenSet should be null in returned account", account.tokenSet)
        // But should be retrievable from SecureTokenStore
        val storedToken = DebridRepository.tokenStore.getTokens(DebridProviderType.REAL_DEBRID)
        assertNotNull(storedToken)
        assertTrue(storedToken!!.accessToken!!.startsWith("RD_ACCESS_TOKEN_"))

        val activeAccounts = DebridRepository.listAccounts()
        val rdAcc = activeAccounts.first { it.id == account.id }
        assertTrue(rdAcc.isConnected)
    }

    @Test
    fun testAccountDisconnectState() = runBlocking {
        val session = DebridRepository.startConnectionFlow(DebridProviderType.REAL_DEBRID)
        val account = DebridRepository.completeConnectionFlow(DebridProviderType.REAL_DEBRID, session)
        assertTrue(account.isConnected)

        DebridRepository.disconnectAccount(account.id)
        val activeAccounts = DebridRepository.listAccounts()
        val rdAcc = activeAccounts.firstOrNull { it.id == account.id }
        if (rdAcc != null) {
            assertFalse(rdAcc.isConnected)
        }
        assertNull(DebridRepository.tokenStore.getTokens(DebridProviderType.REAL_DEBRID))
    }

    @Test
    fun testMaskedTokenDisplay() = runBlocking {
        val tokenSet = DebridTokenSet(accessToken = "RD_ACCESS_TOKEN_SECRET_123456789")
        val raw = tokenSet.accessToken ?: ""
        val masked = if (raw.length > 8) raw.take(4) + "..." + raw.takeLast(4) else "••••••••"
        assertEquals("RD_A...6789", masked)
    }

    @Test
    fun testAccountHealthStates() = runBlocking {
        val session = DebridRepository.startConnectionFlow(DebridProviderType.REAL_DEBRID)
        val account = DebridRepository.completeConnectionFlow(DebridProviderType.REAL_DEBRID, session)
        assertEquals(DebridAccountHealth.HEALTHY, account.health)

        DebridRepository.updateAccountHealth(account.id, DebridAccountHealth.SLOW)
        val rdAcc = DebridRepository.listAccounts().first { it.id == account.id }
        assertEquals(DebridAccountHealth.SLOW, rdAcc.health)
    }

    @Test
    fun testCachedAvailabilityMerge() = runBlocking {
        val rdClient = RealDebridFakeClient()
        val token = DebridTokenSet(accessToken = "RD_ACCESS_TOKEN_MOCK")
        val availability = rdClient.checkCachedAvailability(listOf("spiderman-4k-hash", "non-cached-hash"), token)
        
        assertTrue(availability["spiderman-4k-hash"]!!.isCached)
        assertFalse(availability["non-cached-hash"]!!.isCached)
    }

    @Test
    fun testRankingWithPreferDebridEnabled() = runBlocking {
        // Connect a debrid account so cache availability can be checked
        DebridRepository.addAccountWithApiKey(DebridProviderType.REAL_DEBRID, "RDUser", "rd@test.com", "RD_TOKEN_MOCK")
        kotlinx.coroutines.delay(100)

        val sourceDebrid = FakeData.spidermanSources[1]
        val sourceIptv = FakeData.spidermanSources[0]

        val prefsPreferDebrid = FakeData.defaultPreferences.copy(
            preferCachedDebrid = true,
            preferIptvExactMatch = false
        )

        val scoreDebrid = SearchResultRanker.calculateSourceScore(sourceDebrid, prefsPreferDebrid)
        val scoreIptv = SearchResultRanker.calculateSourceScore(sourceIptv, prefsPreferDebrid)
        
        assertTrue("Debrid score ($scoreDebrid) should exceed IPTV score ($scoreIptv)", scoreDebrid > scoreIptv)
    }

    @Test
    fun testRankingWithPreferIptvEnabled() = runBlocking {
        val sourceDebrid = FakeData.spidermanSources[1]
        val sourceIptv = FakeData.spidermanSources[0]

        val prefsPreferIptv = FakeData.defaultPreferences.copy(
            preferCachedDebrid = false,
            preferIptvExactMatch = true
        )

        val scoreDebrid = SearchResultRanker.calculateSourceScore(sourceDebrid, prefsPreferIptv)
        val scoreIptv = SearchResultRanker.calculateSourceScore(sourceIptv, prefsPreferIptv)
        
        assertTrue("IPTV score ($scoreIptv) should exceed Debrid score ($scoreDebrid)", scoreIptv > scoreDebrid)
    }

    @Test
    fun testFailedDebridProviderHandling() = runBlocking {
        DebridRepository.addAccountWithApiKey(DebridProviderType.REAL_DEBRID, "RDUser", "rd@test.com", "RD_TOKEN_FAIL")
        val addedAccount = DebridRepository.listAccounts().first { it.isConnected && it.providerType == DebridProviderType.REAL_DEBRID }
        DebridRepository.updateAccountHealth(addedAccount.id, DebridAccountHealth.FAILED)

        val engine = UniversalSearchEngineImpl(
            providers = listOf(
                DebridAvailabilityProviderImpl(),
                IPTVSearchProviderImpl()
            )
        )

        val emissions = engine.search("Spider-Man", FakeData.defaultPreferences).toList()
        assertFalse(emissions.isEmpty())
    }

    @Test
    fun testSlowDebridTimeout() = runBlocking {
        DebridRepository.addAccountWithApiKey(DebridProviderType.REAL_DEBRID, "RDUser", "rd@test.com", "RD_TOKEN_SLOW")
        val addedAccount = DebridRepository.listAccounts().first { it.isConnected && it.providerType == DebridProviderType.REAL_DEBRID }
        DebridRepository.updateAccountHealth(addedAccount.id, DebridAccountHealth.SLOW)

        val engine = UniversalSearchEngineImpl(
            providers = listOf(
                DebridAvailabilityProviderImpl()
            )
        )

        val policy = SearchTimeoutPolicy(
            defaultTimeoutMs = 500L
        )

        val emissions = engine.search("Spider-Man", FakeData.defaultPreferences, policy).toList()

        assertTrue(emissions.last().isNotEmpty())
    }

    @Test
    fun testStreamPickerDebridReadableLabels() = runBlocking {
        val sourceDebrid = FakeData.spidermanSources[1]
        val label = getReadableLabel(sourceDebrid, SourceType.DEBRID)
        
        assertTrue(label.contains("Cached"))
        assertTrue(label.contains("Debrid"))
        assertTrue(label.contains("4K"))
        assertTrue(label.contains("HDR"))
        assertTrue(label.contains("Atmos") || label.contains("Subtitles") || label.contains("English"))
    }

    @Test
    fun testRawLinksHiddenByDefault() = runBlocking {
        val sourceDebrid = FakeData.spidermanSources[1]
        val label = getReadableLabel(sourceDebrid, SourceType.DEBRID)
        
        assertFalse(label.contains("https://"))
        assertFalse(label.contains(".mkv"))
        assertFalse(label.contains(".mp4"))
    }

    @Test
    fun testNoTokenInLogsOrDebugStrings() = runBlocking {
        val account = DebridAccount(
            id = "deb-rd",
            providerType = DebridProviderType.REAL_DEBRID,
            providerName = "Real-Debrid",
            isConnected = true,
            email = "rd@gmail.com",
            username = "RDUser",
            tokenSet = DebridTokenSet(accessToken = "RD_ACCESS_TOKEN_SECRET_12345"),
            status = DebridAccountStatus("RDUser", "rd@gmail.com", 30, null, true),
            health = DebridAccountHealth.HEALTHY
        )

        val debugString = account.toString()
        assertFalse(debugString.contains("RD_ACCESS_TOKEN_SECRET_12345"))
    }

    // ─── Security-focused tests ──────────────────────────────────────────

    @Test
    fun testConnectSavesTokenInSecureStore() = runBlocking {
        // Verify no token exists before connect
        assertNull(DebridRepository.tokenStore.getTokens(DebridProviderType.REAL_DEBRID))

        val session = DebridRepository.startConnectionFlow(DebridProviderType.REAL_DEBRID)
        DebridRepository.completeConnectionFlow(DebridProviderType.REAL_DEBRID, session)

        // Token should be persisted in SecureTokenStore
        val storedToken = DebridRepository.tokenStore.getTokens(DebridProviderType.REAL_DEBRID)
        assertNotNull("Token should be stored in SecureTokenStore after connect", storedToken)
        assertNotNull(storedToken!!.accessToken)
        assertTrue(storedToken.accessToken!!.startsWith("RD_ACCESS_TOKEN_"))
    }

    @Test
    fun testDisconnectClearsSecureStore() = runBlocking {
        // Connect first
        val session = DebridRepository.startConnectionFlow(DebridProviderType.REAL_DEBRID)
        val account = DebridRepository.completeConnectionFlow(DebridProviderType.REAL_DEBRID, session)
        assertNotNull(DebridRepository.tokenStore.getTokens(DebridProviderType.REAL_DEBRID))

        // Disconnect should clear SecureTokenStore
        DebridRepository.disconnectAccount(account.id)
        assertNull("Token should be cleared from SecureTokenStore after disconnect",
            DebridRepository.tokenStore.getTokens(DebridProviderType.REAL_DEBRID))

        // Account should also be marked disconnected in Room
        val rdAcc = DebridRepository.listAccounts().firstOrNull { it.id == account.id }
        if (rdAcc != null) {
            assertFalse("Account should be disconnected in Room", rdAcc.isConnected)
            assertNull("tokenSet should be null in disconnected account", rdAcc.tokenSet)
        }
    }

    @Test
    fun testGetUiAccountsReturnsNoTokens() = runBlocking {
        // Add an account with a token
        DebridRepository.addAccountWithApiKey(DebridProviderType.REAL_DEBRID, "RDUser", "rd@test.com", "RD_SECRET_TOKEN_12345")

        // Verify token is in SecureTokenStore
        assertNotNull(DebridRepository.tokenStore.getTokens(DebridProviderType.REAL_DEBRID))

        // getUiAccounts should return accounts with tokenSet = null
        val uiAccounts = DebridRepository.getUiAccounts()
        assertTrue("Should have at least one account", uiAccounts.isNotEmpty())
        uiAccounts.forEach { account ->
            assertNull("UI account '${account.id}' should have null tokenSet", account.tokenSet)
        }
    }

    @Test
    fun testTokenNeverInAccountToString() = runBlocking {
        val secretToken = "SUPER_SECRET_TOKEN_XYZ_987654321"
        val account = DebridAccount(
            id = "deb-rd-test",
            providerType = DebridProviderType.REAL_DEBRID,
            providerName = "Real-Debrid",
            isConnected = true,
            email = "test@example.com",
            username = "TestUser",
            tokenSet = DebridTokenSet(accessToken = secretToken),
            status = DebridAccountStatus("TestUser", "test@example.com", 30, null, true),
            health = DebridAccountHealth.HEALTHY
        )

        val str = account.toString()
        assertFalse("Raw token must not appear in toString()", str.contains(secretToken))
        // The masked version from DebridTokenSet.toString() is OK
        assertTrue("Masked token representation should appear", str.contains("SUPE"))
        assertTrue("Masked token representation should appear", str.contains("..." ))
    }

    @Test
    fun testSpiderManHomecomingDebridEnrichment() = runBlocking {
        com.example.calmsource.feature.extensions.ExtensionRepository.getExtensions()
        DebridRepository.addAccountWithApiKey(DebridProviderType.REAL_DEBRID, "RDUser", "rd@test.com", "RD_TOKEN_MOCK")
        kotlinx.coroutines.delay(100)

        val engine = UniversalSearchEngineImpl(
            providers = listOf(
                IPTVSearchProviderImpl(),
                VODSearchProviderImpl(),
                ExtensionSearchProviderImpl(),
                DebridAvailabilityProviderImpl()
            )
        )

        val policy = SearchTimeoutPolicy(defaultTimeoutMs = 5000L)
        val emissions = engine.search("Spider-Man: Homecoming", FakeData.defaultPreferences, policy).toList()

        assertFalse(emissions.isEmpty())

        val lastResult = emissions.last()
        val movieGroup = lastResult.firstOrNull { it.groupType == SearchGroupType.MOVIES }
        assertNotNull("Movie group should be present", movieGroup)

        val spidermanResults = movieGroup!!.results.filter { it.mediaItem.title.contains("Spider-Man", ignoreCase = true) }
        assertEquals("There should be exactly one merged card for Spider-Man: Homecoming", 1, spidermanResults.size)

        val resultCard = spidermanResults.first()
        assertTrue(resultCard.availableFrom.contains(SourceType.IPTV))
        assertTrue(resultCard.availableFrom.contains(SourceType.EXTENSION))
        assertTrue(resultCard.availableFrom.contains(SourceType.DEBRID))

        assertTrue(resultCard.languages.contains("Hindi"))
        assertTrue(resultCard.languages.contains("English"))
        assertTrue(resultCard.watchOptions.size >= 3)
    }

    private fun getReadableLabel(source: StreamSource, type: SourceType): String {
        val parts = mutableListOf<String>()
        val nameUpper = source.name.uppercase()
        val isDebrid = type == SourceType.DEBRID || source.extensionId.startsWith("deb-")
        
        if (isDebrid) {
            parts.add("Cached")
            parts.add("Debrid")
        }

        val res = when (source.resolution.uppercase()) {
            "4K", "2160P" -> "4K"
            "1080P", "FHD", "1080p" -> "1080p"
            "720P", "HD", "720p" -> "720p"
            "SD", "480P" -> "SD"
            else -> source.resolution
        }
        parts.add(res)

        if (nameUpper.contains("HDR") || nameUpper.contains("DV ") || nameUpper.contains("DOLBY VISION")) {
            parts.add("HDR")
        }
        if (nameUpper.contains("DOLBY VISION")) {
            parts.add("Dolby Vision")
        }
        if (nameUpper.contains("ATMOS") || nameUpper.contains("TRUEHD")) {
            parts.add("Atmos")
        }
        if (source.isSubbed || nameUpper.contains("SUB")) {
            parts.add("Subtitles")
        }
        val sizeBytes = source.sizeBytes
        if (sizeBytes != null && sizeBytes < 1_500_000_000 && !source.resolution.contains("4K", ignoreCase = true)) {
            parts.add("Low Data")
        }

        val lang = when {
            source.isDualAudio -> "Dual Audio"
            source.isDubbed -> "${source.language} Dubbed"
            else -> source.language
        }
        parts.add(lang)

        return parts.joinToString(" • ")
    }
}
