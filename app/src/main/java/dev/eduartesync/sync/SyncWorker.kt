package dev.eduartesync.sync

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.eduartesync.Config
import dev.eduartesync.auth.SessionManager
import dev.eduartesync.calendar.CalendarWriter
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
            report("Eduarte Sync needs calendar permission")
            return Result.success()
        }

        // Today through Sunday of next week; days that already passed are never touched.
        val from = LocalDate.now(Config.ZONE)
        val to = from.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusWeeks(1)

        val lessons = try {
            mergeConsecutive(EduarteSource(context).fetch(from, to))
        } catch (e: SessionExpiredException) {
            Notifier.loginRequired(context)
            report("Eduarte login expired, tap the notification to log in again")
            return Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "Sync failed (attempt $runAttemptCount)", e)
            if (runAttemptCount < MAX_RETRIES) return Result.retry()
            Notifier.syncFailed(context, e.message)
            report("Sync failed: no connection to Eduarte")
            return Result.failure()
        } catch (e: Throwable) {
            Log.e(TAG, "Sync failed", e)
            val reason = describe(e)
            Notifier.syncFailed(context, reason)
            report("Sync failed: $reason")
            return Result.failure()
        }

        val plan = CalendarWriter(context).sync(lessons, from)
        Notifier.clear(context)
        Log.i(TAG, "Synced ${lessons.size} lessons: +${plan.inserts.size} ~${plan.updates.size} -${plan.deletes.size}")
        report("Synced ${lessons.size} lessons")
        return Result.success()
    }

    /** Error type and message including causes, so a screenshot of the toast is enough to debug. */
    private fun describe(e: Throwable): String =
        generateSequence(e) { it.cause }.take(3)
            .joinToString(" / caused by ") { "${it.javaClass.simpleName}: ${it.message}" }

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
