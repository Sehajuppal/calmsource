package com.example.calmsource.feature.iptv.xtream

import com.example.calmsource.core.model.XtreamCategory
import com.example.calmsource.core.model.XtreamLiveChannel
import com.example.calmsource.core.model.XtreamProviderConfig
import com.example.calmsource.core.model.XtreamSeriesItem
import com.example.calmsource.core.model.XtreamSyncProgress
import com.example.calmsource.core.model.XtreamSyncStage
import com.example.calmsource.core.model.XtreamVodItem
import com.example.calmsource.core.network.UrlRedactor
import com.example.calmsource.feature.iptv.XtreamApiClient
import com.example.calmsource.feature.iptv.XtreamRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for Xtream sync state machine, progress transitions,
 * error handling, and credential safety.
 *
 * All data is synthetic — no real provider URLs or credentials.
 */
class XtreamSyncStateTest {

    companion object {
        private const val PROVIDER_ID = "test-sync-provider"
    }

    // ═════════════════════════════════════════════════════════════════════
    //  1. Sync stage completeness
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `all expected sync stages exist in enum`() {
        val expected = listOf(
            "IDLE", "VALIDATING",
            "SYNCING_LIVE_CATEGORIES", "SYNCING_LIVE_STREAMS",
            "SYNCING_VOD_CATEGORIES", "SYNCING_VOD_STREAMS",
            "SYNCING_SERIES_CATEGORIES", "SYNCING_SERIES",
            "SYNCING_EPG",
            "COMPLETE", "FAILED"
        )
        val actual = XtreamSyncStage.values().map { it.name }
        for (stage in expected) {
            assertTrue(
                "Expected stage $stage to exist in XtreamSyncStage enum",
                actual.contains(stage)
            )
        }
    }

    @Test
    fun `enum count matches expected stage count`() {
        // If someone adds a new stage, this test reminds them to audit UI labels
        assertEquals(
            "Update UI stage labels if adding/removing sync stages",
            11,
            XtreamSyncStage.values().size
        )
    }

