/**
 * Core domain models for the CalmSource media player application.
 *
 * This file defines all shared data classes, enums, and sealed interfaces used across
 * the application's feature modules. Models are organized by domain:
 *
 * **Media** — [MediaItem], [Movie], [Show], [Episode] for content metadata
 * **Live TV** — [Channel], [Program] for live broadcast display
 * **IPTV** — [IPTVProvider], [IPTVChannel], [IPTVChannelGroup], [EPGSource],
 *            [EPGProgram], [EPGMatch] for IPTV import and EPG matching
 * **Extensions** — [ExtensionProvider], [ExtensionManifest], [ExtensionCapability]
 *                  for the Stremio-compatible extension hub
 * **Debrid** — [DebridAccount], [DebridTokenSet], [DebridAuthSession] for
 *             debrid service integration (Real-Debrid, AllDebrid, Premiumize)
 * **Search** — [SearchProviderResult], [NormalizedSearchResult], [SearchResultGroup]
 *             for the Universal Search pipeline
 * **Streaming** — [StreamSource], [WatchOption], [SourceType] for stream resolution
 * **Preferences** — [UserPreferences] for user-configurable behavior
 *
 * @see FakeData for demo/test instances of these models
 */
package com.example.calmsource.core.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

/** The broad content type for a media item. */
enum class MediaType { MOVIE, SHOW }

/**
 * A unified media content descriptor shared across all feature modules.
 *
 * [MediaItem] is the canonical representation used by search, streaming, and UI layers.
 * The [id] is a stable, opaque identifier typically prefixed with the content type
 * (e.g. `"movie-inception"`, `"show-breakingbad"`). It is used as the key in
 * [FakeData.mediaSourcesMap] and for deep-link routing.
 *
 * @property id Stable identifier, prefixed by content type (e.g. `"movie-inception"`)
 * @property rating Average rating on a 0–10 scale, or `null` if unavailable
 */
@Stable
@Serializable
data class MediaItem(
    val id: String,
    val title: String,
    val type: MediaType,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val releaseDate: String? = null,
    val rating: Double? = null,
    val externalIds: Map<String, String> = emptyMap()
)

/** A movie wrapper combining [MediaItem] metadata with movie-specific fields. */
@Immutable
data class Movie(
    val mediaItem: MediaItem,
    val durationMin: Int? = null,
    val isFavorite: Boolean = false
)

/** A TV show wrapper combining [MediaItem] metadata with show-specific fields. */
@Immutable
data class Show(
    val mediaItem: MediaItem,
    val seasonsCount: Int = 1,
    val isFavorite: Boolean = false
)

/** A single episode within a [Show], keyed by [showId] + season/episode numbers. */
@Immutable
data class Episode(
    val id: String,
    val showId: String,
    val title: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val overview: String? = null,
    val rating: Double? = null,
    val durationMin: Int? = null
)

/** A live TV channel for the channel guide. Simplified view of [IPTVChannel]. */
@Immutable
data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val streamUrl: String,
    val category: String? = null
)

/** A scheduled broadcast within a live TV [Channel]'s electronic program guide. */
@Immutable
data class Program(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startTimeMs: Long,
    val endTimeMs: Long
)

/** Operational health status for an IPTV provider or extension. */
enum class ProviderHealth { HEALTHY, SLOW, FAILED }

/** An IPTV service provider whose M3U playlist or Xtream API is imported and synced. */
data class IPTVProvider(
    val id: String,
    val name: String,
    val playlistUrl: String,
    val isEnabled: Boolean = true,
    val health: ProviderHealth = ProviderHealth.HEALTHY,
    val type: IPTVProviderType = IPTVProviderType.M3U,
    val serverUrl: String = "",
    val username: String? = null
)

/**
 * Capabilities that a Stremio-compatible extension can advertise.
 *
 * @property SOURCE_RESOLVER_PLACEHOLDER Reserved for future direct source resolution.
 */
enum class ExtensionCapability {
    CatalogProvider, SearchCatalogProvider, MetadataProvider, StreamProvider, SubtitleProvider, ConfigRequired, UnsupportedResource
}

