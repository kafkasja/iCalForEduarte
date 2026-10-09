package dev.eduartesync.calendar

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import dev.eduartesync.Config
import dev.eduartesync.model.Lesson
import dev.eduartesync.subjects.SubjectStyle
import java.time.LocalDate

/**
 * Writes lessons into the app's own local calendar ("Eduarte rooster").
 * Only that calendar is ever read or changed.
 */
class CalendarWriter(context: Context) {
    private val resolver = context.contentResolver

    /**
     * Makes the calendar match [lessons] for everything starting on or after [from], using the
     * subject [styles] from the Subjects page. Returns what changed in the schedule up to
     * [reportUntil] in readable form; later days are new to the calendar, not changes.
     */
    fun sync(
        lessons: List<Lesson>,
        from: LocalDate,
        styles: Map<String, SubjectStyle>,
        reportUntil: LocalDate?,
    ): List<ScheduleChange> {
        val calendarId = findOrCreateCalendar()
        val fromMillis = from.atStartOfDay(Config.ZONE).toInstant().toEpochMilli()

        val existing = queryEvents(calendarId, fromMillis)
        val plan = planChanges(existing, wanted = lessons.associate { it.id to it.toFields(styles[it.subject]) })

        val ops = ArrayList<ContentProviderOperation>()
        plan.deletes.forEach { rowId ->
            ops += ContentProviderOperation.newDelete(eventUri(rowId)).build()
        }
        plan.updates.forEach { (rowId, fields) ->
            ops += ContentProviderOperation.newUpdate(eventUri(rowId))
                .withValues(fields.toValues().apply { put(Events.DIRTY, 0) })
                .build()
        }
        plan.inserts.forEach { (key, fields) ->
            ops += ContentProviderOperation.newInsert(asSyncAdapter(Events.CONTENT_URI))
                .withValues(fields.toValues().apply {
                    put(Events.CALENDAR_ID, calendarId)
                    put(Events.SYNC_DATA1, key)
                })
                .build()
        }
        if (ops.isNotEmpty()) resolver.applyBatch(CalendarContract.AUTHORITY, ops)

        val window = if (reportUntil == null) LongRange.EMPTY
        else fromMillis until reportUntil.plusDays(1).atStartOfDay(Config.ZONE).toInstant().toEpochMilli()
        return describeChanges(existing, plan, Config.ZONE, window)
    }

    /** Deletes the app's calendar and everything in it. The next sync creates it again. */
    fun deleteCalendar(): Int = resolver.delete(
        asSyncAdapter(Calendars.CONTENT_URI),
        "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?",
        arrayOf(Config.ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL),
    )

    /** Looked up by account each time (not stored), so "Clear storage" never duplicates it. */
    private fun findOrCreateCalendar(): Long {
        resolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID),
            "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(Config.ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL),
            null,
        )?.use { if (it.moveToFirst()) return it.getLong(0) }

        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, Config.ACCOUNT_NAME)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, Config.CALENDAR_NAME)
            put(Calendars.CALENDAR_DISPLAY_NAME, Config.CALENDAR_NAME)
            put(Calendars.CALENDAR_COLOR, Config.CALENDAR_COLOR)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, Config.ACCOUNT_NAME)
            put(Calendars.CALENDAR_TIME_ZONE, Config.ZONE.id)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
        }
        val uri = resolver.insert(asSyncAdapter(Calendars.CONTENT_URI), values)
            ?: error("Could not create calendar")
        return ContentUris.parseId(uri)
    }

    private fun queryEvents(calendarId: Long, fromMillis: Long): List<ExistingEvent> {
        val projection = arrayOf(
            Events._ID, Events.SYNC_DATA1, Events.TITLE, Events.EVENT_LOCATION,
            Events.DESCRIPTION, Events.DTSTART, Events.DTEND, Events.DELETED, Events.EVENT_COLOR,
        )
        val events = mutableListOf<ExistingEvent>()
        resolver.query(
            asSyncAdapter(Events.CONTENT_URI),
            projection,
            "${Events.CALENDAR_ID} = ? AND ${Events.DTSTART} >= ?",
            arrayOf(calendarId.toString(), fromMillis.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                events += ExistingEvent(
                    rowId = c.getLong(0),
                    key = c.getString(1),
                    fields = EventFields(
                        title = c.getString(2).orEmpty(),
                        location = c.getString(3).orEmpty(),
                        description = c.getString(4).orEmpty(),
                        startMillis = c.getLong(5),
                        endMillis = c.getLong(6),
                        color = if (c.isNull(8)) 0 else c.getInt(8),
                    ),
                    deleted = c.getInt(7) != 0,
                )
            }
        }
        return events
    }

    private fun EventFields.toValues() = ContentValues().apply {
        put(Events.TITLE, title)
        put(Events.EVENT_LOCATION, location)
        put(Events.DESCRIPTION, description)
        put(Events.DTSTART, startMillis)
        put(Events.DTEND, endMillis)
        put(Events.EVENT_TIMEZONE, Config.ZONE.id)
        put(Events.EVENT_COLOR, color)
    }

    private fun eventUri(rowId: Long) =
        asSyncAdapter(ContentUris.withAppendedId(Events.CONTENT_URI, rowId))

    /** Sync-adapter writes skip the "dirty"/soft-delete bookkeeping meant for user edits. */
    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, Config.ACCOUNT_NAME)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    companion object {
        fun hasPermission(context: Context): Boolean =
            listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all {
                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }
    }
}
