package dev.eduartesync.portal

import dev.eduartesync.portal.DutchDates.clean
import dev.eduartesync.source.AgendaFormatException
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.LocalTime

/**
 * Parses the attendance page (/presentie/presentie.overzicht).
 *
 * The page has a period header ("Vanaf 02-11-2026 tot en met 08-11-2026"), totals per category,
 * and per week the days ("wo") with numbered 15-minute blocks. Block n starts at
 * 08:00 + (n - 1) * 15 minutes; each block repeats the lesson ("11:45 - 12:45 Mentoruur  GROUP -
 * teacher") and has its own status. Consecutive blocks of the same lesson and status are merged.
 */
object AttendanceParser {
    const val PERIOD_HEADER = ".abstract-absentie-page .week-header h2"

    private val PERIOD = Regex("""Vanaf\s+(\S+)\s+tot\s+en\s+met\s+(\S+)""", RegexOption.IGNORE_CASE)
    private val LESSON = Regex("""^(\d{1,2}:\d{2})\s*-\s*(\d{1,2}:\d{2})\s+(.*)$""", RegexOption.DOT_MATCHES_ALL)
    private val BLOCK_TIME = Regex("""\(\s*(\d{1,2}:\d{2})\s*-\s*(\d{1,2}:\d{2})\s*\)""")
    private val GROUP_SEPARATOR = Regex("""\s{2,}""")
    private val FIRST_BLOCK = LocalTime.of(8, 0)
    private const val BLOCK_MINUTES = 15L

    fun parse(html: String): Attendance {
        val doc = Jsoup.parse(html)
        val header = doc.selectFirst(PERIOD_HEADER)?.text()
            ?: doc.select("h2").map { it.text() }.firstOrNull { PERIOD.containsMatchIn(it) }
        val (from, to) = parsePeriod(header) ?: throw AgendaFormatException("attendance period not found")

        val totals = doc.select("div.presence-total").mapNotNull(::parseTotal)
        val blocks = doc.select("div.content--part").flatMap { parseWeek(it, from) }
        return Attendance(from, to, totals, merge(blocks))
    }

    /** "Vanaf 02-11-2026 tot en met 08-11-2026" -> the two dates. */
    fun parsePeriod(header: String?): Pair<LocalDate, LocalDate>? {
        val match = PERIOD.find(header ?: return null) ?: return null
        val from = DutchDates.parseNumericDate(match.groupValues[1]) ?: return null
        val to = DutchDates.parseNumericDate(match.groupValues[2]) ?: return null
        return from to to
    }

    private fun parseTotal(box: Element): AttendanceTotal? {
        val category = clean(box.selectFirst(".presence-total--title")?.text()).ifEmpty { return null }
        var lessonHours: Double? = null
        var hours: Double? = null
        for (title in box.select(".presence-total--content-title")) {
            val value = title.nextElementSibling()
                ?.takeIf { it.hasClass("presence-total--content-text") }
                ?.let { DutchDates.firstNumber(it.selectFirst("label")?.text() ?: it.text()) }
            when (clean(title.text()).lowercase()) {
                "aantal lesuren" -> lessonHours = value
                "aantal uren" -> hours = value
            }
        }
        return AttendanceTotal(category, lessonHours, hours)
    }

    private data class Block(
        val date: LocalDate,
        val number: Int?,
        val start: LocalTime?,
        val end: LocalTime?,
        val description: String,
        val status: String,
        val reported: String?,
    )

