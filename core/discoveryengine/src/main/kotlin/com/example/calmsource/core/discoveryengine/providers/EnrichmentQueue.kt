package com.example.calmsource.core.discoveryengine.providers

import com.example.calmsource.core.database.SlowQueryLogger
import com.example.calmsource.core.model.ResourceGovernor
import com.example.calmsource.core.model.ResourceGovernorSnapshot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class EnrichmentQueue(
    private val providerManager: ProviderManager,
    private val cacheStore: ProviderCacheStore,
    private val isLowMemoryMode: () -> Boolean,
    private val isPlaybackActive: () -> Boolean,
    private val isLocalOnlyMode: () -> Boolean,
    private val resourceSnapshot: () -> ResourceGovernorSnapshot = { ResourceGovernor.snapshot.value },
    private val rateLimiter: TokenBucketRateLimiter = TokenBucketRateLimiter(),
    private val capacity: Int = MAX_DEFERRED_TASKS
) {
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(supervisorJob + Dispatchers.IO)
    private val queueLock = Any()
    private val channel = Channel<EnrichmentTask>(
        capacity = capacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { task ->
            synchronized(queueLock) {
                queuedKeys.remove(task.dedupeKey)
            }
            SlowQueryLogger.record(
                sql = "provider_enrichment_deferred_queue_drop",
                args = listOf(task.mediaId),
                durationMs = 501L
            )
        }
    )
    private val queuedKeys = linkedSetOf<String>()
    private val canceledMediaIds = linkedSetOf<String>()
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<ProviderResult<*>>>()

    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            for (task in channel) {
                synchronized(queueLock) {
                    queuedKeys.remove(task.dedupeKey)
                }
                if (synchronized(queueLock) { canceledMediaIds.remove(task.mediaId) }) continue
                // Periodically prune canceledMediaIds to prevent unbounded growth
                // (entries are only removed when their task is dequeued; if the task was
                // already processed or dropped, the entry stays forever)
                if (synchronized(queueLock) { canceledMediaIds.size > MAX_CANCELED_ENTRIES }) {
                    synchronized(queueLock) {
                        val excess = canceledMediaIds.size - MAX_CANCELED_ENTRIES / 2
                        if (excess > 0) {
                            val iter = canceledMediaIds.iterator()
                            repeat(excess) { if (iter.hasNext()) { iter.next(); iter.remove() } }
                        }
                    }
                }
                waitUntilAllowed()
                try {
                    process(task)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Propagate cancellation so structured concurrency is preserved
                    throw e
                } catch (e: Exception) {
                    // Swallow only non-cancellation failures to keep the queue alive
                    android.util.Log.w("EnrichmentQueue", "Task ${task::class.simpleName} failed", e)
                }
            }
        }
    }

    fun enqueue(task: EnrichmentTask): Boolean {
        if (isLocalOnlyMode()) return false
        synchronized(queueLock) {
            if (!queuedKeys.add(task.dedupeKey)) return false
        }
        val result = channel.trySend(task)
        if (!result.isSuccess) {
            synchronized(queueLock) {
                queuedKeys.remove(task.dedupeKey)
            }
            return false
        }
        return true
    }

    fun cancelPendingForMedia(mediaId: String) {
        synchronized(queueLock) {
            canceledMediaIds.add(mediaId)
            if (canceledMediaIds.size > MAX_CANCELED_ENTRIES) {
                val excess = canceledMediaIds.size - MAX_CANCELED_ENTRIES / 2
                val iter = canceledMediaIds.iterator()
                repeat(excess) { if (iter.hasNext()) { iter.next(); iter.remove() } }
            }
        }
    }

    suspend fun snapshotRateLimits(): List<RateLimiterSnapshot> = rateLimiter.snapshot()

    fun snapshotQueuedCount(): Int {
        return synchronized(queueLock) { queuedKeys.size }
    }

    /**
     * Cancels the processing loop and closes the channel.
     * After shutdown, no new tasks will be processed.
     */
    fun shutdown() {
        started = false
        channel.cancel()
        supervisorJob.cancel()
        kotlinx.coroutines.runBlocking {
            supervisorJob.join()
        }
    }

    private suspend fun waitUntilAllowed() {
        while (
            scope.isActive &&
            (
                resourceSnapshot().shouldPauseBackgroundWork ||
                    isLowMemoryMode() ||
                    isPlaybackActive() ||
                    isLocalOnlyMode()
                )
        ) {
            delay(500)
        }
    }

    private suspend fun process(task: EnrichmentTask) {
        when (task) {
            is EnrichmentTask.FetchMetadata -> fetchMetadata(task)
            is EnrichmentTask.FetchRatings -> fetchRatings(task)
            is EnrichmentTask.FetchSimilar -> fetchSimilar(task)
            is EnrichmentTask.FetchSubtitles -> fetchSubtitles(task)
            is EnrichmentTask.CheckAvailability -> checkAvailability(task)
            is EnrichmentTask.RefreshArtwork -> refreshArtwork(task)
            is EnrichmentTask.FullEnrichment -> {
                fetchMetadata(EnrichmentTask.FetchMetadata(task.mediaId, task.profileId, task.externalIds))
                fetchRatings(EnrichmentTask.FetchRatings(task.mediaId, task.profileId, task.externalIds))
                fetchSimilar(EnrichmentTask.FetchSimilar(task.mediaId, task.profileId, task.externalIds))
                fetchSubtitles(EnrichmentTask.FetchSubtitles(task.mediaId, task.profileId, task.externalIds))
                checkAvailability(EnrichmentTask.CheckAvailability(task.mediaId, task.profileId, task.externalIds))
            }
        }
    }

    private suspend fun fetchMetadata(task: EnrichmentTask.FetchMetadata) {
        providerManager.getEnabledProviders(ProviderType.METADATA)
            .filterIsInstance<MetadataProvider>()
            .forEach { provider ->
                val result = safeCall(provider.providerId, "metadata", task.requestKey(provider.providerId, "metadata")) {
                    provider.fetchMetadata(task.mediaId, task.externalIds)
                }
                providerManager.recordResult(provider.providerId, "metadata", result)
                if (result is ProviderResult.Success) {
                    cacheStore.putMetadata(task.mediaId, provider.providerId, result.value)
                }
            }
    }

    private suspend fun fetchRatings(task: EnrichmentTask.FetchRatings) {
        providerManager.getEnabledProviders(ProviderType.RATING)
            .filterIsInstance<RatingProvider>()
            .forEach { provider ->
                val result = safeCall(provider.providerId, "ratings", task.requestKey(provider.providerId, "ratings")) {
                    provider.fetchRatings(task.mediaId, task.externalIds)
                }
                providerManager.recordResult(provider.providerId, "ratings", result)
                if (result is ProviderResult.Success) {
                    cacheStore.putRatings(task.mediaId, provider.providerId, result.value)
                }
            }
    }

    private suspend fun fetchSimilar(task: EnrichmentTask.FetchSimilar) {
        providerManager.getEnabledProviders(ProviderType.SIMILAR)
            .filterIsInstance<SimilarProvider>()
            .forEach { provider ->
                val result = safeCall(provider.providerId, "similar", task.requestKey(provider.providerId, "similar")) {
                    provider.fetchSimilar(task.mediaId, task.externalIds, task.limit)
                }
                providerManager.recordResult(provider.providerId, "similar", result)
                if (result is ProviderResult.Success) {
                    cacheStore.putSimilar(task.mediaId, provider.providerId, result.value)
                }
            }
    }

    private suspend fun fetchSubtitles(task: EnrichmentTask.FetchSubtitles) {
        providerManager.getEnabledProviders(ProviderType.SUBTITLE)
            .filterIsInstance<SubtitleProvider>()
            .forEach { provider ->
                val result = safeCall(provider.providerId, "subtitles", task.requestKey(provider.providerId, "subtitles")) {
                    provider.fetchSubtitles(task.mediaId, task.externalIds, task.languageHints)
                }
                providerManager.recordResult(provider.providerId, "subtitles", result)
                if (result is ProviderResult.Success) {
                    cacheStore.putSubtitles(task.mediaId, provider.providerId, result.value)
                }
            }
    }

    private suspend fun checkAvailability(task: EnrichmentTask.CheckAvailability) {
        providerManager.getEnabledProviders(ProviderType.AVAILABILITY)
            .filterIsInstance<AvailabilityProvider>()
            .forEach { provider ->
                val result = safeCall(provider.providerId, "availability", task.requestKey(provider.providerId, "availability")) {
                    provider.checkAvailability(task.mediaId, task.externalIds, task.addonIds)
                }
                providerManager.recordResult(provider.providerId, "availability", result)
                if (result is ProviderResult.Success) {
                    cacheStore.putAvailability(task.mediaId, provider.providerId, result.value)
                }
            }
    }

    private suspend fun refreshArtwork(task: EnrichmentTask.RefreshArtwork) {
        providerManager.getEnabledProviders(ProviderType.ARTWORK)
            .filterIsInstance<ArtworkProvider>()
            .forEach { provider ->
                val result = safeCall(provider.providerId, "artwork", task.requestKey(provider.providerId, "artwork")) {
                    provider.fetchArtwork(task.mediaId, task.externalIds)
                }
                providerManager.recordResult(provider.providerId, "artwork", result)
                if (result is ProviderResult.Success) {
                    val artwork = result.value
                    cacheStore.putMetadata(
                        mediaId = task.mediaId,
                        providerId = provider.providerId,
                        metadata = EnrichedMetadata(
                            title = null,
                            originalTitle = null,
                            overview = null,
                            director = null,
                            runtimeMinutes = null,
                            language = null,
                            country = null,
                            posterUrl = artwork.posterUrl ?: artwork.thumbnailUrl,
                            backdropUrl = artwork.backdropUrl,
                            externalIds = emptyMap()
                        )
                    )
                }
            }
    }

    private suspend fun <T> safeCall(
        providerId: String,
        requestType: String,
        requestKey: String,
        block: suspend () -> ProviderResult<T>
    ): ProviderResult<T> {
        @Suppress("UNCHECKED_CAST")
        val existing = inFlightRequests[requestKey] as Deferred<ProviderResult<T>>?
        if (existing != null) return existing.await()

        val deferred = scope.async(start = CoroutineStart.LAZY) {
            executeProviderCall(providerId, requestType, block)
        }
        @Suppress("UNCHECKED_CAST")
        val winner = inFlightRequests.putIfAbsent(requestKey, deferred) as Deferred<ProviderResult<T>>?
        if (winner != null) return winner.await()

        return try {
            deferred.await()
        } finally {
            inFlightRequests.remove(requestKey)
        }
    }

    private suspend fun <T> executeProviderCall(
        providerId: String,
        requestType: String,
        block: suspend () -> ProviderResult<T>
    ): ProviderResult<T> {
        return try {
            providerManager.requireProviderAvailable(providerId)
            if (!rateLimiter.acquire(providerId, requestType, ProviderRequestPriority.BACKGROUND)) {
                return ProviderResult.Skipped("rate_limited")
            }
            block()
        } catch (e: ProviderUnavailableException) {
            ProviderResult.CacheOnly()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ProviderResult.Failure(
                errorCode = "provider_exception",
                message = "$providerId/$requestType: ${e.javaClass.simpleName}"
            )
        }
    }

    private fun EnrichmentTask.requestKey(providerId: String, requestType: String): String {
        return listOf(providerId, requestType, dedupeKey).joinToString("|")
    }

    private companion object {
        private const val MAX_DEFERRED_TASKS = 50
        private const val MAX_CANCELED_ENTRIES = 200
    }
}
