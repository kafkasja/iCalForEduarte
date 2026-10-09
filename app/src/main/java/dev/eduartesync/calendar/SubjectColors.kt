package dev.eduartesync.calendar

import kotlin.math.abs

/** A fixed color per subject, the same on every sync and every phone. */
object SubjectColors {
    private val PALETTE = intArrayOf(
        0xFF1E88E5.toInt(), // blue
        0xFF43A047.toInt(), // green
        0xFFE53935.toInt(), // red
        0xFFFB8C00.toInt(), // orange
        0xFF8E24AA.toInt(), // purple
        0xFF00ACC1.toInt(), // cyan
        0xFFD81B60.toInt(), // pink
        0xFF6D4C41.toInt(), // brown
        0xFF3949AB.toInt(), // indigo
        0xFF7CB342.toInt(), // light green
        0xFFF4511E.toInt(), // deep orange
        0xFF546E7A.toInt(), // blue grey
    )

    fun forSubject(subject: String): Int =
        PALETTE[abs(subject.trim().lowercase().hashCode() % PALETTE.size)]
}
