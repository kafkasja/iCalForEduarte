package dev.eduartesync.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.eduartesync.Config
import dev.eduartesync.auth.SessionManager
import dev.eduartesync.calendar.CalendarWriter
import dev.eduartesync.calendar.summarize
import dev.eduartesync.model.Lesson
import dev.eduartesync.source.EduarteSource
import dev.eduartesync.source.SessionExpiredException
import dev.eduartesync.settings.AppSettings
import dev.eduartesync.source.mergeConsecutive
import dev.eduartesync.subjects.SubjectStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.LocalDate

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

    /** Only used on Android 11 and older, where a manual (expedited) sync runs as a foreground job. */
    override suspend fun getForegroundInfo(): ForegroundInfo = Notifier.syncingForegroundInfo(applicationContext)

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

        // From today up to the range picked in settings; days that already passed are never touched.
        val settings = AppSettings(context)
        val from = LocalDate.now(Config.ZONE)
        val to = settings.range.lastDay(from)

        val lessons = try {
            mergeConsecutive(fetch(from, to))
        } catch (e: SessionExpiredException) {
            Notifier.clearApproveSignIn(context)
            Notifier.loginRequired(context, e.reason)
            report("Not synced: ${Notifier.loginRequiredText(context, e.reason)}")
            return Result.success()
        } catch (e: Throwable) {
            Notifier.clearApproveSignIn(context)
            Log.w(TAG, "Sync failed (attempt $runAttemptCount)", e)
            val problem = SyncProblem.from(e)
            // A background sync tries again a few times when the connection failed; a manual
            // sync already tried twice and the user sees the result right away.
            if (!manual && e is IOException && runAttemptCount < MAX_RETRIES) {
                saveResult("Not synced yet, trying again soon. ${problem.message}\n(${problem.details})")
                return Result.retry()
            }
            report("Not synced. ${problem.message}\n(${problem.details})")
            if (!manual) Notifier.syncFailed(context, problem)
            return Result.failure()
        }
        Notifier.clearApproveSignIn(context)

        val subjects = SubjectStore(context)
        subjects.addSeen(lessons.map { it.subject })
        // Changes are only reported for days the previous sync already covered.
        val reportUntil = settings.syncedUntil?.let { minOf(it, to) }
        val changes = CalendarWriter(context).sync(lessons, from, subjects.byName(), reportUntil, settings.roomInTitle)
        settings.syncedUntil = to

        Notifier.clearProblem(context)
        Notifier.scheduleChanged(context, changes)
        Log.i(TAG, "Synced ${lessons.size} lessons: ${summarize(changes)}")
        report("Synced ${lessons.size} lessons until ${to.dayOfMonth}/${to.monthValue}: ${summarize(changes)}")
        return Result.success()
    }

    /**
     * Reads the schedule. A manual sync tries a second time with a fresh page when Eduarte was
     * just slow, so a single hiccup doesn't end in an error message.
     */
    private suspend fun fetch(from: LocalDate, to: LocalDate): List<Lesson> {
        val source = EduarteSource(applicationContext, interactive = manual, onApproveSignIn = ::approveSignIn)
        return try {
            source.fetch(from, to)
        } catch (e: IOException) {
            if (!manual) throw e
            Log.w(TAG, "Sync attempt failed, trying once more", e)
            source.fetch(from, to)
        }
    }

    /** Microsoft wants the Authenticator during a sync the user started: tell them what to tap. */
    private fun approveSignIn(number: String?) {
        Notifier.approveSignIn(applicationContext, number)
        toast(
            if (number != null) "Open the Authenticator and tap $number to finish syncing"
            else "Approve the sign-in in the Authenticator to finish syncing"
        )
    }

    /** Saves the result for the settings page, and shows it as a toast for a manual sync. */
    private fun report(message: String) {
        saveResult(message)
        if (manual) toast(message)
    }

    private fun saveResult(message: String) {
        AppSettings(applicationContext).apply {
            lastSyncAt = System.currentTimeMillis()
            lastSyncResult = message
        }
    }

    private fun toast(message: String) {
        Handler(Looper.getMainLooper()).post {
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
