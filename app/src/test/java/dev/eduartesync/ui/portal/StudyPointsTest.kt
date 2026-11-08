package dev.eduartesync.ui.portal

import dev.eduartesync.portal.StudyPointsChart
import dev.eduartesync.portal.StudyPointsRow
import dev.eduartesync.portal.StudyProgress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPointsTest {
    private fun row(achieved: Double? = 0.0, total: Double? = 0.0, provisional: Double? = 0.0, result: String? = null) =
        StudyPointsRow("PDO", result, total, achieved, provisional)

    @Test
    fun `all zeros or nothing is no study points`() {
        assertFalse(StudyProgress(emptyList(), emptyList(), null).hasPoints())
        assertFalse(
            StudyProgress(
                rows = listOf(row(), row(achieved = null, total = null, provisional = null)),
                charts = listOf(StudyPointsChart("Totale studievoortgang", 0.0, null, 0.0)),
                total = row(),
            ).hasPoints(),
        )
    }

    @Test
    fun `any points or a result count`() {
        assertTrue(StudyProgress(listOf(row(total = 60.0)), emptyList(), null).hasPoints())
        assertTrue(StudyProgress(listOf(row(result = "V")), emptyList(), null).hasPoints())
        assertTrue(StudyProgress(emptyList(), listOf(StudyPointsChart("Fase", 12.5, null, null)), null).hasPoints())
        assertTrue(StudyProgress(emptyList(), emptyList(), row(achieved = 3.0)).hasPoints())
    }
}
