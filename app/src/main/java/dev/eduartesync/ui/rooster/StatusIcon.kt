package dev.eduartesync.ui.rooster

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.eduartesync.Config
import dev.eduartesync.R
import dev.eduartesync.auth.SessionManager
import dev.eduartesync.settings.AppSettings
import dev.eduartesync.ui.StatusAction
import dev.eduartesync.ui.StatusSummary
import dev.eduartesync.ui.SyncState
import dev.eduartesync.ui.SyncStatus
import dev.eduartesync.ui.SyncWork
import dev.eduartesync.ui.stateColor
import java.time.LocalDate

/**
 * The sync status as a small icon in the Rooster tab's top bar: a check when up to date, a
 * spinner while syncing, a clock when it may be out of date, an alert when it failed or needs a
 * login. It takes no room of its own; tapping it shows the full status, like the settings page,
 * with what to do next.
 */
class StatusIcon(
    private val button: View,
    private val onSync: () -> Unit,
    private val onLogIn: () -> Unit,
    private val onSettings: () -> Unit,
) {
    private val context = button.context
    private val settings = AppSettings(context)
    private val icon = button.findViewById<ImageView>(R.id.syncIcon)
    private val progress = button.findViewById<View>(R.id.syncProgress)
    private var summary: StatusSummary? = null

    init {
        button.setOnClickListener { showDetails() }
    }

    fun show(work: SyncWork) {
        val now = System.currentTimeMillis()
        val summary = SyncStatus.summarize(
            loggedIn = SessionManager.isLoggedIn(context),
            lastSyncAt = settings.lastSyncAt,
            lastResult = settings.lastSyncResult,
            running = work.running,
            waiting = work.waiting,
            now = now,
        ).also { summary = it }

        val busy = summary.state == SyncState.SYNCING || summary.state == SyncState.WAITING
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        icon.visibility = if (busy) View.INVISIBLE else View.VISIBLE
        icon.setImageResource(
            when (summary.state) {
                SyncState.OK -> R.drawable.ic_check_circle
                SyncState.STALE -> R.drawable.ic_schedule
                SyncState.FAILED, SyncState.LOGIN_NEEDED -> R.drawable.ic_error
                SyncState.NEVER, SyncState.SYNCING, SyncState.WAITING -> R.drawable.ic_sync
            },
        )
        icon.imageTintList = ColorStateList.valueOf(context.getColor(stateColor(summary.state)))
        button.contentDescription = "Sync status: ${SyncStatus.compact(summary, settings.lastSyncAt, now)}. Tap for details"
    }

    /** Headline, explanation, which days are covered and the last result, with what to do next. */
    fun showDetails() {
        val summary = summary ?: return
        val today = LocalDate.now(Config.ZONE)
        val message = listOfNotNull(
            summary.subline,
            SyncStatus.coverage(settings.syncedUntil, settings.range.lastDay(today), today),
            settings.lastSyncResult?.takeIf { it.isNotBlank() },
        ).joinToString("\n\n")
        val logIn = summary.action == StatusAction.LOG_IN
        MaterialAlertDialogBuilder(context)
            .setTitle(summary.headline)
            .setMessage(message)
            .setPositiveButton(if (logIn) "Log in" else "Sync now") { _, _ -> if (logIn) onLogIn() else onSync() }
            .setNeutralButton("Settings") { _, _ -> onSettings() }
            .setNegativeButton("Close", null)
            .show()
    }
}
