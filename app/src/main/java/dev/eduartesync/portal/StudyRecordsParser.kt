package dev.eduartesync.portal

import dev.eduartesync.portal.DutchDates.clean
import dev.eduartesync.source.AgendaFormatException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate

/**
 * Parses the record pages under Studie: Dossier (/studie/dossier), Documenten
 * (/studie/documenten), Vakken (/studie/archief) and Groepen (/studie/groepen).
 *
 * Each throws [AgendaFormatException] when the page doesn't have its list or table at all, and
 * returns an empty list when the portal says there is nothing ("Er zijn geen ...").
 */
object StudyRecordsParser {
    /** "Donderdag 5 november door J. Jansen", or with a year: "Maandag 26 januari 2025 door ...". */
    private val DOSSIER_LINE = Regex(
        """^(?:\p{L}+\s+)?(\d{1,2})\s+(\p{L}+)\.?(?:\s+(\d{4}))?(?:\s+door\s+(.+))?$""",
        RegexOption.IGNORE_CASE,
    )
    private val BRACKETS = Regex("""^(.*?)\s*\((.*)\)\s*$""")

    /**
     * The dossier cards, newest first as the portal lists them. The dates have no year, so
     * each gets the latest year that puts it on or before [today] and the entry above it.
     */
    fun parseDossier(html: String, today: LocalDate): List<DossierEntry> {
        val doc = Jsoup.parse(html)
        val cards = doc.select(".dossier-card")
        if (cards.isEmpty()) {
            if (doc.selectFirst(".dossier-table, table") != null || saysNothing(doc)) return emptyList()
            throw AgendaFormatException("dossier not found")
        }
        var latest = today
        return cards.mapNotNull { card ->
            val title = clean(card.selectFirst(".dossier-card--title, h3")?.text()).ifEmpty { return@mapNotNull null }
            val line = clean(card.selectFirst(".dossier-card--text, small")?.text())
            val match = DOSSIER_LINE.find(line)
            val date = match?.let { dossierDate(it, latest) }
            if (date != null) latest = date
            val brackets = BRACKETS.find(title)?.takeIf { it.groupValues[1].isNotBlank() }
            DossierEntry(
                title = title,
                category = brackets?.groupValues?.get(1)?.let(::clean),
                subject = brackets?.groupValues?.get(2)?.let(::clean)?.ifEmpty { null } ?: title,
                date = date,
                author = match?.groupValues?.get(4)?.let(::clean)?.ifEmpty { null }
                    ?: line.substringAfter(" door ", "").let(::clean).ifEmpty { null },
            )
        }
    }

    private fun dossierDate(match: MatchResult, notAfter: LocalDate): LocalDate? {
        val day = match.groupValues[1].toInt()
        val month = DutchDates.month(match.groupValues[2]) ?: return null
        match.groupValues[3].toIntOrNull()?.let { year -> return runCatching { LocalDate.of(year, month, day) }.getOrNull() }
        return (notAfter.year downTo notAfter.year - 2)
            .mapNotNull { runCatching { LocalDate.of(it, month, day) }.getOrNull() }
            .firstOrNull { !it.isAfter(notAfter) }
    }

