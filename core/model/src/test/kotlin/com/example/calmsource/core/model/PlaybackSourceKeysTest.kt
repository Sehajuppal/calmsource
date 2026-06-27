package com.example.calmsource.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSourceKeysTest {

    @Test
    fun `stableSourceIdForWatchOption hashes iptv pseudo urls`() {
        val url = "xtream://stream_id/42"
        assertEquals(generateSafeSourceId(url), stableSourceIdForWatchOption(SourceType.IPTV, url, "opt-1"))
    }

    @Test
    fun `stableSourceIdForWatchOption uses option id for extension and debrid`() {
        assertEquals("ext-99", stableSourceIdForWatchOption(SourceType.EXTENSION, "http://x", "ext-99"))
        assertEquals("deb-1", stableSourceIdForWatchOption(SourceType.DEBRID, "magnet:?xt=urn:btih:abc", "deb-1"))
    }
}
