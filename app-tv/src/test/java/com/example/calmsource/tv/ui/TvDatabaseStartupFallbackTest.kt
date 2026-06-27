package com.example.calmsource.tv.ui

import android.app.Application
import com.example.calmsource.core.database.CalmSourceDatabase
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.repository.FallbackUserMemoryRepository
import com.example.calmsource.core.discoveryengine.DiscoveryEngine
import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineRepository
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.lang.reflect.Field

class TvDatabaseStartupFallbackTest {

    private lateinit var mockApp: Application
    private lateinit var mockDiscoveryRepo: DiscoveryEngineRepository

    @Before
    fun setUp() {
        mockApp = mock(Application::class.java)
        mockDiscoveryRepo = mock(DiscoveryEngineRepository::class.java)

        // Find the CalmSourceDatabase field in DatabaseProvider (type CalmSourceDatabase)
        val dbField = DatabaseProvider::class.java.declaredFields.firstOrNull {
            CalmSourceDatabase::class.java.isAssignableFrom(it.type)
        }
        if (dbField != null) {
            dbField.isAccessible = true
            dbField.set(null, null)
        }

        // Reset appContext to null (static field)
        val contextField = DatabaseProvider::class.java.getDeclaredField("appContext")
        contextField.isAccessible = true
        contextField.set(null, null)

        // Mock DiscoveryEngine repository injection
        val repoField = DiscoveryEngine::class.java.getDeclaredField("repository")
        repoField.isAccessible = true
        repoField.set(DiscoveryEngine, mockDiscoveryRepo)
    }

    private fun getMemoryRepositoryField(clazz: Class<*>): Field {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField("memoryRepository")
            } catch (e: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException("memoryRepository not found in class hierarchy")
    }

    @Test
    fun testTvHomeViewModelFallbackWhenDatabaseThrows() {
        whenever(mockApp.applicationContext).thenThrow(RuntimeException("Simulated TV database startup failure"))

        val tvHomeViewModel = TvHomeViewModel(mockApp)

        val memoryRepoField = getMemoryRepositoryField(TvHomeViewModel::class.java)
        memoryRepoField.isAccessible = true
        val memoryRepo = memoryRepoField.get(tvHomeViewModel)

        assertNotNull("memoryRepository should not be null on TV HomeViewModel", memoryRepo)
        assertTrue(
            "memoryRepository should fall back to FallbackUserMemoryRepository on TV HomeViewModel",
            memoryRepo is FallbackUserMemoryRepository
        )
    }

    @Test
    fun testTvSearchViewModelFallbackWhenDatabaseThrows() {
        whenever(mockApp.applicationContext).thenThrow(RuntimeException("Simulated TV database startup failure"))

        val tvSearchViewModel = TvSearchViewModel(mockApp)

        val memoryRepoField = getMemoryRepositoryField(TvSearchViewModel::class.java)
        memoryRepoField.isAccessible = true
        val memoryRepo = memoryRepoField.get(tvSearchViewModel)

        assertNotNull("memoryRepository should not be null on TV SearchViewModel", memoryRepo)
        assertTrue(
            "memoryRepository should fall back to FallbackUserMemoryRepository on TV SearchViewModel",
            memoryRepo is FallbackUserMemoryRepository
        )
    }
}
