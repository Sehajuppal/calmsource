package com.example.calmsource.core.database

import android.util.Log
import androidx.room.TypeConverter
import com.example.calmsource.core.model.PlaybackSourceType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Date

class Converters {
    companion object {
        private const val TAG = "Converters"
        private val json = Json { ignoreUnknownKeys = true }

        private fun logE(tag: String, msg: String, tr: Throwable? = null) {
            try {
                if (tr != null) {
                    Log.e(tag, msg, tr)
                } else {
                    Log.e(tag, msg)
                }
            } catch (_: Throwable) {
                if (tr != null) {
                    System.err.println("[$tag] $msg: ${tr.message}")
                } else {
                    System.err.println("[$tag] $msg")
                }
            }
        }

        // Timestamp Converters
        @TypeConverter
        @JvmStatic
        fun fromTimestamp(value: Long?): Date? {
            return value?.let { Date(it) }
        }

        @TypeConverter
        @JvmStatic
        fun dateToTimestamp(date: Date?): Long? {
            return date?.time
        }

        // List Converters
        @TypeConverter
        @JvmStatic
        fun fromStringList(value: String?): List<String> {
            if (value.isNullOrEmpty()) return emptyList()
            return try {
                json.decodeFromString<List<String>>(value)
            } catch (e: Exception) {
                logE(TAG, "Failed to decode List<String>, falling back to comma-split", e)
                val trimmed = value.trim()
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    val content = trimmed.substring(1, trimmed.length - 1).trim()
                    if (content.startsWith("\"") && content.endsWith("\"")) {
                        content.split(Regex("\",\\s*\"")).map { it.removeSurrounding("\"").trim() }
                    } else {
                        content.split(",").map { it.trim().removeSurrounding("\"").trim() }
                    }
                } else {
                    value.split(",")
                }
            }
        }

        @TypeConverter
        @JvmStatic
        fun toStringList(list: List<String>?): String {
            if (list.isNullOrEmpty()) return ""
            return try {
                json.encodeToString(list)
            } catch (e: Exception) {
                logE(TAG, "Failed to encode List<String>, returning empty", e)
                ""
            }
        }

        // Map<String, String> Converters
        @TypeConverter
        @JvmStatic
        fun fromStringMap(value: String?): Map<String, String> {
            if (value.isNullOrEmpty()) return emptyMap()
            return try {
                json.decodeFromString<Map<String, String>>(value)
            } catch (e: Exception) {
                logE(TAG, "Failed to decode Map<String, String>, returning empty", e)
                emptyMap()
            }
        }

        @TypeConverter
        @JvmStatic
        fun toStringMap(map: Map<String, String>?): String {
            if (map.isNullOrEmpty()) return "{}"
            return try {
                json.encodeToString(map)
            } catch (e: Exception) {
                logE(TAG, "Failed to encode Map<String, String>, returning {}", e)
                "{}"
            }
        }

        // PlaybackSourceType Converters
        @TypeConverter
        @JvmStatic
        fun fromPlaybackSourceType(value: String?): PlaybackSourceType? {
            if (value == null) return null
            return try {
                PlaybackSourceType.valueOf(value)
            } catch (e: IllegalArgumentException) {
                logE(TAG, "Unknown PlaybackSourceType '$value', falling back to UNKNOWN", e)
                PlaybackSourceType.UNKNOWN
            }
        }

        @TypeConverter
        @JvmStatic
        fun toPlaybackSourceType(type: PlaybackSourceType?): String? {
            return type?.name
        }
    }
}
