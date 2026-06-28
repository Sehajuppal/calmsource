package com.example.calmsource.feature.extensions

import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.entity.ExtensionProviderEntity
import com.example.calmsource.core.model.TestEnvironment
import com.example.calmsource.core.database.mapper.*
import com.example.calmsource.core.discoveryengine.DiscoveryEngine
import com.example.calmsource.core.discoveryengine.models.MediaItem as DiscoveryMediaItem
import com.example.calmsource.core.discoveryengine.models.RecommendationItem
import com.example.calmsource.core.discoveryengine.models.RecommendationRow
import com.example.calmsource.core.discoveryengine.models.ScoreBreakdown
import kotlinx.collections.immutable.toImmutableList
import com.example.calmsource.core.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

object ExtensionRepository {
    private const val HOME_CATALOG_ITEM_LIMIT = 30
    private const val EXTENSION_REQUEST_TIMEOUT_MS = 25_000L
    private const val CATALOG_HOME_TTL_MS = 30 * 60 * 1000L
    private const val DEFAULT_DISCOVERY_ID = "com.linvo.cinemeta"
    private const val DEFAULT_DISCOVERY_URL = "https://v3-cinemeta.strem.io/manifest.json"
    private const val HEALTH_RECOVERY_INTERVAL_MS = 15 * 60 * 1000L  // 15 minutes

    private val configJsonParser = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val isTest: Boolean get() = TestEnvironment.isTest
    private val scope = kotlinx.coroutines.CoroutineScope((if (isTest) kotlinx.coroutines.Dispatchers.Unconfined else kotlinx.coroutines.Dispatchers.IO) + kotlinx.coroutines.SupervisorJob())
    private fun runIO(block: suspend () -> Unit) { if (isTest) kotlinx.coroutines.runBlocking { block() } else scope.launch { block() } }
    private val productionDemoExtensionIds = setOf("ext-legal-demo", "ext-slow", "ext-failed")
    private val productionDemoExtensionHosts = setOf("legal-demo.com", "slowaddon.org", "failedaddon.com")
    private val productionDemoExtensionNames = setOf("Public Domain Movies", "Slow Catalog Addon", "Failed Addon Engine", "Failed Scraper Engine")
    private val discoveryCatalogRefreshMutex = Mutex()
    private val installMutex = Mutex()
    private val _vaultRestoreErrors = MutableStateFlow<List<String>>(emptyList())
    val vaultRestoreErrors: StateFlow<List<String>> = _vaultRestoreErrors.asStateFlow()
    @Volatile private var cachedHomeCatalogRows: List<RecommendationRow> = emptyList()
    @Volatile private var cachedHomeCatalogRefreshMs: Long = 0L
    @Volatile private var startupCatalogRefreshScheduled = false
    private val defaultDiscoveryProvider = ExtensionProvider(
        id = DEFAULT_DISCOVERY_ID,
        name = "Cinemeta",
        url = DEFAULT_DISCOVERY_URL,
        isEnabled = true,
        health = ExtensionHealth.ACTIVE,
        priority = 0,
        manifest = ExtensionManifest(
            id = DEFAULT_DISCOVERY_ID,
            name = "Cinemeta",
            description = "Official Stremio movie and series discovery catalog",
            version = "3",
            resources = listOf("catalog", "meta"),
            types = listOf("movie", "series"),
            catalogs = listOf(
                ExtensionCatalog(type = "movie", id = "top", name = "Popular Movies"),
                ExtensionCatalog(type = "series", id = "top", name = "Popular Series")
            )
        ),
        capabilities = setOf(
            ExtensionCapability.CatalogProvider,
            ExtensionCapability.MetadataProvider
        ),
        supportedTypes = setOf("movie", "series")
    )

    private val dao: com.example.calmsource.core.database.dao.ExtensionDao
        get() {
            return try {
                val context = DatabaseProvider.context
                val db = DatabaseProvider.databaseOrNull() ?: if (context != null) DatabaseProvider.getDatabase(context) else null
                db?.extensionDao() ?: throw IllegalStateException("Database not ready")
            } catch (e: Exception) {
                if (isTest) {
                    try {
                        android.util.Log.e("ExtensionRepository", "Extension DAO unavailable, falling back to in-memory store", e)
                    } catch (_: Throwable) {}
                    fallbackDao
                } else {
                    throw IllegalStateException("Extension database unavailable", e)
                }
            }
        }

    private val fallbackDao by lazy {
        object : com.example.calmsource.core.database.dao.ExtensionDao {
            private val flow = kotlinx.coroutines.flow.MutableStateFlow<List<com.example.calmsource.core.database.entity.ExtensionProviderEntity>>(
                if (isTest) {
                    com.example.calmsource.core.model.FakeData.extensionProviders.map { it.toEntity() }
                } else {
                    emptyList()
                }
            )
            override fun getAllExtensions(): kotlinx.coroutines.flow.Flow<List<com.example.calmsource.core.database.entity.ExtensionProviderEntity>> = flow
            override fun getExtensionById(id: String) = flow.map { list -> list.find { it.id == id } }
            override fun insertExtension(extension: com.example.calmsource.core.database.entity.ExtensionProviderEntity) {
                flow.value = flow.value.filter { it.id != extension.id } + extension
            }
            override fun updateExtension(extension: com.example.calmsource.core.database.entity.ExtensionProviderEntity) {
                insertExtension(extension)
            }
            override fun deleteExtension(extension: com.example.calmsource.core.database.entity.ExtensionProviderEntity) {
                flow.value = flow.value.filter { it.id != extension.id }
            }
        }
    }

