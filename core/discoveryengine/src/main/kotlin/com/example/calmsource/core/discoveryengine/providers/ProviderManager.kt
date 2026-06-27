package com.example.calmsource.core.discoveryengine.providers

import android.content.Context
import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineDatabase
import com.example.calmsource.core.model.ResourceGovernor
import com.example.calmsource.core.model.ResourcePlaybackState
import com.example.calmsource.core.model.ResourceGovernorSnapshot

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Central authority for discovery providers. It owns registry/cache/telemetry
 * plumbing and never selects playback streams.
 */
object ProviderManager {

    private val _initialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()

    @Volatile private var registryStore: ProviderRegistryStore? = null
    @Volatile private var cacheStore: ProviderCacheStore? = null
    @Volatile private var telemetryStore: ProviderTelemetryStore? = null
    @Volatile private var featureExtractor: ProviderFeatureExtractor? = null
    @Volatile private var queue: EnrichmentQueue? = null
    private val circuitBreaker = ProviderCircuitBreaker()

    private val providerLock = Any()
    private val providers = mutableMapOf<String, Any>()

    @Volatile private var localOnlyMode: Boolean = false
    private val enrichmentLock = Any()
    private val enrichmentEnabled = ProviderType.entries.associateWith { true }.toMutableMap()
    private val _lowMemory = MutableStateFlow(false)
    private val _playbackActive = MutableStateFlow(false)

    fun init(context: Context) {
        if (_initialized.value) return
        synchronized(this) {
            if (_initialized.value) return
            val db = DiscoveryEngineDatabase.getInstance(context.applicationContext)
            val reg = ProviderRegistryStore(db.providerRegistryDao())
            val cache = ProviderCacheStore(db.providerCacheDao())
            val telemetry = ProviderTelemetryStore(db.providerTelemetryDao())
            registryStore = reg
            cacheStore = cache
            telemetryStore = telemetry
            featureExtractor = ProviderFeatureExtractor(cache)
            queue = EnrichmentQueue(
                providerManager = this,
                cacheStore = cache,
                isLowMemoryMode = { _lowMemory.value },
                isPlaybackActive = { _playbackActive.value },
                isLocalOnlyMode = { localOnlyMode }
            ).also { it.start() }
            _initialized.value = true
        }
    }

    internal fun initForTest(
        reg: ProviderRegistryStore,
        cache: ProviderCacheStore,
        telemetry: ProviderTelemetryStore
    ) {
        registryStore = reg
        cacheStore = cache
        telemetryStore = telemetry
        featureExtractor = ProviderFeatureExtractor(cache)
        circuitBreaker.resetAll()
        _initialized.value = true
    }

    suspend fun registerAddonProvider(
        addonId: String,
        addonName: String,
        endpointUrl: String,
        capabilities: Set<ProviderType>,
        isEnabled: Boolean,
        priority: Int,
        createProvider: () -> Any
    ) {
        val reg = registryStore ?: return
        if (capabilities.isEmpty()) return
        val existing = reg.get(addonId)
        reg.upsert(
            row = ProviderStatusRow(
                providerId = addonId,
                name = addonName,
                type = capabilities.firstOrNull() ?: ProviderType.METADATA,
                kind = ProviderKind.STREMIO_ADDON,
                isEnabled = existing?.isEnabled ?: isEnabled,
                isSystemProvider = false,
                isUserInstalled = true,
                priority = existing?.priority ?: priority,
                reliabilityScore = existing?.reliabilityScore ?: 1.0,
                failureCount = existing?.failureCount ?: 0,
                lastSuccessAt = existing?.lastSuccessAt,
                lastFailureAt = existing?.lastFailureAt,
                capabilities = capabilities
            ),
            endpointUrl = endpointUrl,
            privacySensitive = true
        )
        synchronized(providerLock) {
            providers[addonId] = createProvider()
        }
    }

    suspend fun unregisterProvider(providerId: String) {
        synchronized(providerLock) {
            providers.remove(providerId)
        }
        registryStore?.delete(providerId)
        circuitBreaker.reset(providerId)
    }

    suspend fun retainAddonProviders(activeAddonIds: Set<String>) {
        val reg = registryStore ?: return
        reg.getAll()
            .filter { it.kind == ProviderKind.STREMIO_ADDON && it.providerId !in activeAddonIds }
            .forEach { unregisterProvider(it.providerId) }
    }

    fun getEnabledProviders(type: ProviderType): List<Any> {
        if (!_initialized.value) return emptyList()
        if (!isEnrichmentAllowed(type)) return emptyList()
        val rows = registryStore?.getEnabled() ?: return emptyList()
        return rows.asSequence()
            .filter { type in it.capabilities }
            .filterNot { ReliabilityTracker.shouldQuarantine(it.reliabilityScore, it.failureCount) }
            .filterNot { circuitBreaker.isOpen(it.providerId) }
            .filterNot { localOnlyMode && !it.kind.localOnly }
            .sortedBy { it.priority }
            .mapNotNull { row ->
                synchronized(providerLock) { providers[row.providerId] }
            }
            .toList()
    }

    fun getProviderStatus(): Flow<List<ProviderStatusRow>> {
        return registryStore?.observeAll() ?: flowOf(emptyList())
    }

    fun snapshotProviderStatus(): List<ProviderStatusRow> = registryStore?.getAll() ?: emptyList()

    fun isLocalOnlyMode(): Boolean = localOnlyMode

    fun setLocalOnlyMode(enabled: Boolean) {
        localOnlyMode = enabled
    }

    fun isEnrichmentAllowed(type: ProviderType): Boolean {
        return synchronized(enrichmentLock) { enrichmentEnabled[type] ?: true }
    }

