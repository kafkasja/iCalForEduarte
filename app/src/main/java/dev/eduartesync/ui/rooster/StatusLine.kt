package dev.eduartesync.ui.rooster

import android.content.res.ColorStateList
import android.view.View
import android.widget.Button
import android.widget.TextView
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
 * The compact status line at the top of the Rooster tab ("Up to date · 3 min ago"), with a
 * button when something needs doing. Tapping it shows the full status, like the settings page.
 */
class StatusLine(
    private val card: View,
    private val onSync: () -> Unit,
    private val onLogIn: () -> Unit,
    private val onSettings: () -> Unit,
) {
    private val context = card.context
    private val settings = AppSettings(context)
    private var summary: StatusSummary? = null

    init {
        card.setOnClickListener { showDetails() }
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

        card.findViewById<TextView>(R.id.statusText).text = SyncStatus.compact(summary, settings.lastSyncAt, now)
        val busy = summary.state == SyncState.SYNCING || summary.state == SyncState.WAITING
        card.findViewById<View>(R.id.statusProgress).visibility = if (busy) View.VISIBLE else View.GONE
        card.findViewById<View>(R.id.statusDot).apply {
            visibility = if (busy) View.INVISIBLE else View.VISIBLE
            backgroundTintList = ColorStateList.valueOf(context.getColor(stateColor(summary.state)))
        }

        val action = when {
            summary.action == StatusAction.LOG_IN -> "Log in"
            summary.state == SyncState.FAILED -> "Retry"
            summary.state == SyncState.NEVER || summary.state == SyncState.STALE -> "Sync"
            else -> null
        }
        card.findViewById<Button>(R.id.statusAction).apply {
            visibility = if (action == null) View.GONE else View.VISIBLE
            text = action
            setOnClickListener { if (summary.action == StatusAction.LOG_IN) onLogIn() else onSync() }
        }
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
