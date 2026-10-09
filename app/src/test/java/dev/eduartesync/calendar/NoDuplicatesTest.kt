package dev.eduartesync.calendar

import dev.eduartesync.model.Lesson
import dev.eduartesync.source.mergeConsecutive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs syncs against an in-memory "calendar" and checks every lesson ends up exactly once. */
class NoDuplicatesTest {
    private val hour = 3_600_000L
    private var nextRowId = 1L
    private val calendar = mutableListOf<ExistingEvent>()

    private fun lesson(id: String, start: Long, end: Long, subject: String = "PDO-Expert", room: String = "O_235") =
        Lesson(id, subject, start * hour, end * hour, room, teacher = "T", group = "G")

    /** Same steps as CalendarWriter.sync, applied to the in-memory list. Returns the plan. */
    private fun sync(lessons: List<Lesson>): SyncPlan {
        val plan = planChanges(calendar.toList(), mergeConsecutive(lessons).associate { it.id to it.toFields() })
        calendar.removeAll { it.rowId in plan.deletes }
        plan.updates.forEach { (rowId, fields) ->
            val i = calendar.indexOfFirst { it.rowId == rowId }
            calendar[i] = calendar[i].copy(fields = fields)
        }
        plan.inserts.forEach { (key, fields) -> calendar += ExistingEvent(nextRowId++, key, fields) }
        return plan
    }

    private fun assertExactlyOnce(expected: List<Lesson>) {
        val wanted = mergeConsecutive(expected).map { it.id }
        assertEquals(wanted.sorted(), calendar.map { it.key }.sortedBy { it })
        assertEquals("duplicate keys", calendar.size, calendar.map { it.key }.toSet().size)
    }

    private val week = listOf(
        lesson("mon-0830-pdo", 8, 9),
        lesson("mon-0930-pdo", 9, 10),                       // back-to-back with the one above
        lesson("mon-1045-nosql", 11, 12, subject = "NOSQL"),
        lesson("tue-0830-python", 32, 33, subject = "Python"),
    )

    @Test
    fun `syncing many times never adds anything twice`() {
        repeat(5) { sync(week) }
        assertExactlyOnce(week)
        val last = sync(week)
        assertTrue(last.inserts.isEmpty() && last.updates.isEmpty() && last.deletes.isEmpty())
    }

    @Test
    fun `room change updates the existing event`() {
        sync(week)
        val changed = week.map { if (it.subject == "NOSQL") it.copy(location = "O_239") else it }
        val plan = sync(changed)
        assertEquals(1, plan.updates.size)
        assertTrue(plan.inserts.isEmpty())
        assertExactlyOnce(changed)
    }

    @Test
    fun `moved lesson replaces the old event instead of adding a second`() {
        sync(week)
        // A new time gives a new id (the id contains the start time).
        val moved = week.map { if (it.subject == "Python") lesson("tue-1300-python", 37, 38, subject = "Python") else it }
        sync(moved)
        assertExactlyOnce(moved)
        assertEquals(1, calendar.count { it.fields.title == "Python" })
    }

    @Test
    fun `existing duplicates are cleaned up`() {
        sync(week)
        val pdo = calendar.first()
        calendar += pdo.copy(rowId = nextRowId++)
        calendar += pdo.copy(rowId = nextRowId++, key = null)
        sync(week)
        assertExactlyOnce(week)
    }

    @Test
    fun `lesson that disappears is removed`() {
        sync(week)
        val fewer = week.filter { it.subject != "NOSQL" }
        sync(fewer)
        assertExactlyOnce(fewer)
    }

    @Test
    fun `events from before merging existed don't linger`() {
        // Old app version: both PDO hours as separate events.
        week.forEach { calendar += ExistingEvent(nextRowId++, it.id, it.toFields()) }
        sync(week)
        assertExactlyOnce(week)
        assertEquals(1, calendar.count { it.fields.title == "PDO-Expert" })
    }
}
