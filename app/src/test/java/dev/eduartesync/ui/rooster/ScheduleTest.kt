package dev.eduartesync.ui.rooster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ScheduleTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val day = LocalDate.of(2026, 11, 9) // a Monday

    private fun at(time: String) = day.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    private fun lesson(name: String, from: String, to: String) =
        ScheduleLesson(0, name, "B1.12", null, null, at(from), at(to), 0, cancelled = false)

    private val pdo = lesson("PDO", "08:30", "10:00")
    private val engels = lesson("Engels", "10:45", "11:30")

    @Test
    fun `free time between lessons gets its own row`() {
        val items = dayItems(listOf(engels, pdo), now = null)
        assertEquals(3, items.size)
        assertEquals(pdo, (items[0] as DayItem.Lesson).lesson)
        assertEquals(45L, (items[1] as DayItem.Free).minutes)
        assertEquals(engels, (items[2] as DayItem.Lesson).lesson)
    }

    @Test
    fun `short breaks and overlaps get no free row`() {
        val next = lesson("NL", "10:03", "11:00")
        val overlapping = lesson("Rekenen", "09:00", "10:30")
        assertEquals(2, dayItems(listOf(pdo, next), now = null).size)
        val items = dayItems(listOf(pdo, overlapping, engels), now = null)
        assertEquals(15L, (items[2] as DayItem.Free).minutes) // from 10:30, the later end
    }

    @Test
    fun `lesson going on now is marked`() {
        val items = dayItems(listOf(pdo, engels), now = at("09:00"))
        assertTrue((items[0] as DayItem.Lesson).now)
        assertTrue(items.none { it is DayItem.Now })
    }

    @Test
    fun `free time going on now is marked`() {
        val items = dayItems(listOf(pdo, engels), now = at("10:15"))
        assertTrue((items[1] as DayItem.Free).now)
    }

    @Test
    fun `now line before the first and after the last lesson`() {
        assertTrue(dayItems(listOf(pdo), now = at("07:00")).first() is DayItem.Now)
        assertTrue(dayItems(listOf(pdo), now = at("12:00")).last() is DayItem.Now)
    }

    @Test
    fun `weekend days only get a page when they have lessons`() {
        val saturday = day.plusDays(5)
        val days = pageDays(day, day.plusDays(6), setOf(saturday))
        assertEquals((0L..5L).map { day.plusDays(it) }, days)
    }

    @Test
    fun `empty days say why they are empty`() {
        assertEquals(EmptyDay.NO_ACCESS, emptyDay(day, hasLessons = true, access = false, syncedUntil = day))
        assertEquals(null, emptyDay(day, hasLessons = true, access = true, syncedUntil = null))
        assertEquals(EmptyDay.NOT_SYNCED_YET, emptyDay(day, hasLessons = false, access = true, syncedUntil = null))
        assertEquals(EmptyDay.NO_LESSONS, emptyDay(day, hasLessons = false, access = true, syncedUntil = day))
        assertEquals(EmptyDay.NOT_SYNCED_THIS_FAR, emptyDay(day.plusDays(1), hasLessons = false, access = true, syncedUntil = day))
    }

    @Test
    fun `durations read naturally`() {
        assertEquals("45 min", formatDuration(45))
        assertEquals("1 h", formatDuration(60))
        assertEquals("1 h 30 min", formatDuration(90))
    }

    @Test
    fun `monday of a week`() {
        assertEquals(day, mondayOf(day.plusDays(6)))
        assertEquals(day, mondayOf(day))
    }

    @Test
    fun `day header says which day, how many lessons and from when to when`() {
        val lessons = listOf(engels, pdo)
        assertEquals("Today · 2 lessons · 08:30 – 11:30", dayHeader(day, today = day, lessons))
        assertEquals("Tomorrow · 2 lessons · 08:30 – 11:30", dayHeader(day, today = day.minusDays(1), lessons))
        assertEquals("Yesterday · 1 lesson · 08:30 – 10:00", dayHeader(day, today = day.plusDays(1), listOf(pdo)))
        assertEquals("Monday · 2 lessons · 08:30 – 11:30", dayHeader(day, today = day.plusDays(2), lessons))
        assertEquals("Monday · 2 lessons (1 cancelled) · 08:30 – 11:30", dayHeader(day, today = day.plusDays(2), listOf(pdo, engels.copy(cancelled = true))))
        assertEquals("Wednesday", dayHeader(day.plusDays(2), today = day.minusDays(7), emptyList()))
    }

    @Test
    fun `week hours leave out cancelled lessons`() {
        assertEquals(135L, lessonMinutes(listOf(pdo, engels)))
        assertEquals(90L, lessonMinutes(listOf(pdo, engels.copy(cancelled = true))))
        assertEquals(0L, lessonMinutes(emptyList()))
    }

    @Test
    fun `week title shows the hours with a Dutch comma, or none`() {
        assertEquals("Week 46 · 13 h", weekTitle(day, 13 * 60))
        assertEquals("Week 46 · 12,5 h", weekTitle(day, 12 * 60 + 30))
        assertEquals("Week 46 · 2,3 h", weekTitle(day, 135))
        assertEquals("Week 46 · 13 h", weekTitle(day, 13 * 60 - 1)) // rounds to 13,0
        assertEquals("Week 46", weekTitle(day, 0))
        assertEquals("Week 53", weekTitle(LocalDate.of(2026, 12, 28), 0))
    }
}
