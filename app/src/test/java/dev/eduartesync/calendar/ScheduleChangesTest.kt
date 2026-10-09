package dev.eduartesync.calendar

import dev.eduartesync.calendar.ScheduleChange.Kind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleChangesTest {
    private val zone = ZoneId.of("Europe/Amsterdam")

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun fields(title: String, day: Int, hour: Int, room: String = "O_235", color: Int = 1) =
        EventFields(title, room, "", at(day, hour), at(day, hour + 1), color)

    private fun changes(existing: List<ExistingEvent>, wanted: Map<String, EventFields>) =
        describeChanges(existing, planChanges(existing, wanted), zone)

    @Test
    fun `nothing changed gives no changes`() {
        val event = ExistingEvent(1, "a", fields("PDO-Expert", 12, 8))
        assertEquals(emptyList<ScheduleChange>(), changes(listOf(event), mapOf("a" to event.fields)))
        assertEquals("no changes", summarize(emptyList()))
    }

    @Test
    fun `added lesson`() {
        val result = changes(emptyList(), mapOf("a" to fields("PDO-Expert", 12, 8)))
        assertEquals(listOf(ScheduleChange(Kind.ADDED, "PDO-Expert Mon 12 Oct 08:00-09:00, O_235")), result)
    }

    @Test
    fun `removed lesson`() {
        val result = changes(listOf(ExistingEvent(1, "a", fields("NOSQL", 16, 10))), emptyMap())
        assertEquals(listOf(ScheduleChange(Kind.REMOVED, "NOSQL Fri 16 Oct 10:00-11:00")), result)
    }

    @Test
    fun `new time on the same day is reported as moved`() {
        val result = changes(
            listOf(ExistingEvent(1, "old", fields("Python", 13, 13))),
            mapOf("new" to fields("Python", 13, 14)),
        )
        assertEquals(listOf(ScheduleChange(Kind.MOVED, "Python Tue 13 Oct: 13:00-14:00 → 14:00-15:00")), result)
    }

    @Test
    fun `room change`() {
        val result = changes(
            listOf(ExistingEvent(1, "a", fields("NOSQL", 16, 10, room = "O_318"))),
            mapOf("a" to fields("NOSQL", 16, 10, room = "O_239")),
        )
        assertEquals(listOf(ScheduleChange(Kind.CHANGED, "NOSQL Fri 16 Oct 10:00: room O_318 → O_239")), result)
    }

    @Test
    fun `cancelled lesson`() {
        val result = changes(
            listOf(ExistingEvent(1, "a", fields("Engels", 13, 9))),
            mapOf("a" to fields("VERVALLEN – Engels", 13, 9)),
        )
        assertEquals(listOf(ScheduleChange(Kind.CANCELLED, "Engels Tue 13 Oct 09:00 is cancelled")), result)
    }

    @Test
    fun `housekeeping is not reported`() {
        val pdo = fields("PDO-Expert", 12, 8)
        val existing = listOf(
            ExistingEvent(1, "a", pdo.copy(color = 0)),          // gets its color: not a schedule change
            ExistingEvent(2, "a", pdo),                          // duplicate: removed quietly
            ExistingEvent(3, "b", fields("NOSQL", 12, 10), deleted = true), // deleted on the phone: restored
        )
        val result = changes(existing, mapOf("a" to pdo, "b" to fields("NOSQL", 12, 10)))
        assertEquals(emptyList<ScheduleChange>(), result)
    }

    @Test
    fun `restyling a subject is not a schedule change`() {
        val result = changes(
            listOf(ExistingEvent(1, "a", fields("PDO-Expert", 12, 8, color = 1))),
            mapOf("a" to fields("💻 PDO", 12, 8, color = 2)),
        )
        assertEquals(emptyList<ScheduleChange>(), result)
    }

    @Test
    fun `days outside the window are not reported`() {
        val existing = listOf(ExistingEvent(1, "old", fields("NOSQL", 12, 10)))
        val wanted = mapOf("new" to fields("Python", 20, 9))
        val plan = planChanges(existing, wanted)
        // Window covers only the 12th: the removal there counts, the new lesson on the 20th doesn't.
        val window = at(12, 0) until at(13, 0)
        assertEquals(
            listOf(ScheduleChange(Kind.REMOVED, "NOSQL Mon 12 Oct 10:00-11:00")),
            describeChanges(existing, plan, zone, window),
        )
        assertEquals(emptyList<ScheduleChange>(), describeChanges(existing, plan, zone, LongRange.EMPTY))
    }

    @Test
    fun `summary counts each kind`() {
        val list = listOf(
            ScheduleChange(Kind.ADDED, ""), ScheduleChange(Kind.ADDED, ""), ScheduleChange(Kind.MOVED, ""),
        )
        assertEquals("2 added, 1 moved", summarize(list))
    }
}
