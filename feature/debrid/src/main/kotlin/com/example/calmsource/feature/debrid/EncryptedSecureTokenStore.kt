package com.example.calmsource.feature.debrid

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.calmsource.core.model.DebridProviderType

/**
 * Production-ready secure token store backed by AndroidX Security Crypto.
 *
 * Uses [EncryptedSharedPreferences] with:
 * - AES256_SIV for key encryption
 * - AES256_GCM for value encryption
 * - Android Keystore as the master key provider
 *
 * All operations are wrapped in try-catch to prevent crashes from
 * Keystore failures, corrupted storage, or device compatibility issues.
 *
 * Key naming convention: "cs_secure_{providerType}_{accountId}_{tokenType}"
 */
class EncryptedSecureTokenStore(context: Context) : SecureTokenStore {

    companion object {
        private const val FILE_NAME = "calmsource_secure_tokens"
        private const val KEY_PREFIX = "cs_secure"
        /**
         * All known token type keys for a (providerType, accountId) pair.
         * The Debrid token store stores access tokens, refresh tokens, API keys,
         * and account metadata — enumerate them so we can rebuild full keys
         * without calling EncryptedSharedPreferences.getAll().
         */
        private val SUPPORTED_TOKEN_TYPES = listOf(
            "access_token",
            "refresh_token",
            "expires_at",
            "api_key",
            "accessToken",
            "refreshToken",
            "apiKey",
            "metadata",
            "client_id",
            "client_secret",
            "clientId",
            "clientSecret"
        )
    }

