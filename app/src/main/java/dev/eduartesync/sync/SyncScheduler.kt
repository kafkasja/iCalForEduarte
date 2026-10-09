package dev.eduartesync.sync

import android.content.Context
import androidx.work.Constraints
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
    private const val PERIODIC_WORK = "periodic-sync"
    private const val MANUAL_WORK = "manual-sync"

    private val needsNetwork = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Turns the background sync (every 12 hours) on or off to match the setting.
     * KEEP: calling this on every app open doesn't reset the timer.
     */
    fun updatePeriodic(context: Context) {
        if (!AppSettings(context).autoSync) return cancelPeriodic(context)
        val request = PeriodicWorkRequestBuilder<SyncWorker>(Config.PERIODIC_SYNC_HOURS, TimeUnit.HOURS)
            .setConstraints(needsNetwork)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }

    /** Sync now (app icon tapped). KEEP: tapping twice doesn't start two syncs. */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(needsNetwork)
            .setInputData(workDataOf(SyncWorker.KEY_MANUAL to true))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(MANUAL_WORK, ExistingWorkPolicy.KEEP, request)
    }
}
