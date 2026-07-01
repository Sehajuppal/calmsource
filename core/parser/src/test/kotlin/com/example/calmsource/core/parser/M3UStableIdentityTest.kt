package com.example.calmsource.core.parser

import com.example.calmsource.core.model.IPTVChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class M3UStableIdentityTest {

    @Test
    fun `credential rotation keeps channel identity stable`() = runBlocking {
        val first = parseChannel("https://provider.test/live/user/old-token/42.ts")
        val rotated = parseChannel("https://provider.test/live/user/new-token/42.ts")

        assertEquals(first.id, rotated.id)
        assertNotEquals(first.streamUrl, rotated.streamUrl)
    }

    private suspend fun parseChannel(url: String): IPTVChannel {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="news.ca" tvg-name="News CA" group-title="News",News CA
            $url
        """.trimIndent()
        val channels = mutableListOf<IPTVChannel>()
        val result = M3UParser.parse(
            ByteArrayInputStream(playlist.toByteArray()),
            providerId = "provider-1"
        ) { channels += it }
        check(result.isSuccess)
        return channels.single()
    }
}
