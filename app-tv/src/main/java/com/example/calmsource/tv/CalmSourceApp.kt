package com.example.calmsource.tv

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.pm.ApplicationInfo
import dagger.hilt.android.HiltAndroidApp
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.feature.debrid.DebridRepository
import com.example.calmsource.feature.iptv.IPTVRepository
import com.example.calmsource.core.data.ProfileSessionManager
import com.example.calmsource.core.database.repository.RoomUserMemoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.calmsource.core.data.sync.VaultSyncManager
import com.example.calmsource.core.model.DebridProviderType
import com.example.calmsource.core.model.IPTVProviderType
import com.example.calmsource.feature.iptv.XtreamRepository
import com.example.calmsource.feature.extensions.ExtensionInstallValidator

@HiltAndroidApp
class CalmSourceApp : Application() {
    private val appScopeJob = SupervisorJob()
    private val appScope = CoroutineScope(appScopeJob + Dispatchers.IO)

    @Inject
    lateinit var vaultSyncManager: VaultSyncManager

    @Inject
    lateinit var profileSessionManager: ProfileSessionManager

    /**
     * Dedicated encrypted store for extension config secrets. Created lazily on first use (off the
     * startup path so there is no Keystore ANR) and independent of [DebridRepository.init] timing,
     * so a secret saved right after a cold start is never lost when the encrypted store is later
     * installed (bug #7). The "ext_" namespace keeps these separate from debrid tokens (bug #31)
     * while reusing the same encrypted file so existing extension secrets stay readable.
     */
    private val extensionSecretStore: com.example.calmsource.feature.debrid.SecureTokenStore by lazy {
        com.example.calmsource.feature.debrid.EncryptedSecureTokenStore(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()

        // Wire up VaultSyncManager delegates
        vaultSyncManager.gatherIpTvDelegate = {
            val provider = IPTVRepository.providers.value.firstOrNull { 
                it.type == IPTVProviderType.XTREAM && it.isEnabled 
            }
            if (provider != null) {
                val password = XtreamRepository.getPassword(provider.id, provider.username ?: "") ?: ""
                Triple(provider.serverUrl, provider.username ?: "", password)
            } else {
                null
            }
        }

        vaultSyncManager.restoreIpTvDelegate = { url, user, pass ->
            val existing = IPTVRepository.providers.value.firstOrNull { 
                it.type == IPTVProviderType.XTREAM && 
                it.serverUrl == url && 
                it.username == user 
            }
            if (existing != null) {
                IPTVRepository.startXtreamProviderSync(existing.id)
            } else {
                val result = IPTVRepository.addXtreamProvider(
                    name = "Xtream Sync",
                    serverUrl = url,
                    username = user,
                    password = pass
                )
                result.getOrNull()?.let { provider ->
                    IPTVRepository.startXtreamProviderSync(provider.id)
                }
            }
        }

        vaultSyncManager.gatherDebridDelegate = {
            val account = DebridRepository.listAccounts().firstOrNull { it.isConnected }
            if (account != null) {
                val tokens = DebridRepository.tokenStore.getTokensForAccount(account.providerType, account.id)
                    ?: DebridRepository.tokenStore.getTokens(account.providerType)
                if (account.providerType == DebridProviderType.REAL_DEBRID) tokens?.accessToken else tokens?.apiKey
            } else {
                null
            }
        }

        vaultSyncManager.restoreDebridDelegate = { token ->
            val isActive = DebridRepository.listAccounts().any { it.isConnected }
            if (!isActive) {
                DebridRepository.addAccountWithApiKey(
                    providerType = DebridProviderType.REAL_DEBRID,
                    username = "RealDebrid Sync",
                    email = "sync@example.com",
                    apiKeyOrToken = token
                )
            }
        }

        vaultSyncManager.gatherExtensionsDelegate = {
            val demoIds = setOf("ext-legal-demo", "ext-slow", "ext-failed")
            com.example.calmsource.feature.extensions.ExtensionRepository.getExtensions()
                .filter { it.isEnabled && it.id !in demoIds }
                .map { it.url }
        }

        vaultSyncManager.restoreExtensionsDelegate = { urls ->
            appScope.launch {
                com.example.calmsource.feature.extensions.ExtensionRepository.awaitExtensions()
                com.example.calmsource.feature.extensions.ExtensionRepository.restoreExtensionsFromUrls(urls)
            }
        }

        com.example.calmsource.core.playback.PlaybackCrashMarker.installGlobalUncaughtHandler(this)
        wireCrashReporter()
        com.example.calmsource.core.playback.FallbackPreferences.warmBestEffort(this)
        com.example.calmsource.core.playback.FrameRateMatchingPreferences.warmBlockingBestEffort(this)
        com.example.calmsource.core.playback.StreamRacePreferences.warmBlockingBestEffort(this)
        com.example.calmsource.core.discoveryengine.database.DiscoverySearchFeatureFlags.warmBestEffort(this)
        DatabaseProvider.init(this)

        // Gate Ktor request/response logging to debug builds so release APKs
        // do not emit URLs to `adb logcat`. This must be set before the
        // first access to NetworkClient.client / xtreamClient.
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        com.example.calmsource.core.network.NetworkClient.setLoggingEnabled(isDebuggable)

        // Link ExtensionSecrets delegates to a dedicated encrypted store (see extensionSecretStore).
        com.example.calmsource.core.network.ExtensionSecrets.readDelegate = { providerId, key ->
            extensionSecretStore.readToken(
                com.example.calmsource.core.model.DebridProviderType.FAKE_DEMO,
                "ext_$providerId",
                key
            )
        }
        com.example.calmsource.core.network.ExtensionSecrets.saveDelegate = { providerId, key, value ->
            extensionSecretStore.saveToken(
                com.example.calmsource.core.model.DebridProviderType.FAKE_DEMO,
                "ext_$providerId",
                key,
                value
            )
        }
        com.example.calmsource.core.network.ExtensionSecrets.deleteDelegate = { providerId, key ->
            extensionSecretStore.deleteToken(
                com.example.calmsource.core.model.DebridProviderType.FAKE_DEMO,
                "ext_$providerId",
                key
            )
        }
        com.example.calmsource.core.network.ExtensionSecrets.clearDelegate = { providerId ->
            extensionSecretStore.clearAccount(
                com.example.calmsource.core.model.DebridProviderType.FAKE_DEMO,
                "ext_$providerId"
            )
        }
        com.example.calmsource.feature.search.ExtensionSearchProviderImpl.setupStremioAddonClientDelegate()

        // Defer all I/O-heavy init to avoid main-thread ANR.
        // DebridRepository.init() touches Android Keystore (EncryptedSharedPreferences)
        // which blocks the caller for Binder IPC to keymaster HAL.
        appScope.launch {
            DebridRepository.init(this@CalmSourceApp)
            DatabaseProvider.warmup(this@CalmSourceApp)
            com.example.calmsource.core.discoveryengine.DiscoveryEngine.initialize(this@CalmSourceApp)
            com.example.calmsource.feature.extensions.ExtensionRepository.initialize(this@CalmSourceApp)
            IPTVRepository.init(this@CalmSourceApp)
            com.example.calmsource.feature.iptv.IptvSyncScheduler.schedulePeriodicSync(this@CalmSourceApp)
            @OptIn(ExperimentalCoroutinesApi::class)
            launch {
                val memoryRepository = RoomUserMemoryRepository(DatabaseProvider.getDatabase(this@CalmSourceApp))
                profileSessionManager.activeProfile
                    .map { profile -> profile?.id ?: "default" }
                    .distinctUntilChanged()
                    .flatMapLatest { profileId ->
                        memoryRepository.observeContinueWatching(profileId)
                    }
                    .collect { items ->
                        TvWatchNextPublisher.publish(this@CalmSourceApp, items)
                    }
            }
        }

    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        handleMemoryPressure(level)
    }

    /**
     * Reacts to system memory pressure by trimming the image cache and, under severe pressure,
     * switching discovery/enrichment into low-memory mode. Important on memory-constrained TV
     * devices (e.g. Fire TV Stick) where previously this OS signal was ignored.
     */
    private fun handleMemoryPressure(level: Int) {
        runCatching {
            when (level) {
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
                ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                    com.example.calmsource.core.playback.ImageCacheController.trimForMemoryPressure(this, clearAll = true)
                    com.example.calmsource.core.discoveryengine.providers.ProviderManager.setLowMemoryMode(true)
                    appScopeJob.cancelChildren()
                }
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
                ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
                ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                    com.example.calmsource.core.playback.ImageCacheController.trimForMemoryPressure(this, clearAll = false)
                }
                else -> Unit
            }
        }
    }

    /**
     * Routes the dependency-free [com.example.calmsource.core.observability.CrashReporter] facade
     * to Firebase Crashlytics so core/feature modules can leave breadcrumbs and record non-fatals
     * without taking a Firebase dependency. Never forward secrets here.
     */
    private fun wireCrashReporter() {
        runCatching {
            val crashlytics = com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
            com.example.calmsource.core.observability.CrashReporter.logDelegate = { message ->
                crashlytics.log(message)
            }
            com.example.calmsource.core.observability.CrashReporter.setKeyDelegate = { key, value ->
                crashlytics.setCustomKey(key, value)
            }
            com.example.calmsource.core.observability.CrashReporter.recordDelegate = { throwable ->
                crashlytics.recordException(throwable)
            }
        }
    }
}
