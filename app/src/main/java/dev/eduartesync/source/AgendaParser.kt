package dev.eduartesync.source

import dev.eduartesync.model.Lesson
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** One week of the portal's agenda page. */
data class AgendaWeek(
    val days: List<LocalDate>,
    val lessons: List<Lesson>,
)

/**
 * Parses the Eduarte portal agenda page (`/agenda`, "Week" view).
 *
 * The page has no times in text. It draws lessons on a grid of 15-minute rows starting at the
 * first label of the time column (08:00), and each lesson gets a `top`/`height` in pixels via an
 * inline `<style>` block. Times are derived from those, using the row height as the scale.
 */
object AgendaParser {
    private const val MINUTES_PER_ROW = 15
    private const val DAYS_PER_WEEK = 7
    // Android's regex engine (ICU) rejects an unescaped '}', unlike desktop Java.
    private val STYLE_RULE = Regex("""#([\w-]+)\s*\{([^\}]*)\}""")
    private val PX_PROPERTY = Regex("""(top|height)\s*:\s*(-?[\d.]+)px""")
    private val WHITESPACE = Regex("""\s+""")
    private val SUBJECT_SEPARATOR = Regex("""\s{2,}""")
    private val MONTHS = listOf("jan", "feb", "mrt", "apr", "mei", "jun", "jul", "aug", "sep", "okt", "nov", "dec")
    private val FILTER_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    /**
     * Strict on purpose: whatever lessons are missing from the result get deleted from the
     * calendar, so anything unexpected (a lesson without a position, a day that can't be read,
     * not exactly one week) throws [AgendaFormatException] instead of being skipped.
     */
    fun parse(html: String, pageUrl: String, zone: ZoneId): AgendaWeek {
        val doc = Jsoup.parse(html, pageUrl)
        val boxes = parseStyleBoxes(doc.select("style").joinToString("\n") { it.data() })

        val dayStart = doc.selectFirst("div.agenda--time li span")?.text()?.trim()
            ?.let { text -> parseOrFail("time label '$text'") { LocalTime.parse(text) } }
            ?: throw AgendaFormatException("time column not found (is the agenda in 'Week' view?)")
        val pxPerMinute = rowHeight(doc, boxes) / MINUTES_PER_ROW

        // Day headers have no year ("ma 5 okt"); take it from the selected date in the filter.
        val selectedDate = doc.selectFirst("input[name=filter:datum]")?.attr("value")
            ?.let { text -> parseOrFail("selected date '$text'") { LocalDate.parse(text, FILTER_DATE) } }
            ?: throw AgendaFormatException("selected date not found")

        val days = mutableListOf<LocalDate>()
        val lessons = mutableListOf<Lesson>()
        for (day in doc.select("div.agenda--day")) {
            val header = day.selectFirst("h4")?.text()
                ?: throw AgendaFormatException("day without a header")
            val date = parseDayHeader(header, selectedDate)
                ?: throw AgendaFormatException("day header '$header' not understood")
            days += date
            val seen = HashMap<String, Int>()
            for (item in day.select("li.agenda--course")) {
                // No position means no time; skipping it would delete the lesson from the calendar.
                val box = boxes[item.id()]
                    ?: throw AgendaFormatException("lesson '${item.textOf("span.is-subject")}' on $date has no position")
                val startMinute = (box.top / pxPerMinute).roundToInt()
                val endMinute = ((box.top + box.height) / pxPerMinute).roundToInt()
                val start = date.atTime(dayStart).plusMinutes(startMinute.toLong()).atZone(zone)
                val end = date.atTime(dayStart).plusMinutes(endMinute.toLong()).atZone(zone)

                // wholeText, because text() collapses the double space between subject and group.
                val (subject, group) = splitSubject(item.selectFirst("span.is-subject")?.wholeText())
                // No room in the key: a room change updates the event, a new time replaces it.
                val baseKey = "$date|${start.toLocalTime()}|$subject|$group"
                val n = seen.merge(baseKey, 1, Int::plus)!!
                lessons += Lesson(
                    id = if (n == 1) baseKey else "$baseKey|$n",
                    subject = subject,
                    startMillis = start.toInstant().toEpochMilli(),
                    endMillis = end.toInstant().toEpochMilli(),
                    location = item.textOf("span.is-location"),
                    teacher = item.textOf("span.is-participant"),
                    group = group,
                    cancelled = isCancelled(item),
                )
            }
        }

        if (days.isEmpty()) throw AgendaFormatException("no days found")
        // The Week view always shows Monday to Sunday (seen in every capture).
        if (days.size != DAYS_PER_WEEK) throw AgendaFormatException("expected $DAYS_PER_WEEK days, found ${days.size}: $days")
        if (days.zipWithNext().any { (a, b) -> b != a.plusDays(1) }) {
            throw AgendaFormatException("days are not consecutive: $days")
        }
        return AgendaWeek(days, lessons)
    }

