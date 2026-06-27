package com.example.calmsource.tv

import android.content.ContentValues
import android.content.Context
import android.os.Build
import androidx.tvprovider.media.tv.TvContractCompat
import com.example.calmsource.core.model.CalmSourceDeepLink
import com.example.calmsource.core.model.ContinueWatchingItem
import com.example.calmsource.core.model.UserMemoryContentType

object TvWatchNextPublisher {
    private const val PREFS_NAME = "mission27_watch_next_ids"
    private const val COLUMN_TYPE = "type"
    private const val COLUMN_TITLE = "title"
    private const val COLUMN_SHORT_DESCRIPTION = "short_description"
    private const val COLUMN_POSTER_ART_URI = "poster_art_uri"
    private const val COLUMN_INTENT_URI = "intent_uri"
    private const val COLUMN_INTERNAL_PROVIDER_ID = "internal_provider_id"
    private const val COLUMN_LAST_PLAYBACK_POSITION_MILLIS = "last_playback_position_millis"
    private const val COLUMN_DURATION_MILLIS = "duration_millis"

    fun publish(
        context: Context,
        items: List<ContinueWatchingItem>
    ): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return 0
        val appContext = context.applicationContext ?: context
        val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingIds = preferences.all.mapNotNull { (key, value) ->
            (value as? Long)?.let { key to it }
        }.toMap()
        val activeKeys = items.mapTo(linkedSetOf()) { it.reference.itemKey }
        val editor = preferences.edit()
        var publishedCount = 0

        existingIds
            .filterKeys { it !in activeKeys }
            .forEach { (itemKey, programId) ->
                runCatching {
                    appContext.contentResolver.delete(
                        TvContractCompat.buildWatchNextProgramUri(programId),
                        null,
                        null
                    )
                }
                editor.remove(itemKey)
            }

        items.take(MAX_WATCH_NEXT_ITEMS).forEach { item ->
            val values = buildProgramValues(appContext, item)
            val existingId = existingIds[item.reference.itemKey]
            val programId = if (existingId != null) {
                val updated = runCatching {
                    appContext.contentResolver.update(
                        TvContractCompat.buildWatchNextProgramUri(existingId),
                        values,
                        null,
                        null
                    )
                }.getOrDefault(0)
                existingId.takeIf { updated > 0 }
            } else {
                null
            } ?: runCatching {
                appContext.contentResolver
                    .insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, values)
                    ?.lastPathSegment
                    ?.toLongOrNull()
            }.getOrNull()

            if (programId != null) {
                editor.putLong(item.reference.itemKey, programId)
                publishedCount++
            }
        }

        editor.apply()
        return publishedCount
    }

    internal fun buildProgramValues(
        context: Context,
        item: ContinueWatchingItem
    ): ContentValues {
        val spec = buildSpec(item)
        val posterUri = "android.resource://${context.packageName}/mipmap/ic_launcher"
        return ContentValues().apply {
            put(COLUMN_TYPE, spec.type)
            put(
                TvContractCompat.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
                TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
            )
            put(
                TvContractCompat.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
                spec.lastEngagementTimeMs
            )
            put(COLUMN_TITLE, spec.title)
            put(COLUMN_SHORT_DESCRIPTION, spec.description)
            put(COLUMN_POSTER_ART_URI, posterUri)
            put(COLUMN_INTENT_URI, spec.intentUri)
            put(COLUMN_INTERNAL_PROVIDER_ID, spec.internalProviderId)
            put(COLUMN_LAST_PLAYBACK_POSITION_MILLIS, spec.progressMs)
            put(COLUMN_DURATION_MILLIS, spec.durationMs)
        }
    }

    internal fun buildSpec(item: ContinueWatchingItem): TvWatchNextProgramSpec {
        val type = when (item.reference.contentType) {
            UserMemoryContentType.SHOW,
            UserMemoryContentType.EPISODE -> TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
            else -> TvContractCompat.WatchNextPrograms.TYPE_MOVIE
        }
        return TvWatchNextProgramSpec(
            type = type,
            title = item.reference.title,
            description = item.reference.subtitle ?: "Continue watching",
            intentUri = CalmSourceDeepLink.detailsUri(
                reference = item.reference,
                positionMs = item.progressMs
            ),
            internalProviderId = item.reference.itemKey,
            progressMs = item.progressMs.coerceToInt(),
            durationMs = item.durationMs.coerceToInt(),
            lastEngagementTimeMs = item.updatedAt
        )
    }

    private fun Long.coerceToInt(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    private const val MAX_WATCH_NEXT_ITEMS = 20
}

data class TvWatchNextProgramSpec(
    val type: Int,
    val title: String,
    val description: String,
    val intentUri: String,
    val internalProviderId: String,
    val progressMs: Int,
    val durationMs: Int,
    val lastEngagementTimeMs: Long
)
