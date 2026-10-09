package dev.eduartesync.source

import dev.eduartesync.model.Lesson

/**
 * Joins lessons that follow each other directly (no break) with the same subject, room,
 * teacher, group and status into one. The merged lesson keeps the first lesson's id.
 */
fun mergeConsecutive(lessons: List<Lesson>): List<Lesson> {
    val merged = mutableListOf<Lesson>()
    for (lesson in lessons.sortedBy { it.startMillis }) {
        val last = merged.lastOrNull()
        if (last != null && last.endMillis == lesson.startMillis && last.sameClassAs(lesson)) {
            merged[merged.lastIndex] = last.copy(endMillis = lesson.endMillis)
        } else {
            merged += lesson
        }
    }
    return merged
}

private fun Lesson.sameClassAs(other: Lesson) =
    subject == other.subject && location == other.location && teacher == other.teacher &&
        group == other.group && cancelled == other.cancelled
