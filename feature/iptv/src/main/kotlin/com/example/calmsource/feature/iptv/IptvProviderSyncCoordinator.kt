package com.example.calmsource.feature.iptv

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes catalog and EPG mutations that target the same provider. */
internal object IptvProviderSyncCoordinator {
    private val providerLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withProviderLock(providerId: String, block: suspend () -> T): T {
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        val mutex = synchronized(providerLocks) {
            providerLocks.getOrPut(providerId) { Mutex() }
        }
        return mutex.withLock { block() }
    }

    fun removeProviderLock(providerId: String) {
        providerLocks.remove(providerId)
    }
}
