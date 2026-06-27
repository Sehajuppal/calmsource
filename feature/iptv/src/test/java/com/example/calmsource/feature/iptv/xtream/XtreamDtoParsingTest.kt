package com.example.calmsource.feature.iptv.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Xtream DTO construction and domain model mapping.
 *
 * NOTE: JSON deserialization tests require the `kotlin.serialization` plugin in
 * feature/iptv/build.gradle.kts. These tests validate DTO defaults and mapper
 * logic using manually constructed DTOs instead.
 *
 * All test data is synthetic — no real provider URLs or credentials are used.
 */
class XtreamDtoParsingTest {

    // ─── Category DTO Defaults ───────────────────────────────────────

    @Test
    fun `category DTO defaults are correct`() {
        val dto = XtreamCategoryDto()

        assertEquals("", dto.categoryId)
        assertEquals("", dto.categoryName)
        assertEquals(0, dto.parentId)
    }

    @Test
    fun `category DTO with all fields`() {
        val dto = XtreamCategoryDto(
            categoryId = "5",
            categoryName = "Sports",
            parentId = 2
        )

        assertEquals("5", dto.categoryId)
        assertEquals("Sports", dto.categoryName)
        assertEquals(2, dto.parentId)
    }

    @Test
    fun `category DTO maps to domain model`() {
        val dto = XtreamCategoryDto(categoryId = "10", categoryName = "Movies", parentId = 3)
        val domain = dto.toDomain()

        assertEquals("10", domain.id)
        assertEquals("Movies", domain.name)
        assertEquals("3", domain.parentId)
    }

    @Test
    fun `category DTO with zero parentId maps to null`() {
        val dto = XtreamCategoryDto(categoryId = "1", categoryName = "General", parentId = 0)
        val domain = dto.toDomain()

        assertNull(domain.parentId)
    }

    // ─── Live Stream DTO → Domain ────────────────────────────────────

    @Test
    fun `live stream DTO with all fields`() {
        val dto = XtreamLiveStreamDto(
            num = 1,
            name = "Test Channel",
            streamType = "live",
            streamId = 42,
            streamIcon = "https://example.com/icon.png",
            epgChannelId = "test.ch",
            added = "1609459200",
            categoryId = "5",
            tvArchive = 1,
            directSource = "",
            tvArchiveDuration = 7
        )

        assertEquals(1, dto.num)
        assertEquals("Test Channel", dto.name)
        assertEquals("live", dto.streamType)
        assertEquals(42, dto.streamId)
        assertEquals("test.ch", dto.epgChannelId)
        assertEquals(1, dto.tvArchive)
        assertEquals(7, dto.tvArchiveDuration)
    }

    @Test
    fun `live stream DTO maps to domain model`() {
        val dto = XtreamLiveStreamDto(
            num = 1,
            name = "News HD",
            streamType = "live",
            streamId = 100,
            streamIcon = "https://example.com/news.png",
            epgChannelId = "news.hd",
            categoryId = "3",
            tvArchive = 1,
            tvArchiveDuration = 14
        )

        val domain = dto.toDomain("provider-1")

        assertEquals("provider-1_live_100", domain.id)
        assertEquals("News HD", domain.name)
        assertEquals("100", domain.streamId)
        assertEquals("3", domain.categoryId)
        assertEquals("https://example.com/news.png", domain.logo)
        assertEquals("news.hd", domain.epgChannelId)
        assertTrue(domain.tvArchive)
        assertEquals(14, domain.tvArchiveDuration)
    }

    @Test
    fun `live stream with empty icon maps to null logo`() {
        val dto = XtreamLiveStreamDto(
            name = "No Icon Channel",
            streamId = 200,
            streamIcon = "",
            categoryId = "1"
        )

        val domain = dto.toDomain("p1")
        assertNull(domain.logo)
    }

    @Test
    fun `live stream with null epgChannelId maps to empty string`() {
        val dto = XtreamLiveStreamDto(
            name = "No EPG",
            streamId = 201,
            streamIcon = "",
            categoryId = "1",
            epgChannelId = null
        )

        val domain = dto.toDomain("p1")
        assertEquals("", domain.epgChannelId)
    }

