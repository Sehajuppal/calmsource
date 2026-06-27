package com.example.calmsource.core.playback.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackLanguageFormatterTest {
    @Test
    fun displayLanguage_formatsIsoCode() {
        val english = TrackLanguageFormatter.displayLanguage("en")
        assertTrue(english.equals("English", ignoreCase = true))
    }

    @Test
    fun trackLabel_appendsLanguageWhenMissingFromName() {
        val label = TrackLanguageFormatter.trackLabel("Track 1", "es")
        assertTrue(label.contains("Track 1"))
        assertTrue(label.contains("·"))
    }

    @Test
    fun trackLabel_skipsDuplicateLanguageInName() {
        val label = TrackLanguageFormatter.trackLabel("English", "en")
        assertEquals("English", label)
    }
}