    /**
     * The documents table (Documenttype, Datum, Link). [pageUrl] is the address of the page,
     * to make the links absolute.
     */
    fun parseDocuments(html: String, pageUrl: String): List<PortalDocument> {
        val doc = Jsoup.parse(html, pageUrl)
        val table = doc.select("table").firstOrNull { t -> headers(t).any { it.equals("Documenttype", true) } }
            ?: if (saysNothing(doc)) return emptyList() else throw AgendaFormatException("documents table not found")
        val headers = headers(table)
        val typeCol = headers.indexOfFirst { it.equals("Documenttype", true) }
        val dateCol = headers.indexOfFirst { it.equals("Datum", true) }
        val linkCol = headers.indexOfFirst { it.equals("Link", true) }
        return table.select("tbody tr").mapNotNull { tr ->
            val cells = tr.children().filter { it.tagName() == "td" }
            if (cells.size < 2) return@mapNotNull null
            val link = cells.getOrNull(linkCol)?.selectFirst("a[href]") ?: tr.selectFirst("a[href]")
            val name = clean(link?.text()).ifEmpty { clean(cells.getOrNull(linkCol)?.text()) }
            PortalDocument(
                type = clean(cells.getOrNull(typeCol)?.text()),
                date = DutchDates.parseNumericDate(cells.getOrNull(dateCol)?.text()),
                name = name.ifEmpty { return@mapNotNull null },
                url = link?.absUrl("href")?.ifEmpty { null },
            )
        }
    }

    /** The subjects table (Vak, Code, Afkorting, Begindatum, Einddatum); it can have several tbody parts. */
    fun parseSubjects(html: String): List<PortalSubject> {
        val doc = Jsoup.parse(html)
        val table = doc.select("table").firstOrNull { t ->
            val headers = headers(t)
            headers.any { it.equals("Vak", true) } && headers.any { it.equals("Code", true) }
        } ?: if (saysNothing(doc)) return emptyList() else throw AgendaFormatException("subjects table not found")
        val headers = headers(table)
        fun column(name: String) = headers.indexOfFirst { it.equals(name, true) }
        val nameCol = column("Vak")
        val codeCol = column("Code")
        val abbreviationCol = column("Afkorting")
        val startCol = column("Begindatum")
        val endCol = column("Einddatum")
        return table.select("tbody tr").mapNotNull { tr ->
            val cells = tr.children().filter { it.tagName() == "td" }.map { clean(it.text()) }
            if (cells.size < 2) return@mapNotNull null
            fun cell(index: Int) = cells.getOrNull(index)?.takeIf { it.isNotEmpty() && it != "-" }
            PortalSubject(
                name = cell(nameCol) ?: return@mapNotNull null,
                code = cell(codeCol),
                abbreviation = cell(abbreviationCol),
                startDate = DutchDates.parseNumericDate(cell(startCol)),
                endDate = DutchDates.parseNumericDate(cell(endCol)),
            )
        }
    }

    /** The groups list: per group its name, the number of students, and their names. */
    fun parseGroups(html: String): List<PortalGroup> {
        val doc = Jsoup.parse(html)
        val body = doc.selectFirst(".students-list-table, .students-list-body")
        val rows = doc.select(".students-list-body > .task")
        if (rows.isEmpty()) {
            if (body != null || saysNothing(doc)) return emptyList()
            throw AgendaFormatException("groups not found")
        }
        return rows.mapNotNull { row ->
            val summary = row.selectFirst(".task--summary") ?: row
            val name = clean(summary.selectFirst("p")?.text()).ifEmpty { return@mapNotNull null }
            PortalGroup(
                name = name,
                size = clean(summary.selectFirst(".group--count, .task--course, small")?.text()).toIntOrNull(),
                members = row.select(".popout .popout--body").map { clean(it.text()) }.filter { it.isNotEmpty() },
            )
        }
    }

    private fun headers(table: Element): List<String> =
        (table.selectFirst("thead tr") ?: table.selectFirst("tr"))?.select("th, td")?.map { clean(it.text()) }.orEmpty()

    /**
     * The portal's empty page: a `div.page-is-empty` with "Er zijn geen ...", "Er zijn op dit
     * moment geen ..." or "Er zijn momenteel geen ...".
     */
    private fun saysNothing(doc: Document) =
        doc.selectFirst(EMPTY_PAGE) != null || NOTHING.containsMatchIn(doc.text())

    const val EMPTY_PAGE = ".page-is-empty"
    private val NOTHING = Regex("""Er zijn (?:\S+\s+){0,4}?geen\b""", RegexOption.IGNORE_CASE)
}
