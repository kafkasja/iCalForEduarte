package dev.eduartesync.calendar

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract.Instances
import dev.eduartesync.Config

/** One event of the app's calendar in a time range, as the calendar provider has it. */
data class CalendarInstance(
    val eventId: Long,
    val title: String,
    val location: String,
    val description: String,
    val beginMillis: Long,
    val endMillis: Long,
    /** Opaque ARGB; the calendar color when the event has none. */
    val color: Int,
)

/**
 * Events of the "Eduarte rooster" calendar overlapping [begin] until [end] (epoch millis), sorted
 * by start. Null when there is no calendar access or no calendar yet. Instances (not Events), so
 * the calendar provider does the time range. Used by the widget and the Rooster tab.
 */
fun readInstances(context: Context, begin: Long, end: Long): List<CalendarInstance>? {
    if (!CalendarWriter.hasPermission(context)) return null
    return try {
        val calendarId = CalendarWriter(context).findCalendarId() ?: return null
        query(context, calendarId, begin, end)
    } catch (e: SecurityException) {
        null // calendar access was taken away between the check and the query
    }
}

private fun query(context: Context, calendarId: Long, begin: Long, end: Long): List<CalendarInstance> {
    val uri = Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, begin)
        ContentUris.appendId(it, end)
    }.build()
    val projection = arrayOf(
        Instances.EVENT_ID, Instances.TITLE, Instances.EVENT_LOCATION, Instances.DESCRIPTION,
        Instances.BEGIN, Instances.END, Instances.DISPLAY_COLOR,
    )
    val result = mutableListOf<CalendarInstance>()
    context.contentResolver.query(
        uri, projection, "${Instances.CALENDAR_ID} = ?", arrayOf(calendarId.toString()), "${Instances.BEGIN} ASC",
    )?.use { c ->
        while (c.moveToNext()) {
            val color = if (c.isNull(6) || c.getInt(6) == 0) Config.CALENDAR_COLOR else c.getInt(6)
            result += CalendarInstance(
                eventId = c.getLong(0),
                title = c.getString(1).orEmpty(),
                location = c.getString(2).orEmpty(),
                description = c.getString(3).orEmpty(),
                beginMillis = c.getLong(4),
                endMillis = c.getLong(5),
                color = color or 0xFF000000.toInt(),
            )
        }
    }
    return result
}