    fun setEnrichmentAllowed(type: ProviderType, enabled: Boolean) {
        synchronized(enrichmentLock) {
            enrichmentEnabled[type] = enabled
        }
    }

    fun snapshotEnrichmentSettings(): Map<ProviderType, Boolean> {
        return synchronized(enrichmentLock) { enrichmentEnabled.toMap() }
    }

    fun setLowMemoryMode(enabled: Boolean) {
        _lowMemory.value = enabled
        ResourceGovernor.setLowMemoryMode(enabled)
    }

    fun setPlaybackActive(active: Boolean) {
        _playbackActive.value = active
        ResourceGovernor.setPlaybackActive(active)
    }

    fun setPlaybackState(state: ResourcePlaybackState) {
        _playbackActive.value = state == ResourcePlaybackState.BUFFERING ||
            state == ResourcePlaybackState.READY_PLAYING
        ResourceGovernor.setPlaybackState(state)
    }

    fun resourceSnapshot(): ResourceGovernorSnapshot = ResourceGovernor.snapshot.value

    suspend fun snapshotRateLimits(): List<RateLimiterSnapshot> = queue?.snapshotRateLimits().orEmpty()

    fun snapshotProviderQueueSize(): Int = queue?.snapshotQueuedCount() ?: 0

    val providerCircuitState: StateFlow<Map<String, BreakerStatus>>
        get() = circuitBreaker.state

    fun snapshotProviderCircuitBreakers(): Map<String, BreakerStatus> = circuitBreaker.snapshot()

    fun resetAllProviderCircuitBreakers() {
        circuitBreaker.resetAll()
    }

    fun isProviderCircuitOpen(providerId: String): Boolean = circuitBreaker.isOpen(providerId)

    fun requireProviderAvailable(providerId: String) {
        circuitBreaker.requireAvailable(providerId)
    }

    suspend fun setProviderEnabled(providerId: String, enabled: Boolean) {
        registryStore?.setEnabled(providerId, enabled)
        circuitBreaker.reset(providerId)
    }

    suspend fun setProviderPriority(providerId: String, priority: Int) {
        registryStore?.setPriority(providerId, priority)
    }

    suspend fun replaceProviderEndpoint(providerId: String, newEndpoint: String) {
        val reg = registryStore ?: return
        reg.updateReliability(providerId, 1.0, 0, null, null)
        reg.updateEndpoint(providerId, newEndpoint)
        circuitBreaker.reset(providerId)
    }

    suspend fun clearProviderCache() {
        cacheStore?.clearAll()
    }

    internal suspend fun recordResult(providerId: String, requestType: String, result: ProviderResult<*>) {
        val reg = registryStore ?: return
        val tel = telemetryStore ?: return
        val row = reg.get(providerId) ?: return
        val now = System.currentTimeMillis()
        when (result) {
            is ProviderResult.Success -> {
                circuitBreaker.recordSuccess(providerId)
                val (newRel, newFailures) = ReliabilityTracker.onSuccess(row.reliabilityScore, row.failureCount)
                reg.updateReliability(providerId, newRel, newFailures, now, row.lastFailureAt)
                tel.logUsage(providerId, requestType, null, cacheHit = false, durationMs = 0, success = true)
            }
            is ProviderResult.Timeout -> {
                circuitBreaker.recordFailure(providerId)
                val (newRel, newFailures) = ReliabilityTracker.onFailure(row.reliabilityScore, row.failureCount)
                reg.updateReliability(providerId, newRel, newFailures, row.lastSuccessAt, now)
                tel.logFailure(providerId, requestType, null, "timeout")
                tel.logUsage(providerId, requestType, null, cacheHit = false, durationMs = 0, success = false)
            }
            is ProviderResult.Failure -> {
                circuitBreaker.recordFailure(providerId)
                val (newRel, newFailures) = ReliabilityTracker.onFailure(row.reliabilityScore, row.failureCount)
                reg.updateReliability(providerId, newRel, newFailures, row.lastSuccessAt, now)
                tel.logFailure(providerId, requestType, null, result.errorCode, result.message)
                tel.logUsage(providerId, requestType, null, cacheHit = false, durationMs = 0, success = false)
            }
            is ProviderResult.CacheOnly,
            is ProviderResult.Disabled,
            is ProviderResult.LocalOnly,
            is ProviderResult.Skipped -> Unit
        }
    }

    fun enqueueMetadataRefresh(mediaId: String) {
        queue?.enqueue(
            EnrichmentTask.FullEnrichment(
                mediaId = mediaId,
                profileId = "",
                externalIds = ExternalIdSet()
            )
        )
    }

    fun enrichItem(mediaId: String, profileId: String, ids: ExternalIdSet) {
        queue?.enqueue(EnrichmentTask.FullEnrichment(mediaId, profileId, ids))
    }

    fun cancelPendingForMedia(mediaId: String) {
        queue?.cancelPendingForMedia(mediaId)
    }

    fun extractFeatures(mediaId: String): EnrichmentFeatures {
        val extractor = featureExtractor ?: return EnrichmentFeatures(mediaId = mediaId)
        return extractor.extract(mediaId)
    }

    fun getCacheStore(): ProviderCacheStore? = cacheStore

    fun getTelemetryStore(): ProviderTelemetryStore? = telemetryStore

    fun getRegistryStore(): ProviderRegistryStore? = registryStore

    /**
     * Shuts down the enrichment queue. Call from Application.onTerminate()
     * or test teardown to prevent leaked coroutines.
     */
    fun shutdown() {
        queue?.shutdown()
        queue = null
    }
}
