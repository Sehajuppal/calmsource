package com.example.calmsource.core.database

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.example.calmsource.core.database.dao.DebridDao
import com.example.calmsource.core.database.dao.ExtensionDao
import com.example.calmsource.core.database.dao.HealthDao
import com.example.calmsource.core.database.dao.IPTVDao
import com.example.calmsource.core.database.dao.PreferencesDao
import com.example.calmsource.core.database.dao.UserMemoryDao
import com.example.calmsource.core.database.dao.XtreamDao
import com.example.calmsource.core.database.dao.UserTelemetryDao
import com.example.calmsource.core.database.dao.ProfileDao
import com.example.calmsource.core.database.repository.RoomUserMemoryRepository
import com.example.calmsource.core.database.repository.UserMemoryRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideCalmSourceDatabase(@ApplicationContext context: Context): CalmSourceDatabase {
        DatabaseProvider.init(context)
        val db = DatabaseProvider.getDatabase(context)
        // Eagerly trigger database open and migrations so Hilt and ExtensionRepository share one DB.
        db.openHelper.writableDatabase
        return db
    }

    @Provides
    fun provideProfileDao(database: CalmSourceDatabase): ProfileDao {
        return database.profileDao()
    }

    @Provides
    fun provideUserMemoryDao(database: CalmSourceDatabase): UserMemoryDao {
        return database.userMemoryDao()
    }

    @Provides
    fun provideIptvDao(database: CalmSourceDatabase): IPTVDao {
        return database.iptvDao()
    }

    @Provides
    fun provideExtensionDao(database: CalmSourceDatabase): ExtensionDao {
        return database.extensionDao()
    }

    @Provides
    fun provideDebridDao(database: CalmSourceDatabase): DebridDao {
        return database.debridDao()
    }

    @Provides
    fun providePreferencesDao(database: CalmSourceDatabase): PreferencesDao {
        return database.preferencesDao()
    }

    @Provides
    fun provideHealthDao(database: CalmSourceDatabase): HealthDao {
        return database.healthDao()
    }

    @Provides
    fun provideXtreamDao(database: CalmSourceDatabase): XtreamDao {
        return database.xtreamDao()
    }

    @Provides
    fun provideUserTelemetryDao(database: CalmSourceDatabase): UserTelemetryDao {
        return database.userTelemetryDao()
    }

    @Provides
    @Singleton
    fun provideUserMemoryRepository(database: CalmSourceDatabase): UserMemoryRepository {
        return RoomUserMemoryRepository(database)
    }
}
