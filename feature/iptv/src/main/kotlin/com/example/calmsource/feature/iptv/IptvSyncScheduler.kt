package com.example.calmsource.feature.iptv

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object IptvSyncScheduler {
    private const val UNIQUE_WORK_NAME = "iptv_epg_background_sync"
    private const val XTREAM_EPG_WORK_PREFIX = "xtream_epg_sync_"

    fun schedulePeriodicSync(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<IptvSyncWorker>(
                24, TimeUnit.HOURS,
                1, TimeUnit.HOURS
            )
            .setConstraints(constraints)
            .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            android.util.Log.i("IptvSyncScheduler", "Periodic background sync worker scheduled successfully.")
        } catch (e: Exception) {
            android.util.Log.e("IptvSyncScheduler", "Failed to schedule background sync worker: ${e.message}", e)
        }
    }

    /** Schedules a deferred Xtream short-EPG sync after catalog content is browse-ready. */
    fun scheduleXtreamEpgSync(context: Context, providerId: String) {
        if (!XtreamRepository.shouldScheduleBackgroundEpgSync()) return
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<XtreamEpgSyncWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(XtreamEpgSyncWorker.KEY_PROVIDER_ID to providerId))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "$XTREAM_EPG_WORK_PREFIX$providerId",
                ExistingWorkPolicy.REPLACE,
                request
            )
            android.util.Log.i("IptvSyncScheduler", "Deferred Xtream EPG sync scheduled for $providerId")
        } catch (e: Exception) {
            android.util.Log.e("IptvSyncScheduler", "Failed to schedule Xtream EPG sync: ${e.message}", e)
        }
    }
}