    val extensions: StateFlow<List<ExtensionProvider>> by lazy {
        extensionsEntityFlow()
            .map { list -> list.map { it.toDomain() }.sortedBy { it.priority } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun extensionsEntityFlow(): Flow<List<com.example.calmsource.core.database.entity.ExtensionProviderEntity>> {
        if (isTest) {
            return dao.getAllExtensions()
        }
        return DatabaseProvider.databaseReady.flatMapLatest { ready ->
            if (!ready) {
                flowOf(emptyList())
            } else {
                dao.getAllExtensions()
            }
        }
    }

    init {
        val backendUrl = getBackendUrl()
        if (backendUrl.isNotBlank()) {
            com.example.calmsource.core.network.BackendApiClient.baseUrl = backendUrl
        }
        runIO {
            try {
                if (!isTest) {
                    kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                        DatabaseProvider.databaseReady.first { it }
                    }
                }
                kotlinx.coroutines.coroutineScope {
                    val loaded = runCatching { kotlinx.coroutines.withTimeout(30_000L) { dao.getAllExtensions().first() } }.getOrDefault(emptyList())
                    val current = if (isTest) {
                        loaded
                    } else {
                        val demoEntries = loaded.filter { it.isProductionDemoExtension() }
                        demoEntries.forEach { dao.deleteExtension(it) }
                        loaded.filterNot { it.isProductionDemoExtension() }
                    }

                    if (current.isEmpty()) {
                        if (isTest) {
                            seedInitialExtensions()
                        }
                    } else {
                        val providersToIndex = mutableListOf<ExtensionProvider>()
                        current.forEach { entity ->
                            val provider = entity.toDomain()
                            var effectiveProvider = provider
                            val currentConfigs = com.example.calmsource.core.network.StremioAddonClient.parseConfigFromUrl(provider.url)
                            if (currentConfigs.isNotEmpty()) {
                                val declaredConfigs = getAddonConfigList(provider.manifest)
                                val effectiveConfigs = if (declaredConfigs.isNotEmpty()) declaredConfigs else currentConfigs.map { (k, _) ->
                                    StremioAddonConfig(key = k, type = "text", required = false, default = null, title = null, options = null)
                                }

                                var needsMigration = false
                                effectiveConfigs.forEach { config ->
                                    val value = currentConfigs[config.key]
                                    if (isSecretConfigKey(config) && value != null && !value.startsWith("{secret_")) {
                                        needsMigration = true
                                    }
                                }

                                if (needsMigration) {
                                    val newUrl = compileConfigUrl(provider.url, currentConfigs, effectiveConfigs, provider.id)
                                    effectiveProvider = effectiveProvider.copy(url = newUrl)
                                }
                            }

                            val configurationHealth = checkAddonHealth(effectiveProvider)
                            val finalHealth = when {
                                effectiveProvider.health == ExtensionHealth.DISABLED -> ExtensionHealth.DISABLED
                                effectiveProvider.health == ExtensionHealth.INVALID_MANIFEST -> ExtensionHealth.INVALID_MANIFEST
                                configurationHealth == ExtensionHealth.NEEDS_CONFIGURATION -> ExtensionHealth.NEEDS_CONFIGURATION
                                effectiveProvider.health == ExtensionHealth.NEEDS_CONFIGURATION && configurationHealth == ExtensionHealth.ACTIVE -> ExtensionHealth.ACTIVE
                                else -> effectiveProvider.health
                            }
                            effectiveProvider = effectiveProvider.copy(health = finalHealth)
                            if (effectiveProvider != provider) {
                                dao.updateExtension(effectiveProvider.toEntity())
                            }
                            providersToIndex += effectiveProvider
                        }
                        scheduleStartupDiscoveryCatalogRefresh()
                        scheduleExtensionHealthRecovery()
                    }
                }
            } catch (e: Exception) {
                try {
                    android.util.Log.w("ExtensionRepository", "Init migration failed: ${e.message}")
                } catch (_: Throwable) {}
            }
        }
    }

    fun getExtensions(): List<ExtensionProvider> {
        return extensions.value
    }

    suspend fun awaitExtensions(): List<ExtensionProvider> {
        if (isTest) {
            return dao.getAllExtensions()
                .first()
                .map { it.toDomain() }
                .sortedBy { it.priority }
        }
        return withTimeoutOrNull(30_000L) {
            DatabaseProvider.databaseReady.first { it }
            dao.getAllExtensions()
                .first()
                .map { it.toDomain() }
                .sortedBy { it.priority }
        } ?: getExtensions()
    }

    data class ExtensionMediaResolution(
        val mediaItem: MediaItem,
        val streamSources: List<StreamSource>,
        val streamRequestIds: List<String>,
        val errors: List<String> = emptyList(),
        val failedExtensions: List<String> = emptyList()
    )
 
    data class ExtensionMetadataResolution(
        val mediaItem: MediaItem,
        val primaryMeta: StremioMeta?,
        val metas: List<StremioMeta>
    )

    suspend fun previewExtension(url: String): ExtensionInstallResult {
        return ExtensionManifestLoader.loadManifest(url)
    }

    suspend fun restoreExtensionsFromUrls(urls: List<String>): List<String> {
        val errors = mutableListOf<String>()
        val installedUrls = getExtensions()
            .map { ExtensionInstallValidator.normalizeUrl(it.url) }
            .toMutableSet()

        for (url in urls) {
            val normalized = ExtensionInstallValidator.normalizeUrl(url)
            if (installedUrls.contains(normalized)) continue
            try {
                val previewResult = ExtensionManifestLoader.loadManifest(
                    url = url,
                    forceRefresh = true,
                    allowStaleFallback = false
                )
                val manifest = previewResult.manifest
                if (!previewResult.isSuccess || manifest == null) {
                    errors.add(
                        previewResult.error?.message
                            ?: previewResult.warnings.firstOrNull()
                            ?: "Failed to load manifest for extension"
                    )
                    continue
                }
                if (manifest.id in productionDemoExtensionIds) continue
                val installResult = confirmInstall(manifest, url, previewResult.warnings)
                if (installResult.isSuccess) {
                    installedUrls.add(normalized)
                } else {
                    errors.add(
                        installResult.error?.message
                            ?: installResult.warnings.firstOrNull()
                            ?: "Failed to install ${manifest.name}"
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                errors.add(e.message ?: "Failed to restore extension")
            }
        }
        _vaultRestoreErrors.value = errors
        return errors
    }

    suspend fun confirmInstall(manifest: ExtensionManifest, url: String, warnings: List<String> = emptyList()): ExtensionInstallResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val freshManifest = ExtensionManifestLoader.loadManifest(
            url = url,
            forceRefresh = true,
            allowStaleFallback = true
        )
        val manifestVal = (freshManifest.manifest ?: manifest).takeIf {
            it.id.isNotBlank() || it.name.isNotBlank()
        }
        if (manifestVal == null) {
            return@withContext freshManifest.copy(
                isSuccess = false,
                error = freshManifest.error ?: ExtensionError.InvalidManifest("Could not verify manifest before install")
            )
        }
        val manifestToInstall = manifestVal.copy(id = manifest.id.ifBlank { manifestVal.id })
        val installWarnings = (warnings + freshManifest.warnings).distinct().toMutableList()
        if (!freshManifest.isSuccess && freshManifest.manifest == null && !isTest) {
            installWarnings.add("Installed from preview manifest; live re-verification was unavailable.")
        }
        val providerId = manifestToInstall.id.ifBlank { "ext-${UUID.randomUUID()}" }
        // Stream providers the user explicitly installs (Torrentio, AIOStreams, …) should be queried
        // first, so give them a lower priority value than existing extensions rather than pushing
        // them to the back of the query order (bug #23). Non-stream addons keep appending at the end.
        val newCapabilities = manifestToInstall.detectCapabilities()
        val priority = if (newCapabilities.contains(ExtensionCapability.StreamProvider)) {
            (getExtensions().minOfOrNull { it.priority } ?: 100) - 10
        } else {
            (getExtensions().maxOfOrNull { it.priority } ?: 100) + 10
        }

        var finalUrl = ExtensionInstallValidator.normalizeUrl(url)
        val currentConfigs = com.example.calmsource.core.network.StremioAddonClient.parseConfigFromUrl(finalUrl)
        if (currentConfigs.isNotEmpty()) {
            val declaredConfigs = getAddonConfigList(manifestToInstall)
            val effectiveConfigs = if (declaredConfigs.isNotEmpty()) declaredConfigs else currentConfigs.map { (k, _) ->
                StremioAddonConfig(key = k, type = "text", required = false, default = null, title = null, options = null)
            }
            finalUrl = compileConfigUrl(finalUrl, currentConfigs, effectiveConfigs, providerId)
        }

        val provider = ExtensionProvider(
            id = providerId,
            name = manifestToInstall.name.ifBlank { "Unknown Extension" },
            url = finalUrl,
            isEnabled = true,
            health = ExtensionHealth.ACTIVE,
            priority = priority,
            manifest = manifestToInstall,
            permissions = listOf(ExtensionPermission.INTERNET, ExtensionPermission.READ_METADATA),
            capabilities = manifestToInstall.detectCapabilities(),
            supportedTypes = manifestToInstall.detectContentTypes()
        )

        val updatedProvider = provider.copy(health = checkAddonHealth(provider))
        if (
            updatedProvider.health == ExtensionHealth.NEEDS_CONFIGURATION &&
            getAddonConfigList(manifestToInstall).isEmpty()
        ) {
            installWarnings.add(
                "This extension requires configuration. Paste its configured manifest URL after setup."
            )
        }

        return@withContext installMutex.withLock {
            val existingWithSameId = getExtensions().find { it.id == providerId }
            if (existingWithSameId != null && existingWithSameId.url != finalUrl) {
                ExtensionInstallResult(
                    isSuccess = false,
                    error = ExtensionError.InvalidManifest(
                        "An extension with ID '$providerId' is already installed from a different URL. Remove it first or use the existing entry."
                    ),
                    warnings = installWarnings
                )
            } else {
                try {
                    dao.insertExtension(updatedProvider.toEntity())
                    queueDiscoveryCatalogRefresh(updatedProvider)
                    ExtensionInstallResult(
                        isSuccess = true,
                        manifest = manifestToInstall.copy(id = providerId),
                        warnings = installWarnings
                    )
                } catch (e: Exception) {
                    ExtensionInstallResult(
                        isSuccess = false,
                        error = ExtensionError.Unknown(e.message ?: "Failed to save extension"),
                        warnings = installWarnings
                    )
                }
            }
        }
    }

    fun getAddonConfigList(manifest: ExtensionManifest?): List<StremioAddonConfig> {
        val configJson = manifest?.rawAttributes?.get("config") ?: return emptyList()
        return try {
            configJsonParser.decodeFromString<List<StremioAddonConfig>>(configJson)
        } catch (e: Exception) {
            try {
                android.util.Log.w("ExtensionRepository", "Failed to parse addon config JSON", e)
            } catch (_: Throwable) {}
            emptyList()
        }
    }

    fun isConfigurable(manifest: ExtensionManifest?): Boolean {
        val configList = getAddonConfigList(manifest)
        if (configList.isNotEmpty()) return true
        return manifest?.behaviorHints?.get("configurable")?.toBoolean() == true
    }

    fun isConfigurationRequired(manifest: ExtensionManifest?): Boolean {
        return manifest?.behaviorHints?.get("configurationRequired")?.toBoolean() == true
    }

    fun checkAddonHealth(provider: ExtensionProvider): ExtensionHealth {
        if (!provider.isEnabled) return ExtensionHealth.DISABLED
        val manifest = provider.manifest ?: return ExtensionHealth.UNKNOWN

        val configList = getAddonConfigList(manifest)
        if (configList.isEmpty() && manifest.resources.isEmpty() && manifest.catalogs.isEmpty()) {
            return ExtensionHealth.NEEDS_CONFIGURATION
        }
        val currentConfig = com.example.calmsource.core.network.StremioAddonClient.parseConfigFromUrl(provider.url)
        if (isConfigurationRequired(manifest) && currentConfig.isEmpty()) {
            return ExtensionHealth.NEEDS_CONFIGURATION
        }
        val missingRequired = configList.filter { it.required == true }.any { config ->
            if (isSecretConfigKey(config)) {
                com.example.calmsource.core.network.ExtensionSecrets.readSecret(provider.id, config.key).isNullOrBlank()
            } else {
                currentConfig[config.key].isNullOrBlank()
            }
        }
        if (missingRequired) {
            return ExtensionHealth.NEEDS_CONFIGURATION
        }
        return ExtensionHealth.ACTIVE
    }

    fun compileConfigUrl(baseUrl: String, configValues: Map<String, String>, configs: List<StremioAddonConfig>, providerId: String): String {
        val withoutManifest = baseUrl.substringBefore("/manifest.json").trimEnd('/')
        val segments = withoutManifest.split("/")
        val originalConfigSegment = segments.lastOrNull()?.takeIf { it.contains("=") }
        val base = if (originalConfigSegment != null) {
            segments.dropLast(1).joinToString("/")
        } else {
            withoutManifest
        }
        // Preserve the addon's config-segment delimiter. Torrentio uses '|' and breaks if the
        // segment is rebuilt with '&'; most other addons use '&' (bug #3).
        val delimiter = if (originalConfigSegment?.contains("|") == true) "|" else "&"

        val configPairs = mutableListOf<String>()
        configs.forEach { config ->
            val rawVal = configValues[config.key] ?: config.default ?: ""
            if (isSecretConfigKey(config)) {
                if (rawVal.isNotEmpty()) {
                    com.example.calmsource.core.network.ExtensionSecrets.saveSecret(providerId, config.key, rawVal)
                } else {
                    com.example.calmsource.core.network.ExtensionSecrets.deleteSecret(providerId, config.key)
                }
                configPairs.add("${encodeConfigComponent(config.key)}={secret_${config.key}}")
            } else {
                configPairs.add(
                    "${encodeConfigComponent(config.key)}=${encodeConfigComponent(rawVal)}"
                )
            }
        }

        val configSegment = configPairs.joinToString(delimiter)
        return if (configSegment.isNotEmpty()) {
            "$base/$configSegment/manifest.json"
        } else {
            "$base/manifest.json"
        }
    }

    private fun encodeConfigComponent(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

    /**
     * Known sensitive Stremio addon config keys (debrid API tokens, generic secrets). Kept as an
     * allow-list plus suffix rules so we catch real secrets such as `realdebrid=` without treating
     * innocuous keys like `monkey` or `keywords` as secrets (bug #15).
     */
    private val KNOWN_SECRET_CONFIG_KEYS = setOf(
        "token", "secret", "password", "passwd", "apikey", "api_key", "auth", "authorization",
        "realdebrid", "real_debrid", "alldebrid", "all_debrid", "premiumize", "debridlink",
        "debrid_link", "offcloud", "torbox", "easydebrid", "putio", "put_io"
    )

    fun isSecretConfigKey(config: StremioAddonConfig): Boolean {
        if (config.type == "password") return true
        val key = config.key.lowercase()
        if (key in KNOWN_SECRET_CONFIG_KEYS) return true
        return key.endsWith("token") ||
                key.endsWith("secret") ||
                key.endsWith("password") ||
                key.endsWith("apikey") ||
                key.endsWith("api_key") ||
                key.endsWith("_key")
    }

    /**
     * Persists configuration for an installed extension and returns whether it succeeded so the UI
     * can surface a failure instead of silently closing the dialog (bug #22).
     */
    suspend fun saveConfiguration(
        providerId: String,
        configValues: Map<String, String>
    ): ExtensionInstallResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val provider = getExtensions().find { it.id == providerId }
            ?: return@withContext ExtensionInstallResult(isSuccess = false, error = ExtensionError.Unknown("Extension not found"))
        val manifest = provider.manifest
            ?: return@withContext ExtensionInstallResult(isSuccess = false, error = ExtensionError.Unknown("Extension manifest unavailable"))
        val configs = getAddonConfigList(manifest)

        if (configs.isEmpty() && configValues.isNotEmpty()) {
            return@withContext ExtensionInstallResult(
                isSuccess = false,
                error = ExtensionError.InvalidManifest("No configurable fields detected in manifest")
            )
        }

        val newUrl = compileConfigUrl(provider.url, configValues, configs, providerId)

        // Recalculate health
        val tempProvider = provider.copy(url = newUrl, health = ExtensionHealth.ACTIVE)
        val finalHealth = if (provider.health == ExtensionHealth.INVALID_MANIFEST) {
            ExtensionHealth.INVALID_MANIFEST
        } else {
            checkAddonHealth(tempProvider)
        }

        val updated = provider.copy(
            url = newUrl,
            health = finalHealth
        )
        return@withContext try {
            dao.updateExtension(updated.toEntity())
            ExtensionInstallResult(isSuccess = true, manifest = manifest.copy(id = providerId))
        } catch (e: Exception) {
            ExtensionInstallResult(
                isSuccess = false,
                error = ExtensionError.Unknown(e.message ?: "Failed to save configuration")
            )
        }
    }

    fun toggleExtension(id: String, isEnabled: Boolean) {
        runIO {
            if (!isTest) {
                kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                    DatabaseProvider.databaseReady.first { it }
                }
            }
            val provider = getExtensions().find { it.id == id } ?: return@runIO
            val tempProvider = provider.copy(isEnabled = isEnabled)
            val finalHealth = if (!isEnabled) {
                ExtensionHealth.DISABLED
            } else {
                if (provider.health == ExtensionHealth.INVALID_MANIFEST) {
                    ExtensionHealth.INVALID_MANIFEST
                } else {
                    checkAddonHealth(tempProvider)
                }
            }
            val updated = tempProvider.copy(health = finalHealth)
            dao.updateExtension(updated.toEntity())
        }
    }

    fun removeExtension(id: String) {
        runIO {
            if (!isTest) {
                kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                    DatabaseProvider.databaseReady.first { it }
                }
            }
            val provider = getExtensions().find { it.id == id } ?: return@runIO
            dao.deleteExtension(provider.toEntity())
            com.example.calmsource.core.network.ExtensionSecrets.clearSecrets(id)
        }
    }

