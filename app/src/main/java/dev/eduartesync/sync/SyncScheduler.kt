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
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val PERIODIC_WORK = "periodic-sync"
    private const val MANUAL_WORK = "manual-sync"

    private val needsNetwork = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Background sync every 12 hours. KEEP: re-scheduling on every app open doesn't reset the timer. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(Config.PERIODIC_SYNC_HOURS, TimeUnit.HOURS)
            .setConstraints(needsNetwork)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
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
