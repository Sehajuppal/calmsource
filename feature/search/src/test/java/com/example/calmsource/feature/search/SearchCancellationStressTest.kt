package com.example.calmsource.feature.search

import com.example.calmsource.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SearchCancellationStressTest {

    private val defaultPrefs = FakeData.defaultPreferences

    @Test
    fun testRapidCancellationStress() = runBlocking {
        val providers = listOf(
            object : SearchProvider {
                override val id = "p1"
                override val name = "P1"
                override val priority = 1
                override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
                    delay(100)
                    emit(SearchProviderResult(id, name, query))
                }
            },
            object : SearchProvider {
                override val id = "p2"
                override val name = "P2"
                override val priority = 2
                override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
                    delay(200)
                    emit(SearchProviderResult(id, name, query))
                }
            }
        )

        val engine = UniversalSearchEngineImpl(providers)
        val activeJobs = List(100) { i ->
            launch(Dispatchers.Default) {
                try {
                    engine.search("query-$i", defaultPrefs).collect {
                        // Consuming emissions
                    }
                } catch (e: CancellationException) {
                    // Expect cancellations
                }
            }
        }

        delay(30)
        activeJobs.forEach { it.cancel() }
        activeJobs.forEach { it.join() }
    }

    @Test
    fun testCancellationOfSingleProviderDoesNotCancelSiblings() = runBlocking {
        val provider1Completed = AtomicInteger(0)
        val provider2Cancelled = AtomicInteger(0)

        val provider1 = object : SearchProvider {
            override val id = "p1"
            override val name = "P1"
            override val priority = 1
            override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
                delay(50)
                provider1Completed.incrementAndGet()
                emit(SearchProviderResult(id, name, query))
            }
        }

        val provider2 = object : SearchProvider {
            override val id = "p2"
            override val name = "P2"
            override val priority = 2
            override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
                try {
                    delay(200)
                    emit(SearchProviderResult(id, name, query))
                } catch (e: CancellationException) {
                    provider2Cancelled.incrementAndGet()
                    throw e
                }
            }
        }

        val provider3 = object : SearchProvider {
            override val id = "p3"
            override val name = "P3"
            override val priority = 3
            override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
                delay(100)
                throw CancellationException("Intentional Cancellation")
            }
        }

        val engine = UniversalSearchEngineImpl(listOf(provider1, provider2, provider3))

        val emissions = engine.search("Spider-Man", defaultPrefs).toList()
        assertTrue("Emissions should not be empty", emissions.isNotEmpty())
        assertEquals(0, provider2Cancelled.get())
        assertEquals(1, provider1Completed.get())
    }

    @Test
    fun testRegularExceptionDoesNotCancelSiblings() = runBlocking {
        val provider1Completed = AtomicInteger(0)
        val provider2Completed = AtomicInteger(0)

        val provider1 = object : SearchProvider {
            override val id = "p1"
            override val name = "P1"
            override val priority = 1
            override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
                delay(100)
                throw RuntimeException("Intentional Failure")
            }
        }

        val provider2 = object : SearchProvider {
            override val id = "p2"
            override val name = "P2"
            override val priority = 2
            override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> = flow {
                delay(200)
                provider2Completed.incrementAndGet()
                emit(SearchProviderResult(id, name, query))
            }
        }

        val engine = UniversalSearchEngineImpl(listOf(provider1, provider2))
        val emissions = engine.search("Spider-Man", defaultPrefs).toList()

        assertEquals(1, provider2Completed.get())
        assertTrue("Emissions should contain results", emissions.isNotEmpty())
    }
}
