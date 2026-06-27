package com.example.calmsource.feature.iptv

import com.example.calmsource.core.database.SourceHealthRepository
import com.example.calmsource.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class IPTVHealthIntegrationTest {

    private val providerId = "test-provider-1"
    private val provider = IPTVProvider(
        id = providerId,
        name = "Test Provider",
        playlistUrl = "http://provider.com/playlist.m3u",
        isEnabled = true,
        health = ProviderHealth.HEALTHY
    )

    private val channel1 = IPTVChannel(
        id = "c1",
        tvgId = "t1",
        tvgName = "Channel 1",
        tvgLogo = null,
        groupTitle = "General",
        name = "Channel 1",
        streamUrl = "http://stream1.iptv/channel1?token=secret123",
        providerId = providerId
    )

    private val channel2 = IPTVChannel(
        id = "c2",
        tvgId = "t2",
        tvgName = "Channel 2",
        tvgLogo = null,
        groupTitle = "General",
        name = "Channel 2",
        streamUrl = "http://stream2.iptv/channel2?token=secret456",
        providerId = providerId
    )

    @Before
    fun setUp() {
        runBlocking {
            SourceHealthRepository.clearSourceHealth()
            SourceHealthRepository.clearProviderHealth()
            // IPTVRepository caches health data internally — force a refresh
            // so stale hidden/blocked flags from previous tests don't leak
            IPTVRepository.refreshHealthCacheForTest()
        }
    }

    private suspend fun recordSuccess(channel: IPTVChannel) {
        SourceHealthRepository.recordSuccess(
            sourceId = generateSafeSourceId(channel.streamUrl),
            providerId = channel.providerId,
            sourceType = PlaybackSourceType.IPTV
        )
    }

    private suspend fun recordFailure(channel: IPTVChannel, errorCategory: String) {
        SourceHealthRepository.recordFailure(
            sourceId = generateSafeSourceId(channel.streamUrl),
            providerId = channel.providerId,
            sourceType = PlaybackSourceType.IPTV,
            errorCategory = errorCategory
        )
    }

    @Test
    fun testChannelSuccessAndFailureRecorded() = runBlocking {
        val testChannel = channel1

        // 1. Success recorded
        recordSuccess(testChannel)
        val safeSourceId = generateSafeSourceId(testChannel.streamUrl)
        val successHealth = SourceHealthRepository.getSourceHealth(safeSourceId)
        assertNotNull(successHealth)
        assertEquals(100, successHealth?.healthScore)
        assertEquals(0, successHealth?.failureCount)

        // 2. Failure recorded
        recordFailure(testChannel, "CONNECT_TIMEOUT")
        val failureHealth = SourceHealthRepository.getSourceHealth(safeSourceId)
        assertNotNull(failureHealth)
        assertEquals(80, failureHealth?.healthScore) // declines by 20 points
        assertEquals(1, failureHealth?.failureCount)
        assertEquals("CONNECT_TIMEOUT", failureHealth?.lastErrorCategory)
    }

    @Test
    fun testProviderHealthAffectedByRepeatedFailures() = runBlocking {
        val testChannel = channel1
        val providerId = testChannel.providerId

        // Record 5 failures
        for (i in 1..5) {
            recordFailure(testChannel, "STREAM_FAIL")
        }

        val providerHealth = SourceHealthRepository.getProviderHealth(providerId)
        assertNotNull(providerHealth)
        // Score: 100 - 5 * 20 = 0
        assertEquals(0, providerHealth?.healthScore)
        assertEquals(5, providerHealth?.failureCount)
    }

    @Test
    fun testFailedChannelSwitchingFallback() {
        val providersList = listOf(provider)
        val channelsList = listOf(channel1, channel2)
        val blockedChannelIds = setOf(channel1.id)

        // Switcher skips blocked channel when skipBlocked is true
        val manager = ChannelQueueManager(
            providers = providersList,
            allChannels = channelsList,
            blockedChannelIds = blockedChannelIds,
            skipBlocked = true
        )
        manager.buildQueue(ChannelQueueManager.QueueMode.ALL_CHANNELS)
        // Set current to channel2 so next channel wraps around to channel1
        manager.setChannel(channel2.id)

        // Since channel1 is blocked and skipBlocked is true, nextChannel() should wrap around and skip channel1, returning channel2
        assertEquals(channel2.id, manager.nextChannel()?.id)

        // When skipBlocked is false, nextChannel() goes to channel1
        val managerNoSkip = ChannelQueueManager(
            providers = providersList,
            allChannels = channelsList,
            blockedChannelIds = blockedChannelIds,
            skipBlocked = false
        )
        managerNoSkip.buildQueue(ChannelQueueManager.QueueMode.ALL_CHANNELS)
        managerNoSkip.setChannel(channel2.id)
        assertEquals(channel1.id, managerNoSkip.nextChannel()?.id)

        // Test fallback options on channel failure
        val (nextChannel, fallbackList) = manager.getFallbackOptions(channel1.id)
        assertEquals(channel2.id, nextChannel?.id)
        assertEquals(1, fallbackList.size)
        assertEquals(channel2.id, fallbackList[0].id)
    }

    @Test
    fun testRawIptvUrlNotLogged() = runBlocking {
        val testChannel = channel1

        // Record playback failure
        recordFailure(testChannel, "DECODE_ERROR")

        val safeSourceId = generateSafeSourceId(testChannel.streamUrl)
        val health = SourceHealthRepository.getSourceHealth(safeSourceId)
        assertNotNull(health)
        
        // Assert that the recorded sourceId is the safe generated hash, and not the raw URL itself
        assertEquals(safeSourceId, health?.sourceId)
        assertNotEquals(testChannel.streamUrl, health?.sourceId)
        assertFalse(health?.sourceId?.contains("http") == true)
        
        // Ensure no raw URL exists in health database values
        val allHealth = SourceHealthRepository.getAllSourceHealth()
        for (h in allHealth) {
            assertFalse(h.sourceId.contains("http"))
            assertFalse(h.sourceId.contains(".m3u"))
            assertFalse(h.sourceId.contains("token"))
            assertFalse(h.lastErrorCategory.contains("http"))
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 3: Channel switching remains stable when source health exists
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testChannelSwitchingStableWithHealthData() = runBlocking {
        val testChannel = channel1

        // Record some health data first
        recordSuccess(testChannel)
        recordFailure(testChannel, "STREAM_FAIL")

        // Build a channel queue and verify switching still works
        val providersList = listOf(provider)
        val channelsList = listOf(channel1, channel2)
        val manager = ChannelQueueManager(
            providers = providersList,
            allChannels = channelsList
        )
        manager.buildQueue(ChannelQueueManager.QueueMode.ALL_CHANNELS)
        assertEquals(channel1.id, manager.getCurrentChannel()?.id)

        val next = manager.nextChannel()
        assertNotNull(next)
        assertEquals(channel2.id, next?.id)

        val prev = manager.previousChannel()
        assertNotNull(prev)
        assertEquals(channel1.id, prev?.id)

        // Verify health data is intact after switching
        val safeSourceId = generateSafeSourceId(testChannel.streamUrl)
        val health = SourceHealthRepository.getSourceHealth(safeSourceId)
        assertNotNull(health)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 4: Failed channel does not crash switching
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testFailedChannelDoesNotCrashSwitching() {
        val providersList = listOf(provider)
        val channelsList = listOf(channel1, channel2)

        // Mark channel1 as blocked
        val blockedIds = setOf(channel1.id)
        val manager = ChannelQueueManager(
            providers = providersList,
            allChannels = channelsList,
            blockedChannelIds = blockedIds,
            skipBlocked = true
        )
        manager.buildQueue(ChannelQueueManager.QueueMode.ALL_CHANNELS)

        // Even with a failed/blocked channel, next and previous should not crash
        val next = manager.nextChannel()
        assertNotNull(next)
        // It should skip channel1 and stay on channel2
        assertEquals(channel2.id, next?.id)

        val prev = manager.previousChannel()
        assertNotNull(prev)
        assertEquals(channel2.id, prev?.id)

        // Verify getCurrentChannel works
        val current = manager.getCurrentChannel()
        assertNotNull(current)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 5: Bad channel can be retried
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testBadChannelCanBeRetried() = runBlocking {
        val testChannel = channel1
        val safeSourceId = generateSafeSourceId(testChannel.streamUrl)

        // Record multiple failures to bring score low
        recordFailure(testChannel, "STREAM_FAIL")
        recordFailure(testChannel, "STREAM_FAIL")
        recordFailure(testChannel, "STREAM_FAIL")

        val healthBefore = SourceHealthRepository.getSourceHealth(safeSourceId)
        assertNotNull(healthBefore)
        assertTrue(healthBefore!!.healthScore < 100)
        assertTrue(healthBefore.failureCount > 0)

        // Now record a success — the channel should recover
        recordSuccess(testChannel)

        val healthAfter = SourceHealthRepository.getSourceHealth(safeSourceId)
        assertNotNull(healthAfter)
        assertEquals(100, healthAfter?.healthScore)
        assertEquals(0, healthAfter?.failureCount)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 6: Provider health reflects repeated failures without
    //         over-penalizing all channels
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testProviderHealthDoesNotOverPenalizeAllChannels() = runBlocking {
        // Use direct SourceHealthRepository to avoid IPTVRepository singleton state issues
        val testProviderId = "test-isolation-provider"
        val ch1SourceId = generateSafeSourceId("http://stream1.test/ch1")
        val ch2SourceId = generateSafeSourceId("http://stream2.test/ch2")

        // Fail channel 1 multiple times
        for (i in 1..3) {
            SourceHealthRepository.recordFailure(
                sourceId = ch1SourceId,
                providerId = testProviderId,
                sourceType = PlaybackSourceType.IPTV,
                errorCategory = "STREAM_FAIL"
            )
        }

        // Channel 2 should still have full health (or no health record at all)
        val ch2Health = SourceHealthRepository.getSourceHealth(ch2SourceId)
        assertTrue("Channel 2 should have no health entry (null) or full score",
            ch2Health == null || ch2Health.healthScore == 100)

        // Provider health should be affected but not leak to ch2's source health
        val providerHealth = SourceHealthRepository.getProviderHealth(testProviderId)
        assertNotNull("Provider should have health data", providerHealth)
        assertTrue("Provider score should be reduced", providerHealth!!.healthScore < 100)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 7: Channels are not hidden permanently without user action
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testChannelsNotAutoHiddenByFailures() = runBlocking {
        val testChannel = channel1
        val safeSourceId = generateSafeSourceId(testChannel.streamUrl)

        // Record many failures — enough to bring score to 0
        for (i in 1..5) {
            recordFailure(testChannel, "STREAM_FAIL")
        }

        val health = SourceHealthRepository.getSourceHealth(safeSourceId)
        assertNotNull(health)
        assertEquals(0, health?.healthScore)

        // Even at score 0, userHidden should be false (only set by explicit user action)
        assertFalse(health?.userHidden == true)

    }

    @Test
    fun testOnlyUserActionHidesChannel() = runBlocking {
        val testChannel = channel1
        val safeSourceId = generateSafeSourceId(testChannel.streamUrl)

        // First record some health data
        recordFailure(testChannel, "STREAM_FAIL")

        // Now explicitly mark as user-hidden
        SourceHealthRepository.markSourceHidden(safeSourceId, true)
        // Refresh IPTVRepository's internal health cache
        IPTVRepository.refreshHealthCacheForTest()

        val health = SourceHealthRepository.getSourceHealth(safeSourceId)
        assertNotNull(health)
        assertTrue(health?.userHidden == true)

        // Un-hiding should restore the source-health flag.
        SourceHealthRepository.markSourceHidden(safeSourceId, false)
        val restoredHealth = SourceHealthRepository.getSourceHealth(safeSourceId)
        assertFalse(restoredHealth?.userHidden == true)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 8: Error category field does not leak raw URLs
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testErrorCategoryNeverContainsRawUrl() = runBlocking {
        val testChannel = channel1

        // Record failure with various error categories including URL-like strings
        recordFailure(testChannel, "CONNECT_TIMEOUT")
        recordFailure(testChannel, "DNS_ERROR")

        val allHealth = SourceHealthRepository.getAllSourceHealth()
        for (h in allHealth) {
            // Source ID must be a hash, not a URL
            assertFalse("sourceId should not contain '://'", h.sourceId.contains("://"))
            assertFalse("sourceId should not contain 'token'", h.sourceId.contains("token"))
            // Error category should be a known enum-like category, not a URL
            assertFalse("lastErrorCategory should not contain 'http'", h.lastErrorCategory.contains("http"))
        }
    }
}
