package com.example.calmsource.core.playback

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamFormatFallbackTest {

    @Test
    fun `buildMimeRetrySequence filters out inferred HLS mime type`() {
        val uri = "http://example.com/stream.m3u8"
        val sequence = StreamFormatFallback.buildMimeRetrySequence(uri)

        // HLS is inferred, so it should be filtered out
        assertFalse(sequence.contains(MimeTypes.APPLICATION_M3U8))
        assertTrue(sequence.contains(MimeTypes.VIDEO_MP4))
        assertEquals(1, sequence.size)
    }

    @Test
    fun `buildMimeRetrySequence filters out inferred TS mime type`() {
        val uri = "http://example.com/stream.ts"
        val sequence = StreamFormatFallback.buildMimeRetrySequence(uri)

        // TS is inferred, so it should be filtered out
        assertFalse(sequence.contains(MimeTypes.VIDEO_MP2T))
        assertTrue(sequence.contains(MimeTypes.APPLICATION_M3U8))
        assertEquals(1, sequence.size)
    }

    @Test
    fun `buildMimeRetrySequence filters out inferred DASH mime type`() {
        val uri = "http://example.com/stream.mpd"
        val sequence = StreamFormatFallback.buildMimeRetrySequence(uri)

        // DASH is inferred, so it should be filtered out
        assertFalse(sequence.contains(MimeTypes.APPLICATION_MPD))
        assertTrue(sequence.contains(MimeTypes.VIDEO_MP4))
        assertEquals(1, sequence.size)
    }

    @Test
    fun `buildMimeRetrySequence filters out inferred MP4 mime type`() {
        val uri = "http://example.com/stream.mp4"
        val sequence = StreamFormatFallback.buildMimeRetrySequence(uri)

        // MP4 is inferred, but default for unknown includes HLS and MP4, so MP4 is filtered out
        assertFalse(sequence.contains(MimeTypes.VIDEO_MP4))
        assertTrue(sequence.contains(MimeTypes.APPLICATION_M3U8))
        assertEquals(1, sequence.size)
    }

    @Test
    fun `buildMimeRetrySequence handles uppercase extensions`() {
        val uriHls = "http://example.com/stream.M3U8"
        val sequenceHls = StreamFormatFallback.buildMimeRetrySequence(uriHls)
        assertFalse(sequenceHls.contains(MimeTypes.APPLICATION_M3U8))
        assertTrue(sequenceHls.contains(MimeTypes.VIDEO_MP4))

        val uriTs = "HTTP://EXAMPLE.COM/STREAM.TS"
        val sequenceTs = StreamFormatFallback.buildMimeRetrySequence(uriTs)
        assertFalse(sequenceTs.contains(MimeTypes.VIDEO_MP2T))
        assertTrue(sequenceTs.contains(MimeTypes.APPLICATION_M3U8))
    }

    @Test
    fun `buildMimeRetrySequence detects formats from query parameters`() {
        val hlsUrl = "http://example.com/stream?type=apple&auth=123"
        val hlsSeq = StreamFormatFallback.buildMimeRetrySequence(hlsUrl)
        assertFalse(hlsSeq.contains(MimeTypes.APPLICATION_M3U8))
        assertTrue(hlsSeq.contains(MimeTypes.VIDEO_MP4))

        val tsUrl = "http://example.com/stream?output=ts"
        val tsSeq = StreamFormatFallback.buildMimeRetrySequence(tsUrl)
        assertFalse(tsSeq.contains(MimeTypes.VIDEO_MP2T))
        assertTrue(tsSeq.contains(MimeTypes.APPLICATION_M3U8))
    }

    @Test
    fun `buildMimeRetrySequence defaults for unknown formats or empty URI`() {
        val unknownUrl = "http://example.com/stream.mkv"
        val seqUnknown = StreamFormatFallback.buildMimeRetrySequence(unknownUrl)
        assertTrue(seqUnknown.contains(MimeTypes.APPLICATION_M3U8))
        assertTrue(seqUnknown.contains(MimeTypes.VIDEO_MP4))
        assertFalse(seqUnknown.contains(MimeTypes.VIDEO_MATROSKA))
        assertEquals(2, seqUnknown.size)

        val extensionlessUrl = "http://example.com/d/abc123"
        val seqExtensionless = StreamFormatFallback.buildMimeRetrySequence(extensionlessUrl)
        assertTrue(seqExtensionless.contains(MimeTypes.VIDEO_MATROSKA))
        assertTrue(seqExtensionless.contains(MimeTypes.APPLICATION_M3U8))
        assertTrue(seqExtensionless.contains(MimeTypes.VIDEO_MP4))
        assertTrue(seqExtensionless.contains(MimeTypes.VIDEO_MP2T))
        assertEquals(4, seqExtensionless.size)

        val emptyUrl = ""
        val seqEmpty = StreamFormatFallback.buildMimeRetrySequence(emptyUrl)
        assertEquals(4, seqEmpty.size)
    }
}

