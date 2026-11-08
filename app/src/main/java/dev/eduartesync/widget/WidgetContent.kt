package dev.eduartesync.widget

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One lesson as the widget shows it, read from the calendar. */
data class WidgetLesson(
    val eventId: Long,
    val title: String,
    val room: String,
    val startMillis: Long,
    val endMillis: Long,
    val color: Int,
)

/** What the widget shows. Kept free of Android classes so it can be tested on its own. */
sealed interface WidgetContent {
    /** No calendar access, or the app's calendar doesn't exist yet: the app has to be opened. */
    data object NeedsApp : WidgetContent

    /** Nothing in the coming days (holiday, or not synced far enough). */
    data object NothingComingUp : WidgetContent

    /** The lessons of one [date]; [nowIndex] is the lesson in progress, or -1. */
    data class Day(val date: LocalDate, val lessons: List<WidgetLesson>, val nowIndex: Int) : WidgetContent
}

/** How many days ahead the widget looks for the next school day. */
const val WIDGET_DAYS_AHEAD = 7L

/**
 * Picks the day to show: today's lessons that haven't ended yet, or else the first later day with
 * lessons within [WIDGET_DAYS_AHEAD] days. Lessons that already ended are left out, so the
 * lesson in progress (if any) is always the first row.
 */
fun chooseContent(lessons: List<WidgetLesson>, now: Long, zone: ZoneId): WidgetContent {
    val today = dateOf(now, zone)
    val windowEnd = today.plusDays(WIDGET_DAYS_AHEAD + 1).atStartOfDay(zone).toInstant().toEpochMilli()
    val upcoming = lessons
        .filter { it.endMillis > now && it.startMillis < windowEnd }
        .sortedWith(compareBy({ it.startMillis }, { it.endMillis }))
    val first = upcoming.firstOrNull() ?: return WidgetContent.NothingComingUp

    // A lesson in progress belongs to today, even if it somehow started before midnight.
    val day = if (first.startMillis <= now) today else dateOf(first.startMillis, zone)
    val shown = upcoming.filter { dateOf(maxOf(it.startMillis, now), zone) == day }
    val nowIndex = shown.indexOfFirst { it.startMillis <= now && now < it.endMillis }
    return WidgetContent.Day(day, shown, nowIndex)
}

/**
 * When the widget should next redraw by itself: the next start or end of a shown lesson (so the
 * "now" highlight moves along), or midnight at the latest (so "Tomorrow" becomes "Today").
 */
fun nextRefreshAt(content: WidgetContent, now: Long, zone: ZoneId): Long {
    val midnight = dateOf(now, zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val lessons = (content as? WidgetContent.Day)?.lessons.orEmpty()
    return lessons.flatMap { listOf(it.startMillis, it.endMillis) }
        .filter { it > now }
        .fold(midnight, ::minOf)
}

/** Header for [date]: [todayText], [tomorrowText] (from strings.xml) or e.g. "Monday 10 Nov". */
fun dayHeader(date: LocalDate, today: LocalDate, todayText: String, tomorrowText: String): String = when (date) {
    today -> todayText
    today.plusDays(1) -> tomorrowText
    else -> date.format(DAY_FORMAT)
}

/** "14:05" for a sync today, "7 Nov" for an older one, null if it never synced. */
fun syncedWhen(lastSyncAt: Long, now: Long, zone: ZoneId): String? {
    if (lastSyncAt <= 0) return null
    val at = Instant.ofEpochMilli(lastSyncAt).atZone(zone)
    return if (at.toLocalDate() == dateOf(now, zone)) at.format(TIME_FORMAT) else at.format(SHORT_DATE_FORMAT)
}

/** "08:30 – 10:00", plus " · B1.12" when there is a room. */
fun lessonDetail(lesson: WidgetLesson, zone: ZoneId): String {
    val time = "${timeOf(lesson.startMillis, zone)} – ${timeOf(lesson.endMillis, zone)}"
    return if (lesson.room.isBlank()) time else "$time · ${lesson.room.trim()}"
}

private fun dateOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
private fun timeOf(millis: Long, zone: ZoneId): String = Instant.ofEpochMilli(millis).atZone(zone).format(TIME_FORMAT)

// English on purpose: all text in the app is English.
private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMM", Locale.ENGLISH)
private val SHORT_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
