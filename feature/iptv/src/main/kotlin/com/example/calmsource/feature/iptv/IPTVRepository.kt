package com.example.calmsource.feature.iptv

import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.dao.XtreamDao
import com.example.calmsource.core.database.dao.escapeSqlLike
import com.example.calmsource.core.database.SourceHealthRepository
import com.example.calmsource.core.database.repository.UserPreferencesRepository
import com.example.calmsource.core.database.mapper.*
import com.example.calmsource.core.discoveryengine.DiscoveryEngine
import com.example.calmsource.core.discoveryengine.models.EpgProgram as DiscoveryEpgProgram
import com.example.calmsource.core.discoveryengine.models.MediaItem as DiscoveryMediaItem
import com.example.calmsource.core.model.*
import com.example.calmsource.core.parser.M3UParser
import com.example.calmsource.core.parser.XMLTVParser
import com.example.calmsource.core.parser.XMLTVTimeWindow
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.use
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.client.plugins.timeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import androidx.room.withTransaction
import kotlinx.coroutines.sync.withLock
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale
import android.content.Context
import androidx.annotation.VisibleForTesting
import com.example.calmsource.feature.iptv.xtream.XtreamApiClientImpl
import com.example.calmsource.feature.iptv.xtream.XtreamServerUrlNormalizer
import com.example.calmsource.feature.iptv.xtream.XtreamStreamUrlBuilder
import com.example.calmsource.feature.iptv.XtreamApiClient
import com.example.calmsource.core.model.TestEnvironment
import java.util.UUID

@OptIn(kotlinx.coroutines.FlowPreview::class)
object IPTVRepository {

