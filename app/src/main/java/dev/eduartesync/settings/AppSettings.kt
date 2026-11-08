package dev.eduartesync.settings

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** How far ahead a sync goes. Saved by [key], so options can be added or reordered later. */
enum class SyncRange(val key: String, val label: String) {
    TODAY("today", "Today only"),
    THIS_WEEK("this_week", "Rest of this week"),
    NEXT_WEEK("next_week", "Through next week"),
    MAX("max", "As far as possible (8 weeks)");

    fun lastDay(today: LocalDate): LocalDate = when (this) {
        TODAY -> today
        THIS_WEEK -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        NEXT_WEEK -> THIS_WEEK.lastDay(today).plusWeeks(1)
        MAX -> today.plusWeeks(8)
    }

    companion object {
        val DEFAULT = NEXT_WEEK
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * Whether the room goes into the event title. Lock screens and widgets (like Samsung's) often
 * show only the title and time of an event, not its location. Saved by [key], like [SyncRange].
 */
enum class RoomInTitle(val key: String, val label: String) {
    OFF("off", "Don't add the room"),
    AFTER("after", "After the name: PDO · B1.12"),
    BEFORE("before", "Before the name: B1.12 · PDO");

    companion object {
        val DEFAULT = OFF
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** User settings and sync status, stored in the app's private storage. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var autoSync: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SYNC, value).apply()

    var range: SyncRange
        get() = SyncRange.fromKey(prefs.getString(KEY_RANGE, null))
        set(value) = prefs.edit().putString(KEY_RANGE, value.key).apply()

    var roomInTitle: RoomInTitle
        get() = RoomInTitle.fromKey(prefs.getString(KEY_ROOM_IN_TITLE, null))
        set(value) = prefs.edit().putString(KEY_ROOM_IN_TITLE, value.key).apply()

    /** Shorter lesson names in the calendar, see [dev.eduartesync.calendar.ShortNames]. On by default. */
    var shortNames: Boolean
        get() = prefs.getBoolean(KEY_SHORT_NAMES, true)
        set(value) = prefs.edit().putBoolean(KEY_SHORT_NAMES, value).apply()

    /** Epoch millis of the last finished sync, 0 if never. */
    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC_AT, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_AT, value).apply()

    var lastSyncResult: String?
        get() = prefs.getString(KEY_LAST_SYNC_RESULT, null)
        set(value) = prefs.edit().putString(KEY_LAST_SYNC_RESULT, value).apply()

    /**
     * Last day covered by the previous successful sync. Lessons after it are new to the
     * calendar, not changes, so they don't trigger a change notification.
     */
    var syncedUntil: LocalDate?
        get() = prefs.getString(KEY_SYNCED_UNTIL, null)?.let(LocalDate::parse)
        set(value) = prefs.edit().putString(KEY_SYNCED_UNTIL, value?.toString()).apply()

    fun registerListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private companion object {
        const val PREFS = "settings"
        const val KEY_AUTO_SYNC = "auto_sync"
        const val KEY_RANGE = "range"
        const val KEY_ROOM_IN_TITLE = "room_in_title"
        const val KEY_SHORT_NAMES = "short_names"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
        const val KEY_LAST_SYNC_RESULT = "last_sync_result"
        const val KEY_SYNCED_UNTIL = "synced_until"
    }
}
