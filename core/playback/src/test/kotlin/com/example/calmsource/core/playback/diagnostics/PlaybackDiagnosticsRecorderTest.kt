package com.example.calmsource.core.playback.diagnostics

import com.example.calmsource.core.model.PlaybackSessionDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackDiagnosticsRecorderTest {

    @Before
    fun setup() {
        PlaybackDiagnosticsRecorder.clearForTests()
    }

    @Test
    fun `record keeps only the most recent events`() {
        repeat(40) { index ->
            PlaybackDiagnosticsRecorder.record("test", "event-$index", "src-$index")
        }
        val events = PlaybackDiagnosticsRecorder.recentEvents()
        assertEquals(32, events.size)
        assertEquals("event-8", events.first().detail)
        assertEquals("event-39", events.last().detail)
    }

    @Test
    fun `updateSnapshot attaches recent events`() {
        PlaybackDiagnosticsRecorder.record("prepare", "Initial prepare", "src-1")
        PlaybackDiagnosticsRecorder.updateSnapshot(
            PlaybackSessionDiagnostics(sessionId = 7L, phase = "Preparing", sourceId = "src-1")
        )
        val snapshot = PlaybackDiagnosticsRecorder.lastSessionSnapshot
        assertEquals(7L, snapshot.sessionId)
        assertEquals(1, snapshot.recentEvents.size)
        assertEquals("prepare", snapshot.recentEvents.single().kind)
    }

    @Test
    fun `clearForTests resets buffer and snapshot`() {
        PlaybackDiagnosticsRecorder.record("terminal", "All sources failed")
        PlaybackDiagnosticsRecorder.updateSnapshot(
            PlaybackSessionDiagnostics(sessionId = 1L, consecutiveFailures = 5)
        )
        PlaybackDiagnosticsRecorder.clearForTests()
        assertTrue(PlaybackDiagnosticsRecorder.recentEvents().isEmpty())
        assertEquals(0L, PlaybackDiagnosticsRecorder.lastSessionSnapshot.sessionId)
    }
}
