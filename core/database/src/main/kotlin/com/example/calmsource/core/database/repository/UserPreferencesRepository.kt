package com.example.calmsource.core.database.repository

import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.mapper.toDomain
import com.example.calmsource.core.database.mapper.toEntity
import com.example.calmsource.core.model.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

object UserPreferencesRepository {
    private val isTest: Boolean get() = com.example.calmsource.core.model.TestEnvironment.isTest
    private val scope = CoroutineScope((if (isTest) Dispatchers.Unconfined else Dispatchers.IO) + SupervisorJob())
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private val fallbackDao: com.example.calmsource.core.database.dao.PreferencesDao by lazy {
        object : com.example.calmsource.core.database.dao.PreferencesDao {
            private val mem = MutableStateFlow<com.example.calmsource.core.database.entity.UserPreferencesEntity?>(null)
            override fun getPreferences() = mem
            override fun insertPreferences(preferences: com.example.calmsource.core.database.entity.UserPreferencesEntity): Long {
                mem.value = preferences
                return 1L
            }
            override fun updatePreferences(preferences: com.example.calmsource.core.database.entity.UserPreferencesEntity): Int {
                insertPreferences(preferences)
                return 1
            }
        }
    }

    private val dao: com.example.calmsource.core.database.dao.PreferencesDao
        get() = DatabaseProvider.databaseOrNull()?.preferencesDao() ?: fallbackDao

    val preferences: StateFlow<UserPreferences> by lazy {
        preferencesFlow()
            .map { it?.toDomain() ?: UserPreferences() }
            .stateIn(scope, SharingStarted.Eagerly, UserPreferences())
    }

    init {
        scope.launch {
            DatabaseProvider.databaseReady.collect { ready ->
                if (ready) ensureDefaultPreferences()
            }
        }
    }

    fun updatePreferences(update: (UserPreferences) -> UserPreferences) {
        scope.launch {
            mutex.withLock {
                val current = preferences.value
                val updated = update(current)
                dao.insertPreferences(updated.toEntity())
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun preferencesFlow(): Flow<com.example.calmsource.core.database.entity.UserPreferencesEntity?> {
        return DatabaseProvider.databaseReady.flatMapLatest {
            dao.getPreferences()
        }
    }

    private suspend fun ensureDefaultPreferences() {
        mutex.withLock {
            val current = dao.getPreferences().firstOrNull()
            if (current == null) {
                dao.insertPreferences(UserPreferences().toEntity())
            }
        }
    }
}
