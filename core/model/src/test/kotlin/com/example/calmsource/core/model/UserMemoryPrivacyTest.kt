package com.example.calmsource.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMemoryPrivacyTest {

    @Test
    fun safeReference_isNormalizedWithoutLosingGenericIds() {
        val result = UserMemoryPrivacy.sanitizeReference(
            UserMemoryReference(
                itemKey = "  movie-tt1375666  ",
                contentType = UserMemoryContentType.MOVIE,
                title = "  Inception  ",
                providerId = "ext-torrentio",
                sourceId = "catalog-item-42"
            )
        )

        assertEquals("movie-tt1375666", result.itemKey)
        assertEquals("Inception", result.title)
        assertEquals("ext-torrentio", result.providerId)
    }

    @Test
    fun reference_rejectsUrlsAndCredentials() {
        assertThrows(IllegalArgumentException::class.java) {
            UserMemoryPrivacy.sanitizeReference(
                UserMemoryReference(
                    itemKey = "https://provider.example/movie/1?token=secret",
                    contentType = UserMemoryContentType.VOD,
                    title = "Movie"
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UserMemoryPrivacy.requireSafeIdentifier("access_token=abc123", "sourceId")
        }
    }

    @Test
    fun searchHistory_rejectsPrivateQueriesAndCleansBenignText() {
        assertNull(UserMemoryPrivacy.sanitizeSearchQuery("https://private.example/manifest.json"))
        assertNull(UserMemoryPrivacy.sanitizeSearchQuery("Bearer abcdefghijklmnopqrstuvwxyz"))
        assertNull(UserMemoryPrivacy.sanitizeSearchQuery("username=viewer password=secret"))
        assertNull(UserMemoryPrivacy.sanitizeSearchQuery("192.168.1.10/private/playlist"))
        assertNull(UserMemoryPrivacy.sanitizeSearchQuery("aBcdEfghIjklMNOPqrstuVWXyz123456"))
        assertEquals("The Matrix", UserMemoryPrivacy.sanitizeSearchQuery("  The   Matrix  "))
    }

    @Test
    fun sensitivityCheck_doesNotRejectNormalOpaqueIds() {
        assertEquals(
            "provider-8f14e45f-ea4a-4a2f",
            UserMemoryPrivacy.requireSafeIdentifier(
                "provider-8f14e45f-ea4a-4a2f",
                "providerId"
            )
        )
        assertTrue(UserMemoryPrivacy.looksSensitive("xtream://stream_id/123"))
        assertEquals(
            "org.stremio.torrentio",
            UserMemoryPrivacy.requireSafeIdentifier("org.stremio.torrentio", "providerId")
        )
    }
}
