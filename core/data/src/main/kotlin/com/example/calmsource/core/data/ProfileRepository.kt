package com.example.calmsource.core.data

import com.example.calmsource.core.database.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeProfiles(): Flow<List<ProfileEntity>>
    suspend fun getProfiles(): List<ProfileEntity>
    fun observeProfileById(id: String): Flow<ProfileEntity?>
    suspend fun getProfileById(id: String): ProfileEntity?
    suspend fun insertProfile(profile: ProfileEntity): Long
    suspend fun updateProfile(profile: ProfileEntity): Int
    suspend fun deleteProfile(profile: ProfileEntity): Int
    suspend fun deleteProfileById(id: String): Int
}
