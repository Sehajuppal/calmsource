package com.example.calmsource.core.model

/** Type of IPTV Provider */
enum class IPTVProviderType { M3U, XTREAM }

/** Configuration for an Xtream provider */
data class XtreamProviderConfig(
    val id: String,
    val name: String,
    val serverUrl: String,
    val username: String
)

/** Reference to Xtream credentials stored securely */
data class XtreamCredentialsRef(
    val providerId: String,
    val username: String
)

/** Sync status of Xtream API */
data class XtreamSyncStatus(
    val providerId: String,
    val lastSyncMs: Long,
    val isSyncing: Boolean,
    val error: String? = null
)

/** A category in Xtream API */
data class XtreamCategory(
    val id: String,
    val name: String,
    val parentId: String? = null
)

/** A live channel in Xtream API */
data class XtreamLiveChannel(
    val id: String,
    val name: String,
    val streamId: String,
    val categoryId: String,
    val logo: String? = null,
    val epgChannelId: String = "",
    val tvArchive: Boolean = false,
    val tvArchiveDuration: Int = 0
)

/** A VOD item in Xtream API */
data class XtreamVodItem(
    val id: String,
    val name: String,
    val streamId: String,
    val categoryId: String,
    val poster: String? = null,
    val rating: Double? = null,
    val containerExtension: String = "mp4",
    val added: Long = 0,
    val providerId: String = ""
)

/** A Series item in Xtream API */
data class XtreamSeriesItem(
    val id: String,
    val name: String,
    val seriesId: String,
    val categoryId: String,
    val poster: String? = null,
    val rating: Double? = null,
    val providerId: String = ""
)

/** A playable episode entry from an Xtream series catalog. */
data class XtreamSeriesEpisode(
    val id: String,
    val seriesId: String,
    val episodeId: String,
    val title: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val containerExtension: String = "mp4",
    val providerId: String = ""
)

/** Auth result from Xtream API */
data class XtreamAuthResult(
    val isAuthenticated: Boolean,
    val userInfo: XtreamUserInfo? = null,
    val serverInfo: XtreamServerInfo? = null,
    val error: String? = null,
    // Legacy fields used by XtreamApiClientImpl — prefer userInfo/serverInfo for new code
    val username: String? = null,
    val status: String? = null,
    val expirationTimestamp: Long? = null,
    val maxConnections: String? = null,
    val activatedChannels: String? = null
)

/** User info from Xtream API auth response */
data class XtreamUserInfo(
    val username: String = "",
    val status: String = "",
    val expirationDate: Long? = null,
    val isTrial: Boolean = false,
    val activeConnections: Int = 0,
    val maxConnections: Int = 0,
    val allowedOutputFormats: List<String> = emptyList()
)

/** Server info from Xtream API auth response */
data class XtreamServerInfo(
    val url: String = "",
    val port: Int = 0,
    val httpsPort: Int = 0,
    val serverProtocol: String = "http",
    val timezone: String = ""
)

/** Sync progress stages */
enum class XtreamSyncStage {
    IDLE,
    VALIDATING,
    SYNCING_LIVE_CATEGORIES,
    SYNCING_LIVE_STREAMS,
    SYNCING_VOD_CATEGORIES,
    SYNCING_VOD_STREAMS,
    SYNCING_SERIES_CATEGORIES,
    SYNCING_SERIES,
    SYNCING_EPG,
    COMPLETE,
    FAILED
}

/** Short user-facing label for sync UI banners and overlays. */
fun XtreamSyncStage.userLabel(): String = when (this) {
    XtreamSyncStage.IDLE -> "Ready"
    XtreamSyncStage.VALIDATING -> "Validating account…"
    XtreamSyncStage.SYNCING_LIVE_CATEGORIES,
    XtreamSyncStage.SYNCING_LIVE_STREAMS -> "Syncing live channels…"
    XtreamSyncStage.SYNCING_VOD_CATEGORIES,
    XtreamSyncStage.SYNCING_VOD_STREAMS -> "Syncing movies…"
    XtreamSyncStage.SYNCING_SERIES_CATEGORIES,
    XtreamSyncStage.SYNCING_SERIES -> "Syncing series…"
    XtreamSyncStage.SYNCING_EPG -> "Syncing program guide…"
    XtreamSyncStage.COMPLETE -> "Sync complete"
    XtreamSyncStage.FAILED -> "Sync failed"
}

/** Progress state for Xtream sync */
data class XtreamSyncProgress(
    val providerId: String,
    val stage: XtreamSyncStage = XtreamSyncStage.IDLE,
    val progressPercent: Int = 0,
    val liveChannelCount: Int = 0,
    val vodCount: Int = 0,
    val seriesCount: Int = 0,
    val error: String? = null,
    val warning: String? = null
)

/** Short EPG program from Xtream API */
data class XtreamShortEpgProgram(
    val id: String,
    val epgId: String,
    val title: String,
    val language: String = "",
    val startTimestamp: Long = 0,
    val endTimestamp: Long = 0,
    val description: String = ""
)