    /**
     * Height of one 15-minute row, the scale for all times. Every row must have a position and
     * the same height; otherwise the scale is unknown and all times could be off.
     */
    private fun rowHeight(doc: Document, boxes: Map<String, Box>): Double {
        val rows = doc.select("div.agenda--schedule li[id]")
        if (rows.isEmpty()) throw AgendaFormatException("schedule rows not found")
        val heights = rows.map { row ->
            boxes[row.id()]?.height ?: throw AgendaFormatException("schedule row ${row.id()} has no position")
        }
        val height = heights.first()
        if (height <= 0 || heights.any { abs(it - height) > 0.01 }) {
            throw AgendaFormatException("schedule rows have different heights: ${heights.distinct()}")
        }
        return height
    }

    /** Runs [parse], turning a date/time parse error into an [AgendaFormatException] about [what]. */
    private inline fun <T> parseOrFail(what: String, parse: () -> T): T = try {
        parse()
    } catch (e: DateTimeException) {
        throw AgendaFormatException("$what not understood")
    }

    /** "PDO-Expert  KLAS01" -> ("PDO-Expert", "KLAS01"). The portal separates them with 2+ spaces. */
    internal fun splitSubject(raw: String?): Pair<String, String?> {
        val parts = raw.orEmpty().trim().split(SUBJECT_SEPARATOR, limit = 2)
        return parts[0] to parts.getOrNull(1)?.trim()?.replace(WHITESPACE, " ")?.ifEmpty { null }
    }

    /** "ma 5 okt" -> the date in that week nearest to [near] (handles the new-year week). */
    internal fun parseDayHeader(header: String, near: LocalDate): LocalDate? {
        val tokens = header.trim().split(WHITESPACE)
        if (tokens.size < 2) return null
        val day = tokens[tokens.size - 2].toIntOrNull() ?: return null
        val month = MONTHS.indexOf(tokens.last().lowercase().take(3)) + 1
        if (month == 0) return null
        return (near.year - 1..near.year + 1)
            .mapNotNull { runCatching { LocalDate.of(it, month, day) }.getOrNull() }
            .minByOrNull { abs(ChronoUnit.DAYS.between(near, it)) }
    }

    // Guess: no cancelled lesson has shown up in a capture yet.
    private fun isCancelled(item: Element): Boolean {
        val classes = item.className().lowercase()
        return listOf("vervallen", "cancel", "geannuleerd").any { it in classes }
    }

    private fun Element.textOf(selector: String) = selectFirst(selector)?.text()?.trim()?.ifEmpty { null }

    private data class Box(val top: Double, val height: Double)

    private fun parseStyleBoxes(css: String): Map<String, Box> =
        STYLE_RULE.findAll(css).mapNotNull { rule ->
            val props = PX_PROPERTY.findAll(rule.groupValues[2])
                .associate { it.groupValues[1] to it.groupValues[2].toDouble() }
            val top = props["top"] ?: return@mapNotNull null
            val height = props["height"] ?: return@mapNotNull null
            rule.groupValues[1] to Box(top, height)
        }.toMap()
}

class AgendaFormatException(message: String) : Exception("Unexpected agenda page: $message")
