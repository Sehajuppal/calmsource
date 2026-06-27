package com.example.calmsource.feature.search

import com.example.calmsource.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SearchConcurrencyStressTest {

    private val defaultPrefs = FakeData.defaultPreferences

    @Test
    fun testUniversalSearchHighConcurrency() = runBlocking {
        // Create 5 fake providers with varying delays
        val providers = List(5) { id ->
            object : SearchProvider {
                override val id = "prov-$id"
                override val name = "Provider $id"
                override val priority = id * 10
                override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> {
                    val pid = this.id
                    val pname = this.name
                    return flow {
                        delay((10..50).random().toLong())
                        emit(SearchProviderResult(
                            providerId = pid,
                            providerName = pname,
                            query = query,
                            mediaItems = listOf(
                                MediaItem("item-$id-1", "${query.query} from $id A", MediaType.MOVIE),
                                MediaItem("item-$id-2", "${query.query} from $id B", MediaType.MOVIE)
                            )
                        ))
                    }
                }
            }
        }

        val engine = UniversalSearchEngineImpl(providers)

        // Launch 100 concurrent search queries in parallel
        val jobCount = 100
        val completedJobs = AtomicInteger(0)

        val jobs = List(jobCount) { index ->
            async(Dispatchers.Default) {
                val flow = engine.search("Query-$index", defaultPrefs)
                val results = flow.toList()
                assertTrue(results.isNotEmpty())
                // Ensure at least some items are returned
                val lastEmission = results.last()
                assertTrue(lastEmission.isNotEmpty())
                completedJobs.incrementAndGet()
            }
        }

        jobs.awaitAll()
        assertEquals(jobCount, completedJobs.get())
    }

    @Test
    fun testUniversalSearchRapidCancellationStress() = runBlocking {
        val slowProvider = object : SearchProvider {
            override val id = "slow-prov"
            override val name = "Slow Provider"
            override val priority = 100
            override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> {
                val pid = this.id
                val pname = this.name
                return flow {
                    delay(1000) // very slow
                    emit(SearchProviderResult(pid, pname, query))
                }
            }
        }

        val fastProvider = object : SearchProvider {
            override val id = "fast-prov"
            override val name = "Fast Provider"
            override val priority = 200
            override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> {
                val pid = this.id
                val pname = this.name
                return flow {
                    delay(5)
                    emit(SearchProviderResult(
                        providerId = pid,
                        providerName = pname,
                        query = query,
                        mediaItems = listOf(FakeData.movieSpiderman)
                    ))
                }
            }
        }

        val engine = UniversalSearchEngineImpl(listOf(slowProvider, fastProvider))

        // Repeat rapid start-then-cancel cycle 50 times
        repeat(50) {
            val scope = CoroutineScope(Dispatchers.Default + Job())
            val job = scope.launch {
                engine.search("Spider-Man", defaultPrefs).collect {
                    // Collect first emission then wait briefly
                }
            }
            delay((1..30).random().toLong()) // cancel at random stage of the flow
            job.cancelAndJoin()
        }
    }

    @Test
    fun testUniversalSearchTimeoutStress() = runBlocking {
        val verySlowProvider = object : SearchProvider {
            override val id = "very-slow"
            override val name = "Very Slow"
            override val priority = 10
            override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> {
                val pid = this.id
                val pname = this.name
                return flow {
                    delay(2000)
                    emit(SearchProviderResult(pid, pname, query))
                }
            }
        }

        val fastProvider = object : SearchProvider {
            override val id = "fast"
            override val name = "Fast"
            override val priority = 100
            override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> {
                val pid = this.id
                val pname = this.name
                return flow {
                    delay(10)
                    emit(SearchProviderResult(
                        providerId = pid,
                        providerName = pname,
                        query = query,
                        mediaItems = listOf(FakeData.movieSpiderman)
                    ))
                }
            }
        }

        val engine = UniversalSearchEngineImpl(listOf(verySlowProvider, fastProvider))
        
        // Timeout limit for very-slow is 50ms, so it will timeout
        val timeoutPolicy = SearchTimeoutPolicy(
            defaultTimeoutMs = 1000L,
            providerTimeoutsMs = mapOf("very-slow" to 50L)
        )

        // Execute searches and verify the slow provider times out but fast provider's results are present
        val emissions = engine.search("Spider-Man", defaultPrefs, timeoutPolicy).toList()
        assertTrue(emissions.isNotEmpty())
        
        val lastGroups = emissions.last()
        assertTrue(lastGroups.isNotEmpty())
        
        // Verify that the VOD/Movie group contains Spiderman from the fast provider
        val movieGroup = lastGroups.firstOrNull { it.groupType == SearchGroupType.MOVIES }
        assertNotNull(movieGroup)
        assertTrue(movieGroup!!.results.any { it.mediaItem.title == FakeData.movieSpiderman.title })
    }

    @Test
    fun testUniversalSearchMultipleConcurrentCancellations() = runBlocking {
        val provider = object : SearchProvider {
            override val id = "prov"
            override val name = "Prov"
            override val priority = 10
            override fun search(query: SearchQuery, prefs: UserPreferences): Flow<SearchProviderResult> {
                val pid = this.id
                val pname = this.name
                return flow {
                    delay(100)
                    emit(SearchProviderResult(pid, pname, query))
                }
            }
        }
        val engine = UniversalSearchEngineImpl(listOf(provider))

        // Spin up 50 scopes, launch search in each, and cancel them concurrently
        val scopes = List(50) { CoroutineScope(Dispatchers.Default + Job()) }
        val jobs = scopes.map { scope ->
            scope.launch {
                try {
                    engine.search("Spider-Man", defaultPrefs).collect {}
                } catch (e: CancellationException) {
                    // expected
                }
            }
        }

        delay(30) // Wait until they are all running/delaying
        // Concurrently cancel all of them
        scopes.forEach { it.cancel() }
        
        // Wait for all to finish cancelling
        jobs.forEach { it.join() }
    }
}
