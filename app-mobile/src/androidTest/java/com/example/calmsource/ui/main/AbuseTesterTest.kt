package com.example.calmsource.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import com.example.calmsource.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AbuseTesterTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun rapidTabSwitching() {
        for (i in 0..20) {
            composeTestRule.onNodeWithText("Live TV").performClick()
            composeTestRule.onNodeWithText("Search").performClick()
            composeTestRule.onNodeWithText("Settings").performClick()
            composeTestRule.onNodeWithText("Home").performClick()
        }
    }

    @Test
    fun rapidDialogToggling() {
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithText("IPTV Playlists & Services").performClick()
        
        for (i in 0..10) {
            composeTestRule.onNodeWithText("+ Add Xtream Login").performClick()
            composeTestRule.onNodeWithText("Cancel").performClick()
        }
        
        // Go back
        composeTestRule.onNodeWithContentDescription("Back").performClick()
    }

    @Test
    fun rapidBackPresses() {
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithText("IPTV Playlists & Services").performClick()
        composeTestRule.onNodeWithText("+ Add Xtream Login").performClick()

        // Press back
        composeTestRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        
        composeTestRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        
        composeTestRule.onNodeWithText("Settings").assertExists()
    }
}
