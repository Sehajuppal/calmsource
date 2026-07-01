package com.example.calmsource.core.discoveryengine.normalization

/**
 * Utility to extract clean show/series names from episode titles by stripping out
 * season, episode, and trailing episode title details.
 */
object ShowExtractor {

    private val EPISODE_METADATA_PATTERNS = listOf(
        Regex("(?i)\\s*-\\s*s\\d+e\\d+.*"),
        Regex("(?i)\\s*s\\d+e\\d+.*"),
        Regex("(?i)\\s*\\d+x\\d+.*"),
        Regex("(?i)\\s*season\\s*\\d+\\s*(?:episode|ep|e)\\s*\\d+.*"),
        Regex("(?i)\\s*\\b(?:episode|ep|e)\\.?\\s*\\d+.*"),
    )

    /**
     * Extracts a normalized show name from an episode title.
     * e.g., "Breaking Bad - S01E01 - Pilot" -> "breaking bad"
     * e.g., "Stranger Things 1x04" -> "stranger things"
     */
    fun extractShowName(title: String): String {
        if (title.isBlank()) return ""
        var clean = title

        for (pattern in EPISODE_METADATA_PATTERNS) {
            val matched = pattern.find(clean)
            if (matched != null) {
                clean = clean.substring(0, matched.range.first)
                break
            }
        }

        return MetadataNormalizer.normalizeTitle(clean)
    }
}
