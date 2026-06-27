package com.example.calmsource.core.discoveryengine.database

import android.content.Context

/**
 * Feature flags for the discovery search engine.
 *
 * [enableFuzzyFallback] enables typo-tolerant (Levenshtein) search candidates when FTS/substring
 * results are sparse. It is bounded by a candidate cap (and a smaller cap under low memory) inside
 * the repository, so enabling it is safe. The in-memory flag defaults to `false`; call
 * [warmBestEffort] at startup to hydrate the user's persisted choice and [setEnabledBestEffort]
 * from a settings toggle to change it.
 */
object DiscoverySearchFeatureFlags {
    private const val PREFS_NAME = "discovery_search_flags"
    private const val KEY_FUZZY = "enable_fuzzy_fallback"

    @Volatile
    var enableFuzzyFallback: Boolean = false

    fun warmBestEffort(context: Context) {
        runCatching {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            enableFuzzyFallback = prefs.getBoolean(KEY_FUZZY, false)
        }
    }

    fun setEnabledBestEffort(context: Context, enabled: Boolean) {
        enableFuzzyFallback = enabled
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_FUZZY, enabled)
                .apply()
        }
    }
}