/** Permissions that an extension may request from the host application. */
enum class ExtensionPermission {
    INTERNET, LOCAL_FILES, PLAYBACK_CONTROL, READ_METADATA
}

/** Runtime health state of an installed extension. */
enum class ExtensionHealth {
    ACTIVE, DISABLED, NEEDS_CONFIGURATION, SLOW, FAILED, INVALID_MANIFEST, UNKNOWN
}

/** A content catalog entry within an extension manifest (Stremio format). */
@Serializable
data class ExtensionCatalog(
    val type: String,
    val id: String,
    val name: String,
    val extra: List<StremioCatalogExtra>? = null
)

/**
 * Parsed Stremio-compatible extension manifest.
 *
 * @property resources Capability resource names the extension provides (e.g. `"catalog"`, `"stream"`)
 * @property types Content types the extension handles (e.g. `"movie"`, `"series"`)
 * @property behaviorHints Stremio behavior hints as string key-value pairs
 * @property rawAttributes Any extra JSON fields not mapped to known properties
 */
@Serializable
data class ExtensionManifest(
    val id: String,
    val name: String,
    val description: String? = null,
    val version: String? = null,
    val logo: String? = null,
    val resources: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val catalogs: List<ExtensionCatalog> = emptyList(),
    val behaviorHints: Map<String, String> = emptyMap(),
    val rawAttributes: Map<String, String> = emptyMap(),
    val resourceTypes: Map<String, List<String>> = emptyMap()
) {
    override fun toString(): String {
        return "ExtensionManifest(id=$id, name=$name, description=$description, version=$version, resources=$resources, types=$types)"
    }
}

fun ExtensionManifest.detectCapabilities(): Set<ExtensionCapability> {
    val caps = mutableSetOf<ExtensionCapability>()
    if (behaviorHints["configurationRequired"]?.toBoolean() == true) caps.add(ExtensionCapability.ConfigRequired)
    if (catalogs.isNotEmpty()) {
        caps.add(ExtensionCapability.CatalogProvider)
        if (catalogs.any { cat -> cat.extra?.any { it.name == "search" } == true }) {
            caps.add(ExtensionCapability.SearchCatalogProvider)
        }
    }
    resources.forEach { res ->
        when (res) {
            "catalog" -> caps.add(ExtensionCapability.CatalogProvider)
            "meta" -> caps.add(ExtensionCapability.MetadataProvider)
            "stream" -> caps.add(ExtensionCapability.StreamProvider)
            "subtitles" -> caps.add(ExtensionCapability.SubtitleProvider)
            else -> caps.add(ExtensionCapability.UnsupportedResource)
        }
    }
    return caps
}

fun ExtensionManifest.detectContentTypes(): Set<String> {
    val typesSet = mutableSetOf<String>()
    typesSet.addAll(types)
    catalogs.forEach { typesSet.add(it.type) }
    
    val known = setOf("movie", "series", "tv", "channel", "events", "anime", "other")
    val finalTypes = mutableSetOf<String>()
    typesSet.forEach { type ->
        val t = type.lowercase()
        if (known.contains(t)) finalTypes.add(t) else finalTypes.add("other")
    }
    return finalTypes
}

fun ExtensionManifest.isResourceSupported(resourceName: String, requestType: String): Boolean {
    if (resourceName == "catalog" && catalogs.any { it.type == requestType }) {
        val scopedTypes = resourceTypes[resourceName]
        return scopedTypes.isNullOrEmpty() || scopedTypes.contains(requestType)
    }
    if (!resources.contains(resourceName)) return false
    resourceTypes[resourceName]?.let { scopedTypes ->
        return scopedTypes.isEmpty() || scopedTypes.contains(requestType)
    }
    if (types.isEmpty()) return true
    if (types.contains(requestType)) return true
    return catalogs.any { it.type == requestType }
}

/**
 * An installed extension provider, combining manifest metadata with runtime state.
 *
 * @property priority Lower values are queried first during search and stream resolution.
 */
