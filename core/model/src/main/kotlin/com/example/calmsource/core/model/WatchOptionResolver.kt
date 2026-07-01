package com.example.calmsource.core.model

/**
 * Resolves [StreamSource]s into user-facing [WatchOption]s with readable labels.
 *
 * This centralizes the label-building logic that was previously duplicated in both
 * [DetailsScreen] (mobile) and [TvDetailsScreen] (TV). The logic handles:
 * - Resolution normalization ("2160P" → "4K", "FHD" → "1080p")
 * - HDR, Dolby Vision, and Atmos detection from source name
 * - Subtitle and dubbing label generation
 * - Debrid cached status tagging
 * - File size categorization ("Low Data" for small non-4K files)
 * - Language label construction (Dual Audio, Dubbed, original)
 *
 * @see StreamSource for the raw source model
 * @see WatchOption for the resolved display model
 */
object WatchOptionResolver {

    // Pre-compiled word-boundary regexes for accurate detection
    private val HD_REGEX = Regex("\\bhd\\b", RegexOption.IGNORE_CASE)
    private val SUB_REGEX = Regex("\\b(sub|subtitle|subtitled|subs)\\b", RegexOption.IGNORE_CASE)
    private val DUB_REGEX = Regex("\\b(dub|dubbed|dubbing)\\b", RegexOption.IGNORE_CASE)
    private val DUAL_AUDIO_REGEX = Regex("\\b(?:dual|multi)[\\s\\.\\-_]*(?:audio|dub)\\b", RegexOption.IGNORE_CASE)

    /**
     * Builds a complete list of [WatchOption]s from raw [StreamSource]s,
     * sorted by type detection (IPTV, Debrid, Extension).
     *
     * @param sources Raw stream sources for a media item
     * @return List of display-ready watch options with labels and type classification
     */
    fun buildWatchOptions(sources: List<StreamSource>): List<WatchOption> {
        return sources.map { source ->
            val type = detectSourceType(source)
            val label = getReadableLabel(source, type)
            WatchOption(
                id = source.id,
                title = source.name,
                source = source,
                type = type,
                languageLabel = label
            )
        }
    }

    /**
     * Detects the [SourceType] from a source's extension ID prefix.
     *
     * Convention:
     * - `"iptv-"` prefix → [SourceType.IPTV]
     * - `"deb-"` prefix → [SourceType.DEBRID]
     * - Everything else → [SourceType.EXTENSION]
     */
    fun detectSourceType(source: StreamSource): SourceType = when {
        source.extensionId.startsWith(ProviderIdPrefix.IPTV) ||
                source.extensionId.startsWith("xtream-") ||
                source.url.startsWith("xtream://") -> SourceType.IPTV
        source.extensionId.startsWith(ProviderIdPrefix.DEBRID) -> SourceType.DEBRID
        else -> SourceType.EXTENSION
    }

    /**
     * Generates a human-readable label for a stream source.
     *
     * The label is a "•"-separated string of attributes like:
     * "Cached • Debrid • 4K • HDR • Dolby Vision • Atmos • Dual Audio"
     *
     * @param source The raw stream source
     * @param type The detected source type
     * @return Readable label string
     */
    fun getReadableLabel(source: StreamSource, type: SourceType): String {
        val parts = mutableListOf<String>()
        val nameUpper = source.name.uppercase()
        val isDebrid = type == SourceType.DEBRID || source.extensionId.startsWith(ProviderIdPrefix.DEBRID)

        if (isDebrid) {
            // Real cached status requires network verification via DebridProviderClient.
            // Fake local logic removed.
            parts.add("Debrid")
        }

        parts.add(normalizeResolution(source.resolution))

        if (nameUpper.contains("HDR") || nameUpper.contains("DV ") || nameUpper.contains("DOLBY VISION")) {
            parts.add("HDR")
        }
        if (nameUpper.contains("DOLBY VISION")) {
            parts.add("Dolby Vision")
        }
        if (nameUpper.contains("ATMOS") || nameUpper.contains("TRUEHD")) {
            parts.add("Atmos")
        }
        if (source.isSubbed || SUB_REGEX.containsMatchIn(source.name)) {
            parts.add("Subtitles")
        }
        val sizeBytes = source.sizeBytes
        if (sizeBytes != null && sizeBytes < 1_500_000_000 && !source.resolution.contains("4K", ignoreCase = true)) {
            parts.add("Low Data")
        }

        val lang = when {
            source.isDualAudio -> "Dual Audio"
            source.isDubbed -> "${source.language} Dubbed"
            else -> source.language
        }
        parts.add(lang)

        return parts.joinToString(" • ")
    }

    /**
     * Normalizes resolution strings to consistent display values.
     * e.g., "2160P" → "4K", "FHD" → "1080p"
     */
    fun normalizeResolution(resolution: String): String = when (resolution.uppercase()) {
        "4K", "2160P" -> "4K"
        "1080P", "FHD" -> "1080p"
        "720P", "HD" -> "720p"
        "SD", "480P" -> "SD"
        else -> resolution
    }

