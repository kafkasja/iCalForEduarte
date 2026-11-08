package dev.eduartesync.calendar

import dev.eduartesync.model.Lesson
import dev.eduartesync.settings.RoomInTitle
import dev.eduartesync.subjects.SubjectStyle

/** The calendar fields we control for one event. */
data class EventFields(
    val title: String,
    val location: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
    /** ARGB event color; 0 means none (events written before colors existed). */
    val color: Int = 0,
)

/** An event currently in our calendar. [key] is the lesson id stored in SYNC_DATA1. */
data class ExistingEvent(
    val rowId: Long,
    val key: String?,
    val fields: EventFields,
    val deleted: Boolean = false,
)

data class SyncPlan(
    val inserts: List<Pair<String, EventFields>>,
    val updates: List<Pair<Long, EventFields>>,
    val deletes: List<Long>,
)

/**
 * How a lesson looks in the calendar. [style] is the user's customization from the Subjects page,
 * [room] says whether the room is also put in the title, [short] whether the name is shortened
 * (only when the user didn't name the subject themselves).
 */
fun Lesson.toFields(style: SubjectStyle? = null, room: RoomInTitle = RoomInTitle.OFF, short: Boolean = false): EventFields {
    val subjectName = listOfNotNull(
        style?.emoji?.takeIf { it.isNotBlank() },
        style?.displayName?.takeIf { it.isNotBlank() } ?: if (short) ShortNames.of(subject) else subject,
    ).joinToString(" ")
    val where = location?.takeIf { it.isNotBlank() }
    val name = when {
        where == null || room == RoomInTitle.OFF -> subjectName
        room == RoomInTitle.AFTER -> "$subjectName$ROOM_SEPARATOR$where"
        else -> "$where$ROOM_SEPARATOR$subjectName"
    }
    return EventFields(
        title = if (cancelled) "$CANCELLED_PREFIX$name" else name,
        location = location.orEmpty(),
        description = listOfNotNull(
            teacher?.let { "$TEACHER_LABEL$it" },
            group?.let { "$GROUP_LABEL$it" },
        ).joinToString("\n"),
        startMillis = startMillis,
        endMillis = endMillis,
        color = style?.color ?: SubjectColors.forSubject(subject),
    )
}

const val CANCELLED_PREFIX = "VERVALLEN – "
const val ROOM_SEPARATOR = " · "

/** Line prefixes in the event description; [readEventText] reads them back for the app. */
const val TEACHER_LABEL = "Teacher: "
const val GROUP_LABEL = "Group: "

/**
 * Works out what to change so the calendar matches [wanted] (lesson id -> fields).
 * Events edited on the phone get overwritten, events deleted on the phone come back,
 * and events whose lesson is gone get removed.
 */
fun planChanges(existing: List<ExistingEvent>, wanted: Map<String, EventFields>): SyncPlan {
    val inserts = mutableListOf<Pair<String, EventFields>>()
    val updates = mutableListOf<Pair<Long, EventFields>>()
    val deletes = mutableListOf<Long>()

    val byKey = HashMap<String, ExistingEvent>()
    for (event in existing) {
        val key = event.key
        if (event.deleted || key == null || key !in wanted || key in byKey) {
            deletes += event.rowId
        } else {
            byKey[key] = event
        }
    }

    for ((key, fields) in wanted) {
        val event = byKey[key]
        when {
            event == null -> inserts += key to fields
            event.fields != fields -> updates += event.rowId to fields
        }
    }
    return SyncPlan(inserts, updates, deletes)
}
