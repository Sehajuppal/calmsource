package com.example.calmsource.core.discoveryengine.normalization

import java.text.Normalizer

object MetadataNormalizer {

    // Pre-compiled regexes. Each is now instantiated exactly once at class load time
    // instead of per-call inside the normalize functions. This eliminates the regex
    // compilation thrash that hit the CPU/Garbage Collector when processing tens of
    // thousands of channels/programs.
    private val DIACRITIC_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val ALPHANUM_ONLY_REGEX = Regex("[^a-z0-9\\s]")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val BRACKETS_REGEX = Regex("[\\[\\(].*?[\\]\\)]")
    // Patched: pattern matches one or more prefix tokens at the start of the string
    private val CHANNEL_PREFIX_REGEX = Regex("^([a-z0-9_-]{2,8}\\s*[:|\\-]\\s*)+")
    private val QUALITY_INDICATOR_REGEX = Regex("\\b(hd|fhd|sd|uhd|4k|1080p|720p|hevc|h264|h265|aac|ac3|dd51|stereo|vip|raw)\\b")
    private val YEAR_BRACKET_REGEX = Regex("[\\[\\(](19\\d\\d|20\\d\\d)[\\]\\)]")
    private val SQUARE_BRACKET_REGEX = Regex("\\[.*?\\]")
    private val PAREN_TAG_REGEX = Regex("\\(.*?\\)")
    private val SURROUND_REGEX = Regex("\\b(5\\.1|7\\.1)\\b")

    private val noiseWords = setOf(
        "1080p", "720p", "2160p", "4k", "8k", "576p", "480p", "bluray", "brrip", "webrip", "web-dl", "webdl", "hdrip", "dvdrip", "cam", "ts", "tc", "r5", "scr",
        "x264", "x265", "h264", "h265", "hevc", "avc", "divx", "xvid",
        "dts", "hd", "ma", "dd51", "dd20", "aac", "ac3", "truehd", "atmos", "ch", "stereo", "dd",
        "english", "french", "spanish", "subbed", "dubbed", "multi", "dual", "dual-audio", "esub", "fsub", "ita", "fra", "ger", "spa", "eng",
        "yts", "yify", "fgt", "rarbg", "psa", "qxr", "tigole", "silence", "utr", "galaxyrg", "tgx", "ion10", "hdr", "10bit", "sdr", "uxn",
        "audio", "video", "sound", "rip", "mp4", "mkv", "avi", "hq", "web", "clean"
    )

    /**
     * Normalizes a movie or series title by lowercasing, removing diacritics/accents,
     * stripping special characters (keeping only alphanumeric and single spaces), and trimming.
     */
    fun normalizeTitle(title: String): String {
        if (title.isBlank()) return ""
        // Lowercase and normalize accents/diacritics
        var cleaned = title.lowercase()
        cleaned = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
            .replace(DIACRITIC_REGEX, "")
        // Keep only alphanumeric characters and spaces
        cleaned = cleaned.replace(ALPHANUM_ONLY_REGEX, " ")
        // Collapse multiple spaces into one and trim
        return cleaned.replace(WHITESPACE_REGEX, " ").trim()
    }

    /**
     * Normalizes a search query. Uses the same clean-up logic as normalizeTitle.
     */
    fun normalizeSearchQuery(query: String): String {
        return normalizeTitle(query)
    }

    /**
     * Normalizes a cast/director name.
     */
    fun normalizePersonName(name: String): String {
        return normalizeTitle(name)
    }

    /**
     * Normalizes an IPTV channel name. IPTV channels often contain country prefixes
     * (e.g. "US: HBO", "FR | CANAL"), quality/resolution tags (e.g. "HD", "FHD", "1080p"),
     * and backup tags. This function cleans those to leave the semantic name.
     */
    fun normalizeChannelName(name: String): String {
        if (name.isBlank()) return ""
        var cleaned = name.lowercase()
        // Remove diacritics
        cleaned = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
            .replace(DIACRITIC_REGEX, "")

        // Remove bracketed info (e.g. "[FR]", "(ES)") and parenthesis info (e.g. "(BACKUP)")
        cleaned = cleaned.replace(BRACKETS_REGEX, " ")

        // Patched (Bug #19): strip ALL leading prefix tokens like
        // "US | USA | NEWS | FHD", not just the very first one.
        cleaned = cleaned.replace(CHANNEL_PREFIX_REGEX, " ")

        // Remove quality indicators
        cleaned = cleaned.replace(QUALITY_INDICATOR_REGEX, " ")

        // Keep only alphanumeric characters and spaces
        cleaned = cleaned.replace(ALPHANUM_ONLY_REGEX, " ")

        // Collapse spaces and trim
        return cleaned.replace(WHITESPACE_REGEX, " ").trim()
    }

