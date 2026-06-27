package com.example.calmsource.core.model

/**
 * Display metadata extensions for [DebridProviderType].
 *
 * Centralizes the provider name/description resolution that was previously
 * duplicated 4+ times across mobile and TV settings screens.
 */

/** Returns the user-facing display name for a debrid provider type. */
fun DebridProviderType.displayName(): String = when (this) {
    DebridProviderType.REAL_DEBRID -> "Real-Debrid"
    DebridProviderType.ALL_DEBRID -> "AllDebrid"
    DebridProviderType.PREMIUMIZE -> "Premiumize"
    DebridProviderType.FAKE_DEMO -> "Demo Debrid"
}

/** Returns a brief description of each debrid provider for settings UI. */
fun DebridProviderType.description(): String = when (this) {
    DebridProviderType.REAL_DEBRID -> "Popular debrid service with device code auth"
    DebridProviderType.ALL_DEBRID -> "AllDebrid with PIN-based authentication"
    DebridProviderType.PREMIUMIZE -> "Premiumize with API key authentication"
    DebridProviderType.FAKE_DEMO -> "Demo provider for testing"
}
