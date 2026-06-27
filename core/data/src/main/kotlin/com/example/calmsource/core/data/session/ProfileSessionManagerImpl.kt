package com.example.calmsource.core.data.session

import android.content.Context
import com.example.calmsource.core.data.ProfileRepository
import com.example.calmsource.core.data.ProfileSessionManager
import com.example.calmsource.core.data.di.ApplicationScope
import com.example.calmsource.core.data.di.IoDispatcher
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.entity.ProfileEntity
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileSessionManagerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val profileRepositoryLazy: Lazy<ProfileRepository>,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ProfileSessionManager {

    private val _activeProfile = MutableStateFlow<ProfileEntity?>(null)
    override val activeProfile: StateFlow<ProfileEntity?> = _activeProfile.asStateFlow()

    private val isInitialized = AtomicBoolean(false)
    private val mutex = Mutex()

    init {
        scope.launch {
            DatabaseProvider.databaseReady.collect { ready ->
                if (ready) {
                    try {
                        initializeProfileSession()
                    } catch (e: Throwable) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        // Catch exception to keep flow collection alive
                    }
                }
            }
        }
    }

    private suspend fun initializeProfileSession() = mutex.withLock {
        if (!isInitialized.compareAndSet(false, true)) return@withLock
        try {
            withContext(ioDispatcher) {
                val repository = profileRepositoryLazy.get()
                var profiles = repository.getProfiles()
                if (profiles.isEmpty()) {
                    val defaultProfile = ProfileEntity(id = "default", name = "Main Profile")
                    repository.insertProfile(defaultProfile)
                    profiles = listOf(defaultProfile)
                }

                val prefs = context.getSharedPreferences("profile_session_prefs", Context.MODE_PRIVATE)
                val activeId = prefs.getString("active_profile_id", null)

                val isTv = try {
                    context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
                } catch (e: Exception) {
                    false
                }

                val active = activeId?.let { repository.getProfileById(it) } ?: if (!isTv) profiles.firstOrNull() else null
                if (active != null) {
                    if (activeId != active.id) {
                        prefs.edit().putString("active_profile_id", active.id).apply()
                    }
                    _activeProfile.value = active
                }
            }
        } catch (t: Throwable) {
            isInitialized.set(false)
            throw t
        }
    }

    override suspend fun selectProfile(profileId: String) = mutex.withLock {
        withContext(ioDispatcher) {
            val repository = profileRepositoryLazy.get()
            val profile = repository.getProfileById(profileId)
            if (profile != null) {
                val prefs = context.getSharedPreferences("profile_session_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("active_profile_id", profileId).apply()
                _activeProfile.value = profile
            }
        }
    }
}
