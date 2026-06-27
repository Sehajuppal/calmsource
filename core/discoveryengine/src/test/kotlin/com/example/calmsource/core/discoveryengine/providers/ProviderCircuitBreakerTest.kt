package com.example.calmsource.core.discoveryengine.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCircuitBreakerTest {
    @Test
    fun `opens after three consecutive failures and closes after cooldown`() {
        var now = 1_000L
        val breaker = ProviderCircuitBreaker(clockMs = { now })

        breaker.recordFailure("provider-a")
        breaker.recordFailure("provider-a")
        assertFalse(breaker.isOpen("provider-a"))

        val opened = breaker.recordFailure("provider-a")
        assertEquals(BreakerState.OPEN, opened.state)
        assertTrue(breaker.isOpen("provider-a"))
        assertEquals(now + 5 * 60 * 1000L, opened.cooldownUntilMs)

        now = opened.cooldownUntilMs!!
        assertFalse(breaker.isOpen("provider-a"))
        assertEquals(BreakerState.CLOSED, breaker.snapshot().getValue("provider-a").state)
    }

    @Test
    fun `repeat opens escalate cooldowns`() {
        var now = 10_000L
        val breaker = ProviderCircuitBreaker(clockMs = { now })

        repeat(3) { breaker.recordFailure("provider-b") }
        val firstOpen = breaker.snapshot().getValue("provider-b")
        assertEquals(now + 5 * 60 * 1000L, firstOpen.cooldownUntilMs)

        now = firstOpen.cooldownUntilMs!!
        assertFalse(breaker.isOpen("provider-b"))

        repeat(3) { breaker.recordFailure("provider-b") }
        val secondOpen = breaker.snapshot().getValue("provider-b")
        assertEquals(now + 15 * 60 * 1000L, secondOpen.cooldownUntilMs)
    }

    @Test
    fun `manual reset closes breaker and clears escalation`() {
        var now = 20_000L
        val breaker = ProviderCircuitBreaker(clockMs = { now })

        repeat(3) { breaker.recordFailure("provider-c") }
        assertTrue(breaker.isOpen("provider-c"))

        breaker.reset("provider-c")
        assertFalse(breaker.isOpen("provider-c"))

        now += 1_000L
        repeat(3) { breaker.recordFailure("provider-c") }
        val reopened = breaker.snapshot().getValue("provider-c")
        assertEquals(now + 5 * 60 * 1000L, reopened.cooldownUntilMs)
    }
}
