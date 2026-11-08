package dev.eduartesync.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WidgetContentTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val friday = LocalDate.of(2026, 11, 6)
    private val monday = LocalDate.of(2026, 11, 9)

    private fun at(date: LocalDate, time: String) =
        date.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    private fun lesson(id: Long, date: LocalDate, start: String, end: String, room: String = "B1.12") =
        WidgetLesson(id, "Lesson $id", room, at(date, start), at(date, end), 0xFF1E88E5.toInt())

    private val fridayLessons = listOf(
        lesson(1, friday, "08:30", "10:00"),
        lesson(2, friday, "10:15", "11:45"),
        lesson(3, friday, "12:30", "14:00"),
    )
    private val mondayLessons = listOf(lesson(4, monday, "09:00", "10:30"), lesson(5, monday, "11:00", "12:00"))

    @Test
    fun `before school shows all of today with nothing in progress`() {
        val content = chooseContent(fridayLessons + mondayLessons, at(friday, "07:00"), zone) as WidgetContent.Day
        assertEquals(friday, content.date)
        assertEquals(listOf(1L, 2L, 3L), content.lessons.map { it.eventId })
        assertEquals(-1, content.nowIndex)
    }

    @Test
    fun `during a lesson ended lessons are hidden and the current one is first and highlighted`() {
        val content = chooseContent(fridayLessons, at(friday, "10:30"), zone) as WidgetContent.Day
        assertEquals(listOf(2L, 3L), content.lessons.map { it.eventId })
        assertEquals(0, content.nowIndex)
    }

    @Test
    fun `between lessons nothing is highlighted`() {
        val content = chooseContent(fridayLessons, at(friday, "12:00"), zone) as WidgetContent.Day
        assertEquals(listOf(3L), content.lessons.map { it.eventId })
        assertEquals(-1, content.nowIndex)
    }

    @Test
    fun `lesson ends exactly now is no longer shown`() {
        val content = chooseContent(fridayLessons, at(friday, "11:45"), zone) as WidgetContent.Day
        assertEquals(listOf(3L), content.lessons.map { it.eventId })
    }

    @Test
    fun `after school on friday shows monday, skipping the weekend`() {
        val content = chooseContent(fridayLessons + mondayLessons, at(friday, "15:00"), zone) as WidgetContent.Day
        assertEquals(monday, content.date)
        assertEquals(listOf(4L, 5L), content.lessons.map { it.eventId })
        assertEquals(-1, content.nowIndex)
    }

    @Test
    fun `unsorted input comes out in time order`() {
        val content = chooseContent(fridayLessons.reversed(), at(friday, "07:00"), zone) as WidgetContent.Day
        assertEquals(listOf(1L, 2L, 3L), content.lessons.map { it.eventId })
    }

    @Test
    fun `nothing in the next week means nothing coming up`() {
        val farAway = lesson(9, friday.plusDays(10), "09:00", "10:00")
        assertEquals(WidgetContent.NothingComingUp, chooseContent(fridayLessons + farAway, at(friday, "15:00"), zone))
        assertEquals(WidgetContent.NothingComingUp, chooseContent(emptyList(), at(friday, "15:00"), zone))
    }

    @Test
    fun `next refresh is the next lesson boundary`() {
        val now = at(friday, "10:30")
        val content = chooseContent(fridayLessons, now, zone)
        assertEquals(at(friday, "11:45"), nextRefreshAt(content, now, zone))
    }

    @Test
    fun `next refresh is midnight when no lesson changes before it`() {
        val now = at(friday, "15:00")
        val content = chooseContent(fridayLessons + mondayLessons, now, zone)
        assertEquals(at(friday.plusDays(1), "00:00"), nextRefreshAt(content, now, zone))
        assertEquals(at(friday.plusDays(1), "00:00"), nextRefreshAt(WidgetContent.NeedsApp, now, zone))
    }

    @Test
    fun `day header`() {
        assertEquals("Today", dayHeader(friday, friday, "Today", "Tomorrow"))
        assertEquals("Tomorrow", dayHeader(friday.plusDays(1), friday, "Today", "Tomorrow"))
        assertEquals("Monday 9 Nov", dayHeader(monday, friday, "Today", "Tomorrow"))
    }

    @Test
    fun `synced shows a time for today and a date for earlier`() {
        val now = at(friday, "15:00")
        assertEquals("14:05", syncedWhen(at(friday, "14:05"), now, zone))
        assertEquals("5 Nov", syncedWhen(at(friday.minusDays(1), "20:00"), now, zone))
        assertEquals(null, syncedWhen(0, now, zone))
    }

    @Test
    fun `detail has time range and room when there is one`() {
        assertEquals("08:30 – 10:00 · B1.12", lessonDetail(lesson(1, friday, "08:30", "10:00"), zone))
        assertTrue(lessonDetail(lesson(1, friday, "08:30", "10:00", room = " "), zone) == "08:30 – 10:00")
    }
}