data class ExtensionProvider(
    val id: String,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val health: ExtensionHealth = ExtensionHealth.ACTIVE,
    val priority: Int = 100,
    val manifest: ExtensionManifest? = null,
    val permissions: List<ExtensionPermission> = emptyList(),
    val capabilities: Set<ExtensionCapability> = emptySet(),
    val supportedTypes: Set<String> = emptySet()
) {
    override fun toString(): String {
        val redactedUrl = url.replace(Regex("([?&])(token|apikey|secret|api_key|code|pin)=[^&]*", RegexOption.IGNORE_CASE), "$1$2=***")
        return "ExtensionProvider(id=$id, name=$name, url=$redactedUrl, isEnabled=$isEnabled, health=$health, priority=$priority, permissions=$permissions)"
    }
}

/** Result of attempting to install or parse an extension manifest. */
data class ExtensionInstallResult(
    val isSuccess: Boolean,
    val manifest: ExtensionManifest? = null,
    val error: ExtensionError? = null,
    val warnings: List<String> = emptyList()
)

/** An outbound query to an extension provider. */
data class ExtensionRequest(
    val query: String,
    val type: String,
    val mediaId: String? = null,
    val capabilities: List<ExtensionCapability> = emptyList()
)

/** Aggregated response from an extension provider query. */
data class ExtensionResponse(
    val catalogItems: List<ExtensionCatalogItem> = emptyList(),
    val metadata: ExtensionMetadataResult? = null,
    val streams: List<ExtensionStreamResult> = emptyList(),
    val subtitles: List<ExtensionSubtitleResult> = emptyList(),
    val error: ExtensionError? = null
)

/** A single catalog item returned by an extension's catalog resource. */
data class ExtensionCatalogItem(
    val id: String,
    val title: String,
    val type: String,
    val posterUrl: String? = null
)

/** Metadata result returned by an extension's metadata resource. */
data class ExtensionMetadataResult(
    val id: String,
    val title: String,
    val type: String,
    val overview: String? = null,
    val posterUrl: String? = null
)

/**
 * A stream result returned by an extension's stream resource.
 *
 * @property infoHash BitTorrent info hash for debrid cache-checking; `null` for HTTP streams
 */
data class ExtensionStreamResult(
    val id: String,
    val name: String,
    val title: String,
    val url: String,
    val extensionId: String,
    val resolution: String = "1080p",
    val sizeBytes: Long? = null,
    val infoHash: String? = null
)

/** A subtitle track returned by an extension's subtitles resource. */
data class ExtensionSubtitleResult(
    val id: String,
    val language: String,
    val url: String
)

/**
 * Typed error hierarchy for extension operations.
 *
 * Each variant carries a human-readable [message] suitable for UI display.
 */
sealed interface ExtensionError {
    val message: String
    data class Timeout(override val message: String) : ExtensionError
    data class NetworkError(override val message: String) : ExtensionError
    data class ParseError(override val message: String) : ExtensionError
    data class InvalidManifest(override val message: String) : ExtensionError
    data class PermissionDenied(override val message: String) : ExtensionError
    data class Unknown(override val message: String) : ExtensionError
}

/** Per-provider timeout configuration for extension queries. */
data class ExtensionTimeoutPolicy(
    val defaultTimeoutMs: Long = 5000L,
    val customProviderTimeoutsMs: Map<String, Long> = emptyMap()
)

/** Ordering hint for extension providers during multi-source resolution. */
data class ExtensionSourcePriority(
    val providerId: String,
    val priorityIndex: Int
)

/**
 * Supported debrid service providers.
 *
 * @property FAKE_DEMO Synthetic provider used in development and UI tests.
 */
enum class DebridProviderType {
    REAL_DEBRID,
    ALL_DEBRID,
    PREMIUMIZE,
    FAKE_DEMO
}

/**
 * Capabilities that a debrid provider can support.
 *
 * @property CLOUD_FILES_PLACEHOLDER Reserved for future cloud file management.
 * @property TRANSFERS_PLACEHOLDER Reserved for future transfer management.
 */
