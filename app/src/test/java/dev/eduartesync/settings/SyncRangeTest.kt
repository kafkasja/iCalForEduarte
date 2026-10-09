package dev.eduartesync.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SyncRangeTest {
    private val monday = LocalDate.of(2026, 10, 12)
    private val thursday = LocalDate.of(2026, 10, 15)
    private val sunday = LocalDate.of(2026, 10, 18)

    @Test
    fun `today only`() {
        assertEquals(thursday, SyncRange.TODAY.lastDay(thursday))
    }

    @Test
    fun `rest of this week ends on sunday`() {
        assertEquals(sunday, SyncRange.THIS_WEEK.lastDay(monday))
        assertEquals(sunday, SyncRange.THIS_WEEK.lastDay(thursday))
        assertEquals(sunday, SyncRange.THIS_WEEK.lastDay(sunday))
    }

    @Test
    fun `through next week ends on next sunday`() {
        assertEquals(LocalDate.of(2026, 10, 25), SyncRange.NEXT_WEEK.lastDay(monday))
        assertEquals(LocalDate.of(2026, 10, 25), SyncRange.NEXT_WEEK.lastDay(sunday))
    }

    @Test
    fun `as far as possible is eight weeks`() {
        assertEquals(LocalDate.of(2026, 12, 10), SyncRange.MAX.lastDay(thursday))
    }

    @Test
    fun `unknown saved value falls back to the default`() {
        assertEquals(SyncRange.NEXT_WEEK, SyncRange.fromKey("something-removed-later"))
        assertEquals(SyncRange.NEXT_WEEK, SyncRange.fromKey(null))
        assertEquals(SyncRange.TODAY, SyncRange.fromKey("today"))
    }
}