    fun updatePriority(id: String, priority: Int) {
        runIO {
            val provider = getExtensions().find { it.id == id } ?: return@runIO
            val updated = provider.copy(priority = priority)
            dao.updateExtension(updated.toEntity())
        }
    }

    fun updateHealth(id: String, health: ExtensionHealth) {
        runIO {
            val provider = getExtensions().find { it.id == id } ?: return@runIO
            if (provider.health == ExtensionHealth.DISABLED) {
                if (health != ExtensionHealth.ACTIVE) return@runIO
            }
            val currentHealth = checkAddonHealth(provider)
            val finalHealth = if (health == ExtensionHealth.ACTIVE && currentHealth == ExtensionHealth.ACTIVE) {
                ExtensionHealth.ACTIVE
            } else if (currentHealth == ExtensionHealth.DISABLED) {
                ExtensionHealth.DISABLED
            } else {
                health
            }
            val updated = provider.copy(health = finalHealth)
            dao.updateExtension(updated.toEntity())
        }
    }

    suspend fun fetchCatalogs(extensionId: String): List<MediaItem> = supervisorScope {
        val provider = awaitExtensions().find { it.id == extensionId } ?: return@supervisorScope emptyList<MediaItem>()
        val catalogs = provider.manifest?.catalogs ?: return@supervisorScope emptyList<MediaItem>()
        val resolvedBase = com.example.calmsource.core.network.StremioAddonClient.resolveUrl(provider.url, provider.id).removeSuffix("/manifest.json")

        val jobs = catalogs.map { catalog ->
            async(Dispatchers.IO) {
                val request = catalog.toBrowseRequest() ?: return@async emptyList<MediaItem>()
                val res = com.example.calmsource.core.network.StremioAddonClient.getCatalog(
                    resolvedBase,
                    catalog.type,
                    catalog.id,
                    request.extraArgs,
                    provider.id,
                    provider.requestTimeoutMs()
                )
                if (res is com.example.calmsource.core.network.StremioResult.Success) {
                    res.data.metas.orEmpty().mapNotNull { meta ->
                        if (meta.id.isBlank() || meta.name.isBlank()) return@mapNotNull null
                        MediaItem(
                            id = meta.id,
                            title = meta.name,
                            type = if (meta.effectiveType(catalog.type) == "series") MediaType.SHOW else MediaType.MOVIE,
                            overview = meta.description ?: "Stremio catalog result",
                            posterUrl = meta.poster,
                            backdropUrl = meta.background,
                            releaseDate = meta.releaseInfo,
                            rating = meta.imdbRating?.toDoubleOrNull(),
                            externalIds = meta.toExternalIds()
                        )
                    }
                } else {
                    emptyList()
                }
            }
        }
        jobs.awaitAll().flatten().distinctBy { it.id }
    }

