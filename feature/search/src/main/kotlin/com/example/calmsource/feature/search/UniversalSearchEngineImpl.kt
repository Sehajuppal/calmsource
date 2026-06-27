/**
 * Concurrent multi-provider search engine implementation.
 *
 * This class launches all registered [SearchProvider]s concurrently using
 * Kotlin coroutines, then emits results in completion order (fastest first).
 * After each provider completes, the accumulated results are merged via
 * [SearchResultMerger] and emitted as a [Flow], giving the UI progressively
 * richer results as slower providers finish.
 *
 * Timeout handling:
 * - Each provider is wrapped in [withTimeout] using limits from
 *   [SearchTimeoutPolicy]. Timed-out providers return an error result
 *   rather than blocking the pipeline.
 * - Non-cancellation exceptions are caught and surfaced as error results.
 *
 * @see SearchProvider for the provider contract
 * @see SearchResultMerger for the merge + rank + group logic
 */
package com.example.calmsource.feature.search

import com.example.calmsource.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UniversalSearchEngineImpl(
    private val providers: List<SearchProvider> = listOf(
        IPTVSearchProviderImpl(),
        EPGSearchProviderImpl(),
        VODSearchProviderImpl(),
        SettingsSearchProviderImpl(),
        MetadataSearchProviderImpl(),
        DebridAvailabilityProviderImpl(),
        SubtitleSearchProviderImpl(),
        ExtensionSearchProviderImpl()
    ),
    private val signalSink: SearchSignalSink = NoOpSearchSignalSink,
    private val memorySnapshot: SearchMemorySnapshot = EmptySearchMemorySnapshot
) : UniversalSearchEngine {

    /**
     * Executes a search across all providers concurrently and emits
     * progressively merged [SearchResultGroup] lists.
     *
     * Provider results are emitted in completion order — whichever provider
     * finishes first gets its results merged and emitted first. This avoids
     * blocking on slow high-priority providers when fast low-priority ones
     * (e.g. Settings) have already completed.
     * Each completion triggers a re-merge and re-emit of the accumulated results.
     *
     * @param query the raw search string; blank queries emit an empty list
     * @param prefs user preferences for scoring and filtering
     * @param timeoutPolicy per-provider and default timeout limits
     * @return a [Flow] of grouped results, emitted once per provider completion
     */
    override fun search(
        query: String,
        prefs: UserPreferences,
        timeoutPolicy: SearchTimeoutPolicy
    ): Flow<List<SearchResultGroup>> = channelFlow {
        val cleanQuery = query.take(500)
        if (cleanQuery.isBlank()) {
            send(emptyList())
            return@channelFlow
        }

        val searchQuery = SearchQuery(cleanQuery)
        val memorySignals = loadMemorySignals()

        if (providers.isEmpty()) {
            send(emptyList())
            recordCompletedQuery(cleanQuery)
            return@channelFlow
        }

        // supervisorScope isolates provider failures: one crashing provider
        // won't cancel siblings. Each provider runs on Dispatchers.IO so
        // slow catalog parsing doesn't block the caller's dispatcher.
        supervisorScope {
            launch(Dispatchers.Default) {
                val flows = providers.map { provider ->
                    flow {
                        val limit = timeoutPolicy.providerTimeoutsMs[provider.id]
                            ?: timeoutPolicy.defaultTimeoutMs
                        try {
                            withTimeout(limit) {
                                provider.search(searchQuery, prefs).collect { emit(it) }
                            }
                        } catch (e: TimeoutCancellationException) {
                            emit(SearchProviderResult(
                                providerId = provider.id,
                                providerName = provider.name,
                                query = searchQuery,
                                error = RuntimeException("Timeout limit of $limit ms exceeded")
                            ))
                        } catch (e: CancellationException) {
                            if (!currentCoroutineContext().isActive) {
                                throw e
                            }
                            emit(SearchProviderResult(
                                providerId = provider.id,
                                providerName = provider.name,
                                query = searchQuery,
                                error = RuntimeException("Provider search cancelled", e)
                            ))
                        } catch (e: Throwable) {
                            emit(SearchProviderResult(
                                providerId = provider.id,
                                providerName = provider.name,
                                query = searchQuery,
                                error = e
                            ))
                        }
                    }.flowOn(Dispatchers.IO)
                }

                val accumulatedResults = mutableListOf<SearchProviderResult>()
                val mutex = Mutex()
                val trigger = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

                launch {
                    for (event in trigger) {
                        delay(50L) // batch rapid provider result completions
                        val currentResults = mutex.withLock { accumulatedResults.toList() }
                        val mergedGroups = withContext(Dispatchers.Default) {
                            SearchResultMerger.merge(
                                providerResults = currentResults,
                                query = query,
                                prefs = prefs,
                                favorites = memorySignals.favoriteMediaIds.toList(),
                                history = memorySignals.historyMediaIds.toList(),
                                recentQueries = memorySignals.recentQueries
                            )
                        }
                        send(mergedGroups)
                    }
                }

                flows.merge().collect { providerResult ->
                    mutex.withLock {
                        val index = accumulatedResults.indexOfFirst { it.providerId == providerResult.providerId }
                        if (index >= 0) {
                            accumulatedResults[index] = providerResult
                        } else {
                            accumulatedResults.add(providerResult)
                        }
                    }
                    trigger.trySend(Unit)
                }
                trigger.close()
            }
        }

        recordCompletedQuery(query)
    }

    private suspend fun loadMemorySignals(): SearchMemorySignals {
        return try {
            memorySnapshot.load().sanitized()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            SearchMemorySignals()
        }
    }

    private suspend fun recordCompletedQuery(query: String) {
        val safeQuery = sanitizeCompletedSearchQuery(query) ?: return
        try {
            signalSink.recordCompletedQuery(safeQuery)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Search completion must not fail because optional memory persistence failed.
        }
    }
}
