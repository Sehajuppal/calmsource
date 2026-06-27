package com.example.calmsource.feature.iptv.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Root auth response DTO from Xtream-Codes API */
@Serializable
data class XtreamAuthResponseDto(
    @SerialName("user_info") val userInfo: XtreamUserInfoDto? = null,
    @SerialName("server_info") val serverInfo: XtreamServerInfoDto? = null
)

/**
 * User info section of the Xtream auth response.
 *
 * Note: The API returns `password` in the response — we deserialize it to satisfy
 * the JSON contract but NEVER store, log, or persist this value.
 */
@Serializable
data class XtreamUserInfoDto(
    val username: String = "",
    val password: String = "", // received but NEVER stored/logged
    val status: String = "",
    val auth: Int = 0,
    @SerialName("exp_date") val expDate: String? = null,
    @SerialName("is_trial") val isTrial: String? = null,
    @SerialName("active_cons") val activeCons: String? = null,
    @SerialName("max_connections") val maxConnections: String? = null,
    @SerialName("allowed_output_formats") val allowedOutputFormats: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null
)

/** Server info section of the Xtream auth response */
@Serializable
data class XtreamServerInfoDto(
    val url: String = "",
    val port: String = "",
    @SerialName("https_port") val httpsPort: String = "",
    @SerialName("server_protocol") val serverProtocol: String = "http",
    val timezone: String = "",
    val timestamp_now: Int = 0
)

/** Category DTO common to live, VOD, and series endpoints */
@Serializable
data class XtreamCategoryDto(
    @SerialName("category_id") val categoryId: String = "",
    @SerialName("category_name") val categoryName: String = "",
    @SerialName("parent_id") val parentId: Int = 0
)

/** Live stream entry from `get_live_streams` endpoint */
@Serializable
data class XtreamLiveStreamDto(
    val num: Int = 0,
    val name: String = "",
    @SerialName("stream_type") val streamType: String = "",
    @SerialName("stream_id") val streamId: Int = 0,
    @SerialName("stream_icon") val streamIcon: String = "",
    @SerialName("epg_channel_id") val epgChannelId: String? = null,
    val added: String? = null,
    @SerialName("category_id") val categoryId: String = "",
    @SerialName("tv_archive") val tvArchive: Int = 0,
    @SerialName("direct_source") val directSource: String = "",
    @SerialName("tv_archive_duration") val tvArchiveDuration: Int = 0,
    @SerialName("custom_sid") val customSid: String? = null
)

/** VOD stream entry from `get_vod_streams` endpoint */
@Serializable
data class XtreamVodStreamDto(
    val num: Int = 0,
    val name: String = "",
    @SerialName("stream_type") val streamType: String = "",
    @SerialName("stream_id") val streamId: Int = 0,
    @SerialName("stream_icon") val streamIcon: String = "",
    val added: String? = null,
    @SerialName("category_id") val categoryId: String = "",
    @SerialName("container_extension") val containerExtension: String = "mp4",
    val rating: String? = null,
    @SerialName("direct_source") val directSource: String = "",
    @SerialName("custom_sid") val customSid: String? = null
)

/** Series entry from `get_series` endpoint */
@Serializable
data class XtreamSeriesDto(
    val num: Int = 0,
    val name: String = "",
    @SerialName("series_id") val seriesId: Int = 0,
    val cover: String = "",
    @SerialName("category_id") val categoryId: String = "",
    val rating: String? = null
)

/** Response wrapper for short EPG endpoint */
@Serializable
data class XtreamShortEpgResponseDto(
    @SerialName("epg_listings") val epgListings: List<XtreamEpgListingDto> = emptyList()
)

/** Individual EPG listing entry */
@Serializable
data class XtreamEpgListingDto(
    val id: String = "",
    @SerialName("epg_id") val epgId: String = "",
    val title: String = "",
    val lang: String = "",
    val start: String = "",
    val end: String = "",
    val description: String = "",
    @SerialName("channel_id") val channelId: String = ""
)
