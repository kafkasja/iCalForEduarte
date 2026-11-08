package dev.eduartesync.widget

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dev.eduartesync.Config
import dev.eduartesync.R

/** Hands the widget's list its rows, so a long day scrolls instead of being cut off. */
class LessonRowsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = LessonRows(applicationContext)
}

private class LessonRows(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var day: WidgetContent.Day? = null

    override fun onCreate() {}

    /** Runs off the main thread, so reading the calendar here is fine. */
    override fun onDataSetChanged() {
        day = loadWidgetContent(context, System.currentTimeMillis()) as? WidgetContent.Day
    }

    override fun onDestroy() {
        day = null
    }

    override fun getCount() = day?.lessons?.size ?: 0

    override fun getViewAt(position: Int): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_lesson_row)
        val day = day ?: return row
        val lesson = day.lessons.getOrNull(position) ?: return row
        val now = position == day.nowIndex

        row.setTextViewText(R.id.title, lesson.title)
        row.setTextViewText(R.id.detail, lessonDetail(lesson, Config.ZONE))
        row.setInt(R.id.bar, "setColorFilter", lesson.color)
        row.setViewVisibility(R.id.now_tag, if (now) View.VISIBLE else View.GONE)
        row.setInt(R.id.row, "setBackgroundResource", if (now) R.drawable.widget_row_now else 0)
        row.setOnClickFillInIntent(
            R.id.row,
            Intent()
                .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, lesson.eventId))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, lesson.startMillis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, lesson.endMillis),
        )
        return row
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount() = 1

    // Event id alone isn't unique for repeating events; mixing in the start time makes it so.
    override fun getItemId(position: Int): Long =
        day?.lessons?.getOrNull(position)?.let { it.eventId * 31 + it.startMillis } ?: position.toLong()

    override fun hasStableIds() = true
}
