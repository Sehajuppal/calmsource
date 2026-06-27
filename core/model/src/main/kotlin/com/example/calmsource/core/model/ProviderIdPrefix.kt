package com.example.calmsource.core.model

/**
 * String prefixes used to identify provider types from IDs.
 *
 * CalmSource uses ID-based type discrimination in several places.
 * These constants centralize the prefix conventions to avoid raw string literals.
 *
 * Example: A source with extensionId "deb-realdebrid" starts with [DEBRID],
 * so it's classified as a debrid-enhanced source.
 */
object ProviderIdPrefix {
    /** IPTV provider IDs start with this prefix (e.g., "iptv-live", "iptv-vod") */
    const val IPTV = "iptv-"
    /** Debrid provider IDs start with this prefix (e.g., "deb-rd", "deb-ad") */
    const val DEBRID = "deb-"
    /** EPG/program guide IDs start with this prefix */
    const val EPG = "epg-"
    /** Extension provider IDs start with this prefix */
    const val EXTENSION = "ext-"
    /** Channel model IDs start with this prefix */
    const val CHANNEL = "chan-"
    /** Program model IDs start with this prefix */
    const val PROGRAM = "prog-"
    /** Settings shortcut IDs start with this prefix */
    const val SETTINGS = "settings-"
}
