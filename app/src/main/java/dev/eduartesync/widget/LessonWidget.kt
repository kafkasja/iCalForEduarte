package dev.eduartesync.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import dev.eduartesync.Config
import dev.eduartesync.R
import dev.eduartesync.settings.AppSettings
import dev.eduartesync.sync.SyncScheduler
import dev.eduartesync.ui.MainActivity
import java.time.Instant

/**
 * Home screen widget with the upcoming lessons from the "Eduarte rooster" calendar.
 * Tapping a lesson opens it in the calendar app; tapping the header syncs.
 * The rows come from [LessonRowsService]; this class draws the frame around them.
 */
class LessonWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, manager, ids)
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(tickIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SYNC -> {
                SyncScheduler.syncNow(context)
                Toast.makeText(context, R.string.widget_syncing, Toast.LENGTH_SHORT).show()
            }
            ACTION_TICK -> refresh(context)
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        private const val TAG = "EduarteSync"
        private const val ACTION_SYNC = "dev.eduartesync.widget.SYNC"
        private const val ACTION_TICK = "dev.eduartesync.widget.TICK"

        /**
         * Redraws every widget on the home screen. Safe to call when there is none. Never throws,
         * so a widget problem can't break a sync.
         */
        fun refresh(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(context, LessonWidget::class.java))
                if (ids.isNotEmpty()) update(context, manager, ids)
            } catch (e: Exception) {
                Log.w(TAG, "Widget refresh failed", e)
            }
        }

        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val now = System.currentTimeMillis()
            val content = loadWidgetContent(context, now)
            val views = frame(context, content, now)
            manager.updateAppWidget(ids, views)
            // The list keeps its old rows until it is told to ask the service again.
            manager.notifyAppWidgetViewDataChanged(ids, R.id.list)
            scheduleNext(context, nextRefreshAt(content, now, Config.ZONE))
        }

        /** Header, empty message, click handlers and the link to the rows. */
        private fun frame(context: Context, content: WidgetContent, now: Long): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_lessons)
            val today = Instant.ofEpochMilli(now).atZone(Config.ZONE).toLocalDate()
            val header = when (content) {
                is WidgetContent.Day -> dayHeader(
                    content.date, today,
                    context.getString(R.string.widget_today), context.getString(R.string.widget_tomorrow),
                )
                else -> context.getString(R.string.widget_title)
            }
            views.setTextViewText(R.id.day, header)

            val synced = syncedWhen(AppSettings(context).lastSyncAt, now, Config.ZONE)
            views.setTextViewText(
                R.id.synced,
                if (synced == null) context.getString(R.string.widget_not_synced)
                else context.getString(R.string.widget_synced, synced),
            )

            views.setTextViewText(
                R.id.empty,
                context.getString(
                    if (content is WidgetContent.NeedsApp) R.string.widget_open_app else R.string.widget_no_lessons
                ),
            )
            @Suppress("DEPRECATION") // the replacement (RemoteCollectionItems) needs Android 12
            views.setRemoteAdapter(R.id.list, Intent(context, LessonRowsService::class.java))
            views.setEmptyView(R.id.list, R.id.empty)

            // Without calendar access or a calendar the app itself has to ask or log in, so open it.
            val headerClick = if (content is WidgetContent.NeedsApp) openAppIntent(context) else syncIntent(context)
            views.setOnClickPendingIntent(R.id.header, headerClick)
            views.setOnClickPendingIntent(R.id.empty, headerClick)
            views.setPendingIntentTemplate(R.id.list, openLessonTemplate(context))
            return views
        }

        /**
         * Inexact, non-waking alarm at the next lesson start/end (or midnight): the highlight moves
         * along while the phone is in use, and a sleeping phone isn't woken up just for this.
         * updatePeriodMillis (30 min) in the widget info is the safety net.
         */
        private fun scheduleNext(context: Context, at: Long) {
            context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, at, tickIntent(context))
        }

        private fun tickIntent(context: Context) = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, LessonWidget::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun syncIntent(context: Context) = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, LessonWidget::class.java).setAction(ACTION_SYNC),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun openAppIntent(context: Context) = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /**
         * Rows fill in the event; the template must be mutable for that. It points at our own
         * [OpenLessonActivity] because Android 14+ refuses mutable PendingIntents without a target.
         */
        private fun openLessonTemplate(context: Context) = PendingIntent.getActivity(
            context, 3,
            Intent(context, OpenLessonActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }
}
