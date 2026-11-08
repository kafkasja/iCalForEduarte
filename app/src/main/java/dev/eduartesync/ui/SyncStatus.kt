package dev.eduartesync.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The state shown by the colored dot on the settings page. */
enum class SyncState { NEVER, OK, STALE, FAILED, LOGIN_NEEDED, SYNCING, WAITING }

/** What the main button on the status card does. */
enum class StatusAction { SYNC, LOG_IN }

data class StatusSummary(
    val state: SyncState,
    val headline: String,
    val subline: String,
    val action: StatusAction,
)

/** Turns the saved sync status into the plain-language lines at the top of the settings page. */
object SyncStatus {
    /** After this long without a successful sync, the calendar may be out of date. */
    private const val STALE_AFTER_MS = 36 * 60 * 60 * 1000L

    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val shortDayFormat = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    /**
     * @param running a sync is running right now.
     * @param waiting a sync is queued but hasn't started, usually because there is no internet.
     */
    fun summarize(
        loggedIn: Boolean,
        lastSyncAt: Long,
        lastResult: String?,
        running: Boolean,
        waiting: Boolean,
        now: Long,
    ): StatusSummary {
        if (!loggedIn) {
            return StatusSummary(SyncState.LOGIN_NEEDED, "Not logged in", "Log in to sync your schedule.", StatusAction.LOG_IN)
        }
        if (running) {
            return StatusSummary(SyncState.SYNCING, "Syncing…", "Getting your schedule from Eduarte.", StatusAction.SYNC)
        }
        if (waiting) {
            return StatusSummary(SyncState.WAITING, "Starting sync…", "If you're offline, it starts once you're back online.", StatusAction.SYNC)
        }
        val result = lastResult.orEmpty()
        return when {
            lastSyncAt == 0L ->
                StatusSummary(SyncState.NEVER, "Not synced yet", "Tap Sync now to fill your calendar.", StatusAction.SYNC)
            isLoginExpired(result) ->
                StatusSummary(SyncState.LOGIN_NEEDED, "Login expired", "Log in again to keep syncing.", StatusAction.LOG_IN)
            !result.startsWith("Synced") ->
                StatusSummary(SyncState.FAILED, "Last sync failed", "Tried ${ago(lastSyncAt, now)}.", StatusAction.SYNC)
            now - lastSyncAt > STALE_AFTER_MS ->
                StatusSummary(SyncState.STALE, "Might be out of date", "Last synced ${ago(lastSyncAt, now)}.", StatusAction.SYNC)
            else ->
                StatusSummary(SyncState.OK, "Up to date", "Synced ${ago(lastSyncAt, now)}.", StatusAction.SYNC)
        }
    }

    fun isLoginExpired(result: String): Boolean {
        val text = result.lowercase()
        return text.startsWith("not synced") && "login" in text && "expired" in text
    }

    /** "just now", "12 min ago", "3 hours ago", "yesterday", "4 days ago". */
    fun ago(at: Long, now: Long): String {
        val minutes = maxOf(0L, now - at) / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            hours == 1L -> "1 hour ago"
            hours < 24 -> "$hours hours ago"
            days == 1L -> "yesterday"
            else -> "$days days ago"
        }
    }

    /**
     * Which days the calendar covers. [syncedUntil] is the last day of the previous successful
     * sync, [rangeEnd] the last day the next sync will cover.
     */
    fun coverage(syncedUntil: LocalDate?, rangeEnd: LocalDate, today: LocalDate): String =
        if (syncedUntil != null && !syncedUntil.isBefore(today)) {
            if (syncedUntil == today) "Covers today only" else "Covers today until ${syncedUntil.format(dayFormat)}"
        } else {
            if (rangeEnd == today) "Next sync covers today only" else "Next sync covers today until ${rangeEnd.format(dayFormat)}"
        }

    /** "Next automatic sync around 23:40", "... tomorrow around 08:10", "... on Tue around 08:10". */
    fun nextSync(nextAt: Long, now: Long, zone: ZoneId): String {
        if (nextAt <= now) return "Next automatic sync soon"
        val time = Instant.ofEpochMilli(nextAt).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val clock = time.format(timeFormat)
        return when (time.toLocalDate()) {
            today -> "Next automatic sync around $clock"
            today.plusDays(1) -> "Next automatic sync tomorrow around $clock"
            else -> "Next automatic sync on ${time.format(shortDayFormat)} around $clock"
        }
    }
}
