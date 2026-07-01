package com.example.calmsource.core.database

import android.os.SystemClock
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.ArrayDeque

data class SlowQueryEntry(
    val sql: String,
    val args: List<String>,
    val durationMs: Long,
    val operation: SlowQueryOperation,
    val recordedAtMs: Long = System.currentTimeMillis()
)

enum class SlowQueryOperation {
    READ,
    WRITE,
    BATCH,
    UNKNOWN
}

object SlowQueryLogger {
    private const val TAG = "SlowQueryLogger"
    private const val MAX_ENTRIES = 200
    private const val SLOW_READ_MS = 50L
    private const val SLOW_WRITE_MS = 100L
    private const val SLOW_BATCH_MS = 500L

    private val entries = ArrayDeque<SlowQueryEntry>(MAX_ENTRIES)

    fun installOnOpen(db: SupportSQLiteDatabase) {
        // SlowQueryLogger uses the trace() helper below for explicit
        // instrumentation in hot DAO paths. Query-callback installation
        // via reflection is fragile across framework versions and was
        // removed. For implicit coverage, wire logSlowQuery() into
        // `DatabaseProvider.getDatabase()` or individual repository
        // methods that perform the most expensive reads.
    }

    inline fun <T> trace(
        sql: String,
        args: List<Any?> = emptyList(),
        block: () -> T
    ): T {
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            block()
        } finally {
            record(
                sql = sql,
                args = args.map { it?.toString().orEmpty() },
                durationMs = SystemClock.elapsedRealtime() - startedAt
            )
        }
    }

    fun record(
        sql: String,
        args: List<String> = emptyList(),
        durationMs: Long
    ) {
        val operation = classify(sql)
        val threshold = thresholdFor(operation)
        if (durationMs < threshold) return

        val entry = SlowQueryEntry(
            sql = sql.sanitizeSql(),
            args = args.map { it.sanitizeArg() },
            durationMs = durationMs,
            operation = operation
        )

        synchronized(entries) {
            while (entries.size >= MAX_ENTRIES) {
                entries.removeFirst()
            }
            entries.addLast(entry)
        }
        runCatching {
            Log.w(TAG, "${operation.name} took ${durationMs}ms: ${entry.sql}")
        }
    }

    fun snapshot(): List<SlowQueryEntry> {
        return synchronized(entries) {
            entries.toList()
        }
    }

    fun clearForTests() {
        synchronized(entries) {
            entries.clear()
        }
    }

    private fun classify(sql: String): SlowQueryOperation {
        val normalized = sql.trimStart().uppercase()
        return when {
            normalized.startsWith("SELECT") ||
                normalized.startsWith("PRAGMA") ||
                normalized.startsWith("WITH") -> SlowQueryOperation.READ
            normalized.startsWith("BEGIN") ||
                normalized.startsWith("COMMIT") ||
                normalized.startsWith("END") -> SlowQueryOperation.BATCH
            normalized.startsWith("INSERT") ||
                normalized.startsWith("UPDATE") ||
                normalized.startsWith("DELETE") ||
                normalized.startsWith("REPLACE") ||
                normalized.startsWith("ALTER") ||
                normalized.startsWith("CREATE") ||
                normalized.startsWith("DROP") -> SlowQueryOperation.WRITE
            else -> SlowQueryOperation.UNKNOWN
        }
    }

    private fun thresholdFor(operation: SlowQueryOperation): Long {
        return when (operation) {
            SlowQueryOperation.READ -> SLOW_READ_MS
            SlowQueryOperation.WRITE -> SLOW_WRITE_MS
            SlowQueryOperation.BATCH -> SLOW_BATCH_MS
            SlowQueryOperation.UNKNOWN -> SLOW_WRITE_MS
        }
    }

    private fun String.sanitizeSql(): String {
        return trim().replace(Regex("\\s+"), " ").take(500)
    }

    private fun String.sanitizeArg(): String {
        var result = this
        val lowercase = result.lowercase()
        if (lowercase.contains("password") || lowercase.contains("token") || lowercase.contains("passwd") || lowercase.contains("secret")) {
            return "[REDACTED]"
        }
        result = result.replace(
            Regex("(?i)(/(?:live|movie|series))/([^/]+)/([^/]+)/"),
            "$1/[REDACTED]/[REDACTED]/",
        )
        if (result.startsWith("http://") || result.startsWith("https://") || result.contains("://")) {
            val urlParts = result.split("?", limit = 2)
            if (urlParts.size == 2) {
                val queryParams = urlParts[1].split("&").map { param ->
                    val pair = param.split("=", limit = 2)
                    if (pair.size == 2) {
                        val key = pair[0]
                        val lowerKey = key.lowercase()
                        if (lowerKey.contains("pass") || lowerKey.contains("token") || lowerKey.contains("user") || lowerKey.contains("key") || lowerKey.contains("secret")) {
                            "$key=REDACTED"
                        } else {
                            param
                        }
                    } else {
                        param
                    }
                }.joinToString("&")
                result = urlParts[0] + "?" + queryParams
            }
        }
        return result.take(120)
    }
}