    /**
     * Refreshes every enabled Stremio catalog and waits until the resulting metadata has
     * been written to the local discovery index. Home can safely reload immediately after
     * this returns instead of racing the background install/startup refresh.
     */
    suspend fun refreshDiscoveryCatalogs(): Int {
        if (isTest) return 0
        val providers = awaitExtensions()
            .filterNot { provider -> provider.isProductionDemoExtension() }
        return refreshDiscoveryCatalogs(providers).indexedItemCount
    }

    suspend fun refreshDiscoveryCatalogHomeRows(forceRefresh: Boolean = false): List<RecommendationRow> {
        return ensureDiscoveryCatalogHomeRows(forceRefresh)
    }

    fun getCachedDiscoveryCatalogHomeRows(): List<RecommendationRow> = cachedHomeCatalogRows

    suspend fun ensureDiscoveryCatalogHomeRows(forceRefresh: Boolean = false): List<RecommendationRow> {
        if (isTest) return emptyList()
        val now = System.currentTimeMillis()
        if (!forceRefresh &&
            cachedHomeCatalogRows.isNotEmpty() &&
            now - cachedHomeCatalogRefreshMs < CATALOG_HOME_TTL_MS
        ) {
            return cachedHomeCatalogRows
        }
        return discoveryCatalogRefreshMutex.withLock {
            val recheck = System.currentTimeMillis()
            if (!forceRefresh &&
                cachedHomeCatalogRows.isNotEmpty() &&
                recheck - cachedHomeCatalogRefreshMs < CATALOG_HOME_TTL_MS
            ) {
                return@withLock cachedHomeCatalogRows
            }
            val providers = awaitExtensions()
                .filterNot { provider -> provider.isProductionDemoExtension() }
            val rows = refreshDiscoveryCatalogs(providers).rows
            cachedHomeCatalogRows = rows
            cachedHomeCatalogRefreshMs = System.currentTimeMillis()
            rows
        }
    }

    suspend fun refreshDefaultDiscoveryHomeRows(): List<RecommendationRow> {
        if (isTest) return emptyList()
        return refreshDiscoveryCatalogs(listOf(defaultDiscoveryProvider)).rows
    }

    fun refreshDiscoveryIndex(extensionId: String) {
        val provider = getExtensions().find { it.id == extensionId } ?: return
        queueDiscoveryCatalogRefresh(provider)
    }

    suspend fun resolveStream(extensionId: String, type: String, streamId: String): List<StreamSource> {
        val provider = getExtensions().find { it.id == extensionId } ?: return emptyList()
        val resolvedBase = com.example.calmsource.core.network.StremioAddonClient.resolveUrl(provider.url, provider.id).removeSuffix("/manifest.json")
        val res = com.example.calmsource.core.network.StremioAddonClient.getStreams(
            resolvedBase,
            type,
            streamId,
            provider.id,
            provider.requestTimeoutMs()
        )
        if (res is com.example.calmsource.core.network.StremioResult.Success) {
            return res.data.streams.orEmpty()
                .map { stream ->
                    WatchOptionResolver.mapStremioStreamToSource(
                        stream = stream,
                        providerId = provider.id,
                        providerName = provider.name,
                        mediaId = streamId
                    )
                }
                .filter { it.url.isNotBlank() }
        }
        return emptyList()
    }

    suspend fun getSeriesMetadata(
        mediaItem: MediaItem,
        installedProviders: List<ExtensionProvider> = getExtensions()
    ): StremioMeta? = withContext(Dispatchers.IO) {
        refreshMediaMetadata(mediaItem, installedProviders).primaryMeta
    }