enum class DebridCapability {
    ACCOUNT_STATUS,
    CACHED_AVAILABILITY_CHECK,
    LINK_RESOLVE,
    DEVICE_CODE_AUTH,
    PIN_AUTH,
    API_KEY_AUTH,
    OAUTH_PKCE,
    CLOUD_FILES_PLACEHOLDER,
    TRANSFERS_PLACEHOLDER
}

/** Authentication methods supported by debrid providers. */
enum class DebridAuthMethod {
    DEVICE_CODE,
    PIN,
    API_KEY,
    OAUTH_PKCE
}

/** Runtime health status of a connected debrid account. */
enum class DebridAccountHealth {
    HEALTHY,
    SLOW,
    FAILED
}

/**
 * OAuth/API credential set for a debrid provider.
 *
 * This class overrides [toString] to mask sensitive token values in logs.
 * Tokens longer than 8 characters show only the first and last 4 characters
 * (e.g. `"RD_A...en_8"`); shorter tokens are fully masked as `"••••••••"`.
 *
 * @property expiresAt Epoch millis when [accessToken] expires; `0` if non-expiring
 * @property apiKey Used by providers that authenticate via static API key (e.g. Premiumize)
 */
@Serializable
data class DebridTokenSet(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAt: Long = 0,
    val apiKey: String? = null,
    val clientId: String? = null,
    val clientSecret: String? = null
) {
    override fun toString(): String {
        val maskedAccess = accessToken?.maskToken()
        val maskedRefresh = refreshToken?.maskToken()
        val maskedApiKey = apiKey?.maskToken()
        val maskedClientId = clientId?.maskToken()
        val maskedClientSecret = clientSecret?.maskToken()
        return "DebridTokenSet(accessToken=$maskedAccess, refreshToken=$maskedRefresh, expiresAt=$expiresAt, apiKey=$maskedApiKey, clientId=$maskedClientId, clientSecret=$maskedClientSecret)"
    }
}

/** Account status snapshot returned by a debrid provider's API. */
@Serializable
data class DebridAccountStatus(
    val username: String,
    val email: String,
    val premiumDaysRemaining: Int,
    val expirationDate: String?,
    val isPremium: Boolean
)

/** Static definition of a debrid provider and its supported capabilities. */
data class DebridProvider(
    val id: String,
    val name: String,
    val type: DebridProviderType,
    val capabilities: Set<DebridCapability>
)

/**
 * A user's connected (or disconnectable) debrid account.
 *
 * @property isConnected `true` when the user has completed authentication and tokens are stored
 * @property tokenSet Credential set; `null` when disconnected
 * @property status Latest account status from the provider; `null` when disconnected
 */
data class DebridAccount(
    val id: String,
    val providerType: DebridProviderType,
    val providerName: String,
    val isConnected: Boolean = false,
    val email: String? = null,
    val username: String? = null,
    val tokenSet: DebridTokenSet? = null,
    val status: DebridAccountStatus? = null,
    val health: DebridAccountHealth = DebridAccountHealth.HEALTHY
)

/** Session data for the OAuth 2.0 Device Code flow (used by Real-Debrid). */
data class DebridDeviceCodeSession(
    val userCode: String,
    val deviceCode: String,
    val verificationUrl: String,
    val intervalSeconds: Int,
    val expiresInSeconds: Int,
    val expiresAtMs: Long = System.currentTimeMillis() + (expiresInSeconds * 1000L)
)

/** Session data for the PIN-based authentication flow (used by AllDebrid). */
data class DebridPinSession(
    val pinUrl: String,
    val pinCode: String,
    val expiresInSeconds: Int = 600,
    val expiresAtMs: Long = System.currentTimeMillis() + (expiresInSeconds * 1000L)
)

/** Session data for direct API key authentication (used by Premiumize). */
data class DebridApiKeySession(
    val providerType: DebridProviderType
)

