package com.example.calmsource.feature.iptv

import android.content.Context
import android.util.Log
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.mapper.*
import com.example.calmsource.core.discoveryengine.DiscoveryEngine
import com.example.calmsource.core.discoveryengine.models.MediaItem as DiscoveryMediaItem
import com.example.calmsource.core.model.*
import com.example.calmsource.core.network.UrlRedactor
import com.example.calmsource.feature.iptv.xtream.toIPTVChannel
import com.example.calmsource.feature.iptv.xtream.XtreamServerUrlNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import androidx.room.withTransaction
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.example.calmsource.core.network.NetworkClient

class XtreamRepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause)

object XtreamRepository {

    /** Batch size for Room inserts during sync.
     *  Dynamically reduced for low-RAM devices (≤256 MB) to 50
     *  to give GC breathing room. Standard devices use 100. */
    internal val SYNC_BATCH_SIZE: Int
        get() = if (isLowRamDevice()) 50 else 100

    /** Max concurrent HTTP requests per sync stage.
     *  Kept at 2 for Fire TV Stick to avoid OOM kills from
     *  parallel network + DB write pressure on low-RAM devices. */
    private const val MAX_CONCURRENT_CATEGORY_FETCHES = 2

    /** Cap per-channel Xtream EPG fetches on standard devices. */
    private const val MAX_EPG_CHANNELS_STANDARD = 500

    /** Batch size for EPG Room inserts during background sync. */
    private const val EPG_INSERT_BATCH_SIZE = 500

    /** Cap live channel inserts per sync to prevent unbounded heap growth on low-RAM devices. */
    internal fun maxLiveChannelsPerSync(): Int {
        val maxHeap = Runtime.getRuntime().maxMemory()
        return when {
            maxHeap <= 256L * 1024 * 1024 -> 5_000
            maxHeap <= 384L * 1024 * 1024 -> 10_000
            maxHeap <= 512L * 1024 * 1024 -> 15_000
            else -> 30_000
        }
    }

    /** Skip duplicate auth during sync when add-provider just validated. */
    private const val FRESH_AUTH_TTL_MS = 30_000L

    private val recentlyAuthedProviders = ConcurrentHashMap<String, Long>()

    /** Devices with ≤256 MB max heap are considered low-RAM.
     *  On these devices VOD and series sync are skipped entirely;
     *  users still access them via on-demand search. */
    private fun isLowRamDevice(): Boolean {
        return Runtime.getRuntime().maxMemory() <= 256L * 1024 * 1024
    }

    /** Background Xtream EPG sync is skipped on low-RAM devices. */
    fun shouldScheduleBackgroundEpgSync(): Boolean = !isLowRamDevice()

    @Volatile
    private var secureTokenStore: IptvSecureTokenStore = FakeInMemoryIptvSecureTokenStore()

    val tokenStore: IptvSecureTokenStore get() = secureTokenStore

    private val _syncProgress = MutableStateFlow<XtreamSyncProgress?>(null)
    val syncProgress: StateFlow<XtreamSyncProgress?> = _syncProgress.asStateFlow()

    fun init(context: Context) {
        val oldStore = secureTokenStore
        val newStore = EncryptedIptvSecureTokenStore(context)

        if (oldStore is FakeInMemoryIptvSecureTokenStore) {
            val credentials = oldStore.exportCredentials()
            val migrationCount = credentials.size
            if (migrationCount > 0) {
                Log.i("XtreamRepository", "Migrating $migrationCount entries.")
                for ((providerId, username, password) in credentials) {
                    newStore.savePassword(providerId, username, password)
                }
                oldStore.clearAll()
            }
        }

        secureTokenStore = newStore
    }

    /**
     * Returns true if encrypted/secure credential storage is available, 
     * and false if it is using an in-memory fallback store.
     */
    fun isEncryptedStorageAvailable(): Boolean {
        return secureTokenStore.isEncryptedStorageAvailable
    }

    // visible for testing
    fun setSecureTokenStore(store: IptvSecureTokenStore) {
        secureTokenStore = store
    }

    private fun databaseOrNull() = DatabaseProvider.databaseOrNull()

