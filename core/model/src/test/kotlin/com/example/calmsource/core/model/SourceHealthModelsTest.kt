package com.example.calmsource.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceHealthModelsTest {

    @Test
    fun `SourceHealthScore fromScore maps correctly to reliability tiers`() {
        // EXCELLENT: 90 - 100
        assertEquals(SourceReliabilityTier.EXCELLENT, SourceHealthScore.fromScore(100).tier)
        assertEquals(SourceReliabilityTier.EXCELLENT, SourceHealthScore.fromScore(90).tier)

        // GOOD: 70 - 89
        assertEquals(SourceReliabilityTier.GOOD, SourceHealthScore.fromScore(89).tier)
        assertEquals(SourceReliabilityTier.GOOD, SourceHealthScore.fromScore(70).tier)

        // UNSTABLE: 40 - 69
        assertEquals(SourceReliabilityTier.UNSTABLE, SourceHealthScore.fromScore(69).tier)
        assertEquals(SourceReliabilityTier.UNSTABLE, SourceHealthScore.fromScore(40).tier)

        // POOR: 1 - 39
        assertEquals(SourceReliabilityTier.POOR, SourceHealthScore.fromScore(39).tier)
        assertEquals(SourceReliabilityTier.POOR, SourceHealthScore.fromScore(1).tier)

        // BLOCKED: 0
        assertEquals(SourceReliabilityTier.BLOCKED, SourceHealthScore.fromScore(0).tier)

        // If userHidden, force BLOCKED and score 0
        val hiddenScore = SourceHealthScore.fromScore(100, userHidden = true)
        assertEquals(0, hiddenScore.score)
        assertEquals(SourceReliabilityTier.BLOCKED, hiddenScore.tier)
    }

    @Test
    fun `SourceHealth applySignal updates healthScore and stats correctly`() {
        val initial = SourceHealth(
            sourceId = "src_1",
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV
        )

        // Verify initial state
        assertEquals(100, initial.healthScore)
        assertEquals(0, initial.failureCount)
        assertEquals(SourceReliabilityTier.EXCELLENT, initial.reliabilityTier)

        // Apply failure signal (-20)
        val time1 = 1000L
        val afterFail = initial.applySignal(SourceHealthSignal.PLAYBACK_FAILURE, timestamp = time1, errorCategory = "DECODER_ERROR")
        assertEquals(80, afterFail.healthScore)
        assertEquals(1, afterFail.failureCount)
        assertEquals(time1, afterFail.lastFailureTime)
        assertEquals("DECODER_ERROR", afterFail.lastErrorCategory)
        assertEquals(SourceReliabilityTier.GOOD, afterFail.reliabilityTier)

        // Apply timeout signal (-20)
        val time2 = 2000L
        val afterTimeout = afterFail.applySignal(SourceHealthSignal.PLAYBACK_TIMEOUT, timestamp = time2)
        assertEquals(60, afterTimeout.healthScore)
        assertEquals(2, afterTimeout.failureCount)
        assertEquals(time2, afterTimeout.lastFailureTime)
        assertEquals("PLAYBACK_TIMEOUT", afterTimeout.lastErrorCategory)
        assertEquals(SourceReliabilityTier.UNSTABLE, afterTimeout.reliabilityTier)

        // Apply provider unreachable (-15)
        val time3 = 3000L
        val afterUnreachable = afterTimeout.applySignal(SourceHealthSignal.PROVIDER_UNREACHABLE, timestamp = time3)
        assertEquals(45, afterUnreachable.healthScore)
        assertEquals(2, afterUnreachable.failureCount) // Failure count shouldn't increment for provider unreachable
        assertEquals(time3, afterUnreachable.lastFailureTime)
        assertEquals("PROVIDER_UNREACHABLE", afterUnreachable.lastErrorCategory)

        // Apply success (restore to 100, reset failureCount)
        val time4 = 4000L
        val afterSuccess = afterUnreachable.applySignal(SourceHealthSignal.PLAYBACK_SUCCESS, timestamp = time4)
        assertEquals(100, afterSuccess.healthScore)
        assertEquals(0, afterSuccess.failureCount)
        assertEquals(time4, afterSuccess.lastSuccessTime)
    }

    @Test
    fun `SourceHealth USER_MARKED_BAD blocks the source`() {
        val initial = SourceHealth(
            sourceId = "src_1",
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV
        )
        val afterMarkedBad = initial.applySignal(SourceHealthSignal.USER_MARKED_BAD, timestamp = 12345L)
        assertEquals(0, afterMarkedBad.healthScore)
        assertTrue(afterMarkedBad.userHidden)
        assertEquals(SourceReliabilityTier.BLOCKED, afterMarkedBad.reliabilityTier)
        assertEquals("USER_MARKED_BAD", afterMarkedBad.lastErrorCategory)
    }

    @Test
    fun `SourceHealth USER_SKIPPED deducts points`() {
        val initial = SourceHealth(
            sourceId = "src_1",
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV
        )
        val afterSkip = initial.applySignal(SourceHealthSignal.USER_SKIPPED)
        assertEquals(95, afterSkip.healthScore)
    }

    @Test
    fun `SourceHealth averages startup and buffering times correctly using EMA`() {
        var health = SourceHealth(
            sourceId = "src_1",
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV
        )

        // First startup signal sets the initial value
        health = health.applySignal(SourceHealthSignal.STARTUP_TIME, startupTimeMs = 400L)
        assertEquals(400L, health.averageStartupTime)

        // Next startup signal applies the EMA formula: (averageStartupTime * 3 + startupTimeMs) / 4
        // (400 * 3 + 800) / 4 = 500
        health = health.applySignal(SourceHealthSignal.STARTUP_TIME, startupTimeMs = 800L)
        assertEquals(500L, health.averageStartupTime)

        // Buffering severity EMA formula: (averageBufferingSeverity * 3.0f + bufferingSeverity) / 4.0f
        health = health.applySignal(SourceHealthSignal.BUFFERING_SEVERITY, bufferingSeverity = 0.8f)
        assertEquals(0.8f, health.averageBufferingSeverity, 0.001f)

        // (0.8 * 3 + 0.4) / 4 = 0.7
        health = health.applySignal(SourceHealthSignal.BUFFERING_SEVERITY, bufferingSeverity = 0.4f)
        assertEquals(0.7f, health.averageBufferingSeverity, 0.001f)
    }

    @Test
    fun `SourceHealth recovery logic applies correctly over time`() {
        val lastFailTime = 1000000L
        val health = SourceHealth(
            sourceId = "src_1",
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV,
            failureCount = 3,
            lastFailureTime = lastFailTime,
            healthScore = 40
        )

        // Querying immediately (or within 1 hour) has no recovery
        val queryImmediate = health.getUpdatedHealth(currentTime = lastFailTime + 1800_000L) // +30 mins
        assertEquals(40, queryImmediate.healthScore)
        assertEquals(3, queryImmediate.failureCount)

        // Querying after exactly 1.5 hours: (1.5 hours elapsed -> recovers 10 points)
        val queryOneAndHalfHours = health.getUpdatedHealth(currentTime = lastFailTime + 5400_000L) // +90 mins
        assertEquals(50, queryOneAndHalfHours.healthScore)
        assertEquals(3, queryOneAndHalfHours.failureCount)

        // Querying after 6 hours: recovers 6 * 10 = 60 points -> score should go to 100
        val querySixHours = health.getUpdatedHealth(currentTime = lastFailTime + 21600_000L) // +6 hours
        assertEquals(100, querySixHours.healthScore)
        assertEquals(0, querySixHours.failureCount) // Resets because it's fully restored

        // Hidden source never recovers
        val hiddenHealth = health.copy(userHidden = true)
        val queryHidden = hiddenHealth.getUpdatedHealth(currentTime = lastFailTime + 21600_000L)
        assertEquals(40, queryHidden.healthScore)
        assertTrue(queryHidden.userHidden)
    }

    @Test
    fun `ProviderHealthScore updates and recovers correctly`() {
        val initial = ProviderHealthScore(
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV
        )

        // Apply signals
        var p = initial.applySignal(SourceHealthSignal.PLAYBACK_FAILURE, timestamp = 1000L)
        assertEquals(80, p.healthScore)
        assertEquals(1, p.failureCount)
        assertEquals(0, p.timeoutCount)

        p = p.applySignal(SourceHealthSignal.PLAYBACK_TIMEOUT, timestamp = 2000L)
        assertEquals(60, p.healthScore)
        assertEquals(2, p.failureCount)
        assertEquals(1, p.timeoutCount)

        p = p.applySignal(SourceHealthSignal.PROVIDER_UNREACHABLE, timestamp = 3000L)
        assertEquals(45, p.healthScore)
        assertEquals(3, p.failureCount)

        // Apply success restores score and resets counts
        p = p.applySignal(SourceHealthSignal.PLAYBACK_SUCCESS, timestamp = 4000L)
        assertEquals(100, p.healthScore)
        assertEquals(0, p.failureCount)
        assertEquals(0, p.timeoutCount)
        assertEquals(1, p.successCount)

        // Test recovery on query
        val damaged = ProviderHealthScore(
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV,
            failureCount = 2,
            timeoutCount = 1,
            lastFailureTime = 1000000L,
            healthScore = 60
        )
        // 4 hours elapsed -> 40 points recovery -> 100 score
        val recovered = damaged.getUpdatedHealth(currentTime = 1000000L + 14400_000L)
        assertEquals(100, recovered.healthScore)
        assertEquals(0, recovered.failureCount)
        assertEquals(0, recovered.timeoutCount)
    }

    @Test
    fun `generateSafeSourceId produces deterministic safe hashes without raw contents`() {
        val secretUrl = "https://my-premium-iptv.xyz/get.php?auth=SECRET_TOKEN_12345&type=ts"
        val safeId1 = generateSafeSourceId(secretUrl)
        val safeId2 = generateSafeSourceId(secretUrl)

        // Must be identical for same URL
        assertEquals(safeId1, safeId2)

        // Must not contain the secret token or domain
        assertTrue(!safeId1.contains("SECRET_TOKEN_12345"))
        assertTrue(!safeId1.contains("my-premium-iptv"))

        // Length should be bounded (e.g. 16 characters as specified in implementation)
        assertEquals(16, safeId1.length)
    }

    @Test
    fun `generateSafeSourceId produces different hashes for different URLs`() {
        val id1 = generateSafeSourceId("https://provider-a.com/stream/1")
        val id2 = generateSafeSourceId("https://provider-b.com/stream/1")
        assertNotEquals(id1, id2)
    }

    @Test
    fun `generateSafeSourceId output contains only hex characters`() {
        val id = generateSafeSourceId("https://example.com/test")
        assertTrue(id.matches(Regex("[0-9a-f]{16}")))
    }

    // ---- Score bounds edge cases ----

    @Test
    fun `SourceHealth score never goes below 0 after repeated failures`() {
        var health = SourceHealth(
            sourceId = "src_1",
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV
        )
        // 100 -> 80 -> 60 -> 40 -> 20 -> 0 -> 0 -> 0
        repeat(8) {
            health = health.applySignal(SourceHealthSignal.PLAYBACK_FAILURE, timestamp = it.toLong())
        }
        assertEquals(0, health.healthScore)
        assertEquals(8, health.failureCount)
        assertEquals(SourceReliabilityTier.BLOCKED, health.reliabilityTier)
    }

    @Test
    fun `SourceHealth score never exceeds 100 after recovery`() {
        val health = SourceHealth(
            sourceId = "src_1",
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV,
            failureCount = 1,
            lastFailureTime = 1000000L,
            healthScore = 95
        )
        // 100 hours elapsed -> 1000 points recovery, but should cap at 100
        val recovered = health.getUpdatedHealth(currentTime = 1000000L + 360_000_000L)
        assertEquals(100, recovered.healthScore)
        assertEquals(0, recovered.failureCount)
    }

    @Test
    fun `PLAYBACK_SUCCESS is idempotent on a healthy source`() {
        val healthy = SourceHealth(
            sourceId = "src_1",
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV,
            healthScore = 100
        )
        val afterSuccess = healthy.applySignal(SourceHealthSignal.PLAYBACK_SUCCESS, timestamp = 5000L)
        assertEquals(100, afterSuccess.healthScore)
        assertEquals(0, afterSuccess.failureCount)
        assertEquals(5000L, afterSuccess.lastSuccessTime)
    }

    // ---- UNSUPPORTED_FORMAT specific test ----

    @Test
    fun `UNSUPPORTED_FORMAT subtracts 20 points and increments failureCount`() {
        val health = SourceHealth(
            sourceId = "src_1",
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV
        )
        val after = health.applySignal(
            SourceHealthSignal.UNSUPPORTED_FORMAT,
            timestamp = 1000L,
            errorCategory = "MKV_NOT_SUPPORTED"
        )
        assertEquals(80, after.healthScore)
        assertEquals(1, after.failureCount)
        assertEquals("MKV_NOT_SUPPORTED", after.lastErrorCategory)
    }

    @Test
    fun `UNSUPPORTED_FORMAT uses signal name as default error category`() {
        val health = SourceHealth(
            sourceId = "src_1",
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV
        )
        val after = health.applySignal(SourceHealthSignal.UNSUPPORTED_FORMAT, timestamp = 1000L)
        assertEquals("UNSUPPORTED_FORMAT", after.lastErrorCategory)
    }

    // ---- BUFFERING_SEVERITY doesn't penalize score ----

    @Test
    fun `BUFFERING_SEVERITY does not change healthScore`() {
        val health = SourceHealth(
            sourceId = "src_1",
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV,
            healthScore = 75
        )
        val after = health.applySignal(SourceHealthSignal.BUFFERING_SEVERITY, bufferingSeverity = 0.9f)
        assertEquals(75, after.healthScore)
    }

    @Test
    fun `STARTUP_TIME does not change healthScore`() {
        val health = SourceHealth(
            sourceId = "src_1",
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV,
            healthScore = 60
        )
        val after = health.applySignal(SourceHealthSignal.STARTUP_TIME, startupTimeMs = 5000L)
        assertEquals(60, after.healthScore)
    }

    // ---- Provider-level signal tests ----

    @Test
    fun `ProviderHealthScore USER_MARKED_BAD subtracts 30 points`() {
        val provider = ProviderHealthScore(
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV,
            healthScore = 100
        )
        val after = provider.applySignal(SourceHealthSignal.USER_MARKED_BAD, timestamp = 1000L)
        assertEquals(70, after.healthScore)
        assertEquals(1000L, after.lastFailureTime)
    }

    @Test
    fun `ProviderHealthScore USER_SKIPPED subtracts 5 points`() {
        val provider = ProviderHealthScore(
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV,
            healthScore = 100
        )
        val after = provider.applySignal(SourceHealthSignal.USER_SKIPPED, timestamp = 1000L)
        assertEquals(95, after.healthScore)
    }

    @Test
    fun `ProviderHealthScore ignores STARTUP_TIME and BUFFERING_SEVERITY`() {
        val provider = ProviderHealthScore(
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV,
            healthScore = 80
        )
        val afterStartup = provider.applySignal(SourceHealthSignal.STARTUP_TIME, timestamp = 1000L)
        assertEquals(80, afterStartup.healthScore)
        assertEquals(provider, afterStartup)

        val afterBuffering = provider.applySignal(SourceHealthSignal.BUFFERING_SEVERITY, timestamp = 2000L)
        assertEquals(80, afterBuffering.healthScore)
        assertEquals(provider, afterBuffering)
    }

    @Test
    fun `ProviderHealthScore score never goes below 0 after repeated failures`() {
        var provider = ProviderHealthScore(
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV
        )
        repeat(8) {
            provider = provider.applySignal(SourceHealthSignal.PLAYBACK_FAILURE, timestamp = it.toLong())
        }
        assertEquals(0, provider.healthScore)
        assertEquals(8, provider.failureCount)
    }

    @Test
    fun `ProviderHealthScore UNSUPPORTED_FORMAT subtracts 20`() {
        val provider = ProviderHealthScore(
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV
        )
        val after = provider.applySignal(SourceHealthSignal.UNSUPPORTED_FORMAT, timestamp = 1000L)
        assertEquals(80, after.healthScore)
        assertEquals(1, after.failureCount)
    }

    // ---- Recovery edge cases ----

    @Test
    fun `SourceHealth partial recovery does not reset failureCount`() {
        val health = SourceHealth(
            sourceId = "src_1",
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV,
            failureCount = 5,
            lastFailureTime = 1000000L,
            healthScore = 20
        )
        // 2 hours elapsed -> 20 points recovery -> score 40, not fully recovered
        val partial = health.getUpdatedHealth(currentTime = 1000000L + 7200_000L)
        assertEquals(40, partial.healthScore)
        assertEquals(5, partial.failureCount) // not reset because not fully recovered
    }

    @Test
    fun `ProviderHealthScore partial recovery does not reset counts`() {
        val provider = ProviderHealthScore(
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV,
            failureCount = 4,
            timeoutCount = 2,
            lastFailureTime = 1000000L,
            healthScore = 20
        )
        // 3 hours elapsed -> 30 points -> score 50
        val partial = provider.getUpdatedHealth(currentTime = 1000000L + 10800_000L)
        assertEquals(50, partial.healthScore)
        assertEquals(4, partial.failureCount)
        assertEquals(2, partial.timeoutCount)
    }

    @Test
    fun `SourceHealth no recovery when lastFailureTime is 0`() {
        val health = SourceHealth(
            sourceId = "src_1",
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV,
            healthScore = 50,
            lastFailureTime = 0L
        )
        val result = health.getUpdatedHealth(currentTime = 999999999L)
        assertEquals(50, result.healthScore)
    }

    @Test
    fun `SourceHealth no recovery when already at 100`() {
        val health = SourceHealth(
            sourceId = "src_1",
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV,
            healthScore = 100,
            lastFailureTime = 1000L
        )
        val result = health.getUpdatedHealth(currentTime = 999999999L)
        assertEquals(100, result.healthScore)
    }

    // ---- Tier mapping boundary tests ----

    @Test
    fun `SourceHealthScore tier boundaries are exact`() {
        // Exact boundaries
        assertEquals(SourceReliabilityTier.EXCELLENT, SourceHealthScore.fromScore(100).tier)
        assertEquals(SourceReliabilityTier.EXCELLENT, SourceHealthScore.fromScore(90).tier)
        assertEquals(SourceReliabilityTier.GOOD, SourceHealthScore.fromScore(89).tier)
        assertEquals(SourceReliabilityTier.GOOD, SourceHealthScore.fromScore(70).tier)
        assertEquals(SourceReliabilityTier.UNSTABLE, SourceHealthScore.fromScore(69).tier)
        assertEquals(SourceReliabilityTier.UNSTABLE, SourceHealthScore.fromScore(40).tier)
        assertEquals(SourceReliabilityTier.POOR, SourceHealthScore.fromScore(39).tier)
        assertEquals(SourceReliabilityTier.POOR, SourceHealthScore.fromScore(1).tier)
        assertEquals(SourceReliabilityTier.BLOCKED, SourceHealthScore.fromScore(0).tier)
    }

    @Test
    fun `SourceHealthScore clamps out-of-range inputs`() {
        // Negative score gets clamped to 0 -> BLOCKED
        val negative = SourceHealthScore.fromScore(-50)
        assertEquals(0, negative.score)
        assertEquals(SourceReliabilityTier.BLOCKED, negative.tier)

        // Score > 100 gets clamped to 100 -> EXCELLENT
        val over = SourceHealthScore.fromScore(150)
        assertEquals(100, over.score)
        assertEquals(SourceReliabilityTier.EXCELLENT, over.tier)
    }

    // ---- Source/Provider isolation test ----

    @Test
    fun `source and provider health scores are independent`() {
        val source = SourceHealth(
            sourceId = "src_1",
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV
        )
        val provider = ProviderHealthScore(
            providerId = "prov_1",
            sourceType = PlaybackSourceType.IPTV
        )

        // Damage the source, provider stays healthy
        val damagedSource = source.applySignal(SourceHealthSignal.PLAYBACK_FAILURE, timestamp = 1000L)
        assertEquals(80, damagedSource.healthScore)
        assertEquals(100, provider.healthScore)

        // Damage the provider, source stays as-is
        val damagedProvider = provider.applySignal(SourceHealthSignal.PLAYBACK_FAILURE, timestamp = 1000L)
        assertEquals(80, damagedProvider.healthScore)
        assertEquals(80, damagedSource.healthScore) // unchanged
    }
}
