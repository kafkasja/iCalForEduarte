package dev.eduartesync.grades

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class GradesJsonTest {
    @Test
    fun `grades survive a round trip, including missing fields`() {
        val grades = listOf(
            Grade("Engels", "Leesvaardigheid", "7,5", 7.5, 2.0, LocalDate.of(2026, 10, 12), "Periode 1"),
            Grade("LO", "Inzet", "V", null, null, null, null),
        )
        assertEquals(grades, gradesFromJson(gradesToJson(grades)))
    }
}