/**
 * Sealed interface representing an active debrid authentication session.
 *
 * Each variant wraps the session details specific to its authentication method:
 * - [DeviceCode] — OAuth 2.0 device code flow (Real-Debrid)
 * - [Pin] — PIN-based browser authorization (AllDebrid)
 * - [ApiKey] — Direct API key entry (Premiumize)
 *
 * The [id] uniquely identifies the session and [providerType] identifies which
 * debrid service the session belongs to.
 */
sealed interface DebridAuthSession {
    val id: String
    val providerType: DebridProviderType

    data class DeviceCode(
        override val id: String,
        override val providerType: DebridProviderType,
        val details: DebridDeviceCodeSession
    ) : DebridAuthSession

    data class Pin(
        override val id: String,
        override val providerType: DebridProviderType,
        val details: DebridPinSession
    ) : DebridAuthSession

    data class ApiKey(
        override val id: String,
        override val providerType: DebridProviderType,
        val details: DebridApiKeySession
    ) : DebridAuthSession
}

/** Result of checking whether a torrent is instantly available on a debrid service. */
data class DebridCachedAvailability(
    val infoHash: String,
    val isCached: Boolean,
    val filesList: List<String> = emptyList()
)

/**
 * Request to resolve a stream link through a debrid provider.
 *
 * @property infoHash BitTorrent info hash to resolve
 * @property fileIndex Index of the specific file within the torrent, if multi-file
 * @property magnetUrl Full magnet URI; alternative to [infoHash]
 * @property hostLink Direct hoster link to unrestrict (non-torrent debrid usage)
 */
data class DebridResolveRequest(
    val infoHash: String,
    val fileIndex: Int? = null,
    val magnetUrl: String? = null,
    val hostLink: String? = null
)

/** Outcome status of a debrid link resolution attempt. */
enum class DebridResolveStatus {
    SUCCESS,
    FAILURE,
    TIMEOUT,
    UNSUPPORTED,
    UNAVAILABLE
}

/** Result of a debrid link resolution containing the unrestricted stream URL. */
data class DebridResolveResult(
    val url: String? = null,
    val error: String? = null,
    val status: DebridResolveStatus = DebridResolveStatus.SUCCESS
)

/**
 * Typed error hierarchy for debrid operations.
 *
 * Each variant carries a human-readable [message] suitable for UI display.
 */
sealed interface DebridError {
    data class RateLimit(val message: String) : DebridError
    data class BadCredentials(val message: String) : DebridError
    data class NetworkError(val message: String) : DebridError
    data class ExpiredSession(val message: String) : DebridError
    data class Unknown(val message: String) : DebridError
}

/** Rate-limiting and batch-size constraints for a debrid provider's API. */
data class DebridProviderLimits(
    val maxCachedAvailabilityBatch: Int = 100,
    val rateLimitRequestsPerMin: Int = 60
)

/** UI-facing connection lifecycle state for debrid authentication flows. */
enum class DebridConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    EXPIRED,
    ERROR
}

/** Per-provider user preference for debrid behavior. */
data class DebridPreference(
    val providerId: String,
    val isPrimary: Boolean,
    val autoCachedPick: Boolean = true
)

/** The origin type of a playable stream source. */
enum class SourceType { IPTV, EXTENSION, DEBRID }

/**
 * A resolved playable stream from any source (IPTV, extension, or debrid).
 *
 * @property id Unique identifier for this stream source instance
 * @property name Human-readable release/file name (e.g. `"Movie.2017.1080p.BluRay.mkv"`)
 * @property url Direct playback URL (HLS, DASH, or direct HTTP link)
 * @property extensionId ID of the provider that supplied this stream (IPTV provider ID,
 *                       extension ID, or debrid account ID)
 * @property resolution Display resolution label: `"4K"`, `"1080p"`, `"720p"`, or `"SD"`
 * @property videoCodec Video codec identifier (e.g. `"HEVC"`, `"AVC"`, `"AV1"`)
 * @property audioCodec Audio codec identifier (e.g. `"E-AC3"`, `"AAC"`, `"DTS"`)
 * @property sizeBytes File size in bytes; `null` for live streams
 * @property seeds Torrent seed count; `null` for IPTV and direct HTTP links
 * @property language Primary audio language as ISO 639-1 code or full name
 */
