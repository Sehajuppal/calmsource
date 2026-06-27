package com.example.calmsource.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

private const val KEY_TOKEN = "jwt_token"

@Singleton
class CloudAuthTokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Throwable) {
        // Fallback gracefully to null if keystore issues arise (common on some devices/tests)
        null
    }

    // In-memory fallback
    private val memoryStorage = ConcurrentHashMap<String, String>()

    fun setToken(token: String?) {
        if (token == null) {
            clearToken()
            return
        }
        val p = prefs
        if (p != null) {
            try {
                p.edit().putString(KEY_TOKEN, token).apply()
            } catch (e: Throwable) {
                // Fallback to in-memory on write error
                memoryStorage[KEY_TOKEN] = token
            }
        } else {
            memoryStorage[KEY_TOKEN] = token
        }
    }

    fun getToken(): String? {
        val p = prefs
        if (p != null) {
            return try {
                p.getString(KEY_TOKEN, null) ?: memoryStorage[KEY_TOKEN]
            } catch (e: Throwable) {
                memoryStorage[KEY_TOKEN]
            }
        }
        return memoryStorage[KEY_TOKEN]
    }

    fun clearToken() {
        val p = prefs
        if (p != null) {
            try {
                p.edit().remove(KEY_TOKEN).apply()
            } catch (e: Throwable) {
                // Ignore
            }
        }
        memoryStorage.remove(KEY_TOKEN)
    }
}
