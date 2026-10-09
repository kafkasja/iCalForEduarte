package dev.eduartesync.calendar

import dev.eduartesync.model.Lesson
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPlanTest {
    private fun fields(title: String = "PDO", start: Long = 1_000, location: String = "B1.12", color: Int = 0xFF1E88E5.toInt()) =
        EventFields(title, location, "", start, start + 3_000_000, color)

    @Test
    fun `color change or missing color is updated`() {
        val plan = planChanges(listOf(ExistingEvent(1, "a", fields(color = 0))), mapOf("a" to fields()))
        assertEquals(listOf(1L to fields()), plan.updates)
    }

    @Test
    fun `same subject gets the same color, cancelled or not`() {
        val a = Lesson("1", "PDO-Expert", 0, 10, null).toFields()
        val b = Lesson("2", "PDO-Expert", 0, 10, null, cancelled = true).toFields()
        assertEquals(a.color, b.color)
        assertEquals(a.color, SubjectColors.forSubject("PDO-Expert"))
    }

    @Test
    fun `new lessons are inserted`() {
        val plan = planChanges(emptyList(), mapOf("a" to fields()))
        assertEquals(listOf("a" to fields()), plan.inserts)
        assertEquals(emptyList<Long>(), plan.updates.map { it.first } + plan.deletes)
    }

    @Test
    fun `unchanged lessons are left alone`() {
        val plan = planChanges(listOf(ExistingEvent(1, "a", fields())), mapOf("a" to fields()))
        assertEquals(SyncPlan(emptyList(), emptyList(), emptyList()), plan)
    }

    @Test
    fun `moved lesson or local edit is updated`() {
        val plan = planChanges(
            listOf(ExistingEvent(1, "a", fields(start = 5_000))),
            mapOf("a" to fields()),
        )
        assertEquals(listOf(1L to fields()), plan.updates)
    }

    @Test
    fun `removed lessons, duplicates and unknown events are deleted`() {
        val plan = planChanges(
            listOf(
                ExistingEvent(1, "a", fields()),
                ExistingEvent(2, "a", fields()),
                ExistingEvent(3, "gone", fields()),
                ExistingEvent(4, null, fields()),
            ),
            mapOf("a" to fields()),
        )
        assertEquals(listOf(2L, 3L, 4L), plan.deletes)
        assertEquals(emptyList<Pair<String, EventFields>>(), plan.inserts)
    }

    @Test
    fun `event deleted on the phone comes back`() {
        val plan = planChanges(listOf(ExistingEvent(1, "a", fields(), deleted = true)), mapOf("a" to fields()))
        assertEquals(listOf(1L), plan.deletes)
        assertEquals(listOf("a" to fields()), plan.inserts)
    }

    @Test
    fun `lesson maps to title, location and description`() {
        val lesson = Lesson("x", "PDO", 0, 10, "B1.12", teacher = "JAN", cancelled = true)
        val f = lesson.toFields()
        assertEquals("VERVALLEN – PDO", f.title)
        assertEquals("B1.12", f.location)
        assertEquals("Teacher: JAN", f.description)
    }
}
