package com.example.calmsource.core.playback.session

import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionStoreTest {

    private val store = PlaybackSessionStore()
    private val source = PlaybackSource("s1", PlaybackSourceType.IPTV, "S1", "https://example.com/s.m3u8")

    @Test
    fun `begin assigns monotonic session ids`() {
        val id1 = store.begin(PlaybackRequest(source), listOf(source), SessionPhase.Preparing)
        val id2 = store.begin(PlaybackRequest(source), listOf(source), SessionPhase.Racing)
        assertTrue(id2 > id1)
    }

    @Test
    fun `isCurrent returns true only for the latest session`() {
        val id1 = store.begin(PlaybackRequest(source), listOf(source), SessionPhase.Preparing)
        assertTrue(store.isCurrent(id1))
        val id2 = store.begin(PlaybackRequest(source), listOf(source), SessionPhase.Preparing)
        assertFalse(store.isCurrent(id1))
        assertTrue(store.isCurrent(id2))
    }

    @Test
    fun `invalidate supersedes prior session ids`() {
        val id = store.begin(PlaybackRequest(source), listOf(source), SessionPhase.Racing)
        store.invalidate()
        assertFalse(store.isCurrent(id))
        assertNull(store.active())
    }

    @Test
    fun `active session tracks phase updates`() {
        val id = store.begin(PlaybackRequest(source), listOf(source), SessionPhase.Racing)
        store.updatePhase(SessionPhase.Preparing)
        assertEquals(SessionPhase.Preparing, store.active()?.phase)
        assertTrue(store.isCurrent(id))
    }

    @Test
    fun `begin replaces active session snapshot`() {
        store.begin(PlaybackRequest(source), listOf(source), SessionPhase.Racing)
        val nextSource = source.copy(id = "s2", title = "S2")
        store.begin(PlaybackRequest(nextSource), listOf(nextSource), SessionPhase.Preparing)
        assertNotNull(store.active())
        assertEquals("s2", store.active()?.request?.source?.id)
    }
}
