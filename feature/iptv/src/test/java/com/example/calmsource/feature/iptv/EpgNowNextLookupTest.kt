package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.EPGProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpgNowNextLookupTest {

    @Test
    fun testCalculateNowNext() {
        val programs = listOf(
            EPGProgram("1", "ch1", "Prog 1", null, 1000L, 2000L),
            EPGProgram("2", "ch1", "Prog 2", null, 2000L, 3000L),
            EPGProgram("3", "ch1", "Prog 3", null, 4000L, 5000L) // Gap from 3000 to 4000
        )

        // Time 500: before Prog 1
        val res1 = IPTVRepository.calculateNowNext(programs, 500L)
        assertNull(res1.currentProgram)
        assertEquals("Prog 1", res1.nextProgram?.title)
        assertEquals(0f, res1.progressPercentage)

        // Time 1500: middle of Prog 1
        val res2 = IPTVRepository.calculateNowNext(programs, 1500L)
        assertEquals("Prog 1", res2.currentProgram?.title)
        assertEquals("Prog 2", res2.nextProgram?.title)
        assertEquals(0.5f, res2.progressPercentage)

        // Time 2000: exact boundary between Prog 1 and Prog 2
        val res3 = IPTVRepository.calculateNowNext(programs, 2000L)
        assertEquals("Prog 2", res3.currentProgram?.title)
        assertEquals("Prog 3", res3.nextProgram?.title)
        assertEquals(0.0f, res3.progressPercentage)

        // Time 3500: in the gap between Prog 2 and Prog 3
        val res4 = IPTVRepository.calculateNowNext(programs, 3500L)
        assertNull(res4.currentProgram)
        assertEquals("Prog 3", res4.nextProgram?.title)
        assertEquals(0f, res4.progressPercentage)

        // Time 4500: middle of Prog 3
        val res5 = IPTVRepository.calculateNowNext(programs, 4500L)
        assertEquals("Prog 3", res5.currentProgram?.title)
        assertNull(res5.nextProgram)
        assertEquals(0.5f, res5.progressPercentage)
        
        // Time 5000: exactly at the end of Prog 3
        val res6 = IPTVRepository.calculateNowNext(programs, 5000L)
        assertNull(res6.currentProgram)
        assertNull(res6.nextProgram)
        assertEquals(0f, res6.progressPercentage)
    }

    @Test
    fun testCalculateNowNext_Empty() {
        val programs = emptyList<EPGProgram>()
        val res = IPTVRepository.calculateNowNext(programs, 1000L)
        assertNull(res.currentProgram)
        assertNull(res.nextProgram)
        assertEquals(0f, res.progressPercentage)
    }

    @Test
    fun testGetNowNextForChannel_Unmatched() = kotlinx.coroutines.runBlocking {
        val res = IPTVRepository.getNowNextForChannel("unknown_channel", 1000L)
        assertNull(res.currentProgram)
        assertNull(res.nextProgram)
        assertEquals(0f, res.progressPercentage)
    }
}
