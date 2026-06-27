package com.example.calmsource.core.playback.ui

import java.util.Locale

/**
 * Human-readable labels for ExoPlayer/VLC track language codes shown in player overlays.
 */
object TrackLanguageFormatter {
    fun displayLanguage(code: String?): String? {
        if (code.isNullOrBlank()) return null
        val normalized = code.trim().replace('_', '-')
        return try {
            val locale = Locale.forLanguageTag(normalized)
            val display = locale.getDisplayName(Locale.getDefault())
            when {
                display.isBlank() -> normalized.uppercase(Locale.ROOT)
                display.equals(normalized, ignoreCase = true) -> normalized.uppercase(Locale.ROOT)
                else -> display
            }
        } catch (_: Exception) {
            normalized.uppercase(Locale.ROOT)
        }
    }

    fun trackLabel(name: String, language: String?): String {
        val lang = displayLanguage(language) ?: return name
        if (name.contains(lang, ignoreCase = true)) return name
        return "$name · $lang"
    }
}
