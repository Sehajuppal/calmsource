package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.EPGSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EpgSourceUrlStorageTest {
    @Before
    fun setUp() {
        XtreamRepository.setSecureTokenStore(FakeInMemoryIptvSecureTokenStore())
    }

    @Test
    fun `credential-bearing EPG URL is redacted and restored`() {
        val raw = "https://guide.example.com/xmltv?access_token=secret-value"
        EpgSourceUrlStorage.persist("provider", "source", raw)
        val sanitized = EpgSourceUrlStorage.sanitizeForPersistence(raw)
        assertFalse(sanitized.contains("secret-value"))
        assertTrue(sanitized.contains("REDACTED"))

        val source = EPGSource(
            id = "source",
            providerId = "provider",
            name = "Guide",
            url = sanitized,
            lastSyncMs = 0L
        )
        assertEquals(raw, EpgSourceUrlStorage.resolve(source))
    }
}
