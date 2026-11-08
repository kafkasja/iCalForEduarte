package dev.eduartesync.presence

import android.content.Context
import dev.eduartesync.portal.AttendanceEntry
import dev.eduartesync.portal.PresencePeriod
import dev.eduartesync.records.Stored
import dev.eduartesync.records.double
import dev.eduartesync.records.string
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * Keeps the last attendance read from the portal in the app's private storage: one overview
 * per period, and the recent lessons. The Presentie screen shows these right away (also
 * offline) while it reads the portal again, and the Meer tab shows the school year's
 * percentage from here.
 */
class PresenceStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Null when this period was never read. */
    fun load(period: PresencePeriod): Stored<PresenceOverview>? = read(KEY_OVERVIEW + period.name) { overviewFromJson(it) }

    fun save(overview: PresenceOverview, fetchedAt: Long) =
        write(KEY_OVERVIEW + overview.period.name, overviewToJson(overview), fetchedAt)

    fun loadRecent(): Stored<List<AttendanceEntry>>? = read(KEY_RECENT) { entriesFromJson(it) }

    fun saveRecent(entries: List<AttendanceEntry>, fetchedAt: Long) = write(KEY_RECENT, entriesToJson(entries), fetchedAt)

    private fun <T> read(key: String, decode: (String) -> T): Stored<T>? {
        val fetchedAt = prefs.getLong(key + SUFFIX_AT, 0)
        if (fetchedAt == 0L) return null
        val value = runCatching { decode(prefs.getString(key, null) ?: return null) }.getOrNull() ?: return null
        return Stored(value, fetchedAt)
    }

    private fun write(key: String, json: String, fetchedAt: Long) {
        prefs.edit().putString(key, json).putLong(key + SUFFIX_AT, fetchedAt).apply()
    }

    private companion object {
        const val PREFS = "presence"
        const val KEY_OVERVIEW = "overview_"
        const val KEY_RECENT = "recent"
        const val SUFFIX_AT = "_fetched_at"
    }
}

internal fun overviewToJson(o: PresenceOverview): String = JSONObject().apply {
    put("period", o.period.name)
    put("presence", numbersToJson(o.presence))
    o.absence?.let { a ->
        put("absence", JSONObject().apply {
            putOpt("authorizedHours", a.authorizedHours)
            putOpt("unauthorizedHours", a.unauthorizedHours)
            putOpt("authorizedReports", a.authorizedReports)
            putOpt("unauthorizedReports", a.unauthorizedReports)
        })
    }
    o.subjects?.let { subjects ->
        put("subjects", JSONArray().apply {
            subjects.forEach { s -> put(JSONObject().put("name", s.name).put("numbers", numbersToJson(s.numbers))) }
        })
    }
    put("subjectsRead", o.subjectsRead)
    put("subjectsTotal", o.subjectsTotal)
}.toString()

internal fun overviewFromJson(json: String): PresenceOverview {
    val o = JSONObject(json)
    val subjects = o.optJSONArray("subjects")?.let { array ->
        List(array.length()) { i ->
            val s = array.getJSONObject(i)
            SubjectPresence(s.getString("name"), numbersFromJson(s.getJSONObject("numbers")))
        }
    }
    return PresenceOverview(
        period = PresencePeriod.valueOf(o.getString("period")),
        presence = numbersFromJson(o.getJSONObject("presence")),
        absence = o.optJSONObject("absence")?.let { a ->
            AbsenceNumbers(a.double("authorizedHours"), a.double("unauthorizedHours"), a.double("authorizedReports"), a.double("unauthorizedReports"))
        },
        subjects = subjects,
        subjectsRead = o.optInt("subjectsRead"),
        subjectsTotal = o.optInt("subjectsTotal"),
    )
}

internal fun entriesToJson(entries: List<AttendanceEntry>): String = JSONArray().apply {
    entries.forEach { e ->
        put(JSONObject().apply {
            put("date", e.date.toString())
            put("start", e.start.toString())
            put("end", e.end.toString())
            putOpt("lessonStart", e.lessonStart?.toString())
            putOpt("lessonEnd", e.lessonEnd?.toString())
            put("lesson", e.lesson)
            putOpt("group", e.group)
            putOpt("teacher", e.teacher)
            put("status", e.status)
            putOpt("reported", e.reported)
        })
    }
}.toString()

internal fun entriesFromJson(json: String): List<AttendanceEntry> {
    val array = JSONArray(json)
    return List(array.length()) { i ->
        val o = array.getJSONObject(i)
        AttendanceEntry(
            date = LocalDate.parse(o.getString("date")),
            start = LocalTime.parse(o.getString("start")),
            end = LocalTime.parse(o.getString("end")),
            lessonStart = o.string("lessonStart")?.let(LocalTime::parse),
            lessonEnd = o.string("lessonEnd")?.let(LocalTime::parse),
            lesson = o.getString("lesson"),
            group = o.string("group"),
            teacher = o.string("teacher"),
            status = o.getString("status"),
            reported = o.string("reported"),
        )
    }
}

private fun numbersToJson(n: PresenceNumbers) = JSONObject().apply {
    putOpt("percent", n.percent)
    putOpt("present", n.presentHours)
    putOpt("absent", n.absentHours)
}

private fun numbersFromJson(o: JSONObject) = PresenceNumbers(o.double("percent"), o.double("present"), o.double("absent"))
