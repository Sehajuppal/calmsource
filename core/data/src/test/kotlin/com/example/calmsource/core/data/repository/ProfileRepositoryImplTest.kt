package com.example.calmsource.core.data.repository

import com.example.calmsource.core.database.dao.ProfileDao
import com.example.calmsource.core.database.entity.ProfileEntity
import dagger.Lazy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class ProfileRepositoryImplTest {

    private lateinit var profileDao: ProfileDao
    private lateinit var repository: ProfileRepositoryImpl
    private var lazyResolved = false

    @Before
    fun setUp() {
        profileDao = mock(ProfileDao::class.java)
        val lazyDao = Lazy {
            lazyResolved = true
            profileDao
        }
        repository = ProfileRepositoryImpl(lazyDao)
    }

    @Test
    fun testLazinessIsPreserved() {
        // Assert that the DAO is not resolved on initialization
        assertEquals(false, lazyResolved)
        
        // Assert that resolving triggers it
        repository.observeProfiles()
        assertEquals(true, lazyResolved)
    }

    @Test
    fun testObserveProfiles() = runTest {
        val list = listOf(ProfileEntity(id = "1", name = "Test"))
        `when`(profileDao.observeProfiles()).thenReturn(flowOf(list))

        repository.observeProfiles().collect {
            assertEquals(list, it)
        }
        verify(profileDao).observeProfiles()
    }

    @Test
    fun testGetProfiles() = runTest {
        val list = listOf(ProfileEntity(id = "1", name = "Test"))
        `when`(profileDao.getProfiles()).thenReturn(list)

        val result = repository.getProfiles()
        assertEquals(list, result)
        verify(profileDao).getProfiles()
    }

    @Test
    fun testObserveProfileById() = runTest {
        val profile = ProfileEntity(id = "1", name = "Test")
        `when`(profileDao.observeProfileById("1")).thenReturn(flowOf(profile))

        repository.observeProfileById("1").collect {
            assertEquals(profile, it)
        }
        verify(profileDao).observeProfileById("1")
    }

    @Test
    fun testGetProfileById() = runTest {
        val profile = ProfileEntity(id = "1", name = "Test")
        `when`(profileDao.getProfileById("1")).thenReturn(profile)

        val result = repository.getProfileById("1")
        assertEquals(profile, result)
        verify(profileDao).getProfileById("1")
    }

    @Test
    fun testInsertProfile() = runTest {
        val profile = ProfileEntity(id = "1", name = "Test")
        `when`(profileDao.insertProfile(profile)).thenReturn(1L)

        val result = repository.insertProfile(profile)
        assertEquals(1L, result)
        verify(profileDao).insertProfile(profile)
    }

    @Test
    fun testUpdateProfile() = runTest {
        val profile = ProfileEntity(id = "1", name = "Test")
        `when`(profileDao.updateProfile(profile)).thenReturn(1)

        val result = repository.updateProfile(profile)
        assertEquals(1, result)
        verify(profileDao).updateProfile(profile)
    }

    @Test
    fun testDeleteProfile() = runTest {
        val profile = ProfileEntity(id = "1", name = "Test")
        `when`(profileDao.deleteProfile(profile)).thenReturn(1)

        val result = repository.deleteProfile(profile)
        assertEquals(1, result)
        verify(profileDao).deleteProfile(profile)
    }

    @Test
    fun testDeleteProfileById() = runTest {
        `when`(profileDao.deleteProfileById("1")).thenReturn(1)

        val result = repository.deleteProfileById("1")
        assertEquals(1, result)
        verify(profileDao).deleteProfileById("1")
    }
}
