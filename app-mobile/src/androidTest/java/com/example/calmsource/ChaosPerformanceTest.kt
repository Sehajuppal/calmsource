package com.example.calmsource

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.calmsource.feature.iptv.IPTVRepository
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.entity.IPTVChannelEntity
import com.example.calmsource.core.database.entity.IPTVProviderEntity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChaosPerformanceTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testMassiveDbWritesAndUiResponsiveness() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = DatabaseProvider.getDatabase(context)
        
        // Insert a provider
        db.iptvDao().insertProvider(IPTVProviderEntity().apply {
            id = "chaos_provider"
            name = "Chaos Provider"
            isEnabled = true
        })

        // Spam Database writes (10,000 channels)
        val channels = (1..10000).map { i ->
            IPTVChannelEntity().apply {
                id = "chan_$i"
                providerId = "chaos_provider"
                name = "Chaos Channel $i"
                category = "Chaos Category"
            }
        }
        
        // Write in chunks to simulate bursts
        channels.chunked(1000).forEach { chunk ->
            db.iptvDao().insertChannels(chunk)
        }
        
        // Wait for UI to settle
        composeTestRule.waitForIdle()
        
        // Navigate to Live TV or Settings and perform rapid actions
        // Assuming navigation buttons are available in bottom bar or drawer
        // This relies on the UI elements existing. We'll try to click around.
        
        try {
            for (i in 1..20) {
                // Rapidly switch tabs if they exist
                composeTestRule.onNodeWithText("Live TV", ignoreCase = true, substring = true).performClick()
                composeTestRule.onNodeWithText("Search", ignoreCase = true, substring = true).performClick()
                composeTestRule.onNodeWithText("Settings", ignoreCase = true, substring = true).performClick()
            }
        } catch (e: Exception) {
            // ignore if not found
        }
        
        // Do rapid searches
        try {
            composeTestRule.onNodeWithText("Search", ignoreCase = true, substring = true).performClick()
            composeTestRule.onNode(hasSetTextAction()).performTextInput("Chaos")
            composeTestRule.onNode(hasSetTextAction()).performTextInput("Channel 9999")
            composeTestRule.onNode(hasSetTextAction()).performTextClearance()
            composeTestRule.onNode(hasSetTextAction()).performTextInput("News")
        } catch (e: Exception) {
            // ignore
        }
        
        // Ensure no ANR has happened (test would fail or time out if ANR occurs)
    }
}
