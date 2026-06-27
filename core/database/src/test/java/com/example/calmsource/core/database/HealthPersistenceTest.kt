package com.example.calmsource.core.database

import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.SourceHealthSignal
import com.example.calmsource.core.model.generateSafeSourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HealthPersistenceTest {

    @Before
    fun setUp() {
        // Use Unconfined dispatcher for tests to avoid threading issues
        SourceHealthRepository.dispatcher = Dispatchers.Unconfined
        runBlocking {
            SourceHealthRepository.clearSourceHealth()
            SourceHealthRepository.clearProviderHealth()
        }
    }

    // ── Source Health Round-Trip ─────────────────────────────────────────

    @Test
    fun testInsertAndRetrieveSourceHealth() = runBlocking {
        val sourceId = "test-source-id"
        val providerId = "test-provider-id"

        // Verify initially null
        assertNull(SourceHealthRepository.getSourceHealth(sourceId))

        // Record a failure
        SourceHealthRepository.recordFailure(
            sourceId = sourceId,
            providerId = providerId,
            sourceType = PlaybackSourceType.EXTENSION,
            errorCategory = "TIMEOUT"
        )

        val health = SourceHealthRepository.getSourceHealth(sourceId)
        assertNotNull(health)
        assertEquals(sourceId, health?.sourceId)
        assertEquals(providerId, health?.providerId)
        assertEquals(PlaybackSourceType.EXTENSION, health?.sourceType)
        assertEquals(80, health?.healthScore) // 100 - 20
        assertEquals(1, health?.failureCount)
        assertEquals("TIMEOUT", health?.lastErrorCategory)

        // Record success
        SourceHealthRepository.recordSuccess(
            sourceId = sourceId,
            providerId = providerId,
            sourceType = PlaybackSourceType.EXTENSION
        )

        val healthAfterSuccess = SourceHealthRepository.getSourceHealth(sourceId)
        assertNotNull(healthAfterSuccess)
        assertEquals(100, healthAfterSuccess?.healthScore)
        assertEquals(0, healthAfterSuccess?.failureCount)
    }

    @Test
    fun testSourceHealthRoundTrip_allFieldsPreserved() = runBlocking {
        val sourceId = "round-trip-source"
        val providerId = "round-trip-provider"
        val now = System.currentTimeMillis()

        // Record with startup time and buffering severity
        SourceHealthRepository.recordSignal(
            sourceId = sourceId,
            providerId = providerId,
            sourceType = PlaybackSourceType.STREMIO,
            signal = SourceHealthSignal.PLAYBACK_FAILURE,
            timestamp = now,
            errorCategory = "DECODER_ERROR"
        )

        // Now also record startup time
        SourceHealthRepository.recordSignal(
            sourceId = sourceId,
            providerId = providerId,
            sourceType = PlaybackSourceType.STREMIO,
            signal = SourceHealthSignal.STARTUP_TIME,
            timestamp = now,
            startupTimeMs = 2500L
        )

        val health = SourceHealthRepository.getSourceHealth(sourceId, currentTime = now)
        assertNotNull(health)
        assertEquals(sourceId, health!!.sourceId)
        assertEquals(providerId, health.providerId)
        assertEquals(PlaybackSourceType.STREMIO, health.sourceType)
        assertEquals(2500L, health.averageStartupTime)
        assertTrue(health.lastFailureTime > 0)
    }

    // ── Provider Health Round-Trip ──────────────────────────────────────

    @Test
    fun testInsertAndRetrieveProviderHealth() = runBlocking {
        val sourceId = "test-source-id"
        val providerId = "test-provider-id"

        // Record a timeout signal
        SourceHealthRepository.recordSignal(
            sourceId = sourceId,
            providerId = providerId,
            sourceType = PlaybackSourceType.IPTV,
            signal = SourceHealthSignal.PLAYBACK_TIMEOUT
        )

        val providerHealth = SourceHealthRepository.getProviderHealth(providerId)
        assertNotNull(providerHealth)
        assertEquals(providerId, providerHealth?.providerId)
        assertEquals(PlaybackSourceType.IPTV, providerHealth?.sourceType)
        assertEquals(80, providerHealth?.healthScore)
        assertEquals(1, providerHealth?.failureCount)
        assertEquals(1, providerHealth?.timeoutCount)
    }

    @Test
    fun testProviderHealthRoundTrip_successIncrements() = runBlocking {
        val sourceId = "prov-rt-source"
        val providerId = "prov-rt-provider"

        // Record success
        SourceHealthRepository.recordSignal(
            sourceId = sourceId,
            providerId = providerId,
            sourceType = PlaybackSourceType.EXTENSION,
            signal = SourceHealthSignal.PLAYBACK_SUCCESS
        )

        val providerHealth = SourceHealthRepository.getProviderHealth(providerId)
        assertNotNull(providerHealth)
        assertEquals(1, providerHealth!!.successCount)
        assertEquals(0, providerHealth.failureCount)
        assertEquals(100, providerHealth.healthScore)
    }

    // ── Recovery on Read ────────────────────────────────────────────────

    @Test
    fun testTimeBasedRecoveryOnRead() = runBlocking {
        val sourceId = "recovery-source"
        val providerId = "recovery-provider"
        val now = System.currentTimeMillis()

        // Record a failure at now - 2 hours (7200000 ms ago)
        SourceHealthRepository.recordSignal(
            sourceId = sourceId,
            providerId = providerId,
            sourceType = PlaybackSourceType.EXTENSION,
            signal = SourceHealthSignal.PLAYBACK_FAILURE,
            timestamp = now - 7200000L
        )

        // The initial score is 80 (since starting score is 100 - 20)
        // With 2 hours elapsed, it should recover 20 points: 80 + 20 = 100.
        // It should also reset failureCount to 0 when reaching 100.
        val health = SourceHealthRepository.getSourceHealth(sourceId, currentTime = now)
        assertNotNull(health)
        assertEquals(100, health?.healthScore)
        assertEquals(0, health?.failureCount)
    }

    @Test
    fun testProviderRecoveryOnRead() = runBlocking {
        val sourceId = "prov-recovery-source"
        val providerId = "prov-recovery-provider"
        val now = System.currentTimeMillis()

        // Record a failure 3 hours ago
        SourceHealthRepository.recordSignal(
            sourceId = sourceId,
            providerId = providerId,
            sourceType = PlaybackSourceType.IPTV,
            signal = SourceHealthSignal.PLAYBACK_TIMEOUT,
            timestamp = now - 3 * 3600_000L
        )

        // Score was 80, 3 hours elapsed → 80 + 30 = 110, clamped to 100
        val providerHealth = SourceHealthRepository.getProviderHealth(providerId, currentTime = now)
        assertNotNull(providerHealth)
        assertEquals(100, providerHealth?.healthScore)
        assertEquals(0, providerHealth?.failureCount)
        assertEquals(0, providerHealth?.timeoutCount)
    }

    // ── Mark Source Hidden ───────────────────────────────────────────────

    @Test
    fun testMarkSourceHidden() = runBlocking {
        val sourceId = "hidden-source"
        val providerId = "hidden-provider"

        SourceHealthRepository.recordSuccess(
            sourceId = sourceId,
            providerId = providerId,
            sourceType = PlaybackSourceType.EXTENSION
        )

        var health = SourceHealthRepository.getSourceHealth(sourceId)
        assertNotNull(health)
        assertFalse(health!!.userHidden)

        SourceHealthRepository.markSourceHidden(sourceId, true)

        health = SourceHealthRepository.getSourceHealth(sourceId)
        assertNotNull(health)
        assertTrue(health!!.userHidden)
    }

    @Test
    fun testMarkSourceHidden_andUnhide() = runBlocking {
        val sourceId = "toggle-hidden-source"
        val providerId = "toggle-hidden-provider"

        SourceHealthRepository.recordSuccess(
            sourceId = sourceId,
            providerId = providerId,
            sourceType = PlaybackSourceType.IPTV
        )

        SourceHealthRepository.markSourceHidden(sourceId, true)
        var health = SourceHealthRepository.getSourceHealth(sourceId)
        assertTrue(health!!.userHidden)

        SourceHealthRepository.markSourceHidden(sourceId, false)
        health = SourceHealthRepository.getSourceHealth(sourceId)
        assertFalse(health!!.userHidden)
    }

    // ── Reset / Clear Operations ────────────────────────────────────────

    @Test
    fun testClearSourceHealth() = runBlocking {
        SourceHealthRepository.recordSuccess(
            sourceId = "s1", providerId = "p1", sourceType = PlaybackSourceType.EXTENSION
        )
        SourceHealthRepository.recordSuccess(
            sourceId = "s2", providerId = "p2", sourceType = PlaybackSourceType.IPTV
        )

        val before = SourceHealthRepository.getAllSourceHealth()
        assertEquals(2, before.size)

        SourceHealthRepository.clearSourceHealth()

        val after = SourceHealthRepository.getAllSourceHealth()
        assertEquals(0, after.size)
    }

    @Test
    fun testClearProviderHealth() = runBlocking {
        SourceHealthRepository.recordSuccess(
            sourceId = "s1", providerId = "p1", sourceType = PlaybackSourceType.EXTENSION
        )

        assertNotNull(SourceHealthRepository.getProviderHealth("p1"))

        SourceHealthRepository.clearProviderHealth()

        assertNull(SourceHealthRepository.getProviderHealth("p1"))
    }

    // ── No Raw URLs / Secrets in Entities ───────────────────────────────

    @Test
    fun testNoSecretsStored() {
        val forbidden = listOf("url", "token", "password", "secret", "key")

        val sourceFields = com.example.calmsource.core.database.entity.SourceHealthEntity::class.java.declaredFields
        for (field in sourceFields) {
            val nameLower = field.name.lowercase()
            for (kw in forbidden) {
                if (nameLower.contains(kw) && nameLower != "sourceid" && nameLower != "providerid") {
                    org.junit.Assert.fail("SourceHealthEntity contains forbidden field: ${field.name}")
                }
            }
        }

        val providerFields = com.example.calmsource.core.database.entity.ProviderHealthScoreEntity::class.java.declaredFields
        for (field in providerFields) {
            val nameLower = field.name.lowercase()
            for (kw in forbidden) {
                if (nameLower.contains(kw) && nameLower != "providerid") {
                    org.junit.Assert.fail("ProviderHealthScoreEntity contains forbidden field: ${field.name}")
                }
            }
        }
    }

    // ── Safe Source ID ───────────────────────────────────────────────────

    @Test
    fun testSafeSourceIdIsHashNotUrl() {
        val rawUrl = "http://evil.example.com/stream?token=secret123&key=abc"
        val safeId = generateSafeSourceId(rawUrl)

        // The safe ID should NOT contain the raw URL or any part of the token
        assertFalse("Safe ID must not contain raw URL", safeId.contains("evil.example.com"))
        assertFalse("Safe ID must not contain token", safeId.contains("secret123"))
        assertFalse("Safe ID must not contain key", safeId.contains("abc"))
        assertFalse("Safe ID must not contain 'http'", safeId.contains("http"))

        // It should be a fixed-length hex hash (16 chars from SHA-256)
        assertEquals("Safe ID should be 16 hex characters", 16, safeId.length)
        assertTrue("Safe ID must be hex", safeId.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun testSafeSourceIdIsDeterministic() {
        val url = "http://example.com/stream.m3u8"
        val id1 = generateSafeSourceId(url)
        val id2 = generateSafeSourceId(url)
        assertEquals("Same URL must produce same safe ID", id1, id2)
    }

    @Test
    fun testSafeSourceIdDifferentForDifferentUrls() {
        val id1 = generateSafeSourceId("http://a.com/stream1")
        val id2 = generateSafeSourceId("http://b.com/stream2")
        assertTrue("Different URLs must produce different safe IDs", id1 != id2)
    }

    // ── In-Memory Fallback DAO ──────────────────────────────────────────

    @Test
    fun testInMemoryDaoFallback_insertAndRetrieve() = runBlocking {
        // The repository already uses in-memory DAO in test context since
        // DatabaseProvider is not initialized. Verify basic operations work.
        val sourceId = "in-mem-source"
        val providerId = "in-mem-provider"

        SourceHealthRepository.recordSuccess(
            sourceId = sourceId,
            providerId = providerId,
            sourceType = PlaybackSourceType.DEBRID_RESOLVED
        )

        val health = SourceHealthRepository.getSourceHealth(sourceId)
        assertNotNull(health)
        assertEquals(sourceId, health!!.sourceId)
        assertEquals(PlaybackSourceType.DEBRID_RESOLVED, health.sourceType)
        assertEquals(100, health.healthScore)
    }

    @Test
    fun testInMemoryDaoFallback_getAllSourceHealth() = runBlocking {
        SourceHealthRepository.recordSuccess(
            sourceId = "a", providerId = "pa", sourceType = PlaybackSourceType.IPTV
        )
        SourceHealthRepository.recordSuccess(
            sourceId = "b", providerId = "pb", sourceType = PlaybackSourceType.EXTENSION
        )

        val all = SourceHealthRepository.getAllSourceHealth()
        assertEquals(2, all.size)
        val ids = all.map { it.sourceId }.toSet()
        assertTrue(ids.contains("a"))
        assertTrue(ids.contains("b"))
    }

    @Test
    fun testInMemoryDaoFallback_markHiddenNoInserted() = runBlocking {
        // markSourceHidden on non-existent source should not crash
        SourceHealthRepository.markSourceHidden("nonexistent", true)

        // No source should be returned
        assertNull(SourceHealthRepository.getSourceHealth("nonexistent"))
    }

    // ── Pruning Stale Entries ───────────────────────────────────────────

    @Test
    fun testPruneStaleSourceHealth_removesOldEntries() = runBlocking {
        val now = System.currentTimeMillis()
        val thirtyOneDaysAgo = now - 31L * 24 * 3600_000L

        // Record a source with old timestamps
        SourceHealthRepository.recordSignal(
            sourceId = "old-source",
            providerId = "old-provider",
            sourceType = PlaybackSourceType.IPTV,
            signal = SourceHealthSignal.PLAYBACK_SUCCESS,
            timestamp = thirtyOneDaysAgo
        )

        // Record a recent source
        SourceHealthRepository.recordSignal(
            sourceId = "new-source",
            providerId = "new-provider",
            sourceType = PlaybackSourceType.EXTENSION,
            signal = SourceHealthSignal.PLAYBACK_SUCCESS,
            timestamp = now
        )

        assertEquals(2, SourceHealthRepository.getAllSourceHealth().size)

        // Prune with 30-day retention
        SourceHealthRepository.pruneStaleSourceHealth(currentTime = now)

        val remaining = SourceHealthRepository.getAllSourceHealth()
        assertEquals(1, remaining.size)
        assertEquals("new-source", remaining[0].sourceId)
    }

    @Test
    fun testPruneStaleSourceHealth_keepsRecentEntries() = runBlocking {
        val now = System.currentTimeMillis()
        val fiveDaysAgo = now - 5L * 24 * 3600_000L

        SourceHealthRepository.recordSignal(
            sourceId = "recent-source",
            providerId = "recent-provider",
            sourceType = PlaybackSourceType.EXTENSION,
            signal = SourceHealthSignal.PLAYBACK_SUCCESS,
            timestamp = fiveDaysAgo
        )

        SourceHealthRepository.pruneStaleSourceHealth(currentTime = now)

        val remaining = SourceHealthRepository.getAllSourceHealth()
        assertEquals(1, remaining.size)
    }

    // ── Multiple Failures Reduce Score Correctly ────────────────────────

    @Test
    fun testMultipleFailuresReduceScore() = runBlocking {
        val sourceId = "multi-fail"
        val providerId = "multi-fail-provider"
        val now = System.currentTimeMillis()

        // 5 failures: 100 → 80 → 60 → 40 → 20 → 0
        for (i in 1..5) {
            SourceHealthRepository.recordSignal(
                sourceId = sourceId,
                providerId = providerId,
                sourceType = PlaybackSourceType.EXTENSION,
                signal = SourceHealthSignal.PLAYBACK_FAILURE,
                timestamp = now + i // slight offset to avoid recovery
            )
        }

        val health = SourceHealthRepository.getSourceHealth(sourceId, currentTime = now + 6)
        assertNotNull(health)
        assertEquals(0, health!!.healthScore)
        assertEquals(5, health.failureCount)
    }

    // ── RecordSignal Updates Both Source and Provider ────────────────────

    @Test
    fun testRecordSignalUpdatesBothSourceAndProvider() = runBlocking {
        val sourceId = "dual-update-source"
        val providerId = "dual-update-provider"

        SourceHealthRepository.recordSignal(
            sourceId = sourceId,
            providerId = providerId,
            sourceType = PlaybackSourceType.STREMIO,
            signal = SourceHealthSignal.PLAYBACK_FAILURE
        )

        val sourceHealth = SourceHealthRepository.getSourceHealth(sourceId)
        val providerHealth = SourceHealthRepository.getProviderHealth(providerId)

        assertNotNull(sourceHealth)
        assertNotNull(providerHealth)
        assertEquals(80, sourceHealth!!.healthScore)
        assertEquals(80, providerHealth!!.healthScore)
    }

    // ── Regression: PERSIST-005 — Prune failure-only entries ─────────────

    @Test
    fun testPruneStaleSourceHealth_removesFailureOnlyEntries() = runBlocking {
        val now = System.currentTimeMillis()
        val fortyDaysAgo = now - 40L * 24 * 3600_000L

        // Record a source that ONLY ever had failures (lastSuccessTime stays 0)
        SourceHealthRepository.recordSignal(
            sourceId = "failure-only-source",
            providerId = "failure-only-provider",
            sourceType = PlaybackSourceType.IPTV,
            signal = SourceHealthSignal.PLAYBACK_FAILURE,
            timestamp = fortyDaysAgo
        )

        // Record a recent source
        SourceHealthRepository.recordSignal(
            sourceId = "recent-source",
            providerId = "recent-provider",
            sourceType = PlaybackSourceType.EXTENSION,
            signal = SourceHealthSignal.PLAYBACK_SUCCESS,
            timestamp = now
        )

        assertEquals(2, SourceHealthRepository.getAllSourceHealth().size)

        // Prune with 30-day retention — failure-only entry should be pruned
        SourceHealthRepository.pruneStaleSourceHealth(currentTime = now)

        val remaining = SourceHealthRepository.getAllSourceHealth()
        assertEquals(1, remaining.size)
        assertEquals("recent-source", remaining[0].sourceId)
    }

    @Test
    fun testPruneStaleSourceHealth_keepsRecentFailureOnlyEntries() = runBlocking {
        val now = System.currentTimeMillis()
        val twoDaysAgo = now - 2L * 24 * 3600_000L

        // Record a recent failure-only source (should NOT be pruned)
        SourceHealthRepository.recordSignal(
            sourceId = "recent-failure",
            providerId = "recent-failure-provider",
            sourceType = PlaybackSourceType.EXTENSION,
            signal = SourceHealthSignal.PLAYBACK_FAILURE,
            timestamp = twoDaysAgo
        )

        SourceHealthRepository.pruneStaleSourceHealth(currentTime = now)

        val remaining = SourceHealthRepository.getAllSourceHealth()
        assertEquals(1, remaining.size)
        assertEquals("recent-failure", remaining[0].sourceId)
    }
}
