package com.example.calmsource.core.database.dao

import androidx.room.*
import com.example.calmsource.core.database.entity.UserPreferencesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferencesDao {
    @Query("SELECT * FROM user_preferences WHERE id = 1")
    fun getPreferences(): Flow<UserPreferencesEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPreferences(preferences: UserPreferencesEntity): Long

    @Update
    fun updatePreferences(preferences: UserPreferencesEntity): Int
}
