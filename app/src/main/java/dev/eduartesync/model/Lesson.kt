package dev.eduartesync.model

/** One lesson as read from Eduarte. Times are epoch milliseconds. */
data class Lesson(
    /** Stable id across syncs, so a changed lesson updates its event instead of duplicating it. */
    val id: String,
    /** Class name, e.g. "PDO". Becomes the event title. */
    val subject: String,
    val startMillis: Long,
    val endMillis: Long,
    val location: String?,
    val teacher: String? = null,
    val group: String? = null,
    val cancelled: Boolean = false,
)
