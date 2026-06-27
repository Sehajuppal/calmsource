package com.example.calmsource.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authPrefsStore by preferencesDataStore(name = "auth_preferences")

@Singleton
class AuthPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val HAS_SKIPPED_AUTH = booleanPreferencesKey("has_skipped_auth")
    }

    val hasSkippedAuth: Flow<Boolean> = context.authPrefsStore.data.map { preferences ->
        preferences[PreferencesKeys.HAS_SKIPPED_AUTH] ?: false
    }

    suspend fun setAuthSkipped(skipped: Boolean) {
        context.authPrefsStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SKIPPED_AUTH] = skipped
        }
    }
}
