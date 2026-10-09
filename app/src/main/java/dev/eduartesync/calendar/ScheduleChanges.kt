package dev.eduartesync.calendar

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A change to the schedule, in words a person can read in a notification. */
data class ScheduleChange(val kind: Kind, val text: String) {
    enum class Kind { ADDED, REMOVED, MOVED, CANCELLED, CHANGED }
}

private const val CANCELLED_PREFIX = "VERVALLEN – "
private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/**
 * Turns a [SyncPlan] into readable changes. Calendar housekeeping is left out: duplicates
 * being removed, events edited on the phone being restored, and color-only updates.
 */
fun describeChanges(existing: List<ExistingEvent>, plan: SyncPlan, zone: ZoneId): List<ScheduleChange> {
    val byRow = existing.associateBy { it.rowId }
    val deleted = plan.deletes.mapNotNull { byRow[it] }
    val keptKeys = existing.filter { it.rowId !in plan.deletes }.mapNotNull { it.key }.toSet()
    val insertedKeys = plan.inserts.map { it.first }.toSet()

    // A real removal is a lesson whose key is gone; anything else was a duplicate or a restore.
    val removed = deleted
        .filter { !it.deleted && it.key != null && it.key !in keptKeys && it.key !in insertedKeys }
        .map { it.fields }
        .toMutableList()
    val restoredKeys = deleted.filter { it.key in insertedKeys }.mapNotNull { it.key }.toSet()
    val added = plan.inserts.filter { it.first !in restoredKeys }.map { it.second }

    val changes = mutableListOf<ScheduleChange>()
    fun day(f: EventFields) = DAY.format(Instant.ofEpochMilli(f.startMillis).atZone(zone))
    fun time(millis: Long) = TIME.format(Instant.ofEpochMilli(millis).atZone(zone))
    fun span(f: EventFields) = "${time(f.startMillis)}-${time(f.endMillis)}"
    fun subject(f: EventFields) = f.title.removePrefix(CANCELLED_PREFIX)

    for (new in added) {
        // A new time changes the lesson's key, so a move shows up as a removal plus an addition.
        val old = removed.firstOrNull { subject(it) == subject(new) && day(it) == day(new) }
        if (old != null) {
            removed -= old
            changes += ScheduleChange(
                ScheduleChange.Kind.MOVED,
                "${subject(new)} ${day(new)}: ${span(old)} → ${span(new)}",
            )
        } else {
            changes += ScheduleChange(
                ScheduleChange.Kind.ADDED,
                "${new.title} ${day(new)} ${span(new)}${new.location.let { if (it.isEmpty()) "" else ", $it" }}",
            )
        }
    }
    for (old in removed) {
        changes += ScheduleChange(ScheduleChange.Kind.REMOVED, "${subject(old)} ${day(old)} ${span(old)}")
    }
    for ((rowId, new) in plan.updates) {
        val old = byRow[rowId]?.fields ?: continue
        val where = "${subject(new)} ${day(new)} ${time(new.startMillis)}"
        changes += when {
            new.title.startsWith(CANCELLED_PREFIX) && !old.title.startsWith(CANCELLED_PREFIX) ->
                ScheduleChange(ScheduleChange.Kind.CANCELLED, "$where is cancelled")
            old.title.startsWith(CANCELLED_PREFIX) && !new.title.startsWith(CANCELLED_PREFIX) ->
                ScheduleChange(ScheduleChange.Kind.CHANGED, "$where is no longer cancelled")
            old.location != new.location ->
                ScheduleChange(ScheduleChange.Kind.CHANGED, "$where: room ${old.location.ifEmpty { "none" }} → ${new.location.ifEmpty { "none" }}")
            old.startMillis != new.startMillis || old.endMillis != new.endMillis ->
                ScheduleChange(ScheduleChange.Kind.MOVED, "${subject(new)} ${day(new)}: ${span(old)} → ${span(new)}")
            else -> continue
        }
    }
    return changes
}

/** "2 added, 1 moved" or "no changes". */
fun summarize(changes: List<ScheduleChange>): String {
    if (changes.isEmpty()) return "no changes"
    return ScheduleChange.Kind.entries
        .mapNotNull { kind ->
            val n = changes.count { it.kind == kind }
            if (n == 0) null else "$n ${kind.name.lowercase()}"
        }
        .joinToString(", ")
}