    private fun parseWeek(part: Element, periodStart: LocalDate): List<Block> {
        val spans = part.select(".week-header h2 > span:not(.is-soft)")
        val weekStart = DutchDates.parseDayMonth(spans.firstOrNull()?.text(), periodStart) ?: return emptyList()
        val blocks = mutableListOf<Block>()
        for (day in part.select("div.student-absence--day")) {
            val weekday = DutchDates.weekday(day.selectFirst(".student-absence--day-title")?.text().orEmpty())
                ?: continue
            val date = weekStart.plusDays(((weekday.value - weekStart.dayOfWeek.value + 7) % 7).toLong())
            for (hour in day.select("div.student-absence--hour")) {
                val number = hour.selectFirst(".student-absence--hour-title")?.text()?.trim()?.toIntOrNull()
                for (course in hour.select("div.student-absence--course")) {
                    val rawStatus = clean(course.selectFirst("strong")?.text() ?: course.selectFirst("p")?.text())
                    val blockTime = BLOCK_TIME.find(rawStatus)
                    val start = blockTime?.let { DutchDates.parseTime(it.groupValues[1]) }
                        ?: number?.let { FIRST_BLOCK.plusMinutes((it - 1) * BLOCK_MINUTES) }
                    val end = blockTime?.let { DutchDates.parseTime(it.groupValues[2]) }
                        ?: start?.plusMinutes(BLOCK_MINUTES)
                    // wholeText keeps the double space between lesson and group.
                    val description = course.select("small")
                        .firstOrNull { !it.hasClass("student-absence--reported") }
                        ?.wholeText()?.replace(' ', ' ')?.trim().orEmpty()
                    blocks += Block(
                        date = date,
                        number = number,
                        start = start,
                        end = end,
                        description = description,
                        status = clean(rawStatus.replace(BLOCK_TIME, "")),
                        reported = clean(course.selectFirst("small.student-absence--reported")?.text()).ifEmpty { null },
                    )
                }
            }
        }
        return blocks
    }

    /** Merges consecutive blocks of the same lesson with the same status into one entry. */
    private fun merge(blocks: List<Block>): List<AttendanceEntry> {
        val entries = mutableListOf<AttendanceEntry>()
        var run = mutableListOf<Block>()
        fun flush() {
            if (run.isEmpty()) return
            entries += toEntry(run)
            run = mutableListOf()
        }
        val sorted = blocks.sortedWith(compareBy<Block> { it.date }.thenBy { it.start ?: LocalTime.MIN })
        for (block in sorted) {
            val last = run.lastOrNull()
            val continues = last != null &&
                last.date == block.date &&
                last.description == block.description &&
                last.status == block.status &&
                last.reported == block.reported &&
                (last.end == null || block.start == null || !block.start.isAfter(last.end))
            if (!continues) flush()
            run += block
        }
        flush()
        return entries
    }

    private fun toEntry(run: List<Block>): AttendanceEntry {
        val first = run.first()
        val match = LESSON.find(first.description)
        val lessonStart = match?.let { DutchDates.parseTime(it.groupValues[1]) }
        val lessonEnd = match?.let { DutchDates.parseTime(it.groupValues[2]) }
        val (lesson, group, teacher) = splitDescription(match?.groupValues?.get(3) ?: first.description)
        return AttendanceEntry(
            date = first.date,
            start = run.mapNotNull { it.start }.minOrNull() ?: lessonStart ?: LocalTime.MIN,
            end = run.mapNotNull { it.end }.maxOrNull() ?: lessonEnd ?: LocalTime.MIN,
            lessonStart = lessonStart,
            lessonEnd = lessonEnd,
            lesson = lesson,
            group = group,
            teacher = teacher,
            status = first.status,
            reported = first.reported,
        )
    }

    /**
     * "Mentoruur  KLAS01 - dhr. Jansen" -> (Mentoruur, KLAS01, dhr. Jansen). The portal puts two
     * spaces between lesson and group, and " - " before the teacher.
     */
    internal fun splitDescription(text: String): Triple<String, String?, String?> {
        val parts = text.trim().split(GROUP_SEPARATOR, limit = 2)
        if (parts.size == 2) {
            val rest = parts[1].split(" - ", limit = 2)
            return Triple(
                clean(parts[0]),
                clean(rest[0]).ifEmpty { null },
                rest.getOrNull(1)?.let(::clean)?.ifEmpty { null },
            )
        }
        val dash = text.lastIndexOf(" - ")
        return if (dash < 0) Triple(clean(text), null, null)
        else Triple(clean(text.substring(0, dash)), null, clean(text.substring(dash + 3)).ifEmpty { null })
    }
}
