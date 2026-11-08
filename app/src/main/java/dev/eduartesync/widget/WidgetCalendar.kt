package dev.eduartesync.widget

import android.content.Context
import dev.eduartesync.Config
import dev.eduartesync.calendar.readInstances
import java.time.Instant

/**
 * Reads what the widget shows from the app's own calendar. Both the widget header and its rows
 * call this, so they always agree; the query is small (one week of one calendar).
 */
internal fun loadWidgetContent(context: Context, now: Long): WidgetContent {
    val lessons = readLessons(context, now)
    return if (lessons == null) WidgetContent.NeedsApp else chooseContent(lessons, now, Config.ZONE)
}

/**
 * Lessons for today and the next [WIDGET_DAYS_AHEAD] days, or null when there is no calendar
 * access or no calendar yet.
 */
private fun readLessons(context: Context, now: Long): List<WidgetLesson>? {
    val today = Instant.ofEpochMilli(now).atZone(Config.ZONE).toLocalDate()
    val begin = today.atStartOfDay(Config.ZONE).toInstant().toEpochMilli()
    val end = today.plusDays(WIDGET_DAYS_AHEAD + 1).atStartOfDay(Config.ZONE).toInstant().toEpochMilli()
    return readInstances(context, begin, end)?.map {
        WidgetLesson(it.eventId, it.title, it.location, it.beginMillis, it.endMillis, it.color)
    }
}
