package dev.eduartesync.portal

import dev.eduartesync.portal.DutchDates.clean
import dev.eduartesync.source.AgendaFormatException
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Parses the results pages: the matrix (/resultaten), a grade's popover, and the overview (/alleresultaten). */
object GradesParser {
    const val MATRIX_TABLE = "table.result-overview--table"
    /** All grade links of the matrix, in the order [PortalGrade.index] counts them. */
    const val GRADE_LINKS = "$MATRIX_TABLE a.lozange"

    /**
     * The grades in the "Matrix" tab. Each row is a subject with its grades; the last column
     * may hold the subject's average (`is-average`). Throws [AgendaFormatException] when the
     * matrix isn't on the page.
     */
    fun parseMatrix(html: String): List<PortalGrade> {
        val doc = Jsoup.parse(html)
        val table = doc.selectFirst(MATRIX_TABLE) ?: throw AgendaFormatException("results matrix not found")
        return table.select("a.lozange").mapIndexedNotNull { index, link ->
            val row = link.closest("tr") ?: return@mapIndexedNotNull null
            val subject = clean(row.selectFirst("td.result-overview--course")?.text())
            val box = link.closest("div.result-overview--grade")
            val value = clean(link.selectFirst("span.grade-structure--grade")?.text() ?: link.text())
            if (value.isEmpty()) return@mapIndexedNotNull null
            PortalGrade(
                subject = subject,
                value = value,
                numeric = DutchDates.parseNumber(value),
                passed = when {
                    box?.hasClass("is-positive") == true -> true
                    box?.hasClass("is-negative") == true -> false
                    else -> null
                },
                isAverage = box?.hasClass("is-average") == true,
                index = index,
            )
        }
    }

    /** The popover of one grade (the popover element itself or a page containing it). */
    fun parseDetail(html: String): GradeDetail {
        val doc = Jsoup.parse(html)
        val popover = doc.selectFirst("div.popover.is-open") ?: doc.selectFirst("div.popover") ?: doc.body()
        val title = clean(popover.selectFirst(".popover-header h2, h2")?.text()).ifEmpty { null }

        val fields = LinkedHashMap<String, String>()
        val attempts = mutableListOf<Attempt>()
        var resultLabel: String? = null
        var resultValue: String? = null
        for (dl in popover.select("dl")) {
            if (dl.hasClass("pogingen") || dl.selectFirst("span.poging-title") != null) {
                attempts += parseAttempts(dl)
                continue
            }
            for (dt in dl.select("dt")) {
                val label = cleanLabel(dt.text())
                val dd = dt.nextElementSibling()?.takeIf { it.tagName() == "dd" } ?: continue
                val value = clean(dd.text())
                fields.putIfAbsent(label, value)
                // The first pair that isn't a known detail is the result itself.
                if (resultLabel == null && KNOWN.none { label.equals(it, ignoreCase = true) }) {
                    resultLabel = label
                    resultValue = value.ifEmpty { null }
                }
            }
        }
        fun field(name: String) = fields.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value?.ifEmpty { null }

        return GradeDetail(
            title = title,
            resultLabel = resultLabel,
            resultValue = resultValue,
            code = field("Toetscode"),
            date = DutchDates.parseDate(field("Datum behaald")) ?: DutchDates.parseNumericDate(field("Datum behaald")),
            type = field("Toetssoort"),
            status = field("Status"),
            weight = DutchDates.firstNumber(field("Weging") ?: field("Weegfactor")),
            period = field("Periode"),
            attempts = attempts,
            fields = fields,
        )
    }

    /**
     * The "Overzicht" tab: every subject with its final result (if any) and status, under the
     * headings "Toetsen" and "Examengericht".
     */
    fun parseOverview(html: String): List<SubjectResult> {
        val doc = Jsoup.parse(html)
        val lists = doc.select("ul.result-structure")
        if (lists.isEmpty()) throw AgendaFormatException("results overview not found")
        return lists.flatMap { list ->
            val group = groupTitle(list)
            list.select("div.results-item").mapNotNull { item ->
                val subject = clean(item.selectFirst(".results-item--link")?.text()).ifEmpty { return@mapNotNull null }
                val final = clean(item.selectFirst(".student-progress--average")?.text()).ifEmpty { null }
                SubjectResult(
                    subject = subject,
                    finalValue = final,
                    numeric = DutchDates.parseNumber(final),
                    status = clean(item.selectFirst(".student-progress--achieved-label")?.text()).ifEmpty { null },
                    group = group,
                )
            }
        }
    }

    private val KNOWN = listOf(
        "Toetscode", "Datum behaald", "Toetssoort", "Status", "Weging", "Weegfactor", "Periode", "Omschrijving",
    )

    /** "Eindresultaat ( Hoogste geldt )" -> "Eindresultaat (Hoogste geldt)". */
    private fun cleanLabel(text: String) = clean(text).replace("( ", "(").replace(" )", ")")

    private fun parseAttempts(dl: Element): List<Attempt> =
        dl.select("span.poging-title").map { title ->
            var next = title.nextElementSibling()
            while (next != null && !next.hasClass("poging-resultaat") && !next.hasClass("poging-title")) {
                next = next.nextElementSibling()
            }
            Attempt(clean(title.text()), clean(next?.takeIf { it.hasClass("poging-resultaat") }?.text()))
        }

    private fun groupTitle(list: Element): String? {
        var previous = list.previousElementSibling()
        while (previous != null) {
            if (previous.hasClass("result-structure--title") || previous.tagName() == "h2") {
                return clean(previous.text()).ifEmpty { null }
            }
            previous = previous.previousElementSibling()
        }
        return list.closest(".grade-structure")?.selectFirst(".result-structure--title")?.text()?.let(::clean)
    }
}
