package com.example.calmsource.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalmSourceDeepLinkTest {
    @Test
    fun `details route round trips sanitized user memory`() {
        val uri = CalmSourceDeepLink.detailsUri(
            UserMemoryReference(
                itemKey = "media-safe-id",
                contentType = UserMemoryContentType.SHOW,
                title = "Example Show",
                sourceId = "tt1234567"
            ),
            positionMs = 42_000L
        )

        val parsed = CalmSourceDeepLink.parse(uri) as CalmSourceDeepLink.Details
        assertEquals("tt1234567", parsed.mediaItem.id)
        assertEquals("Example Show", parsed.mediaItem.title)
        assertEquals(MediaType.SHOW, parsed.mediaItem.type)
        assertEquals(42_000L, parsed.startPositionMs)
    }

    @Test
    fun `unsafe or unrelated routes are rejected`() {
        assertNull(CalmSourceDeepLink.parse("https://example.com/details/movie"))
        assertNull(CalmSourceDeepLink.parse("calmsource://details/https%3A%2F%2Fsecret.example%2Fstream"))
        assertNull(CalmSourceDeepLink.parse("calmsource://unknown/value"))
        assertNull(CalmSourceDeepLink.parse("calmsource://channel/..%2Fsecret"))
    }

    @Test
    fun `malformed route stress remains bounded`() {
        repeat(2_000) { index ->
            val parsed = CalmSourceDeepLink.parse("calmsource://details/%$index?positionMs=not-a-number")
            assertTrue(parsed == null || parsed is CalmSourceDeepLink.Details)
        }
    }
}
