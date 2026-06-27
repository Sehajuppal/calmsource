/**
 * Provider contracts for the CalmSource Universal Search system.
 *
 * This file defines the interface hierarchy that all search providers must
 * implement to participate in the concurrent search pipeline. The base
 * [SearchProvider] contract specifies the search method signature and a
 * priority value used to order result emission.
 *
 * Specialized marker interfaces ([IPTVSearchProvider], [EPGSearchProvider],
 * [VODSearchProvider], etc.) exist for type-safe provider registration and
 * allow the engine to identify provider categories without reflection.
 *
 * The [UniversalSearchEngine] interface defines the public search contract
 * that [UniversalSearchEngineImpl] implements.
 *
 * @see UniversalSearchEngineImpl for the concrete orchestration logic
 * @see FakeSearchProviders for demo/test implementations
 */
package com.example.calmsource.feature.search

import com.example.calmsource.core.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Base contract for a search provider that can contribute results to the
 * universal search pipeline.
 *
 * Each provider is identified by a unique [id], a human-readable [name],
 * and a [priority] that determines the order in which its results are
 * awaited and emitted (higher priority = emitted first).
 *
 * Implementations must be safe for concurrent invocation and should
 * respect the timeout configured in [SearchTimeoutPolicy].
 */
interface SearchProvider {
    val id: String
    val name: String
    val priority: Int // Higher value = higher priority sorting
    fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult>
}

interface IPTVSearchProvider : SearchProvider
interface EPGSearchProvider : SearchProvider
interface VODSearchProvider : SearchProvider
interface ExtensionSearchProvider : SearchProvider
interface DebridAvailabilityProvider : SearchProvider
interface SubtitleSearchProvider : SearchProvider
interface MetadataSearchProvider : SearchProvider
interface HistorySearchProvider : SearchProvider
interface SettingsSearchProvider : SearchProvider

/**
 * Contract for the universal search engine that orchestrates multiple
 * [SearchProvider]s concurrently and emits progressively refined
 * [SearchResultGroup] lists as providers complete.
 *
 * @see UniversalSearchEngineImpl for the default implementation
 */
interface UniversalSearchEngine {
    fun search(
        query: String,
        prefs: UserPreferences,
        timeoutPolicy: SearchTimeoutPolicy = SearchTimeoutPolicy()
    ): Flow<List<SearchResultGroup>>
}

private val normalizeForSearchRegex = Regex("[^a-z0-9]")

fun String.normalizeForSearch(): String {
    return this.lowercase().replace(normalizeForSearchRegex, "")
}

