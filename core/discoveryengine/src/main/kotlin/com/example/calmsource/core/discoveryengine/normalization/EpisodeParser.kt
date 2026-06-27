package com.example.calmsource.core.discoveryengine.normalization

/**
 * Utility to parse season and episode numbers from messy string filenames or titles.
 */
object EpisodeParser {

    data class ParsedEpisode(val season: Int, val episode: Int)

    /**
     * Parses season and episode numbers from the given title/filename.
     * Returns a ParsedEpisode, or null if no patterns match.
     */
    fun parse(title: String): ParsedEpisode? {
        if (title.isBlank()) return null
        val clean = title.lowercase().trim()

        // 1. S01E04 / s1e4
        val sEPattern = Regex("[sS](\\d+)\\s*[eE](\\d+)")
        sEPattern.find(clean)?.let { match ->
            val s = match.groupValues[1].toIntOrNull()
            val e = match.groupValues[2].toIntOrNull()
            if (s != null && e != null) return ParsedEpisode(s, e)
        }

        // 2. Season 1 Episode 4 / season 1 ep 4 / season 1 e 4
        val seasonEpPattern = Regex("season\\s*(\\d+)\\s*(?:episode|ep|e)\\s*(\\d+)")
        seasonEpPattern.find(clean)?.let { match ->
            val s = match.groupValues[1].toIntOrNull()
            val e = match.groupValues[2].toIntOrNull()
            if (s != null && e != null) return ParsedEpisode(s, e)
        }

        // 3. 1x04 / 01x04
        val xPattern = Regex("(\\d+)\\s*[xX]\\s*(\\d+)")
        xPattern.find(clean)?.let { match ->
            val s = match.groupValues[1].toIntOrNull()
            val e = match.groupValues[2].toIntOrNull()
            if (s != null && e != null) return ParsedEpisode(s, e)
        }

        // 4. Episode 4 / Ep 4 / Ep.4 / E 4
        val epPattern = Regex("\\b(?:episode|ep|e)\\.?\\s*(\\d+)\\b")
        epPattern.find(clean)?.let { match ->
            val e = match.groupValues[1].toIntOrNull()
            if (e != null) return ParsedEpisode(1, e) // Default to season 1
        }

        // 5. 3 or 4 digit numbers (e.g. 104 -> S01E04, 1204 -> S12E04)
        // Matches digits where the last two digits represent the episode,
        // and the leading digits represent the season.
        // Excludes years (1900-2099) to prevent false positives.
        val numPattern = Regex("\\b(\\d{1,2})(\\d{2})\\b")
        numPattern.findAll(clean).forEach { match ->
            val fullNumber = match.value.toIntOrNull() ?: 0
            if (fullNumber !in 1900..2099) {
                val s = match.groupValues[1].toIntOrNull()
                val e = match.groupValues[2].toIntOrNull()
                if (s != null && e != null && s > 0 && e > 0) {
                    return ParsedEpisode(s, e)
                }
            }
        }

        return null
    }
}
