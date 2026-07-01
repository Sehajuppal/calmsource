package com.example.calmsource.ui

import com.example.calmsource.core.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.R as CoreUiR
import com.example.calmsource.core.discoveryengine.models.MediaItem as DiscoveryMediaItem
import com.example.calmsource.core.model.*
import com.example.calmsource.core.sourceintelligence.models.toRawSourceInput
import com.example.calmsource.core.ui.components.*

internal sealed interface DetailsNotice {
    data object SourceUnavailable : DetailsNotice
    data object SourceBlocked : DetailsNotice
}

@Composable
internal fun DetailsBlockedNotice(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLumenTokens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(LumenTokens.Shape.md)
            .background(t.colors.card.copy(alpha = 0.94f))
            .border(1.dp, t.colors.border, LumenTokens.Shape.md)
            .padding(LumenTokens.Space.s5),
        verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm),
    ) {
        Text(text = title, style = LumenType.Title.toTextStyle(), color = t.colors.foreground)
        Text(text = body, style = LumenType.Body.toTextStyle(), color = t.colors.mutedForeground)
        Row(horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm)) {
            TextButton(onClick = onAction) {
                Text(text = actionLabel, color = t.colors.brand)
            }
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(CoreUiR.string.cta_dismiss), color = t.colors.mutedForeground)
            }
        }
    }
}

