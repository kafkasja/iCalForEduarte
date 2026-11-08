package dev.eduartesync.widget

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract.Instances
import dev.eduartesync.Config
import dev.eduartesync.calendar.CalendarWriter
import java.time.Instant

/**
 * Reads what the widget shows from the app's own calendar. Both the widget header and its rows
 * call this, so they always agree; the query is small (one week of one calendar).
 */
internal fun loadWidgetContent(context: Context, now: Long): WidgetContent {
    val lessons = try {
        readLessons(context, now)
    } catch (e: SecurityException) {
        null // calendar access was taken away between the check and the query
    }
    return if (lessons == null) WidgetContent.NeedsApp else chooseContent(lessons, now, Config.ZONE)
}

/**
 * Lessons for today and the next [WIDGET_DAYS_AHEAD] days, or null when there is no calendar
 * access or no calendar yet. Instances (not Events) so the calendar provider does the time range.
 */
private fun readLessons(context: Context, now: Long): List<WidgetLesson>? {
    if (!CalendarWriter.hasPermission(context)) return null
    val calendarId = CalendarWriter(context).findCalendarId() ?: return null
    val today = Instant.ofEpochMilli(now).atZone(Config.ZONE).toLocalDate()
    val begin = today.atStartOfDay(Config.ZONE).toInstant().toEpochMilli()
    val end = today.plusDays(WIDGET_DAYS_AHEAD + 1).atStartOfDay(Config.ZONE).toInstant().toEpochMilli()
    val uri = Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, begin)
        ContentUris.appendId(it, end)
    }.build()

    val lessons = mutableListOf<WidgetLesson>()
    context.contentResolver.query(
        uri,
        arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.EVENT_LOCATION, Instances.BEGIN, Instances.END, Instances.DISPLAY_COLOR),
        "${Instances.CALENDAR_ID} = ?",
        arrayOf(calendarId.toString()),
        "${Instances.BEGIN} ASC",
    )?.use { c ->
        while (c.moveToNext()) {
            val color = if (c.isNull(5) || c.getInt(5) == 0) Config.CALENDAR_COLOR else c.getInt(5)
            lessons += WidgetLesson(
                eventId = c.getLong(0),
                title = c.getString(1).orEmpty(),
                room = c.getString(2).orEmpty(),
                startMillis = c.getLong(3),
                endMillis = c.getLong(4),
                color = color or 0xFF000000.toInt(),
            )
        }
    }
    return lessons
}
