package dev.eduartesync.presence

import dev.eduartesync.portal.AttendanceEntry
import dev.eduartesync.portal.ChartItem
import dev.eduartesync.portal.PresenceChart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class PresenceTest {
    private fun chart(title: String, center: String?, vararg items: Pair<String, Double>) =
        PresenceChart(title, center, center?.let { it.trimEnd('%').toDoubleOrNull() }, null, items.map { ChartItem(it.first, it.second) })

    @Test
    fun presenceNumbersFromBothCharts() {
        val numbers = presenceNumbers(
            listOf(
                chart("Presentie", "137.0", "Afwezig" to 19.0, "Aanwezig" to 137.0),
                chart("Percentage", "88%", "Afwezig" to 12.0, "Aanwezig" to 88.0),
                chart("Stage", "0.0", "Nog gepland" to 0.0),
            ),
        )
        assertEquals(PresenceNumbers(88.0, 137.0, 19.0), numbers)
        assertTrue(numbers.hasData)
    }

    @Test
    fun percentageFromHoursWhenThatChartIsMissing() {
        val numbers = presenceNumbers(listOf(chart("Presentie", "3.0", "Afwezig" to 1.0, "Aanwezig" to 3.0)))
        assertEquals(75.0, numbers.percent!!, 0.001)
    }

    @Test
    fun subjectWithoutLessonsHasNoData() {
        val numbers = presenceNumbers(
            listOf(
                chart("Presentie", "0.0", "Afwezig" to 0.0, "Aanwezig" to 0.0),
                chart("Percentage", "0%", "Afwezig" to 0.0, "Aanwezig" to 0.0),
            ),
        )
        assertNull(numbers.percent)
        assertFalse(numbers.hasData)
    }

    @Test
    fun allAbsentStillHasData() {
        val numbers = presenceNumbers(
            listOf(
                chart("Presentie", "0.0", "Afwezig" to 2.0, "Aanwezig" to 0.0),
                chart("Percentage", "0%", "Afwezig" to 100.0, "Aanwezig" to 0.0),
            ),
        )
        assertEquals(0.0, numbers.percent!!, 0.001)
        assertTrue(numbers.hasData)
    }

    @Test
    fun absenceNumbersFromTheAbsentieTab() {
        val absence = absenceNumbers(
            listOf(
                chart("Absentiemeldingen", "2", "Geoorloofd" to 2.0, "Ongeoorloofd" to 0.0),
                chart("Urenoverzicht", "19.0", "Geoorloofd" to 11.0, "Ongeoorloofd" to 8.0),
                chart("Percentage uren", "58%", "Geoorloofd" to 58.0, "Ongeoorloofd" to 42.0),
            ),
        )
        assertEquals(AbsenceNumbers(11.0, 8.0, 2.0, 0.0), absence)
        assertNull(absenceNumbers(emptyList()))
    }

    @Test
    fun duplicateSubjectNamesGetANumber() {
        assertEquals(
            listOf("Frans", "Biologie", "Frans (2)", "Tekenen", "Tekenen (2)"),
            labelDuplicates(listOf("Frans", "Biologie", "Frans", "Tekenen", "Tekenen ")),
        )
    }

    @Test
    fun lowestPercentageFirstAndUnknownLast() {
        val sorted = lowestFirst(
            listOf(
                SubjectPresence("B", PresenceNumbers(95.0, 19.0, 1.0)),
                SubjectPresence("Unknown", PresenceNumbers(null, 2.0, null)),
                SubjectPresence("A", PresenceNumbers(70.0, 7.0, 3.0)),
                SubjectPresence("C", PresenceNumbers(70.0, 7.0, 3.0)),
            ),
        )
        assertEquals(listOf("A", "C", "B", "Unknown"), sorted.map { it.name })
    }

    @Test
    fun levelThresholds() {
        assertEquals(PresenceLevel.GOOD, presenceLevel(GOOD_PRESENCE_PERCENT))
        assertEquals(PresenceLevel.WARNING, presenceLevel(89.9))
        assertEquals(PresenceLevel.WARNING, presenceLevel(WARNING_PRESENCE_PERCENT))
        assertEquals(PresenceLevel.LOW, presenceLevel(79.9))
    }

    private fun entry(date: LocalDate, start: Int, status: String, reported: String? = null, lesson: String = "Les") =
        AttendanceEntry(date, LocalTime.of(start, 0), LocalTime.of(start + 1, 0), null, null, lesson, null, null, status, reported)

    @Test
    fun lessonStatuses() {
        val day = LocalDate.of(2026, 11, 2)
        assertEquals(LessonStatus.PRESENT, lessonStatus(entry(day, 9, "Aanwezig")))
        assertEquals(LessonStatus.NOT_APPLICABLE, lessonStatus(entry(day, 9, "N.v.t.")))
        assertEquals(LessonStatus.UNAUTHORIZED, lessonStatus(entry(day, 9, "Afwezig", "Ongeoorloofd, niet afgehandeld")))
        assertEquals(LessonStatus.AUTHORIZED, lessonStatus(entry(day, 9, "Afwezig", "Geoorloofd")))
        assertEquals(LessonStatus.AUTHORIZED, lessonStatus(entry(day, 9, "Geoorloofd afwezig")))
        assertEquals(LessonStatus.ABSENT, lessonStatus(entry(day, 9, "Afwezig")))
        assertEquals(LessonStatus.OTHER, lessonStatus(entry(day, 9, "Niet geregistreerd")))
    }

    @Test
    fun lessonsGroupedByDayNewestFirstWithoutFutureDays() {
        val mon = LocalDate.of(2026, 11, 2)
        val tue = mon.plusDays(1)
        val days = lessonsByDay(
            listOf(entry(mon, 11, "Aanwezig", lesson = "B"), entry(tue, 9, "Aanwezig"), entry(mon, 9, "Aanwezig", lesson = "A"), entry(tue.plusDays(1), 9, "Aanwezig")),
            today = tue,
        )
        assertEquals(listOf(tue, mon), days.map { it.first })
        assertEquals(listOf("A", "B"), days[1].second.map { it.lesson })
    }

    @Test
    fun formatting() {
        assertEquals("88%", formatPercent(87.6))
        assertEquals("19", formatHours(19.0))
        assertEquals("18,5", formatHours(18.5))
        assertEquals("2,25", formatHours(2.25))
        assertEquals("0", formatHours(0.0))
        assertEquals("100", formatHours(100.0))
    }
}