  @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        XtreamRepository.init(context)
        IptvOptimizationStore.init(context)
    }

    private val dataLock = Any()
    private val epgSyncMutex = kotlinx.coroutines.sync.Mutex()
    private val epgBackgroundSyncProviders = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )
    private val playlistSyncMutex = kotlinx.coroutines.sync.Mutex()
    private val xtreamSyncMutex = kotlinx.coroutines.sync.Mutex()
    private val epgMatchMutex = kotlinx.coroutines.sync.Mutex()

    /** Clears in-memory EPG resolution caches (e.g. after credential or provider changes). */
    fun clearResolvedUrlCache() {
        epgCache.evictAll()
    }
    
    @VisibleForTesting
    var epgProgramsIngestedInTest: MutableList<EPGProgram>? = null

    private val activeSyncJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    private fun registerSyncJob(id: String, job: kotlinx.coroutines.Job) {
        val oldJob = activeSyncJobs.put(id, job)
        oldJob?.cancel()
    }

    private fun unregisterSyncJob(id: String, job: kotlinx.coroutines.Job) {
        activeSyncJobs.remove(id, job)
    }

    private val NORMALIZE_REGEX = Regex("[^a-z0-9]")
    private val isTest: Boolean get() = TestEnvironment.isTest
    private val scope = kotlinx.coroutines.CoroutineScope((if (isTest) kotlinx.coroutines.Dispatchers.Unconfined else kotlinx.coroutines.Dispatchers.IO) + kotlinx.coroutines.SupervisorJob())
    private var coroutineScope: kotlinx.coroutines.CoroutineScope = scope

    // Dedicated scope for provider sync (add + full catalog fetch).
    // Separate from `scope` so cancelBackgroundWork() only cancels
    // discovery ingestion, not the provider sync the user just initiated.
    private val syncScope = kotlinx.coroutines.CoroutineScope(
        (if (isTest) kotlinx.coroutines.Dispatchers.Unconfined else kotlinx.coroutines.Dispatchers.IO) + kotlinx.coroutines.SupervisorJob()
    )

    fun setCoroutineScopeForTesting(testScope: kotlinx.coroutines.CoroutineScope) {
        coroutineScope = testScope
    }

    @Volatile
    private var _xtreamApiClient: XtreamApiClient? = null

    private val xtreamApiClient: XtreamApiClient
        get() = _xtreamApiClient ?: synchronized(this) {
            _xtreamApiClient ?: XtreamApiClientImpl().also { _xtreamApiClient = it }
        }

    @VisibleForTesting
    fun setXtreamApiClientForTesting(client: XtreamApiClient) {
        _xtreamApiClient = client
    }

    /** Cancel in-flight discovery ingestion to free up the database for
     *  playback navigation. Called before opening the player screen to
     *  avoid ANRs from Room contention.
     *
     *  Provider sync jobs (add + catalog fetch) are NOT cancelled —
     *  they run in a separate [syncScope] so the user's Xtream setup
     *  completes even if they navigate to the player. */
    fun cancelBackgroundWork() {
        discoveryIngestionJob?.cancel()
    }

    private fun runIO(block: suspend () -> Unit) {
        if (isTest) {
            kotlinx.coroutines.runBlocking { block() }
        } else {
            coroutineScope.launch { block() }
        }
    }
    private fun databaseOrNull() = DatabaseProvider.databaseOrNull()

    private val fallbackDao: com.example.calmsource.core.database.dao.IPTVDao by lazy {
        object : com.example.calmsource.core.database.dao.IPTVDao {
            private val fallbackLock = Any()
            @Volatile private var provMem = listOf<com.example.calmsource.core.database.entity.IPTVProviderEntity>()
            @Volatile private var chanMem = listOf<com.example.calmsource.core.database.entity.IPTVChannelEntity>()
            @Volatile private var epgSrcMem = listOf<com.example.calmsource.core.database.entity.EPGSourceEntity>()
            @Volatile private var progMem = listOf<com.example.calmsource.core.database.entity.EPGProgramEntity>()

            private val provFlow = MutableStateFlow(emptyList<com.example.calmsource.core.database.entity.IPTVProviderEntity>())
            private val chanFlow = MutableStateFlow(emptyList<com.example.calmsource.core.database.entity.IPTVChannelEntity>())
            private val epgSrcFlow = MutableStateFlow(emptyList<com.example.calmsource.core.database.entity.EPGSourceEntity>())
            private val progFlow = MutableStateFlow(emptyList<com.example.calmsource.core.database.entity.EPGProgramEntity>())

            override fun getAllProviders() = provFlow
            override fun getProviderById(id: String) = provFlow.map { list -> list.firstOrNull { it.id == id } }
            override suspend fun getProviderByIdDirect(id: String): com.example.calmsource.core.database.entity.IPTVProviderEntity? = synchronized(fallbackLock) {
                provMem.firstOrNull { it.id == id }
            }
            override fun insertProvider(provider: com.example.calmsource.core.database.entity.IPTVProviderEntity): Long {
                synchronized(fallbackLock) {
                    provMem = provMem.filter { it.id != provider.id } + provider
                    provFlow.value = provMem
                }
                return 1L
            }
            override fun updateProvider(provider: com.example.calmsource.core.database.entity.IPTVProviderEntity): Int {
                insertProvider(provider)
                return 1
            }
            override fun deleteProvider(provider: com.example.calmsource.core.database.entity.IPTVProviderEntity): Int {
                synchronized(fallbackLock) {
                    provMem = provMem.filter { it.id != provider.id }
                    provFlow.value = provMem
                }
                return 1
            }
            override fun getAllChannels() = chanFlow
            override fun getChannelsByProvider(providerId: String) = chanFlow.map { list -> list.filter { it.providerId == providerId } }
            override fun insertChannels(channels: List<com.example.calmsource.core.database.entity.IPTVChannelEntity>): List<Long> {
                val ids = channels.map { it.id }.toSet()
                synchronized(fallbackLock) {
                    chanMem = chanMem.filter { it.id !in ids } + channels
                    chanFlow.value = chanMem
                }
                return channels.map { 1L }
            }
            override fun deleteChannelsByProvider(providerId: String): Int {
                synchronized(fallbackLock) {
                    chanMem = chanMem.filter { it.providerId != providerId }
                    chanFlow.value = chanMem
                }
                return 1
            }
            override fun getAllEPGSources() = epgSrcFlow
            override fun insertEPGSource(source: com.example.calmsource.core.database.entity.EPGSourceEntity): Long {
                synchronized(fallbackLock) {
                    epgSrcMem = epgSrcMem.filter { it.id != source.id } + source
                    epgSrcFlow.value = epgSrcMem
                }
                return 1L
            }
            override fun deleteEPGSource(source: com.example.calmsource.core.database.entity.EPGSourceEntity): Int {
                synchronized(fallbackLock) {
                    epgSrcMem = epgSrcMem.filter { it.id != source.id }
                    epgSrcFlow.value = epgSrcMem
                }
                return 1
            }
            override fun deleteEPGSourcesByProvider(providerId: String): Int {
                synchronized(fallbackLock) {
                    epgSrcMem = epgSrcMem.filter { it.providerId != providerId }
                    epgSrcFlow.value = epgSrcMem
                }
                return 1
            }
            override fun getEPGProgramsByChannel(channelId: String) = progFlow.map { list -> list.filter { it.channelId == channelId } }
            override fun insertEPGPrograms(programs: List<com.example.calmsource.core.database.entity.EPGProgramEntity>): List<Long> {
                val ids = programs.map { it.id }.toSet()
                synchronized(fallbackLock) {
                    progMem = progMem.filter { it.id !in ids } + programs
                    progFlow.value = progMem
                }
                return programs.map { 1L }
            }
            override fun deleteEPGProgramsByChannel(channelId: String): Int {
                synchronized(fallbackLock) {
                    progMem = progMem.filter { it.channelId != channelId }
                    progFlow.value = progMem
                }
                return 1
            }
            override fun deleteEPGProgramsByChannels(channelIds: Set<String>): Int {
                synchronized(fallbackLock) {
                    progMem = progMem.filter { it.channelId !in channelIds }
                    progFlow.value = progMem
                }
                return 1
            }
            override fun deleteEPGProgramsByProvider(providerId: String): Int {
                synchronized(fallbackLock) {
                    val providerChannelIds = chanMem
                        .asSequence()
                        .filter { it.providerId == providerId }
                        .flatMap { sequenceOf(it.id, it.tvgId).filterNotNull() }
                        .toSet()
                    val sharedTvgIds = chanMem
                        .asSequence()
                        .filter { it.providerId != providerId }
                        .mapNotNull { it.tvgId }
                        .toSet()
                    progMem = progMem.filter {
                        it.channelId !in providerChannelIds || it.channelId in sharedTvgIds
                    }
                    progFlow.value = progMem
                }
                return 1
            }
            override suspend fun getAllEPGPrograms() = progFlow.value
            override fun pruneOldEPGPrograms(cutoffTime: Long): Int {
                synchronized(fallbackLock) {
                    progMem = progMem.filter { it.endTimeMs >= cutoffTime }
                    progFlow.value = progMem
                }
                return 1
            }
            override suspend fun getUniqueEPGChannelIds(): List<String> {
                return synchronized(fallbackLock) {
                    progMem.map { it.channelId }.distinct()
                }
            }
            override suspend fun getEPGProgramsByChannelDirect(channelId: String): List<com.example.calmsource.core.database.entity.EPGProgramEntity> {
                return synchronized(fallbackLock) {
                    progMem.filter { it.channelId == channelId }.sortedBy { it.startTimeMs }
                }
            }
            override suspend fun getEPGProgramsByChannelsDirect(channelIds: List<String>): List<com.example.calmsource.core.database.entity.EPGProgramEntity> {
                return synchronized(fallbackLock) {
                    progMem.filter { it.channelId in channelIds }.sortedBy { it.startTimeMs }
                }
            }
            override suspend fun searchEPGPrograms(query: String): List<com.example.calmsource.core.database.entity.EPGProgramEntity> {
                return synchronized(fallbackLock) {
                    progMem.filter { it.title.contains(query, ignoreCase = true) }.take(100)
                }
            }
        }
    }

    @Volatile
    private var _cachedDao: com.example.calmsource.core.database.dao.IPTVDao? = null
    private val daoLock = Any()

    private val dao: com.example.calmsource.core.database.dao.IPTVDao
        get() {
            val cached = _cachedDao
            if (cached != null) return cached
            return synchronized(daoLock) {
                val cachedAgain = _cachedDao
                if (cachedAgain != null) return@synchronized cachedAgain
                val roomDao = DatabaseProvider.databaseOrNull()?.iptvDao()
                if (roomDao != null) {
                    _cachedDao = roomDao
                    return@synchronized roomDao
                }
                val resolved = resolveDao()
                _cachedDao = resolved
                return@synchronized resolved
            }
        }

    private fun resolveDao(): com.example.calmsource.core.database.dao.IPTVDao {
        val roomDao = DatabaseProvider.databaseOrNull()?.iptvDao()
        if (roomDao == null && !TestEnvironment.isTest) {
            android.util.Log.e("IPTVRepository", "Room database unavailable, falling back to in-memory DAO — channel data will be lost on process death")
        }
        return roomDao ?: fallbackDao
    }

    private fun invalidateDaoCache() {
        _cachedDao = null
    }

    private val migrationDone = java.util.concurrent.atomic.AtomicBoolean(false)

    private suspend fun migrateFallbackToRoom() {
        IptvMigrationHelper.migrateFallbackToRoom(fallbackDao)
    }

    val providers: StateFlow<List<IPTVProvider>> by lazy {
        daoFlow { it.getAllProviders() }
            .map { list -> list.map { it.toDomain() } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    val epgSources: StateFlow<List<EPGSource>> by lazy {
        daoFlow { it.getAllEPGSources() }
            .map { list -> list.map { it.toDomain() } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    fun observeEPGProgramsByChannel(channelId: String): Flow<List<EPGProgram>> {
        return daoFlow { it.getEPGProgramsByChannel(channelId) }
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> daoFlow(
        block: (com.example.calmsource.core.database.dao.IPTVDao) -> Flow<T>
    ): Flow<T> {
        return DatabaseProvider.databaseReady.flatMapLatest { ready ->
            invalidateDaoCache()
            if (!ready && !isTest) {
                emptyFlow()
            } else {
                block(dao)
            }
        }
    }

    private val _syncStates = MutableStateFlow<Map<String, ProviderSyncState>>(emptyMap())
    val syncStates: StateFlow<Map<String, ProviderSyncState>> = _syncStates.asStateFlow()
    private val _channels = MutableStateFlow<List<IPTVChannel>>(emptyList())
    val channels: StateFlow<List<IPTVChannel>> = _channels.asStateFlow()
    private val _liveGuideIndex = MutableStateFlow(IptvLiveGuideIndex.EMPTY)
    val liveGuideIndex: StateFlow<IptvLiveGuideIndex> = _liveGuideIndex.asStateFlow()
    private val _channelsReady = MutableStateFlow(false)
    val channelsReady: StateFlow<Boolean> = _channelsReady.asStateFlow()
    private val _playbackResolutionError = MutableStateFlow<String?>(null)
    val playbackResolutionError: StateFlow<String?> = _playbackResolutionError.asStateFlow()
    val optimizationPreferences: StateFlow<IptvOptimizationPreferences>
        get() = IptvOptimizationStore.preferences
    private val _optimizationStats = MutableStateFlow(IptvOptimizationStats())
    val optimizationStats: StateFlow<IptvOptimizationStats> = _optimizationStats.asStateFlow()

    private var parsedChannels: List<IPTVChannel> = emptyList()
    private var parsedPrograms: List<EPGProgram> = emptyList()
    private val epgCache = android.util.LruCache<String, List<EPGProgram>>(200)
    private var matches: Map<String, EPGMatch> = emptyMap()
    private var sourceHealthMap: Map<String, SourceHealth> = emptyMap()
    private val onDemandVodCache = java.util.concurrent.ConcurrentHashMap<String, XtreamVodItem>()
    private val onDemandSeriesCache = java.util.concurrent.ConcurrentHashMap<String, XtreamSeriesItem>()

    private var sortedChannelsCache: List<IPTVChannel> = emptyList()

    private var discoveryIngestionJob: kotlinx.coroutines.Job? = null
    private var liveGuideIndexRefreshJob: kotlinx.coroutines.Job? = null
    private var lastLiveGuideIndexBuildMs = 0L

    /**
     * Tick flow that fires whenever channel/EPG data is mutated. The downstream
     * debounce consumer uses this to coalesce the rapid burst of emissions that
     * arrive during a multi-batch Xtream sync into a single final settled
     * state, preventing the main thread from being starved by per-batch
     * recompositions (which previously produced ANRs on button press).
     */
    private val dataUpdateTick = kotlinx.coroutines.flow.MutableStateFlow(0L)

    private val matchEpgRequests = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    private fun triggerMatchEpg() {
        matchEpgRequests.tryEmit(Unit)
    }

    init {
        scope.launch {
            val stream = if (isTest) matchEpgRequests else matchEpgRequests.debounce(100L)
            stream.collect {
                matchEPGSync()
            }
        }

        // Eagerly populate parsedChannels when database becomes ready,
        // bypassing the debounce pipeline to avoid the 750ms delay on
        // first load (and to prevent 'sync required' while channels exist).
        scope.launch {
            try {
                DatabaseProvider.databaseReady.collect { ready ->
                    if (!ready) return@collect
                    invalidateDaoCache()
                    if (ready) {
                        migrateFallbackToRoom()
                        IptvMigrationHelper.migrateLegacyXtreamPseudoUrls(dao)
                    }
                    val entities = withContext(Dispatchers.IO) {
                        dao.getAllChannels().first()
                    }
                    synchronized(dataLock) {
                        parsedChannels = entities.map { it.toDomain() }
                    }
                    updateSortedChannelsCacheSync()
                    triggerMatchEpg()
                    refreshHealthCache()
                    publishChannels(getChannels())
                    _channelsReady.value = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("IPTVRepository", "Failed eager populate of channels", e)
            }
        }
        scope.launch {
            try {
                val rawChannelsFlow = daoFlow { it.getAllChannels() }
                val debouncedChannelsFlow = if (isTest) rawChannelsFlow else rawChannelsFlow.debounce(CHANNEL_EMISSION_DEBOUNCE_MS)
                debouncedChannelsFlow
                    .map { entities ->
                        // Heavy CPU work runs on IO. This is the per-emission work
                        // that updates the internal caches and the EPG match map.
                        synchronized(dataLock) {
                            parsedChannels = entities.map { it.toDomain() }
                        }
                        updateSortedChannelsCacheSync()
                        if (!isProviderSyncInProgress() && !isEpgBackgroundSyncInProgress()) {
                            triggerMatchEpg()
                        }
                        refreshHealthCache()
                        entities
                    }
                    .collect { entities ->
                        _channelsReady.value = true

                        if (entities.isNotEmpty()) {
                            scheduleLightweightLiveGuideIndexRefresh()
                        }

                        // Bump the debounce tick so the consumer below (and the
                        // discovery ingestion job) see that new data is available.
                        dataUpdateTick.update { it + 1 }

                        if (!isTest && entities.isNotEmpty() &&
                            !isProviderSyncInProgress() &&
                            !isEpgBackgroundSyncInProgress()
                        ) {
                            // Cancel any in-flight discovery ingestion so we don't
                            // pile up exponentially slower batches.
                            discoveryIngestionJob?.cancel()
                            discoveryIngestionJob = launch(Dispatchers.IO) {
                                try {
                                    // Debounce: wait a bit for the flow to settle
                                    // (Xtream sync inserts batches rapidly).
                                    kotlinx.coroutines.delay(2000L)
                                    val discoveryChannels = entities.map { entity ->
                                        com.example.calmsource.core.discoveryengine.models.IptvChannel(
                                            id = entity.id,
                                            name = entity.name,
                                            logoUrl = entity.tvgLogo,
                                            streamUrl = com.example.calmsource.core.network.UrlRedactor.redactUrl(entity.streamUrl),
                                            category = entity.groupTitle,
                                            providerId = entity.providerId,
                                            tvgId = entity.tvgId
                                        )
                                    }
                                    discoveryChannels.chunked(500).forEach { chunk ->
                                        currentCoroutineContext().ensureActive()
                                        DiscoveryEngine.ingestIptvChannels(chunk)
                                    }
                                } catch (e: CancellationException) {
                                    // Expected when a newer emission cancels us
                                } catch (e: Exception) {
                                    android.util.Log.w("IPTVRepository", "Failed to ingest IPTV channels into discovery index", e)
                                }
                            }
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("IPTVRepository", "Failed channels Flow collect", e)
            }
        }
        // ANR fix: debounce the public `_channels` emission so Compose isn't
        // asked to recompose the live TV guide for every single batch insert
        // during an Xtream sync. We only need the final, settled list.
        //
        // In tests (isTest=true) the scope is Unconfined and `debounce` would
        // misbehave under runBlocking; tests assert state synchronously and
        // need the emit to happen immediately.
        // We combine the tickStream, optimizationPreferences, and user preferences
        // so that channels are also re-filtered and re-emitted immediately whenever
        // settings are loaded or changed.
        scope.launch {
            try {
                val tickStream: kotlinx.coroutines.flow.Flow<Long> =
                    if (isTest) dataUpdateTick
                    else dataUpdateTick.debounce(CHANNEL_EMISSION_DEBOUNCE_MS)
                combine(
                    tickStream,
                    providers,
                    optimizationPreferences,
                    UserPreferencesRepository.preferences
                ) { _, _, _, _ ->
                    getChannels()
                }.collect { channelsList ->
                    publishChannels(channelsList)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("IPTVRepository", "Failed channels update tick collect", e)
            }
        }
        scope.launch {
            try {
                DatabaseProvider.databaseReady.collect { ready ->
                    if (!ready) return@collect
                    invalidateDaoCache()
                    val database = databaseOrNull()
                    if (database == null) {
                        val entities = withContext(Dispatchers.IO) {
                            dao.getAllEPGPrograms()
                        }
                        synchronized(dataLock) {
                            parsedPrograms = entities.map { it.toDomain() }
                        }
                    }
                    triggerMatchEpg()
                    // Bump the tick so the EPG match triggers a `_channels` re-emit
                    // after the debounce window.
                    dataUpdateTick.update { it + 1 }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("IPTVRepository", "Failed eager populate of EPG programs", e)
            }
        }
        schedulePeriodicHealthRefresh()
    }

    // Window for coalescing per-batch Room emissions during a multi-batch
    // Xtream sync. 750ms is long enough to absorb the entire burst of
    // channel insertions in a single sync, short enough that the UI
    // still feels responsive.
    private const val CHANNEL_EMISSION_DEBOUNCE_MS = 750L
    private const val LIVE_GUIDE_INDEX_THROTTLE_MS = 2_000L
    private const val MAX_M3U_DOWNLOAD_BYTES = 64L * 1024L * 1024L
    private const val MAX_XMLTV_DOWNLOAD_BYTES = 256L * 1024L * 1024L
    private const val MAX_IMPORTED_CHANNELS = 100_000
    private const val MAX_IMPORTED_EPG_PROGRAMS = 25_000

    private fun java.io.InputStream.copyToBounded(
        output: java.io.OutputStream,
        maxBytes: Long
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) return
            total += count
            if (total > maxBytes) {
                throw java.io.IOException("IPTV download exceeds the supported size limit")
            }
            output.write(buffer, 0, count)
        }
    }

    private class BoundedInputStream(
        input: java.io.InputStream,
        private val maxBytes: Long
    ) : java.io.FilterInputStream(input) {
        private var bytesRead = 0L

        private fun record(count: Int): Int {
            if (count > 0) {
                bytesRead += count
                if (bytesRead > maxBytes) {
                    throw java.io.IOException("IPTV download exceeds the supported size limit")
                }
            }
            return count
        }

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) record(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            record(super.read(buffer, offset, length))
    }

    private fun publishChannels(channelsList: List<IPTVChannel>) {
        _channels.value = channelsList
        _liveGuideIndex.value = IptvLiveGuideIndex.buildFromChannels(channelsList)
        lastLiveGuideIndexBuildMs = System.currentTimeMillis()
    }

    /**
     * Live channel count for loading gates. Reads the prebuilt index first, then
     * falls back to the in-memory sorted cache so Home and Live stay in sync
     * while Room batches are still debouncing public emissions.
     */
    fun getLiveChannelCount(): Int {
        val indexed = _liveGuideIndex.value.liveChannels.size
        if (indexed > 0) return indexed
        return synchronized(dataLock) {
            sortedChannelsCache.count { !it.isVod }
        }
    }

    private fun isProviderSyncInProgress(): Boolean {
        return _syncStates.value.values.any { it.status == ProviderSyncStatus.SYNCING }
    }

    private fun isEpgBackgroundSyncInProgress(): Boolean {
        return epgBackgroundSyncProviders.isNotEmpty()
    }

    internal fun beginEpgBackgroundSync(providerId: String) {
        epgBackgroundSyncProviders.add(providerId)
    }

    internal fun endEpgBackgroundSync(providerId: String) {
        epgBackgroundSyncProviders.remove(providerId)
        epgCache.evictAll()
        scope.launch(Dispatchers.IO) {
            triggerMatchEpg()
            dataUpdateTick.update { it + 1 }
        }
    }

    private fun scheduleLightweightLiveGuideIndexRefresh(immediate: Boolean = false) {
        if (!immediate && liveGuideIndexRefreshJob?.isActive == true) return
        if (immediate) {
            liveGuideIndexRefreshJob?.cancel()
        }
        liveGuideIndexRefreshJob = scope.launch {
            if (!immediate) {
                kotlinx.coroutines.delay(LIVE_GUIDE_INDEX_THROTTLE_MS)
            }
            val organizedLive = getChannels().filterNot { it.isVod }
            _liveGuideIndex.value = IptvLiveGuideIndex.buildFromChannels(
                channels = organizedLive,
                lightweight = isProviderSyncInProgress()
            )
            lastLiveGuideIndexBuildMs = System.currentTimeMillis()
        }
    }

    private fun updateSortedChannelsCacheSync() {
        // Read both parsedChannels and sourceHealthMap in a single
        // synchronized block to guarantee a consistent snapshot.
        val snapshot = synchronized(dataLock) {
            Pair(parsedChannels, sourceHealthMap)
        }
        val currentChannels = snapshot.first
        val healthMap = snapshot.second
        val sorted = currentChannels
            .filter { channel ->
                val safeSourceId = channel.safeSourceId
                val health = healthMap[safeSourceId]
                health?.userHidden != true
            }
            .sortedWith { c1, c2 ->
                val safeSourceId1 = c1.safeSourceId
                val safeSourceId2 = c2.safeSourceId
                val isBlocked1 = healthMap[safeSourceId1]?.healthScore == 0
                val isBlocked2 = healthMap[safeSourceId2]?.healthScore == 0
                when {
                    isBlocked1 && !isBlocked2 -> 1
                    !isBlocked1 && isBlocked2 -> -1
                    else -> 0
                }
            }

        synchronized(dataLock) {
            sortedChannelsCache = sorted
        }
    }

    private fun updateSortedChannelsCache() {
        scope.launch(Dispatchers.IO) {
            updateSortedChannelsCacheSync()
        }
    }

    fun getChannels(): List<IPTVChannel> {
        // Read both sortedChannelsCache and sourceHealthMap in a single
        // synchronized block to guarantee a consistent snapshot.
        val snapshot = synchronized(dataLock) {
            Pair(sortedChannelsCache, sourceHealthMap)
        }
        val all = snapshot.first
        val currentProviders = providers.value
        val disabledProviderIds = currentProviders.filterNot { it.isEnabled }.map { it.id }.toSet()
        val allFiltered = all.filterNot { it.providerId in disabledProviderIds }
        
        val healthMap = snapshot.second
        val organized = IptvChannelOrganizer.organize(
            channels = allFiltered,
            preferences = optimizationPreferences.value,
            healthBySourceId = healthMap
        )
        _optimizationStats.value = organized.stats
        val separate = UserPreferencesRepository.preferences.value.separateIptvCategoriesByProvider
        if (!separate) return organized.channels
        return organized.channels.map { channel ->
            val provider = currentProviders.firstOrNull { it.id == channel.providerId }
            if (provider != null) {
                channel.copy(groupTitle = "${provider.name} - ${channel.groupTitle ?: "General"}")
            } else {
                channel
            }
        }
    }

    fun updateOptimizationPreferences(
        transform: (IptvOptimizationPreferences) -> IptvOptimizationPreferences
    ) {
        IptvOptimizationStore.update(transform)
        dataUpdateTick.update { it + 1 }
    }

    fun resetOptimizationPreferences() {
        IptvOptimizationStore.reset()
        dataUpdateTick.update { it + 1 }
    }

    fun getLiveChannels(limit: Int = Int.MAX_VALUE): List<IPTVChannel> {
        val indexed = _liveGuideIndex.value.liveChannels
        if (indexed.isNotEmpty()) {
            return if (limit == Int.MAX_VALUE) indexed else indexed.take(limit.coerceAtLeast(0))
        }
        val liveChannels = getChannels().filterNot { it.isVod }
        return if (limit == Int.MAX_VALUE) {
            liveChannels
        } else {
            liveChannels.take(limit.coerceAtLeast(0))
        }
    }

    suspend fun getLiveChannelHomeRow(limit: Int = 30): com.example.calmsource.core.discoveryengine.models.RecommendationRow? {
        val channels = getLiveChannels(limit)
        val nowNextByChannel = getNowNextForChannels(channels.map { it.id })
        val items = channels.map { channel ->
            val nowNext = nowNextByChannel[channel.id]
            val nowTitle = nowNext?.currentProgram?.title
            val nextTitle = nowNext?.nextProgram?.title
            com.example.calmsource.core.discoveryengine.models.RecommendationItem(
                id = channel.id,
                type = "channel",
                title = channel.name,
                score = 0.0,
                reason = nowTitle?.let { "Live now: $it" } ?: "Live channel",
                scoreBreakdown = com.example.calmsource.core.discoveryengine.models.ScoreBreakdown(
                    reasons = listOf("IPTV")
                ),
                subtitle = channel.groupTitle,
                posterUrl = channel.tvgLogo,
                liveNowTitle = nowTitle,
                liveNextTitle = nextTitle,
                liveProgress = nowNext?.progressPercentage,
                source = channel.providerId
            )
        }
        return items.takeIf { it.isNotEmpty() }?.let {
            com.example.calmsource.core.discoveryengine.models.RecommendationRow(
                title = "Live TV",
                rowType = "live_tv",
                items = it.toImmutableList()
            )
        }
    }

    private fun isChannelUserHidden(channel: IPTVChannel): Boolean {
        val health = synchronized(dataLock) { sourceHealthMap[channel.safeSourceId] }
        return health?.userHidden == true
    }

    fun findChannel(channelId: String): IPTVChannel? {
        if (channelId.isBlank()) return null
        val channel = synchronized(dataLock) {
            parsedChannels.firstOrNull { it.id == channelId }
        } ?: return null
        val provider = providers.value.firstOrNull { it.id == channel.providerId }
        if (provider == null || !provider.isEnabled) return null
        if (isChannelUserHidden(channel)) return null
        return channel
    }

    /**
     * Returns the channel only if its provider is enabled and the channel is not user-hidden.
     * Resolves Xtream VOD/series entries from the database when absent from the in-memory cache.
     */
    suspend fun findChannelForPlayback(channelId: String): IPTVChannel? {
        findPlaybackChannel(channelId)?.let { channel ->
            if (isChannelUserHidden(channel)) return null
            val provider = providers.value.firstOrNull { it.id == channel.providerId }
            if (provider == null || !provider.isEnabled) return null
            return channel
        }
        return findChannel(channelId)
    }


    suspend fun setLiveChannelHidden(channelId: String, hidden: Boolean = true) = withContext(Dispatchers.IO) {
        val channel = findChannel(channelId) ?: return@withContext
        if (channel.isVod) return@withContext

        SourceHealthRepository.setSourceHidden(
            sourceId = channel.safeSourceId,
            providerId = channel.providerId,
            sourceType = PlaybackSourceType.IPTV,
            hidden = hidden
        )
        refreshHealthCache()
    }

    suspend fun setLiveChannelGroupHidden(
        groupTitle: String,
        hidden: Boolean = true,
        providerId: String? = null
    ) = withContext(Dispatchers.IO) {
        val normalizedGroupTitle = groupTitle.trim()
        if (normalizedGroupTitle.isEmpty() || normalizedGroupTitle.equals("All", ignoreCase = true)) {
            return@withContext
        }

        val matchingChannels = getChannels().filter { channel ->
            !channel.isVod &&
                (providerId == null || channel.providerId == providerId) &&
                (channel.groupTitle ?: "General").equals(normalizedGroupTitle, ignoreCase = true)
        }
        matchingChannels.forEach { channel ->
            SourceHealthRepository.setSourceHidden(
                sourceId = channel.safeSourceId,
                providerId = channel.providerId,
                sourceType = PlaybackSourceType.IPTV,
                hidden = hidden
            )
        }
        refreshHealthCache()
    }

    suspend fun restoreHiddenIptvChannels() = withContext(Dispatchers.IO) {
        val liveSourceIds = synchronized(dataLock) {
            parsedChannels
                .asSequence()
                .filterNot { it.isVod }
                .map { it.safeSourceId }
                .toSet()
        }
        SourceHealthRepository.getAllSourceHealth()
            .filter {
                it.sourceType == PlaybackSourceType.IPTV &&
                    it.userHidden &&
                    it.sourceId in liveSourceIds
            }
            .forEach { health ->
                SourceHealthRepository.setSourceHidden(
                    sourceId = health.sourceId,
                    providerId = health.providerId,
                    sourceType = health.sourceType,
                    hidden = false
                )
            }
        refreshHealthCache()
    }

    suspend fun searchXtreamVod(query: String, limit: Int = 100): List<XtreamVodItem> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return@withContext emptyList()

        val database = databaseOrNull() ?: return@withContext emptyList()
        val coercedLimit = limit.coerceIn(1, 500)

        // 1. Attempt FTS Search
        val ftsResults = runCatching {
            val ftsQueryString = buildFtsQuery(trimmedQuery)
            if (ftsQueryString.isNotEmpty()) {
                val sqliteQuery = XtreamDao.ftsVodQuery(ftsQueryString, coercedLimit)
                database.xtreamDao().searchVodFts(sqliteQuery).map { it.toDomain() }
            } else {
                emptyList()
            }
        }.getOrNull()

        // 2. If FTS succeeds and is not empty, return results
        if (!ftsResults.isNullOrEmpty()) {
            indexXtreamVodResults(ftsResults)
            return@withContext ftsResults
        }

        // 3. Fallback to LIKE Search
        val escapedQuery = trimmedQuery.escapeSqlLike()
        val normalizedQuery = trimmedQuery.lowercase(Locale.ROOT).replace(NORMALIZE_REGEX, "")
        val results = runCatching {
            database.xtreamDao()
                .searchVod(escapedQuery, normalizedQuery, coercedLimit)
                .map { it.toDomain() }
        }.getOrDefault(emptyList())
        val finalResults = if (results.isEmpty() && XtreamRepository.usesOnDemandCatalogSearch()) {
            XtreamRepository.searchVodOnDemand(trimmedQuery, coercedLimit).also { remote ->
                if (onDemandVodCache.size > 1_000) onDemandVodCache.clear()
                remote.forEach { onDemandVodCache[it.id] = it }
            }
        } else {
            results
        }
        indexXtreamVodResults(finalResults)
        finalResults
    }

    suspend fun searchXtreamSeries(query: String, limit: Int = 100): List<XtreamSeriesItem> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return@withContext emptyList()

        val database = databaseOrNull() ?: return@withContext emptyList()
        val coercedLimit = limit.coerceIn(1, 500)

        // 1. Attempt FTS Search
        val ftsResults = runCatching {
            val ftsQueryString = buildFtsQuery(trimmedQuery)
            if (ftsQueryString.isNotEmpty()) {
                val sqliteQuery = XtreamDao.ftsSeriesQuery(ftsQueryString, coercedLimit)
                database.xtreamDao().searchSeriesFts(sqliteQuery).map { it.toDomain() }
            } else {
                emptyList()
            }
        }.getOrNull()

        // 2. If FTS succeeds and is not empty, return results
        if (!ftsResults.isNullOrEmpty()) {
            indexXtreamSeriesResults(ftsResults)
            return@withContext ftsResults
        }

        // 3. Fallback to LIKE Search
        val escapedQuery = trimmedQuery.escapeSqlLike()
        val normalizedQuery = trimmedQuery.lowercase(Locale.ROOT).replace(NORMALIZE_REGEX, "")
        val results = runCatching {
            database.xtreamDao()
                .searchSeries(escapedQuery, normalizedQuery, coercedLimit)
                .map { it.toDomain() }
        }.getOrDefault(emptyList())
        val finalResults = if (results.isEmpty() && XtreamRepository.usesOnDemandCatalogSearch()) {
            XtreamRepository.searchSeriesOnDemand(trimmedQuery, coercedLimit).also { remote ->
                if (onDemandSeriesCache.size > 1_000) onDemandSeriesCache.clear()
                remote.forEach { onDemandSeriesCache[it.id] = it }
            }
        } else {
            results
        }
        indexXtreamSeriesResults(finalResults)
        finalResults
    }

    private suspend fun indexXtreamVodResults(items: List<XtreamVodItem>) {
        indexXtreamDiscoveryItems(
            items.map { item -> item.toDiscoveryMediaItem() }
        )
    }

    private suspend fun indexXtreamSeriesResults(items: List<XtreamSeriesItem>) {
        indexXtreamDiscoveryItems(
            items.map { item -> item.toDiscoveryMediaItem() }
        )
    }

    private suspend fun indexXtreamDiscoveryItems(items: List<DiscoveryMediaItem>) {
        if (isTest || items.isEmpty()) return
        try {
            items
                .distinctBy { "${it.type}:${it.id}" }
                .chunked(250)
                .forEach { chunk ->
                    currentCoroutineContext().ensureActive()
                    DiscoveryEngine.ingestStremioItems(chunk)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("IPTVRepository", "Failed to index Xtream metadata into discovery: ${e.message}")
        }
    }

    private fun XtreamVodItem.toDiscoveryMediaItem(): DiscoveryMediaItem {
        return DiscoveryMediaItem(
            id = id,
            type = "movie",
            title = name,
            posterUrl = poster,
            rating = rating,
            externalIds = buildMap {
                put("xtream_provider", providerId)
                put("xtream_stream_id", streamId)
            },
            source = providerId
        )
    }

    private fun XtreamSeriesItem.toDiscoveryMediaItem(): DiscoveryMediaItem {
        return DiscoveryMediaItem(
            id = id,
            type = "series",
            title = name,
            posterUrl = poster,
            rating = rating,
            externalIds = buildMap {
                put("xtream_provider", providerId)
                put("xtream_series_id", seriesId)
            },
            source = providerId
        )
    }

    private fun buildFtsQuery(query: String): String {
        val clean = query.replace(Regex("[*\"\\-.:()_]"), " ").trim()
        val tokens = clean.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""
        return tokens.joinToString(" ") { "$it*" }
    }

    suspend fun findIptvStreamSource(itemId: String): StreamSource? = withContext(Dispatchers.IO) {
        if (itemId.startsWith(com.example.calmsource.core.model.PlaybackSource.XTREAM_SERIES_EPISODE_SOURCE_PREFIX)) {
            val parts = itemId.removePrefix(com.example.calmsource.core.model.PlaybackSource.XTREAM_SERIES_EPISODE_SOURCE_PREFIX).split("|")
            if (parts.size >= 3) {
                val providerId = parts[0]
                val seriesId = parts[1]
                val episodeId = parts[2]
                return@withContext StreamSource(
                    id = itemId,
                    name = "Episode Stream",
                    url = "xtream://stream_id/$providerId/$episodeId",
                    extensionId = providerId,
                    resolution = "VOD",
                    language = "Unknown"
                )
            }
        }

        val channel = findChannel(itemId)
        if (channel != null) {
            val resolution = if (channel.isVod) "VOD" else ChannelMapper.extractResolution(channel).ifEmpty { "Live" }
            return@withContext StreamSource(
                id = channel.id,
                name = channel.name,
                url = channel.streamUrl,
                extensionId = channel.providerId,
                resolution = resolution,
                language = ChannelMapper.extractLanguage(channel).ifEmpty { "Unknown" }
            )
        }

        onDemandVodCache[itemId]?.let { vod ->
            return@withContext StreamSource(
                id = vod.id,
                name = vod.name,
                url = XtreamStreamUrlBuilder.createPseudoUrl(vod.providerId, vod.streamId) ?: vod.id,
                extensionId = vod.providerId,
                resolution = "VOD",
                language = "Unknown"
            )
        }
        onDemandSeriesCache[itemId]?.let { series ->
            return@withContext buildXtreamSeriesEpisodeSources(series, limit = 1).firstOrNull()
        }

        val database = databaseOrNull()
        val vod = runCatching {
            database?.xtreamDao()?.getVodById(itemId)
        }.onFailure { e ->
            android.util.Log.e("IPTVRepository", "findIptvStreamSource: VOD lookup failed for $itemId", e)
        }.getOrNull()
        if (vod != null) {
            return@withContext StreamSource(
                id = vod.id,
                name = vod.name,
                url = XtreamStreamUrlBuilder.createPseudoUrl(vod.providerId, vod.streamId) ?: vod.id,
                extensionId = vod.providerId,
                resolution = "VOD",
                language = "Unknown"
            )
        }

        val series = runCatching {
            database?.xtreamDao()?.getSeriesById(itemId)
        }.onFailure { e ->
            android.util.Log.e("IPTVRepository", "findIptvStreamSource: series lookup failed for $itemId", e)
        }.getOrNull() ?: return@withContext null

        buildXtreamSeriesEpisodeSources(series.toDomain(), limit = 1).firstOrNull()
    }

    suspend fun findIptvStreamSources(itemId: String, title: String): List<StreamSource> = withContext(Dispatchers.IO) {
        val sources = mutableListOf<StreamSource>()
        findIptvStreamSource(itemId)?.let(sources::add)

        if (title.isNotBlank()) {
            val titleNorm = title.lowercase(Locale.ROOT).replace(NORMALIZE_REGEX, "")
            getChannels()
                .filter { channel ->
                    channel.isVod &&
                        channel.name.lowercase(Locale.ROOT).replace(NORMALIZE_REGEX, "") == titleNorm
                }
                .forEach { channel ->
                    sources.add(
                        StreamSource(
                            id = channel.id,
                            name = channel.name,
                            url = channel.streamUrl,
                            extensionId = channel.providerId,
                            resolution = "VOD",
                            language = "Unknown"
                        )
                    )
                }
            searchXtreamVod(title, limit = 20)
                .filter { it.name.lowercase(Locale.ROOT).replace(NORMALIZE_REGEX, "") == titleNorm }
                .forEach { vod ->
                    sources.add(
                        StreamSource(
                            id = vod.id,
                            name = vod.name,
                            url = XtreamStreamUrlBuilder.createPseudoUrl(vod.providerId, vod.streamId) ?: vod.id,
                            extensionId = vod.providerId,
                            resolution = "VOD",
                            language = "Unknown"
                        )
                    )
                }
            searchXtreamSeries(title, limit = 20)
                .filter { it.name.lowercase(Locale.ROOT).replace(NORMALIZE_REGEX, "") == titleNorm }
                .forEach { series ->
                    sources.addAll(buildXtreamSeriesEpisodeSources(series, limit = 100))
                }
        }

        sources.distinctBy { it.id }
    }

    suspend fun findPlaybackChannel(itemId: String): IPTVChannel? = withContext(Dispatchers.IO) {
        parseXtreamSeriesEpisodeSourceId(itemId)?.let { ref ->
            val series = runCatching {
                databaseOrNull()?.xtreamDao()?.getSeriesByProviderAndSeriesId(ref.providerId, ref.seriesId)
            }.onFailure { e ->
                android.util.Log.e("IPTVRepository", "findPlaybackChannel: series lookup failed for $itemId", e)
            }.getOrNull() ?: return@withContext null
            val seriesName = series.name.ifBlank { "Series" }
            return@withContext IPTVChannel(
                id = itemId,
                tvgId = null,
                tvgName = seriesName,
                tvgLogo = series.poster.ifEmpty { null },
                groupTitle = series.categoryName.ifEmpty { "Series" },
                name = "$seriesName - Episode ${ref.episodeId}",
                streamUrl = XtreamStreamUrlBuilder.createPseudoUrl(ref.providerId, ref.episodeId) ?: ref.episodeId,
                providerId = ref.providerId,
                rawAttributes = mapOf(
                    "xtream_stream_id" to ref.episodeId,
                    "xtream_source" to "true",
                    "xtream_content_type" to "series",
                    "container_extension" to ref.containerExtension
                )
            )
        }

        findChannel(itemId) ?: onDemandVodCache[itemId]?.let { vod ->
            IPTVChannel(
                id = vod.id,
                tvgId = null,
                tvgName = vod.name,
                tvgLogo = vod.poster?.ifEmpty { null },
                groupTitle = "VOD",
                name = vod.name,
                streamUrl = XtreamStreamUrlBuilder.createPseudoUrl(vod.providerId, vod.streamId) ?: vod.streamId,
                providerId = vod.providerId,
                rawAttributes = mapOf(
                    "xtream_stream_id" to vod.streamId,
                    "xtream_source" to "true",
                    "xtream_content_type" to "vod",
                    "container_extension" to vod.containerExtension
                )
            )
        } ?: runCatching {
            databaseOrNull()?.xtreamDao()?.getVodById(itemId)
        }.onFailure { e ->
            android.util.Log.e("IPTVRepository", "findPlaybackChannel: VOD lookup failed for $itemId", e)
        }.getOrNull()?.let { vod ->
            IPTVChannel(
                id = vod.id,
                tvgId = null,
                tvgName = vod.name,
                tvgLogo = vod.poster.ifEmpty { null },
                groupTitle = vod.categoryName.ifEmpty { "VOD" },
                name = vod.name,
                streamUrl = XtreamStreamUrlBuilder.createPseudoUrl(vod.providerId, vod.streamId) ?: vod.streamId,
                providerId = vod.providerId,
                rawAttributes = mapOf(
                    "xtream_stream_id" to vod.streamId,
                    "xtream_source" to "true",
                    "xtream_content_type" to "vod",
                    "container_extension" to vod.containerExtension
                )
            )
        }
    }

    fun buildLivePlaybackRequest(
        channel: IPTVChannel,
        streamUrl: String = channel.streamUrl,
        programTitle: String? = null,
        programDescription: String? = null,
        programDurationMs: Long? = null,
        liveOutputFormat: String = "ts"
    ): PlaybackRequest {
        return PlaybackRequest(
            source = PlaybackSource(
                id = channel.id,
                type = PlaybackSourceType.IPTV,
                title = channel.name,
                rawUrl = streamUrl,
                metadata = PlaybackItemMetadata(
                    title = programTitle ?: channel.name,
                    description = programDescription,
                    durationMs = programDurationMs,
                    isLive = !channel.isVod,
                    containerFormat = liveOutputFormat.takeIf { !channel.isVod }
                ),
                allowInsecureHttp = streamUrl.startsWith("xtream://", ignoreCase = true) ||
                    streamUrl.startsWith("http://", ignoreCase = true),
                // Anchor health to the channel's pseudo URL so it stays consistent after the URL is
                // resolved to a real http(s) URL at play time (bug #5).
                stableSourceId = channel.safeSourceId
            ),
            userMemoryReference = channel.toUserMemoryReference()
        )
    }

    /**
     * Alternate Xtream live container (e.g. m3u8 when primary is ts) for [prepareBest] fallback.
     */
    fun buildLivePlaybackFallbackSources(channel: IPTVChannel): List<PlaybackSource> {
        if (channel.isVod || !channel.streamUrl.startsWith("xtream://")) return emptyList()
        val alternateFormat = "m3u8"
        return listOf(
            PlaybackSource(
                id = "${channel.id}-alt-$alternateFormat",
                type = PlaybackSourceType.IPTV,
                title = channel.name,
                rawUrl = channel.streamUrl,
                metadata = PlaybackItemMetadata(
                    title = channel.name,
                    isLive = true,
                    containerFormat = alternateFormat
                ),
                allowInsecureHttp = true,
                // Share the base channel's health key so a successful m3u8 fallback credits the same
                // channel rather than an orphaned "-alt-" id (bug #5).
                stableSourceId = channel.safeSourceId
            )
        )
    }

    fun isProviderSyncActive(): Boolean = isProviderSyncInProgress()

    /** Retries providers currently carrying an error/warning while preserving per-provider locks. */
    fun retryFailedProviderSyncs() {
        val states = _syncStates.value
        providers.value
            .filter { provider ->
                if (!provider.isEnabled) return@filter false
                val state = states[provider.id]
                state?.status == ProviderSyncStatus.ERROR ||
                    !state?.error.isNullOrBlank() ||
                    !state?.warning.isNullOrBlank()
            }
            .forEach { provider ->
                if (provider.type == IPTVProviderType.XTREAM) {
                    startXtreamProviderSync(provider.id)
                } else {
                    syncScope.launch { syncPlaylistFromUrl(provider.id) }
                }
            }
    }

    private suspend fun refreshHealthCache() = withContext(Dispatchers.IO) {
        val healthList = SourceHealthRepository.getAllSourceHealth()
        synchronized(dataLock) {
            sourceHealthMap = healthList.associateBy { it.sourceId }
        }
        updateSortedChannelsCacheSync()
        dataUpdateTick.update { it + 1 }
        publishChannels(getChannels())
    }

    /**
     * Synchronous version of [refreshHealthCache] for test use.
     * Ensures the internal health cache reflects the current SourceHealthRepository state.
     */
    @VisibleForTesting
    suspend fun refreshHealthCacheForTest() {
        val healthList = SourceHealthRepository.getAllSourceHealth()
        synchronized(dataLock) {
            sourceHealthMap = healthList.associateBy { it.sourceId }
        }
        withContext(Dispatchers.IO) {
            updateSortedChannelsCacheSync()
        }
        publishChannels(getChannels())
    }

    /**
     * Periodically refreshes provider health to recover FAILED/SLOW providers
     * that have healed via time-based score recovery in SourceHealthModels.
     */
    private fun schedulePeriodicHealthRefresh() {
        scope.launch(Dispatchers.IO) {
            while (true) {
                kotlinx.coroutines.delay(30 * 60 * 1000L)  // 30 minutes
                try {
                    // Re-evaluate provider health based on time-based recovery
                    val providers = dao.getAllProviders().first().map { it.toDomain() }
                    for (provider in providers) {
                        if (!provider.isEnabled) continue
                        if (provider.health == ProviderHealth.FAILED || provider.health == ProviderHealth.SLOW) {
                            val scoreObj = SourceHealthRepository.getProviderHealth(provider.id)
                            if (scoreObj != null && scoreObj.healthScore > 60) {
                                val entity = dao.getProviderByIdDirect(provider.id)
                                if (entity != null) {
                                    entity.health = ProviderHealth.HEALTHY.name
                                    dao.updateProvider(entity)
                                }
                            }
                        }
                    }
                    refreshHealthCache()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    android.util.Log.w(
                        "IPTVRepository",
                        "Periodic provider health recovery check failed; will retry next cycle",
                        e,
                    )
                }
            }
        }
    }

    fun recordPlaybackSuccess(channelId: String) {
        // Alternate-container fallback sources use ids like "<channelId>-alt-m3u8"; resolve them
        // back to the base channel so health is recorded against a stable key (bug #5).
        val channel = findChannel(channelId.substringBefore("-alt-")) ?: return
        val safeSourceId = generateSafeSourceId(channel.streamUrl)
        val providerId = channel.providerId
        scope.launch {
            SourceHealthRepository.recordSuccess(
                sourceId = safeSourceId,
                providerId = providerId,
                sourceType = PlaybackSourceType.IPTV
            )
            updateProviderHealthInDb(providerId)
            val health = SourceHealthRepository.getSourceHealth(safeSourceId)
            if (health != null) {
                synchronized(dataLock) {
                    sourceHealthMap = sourceHealthMap + (safeSourceId to health)
                }
                updateSortedChannelsCacheSync()
            }
        }
    }

    fun recordPlaybackFailure(channelId: String, errorCategory: String = "") {
        val channel = findChannel(channelId.substringBefore("-alt-")) ?: return
        val safeSourceId = generateSafeSourceId(channel.streamUrl)
        val providerId = channel.providerId
        scope.launch {
            SourceHealthRepository.recordFailure(
                sourceId = safeSourceId,
                providerId = providerId,
                sourceType = PlaybackSourceType.IPTV,
                errorCategory = errorCategory
            )
            updateProviderHealthInDb(providerId)
            val health = SourceHealthRepository.getSourceHealth(safeSourceId)
            if (health != null) {
                synchronized(dataLock) {
                    sourceHealthMap = sourceHealthMap + (safeSourceId to health)
                }
                updateSortedChannelsCacheSync()
            }
        }
    }

    private suspend fun updateProviderHealthInDb(providerId: String) {
        val scoreObj = SourceHealthRepository.getProviderHealth(providerId) ?: return
        // Never set FAILED from playback health score alone — use DEGRADED instead.
        // Channel-level health already handles individual bad channels.
        val newHealth = when {
            scoreObj.healthScore <= 20 -> ProviderHealth.SLOW
            scoreObj.healthScore <= 60 -> ProviderHealth.SLOW
            else -> ProviderHealth.HEALTHY
        }
        val providerEntity = dao.getProviderById(providerId).firstOrNull() ?: return
        if (providerEntity.health != newHealth.name) {
            providerEntity.health = newHealth.name
            dao.updateProvider(providerEntity)
        }
    }

    private suspend fun getUniqueEPGChannelIds(): List<String> {
        val database = databaseOrNull()
        return if (database != null) {
            withContext(Dispatchers.IO) {
                dao.getUniqueEPGChannelIds()
            }
        } else {
            synchronized(dataLock) { parsedPrograms.map { it.channelId }.distinct() }
        }
    }

    private suspend fun getEPGProgramsForChannelCached(epgId: String): List<EPGProgram> = withContext(Dispatchers.IO) {
        val cached = epgCache.get(epgId)
        if (cached != null) {
            return@withContext cached
        }
        val database = databaseOrNull()
        if (database != null) {
            val entities = dao.getEPGProgramsByChannelDirect(epgId)
            val domainPrograms = entities.map { it.toDomain() }
            epgCache.put(epgId, domainPrograms)
            domainPrograms
        } else {
            val fallback = synchronized(dataLock) { parsedPrograms }.filter { it.channelId == epgId }
            epgCache.put(epgId, fallback)
            fallback
        }
    }

    suspend fun getPrograms(): List<EPGProgram> {
        val database = databaseOrNull()
        return if (database != null) {
            withContext(Dispatchers.IO) {
                dao.getAllEPGPrograms().map { it.toDomain() }
            }
        } else {
            synchronized(dataLock) { parsedPrograms }
        }
    }

    suspend fun searchPrograms(query: String): List<EPGProgram> {
        val database = databaseOrNull()
        return if (database != null) {
            withContext(Dispatchers.IO) {
                dao.searchEPGPrograms(query.escapeSqlLike()).map { it.toDomain() }
            }
        } else {
            val queryNorm = query.normalizeForSearch()
            synchronized(dataLock) { parsedPrograms }
                .filter { it.title.normalizeForSearch().contains(queryNorm) }
                .take(100)
        }
    }


    fun getGroups(): List<IPTVChannelGroup> {
        val channelsList = getChannels()
        return channelsList
            .groupBy { channel -> channel.providerId to (channel.groupTitle ?: "General") }
            .map { (key, channels) ->
                IPTVChannelGroup(
                    title = key.second,
                    providerId = key.first,
                    channels = channels
                )
            }
    }

    data class ResolveResult(val url: String, val error: String? = null)

    /**
     * Clears any stale playback resolution error. Player screens call this at the start of every
     * prepare cycle so an error from a previously-failed channel never bleeds into a new request.
     */
    fun clearPlaybackResolutionError() {
        _playbackResolutionError.value = null
    }

    /**
     * Surfaces a structured resolution error to the player UI. Used by [IptvXtreamPlaybackResolver]
     * so callers of [resolvePlaybackUrlOrError] (the shared resolver path) get the same actionable
     * messages that [resolvePlaybackUrl] produces, instead of a generic fallback string.
     */
    internal fun reportPlaybackResolutionError(error: String?) {
        _playbackResolutionError.value = error
        if (error != null) {
            com.example.calmsource.core.observability.CrashReporter.log("xtream_resolve_failed reason=$error")
        }
    }

    suspend fun resolvePlaybackUrl(channel: IPTVChannel): String? {
        val result = resolvePlaybackUrlOrError(channel)
        if (result.error != null) {
            _playbackResolutionError.value = result.error
            com.example.calmsource.core.observability.CrashReporter.log("xtream_resolve_failed reason=${result.error}")
            return null
        } else {
            _playbackResolutionError.value = null
        }
        return result.url
    }

    suspend fun resolvePlaybackUrlOrError(
        channel: IPTVChannel,
        liveOutputFormat: String? = null
    ): ResolveResult = withContext(Dispatchers.IO) {
        if (!channel.streamUrl.startsWith("xtream://")) {
            val m3uUrl = M3uStreamUrlStorage.resolvePlaybackUrl(channel)
            if (m3uUrl != null) {
                return@withContext ResolveResult(m3uUrl)
            }
            return@withContext ResolveResult(
                channel.streamUrl,
                "Stream credentials lost. Please re-enter your IPTV provider credentials."
            )
        }

        val provider = findProviderForPlayback(channel.providerId)
        if (provider == null) {
            android.util.Log.w("IPTVRepository", "resolvePlaybackUrl: Provider ${channel.providerId} not found for channel ${channel.name}")
            return@withContext ResolveResult(channel.streamUrl, "IPTV provider not found")
        }
        val username = provider.username
        if (username == null) {
            android.util.Log.w("IPTVRepository", "resolvePlaybackUrl: Username missing for provider ${provider.id} (type=${provider.type})")
            return@withContext ResolveResult(channel.streamUrl, "Provider username missing — re-add the provider with credentials")
        }
        val password = XtreamRepository.getPassword(provider.id, username)
        if (password == null) {
            val credentialMessage = when {
                !XtreamRepository.isEncryptedStorageAvailable() ->
                    "Secure credential storage is unavailable on this device. Re-enter your IPTV provider in settings."
                XtreamRepository.tokenStore.hasPassword(provider.id, username) ->
                    "IPTV credentials could not be read — secure storage may be corrupted after a system update. Re-enter your username and password in settings."
                else ->
                    "IPTV credentials missing — re-enter your username and password in settings"
            }
            android.util.Log.w("IPTVRepository", "resolvePlaybackUrl: $credentialMessage (provider=${provider.id})")
            return@withContext ResolveResult(channel.streamUrl, credentialMessage)
        }
        
        val streamId = com.example.calmsource.feature.iptv.xtream.XtreamStreamUrlBuilder.extractStreamId(channel.streamUrl)
        if (streamId == null) {
            android.util.Log.w("IPTVRepository", "resolvePlaybackUrl: Invalid stream ID in pseudo-URL for channel ${channel.id}")
            return@withContext ResolveResult(channel.streamUrl, "Invalid stream identifier")
        }
        val serverUrl = resolveXtreamPortalUrl(provider)
        if (serverUrl == null) {
            android.util.Log.w("IPTVRepository", "resolvePlaybackUrl: Cannot determine server URL for provider ${provider.id}")
            return@withContext ResolveResult(channel.streamUrl, "Provider server URL is invalid")
        }
        val containerExtension = channel.rawAttributes["container_extension"]
            ?.takeIf { it.isNotBlank() }
            ?: "mp4"

        val contentType = channel.rawAttributes["xtream_content_type"]
        val resolvedUrl = when {
            contentType == "series" -> {
                com.example.calmsource.feature.iptv.xtream.XtreamStreamUrlBuilder.buildSeriesUrl(serverUrl, username, password, streamId, containerExtension)
            }
            contentType == "vod" -> {
                com.example.calmsource.feature.iptv.xtream.XtreamStreamUrlBuilder.buildVodUrl(serverUrl, username, password, streamId, containerExtension)
            }
            else -> {
                val format = normalizeXtreamLiveOutputFormat(liveOutputFormat) ?: "ts"
                com.example.calmsource.feature.iptv.xtream.XtreamStreamUrlBuilder.buildLiveUrl(
                    serverUrl,
                    username,
                    password,
                    streamId,
                    format
                )
            }
        }
        if (resolvedUrl == null) {
            android.util.Log.w("IPTVRepository", "resolvePlaybackUrl: Failed to build playback URL for channel ${channel.id}")
            return@withContext ResolveResult(channel.streamUrl, "Could not construct playback URL")
        }
        ResolveResult(resolvedUrl)
    }

    private fun normalizeXtreamLiveOutputFormat(format: String?): String? {
        return when (format?.trim()?.lowercase()?.removePrefix(".")) {
            "m3u8", "hls" -> "m3u8"
            "ts", "mpegts" -> "ts"
            else -> null
        }
    }

    private fun resolveXtreamPortalUrl(provider: IPTVProvider): String? {
        return XtreamRepository.resolveProviderPortalUrl(provider.id, provider.serverUrl)
            .takeIf { it.isNotBlank() }
            ?: com.example.calmsource.feature.iptv.xtream.XtreamPlaybackHelper.extractBaseUrl(provider.playlistUrl)
    }

    private suspend fun findProviderForPlayback(providerId: String): IPTVProvider? {
        providers.value.firstOrNull { it.id == providerId && it.isEnabled }?.let { return it }
        return runCatching {
            dao.getProviderById(providerId).firstOrNull()?.toDomain()?.takeIf { it.isEnabled }
        }.getOrNull()
    }

    suspend fun setProviderEnabled(providerId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val entity = dao.getProviderByIdDirect(providerId) ?: return@withContext
        if (entity.isEnabled == enabled) return@withContext
        entity.isEnabled = enabled
        dao.updateProvider(entity)
        updateSortedChannelsCacheSync()
        publishChannels(getChannels())
        refreshHealthCache()
    }

    /** Maps an XMLTV/EPG channel id to the IPTV channel id that owns the match, if any. */
    fun findChannelIdForEpgChannel(epgChannelId: String): String? {
        if (epgChannelId.isBlank()) return null
        return synchronized(dataLock) {
            matches.entries.firstOrNull { it.value.epgId == epgChannelId }?.key
        }
    }

    private data class XtreamSeriesEpisodeSourceRef(
        val providerId: String,
        val seriesId: String,
        val episodeId: String,
        val containerExtension: String
    )

    private suspend fun buildXtreamSeriesEpisodeSources(
        series: XtreamSeriesItem,
        limit: Int
    ): List<StreamSource> {
        if (series.providerId.isBlank() || series.seriesId.isBlank()) return emptyList()
        val provider = findProviderForPlayback(series.providerId) ?: return emptyList()
        val username = provider.username ?: return emptyList()
        val password = XtreamRepository.getPassword(provider.id, username) ?: return emptyList()
        val serverUrl = resolveXtreamPortalUrl(provider)
            ?: return emptyList()
        val config = XtreamProviderConfig(
            id = provider.id,
            name = provider.name,
            serverUrl = serverUrl,
            username = username
        )
        val episodes = runCatching {
            xtreamApiClient.getSeriesEpisodes(config, password, series.seriesId)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            emptyList()
        }

        return episodes
            .sortedWith(
                compareBy<XtreamSeriesEpisode> { it.seasonNumber ?: Int.MAX_VALUE }
                    .thenBy { it.episodeNumber ?: Int.MAX_VALUE }
                    .thenBy { it.episodeId }
            )
            .take(limit.coerceIn(1, 100))
            .map { episode ->
                val label = formatXtreamEpisodeLabel(episode)
                StreamSource(
                    id = createXtreamSeriesEpisodeSourceId(
                        providerId = series.providerId,
                        seriesId = series.seriesId,
                        episodeId = episode.episodeId,
                        containerExtension = episode.containerExtension
                    ),
                    name = "${series.name} - $label${episode.title.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}",
                    url = XtreamStreamUrlBuilder.createPseudoUrl(series.providerId, episode.episodeId) ?: episode.episodeId,
                    extensionId = series.providerId,
                    resolution = label,
                    language = "Unknown"
                )
            }
    }

    private fun formatXtreamEpisodeLabel(episode: XtreamSeriesEpisode): String {
        val season = episode.seasonNumber
        val episodeNumber = episode.episodeNumber
        return when {
            season == 0 && episodeNumber != null -> "Special E%02d".format(Locale.ROOT, episodeNumber)
            season == 0 -> "Specials"
            season != null && episodeNumber != null -> "S%02dE%02d".format(Locale.ROOT, season, episodeNumber)
            episodeNumber != null -> "E%02d".format(Locale.ROOT, episodeNumber)
            season != null -> "Season $season"
            else -> "Episode"
        }
    }

    private fun createXtreamSeriesEpisodeSourceId(
        providerId: String,
        seriesId: String,
        episodeId: String,
        containerExtension: String
    ): String {
        return "${com.example.calmsource.core.model.PlaybackSource.XTREAM_SERIES_EPISODE_SOURCE_PREFIX}$providerId|$seriesId|$episodeId|$containerExtension"
    }

    private fun parseXtreamSeriesEpisodeSourceId(id: String): XtreamSeriesEpisodeSourceRef? {
        if (!id.startsWith(com.example.calmsource.core.model.PlaybackSource.XTREAM_SERIES_EPISODE_SOURCE_PREFIX)) return null
        val parts = id.removePrefix(com.example.calmsource.core.model.PlaybackSource.XTREAM_SERIES_EPISODE_SOURCE_PREFIX).split("|")
        if (parts.size < 4) return null
        val providerId = parts[0].takeIf { it.isNotBlank() } ?: return null
        val seriesId = parts[1].takeIf { it.isNotBlank() } ?: return null
        val episodeId = parts[2].takeIf { it.isNotBlank() } ?: return null
        val containerExtension = parts[3].takeIf { it.isNotBlank() } ?: "mp4"
        return XtreamSeriesEpisodeSourceRef(providerId, seriesId, episodeId, containerExtension)
    }

    private fun createM3uProvider(name: String, playlistUrl: String): IPTVProvider {
        val id = "iptv-${UUID.randomUUID()}"
        return IPTVProvider(
            id = id,
            name = name,
            playlistUrl = playlistUrl,
            isEnabled = true,
            health = ProviderHealth.HEALTHY,
            type = IPTVProviderType.M3U
        )
    }

    fun addProvider(name: String, playlistUrl: String): IPTVProvider {
        val redactedUrl = com.example.calmsource.core.network.UrlRedactor.redactUrl(playlistUrl)
        val provider = createM3uProvider(name, redactedUrl)
        XtreamRepository.tokenStore.savePassword(provider.id, "m3u_playlist_url", playlistUrl)
        runIO { dao.insertProvider(provider.toEntity()) }
        _syncStates.update { current ->
            current + (provider.id to ProviderSyncState(provider.id, ProviderSyncStatus.IDLE))
        }
        return provider
    }

    suspend fun addM3uProvider(name: String, playlistUrl: String): IPTVProvider = withContext(Dispatchers.IO) {
        val redactedUrl = com.example.calmsource.core.network.UrlRedactor.redactUrl(playlistUrl)
        val provider = createM3uProvider(name, redactedUrl)
        XtreamRepository.tokenStore.savePassword(provider.id, "m3u_playlist_url", playlistUrl)
        dao.insertProvider(provider.toEntity())
        _syncStates.update { current ->
            current + (provider.id to ProviderSyncState(provider.id, ProviderSyncStatus.IDLE))
        }
        provider
    }

    /** Updates M3U metadata and secure playlist material in place so channels, favourites,
     * history, and EPG associations keep the same provider identity. */
    suspend fun updateM3uProvider(
        providerId: String,
        name: String,
        playlistUrl: String
    ): IPTVProvider = withContext(Dispatchers.IO) {
        val trimmedUrl = playlistUrl.trim()
        val scheme = runCatching { java.net.URI(trimmedUrl).scheme?.lowercase() }.getOrNull()
        require(scheme == "http" || scheme == "https") { "Playlist URL must use http or https" }

        val existingEntity = dao.getProviderByIdDirect(providerId)
            ?: throw IllegalArgumentException("IPTV provider not found")
        val existing = existingEntity.toDomain()
        require(existing.type == IPTVProviderType.M3U) { "Provider is not an M3U playlist" }

        val oldSecureUrl = XtreamRepository.tokenStore.readPassword(providerId, "m3u_playlist_url")
        val updated = existing.copy(
            name = name.trim().ifBlank { "M3U Playlist" },
            playlistUrl = com.example.calmsource.core.network.UrlRedactor.redactUrl(trimmedUrl)
        )
        try {
            XtreamRepository.tokenStore.savePassword(providerId, "m3u_playlist_url", trimmedUrl)
            dao.updateProvider(updated.toEntity())
        } catch (error: Exception) {
            if (oldSecureUrl != null) {
                runCatching {
                    XtreamRepository.tokenStore.savePassword(providerId, "m3u_playlist_url", oldSecureUrl)
                }
            }
            throw error
        }
        updated
    }

    suspend fun addXtreamProvider(name: String, serverUrl: String, username: String, password: String): Result<IPTVProvider> {
        val result = XtreamRepository.addXtreamProvider(
            name = name,
            serverUrl = serverUrl,
            username = username,
            password = password,
            persistProvider = false
        )
        if (result.isSuccess) {
            val p = result.getOrThrow()
            val persisted = runCatching {
                withContext(Dispatchers.IO) {
                    val iptvDao = databaseOrNull()?.iptvDao() ?: dao
                    iptvDao.insertProvider(p.toEntity())
                }
            }
            persisted.getOrElse { throwable ->
                runCatching { XtreamRepository.deleteXtreamProvider(p.id) }
                val safeMessage = com.example.calmsource.core.network.UrlRedactor
                    .redactErrorMessage(throwable.message ?: "Failed to save Xtream provider")
                return Result.failure(Exception(safeMessage))
            }
            _syncStates.update { current -> current + (p.id to ProviderSyncState(p.id, ProviderSyncStatus.IDLE)) }
        }
        return result
    }

    /** Validates replacement Xtream credentials before changing the existing provider. */
    suspend fun updateXtreamProvider(
        providerId: String,
        name: String,
        serverUrl: String,
        username: String,
        password: String
    ): Result<IPTVProvider> = withContext(Dispatchers.IO) {
        val existingEntity = dao.getProviderByIdDirect(providerId)
            ?: return@withContext Result.failure(IllegalArgumentException("IPTV provider not found"))
        val existing = existingEntity.toDomain()
        if (existing.type != IPTVProviderType.XTREAM) {
            return@withContext Result.failure(IllegalArgumentException("Provider is not an Xtream account"))
        }

        val validatedResult = XtreamRepository.addXtreamProvider(
            name = name,
            serverUrl = serverUrl,
            username = username,
            password = password,
            persistProvider = false
        )
        if (validatedResult.isFailure) return@withContext validatedResult

        val temporary = validatedResult.getOrThrow()
        val oldUsername = existing.username.orEmpty()
        val oldPassword = oldUsername.takeIf { it.isNotBlank() }?.let {
            XtreamRepository.tokenStore.readPassword(providerId, it)
        }
        val oldPortal = XtreamRepository.tokenStore.readPortalUrl(providerId)
        val updated = temporary.copy(id = providerId, isEnabled = existing.isEnabled)
        val userPortalUrl = XtreamServerUrlNormalizer.normalizePortalUrl(
            XtreamServerUrlNormalizer.preprocessPortalInput(serverUrl)
        ) ?: updated.serverUrl

        try {
            XtreamRepository.tokenStore.savePortalUrl(providerId, userPortalUrl)
            XtreamRepository.tokenStore.savePassword(providerId, updated.username.orEmpty(), password)
            dao.updateProvider(updated.copy(serverUrl = userPortalUrl, playlistUrl = userPortalUrl).toEntity())
            if (oldUsername.isNotBlank() && oldUsername != updated.username) {
                XtreamRepository.tokenStore.deletePassword(providerId, oldUsername)
            }
            XtreamRepository.markProviderRecentlyAuthed(providerId)
            _syncStates.update { current ->
                current + (providerId to ProviderSyncState(providerId, ProviderSyncStatus.IDLE))
            }
            Result.success(updated.copy(serverUrl = userPortalUrl, playlistUrl = userPortalUrl))
        } catch (error: Exception) {
            val newUsername = updated.username.orEmpty()
            if (newUsername.isNotBlank() && newUsername != oldUsername) {
                runCatching { XtreamRepository.tokenStore.deletePassword(providerId, newUsername) }
            }
            if (oldPassword != null && oldUsername.isNotBlank()) {
                runCatching { XtreamRepository.tokenStore.savePassword(providerId, oldUsername, oldPassword) }
            } else if (newUsername.isNotBlank() && newUsername == oldUsername) {
                runCatching { XtreamRepository.tokenStore.deletePassword(providerId, newUsername) }
            }
            if (oldPortal != null) {
                runCatching { XtreamRepository.tokenStore.savePortalUrl(providerId, oldPortal) }
            } else {
                runCatching { XtreamRepository.tokenStore.deletePortalUrl(providerId) }
            }
            val safeMessage = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(
                error.message ?: "Failed to update Xtream provider"
            )
            Result.failure(Exception(safeMessage))
        } finally {
            runCatching { XtreamRepository.tokenStore.clearProvider(temporary.id) }
            XtreamRepository.clearRecentlyAuthed(temporary.id)
        }
    }

    suspend fun deleteProvider(providerId: String) = withContext(Dispatchers.IO) {
        activeSyncJobs[providerId]?.cancel()
        activeSyncJobs.remove(providerId)
        epgCache.evictAll()
        val database = databaseOrNull()
        val providerEntity = dao.getAllProviders().first().find { it.id == providerId }
        if (providerEntity == null) {
            // No DB record left to protect — clear any orphaned credentials and exit.
            runCatching { XtreamRepository.tokenStore.clearProvider(providerId) }
            return@withContext
        }
        val provider = providerEntity.toDomain()

        if (provider.type == IPTVProviderType.XTREAM) {
            if (database != null) {
                XtreamRepository.deleteXtreamProvider(providerId)
            } else {
                dao.deleteEPGProgramsByProvider(providerId)
                dao.deleteEPGSourcesByProvider(providerId)
                dao.deleteChannelsByProvider(providerId)
                dao.deleteProvider(providerEntity)
                XtreamRepository.deleteXtreamProvider(providerId)
            }
        } else {
            if (database != null) {
                database.withTransaction {
                    dao.deleteEPGProgramsByProvider(providerId)
                    dao.deleteEPGSourcesByProvider(providerId)
                    dao.deleteChannelsByProvider(providerId)
                    dao.deleteProvider(providerEntity)
                }
            } else {
                dao.deleteEPGProgramsByProvider(providerId)
                dao.deleteEPGSourcesByProvider(providerId)
                dao.deleteChannelsByProvider(providerId)
                dao.deleteProvider(providerEntity)
            }
        }

        // Clear credentials only after DB cleanup has succeeded; if a delete above throws we keep
        // the credentials so the (still-present) provider remains usable and re-syncable (bug #9).
        runCatching { XtreamRepository.tokenStore.clearProvider(providerId) }

        _syncStates.update { current -> current - providerId }
        clearResolvedUrlCache()
        val programs = dao.getAllEPGPrograms().map { it.toDomain() }
        synchronized(dataLock) {
            parsedChannels = parsedChannels.filter { it.providerId != providerId }
            parsedPrograms = programs
        }
        updateSortedChannelsCacheSync()
        publishChannels(getChannels())
        triggerMatchEpg()
        refreshHealthCache()
    }

    /** Xtream sync progress, delegated from [XtreamRepository]. */
    val xtreamSyncProgress get() = XtreamRepository.syncProgress

    suspend fun syncXtreamProvider(providerId: String) {
        syncXtreamProvider(providerId, xtreamApiClient)
    }

    fun startXtreamProviderSync(providerId: String) {
        // Run on the dedicated syncScope (not the shared `scope`) so user-initiated provider sync
        // is isolated from discovery ingestion / other background work, matching the documented
        // intent of syncScope (bug #11). Tests still run synchronously.
        if (isTest) {
            kotlinx.coroutines.runBlocking { syncXtreamProvider(providerId) }
        } else {
            syncScope.launch { syncXtreamProvider(providerId) }
        }
    }

    private suspend fun onSyncComplete(providerId: String) {
        withContext(Dispatchers.IO) {
            refreshHealthCache()
        }
        schedulePostSyncDiscoveryIngest(providerId)
        if (XtreamRepository.shouldScheduleBackgroundEpgSync()) {
            appContext?.let { IptvSyncScheduler.scheduleXtreamEpgSync(it, providerId) }
        }
    }

    private fun schedulePostSyncDiscoveryIngest(providerId: String) {
        discoveryIngestionJob?.cancel()
        discoveryIngestionJob = scope.launch(Dispatchers.IO) {
            try {
                val entities = dao.getChannelsByProvider(providerId).first()
                if (entities.isEmpty()) return@launch
                val discoveryChannels = entities.map { entity ->
                    com.example.calmsource.core.discoveryengine.models.IptvChannel(
                        id = entity.id,
                        name = entity.name,
                        logoUrl = entity.tvgLogo,
                        streamUrl = com.example.calmsource.core.network.UrlRedactor.redactUrl(entity.streamUrl),
                        category = entity.groupTitle,
                        providerId = entity.providerId,
                        tvgId = entity.tvgId
                    )
                }
                discoveryChannels.chunked(500).forEach { chunk ->
                    currentCoroutineContext().ensureActive()
                    DiscoveryEngine.ingestIptvChannels(chunk)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("IPTVRepository", "Failed post-sync discovery ingest for $providerId", e)
            }
        }
    }

    private fun markLiveChannelsReadyForBrowsing(providerId: String, progressPercent: Int) {
        _syncStates.update { current ->
            current + (
                providerId to ProviderSyncState(
                    providerId = providerId,
                    status = ProviderSyncStatus.SYNCING,
                    progressPercent = progressPercent,
                    warning = "Live channels ready. Still syncing movies and series in the background."
                )
                )
        }
        scheduleLightweightLiveGuideIndexRefresh(immediate = true)
    }

    suspend fun syncXtreamProvider(providerId: String, apiClient: XtreamApiClient) {
        IptvProviderSyncCoordinator.withProviderLock(providerId) {
          xtreamSyncMutex.withLock {
            kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.let { registerSyncJob(providerId, it) }
            try {
                _syncStates.update { current -> current + (providerId to ProviderSyncState(providerId, ProviderSyncStatus.SYNCING, 10)) }
                try {
                    XtreamRepository.syncProvider(providerId, apiClient) { progress ->
                        val percent = progress.progressPercent.coerceIn(0, 100)
                        when (progress.stage) {
                            com.example.calmsource.core.model.XtreamSyncStage.SYNCING_VOD_CATEGORIES,
                            com.example.calmsource.core.model.XtreamSyncStage.SYNCING_VOD_STREAMS,
                            com.example.calmsource.core.model.XtreamSyncStage.SYNCING_SERIES_CATEGORIES,
                            com.example.calmsource.core.model.XtreamSyncStage.SYNCING_SERIES,
                            com.example.calmsource.core.model.XtreamSyncStage.SYNCING_EPG -> {
                                markLiveChannelsReadyForBrowsing(providerId, percent)
                            }
                            com.example.calmsource.core.model.XtreamSyncStage.VALIDATING,
                            com.example.calmsource.core.model.XtreamSyncStage.SYNCING_LIVE_CATEGORIES,
                            com.example.calmsource.core.model.XtreamSyncStage.SYNCING_LIVE_STREAMS -> {
                                _syncStates.update { current ->
                                    current + (providerId to ProviderSyncState(providerId, ProviderSyncStatus.SYNCING, percent))
                                }
                                scheduleLightweightLiveGuideIndexRefresh()
                            }
                            else -> Unit
                        }
                    }
                    val syncWarning = XtreamRepository.syncProgress.value?.warning?.takeIf { it.isNotBlank() }
                    _syncStates.update { current ->
                        current + (providerId to ProviderSyncState(
                            providerId = providerId,
                            status = ProviderSyncStatus.SUCCESS,
                            progressPercent = 100,
                            warning = syncWarning
                        ))
                    }
                    onSyncComplete(providerId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val safeMessage = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(e.message ?: "Unknown error")
                    _syncStates.update { current -> current + (providerId to ProviderSyncState(providerId, ProviderSyncStatus.ERROR, 100, safeMessage)) }
                }
            } finally {
                kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.let { unregisterSyncJob(providerId, it) }
            }
          }
        }
    }

    suspend fun addEpgSource(providerId: String, name: String, url: String): EPGSource = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        val scheme = try { java.net.URI(trimmedUrl).scheme?.lowercase() } catch (e: Exception) { null }
        require(scheme == "http" || scheme == "https") { "EPG URL must use http or https" }
        val id = "epg-${UUID.randomUUID()}"
        EpgSourceUrlStorage.persist(providerId, id, trimmedUrl)
        val source = EPGSource(
            id = id,
            providerId = providerId,
            name = name.trim().ifBlank { "EPG Guide" },
            url = EpgSourceUrlStorage.sanitizeForPersistence(trimmedUrl),
            lastSyncMs = 0
        )
        try {
            dao.insertEPGSource(source.toEntity())
        } catch (e: Exception) {
            EpgSourceUrlStorage.clear(providerId, id)
            throw e
        }
        source
    }

    suspend fun syncPlaylistFromUrl(providerId: String) = withContext(Dispatchers.IO) {
        IptvProviderSyncCoordinator.withProviderLock(providerId) {
            kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.let { registerSyncJob(providerId, it) }
            try {
                updateSyncState(providerId, ProviderSyncStatus.SYNCING, 10)
                val providerEntity = dao.getAllProviders().first().find { it.id == providerId }
                if (providerEntity == null) {
                    updateSyncState(providerId, ProviderSyncStatus.ERROR, 100, "Provider not found")
                    return@withProviderLock
                }
                val provider = providerEntity.toDomain()
                // The real playlist URL lives in the secure store; provider.playlistUrl is the redacted
                // copy persisted in Room. Only fall back to it when it carries no redaction markers
                // (i.e. a credential-free URL). Never sync a "password=REDACTED" URL — it would fail
                // silently — surface a clear re-enter prompt instead (bug #8).
                val originalUrl = XtreamRepository.tokenStore.readPassword(providerId, "m3u_playlist_url")
                    ?: provider.playlistUrl.takeUnless { it.contains("REDACTED") }
                if (originalUrl == null) {
                    updateSyncState(
                        providerId,
                        ProviderSyncStatus.ERROR,
                        100,
                        "Saved playlist URL is unavailable — please re-enter the playlist URL for this provider."
                    )
                    return@withProviderLock
                }
                try {
                    val scheme = try { java.net.URI(originalUrl).scheme?.lowercase() } catch (e: Exception) { null }
                    if (scheme != "http" && scheme != "https") {
                        updateSyncState(providerId, ProviderSyncStatus.ERROR, 100, "Unsafe or invalid URL scheme")
                        return@withProviderLock
                    }
                    
                    var downloadSuccess = false
                    var errorMsg: String? = null
                    
                    // Retry up to 3 times with backoff for transient network failures
                    for (downloadAttempt in 1..3) {
                        try {
                            com.example.calmsource.core.network.NetworkClient.iptvClient.get(originalUrl) {
                                timeout {
                                    requestTimeoutMillis = 300_000L
                                    connectTimeoutMillis = 30_000L
                                    socketTimeoutMillis = 300_000L
                                }
                            }.use { response ->
                                if (response.status.value in 200..299) {
                                    response.bodyAsChannel().toInputStream().use { stream ->
                                        syncPlaylist(providerId, stream, alreadyLocked = true)
                                    }
                                    downloadSuccess = true
                                } else if (response.status.value in listOf(429, 502, 503, 504) && downloadAttempt < 3) {
                                    errorMsg = "HTTP ${response.status.value}"
                                    kotlinx.coroutines.delay(if (downloadAttempt == 1) 2_000L else 5_000L)
                                } else {
                                    errorMsg = "HTTP ${response.status.value}"
                                }
                            }
                            if (downloadSuccess) break
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errorMsg = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(
                                e.message ?: "Download failed"
                            )
                            if (downloadAttempt < 3) {
                                kotlinx.coroutines.delay(if (downloadAttempt == 1) 2_000L else 5_000L)
                            }
                        }
                    }
                    
                    if (!downloadSuccess) {
                        updateSyncState(providerId, ProviderSyncStatus.ERROR, 100, errorMsg ?: "Download failed")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch(e: Exception) {
                    val safeMessage = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(e.message ?: "Unknown error")
                    updateSyncState(providerId, ProviderSyncStatus.ERROR, 100, safeMessage)
                }
            } finally {
                kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.let { unregisterSyncJob(providerId, it) }
            }
        }
    }

    suspend fun syncEpgFromUrl(sourceId: String) = withContext(Dispatchers.IO) {
        val sourceEntity = dao.getAllEPGSources().first().find { it.id == sourceId } ?: return@withContext
        val source = sourceEntity.toDomain()
        val providerId = source.providerId
        IptvProviderSyncCoordinator.withProviderLock(providerId) {
            kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.let { registerSyncJob(providerId, it) }
            try {
                val resolvedUrl = EpgSourceUrlStorage.resolve(source)
                if (resolvedUrl == null) {
                    updateSyncState(
                        providerId,
                        ProviderSyncStatus.ERROR,
                        100,
                        "Saved EPG URL is unavailable — please re-enter the XMLTV URL."
                    )
                    return@withProviderLock
                }
                val sanitizedUrl = EpgSourceUrlStorage.sanitizeForPersistence(resolvedUrl)
                if (sanitizedUrl != source.url) {
                    // One-time migration for EPG sources created before secure URL storage existed.
                    EpgSourceUrlStorage.persist(providerId, source.id, resolvedUrl)
                    dao.insertEPGSource(source.copy(url = sanitizedUrl).toEntity())
                }
                val scheme = try { java.net.URI(resolvedUrl).scheme?.lowercase() } catch (e: Exception) { null }
                if (scheme != "http" && scheme != "https") {
                    android.util.Log.e("IPTVRepository", "Invalid EPG URL scheme: $scheme for source: ${source.id}")
                    updateSyncState(providerId, ProviderSyncStatus.ERROR, 100, "Invalid EPG URL scheme")
                    return@withProviderLock
                }
                try {
                        var downloadSuccess = false
                        var syncSucceeded = false
                        
                        // Retry up to 3 times with backoff for transient network failures
                        for (downloadAttempt in 1..3) {
                            try {
                                com.example.calmsource.core.network.NetworkClient.iptvClient.get(resolvedUrl) {
                                    timeout {
                                        requestTimeoutMillis = 300_000L
                                        connectTimeoutMillis = 30_000L
                                        socketTimeoutMillis = 300_000L
                                    }
                                }.use { response ->
                                    if (response.status.value in 200..299) {
                                        response.bodyAsChannel().toInputStream().use { stream ->
                                            syncSucceeded = syncEPG(sourceId, stream, alreadyLocked = true)
                                        }
                                        downloadSuccess = true
                                    } else if (response.status.value in listOf(429, 502, 503, 504) && downloadAttempt < 3) {
                                        kotlinx.coroutines.delay(if (downloadAttempt == 1) 2_000L else 5_000L)
                                    }
                                }
                                if (downloadSuccess) break
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                if (downloadAttempt < 3) {
                                    kotlinx.coroutines.delay(if (downloadAttempt == 1) 2_000L else 5_000L)
                                }
                            }
                        }
                        
                        if (downloadSuccess && syncSucceeded) {
                            // Sync completed successfully
                        } else if (!downloadSuccess) {
                            updateSyncState(providerId, ProviderSyncStatus.ERROR, 100, "Download failed")
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val safeMessage = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(e.message ?: "Unknown error")
                    updateSyncState(providerId, ProviderSyncStatus.ERROR, 100, safeMessage)
                }
            } finally {
                kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.let { unregisterSyncJob(providerId, it) }
            }
        }
    }

    suspend fun syncPlaylist(providerId: String, m3uStream: java.io.InputStream, alreadyLocked: Boolean = false) = withContext(Dispatchers.IO) {
        val block = suspend {
          playlistSyncMutex.withLock {
            updateSyncState(providerId, ProviderSyncStatus.SYNCING, 10)
            var tempFile: java.io.File? = null
            try {
                tempFile = java.io.File.createTempFile("m3u_", ".tmp")
                tempFile.outputStream().use { output ->
                    val boundedStream = BoundedInputStream(m3uStream, MAX_M3U_DOWNLOAD_BYTES)
                    boundedStream.copyTo(output)
                }

                suspend fun prepareChunk(chunk: List<IPTVChannel>) = withContext(Dispatchers.Default) {
                    chunk.map { channel ->
                        val lang = IptvChannelOrganizer.detectLanguage(channel)
                        val cntry = IptvChannelOrganizer.detectCountry(channel)
                        val withMeta = channel.copy(language = lang, country = cntry)
                        M3uStreamUrlStorage.persistSecureUrl(
                            providerId = providerId,
                            channelId = withMeta.id,
                            rawUrl = withMeta.streamUrl
                        )
                        withMeta.copy(
                            streamUrl = M3uStreamUrlStorage.sanitizeForPersistence(withMeta.streamUrl)
                        ).toEntity()
                    }
                }

                val database = databaseOrNull()
                val collectedEntities = mutableListOf<List<com.example.calmsource.core.database.entity.IPTVChannelEntity>>()

                val parseResult = withContext(Dispatchers.Default) {
                    tempFile.inputStream().use { input ->
                        M3UParser.parse(
                            input,
                            providerId,
                            maxChannels = MAX_IMPORTED_CHANNELS,
                            parsingContext = kotlin.coroutines.coroutineContext
                        ) { chunk ->
                            val prepared = prepareChunk(chunk)
                            collectedEntities.add(prepared)
                        }
                    }
                }

                if (!parseResult.isSuccess) {
                    val reason = parseResult.warnings.take(3).joinToString("; ")
                    throw java.io.IOException(reason.ifBlank { "Playlist import failed" })
                }

                if (database != null) {
                    database.withTransaction {
                        dao.deleteChannelsByProvider(providerId)
                        collectedEntities.forEach { batch ->
                            currentCoroutineContext().ensureActive()
                            dao.insertChannels(batch)
                        }
                    }
                } else {
                    dao.deleteChannelsByProvider(providerId)
                    collectedEntities.forEach { batch ->
                        dao.insertChannels(batch)
                    }
                }

                val dbChannels = dao.getChannelsByProvider(providerId).first().map { it.toDomain() }
                synchronized(dataLock) {
                    parsedChannels = parsedChannels.filter { it.providerId != providerId } + dbChannels
                }
                updateSortedChannelsCacheSync()
                publishChannels(getChannels())
                _channelsReady.value = true
                updateSyncState(providerId, ProviderSyncStatus.SUCCESS, 100)
                schedulePostSyncDiscoveryIngest(providerId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val safeMessage = com.example.calmsource.core.network.UrlRedactor
                    .redactErrorMessage(e.message ?: e.javaClass.simpleName)
                updateSyncState(providerId, ProviderSyncStatus.ERROR, 100, safeMessage)
            } finally {
                tempFile?.delete()
            }
          }
        }
        if (alreadyLocked) {
            block()
        } else {
            IptvProviderSyncCoordinator.withProviderLock(providerId) { block() }
        }
    }

    suspend fun syncEPG(sourceId: String, xmltvStream: java.io.InputStream, alreadyLocked: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val providerId = dao.getAllEPGSources().first().find { it.id == sourceId }?.providerId
            ?: return@withContext false
        val block = suspend {
          epgSyncMutex.withLock {
            val syncNowMs = System.currentTimeMillis()
            var tempFile: java.io.File? = null
            try {
                tempFile = java.io.File.createTempFile("epg_", ".tmp")
                tempFile.outputStream().use { output ->
                    xmltvStream.copyToBounded(output, MAX_XMLTV_DOWNLOAD_BYTES)
                }

                val database = databaseOrNull()
                val currentCoroutineContext = kotlinx.coroutines.currentCoroutineContext()

                val collectedChunks = mutableListOf<List<com.example.calmsource.core.database.entity.EPGProgramEntity>>()
                val chunkBuffer = mutableListOf<com.example.calmsource.core.database.entity.EPGProgramEntity>()

                fun flushParseBuffers() {
                    if (chunkBuffer.isNotEmpty()) {
                        collectedChunks.add(chunkBuffer.toList())
                        chunkBuffer.clear()
                    }
                }

                val parseResult = withContext(Dispatchers.Default) {
                    tempFile.inputStream().use { input ->
                        XMLTVParser.parse(
                            input,
                            timeWindow = XMLTVTimeWindow.forSync(syncNowMs),
                            maxPrograms = MAX_IMPORTED_EPG_PROGRAMS,
                            onProgramParsed = { program ->
                                currentCoroutineContext.ensureActive()
                                chunkBuffer.add(program.toEntity())
                                if (chunkBuffer.size >= 500) {
                                    flushParseBuffers()
                                }
                            }
                        )
                    }
                }

                val result = parseResult
                if (!result.isSuccess) {
                    val errorMsg = result.warnings
                        .take(3)
                        .joinToString(separator = "; ")
                        .ifBlank { "EPG import failed" }
                    updateSyncState(providerId, ProviderSyncStatus.ERROR, 100, errorMsg)
                    false
                } else {
                flushParseBuffers()
                if (collectedChunks.isEmpty()) {
                    updateSyncState(
                        providerId,
                        ProviderSyncStatus.ERROR,
                        100,
                        "EPG import produced no programs in the sync window"
                    )
                    false
                } else {

                if (database != null) {
                    database.withTransaction {
                        dao.deleteEPGProgramsByProvider(providerId)
                        collectedChunks.forEach { chunk -> dao.insertEPGPrograms(chunk) }
                    }
                } else {
                    val providerChannelIds = dao.getChannelsByProvider(providerId).first()
                        .flatMap { listOf(it.id, it.tvgId).filterNotNull() }
                        .toSet()
                    val previousPrograms = dao.getAllEPGPrograms().filter { it.channelId in providerChannelIds }
                    
                    try {
                        dao.deleteEPGProgramsByProvider(providerId)
                        collectedChunks.forEach { chunk -> dao.insertEPGPrograms(chunk) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (previousPrograms.isNotEmpty()) {
                            runCatching { dao.insertEPGPrograms(previousPrograms) }
                        }
                        throw e
                    }
                }

                collectedChunks.forEach { chunk ->
                    currentCoroutineContext.ensureActive()
                    indexDiscoveryEpgPrograms(chunk.map { it.toDomain() })
                }
                dao.pruneOldEPGPrograms(syncNowMs)
                epgCache.evictAll()

                if (database == null) {
                    val entities = dao.getAllEPGPrograms()
                    synchronized(dataLock) {
                        parsedPrograms = entities.map { it.toDomain() }
                    }
                }

                val currentSource = epgSources.value.find { it.id == sourceId }
                if (currentSource != null) {
                    dao.insertEPGSource(currentSource.copy(lastSyncMs = System.currentTimeMillis()).toEntity())
                }
                triggerMatchEpg()
                true
                }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val safeMessage = com.example.calmsource.core.network.UrlRedactor
                    .redactErrorMessage(e.message ?: e.javaClass.simpleName)
                updateSyncState(providerId, ProviderSyncStatus.ERROR, 100, "EPG sync: $safeMessage")
                android.util.Log.e("IPTVRepository", "EPG sync failed: $safeMessage")
                false
            } finally {
                tempFile?.delete()
            }
          }
        }
        if (alreadyLocked) {
            block()
        } else {
            IptvProviderSyncCoordinator.withProviderLock(providerId) { block() }
        }
    }

    private suspend fun indexDiscoveryEpgPrograms(programs: List<EPGProgram>) {
        if (isTest) {
            epgProgramsIngestedInTest?.addAll(programs)
        }
        if (isTest || programs.isEmpty()) return
        try {
            programs
                .map { program -> program.toDiscoveryEpgProgram() }
                .distinctBy { it.id }
                .chunked(500)
                .forEach { chunk ->
                    currentCoroutineContext().ensureActive()
                    DiscoveryEngine.ingestEpgPrograms(chunk)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("IPTVRepository", "Failed to ingest EPG programs into discovery index: ${e.message}")
        }
    }

    private fun EPGProgram.toDiscoveryEpgProgram(): DiscoveryEpgProgram {
        return DiscoveryEpgProgram(
            id = id,
            channelId = channelId,
            title = title,
            description = description,
            category = category,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            language = language,
            episodeNum = episodeNum
        )
    }

    private fun updateSyncState(
        providerId: String,
        status: ProviderSyncStatus,
        progress: Int,
        error: String? = null,
        warning: String? = null
    ) {
        _syncStates.update { current ->
            current + (providerId to ProviderSyncState(providerId, status, progress, error, warning))
        }
    }

    fun matchEP() {
        runIO {
            triggerMatchEpg()
        }
    }

    /**
     * Matches all channels against EPG programs to produce a channel→EPG ID map.
     *
     * PERF: The channel and program lists are pre-computed BEFORE acquiring
     * [epgMatchMutex], so the expensive `getChannels()` + `getPrograms()` calls
     * don't block concurrent EPG sync operations.
     */
    private suspend fun matchEPGSync() {
        // Pre-compute outside the mutex to avoid blocking EPG sync
        val channelsList = getChannels()
        val uniqueEpgChannelIds = getUniqueEPGChannelIds()
        epgMatchMutex.withLock {
            // PERF: IPTV-4 — Pre-compute normalized EPG ID maps for O(N+M) matching
            // instead of the previous O(N×M) approach.
            val epgIdSet = uniqueEpgChannelIds.toSet()
            val normalizedEpgIdMap = uniqueEpgChannelIds.groupBy { it.normalize() }
            val precomputedNormalizedEpgIds = uniqueEpgChannelIds.map { it to it.normalize() }

            val fuzzyCache = mutableMapOf<String, String?>()

            var count = 0
            val newMatches = channelsList.associate { channel ->
                count++
                if (count % 100 == 0) {
                    kotlinx.coroutines.yield()
                }
                channel.id to findMatch(channel, epgIdSet, normalizedEpgIdMap, precomputedNormalizedEpgIds, fuzzyCache)
            }

            synchronized(dataLock) {
                matches = newMatches
            }
        }
    }

    private fun findMatch(
        channel: IPTVChannel,
        epgIdSet: Set<String>,
        normalizedEpgIdMap: Map<String, List<String>>,
        precomputedNormalizedEpgIds: List<Pair<String, String>>,
        fuzzyCache: MutableMap<String, String?>
    ): EPGMatch {
        // Tier 1: Exact tvg-id match
        val tvgId = channel.tvgId
        if (!tvgId.isNullOrEmpty() && epgIdSet.contains(tvgId)) {
            return EPGMatch(channel.id, tvgId, EPGMatchType.EXACT_ID)
        }

        // Tier 2: Normalized tvg-name match
        val normTvgName = channel.tvgName?.normalize()
        if (!normTvgName.isNullOrEmpty()) {
            val tvgNameMatch = normalizedEpgIdMap[normTvgName]?.firstOrNull()
            if (tvgNameMatch != null) return EPGMatch(channel.id, tvgNameMatch, EPGMatchType.NORMALIZED_NAME)
        }

        // Tier 3: Normalized display name match
        val normName = channel.name.normalize()
        val nameMatch = normalizedEpgIdMap[normName]?.firstOrNull()
        if (nameMatch != null) return EPGMatch(channel.id, nameMatch, EPGMatchType.NORMALIZED_NAME)

        // Tier 4: Fuzzy contains match (must be linear scan)
        val fuzzyMatch = if (normName.length >= 3) {
            fuzzyCache.getOrPut(normName) {
                var candidate: String? = null
                for ((rawId, normId) in precomputedNormalizedEpgIds) {
                    if (normId.startsWith(normName) || normName.startsWith(normId)) {
                        candidate = rawId
                        break
                    }
                    if (candidate == null && (normId.contains(normName) || normName.contains(normId))) {
                        candidate = rawId
                    }
                }
                candidate
            }
        } else null
        if (fuzzyMatch != null) return EPGMatch(channel.id, fuzzyMatch, EPGMatchType.FUZZY)

        return EPGMatch(channel.id, "", EPGMatchType.NONE)
    }



    private fun String.normalize(): String {
        return this.lowercase(Locale.ROOT).replace(NORMALIZE_REGEX, "")
    }

    private fun String.normalizeForSearch(): String {
        return this.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
    }


    fun getMatchStatusForChannel(channelId: String): EPGMatch? {
        return synchronized(dataLock) { matches[channelId] }
    }

    fun updateManualMatch(channelId: String, epgId: String) {
        synchronized(dataLock) {
            matches = matches.toMutableMap().apply {
                put(channelId, EPGMatch(channelId, epgId, EPGMatchType.MANUAL))
            }
        }
    }

    suspend fun getNowNextForChannel(channelId: String, currentTimeMs: Long = System.currentTimeMillis()): EpgNowNext {
        val match = getMatchStatusForChannel(channelId)
        val epgId = match?.epgId ?: return EpgNowNext(null, null, 0f)

        if (epgId.isEmpty()) {
            return EpgNowNext(null, null, 0f)
        }

        val channelPrograms = getEPGProgramsForChannelCached(epgId)

        return calculateNowNext(channelPrograms, currentTimeMs)
    }

    /**
     * Returns now/next EPG info for multiple channels in a single pass.
     *
     * PERF: Builds a map by iterating all programs once and collecting only
     * the EPG IDs we actually need (O(N) where N = total programs), instead of
     * the previous filter+groupBy which creates an intermediate filtered list
     * (also O(N) but with higher allocation and groupBy overhead).
     */
    suspend fun getNowNextForChannels(channelIds: List<String>, currentTimeMs: Long = System.currentTimeMillis()): Map<String, EpgNowNext> {
        val result = mutableMapOf<String, EpgNowNext>()
        val matchedEpgIds = mutableMapOf<String, String>()

        val currentMatches = synchronized(dataLock) { matches }
        for (channelId in channelIds) {
            val match = currentMatches[channelId]
            if (match != null && match.epgId.isNotEmpty()) {
                matchedEpgIds[channelId] = match.epgId
            }
        }

        val uniqueEpgIds = matchedEpgIds.values.toSet()
        if (uniqueEpgIds.isEmpty()) {
            return channelIds.associateWith { EpgNowNext(null, null, 0f) }
        }

        val missingEpgIds = uniqueEpgIds.filter { epgCache.get(it) == null }
        val database = databaseOrNull()
        if (database != null && missingEpgIds.isNotEmpty()) {
            val allPrograms = withContext(Dispatchers.IO) {
                dao.getEPGProgramsByChannelsDirect(missingEpgIds)
            }
            val programsByChannel = allPrograms.groupBy { it.channelId }
            for (epgId in missingEpgIds) {
                val entities = programsByChannel[epgId] ?: emptyList()
                val domainPrograms = entities.map { it.toDomain() }
                epgCache.put(epgId, domainPrograms)
            }
        } else if (database == null && missingEpgIds.isNotEmpty()) {
            val fallbackPrograms = synchronized(dataLock) { parsedPrograms }
            val programsByChannel = fallbackPrograms.groupBy { it.channelId }
            for (epgId in missingEpgIds) {
                val programs = programsByChannel[epgId] ?: emptyList()
                epgCache.put(epgId, programs)
            }
        }

        for (channelId in channelIds) {
            val epgId = matchedEpgIds[channelId]
            if (epgId != null) {
                val channelPrograms = getEPGProgramsForChannelCached(epgId)
                result[channelId] = calculateNowNext(channelPrograms, currentTimeMs)
            } else {
                result[channelId] = EpgNowNext(null, null, 0f)
            }
        }
        return result
    }

    // Exposed for testing
    internal fun calculateNowNext(channelPrograms: List<EPGProgram>, currentTimeMs: Long): EpgNowNext {
        if (channelPrograms.isEmpty()) return EpgNowNext(null, null, 0f)

        val currentProgram = channelPrograms.firstOrNull {
            currentTimeMs >= it.startTimeMs && currentTimeMs < it.endTimeMs
        }

        val nextProgram = if (currentProgram != null) {
            channelPrograms
                .filter { it.startTimeMs >= currentProgram.endTimeMs }
                .minByOrNull { it.startTimeMs }
        } else {
            channelPrograms
                .filter { it.startTimeMs >= currentTimeMs }
                .minByOrNull { it.startTimeMs }
        }

        val progress = if (currentProgram != null) {
            val duration = currentProgram.endTimeMs - currentProgram.startTimeMs
            if (duration > 0) {
                ((currentTimeMs - currentProgram.startTimeMs).toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
        } else {
            0f
        }

        return EpgNowNext(currentProgram, nextProgram, progress)
    }
}

data class EpgNowNext(
    val currentProgram: EPGProgram?,
    val nextProgram: EPGProgram?,
    val progressPercentage: Float
)