data class StreamSource(
    val id: String,
    val name: String,
    val url: String,
    val extensionId: String,
    val resolution: String,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val sizeBytes: Long? = null,
    val seeds: Int? = null,
    val language: String,
    val isSubbed: Boolean = false,
    val isDubbed: Boolean = false,
    val isDualAudio: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val sourceExtensionName: String? = null,
    val rawTitle: String? = null
)

/**
 * A user-facing playback option combining a [StreamSource] with display metadata.
 *
 * @property languageLabel Human-readable language description shown in the stream picker,
 *                         e.g. `"Hindi"`, `"English (Subbed)"`, `"Hindi + English (Dual Audio)"`
 */
data class WatchOption(
    val id: String,
    val title: String,
    val source: StreamSource,
    val type: SourceType,
    val languageLabel: String
)

/** Availability metadata for a stream, enriched with debrid cache status. */
data class SourceAvailability(
    val streamSource: StreamSource,
    val isCachedOnDebrid: Boolean = false,
    val isIptvVod: Boolean = false
)

/** A search hit combining media metadata with source availability information. */
data class SearchResult(
    val mediaItem: MediaItem,
    val availableFrom: List<SourceType>,
    val languages: List<String>,
    val bestMatchOption: WatchOption? = null,
    val isDualAudio: Boolean = false
)

/** An available language option for user preference selection. */
data class LanguageOption(
    val code: String,
    val name: String
)

/**
 * User-configurable playback and source-selection preferences.
 *
 * These preferences drive the automatic source ranking algorithm in the search and
 * stream picker screens. When [askBeforeChoosingSource] is `false`, the system
 * auto-selects the best [WatchOption] based on these weights. When `true`, the
 * full stream picker is always shown.
 *
 * @property sourcePriority Strategy label: `"Auto-pick best source"` or manual ordering
 * @property preferCachedDebrid Prefer debrid-cached torrents over uncached ones
 * @property preferIptvExactMatch Prefer IPTV VOD streams with exact title matches
 * @property primaryDebridProvider ID of the preferred debrid provider for auto-pick
 */
data class UserPreferences(
    val primaryLanguage: String = "Hindi",
    val secondaryLanguage: String = "English",
    val subtitleLanguage: String = "English",
    val sourcePriority: String = "Auto-pick best source",
    val preferCachedDebrid: Boolean = true,
    val preferIptvExactMatch: Boolean = true,
    val preferFhdOrBetter: Boolean = true,
    val hideLowQuality: Boolean = true,
    val hideDuplicates: Boolean = true,
    val preferOriginalAudio: Boolean = false,
    val preferDubbedAudio: Boolean = false,
    val preferDualAudio: Boolean = true,
    val preferHighestQuality: Boolean = true,
    val preferLowerDataUsage: Boolean = false,
    val askBeforeChoosingSource: Boolean = false,
    val askBeforeDebrid: Boolean = false,
    val hideNonCached: Boolean = false,
    val showDebridStatusInStreamPicker: Boolean = true,
    val primaryDebridProvider: String = "",
    val allowCleartextUserSources: Boolean = false,
    val separateIptvCategoriesByProvider: Boolean = false
)

/** Current media playback state for the player UI. */
data class PlaybackState(
    val currentWatchOption: WatchOption? = null,
    val isPlaying: Boolean = false,
    val playbackPositionMs: Long = 0,
    val durationMs: Long = 0
)

/** Health snapshot of a single provider, including measured latency. */
data class ProviderHealthState(
    val providerId: String,
    val providerName: String,
    val health: ProviderHealth,
    val latencyMs: Long? = null
)

// ─── Search Engine Models ────────────────────────────────────────────

/** User-submitted search query with optional resolution and language filters. */
data class SearchQuery(
    val query: String,
    val resolutionFilter: String? = null,
    val languageFilter: String? = null
)

