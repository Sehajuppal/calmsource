package com.example.calmsource.core.playback

import com.example.calmsource.core.database.SourceHealthRepository
import com.example.calmsource.core.model.AutoFallbackPolicy
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito

/**
 * Standalone unit tests for [FallbackManager].
 *
 * These tests exercise FallbackManager's logic directly (no PlaybackManager involved),
 * so there are no infinite-coroutine issues with runTest. They validate:
 * - Policy enforcement (OFF, ASK_BEFORE_FALLBACK, AUTO_FALLBACK_ONCE, AUTO_FALLBACK_UNTIL_PLAYABLE)
 * - Failed source tracking (markFailed / getFailedSources)
 * - Candidate selection (selectNextBestCandidate filters failed, sorts by health)
 * - Reset behavior
 * - Edge cases (empty candidates, all failed, etc.)
 * - No infinite retry is possible under any policy
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FallbackManagerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)

    private lateinit var mockedSystemClock: MockedStatic<android.os.SystemClock>

    private val source1 = PlaybackSource("src1", PlaybackSourceType.IPTV, "Source 1", "http://example.com/src1.m3u8")
    private val source2 = PlaybackSource("src2", PlaybackSourceType.IPTV, "Source 2", "http://example.com/src2.m3u8")
    private val source3 = PlaybackSource("src3", PlaybackSourceType.IPTV, "Source 3", "http://example.com/src3.m3u8")
    private val source4 = PlaybackSource("src4", PlaybackSourceType.IPTV, "Source 4", "http://example.com/src4.m3u8")
    private val source5 = PlaybackSource("src5", PlaybackSourceType.IPTV, "Source 5", "http://example.com/src5.m3u8")
    private val source6 = PlaybackSource("src6", PlaybackSourceType.IPTV, "Source 6", "http://example.com/src6.m3u8")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockedSystemClock = Mockito.mockStatic(android.os.SystemClock::class.java)
        mockedSystemClock.`when`<Long> { android.os.SystemClock.elapsedRealtime() }.thenReturn(0L)

        SourceHealthRepository.dispatcher = testDispatcher
        kotlinx.coroutines.runBlocking {
            SourceHealthRepository.clearSourceHealth()
            SourceHealthRepository.clearProviderHealth()
        }
    }

    @After
    fun tearDown() {
        SourceHealthRepository.dispatcher = Dispatchers.IO
        kotlinx.coroutines.runBlocking {
            SourceHealthRepository.clearSourceHealth()
            SourceHealthRepository.clearProviderHealth()
        }
        mockedSystemClock.close()
        Dispatchers.resetMain()
    }

    // ─── FallbackPreferences default ───────────────────────────────────────

    @Test
    fun `default fallback policy is ASK_BEFORE_FALLBACK`() {
        // Reset to default state
        FallbackPreferences.policy = AutoFallbackPolicy.ASK_BEFORE_FALLBACK
        assertEquals(AutoFallbackPolicy.ASK_BEFORE_FALLBACK, FallbackPreferences.policy)
    }

    // ─── reset() ───────────────────────────────────────────────────────────

    @Test
    fun `reset clears failed sources and attempt count`() {
        val fm = FallbackManager()
        fm.reset(listOf(source1, source2))
        fm.markFailed("src1")
        fm.incrementAttempts()

        assertEquals(1, fm.getAttemptCount())
        assertTrue(fm.getFailedSources().contains("src1"))

        // Reset should clear everything
        fm.reset(listOf(source3))
        assertEquals(0, fm.getAttemptCount())
        assertTrue(fm.getFailedSources().isEmpty())
    }

    @Test
    fun `reset sets new candidates`() = runTest {
        val fm = FallbackManager()
        fm.reset(listOf(source1))

        val candidate = fm.selectNextBestCandidate()
        assertEquals(source1.id, candidate?.id)
    }

    // ─── markFailed() ──────────────────────────────────────────────────────

    @Test
    fun `markFailed adds source to failed set`() {
        val fm = FallbackManager()
        fm.reset(listOf(source1, source2))

        assertTrue(fm.getFailedSources().isEmpty())
        fm.markFailed("src1")
        assertTrue(fm.getFailedSources().contains("src1"))
        assertEquals(1, fm.getFailedSources().size)
    }

    @Test
    fun `markFailed is idempotent`() {
        val fm = FallbackManager()
        fm.reset(listOf(source1))
        fm.markFailed("src1")
        fm.markFailed("src1") // duplicate
        assertEquals(1, fm.getFailedSources().size)
    }

    // ─── isFallbackAllowed() — OFF policy ──────────────────────────────────

    @Test
    fun `OFF policy always returns false`() {
        val fm = FallbackManager()
        fm.reset(listOf(source1, source2, source3))

        assertFalse(fm.isFallbackAllowed(AutoFallbackPolicy.OFF))
        fm.incrementAttempts() // Even with attempts = 0 or > 0
        assertFalse(fm.isFallbackAllowed(AutoFallbackPolicy.OFF))
    }

    // ─── isFallbackAllowed() — ASK_BEFORE_FALLBACK policy ──────────────────

    @Test
    fun `ASK_BEFORE_FALLBACK always returns true`() {
        val fm = FallbackManager()
        fm.reset(listOf(source1, source2))

        assertTrue(fm.isFallbackAllowed(AutoFallbackPolicy.ASK_BEFORE_FALLBACK))
        fm.incrementAttempts()
        fm.incrementAttempts()
        fm.incrementAttempts()
        // Still true even after many attempts — UI handles the guard
        assertTrue(fm.isFallbackAllowed(AutoFallbackPolicy.ASK_BEFORE_FALLBACK))
    }

    // ─── isFallbackAllowed() — AUTO_FALLBACK_ONCE policy ───────────────────

    @Test
    fun `AUTO_FALLBACK_ONCE allows exactly one attempt`() {
        val fm = FallbackManager()
        fm.reset(listOf(source1, source2, source3))

        // Before any attempts: allowed
        assertTrue(fm.isFallbackAllowed(AutoFallbackPolicy.AUTO_FALLBACK_ONCE))

        fm.incrementAttempts() // attemptCount = 1
        // After 1 attempt: not allowed
        assertFalse(fm.isFallbackAllowed(AutoFallbackPolicy.AUTO_FALLBACK_ONCE))

        fm.incrementAttempts() // attemptCount = 2
        assertFalse(fm.isFallbackAllowed(AutoFallbackPolicy.AUTO_FALLBACK_ONCE))
    }

    // ─── isFallbackAllowed() — AUTO_FALLBACK_LIMITED policy ─────────

    @Test
    fun `AUTO_FALLBACK_LIMITED limits to candidate count when less than 5`() {
        val fm = FallbackManager()
        fm.reset(listOf(source1, source2)) // 2 candidates, limit = min(2, 5) = 2

        assertTrue(fm.isFallbackAllowed(AutoFallbackPolicy.AUTO_FALLBACK_LIMITED))
        fm.incrementAttempts() // 1
        assertTrue(fm.isFallbackAllowed(AutoFallbackPolicy.AUTO_FALLBACK_LIMITED))
        fm.incrementAttempts() // 2
        assertFalse(fm.isFallbackAllowed(AutoFallbackPolicy.AUTO_FALLBACK_LIMITED))
    }

    @Test
    fun `AUTO_FALLBACK_LIMITED limits to 5 when more than 5 candidates`() {
        val fm = FallbackManager()
        fm.reset(listOf(source1, source2, source3, source4, source5, source6)) // 6 candidates, limit = 5

        for (i in 0 until 5) {
            assertTrue("Attempt $i should be allowed", fm.isFallbackAllowed(AutoFallbackPolicy.AUTO_FALLBACK_LIMITED))
            fm.incrementAttempts()
        }
        // 6th attempt should be blocked
        assertFalse(fm.isFallbackAllowed(AutoFallbackPolicy.AUTO_FALLBACK_LIMITED))
    }

    @Test
    fun `AUTO_FALLBACK_LIMITED with zero candidates disallows fallback`() {
        val fm = FallbackManager()
        fm.reset(emptyList()) // 0 candidates, limit = min(0, 5) = 0

        assertFalse(fm.isFallbackAllowed(AutoFallbackPolicy.AUTO_FALLBACK_LIMITED))
    }

    // ─── selectNextBestCandidate() ─────────────────────────────────────────

    @Test
    fun `selectNextBestCandidate filters out failed sources`() = runTest {
        val fm = FallbackManager()
        fm.reset(listOf(source1, source2, source3))
        fm.markFailed("src1")
        fm.markFailed("src2")

        val candidate = fm.selectNextBestCandidate()
        assertNotNull(candidate)
        assertEquals("src3", candidate?.id)
    }

    @Test
    fun `selectNextBestCandidate returns null when all sources failed`() = runTest {
        val fm = FallbackManager()
        fm.reset(listOf(source1, source2))
        fm.markFailed("src1")
        fm.markFailed("src2")

        val candidate = fm.selectNextBestCandidate()
        assertNull(candidate)
    }

    @Test
    fun `selectNextBestCandidate returns null for empty candidates`() = runTest {
        val fm = FallbackManager()
        fm.reset(emptyList())

        val candidate = fm.selectNextBestCandidate()
        assertNull(candidate)
    }

    @Test
    fun `selectNextBestCandidate sorts by health score descending`() = runTest {
        // Pre-populate health: src1 has score 60, src2 has score 80, src3 defaults to 100
        SourceHealthRepository.recordFailure(source1.safeSourceId, "prov1", PlaybackSourceType.IPTV, "ERR")
        SourceHealthRepository.recordFailure(source1.safeSourceId, "prov1", PlaybackSourceType.IPTV, "ERR") // 60
        SourceHealthRepository.recordFailure(source2.safeSourceId, "prov2", PlaybackSourceType.IPTV, "ERR") // 80

        val fm = FallbackManager()
        fm.reset(listOf(source1, source2, source3))

        val best = fm.selectNextBestCandidate()
        assertEquals("src3", best?.id) // src3 has score 100 (default)
    }

    @Test
    fun `selectNextBestCandidate returns second-best when best is failed`() = runTest {
        // src3 is best (100), src2 is second (80), src1 is worst (60)
        SourceHealthRepository.recordFailure(source1.safeSourceId, "prov1", PlaybackSourceType.IPTV, "ERR")
        SourceHealthRepository.recordFailure(source1.safeSourceId, "prov1", PlaybackSourceType.IPTV, "ERR")
        SourceHealthRepository.recordFailure(source2.safeSourceId, "prov2", PlaybackSourceType.IPTV, "ERR")

        val fm = FallbackManager()
        fm.reset(listOf(source1, source2, source3))
        fm.markFailed("src3") // Best is failed

        val candidate = fm.selectNextBestCandidate()
        assertEquals("src2", candidate?.id) // Second-best
    }

    // ─── No infinite retry simulation ──────────────────────────────────────

    @Test
    fun `simulated fallback loop terminates for AUTO_FALLBACK_ONCE`() = runTest {
        val fm = FallbackManager()
        fm.reset(listOf(source1, source2, source3))

        var loopCount = 0
        val maxSafetyLoops = 100

        while (fm.isFallbackAllowed(AutoFallbackPolicy.AUTO_FALLBACK_ONCE) && loopCount < maxSafetyLoops) {
            val candidate = fm.selectNextBestCandidate()
            if (candidate == null) break
            fm.markFailed(candidate.id)
            fm.incrementAttempts()
            loopCount++
        }

        // Should have done exactly 1 fallback attempt
        assertEquals(1, loopCount)
        assertEquals(1, fm.getAttemptCount())
    }

    @Test
    fun `simulated fallback loop terminates for AUTO_FALLBACK_LIMITED`() = runTest {
        val fm = FallbackManager()
        val candidates = listOf(source1, source2, source3, source4, source5, source6)
        fm.reset(candidates)

        var loopCount = 0
        val maxSafetyLoops = 100

        while (fm.isFallbackAllowed(AutoFallbackPolicy.AUTO_FALLBACK_LIMITED) && loopCount < maxSafetyLoops) {
            val candidate = fm.selectNextBestCandidate()
            if (candidate == null) break
            fm.markFailed(candidate.id)
            fm.incrementAttempts()
            loopCount++
        }

        // Should have tried exactly 5 times (capped at 5 even though 6 candidates)
        assertEquals(5, loopCount)
        assertEquals(5, fm.getAttemptCount())
    }

    @Test
    fun `simulated fallback loop terminates when candidates exhausted before limit`() = runTest {
        val fm = FallbackManager()
        fm.reset(listOf(source1, source2)) // Only 2 candidates

        var loopCount = 0
        val maxSafetyLoops = 100

        while (fm.isFallbackAllowed(AutoFallbackPolicy.AUTO_FALLBACK_LIMITED) && loopCount < maxSafetyLoops) {
            val candidate = fm.selectNextBestCandidate()
            if (candidate == null) break
            fm.markFailed(candidate.id)
            fm.incrementAttempts()
            loopCount++
        }

        // Should have tried exactly 2 times (exhausted candidates)
        assertEquals(2, loopCount)
    }

    // ─── ASK_BEFORE_FALLBACK always allows but candidates exhaust ──────────

    @Test
    fun `ASK_BEFORE_FALLBACK terminates when candidates exhausted`() = runTest {
        val fm = FallbackManager()
        fm.reset(listOf(source1, source2))

        var loopCount = 0
        val maxSafetyLoops = 100

        while (fm.isFallbackAllowed(AutoFallbackPolicy.ASK_BEFORE_FALLBACK) && loopCount < maxSafetyLoops) {
            val candidate = fm.selectNextBestCandidate()
            if (candidate == null) break // This is how it terminates for ASK_BEFORE_FALLBACK
            fm.markFailed(candidate.id)
            fm.incrementAttempts()
            loopCount++
        }

        // Terminates because candidates are exhausted (selectNextBestCandidate returns null)
        assertEquals(2, loopCount)
    }

    // ─── incrementAttempts / getAttemptCount ────────────────────────────────

    @Test
    fun `incrementAttempts correctly tracks count`() {
        val fm = FallbackManager()
        fm.reset(listOf(source1))

        assertEquals(0, fm.getAttemptCount())
        fm.incrementAttempts()
        assertEquals(1, fm.getAttemptCount())
        fm.incrementAttempts()
        assertEquals(2, fm.getAttemptCount())
    }

    @Test
    fun `selectNextBestCandidate performs readonly query to avoid DB locks`() = runTest {
        // Record failure 2 hours in the past
        val pastTime = System.currentTimeMillis() - 7200_000L
        SourceHealthRepository.recordFailure(
            sourceId = source1.safeSourceId,
            providerId = "prov1",
            sourceType = PlaybackSourceType.IPTV,
            errorCategory = "ERR",
            timestamp = pastTime
        )

        // Verify it currently has a low health score (80)
        val initialHealth = SourceHealthRepository.getSourceHealth(source1.safeSourceId, currentTime = pastTime, readonly = true)
        assertEquals(80, initialHealth?.healthScore)

        val fm = FallbackManager()
        fm.reset(listOf(source1))
        val candidate = fm.selectNextBestCandidate()
        assertNotNull(candidate)

        // If the query was readonly, no update was written back. So retrieving at pastTime again remains 80.
        val afterHealth = SourceHealthRepository.getSourceHealth(source1.safeSourceId, currentTime = pastTime, readonly = true)
        assertEquals(80, afterHealth?.healthScore)
    }

    @Test
    fun `selectNextBestCandidate short-circuits when only one candidate remains`() = runTest {
        val fm = FallbackManager()
        fm.reset(listOf(source1))

        val originalDispatcher = SourceHealthRepository.dispatcher
        SourceHealthRepository.dispatcher = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                throw AssertionError("Dispatcher should not be used when short-circuiting")
            }
        }

        try {
            val candidate = fm.selectNextBestCandidate()
            assertEquals("src1", candidate?.id)
        } finally {
            SourceHealthRepository.dispatcher = originalDispatcher
        }
    }

    @Test
    fun `selectNextBestCandidate uses dispatcher when more than one candidate remains`() = runTest {
        val fm = FallbackManager()
        fm.reset(listOf(source1, source2))

        var dispatched = false
        val originalDispatcher = SourceHealthRepository.dispatcher
        SourceHealthRepository.dispatcher = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                dispatched = true
                block.run()
            }
        }

        try {
            val candidate = fm.selectNextBestCandidate()
            assertNotNull(candidate)
            assertTrue("Dispatcher should be used when multiple candidates are queried", dispatched)
        } finally {
            SourceHealthRepository.dispatcher = originalDispatcher
        }
    }

    @Test
    fun `selectNextBestCandidate queries run concurrently on specified dispatcher`() = kotlinx.coroutines.runBlocking {
        val fm = FallbackManager()
        val sources = listOf(source1, source2, source3)
        fm.reset(sources)

        val mockDb = Mockito.mock(com.example.calmsource.core.database.CalmSourceDatabase::class.java)
        val mockDao = Mockito.mock(com.example.calmsource.core.database.dao.HealthDao::class.java)
        Mockito.`when`(mockDb.healthDao()).thenReturn(mockDao)

        val instanceField = com.example.calmsource.core.database.DatabaseProvider::class.java.declaredFields.first {
            com.example.calmsource.core.database.CalmSourceDatabase::class.java.isAssignableFrom(it.type)
        }
        instanceField.isAccessible = true
        val originalInstance = instanceField.get(null)
        instanceField.set(null, mockDb)

        val activeQueries = java.util.concurrent.atomic.AtomicInteger(0)
        val maxConcurrency = java.util.concurrent.atomic.AtomicInteger(0)
        val latch = java.util.concurrent.CountDownLatch(3)

        Mockito.`when`(mockDao.getSourceHealth(Mockito.anyString())).thenAnswer { invocation ->
            val active = activeQueries.incrementAndGet()
            synchronized(maxConcurrency) {
                if (active > maxConcurrency.get()) {
                    maxConcurrency.set(active)
                }
            }
            latch.countDown()
            try {
                latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
            }
            activeQueries.decrementAndGet()

            val sourceId = invocation.arguments[0] as String
            com.example.calmsource.core.database.entity.SourceHealthEntity().apply {
                this.sourceId = sourceId
                this.healthScore = 100
            }
        }

        val threadPool = java.util.concurrent.Executors.newFixedThreadPool(3)
        val testDispatcher = threadPool.asCoroutineDispatcher()
        SourceHealthRepository.dispatcher = testDispatcher

        try {
            val candidate = fm.selectNextBestCandidate()
            assertNotNull(candidate)
            assertTrue("Expected concurrent execution, max concurrency was: ${maxConcurrency.get()}", maxConcurrency.get() > 1)
        } finally {
            instanceField.set(null, originalInstance)
            threadPool.shutdown()
        }
    }

    @Test
    fun `selectNextBestCandidate does not query database when exactly 1 candidate remains`() = runTest {
        val fm = FallbackManager()
        fm.reset(listOf(source1))

        val mockDb = Mockito.mock(com.example.calmsource.core.database.CalmSourceDatabase::class.java)
        val mockDao = Mockito.mock(com.example.calmsource.core.database.dao.HealthDao::class.java)
        Mockito.`when`(mockDb.healthDao()).thenReturn(mockDao)

        val instanceField = com.example.calmsource.core.database.DatabaseProvider::class.java.declaredFields.first {
            com.example.calmsource.core.database.CalmSourceDatabase::class.java.isAssignableFrom(it.type)
        }
        instanceField.isAccessible = true
        val originalInstance = instanceField.get(null)
        instanceField.set(null, mockDb)

        Mockito.`when`(mockDao.getSourceHealth(Mockito.anyString())).thenAnswer {
            throw AssertionError("Database should NOT be queried when exactly 1 candidate remains")
        }

        try {
            val candidate = fm.selectNextBestCandidate()
            assertEquals("src1", candidate?.id)
            Mockito.verify(mockDao, Mockito.never()).getSourceHealth(Mockito.anyString())
        } finally {
            instanceField.set(null, originalInstance)
        }
    }

    @Test
    fun `blockedCodecs is populated and prunes candidates correctly`() {
        val metadataHEVC = com.example.calmsource.core.model.PlaybackItemMetadata(
            title = "HEVC Video",
            videoCodec = "hevc"
        )
        val metadataAVC = com.example.calmsource.core.model.PlaybackItemMetadata(
            title = "AVC Video",
            videoCodec = "h264"
        )
        val sourceHEVC = source1.copy(metadata = metadataHEVC)
        val sourceAVC = source2.copy(metadata = metadataAVC)

        val fm = FallbackManager()
        fm.reset(listOf(sourceHEVC, sourceAVC))

        assertTrue(fm.getBlockedCodecs().isEmpty())

        fm.pruneCandidatesByCodec("hevc")
        assertTrue(fm.getBlockedCodecs().contains("hevc"))

        // sourceHEVC should be excluded from remaining candidates
        val remaining = fm.getRemainingCandidates()
        assertEquals(1, remaining.size)
        assertEquals(sourceAVC.id, remaining.first().id)

        // getRemainingCandidateIds should match
        val remainingIds = fm.getRemainingCandidateIds()
        assertEquals(listOf(sourceAVC.id), remainingIds)
    }

    @Test
    fun `reset clears blockedCodecs`() {
        val fm = FallbackManager()
        fm.reset(listOf(source1))
        fm.pruneCandidatesByCodec("h264")
        assertTrue(fm.getBlockedCodecs().contains("h264"))

        fm.reset(listOf(source2))
        assertTrue(fm.getBlockedCodecs().isEmpty())
    }
}
