package com.example.calmsource.core.discoveryengine.providers

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenBucketRateLimiterTest {

    @Test
    fun deniesBackgroundRequestWhenBurstIsConsumedAndWaitBudgetIsZero() = runBlocking {
        val limiter = TokenBucketRateLimiter(
            configs = mapOf(
                "metadata" to RateLimitConfig(tokensPerSecond = 0.0, burstCapacity = 1, maxWaitMs = 0L),
                "default" to RateLimitConfig(tokensPerSecond = 0.0, burstCapacity = 1, maxWaitMs = 0L)
            )
        )

        assertTrue(limiter.acquire("provider-a", "metadata"))
        assertFalse(limiter.acquire("provider-a", "metadata"))

        val snapshot = limiter.snapshot().single()
        assertEquals("provider-a", snapshot.providerId)
        assertEquals("metadata", snapshot.requestType)
        assertEquals(1L, snapshot.deniedRequests)
    }

    @Test
    fun keepsProviderBucketsIndependent() = runBlocking {
        val limiter = TokenBucketRateLimiter(
            configs = mapOf(
                "availability" to RateLimitConfig(tokensPerSecond = 0.0, burstCapacity = 1, maxWaitMs = 0L),
                "default" to RateLimitConfig(tokensPerSecond = 0.0, burstCapacity = 1, maxWaitMs = 0L)
            )
        )

        assertTrue(limiter.acquire("provider-a", "availability"))
        assertTrue(limiter.acquire("provider-b", "availability"))
        assertFalse(limiter.acquire("provider-a", "availability"))

        val denied = limiter.snapshot().associate { it.providerId to it.deniedRequests }
        assertEquals(1L, denied["provider-a"])
        assertEquals(0L, denied["provider-b"])
    }
}
