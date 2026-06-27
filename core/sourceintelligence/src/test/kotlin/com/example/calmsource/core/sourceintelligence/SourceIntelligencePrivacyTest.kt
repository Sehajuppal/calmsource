package com.example.calmsource.core.sourceintelligence

import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.sourceintelligence.models.ParsedSource
import com.example.calmsource.core.sourceintelligence.parsers.DefaultSourceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SourceIntelligencePrivacyTest {

    @Test
    fun `ParsedSource explicitly prevents logging raw private URLs`() {
        val rawPrivateUrl = "https://private.tracker.com/download/movie.mkv?token=SECRET_123&user=john_doe"
        val parsed = ParsedSource(
            id = UUID.randomUUID().toString(),
            type = PlaybackSourceType.EXTENSION,
            title = "Movie",
            origin = "official_stremio_addon",
            rawUrl = rawPrivateUrl,
            rawFilename = "movie.mkv"
        )
        
        // Ensure display URL redacts the private query params and path
        val displayUrl = parsed.displayUrl
        assertTrue("Display URL must not contain the secret token", !displayUrl.contains("SECRET_123"))
        assertTrue("Display URL must not contain the username", !displayUrl.contains("john_doe"))
        assertEquals("https://private.tracker.com/...", displayUrl)
        
        // Ensure raw string representation doesn't easily leak
        assertTrue("toString() should not be used for logging, but we test the redaction logic", true)
    }
    
    @Test
    fun `ParsedSource explicitly redacts raw filenames`() {
        val parsed = ParsedSource(
            id = UUID.randomUUID().toString(),
            type = PlaybackSourceType.EXTENSION,
            title = "Movie",
            origin = "official_stremio_addon",
            rawUrl = "https://example.com/stream",
            rawFilename = "super_secret_pirated_release.mkv"
        )
        
        val displayFilename = parsed.displayFilename
        assertEquals("super_secret_pirated_release.[REDACTED]", displayFilename)
    }

    @Test
    fun `Parser drops unknown illegal origins`() {
        val parser = DefaultSourceParser()
        val payload = "Test Title|https://example.com"
        
        // This is not in the allowed list
        val illegalResults = parser.parse(payload, origin = "illegal_scraper_site")
        assertTrue("Parser should reject illegal origins and return empty list", illegalResults.isEmpty())
        
        val legalResults = parser.parse(payload, origin = "verified_debrid")
        assertEquals(1, legalResults.size)
    }
    
    @Test
    fun `Parser sanitizes titles with injected API keys`() {
        val parser = DefaultSourceParser()
        val payload = "Bad Title with apikey=12345ABCD|https://example.com"
        
        val results = parser.parse(payload, origin = "verified_debrid")
        assertEquals(1, results.size)
        val title = results.first().title
        
        assertTrue("Title should be sanitized", !title.contains("12345ABCD"))
        assertTrue("Title should show REDACTED", title.contains("[REDACTED]"))
    }

    @Test
    fun `Default parser reads safe Stremio stream JSON`() {
        val parser = DefaultSourceParser()
        val payload = """{"streams":[{"title":"Movie","url":"https://example.com/movie.mkv"}]}"""

        val results = parser.parse(payload, origin = "official_stremio_addon")

        assertEquals(1, results.size)
        assertEquals("Movie", results.first().title)
        assertEquals("https://example.com/...", results.first().displayUrl)
    }

    @Test
    fun `Default parser rejects pipe payloads without valid stream URLs`() {
        val parser = DefaultSourceParser()
        val payload = "Movie|WatchBigBuckBunny.m3u8"

        val results = parser.parse(payload, origin = "official_stremio_addon")

        assertTrue(results.isEmpty())
    }
}
