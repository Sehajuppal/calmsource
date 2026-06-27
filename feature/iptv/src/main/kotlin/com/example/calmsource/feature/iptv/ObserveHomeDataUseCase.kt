package com.example.calmsource.feature.iptv

import com.example.calmsource.core.data.ProfileSessionManager
import com.example.calmsource.core.database.repository.UserMemoryRepository
import com.example.calmsource.feature.extensions.ExtensionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class ExtensionHomeKey(
    val id: String,
    val url: String,
    val isEnabled: Boolean,
    val manifestHash: Int
)

data class ChannelHomeKey(
    val id: String,
    val name: String,
    val group: String?,
    val logo: String?
)

class ObserveHomeDataUseCase @Inject constructor(
    private val memoryRepository: UserMemoryRepository,
    private val sessionManager: ProfileSessionManager
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun execute(): Flow<Unit> {
        return sessionManager.activeProfile
            .filterNotNull()
            .flatMapLatest { profile ->
                combine(
                    listOf(
                        ExtensionRepository.extensions
                            .map { providers ->
                                providers.map { provider ->
                                    ExtensionHomeKey(
                                        id = provider.id,
                                        url = provider.url,
                                        isEnabled = provider.isEnabled,
                                        manifestHash = provider.manifest?.hashCode() ?: 0
                                    )
                                }
                            }
                            .distinctUntilChanged()
                            .map { Unit },
                        IPTVRepository.channels
                            .map { channels ->
                                channels.map { channel ->
                                    ChannelHomeKey(
                                        id = channel.id,
                                        name = channel.name,
                                        group = channel.groupTitle,
                                        logo = channel.tvgLogo
                                    )
                                }
                            }
                            .distinctUntilChanged()
                            .map { Unit },
                        memoryRepository.observeContinueWatching(profile.id).map { Unit },
                        memoryRepository.observeWatchHistory(profile.id).map { Unit },
                        memoryRepository.observeFavorites(profile.id).map { Unit },
                        memoryRepository.observePreferenceSignals(profile.id).map { Unit }
                    )
                ) { Unit }
            }
            .flowOn(Dispatchers.Default)
    }
}
