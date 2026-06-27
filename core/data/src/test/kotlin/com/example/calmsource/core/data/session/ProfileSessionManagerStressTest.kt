package com.example.calmsource.core.data.session

import android.content.Context
import android.content.SharedPreferences
import com.example.calmsource.core.data.ProfileRepository
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.entity.ProfileEntity
import dagger.Lazy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ProfileSessionManagerStressTest {

    private lateinit var context: Context
    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var executorService: java.util.concurrent.ExecutorService
    private lateinit var testDispatcher: CoroutineDispatcher

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        fakePrefs = FakeSharedPreferences()
        profileRepository = FakeProfileRepository()

        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(fakePrefs)

        executorService = Executors.newFixedThreadPool(8)
        testDispatcher = executorService.asCoroutineDispatcher()

        setDatabaseReady(false)
    }

    @After
    fun tearDown() {
        executorService.shutdown()
        DatabaseProvider.resetForTesting()
    }

    private fun setDatabaseReady(ready: Boolean) {
        val field = DatabaseProvider::class.java.getDeclaredField("_databaseReady")
        field.isAccessible = true
        val mutableFlow = field.get(DatabaseProvider) as MutableStateFlow<Boolean>
        mutableFlow.value = ready
    }

    @Test
    fun testSelectProfileConcurrency_stressAndDesync() = runBlocking {
        // Pre-populate 2 profiles
        val profile1 = ProfileEntity("profile_1", "Profile 1")
        val profile2 = ProfileEntity("profile_2", "Profile 2")
        profileRepository.insertProfile(profile1)
        profileRepository.insertProfile(profile2)

        val lazyRepository: Lazy<ProfileRepository> = Lazy { profileRepository }
        val scope = CoroutineScope(SupervisorJob() + testDispatcher)

        val manager = ProfileSessionManagerImpl(
            context = context,
            profileRepositoryLazy = lazyRepository,
            scope = scope,
            ioDispatcher = testDispatcher
        )

        // Initialize session first
        setDatabaseReady(true)
        while (manager.activeProfile.value == null) {
            delay(10)
        }

        // Set the interceptor: if profile_1 is written to prefs, we block the executing thread for 100ms.
        // This simulates a context switch/preemption after the prefs write but before the StateFlow update.
        fakePrefs.onApplyInterceptor = { key, value ->
            if (key == "active_profile_id" && value == "profile_1") {
                Thread.sleep(100)
            }
        }

        // Launch selectProfile("profile_1") and selectProfile("profile_2") concurrently
        val job1 = scope.launch {
            manager.selectProfile("profile_1")
        }
        delay(10) // Ensure job1 starts first and enters preference write
        val job2 = scope.launch {
            manager.selectProfile("profile_2")
        }

        joinAll(job1, job2)

        val activeInMemory = manager.activeProfile.value?.id
        val activeInPrefs = fakePrefs.getString("active_profile_id", null)

        println("Stress Test Result:")
        println("Active In-Memory: $activeInMemory")
        println("Active In-Prefs: $activeInPrefs")

        // Assert that the memory state and persistent preferences are synchronized.
        // Under high concurrency, due to lack of synchronization/locks in selectProfile,
        // they can drift apart (desynchronize).
        assertEquals("Memory and preference active profile IDs should be synchronized", activeInMemory, activeInPrefs)
    }

    @Test
    fun testAutoGeneration_onEmptyDatabase_concurrentFirstBoot() = runBlocking {
        // Empty repository initially
        val lazyRepository: Lazy<ProfileRepository> = Lazy { profileRepository }
        val scope = CoroutineScope(SupervisorJob() + testDispatcher)

        // Create 5 concurrent managers using the same shared repository and context
        val managers = List(5) {
            ProfileSessionManagerImpl(
                context = context,
                profileRepositoryLazy = lazyRepository,
                scope = scope,
                ioDispatcher = testDispatcher
            )
        }

        // Concurrently set database ready, triggering initializeProfileSession on all 5 managers
        setDatabaseReady(true)

        // Wait until all managers have initialized
        withTimeout(5000) {
            while (managers.any { it.activeProfile.value == null }) {
                delay(10)
            }
        }

        // Check if duplicate default profiles are created.
        // In FakeProfileRepository, we track the insert count.
        val profiles = profileRepository.getProfiles()
        println("Auto-generation Profiles: $profiles")
        println("Total Insert Count: ${profileRepository.insertCount.get()}")

        assertEquals(1, profiles.size)
        assertEquals("default", profiles[0].id)
        // Since all 5 managers checked profiles.isEmpty() concurrently,
        // they might have all called insertProfile.
        // We assert that the database handles it and does not end up with duplicate profiles.
        assertTrue("Duplicate profiles should not exist in DB", profiles.size == 1)
    }

    @Test
    fun testSelectProfileThreadSafety_noCrashUnderLoad() = runBlocking {
        val profiles = (0..9).map { i ->
            ProfileEntity("profile_$i", "Profile Name $i")
        }
        profiles.forEach { profileRepository.insertProfile(it) }

        val lazyRepository: Lazy<ProfileRepository> = Lazy { profileRepository }
        val scope = CoroutineScope(SupervisorJob() + testDispatcher)

        val manager = ProfileSessionManagerImpl(
            context = context,
            profileRepositoryLazy = lazyRepository,
            scope = scope,
            ioDispatcher = testDispatcher
        )

        setDatabaseReady(true)
        while (manager.activeProfile.value == null) {
            delay(10)
        }

        // Concurrently select profile from 200 coroutines running on different threads
        val jobs = List(200) { index ->
            scope.launch {
                val targetProfile = "profile_${index % 10}"
                manager.selectProfile(targetProfile)
            }
        }

        // This verifies that no ConcurrentModificationException or other thread-safety crashes occur.
        jobs.joinAll()
    }

    @Test
    fun testInitializationRecovery_onTransientFailure() = runBlocking {
        // Simulate a transient DB exception (e.g. database locked) during first call to getProfiles
        var attempts = 0
        profileRepository.getProfilesInterceptor = {
            attempts++
            if (attempts == 1) {
                throw RuntimeException("Transient DB Exception (Locked)")
            }
            profileRepository.profiles.values.toList()
        }

        val lazyRepository: Lazy<ProfileRepository> = Lazy { profileRepository }
        val scope = CoroutineScope(SupervisorJob() + testDispatcher)

        val manager = ProfileSessionManagerImpl(
            context = context,
            profileRepositoryLazy = lazyRepository,
            scope = scope,
            ioDispatcher = testDispatcher
        )

        // Set database ready. The first init attempt will fail with the exception.
        setDatabaseReady(true)
        // Wait until attempts is at least 1
        withTimeout(2000) {
            while (attempts < 1) {
                delay(10)
            }
        }
        delay(50) // small grace period for StateFlow propagation

        assertNull("Active profile should be null after failed initialization", manager.activeProfile.value)

        // Simulate database ready being fired again (retrying initialization)
        setDatabaseReady(false)
        delay(50)
        setDatabaseReady(true)
        // Wait until attempts is at least 2
        withTimeout(2000) {
            while (attempts < 2) {
                delay(10)
            }
        }
        // Wait for activeProfile to become non-null
        withTimeout(2000) {
            while (manager.activeProfile.value == null) {
                delay(10)
            }
        }

        // If compareAndSet blocks subsequent retries, the manager will remain uninitialized forever.
        // This test checks if it is able to recover.
        assertNotNull("Active profile should not be null if recovery works", manager.activeProfile.value)
    }

    // --- Fakes ---

    class FakeSharedPreferences : SharedPreferences {
        private val map = ConcurrentHashMap<String, Any>()
        var onApplyInterceptor: ((key: String, value: String?) -> Unit)? = null

        override fun getAll(): Map<String, *> = map

        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue

        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = map[key] as? Set<String> ?: defValues

        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(map, onApplyInterceptor)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(
            private val sharedMap: ConcurrentHashMap<String, Any>,
            private val onApplyInterceptor: ((key: String, value: String?) -> Unit)?
        ) : SharedPreferences.Editor {
            private val tempMap = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }

            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
                tempMap[key] = values
                return this
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }

            override fun remove(key: String): SharedPreferences.Editor {
                tempMap[key] = null
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) {
                    sharedMap.clear()
                }
                for ((key, value) in tempMap) {
                    if (value == null) {
                        sharedMap.remove(key)
                    } else {
                        sharedMap[key] = value
                        if (value is String?) {
                            onApplyInterceptor?.invoke(key, value)
                        }
                    }
                }
            }
        }
    }

    class FakeProfileRepository : ProfileRepository {
        val profiles = ConcurrentHashMap<String, ProfileEntity>()
        val insertCount = AtomicInteger(0)
        var getProfilesInterceptor: (() -> List<ProfileEntity>)? = null

        override fun observeProfiles(): kotlinx.coroutines.flow.Flow<List<ProfileEntity>> = kotlinx.coroutines.flow.flowOf(profiles.values.toList())

        override suspend fun getProfiles(): List<ProfileEntity> {
            val interceptor = getProfilesInterceptor
            if (interceptor != null) {
                return interceptor()
            }
            return profiles.values.toList()
        }

        override fun observeProfileById(id: String): kotlinx.coroutines.flow.Flow<ProfileEntity?> = kotlinx.coroutines.flow.flowOf(profiles[id])

        override suspend fun getProfileById(id: String): ProfileEntity? = profiles[id]

        override suspend fun insertProfile(profile: ProfileEntity): Long {
            insertCount.incrementAndGet()
            profiles[profile.id] = profile
            return 1L
        }

        override suspend fun updateProfile(profile: ProfileEntity): Int {
            profiles[profile.id] = profile
            return 1
        }

        override suspend fun deleteProfile(profile: ProfileEntity): Int {
            profiles.remove(profile.id)
            return 1
        }

        override suspend fun deleteProfileById(id: String): Int {
            profiles.remove(id)
            return 1
        }
    }
}