    /**
     * Generates alternative aliases for titles to increase match rates.
     * For example, "Spider-Man: Into the Spider-Verse" yields:
     * - "spider man into the spider verse"
     * - "spidermanintothespiderverse"
     * - "spider man"
     * - "into the spider verse"
     */
    fun generateTitleAliases(title: String): Set<String> {
        val aliases = mutableSetOf<String>()
        val normalized = normalizeTitle(title)
        if (normalized.isEmpty()) return aliases

        aliases.add(normalized)

        // 1. Space-stripped variation (e.g., "spiderman")
        val noSpaces = normalized.replace(" ", "")
        if (noSpaces != normalized && noSpaces.isNotEmpty()) {
            aliases.add(noSpaces)
        }

        // 2. Split by colon ':' (separates title and subtitle)
        val colonParts = title.split(":")
        if (colonParts.size > 1) {
            colonParts.forEach { part ->
                val normPart = normalizeTitle(part)
                if (normPart.isNotEmpty() && normPart.length > 2) {
                    aliases.add(normPart)
                }
            }
        }

        // 3. For hyphenated words like "Spider-Man", generate a version replacing '-' with space
        if (title.contains("-")) {
            val withSpaces = title.replace("-", " ")
            val normWithSpaces = normalizeTitle(withSpaces)
            if (normWithSpaces.isNotEmpty() && normWithSpaces != normalized) {
                aliases.add(normWithSpaces)
            }
            // Also add split parts
            val dashParts = title.split("-")
            dashParts.forEach { part ->
                val normPart = normalizeTitle(part)
                if (normPart.isNotEmpty() && normPart.length > 2) {
                    aliases.add(normPart)
                }
            }
        }

        return aliases
    }

    /**
     * Generates alternative aliases for channel names (e.g., "canal+" -> "canal plus", "canalplus").
     */
    fun generateChannelAliases(name: String): Set<String> {
        val aliases = mutableSetOf<String>()
        val normalized = normalizeChannelName(name)
        if (normalized.isEmpty()) return aliases

        aliases.add(normalized)

        // Generate variation without spaces
        val noSpaces = normalized.replace(" ", "")
        if (noSpaces != normalized && noSpaces.isNotEmpty()) {
            aliases.add(noSpaces)
        }

        // Handle "+" symbol replacement
        if (name.contains("+")) {
            val withPlus = name.replace("+", " plus")
            val normPlus = normalizeChannelName(withPlus)
            if (normPlus.isNotEmpty()) {
                aliases.add(normPlus)
                val noSpacePlus = normPlus.replace(" ", "")
                if (noSpacePlus.isNotEmpty()) {
                    aliases.add(noSpacePlus)
                }
            }
        }

        return aliases
    }

    /**
     * Cleans up noise (resolution tags, release groups, codecs, subtitle indicators)
     * from Stremio stream titles to isolate clean semantic search terms or display names.
     */
    fun removeStreamNoise(streamTitle: String): String {
        if (streamTitle.isBlank()) return ""
        var cleaned = streamTitle.lowercase()

        // Preserve 4-digit years in brackets or parentheses (e.g. "" -> " 2018 ", "[2010]" -> " 2010 ")
        cleaned = YEAR_BRACKET_REGEX.replace(cleaned) { match -> " ${match.groupValues[1]} " }

        // Remove square bracket tags like [AIO Streams] or [Debrid]
        cleaned = cleaned.replace(SQUARE_BRACKET_REGEX, " ")
        // Remove parenthesis tags
        cleaned = cleaned.replace(PAREN_TAG_REGEX, " ")

        // Remove 5.1 and 7.1 audio tag variations first so they don't leave separate '5' and '1'
        cleaned = cleaned.replace(SURROUND_REGEX, " ")

        // Replace all non-alphanumeric with spaces to split words easily
        cleaned = cleaned.replace(ALPHANUM_ONLY_REGEX, " ")

        // Split by whitespace, filter out noise words, and join back
        val words = cleaned.split(WHITESPACE_REGEX)
        val filteredWords = words.filter { word ->
            word.isNotEmpty() && !noiseWords.contains(word)
        }

        return filteredWords.joinToString(" ")
    }
}
