package com.example.calmsource.feature.iptv

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.mapper.toDomain
import com.example.calmsource.core.model.IPTVProviderType
import com.example.calmsource.core.model.ProviderSyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class IptvSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        android.util.Log.i("IptvSyncWorker", "Starting background IPTV and EPG synchronization...")
        
        val db = try {
            DatabaseProvider.getDatabase(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("IptvSyncWorker", "Database not initialized: ${e.message}")
            return retryOrFail()
        }
        val dao = db.iptvDao()
        
        try {
            // 1. Sync Playlists (M3U & Xtream)
            var hadFailure = false
            val providers = dao.getAllProviders().first().map { it.toDomain() }
            for (provider in providers) {
                if (isStopped) throw CancellationException("Worker stopped")
                if (!provider.isEnabled) continue
                android.util.Log.i("IptvSyncWorker", "Syncing provider: ${provider.name} (${provider.type})")
                try {
                    when (provider.type) {
                        IPTVProviderType.M3U -> {
                            IPTVRepository.syncPlaylistFromUrl(provider.id)
                        }
                        IPTVProviderType.XTREAM -> {
                            IPTVRepository.syncXtreamProvider(provider.id)
                        }
                    }
                    val state = IPTVRepository.syncStates.value[provider.id]
                    if (state?.status == ProviderSyncStatus.ERROR) {
                        hadFailure = true
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    hadFailure = true
                    val safeMsg = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(e.message ?: "Unknown error")
                    android.util.Log.e("IptvSyncWorker", "Failed to sync provider ${provider.name}: $safeMsg")
                }
            }

            // 2. Sync EPG Sources
            val epgSources = dao.getAllEPGSources().first().map { it.toDomain() }
            for (source in epgSources) {
                if (isStopped) throw CancellationException("Worker stopped")
                val provider = providers.find { it.id == source.providerId }
                if (provider == null || !provider.isEnabled) continue
                android.util.Log.i("IptvSyncWorker", "Syncing EPG source: ${source.name}")
                try {
                    IPTVRepository.syncEpgFromUrl(source.id)
                    val state = IPTVRepository.syncStates.value[source.providerId]
                    if (state?.status == ProviderSyncStatus.ERROR) {
                        hadFailure = true
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    hadFailure = true
                    val safeMsg = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(e.message ?: "Unknown error")
                    android.util.Log.e("IptvSyncWorker", "Failed to sync EPG source ${source.name}: $safeMsg")
                }
            }
            
            android.util.Log.i("IptvSyncWorker", "Background synchronization complete.")
            return if (hadFailure) retryOrFail() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val safeMsg = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(e.message ?: "Unknown error")
            android.util.Log.e("IptvSyncWorker", "WorkManager sync failed: $safeMsg", e)
            return retryOrFail()
        }
    }

    private fun retryOrFail(): Result {
        return if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            Result.failure()
        } else {
            Result.retry()
        }
    }

    private companion object {
        const val MAX_RETRY_ATTEMPTS = 3
    }
}
