package dev.eduartesync.sync

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.eduartesync.Config
import dev.eduartesync.auth.SessionManager
import dev.eduartesync.calendar.CalendarWriter
import dev.eduartesync.calendar.summarize
import dev.eduartesync.source.EduarteSource
import dev.eduartesync.source.SessionExpiredException
import dev.eduartesync.source.mergeConsecutive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * One sync: read the schedule from Eduarte, update the calendar, tell the user what happened.
 * A sync started by tapping the app icon reports back with a toast; background syncs only
 * notify about schedule changes and problems.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val manual get() = inputData.getBoolean(KEY_MANUAL, false)

    // The periodic and the manual sync are separate jobs. Running them at the same time could
    // insert the same lesson twice, so they take turns.
    override suspend fun doWork(): Result = syncLock.withLock { sync() }

    private suspend fun sync(): Result {
        val context = applicationContext
        if (!SessionManager.isLoggedIn(context)) {
            Notifier.loginRequired(context)
            return Result.success()
        }
        if (!CalendarWriter.hasPermission(context)) {
            report("Not synced: Eduarte Sync has no access to your calendar. Open the app to allow it.")
            return Result.success()
        }

        // Today through Sunday of next week; days that already passed are never touched.
        val from = LocalDate.now(Config.ZONE)
        val to = from.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusWeeks(1)

        val lessons = try {
            mergeConsecutive(EduarteSource(context).fetch(from, to))
        } catch (e: SessionExpiredException) {
            Notifier.loginRequired(context)
            report("Not synced: your Eduarte login has expired. Tap the notification to log in again.")
            return Result.success()
        } catch (e: Throwable) {
            Log.w(TAG, "Sync failed (attempt $runAttemptCount)", e)
            val problem = SyncProblem.from(e)
            // Connection problems are often temporary: try again later, but say so right away.
            val retry = e is IOException && runAttemptCount < MAX_RETRIES
            if (retry && runAttemptCount > 0) return Result.retry()
            report(
                "Not synced. ${problem.message}" +
                    (if (retry) " Trying again in the background." else "") +
                    "\n(${problem.details})"
            )
            if (retry) return Result.retry()
            Notifier.syncFailed(context, problem)
            return Result.failure()
        }

        val changes = CalendarWriter(context).sync(lessons, from)
        Notifier.clearProblem(context)
        Notifier.scheduleChanged(context, changes)
        Log.i(TAG, "Synced ${lessons.size} lessons: ${summarize(changes)}")
        report("Synced ${lessons.size} lessons until ${to.dayOfMonth}/${to.monthValue}: ${summarize(changes)}")
        return Result.success()
    }

    /** Toast only for syncs started by tapping the app icon; background syncs stay silent. */
    private suspend fun report(message: String) {
        if (!manual) return
        withContext(Dispatchers.Main) {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val KEY_MANUAL = "manual"
        private val syncLock = Mutex()
        private const val TAG = "EduarteSync"
        private const val MAX_RETRIES = 3
    }
}
