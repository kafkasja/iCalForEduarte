package dev.eduartesync.grades

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** The grades of the last successful fetch and when that was. */
data class StoredGrades(val grades: List<Grade>, val fetchedAt: Long)

/**
 * Keeps the last fetched grades in the app's private storage, so the Cijfers tab shows them
 * right away (also offline) while a new fetch runs.
 */
class GradesStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Null when grades were never fetched (as opposed to fetched and empty). */
    fun load(): StoredGrades? {
        val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0)
        if (fetchedAt == 0L) return null
        val grades = runCatching { gradesFromJson(prefs.getString(KEY_GRADES, null) ?: "[]") }.getOrDefault(emptyList())
        return StoredGrades(grades, fetchedAt)
    }

    fun save(grades: List<Grade>, fetchedAt: Long) {
        prefs.edit()
            .putString(KEY_GRADES, gradesToJson(grades))
            .putLong(KEY_FETCHED_AT, fetchedAt)
            .apply()
    }

    private companion object {
        const val PREFS = "grades"
        const val KEY_GRADES = "grades"
        const val KEY_FETCHED_AT = "fetched_at"
    }
}

internal fun gradesToJson(grades: List<Grade>): String = JSONArray().apply {
    grades.forEach { g ->
        put(JSONObject().apply {
            put("subject", g.subject)
            put("description", g.description)
            put("value", g.value)
            g.numeric?.let { put("numeric", it) }
            g.weight?.let { put("weight", it) }
            g.date?.let { put("date", it.toString()) }
            g.period?.let { put("period", it) }
        })
    }
}.toString()

internal fun gradesFromJson(json: String): List<Grade> {
    val array = JSONArray(json)
    return List(array.length()) { i ->
        val o = array.getJSONObject(i)
        Grade(
            subject = o.getString("subject"),
            description = o.optString("description"),
            value = o.getString("value"),
            numeric = if (o.has("numeric")) o.getDouble("numeric") else null,
            weight = if (o.has("weight")) o.getDouble("weight") else null,
            date = if (o.has("date")) LocalDate.parse(o.getString("date")) else null,
            period = if (o.has("period")) o.getString("period") else null,
        )
    }
}
