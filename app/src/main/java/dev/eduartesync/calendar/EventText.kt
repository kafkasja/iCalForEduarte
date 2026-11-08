package dev.eduartesync.calendar

/** What the app shows for an event, read back from the title and description [toFields] wrote. */
data class EventText(
    /** Subject name (with the user's emoji and name), without the room or "cancelled" prefix. */
    val name: String,
    val cancelled: Boolean,
    val teacher: String?,
    val group: String?,
)

/**
 * Undoes what [toFields] puts into the title for other calendar apps: the cancelled prefix and
 * the room (when "Room in the event name" is on), since the app shows those separately.
 */
fun readEventText(title: String, location: String, description: String): EventText {
    val cancelled = title.startsWith(CANCELLED_PREFIX)
    var name = title.removePrefix(CANCELLED_PREFIX)
    if (location.isNotBlank()) {
        name = name.removeSuffix("$ROOM_SEPARATOR$location").removePrefix("$location$ROOM_SEPARATOR")
    }
    val lines = description.lines()
    fun field(label: String) = lines.firstOrNull { it.startsWith(label) }?.removePrefix(label)?.trim()?.takeIf { it.isNotEmpty() }
    return EventText(name.ifBlank { title }, cancelled, field(TEACHER_LABEL), field(GROUP_LABEL))
}
