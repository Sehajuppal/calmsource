package com.example.calmsource.core.database.mapper

import com.example.calmsource.core.database.entity.XtreamSeriesEntity
import com.example.calmsource.core.database.entity.XtreamVodEntity
import com.example.calmsource.core.model.XtreamSeriesItem
import com.example.calmsource.core.model.XtreamVodItem

// ─── XtreamVodItem ↔ XtreamVodEntity ─────────────────────────────────

/**
 * Converts a domain [XtreamVodItem] to a Room [XtreamVodEntity].
 *
 * The entity ID is deterministic based on providerId + streamId, ensuring
 * that re-syncing the same provider replaces rows instead of duplicating.
 *
 * @param providerId The owning provider ID.
 * @param categoryName Optional human-readable category name to store alongside category ID.
 */
fun XtreamVodItem.toEntity(providerId: String, categoryName: String = ""): XtreamVodEntity {
    return XtreamVodEntity().apply {
        id = this@toEntity.id.ifEmpty { "xtream-vod-${providerId}-${this@toEntity.streamId}" }
        name = this@toEntity.name
        streamId = this@toEntity.streamId
        categoryId = this@toEntity.categoryId
        this.categoryName = categoryName
        poster = this@toEntity.poster ?: ""
        rating = this@toEntity.rating ?: 0.0
        containerExtension = this@toEntity.containerExtension
        addedTimestamp = this@toEntity.added
        this.providerId = providerId
    }
}

/** Converts a Room [XtreamVodEntity] back to a domain [XtreamVodItem]. */
fun XtreamVodEntity.toDomain(): XtreamVodItem {
    return XtreamVodItem(
        id = this.id,
        name = this.name,
        streamId = this.streamId,
        categoryId = this.categoryId,
        poster = this.poster.ifEmpty { null },
        rating = if (this.rating != 0.0) this.rating else null,
        containerExtension = this.containerExtension,
        added = this.addedTimestamp,
        providerId = this.providerId
    )
}

// ─── XtreamSeriesItem ↔ XtreamSeriesEntity ───────────────────────────

/**
 * Converts a domain [XtreamSeriesItem] to a Room [XtreamSeriesEntity].
 *
 * @param providerId The owning provider ID.
 * @param categoryName Optional human-readable category name.
 */
fun XtreamSeriesItem.toEntity(providerId: String, categoryName: String = ""): XtreamSeriesEntity {
    return XtreamSeriesEntity().apply {
        id = this@toEntity.id.ifEmpty { "xtream-series-${providerId}-${this@toEntity.seriesId}" }
        name = this@toEntity.name
        seriesId = this@toEntity.seriesId
        categoryId = this@toEntity.categoryId
        this.categoryName = categoryName
        poster = this@toEntity.poster ?: ""
        rating = this@toEntity.rating ?: 0.0
        this.providerId = providerId
    }
}

/** Converts a Room [XtreamSeriesEntity] back to a domain [XtreamSeriesItem]. */
fun XtreamSeriesEntity.toDomain(): XtreamSeriesItem {
    return XtreamSeriesItem(
        id = this.id,
        name = this.name,
        seriesId = this.seriesId,
        categoryId = this.categoryId,
        poster = this.poster.ifEmpty { null },
        rating = if (this.rating != 0.0) this.rating else null,
        providerId = this.providerId
    )
}
