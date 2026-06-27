package com.example.calmsource.feature.debrid

import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.mapper.*
import com.example.calmsource.core.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

import com.example.calmsource.core.model.TestEnvironment

class DebridRepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause)

object DebridRepository {
    private val isTest: Boolean get() = TestEnvironment.isTest
    private val scope = kotlinx.coroutines.CoroutineScope(
        object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun isDispatchNeeded(context: kotlin.coroutines.CoroutineContext): Boolean {
                val target = if (TestEnvironment.isTest) kotlinx.coroutines.Dispatchers.Unconfined else kotlinx.coroutines.Dispatchers.IO
                return target.isDispatchNeeded(context)
            }
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                val target = if (TestEnvironment.isTest) kotlinx.coroutines.Dispatchers.Unconfined else kotlinx.coroutines.Dispatchers.IO
                if (target === kotlinx.coroutines.Dispatchers.Unconfined) {
                    block.run()
                } else {
                    target.dispatch(context, block)
                }
            }
        } + kotlinx.coroutines.SupervisorJob()
    )
    private fun runIO(block: suspend () -> Unit) { if (isTest) kotlinx.coroutines.runBlocking { block() } else scope.launch { block() } }

    // Short-TTL, in-memory cache of resolved debrid direct links keyed by infoHash/magnet so a
    // retry or fallback within the same session doesn't re-run the (slow) add/select/poll flow.
    // Resolved links are sensitive and intentionally never persisted to disk.
    private data class CachedLink(val url: String, val expiresAtMs: Long)
    private val resolvedLinkCache = java.util.concurrent.ConcurrentHashMap<String, CachedLink>()
    private const val RESOLVED_LINK_TTL_MS = 10 * 60 * 1000L

    /** Returns a cached resolved debrid link for [key] if present and unexpired, else null. */
    fun cachedResolvedLink(key: String): String? {
        if (key.isBlank()) return null
        val entry = resolvedLinkCache[key] ?: return null
        if (System.currentTimeMillis() >= entry.expiresAtMs) {
            resolvedLinkCache.remove(key)
            return null
        }
        return entry.url
    }

    /** Stores a freshly resolved debrid [url] for [key] with a short TTL. */
    fun putResolvedLink(key: String, url: String) {
        if (key.isBlank() || url.isBlank()) return
        resolvedLinkCache[key] = CachedLink(url, System.currentTimeMillis() + RESOLVED_LINK_TTL_MS)
    }

    /** Clears the in-memory resolved debrid link cache (e.g. on account disconnect). */
    fun clearResolvedLinkCache() {
        resolvedLinkCache.clear()
    }

    private val fallbackDao: com.example.calmsource.core.database.dao.DebridDao by lazy {
        object : com.example.calmsource.core.database.dao.DebridDao {
            private val mem = MutableStateFlow<List<com.example.calmsource.core.database.entity.DebridAccountEntity>>(emptyList())
            override fun getAllAccounts() = mem
            override fun getAccountById(id: String) = mem.map { list -> list.firstOrNull { it.id == id } }
            override fun insertAccount(account: com.example.calmsource.core.database.entity.DebridAccountEntity) { mem.value = mem.value.filter { it.id != account.id } + account }
            override fun updateAccount(account: com.example.calmsource.core.database.entity.DebridAccountEntity) { insertAccount(account) }
            override fun deleteAccount(account: com.example.calmsource.core.database.entity.DebridAccountEntity) { mem.value = mem.value.filter { it.id != account.id } }
        }
    }

    private val dao: com.example.calmsource.core.database.dao.DebridDao
         get() = DatabaseProvider.databaseOrNull()?.debridDao() ?: fallbackDao

    @Volatile
    private var _tokenStore: SecureTokenStore? = null
    private val tokenStoreLock = Any()

    val tokenStore: SecureTokenStore
        get() = _tokenStore ?: synchronized(tokenStoreLock) {
            _tokenStore ?: if (isTest) {
                FakeInMemorySecureTokenStore().also { _tokenStore = it }
            } else {
                android.util.Log.e("DebridRepository", "Secure store accessed before init(context); using fallback store")
                FakeInMemorySecureTokenStore().also { _tokenStore = it }
            }
        }

    fun init(context: android.content.Context) {
        if (_tokenStore == null || _tokenStore is FakeInMemorySecureTokenStore) {
            synchronized(tokenStoreLock) {
                if (_tokenStore == null || _tokenStore is FakeInMemorySecureTokenStore) {
                    _tokenStore = EncryptedSecureTokenStore(context)
                }
            }
        }
    }

    val accounts: StateFlow<List<DebridAccount>> by lazy {
        accountsFlow()
            .map { list ->
                list.map { entity ->
                    entity.toDomain()
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun accountsFlow(): Flow<List<com.example.calmsource.core.database.entity.DebridAccountEntity>> {
        return DatabaseProvider.databaseReady.flatMapLatest {
            dao.getAllAccounts()
        }
    }

    /**
     * Returns accounts WITHOUT tokenSet for safe UI display.
     * This is the preferred accessor for any UI/ViewModel layer.
     */
    fun getUiAccounts(): List<DebridAccount> {
        return accounts.value.map { it.copy(tokenSet = null) }
    }

    /**
     * Returns a single account enriched with its tokenSet from SecureTokenStore.
     * Use this only when you actually need the token (e.g. making API calls).
     */
    fun getAccountWithTokens(id: String): DebridAccount? {
        val account = accounts.value.find { it.id == id } ?: return null
        val tokenSet = tokenStore.getTokensForAccount(account.providerType, account.id)
            ?: tokenStore.getTokens(account.providerType)
        return account.copy(tokenSet = tokenSet)
    }

    /**
     * Returns the token set for a given provider from SecureTokenStore.
     * Internal helper — never expose tokens to UI.
     */
    fun getTokensForProvider(providerType: DebridProviderType): DebridTokenSet? {
        return tokenStore.getTokens(providerType)
    }

    private val _connectionState = MutableStateFlow<DebridConnectionState>(DebridConnectionState.IDLE)
    val connectionState: StateFlow<DebridConnectionState> = _connectionState.asStateFlow()

    private var clients: Map<DebridProviderType, DebridProviderClient> = mapOf(
        DebridProviderType.REAL_DEBRID to RealDebridHttpClient(),
        DebridProviderType.ALL_DEBRID to AllDebridHttpClient(),
        DebridProviderType.PREMIUMIZE to PremiumizeHttpClient()
    )

    @androidx.annotation.VisibleForTesting
    fun setClientsForTest(testClients: Map<DebridProviderType, DebridProviderClient>) {
        clients = testClients
    }

    fun getClient(type: DebridProviderType): DebridProviderClient? {
        return clients[type]
    }

    fun listAccounts(): List<DebridAccount> {
        return accounts.value
    }

    fun addAccountWithApiKey(providerType: DebridProviderType, username: String, email: String, apiKeyOrToken: String) {
        val providerName = when (providerType) {
            DebridProviderType.REAL_DEBRID -> "Real-Debrid"
            DebridProviderType.ALL_DEBRID -> "AllDebrid"
            DebridProviderType.PREMIUMIZE -> "Premiumize"
            DebridProviderType.FAKE_DEMO -> "Demo Debrid"
        }
        val id = "deb-${providerType.name.lowercase().take(2)}-${UUID.randomUUID().toString().take(8)}"
        val tokenSet = if (providerType == DebridProviderType.REAL_DEBRID) {
            DebridTokenSet(accessToken = apiKeyOrToken)
        } else {
            DebridTokenSet(apiKey = apiKeyOrToken)
        }

        // Store token in secure storage only — never in Room
        tokenStore.saveTokensForAccount(providerType, id, tokenSet)
        tokenStore.saveTokens(providerType, tokenSet)

        // Account object intentionally omits tokenSet — it lives only in SecureTokenStore
        val newAccount = DebridAccount(
            id = id,
            providerType = providerType,
            providerName = providerName,
            isConnected = true,
            email = email,
            username = username,
            // tokenSet intentionally null: tokens belong in SecureTokenStore, not in domain/entity objects
            tokenSet = null,
            status = DebridAccountStatus(username, email, 90, "2026-09-03", true),
            health = DebridAccountHealth.HEALTHY
        )
        
        if (isTest) {
            dao.insertAccount(newAccount.toEntity())
        } else {
            runIO {
                dao.insertAccount(newAccount.toEntity())
            }
        }
    }

    @Deprecated("Use addAccountWithApiKey; retained for older tests.")
    fun addFakeAccount(providerType: DebridProviderType, username: String, email: String, apiKeyOrToken: String) {
        addAccountWithApiKey(providerType, username, email, apiKeyOrToken)
    }

    suspend fun startConnectionFlow(providerType: DebridProviderType): DebridAuthSession {
        _connectionState.value = DebridConnectionState.CONNECTING
        val client = clients[providerType] ?: throw DebridRepositoryException("No client found for $providerType")
        return client.startAuth()
    }

    suspend fun completeConnectionFlow(providerType: DebridProviderType, session: DebridAuthSession): DebridAccount {
        val client = clients[providerType] ?: throw DebridRepositoryException("No client found for $providerType")
        val id = "deb-${providerType.name.lowercase().take(2)}-${java.util.UUID.randomUUID().toString().take(8)}"
        
        val polledSession = client.pollAuth(session)
        val tokenSet = client.completeAuth(polledSession)
        
        // Store token securely — never pass to entity/Room layer
        val status = client.getAccountStatus(tokenSet)
        val health = client.getHealth()

        val account = DebridAccount(
            id = id,
            providerType = providerType,
            providerName = client.displayName,
            isConnected = true,
            email = status.email,
            username = status.username,
            // tokenSet intentionally null in the stored account — lives only in SecureTokenStore
            tokenSet = null,
            status = status,
            health = health
        )

        tokenStore.saveTokensForAccount(providerType, id, tokenSet)
        tokenStore.saveTokens(providerType, tokenSet)
        try {
            dao.insertAccount(account.toEntity())
            _connectionState.value = DebridConnectionState.CONNECTED
        } catch (e: Exception) {
            tokenStore.deleteTokensForAccount(providerType, id)
            tokenStore.deleteTokens(providerType)
            throw e
        }
        return account
    }

    fun disconnectAccount(id: String) {
        runIO {
            val account = listAccounts().find { it.id == id }
            if (account != null) {
                tokenStore.deleteTokensForAccount(account.providerType, account.id)
                tokenStore.deleteTokens(account.providerType)
                val updated = account.copy(isConnected = false, email = null, username = null, tokenSet = null, status = null, health = DebridAccountHealth.HEALTHY)
                dao.updateAccount(updated.toEntity())
                clearResolvedLinkCache()
            }
        }
    }

    fun updateAccountHealth(id: String, health: DebridAccountHealth) {
        runIO {
            val account = listAccounts().find { it.id == id }
            if (account != null) {
                dao.updateAccount(account.copy(health = health).toEntity())
            }
        }
    }

    suspend fun refreshAccountStatus(id: String) {
        val account = listAccounts().find { it.id == id }
        if (account != null && account.isConnected) {
            // Retrieve token lazily from SecureTokenStore — never from the cached account object
            val tokenSet = tokenStore.getTokensForAccount(account.providerType, account.id)
                ?: tokenStore.getTokens(account.providerType)
            if (tokenSet != null) {
                val client = clients[account.providerType]
                if (client != null) {
                    try {
                        val status = client.getAccountStatus(tokenSet)
                        val health = client.getHealth()
                        val updated = account.copy(status = status, health = health, email = status.email, username = status.username)
                        dao.updateAccount(updated.toEntity())
                    } catch (e: Exception) {
                        updateAccountHealth(id, DebridAccountHealth.FAILED)
                    }
                }
            }
        }
    }

    suspend fun checkCachedAvailability(providerType: DebridProviderType, hashes: List<String>): Map<String, DebridCachedAvailability> {
        val client = clients[providerType] ?: return emptyMap()
        val tokenSet = tokenStore.getTokens(providerType) ?: return emptyMap()
        return try {
            client.checkCachedAvailability(hashes, tokenSet)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // Fake sync removed
}