/** Tracks a single search session for deduplication and analytics. */
data class SearchSession(
    val id: String,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Raw results from a single search provider (IPTV, extension, or debrid).
 *
 * This is a multi-type result container: a single provider may return any combination
 * of [mediaItems], [streamSources], [channels], [programs], and [settingsRoutes].
 * The Universal Search engine normalizes these into [NormalizedSearchResult] instances
 * before display.
 *
 * @property latencyMs Time in milliseconds the provider took to respond
 * @property error Non-null if the provider failed; partial results may still be present
 * @property settingsRoutes Deep-link routes to settings screens matching the query
 */
data class SearchProviderResult(
    val providerId: String,
    val providerName: String,
    val query: SearchQuery,
    val mediaItems: List<MediaItem> = emptyList(),
    val streamSources: List<StreamSource> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val programs: List<Program> = emptyList(),
    val settingsRoutes: List<String> = emptyList(),
    val latencyMs: Long = 0,
    val error: Throwable? = null
)

/**
 * A deduplicated, ranked search result ready for UI display.
 *
 * @property score Relevance score computed by the search ranking algorithm. Higher is better.
 *                 Factors include: title match quality, user language preference, resolution,
 *                 debrid cache status, and source type priority.
 * @property watchOptions All available [WatchOption]s for this media item across providers
 * @property bestMatchOption The top-ranked option according to [UserPreferences]
 */
data class NormalizedSearchResult(
    val mediaItem: MediaItem,
    val availableFrom: List<SourceType>,
    val languages: List<String>,
    val watchOptions: List<WatchOption>,
    val bestMatchOption: WatchOption? = null,
    val isDualAudio: Boolean = false,
    val score: Int = 0
)

/** Categories for grouping search results in the UI. */
enum class SearchGroupType {
    TOP_RESULTS,
    MOVIES,
    SHOWS,
    LIVE_CHANNELS,
    LIVE_PROGRAMS,
    IPTV_VOD,
    EXTENSION_RESULTS,
    SETTINGS
}

/** A titled group of search results for a specific [SearchGroupType] category. */
data class SearchResultGroup(
    val groupType: SearchGroupType,
    val title: String,
    val results: List<NormalizedSearchResult>
)

/** Timeout configuration for the Universal Search engine's provider queries. */
data class SearchTimeoutPolicy(
    val defaultTimeoutMs: Long = 5000L,
    val providerTimeoutsMs: Map<String, Long> = emptyMap()
)

// ─── IPTV & EPG Core Models ─────────────────────────────────────────

/** A parsed IPTV playlist descriptor. */
data class IPTVPlaylist(
    val id: String,
    val providerId: String,
    val name: String,
    val url: String,
    val channelsCount: Int
)

/**
 * A single channel parsed from an M3U playlist.
 *
 * @property tvgId The `tvg-id` attribute from `#EXTINF`, used for EPG matching
 * @property tvgName The `tvg-name` attribute; fallback display name
 * @property rawAttributes All key-value pairs parsed from the `#EXTINF` line
 */
@Stable
data class IPTVChannel(
    val id: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val tvgLogo: String? = null,
    val groupTitle: String? = null,
    val name: String,
    val streamUrl: String,
    val providerId: String,
    val rawAttributes: Map<String, String> = emptyMap(),
    val language: String? = null,
    val country: String? = null
) {
    val safeSourceId: String by lazy { generateSafeSourceId(streamUrl) }

    val isVod: Boolean
        get() = when (rawAttributes["xtream_content_type"]?.lowercase()) {
            "live" -> false
            "vod", "series" -> true
            else -> groupTitle?.contains("VOD", ignoreCase = true) == true ||
                    groupTitle?.contains("Movies", ignoreCase = true) == true ||
                    groupTitle?.contains("Series", ignoreCase = true) == true ||
                    streamUrl.contains("/movie/") ||
                    streamUrl.contains("/series/")
        }
}

/** A group of IPTV channels sharing the same `group-title` attribute. */
@Stable
data class IPTVChannelGroup(
    val title: String,
    val providerId: String,
    val channels: List<IPTVChannel>
)

/**
 * An XMLTV EPG data source associated with an IPTV provider.
 *
 * @property lastSyncMs Epoch millis of the last successful sync; `0` if never synced
 */
data class EPGSource(
    val id: String,
    val providerId: String,
    val name: String,
    val url: String,
    val lastSyncMs: Long = 0
)

/** A single program entry parsed from XMLTV EPG data. */
data class EPGProgram(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String?,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val subtitle: String? = null,
    val category: String? = null,
    val language: String? = null,
    val episodeNum: String? = null
)

/** Classification of how an IPTV channel was matched to its EPG data. */
enum class EPGMatchType {
    /** Channel's tvg-id exactly matches an EPG channel ID. */
    EXACT_ID,
    /** Channel's name matches after normalization (lowercase, strip non-alphanumeric). */
    NORMALIZED_NAME,
    /** Channel name partially contains or is contained by an EPG channel ID. */
    FUZZY,
    /** User manually assigned this EPG mapping. */
    MANUAL,
    /** No EPG match found. */
    NONE
}

/**
 * Records the EPG match result for a single IPTV channel.
 *
 * @property channelId The IPTV channel's internal ID
 * @property epgId The matched EPG channel ID, or empty string if [matchType] is [EPGMatchType.NONE]
 * @property matchType How the match was determined; see [EPGMatchType] for details
 */
data class EPGMatch(
    val channelId: String,
    val epgId: String,
    val matchType: EPGMatchType
)

/** Result of importing an M3U playlist via [M3UParser]. */
data class PlaylistParseStats(
    val totalLines: Int = 0,
    val extInfLines: Int = 0,
    val parsedChannels: Int = 0,
    val skippedChannels: Int = 0,
    val duplicateChannels: Int = 0,
    val malformedEntries: Int = 0,
    val missingName: Int = 0,
    val missingUrl: Int = 0,
    val invalidUrl: Int = 0,
    val durationMs: Long = 0
)

data class PlaylistImportResult(
    val isSuccess: Boolean,
    val channelCount: Int = 0,
    val warnings: List<String> = emptyList(),
    val stats: PlaylistParseStats = PlaylistParseStats(parsedChannels = channelCount)
)

/** Result of importing XMLTV EPG data via [XMLTVParser]. */
data class EPGParseStats(
    val totalProgramElements: Int = 0,
    val parsedPrograms: Int = 0,
    val skippedPrograms: Int = 0,
    val malformedPrograms: Int = 0,
    val missingChannelPrograms: Int = 0,
    val invalidTimePrograms: Int = 0,
    val massiveProgramElements: Int = 0,
    val outsideWindowPrograms: Int = 0,
    val durationMs: Long = 0
)

data class EPGImportResult(
    val isSuccess: Boolean,
    val programs: List<EPGProgram> = emptyList(),
    val channelIds: Set<String> = programs.mapTo(linkedSetOf()) { it.channelId },
    val warnings: List<String> = emptyList(),
    val stats: EPGParseStats = EPGParseStats(parsedPrograms = programs.size)
)

/** Sync lifecycle state for an IPTV provider's playlist/EPG refresh. */
enum class ProviderSyncStatus {
    IDLE, SYNCING, SUCCESS, ERROR
}

/** Observable sync progress for a single IPTV provider. */
data class ProviderSyncState(
    val providerId: String,
    val status: ProviderSyncStatus,
    val progressPercent: Int = 0,
    val error: String? = null,
    val warning: String? = null
)

data class StreamSearchUiState(
    val isLoading: Boolean = true,
    val watchOptions: List<WatchOption> = emptyList(),
    val subtitles: List<StremioSubtitle> = emptyList(),
    val errors: List<String> = emptyList(),
    val failedExtensions: List<String> = emptyList()
)

enum class SortingPreference {
    BEST_MATCH,
    HIGHEST_QUALITY
}
