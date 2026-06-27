package com.example.calmsource.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.calmsource.core.database.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY name ASC")
    fun observeProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY name ASC")
    suspend fun getProfiles(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    fun observeProfileById(id: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: ProfileEntity): Int

    @Delete
    suspend fun deleteProfile(profile: ProfileEntity): Int

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteProfileById(id: String): Int
}
