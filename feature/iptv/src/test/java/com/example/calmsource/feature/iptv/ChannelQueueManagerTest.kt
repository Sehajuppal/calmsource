package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.IPTVProvider
import com.example.calmsource.core.model.ProviderHealth
import org.junit.Assert.*
import org.junit.Test

class ChannelQueueManagerTest {

    private val provider1 = IPTVProvider(
        id = "p1", name = "Provider 1", playlistUrl = "url1",
        isEnabled = true, health = ProviderHealth.HEALTHY
    )
    private val provider2 = IPTVProvider(
        id = "p2", name = "Provider 2", playlistUrl = "url2",
        isEnabled = true, health = ProviderHealth.HEALTHY
    )
    private val disabledProvider = IPTVProvider(
        id = "p3", name = "Provider 3", playlistUrl = "url3",
        isEnabled = false, health = ProviderHealth.HEALTHY
    )
    private val failedProvider = IPTVProvider(
        id = "p4", name = "Provider 4", playlistUrl = "url4",
        isEnabled = true, health = ProviderHealth.FAILED
    )

    private val providers = listOf(provider2, provider1, disabledProvider, failedProvider)

    private val channel1 = IPTVChannel(
        id = "c1", tvgId = "t1", tvgName = "TN1", tvgLogo = "logo1",
        groupTitle = "News", name = "Channel 1", streamUrl = "http://c1", providerId = "p1"
    )
    private val channel2 = IPTVChannel(
        id = "c2", tvgId = "t2", tvgName = "TN2", tvgLogo = null, // Missing logo
        groupTitle = "Sports", name = "Channel 2", streamUrl = "http://c2", providerId = "p2"
    )
    private val channel3 = IPTVChannel(
        id = "c3", tvgId = "t3", tvgName = "TN3", tvgLogo = "logo3",
        groupTitle = null, // Missing group
        name = "Channel 3", streamUrl = "http://c3", providerId = "p1"
    )
    private val disabledChannel = IPTVChannel(
        id = "c4", tvgId = "t4", tvgName = "TN4", tvgLogo = "logo4",
        groupTitle = "News", name = "Disabled Channel", streamUrl = "http://c4", providerId = "p3"
    )
    private val failedChannel = IPTVChannel(
        id = "c5", tvgId = "t5", tvgName = "TN5", tvgLogo = "logo5",
        groupTitle = "News", name = "Failed Channel", streamUrl = "http://c5", providerId = "p4"
    )
    private val duplicateChannel1 = IPTVChannel(
        id = "c1", tvgId = "t1", tvgName = "TN1", tvgLogo = "logo1",
        groupTitle = "News", name = "Channel 1 duplicate", streamUrl = "http://c1-dup", providerId = "p1"
    )

    private val allChannels = listOf(
        channel1, channel2, channel3, disabledChannel, failedChannel, duplicateChannel1
    )

    @Test
    fun verifyAllChannelsQueueWorksAndProviderOrderPreserved() {
        // provider2 is before provider1 in the `providers` list
        val manager = ChannelQueueManager(providers, allChannels)
        manager.buildQueue(ChannelQueueManager.QueueMode.ALL_CHANNELS)

        val queue = manager.currentQueue
        assertEquals("Queue should have 3 healthy enabled channels", 3, queue.size)
        // Provider 2 channels first
        assertEquals("c2", queue[0].id)
        // Provider 1 channels next
        assertEquals("c1", queue[1].id)
        assertEquals("c3", queue[2].id)
    }

    @Test
    fun verifyCategoryQueueWorks() {
        val manager = ChannelQueueManager(providers, allChannels)
        manager.buildQueue(ChannelQueueManager.QueueMode.CATEGORY, "News")

        val queue = manager.currentQueue
        assertEquals(1, queue.size)
        assertEquals("c1", queue[0].id)
    }

    @Test
    fun verifyFavoritesQueueWorks() {
        val manager = ChannelQueueManager(providers, allChannels, favoriteChannelIds = setOf("c2", "c3"))
        manager.buildQueue(ChannelQueueManager.QueueMode.FAVORITES)

        val queue = manager.currentQueue
        assertEquals(2, queue.size)
        // Preserves provider order
        assertEquals("c2", queue[0].id)
        assertEquals("c3", queue[1].id)
    }

    @Test
    fun verifyDuplicateChannelsHandledSafely() {
        val manager = ChannelQueueManager(providers, allChannels)
        manager.buildQueue(ChannelQueueManager.QueueMode.ALL_CHANNELS)

        // c1 duplicate is safely ignored due to id distinctness
        val c1Count = manager.currentQueue.count { it.id == "c1" }
        assertEquals(1, c1Count)
    }

    @Test
    fun verifyMissingLogosOrGroupsDoNotBreak() {
        val manager = ChannelQueueManager(providers, allChannels)
        manager.buildQueue(ChannelQueueManager.QueueMode.ALL_CHANNELS)

        val queue = manager.currentQueue
        val c2 = queue.find { it.id == "c2" }
        assertNull(c2?.tvgLogo)

        val c3 = queue.find { it.id == "c3" }
        assertNull(c3?.groupTitle)
    }

    @Test
    fun verifyEmptyCategoryHandledCalmly() {
        val manager = ChannelQueueManager(providers, allChannels)
        manager.buildQueue(ChannelQueueManager.QueueMode.CATEGORY, "NonExistent")

        assertTrue(manager.currentQueue.isEmpty())
        assertNull(manager.getCurrentChannel())
        assertNull(manager.nextChannel())
        assertNull(manager.previousChannel())
    }

    @Test
    fun verifyPreviousNextWrappingBehavior() {
        val manager = ChannelQueueManager(providers, allChannels)
        manager.buildQueue(ChannelQueueManager.QueueMode.ALL_CHANNELS)

        assertEquals("c2", manager.getCurrentChannel()?.id)

        // Next
        assertEquals("c1", manager.nextChannel()?.id)
        assertEquals("c3", manager.nextChannel()?.id)
        // Wrap to first
        assertEquals("c2", manager.nextChannel()?.id)

        // Previous
        assertEquals("c3", manager.previousChannel()?.id)
        assertEquals("c1", manager.previousChannel()?.id)
        assertEquals("c2", manager.previousChannel()?.id)
    }

    @Test
    fun verifyDisabledUnhealthyChannelStateHandled() {
        // Disabled Provider and Failed Provider are skipped.
        val manager = ChannelQueueManager(providers, allChannels)
        manager.buildQueue(ChannelQueueManager.QueueMode.ALL_CHANNELS)

        val queue = manager.currentQueue
        assertTrue("Disabled channel should not be in queue", queue.none { it.id == "c4" })
        assertTrue("Failed channel should not be in queue", queue.none { it.id == "c5" })
    }

    @Test
    fun verifyInfiniteLoopPreventedWhenIndexIsInvalid() {
        val manager = ChannelQueueManager(providers, allChannels, blockedChannelIds = setOf("c1", "c2", "c3"), skipBlocked = true)
        manager.buildQueue(ChannelQueueManager.QueueMode.ALL_CHANNELS)
        
        // Force invalid index
        val field = ChannelQueueManager::class.java.getDeclaredField("currentIndex")
        field.isAccessible = true
        field.set(manager, -1)

        // Ensure nextChannel() and previousChannel() terminate immediately and safely
        val next = manager.nextChannel()
        val prev = manager.previousChannel()
        
        assertNotNull(next)
        assertNotNull(prev)
    }
}