    /**
     * Validates the server URL for Xtream provider setup.
     *
     * Rejects empty URLs, URLs without http/https scheme, and URLs containing whitespace.
     * Returns a human-readable error message or null if valid.
     */
    internal fun validateServerUrl(serverUrl: String): String? {
        if (serverUrl.isBlank()) {
            return "Server URL is required"
        }
        if (serverUrl != serverUrl.trim()) {
            return "Server URL must not contain whitespace"
        }
        if (serverUrl.any { it.isWhitespace() }) {
            return "Server URL must not contain whitespace"
        }
        val prepared = XtreamServerUrlNormalizer.preprocessPortalInput(serverUrl)
        val lower = prepared.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return "Server URL must start with http:// or https://"
        }
        return null
    }

    /**
     * Validates a username string.
     *
     * Rejects empty strings and strings containing only whitespace.
     */
    internal fun validateUsername(username: String): String? {
        if (username.isBlank()) {
            return "Username cannot be empty"
        }
        return null
    }

    /**
     * Validates a password string.
     *
     * Rejects empty strings and strings containing only whitespace.
     */
    internal fun validatePassword(password: String): String? {
        if (password.isBlank()) {
            return "Password cannot be empty"
        }
        return null
    }

    internal fun markProviderRecentlyAuthed(providerId: String) {
        recentlyAuthedProviders[providerId] = System.currentTimeMillis()
    }

    internal fun clearRecentlyAuthed(providerId: String) {
        recentlyAuthedProviders.remove(providerId)
    }

    internal fun isProviderRecentlyAuthed(providerId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val authedAt = recentlyAuthedProviders[providerId] ?: return false
        return nowMs - authedAt < FRESH_AUTH_TTL_MS
    }

    private fun userFacingValidationError(message: String): String {
        val safeMessage = UrlRedactor.redactErrorMessage(message)
        return safeMessage
            .removePrefix("Network error while fetching account details: ")
            .ifBlank { safeMessage }
    }

    suspend fun addXtreamProvider(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        client: io.ktor.client.HttpClient = NetworkClient.xtreamClient,
        persistProvider: Boolean = true
    ): Result<IPTVProvider> = withContext(Dispatchers.IO) {
        try {
            val trimmedName = name.trim()
            val preprocessedServerUrl = XtreamServerUrlNormalizer.preprocessPortalInput(serverUrl)

            // Input validation — each field checked individually for specific error messages
            validateServerUrl(preprocessedServerUrl)?.let { msg ->
                return@withContext Result.failure(IllegalArgumentException(msg))
            }
            validateUsername(username)?.let { msg ->
                return@withContext Result.failure(IllegalArgumentException(msg))
            }
            validatePassword(password)?.let { msg ->
                return@withContext Result.failure(IllegalArgumentException(msg))
            }

            val normalizedServerUrl = XtreamServerUrlNormalizer.normalizePortalUrl(preprocessedServerUrl)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid server URL format. Use http://host:port"))

            val config = XtreamProviderConfig(
                id = "pre-auth",
                name = trimmedName,
                serverUrl = normalizedServerUrl,
                username = username.trim()
            )
            val impl = com.example.calmsource.feature.iptv.xtream.XtreamApiClientImpl(client)
            val authResult = impl.validateAccount(config, password)
            if (!authResult.isAuthenticated) {
                val safeMessage = userFacingValidationError(authResult.error ?: "Authentication failed")
                return@withContext Result.failure(Exception(safeMessage))
            }
            val userInfo = authResult.userInfo
            if (userInfo != null && userInfo.status.equals("Expired", ignoreCase = true)) {
                return@withContext Result.failure(Exception("Subscription expired"))
            }
            val expDate = userInfo?.expirationDate
            if (expDate != null && expDate > 0 && expDate * 1000L < System.currentTimeMillis()) {
                return@withContext Result.failure(Exception("Subscription expired"))
            }

            val canonicalServerUrl = authResult.serverInfo?.let { serverInfo ->
                XtreamServerUrlNormalizer.resolveStoredPortalUrl(
                    normalizedUserUrl = normalizedServerUrl,
                    serverUrl = serverInfo.url,
                    port = serverInfo.port,
                    httpsPort = serverInfo.httpsPort,
                    serverProtocol = serverInfo.serverProtocol
                )
            } ?: normalizedServerUrl

            val id = "xtream-${UUID.randomUUID()}"
            val provider = IPTVProvider(
                id = id,
                name = trimmedName.ifBlank { "Xtream Provider" },
                playlistUrl = canonicalServerUrl,
                serverUrl = canonicalServerUrl,
                type = IPTVProviderType.XTREAM,
                username = username.trim(),
                isEnabled = true,
                health = ProviderHealth.HEALTHY
            )

            // Save credentials before publishing the provider metadata.
            runCatching {
                secureTokenStore.savePortalUrl(id, normalizedServerUrl)
                secureTokenStore.savePassword(id, username.trim(), password)
            }.getOrElse { throwable ->
                val safeMessage = UrlRedactor.redactErrorMessage(
                    throwable.message ?: "Failed to save Xtream credentials"
                )
                return@withContext Result.failure(Exception(safeMessage))
            }

            // Save to DB (metadata only; no credentials are persisted in Room).
            // IPTVRepository's wrapper owns this insert in app flows so it can
            // use its fallback DAO without causing a second Room write.
            if (persistProvider) {
                val database = databaseOrNull()
                if (database != null) {
                    runCatching {
                        database.iptvDao().insertProvider(provider.toEntity())
                    }.getOrElse { throwable ->
                        secureTokenStore.clearProvider(id)
                        val safeMessage = UrlRedactor.redactErrorMessage(
                            throwable.message ?: "Failed to save Xtream provider"
                        )
                        return@withContext Result.failure(Exception(safeMessage))
                    }
                }
            }

            markProviderRecentlyAuthed(id)
            Result.success(provider)
        } catch (e: CancellationException) {
            _syncProgress.value = null
            throw e
        } catch (e: Exception) {
            val safeMessage = UrlRedactor.redactErrorMessage(e.message ?: e.javaClass.simpleName)
            Result.failure(Exception(safeMessage))
        }
    }

    suspend fun deleteXtreamProvider(providerId: String) = withContext(Dispatchers.IO) {
        val database = databaseOrNull()
        if (database != null) {
            database.withTransaction {
                val db = database.openHelper.writableDatabase
                db.execSQL("DELETE FROM xtream_vod_fts WHERE rowid IN (SELECT rowid FROM xtream_vod WHERE providerId = ?)", arrayOf(providerId))
                db.execSQL("DELETE FROM xtream_series_fts WHERE rowid IN (SELECT rowid FROM xtream_series WHERE providerId = ?)", arrayOf(providerId))

                val iptvDao = database.iptvDao()
                val provider = iptvDao.getProviderByIdDirect(providerId)
                iptvDao.deleteEPGProgramsByProvider(providerId)
                iptvDao.deleteEPGSourcesByProvider(providerId)
                iptvDao.deleteChannelsByProvider(providerId)
                database.xtreamDao().deleteVodByProvider(providerId)
                database.xtreamDao().deleteSeriesByProvider(providerId)
                if (provider != null) {
                    iptvDao.deleteProvider(provider)
                }
            }
        }

        secureTokenStore.clearProvider(providerId)
        clearRecentlyAuthed(providerId)
        if (_syncProgress.value?.providerId == providerId) {
            _syncProgress.value = null
        }
    }

    suspend fun getPassword(providerId: String, username: String): String? {
        return secureTokenStore.readPassword(providerId, username)
    }

    // ─── Sync implementation ────────────────────────────────────────────

    /**
     * Full synchronisation of an Xtream provider:
     * authenticate → live categories/streams → VOD → series.
     *
     * **Memory-efficient per-category fetching**: Instead of downloading
     * the entire catalog in one API call (which OOM-kills Fire TV Stick),
     * we fetch categories first, then fetch + insert streams one category
     * at a time. Peak memory is limited to one category's worth of items.
     *
     * Batch inserts use [SYNC_BATCH_SIZE] items per Room insert call.
     */
    suspend fun syncProvider(
        providerId: String,
        apiClient: XtreamApiClient,
        onProgress: ((XtreamSyncProgress) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        fun emitProgress(progress: XtreamSyncProgress) {
            _syncProgress.value = progress
            onProgress?.invoke(progress)
        }

        var progress = XtreamSyncProgress(providerId = providerId, stage = XtreamSyncStage.IDLE)
        emitProgress(progress)

        fun sanitizedError(error: Throwable): String {
            return UrlRedactor.redactErrorMessage(error.message ?: error.javaClass.simpleName)
        }

        /** Compute linear progress percentage within a [minPercent..maxPercent] range.
         *  Uses `toFloat()` to avoid the integer-division stall that previously kept
         *  progress stuck at the minimum value when categoryCount > divisor. */
        fun linearProgress(categoryIndex: Int, categoryCount: Int, minPercent: Int, maxPercent: Int): Int {
            val count = categoryCount.coerceAtLeast(1)
            val range = (maxPercent - minPercent).coerceAtLeast(1)
            return minPercent + ((categoryIndex + 1).toFloat() / count.toFloat() * range).toInt()
        }

        val contentErrors = Collections.synchronizedList(mutableListOf<String>())
        fun recordContentError(message: String) {
            contentErrors.add(message)
        }
        fun contentErrorDetails(): String {
            val snapshot = synchronized(contentErrors) { contentErrors.toList() }
            return snapshot.takeIf { it.isNotEmpty() }
                ?.take(8)
                ?.joinToString(prefix = " Last error: ", separator = "; ")
                .orEmpty()
        }

        fun dnsFailureWarning(serverUrl: String): String? {
            val host = XtreamServerUrlNormalizer.extractHost(serverUrl) ?: return null
            val snapshot = synchronized(contentErrors) { contentErrors.toList() }
            val dnsFailures = snapshot.count {
                it.contains("Unable to resolve host", ignoreCase = true) &&
                    it.contains(host, ignoreCase = true)
            }
            if (dnsFailures == 0) return null
            return "Server hostname \"$host\" could not be reached (DNS failed). Remove this provider and add it again using the portal URL from your IPTV supplier — not an internal CDN hostname like \"$host\"."
        }

        try {
            // 1. Read provider config from Room
            val database = databaseOrNull()
                ?: throw XtreamRepositoryException("Database unavailable")
            val providerEntity = try {
                database.iptvDao().getProviderByIdDirect(providerId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
                ?: throw XtreamRepositoryException("Provider $providerId not found")

            val providerDomain = providerEntity.toDomain()
            val syncPortalUrl = secureTokenStore.readPortalUrl(providerId)
                ?.let { XtreamServerUrlNormalizer.normalizePortalUrl(it) }
                ?.takeIf { it.isNotBlank() }
                ?: providerDomain.serverUrl
            val config = XtreamProviderConfig(
                id = providerDomain.id,
                name = providerDomain.name,
                serverUrl = syncPortalUrl,
                username = providerDomain.username ?: ""
            )

            // 2. Read password from SecureTokenStore — NEVER log it
            val password = secureTokenStore.readPassword(providerId, config.username)
            if (password == null) {
                // Credential loss (KeyStore corruption, data wipe) must not mark the
                // provider FAILED — that permanently hides all channels. Instead, emit
                // a clear error so the UI can prompt the user to re-enter credentials.
                progress = progress.copy(
                    stage = XtreamSyncStage.FAILED,
                    error = "Credentials lost. Please re-enter your IPTV credentials in Settings."
                )
                emitProgress(progress)
                return@withContext
            }

            // 3. Validate / authenticate
            progress = progress.copy(stage = XtreamSyncStage.VALIDATING, progressPercent = 5)
            emitProgress(progress)

            val authenticated = if (isProviderRecentlyAuthed(providerId)) {
                true
            } else {
                apiClient.authenticate(config, password)
            }
            if (!authenticated) {
                throw SecurityException("Authentication failed")
            }
            var liveChannelCount = 0
            var vodCount = 0
            var seriesCount = 0
            val channelCap = maxLiveChannelsPerSync()

            // ── Stage: Live channels (per-category fetch → insert) ──────
            progress = progress.copy(stage = XtreamSyncStage.SYNCING_LIVE_CATEGORIES, progressPercent = 10)
            emitProgress(progress)

            val liveCategories = runCatching {
                apiClient.getLiveCategories(config, password)
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                emptyList()
            }
            val liveCategoryMap = liveCategories.associate { it.id to it.name }

            progress = progress.copy(stage = XtreamSyncStage.SYNCING_LIVE_STREAMS, progressPercent = 15)
            emitProgress(progress)

            val liveChannelMap = java.util.LinkedHashMap<String, IPTVChannel>()
            if (liveCategories.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val liveSemaphore = Semaphore(MAX_CONCURRENT_CATEGORY_FETCHES)
                coroutineScope {
                    liveCategories.map { category ->
                        async {
                            liveSemaphore.withPermit {
                                runCatching {
                                    apiClient.getLiveStreams(config, password, categoryId = category.id)
                                }.getOrElse { error ->
                                    if (error is CancellationException) throw error
                                    recordContentError("live(${category.name}): ${sanitizedError(error)}")
                                    emptyList()
                                }
                            }
                        }
                    }.forEachIndexed { catIdx, deferred ->
                        currentCoroutineContext().ensureActive()
                        val catStreams = deferred.await()
                        for (stream in catStreams) {
                            val channel = stream.toIPTVChannel(providerId)
                            val groupName = liveCategoryMap[stream.categoryId] ?: stream.categoryId
                            liveChannelMap.putIfAbsent(channel.id, channel.copy(groupTitle = groupName))
                        }
                        liveChannelCount = liveChannelMap.size
                        progress = progress.copy(
                            liveChannelCount = liveChannelCount,
                            progressPercent = linearProgress(catIdx, liveCategories.size, 15, 35)
                        )
                        emitProgress(progress)
                    }
                }
                // Fallback: no data from per-category fetches -> fetch all at once
                if (liveChannelMap.isEmpty()) {
                    val allStreams = runCatching {
                        apiClient.getLiveStreams(config, password)
                    }.getOrElse { error ->
                        if (error is CancellationException) throw error
                        recordContentError("live: ${sanitizedError(error)}")
                        emptyList()
                    }
                    for (stream in allStreams) {
                        val channel = stream.toIPTVChannel(providerId)
                        val groupName = liveCategoryMap[stream.categoryId] ?: stream.categoryId
                        liveChannelMap.putIfAbsent(channel.id, channel.copy(groupTitle = groupName))
                    }
                }
                val allLiveChannels = liveChannelMap.values.toList()
                val capped = allLiveChannels.take(channelCap)
                liveChannelCount = capped.size
                if (capped.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    database.withTransaction {
                        database.iptvDao().deleteChannelsByProvider(providerId)
                        capped.map { channel ->
                            val lang = IptvChannelOrganizer.detectLanguage(channel)
                            val cntry = IptvChannelOrganizer.detectCountry(channel)
                            channel.copy(language = lang, country = cntry).toEntity()
                        }.chunked(SYNC_BATCH_SIZE).forEach { batch ->
                            currentCoroutineContext().ensureActive()
                            database.iptvDao().insertChannels(batch)
                        }
                    }
                }
                if (allLiveChannels.size > channelCap) {
                    val exceeded = allLiveChannels.size - channelCap
                    progress = progress.copy(
                        warning = "Showing $channelCap of ${allLiveChannels.size} live channels on this device. $exceeded more were not imported."
                    )
                }
            } else { // end liveCategories.isNotEmpty() block
                val allStreams = runCatching {
                    apiClient.getLiveStreams(config, password)
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    recordContentError("live: ${sanitizedError(error)}")
                    emptyList()
                }
                for (stream in allStreams) {
                    val channel = stream.toIPTVChannel(providerId)
                    val groupName = liveCategoryMap[stream.categoryId] ?: stream.categoryId
                    liveChannelMap.putIfAbsent(channel.id, channel.copy(groupTitle = groupName))
                }
                val allLiveChannels = liveChannelMap.values.toList()
                val capped = allLiveChannels.take(channelCap)
                liveChannelCount = capped.size
                if (capped.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    database.withTransaction {
                        database.iptvDao().deleteChannelsByProvider(providerId)
                        capped.map { channel ->
                            val lang = IptvChannelOrganizer.detectLanguage(channel)
                            val cntry = IptvChannelOrganizer.detectCountry(channel)
                            channel.copy(language = lang, country = cntry).toEntity()
                        }.chunked(SYNC_BATCH_SIZE).forEach { batch ->
                            currentCoroutineContext().ensureActive()
                            database.iptvDao().insertChannels(batch)
                        }
                    }
                }
                if (allLiveChannels.size > channelCap) {
                    val exceeded = allLiveChannels.size - channelCap
                    progress = progress.copy(
                        warning = "Showing $channelCap of ${allLiveChannels.size} live channels on this device. $exceeded more were not imported."
                    )
                }
            }

            progress = progress.copy(liveChannelCount = liveChannelCount, progressPercent = 35)
            emitProgress(progress)
            currentCoroutineContext().ensureActive()

            // ── Stage: VOD (SKIPPED on low-RAM devices) ─────────────────
            if (isLowRamDevice()) {
                Log.i("XtreamRepository", "Low-RAM device — skipping VOD sync (available via on-demand search)")
                vodCount = 0
            } else {
            progress = progress.copy(stage = XtreamSyncStage.SYNCING_VOD_CATEGORIES, progressPercent = 40)
            emitProgress(progress)

            val vodCategories = runCatching {
                apiClient.getVodCategories(config, password)
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                emptyList()
            }
            val vodCategoryMap = vodCategories.associate { it.id to it.name }

            progress = progress.copy(stage = XtreamSyncStage.SYNCING_VOD_STREAMS, progressPercent = 45)
            emitProgress(progress)

            val vodEntityMap = java.util.LinkedHashMap<String, com.example.calmsource.core.database.entity.XtreamVodEntity>()
            if (vodCategories.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val vodSemaphore = Semaphore(MAX_CONCURRENT_CATEGORY_FETCHES)
                coroutineScope {
                    vodCategories.map { category ->
                        async {
                            vodSemaphore.withPermit {
                                val catVods = runCatching {
                                    apiClient.getVodStreams(config, password, categoryId = category.id)
                                }.getOrElse { error ->
                                    if (error is CancellationException) throw error
                                    recordContentError("VOD(${category.name}): ${sanitizedError(error)}")
                                    emptyList()
                                }
                                catVods
                            }
                        }
                    }.forEachIndexed { catIdx, deferred ->
                        currentCoroutineContext().ensureActive()
                        val catVodsResult = deferred.await()
                        for (item in catVodsResult) {
                            val catEntity = item.toEntity(providerId, vodCategoryMap[item.categoryId] ?: item.categoryId)
                            vodEntityMap.putIfAbsent(catEntity.id, catEntity)
                        }
                        vodCount = vodEntityMap.size
                        progress = progress.copy(
                            vodCount = vodCount,
                            progressPercent = linearProgress(catIdx, vodCategories.size, 45, 65)
                        )
                        emitProgress(progress)
                    }
                }
                if (vodEntityMap.isEmpty()) {
                    val allVods = runCatching {
                        apiClient.getVodStreams(config, password)
                    }.getOrElse { error ->
                        if (error is CancellationException) throw error
                        recordContentError("VOD: ${sanitizedError(error)}")
                        emptyList()
                    }
                    for (item in allVods) {
                        val catEntity = item.toEntity(providerId, vodCategoryMap[item.categoryId] ?: "")
                        vodEntityMap.putIfAbsent(catEntity.id, catEntity)
                    }
                    vodCount = vodEntityMap.size
                }
                val finalVodEntities = vodEntityMap.values.toList()
                if (finalVodEntities.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    database.withTransaction {
                        val db = database.openHelper.writableDatabase
                        db.execSQL("DELETE FROM xtream_vod_fts WHERE rowid IN (SELECT rowid FROM xtream_vod WHERE providerId = ?)", arrayOf(providerId))
                        database.xtreamDao().deleteVodByProvider(providerId)
                        finalVodEntities.chunked(SYNC_BATCH_SIZE).forEach { batch ->
                            currentCoroutineContext().ensureActive()
                            database.xtreamDao().insertVod(batch)
                        }
                        db.execSQL(
                            "INSERT INTO xtream_vod_fts(rowid, name, categoryName) SELECT rowid, name, IFNULL(categoryName, '') FROM xtream_vod WHERE providerId = ?",
                            arrayOf(providerId)
                        )
                        db.execSQL(com.example.calmsource.core.database.dao.XtreamDao.ftsPruneVodOrphansQuery().sql)
                    }
                    indexDiscoveryMediaItems(finalVodEntities.map { entity -> entity.toDomain().toDiscoveryMediaItem() })
                }
            } else {
                val allVods = runCatching {
                    apiClient.getVodStreams(config, password)
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    recordContentError("VOD: ${sanitizedError(error)}")
                    emptyList()
                }
                if (allVods.isNotEmpty()) {
                    val allEntities = allVods.map { it.toEntity(providerId, "") }
                    vodCount = allEntities.size
                    database.withTransaction {
                        val db = database.openHelper.writableDatabase
                        db.execSQL("DELETE FROM xtream_vod_fts WHERE rowid IN (SELECT rowid FROM xtream_vod WHERE providerId = ?)", arrayOf(providerId))
                        database.xtreamDao().deleteVodByProvider(providerId)
                        allEntities.chunked(SYNC_BATCH_SIZE).forEach { batch ->
                            database.xtreamDao().insertVod(batch)
                        }
                        db.execSQL(
                            "INSERT INTO xtream_vod_fts(rowid, name, categoryName) SELECT rowid, name, IFNULL(categoryName, '') FROM xtream_vod WHERE providerId = ?",
                            arrayOf(providerId)
                        )
                        db.execSQL(com.example.calmsource.core.database.dao.XtreamDao.ftsPruneVodOrphansQuery().sql)
                    }
                    indexDiscoveryMediaItems(allEntities.map { entity -> entity.toDomain().toDiscoveryMediaItem() })
                }
            }

            progress = progress.copy(vodCount = vodCount, progressPercent = 65)
            emitProgress(progress)
            } // end isLowRamDevice VOD guard
            currentCoroutineContext().ensureActive()

            // ── Stage: Series (SKIPPED on low-RAM devices) ──────────────
            if (isLowRamDevice()) {
                Log.i("XtreamRepository", "Low-RAM device — skipping series sync (available via on-demand search)")
                seriesCount = 0
            } else {
            progress = progress.copy(stage = XtreamSyncStage.SYNCING_SERIES_CATEGORIES, progressPercent = 70)
            emitProgress(progress)

            val seriesCategories = runCatching {
                apiClient.getSeriesCategories(config, password)
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                emptyList()
            }
            val seriesCategoryMap = seriesCategories.associate { it.id to it.name }

            progress = progress.copy(stage = XtreamSyncStage.SYNCING_SERIES, progressPercent = 75)
            emitProgress(progress)

            val seriesEntityMap = java.util.LinkedHashMap<String, com.example.calmsource.core.database.entity.XtreamSeriesEntity>()
            if (seriesCategories.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val seriesSemaphore = Semaphore(MAX_CONCURRENT_CATEGORY_FETCHES)
                coroutineScope {
                    seriesCategories.map { category ->
                        async {
                            seriesSemaphore.withPermit {
                                val catSeries = runCatching {
                                    apiClient.getSeries(config, password, categoryId = category.id)
                                }.getOrElse { error ->
                                    if (error is CancellationException) throw error
                                    recordContentError("series(${category.name}): ${sanitizedError(error)}")
                                    emptyList()
                                }
                                catSeries
                            }
                        }
                    }.forEachIndexed { catIdx, deferred ->
                        currentCoroutineContext().ensureActive()
                        val catSeriesResult = deferred.await()
                        for (item in catSeriesResult) {
                            val catEntity = item.toEntity(providerId, seriesCategoryMap[item.categoryId] ?: item.categoryId)
                            seriesEntityMap.putIfAbsent(catEntity.id, catEntity)
                        }
                        seriesCount = seriesEntityMap.size
                        progress = progress.copy(
                            seriesCount = seriesCount,
                            progressPercent = linearProgress(catIdx, seriesCategories.size, 75, 90)
                        )
                        emitProgress(progress)
                    }
                }
                if (seriesEntityMap.isEmpty()) {
                    val allSeries = runCatching {
                        apiClient.getSeries(config, password)
                    }.getOrElse { error ->
                        if (error is CancellationException) throw error
                        recordContentError("series: ${sanitizedError(error)}")
                        emptyList()
                    }
                    for (item in allSeries) {
                        val catEntity = item.toEntity(providerId, seriesCategoryMap[item.categoryId] ?: "")
                        seriesEntityMap.putIfAbsent(catEntity.id, catEntity)
                    }
                    seriesCount = seriesEntityMap.size
                }
                val finalSeriesEntities = seriesEntityMap.values.toList()
                if (finalSeriesEntities.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    database.withTransaction {
                        val db = database.openHelper.writableDatabase
                        db.execSQL("DELETE FROM xtream_series_fts WHERE rowid IN (SELECT rowid FROM xtream_series WHERE providerId = ?)", arrayOf(providerId))
                        database.xtreamDao().deleteSeriesByProvider(providerId)
                        finalSeriesEntities.chunked(SYNC_BATCH_SIZE).forEach { batch ->
                            currentCoroutineContext().ensureActive()
                            database.xtreamDao().insertSeries(batch)
                        }
                        db.execSQL(
                            "INSERT INTO xtream_series_fts(rowid, name, categoryName) SELECT rowid, name, IFNULL(categoryName, '') FROM xtream_series WHERE providerId = ?",
                            arrayOf(providerId)
                        )
                        db.execSQL(com.example.calmsource.core.database.dao.XtreamDao.ftsPruneSeriesOrphansQuery().sql)
                    }
                    indexDiscoveryMediaItems(finalSeriesEntities.map { entity -> entity.toDomain().toDiscoveryMediaItem() })
                }
            } else {
                val allSeries = runCatching {
                    apiClient.getSeries(config, password)
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    recordContentError("series: ${sanitizedError(error)}")
                    emptyList()
                }
                if (allSeries.isNotEmpty()) {
                    val allEntities = allSeries.map { it.toEntity(providerId, "") }
                    seriesCount = allEntities.size
                    database.withTransaction {
                        val db = database.openHelper.writableDatabase
                        db.execSQL("DELETE FROM xtream_series_fts WHERE rowid IN (SELECT rowid FROM xtream_series WHERE providerId = ?)", arrayOf(providerId))
                        database.xtreamDao().deleteSeriesByProvider(providerId)
                        allEntities.chunked(SYNC_BATCH_SIZE).forEach { batch ->
                            database.xtreamDao().insertSeries(batch)
                        }
                        db.execSQL(
                            "INSERT INTO xtream_series_fts(rowid, name, categoryName) SELECT rowid, name, IFNULL(categoryName, '') FROM xtream_series WHERE providerId = ?",
                            arrayOf(providerId)
                        )
                        db.execSQL(com.example.calmsource.core.database.dao.XtreamDao.ftsPruneSeriesOrphansQuery().sql)
                    }
                    indexDiscoveryMediaItems(allEntities.map { entity -> entity.toDomain().toDiscoveryMediaItem() })
                }
            }

            } // end isLowRamDevice series guard

            // Validate we got some content
            if (liveChannelCount == 0 && vodCount == 0 && seriesCount == 0) {
                val details = contentErrorDetails()
                throw XtreamRepositoryException(
                    "Provider returned no live channels, VOD, or series. Check the account and server URL.$details"
                )
            }

            // Surface any content errors as a warning even when the sync succeeded
            val contentErrDetails = contentErrorDetails()
            dnsFailureWarning(config.serverUrl)?.let { dnsWarning ->
                val existingWarning = progress.warning
                progress = progress.copy(
                    warning = buildString {
                        if (!existingWarning.isNullOrBlank()) append(existingWarning).append(' ')
                        append(dnsWarning)
                    }.trim()
                )
            }
            if (contentErrDetails.isNotEmpty()) {
                val existingWarning = progress.warning
                progress = progress.copy(warning = buildString {
                    if (!existingWarning.isNullOrBlank()) append(existingWarning).append(" ")
                    append("Some content could not be synced:$contentErrDetails")
                }.trim())
            }

            // ── Content sync complete; EPG runs in background (WorkManager) ──
            val epgDeferNote = if (isLowRamDevice()) {
                "Program guide sync skipped on this device. Add an XMLTV URL in Settings for EPG."
            } else {
                "Program guide will sync in the background."
            }
            val existingWarning = progress.warning
            progress = progress.copy(
                warning = buildString {
                    if (!existingWarning.isNullOrBlank()) append(existingWarning).append(' ')
                    append(epgDeferNote)
                }.trim()
            )

            // Update provider health to HEALTHY
            runCatching {
                val updated = providerEntity.apply { health = ProviderHealth.HEALTHY.name }
                database.iptvDao().updateProvider(updated)
            }.getOrElse {
                throw XtreamRepositoryException("Synced content could not be saved")
            }

            // Discovery indexing is handled automatically:
            // - Live channels: IPTVRepository.init{} collects Room's
            //   getAllChannels() Flow and ingests into DiscoveryEngine
            // - VOD/Series: indexed after sync when available, and lazily
            //   indexed by search/browse flows on low-RAM devices

            // Mark COMPLETE
            progress = progress.copy(stage = XtreamSyncStage.COMPLETE, progressPercent = 100)
            emitProgress(progress)

        } catch (e: CancellationException) {
            _syncProgress.value = null
            throw e
        } catch (e: Exception) {
            // Don't mark as FAILED on transient sync errors — use SLOW instead.
            // FAILED permanently blocks all channels for this provider.
            // A successful re-sync will reset to HEALTHY.
            databaseOrNull()?.let { database ->
                runCatching {
                    database.iptvDao().getProviderByIdDirect(providerId)?.let { provider ->
                        provider.health = ProviderHealth.SLOW.name
                        database.iptvDao().updateProvider(provider)
                    }
                }
            }
            val errDetails = contentErrorDetails()
            val failedProgress = progress.copy(
                stage = XtreamSyncStage.FAILED,
                error = UrlRedactor.redactErrorMessage(e.message ?: "Sync failed") + errDetails
            )
            emitProgress(failedProgress)
            throw e
        }
    }

    /**
     * Background Xtream short-EPG sync. Capped to channels with tvgId plus recent
     * favorites, batched in transactions of [EPG_INSERT_BATCH_SIZE].
     */
    suspend fun syncProviderEpg(
        providerId: String,
        apiClient: XtreamApiClient
    ) = withContext(Dispatchers.IO) {
        if (!shouldScheduleBackgroundEpgSync()) return@withContext

        val database = databaseOrNull() ?: return@withContext
        val providerEntity = database.iptvDao().getProviderByIdDirect(providerId) ?: return@withContext
        val providerDomain = providerEntity.toDomain()
        val syncPortalUrl = secureTokenStore.readPortalUrl(providerId)
            ?.let { XtreamServerUrlNormalizer.normalizePortalUrl(it) }
            ?.takeIf { it.isNotBlank() }
            ?: providerDomain.serverUrl
        val config = XtreamProviderConfig(
            id = providerDomain.id,
            name = providerDomain.name,
            serverUrl = syncPortalUrl,
            username = providerDomain.username ?: ""
        )
        val password = secureTokenStore.readPassword(providerId, config.username) ?: return@withContext

        val savedChannels = database.iptvDao()
            .getChannelsByProvider(providerId)
            .firstOrNull()
            ?.map { it.toDomain() }
            .orEmpty()
            .filter { !it.isVod }
        if (savedChannels.isEmpty()) return@withContext

        val recentChannelIds = runCatching {
            database.userMemoryDao().observeRecentChannels(100).first()
                .map { it.itemKey }
                .toSet()
        }.getOrDefault(emptySet())
        val favoriteChannelIds = runCatching {
            database.userMemoryDao().observeFavorites(100).first()
                .filter { it.contentType == "channel" }
                .map { it.itemKey }
                .toSet()
        }.getOrDefault(emptySet())
        val prioritizedIds = recentChannelIds + favoriteChannelIds
        val channelsForEpg = selectChannelsForEpgSync(savedChannels, prioritizedIds, MAX_EPG_CHANNELS_STANDARD)
        if (channelsForEpg.isEmpty()) return@withContext

        val contentErrors = Collections.synchronizedList(mutableListOf<String>())
        fun recordContentError(message: String) {
            contentErrors.add(message)
        }

        IPTVRepository.beginEpgBackgroundSync(providerId)
        try {
            // Do NOT wipe the provider's EPG up front: if the worker is cancelled or the network
            // fails mid-run, the guide would stay empty until the next successful sync (bug #4).
            // Instead each batch atomically replaces only the EPG for the channels it contains, so
            // every other channel keeps its previous programmes throughout the refresh. A channel
            // is cleared at most once per sync so programmes split across batches aren't dropped.
            val pendingPrograms = ArrayList<com.example.calmsource.core.database.entity.EPGProgramEntity>(
                EPG_INSERT_BATCH_SIZE
            )
            val clearedChannelIds = HashSet<String>()
            suspend fun flushPrograms() {
                if (pendingPrograms.isEmpty()) return
                val batch = pendingPrograms.toList()
                pendingPrograms.clear()
                val channelIdsInBatch = batch.map { it.channelId }.toSet()
                val channelsToClear = channelIdsInBatch - clearedChannelIds
                clearedChannelIds.addAll(channelIdsInBatch)
                // Atomically clear only the channels seen for the first time this sync, then insert
                // this batch — so other channels keep their existing programmes throughout (bug #4).
                // withTransaction dispatches to Room's transaction executor (off the caller thread).
                database.withTransaction {
                    if (channelsToClear.isNotEmpty()) {
                        database.iptvDao().deleteEPGProgramsByChannels(channelsToClear)
                    }
                    batch.chunked(EPG_INSERT_BATCH_SIZE).forEach { chunk ->
                        database.iptvDao().insertEPGPrograms(chunk)
                    }
                }
            }

            val epgSemaphore = Semaphore(MAX_CONCURRENT_CATEGORY_FETCHES)
            coroutineScope {
                val deferreds = channelsForEpg.map { channel ->
                    val streamId = channel.rawAttributes["xtream_stream_id"] ?: return@map null
                    async {
                        epgSemaphore.withPermit {
                            runCatching {
                                apiClient.getShortEpg(config, password, streamId)
                            }.mapCatching { listings ->
                                listings.map { program ->
                                    val startMs = if (program.startTimestamp < 100_000_000_000L) {
                                        program.startTimestamp * 1000
                                    } else {
                                        program.startTimestamp
                                    }
                                    val endMs = if (program.endTimestamp < 100_000_000_000L) {
                                        program.endTimestamp * 1000
                                    } else {
                                        program.endTimestamp
                                    }
                                    val epgChannelId = channel.tvgId ?: channel.name
                                    val id = generateSafeSourceId("${channel.id}-$startMs-$endMs-${program.title}")
                                    com.example.calmsource.core.database.entity.EPGProgramEntity().apply {
                                        this.id = id
                                        this.channelId = epgChannelId
                                        this.title = program.title
                                        this.description = program.description
                                        this.startTimeMs = startMs
                                        this.endTimeMs = endMs
                                        this.language = program.language
                                    }
                                }
                            }.getOrElse { error ->
                                if (error is CancellationException) throw error
                                recordContentError("EPG(${channel.name}): ${UrlRedactor.redactErrorMessage(error.message ?: error.javaClass.simpleName)}")
                                emptyList()
                            }
                        }
                    }
                }.filterNotNull()
                for ((idx, deferred) in deferreds.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val channelEpg = deferred.await()
                    if (channelEpg.isNotEmpty()) {
                        pendingPrograms.addAll(channelEpg)
                        if (pendingPrograms.size >= EPG_INSERT_BATCH_SIZE) {
                            flushPrograms()
                        }
                    }
                    if (idx % 50 == 0) {
                        yield()
                    }
                }
            }
            flushPrograms()

        } finally {
            IPTVRepository.endEpgBackgroundSync(providerId)
        }
    }

    internal fun selectChannelsForEpgSync(
        channels: List<IPTVChannel>,
        prioritizedChannelIds: Set<String>,
        maxChannels: Int
    ): List<IPTVChannel> {
        if (maxChannels <= 0 || channels.isEmpty()) return emptyList()
        val withTvgId = channels.filter { !it.tvgId.isNullOrBlank() }
        val prioritized = channels.filter { it.id in prioritizedChannelIds && it !in withTvgId }
        val remainder = channels.filter { it !in withTvgId && it !in prioritized }
        return (withTvgId + prioritized + remainder).take(maxChannels)
    }

    private suspend fun indexDiscoveryMediaItems(items: List<DiscoveryMediaItem>) {
        if (items.isEmpty()) return
        try {
            items
                .distinctBy { "${it.type}:${it.id}" }
                .chunked(500)
                .forEach { chunk ->
                    currentCoroutineContext().ensureActive()
                    DiscoveryEngine.ingestStremioItems(chunk)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("XtreamRepository", "Failed to index Xtream metadata into discovery: ${e.message}")
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
}
