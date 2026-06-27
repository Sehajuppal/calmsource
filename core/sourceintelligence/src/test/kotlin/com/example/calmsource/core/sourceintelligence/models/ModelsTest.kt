package com.example.calmsource.core.sourceintelligence.models

import com.example.calmsource.core.model.StreamSource
import com.example.calmsource.core.model.SourceType
import com.example.calmsource.core.model.WatchOption
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {

    @Test
    fun `test unknown safe states`() {
        val parsed = ParsedSourceMetadata() // default constructor should use UNKNOWN

        assertEquals(SourceQuality.UNKNOWN, parsed.quality)
        assertEquals(SourceResolution.UNKNOWN, parsed.resolution)
        assertEquals(SourceHdrFormat.UNKNOWN, parsed.hdrFormat)
        assertEquals(SourceVideoCodec.UNKNOWN, parsed.videoCodec)
        assertEquals(SourceAudioFormat.UNKNOWN, parsed.audioFormat)
        assertEquals(SourceAudioChannelLayout.UNKNOWN, parsed.audioChannels)
        assertEquals(SourceLanguageInfo.UNKNOWN, parsed.languageInfo)
        assertEquals(SourceSubtitleInfo.UNKNOWN, parsed.subtitleInfo)
        assertEquals(SourceReleaseType.UNKNOWN, parsed.releaseType)
        assertEquals(SourceSizeInfo.UNKNOWN, parsed.sizeInfo)
    }

    @Test
    fun `test mapping from StreamSource to RawSourceInput`() {
        val streamSource = StreamSource(
            id = "test_id",
            name = "Inception.2010.1080p.BluRay.x264.mkv",
            url = "http://example.com/stream",
            extensionId = "ext_1",
            resolution = "1080p",
            language = "English"
        )

        val rawInput = streamSource.toRawSourceInput()
        assertEquals("Inception.2010.1080p.BluRay.x264.mkv", rawInput.rawFilename)
        assertEquals("http://example.com/stream", rawInput.rawUrl)
        assertEquals(null, rawInput.rawTitle)
    }

    @Test
    fun `test mapping from WatchOption to RawSourceInput`() {
        val streamSource = StreamSource(
            id = "test_id",
            name = "Inception.2010.1080p.BluRay.x264.mkv",
            url = "http://example.com/stream",
            extensionId = "ext_1",
            resolution = "1080p",
            language = "English"
        )

        val watchOption = WatchOption(
            id = "opt_1",
            title = "Inception",
            source = streamSource,
            type = SourceType.EXTENSION,
            languageLabel = "English"
        )

        val rawInput = watchOption.toRawSourceInput()
        assertEquals("Inception.2010.1080p.BluRay.x264.mkv", rawInput.rawFilename)
        assertEquals("http://example.com/stream", rawInput.rawUrl)
        assertEquals("Inception", rawInput.rawTitle)
    }

    @Test
    fun `test label creation and raw filename separation`() {
        val rawInput = RawSourceInput(
            rawFilename = "Inception.2010.1080p.BluRay.x264.mkv",
            rawTitle = "Inception",
            rawUrl = "http://example.com/stream"
        )
        
        val displayLabel = SourceDisplayLabel(
            primaryLabel = "Inception (2010)",
            secondaryLabel = "1080p BluRay x264",
            tags = listOf("HD", "English")
        )

        val result = SourceIntelligenceResult(
            rawMetadata = rawInput.toRawSourceMetadata(),
            parsedMetadata = ParsedSourceMetadata(
                quality = SourceQuality.BLURAY,
                resolution = SourceResolution.FHD
            ),
            confidence = SourceConfidence(0.9f),
            displayLabel = displayLabel,
            rankingFeatures = SourceRankingFeatures(isHevc = false)
        )

        assertEquals("Inception (2010)", result.displayLabel.primaryLabel)
        assertEquals("1080p BluRay x264", result.displayLabel.secondaryLabel)
        assertEquals(listOf("HD", "English"), result.displayLabel.tags)
    }
}
