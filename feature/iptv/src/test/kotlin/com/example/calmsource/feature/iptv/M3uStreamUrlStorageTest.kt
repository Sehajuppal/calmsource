package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.IPTVChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class M3uStreamUrlStorageTest {

    @Before
    fun setUp() {
        XtreamRepository.setSecureTokenStore(FakeInMemoryIptvSecureTokenStore())
    }

    @Test
    fun `sensitive M3U URLs are redacted for persistence`() {
        val raw = "http://user:secret@example.com/live/stream.ts"
        assertTrue(M3uStreamUrlStorage.containsSensitiveMaterial(raw))
        val sanitized = M3uStreamUrlStorage.sanitizeForPersistence(raw)
        assertFalse(sanitized.contains("secret"))
        assertTrue(sanitized.contains("REDACTED") || !sanitized.contains("user:secret"))
    }

    @Test
    fun `playback restores credential-bearing URL from secure store`() {
        val channelId = "channel-1"
        val providerId = "provider-1"
        val raw = "http://user:secret@example.com/live/stream.ts"
        M3uStreamUrlStorage.persistSecureUrl(providerId, channelId, raw)
        val channel = IPTVChannel(
            id = channelId,
            name = "Test",
            streamUrl = M3uStreamUrlStorage.sanitizeForPersistence(raw),
            providerId = providerId
        )
        val resolved = M3uStreamUrlStorage.resolvePlaybackUrl(channel)
        org.junit.Assert.assertNotNull("resolvePlaybackUrl should not return null after persistSecureUrl", resolved)
        assertEquals(raw, resolved)
    }
}
