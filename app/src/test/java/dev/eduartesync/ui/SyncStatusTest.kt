package dev.eduartesync.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SyncStatusTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val now = ZonedDateTime.of(2026, 11, 8, 14, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun summarize(
        loggedIn: Boolean = true,
        at: Long = now - 12 * minute,
        result: String? = "Synced 20 lessons until 15/11: no changes",
        running: Boolean = false,
        waiting: Boolean = false,
    ) = SyncStatus.summarize(loggedIn, at, result, running, waiting, now)

    @Test
    fun `recent successful sync is up to date`() {
        val s = summarize()
        assertEquals(SyncState.OK, s.state)
        assertEquals("Up to date", s.headline)
        assertEquals("Synced 12 min ago.", s.subline)
        assertEquals(StatusAction.SYNC, s.action)
    }

    @Test
    fun `old successful sync might be out of date`() {
        val s = summarize(at = now - 3 * 24 * hour)
        assertEquals(SyncState.STALE, s.state)
        assertEquals("Last synced 3 days ago.", s.subline)
    }

    @Test
    fun `expired login asks to log in`() {
        val s = summarize(result = "Not synced: your Eduarte login has expired. Tap the notification to log in again.")
        assertEquals(SyncState.LOGIN_NEEDED, s.state)
        assertEquals(StatusAction.LOG_IN, s.action)
    }

    @Test
    fun `logged out asks to log in even while a sync is queued`() {
        val s = summarize(loggedIn = false, running = true)
        assertEquals(SyncState.LOGIN_NEEDED, s.state)
        assertEquals(StatusAction.LOG_IN, s.action)
    }

    @Test
    fun `other failures offer a new sync`() {
        val s = summarize(result = "Not synced. Couldn't reach Eduarte. Check your internet connection.\n(IOException: x)")
        assertEquals(SyncState.FAILED, s.state)
        assertEquals(StatusAction.SYNC, s.action)
        assertEquals("Tried 12 min ago.", s.subline)
    }

    @Test
    fun `running and waiting syncs win over the last result`() {
        assertEquals(SyncState.SYNCING, summarize(running = true, waiting = true).state)
        assertEquals(SyncState.WAITING, summarize(waiting = true).state)
    }

    @Test
    fun `never synced`() {
        assertEquals(SyncState.NEVER, summarize(at = 0, result = null).state)
    }

    @Test
    fun `login expired detection`() {
        assertTrue(SyncStatus.isLoginExpired("Not synced: your Eduarte login has expired."))
        assertFalse(SyncStatus.isLoginExpired("Synced 3 lessons until 9/11: no changes"))
    }

    @Test
    fun `time ago`() {
        assertEquals("just now", SyncStatus.ago(now - 20_000, now))
        assertEquals("just now", SyncStatus.ago(now + 5 * minute, now))
        assertEquals("59 min ago", SyncStatus.ago(now - 59 * minute, now))
        assertEquals("1 hour ago", SyncStatus.ago(now - 90 * minute, now))
        assertEquals("23 hours ago", SyncStatus.ago(now - 23 * hour, now))
        assertEquals("yesterday", SyncStatus.ago(now - 30 * hour, now))
        assertEquals("2 days ago", SyncStatus.ago(now - 50 * hour, now))
    }

    @Test
    fun `coverage uses the last synced day while it hasn't passed`() {
        val today = LocalDate.of(2026, 11, 8)
        val end = LocalDate.of(2026, 11, 22)
        assertEquals("Covers today until Sun 15 Nov", SyncStatus.coverage(LocalDate.of(2026, 11, 15), end, today))
        assertEquals("Covers today only", SyncStatus.coverage(today, end, today))
        assertEquals("Next sync covers today until Sun 22 Nov", SyncStatus.coverage(null, end, today))
        assertEquals("Next sync covers today until Sun 22 Nov", SyncStatus.coverage(today.minusDays(1), end, today))
        assertEquals("Next sync covers today only", SyncStatus.coverage(null, today, today))
    }

    @Test
    fun `next automatic sync`() {
        val at = { day: Int, h: Int, m: Int -> ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone).toInstant().toEpochMilli() }
        assertEquals("Next automatic sync around 23:40", SyncStatus.nextSync(at(8, 23, 40), now, zone))
        assertEquals("Next automatic sync tomorrow around 08:10", SyncStatus.nextSync(at(9, 8, 10), now, zone))
        assertEquals("Next automatic sync on Tue around 08:10", SyncStatus.nextSync(at(10, 8, 10), now, zone))
        assertEquals("Next automatic sync soon", SyncStatus.nextSync(now - minute, now, zone))
    }

    @Test
    fun `compact line for the Rooster tab`() {
        val at = now - 3 * minute
        assertEquals("Up to date · 3 min ago", SyncStatus.compact(summarize(at = at), at, now))
        assertEquals("Last sync failed · 3 min ago", SyncStatus.compact(summarize(at = at, result = "Not synced. No internet"), at, now))
        assertEquals("Log in needed", SyncStatus.compact(summarize(loggedIn = false), at, now))
        assertEquals("Syncing…", SyncStatus.compact(summarize(running = true), at, now))
    }

    @Test
    fun `opening the app skips the sync only right after a successful one`() {
        val ok = "Synced 20 lessons until 15/11: no changes"
        assertFalse(SyncStatus.shouldSyncOnOpen(now - minute, ok, now))
        assertTrue(SyncStatus.shouldSyncOnOpen(now - 3 * minute, ok, now))
        assertTrue(SyncStatus.shouldSyncOnOpen(now - minute, "Not synced. No internet", now))
        assertTrue(SyncStatus.shouldSyncOnOpen(0, null, now))
    }
}
