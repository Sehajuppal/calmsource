package com.example.calmsource.feature.iptv.xtream

import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.XtreamCategory
import com.example.calmsource.core.model.XtreamLiveChannel
import com.example.calmsource.core.model.XtreamServerInfo
import com.example.calmsource.core.model.XtreamSeriesItem
import com.example.calmsource.core.model.XtreamShortEpgProgram
import com.example.calmsource.core.model.XtreamUserInfo
import com.example.calmsource.core.model.XtreamVodItem

// ─── DTO → Domain Mappers ────────────────────────────────────────────

/** Maps a category DTO to the domain [XtreamCategory] model. */
fun XtreamCategoryDto.toDomain(): XtreamCategory = XtreamCategory(
    id = categoryId,
    name = categoryName,
    parentId = if (parentId != 0) parentId.toString() else null
)

/**
 * Maps a live stream DTO to the domain [XtreamLiveChannel] model.
 *
 * @param providerId The provider that owns this channel, used as id prefix for uniqueness.
 */
fun XtreamLiveStreamDto.toDomain(providerId: String): XtreamLiveChannel = XtreamLiveChannel(
    id = "${providerId}_live_${streamId}",
    name = name,
    streamId = streamId.toString(),
    categoryId = categoryId,
    logo = streamIcon.ifEmpty { null },
    epgChannelId = epgChannelId.orEmpty(),
    tvArchive = tvArchive != 0,
    tvArchiveDuration = tvArchiveDuration
)

/**
 * Maps a VOD stream DTO to the domain [XtreamVodItem] model.
 *
 * @param providerId The provider that owns this VOD item.
 */
fun XtreamVodStreamDto.toDomain(providerId: String): XtreamVodItem = XtreamVodItem(
    id = "${providerId}_vod_${streamId}",
    name = name,
    streamId = streamId.toString(),
    categoryId = categoryId,
    poster = streamIcon.ifEmpty { null },
    rating = rating?.toDoubleOrNull(),
    containerExtension = containerExtension,
    added = added?.toLongOrNull() ?: 0L,
    providerId = providerId
)

/**
 * Maps a series DTO to the domain [XtreamSeriesItem] model.
 *
 * @param providerId The provider that owns this series.
 */
fun XtreamSeriesDto.toDomain(providerId: String): XtreamSeriesItem = XtreamSeriesItem(
    id = "${providerId}_series_${seriesId}",
    name = name,
    seriesId = seriesId.toString(),
    categoryId = categoryId,
    poster = cover.ifEmpty { null },
    rating = rating?.toDoubleOrNull(),
    providerId = providerId
)

// ─── Domain → Domain Mappers ─────────────────────────────────────────

/**
 * Converts an [XtreamLiveChannel] to a generic [IPTVChannel] for unified channel lists.
 *
 * The stream URL is left empty because Xtream stream URLs are built lazily at playback
 * time using server URL + credentials + stream ID. This avoids persisting credentials
 * in the channel model.
 *
 * @param providerId The provider that owns this channel.
 */
fun XtreamLiveChannel.toIPTVChannel(providerId: String): IPTVChannel = IPTVChannel(
    id = id,
    tvgId = epgChannelId.ifEmpty { null },
    tvgName = name,
    tvgLogo = logo,
    groupTitle = categoryId,
    name = name,
    streamUrl = XtreamStreamUrlBuilder.createPseudoUrl(providerId, streamId) ?: "xtream://stream_id/$providerId/${if (streamId.isBlank()) "0" else streamId}",
    providerId = providerId,
    rawAttributes = buildMap {
        put("xtream_stream_id", streamId)
        put("xtream_source", "true")
        put("xtream_content_type", "live")
        if (tvArchive) put("tv_archive", "1")
        if (tvArchiveDuration > 0) put("tv_archive_duration", tvArchiveDuration.toString())
    }
)

// ─── Searchable VOD Helper ───────────────────────────────────────────

/**
 * Lightweight searchable representation of a VOD item for quick filtering.
 * Does not contain stream URL — that's built at playback time.
 */
data class XtreamSearchableVod(
    val name: String,
    val streamId: String,
    val categoryId: String,
    val poster: String?,
    val rating: Double?,
    val containerExtension: String,
    val providerId: String
)

/** Converts an [XtreamVodItem] to a lightweight [XtreamSearchableVod]. */
fun XtreamVodItem.toSearchableVod(providerId: String): XtreamSearchableVod = XtreamSearchableVod(
    name = name,
    streamId = streamId,
    categoryId = categoryId,
    poster = poster,
    rating = rating,
    containerExtension = containerExtension,
    providerId = providerId
)

// ─── EPG & Auth Mappers ─────────────────────────────────────────────

/** Maps an EPG listing DTO to the domain [XtreamShortEpgProgram] model. */
fun XtreamEpgListingDto.toDomain(): XtreamShortEpgProgram = XtreamShortEpgProgram(
    id = id,
    epgId = epgId,
    title = title,
    language = lang,
    startTimestamp = start.toLongOrNull() ?: 0L,
    endTimestamp = end.toLongOrNull() ?: 0L,
    description = description
)

/**
 * Maps user info DTO to the domain [XtreamUserInfo].
 * The password field from the DTO is intentionally NOT propagated.
 */
fun XtreamUserInfoDto.toDomain(): XtreamUserInfo = XtreamUserInfo(
    username = username,
    status = status,
    expirationDate = expDate?.toLongOrNull(),
    isTrial = isTrial == "1",
    activeConnections = activeCons?.toIntOrNull() ?: 0,
    maxConnections = maxConnections?.toIntOrNull() ?: 0,
    allowedOutputFormats = allowedOutputFormats
)

/** Maps server info DTO to the domain [XtreamServerInfo]. */
fun XtreamServerInfoDto.toDomain(): XtreamServerInfo = XtreamServerInfo(
    url = url,
    port = port.toIntOrNull() ?: 0,
    httpsPort = httpsPort.toIntOrNull() ?: 0,
    serverProtocol = serverProtocol,
    timezone = timezone
)
