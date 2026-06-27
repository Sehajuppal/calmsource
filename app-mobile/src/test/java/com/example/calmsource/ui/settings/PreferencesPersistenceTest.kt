package com.example.calmsource.ui.settings

import com.example.calmsource.core.database.repository.UserPreferencesRepository
import com.example.calmsource.core.model.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PreferencesPersistenceTest {

    @Test
    fun testUserPreferencesUpdateSurvivesInRepository() = runBlocking {
        // Initial state
        val initialPrefs = UserPreferencesRepository.preferences.first()
        
        // Update language to Hindi
        UserPreferencesRepository.updatePreferences { it.copy(primaryLanguage = "Hindi", preferCachedDebrid = true) }
        
        // Wait briefly for StateFlow update to propagate
        val updatedPrefs = UserPreferencesRepository.preferences.first { it.primaryLanguage == "Hindi" }
        
        assertEquals("Hindi", updatedPrefs.primaryLanguage)
        assertEquals(true, updatedPrefs.preferCachedDebrid)
    }

    @Test
    fun testSeparateIptvCategoriesByProviderToggle() = runBlocking {
        val initial = UserPreferencesRepository.preferences.value.separateIptvCategoriesByProvider
        UserPreferencesRepository.updatePreferences { it.copy(separateIptvCategoriesByProvider = !initial) }

        // Wait briefly for StateFlow update to propagate
        val updated = UserPreferencesRepository.preferences.first { it.separateIptvCategoriesByProvider != initial }.separateIptvCategoriesByProvider

        assertEquals(!initial, updated)
    }
}
