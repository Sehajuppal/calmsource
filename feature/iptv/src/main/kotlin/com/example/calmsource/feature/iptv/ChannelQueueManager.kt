package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.IPTVProvider
import com.example.calmsource.core.model.ProviderHealth

class ChannelQueueManager(
    private val providers: List<IPTVProvider>,
    private val allChannels: List<IPTVChannel>,
    private val favoriteChannelIds: Set<String>? = null,
    private val blockedChannelIds: Set<String> = emptySet(),
    private val skipBlocked: Boolean = false
) {

    enum class QueueMode {
        ALL_CHANNELS,
        CATEGORY,
        FAVORITES
    }

    var currentQueue: List<IPTVChannel> = emptyList()
        private set
    
    var currentIndex: Int = -1
        private set

    fun buildQueue(mode: QueueMode, categoryName: String? = null) {
        val validProviders = providers.filter { it.isEnabled && it.health != ProviderHealth.FAILED }
        val validProviderIds = validProviders.map { it.id }.toSet()

        val validChannels = allChannels.filter { 
            it.providerId in validProviderIds 
        }

        // Deduplicate channels (keep first seen by id to be safe)
        val distinctChannels = validChannels.distinctBy { it.id }

        // Provider order is preserved by matching the order in the `providers` list
        val providerOrderMap = providers.mapIndexed { index, provider -> provider.id to index }.toMap()
        
        val sortedChannels = distinctChannels.sortedWith(
            compareBy<IPTVChannel> { providerOrderMap[it.providerId] ?: Int.MAX_VALUE }
        )

        currentQueue = when (mode) {
            QueueMode.ALL_CHANNELS -> {
                sortedChannels
            }
            QueueMode.CATEGORY -> {
                if (categoryName.isNullOrEmpty()) {
                    emptyList() // Empty category handled calmly
                } else {
                    sortedChannels.filter { it.groupTitle.equals(categoryName, ignoreCase = true) }
                }
            }
            QueueMode.FAVORITES -> {
                if (favoriteChannelIds != null) {
                    sortedChannels.filter { it.id in favoriteChannelIds }
                } else {
                    emptyList()
                }
            }
        }
        
        currentIndex = if (currentQueue.isNotEmpty()) 0 else -1
    }

    fun setChannel(channelId: String) {
        val index = currentQueue.indexOfFirst { it.id == channelId }
        if (index != -1) {
            currentIndex = index
        }
    }

    fun getCurrentChannel(): IPTVChannel? {
        if (currentIndex in currentQueue.indices) {
            return currentQueue[currentIndex]
        }
        return null
    }

    /**
     * Advances to the next channel in queue order. When [skipBlocked] is true,
     * channels whose id is in [blockedChannelIds] are skipped. When
     * [skipBlocked] is false, all channels are returned in order regardless
     * of blocked status.
     *
     * If every channel is blocked and [skipBlocked] is true, stays on the
     * current channel rather than looping forever.
     */
    fun nextChannel(): IPTVChannel? {
        if (currentQueue.isEmpty()) return null
        val startIdx = normalizeCurrentIndex()
        var tempIdx = startIdx
        do {
            tempIdx = (tempIdx + 1) % currentQueue.size
            val candidate = currentQueue[tempIdx]
            val isBlocked = candidate.id in blockedChannelIds
            val shouldAccept = if (skipBlocked) !isBlocked else true
            if (shouldAccept) {
                currentIndex = tempIdx
                return candidate
            }
        } while (tempIdx != startIdx)
        
        // All channels are blocked and skipBlocked=true: stay on current
        currentIndex = startIdx
        return getCurrentChannel()
    }

    fun previousChannel(): IPTVChannel? {
        if (currentQueue.isEmpty()) return null
        val startIdx = normalizeCurrentIndex()
        var tempIdx = startIdx
        do {
            tempIdx = if (tempIdx - 1 < 0) currentQueue.size - 1 else tempIdx - 1
            val candidate = currentQueue[tempIdx]
            val isBlocked = candidate.id in blockedChannelIds
            val shouldAccept = if (skipBlocked) !isBlocked else true
            if (shouldAccept) {
                currentIndex = tempIdx
                return candidate
            }
        } while (tempIdx != startIdx)
        
        // All channels are blocked and skipBlocked=true: stay on current
        currentIndex = startIdx
        return getCurrentChannel()
    }

    fun getFallbackOptions(failedChannelId: String): Pair<IPTVChannel?, List<IPTVChannel>> {
        val nextUnblocked = findNextUnblockedChannel(failedChannelId)
        val otherChannels = currentQueue.filter { it.id != failedChannelId && it.id !in blockedChannelIds }
        return Pair(nextUnblocked, otherChannels)
    }

    /**
     * Finds the next unblocked channel after [startChannelId], regardless of
     * [skipBlocked]. This is used for playback fallback — if the current
     * channel fails, we always want an unblocked alternative.
     */
    private fun findNextUnblockedChannel(startChannelId: String): IPTVChannel? {
        val startIndex = currentQueue.indexOfFirst { it.id == startChannelId }
        if (startIndex == -1 || currentQueue.size <= 1) return null
        var tempIdx = startIndex
        do {
            tempIdx = (tempIdx + 1) % currentQueue.size
            val candidate = currentQueue[tempIdx]
            if (candidate.id !in blockedChannelIds) {
                return candidate
            }
        } while (tempIdx != startIndex)
        return null
    }

    private fun normalizeCurrentIndex(): Int {
        if (currentQueue.isEmpty()) {
            currentIndex = -1
            return -1
        }
        if (currentIndex !in currentQueue.indices) {
            currentIndex = 0
        }
        return currentIndex
    }
}
