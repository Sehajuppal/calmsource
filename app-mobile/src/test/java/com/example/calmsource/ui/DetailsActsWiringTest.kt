package com.example.calmsource.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsActsWiringTest {

    private fun readMobileSource(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("app-mobile/$relativePath"),
            File("../../app-mobile/$relativePath"),
        )
        val file = candidates.firstOrNull { it.exists() }
        return file?.readText() ?: throw java.io.FileNotFoundException("Could not find $relativePath")
    }

    @Test
    fun detailsScreenUsesCollapsedSourcesActByDefault() {
        val source = readMobileSource("src/main/java/com/example/calmsource/ui/DetailsScreen.kt")

        assertTrue(
            "Details should default sources panel to collapsed",
            source.contains("var isSourcesExpanded by remember { mutableStateOf(false) }"),
        )
        assertTrue(
            "Details should expose a Sources header",
            source.contains("details_sources_title"),
        )
        assertFalse(
            "Ways to Watch should not render outside the sources act",
            source.contains("item(key = \"alternative_options_header\")"),
        )
        assertTrue(
            "Alternative watch options belong inside expanded sources",
            source.contains("item(key = \"sources_alt_options\")"),
        )
    }

    @Test
    fun detailsScreenUsesInlineNoticesInsteadOfBlockingDialogs() {
        val source = readMobileSource("src/main/java/com/example/calmsource/ui/DetailsScreen.kt")

        assertFalse(
            "Details should not use AlertDialog for source failures",
            source.contains("AlertDialog("),
        )
        assertTrue(
            "Details should route playback issues to inline notices",
            source.contains("detailsNotice = DetailsNotice.SourceUnavailable") &&
                source.contains("detailsNotice = DetailsNotice.SourceBlocked"),
        )
        assertTrue(
            "Details should render inline unavailable notice",
            source.contains("LumenInlineMessage("),
        )
        assertTrue(
            "Details should render blocked notice with action",
            source.contains("DetailsBlockedNotice("),
        )
    }

    @Test
    fun detailsScreenKeepsPlayActionsInStickyChrome() {
        val source = readMobileSource("src/main/java/com/example/calmsource/ui/DetailsScreen.kt")

        assertTrue(
            "Details should keep Play in bottom chrome",
            source.contains("AdaptiveButton(") && source.contains("handlePlayOption(bestMatch, true)"),
        )
        assertTrue(
            "Details should keep My List in bottom chrome",
            source.contains("details_my_list_add") || source.contains("details_my_list_saved"),
        )
    }
}