@Composable
internal fun AlternativeWatchOptions(
    iptvOption: WatchOption?,
    sortedOptions: List<WatchOption>,
    hindiOption: WatchOption?,
    dualAudioOption: WatchOption?,
    onPlay: (WatchOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val iptvRes = remember(iptvOption) {
        iptvOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) }
    }
    val extOption = remember(sortedOptions) { sortedOptions.firstOrNull { it.type == SourceType.EXTENSION } }
    val extRes = remember(extOption) {
        extOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) }
    }
    val primaryLangRes = remember(hindiOption) {
        hindiOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) }
    }
    val dualRes = remember(dualAudioOption) {
        dualAudioOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) }
    }
    val lowDataOption = remember(sortedOptions) {
        sortedOptions.firstOrNull {
            val r = com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput())
            r.rankingFeatures.isLowDataSuitable
        }
    }
    val lowDataRes = remember(lowDataOption) {
        lowDataOption?.let { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(it.toRawSourceInput()) }
    }
    val hasOptions = iptvOption != null || extOption != null || hindiOption != null ||
        dualAudioOption != null || lowDataOption != null
    if (!hasOptions) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm)) {
        Text(
            text = stringResource(CoreUiR.string.details_ways_to_watch),
            style = LumenType.RowTitle.toTextStyle(),
            color = LocalLumenTokens.current.colors.foreground,
            modifier = Modifier.padding(bottom = LumenTokens.Space.xs),
        )
        if (iptvOption != null && iptvRes != null) {
            LumenGhostButton(
                text = "IPTV · ${iptvRes.displayLabel.primaryLabel}",
                onClick = { onPlay(iptvOption) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (extOption != null && extRes != null) {
            LumenGhostButton(
                text = "Addon · ${extRes.displayLabel.primaryLabel}",
                onClick = { onPlay(extOption) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (hindiOption != null && primaryLangRes != null) {
            LumenGhostButton(
                text = "Primary language · ${primaryLangRes.displayLabel.primaryLabel}",
                onClick = { onPlay(hindiOption) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (dualAudioOption != null && dualRes != null) {
            LumenGhostButton(
                text = "Dual audio · ${dualRes.displayLabel.primaryLabel}",
                onClick = { onPlay(dualAudioOption) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (lowDataOption != null && lowDataRes != null) {
            LumenGhostButton(
                text = "Low data · ${lowDataRes.displayLabel.primaryLabel}",
                onClick = { onPlay(lowDataOption) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun MetaChip(
    text: String,
    color: Color = LocalLumenTokens.current.colors.mutedForeground
) {
    val t = LocalLumenTokens.current
    Box(
        modifier = Modifier
            .clip(LumenTokens.Shape.xs)
            .background(t.colors.muted)
            .padding(horizontal = LumenTokens.Space.sm, vertical = LumenTokens.Space.xs)
    ) {
        Text(
            text = text,
            fontSize = LumenType.size11,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
internal fun EpisodeRow(
    video: StremioVideo,
    backdropUrl: String?,
    isSelected: Boolean,
    progress: Float?,
    onClick: () -> Unit
) {
    val t = LocalLumenTokens.current
    Column(
        modifier = Modifier
            .width(LumenLayout.channelPanelWidth)
            .clickable(onClick = onClick)
    ) {
        PosterCard(
            imageUrl = video.displayImageUrl(backdropUrl),
            orientation = PosterOrientation.Landscape,
            progress = progress,
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = video.episodeDisplayLabel(video.season ?: 1),
            color = if (isSelected) t.colors.brand else t.colors.foreground,
            fontSize = LumenType.size13,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = LumenTokens.Space.sm)
        )
    }
}

@Composable
fun ManualSourceItem(
    option: WatchOption,
    score: Int,
    scoreReasons: List<String> = emptyList(),
    health: SourceHealth?,
    showRawDetails: Boolean,
    onClick: () -> Unit
) {
    val t = LocalLumenTokens.current
    val result = remember(option) { com.example.calmsource.core.sourceintelligence.SourceIntelligence.process(option.toRawSourceInput()) }

    LumenCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(LumenTokens.Space.xs),
            modifier = Modifier.padding(LumenTokens.Space.s5)
        ) {
            Text(
                text = if (showRawDetails) com.example.calmsource.core.network.UrlRedactor.redactFilename(option.source.name) else result.displayLabel.primaryLabel,
                fontSize = LumenType.size14,
                fontWeight = FontWeight.Bold,
                color = t.colors.foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (showRawDetails) {
                Text(
                    text = com.example.calmsource.core.network.UrlRedactor.redactUrl(option.source.url),
                    fontSize = LumenType.size11,
                    color = t.colors.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (result.displayLabel.secondaryLabel.isNotEmpty()) {
                Text(
                    text = result.displayLabel.secondaryLabel,
                    fontSize = LumenType.size12,
                    color = t.colors.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SourceBadge(kind = option.type.toBadgeKind())
                
                val parsedInfo = remember(option.source) {
                    StreamParserUtil.smartParseAll(option.source.rawTitle ?: option.source.name, option.source.extensionId)
                }

                val extensionName = parsedInfo.sourceExtensionName ?: option.source.sourceExtensionName
                if (!extensionName.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(LumenTokens.Shape.xs)
                            .background(t.colors.brand.copy(alpha = 0.2f))
                            .padding(horizontal = LumenTokens.Space.sm, vertical = LumenTokens.Space.xs)
                    ) {
                        Text(
                            text = extensionName,
                            color = t.colors.brandGlow,
                            fontSize = LumenType.size10,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                val quality = parsedInfo.quality
                val sizeStr = WatchOptionResolver.formatFileSize(parsedInfo.fileSizeBytes)
                Box(
                    modifier = Modifier
                        .clip(LumenTokens.Shape.xs)
                        .background(t.colors.muted)
                        .padding(horizontal = LumenTokens.Space.sm, vertical = LumenTokens.Space.xs)
                ) {
                    Text(
                        text = "[$quality] [$sizeStr]",
                        color = t.colors.foreground,
                        fontSize = LumenType.size10,
                        fontWeight = FontWeight.Bold
                    )
                }

                val hdrBadge = parsedInfo.hdrFormat
                if (hdrBadge != null) {
                    Box(
                        modifier = Modifier
                            .clip(LumenTokens.Shape.xs)
                            .background(LumenExtendedColors.ratingGold.copy(alpha = 0.24f))
                            .padding(horizontal = LumenTokens.Space.sm, vertical = LumenTokens.Space.xs)
                    ) {
                        Text(
                            text = "[$hdrBadge]",
                            color = LumenExtendedColors.ratingGold,
                            fontSize = LumenType.size10,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val codecBadge = parsedInfo.videoCodec ?: option.source.videoCodec
                if (!codecBadge.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(LumenTokens.Shape.xs)
                            .background(LumenExtendedColors.cyan.copy(alpha = 0.24f))
                            .padding(horizontal = LumenTokens.Space.sm, vertical = LumenTokens.Space.xs)
                    ) {
                        Text(
                            text = "[$codecBadge]",
                            color = LumenExtendedColors.cyan,
                            fontSize = LumenType.size10,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val audioBadge = parsedInfo.audioCodec ?: option.source.audioCodec
                if (!audioBadge.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(LumenTokens.Shape.xs)
                            .background(LumenExtendedColors.violet.copy(alpha = 0.24f))
                            .padding(horizontal = LumenTokens.Space.sm, vertical = LumenTokens.Space.xs)
                    ) {
                        Text(
                            text = "[$audioBadge]",
                            color = LumenExtendedColors.violet,
                            fontSize = LumenType.size10,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                val tier = health?.reliabilityTier ?: SourceReliabilityTier.EXCELLENT
                val (labelText, labelColor) = when (tier) {
                    SourceReliabilityTier.EXCELLENT, SourceReliabilityTier.GOOD -> "Reliable" to LumenExtendedColors.statusHealthy
                    SourceReliabilityTier.UNSTABLE, SourceReliabilityTier.POOR -> "Unstable" to LumenTokens.Color.warning
                    SourceReliabilityTier.BLOCKED -> "Failed recently" to LumenExtendedColors.errorBright
                    else -> "Reliable" to LumenExtendedColors.statusHealthy
                }
                Text(
                    text = labelText,
                    color = labelColor,
                    fontSize = LumenType.size11,
                    fontWeight = FontWeight.Bold
                )
                
                val parsedSeeds = parsedInfo.seeds ?: option.source.seeds
                if (parsedSeeds != null) {
                    Text(
                        text = "Seeds: $parsedSeeds",
                        fontSize = LumenType.size11,
                        color = LumenTokens.Color.success
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Score: $score",
                    fontSize = LumenType.size11,
                    color = t.colors.brand,
                    fontWeight = FontWeight.Bold
                )
            }
            if (showRawDetails && scoreReasons.isNotEmpty()) {
                Text(
                    text = scoreReasons.joinToString(" · "),
                    fontSize = LumenType.size10,
                    color = t.colors.mutedForeground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (health != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.s5),
                    modifier = Modifier.fillMaxWidth().padding(top = LumenTokens.Space.xs)
                ) {
                    Text(text = "Failures: ${health.failureCount}", fontSize = LumenType.size10, color = t.colors.mutedForeground)
                    Text(text = "Startup: ${health.averageStartupTime}ms", fontSize = LumenType.size10, color = t.colors.mutedForeground)
                    Text(text = "Buffering: ${String.format("%.1f", health.averageBufferingSeverity)}", fontSize = LumenType.size10, color = t.colors.mutedForeground)
                }
            }
        }
    }
}

internal fun SourceType.toBadgeKind(): SourceBadgeKind = when (this) {
    SourceType.IPTV -> SourceBadgeKind.IPTV
    SourceType.EXTENSION -> SourceBadgeKind.EXTENSION
    SourceType.DEBRID -> SourceBadgeKind.DEBRID
}

internal fun MediaItem.toDiscoveryMediaItem(): DiscoveryMediaItem {
    return DiscoveryMediaItem(
        id = id,
        type = when (type) {
            MediaType.MOVIE -> "movie"
            MediaType.SHOW -> "series"
        },
        title = title,
        overview = overview,
        posterUrl = posterUrl,
        rating = rating,
        releaseYear = releaseDate?.take(4)?.toIntOrNull(),
        externalIds = externalIds,
        source = "details"
    )
}

internal suspend fun recordTasteSignals(
    memoryRepository: com.example.calmsource.core.database.repository.UserMemoryRepository,
    mediaItem: MediaItem,
    stremioMeta: StremioMeta?,
    profileId: String,
) {
    runCatching {
        stremioMeta?.genres?.forEach { genre ->
            val key = genre.trim().lowercase()
            if (key.isNotBlank()) {
                memoryRepository.incrementPreferenceSignal(
                    signalType = UserPreferenceSignalType.GENRE,
                    signalKey = key,
                    profileId = profileId,
                )
            }
        }
        memoryRepository.incrementPreferenceSignal(
            signalType = UserPreferenceSignalType.CONTENT_TYPE,
            signalKey = if (mediaItem.type == MediaType.SHOW) "series" else "movie",
            profileId = profileId,
        )
    }
}

fun selectBestMatch(options: List<WatchOption>, scores: Map<String, Int>): WatchOption? {
    val sizeCap = 20L * 1024 * 1024 * 1024L  // 20GB cap
    val cappedOptions = options.filter { option ->
        val size = option.source.sizeBytes
        size == null || size < sizeCap
    }
    val candidates = if (cappedOptions.isNotEmpty()) cappedOptions else options

    return candidates.maxByOrNull { option ->
        var score = scores[option.id] ?: 0
        when (option.source.resolution) {
            "1080p" -> score += 50
            "4K" -> score += 30
            "720p" -> score += 10
        }
        when (option.source.videoCodec?.uppercase()) {
            "AV1", "HEVC" -> score += 20
            "H264", "AVC" -> score += 5
        }
        val seeds = option.source.seeds ?: 0
        if (seeds > 50) score += 20
        else if (seeds > 10) score += 10

        val sizeBytes = option.source.sizeBytes ?: 0L
        when {
            sizeBytes in 2_000_000_000L..8_000_000_000L -> score += 30
            sizeBytes in 8_000_000_001L..15_000_000_000L -> score += 15
            sizeBytes in 1L..1_999_999_999L -> score -= 10
        }
        score
    }
}

fun selectHighestQuality(options: List<WatchOption>, scores: Map<String, Int>): WatchOption? {
    return options.maxByOrNull { option ->
        var score = scores[option.id] ?: 0
        when (option.source.resolution) {
            "4K" -> score += 100
            "1080p" -> score += 50
        }
        val sizeBytes = option.source.sizeBytes ?: 0L
        when {
            sizeBytes >= 40_000_000_000L -> score += 100
            sizeBytes >= 20_000_000_000L -> score += 50
            sizeBytes >= 10_000_000_000L -> score += 20
        }
        val nameLower = option.source.name.lowercase()
        val sourceTitleLower = option.title.lowercase()
        val combined = "$nameLower $sourceTitleLower"
        when {
            combined.contains("dolby vision") || combined.contains("dv") -> score += 50
            combined.contains("hdr10+") || combined.contains("hdr10plus") -> score += 30
            combined.contains("hdr10") || combined.contains("hdr") -> score += 10
        }
        when {
            combined.contains("atmos") || combined.contains("truehd") -> score += 30
            combined.contains("dts-hd") || combined.contains("dts:x") || combined.contains("dtsx") -> score += 20
            combined.contains("e-ac3") || combined.contains("dd+") || combined.contains("dolby digital plus") -> score += 10
        }
        when (option.source.videoCodec?.uppercase()) {
            "AV1" -> score += 30
            "HEVC" -> score += 20
        }
        score
    }
}
