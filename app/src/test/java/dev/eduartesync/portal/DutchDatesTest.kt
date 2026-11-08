package dev.eduartesync.portal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class DutchDatesTest {
    @Test
    fun parsesDatesWithMonthNames() {
        assertEquals(LocalDate.of(2026, 4, 8), DutchDates.parseDate("8 april 2026"))
        assertEquals(LocalDate.of(2026, 3, 1), DutchDates.parseDate(" 1 maart 2026 "))
        assertEquals(LocalDate.of(2026, 10, 12), DutchDates.parseDate("12 okt. 2026"))
        assertNull(DutchDates.parseDate("31 februari 2026"))
        assertNull(DutchDates.parseDate("8 april"))
        assertNull(DutchDates.parseDate(null))
    }

    @Test
    fun parsesDayAndMonthNearADate() {
        val near = LocalDate.of(2026, 12, 28)
        assertEquals(LocalDate.of(2026, 11, 2), DutchDates.parseDayMonth("2 november", near))
        assertEquals(LocalDate.of(2027, 1, 4), DutchDates.parseDayMonth("4 januari", near))
    }

    @Test
    fun parsesWeekdays() {
        assertEquals(DayOfWeek.MONDAY, DutchDates.weekday("ma"))
        assertEquals(DayOfWeek.WEDNESDAY, DutchDates.weekday("wo"))
        assertEquals(DayOfWeek.FRIDAY, DutchDates.weekday("Vr"))
        assertEquals(DayOfWeek.SUNDAY, DutchDates.weekday("zondag"))
        assertNull(DutchDates.weekday("xx"))
    }

    @Test
    fun parsesNumbersAndTimes() {
        assertEquals(6.1, DutchDates.parseNumber("6,1")!!, 1e-9)
        assertEquals(10.0, DutchDates.parseNumber("10,0")!!, 1e-9)
        assertEquals(9.0, DutchDates.parseNumber("9")!!, 1e-9)
        assertNull(DutchDates.parseNumber("V"))
        assertNull(DutchDates.parseNumber("-"))
        assertEquals(LocalDate.of(2026, 8, 1), DutchDates.parseNumericDate("Actief vanaf 1-8-2026 op"))
        assertEquals(LocalDate.of(2026, 11, 2), DutchDates.parseNumericDate("02-11-2026"))
        assertEquals(LocalTime.of(9, 5), DutchDates.parseTime("9:05"))
    }
}