    @Test
    fun `live stream tvArchive 0 maps to false`() {
        val dto = XtreamLiveStreamDto(
            name = "No Archive",
            streamId = 202,
            streamIcon = "",
            categoryId = "1",
            tvArchive = 0
        )

        val domain = dto.toDomain("p1")
        assertFalse(domain.tvArchive)
    }

    // ─── Live Stream DTO defaults (malformed response) ───────────────

    @Test
    fun `live stream DTO defaults handle empty response`() {
        val dto = XtreamLiveStreamDto()

        assertEquals(0, dto.num)
        assertEquals("", dto.name)
        assertEquals(0, dto.streamId)
        assertEquals("", dto.streamIcon)
        assertEquals(0, dto.tvArchive)
        assertEquals("", dto.categoryId)
    }

    // ─── VOD Stream DTO → Domain ─────────────────────────────────────

    @Test
    fun `VOD stream DTO with all fields`() {
        val dto = XtreamVodStreamDto(
            num = 5,
            name = "Test Movie",
            streamType = "movie",
            streamId = 300,
            streamIcon = "https://example.com/poster.jpg",
            added = "1640000000",
            categoryId = "10",
            containerExtension = "mkv",
            rating = "7.5",
            directSource = ""
        )

        assertEquals(5, dto.num)
        assertEquals("Test Movie", dto.name)
        assertEquals(300, dto.streamId)
        assertEquals("mkv", dto.containerExtension)
        assertEquals("7.5", dto.rating)
    }

    @Test
    fun `VOD stream DTO maps to domain model`() {
        val dto = XtreamVodStreamDto(
            name = "Action Movie",
            streamId = 500,
            streamIcon = "https://example.com/action.jpg",
            categoryId = "12",
            containerExtension = "avi",
            rating = "8.2",
            added = "1640000000"
        )

        val domain = dto.toDomain("provider-2")

        assertEquals("provider-2_vod_500", domain.id)
        assertEquals("Action Movie", domain.name)
        assertEquals("500", domain.streamId)
        assertEquals("12", domain.categoryId)
        assertEquals("https://example.com/action.jpg", domain.poster)
        assertEquals(8.2, domain.rating!!, 0.01)
        assertEquals("avi", domain.containerExtension)
        assertEquals(1640000000L, domain.added)
    }

    @Test
    fun `VOD stream with null rating maps to null`() {
        val dto = XtreamVodStreamDto(
            name = "No Rating Movie",
            streamId = 600,
            streamIcon = "",
            categoryId = "1",
            rating = null
        )

        val domain = dto.toDomain("p1")
        assertNull(domain.rating)
        assertNull(domain.poster) // empty icon → null poster
    }

    @Test
    fun `VOD stream with invalid rating string maps to null`() {
        val dto = XtreamVodStreamDto(
            name = "Bad Rating",
            streamId = 601,
            streamIcon = "",
            categoryId = "1",
            rating = "N/A"
        )

        val domain = dto.toDomain("p1")
        assertNull(domain.rating)
    }

    @Test
    fun `VOD stream with null added maps to zero`() {
        val dto = XtreamVodStreamDto(
            name = "No Added",
            streamId = 602,
            streamIcon = "",
            categoryId = "1",
            added = null
        )

        val domain = dto.toDomain("p1")
        assertEquals(0L, domain.added)
    }

    @Test
    fun `VOD stream DTO defaults handle empty response`() {
        val dto = XtreamVodStreamDto()

        assertEquals(0, dto.num)
        assertEquals("", dto.name)
        assertEquals(0, dto.streamId)
        assertEquals("mp4", dto.containerExtension)
    }

    // ─── Series DTO → Domain ─────────────────────────────────────────

    @Test
    fun `series DTO with all fields`() {
        val dto = XtreamSeriesDto(
            num = 2,
            name = "Test Series",
            seriesId = 77,
            cover = "https://example.com/cover.jpg",
            categoryId = "8",
            rating = "9.1"
        )

        assertEquals(2, dto.num)
        assertEquals("Test Series", dto.name)
        assertEquals(77, dto.seriesId)
        assertEquals("https://example.com/cover.jpg", dto.cover)
        assertEquals("9.1", dto.rating)
    }

