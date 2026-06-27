package com.example.calmsource.core.data.di

import com.example.calmsource.core.data.ProfileRepository
import com.example.calmsource.core.data.ProfileSessionManager
import com.example.calmsource.core.data.repository.ProfileRepositoryImpl
import com.example.calmsource.core.data.session.ProfileSessionManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Binds
    @Singleton
    fun bindProfileRepository(
        impl: ProfileRepositoryImpl
    ): ProfileRepository

    @Binds
    @Singleton
    fun bindProfileSessionManager(
        impl: ProfileSessionManagerImpl
    ): ProfileSessionManager
}
