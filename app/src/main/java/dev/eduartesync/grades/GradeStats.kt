package dev.eduartesync.grades

import java.math.BigDecimal
import java.math.RoundingMode

/** Below this a grade is insufficient ("onvoldoende") in Dutch schools. */
const val PASS_MARK = 5.5

/** All grades of one subject, newest first, with the weighted average when it can be worked out. */
data class SubjectGrades(val subject: String, val grades: List<Grade>, val average: Double?)

/**
 * Weighted average of the grades that have both a number and a weight. Null when there are none:
 * an average without weights could be far off from the school's, so it isn't guessed.
 */
fun weightedAverage(grades: List<Grade>): Double? {
    val counted = grades.filter { it.numeric != null && (it.weight ?: 0.0) > 0.0 }
    if (counted.isEmpty()) return null
    val totalWeight = counted.sumOf { it.weight!! }
    return counted.sumOf { it.numeric!! * it.weight!! } / totalWeight
}

/** One group per subject, alphabetical; inside a group the newest grade first, undated last. */
fun groupBySubject(grades: List<Grade>): List<SubjectGrades> =
    grades.groupBy { it.subject }
        .map { (subject, list) -> SubjectGrades(subject, newestFirst(list), weightedAverage(list)) }
        .sortedBy { it.subject.lowercase() }

/** The [limit] newest dated grades, for the "Recent" section. */
fun recentGrades(grades: List<Grade>, limit: Int = 5): List<Grade> =
    newestFirst(grades.filter { it.date != null }).take(limit)

fun isInsufficient(grade: Grade): Boolean = grade.numeric != null && grade.numeric < PASS_MARK

fun isInsufficient(average: Double): Boolean = average < PASS_MARK

/** "7,5" — one decimal, Dutch decimal comma, halves rounded up like report cards do. */
fun formatDutch(value: Double, decimals: Int = 1): String =
    BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).toPlainString().replace('.', ',')

/** "×2" for a weight, "×0,5" when it isn't whole; null when there is no weight. */
fun formatWeight(weight: Double?): String? = when {
    weight == null -> null
    weight % 1.0 == 0.0 -> "×${weight.toLong()}"
    else -> "×${formatDutch(weight)}"
}

private fun newestFirst(grades: List<Grade>): List<Grade> =
    grades.sortedWith(compareBy<Grade> { it.date == null }.thenByDescending { it.date })
