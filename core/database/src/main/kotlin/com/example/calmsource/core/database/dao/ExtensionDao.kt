package com.example.calmsource.core.database.dao

import androidx.room.*
import com.example.calmsource.core.database.entity.ExtensionProviderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExtensionDao {
    @Query("SELECT * FROM extension_providers ORDER BY priority ASC")
    fun getAllExtensions(): Flow<List<ExtensionProviderEntity>>

    @Query("SELECT * FROM extension_providers WHERE id = :id")
    fun getExtensionById(id: String): Flow<ExtensionProviderEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertExtension(extension: ExtensionProviderEntity)

    @Update
    fun updateExtension(extension: ExtensionProviderEntity)

    @Delete
    fun deleteExtension(extension: ExtensionProviderEntity)
}