    @Test
    fun `series DTO maps to domain model`() {
        val dto = XtreamSeriesDto(
            name = "Drama Series",
            seriesId = 88,
            cover = "https://example.com/drama.jpg",
            categoryId = "6",
            rating = "8.5"
        )

        val domain = dto.toDomain("provider-3")

        assertEquals("provider-3_series_88", domain.id)
        assertEquals("Drama Series", domain.name)
        assertEquals("88", domain.seriesId)
        assertEquals("6", domain.categoryId)
        assertEquals("https://example.com/drama.jpg", domain.poster)
        assertEquals(8.5, domain.rating!!, 0.01)
    }

    @Test
    fun `series with empty cover maps to null poster`() {
        val dto = XtreamSeriesDto(
            name = "No Cover",
            seriesId = 99,
            cover = "",
            categoryId = "1"
        )

        val domain = dto.toDomain("p1")
        assertNull(domain.poster)
    }

    @Test
    fun `series DTO defaults handle empty response`() {
        val dto = XtreamSeriesDto()

        assertEquals(0, dto.num)
        assertEquals("", dto.name)
        assertEquals(0, dto.seriesId)
        assertEquals("", dto.cover)
    }

    // ─── Live Channel → IPTVChannel ──────────────────────────────────

    @Test
    fun `live channel maps to IPTVChannel with xtream pseudo URL`() {
        val channel = com.example.calmsource.core.model.XtreamLiveChannel(
            id = "prov_live_42",
            name = "Sports HD",
            streamId = "42",
            categoryId = "5",
            logo = "https://example.com/sports.png",
            epgChannelId = "sports.hd",
            tvArchive = true,
            tvArchiveDuration = 7
        )

        val iptv = channel.toIPTVChannel("prov")

        assertEquals("prov_live_42", iptv.id)
        assertEquals("Sports HD", iptv.name)
        assertEquals("sports.hd", iptv.tvgId)
        assertEquals("https://example.com/sports.png", iptv.tvgLogo)
        assertEquals("xtream://stream_id/prov/42", iptv.streamUrl) // Pseudo-URL — resolved to real URL at playback
        assertEquals("prov", iptv.providerId)
        assertEquals("42", iptv.rawAttributes["xtream_stream_id"])
        assertEquals("true", iptv.rawAttributes["xtream_source"])
        assertEquals("1", iptv.rawAttributes["tv_archive"])
        assertEquals("7", iptv.rawAttributes["tv_archive_duration"])
    }

    @Test
    fun `live channel without EPG id maps tvgId to null`() {
        val channel = com.example.calmsource.core.model.XtreamLiveChannel(
            id = "prov_live_10",
            name = "Basic Channel",
            streamId = "10",
            categoryId = "1",
            epgChannelId = ""
        )

        val iptv = channel.toIPTVChannel("prov")
        assertNull(iptv.tvgId)
    }

    @Test
    fun `live channel without archive omits archive attributes`() {
        val channel = com.example.calmsource.core.model.XtreamLiveChannel(
            id = "prov_live_11",
            name = "Simple Channel",
            streamId = "11",
            categoryId = "1",
            tvArchive = false,
            tvArchiveDuration = 0
        )

        val iptv = channel.toIPTVChannel("prov")
        assertNull(iptv.rawAttributes["tv_archive"])
        assertNull(iptv.rawAttributes["tv_archive_duration"])
    }

    // ─── Auth & Server Info Mappers ──────────────────────────────────

    @Test
    fun `user info DTO maps to domain without password`() {
        val dto = XtreamUserInfoDto(
            username = "testuser",
            password = "secret123", // Should NOT propagate
            status = "Active",
            auth = 1,
            expDate = "1700000000",
            isTrial = "1",
            activeCons = "2",
            maxConnections = "5",
            allowedOutputFormats = listOf("m3u8", "ts")
        )

        val domain = dto.toDomain()

        assertEquals("testuser", domain.username)
        assertEquals("Active", domain.status)
        assertEquals(1700000000L, domain.expirationDate)
        assertTrue(domain.isTrial)
        assertEquals(2, domain.activeConnections)
        assertEquals(5, domain.maxConnections)
        assertEquals(listOf("m3u8", "ts"), domain.allowedOutputFormats)
        // XtreamUserInfo has no password field — verified by compilation
    }