    suspend fun refreshMediaMetadata(
        mediaItem: MediaItem,
        installedProviders: List<ExtensionProvider> = getExtensions()
    ): ExtensionMetadataResolution = withContext(Dispatchers.IO) {
        val activeProviders = installedProviders.filter {
            it.isEnabled &&
                it.health != ExtensionHealth.NEEDS_CONFIGURATION &&
                it.health != ExtensionHealth.INVALID_MANIFEST
        }
        val type = mediaItem.toStremioType()
        val metadataProviders = withDefaultDiscoveryProvider(activeProviders)
        val metas = fetchMetadataForStreamResolution(mediaItem, metadataProviders, type)
        var primaryMeta = if (type == "series") {
            metas.firstOrNull { it.videos.orEmpty().isNotEmpty() } ?: metas.firstOrNull()
        } else {
            metas.firstOrNull()
        }

        if (isBackendEnrichmentEnabled() &&
            com.example.calmsource.core.network.BackendApiClient.canAttemptRequest() &&
            primaryMeta != null
        ) {
            try {
                val backendRes = com.example.calmsource.core.network.BackendApiClient.getMeta(type, primaryMeta.id)
                val backendMeta = backendRes?.meta
                if (backendMeta != null) {
                    primaryMeta = primaryMeta.copy(
                        logo = backendMeta.logo ?: primaryMeta.logo,
                        rtRating = backendMeta.rtRating,
                        metascore = backendMeta.metascore,
                        simklRating = backendMeta.enrichment?.simklRating,
                        malRating = backendMeta.enrichment?.malRating,
                        studios = backendMeta.enrichment?.studios,
                        videos = mergeVideos(primaryMeta.videos, backendMeta.videos)
                    )
                }
            } catch (e: Exception) {
                // Fail silently
            }
        }

        val resolvedMediaItem = primaryMeta?.let { meta ->
            mediaItem.copy(
                overview = meta.description ?: mediaItem.overview,
                posterUrl = meta.poster ?: mediaItem.posterUrl,
                backdropUrl = meta.background ?: mediaItem.backdropUrl,
                releaseDate = meta.releaseInfo ?: mediaItem.releaseDate,
                rating = meta.imdbRating?.toDoubleOrNull() ?: mediaItem.rating,
                externalIds = buildMap {
                    putAll(mediaItem.externalIds)
                    put("stremio", meta.id)
                    meta.imdbId?.takeIf { it.isNotBlank() }?.let { put("imdb", it) }
                    if (meta.id.startsWith("tt") && meta.id.drop(2).all(Char::isDigit)) {
                        put("imdb", meta.id)
                    }
                }
            )
        } ?: mediaItem

        ExtensionMetadataResolution(
            mediaItem = resolvedMediaItem,
            primaryMeta = primaryMeta,
            metas = if (primaryMeta != null) listOf(primaryMeta) else metas
        )
    }

    private fun mergeVideos(
        addonVideos: List<StremioVideo>?,
        backendVideos: List<com.example.calmsource.core.model.BackendVideo>?
    ): List<StremioVideo>? {
        if (addonVideos == null) return null
        if (backendVideos == null) return addonVideos
        val backendVideoMap = backendVideos.associateBy { it.id }
        val backendBySeasonEpisode = backendVideos.associateBy { "${it.season}:${it.episode}" }
        return addonVideos.map { addonVideo ->
            val match = backendVideoMap[addonVideo.id]
                ?: backendBySeasonEpisode["${addonVideo.season}:${addonVideo.episode}"]
            if (match != null) {
                addonVideo.copy(
                    title = match.title?.takeIf { it.isNotBlank() } ?: addonVideo.resolvedTitle(),
                    overview = match.overview?.takeIf { it.isNotBlank() } ?: addonVideo.overview,
                    thumbnail = match.thumbnail?.takeIf { it.isNotBlank() } ?: addonVideo.thumbnail,
                    skipTimes = match.skipTimes?.map { skip ->
                        StremioSkipTime(
                            interval = StremioSkipInterval(skip.interval.start_time, skip.interval.end_time),
                            skipType = skip.skip_type
                        )
                    } ?: addonVideo.skipTimes,
                )
            } else {
                addonVideo
            }
        }
    }

    fun lookupMediaStreams(
        mediaItem: MediaItem,
        installedProviders: List<ExtensionProvider> = getExtensions(),
        episodeId: String? = null
    ): Flow<ExtensionMediaResolution> = channelFlow {
        val activeProviders = installedProviders.filter {
            it.isEnabled &&
                it.health != ExtensionHealth.NEEDS_CONFIGURATION &&
                it.health != ExtensionHealth.INVALID_MANIFEST &&
                it.health != ExtensionHealth.FAILED
        }
        val type = mediaItem.toStremioType()
        val metadataProviders = withDefaultDiscoveryProvider(activeProviders)
        val metaResults = fetchMetadataForStreamResolution(mediaItem, metadataProviders, type)
        val resolvedMediaItem = metaResults.firstOrNull()?.let { meta ->
            mediaItem.copy(
                overview = meta.description ?: mediaItem.overview,
                posterUrl = meta.poster ?: mediaItem.posterUrl,
                backdropUrl = meta.background ?: mediaItem.backdropUrl,
                releaseDate = meta.releaseInfo ?: mediaItem.releaseDate,
                rating = meta.imdbRating?.toDoubleOrNull() ?: mediaItem.rating,
                externalIds = buildMap {
                    putAll(mediaItem.externalIds)
                    put("stremio", meta.id)
                    meta.imdbId?.takeIf { it.isNotBlank() }?.let { put("imdb", it) }
                    if (meta.id.startsWith("tt") && meta.id.drop(2).all(Char::isDigit)) {
                        put("imdb", meta.id)
                    }
                }
            )
        } ?: mediaItem
        val requestIds = buildStreamRequestIds(mediaItem, metaResults, type, episodeId)
        if (requestIds.isEmpty()) {
            send(ExtensionMediaResolution(resolvedMediaItem, emptyList(), emptyList()))
            return@channelFlow
        }

        val accumulatedErrors = mutableListOf<String>()
        val accumulatedFailed = mutableListOf<String>()
        val mutex = kotlinx.coroutines.sync.Mutex()

        val streamProviders = activeProviders.filter { it.supportsResource("stream", type) }
        if (streamProviders.isEmpty()) {
            send(ExtensionMediaResolution(resolvedMediaItem, emptyList(), requestIds))
            return@channelFlow
        }

        // Emit initial resolved state
        send(ExtensionMediaResolution(resolvedMediaItem, emptyList(), requestIds))

        kotlinx.coroutines.withTimeout(2 * EXTENSION_REQUEST_TIMEOUT_MS) {
        supervisorScope {
            streamProviders.map { provider ->
                launch(Dispatchers.IO) {
                    var errorMsg: String? = null
                    var failedName: String? = null
                    var wasTimeout = false
                    val providerSources = try {
                        val timeoutMs = provider.requestTimeoutMs()
                        val resolvedBase = com.example.calmsource.core.network.StremioAddonClient
                            .resolveUrl(provider.url, provider.id)
                            .removeSuffix("/manifest.json")
                        var resolvedStreams: List<StreamSource>? = null
                        // Wrap the entire multi-request loop in a single timeout so
                        // a cascade of slow requests doesn't stall the provider slot.
                        kotlinx.coroutines.withTimeout(timeoutMs) {
                            for (requestId in requestIds) {
                                val streamRes = com.example.calmsource.core.network.StremioAddonClient
                                    .getStreams(resolvedBase, type, requestId, provider.id, timeoutMs)
                                if (streamRes is com.example.calmsource.core.network.StremioResult.Success) {
                                    val mapped = (streamRes.data.streams ?: emptyList())
                                        .map { stream ->
                                            WatchOptionResolver.mapStremioStreamToSource(
                                                stream = stream,
                                                providerId = provider.id,
                                                providerName = provider.name,
                                                mediaId = mediaItem.id
                                            )
                                        }
                                        .filter { it.url.isNotBlank() }
                                    if (mapped.isNotEmpty()) {
                                        resolvedStreams = mapped
                                        break
                                    }
                                } else if (streamRes is com.example.calmsource.core.network.StremioResult.Failure) {
                                    errorMsg = streamFailureMessage(provider.name, streamRes.error)
                                    failedName = provider.name
                                }
                            }
                        }
                        resolvedStreams ?: emptyList()
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        errorMsg = "Extension '${provider.name}' failed: Request timed out"
                        failedName = provider.name
                        wasTimeout = true
                        emptyList()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        errorMsg = streamFailureMessage(provider.name, e)
                        failedName = provider.name
                        emptyList()
                    }

                    val dedupedSources = providerSources.deduplicateStreamSources()

                    if (dedupedSources.isNotEmpty()) {
                        errorMsg = null
                        failedName = null
                        wasTimeout = false
                    }

                    // Feed the stream-resolution outcome into provider health so a consistently
                    // failing stream provider stops showing ACTIVE in settings (bug #20). Reuses the
                    // same signal delegate the search/catalog paths use.
                    if (failedName != null) {
                        com.example.calmsource.core.network.StremioAddonClient.recordSignalDelegate
                            ?.invoke(provider.id, provider.url, wasTimeout, errorMsg ?: "Stream lookup failed")
                    }

                    // Accumulate error/failed names for the warning banner
                    mutex.withLock {
                        if (errorMsg != null) {
                            accumulatedErrors.add(errorMsg)
                        }
                        if (failedName != null) {
                            accumulatedFailed.add(failedName)
                        }
                    }
                    // Emit this provider's results immediately — per-provider delta,
                    // not the full accumulated list. The collector merges incrementally.
                    send(ExtensionMediaResolution(
                        mediaItem = resolvedMediaItem,
                        streamSources = dedupedSources,
                        streamRequestIds = requestIds,
                        errors = accumulatedErrors.toList(),
                        failedExtensions = accumulatedFailed.toList()
                    ))
                }
            }
        }
        }
        // All providers launched; the channelFlow stays open until all children finish.
    }

