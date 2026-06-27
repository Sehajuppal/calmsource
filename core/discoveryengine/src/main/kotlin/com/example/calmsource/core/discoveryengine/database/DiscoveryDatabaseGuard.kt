package com.example.calmsource.core.discoveryengine.database

import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteException
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

object DiscoveryDatabaseGuard {
    private const val TAG = "DiscoveryDbGuard"
    private const val SLOW_READ_MS = 50L
    private const val SLOW_WRITE_MS = 100L
    private val retryDelaysMs = longArrayOf(50L, 100L, 200L)

    private val writeDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DiscoveryDbWriter").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    suspend fun <T> read(name: String, block: () -> T): T {
        return withContext(Dispatchers.IO) {
            timed(name, slowThresholdMs = SLOW_READ_MS, block = block)
        }
    }

    suspend fun <T> write(name: String, block: () -> T): T {
        return withContext(writeDispatcher) {
            var attempt = 0
            while (true) {
                try {
                    return@withContext timed(name, slowThresholdMs = SLOW_WRITE_MS, block = block)
                } catch (e: Exception) {
                    val delayMs = retryDelaysMs.getOrNull(attempt)
                    if (!isTransientLock(e) || delayMs == null) {
                        safeWarn("$name failed after ${attempt + 1} attempt(s): ${e.javaClass.simpleName}")
                        throw e
                    }
                    attempt++
                    safeWarn("$name hit SQLite lock; retrying in ${delayMs}ms")
                    delay(delayMs)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            throw IllegalStateException("Unreachable database guard state")
        }
    }

    private fun <T> timed(name: String, slowThresholdMs: Long, block: () -> T): T {
        val startedAt = System.currentTimeMillis()
        return try {
            block()
        } finally {
            val durationMs = System.currentTimeMillis() - startedAt
            if (durationMs >= slowThresholdMs) {
                safeInfo("$name took ${durationMs}ms")
            }
        }
    }

    private fun isTransientLock(error: Throwable?): Boolean {
        if (error == null) return false
        if (error is SQLiteDatabaseLockedException) return true
        if (error is SQLiteException) {
            val message = error.message?.lowercase().orEmpty()
            if ("locked" in message || "busy" in message) return true
        }
        return isTransientLock(error.cause)
    }

    private fun safeInfo(message: String) {
        try {
            Log.i(TAG, message)
        } catch (_: Throwable) {
            // Local JVM tests use Android stubs.
        }
    }

    private fun safeWarn(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: Throwable) {
            // Local JVM tests use Android stubs.
        }
    }
}
