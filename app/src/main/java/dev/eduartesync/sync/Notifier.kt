package dev.eduartesync.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import dev.eduartesync.R
import dev.eduartesync.auth.LoginActivity
import dev.eduartesync.calendar.ScheduleChange
import dev.eduartesync.calendar.summarize
import dev.eduartesync.ui.MainActivity

/**
 * Two notification channels, each with its own switch in Android settings:
 * - "Sync problems": login expired or a sync failed. Always shown.
 * - "Schedule changes": what changed in the schedule. Can be muted with "Ignore".
 */
object Notifier {
    private const val CHANNEL_PROBLEMS = "sync"
    private const val CHANNEL_CHANGES = "changes"
    private const val ID_PROBLEM = 1
    private const val ID_CHANGES = 2
    private const val MAX_LINES = 6

    fun loginRequired(context: Context) = show(
        context, CHANNEL_PROBLEMS, ID_PROBLEM,
        title = "Log in to Eduarte again",
        text = "Your Eduarte login has expired. Tap to log in, your schedule syncs right after.",
        intent = Intent(context, LoginActivity::class.java),
    )

    fun syncFailed(context: Context, problem: SyncProblem) = show(
        context, CHANNEL_PROBLEMS, ID_PROBLEM,
        title = "Schedule not synced",
        text = "${problem.message}\n\nDetails: ${problem.details}",
        intent = Intent(context, MainActivity::class.java),
    )

    fun clearProblem(context: Context) =
        context.getSystemService(NotificationManager::class.java).cancel(ID_PROBLEM)

    /** Lists what changed. Nothing is shown when nothing changed or the user muted these. */
    fun scheduleChanged(context: Context, changes: List<ScheduleChange>) {
        if (changes.isEmpty()) return
        val lines = changes.take(MAX_LINES).map { it.text } +
            listOfNotNull(if (changes.size > MAX_LINES) "and ${changes.size - MAX_LINES} more" else null)

        val ignore = PendingIntent.getBroadcast(
            context, 0, Intent(context, IgnoreChangesReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        show(
            context, CHANNEL_CHANGES, ID_CHANGES,
            title = "Schedule changed: ${summarize(changes)}",
            text = lines.joinToString("\n"),
            intent = Intent(context, MainActivity::class.java),
        ) { it.addAction(0, "Ignore", ignore) }
    }

    /**
     * "Ignore": turns the "Schedule changes" channel off, the same switch as in Android
     * settings > Apps > Eduarte Sync > Notifications, where it can be turned back on.
     * Returns false if Android didn't allow it, which happens when that switch was changed
     * by hand before.
     */
    fun muteChanges(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(ID_CHANGES)
        manager.createNotificationChannel(changesChannel(NotificationManager.IMPORTANCE_NONE))
        return manager.getNotificationChannel(CHANNEL_CHANGES)?.importance == NotificationManager.IMPORTANCE_NONE
    }

    /** Creating an existing channel never raises its importance, so a muted channel stays muted. */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PROBLEMS, "Sync problems", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Login expired or the schedule could not be synced"
            }
        )
        manager.createNotificationChannel(changesChannel(NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** Whether schedule change notifications can show up at all. */
    fun changesEnabled(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_CHANGES)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Android's own settings screen for the "Schedule changes" notifications. */
    fun changesSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_CHANGES)

    private fun changesChannel(importance: Int) =
        NotificationChannel(CHANNEL_CHANGES, "Schedule changes", importance).apply {
            description = "What changed in your schedule after a sync"
        }

    private fun show(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        text: String,
        intent: Intent,
        extra: (NotificationCompat.Builder) -> Unit = {},
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        ensureChannels(context)

        val pending = PendingIntent.getActivity(
            context, id, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
        extra(builder)
        manager.notify(id, builder.build())
    }
}
