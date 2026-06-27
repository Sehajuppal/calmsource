package com.example.calmsource.feature.iptv

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.calmsource.feature.iptv.xtream.XtreamApiClientImpl
import kotlinx.coroutines.CancellationException

/**
 * One-shot background worker for deferred Xtream short-EPG sync after catalog sync completes.
 */
class XtreamEpgSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val providerId = inputData.getString(KEY_PROVIDER_ID)?.trim().orEmpty()
        if (providerId.isEmpty()) {
            return Result.failure()
        }
        if (!XtreamRepository.shouldScheduleBackgroundEpgSync()) {
            return Result.success()
        }

        return try {
            android.util.Log.i(TAG, "Starting deferred Xtream EPG sync for provider $providerId")
            IptvProviderSyncCoordinator.withProviderLock(providerId) {
                XtreamRepository.syncProviderEpg(providerId, XtreamApiClientImpl())
            }
            android.util.Log.i(TAG, "Deferred Xtream EPG sync finished for provider $providerId")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val safeMsg = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(
                e.message ?: "Unknown error"
            )
            android.util.Log.e(TAG, "Deferred Xtream EPG sync failed for $providerId: $safeMsg", e)
            if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        const val KEY_PROVIDER_ID = "provider_id"
        private const val TAG = "XtreamEpgSyncWorker"
        private const val MAX_RETRY_ATTEMPTS = 2
    }
}
