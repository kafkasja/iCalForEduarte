package dev.eduartesync.records

import android.content.Context

/** Something read from the portal and when that was. */
data class Stored<T>(val value: T, val fetchedAt: Long)

/** One kind of portal data with its storage key and how to write it as JSON and back. */
class Record<T>(val key: String, val encode: (T) -> String, val decode: (String) -> T)

/**
 * Keeps the last read of the portal's record pages (dossier, documents, subjects, groups,
 * programmes) in the app's private storage, so their screens show something right away (also
 * offline) while they read the portal again. Like GradesStore, one JSON string plus the time.
 */
class PortalStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Null when it was never read (or can't be read back after an app update changed the format). */
    fun <T> load(record: Record<T>): Stored<T>? {
        val fetchedAt = prefs.getLong(record.key + SUFFIX_AT, 0)
        if (fetchedAt == 0L) return null
        val json = prefs.getString(record.key, null) ?: return null
        val value = runCatching { record.decode(json) }.getOrNull() ?: return null
        return Stored(value, fetchedAt)
    }

    fun <T> save(record: Record<T>, value: T, fetchedAt: Long) {
        prefs.edit()
            .putString(record.key, record.encode(value))
            .putLong(record.key + SUFFIX_AT, fetchedAt)
            .apply()
    }

    private companion object {
        const val PREFS = "portal_records"
        const val SUFFIX_AT = "_fetched_at"
    }
}
