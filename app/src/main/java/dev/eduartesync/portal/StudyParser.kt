package dev.eduartesync.portal

import dev.eduartesync.portal.DutchDates.clean
import dev.eduartesync.source.AgendaFormatException
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Parses the study pages: programmes (/studie/opleiding), study progress (/studievoortgang) and the work list (/werklijst). */
object StudyParser {
    private val LEVEL = Regex("""^Niveau\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val LOCATION = Regex("""\bop\s+(.+?)\.?$""")
    private val PATHWAYS = setOf("BOL", "BBL")
    private val STUDY_MODES = setOf("voltijd", "deeltijd")

    /**
     * One card per programme: a name, then info lines in no fixed order (code, BOL/BBL,
     * "Actief vanaf 1-8-2026 op <location>." or "Van 1-8-2025 tot 31-7-2026 op <location>.",
     * "Niveau 4", "Voltijd") and a status badge.
     */
    fun parseProgrammes(html: String): List<Programme> {
        val doc = Jsoup.parse(html)
        val cards = doc.select(".study-card").ifEmpty { doc.select("div.study") }
        if (cards.isEmpty()) throw AgendaFormatException("programmes not found")
        return cards.mapNotNull { card ->
            val name = clean(card.selectFirst("h2")?.text()).ifEmpty { return@mapNotNull null }
            var code: String? = null
            var pathway: String? = null
            var level: Int? = null
            var studyMode: String? = null
            var start: java.time.LocalDate? = null
            var end: java.time.LocalDate? = null
            var location: String? = null
            val other = mutableListOf<String>()
            for (info in card.select(".study--info")) {
                val text = clean(info.text())
                when {
                    text.isEmpty() -> Unit
                    code == null && text.all { it.isDigit() } -> code = text
                    text.uppercase() in PATHWAYS -> pathway = text.uppercase()
                    LEVEL.containsMatchIn(text) -> level = LEVEL.find(text)!!.groupValues[1].toInt()
                    text.lowercase() in STUDY_MODES -> studyMode = text
                    DutchDates.parseNumericDate(text) != null -> {
                        val dates = DutchDates.numericDates(text)
                        start = dates.firstOrNull()
                        end = if (text.lowercase().startsWith("van ")) dates.getOrNull(1) else null
                        location = LOCATION.find(text)?.groupValues?.get(1)?.trim()
                    }
                    else -> other += text
                }
            }
            Programme(
                name = name,
                code = code,
                status = clean(card.selectFirst(".badge")?.text()).ifEmpty { null },
                pathway = pathway,
                level = level,
                studyMode = studyMode,
                startDate = start,
                endDate = end,
                location = location,
                other = other,
            )
        }
    }

    /**
     * The study points table (Vak, Resultaat, Totaal SP, Behaald SP, Voorlopig SP), found by
     * its headers, plus the legends of the donut charts ("Behaalde studiepunten (12)").
     */
    fun parseProgress(html: String): StudyProgress {
        val doc = Jsoup.parse(html)
        val table = doc.select("table").firstOrNull { t ->
            val headers = headers(t)
            headers.any { it.equals("Vak", true) } && headers.any { it.contains("SP", false) }
        } ?: throw AgendaFormatException("study progress table not found")

        val headers = headers(table)
        fun column(vararg names: String) = headers.indexOfFirst { h -> names.any { h.equals(it, true) } }
        val subjectCol = column("Vak")
        val resultCol = column("Resultaat")
        val totalCol = column("Totaal SP")
        val achievedCol = column("Behaald SP")
        val provisionalCol = column("Voorlopig SP")

        val rows = mutableListOf<StudyPointsRow>()
        var total: StudyPointsRow? = null
        for (tr in table.select("tbody tr, tfoot tr")) {
            val cells = tr.select("td, th").map { clean(it.text()) }
            if (cells.size < 2) continue // "no results" row
            fun cell(index: Int) = cells.getOrNull(index)?.takeIf { it.isNotEmpty() && it != "-" }
            val row = StudyPointsRow(
                subject = cell(subjectCol) ?: continue,
                result = cell(resultCol),
                totalPoints = DutchDates.parseNumber(cell(totalCol)),
                achievedPoints = DutchDates.parseNumber(cell(achievedCol)),
                provisionalPoints = DutchDates.parseNumber(cell(provisionalCol)),
            )
            // Guess: no sample had a totals row yet.
            if (tr.parent()?.tagName() == "tfoot" || row.subject.startsWith("Totaal", ignoreCase = true)) {
                total = row
            } else {
                rows += row
            }
        }

        val charts = doc.select(".card-chart").ifEmpty { doc.select(".chart--multiple") }.mapNotNull(::parseChart)
        return StudyProgress(rows, charts, total)
    }

    /** The work list table (Naam, Status, Type, Deadline, Feedback, Resultaat; maybe Vakken). */
    fun parseWorkList(html: String): List<WorkItem> {
        val doc = Jsoup.parse(html)
        val table = doc.select("table").firstOrNull { t -> headers(t).any { it.equals("Naam", true) } }
            ?: throw AgendaFormatException("work list table not found")
        val headers = headers(table)
        return table.select("tbody tr").mapNotNull { tr ->
            val cells = tr.select("td")
            if (cells.size < 2) return@mapNotNull null // "Er zijn geen resultaten."
            val columns = LinkedHashMap<String, String>()
            headers.forEachIndexed { i, header -> cells.getOrNull(i)?.let { columns[header] = clean(it.text()) } }
            fun col(name: String) = columns.entries.firstOrNull { it.key.equals(name, true) }?.value?.ifEmpty { null }
            WorkItem(
                name = col("Naam") ?: return@mapNotNull null,
                status = col("Status"),
                type = col("Type"),
                deadline = col("Deadline"),
                subjects = col("Vakken") ?: col("Vak"),
                feedback = col("Feedback"),
                result = col("Resultaat"),
                columns = columns,
            )
        }
    }

    private fun headers(table: Element): List<String> =
        (table.selectFirst("thead tr") ?: table.selectFirst("tr"))?.select("th, td")?.map { clean(it.text()) }.orEmpty()

    private fun parseChart(chart: Element): StudyPointsChart? {
        val title = clean(chart.selectFirst(".chartTitel, h2")?.text()).ifEmpty { return null }
        val legend = chart.select(".legend li").map { clean(it.text()) }
        if (legend.isEmpty()) return null
        fun value(prefix: String) = legend.firstOrNull { it.startsWith(prefix, true) }
            ?.substringAfterLast('(')?.let(DutchDates::firstNumber)
        return StudyPointsChart(
            title = title,
            achieved = value("Behaalde"),
            provisional = value("Voorlopige"),
            notAchieved = value("Niet behaalde"),
        )
    }
}
