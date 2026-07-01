package com.example.calmsource.feature.iptv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calmsource.core.data.ProfileSessionManager
import com.example.calmsource.core.data.FallbackProfileSessionManager
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.repository.UserMemoryRepository
import com.example.calmsource.core.database.repository.RoomUserMemoryRepository
import com.example.calmsource.core.database.repository.FallbackUserMemoryRepository
import com.example.calmsource.core.discoveryengine.DiscoveryEngine
import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineRepository
import com.example.calmsource.core.discoveryengine.models.RecommendationRow
import com.example.calmsource.feature.extensions.ExtensionRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
open class BaseHomeViewModel(
    application: Application,
    private val maxRows: Int? = null,
    private val observeHomeDataUseCase: ObserveHomeDataUseCase? = null,
    protected val sessionManager: ProfileSessionManager? = null
) : AndroidViewModel(application) {
    protected val repository: DiscoveryEngineRepository get() = DiscoveryEngine.repositoryOrThrow
    @Volatile
    protected var memoryRepository: UserMemoryRepository = FallbackUserMemoryRepository()

    private val _homeRows = MutableStateFlow<List<RecommendationRow>>(emptyList())
    val homeRows: StateFlow<List<RecommendationRow>> = _homeRows
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    private var loadJob: Job? = null
    private var cachedExtensionRows = emptyList<RecommendationRow>()

    init {
        loadDeferredExtensionRows()
        viewModelScope.launch {
            DatabaseProvider.databaseReady
                .flatMapLatest { ready ->
                    if (!ready) {
                        memoryRepository = FallbackUserMemoryRepository()
                        flowOf(Unit)
                    } else {
                        memoryRepository = runCatching {
                            withContext(Dispatchers.IO) {
                                RoomUserMemoryRepository(DatabaseProvider.getDatabase(getApplication()))
                            }
                        }.getOrElse { e ->
                            runCatching {
                                android.util.Log.e("BaseHomeViewModel", "Failed to create Room repository, falling back to in-memory", e)
                            }
                            FallbackUserMemoryRepository()
                        }
                        val useCase = observeHomeDataUseCase ?: ObserveHomeDataUseCase(
                            memoryRepository,
                            sessionManager ?: FallbackProfileSessionManager()
                        )
                        useCase.execute()
                    }
                }
                .debounce(500)
                .collect {
                    if (IPTVRepository.isProviderSyncActive() && _homeRows.value.isNotEmpty()) {
                        refreshLiveRowOnly()
                    } else {
                        loadHomeRows()
                    }
                }
        }
    }

    private fun refreshLiveRowOnly() {
        viewModelScope.launch {
            val liveRow = withContext(Dispatchers.IO) {
                IPTVRepository.getLiveChannelHomeRow()
            } ?: return@launch
            val current = _homeRows.value
            if (current.isEmpty()) return@launch
            val updated = current.map { row ->
                if (row.rowType == "live_tv") liveRow else row
            }
            if (updated != current) {
                _homeRows.value = updated
            }
        }
    }

    fun loadHomeRows() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val rows = withContext(Dispatchers.IO) {
                    coroutineScope {
                        val currentProfileId = sessionManager?.activeProfile?.value?.id ?: "default"
                        val preferenceSignalsDeferred = async { memoryRepository.observePreferenceSignals(currentProfileId).first() }
                        val continueWatchingDeferred = async { memoryRepository.observeContinueWatching(currentProfileId).first() }
                        val watchHistoryDeferred = async { memoryRepository.observeWatchHistory(currentProfileId).first() }
                        val favoritesDeferred = async { memoryRepository.observeFavorites(currentProfileId).first() }

                        val preferenceSignals = preferenceSignalsDeferred.await()
                        DiscoveryEngine.syncUserMemory(
                            profileId = currentProfileId,
                            continueWatching = continueWatchingDeferred.await(),
                            watchHistory = watchHistoryDeferred.await(),
                            favorites = favoritesDeferred.await()
                        )
                        kotlinx.coroutines.yield()

                        val localRows = repository.getHomeRows(
                            profileId = currentProfileId,
                            preferenceSignals = preferenceSignals
                        )
                        kotlinx.coroutines.yield()

                        val iptvRows = listOfNotNull(IPTVRepository.getLiveChannelHomeRow())
                        val extensionRows = cachedExtensionRows.ifEmpty {
                            ExtensionRepository.getCachedDiscoveryCatalogHomeRows()
                        }
                        iptvRows + localRows.filterNot { it.rowType == "live_tv" } + extensionRows
                    }
                }
                publishHomeRows(rows)
            } catch (e: CancellationException) {
                throw e
            } catch (primary: Exception) {
                runCatching {
                    android.util.Log.e("BaseHomeViewModel", "Primary home-row load failed; attempting fallback", primary)
                }
                com.example.calmsource.core.observability.CrashReporter.recordNonFatal(primary, "home_rows_primary_load")
                try {
                    val fallbackRows = withContext(Dispatchers.IO) {
                        coroutineScope {
                            val currentProfileId = sessionManager?.activeProfile?.value?.id ?: "default"
                            val preferenceSignalsDeferred = async { memoryRepository.observePreferenceSignals(currentProfileId).first() }
                            val iptvRows = listOfNotNull(IPTVRepository.getLiveChannelHomeRow())
                            val discoveryRows = repository.getHomeRows(
                                profileId = currentProfileId,
                                forceRefresh = true,
                                preferenceSignals = preferenceSignalsDeferred.await()
                            )
                            iptvRows + discoveryRows.filterNot { it.rowType == "live_tv" } + cachedExtensionRows
                        }
                    }
                    publishHomeRows(fallbackRows)
                } catch (e: CancellationException) {
                    throw e
                } catch (fallback: Exception) {
                    runCatching {
                        android.util.Log.e("BaseHomeViewModel", "Fallback home-row load failed", fallback)
                    }
                    com.example.calmsource.core.observability.CrashReporter.recordNonFatal(fallback, "home_rows_fallback_load")
                    if (_homeRows.value.isEmpty()) {
                        _loadError.value = "We couldn't load your home feed. Check your connection and try again."
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadDeferredExtensionRows() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(3_000L)
            try {
                val extensionRows = kotlinx.coroutines.withTimeoutOrNull(35_000L) {
                    withContext(Dispatchers.IO) {
                        ExtensionRepository.ensureDiscoveryCatalogHomeRows()
                    }
                } ?: ExtensionRepository.getCachedDiscoveryCatalogHomeRows()
                if (extensionRows.isNotEmpty()) {
                    cachedExtensionRows = extensionRows
                    if (!IPTVRepository.isProviderSyncActive()) {
                        loadHomeRows()
                    } else {
                        refreshLiveRowOnly()
                    }
                }
            } catch (_: CancellationException) {
                // cancellation on scope exit — expected, not logged
            } catch (e: Exception) {
                android.util.Log.w("BaseHomeViewModel", "Extension catalog refresh failed, using cached rows", e)
                cachedExtensionRows = ExtensionRepository.getCachedDiscoveryCatalogHomeRows()
            }
        }
    }

    fun retry() {
        _loadError.value = null
        loadDeferredExtensionRows()
        loadHomeRows()
    }

    private fun publishHomeRows(rows: List<RecommendationRow>): Boolean {
        var visibleRows = rows
            .filter { it.items.isNotEmpty() }
            .distinctBy { it.rowType }
        if (maxRows != null) {
            visibleRows = visibleRows.take(maxRows)
        }
        val quickPreview = buildQuickPreviewRow(visibleRows)
        if (quickPreview != null && visibleRows.none { it.rowType == "quick_preview" }) {
            visibleRows = visibleRows + quickPreview
        }
        if (visibleRows.isEmpty()) return false
        val ordered = visibleRows.sortedBy { row ->
            when (row.rowType) {
                "continue_watching" -> 0
                "live_tv" -> 1
                "quick_preview" -> 2
                else -> 3
            }
        }
        _homeRows.value = ordered
        _loadError.value = null
        return true
    }

    private fun buildQuickPreviewRow(rows: List<RecommendationRow>): RecommendationRow? {
        val items = rows.asSequence()
            .filter {
                it.rowType != "quick_preview" &&
                    it.rowType != "live_tv" &&
                    it.rowType != "continue_watching"
            }
            .flatMap { it.items.asSequence() }
            .filter { item ->
                item.type != "channel" &&
                    (!item.backdropUrl.isNullOrBlank() || !item.posterUrl.isNullOrBlank())
            }
            .distinctBy { it.id }
            .take(10)
            .toList()
        if (items.size < 3) return null
        return RecommendationRow(
            title = "quick_preview",
            rowType = "quick_preview",
            items = items.toImmutableList(),
        )
    }
}
