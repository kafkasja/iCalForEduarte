package dev.eduartesync.records

import dev.eduartesync.portal.DossierEntry
import dev.eduartesync.portal.PortalDocument
import dev.eduartesync.portal.PortalGroup
import dev.eduartesync.portal.PortalSubject
import dev.eduartesync.portal.Programme
import dev.eduartesync.portal.StudyInfo
import dev.eduartesync.portal.StudyPointsChart
import dev.eduartesync.portal.StudyPointsRow
import dev.eduartesync.portal.StudyProgress
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** The record pages the app keeps, see [PortalStore]. */
object Records {
    val DOSSIER = Record("dossier", ::dossierToJson, ::dossierFromJson)
    val DOCUMENTS = Record("documents", ::documentsToJson, ::documentsFromJson)
    val SUBJECTS = Record("subjects", ::subjectsToJson, ::subjectsFromJson)
    val GROUPS = Record("groups", ::groupsToJson, ::groupsFromJson)
    val STUDY = Record("study", ::studyToJson, ::studyFromJson)
}

internal fun dossierToJson(entries: List<DossierEntry>): String = array(entries) { e ->
    put("title", e.title)
    putOpt("category", e.category)
    put("subject", e.subject)
    putOpt("date", e.date?.toString())
    putOpt("author", e.author)
}

internal fun dossierFromJson(json: String): List<DossierEntry> = objects(json) { o ->
    DossierEntry(o.getString("title"), o.string("category"), o.getString("subject"), o.date("date"), o.string("author"))
}

internal fun documentsToJson(documents: List<PortalDocument>): String = array(documents) { d ->
    put("type", d.type)
    putOpt("date", d.date?.toString())
    put("name", d.name)
    putOpt("url", d.url)
}

internal fun documentsFromJson(json: String): List<PortalDocument> = objects(json) { o ->
    PortalDocument(o.getString("type"), o.date("date"), o.getString("name"), o.string("url"))
}

internal fun subjectsToJson(subjects: List<PortalSubject>): String = array(subjects) { s ->
    put("name", s.name)
    putOpt("code", s.code)
    putOpt("abbreviation", s.abbreviation)
    putOpt("start", s.startDate?.toString())
    putOpt("end", s.endDate?.toString())
}

internal fun subjectsFromJson(json: String): List<PortalSubject> = objects(json) { o ->
    PortalSubject(o.getString("name"), o.string("code"), o.string("abbreviation"), o.date("start"), o.date("end"))
}

internal fun groupsToJson(groups: List<PortalGroup>): String = array(groups) { g ->
    put("name", g.name)
    putOpt("size", g.size)
    put("members", JSONArray(g.members))
}

internal fun groupsFromJson(json: String): List<PortalGroup> = objects(json) { o ->
    val members = o.optJSONArray("members")
    PortalGroup(
        o.getString("name"),
        if (o.has("size")) o.getInt("size") else null,
        List(members?.length() ?: 0) { members!!.getString(it) },
    )
}

internal fun studyToJson(study: StudyInfo): String = JSONObject().apply {
    put("programmes", JSONArray().apply {
        study.programmes.forEach { p ->
            put(JSONObject().apply {
                put("name", p.name)
                putOpt("code", p.code)
                putOpt("status", p.status)
                putOpt("pathway", p.pathway)
                putOpt("level", p.level)
                putOpt("studyMode", p.studyMode)
                putOpt("start", p.startDate?.toString())
                putOpt("end", p.endDate?.toString())
                putOpt("location", p.location)
                put("other", JSONArray(p.other))
            })
        }
    })
    study.progress?.let { progress ->
        put("progress", JSONObject().apply {
            put("rows", JSONArray().apply { progress.rows.forEach { put(pointsRowToJson(it)) } })
            progress.total?.let { put("total", pointsRowToJson(it)) }
            put("charts", JSONArray().apply {
                progress.charts.forEach { c ->
                    put(JSONObject().apply {
                        put("title", c.title)
                        putOpt("achieved", c.achieved)
                        putOpt("provisional", c.provisional)
                        putOpt("notAchieved", c.notAchieved)
                    })
                }
            })
        })
    }
}.toString()

internal fun studyFromJson(json: String): StudyInfo {
    val o = JSONObject(json)
    val programmes = objects(o.getJSONArray("programmes")) { p ->
        val other = p.optJSONArray("other")
        Programme(
            name = p.getString("name"),
            code = p.string("code"),
            status = p.string("status"),
            pathway = p.string("pathway"),
            level = if (p.has("level")) p.getInt("level") else null,
            studyMode = p.string("studyMode"),
            startDate = p.date("start"),
            endDate = p.date("end"),
            location = p.string("location"),
            other = List(other?.length() ?: 0) { other!!.getString(it) },
        )
    }
    val progress = o.optJSONObject("progress")?.let { p ->
        StudyProgress(
            rows = objects(p.getJSONArray("rows"), ::pointsRowFromJson),
            charts = objects(p.optJSONArray("charts") ?: JSONArray()) { c ->
                StudyPointsChart(c.getString("title"), c.double("achieved"), c.double("provisional"), c.double("notAchieved"))
            },
            total = p.optJSONObject("total")?.let(::pointsRowFromJson),
        )
    }
    return StudyInfo(programmes, progress)
}

private fun pointsRowToJson(r: StudyPointsRow) = JSONObject().apply {
    put("subject", r.subject)
    putOpt("result", r.result)
    putOpt("total", r.totalPoints)
    putOpt("achieved", r.achievedPoints)
    putOpt("provisional", r.provisionalPoints)
}

private fun pointsRowFromJson(o: JSONObject) =
    StudyPointsRow(o.getString("subject"), o.string("result"), o.double("total"), o.double("achieved"), o.double("provisional"))

private fun <T> array(items: List<T>, fill: JSONObject.(T) -> Unit): String =
    JSONArray().apply { items.forEach { put(JSONObject().apply { fill(it) }) } }.toString()

private fun <T> objects(json: String, read: (JSONObject) -> T): List<T> = objects(JSONArray(json), read)

private fun <T> objects(array: JSONArray, read: (JSONObject) -> T): List<T> = List(array.length()) { read(array.getJSONObject(it)) }

internal fun JSONObject.string(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

internal fun JSONObject.double(key: String): Double? = if (has(key) && !isNull(key)) getDouble(key) else null

private fun JSONObject.date(key: String): LocalDate? = string(key)?.let(LocalDate::parse)
