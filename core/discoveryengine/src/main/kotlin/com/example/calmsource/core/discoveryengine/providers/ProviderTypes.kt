package com.example.calmsource.core.discoveryengine.providers

/**
 * What kind of enrichment the provider offers. A single provider may support
 * multiple types (e.g. `GenericStremioAddonProvider` can be metadata + ratings
 * + similar + subtitles + availability, depending on the addon's manifest).
 */
enum class ProviderType {
    METADATA,
    RATING,
    SIMILAR,
    SUBTITLE,
    STREAM,
    CATALOG,
    ARTWORK,
    AVAILABILITY
}

/**
 * Where the provider comes from. Used to distinguish built-in/system providers,
 * user-installed Stremio addons, external HTTP APIs, and local-only sources.
 *
 * `localOnly` returns true if the provider is never expected to make network
 * calls (cache + local pack providers fall into this category).
 */
enum class ProviderKind(val localOnly: Boolean) {
    SYSTEM(false),
    SYSTEM_API(false),
    STREMIO_ADDON(false),
    EXTERNAL_API(false),
    LOCAL_PACK(true),
    LOCAL_CACHE(true)
}
