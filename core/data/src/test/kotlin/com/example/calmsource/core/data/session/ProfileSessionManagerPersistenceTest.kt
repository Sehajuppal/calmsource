package com.example.calmsource.core.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.example.calmsource.core.data.ProfileRepository
import com.example.calmsource.core.data.ProfileSessionManager
import com.example.calmsource.core.data.repository.ProfileRepositoryImpl
import com.example.calmsource.core.data.repository.ProfileRepositoryImpl_Factory
import com.example.calmsource.core.data.session.ProfileSessionManagerImpl
import com.example.calmsource.core.data.session.ProfileSessionManagerImpl_Factory
import com.example.calmsource.core.database.CalmSourceDatabase
import com.example.calmsource.core.database.DatabaseModule_ProvideCalmSourceDatabaseFactory
import com.example.calmsource.core.database.DatabaseModule_ProvideProfileDaoFactory
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.dao.ProfileDao
import com.example.calmsource.core.database.entity.ProfileEntity
import dagger.Lazy
import dagger.internal.DoubleCheck
import dagger.internal.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ProfileSessionManagerPersistenceTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val sharedPrefsMap = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        sharedPrefsMap.clear()
        DatabaseProvider.resetForTesting()
        setDatabaseReady(false)
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

    private fun createMockContext(): Context {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)

        `when`(context.applicationContext).thenReturn(context)
        `when`(context.getSharedPreferences(eq("profile_session_prefs"), anyInt())).thenReturn(prefs)
        `when`(prefs.edit()).thenReturn(editor)

        `when`(editor.putString(eq("active_profile_id"), anyString())).thenAnswer { invocation ->
            val value = invocation.arguments[1] as String
            sharedPrefsMap["active_profile_id"] = value
            editor
        }
        `when`(editor.apply()).thenAnswer { }
        `when`(prefs.getString(eq("active_profile_id"), any())).thenAnswer { invocation ->
            val defaultVal = invocation.arguments[1] as? String
            sharedPrefsMap["active_profile_id"] ?: defaultVal
        }

        return context
    }

    private fun setDatabaseMockInstance(mockDb: CalmSourceDatabase) {
        val dbField = DatabaseProvider::class.java.declaredFields.firstOrNull {
            CalmSourceDatabase::class.java.isAssignableFrom(it.type)
        }
        dbField?.isAccessible = true
        dbField?.set(null, mockDb)
    }

    @Test
    fun testSharedPreferencesPersistenceAcrossInstantiations() = runTest(testDispatcher) {
        // Prepare context and sharedPrefsMap
        val context1 = createMockContext()

        // Prepare repository with two profiles
        val profiles = mutableListOf(
            ProfileEntity("profile_1", "Profile One"),
            ProfileEntity("profile_2", "Profile Two")
        )
        val profileRepository = mock(ProfileRepository::class.java)
        `when`(profileRepository.getProfiles()).thenReturn(profiles)
        `when`(profileRepository.getProfileById("profile_1")).thenReturn(profiles[0])
        `when`(profileRepository.getProfileById("profile_2")).thenReturn(profiles[1])

        val lazyRepository1: Lazy<ProfileRepository> = Lazy { profileRepository }

        // Instantiate manager 1
        val manager1 = ProfileSessionManagerImpl(
            context = context1,
            profileRepositoryLazy = lazyRepository1,
            scope = testScope,
            ioDispatcher = testDispatcher
        )

        // Ready database and let it initialize
        setDatabaseReady(true)
        testScheduler.advanceUntilIdle()

        // By default, since no profile is saved, it falls back to the first profile
        assertEquals("profile_1", manager1.activeProfile.value?.id)
        assertEquals("profile_1", sharedPrefsMap["active_profile_id"])

        // Select second profile
        manager1.selectProfile("profile_2")
        testScheduler.advanceUntilIdle()

        assertEquals("profile_2", manager1.activeProfile.value?.id)
        assertEquals("profile_2", sharedPrefsMap["active_profile_id"])

        // Now, destroy manager 1 and instantiate manager 2
        // We use a new mock context that shares the SAME underlying sharedPrefsMap
        val context2 = createMockContext()
        val lazyRepository2: Lazy<ProfileRepository> = Lazy { profileRepository }

        // We reset database ready flow status and the manager state
        setDatabaseReady(false)
        val manager2 = ProfileSessionManagerImpl(
            context = context2,
            profileRepositoryLazy = lazyRepository2,
            scope = testScope,
            ioDispatcher = testDispatcher
        )

        // Set database ready for manager 2
        setDatabaseReady(true)
        testScheduler.advanceUntilIdle()

        // Manager 2 should read from the shared preferences map and initialize with profile_2 immediately!
        assertEquals("profile_2", manager2.activeProfile.value?.id)
    }

    @Test
    fun testDatabaseLazinessOnHiltModuleInstantiation() = runTest(testDispatcher) {
        // Reset and clear any database provider state
        DatabaseProvider.resetForTesting()
        assertNull(DatabaseProvider.databaseOrNull())
        assertFalse(DatabaseProvider.isInitialized())

        // Set up context provider
        val mockContext = createMockContext()
        val contextProvider = Provider { mockContext }

        // Set up providers imitating Hilt modules:
        // DatabaseModule.provideCalmSourceDatabase(context) -> CalmSourceDatabase
        val databaseProvider = DoubleCheck.provider<CalmSourceDatabase>(
            DatabaseModule_ProvideCalmSourceDatabaseFactory.create(contextProvider)
        )

        // DatabaseModule.provideProfileDao(database) -> ProfileDao
        val profileDaoProvider = DoubleCheck.provider<ProfileDao>(
            DatabaseModule_ProvideProfileDaoFactory.create(databaseProvider)
        )

        // DataModule.bindProfileRepository(ProfileRepositoryImpl) -> ProfileRepository
        @Suppress("UNCHECKED_CAST")
        val profileRepositoryProvider = DoubleCheck.provider<ProfileRepositoryImpl>(
            ProfileRepositoryImpl_Factory.create(profileDaoProvider)
        ) as Provider<ProfileRepository>

        // Scope and dispatcher providers
        val scopeProvider = Provider<CoroutineScope> { testScope }
        val dispatcherProvider = Provider<CoroutineDispatcher> { testDispatcher }

        // DataModule.bindProfileSessionManager(ProfileSessionManagerImpl) -> ProfileSessionManager
        @Suppress("UNCHECKED_CAST")
        val sessionManagerProvider = DoubleCheck.provider<ProfileSessionManagerImpl>(
            ProfileSessionManagerImpl_Factory.create(
                contextProvider,
                profileRepositoryProvider,
                scopeProvider,
                dispatcherProvider
            )
        ) as Provider<ProfileSessionManager>

        // 1. Verify that building the Dagger Providers does NOT trigger DatabaseProvider initialization or open
        assertNull("DatabaseProvider database must not be opened yet", DatabaseProvider.databaseOrNull())
        assertFalse("DatabaseProvider must not be initialized yet", DatabaseProvider.isInitialized())

        // 2. Inject (get) the ProfileRepository and ProfileSessionManager
        val repository = profileRepositoryProvider.get()
        val sessionManager = sessionManagerProvider.get()

        // Verify that after injection/construction, the database is STILL not opened or initialized
        assertNull("DatabaseProvider database must not be opened after injection", DatabaseProvider.databaseOrNull())
        assertFalse("DatabaseProvider must not be initialized after injection", DatabaseProvider.isInitialized())

        // 3. Now verify that executing a query DOES trigger database open/initialization
        // Let's set a mock CalmSourceDatabase in DatabaseProvider using reflection so that when it gets resolved,
        // it doesn't crash on Room builder.
        val mockDb = mock(CalmSourceDatabase::class.java)
        val mockDao = mock(ProfileDao::class.java)
        val mockOpenHelper = mock(SupportSQLiteOpenHelper::class.java)
        val mockSqlDb = mock(SupportSQLiteDatabase::class.java)

        `when`(mockDb.openHelper).thenReturn(mockOpenHelper)
        `when`(mockOpenHelper.writableDatabase).thenReturn(mockSqlDb)
        `when`(mockDb.profileDao()).thenReturn(mockDao)
        `when`(mockDao.getProfiles()).thenReturn(emptyList())

        setDatabaseMockInstance(mockDb)

        // Execute query on the repository
        repository.getProfiles()

        // Now, DatabaseProvider MUST be initialized
        assertTrue("DatabaseProvider must be initialized after query execution", DatabaseProvider.isInitialized())
        assertNotNull("DatabaseProvider database must be non-null after query execution", DatabaseProvider.databaseOrNull())
        assertEquals(mockDb, DatabaseProvider.databaseOrNull())
    }
}
