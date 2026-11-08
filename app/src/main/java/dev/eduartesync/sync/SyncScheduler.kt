package dev.eduartesync.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.OutOfQuotaPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.eduartesync.Config
import dev.eduartesync.settings.AppSettings
import java.util.concurrent.TimeUnit

object SyncScheduler {
    const val PERIODIC_WORK = "periodic-sync"
    const val MANUAL_WORK = "manual-sync"

    private val needsNetwork = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Turns the background sync (every 12 hours) on or off to match the setting.
     * UPDATE: calling this on every app open doesn't reset the timer.
     */
    fun updatePeriodic(context: Context) {
        if (!AppSettings(context).autoSync) return cancelPeriodic(context)
        val request = PeriodicWorkRequestBuilder<SyncWorker>(Config.PERIODIC_SYNC_HOURS, TimeUnit.HOURS)
            .setConstraints(needsNetwork)
            // When Eduarte didn't load, try again after a few minutes instead of waiting hours.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }

    /**
     * Sync now (app opened, pulled to refresh, widget button). KEEP: asking twice doesn't start
     * two syncs. [quiet]: the app is open and shows the result itself, so no toast.
     */
    fun syncNow(context: Context, quiet: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(needsNetwork)
            // Start right away, also when Android is saving battery for background apps.
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(SyncWorker.KEY_MANUAL to true, SyncWorker.KEY_QUIET to quiet))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(MANUAL_WORK, ExistingWorkPolicy.KEEP, request)
    }
}
