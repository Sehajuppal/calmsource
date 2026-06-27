package com.example.calmsource.core.database.dao

import androidx.room.*
import com.example.calmsource.core.database.entity.DebridAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DebridDao {
    @Query("SELECT * FROM debrid_accounts")
    fun getAllAccounts(): Flow<List<DebridAccountEntity>>

    @Query("SELECT * FROM debrid_accounts WHERE id = :id")
    fun getAccountById(id: String): Flow<DebridAccountEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAccount(account: DebridAccountEntity)

    @Update
    fun updateAccount(account: DebridAccountEntity)

    @Delete
    fun deleteAccount(account: DebridAccountEntity)
}