    private val prefs: SharedPreferences? = try {
        val appContext = context.applicationContext ?: context
        // NOTE: Key rotation is handled by Android Keystore at the hardware level.
        // EncryptedSharedPreferences keys persist for the lifetime of the app install.
        // If a key compromise is suspected, a full app reinstall or data clear is required.
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Throwable) {
        // Keystore initialization can fail on some devices or during first boot.
        // Fall back to null — all operations will safely return null/false.
        null
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace(":", "\\:")

    private fun key(providerType: DebridProviderType, accountId: String, tokenType: String): String =
        "${KEY_PREFIX}:${providerType.name}:${escape(accountId)}:${escape(tokenType)}"

    private val accountLock = Any()

    override fun saveToken(providerType: DebridProviderType, accountId: String, tokenType: String, value: String) {
        try {
            val editor = prefs?.edit() ?: return
            val tokenKey = key(providerType, accountId, tokenType)
            editor.putString(tokenKey, value)
            
            // Track the key dynamically
            val keysKey = "cs_secure_keys:${providerType.name}:${escape(accountId)}"
            val existingKeys = prefs.getStringSet(keysKey, null)?.toMutableSet() ?: mutableSetOf()
            if (existingKeys.add(tokenKey)) {
                editor.putStringSet(keysKey, existingKeys)
            }
            editor.apply()
            registerAccount(providerType, accountId)
        } catch (e: Exception) {
            // Silently fail — do not crash the app for a storage write failure.
        }
    }

    override fun readToken(providerType: DebridProviderType, accountId: String, tokenType: String): String? {
        return try {
            prefs?.getString(key(providerType, accountId, tokenType), null)
        } catch (e: Exception) {
            null
        }
    }

    override fun deleteToken(providerType: DebridProviderType, accountId: String, tokenType: String) {
        try {
            val editor = prefs?.edit() ?: return
            val tokenKey = key(providerType, accountId, tokenType)
            editor.remove(tokenKey)
            
            val keysKey = "cs_secure_keys:${providerType.name}:${escape(accountId)}"
            val existingKeys = prefs.getStringSet(keysKey, null)?.toMutableSet()
            if (existingKeys != null && existingKeys.remove(tokenKey)) {
                if (existingKeys.isEmpty()) {
                    editor.remove(keysKey)
                } else {
                    editor.putStringSet(keysKey, existingKeys)
                }
            }
            editor.apply()
        } catch (e: Exception) {
            // Silently fail.
        }
    }

    override fun clearAccount(providerType: DebridProviderType, accountId: String) {
        try {
            val editor = prefs?.edit() ?: return
            
            // Retrieve dynamically tracked keys
            val keysKey = "cs_secure_keys:${providerType.name}:${escape(accountId)}"
            val trackedKeys = prefs.getStringSet(keysKey, null) ?: emptySet()
            for (k in trackedKeys) {
                editor.remove(k)
            }
            editor.remove(keysKey)
            
            // Fallback / legacy support: also clear SUPPORTED_TOKEN_TYPES
            for (tokenType in SUPPORTED_TOKEN_TYPES) {
                editor.remove(key(providerType, accountId, tokenType))
            }

            // Retrieve the persisted set of account IDs. If it exists and contains accountId, remove it.
            val accountsKey = "cs_secure_account_ids:${providerType.name}"
            val persistedIds = prefs.getStringSet(accountsKey, null)?.toMutableSet()
            if (persistedIds != null && persistedIds.contains(accountId)) {
                persistedIds.remove(accountId)
                if (persistedIds.isEmpty()) {
                    editor.remove(accountsKey)
                } else {
                    editor.putStringSet(accountsKey, persistedIds)
                }
            }

            editor.apply()

            // After editor.apply() succeeds, synchronized on accountLock
            synchronized(accountLock) {
                val ids = knownAccountIds[providerType]
                if (ids != null) {
                    ids.remove(accountId)
                    if (ids.isEmpty()) {
                        knownAccountIds.remove(providerType)
                    }
                }
            }
        } catch (e: Exception) {
            // Silently fail.
        }
    }

    override fun clearProvider(providerType: DebridProviderType) {
        try {
            val editor = prefs?.edit()
            // Retrieve account IDs from prefs
            val accountIds = prefs?.getStringSet("cs_secure_account_ids:${providerType.name}", null) ?: synchronized(accountLock) {
                knownAccountIds[providerType]?.toSet() ?: emptySet()
            }
            if (editor != null) {
                for (accountId in accountIds) {
                    // Retrieve dynamically tracked keys for this account
                    val keysKey = "cs_secure_keys:${providerType.name}:${escape(accountId)}"
                    val trackedKeys = prefs.getStringSet(keysKey, null) ?: emptySet()
                    for (k in trackedKeys) {
                        editor.remove(k)
                    }
                    editor.remove(keysKey)
                    
                    // Fallback
                    for (tokenType in SUPPORTED_TOKEN_TYPES) {
                        editor.remove(key(providerType, accountId, tokenType))
                    }
                }
                // Also remove the provider's account IDs set
                editor.remove("cs_secure_account_ids:${providerType.name}")
                editor.apply()
                synchronized(accountLock) {
                    knownAccountIds.remove(providerType)
                }
            }
        } catch (e: Exception) {
            // Silently fail.
        }
    }

    /**
     * Tracks the set of account IDs per provider so that [clearProvider] can
     * build exact key names without depending on EncryptedSharedPreferences.getAll().
     * Callers must register an accountId when it is first written.
     */
    private val knownAccountIds: MutableMap<DebridProviderType, MutableSet<String>> = mutableMapOf()

    init {
        try {
            prefs?.let { p ->
                for (providerType in DebridProviderType.entries) {
                    val saved = p.getStringSet("cs_secure_account_ids:${providerType.name}", null)
                    if (saved != null) {
                        knownAccountIds[providerType] = saved.toMutableSet()
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun registerAccount(providerType: DebridProviderType, accountId: String) {
        synchronized(accountLock) {
            val ids = knownAccountIds.getOrPut(providerType) { mutableSetOf() }
            if (!ids.contains(accountId)) {
                try {
                    val newIds = ids.toMutableSet()
                    newIds.add(accountId)
                    prefs?.edit()?.putStringSet(
                        "cs_secure_account_ids:${providerType.name}",
                        newIds
                    )?.apply()
                    ids.add(accountId)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    private fun unregisterAccount(providerType: DebridProviderType, accountId: String) {
        synchronized(accountLock) {
            val ids = knownAccountIds[providerType] ?: return
            if (ids.contains(accountId)) {
                try {
                    val editor = prefs?.edit() ?: return
                    val newIds = ids.toMutableSet()
                    newIds.remove(accountId)
                    if (newIds.isEmpty()) {
                        editor.remove("cs_secure_account_ids:${providerType.name}")
                    } else {
                        editor.putStringSet("cs_secure_account_ids:${providerType.name}", newIds)
                    }
                    editor.apply()
                    if (newIds.isEmpty()) {
                        knownAccountIds.remove(providerType)
                    } else {
                        ids.remove(accountId)
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    override fun clearAll() {
        try {
            prefs?.edit()?.clear()?.apply()
            synchronized(accountLock) {
                knownAccountIds.clear()
            }
        } catch (e: Exception) {
            // Silently fail.
        }
    }

    override fun hasToken(providerType: DebridProviderType, accountId: String, tokenType: String): Boolean {
        return try {
            prefs?.contains(key(providerType, accountId, tokenType)) ?: false
        } catch (e: Exception) {
            false
        }
    }
}
