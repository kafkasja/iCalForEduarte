package dev.eduartesync.subjects

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** How one subject shows up in the calendar. Null fields mean "use the default". */
data class SubjectStyle(
    /** The subject as Eduarte names it, e.g. "PDO-Expert". */
    val name: String,
    val displayName: String? = null,
    val emoji: String? = null,
    val color: Int? = null,
)

/**
 * Every subject seen in the schedule, with its custom style. Subjects stay in the list until
 * the user removes them. Stored as JSON in the app's private storage.
 */
class SubjectStore(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    fun all(): List<SubjectStyle> = synchronized(lock) { read() }.sortedBy { it.name.lowercase() }

    fun byName(): Map<String, SubjectStyle> = synchronized(lock) { read() }.associateBy { it.name }

    /** Adds subjects that aren't in the list yet. Existing styles are left alone. */
    fun addSeen(names: Collection<String>) = synchronized(lock) {
        val current = read()
        val known = current.map { it.name }.toSet()
        val added = names.toSet().filter { it.isNotBlank() && it !in known }.map { SubjectStyle(it) }
        if (added.isNotEmpty()) write(current + added)
    }

    fun save(style: SubjectStyle) = synchronized(lock) {
        write(read().filter { it.name != style.name } + style)
    }

    fun remove(name: String) = synchronized(lock) {
        write(read().filter { it.name != name })
    }

    private fun read(): List<SubjectStyle> =
        if (file.exists()) decode(runCatching { file.readText() }.getOrDefault("")) else emptyList()

    private fun write(subjects: List<SubjectStyle>) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(encode(subjects))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        private const val FILE_NAME = "subjects.json"
        private const val VERSION = 1
        private val lock = Any()

        internal fun encode(subjects: List<SubjectStyle>): String {
            val list = JSONArray()
            subjects.forEach { s ->
                list.put(JSONObject().apply {
                    put("name", s.name)
                    s.displayName?.let { put("displayName", it) }
                    s.emoji?.let { put("emoji", it) }
                    s.color?.let { put("color", it) }
                })
            }
            return JSONObject().put("version", VERSION).put("subjects", list).toString(2)
        }

        /** A missing or broken file gives an empty list rather than a crash. */
        internal fun decode(json: String): List<SubjectStyle> = runCatching {
            val list = JSONObject(json).getJSONArray("subjects")
            (0 until list.length()).map { i ->
                val o = list.getJSONObject(i)
                SubjectStyle(
                    name = o.getString("name"),
                    displayName = o.optString("displayName").ifEmpty { null },
                    emoji = o.optString("emoji").ifEmpty { null },
                    color = if (o.has("color")) o.getInt("color") else null,
                )
            }
        }.getOrDefault(emptyList())
    }
}
