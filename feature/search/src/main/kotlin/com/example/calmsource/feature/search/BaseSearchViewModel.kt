package com.example.calmsource.feature.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.repository.RoomUserMemoryRepository
import com.example.calmsource.core.database.repository.UserMemoryRepository
import com.example.calmsource.core.database.repository.FallbackUserMemoryRepository
import com.example.calmsource.core.database.repository.UserPreferencesRepository
import com.example.calmsource.core.discoveryengine.DiscoveryEngine
import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineRepository
import com.example.calmsource.core.discoveryengine.models.MediaItem as DiscoveryMediaItem
import com.example.calmsource.core.discoveryengine.models.SearchEvent
import com.example.calmsource.core.model.SearchTimeoutPolicy
import com.example.calmsource.core.model.UserMemoryPrivacy
import com.example.calmsource.core.model.UserPreferenceSignalType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class BaseSearchViewModel(application: Application) : AndroidViewModel(application) {
    protected val repository: DiscoveryEngineRepository get() = DiscoveryEngine.repositoryOrThrow
    @Volatile
    protected var memoryRepository: UserMemoryRepository = FallbackUserMemoryRepository()
    private val signalSink = SearchSignalSink { query -> memoryRepository.recordSearch(query) }
    private val memorySnapshot = SearchMemorySnapshot {
        withContext(Dispatchers.IO) {
            val favorites = memoryRepository.observeFavorites().first()
            val history = memoryRepository.observeWatchHistory().first()
            val searches = memoryRepository.observeSearchHistory().first()
            SearchMemorySignals(
                recentQueries = searches.map { it.query },
                favoriteMediaIds = favorites.mapTo(linkedSetOf()) {
                    it.reference.sourceId ?: it.reference.itemKey
                },
                historyMediaIds = history.mapTo(linkedSetOf()) {
                    it.reference.sourceId ?: it.reference.itemKey
                }
            )
        }
    }
    private val searchEngine = UniversalSearchEngineImpl(
        signalSink = signalSink,
        memorySnapshot = memorySnapshot
    )

    init {
        viewModelScope.launch {
            DatabaseProvider.databaseReady.filter { it }.collect {
                memoryRepository = runCatching {
                    withContext(Dispatchers.IO) {
                        RoomUserMemoryRepository(DatabaseProvider.getDatabase(getApplication()))
                    }
                }.getOrElse { e ->
                    runCatching {
                        android.util.Log.e("BaseSearchViewModel", "Failed to create Room repository, falling back to in-memory", e)
                    }
                    FallbackUserMemoryRepository()
                }
            }
        }
    }
    
    private val _searchResults = MutableStateFlow<List<SearchDisplayResult>>(emptyList())
    val searchResults: StateFlow<List<SearchDisplayResult>> = _searchResults

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _scrollPosition = MutableStateFlow(0 to 0)
    val scrollPosition: StateFlow<Pair<Int, Int>> = _scrollPosition
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    // Active facet filters (e.g. {"type":"movie"}, {"genre":"action"}). Empty means unfiltered.
    private val _filters = MutableStateFlow<Map<String, String>>(emptyMap())
    val filters: StateFlow<Map<String, String>> = _filters

    private var searchJob: Job? = null
    private var searchGeneration = 0

    /** Sets or clears a single facet filter and re-runs the current query immediately. */
    fun setFilter(key: String, value: String?) {
        val current = _filters.value.toMutableMap()
        if (value.isNullOrBlank() || value.equals("all", ignoreCase = true)) {
            current.remove(key)
        } else {
            current[key] = value
        }
        if (current == _filters.value) return
        _filters.value = current
        val activeQuery = _query.value
        if (activeQuery.isNotBlank()) {
            runSearch(query = activeQuery, includeConnectedProviders = false, debounceMs = 0)
        }
    }

    private fun matchesTypeFilter(result: SearchDisplayResult): Boolean {
        val typeFilter = _filters.value["type"]?.lowercase() ?: return true
        return when (typeFilter) {
            "movie" -> result.type == "movie"
            "series", "show" -> result.type == "series"
            "channel" -> result.type == "channel"
            else -> true
        }
    }

    suspend fun recordSearchInterest(query: String, result: SearchDisplayResult) {
        if (query.isBlank() || result.type == "channel") return
        val safeQuery = UserMemoryPrivacy.sanitizeSearchQuery(query) ?: return
        val safeResultId = runCatching {
            UserMemoryPrivacy.requireSafeIdentifier(result.id, "searchResultId")
        }.getOrNull() ?: return
        try {
            DiscoveryEngine.ingestStremioItems(
                listOf(
                    DiscoveryMediaItem(
                        id = safeResultId,
                        type = if (result.type == "series") "series" else "movie",
                        title = result.title,
                        overview = result.subtitle,
                        posterUrl = result.posterUrl,
                        externalIds = result.externalIds,
                        source = result.sourceLabel
                    )
                )
            )
            DiscoveryEngine.trackSearchEvent(
                SearchEvent(
                    profileId = "default",
                    query = safeQuery,
                    timestamp = System.currentTimeMillis(),
                    selectedItemId = safeResultId
                )
            )
            memoryRepository.incrementPreferenceSignal(
                signalType = UserPreferenceSignalType.SEARCH_RESULT_SELECTION,
                signalKey = safeResultId
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Search remains usable when optional personalization persistence fails.
        }
    }

    fun search(query: String) {
        updateQuery(query)
        runSearch(query = query, includeConnectedProviders = false, debounceMs = 300)
    }

    fun submitSearch(query: String) {
        updateQuery(query)
        runSearch(query = query, includeConnectedProviders = true, debounceMs = 0)
    }

    fun updateScrollPosition(index: Int, offset: Int) {
        _scrollPosition.value = index.coerceAtLeast(0) to offset.coerceAtLeast(0)
    }

    private fun updateQuery(query: String) {
        if (_query.value != query) {
            _query.value = query
            _scrollPosition.value = 0 to 0
        }
    }

    private fun runSearch(
        query: String,
        includeConnectedProviders: Boolean,
        debounceMs: Long
    ) {
        searchJob?.cancel()
        val generation = ++searchGeneration
        
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            if (debounceMs > 0) {
                delay(debounceMs)
            }
            
            var localResults = emptyList<SearchDisplayResult>()
            try {
                localResults = try {
                    repository.fullSearch(query = query, profileId = "default", filters = _filters.value)
                        .map { result ->
                            SearchDisplayResult(
                                id = result.id,
                                type = result.type,
                                title = result.title,
                                subtitle = result.subtitle,
                                posterUrl = result.posterUrl,
                                sourceLabel = result.source.ifBlank { "Local index" },
                                hasPlayableSource = result.scoreBreakdown.availabilityScore > 0.0,
                                score = result.score,
                                externalIds = result.externalIds
                            )
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
                if (generation == searchGeneration) {
                    _searchResults.value = localResults
                }

                if (includeConnectedProviders) {
                    val timeoutPolicy = SearchTimeoutPolicy(
                        providerTimeoutsMs = mapOf("prov-extensions" to 20_000L)
                    )
                    searchEngine.search(
                        query = query,
                        prefs = UserPreferencesRepository.preferences.value,
                        timeoutPolicy = timeoutPolicy
                    ).collect { groups ->
                        if (generation == searchGeneration) {
                            _searchResults.value = (groups.toSearchDisplayResults() + localResults)
                                .distinctBy { "${it.type}:${it.id}" }
                                .filter(::matchesTypeFilter)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (generation == searchGeneration) {
                    _searchResults.value = localResults
                }
            } finally {
                if (generation == searchGeneration) {
                    _isSearching.value = false
                }
            }
        }
    }
}