    // Sync logic removed to prevent polluting FakeData.

    private fun scheduleStartupDiscoveryCatalogRefresh() {
        if (isTest || startupCatalogRefreshScheduled) return
        startupCatalogRefreshScheduled = true
        scope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(1_500L)
                ensureDiscoveryCatalogHomeRows()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Startup catalog refresh must not block app launch.
            }
        }
    }

    private fun scheduleExtensionHealthRecovery() {
        if (isTest) return
        scope.launch(Dispatchers.IO) {
            while (true) {
                kotlinx.coroutines.delay(HEALTH_RECOVERY_INTERVAL_MS)
                try {
                    val unhealthy = getExtensions().filter {
                        it.health == ExtensionHealth.FAILED || it.health == ExtensionHealth.SLOW
                    }
                    for (provider in unhealthy) {
                        try {
                            val result = ExtensionManifestLoader.loadManifest(
                                url = provider.url,
                                forceRefresh = true,
                                allowStaleFallback = false
                            )
                            if (result.isSuccess && result.manifest != null) {
                                val recovered = provider.copy(manifest = result.manifest)
                                val updated = recovered.copy(health = checkAddonHealth(recovered))
                                dao.updateExtension(updated.toEntity())
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Throwable) {
                            // Extension still unhealthy, will retry next cycle
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // Recovery check failed, will retry next cycle
                }
            }
        }
    }

    private fun queueDiscoveryCatalogRefresh(provider: ExtensionProvider) {
        if (isTest) return
        if (!provider.isEnabled) return
        if (provider.health == ExtensionHealth.NEEDS_CONFIGURATION || provider.health == ExtensionHealth.INVALID_MANIFEST) return
        val manifest = provider.manifest ?: return
        if (manifest.catalogs.isEmpty()) return

        scope.launch(Dispatchers.IO) {
            try {
                cachedHomeCatalogRefreshMs = 0L
                val fresh = refreshDiscoveryCatalogs(listOf(provider))
                if (fresh.rows.isNotEmpty()) {
                    cachedHomeCatalogRows = (cachedHomeCatalogRows + fresh.rows).distinctBy { it.rowType }
                    cachedHomeCatalogRefreshMs = System.currentTimeMillis()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
                // A failed addon must not prevent other app startup work.
            }
        }
    }

    private suspend fun refreshDiscoveryCatalogs(
        providers: List<ExtensionProvider>
    ): CatalogRefreshResult {
        return try {
            kotlinx.coroutines.withTimeout(30_000L) {
                discoveryCatalogRefreshMutex.withLock {
                    val activeProviders = providers.filter { provider ->
                        provider.isEnabled &&
                            provider.health != ExtensionHealth.NEEDS_CONFIGURATION &&
                            provider.health != ExtensionHealth.INVALID_MANIFEST &&
                            provider.manifest?.catalogs?.isNotEmpty() == true
                    }
                    if (activeProviders.isEmpty()) return@withLock CatalogRefreshResult()

                    val catalogResults = supervisorScope {
                        activeProviders.flatMap { provider ->
                            provider.manifest?.catalogs.orEmpty()
                                .filter { catalog -> provider.supportsResource("catalog", catalog.type) }
                                .mapNotNull { catalog ->
                                    val request = catalog.toBrowseRequest() ?: return@mapNotNull null
                                    async(Dispatchers.IO) {
                                        try {
                                            kotlinx.coroutines.withTimeout(provider.requestTimeoutMs()) {
                                                fetchDiscoveryCatalog(provider, catalog, request)
                                            }
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            throw e
                                        } catch (_: Throwable) {
                                            CatalogFetchResult(provider, catalog, emptyList())
                                        }
                                    }
                                }
                        }.awaitAll()
                    }

                    val indexedItems = catalogResults
                        .flatMap { it.items }
                        .map { it.toDiscoveryMediaItem() }
                        .distinctBy { "${it.type}:${it.id}" }

                    val rows = catalogResults
                        .mapNotNull { result -> result.toRecommendationRow() }
                        .distinctBy { it.rowType }

                    if (indexedItems.isNotEmpty()) {
                        DiscoveryEngine.ingestStremioItems(indexedItems)
                    }
                    CatalogRefreshResult(
                        rows = rows,
                        indexedItemCount = indexedItems.size
                    )
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            CatalogRefreshResult()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            CatalogRefreshResult()
        }
    }

    private suspend fun fetchDiscoveryCatalog(
        provider: ExtensionProvider,
        catalog: ExtensionCatalog,
        request: CatalogBrowseRequest
    ): CatalogFetchResult {
        val resolvedBase = com.example.calmsource.core.network.StremioAddonClient
            .resolveUrl(provider.url, provider.id)
            .removeSuffix("/manifest.json")
        val response = com.example.calmsource.core.network.StremioAddonClient.getCatalog(
            resolvedBaseUrl = resolvedBase,
            type = catalog.type,
            catalogId = catalog.id,
            extraArgs = request.extraArgs,
            providerId = provider.id,
            timeoutMs = provider.requestTimeoutMs()
        )
        val items = if (response is com.example.calmsource.core.network.StremioResult.Success) {
            response.data.metas.orEmpty()
                .asSequence()
                .filter { it.id.isNotBlank() && it.name.isNotBlank() }
                .map { meta -> CatalogMediaItem(provider, catalog, meta) }
                .distinctBy { "${it.type}:${it.meta.id}" }
                .take(HOME_CATALOG_ITEM_LIMIT)
                .toList()
        } else {
            emptyList()
        }
        return CatalogFetchResult(provider, catalog, items)
    }

    private fun ExtensionCatalog.toBrowseRequest(): CatalogBrowseRequest? {
        val requiredExtras = extra.orEmpty().filter { it.isRequired }
        if (requiredExtras.isEmpty()) return CatalogBrowseRequest(extraArgs = null)

        val pairs = requiredExtras.map { extra ->
            val options = extra.options.orEmpty()
            val value = when {
                extra.name == "skip" -> "0"
                extra.name == "search" -> return null
                options.isNotEmpty() -> options.first()
                else -> return null
            }
            "${encodeCatalogExtra(extra.name)}=${encodeCatalogExtra(value)}"
        }
        return CatalogBrowseRequest(extraArgs = pairs.joinToString("&"))
    }

    private fun encodeCatalogExtra(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

    private fun StremioMetaPreview.effectiveType(fallbackType: String): String {
        return type.ifBlank { fallbackType }.lowercase()
    }

    private fun StremioMetaPreview.toExternalIds(): Map<String, String> {
        return buildMap {
            imdbId?.takeIf { it.isNotBlank() }?.let { put("imdb", it) }
            if (id.startsWith("tt") && id.drop(2).all(Char::isDigit)) {
                put("imdb", id)
            }
            put("stremio", id)
        }
    }

    private fun CatalogMediaItem.toDiscoveryMediaItem(): DiscoveryMediaItem {
        return DiscoveryMediaItem(
            id = meta.id,
            type = type,
            title = meta.name,
            overview = meta.description,
            posterUrl = meta.poster ?: meta.logo,
            rating = meta.imdbRating?.toDoubleOrNull(),
            releaseYear = meta.releaseInfo.toReleaseYear(),
            genres = meta.genres.orEmpty(),
            externalIds = meta.toExternalIds(),
            source = provider.id
        )
    }

    private fun CatalogFetchResult.toRecommendationRow(): RecommendationRow? {
        if (items.isEmpty()) return null
        val rowTitle = catalog.name.ifBlank {
            if (catalog.type == "series") "Series" else "Movies"
        }
        val isDefaultDiscovery = provider.id == DEFAULT_DISCOVERY_ID
        return RecommendationRow(
            title = if (isDefaultDiscovery) rowTitle else "${provider.name} - $rowTitle",
            rowType = "extension:${provider.id}:${catalog.type}:${catalog.id}",
            items = items.map { item ->
                RecommendationItem(
                    id = item.meta.id,
                    type = item.type,
                    title = item.meta.name,
                    score = 0.0,
                    reason = if (isDefaultDiscovery) "Popular now" else "From ${provider.name}",
                    scoreBreakdown = ScoreBreakdown(
                        reasons = listOf("Catalog: $rowTitle")
                    ),
                    subtitle = if (item.type == "series") "Series" else "Movie",
                    posterUrl = item.meta.poster ?: item.meta.logo,
                    backdropUrl = item.meta.background,
                    genres = item.meta.genres.orEmpty(),
                    source = provider.id,
                    externalIds = item.meta.toExternalIds()
                )
            }.toImmutableList()
        )
    }

    private data class CatalogBrowseRequest(
        val extraArgs: String?
    )

    private data class CatalogMediaItem(
        val provider: ExtensionProvider,
        val catalog: ExtensionCatalog,
        val meta: StremioMetaPreview
    ) {
        val type: String = meta.effectiveType(catalog.type)
    }

    private data class CatalogFetchResult(
        val provider: ExtensionProvider,
        val catalog: ExtensionCatalog,
        val items: List<CatalogMediaItem>
    )

    private data class CatalogRefreshResult(
        val rows: List<RecommendationRow> = emptyList(),
        val indexedItemCount: Int = 0
    )

    private fun ExtensionProvider.isProductionDemoExtension(): Boolean {
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()
        return id in productionDemoExtensionIds ||
            name in productionDemoExtensionNames ||
            host in productionDemoExtensionHosts
    }

    private fun withDefaultDiscoveryProvider(
        providers: List<ExtensionProvider>
    ): List<ExtensionProvider> {
        if (isTest) return providers
        val hasCinemeta = providers.any { provider ->
            provider.id == DEFAULT_DISCOVERY_ID ||
                provider.manifest?.id == DEFAULT_DISCOVERY_ID
        }
        return if (hasCinemeta) providers else listOf(defaultDiscoveryProvider) + providers
    }

    internal fun defaultDiscoveryProviderForTest(): ExtensionProvider = defaultDiscoveryProvider

    private fun String?.toReleaseYear(): Int? {
        return this?.let { Regex("""\b(19|20)\d{2}\b""").find(it)?.value?.toIntOrNull() }
    }


    private data class MetadataResult(
        val provider: ExtensionProvider,
        val meta: StremioMeta
    )

    private suspend fun fetchMetadataForStreamResolution(
        mediaItem: MediaItem,
        providers: List<ExtensionProvider>,
        type: String
    ): List<StremioMeta> {
        val candidateIds = mediaItem.stremioBaseIds()
        return supervisorScope {
            providers
                .filter { it.supportsResource("meta", type) }
                .map { provider ->
                    async(Dispatchers.IO) {
                        try {
                            val resolvedBase = com.example.calmsource.core.network.StremioAddonClient
                                .resolveUrl(provider.url, provider.id)
                                .removeSuffix("/manifest.json")
                            val meta = candidateIds.firstNotNullOfOrNull { candidateId ->
                                val metaRes = com.example.calmsource.core.network.StremioAddonClient.getMeta(
                                    resolvedBaseUrl = resolvedBase,
                                    type = type,
                                    id = candidateId,
                                    providerId = provider.id,
                                    timeoutMs = provider.requestTimeoutMs()
                                )
                                (metaRes as? com.example.calmsource.core.network.StremioResult.Success)
                                    ?.data
                                    ?.meta
                            }
                            meta?.let { MetadataResult(provider, it) }
                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                            null
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            null
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .sortedBy { it.provider.priority }
                .map { it.meta }
        }
    }

    internal fun buildStreamRequestIds(
        mediaItem: MediaItem,
        metas: List<StremioMeta>,
        type: String,
        episodeId: String? = null
    ): List<String> {
        val baseIds = linkedSetOf<String>()
        baseIds.addAll(mediaItem.stremioBaseIds())
        metas.forEach { meta ->
            meta.imdbId?.trim()?.takeIf { it.isNotEmpty() }?.let(baseIds::add)
            meta.id.trim().takeIf { it.isNotEmpty() }?.let(baseIds::add)
        }

        val requestIds = linkedSetOf<String>()
        if (type == "series") {
            if (episodeId != null && episodeId.isNotBlank()) {
                requestIds.add(episodeId)
                val episodeSuffix = episodeId.toEpisodeSuffix()
                if (episodeSuffix != null) {
                    baseIds.forEach { baseId -> requestIds.add("$baseId:$episodeSuffix") }
                } else if (!episodeId.contains(":")) {
                    baseIds.forEach { baseId -> requestIds.add("$baseId:$episodeId") }
                }
            } else {
                metas.asSequence()
                    .flatMap { it.videos.orEmpty().asSequence() }
                    .mapNotNull { video -> video.toStreamRequestId(baseIds.firstOrNull()) }
                    .forEach { requestIds.add(it) }
                baseIds.forEach { baseId ->
                    if (baseId.contains(":")) {
                        requestIds.add(baseId)
                    } else {
                        requestIds.add("$baseId:1:1")
                    }
                }
                requestIds.addAll(baseIds)
            }
        } else {
            requestIds.addAll(baseIds)
        }

        return requestIds
            .filter { it.length <= 160 && !it.contains('/') && !it.contains('\\') }
            .take(8)
    }

    private fun MediaItem.stremioBaseIds(): List<String> {
        val ids = linkedSetOf<String>()

        fun add(id: String?) {
            val candidate = id?.trim().orEmpty()
            if (
                candidate.isNotEmpty() &&
                candidate.length <= 120 &&
                !candidate.contains('/') &&
                !candidate.contains('\\')
            ) {
                ids.add(candidate)
            }
        }

        add(externalIds["imdb"])
        add(externalIds["stremio"])
        add(id)

        externalIds.forEach { (namespace, value) ->
            val normalizedNamespace = namespace.trim().lowercase()
            val normalizedValue = value.trim()
            if (normalizedNamespace.isEmpty() || normalizedValue.isEmpty()) return@forEach
            if (
                normalizedNamespace != "imdb" &&
                normalizedNamespace != "stremio" &&
                !normalizedValue.contains(":")
            ) {
                add("$normalizedNamespace:$normalizedValue")
            }
            add(normalizedValue)
        }
        return ids.take(8)
    }

    private fun String.toEpisodeSuffix(): String? {
        val match = Regex("(?:^|:)(\\d+):(\\d+)$").find(trim()) ?: return null
        return "${match.groupValues[1]}:${match.groupValues[2]}"
    }

    private fun StremioVideo.toStreamRequestId(fallbackBaseId: String?): String? {
        val directId = id?.trim()?.takeIf { it.isNotEmpty() }
        if (directId != null) return directId
        val baseId = fallbackBaseId?.takeIf { it.isNotBlank() } ?: return null
        val seasonNumber = season ?: return null
        val episodeNumber = episode ?: return null
        return "$baseId:$seasonNumber:$episodeNumber"
    }

    private fun MediaItem.toStremioType(): String = if (type == MediaType.SHOW) "series" else "movie"

    private fun ExtensionProvider.requestTimeoutMs(): Long {
        return EXTENSION_REQUEST_TIMEOUT_MS
    }

    private fun ExtensionProvider.supportsResource(resourceName: String, type: String): Boolean {
        val manifest = manifest ?: return false
        val capability = when (resourceName) {
            "meta" -> ExtensionCapability.MetadataProvider
            "stream" -> ExtensionCapability.StreamProvider
            "subtitles" -> ExtensionCapability.SubtitleProvider
            "catalog" -> ExtensionCapability.CatalogProvider
            else -> ExtensionCapability.UnsupportedResource
        }
        return (capabilities.contains(capability) || manifest.resources.contains(resourceName)) &&
            manifest.isResourceSupported(resourceName, type)
    }

    private fun streamFailureMessage(providerName: String, error: com.example.calmsource.core.model.ExtensionError): String {
        val detail = when (error) {
            is com.example.calmsource.core.model.ExtensionError.Timeout ->
                "Request timed out"
            is com.example.calmsource.core.model.ExtensionError.NetworkError ->
                "Network error"
            is com.example.calmsource.core.model.ExtensionError.ParseError ->
                "Could not parse stream response"
            is com.example.calmsource.core.model.ExtensionError.InvalidManifest ->
                "Invalid addon manifest"
            is com.example.calmsource.core.model.ExtensionError.PermissionDenied ->
                "Permission denied"
            is com.example.calmsource.core.model.ExtensionError.Unknown ->
                error.message.ifBlank { "Unknown error" }
        }
        return "Extension '$providerName' failed: $detail"
    }

    private fun streamFailureMessage(providerName: String, throwable: Throwable): String {
        return when (throwable) {
            is kotlinx.coroutines.TimeoutCancellationException ->
                "Extension '$providerName' failed: Request timed out"
            else ->
                "Extension '$providerName' failed: ${throwable.message ?: throwable.javaClass.simpleName}"
        }
    }

    private fun List<StreamSource>.deduplicateStreamSources(): List<StreamSource> {
        val seenIds = mutableSetOf<String>()
        val seenUrls = mutableSetOf<String>()
        val deduped = mutableListOf<StreamSource>()
        for (source in this) {
            val urlKey = if (source.url.startsWith("magnet:")) {
                source.url.substringAfter("btih:")
            } else {
                source.url
            }
            if (source.id in seenIds || urlKey in seenUrls) continue
            seenIds += source.id
            seenUrls += urlKey
            deduped += source
        }
        return deduped
    }

    private fun ExtensionProviderEntity.isProductionDemoExtension(): Boolean {
        if (id in productionDemoExtensionIds || name in productionDemoExtensionNames) return true
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()
        return host in productionDemoExtensionHosts
    }

    private suspend fun seedInitialExtensions() {
        val legalDemoManifest = ExtensionManifest(
            id = "ext-legal-demo",
            name = "Public Domain Movies",
            description = "Legal public domain content provider",
            version = "1.0.0",
            resources = listOf("catalog", "search", "stream"),
            types = listOf("movie", "series")
        )
        val slowManifest = ExtensionManifest(
            id = "ext-slow",
            name = "Slow Catalog Addon",
            description = "Highly-latent metadata catalog indexing",
            version = "1.0.0",
            resources = listOf("catalog", "search"),
            types = listOf("movie", "series")
        )
        val failedManifest = ExtensionManifest(
            id = "ext-failed",
            name = "Failed Addon Engine",
            description = "Offline uncontactable source registry",
            version = "1.0.0",
            resources = listOf("catalog", "search"),
            types = listOf("movie", "series")
        )

        val seedList = listOf(
            ExtensionProvider("ext-legal-demo", "Public Domain Movies", "https://legal-demo.com/manifest.json", isEnabled = true, ExtensionHealth.ACTIVE, 10, legalDemoManifest),
            ExtensionProvider("ext-slow", "Slow Catalog Addon", "https://slowaddon.org/manifest.json", isEnabled = true, ExtensionHealth.SLOW, 30, slowManifest),
            ExtensionProvider("ext-failed", "Failed Addon Engine", "https://failedaddon.com/manifest.json", isEnabled = true, ExtensionHealth.FAILED, 40, failedManifest)
        )

        seedList.forEach { dao.insertExtension(it.toEntity()) }
    }

    fun getBackendUrl(): String {
        val ctx = DatabaseProvider.context ?: return ""
        val prefs = ctx.getSharedPreferences("extension_prefs", android.content.Context.MODE_PRIVATE)
        return prefs.getString("backend_url", "") ?: ""
    }

    fun setBackendUrl(url: String) {
        val ctx = DatabaseProvider.context ?: return
        val prefs = ctx.getSharedPreferences("extension_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("backend_url", url).apply()
        com.example.calmsource.core.network.BackendApiClient.baseUrl = url
    }

    fun isBackendEnrichmentEnabled(): Boolean {
        val ctx = DatabaseProvider.context ?: return false
        val prefs = ctx.getSharedPreferences("extension_prefs", android.content.Context.MODE_PRIVATE)
        return prefs.getBoolean("backend_enrichment_enabled", false)
    }

    fun setBackendEnrichmentEnabled(enabled: Boolean) {
        val ctx = DatabaseProvider.context ?: return
        val prefs = ctx.getSharedPreferences("extension_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("backend_enrichment_enabled", enabled).apply()
    }

    fun initialize(context: android.content.Context) {
        // Accessing the class triggers its Kotlin 'init' block.
    }
}
