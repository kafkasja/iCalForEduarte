package dev.eduartesync.ui.rooster

import android.content.Context
import dev.eduartesync.Config
import dev.eduartesync.calendar.CalendarWriter
import dev.eduartesync.calendar.readInstances
import dev.eduartesync.settings.AppSettings
import java.time.LocalDate

/** How many weeks back the Rooster tab goes; the calendar keeps past days. */
private const val WEEKS_BACK = 8L

/** How many weeks ahead: the longest sync range is 8 weeks, plus the rest of that week. */
private const val WEEKS_AHEAD = 8L

/** Everything the Rooster tab shows, read from the calendar in one go. */
class ScheduleData(
    /** One page per day, see [pageDays]. */
    val days: List<LocalDate>,
    private val lessons: Map<LocalDate, List<ScheduleLesson>>,
    val access: Boolean,
    val syncedUntil: LocalDate?,
) {
    fun lessonsOn(date: LocalDate): List<ScheduleLesson> = lessons[date].orEmpty()

    /** Page of [date], or of the first day after it with a page (a Saturday shows Monday). */
    fun indexOf(date: LocalDate): Int =
        days.indexOfFirst { !it.isBefore(date) }.takeIf { it >= 0 } ?: days.lastIndex
}

/** Reads the calendar; does disk work, so call it off the main thread. */
fun loadSchedule(context: Context, today: LocalDate): ScheduleData {
    val first = mondayOf(today).minusWeeks(WEEKS_BACK)
    val last = mondayOf(today).plusWeeks(WEEKS_AHEAD).plusDays(6)
    val begin = first.atStartOfDay(Config.ZONE).toInstant().toEpochMilli()
    val end = last.plusDays(1).atStartOfDay(Config.ZONE).toInstant().toEpochMilli()
    val lessons = readInstances(context, begin, end).orEmpty()
        .map { it.toScheduleLesson() }
        .groupBy { dateOf(it.startMillis, Config.ZONE) }
    return ScheduleData(
        days = pageDays(first, last, lessons.keys),
        lessons = lessons,
        access = CalendarWriter.hasPermission(context),
        syncedUntil = AppSettings(context).syncedUntil,
    )
}
