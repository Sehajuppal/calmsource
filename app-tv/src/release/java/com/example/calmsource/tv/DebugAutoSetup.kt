package com.example.calmsource.tv

import android.content.Context

/**
 * No-op in release source set. The debug source set overrides this with
 * actual auto-setup logic that pre-fills IPTV provider credentials
 * and Stremio extension URLs on first debug launch.
 */
object DebugAutoSetup {
    suspend fun runIfNeeded(context: Context) { /* no-op in release */ }
}
