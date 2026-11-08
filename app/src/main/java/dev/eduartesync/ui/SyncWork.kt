package dev.eduartesync.ui

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.eduartesync.sync.SyncScheduler

/** What WorkManager says about the manual and the background sync right now. */
data class SyncWork(
    /** A sync is running. */
    val running: Boolean,
    /** A manual sync is queued but hasn't started, usually because there is no internet. */
    val waiting: Boolean,
    /** When the next background sync is planned, or null when unknown or off. */
    val nextPeriodicAt: Long?,
) {
    /** A manual sync is queued or running. */
    val manualActive get() = running || waiting
}

/**
 * Calls [onChange] whenever either sync job changes state, while [owner] is started. Shared by
 * the settings page and the Rooster tab so both describe the same thing.
 */
fun observeSyncWork(context: Context, owner: LifecycleOwner, onChange: (SyncWork) -> Unit) {
    var manual: List<WorkInfo> = emptyList()
    var periodic: List<WorkInfo> = emptyList()
    fun publish() {
        val activeManual = manual.firstOrNull { !it.state.isFinished }
        val activePeriodic = periodic.firstOrNull { !it.state.isFinished }
        val running = activeManual?.state == WorkInfo.State.RUNNING || activePeriodic?.state == WorkInfo.State.RUNNING
        val next = activePeriodic
            ?.takeIf { it.state == WorkInfo.State.ENQUEUED && it.nextScheduleTimeMillis != Long.MAX_VALUE }
            ?.nextScheduleTimeMillis
        onChange(SyncWork(running, waiting = activeManual != null && !running, nextPeriodicAt = next))
    }
    val workManager = WorkManager.getInstance(context)
    workManager.getWorkInfosForUniqueWorkLiveData(SyncScheduler.MANUAL_WORK).observe(owner) { manual = it; publish() }
    workManager.getWorkInfosForUniqueWorkLiveData(SyncScheduler.PERIODIC_WORK).observe(owner) { periodic = it; publish() }
}
