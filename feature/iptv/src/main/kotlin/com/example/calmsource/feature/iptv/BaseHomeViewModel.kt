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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val loadMutex = Mutex()
    private var cachedExtensionRows = emptyList<RecommendationRow>()

    init {
        loadDeferredExtensionRows()
        viewModelScope.launch {
            DatabaseProvider.databaseReady
                .flatMapLatest { ready ->
                    if (!ready) {
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
                    if (IPTVRepository.isProviderSyncActive()) {
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
            loadMutex.withLock {
                _isLoading.value = true
                try {
                    val rows = withContext(Dispatchers.IO) {
                        val currentProfileId = sessionManager?.activeProfile?.value?.id ?: "default"
                        val preferenceSignals = memoryRepository.observePreferenceSignals(currentProfileId).first()
                        DiscoveryEngine.syncUserMemory(
                            profileId = currentProfileId,
                            continueWatching = memoryRepository.observeContinueWatching(currentProfileId).first(),
                            watchHistory = memoryRepository.observeWatchHistory(currentProfileId).first(),
                            favorites = memoryRepository.observeFavorites(currentProfileId).first()
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
                        localRows + iptvRows + extensionRows
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
                            val currentProfileId = sessionManager?.activeProfile?.value?.id ?: "default"
                            val preferenceSignals = memoryRepository.observePreferenceSignals(currentProfileId).first()
                            val iptvRows = listOfNotNull(IPTVRepository.getLiveChannelHomeRow())
                            val discoveryRows = repository.getHomeRows(
                                profileId = currentProfileId,
                                forceRefresh = true,
                                preferenceSignals = preferenceSignals
                            )
                            iptvRows + discoveryRows + cachedExtensionRows
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
                    val ctxJob = coroutineContext[Job]
                    if (loadJob == ctxJob && ctxJob?.isCancelled != true) {
                        _isLoading.value = false
                    }
                }
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
                // ignored
            } catch (_: Exception) {
                // ignored
            }
        }
    }

    fun retry() {
        _loadError.value = null
        loadHomeRows()
    }

    private fun publishHomeRows(rows: List<RecommendationRow>): Boolean {
        var visibleRows = rows
            .filter { it.items.isNotEmpty() }
            .distinctBy { it.rowType }
        if (maxRows != null) {
            visibleRows = visibleRows.take(maxRows)
        }
        if (visibleRows.isEmpty()) return false
        _homeRows.value = visibleRows
        _loadError.value = null
        return true
    }
}
