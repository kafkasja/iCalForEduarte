package dev.eduartesync.source

import dev.eduartesync.model.Lesson
import org.junit.Assert.assertEquals
import org.junit.Test

class LessonMergerTest {
    private val hour = 3_600_000L

    private fun lesson(id: String, startHour: Long, endHour: Long, subject: String = "PDO-Expert", room: String = "O_235") =
        Lesson(id, subject, startHour * hour, endHour * hour, room, teacher = "TEACH", group = "OITSDO25B")

    @Test
    fun `back-to-back same lesson becomes one`() {
        val merged = mergeConsecutive(listOf(lesson("a", 8, 9), lesson("b", 9, 10)))
        assertEquals(listOf(lesson("a", 8, 10)), merged)
    }

    @Test
    fun `three in a row chain into one, keeping the first id`() {
        val merged = mergeConsecutive(listOf(lesson("c", 10, 11), lesson("a", 8, 9), lesson("b", 9, 10)))
        assertEquals(listOf(lesson("a", 8, 11)), merged)
    }

    @Test
    fun `a break keeps lessons separate`() {
        val input = listOf(lesson("a", 8, 9), lesson("b", 10, 11))
        assertEquals(input, mergeConsecutive(input))
    }

    @Test
    fun `different room or subject keeps lessons separate`() {
        val input = listOf(lesson("a", 8, 9), lesson("b", 9, 10, room = "O_239"), lesson("c", 10, 11, subject = "NOSQL"))
        assertEquals(input, mergeConsecutive(input))
    }
}
