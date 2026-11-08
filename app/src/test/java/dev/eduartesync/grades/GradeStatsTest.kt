package dev.eduartesync.grades

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GradeStatsTest {
    private fun grade(
        subject: String = "Nederlands",
        value: String = "7,0",
        numeric: Double? = 7.0,
        weight: Double? = 1.0,
        date: LocalDate? = LocalDate.of(2026, 10, 1),
        description: String = "Toets",
    ) = Grade(subject, description, value, numeric, weight, date, period = "Periode 1")

    @Test
    fun `average is weighted`() {
        val grades = listOf(grade(numeric = 8.0, weight = 1.0), grade(numeric = 5.0, weight = 2.0))
        assertEquals(6.0, weightedAverage(grades)!!, 1e-9)
    }

    @Test
    fun `grades without a number or weight are left out of the average`() {
        val grades = listOf(
            grade(numeric = 8.0, weight = 1.0),
            grade(value = "V", numeric = null, weight = 1.0),
            grade(numeric = 1.0, weight = null),
            grade(numeric = 1.0, weight = 0.0),
        )
        assertEquals(8.0, weightedAverage(grades)!!, 1e-9)
    }

    @Test
    fun `no average without weights`() {
        assertNull(weightedAverage(listOf(grade(weight = null), grade(value = "G", numeric = null))))
        assertNull(weightedAverage(emptyList()))
    }

    @Test
    fun `groups per subject alphabetically, newest first, undated last`() {
        val old = grade(subject = "wiskunde", date = LocalDate.of(2026, 9, 1), description = "old")
        val undated = grade(subject = "wiskunde", date = null, description = "undated")
        val new = grade(subject = "wiskunde", date = LocalDate.of(2026, 10, 1), description = "new")
        val dutch = grade(subject = "Nederlands")

        val groups = groupBySubject(listOf(old, undated, dutch, new))

        assertEquals(listOf("Nederlands", "wiskunde"), groups.map { it.subject })
        assertEquals(listOf("new", "old", "undated"), groups[1].grades.map { it.description })
        assertEquals(7.0, groups[1].average!!, 1e-9)
    }

    @Test
    fun `recent takes the newest dated grades`() {
        val grades = (1..8).map { grade(date = LocalDate.of(2026, 10, it), description = "d$it") } + grade(date = null)
        assertEquals(listOf("d8", "d7", "d6"), recentGrades(grades, limit = 3).map { it.description })
    }

    @Test
    fun `below 5,5 is insufficient`() {
        assertTrue(isInsufficient(grade(numeric = 5.4)))
        assertFalse(isInsufficient(grade(numeric = 5.5)))
        assertFalse(isInsufficient(grade(value = "O", numeric = null)))
        assertTrue(isInsufficient(5.449))
    }

    @Test
    fun `numbers use a decimal comma and round halves up`() {
        assertEquals("7,5", formatDutch(7.45))
        assertEquals("6,0", formatDutch(6.0))
        assertEquals("8,33", formatDutch(25.0 / 3, decimals = 2))
    }

    @Test
    fun `weights are shown as multipliers`() {
        assertEquals("×2", formatWeight(2.0))
        assertEquals("×0,5", formatWeight(0.5))
        assertNull(formatWeight(null))
    }
}
