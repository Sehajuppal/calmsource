package com.example.calmsource.feature.iptv

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class IptvSecureStorageUnavailableException(
    message: String = "Secure credential storage is unavailable on this device. IPTV credentials cannot be saved.",
) : IllegalStateException(message)

interface IptvSecureTokenStore {
    fun savePassword(providerId: String, username: String, password: String)
    fun readPassword(providerId: String, username: String): String?
    fun deletePassword(providerId: String, username: String)
    fun clearProvider(providerId: String)
    fun hasPassword(providerId: String, username: String): Boolean
    fun savePortalUrl(providerId: String, portalUrl: String) {}
    fun readPortalUrl(providerId: String): String? = null
    fun deletePortalUrl(providerId: String) {}
    val isEncryptedStorageAvailable: Boolean get() = true
}

/**
 * Production-ready secure token store backed by AndroidX Security Crypto.
 *
 * MasterKey is cached in a static holder to avoid repeated AndroidKeyStore operations
 * when XtreamRepository.init() is called multiple times (e.g., process recreation).
 */
class EncryptedIptvSecureTokenStore @VisibleForTesting internal constructor(
    private val prefs: SharedPreferences?
) : IptvSecureTokenStore {

    constructor(context: Context) : this(
        try {
            val masterKey = MasterKeyHolder.masterKey(context.applicationContext)
            EncryptedSharedPreferences.create(
                context.applicationContext,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            try {
                android.util.Log.e(TAG, "Encrypted storage unavailable! Secure keys will be lost on app restart. Cause: ${e.message}")
            } catch (_: Throwable) {
                // Android Log is not available in local JVM tests.
            }
            null
        }
    )

    companion object {
        private const val FILE_NAME = "calmsource_iptv_secure_tokens"
        private const val KEY_PREFIX = "cs_iptv_secure"
        private const val TAG = "IptvSecureStore"
        private val usernameLock = Any()
    }

    /**
     * Static holder for the MasterKey singleton.
     * Avoids repeated AndroidKeyStore key generation on every init() call.
     */
    private object MasterKeyHolder {
        @Volatile
        private var cached: MasterKey? = null

        fun masterKey(context: Context): MasterKey {
            cached?.let { return it }
            return synchronized(this) {
                cached ?: MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                    .also { cached = it }
            }
        }
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace(":", "\\:")

    private fun key(providerId: String, username: String): String =
        "${KEY_PREFIX}:${escape(providerId)}:${escape(username)}"

    private fun portalKey(providerId: String): String =
        "cs_iptv_portal:${escape(providerId)}"

    override val isEncryptedStorageAvailable: Boolean get() = prefs != null

    override fun savePassword(providerId: String, username: String, password: String) {
        val targetPrefs = prefs
        if (targetPrefs == null) {
            throw IptvSecureStorageUnavailableException()
        }
        synchronized(usernameLock) {
            val uKey = "cs_iptv_secure_usernames:${escape(providerId)}"
            val current = targetPrefs.getStringSet(uKey, null)?.toMutableSet() ?: mutableSetOf()
            current.add(username)
            targetPrefs.edit()
                .putString(key(providerId, username), password)
                .putStringSet(uKey, current)
                .apply()
        }
    }

    override fun readPassword(providerId: String, username: String): String? {
        return try {
            val targetPrefs = prefs ?: throw IptvSecureStorageUnavailableException()
            synchronized(usernameLock) {
                targetPrefs.getString(key(providerId, username), null)
            }
        } catch (e: IptvSecureStorageUnavailableException) {
            throw e
        } catch (e: Exception) {
            try {
                android.util.Log.e("EncryptedIptvSecureTokenStore",
                    "Failed to read secure keys for provider $providerId. " +
                    "KeyStore may be corrupted. User may need to re-enter setup keys.")
            } catch (_: Throwable) {}
            null
        }
    }

    override fun deletePassword(providerId: String, username: String) {
        try {
            val targetPrefs = prefs ?: throw IptvSecureStorageUnavailableException()
            synchronized(usernameLock) {
                val uKey = "cs_iptv_secure_usernames:${escape(providerId)}"
                val current = targetPrefs.getStringSet(uKey, null)?.toMutableSet()
                val editor = targetPrefs.edit().remove(key(providerId, username))
                if (current != null && current.remove(username)) {
                    if (current.isEmpty()) {
                        editor.remove(uKey)
                    } else {
                        editor.putStringSet(uKey, current)
                    }
                }
                editor.apply()
            }
        } catch (e: IptvSecureStorageUnavailableException) {
            throw e
        } catch (e: Exception) {
            // Silently fail
        }
    }

    override fun clearProvider(providerId: String) {
        try {
            val targetPrefs = prefs ?: throw IptvSecureStorageUnavailableException()
            val uKey = "cs_iptv_secure_usernames:${escape(providerId)}"
            synchronized(usernameLock) {
                val usernames = targetPrefs.getStringSet(uKey, null)?.toSet() ?: emptySet()
                val editor = targetPrefs.edit()
                for (username in usernames) {
                    editor.remove(key(providerId, username))
                }
                editor.remove(uKey)
                editor.remove(portalKey(providerId))
                editor.apply()
            }
        } catch (e: IptvSecureStorageUnavailableException) {
            throw e
        } catch (e: Exception) {
            // Silently fail
        }
    }

    override fun savePortalUrl(providerId: String, portalUrl: String) {
        val targetPrefs = prefs
        if (targetPrefs == null) {
            throw IptvSecureStorageUnavailableException()
        }
        targetPrefs.edit()
            .putString(portalKey(providerId), portalUrl.trim())
            .apply()
    }

    override fun readPortalUrl(providerId: String): String? {
        val targetPrefs = prefs ?: throw IptvSecureStorageUnavailableException()
        return targetPrefs.getString(portalKey(providerId), null)?.trim()?.takeIf { it.isNotBlank() }
    }

    override fun deletePortalUrl(providerId: String) {
        val targetPrefs = prefs ?: throw IptvSecureStorageUnavailableException()
        targetPrefs.edit().remove(portalKey(providerId)).apply()
    }

    override fun hasPassword(providerId: String, username: String): Boolean {
        return try {
            val targetPrefs = prefs ?: throw IptvSecureStorageUnavailableException()
            synchronized(usernameLock) {
                targetPrefs.contains(key(providerId, username))
            }
        } catch (e: IptvSecureStorageUnavailableException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Fake in-memory store for development.
 */
class FakeInMemoryIptvSecureTokenStore : IptvSecureTokenStore {
    override val isEncryptedStorageAvailable: Boolean get() = false

    private val lock = Any()
    private val store = mutableMapOf<String, String>()
    private val portalUrls = mutableMapOf<String, String>()
    private val knownUsernamesByProvider = mutableMapOf<String, MutableSet<String>>()

    private fun key(providerId: String, username: String): String =
        "${providerId}:${username}"

    override fun savePassword(providerId: String, username: String, password: String) {
        synchronized(lock) {
            knownUsernamesByProvider.getOrPut(providerId) { mutableSetOf() }.add(username)
            store[key(providerId, username)] = password
        }
    }

    override fun readPassword(providerId: String, username: String): String? {
        synchronized(lock) {
            return store[key(providerId, username)]
        }
    }

    override fun deletePassword(providerId: String, username: String) {
        synchronized(lock) {
            knownUsernamesByProvider[providerId]?.remove(username)
            store.remove(key(providerId, username))
        }
    }

    override fun clearProvider(providerId: String) {
        synchronized(lock) {
            val usernames = knownUsernamesByProvider[providerId]?.toList() ?: emptyList()
            for (username in usernames) {
                store.remove(key(providerId, username))
            }
            knownUsernamesByProvider.remove(providerId)
            portalUrls.remove(providerId)
        }
    }

    override fun savePortalUrl(providerId: String, portalUrl: String) {
        synchronized(lock) {
            portalUrls[providerId] = portalUrl.trim()
        }
    }

    override fun readPortalUrl(providerId: String): String? {
        synchronized(lock) {
            return portalUrls[providerId]?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    override fun deletePortalUrl(providerId: String) {
        synchronized(lock) {
            portalUrls.remove(providerId)
        }
    }

    override fun hasPassword(providerId: String, username: String): Boolean {
        synchronized(lock) {
            return store.containsKey(key(providerId, username))
        }
    }

    /**
     * Returns a list of all stored credentials as Triples of (providerId, username, password).
     * Used for migrating credentials to a new token store instance.
     */
    internal fun exportCredentials(): List<Triple<String, String, String>> {
        synchronized(lock) {
            val credentialsList = mutableListOf<Triple<String, String, String>>()
            for ((providerId, usernames) in knownUsernamesByProvider) {
                for (username in usernames) {
                    store[key(providerId, username)]?.let { password ->
                        credentialsList.add(Triple(providerId, username, password))
                    }
                }
            }
            return credentialsList
        }
    }

    /**
     * Clears all in-memory credentials from this store instance.
     */
    internal fun clearAll() {
        synchronized(lock) {
            store.clear()
            portalUrls.clear()
            knownUsernamesByProvider.clear()
        }
    }
}
