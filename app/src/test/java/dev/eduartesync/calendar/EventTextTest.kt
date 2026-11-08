package dev.eduartesync.calendar

import dev.eduartesync.model.Lesson
import dev.eduartesync.settings.RoomInTitle
import dev.eduartesync.subjects.SubjectStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class EventTextTest {
    private val lesson = Lesson("k", "PDO-Expert", 0, 1, "B1.12", teacher = "Jansen", group = "4A")

    private fun roundTrip(lesson: Lesson, room: RoomInTitle, style: SubjectStyle? = null): EventText {
        val fields = lesson.toFields(style, room)
        return readEventText(fields.title, fields.location, fields.description)
    }

    @Test
    fun `reads back what the calendar event holds`() {
        assertEquals(EventText("PDO-Expert", false, "Jansen", "4A"), roundTrip(lesson, RoomInTitle.OFF))
    }

    @Test
    fun `room in the title is taken off again`() {
        assertEquals("PDO-Expert", roundTrip(lesson, RoomInTitle.AFTER).name)
        assertEquals("PDO-Expert", roundTrip(lesson, RoomInTitle.BEFORE).name)
    }

    @Test
    fun `cancelled lessons and custom names`() {
        val style = SubjectStyle("PDO-Expert", displayName = "PDO", emoji = "💻", color = null)
        val text = roundTrip(lesson.copy(cancelled = true), RoomInTitle.AFTER, style)
        assertEquals(EventText("💻 PDO", true, "Jansen", "4A"), text)
    }

    @Test
    fun `missing teacher and group`() {
        assertEquals(EventText("PDO-Expert", false, null, null), roundTrip(lesson.copy(teacher = null, group = null), RoomInTitle.OFF))
    }
}