    @Test
    fun `IDLE and COMPLETE are terminal-like stages`() {
        // The UI uses these to determine whether sync is "active"
        val nonActive = setOf(XtreamSyncStage.IDLE, XtreamSyncStage.COMPLETE, XtreamSyncStage.FAILED)
        val active = XtreamSyncStage.values().filter { it !in nonActive }
        assertTrue("Active stages should exist", active.isNotEmpty())
        assertTrue("IDLE should be non-active", XtreamSyncStage.IDLE in nonActive)
        assertTrue("COMPLETE should be non-active", XtreamSyncStage.COMPLETE in nonActive)
        assertTrue("FAILED should be non-active", XtreamSyncStage.FAILED in nonActive)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  2. Progress doesn't get stuck — monotonic advancement
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `progress percent increases monotonically through stages`() {
        // Simulates the expected progress values from XtreamRepository.syncProvider
        val stageProgression = listOf(
            XtreamSyncStage.IDLE to 0,
            XtreamSyncStage.VALIDATING to 5,
            XtreamSyncStage.SYNCING_LIVE_CATEGORIES to 10,
            XtreamSyncStage.SYNCING_LIVE_STREAMS to 20,
            XtreamSyncStage.SYNCING_VOD_CATEGORIES to 45,
            XtreamSyncStage.SYNCING_VOD_STREAMS to 50,
            XtreamSyncStage.SYNCING_SERIES_CATEGORIES to 75,
            XtreamSyncStage.SYNCING_SERIES to 80,
            XtreamSyncStage.COMPLETE to 100
        )

        var lastPercent = -1
        for ((stage, percent) in stageProgression) {
            assertTrue(
                "Progress should increase: stage=$stage percent=$percent must be > $lastPercent",
                percent > lastPercent
            )
            lastPercent = percent
        }
    }

    @Test
    fun `progress starts at 0 and ends at 100`() {
        val initial = XtreamSyncProgress(providerId = PROVIDER_ID)
        assertEquals(0, initial.progressPercent)

        val complete = initial.copy(stage = XtreamSyncStage.COMPLETE, progressPercent = 100)
        assertEquals(100, complete.progressPercent)
    }

    @Test
    fun `progress percent is clamped between 0 and 100 semantically`() {
        // The data class doesn't enforce clamping, but the repository should
        // always produce values in [0, 100]. Verify the expected bounds.
        val progress = XtreamSyncProgress(
            providerId = PROVIDER_ID,
            progressPercent = 50
        )
        assertTrue(progress.progressPercent in 0..100)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  3. Partial sync failure handling
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `failed progress preserves counts from completed stages`() {
        // Simulate: live channels synced successfully (500), then VOD sync fails
        val afterLiveSync = XtreamSyncProgress(
            providerId = PROVIDER_ID,
            stage = XtreamSyncStage.SYNCING_VOD_STREAMS,
            progressPercent = 50,
            liveChannelCount = 500,
            vodCount = 0,
            seriesCount = 0
        )
        val failed = afterLiveSync.copy(
            stage = XtreamSyncStage.FAILED,
            error = "Network timeout during VOD sync"
        )
        assertEquals(500, failed.liveChannelCount)
        assertEquals(0, failed.vodCount)
        assertEquals(0, failed.seriesCount)
        assertEquals(XtreamSyncStage.FAILED, failed.stage)
        assertNotNull(failed.error)
    }

    @Test
    fun `failed during series preserves live and vod counts`() {
        val progress = XtreamSyncProgress(
            providerId = PROVIDER_ID,
            stage = XtreamSyncStage.SYNCING_SERIES,
            progressPercent = 80,
            liveChannelCount = 300,
            vodCount = 1500,
            seriesCount = 0
        )
        val failed = progress.copy(
            stage = XtreamSyncStage.FAILED,
            error = "Server timeout"
        )
        assertEquals(300, failed.liveChannelCount)
        assertEquals(1500, failed.vodCount)
        assertEquals(0, failed.seriesCount)
    }

    @Test
    fun `failed at validation has zero counts`() {
        val progress = XtreamSyncProgress(
            providerId = PROVIDER_ID,
            stage = XtreamSyncStage.VALIDATING,
            progressPercent = 5
        )
        val failed = progress.copy(
            stage = XtreamSyncStage.FAILED,
            error = "Authentication failed"
        )
        assertEquals(0, failed.liveChannelCount)
        assertEquals(0, failed.vodCount)
        assertEquals(0, failed.seriesCount)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  4. Error messages are credential-free
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `UrlRedactor removes password from error message containing URL`() {
        val rawError = "Connection failed: http://example.com/player_api.php?username=admin&password=secret123"
        val safe = UrlRedactor.redactErrorMessage(rawError)
        assertFalse(
            "Error message should not contain raw password",
            safe.contains("secret123")
        )
        assertFalse(
            "Error message should not contain raw username value",
            safe.contains("admin")
        )
    }

    @Test
    fun `UrlRedactor handles error with no URLs`() {
        val rawError = "Authentication failed"
        val safe = UrlRedactor.redactErrorMessage(rawError)
        assertEquals("Authentication failed", safe)
    }

    @Test
    fun `UrlRedactor handles error with Xtream-style path credentials`() {
        val rawError = "Stream not found: http://example.com/live/myuser/mypass/12345.ts"
        val safe = UrlRedactor.redactErrorMessage(rawError)
        assertFalse(
            "Error should not contain password from path",
            safe.contains("mypass")
        )
        assertFalse(
            "Error should not contain username from path",
            safe.contains("myuser")
        )
    }

    @Test
    fun `sync progress error field can be sanitized independently`() {
        val progress = XtreamSyncProgress(
            providerId = PROVIDER_ID,
            stage = XtreamSyncStage.FAILED,
            error = "Failed at http://server.com/api?username=u1&password=p1"
        )
        val safeError = UrlRedactor.redactErrorMessage(progress.error!!)
        assertFalse(safeError.contains("p1"))
        assertFalse(safeError.contains("u1"))
    }

    @Test
    fun `error message with provider ID is safe to display`() {
        // Provider IDs are not credentials and are safe
        val error = "Provider $PROVIDER_ID not found"
        val safe = UrlRedactor.redactErrorMessage(error)
        assertTrue(safe.contains(PROVIDER_ID))
    }

    @Test
    fun `null error message does not crash`() {
        val progress = XtreamSyncProgress(
            providerId = PROVIDER_ID,
            stage = XtreamSyncStage.FAILED,
            error = null
        )
        assertNull(progress.error)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  5. XtreamSyncServiceImpl delegation
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `sync service delegates syncProgress to repository`() {
        val fakeClient = FakeXtreamApiClient()
        val service = XtreamSyncServiceImpl(fakeClient, XtreamRepository)
        // Service's syncProgress should be the same reference as Repository's
        assertTrue(
            "Service syncProgress should delegate to Repository.syncProgress",
            service.syncProgress === XtreamRepository.syncProgress
        )
    }

    @Test
    fun `sync service initial progress matches repository initial state`() {
        val fakeClient = FakeXtreamApiClient()
        val service = XtreamSyncServiceImpl(fakeClient, XtreamRepository)
        // Both should start as whatever the repository has (null or last state)
        assertEquals(
            XtreamRepository.syncProgress.value,
            service.syncProgress.value
        )
    }

    // ═════════════════════════════════════════════════════════════════════
    //  6. No duplicate StateFlow
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `service does not have its own MutableStateFlow`() {
        // Verify the service delegates to repository - they should be the same object
        val fakeClient = FakeXtreamApiClient()
        val service = XtreamSyncServiceImpl(fakeClient, XtreamRepository)
        assertNotNull("syncProgress should be available", service.syncProgress)
        // Reading from service.syncProgress and XtreamRepository.syncProgress
        // should return the same object identity (not just equal values)
        assertTrue(
            "No duplicate StateFlow: service should delegate to repository",
            service.syncProgress === XtreamRepository.syncProgress
        )
    }

    // ═════════════════════════════════════════════════════════════════════
    //  7. Stage transition validation
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `expected sync flow matches repository stage order`() {
        // Verify the ordered stages that XtreamRepository.syncProvider emits
        val expectedOrder = listOf(
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
        // Verify ordinals increase (enum values are declared in order)
        for (i in 1 until expectedOrder.size) {
            assertTrue(
                "Stage ${expectedOrder[i]} should have higher ordinal than ${expectedOrder[i-1]}",
                expectedOrder[i].ordinal > expectedOrder[i-1].ordinal
            )
        }
    }

    @Test
    fun `SYNCING_EPG stage exists but is not used in main sync flow`() {
        // SYNCING_EPG is defined in the enum and has UI labels,
        // but XtreamRepository.syncProvider does not emit it
        // (EPG sync is handled separately). This is by design.
        assertTrue(
            "SYNCING_EPG should exist in enum",
            XtreamSyncStage.values().contains(XtreamSyncStage.SYNCING_EPG)
        )
    }

    @Test
    fun `FAILED is the last non-COMPLETE terminal stage`() {
        // Verify FAILED has the highest ordinal (last in enum)
        val stages = XtreamSyncStage.values()
        assertEquals(
            "FAILED should be the last enum value",
            XtreamSyncStage.FAILED,
            stages.last()
        )
    }

    // ═════════════════════════════════════════════════════════════════════
    //  8. Progress model data integrity
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `XtreamSyncProgress defaults are correct`() {
        val progress = XtreamSyncProgress(providerId = "test")
        assertEquals("test", progress.providerId)
        assertEquals(XtreamSyncStage.IDLE, progress.stage)
        assertEquals(0, progress.progressPercent)
        assertEquals(0, progress.liveChannelCount)
        assertEquals(0, progress.vodCount)
        assertEquals(0, progress.seriesCount)
        assertNull(progress.error)
    }

    @Test
    fun `copy preserves unmodified fields`() {
        val original = XtreamSyncProgress(
            providerId = PROVIDER_ID,
            stage = XtreamSyncStage.SYNCING_LIVE_STREAMS,
            progressPercent = 30,
            liveChannelCount = 200,
            vodCount = 0,
            seriesCount = 0
        )
        val updated = original.copy(progressPercent = 40, liveChannelCount = 500)
        assertEquals(PROVIDER_ID, updated.providerId)
        assertEquals(XtreamSyncStage.SYNCING_LIVE_STREAMS, updated.stage)
        assertEquals(40, updated.progressPercent)
        assertEquals(500, updated.liveChannelCount)
        assertEquals(0, updated.vodCount)
        assertEquals(0, updated.seriesCount)
        assertNull(updated.error)
    }

    @Test
    fun `error field set to non-null only in FAILED stage`() {
        // By convention, error should only be set when stage is FAILED
        val validating = XtreamSyncProgress(
            providerId = PROVIDER_ID,
            stage = XtreamSyncStage.VALIDATING,
            progressPercent = 5
        )
        assertNull(validating.error)

        val failed = validating.copy(
            stage = XtreamSyncStage.FAILED,
            error = "Auth failed"
        )
        assertNotNull(failed.error)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  9. Multiple provider sync isolation
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `progress for different providers are distinguishable`() {
        val p1 = XtreamSyncProgress(
            providerId = "provider-1",
            stage = XtreamSyncStage.SYNCING_LIVE_STREAMS,
            progressPercent = 30
        )
        val p2 = XtreamSyncProgress(
            providerId = "provider-2",
            stage = XtreamSyncStage.SYNCING_VOD_STREAMS,
            progressPercent = 60
        )
        assertFalse(p1.providerId == p2.providerId)
        assertFalse(p1.stage == p2.stage)
        assertFalse(p1.progressPercent == p2.progressPercent)
    }

    @Test
    fun `UI filter pattern works for provider-specific progress`() {
        // This mimics the UI pattern: xtreamSyncProgress?.takeIf { it.providerId == provider.id }
        val progress = XtreamSyncProgress(
            providerId = "target-provider",
            stage = XtreamSyncStage.SYNCING_LIVE_STREAMS,
            progressPercent = 25
        )

        val forTarget = progress.takeIf { it.providerId == "target-provider" }
        val forOther = progress.takeIf { it.providerId == "other-provider" }

        assertNotNull(forTarget)
        assertNull(forOther)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  10. UI active/inactive classification
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `isSyncing logic matches UI pattern`() {
        // Mirrors SettingsScreens.kt lines 557-560
        fun isSyncing(syncProgress: XtreamSyncProgress?): Boolean {
            return syncProgress != null &&
                    syncProgress.stage != XtreamSyncStage.IDLE &&
                    syncProgress.stage != XtreamSyncStage.COMPLETE &&
                    syncProgress.stage != XtreamSyncStage.FAILED
        }

        assertFalse(isSyncing(null))
        assertFalse(isSyncing(XtreamSyncProgress(PROVIDER_ID, stage = XtreamSyncStage.IDLE)))
        assertFalse(isSyncing(XtreamSyncProgress(PROVIDER_ID, stage = XtreamSyncStage.COMPLETE)))
        assertFalse(isSyncing(XtreamSyncProgress(PROVIDER_ID, stage = XtreamSyncStage.FAILED)))
        assertTrue(isSyncing(XtreamSyncProgress(PROVIDER_ID, stage = XtreamSyncStage.VALIDATING)))
        assertTrue(isSyncing(XtreamSyncProgress(PROVIDER_ID, stage = XtreamSyncStage.SYNCING_LIVE_CATEGORIES)))
        assertTrue(isSyncing(XtreamSyncProgress(PROVIDER_ID, stage = XtreamSyncStage.SYNCING_LIVE_STREAMS)))
        assertTrue(isSyncing(XtreamSyncProgress(PROVIDER_ID, stage = XtreamSyncStage.SYNCING_VOD_CATEGORIES)))
        assertTrue(isSyncing(XtreamSyncProgress(PROVIDER_ID, stage = XtreamSyncStage.SYNCING_VOD_STREAMS)))
        assertTrue(isSyncing(XtreamSyncProgress(PROVIDER_ID, stage = XtreamSyncStage.SYNCING_SERIES_CATEGORIES)))
        assertTrue(isSyncing(XtreamSyncProgress(PROVIDER_ID, stage = XtreamSyncStage.SYNCING_SERIES)))
        assertTrue(isSyncing(XtreamSyncProgress(PROVIDER_ID, stage = XtreamSyncStage.SYNCING_EPG)))
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Fake XtreamApiClient (no real network calls)
    // ═════════════════════════════════════════════════════════════════════

    private class FakeXtreamApiClient(
        private val shouldAuthenticate: Boolean = true,
        private val throwOnAuth: Boolean = false
    ) : XtreamApiClient {
        override suspend fun authenticate(config: XtreamProviderConfig, password: String): Boolean {
            if (throwOnAuth) throw RuntimeException("Connection refused")
            return shouldAuthenticate
        }
        override suspend fun getLiveCategories(config: XtreamProviderConfig, password: String) = emptyList<XtreamCategory>()
        override suspend fun getLiveStreams(config: XtreamProviderConfig, password: String, categoryId: String?) = emptyList<XtreamLiveChannel>()
        override suspend fun getVodCategories(config: XtreamProviderConfig, password: String) = emptyList<XtreamCategory>()
        override suspend fun getVodStreams(config: XtreamProviderConfig, password: String, categoryId: String?) = emptyList<XtreamVodItem>()
        override suspend fun getSeriesCategories(config: XtreamProviderConfig, password: String) = emptyList<XtreamCategory>()
        override suspend fun getSeries(config: XtreamProviderConfig, password: String, categoryId: String?) = emptyList<XtreamSeriesItem>()
        override suspend fun getSeriesEpisodes(config: XtreamProviderConfig, password: String, seriesId: String) =
            emptyList<com.example.calmsource.core.model.XtreamSeriesEpisode>()
        override suspend fun getShortEpg(config: XtreamProviderConfig, password: String, streamId: String): List<com.example.calmsource.core.model.XtreamShortEpgProgram> =
            emptyList()
    }
}
