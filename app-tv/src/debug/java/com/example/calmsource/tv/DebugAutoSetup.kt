package com.example.calmsource.tv

import android.content.Context
import android.util.Log
import com.example.calmsource.feature.extensions.ExtensionRepository
import com.example.calmsource.feature.iptv.IPTVRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.mapper.toDomain
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debug-only auto-setup that pre-fills IPTV provider credentials and Stremio
 * extension URLs on first debug launch. Never shipped in release builds.
 *
 * Credentials are read from res/values/strings.xml in the debug source set.
 * Developers should create their own strings file with the required values
 * and exclude it from git (via .gitignore) to avoid accidentally committing
 * real credentials.
 */
object DebugAutoSetup {

    private const val TAG = "DebugAutoSetup"
    private const val PREFS_NAME = "debug_auto_setup"
    private const val KEY_XTREAM_DONE = "xtream_v4"
    private const val KEY_TORRENTIO_DONE = "torrentio_v4"
    private const val KEY_AIO_DONE = "aio_v4"

    private val hasRun = AtomicBoolean(false)

    suspend fun runIfNeeded(context: Context) {
        if (!hasRun.compareAndSet(false, true)) {
            Log.i(TAG, "runIfNeeded already executed this process, skipping")
            return
        }
        Log.i(TAG, "runIfNeeded called")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        try {
            IPTVRepository.init(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init IPTVRepository", e)
        }

        try {
            withContext(Dispatchers.IO) { setupXtream(context, prefs) }
        } catch (e: Exception) {
            Log.e(TAG, "Xtream setup crashed", e)
        }

        try {
            withContext(Dispatchers.IO) { setupTorrentio(context, prefs) }
        } catch (e: Exception) {
            Log.e(TAG, "Torrentio setup crashed", e)
        }

        try {
            withContext(Dispatchers.IO) { setupAio(context, prefs) }
        } catch (e: Exception) {
            Log.e(TAG, "AIO setup crashed", e)
        }

        Log.i(TAG, "Debug auto-setup pass complete.")
    }

    private suspend fun setupXtream(context: Context, prefs: android.content.SharedPreferences) {
        if (prefs.getBoolean(KEY_XTREAM_DONE, false)) {
            Log.i(TAG, "Xtream already done, skipping")
            return
        }
        val database = DatabaseProvider.getDatabase(context)
        val existingProviders = withContext(Dispatchers.IO) {
            database.iptvDao().getAllProviders().first().map { it.toDomain() }
        }
        Log.i(TAG, "Existing providers in database: ${existingProviders.size}")

        val xtreamProvider = existingProviders.firstOrNull {
            it.type == com.example.calmsource.core.model.IPTVProviderType.XTREAM
        }
        if (xtreamProvider != null) {
            Log.i(TAG, "Xtream provider already exists: ${xtreamProvider.id}, skipping")
            prefs.edit().putBoolean(KEY_XTREAM_DONE, true).apply()
            return
        }

        val serverUrl = resolveDebugString(context, "debug_xtream_server_url")
        val username = resolveDebugString(context, "debug_xtream_username")
        val password = resolveDebugString(context, "debug_xtream_password")

        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            Log.w(TAG, "Debug Xtream credentials not configured in debug res/values/strings.xml")
            return
        }

        Log.i(TAG, "No Xtream provider found, adding...")
        val result = IPTVRepository.addXtreamProvider(
            name = "Debug Xtream",
            serverUrl = serverUrl,
            username = username,
            password = password
        )
        if (result.isSuccess) {
            val provider = result.getOrThrow()
            Log.i(TAG, "Xtream provider added: ${provider.id}. Sync will happen in background.")
            prefs.edit().putBoolean(KEY_XTREAM_DONE, true).apply()
        } else {
            Log.e(TAG, "Failed to add Xtream: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
        }
    }

    private suspend fun setupTorrentio(context: Context, prefs: android.content.SharedPreferences) {
        if (prefs.getBoolean(KEY_TORRENTIO_DONE, false)) {
            Log.i(TAG, "Torrentio already done, skipping")
            return
        }
        val manifestUrl = resolveDebugString(context, "debug_torrentio_url")
        if (manifestUrl.isBlank()) return

        val ok = installExtensionIfMissing("Torrentio", manifestUrl)
        if (ok) prefs.edit().putBoolean(KEY_TORRENTIO_DONE, true).apply()
    }

    private suspend fun setupAio(context: Context, prefs: android.content.SharedPreferences) {
        if (prefs.getBoolean(KEY_AIO_DONE, false)) {
            Log.i(TAG, "AIO already done, skipping")
            return
        }
        val manifestUrl = resolveDebugString(context, "debug_aio_url")
        if (manifestUrl.isBlank()) return

        val ok = installExtensionIfMissing("AIOStreams", manifestUrl)
        if (ok) prefs.edit().putBoolean(KEY_AIO_DONE, true).apply()
    }

    private fun resolveDebugString(context: Context, name: String): String {
        return runCatching {
            val resId = context.resources.getIdentifier(name, "string", context.packageName)
            if (resId == 0) "" else context.resources.getString(resId)
        }.getOrDefault("")
    }

    private suspend fun installExtensionIfMissing(name: String, manifestUrl: String): Boolean {
        try {
            val existing = ExtensionRepository.extensions.value
            val alreadyInstalled = existing.any { ext ->
                ext.url.contains(manifestUrl.substringBefore("/manifest.json").takeLast(30))
            }
            if (alreadyInstalled) {
                Log.i(TAG, "$name already installed, skipping")
                return true
            }

            Log.i(TAG, "Installing $name...")
            val preview = ExtensionRepository.previewExtension(manifestUrl)
            if (preview.isSuccess && preview.manifest != null) {
                val result = ExtensionRepository.confirmInstall(
                    manifest = preview.manifest!!,
                    url = manifestUrl,
                    warnings = preview.warnings
                )
                if (result.isSuccess) {
                    Log.i(TAG, "$name installed successfully")
                    return true
                } else {
                    Log.e(TAG, "$name install failed: ${result.error}")
                }
            } else {
                Log.e(TAG, "$name preview failed: ${preview.error}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "$name auto-install failed", e)
        }
        return false
    }
}
