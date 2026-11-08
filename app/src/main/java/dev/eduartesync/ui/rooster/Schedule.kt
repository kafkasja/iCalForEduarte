package dev.eduartesync.ui.rooster

import dev.eduartesync.Config
import dev.eduartesync.calendar.CalendarInstance
import dev.eduartesync.calendar.readEventText
import dev.eduartesync.grades.formatDutch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** One lesson as the Rooster tab shows it. Free of Android classes, so it can be tested. */
data class ScheduleLesson(
    val eventId: Long,
    val name: String,
    val room: String,
    val teacher: String?,
    val group: String?,
    val startMillis: Long,
    val endMillis: Long,
    val color: Int,
    val cancelled: Boolean,
)

fun CalendarInstance.toScheduleLesson(): ScheduleLesson {
    val text = readEventText(title, location, description)
    return ScheduleLesson(eventId, text.name, location, text.teacher, text.group, beginMillis, endMillis, color, text.cancelled)
}

/** A row in a day's list. */
sealed interface DayItem {
    /** [now] when the lesson is going on right now. */
    data class Lesson(val lesson: ScheduleLesson, val now: Boolean) : DayItem

    /** Time without lessons between two lessons; [now] when it is that time right now. */
    data class Free(val startMillis: Long, val endMillis: Long, val now: Boolean) : DayItem {
        val minutes get() = (endMillis - startMillis) / 60_000
    }

    /** "Now" marker before the first or after the last lesson of today. */
    data class Now(val millis: Long) : DayItem
}

/** Breaks shorter than this (changing rooms) don't get a "Free" row. */
private const val MIN_FREE_MS = 5 * 60_000L

/**
 * The rows for one day: lessons in order with "Free" rows between them, and where "now" is.
 * [now] is null for any day other than today.
 */
fun dayItems(lessons: List<ScheduleLesson>, now: Long?): List<DayItem> {
    if (lessons.isEmpty()) return emptyList()
    val sorted = lessons.sortedWith(compareBy({ it.startMillis }, { it.endMillis }))
    val items = mutableListOf<DayItem>()
    var busyUntil = Long.MIN_VALUE // end of the lessons so far; lessons can overlap
    for (lesson in sorted) {
        if (busyUntil != Long.MIN_VALUE && lesson.startMillis - busyUntil >= MIN_FREE_MS) {
            items += DayItem.Free(busyUntil, lesson.startMillis, now != null && now >= busyUntil && now < lesson.startMillis)
        }
        items += DayItem.Lesson(lesson, now != null && now >= lesson.startMillis && now < lesson.endMillis)
        busyUntil = maxOf(busyUntil, lesson.endMillis)
    }
    if (now != null) {
        if (now < sorted.first().startMillis) items.add(0, DayItem.Now(now))
        if (now >= busyUntil) items += DayItem.Now(now)
    }
    return items
}

/** Why a day shows no lessons, which decides the message on it. */
enum class EmptyDay { NO_ACCESS, NOT_SYNCED_YET, NOT_SYNCED_THIS_FAR, NO_LESSONS }

/**
 * Null when the day has lessons to show. Days after [syncedUntil] (the last day the previous
 * sync covered) have no data, which is different from a day without lessons.
 */
fun emptyDay(date: LocalDate, hasLessons: Boolean, access: Boolean, syncedUntil: LocalDate?): EmptyDay? = when {
    !access -> EmptyDay.NO_ACCESS
    hasLessons -> null
    syncedUntil == null -> EmptyDay.NOT_SYNCED_YET
    date.isAfter(syncedUntil) -> EmptyDay.NOT_SYNCED_THIS_FAR
    else -> EmptyDay.NO_LESSONS
}

/** The days that get a page: every Monday to Friday, Saturday and Sunday only when they have lessons. */
fun pageDays(first: LocalDate, last: LocalDate, daysWithLessons: Set<LocalDate>): List<LocalDate> =
    generateSequence(first) { it.plusDays(1) }
        .takeWhile { !it.isAfter(last) }
        .filter { !isWeekend(it) || it in daysWithLessons }
        .toList()

fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun dateOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/**
 * The one line above a day's lessons: "Tomorrow · 3 lessons · 13:15 – 16:30", or the weekday
 * ("Wednesday · ...") for days further away. The date itself is in the week strip right above.
 */
fun dayHeader(date: LocalDate, today: LocalDate, lessons: List<ScheduleLesson>): String {
    val day = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    }
    if (lessons.isEmpty()) return day
    val cancelled = lessons.count { it.cancelled }
    val count = (if (lessons.size == 1) "1 lesson" else "${lessons.size} lessons") +
        (if (cancelled > 0) " ($cancelled cancelled)" else "")
    val hours = "${clock(lessons.minOf { it.startMillis })} – ${clock(lessons.maxOf { it.endMillis })}"
    return "$day · $count · $hours"
}

/** Minutes of lesson time in [lessons], leaving out cancelled ones. */
fun lessonMinutes(lessons: List<ScheduleLesson>): Long =
    lessons.filter { !it.cancelled }.sumOf { (it.endMillis - it.startMillis) / 60_000 }

/** "Week 46 · 13 h", "Week 46 · 12,5 h", or just "Week 46" for a week without lessons. */
fun weekTitle(monday: LocalDate, minutes: Long): String {
    val number = monday.get(WeekFields.ISO.weekOfWeekBasedYear())
    if (minutes <= 0) return "Week $number"
    val hours = formatDutch(minutes / 60.0).removeSuffix(",0")
    return "Week $number · $hours h"
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/** "08:30" in the school's time zone. */
internal fun clock(millis: Long): String = Instant.ofEpochMilli(millis).atZone(Config.ZONE).format(timeFormat)

/** "45 min", "1 h", "1 h 30 min". */
fun formatDuration(minutes: Long): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0L -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

private fun isWeekend(date: LocalDate) = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
