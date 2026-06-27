package com.example.calmsource.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefetchCoordinatorTest {
    @Test
    fun `planner deduplicates validates and bounds requests`() {
        val plan = PrefetchPlanner.plan(
            urls = listOf(
                "https://images.example/one.jpg",
                "https://images.example/one.jpg",
                "ftp://images.example/two.jpg",
                null,
                "http://images.example/three.jpg",
                "https://images.example/four.jpg"
            ),
            activeUrls = emptySet(),
            allowNonCriticalRequests = true,
            maxRequests = 2
        )

        assertEquals(
            listOf(
                "https://images.example/one.jpg",
                "http://images.example/three.jpg"
            ),
            plan.urls
        )
        assertEquals(1, plan.droppedCount)
        assertFalse(plan.skippedBecausePaused)
    }

    @Test
    fun `planner skips active requests`() {
        val plan = PrefetchPlanner.plan(
            urls = listOf(
                "https://images.example/active.jpg",
                "https://images.example/new.jpg"
            ),
            activeUrls = setOf("https://images.example/active.jpg"),
            allowNonCriticalRequests = true,
            maxRequests = 12
        )

        assertEquals(listOf("https://images.example/new.jpg"), plan.urls)
    }

    @Test
    fun `planner refuses all work while playback pauses image requests`() {
        val plan = PrefetchPlanner.plan(
            urls = listOf("https://images.example/poster.jpg"),
            activeUrls = emptySet(),
            allowNonCriticalRequests = false,
            maxRequests = 12
        )

        assertTrue(plan.urls.isEmpty())
        assertTrue(plan.skippedBecausePaused)
        assertEquals(1, plan.droppedCount)
    }

    @Test
    fun `planner remains bounded under a large home catalog`() {
        val plan = PrefetchPlanner.plan(
            urls = (0 until 10_000).map { "https://images.example/$it.jpg" },
            activeUrls = emptySet(),
            allowNonCriticalRequests = true,
            maxRequests = PrefetchCoordinator.DEFAULT_MAX_REQUESTS
        )

        assertEquals(PrefetchCoordinator.DEFAULT_MAX_REQUESTS, plan.urls.size)
        assertEquals(10_000 - PrefetchCoordinator.DEFAULT_MAX_REQUESTS, plan.droppedCount)
    }
}