    /**
     * Formats file size in bytes to a human-readable GB string.
     * @return Formatted string like "2.80 GB" or "N/A" if null
     */
    fun formatFileSize(bytes: Long?): String = bytes?.let {
        String.format("%.2f GB", it.toDouble() / (1024 * 1024 * 1024))
    } ?: "Unknown"

    /**
     * Cleans a raw stream filename or title into a human-readable label.
     *
     * Strips: file extensions, resolution tags, video/audio codecs, quality indicators,
     * release group suffixes, HDR/DV/Atmos markers, and excess whitespace.
     * Produces clean labels like "Spider-Man Homecoming 2017" from raw torrent filenames.
     *
     * @param name The stream's name field (may be a raw filename)
     * @param title The stream's title field (fallback)
     * @param providerName Provider name used as fallback for URL-only streams
     * @return A cleaned, human-readable stream label (max 60 chars)
     */
    fun cleanStreamTitle(name: String?, title: String?, providerName: String): String {
        val raw = name ?: title ?: "Stream"
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return "$providerName Stream"
        }
        val firstLine = raw.split("\n").firstOrNull()?.trim() ?: "Stream"
        if (firstLine.startsWith("http://") || firstLine.startsWith("https://")) {
            return "$providerName Stream"
        }
        var cleaned = firstLine
            // Strip file extensions
            .replace(Regex("\\.(mkv|mp4|avi|srt|mov|wmv)\\b", RegexOption.IGNORE_CASE), "")
            // Replace dots and underscores with spaces (common in torrent filenames)
            .replace(".", " ")
            .replace("_", " ")

        // Strip resolution tags
        cleaned = cleaned.replace(Regex("\\b(2160p|1080p|720p|480p|4K|UHD|FHD|HD|SD)\\b", RegexOption.IGNORE_CASE), "")
        // Strip video codecs
        cleaned = cleaned.replace(Regex("\\b(x264|x265|h264|h265|h 264|h 265|HEVC|AVC|AV1|MPEG4)\\b", RegexOption.IGNORE_CASE), "")
        // Strip audio codecs
        cleaned = cleaned.replace(Regex("\\b(AAC|AC3|EAC3|E-AC3|DTS|DTS-HD|FLAC|Atmos|TrueHD|DD5 1|DD 5 1|DD7 1)\\b", RegexOption.IGNORE_CASE), "")
        // Strip quality/source indicators
        cleaned = cleaned.replace(Regex("\\b(BluRay|Blu-Ray|BRRip|BDRip|WEB-DL|WEB DL|WEBRip|WEBDL|HDRip|DVDRip|HDTV|REMUX|PROPER|REPACK|EXTENDED|UNRATED|DIRECTORS CUT)\\b", RegexOption.IGNORE_CASE), "")
        // Strip HDR / Dolby Vision / color tags
        cleaned = cleaned.replace(Regex("\\b(HDR|HDR10|HDR10\\+|DV|Dolby Vision|10bit|10-bit)\\b", RegexOption.IGNORE_CASE), "")
        // Strip release group suffix (e.g., "-Torrentio", "-GROUP", "-RealDebrid", "-SPARKS")
        cleaned = cleaned.replace(Regex("-[A-Za-z0-9]+\\s*$"), "")
        // Strip bracketed/parenthesized tags like [Hindi], (Dual Audio), [IPTV VOD]
        cleaned = cleaned.replace(Regex("\\[(?:Hindi|English|Tamil|Telugu|Spanish|French|German|Dual[- ]Audio|IPTV[^]]*|HD|4K|Multi)\\]", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("\\((?:Hindi|English|Tamil|Telugu|Spanish|French|German|Dual[- ]Audio|Dubbed|Sub)\\)", RegexOption.IGNORE_CASE), "")
        // Strip language names that appear after a year (e.g., "2017 Hindi Dubbed")
        cleaned = cleaned.replace(Regex("(?<=\\d{4})\\s+(Hindi|English|Tamil|Telugu|Spanish|French|German)\\s*(Dubbed|Sub|Subtitled)?", RegexOption.IGNORE_CASE), "")
        // Strip standalone "Dubbed", "Sub", "Subtitled" at word boundaries
        cleaned = cleaned.replace(Regex("\\b(Dubbed|Subtitled)\\b", RegexOption.IGNORE_CASE), "")
        // Strip "DD" followed by channel config like "5.1", already partially caught above
        cleaned = cleaned.replace(Regex("\\bDD\\s*\\d+[. ]\\d+\\b", RegexOption.IGNORE_CASE), "")
        // Collapse multiple spaces and trim
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()
        // Strip trailing dash from group removal
        cleaned = cleaned.trimEnd('-', ' ')
        
        // Strip API keys or tokens injected into the title
        cleaned = cleaned.replace(Regex("(?i)(apikey|token|key|secret)=[^&\\s]+"), "REDACTED")
        
        // Hide any remaining URLs
        cleaned = cleaned.replace(Regex("https?://[^\\s]+"), "[URL HIDDEN]")
        
        if (cleaned.isBlank()) {
            return "$providerName Stream"
        }
        if (cleaned.length > 60) {
            cleaned = cleaned.take(57) + "..."
        }
        return cleaned
    }

