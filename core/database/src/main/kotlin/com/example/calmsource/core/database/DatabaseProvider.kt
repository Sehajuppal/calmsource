package com.example.calmsource.core.database

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DatabaseProvider {
    @Volatile
    private var INSTANCE: CalmSourceDatabase? = null
    @Volatile
    private var appContext: Context? = null
    val context: Context? get() = appContext
    private val LOCK = Any()
    private val _databaseReady = MutableStateFlow(false)
    val databaseReady: StateFlow<Boolean> = _databaseReady.asStateFlow()

    fun init(context: Context) {
        if (appContext == null) {
            synchronized(LOCK) {
                if (appContext == null) {
                    appContext = context.applicationContext
                }
            }
        }
    }

    fun getDatabase(context: Context): CalmSourceDatabase {
        init(context)
        return db
    }

    fun isInitialized(): Boolean = appContext != null

    fun databaseOrNull(): CalmSourceDatabase? = INSTANCE

    suspend fun warmup(context: Context): CalmSourceDatabase {
        init(context)
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db
        }
    }

    /**
     * Helper to get the database assuming it's already initialized.
     * Throws an exception if called before initialization.
     */
    val db: CalmSourceDatabase
        get() = INSTANCE ?: synchronized(LOCK) {
            INSTANCE ?: run {
                val context = appContext ?: throw IllegalStateException("DatabaseProvider must be initialized with a Context first.")
                val instance = CalmSourceDatabase.buildDatabase(context)
                INSTANCE = instance
                _databaseReady.value = true
                instance
            }
        }

    fun resetForTesting() {
        synchronized(LOCK) {
            INSTANCE?.close()
            INSTANCE = null
            appContext = null
            _databaseReady.value = false
        }
    }
}