    @Test
    fun `user info DTO with null optional fields uses defaults`() {
        val dto = XtreamUserInfoDto(
            username = "user",
            status = "Active",
            expDate = null,
            isTrial = null,
            activeCons = null,
            maxConnections = null
        )

        val domain = dto.toDomain()

        assertNull(domain.expirationDate)
        assertFalse(domain.isTrial)
        assertEquals(0, domain.activeConnections)
        assertEquals(0, domain.maxConnections)
    }

    @Test
    fun `user info isTrial only true for string 1`() {
        val trialDto = XtreamUserInfoDto(isTrial = "1")
        val nonTrialDto = XtreamUserInfoDto(isTrial = "0")
        val otherDto = XtreamUserInfoDto(isTrial = "true")

        assertTrue(trialDto.toDomain().isTrial)
        assertFalse(nonTrialDto.toDomain().isTrial)
        assertFalse(otherDto.toDomain().isTrial) // Only "1" is true
    }

    @Test
    fun `server info DTO maps to domain with numeric conversions`() {
        val dto = XtreamServerInfoDto(
            url = "example.com",
            port = "8080",
            httpsPort = "8443",
            serverProtocol = "https",
            timezone = "UTC"
        )

        val domain = dto.toDomain()

        assertEquals("example.com", domain.url)
        assertEquals(8080, domain.port)
        assertEquals(8443, domain.httpsPort)
        assertEquals("https", domain.serverProtocol)
        assertEquals("UTC", domain.timezone)
    }

    @Test
    fun `server info DTO with non-numeric ports defaults to zero`() {
        val dto = XtreamServerInfoDto(
            url = "example.com",
            port = "",
            httpsPort = "invalid"
        )

        val domain = dto.toDomain()

        assertEquals(0, domain.port)
        assertEquals(0, domain.httpsPort)
    }

    // ─── EPG Mapper ──────────────────────────────────────────────────

    @Test
    fun `EPG listing DTO maps to domain`() {
        val dto = XtreamEpgListingDto(
            id = "epg-1",
            epgId = "ch.epg",
            title = "Evening News",
            lang = "en",
            start = "1700000000",
            end = "1700003600",
            description = "Daily news broadcast",
            channelId = "ch-1"
        )

        val domain = dto.toDomain()

        assertEquals("epg-1", domain.id)
        assertEquals("ch.epg", domain.epgId)
        assertEquals("Evening News", domain.title)
        assertEquals("en", domain.language)
        assertEquals(1700000000L, domain.startTimestamp)
        assertEquals(1700003600L, domain.endTimestamp)
        assertEquals("Daily news broadcast", domain.description)
    }

    @Test
    fun `EPG listing with non-numeric timestamps defaults to zero`() {
        val dto = XtreamEpgListingDto(
            id = "epg-2",
            epgId = "ch.epg",
            title = "Test",
            start = "bad",
            end = ""
        )

        val domain = dto.toDomain()

        assertEquals(0L, domain.startTimestamp)
        assertEquals(0L, domain.endTimestamp)
    }

    // ─── Searchable VOD ──────────────────────────────────────────────

    @Test
    fun `VOD item maps to searchable VOD`() {
        val vod = com.example.calmsource.core.model.XtreamVodItem(
            id = "p1_vod_100",
            name = "Documentary",
            streamId = "100",
            categoryId = "3",
            poster = "https://example.com/doc.jpg",
            rating = 7.8,
            containerExtension = "mkv"
        )

        val searchable = vod.toSearchableVod("p1")

        assertEquals("Documentary", searchable.name)
        assertEquals("100", searchable.streamId)
        assertEquals("3", searchable.categoryId)
        assertEquals("https://example.com/doc.jpg", searchable.poster)
        assertEquals(7.8, searchable.rating!!, 0.01)
        assertEquals("mkv", searchable.containerExtension)
        assertEquals("p1", searchable.providerId)
    }

    @Test
    fun `VOD item with null optional fields maps correctly`() {
        val vod = com.example.calmsource.core.model.XtreamVodItem(
            id = "p1_vod_200",
            name = "Basic",
            streamId = "200",
            categoryId = "1",
            poster = null,
            rating = null
        )

        val searchable = vod.toSearchableVod("p1")

        assertNull(searchable.poster)
        assertNull(searchable.rating)
        assertEquals("mp4", searchable.containerExtension) // default
    }
}
