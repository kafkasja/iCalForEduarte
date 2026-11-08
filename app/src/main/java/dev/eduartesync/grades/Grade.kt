package dev.eduartesync.grades

import java.time.LocalDate

/**
 * One grade as Eduarte shows it. [value] is kept exactly as shown ("7,5", "V", "G"), because
 * not every grade is a number; [numeric] is filled in only when it is one.
 */
data class Grade(
    val subject: String,
    val description: String,
    /** As shown, e.g. "7,5", "V", "G". */
    val value: String,
    val numeric: Double?,
    /** How much the grade counts towards the average, e.g. 2.0 for a test that counts double. */
    val weight: Double?,
    val date: LocalDate?,
    /** School period, e.g. "Periode 1". */
    val period: String?,
)
