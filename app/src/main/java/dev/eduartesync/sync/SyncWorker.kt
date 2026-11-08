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
import dev.eduartesync.source.AgendaFormatException
import dev.eduartesync.source.SignInPageException
import dev.eduartesync.subjects.SubjectStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
            // "Log in needed" is what the settings page looks for, see SyncStatus.isLoginExpired.
            report("Not synced: log in needed. ${Notifier.loginRequiredText(context, e.reason)}")
            return Result.success()
        } catch (e: CancellationException) {
            // WorkManager stopped the job (constraints, app update, user): not a failure to report.
            Notifier.clearApproveSignIn(context)
            throw e
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
            return failed(problem)
        }
        Notifier.clearApproveSignIn(context)

        val subjects = SubjectStore(context)
        subjects.addSeen(lessons.map { it.subject })
        // Changes are only reported for days the previous sync already covered.
        val reportUntil = settings.syncedUntil?.let { minOf(it, to) }
        val changes = try {
            CalendarWriter(context).sync(lessons, from, subjects.byName(), reportUntil, settings.roomInTitle, settings.shortNames)
        } catch (e: Exception) {
            // E.g. calendar access was taken away during the sync, or the calendar app's storage failed.
            Log.w(TAG, "Writing the calendar failed", e)
            return failed(SyncProblem.fromCalendar(e))
        }
        settings.syncedUntil = to

        Notifier.clearProblem(context)
        Notifier.scheduleChanged(context, changes)
        Log.i(TAG, "Synced ${lessons.size} lessons: ${summarize(changes)}")
        report("Synced ${lessons.size} lessons until ${to.dayOfMonth}/${to.monthValue}: ${summarize(changes)}")
        dev.eduartesync.widget.LessonWidget.refresh(context)
        return Result.success()
    }

    /** Reports a failed sync; the user sees [problem] (a toast or a notification), the settings page also the details. */
    private fun failed(problem: SyncProblem): Result {
        report("Not synced. ${problem.message}", problem.details)
        if (!manual) Notifier.syncFailed(applicationContext, problem)
        return Result.failure()
    }

    /**
     * Reads the schedule. A manual sync tries a second time with a fresh page when Eduarte was
     * just slow, or showed the agenda half drawn, so a single hiccup doesn't end in an error message.
     */
    private suspend fun fetch(from: LocalDate, to: LocalDate): List<Lesson> {
        val source = EduarteSource(applicationContext, interactive = manual, onApproveSignIn = ::approveSignIn)
        return try {
            source.fetch(from, to)
        } catch (e: Exception) {
            if (!manual || (e !is IOException && e !is AgendaFormatException)) throw e
            // Microsoft said "try again later": right away would only make that worse.
            if (e is SignInPageException) throw e
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

    /**
     * Saves the result for the settings page, and shows it as a toast for a manual sync, unless
     * it was started from inside the app, which shows the result itself. [details] (the
     * technical error) is only saved, on a second line in brackets, which the settings page shows.
     */
    private fun report(message: String, details: String? = null) {
        saveResult(if (details != null) "$message\n($details)" else message)
        if (manual && !inputData.getBoolean(KEY_QUIET, false)) toast(message)
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
        const val KEY_QUIET = "quiet"
        private val syncLock = Mutex()
        private const val TAG = "EduarteSync"
        private const val MAX_RETRIES = 3

        /**
         * "Clear calendar" from settings: deletes the app's calendar, waiting for a running sync
         * to finish first, so the sync can't write half of its events into a calendar that is
         * being removed (or recreate it right after). Returns whether there was anything to delete.
         */
        suspend fun clearCalendar(context: Context): Boolean = syncLock.withLock {
            withContext(Dispatchers.IO) {
                val removed = CalendarWriter(context).deleteCalendar() > 0
                AppSettings(context).syncedUntil = null
                removed
            }
        }
    }
}
