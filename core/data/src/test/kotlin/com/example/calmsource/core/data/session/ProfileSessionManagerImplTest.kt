package com.example.calmsource.core.data.session

import android.content.Context
import android.content.SharedPreferences
import com.example.calmsource.core.data.ProfileRepository
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.entity.ProfileEntity
import dagger.Lazy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ProfileSessionManagerImplTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var profileRepository: ProfileRepository
    private lateinit var manager: ProfileSessionManagerImpl

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private var repositoryResolved = false
    private val sharedPrefsMap = mutableMapOf<String, String?>()

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        prefs = mock(SharedPreferences::class.java)
        editor = mock(SharedPreferences.Editor::class.java)
        profileRepository = mock(ProfileRepository::class.java)

        `when`(context.getSharedPreferences(eq("profile_session_prefs"), anyInt())).thenReturn(prefs)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putString(eq("active_profile_id"), anyString())).thenAnswer { invocation ->
            val value = invocation.arguments[1] as String
            sharedPrefsMap["active_profile_id"] = value
            editor
        }
        `when`(prefs.getString(eq("active_profile_id"), any())).thenAnswer { invocation ->
            val defaultVal = invocation.arguments[1] as? String
            sharedPrefsMap["active_profile_id"] ?: defaultVal
        }

        val packageManager = mock(android.content.pm.PackageManager::class.java)
        `when`(context.packageManager).thenReturn(packageManager)
        `when`(packageManager.hasSystemFeature(eq(android.content.pm.PackageManager.FEATURE_LEANBACK))).thenReturn(false)

        // Set databaseReady to false initially using reflection
        setDatabaseReady(false)

        val lazyRepository = Lazy {
            repositoryResolved = true
            profileRepository
        }

        manager = ProfileSessionManagerImpl(
            context = context,
            profileRepositoryLazy = lazyRepository,
            scope = testScope,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        sharedPrefsMap.clear()
        DatabaseProvider.resetForTesting()
    }

    private fun setDatabaseReady(ready: Boolean) {
        val field = DatabaseProvider::class.java.getDeclaredField("_databaseReady")
        field.isAccessible = true
        val mutableFlow = field.get(DatabaseProvider) as MutableStateFlow<Boolean>
        mutableFlow.value = ready
    }

    @Test
    fun testLazinessIsPreserved() = runTest(testDispatcher) {
        // Initially repository is not resolved
        assertFalse(repositoryResolved)
        assertNull(manager.activeProfile.value)

        // Even after databaseReady changes to true, until coroutine runs, it is not resolved
        setDatabaseReady(true)
        assertFalse(repositoryResolved)

        // Advance dispatcher to run initialization
        `when`(profileRepository.getProfiles()).thenReturn(emptyList())
        testScheduler.advanceUntilIdle()

        assertTrue(repositoryResolved)
    }

    @Test
    fun testInit_onEmptyDatabase_generatesDefaultProfile() = runTest(testDispatcher) {
        `when`(profileRepository.getProfiles()).thenReturn(emptyList())
        
        setDatabaseReady(true)
        testScheduler.advanceUntilIdle()

        // Verify that insert was called or default profile was created
        // We check if activeProfile is updated to default
        val active = manager.activeProfile.value
        assertEquals("default", active?.id)
        assertEquals("Main Profile", active?.name)
        assertEquals("default", sharedPrefsMap["active_profile_id"])
    }

    @Test
    fun testInit_onNonEmptyDatabase_setsSavedActiveProfile() = runTest(testDispatcher) {
        val profiles = listOf(
            ProfileEntity("default", "Main Profile"),
            ProfileEntity("profile2", "Second Profile")
        )
        `when`(profileRepository.getProfiles()).thenReturn(profiles)
        `when`(profileRepository.getProfileById("profile2")).thenReturn(profiles[1])

        sharedPrefsMap["active_profile_id"] = "profile2"

        setDatabaseReady(true)
        testScheduler.advanceUntilIdle()

        val active = manager.activeProfile.value
        assertEquals("profile2", active?.id)
        assertEquals("Second Profile", active?.name)
    }

    @Test
    fun testInit_onNonEmptyDatabase_noSavedProfile_fallsBackToFirst() = runTest(testDispatcher) {
        val profiles = listOf(
            ProfileEntity("profile2", "Second Profile"),
            ProfileEntity("default", "Main Profile")
        )
        `when`(profileRepository.getProfiles()).thenReturn(profiles)

        setDatabaseReady(true)
        testScheduler.advanceUntilIdle()

        val active = manager.activeProfile.value
        assertEquals("profile2", active?.id)
        assertEquals("Second Profile", active?.name)
        assertEquals("profile2", sharedPrefsMap["active_profile_id"])
    }

    @Test
    fun testSelectProfile_persistsAndUpdatesStateFlow() = runTest(testDispatcher) {
        val profile = ProfileEntity("profile3", "Third Profile")
        `when`(profileRepository.getProfileById("profile3")).thenReturn(profile)

        manager.selectProfile("profile3")
        testScheduler.advanceUntilIdle()

        assertEquals(profile, manager.activeProfile.value)
        assertEquals("profile3", sharedPrefsMap["active_profile_id"])
    }

    @Test
    fun testInit_onTV_noSavedProfile_remainsNull() = runTest(testDispatcher) {
        val packageManager = mock(android.content.pm.PackageManager::class.java)
        `when`(context.packageManager).thenReturn(packageManager)
        `when`(packageManager.hasSystemFeature(eq(android.content.pm.PackageManager.FEATURE_LEANBACK))).thenReturn(true)

        val profiles = listOf(
            ProfileEntity("default", "Main Profile"),
            ProfileEntity("profile2", "Second Profile")
        )
        `when`(profileRepository.getProfiles()).thenReturn(profiles)

        setDatabaseReady(true)
        testScheduler.advanceUntilIdle()

        assertNull(manager.activeProfile.value)
    }
}
