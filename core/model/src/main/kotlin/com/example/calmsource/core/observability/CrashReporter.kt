package com.example.calmsource.core.observability

/**
 * Lightweight, dependency-free crash/observability facade.
 *
 * Core and feature modules call into this object to leave breadcrumbs, set diagnostic keys, and
 * record non-fatal exceptions without taking a direct dependency on Firebase Crashlytics. The
 * application modules wire the real Crashlytics-backed delegates at startup; until then (and in
 * unit tests) every call is a safe no-op.
 *
 * Callers MUST NOT pass secrets (passwords, tokens, raw playback URLs) into messages or keys.
 */
object CrashReporter {

    @Volatile
    var logDelegate: ((String) -> Unit)? = null

    @Volatile
    var setKeyDelegate: ((String, String) -> Unit)? = null

    @Volatile
    var recordDelegate: ((Throwable) -> Unit)? = null

    /** Leaves a breadcrumb in the crash report timeline. Best-effort; never throws. */
    fun log(message: String) {
        runCatching { logDelegate?.invoke(message) }
    }

    /** Sets a non-sensitive diagnostic key/value attached to subsequent reports. Never throws. */
    fun setKey(key: String, value: String) {
        runCatching { setKeyDelegate?.invoke(key, value) }
    }

    /**
     * Records a non-fatal exception with an optional [context] breadcrumb. Best-effort; never
     * throws and is safe to call before delegates are wired or in tests.
     */
    fun recordNonFatal(throwable: Throwable, context: String? = null) {
        runCatching {
            if (context != null) {
                logDelegate?.invoke(context)
            }
            recordDelegate?.invoke(throwable)
        }
    }

    /** Clears all delegates. Intended for teardown/tests. */
    fun reset() {
        logDelegate = null
        setKeyDelegate = null
        recordDelegate = null
    }
}
