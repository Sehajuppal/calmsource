package com.example.calmsource.core.model

/**
 * Maps between IPTV data models and display-layer models.
 *
 * This centralizes the conversion logic that was previously duplicated in
 * [LiveTvScreen] (mobile) and [TvLiveGuideScreen] (TV).
 *
 * @see IPTVChannel for the parsed IPTV channel model
 * @see Channel for the display-layer channel model
 * @see EPGProgram for the raw EPG program
 * @see Program for the display-layer program model
 */
object ChannelMapper {

    /**
     * Metadata extracted from an [IPTVChannel]'s raw attributes and name.
     *
     * This data class provides language and resolution information that was
     * previously hardcoded to "Hindi" and "1080p" respectively (bugs #39, #40).
     *
     * @property channel The display-layer [Channel] model
     * @property language Extracted from `tvg-language` attribute, group metadata, or empty string
     * @property resolution Extracted from channel name patterns (HD, FHD, 4K, SD) or empty string
     */
    data class ChannelMetadata(
        val channel: Channel,
        val language: String,
        val resolution: String
    )

    /**
     * Converts parsed [IPTVChannel]s to display-layer [Channel] models.
     *
     * @param iptvChannels Parsed channels from the IPTV repository
     * @return Display-ready channel list
     */
    fun toChannels(iptvChannels: List<IPTVChannel>): List<Channel> {
        return iptvChannels.map { ch ->
            Channel(
                id = ch.id,
                name = ch.name,
                logoUrl = ch.tvgLogo,
                streamUrl = ch.streamUrl,
                category = ch.groupTitle
            )
        }
    }

    /**
     * Converts parsed [IPTVChannel]s to [ChannelMetadata] containing the display-layer
     * [Channel] plus extracted language and resolution metadata.
     *
     * Use this instead of [toChannels] when the downstream consumer needs language
     * and resolution information (e.g. the search pipeline).
     *
     * @param iptvChannels Parsed channels from the IPTV repository
     * @return List of [ChannelMetadata] with extracted language and resolution
     */
    fun toChannelsWithMetadata(iptvChannels: List<IPTVChannel>): List<ChannelMetadata> {
        return iptvChannels.map { ch ->
            ChannelMetadata(
                channel = Channel(
                    id = ch.id,
                    name = ch.name,
                    logoUrl = ch.tvgLogo,
                    streamUrl = ch.streamUrl,
                    category = ch.groupTitle
                ),
                language = extractLanguage(ch),
                resolution = extractResolution(ch)
            )
        }
    }

    /**
     * Extracts the language for an IPTV channel.
     *
     * Resolution order:
     * 1. `tvg-language` attribute from the M3U `#EXTINF` line
     * 2. Empty string (unknown)
     *
     * Previously this was hardcoded to "Hindi" (bug #39).
     *
     * @param channel The parsed IPTV channel
     * @return Extracted language string, or empty string if unavailable
     */
    fun extractLanguage(channel: IPTVChannel): String {
        // 1. Explicit tvg-language attribute
        val tvgLanguage = channel.rawAttributes["tvg-language"]?.trim()
        if (!tvgLanguage.isNullOrEmpty()) return tvgLanguage

        // 2. Default to empty string — do NOT hardcode a language
        return ""
    }

    /**
     * Extracts the resolution for an IPTV channel from its name.
     *
     * Checks for common resolution patterns in the channel name:
     * - "4K" / "UHD" → "4K"
     * - "FHD" → "1080p"
     * - "HD" (standalone, not part of FHD/UHD) → "720p"
     * - "SD" → "SD"
     *
     * Previously this was hardcoded to "1080p" (bug #40).
     *
     * @param channel The parsed IPTV channel
     * @return Extracted resolution label, or empty string if undetectable
     */
    fun extractResolution(channel: IPTVChannel): String {
        val nameLower = channel.name.lowercase()
        val tvgNameLower = channel.tvgName?.lowercase() ?: ""
        val combined = "$nameLower $tvgNameLower"

        return when {
            combined.contains("4k") || combined.contains("uhd") || combined.contains("2160p") -> "4K"
            combined.contains("fhd") || combined.contains("1080p") -> "1080p"
            // Match standalone "hd" but not "fhd" or "uhd"
            Regex("\\bhd\\b").containsMatchIn(combined) || combined.contains("720p") -> "720p"
            combined.contains("sd") || combined.contains("480p") -> "SD"
            else -> "" // Do NOT hardcode a resolution
        }
    }

    /**
     * Finds the currently-airing program for a channel.
     *
     * @param epgId The matched EPG channel ID (from [EPGMatch])
     * @param programs All available EPG programs
     * @param nowMs Current time in milliseconds
     * @param displayChannelId The channel ID to use in the returned [Program]
     * @return The current program, or null if no program is airing
     */
    fun findCurrentProgram(
        epgId: String,
        programs: List<EPGProgram>,
        nowMs: Long,
        displayChannelId: String
    ): Program? {
        return programs.firstOrNull {
            it.channelId == epgId && nowMs >= it.startTimeMs && nowMs <= it.endTimeMs
        }?.let {
            Program(
                id = it.id,
                channelId = displayChannelId,
                title = it.title,
                description = it.description,
                startTimeMs = it.startTimeMs,
                endTimeMs = it.endTimeMs
            )
        }
    }

    /**
     * Finds the next upcoming program for a channel.
     *
     * @param epgId The matched EPG channel ID
     * @param programs All available EPG programs
     * @param nowMs Current time in milliseconds
     * @param displayChannelId The channel ID to use in the returned [Program]
     * @return The next program, or null if none scheduled
     */
    fun findNextProgram(
        epgId: String,
        programs: List<EPGProgram>,
        nowMs: Long,
        displayChannelId: String
    ): Program? {
        return programs
            .filter { it.channelId == epgId && it.startTimeMs > nowMs }
            .minByOrNull { it.startTimeMs }
            ?.let {
            Program(
                id = it.id,
                channelId = displayChannelId,
                title = it.title,
                description = it.description,
                startTimeMs = it.startTimeMs,
                endTimeMs = it.endTimeMs
            )
        }
    }
}