    fun mapStremioStreamToSource(
        stream: StremioStream,
        providerId: String,
        providerName: String,
        mediaId: String
    ): StreamSource {
        val name = stream.name ?: ""
        val title = stream.title ?: ""
        val combinedText = "$name $title"
        val combinedTextLower = combinedText.lowercase()

        // 1. Resolution
        val parsedRes = StreamParserUtil.parseQuality(combinedText)
        val resolution = if (parsedRes == "SD" && combinedTextLower.contains("1080p")) "1080p" else parsedRes

        // 2. Video Codec
        val videoCodec = when {
            combinedTextLower.contains("x265") || combinedTextLower.contains("h265") || combinedTextLower.contains("hevc") -> "HEVC"
            combinedTextLower.contains("x264") || combinedTextLower.contains("h264") || combinedTextLower.contains("avc") -> "AVC"
            combinedTextLower.contains("av1") -> "AV1"
            else -> "AVC"
        }

        // 3. Audio Codec
        val audioCodec = when {
            combinedTextLower.contains("atmos") -> "Atmos"
            combinedTextLower.contains("dd5.1") || combinedTextLower.contains("ac3") || combinedTextLower.contains("eac3") -> "E-AC3"
            combinedTextLower.contains("aac") -> "AAC"
            combinedTextLower.contains("dts") -> "DTS"
            else -> "AAC"
        }

        // 4. Size in bytes
        val sizeGb = StreamParserUtil.parseFileSizeGb(combinedText)
        val sizeBytes = if (sizeGb > 0.0) (sizeGb * 1024 * 1024 * 1024).toLong() else null

        // 5. Seeds
        var seeds: Int? = null
        val seedsRegex = Regex("(?i)(?:seeds|👥|👤|s)[:\\s-]*(\\d+)")
        val seedsMatch = seedsRegex.find(combinedTextLower)
        if (seedsMatch != null) {
            seeds = seedsMatch.groupValues[1].toIntOrNull()
        } else {
            if (stream.infoHash != null) {
                val numRegex = Regex("👤\\s*(\\d+)")
                numRegex.find(combinedText)?.let {
                    seeds = it.groupValues[1].toIntOrNull()
                }
            }
        }

        // 6. Language & Audio Options
        val language = when {
            combinedTextLower.contains("hindi") -> "Hindi"
            combinedTextLower.contains("spanish") -> "Spanish"
            combinedTextLower.contains("french") -> "French"
            combinedTextLower.contains("german") -> "German"
            combinedTextLower.contains("tamil") -> "Tamil"
            combinedTextLower.contains("telugu") -> "Telugu"
            else -> "English" // Default fallback
        }

        val isSubbed = SUB_REGEX.containsMatchIn(combinedText)
        val isDubbed = DUB_REGEX.containsMatchIn(combinedText)
        val isDualAudio = DUAL_AUDIO_REGEX.containsMatchIn(combinedText) ||
                (combinedTextLower.contains("hindi") && combinedTextLower.contains("english"))

        // 7. URL or InfoHash resolving
        val url = stream.url ?: stream.infoHash?.let { "magnet:?xt=urn:btih:$it" } ?: ""

        // 8. Clean title (hiding raw URLs/filenames)
        val displayTitle = cleanStreamTitle(stream.name, stream.title, providerName)

        val sourceId = "$providerId-${stream.infoHash ?: stream.url?.hashCode() ?: java.util.UUID.randomUUID().toString()}"

        val headers = mutableMapOf<String, String>()
        stream.behaviorHints?.get("requestHeaders")?.let { element ->
            if (element is kotlinx.serialization.json.JsonObject) {
                element.forEach { (key, value) ->
                    if (value is kotlinx.serialization.json.JsonPrimitive && value.isString) {
                        headers[key] = value.content
                    }
                }
            }
        }

        val sourceExtensionName = StreamParserUtil.parseSourceExtensionName(name, providerName)

        return StreamSource(
            id = sourceId,
            name = displayTitle,
            url = url,
            extensionId = providerId,
            resolution = resolution,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            sizeBytes = sizeBytes,
            seeds = seeds,
            language = language,
            isSubbed = isSubbed,
            isDubbed = isDubbed,
            isDualAudio = isDualAudio,
            headers = headers,
            sourceExtensionName = sourceExtensionName,
            rawTitle = combinedText
        )
    }
}
