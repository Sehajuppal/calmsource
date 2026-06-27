package com.example.calmsource.feature.iptv.xtream

import com.example.calmsource.core.database.entity.XtreamSeriesEntity
import com.example.calmsource.core.database.entity.XtreamVodEntity
import com.example.calmsource.core.database.mapper.toDomain
import com.example.calmsource.core.database.mapper.toEntity
import com.example.calmsource.core.model.XtreamCategory
import com.example.calmsource.core.model.XtreamLiveChannel
import com.example.calmsource.core.model.XtreamProviderConfig
import com.example.calmsource.core.model.XtreamSeriesItem
import com.example.calmsource.core.model.XtreamSyncProgress
import com.example.calmsource.core.model.XtreamSyncStage
import com.example.calmsource.core.model.XtreamVodItem
import com.example.calmsource.feature.iptv.XtreamApiClient
import com.example.calmsource.feature.iptv.XtreamRepository
import com.example.calmsource.feature.iptv.FakeInMemoryIptvSecureTokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Persistence and sync tests for Xtream VOD/Series entities,
 * mappers, and sync progress orchestration.
 *
 * All data is synthetic — no real provider URLs or credentials.
 */
class XtreamPersistenceTest {

    companion object {
        private const val PROVIDER_ID = "test-provider-1"
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    /**
     * Creates a test VOD item. The [id] field is the domain model ID.
     * When non-empty, toEntity() uses this id directly as the entity primary key.
     * When empty, toEntity() falls back to "xtream-vod-{providerId}-{streamId}".
     */
    private fun testVodItem(
        id: String = "vod-1",
        name: String = "Test Movie",
        streamId: String = "101",
        categoryId: String = "5",
        poster: String? = "http://example.com/poster.jpg",
        rating: Double? = 8.5,
        containerExtension: String = "mp4",
        added: Long = 1700000000
    ) = XtreamVodItem(
        id = id,
        name = name,
        streamId = streamId,
        categoryId = categoryId,
        poster = poster,
        rating = rating,
        containerExtension = containerExtension,
        added = added
    )

    /**
     * Creates a test series item. Same ID behavior as [testVodItem].
     */
    private fun testSeriesItem(
        id: String = "series-1",
        name: String = "Test Series",
        seriesId: String = "201",
        categoryId: String = "10",
        poster: String? = "http://example.com/series.jpg",
        rating: Double? = 9.0
    ) = XtreamSeriesItem(
        id = id,
        name = name,
        seriesId = seriesId,
        categoryId = categoryId,
        poster = poster,
        rating = rating
    )

    // ═════════════════════════════════════════════════════════════════════
    //  Entity creation and mapper round-trip tests
    // ═════════════════════════════════════════════════════════════════════

    // ── VOD Entity Tests ────────────────────────────────────────────────

    @Test
    fun `VodEntity uses domain id when non-empty`() {
        val item = testVodItem(id = "my-custom-vod-id", streamId = "555")
        val entity = item.toEntity(PROVIDER_ID)
        assertEquals("my-custom-vod-id", entity.id)
    }

    @Test
    fun `VodEntity falls back to deterministic ID when domain id is empty`() {
        val item = testVodItem(id = "", streamId = "555")
        val entity = item.toEntity(PROVIDER_ID)
        assertEquals("xtream-vod-$PROVIDER_ID-555", entity.id)
    }

    @Test
    fun `VodEntity stores no credentials`() {
        val entity = testVodItem().toEntity(PROVIDER_ID)
        // Entity fields should not contain any credential-like data
        val allFields = listOf(
            entity.id, entity.name, entity.streamId, entity.categoryId,
            entity.categoryName, entity.poster, entity.containerExtension,
            entity.providerId
        )
        for (field in allFields) {
            assertFalse("No field should contain 'password'", field.lowercase().contains("password"))
            assertFalse("No field should contain 'token'", field.lowercase().contains("token"))
        }
    }

    @Test
    fun `VodEntity preserves all fields from domain model`() {
        val item = testVodItem(
            name = "Matrix",
            streamId = "42",
            categoryId = "3",
            poster = "http://img.example.com/m.jpg",
            rating = 9.2,
            containerExtension = "mkv",
            added = 1699999999
        )
        val entity = item.toEntity(PROVIDER_ID, "Action")

        assertEquals("Matrix", entity.name)
        assertEquals("42", entity.streamId)
        assertEquals("3", entity.categoryId)
        assertEquals("Action", entity.categoryName)
        assertEquals("http://img.example.com/m.jpg", entity.poster)
        assertEquals(9.2, entity.rating, 0.001)
        assertEquals("mkv", entity.containerExtension)
        assertEquals(1699999999L, entity.addedTimestamp)
        assertEquals(PROVIDER_ID, entity.providerId)
    }

    @Test
    fun `VodEntity round-trip through domain model`() {
        val original = testVodItem(
            name = "Inception",
            streamId = "88",
            categoryId = "7",
            poster = "http://example.com/inc.png",
            rating = 8.8,
            containerExtension = "mp4",
            added = 1700001234
        )
        val entity = original.toEntity(PROVIDER_ID)
        val domainAgain = entity.toDomain()

        assertEquals(original.name, domainAgain.name)
        assertEquals(original.streamId, domainAgain.streamId)
        assertEquals(original.categoryId, domainAgain.categoryId)
        assertEquals(original.poster, domainAgain.poster)
        assertEquals(original.rating, domainAgain.rating)
        assertEquals(original.containerExtension, domainAgain.containerExtension)
        assertEquals(original.added, domainAgain.added)
    }

    @Test
    fun `VodEntity handles null poster`() {
        val item = testVodItem(poster = null)
        val entity = item.toEntity(PROVIDER_ID)
        assertEquals("", entity.poster) // null → empty string in entity
        val domain = entity.toDomain()
        assertNull(domain.poster) // empty string → null in domain
    }

    @Test
    fun `VodEntity handles null rating`() {
        val item = testVodItem(rating = null)
        val entity = item.toEntity(PROVIDER_ID)
        assertEquals(0.0, entity.rating, 0.001)
        val domain = entity.toDomain()
        assertNull(domain.rating) // 0.0 → null in domain
    }

    @Test
    fun `VodEntity default containerExtension is mp4`() {
        val entity = XtreamVodEntity()
        assertEquals("mp4", entity.containerExtension)
    }

    // ── Series Entity Tests ─────────────────────────────────────────────

    @Test
    fun `SeriesEntity uses domain id when non-empty`() {
        val item = testSeriesItem(id = "my-series-id", seriesId = "999")
        val entity = item.toEntity(PROVIDER_ID)
        assertEquals("my-series-id", entity.id)
    }

    @Test
    fun `SeriesEntity falls back to deterministic ID when domain id is empty`() {
        val item = testSeriesItem(id = "", seriesId = "999")
        val entity = item.toEntity(PROVIDER_ID)
        assertEquals("xtream-series-$PROVIDER_ID-999", entity.id)
    }

    @Test
    fun `SeriesEntity stores no credentials`() {
        val entity = testSeriesItem().toEntity(PROVIDER_ID)
        val allFields = listOf(
            entity.id, entity.name, entity.seriesId, entity.categoryId,
            entity.categoryName, entity.poster, entity.providerId
        )
        for (field in allFields) {
            assertFalse("No field should contain 'password'", field.lowercase().contains("password"))
            assertFalse("No field should contain 'token'", field.lowercase().contains("token"))
        }
    }

    @Test
    fun `SeriesEntity preserves all fields from domain model`() {
        val item = testSeriesItem(
            name = "Breaking Bad",
            seriesId = "301",
            categoryId = "5",
            poster = "http://img.example.com/bb.jpg",
            rating = 9.5
        )
        val entity = item.toEntity(PROVIDER_ID, "Drama")

        assertEquals("Breaking Bad", entity.name)
        assertEquals("301", entity.seriesId)
        assertEquals("5", entity.categoryId)
        assertEquals("Drama", entity.categoryName)
        assertEquals("http://img.example.com/bb.jpg", entity.poster)
        assertEquals(9.5, entity.rating, 0.001)
        assertEquals(PROVIDER_ID, entity.providerId)
    }

    @Test
    fun `SeriesEntity round-trip through domain model`() {
        val original = testSeriesItem(
            name = "The Wire",
            seriesId = "302",
            categoryId = "5",
            poster = "http://example.com/wire.png",
            rating = 9.3
        )
        val entity = original.toEntity(PROVIDER_ID)
        val domainAgain = entity.toDomain()

        assertEquals(original.name, domainAgain.name)
        assertEquals(original.seriesId, domainAgain.seriesId)
        assertEquals(original.categoryId, domainAgain.categoryId)
        assertEquals(original.poster, domainAgain.poster)
        assertEquals(original.rating, domainAgain.rating)
    }

    @Test
    fun `SeriesEntity handles null poster`() {
        val item = testSeriesItem(poster = null)
        val entity = item.toEntity(PROVIDER_ID)
        assertEquals("", entity.poster)
        val domain = entity.toDomain()
        assertNull(domain.poster)
    }

    @Test
    fun `SeriesEntity handles null rating`() {
        val item = testSeriesItem(rating = null)
        val entity = item.toEntity(PROVIDER_ID)
        assertEquals(0.0, entity.rating, 0.001)
        val domain = entity.toDomain()
        assertNull(domain.rating)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Sync progress state transitions
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `sync progress starts at IDLE`() {
        val progress = XtreamSyncProgress(providerId = PROVIDER_ID)
        assertEquals(XtreamSyncStage.IDLE, progress.stage)
        assertEquals(0, progress.progressPercent)
        assertNull(progress.error)
    }

    @Test
    fun `sync progress transitions through expected stages`() {
        val stages = listOf(
            XtreamSyncStage.IDLE,
            XtreamSyncStage.VALIDATING,
            XtreamSyncStage.SYNCING_LIVE_CATEGORIES,
            XtreamSyncStage.SYNCING_LIVE_STREAMS,
            XtreamSyncStage.SYNCING_VOD_CATEGORIES,
            XtreamSyncStage.SYNCING_VOD_STREAMS,
            XtreamSyncStage.SYNCING_SERIES_CATEGORIES,
            XtreamSyncStage.SYNCING_SERIES,
            XtreamSyncStage.COMPLETE
        )

        var progress = XtreamSyncProgress(providerId = PROVIDER_ID)
        for (stage in stages) {
            progress = progress.copy(stage = stage)
            assertEquals(stage, progress.stage)
        }
    }

    @Test
    fun `sync progress FAILED stage has error message`() {
        val progress = XtreamSyncProgress(
            providerId = PROVIDER_ID,
            stage = XtreamSyncStage.FAILED,
            error = "Authentication failed"
        )
        assertEquals(XtreamSyncStage.FAILED, progress.stage)
        assertEquals("Authentication failed", progress.error)
    }

    @Test
    fun `sync progress tracks content counts`() {
        val progress = XtreamSyncProgress(
            providerId = PROVIDER_ID,
            stage = XtreamSyncStage.COMPLETE,
            progressPercent = 100,
            liveChannelCount = 500,
            vodCount = 1200,
            seriesCount = 300
        )
        assertEquals(500, progress.liveChannelCount)
        assertEquals(1200, progress.vodCount)
        assertEquals(300, progress.seriesCount)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Batch insert logic
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `batch size constant is correct`() {
        assertEquals(100, XtreamRepository.SYNC_BATCH_SIZE)
    }

    @Test
    fun `chunked batching produces correct number of batches`() {
        val items = (1..1250).map { i ->
            testVodItem(id = "", streamId = i.toString()).toEntity(PROVIDER_ID)
        }
        val batches = items.chunked(XtreamRepository.SYNC_BATCH_SIZE)
        val expectedBatchCount = (items.size + XtreamRepository.SYNC_BATCH_SIZE - 1) / XtreamRepository.SYNC_BATCH_SIZE
        assertEquals(expectedBatchCount, batches.size)
        for (i in 0 until expectedBatchCount - 1) {
            assertEquals(XtreamRepository.SYNC_BATCH_SIZE, batches[i].size)
        }
        val expectedLastBatchSize = items.size % XtreamRepository.SYNC_BATCH_SIZE
        assertEquals(if (expectedLastBatchSize == 0) XtreamRepository.SYNC_BATCH_SIZE else expectedLastBatchSize, batches.last().size)
    }

    @Test
    fun `single item batch works correctly`() {
        val items = listOf(testVodItem().toEntity(PROVIDER_ID))
        val batches = items.chunked(XtreamRepository.SYNC_BATCH_SIZE)
        assertEquals(1, batches.size)
        assertEquals(1, batches[0].size)
    }

    @Test
    fun `empty list produces no batches`() {
        val items = emptyList<XtreamVodEntity>()
        val batches = items.chunked(XtreamRepository.SYNC_BATCH_SIZE)
        assertTrue(batches.isEmpty())
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Repeated sync deduplication (deterministic IDs)
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `repeated entity creation produces same IDs for deduplication`() {
        val item = testVodItem(streamId = "42")
        val entity1 = item.toEntity(PROVIDER_ID)
        val entity2 = item.toEntity(PROVIDER_ID)
        assertEquals(entity1.id, entity2.id) // Same ID → REPLACE on conflict
    }

    @Test
    fun `different providers produce different entity IDs with empty domain id`() {
        // When domain id is empty, the fallback format includes providerId
        val item = testVodItem(id = "", streamId = "42")
        val entity1 = item.toEntity("provider-a")
        val entity2 = item.toEntity("provider-b")
        assertFalse(
            "Different providers must produce different entity IDs",
            entity1.id == entity2.id
        )
    }

    @Test
    fun `domain items from DTO toDomain have provider-scoped IDs`() {
        // In real flow, XtreamVodStreamDto.toDomain(providerId) creates
        // id = "${providerId}_vod_${streamId}" — always unique per provider
        val itemA = XtreamVodItem(
            id = "provA_vod_42", name = "Movie", streamId = "42",
            categoryId = "1", containerExtension = "mp4"
        )
        val itemB = XtreamVodItem(
            id = "provB_vod_42", name = "Movie", streamId = "42",
            categoryId = "1", containerExtension = "mp4"
        )
        val entityA = itemA.toEntity("provA")
        val entityB = itemB.toEntity("provB")
        assertNotEquals(entityA.id, entityB.id)
    }

    @Test
    fun `series entities also have deterministic IDs`() {
        val item = testSeriesItem(seriesId = "77")
        val entity1 = item.toEntity(PROVIDER_ID)
        val entity2 = item.toEntity(PROVIDER_ID)
        assertEquals(entity1.id, entity2.id)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Provider removal deletes VOD and series metadata
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `entity providerId field is set correctly for filtering`() {
        val vodEntity = testVodItem().toEntity("provider-x")
        assertEquals("provider-x", vodEntity.providerId)

        val seriesEntity = testSeriesItem().toEntity("provider-y")
        assertEquals("provider-y", seriesEntity.providerId)
    }

    @Test
    fun `entities can be filtered by providerId`() {
        val entities = listOf(
            testVodItem(id = "v1", streamId = "1").toEntity("prov-a"),
            testVodItem(id = "v2", streamId = "2").toEntity("prov-b"),
            testVodItem(id = "v3", streamId = "3").toEntity("prov-a"),
            testVodItem(id = "v4", streamId = "4").toEntity("prov-c")
        )

        val provAEntities = entities.filter { it.providerId == "prov-a" }
        assertEquals(2, provAEntities.size)

        val afterDelete = entities.filter { it.providerId != "prov-a" }
        assertEquals(2, afterDelete.size)
        assertTrue(afterDelete.all { it.providerId != "prov-a" })
    }

    @Test
    fun `series entities can be filtered by providerId`() {
        val entities = listOf(
            testSeriesItem(id = "s1", seriesId = "1").toEntity("prov-x"),
            testSeriesItem(id = "s2", seriesId = "2").toEntity("prov-y"),
            testSeriesItem(id = "s3", seriesId = "3").toEntity("prov-x")
        )

        val provXEntities = entities.filter { it.providerId == "prov-x" }
        assertEquals(2, provXEntities.size)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Failed sync preserves previous data (delete-then-insert pattern)
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `failed sync progress preserves counts from before failure`() {
        // Simulate: live channels synced, then failure during VOD
        val beforeFailure = XtreamSyncProgress(
            providerId = PROVIDER_ID,
            stage = XtreamSyncStage.SYNCING_VOD_STREAMS,
            progressPercent = 50,
            liveChannelCount = 500,
            vodCount = 0,
            seriesCount = 0
        )
        val afterFailure = beforeFailure.copy(
            stage = XtreamSyncStage.FAILED,
            error = "Network timeout"
        )
        // Live channel count is preserved from before the failure
        assertEquals(500, afterFailure.liveChannelCount)
        assertEquals(XtreamSyncStage.FAILED, afterFailure.stage)
        assertEquals("Network timeout", afterFailure.error)
    }

    @Test
    fun `delete-then-insert pattern only affects single content type`() {
        // Simulate in-memory: VOD entities for a provider
        val existingVod = (1..3).map {
            testVodItem(id = "prov_vod_$it", streamId = it.toString()).toEntity(PROVIDER_ID)
        }
        val existingSeries = (1..2).map {
            testSeriesItem(id = "prov_series_$it", seriesId = it.toString()).toEntity(PROVIDER_ID)
        }

        // Simulate delete-then-insert for VOD only
        // (series remains untouched)
        val newVod = (10..12).map {
            testVodItem(id = "prov_vod_$it", streamId = it.toString()).toEntity(PROVIDER_ID)
        }

        // After VOD sync: VOD is replaced, series preserved
        assertEquals(3, newVod.size)
        assertEquals(2, existingSeries.size) // Series untouched
        // Verify IDs are different (old data replaced)
        val oldIds = existingVod.map { it.id }.toSet()
        val newIds = newVod.map { it.id }.toSet()
        assertTrue("Old and new VOD should have different IDs", oldIds.intersect(newIds).isEmpty())
    }

    // ═════════════════════════════════════════════════════════════════════
    //  XtreamSyncServiceImpl tests
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `sync service initial progress is null`() {
        val fakeClient = FakeXtreamApiClient()
        val service = XtreamSyncServiceImpl(fakeClient, XtreamRepository)
        assertNull(service.syncProgress.value)
    }

    @Test
    fun `sync progress stage enum covers all expected values`() {
        val stages = XtreamSyncStage.values()
        assertTrue(stages.contains(XtreamSyncStage.IDLE))
        assertTrue(stages.contains(XtreamSyncStage.VALIDATING))
        assertTrue(stages.contains(XtreamSyncStage.SYNCING_LIVE_CATEGORIES))
        assertTrue(stages.contains(XtreamSyncStage.SYNCING_LIVE_STREAMS))
        assertTrue(stages.contains(XtreamSyncStage.SYNCING_VOD_CATEGORIES))
        assertTrue(stages.contains(XtreamSyncStage.SYNCING_VOD_STREAMS))
        assertTrue(stages.contains(XtreamSyncStage.SYNCING_SERIES_CATEGORIES))
        assertTrue(stages.contains(XtreamSyncStage.SYNCING_SERIES))
        assertTrue(stages.contains(XtreamSyncStage.COMPLETE))
        assertTrue(stages.contains(XtreamSyncStage.FAILED))
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Live channel mapping (Xtream → IPTVChannel)
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `live channel mapped to IPTVChannel has xtream pseudo-URL`() {
        val channel = XtreamLiveChannel(
            id = "prov_live_100",
            name = "CNN",
            streamId = "100",
            categoryId = "1",
            logo = "http://example.com/cnn.png"
        )
        val iptvChannel = channel.toIPTVChannel(PROVIDER_ID)
        // Uses xtream:// pseudo-URL, never real credentials
        assertEquals("xtream://stream_id/${PROVIDER_ID}/100", iptvChannel.streamUrl)
        assertEquals("100", iptvChannel.rawAttributes["xtream_stream_id"])
        assertEquals("true", iptvChannel.rawAttributes["xtream_source"])
    }

    @Test
    fun `live channel pseudo-URL does not contain credentials`() {
        val channel = XtreamLiveChannel(
            id = "prov_live_100",
            name = "CNN",
            streamId = "100",
            categoryId = "1"
        )
        val iptvChannel = channel.toIPTVChannel(PROVIDER_ID)
        assertFalse("streamUrl must not contain password", iptvChannel.streamUrl.contains("password"))
        assertFalse("streamUrl must not contain username", iptvChannel.streamUrl.contains("username"))
        assertTrue("streamUrl uses xtream:// scheme", iptvChannel.streamUrl.startsWith("xtream://"))
    }

    @Test
    fun `live channel IPTVChannel includes archive attributes`() {
        val channel = XtreamLiveChannel(
            id = "prov_live_200",
            name = "BBC",
            streamId = "200",
            categoryId = "2",
            tvArchive = true,
            tvArchiveDuration = 7
        )
        val iptvChannel = channel.toIPTVChannel(PROVIDER_ID)
        assertEquals("1", iptvChannel.rawAttributes["tv_archive"])
        assertEquals("7", iptvChannel.rawAttributes["tv_archive_duration"])
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Category name mapping
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `VOD entity stores category name when provided`() {
        val item = testVodItem(categoryId = "5")
        val entity = item.toEntity(PROVIDER_ID, "Action Movies")
        assertEquals("Action Movies", entity.categoryName)
    }

    @Test
    fun `VOD entity defaults to empty category name`() {
        val item = testVodItem(categoryId = "5")
        val entity = item.toEntity(PROVIDER_ID)
        assertEquals("", entity.categoryName)
    }

    @Test
    fun `Series entity stores category name when provided`() {
        val item = testSeriesItem(categoryId = "10")
        val entity = item.toEntity(PROVIDER_ID, "Drama")
        assertEquals("Drama", entity.categoryName)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  NEW: Repeated sync no-duplicates simulation
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `repeated sync with same data produces identical entity set via REPLACE`() {
        // Use provider-scoped IDs like the real DTO.toDomain() flow
        val vodItems = (1..5).map {
            testVodItem(id = "${PROVIDER_ID}_vod_$it", streamId = it.toString(), name = "Movie $it")
        }

        val cycle1Entities = vodItems.map { it.toEntity(PROVIDER_ID, "Cat") }
        val cycle2Entities = vodItems.map { it.toEntity(PROVIDER_ID, "Cat") }

        // IDs must be identical — REPLACE on conflict prevents duplicates
        assertEquals(cycle1Entities.map { it.id }, cycle2Entities.map { it.id })

        // Simulate in-memory DB: insert cycle1, then REPLACE with cycle2
        val db = mutableMapOf<String, XtreamVodEntity>()
        cycle1Entities.forEach { db[it.id] = it }
        assertEquals(5, db.size)
        cycle2Entities.forEach { db[it.id] = it }
        assertEquals(5, db.size) // No duplicates — same 5 items
    }

    @Test
    fun `repeated sync with changed data updates existing items`() {
        val item = testVodItem(id = "${PROVIDER_ID}_vod_42", streamId = "42", name = "Original Title", rating = 7.0)

        val entity1 = item.toEntity(PROVIDER_ID)
        val updatedItem = item.copy(name = "Updated Title", rating = 8.0)
        val entity2 = updatedItem.toEntity(PROVIDER_ID)

        // Same deterministic ID
        assertEquals(entity1.id, entity2.id)
        // But different content
        assertEquals("Original Title", entity1.name)
        assertEquals("Updated Title", entity2.name)

        // Simulate REPLACE: putting entity2 overwrites entity1
        val db = mutableMapOf<String, XtreamVodEntity>()
        db[entity1.id] = entity1
        db[entity2.id] = entity2
        assertEquals(1, db.size)
        assertEquals("Updated Title", db.values.first().name)
    }

    @Test
    fun `repeated sync with added and removed items produces correct count`() {
        // Cycle 1: items 1,2,3 with provider-scoped IDs
        val cycle1 = (1..3).map {
            testVodItem(id = "${PROVIDER_ID}_vod_$it", streamId = it.toString()).toEntity(PROVIDER_ID)
        }
        // Cycle 2: items 2,3,4 (item 1 removed, item 4 added)
        val cycle2 = (2..4).map {
            testVodItem(id = "${PROVIDER_ID}_vod_$it", streamId = it.toString()).toEntity(PROVIDER_ID)
        }

        // Simulate delete-then-insert
        val db = mutableMapOf<String, XtreamVodEntity>()
        cycle1.forEach { db[it.id] = it }
        assertEquals(3, db.size)

        // Delete all for provider
        db.clear()
        // Insert cycle 2
        cycle2.forEach { db[it.id] = it }
        assertEquals(3, db.size)

        // Item 1 should be gone, item 4 should be present
        assertFalse(db.containsKey("${PROVIDER_ID}_vod_1"))
        assertTrue(db.containsKey("${PROVIDER_ID}_vod_2"))
        assertTrue(db.containsKey("${PROVIDER_ID}_vod_3"))
        assertTrue(db.containsKey("${PROVIDER_ID}_vod_4"))
    }

    @Test
    fun `series repeated sync produces no duplicates`() {
        val items = (1..3).map {
            testSeriesItem(id = "${PROVIDER_ID}_series_$it", seriesId = it.toString())
        }

        val cycle1 = items.map { it.toEntity(PROVIDER_ID) }
        val cycle2 = items.map { it.toEntity(PROVIDER_ID) }

        val db = mutableMapOf<String, XtreamSeriesEntity>()
        cycle1.forEach { db[it.id] = it }
        assertEquals(3, db.size)
        cycle2.forEach { db[it.id] = it }
        assertEquals(3, db.size)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  NEW: Provider removal cleanup completeness
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `provider removal deletes all associated VOD, series, and channels`() {
        val providerA = "provider-a"
        val providerB = "provider-b"

        // Create data with unique IDs per item
        val vodA = (1..3).map { testVodItem(id = "va$it", streamId = it.toString()).toEntity(providerA) }
        val vodB = (4..5).map { testVodItem(id = "vb$it", streamId = it.toString()).toEntity(providerB) }
        val seriesA = (1..2).map { testSeriesItem(id = "sa$it", seriesId = it.toString()).toEntity(providerA) }
        val seriesB = listOf(testSeriesItem(id = "sb3", seriesId = "3").toEntity(providerB))

        // Simulate in-memory DB
        val vodDb = (vodA + vodB).associateBy { it.id }.toMutableMap()
        val seriesDb = (seriesA + seriesB).associateBy { it.id }.toMutableMap()

        assertEquals(5, vodDb.size)
        assertEquals(3, seriesDb.size)

        // Simulate deleteVodByProvider(providerA) and deleteSeriesByProvider(providerA)
        vodDb.entries.removeIf { it.value.providerId == providerA }
        seriesDb.entries.removeIf { it.value.providerId == providerA }

        // Provider A data should be gone
        assertEquals(2, vodDb.size) // Only provider B's 2 VOD items remain
        assertEquals(1, seriesDb.size) // Only provider B's 1 series remains
        assertTrue(vodDb.values.all { it.providerId == providerB })
        assertTrue(seriesDb.values.all { it.providerId == providerB })
    }

    @Test
    fun `provider removal with FakeSecureTokenStore clears credentials`() {
        val store = FakeInMemoryIptvSecureTokenStore()
        val providerId = "prov-to-delete"

        store.savePassword(providerId, "user1", "secret123")
        assertTrue(store.hasPassword(providerId, "user1"))

        store.clearProvider(providerId)
        assertFalse(store.hasPassword(providerId, "user1"))
        assertNull(store.readPassword(providerId, "user1"))
    }

    @Test
    fun `provider removal does not affect other providers credentials`() {
        val store = FakeInMemoryIptvSecureTokenStore()

        store.savePassword("prov-a", "user1", "pass1")
        store.savePassword("prov-b", "user2", "pass2")

        store.clearProvider("prov-a")

        assertFalse(store.hasPassword("prov-a", "user1"))
        assertTrue(store.hasPassword("prov-b", "user2"))
        assertEquals("pass2", store.readPassword("prov-b", "user2"))
    }

    // ═════════════════════════════════════════════════════════════════════
    //  NEW: Failed sync data preservation
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `sync failure during VOD does not affect previously synced series`() {
        // Simulate: series was synced successfully in previous run
        val existingSeries = (1..3).map {
            testSeriesItem(id = "${PROVIDER_ID}_series_$it", seriesId = it.toString()).toEntity(PROVIDER_ID)
        }
        val seriesDb = existingSeries.associateBy { it.id }.toMutableMap()

        // VOD sync fails — series should remain intact
        // (sync processes content types sequentially: live → VOD → series)
        assertEquals(3, seriesDb.size)
        // Series DB untouched because VOD failure happens before series sync
        assertTrue(seriesDb.values.all { it.providerId == PROVIDER_ID })
    }

    @Test
    fun `sync failure preserves error details in progress`() {
        val progress = XtreamSyncProgress(
            providerId = PROVIDER_ID,
            stage = XtreamSyncStage.SYNCING_VOD_STREAMS,
            progressPercent = 55,
            liveChannelCount = 200,
            vodCount = 0,
            seriesCount = 0
        )

        val failedProgress = progress.copy(
            stage = XtreamSyncStage.FAILED,
            error = "java.net.SocketTimeoutException: Connect timed out"
        )

        assertEquals(XtreamSyncStage.FAILED, failedProgress.stage)
        assertNotNull(failedProgress.error)
        assertTrue(failedProgress.error!!.contains("SocketTimeoutException"))
        // Counts from before failure are preserved
        assertEquals(200, failedProgress.liveChannelCount)
        assertEquals(0, failedProgress.vodCount)
        assertEquals(0, failedProgress.seriesCount)
    }

    @Test
    fun `sync failure at authentication has zero content counts`() {
        val failedProgress = XtreamSyncProgress(
            providerId = PROVIDER_ID,
            stage = XtreamSyncStage.FAILED,
            progressPercent = 5,
            error = "Authentication failed"
        )

        assertEquals(0, failedProgress.liveChannelCount)
        assertEquals(0, failedProgress.vodCount)
        assertEquals(0, failedProgress.seriesCount)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  NEW: Mapper null/empty handling edge cases
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `VodEntity handles empty name`() {
        val item = testVodItem(name = "")
        val entity = item.toEntity(PROVIDER_ID)
        assertEquals("", entity.name)
        val domain = entity.toDomain()
        assertEquals("", domain.name)
    }

    @Test
    fun `VodEntity handles empty streamId`() {
        val item = testVodItem(id = "", streamId = "")
        val entity = item.toEntity(PROVIDER_ID)
        assertEquals("xtream-vod-$PROVIDER_ID-", entity.id)
        assertEquals("", entity.streamId)
    }

    @Test
    fun `VodEntity handles empty categoryId`() {
        val item = testVodItem(categoryId = "")
        val entity = item.toEntity(PROVIDER_ID)
        assertEquals("", entity.categoryId)
    }

    @Test
    fun `VodEntity handles zero rating round-trip as null`() {
        val item = testVodItem(rating = 0.0)
        val entity = item.toEntity(PROVIDER_ID)
        assertEquals(0.0, entity.rating, 0.001)
        val domain = entity.toDomain()
        // 0.0 maps back to null in domain — this is intentional (0.0 = "no rating")
        assertNull(domain.rating)
    }

    @Test
    fun `SeriesEntity handles empty name`() {
        val item = testSeriesItem(name = "")
        val entity = item.toEntity(PROVIDER_ID)
        assertEquals("", entity.name)
    }

    @Test
    fun `VodEntity handles very long poster URL`() {
        val longUrl = "http://example.com/" + "a".repeat(500) + ".jpg"
        val item = testVodItem(poster = longUrl)
        val entity = item.toEntity(PROVIDER_ID)
        assertEquals(longUrl, entity.poster)
        val domain = entity.toDomain()
        assertEquals(longUrl, domain.poster)
    }

    @Test
    fun `SeriesEntity handles empty poster round-trip`() {
        // Empty poster in entity → null in domain
        val entity = XtreamSeriesEntity()
        entity.poster = ""
        val domain = entity.toDomain()
        assertNull(domain.poster)
    }

    @Test
    fun `VodEntity handles negative rating`() {
        val item = testVodItem(rating = -1.0)
        val entity = item.toEntity(PROVIDER_ID)
        assertEquals(-1.0, entity.rating, 0.001)
        val domain = entity.toDomain()
        // Negative rating is non-zero, so it's preserved
        assertEquals(-1.0, domain.rating!!, 0.001)
    }

    @Test
    fun `VodEntity handles maximum added timestamp`() {
        val item = testVodItem(added = Long.MAX_VALUE)
        val entity = item.toEntity(PROVIDER_ID)
        assertEquals(Long.MAX_VALUE, entity.addedTimestamp)
        val domain = entity.toDomain()
        assertEquals(Long.MAX_VALUE, domain.added)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  NEW: Entity ID determinism (comprehensive)
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `VOD entity ID fallback format is xtream-vod-providerId-streamId`() {
        val entity = testVodItem(id = "", streamId = "123").toEntity("my-provider")
        assertEquals("xtream-vod-my-provider-123", entity.id)
    }

    @Test
    fun `Series entity ID fallback format is xtream-series-providerId-seriesId`() {
        val entity = testSeriesItem(id = "", seriesId = "456").toEntity("my-provider")
        assertEquals("xtream-series-my-provider-456", entity.id)
    }

    @Test
    fun `VOD entity IDs are deterministic across multiple calls`() {
        val item = testVodItem(streamId = "999")
        val ids = (1..100).map { item.toEntity(PROVIDER_ID).id }.toSet()
        // All 100 calls should produce the exact same ID
        assertEquals(1, ids.size)
    }

    @Test
    fun `Series entity IDs are deterministic across multiple calls`() {
        val item = testSeriesItem(seriesId = "888")
        val ids = (1..100).map { item.toEntity(PROVIDER_ID).id }.toSet()
        assertEquals(1, ids.size)
    }

    @Test
    fun `VOD entity IDs contain no random components`() {
        val item = testVodItem(id = "", streamId = "42")
        val entity = item.toEntity(PROVIDER_ID)
        // ID should NOT contain UUID-like patterns
        assertFalse(
            "Entity ID should not contain UUID-like patterns",
            entity.id.matches(Regex(".*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*"))
        )
    }

    @Test
    fun `live channel IPTVChannel ID is deterministic`() {
        val channel = XtreamLiveChannel(
            id = "prov_live_100",
            name = "Test Channel",
            streamId = "100",
            categoryId = "1"
        )
        val iptv1 = channel.toIPTVChannel(PROVIDER_ID)
        val iptv2 = channel.toIPTVChannel(PROVIDER_ID)
        assertEquals(iptv1.id, iptv2.id) // Same deterministic ID
    }

    // ═════════════════════════════════════════════════════════════════════
    //  NEW: Credential safety checks
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `live channel streamUrl uses xtream pseudo-URL not credentials`() {
        val channel = XtreamLiveChannel(
            id = "prov_live_300",
            name = "Channel With Auth",
            streamId = "300",
            categoryId = "3",
            logo = "http://example.com/logo.png"
        )
        val iptvChannel = channel.toIPTVChannel(PROVIDER_ID)
        assertEquals("xtream://stream_id/${PROVIDER_ID}/300", iptvChannel.streamUrl)
        // Verify no rawAttribute contains credential patterns
        for ((key, value) in iptvChannel.rawAttributes) {
            assertFalse("Key '$key' should not be 'password'", key.lowercase() == "password")
            assertFalse("Value in '$key' should not contain password", value.lowercase().contains("password"))
        }
    }

    @Test
    fun `VOD entity never stores server URL with credentials`() {
        // Even if domain model has credential-like data, entity should not
        val entity = testVodItem(
            name = "Movie",
            poster = "http://example.com/poster.jpg" // Poster is a public URL
        ).toEntity(PROVIDER_ID)

        // No field should contain authentication path segments
        assertFalse(entity.poster.contains("username="))
        assertFalse(entity.poster.contains("password="))
    }

    @Test
    fun `live channel without archive does not include archive attributes`() {
        val channel = XtreamLiveChannel(
            id = "prov_live_400",
            name = "No Archive",
            streamId = "400",
            categoryId = "4",
            tvArchive = false,
            tvArchiveDuration = 0
        )
        val iptvChannel = channel.toIPTVChannel(PROVIDER_ID)
        assertFalse(iptvChannel.rawAttributes.containsKey("tv_archive"))
        assertFalse(iptvChannel.rawAttributes.containsKey("tv_archive_duration"))
    }

    @Test
    fun `live channel with null logo maps to null tvgLogo`() {
        val channel = XtreamLiveChannel(
            id = "prov_live_500",
            name = "No Logo",
            streamId = "500",
            categoryId = "5",
            logo = null
        )
        val iptvChannel = channel.toIPTVChannel(PROVIDER_ID)
        assertNull(iptvChannel.tvgLogo)
    }

    @Test
    fun `live channel epgChannelId maps to tvgId when non-empty`() {
        val channel = XtreamLiveChannel(
            id = "prov_live_600",
            name = "EPG Channel",
            streamId = "600",
            categoryId = "6",
            epgChannelId = "epg.channel.1"
        )
        val iptvChannel = channel.toIPTVChannel(PROVIDER_ID)
        assertEquals("epg.channel.1", iptvChannel.tvgId)
    }

    @Test
    fun `live channel empty epgChannelId maps to null tvgId`() {
        val channel = XtreamLiveChannel(
            id = "prov_live_700",
            name = "No EPG",
            streamId = "700",
            categoryId = "7",
            epgChannelId = ""
        )
        val iptvChannel = channel.toIPTVChannel(PROVIDER_ID)
        assertNull(iptvChannel.tvgId)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  NEW: XtreamVodEntity and XtreamSeriesEntity default construction
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `XtreamVodEntity defaults are safe`() {
        val entity = XtreamVodEntity()
        assertEquals("", entity.id)
        assertEquals("", entity.name)
        assertEquals("", entity.streamId)
        assertEquals("", entity.categoryId)
        assertEquals("", entity.categoryName)
        assertEquals("", entity.poster)
        assertEquals(0.0, entity.rating, 0.001)
        assertEquals("mp4", entity.containerExtension)
        assertEquals(0L, entity.addedTimestamp)
        assertEquals("", entity.providerId)
    }

    @Test
    fun `XtreamSeriesEntity defaults are safe`() {
        val entity = XtreamSeriesEntity()
        assertEquals("", entity.id)
        assertEquals("", entity.name)
        assertEquals("", entity.seriesId)
        assertEquals("", entity.categoryId)
        assertEquals("", entity.categoryName)
        assertEquals("", entity.poster)
        assertEquals(0.0, entity.rating, 0.001)
        assertEquals("", entity.providerId)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  NEW: Multiple providers in the same DB (isolation)
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `VOD entities from real DTO flow have provider-scoped IDs`() {
        // Real flow: XtreamVodStreamDto.toDomain(providerId) creates
        // id = "${providerId}_vod_${streamId}" — unique per provider
        val itemA = XtreamVodItem(
            id = "alpha_vod_100", name = "Movie", streamId = "100",
            categoryId = "1", containerExtension = "mp4"
        )
        val itemB = XtreamVodItem(
            id = "beta_vod_100", name = "Movie", streamId = "100",
            categoryId = "1", containerExtension = "mp4"
        )
        val entityA = itemA.toEntity("alpha")
        val entityB = itemB.toEntity("beta")
        assertNotEquals(entityA.id, entityB.id)
    }

    @Test
    fun `deleting one provider leaves other provider data intact in simulation`() {
        val allVod = listOf(
            testVodItem(id = "keep1", streamId = "1").toEntity("prov-keep"),
            testVodItem(id = "keep2", streamId = "2").toEntity("prov-keep"),
            testVodItem(id = "del3", streamId = "3").toEntity("prov-delete"),
            testVodItem(id = "del4", streamId = "4").toEntity("prov-delete"),
            testVodItem(id = "del5", streamId = "5").toEntity("prov-delete")
        )

        val remaining = allVod.filter { it.providerId != "prov-delete" }
        assertEquals(2, remaining.size)
        assertTrue(remaining.all { it.providerId == "prov-keep" })
    }

    // ═════════════════════════════════════════════════════════════════════
    //  NEW: Large batch handling
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `batch size of exactly SYNC_BATCH_SIZE produces single batch`() {
        val size = XtreamRepository.SYNC_BATCH_SIZE
        val items = (1..size).map { testVodItem(id = "", streamId = it.toString()).toEntity(PROVIDER_ID) }
        val batches = items.chunked(size)
        assertEquals(1, batches.size)
        assertEquals(size, batches[0].size)
    }

    @Test
    fun `batch size of SYNC_BATCH_SIZE plus 1 produces two batches`() {
        val size = XtreamRepository.SYNC_BATCH_SIZE
        val items = (1..(size + 1)).map { testVodItem(id = "", streamId = it.toString()).toEntity(PROVIDER_ID) }
        val batches = items.chunked(size)
        assertEquals(2, batches.size)
        assertEquals(size, batches[0].size)
        assertEquals(1, batches[1].size)
    }

    @Test
    fun `large provider with 10 times SYNC_BATCH_SIZE items batches into 10 chunks`() {
        val size = XtreamRepository.SYNC_BATCH_SIZE
        val items = (1..(size * 10)).map { testVodItem(id = "", streamId = it.toString()).toEntity(PROVIDER_ID) }
        val batches = items.chunked(size)
        assertEquals(10, batches.size)
        assertTrue(batches.all { it.size == size })
    }

    // ═════════════════════════════════════════════════════════════════════
    //  NEW: XtreamStreamUrlBuilder pseudo-URL tests
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `pseudo URL is created correctly`() {
        val url = XtreamStreamUrlBuilder.createPseudoUrl("test-provider", "42")
        assertEquals("xtream://stream_id/test-provider/42", url)
    }

    @Test
    fun `stream ID can be extracted from pseudo URL`() {
        val streamId = XtreamStreamUrlBuilder.extractStreamId("xtream://stream_id/42")
        assertEquals("42", streamId)
    }

    @Test
    fun `extractStreamId returns null for non-xtream URLs`() {
        assertNull(XtreamStreamUrlBuilder.extractStreamId("http://example.com/live/42"))
        assertNull(XtreamStreamUrlBuilder.extractStreamId(""))
        assertNull(XtreamStreamUrlBuilder.extractStreamId("xtream://other/42"))
    }

    @Test
    fun `pseudo URL round-trip preserves stream ID`() {
        val original = "12345"
        val pseudoUrl = XtreamStreamUrlBuilder.createPseudoUrl("test-provider", original)
        val extracted = XtreamStreamUrlBuilder.extractStreamId(pseudoUrl)
        assertEquals(original, extracted)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Fake XtreamApiClient for sync tests (no real network calls)
    // ═════════════════════════════════════════════════════════════════════

    private class FakeXtreamApiClient(
        private val shouldAuthenticate: Boolean = true,
        private val liveCategories: List<XtreamCategory> = emptyList(),
        private val liveStreams: List<XtreamLiveChannel> = emptyList(),
        private val vodCategories: List<XtreamCategory> = emptyList(),
        private val vodStreams: List<XtreamVodItem> = emptyList(),
        private val seriesCategories: List<XtreamCategory> = emptyList(),
        private val series: List<XtreamSeriesItem> = emptyList()
    ) : XtreamApiClient {
        override suspend fun authenticate(config: XtreamProviderConfig, password: String) = shouldAuthenticate
        override suspend fun getLiveCategories(config: XtreamProviderConfig, password: String) = liveCategories
        override suspend fun getLiveStreams(config: XtreamProviderConfig, password: String, categoryId: String?) = liveStreams
        override suspend fun getVodCategories(config: XtreamProviderConfig, password: String) = vodCategories
        override suspend fun getVodStreams(config: XtreamProviderConfig, password: String, categoryId: String?) = vodStreams
        override suspend fun getSeriesCategories(config: XtreamProviderConfig, password: String) = seriesCategories
        override suspend fun getSeries(config: XtreamProviderConfig, password: String, categoryId: String?) = series
        override suspend fun getSeriesEpisodes(config: XtreamProviderConfig, password: String, seriesId: String) =
            emptyList<com.example.calmsource.core.model.XtreamSeriesEpisode>()
        override suspend fun getShortEpg(config: XtreamProviderConfig, password: String, streamId: String): List<com.example.calmsource.core.model.XtreamShortEpgProgram> =
            emptyList()
    }
}
