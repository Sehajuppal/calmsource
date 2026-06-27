package com.example.calmsource.core.data.repository

import com.example.calmsource.core.data.ProfileRepository
import com.example.calmsource.core.database.dao.ProfileDao
import com.example.calmsource.core.database.entity.ProfileEntity
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ProfileRepositoryImpl @Inject constructor(
    private val profileDaoLazy: Lazy<ProfileDao>
) : ProfileRepository {

    private val dao: ProfileDao
        get() = profileDaoLazy.get()

    override fun observeProfiles(): Flow<List<ProfileEntity>> {
        return dao.observeProfiles()
    }

    override suspend fun getProfiles(): List<ProfileEntity> {
        return dao.getProfiles()
    }

    override fun observeProfileById(id: String): Flow<ProfileEntity?> {
        return dao.observeProfileById(id)
    }

    override suspend fun getProfileById(id: String): ProfileEntity? {
        return dao.getProfileById(id)
    }

    override suspend fun insertProfile(profile: ProfileEntity): Long {
        return dao.insertProfile(profile)
    }

    override suspend fun updateProfile(profile: ProfileEntity): Int {
        return dao.updateProfile(profile)
    }

    override suspend fun deleteProfile(profile: ProfileEntity): Int {
        return dao.deleteProfile(profile)
    }

    override suspend fun deleteProfileById(id: String): Int {
        return dao.deleteProfileById(id)
    }
}
