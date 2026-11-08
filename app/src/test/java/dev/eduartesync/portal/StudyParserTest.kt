package dev.eduartesync.portal

import dev.eduartesync.source.AgendaFormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StudyParserTest {

    /** Programme cards as on /studie/opleiding, with made-up programmes and places. */
    private val programmes = """
        <html><body><div class="content">
          <div class="l-small study"><div class="panel study-card p-3">
            <h2 class="h2">Applicatieontwikkelaar</h2>
            <span class="study--info">12345</span>
            <span class="badge badge-success">actief</span>
            <span class="study--info">BOL</span>
            <span class="study--info">Actief vanaf 1-8-2026 op Utrecht, Schoolstraat 1.</span>
            <span class="study--info">Niveau 4</span>
            <span class="study--info">Voltijd</span>
          </div></div>
          <div class="l-small study"><div class="panel study-card p-3">
            <h2 class="h2">Medewerker ICT</h2>
            <span class="study--info">54321</span>
            <span class="study--info">BBL</span>
            <span class="badge">beëindigd</span>
            <span class="study--info">Van 1-8-2025 tot 31-7-2026 op Utrecht, Schoolstraat 1.</span>
            <span class="study--info">Niveau 3</span>
            <span class="study--info">Deeltijd</span>
            <span class="study--info">Iets anders</span>
          </div></div>
        </div></body></html>
    """.trimIndent()

    @Test
    fun readsProgrammes() {
        val result = StudyParser.parseProgrammes(programmes)
        assertEquals(
            Programme(
                name = "Applicatieontwikkelaar", code = "12345", status = "actief", pathway = "BOL", level = 4,
                studyMode = "Voltijd", startDate = LocalDate.of(2026, 8, 1), endDate = null,
                location = "Utrecht, Schoolstraat 1", other = emptyList(),
            ),
            result[0],
        )
        val old = result[1]
        assertEquals("beëindigd", old.status)
        assertEquals("BBL", old.pathway)
        assertEquals(3, old.level)
        assertEquals(LocalDate.of(2025, 8, 1), old.startDate)
        assertEquals(LocalDate.of(2026, 7, 31), old.endDate)
        assertEquals("Utrecht, Schoolstraat 1", old.location)
        assertEquals(listOf("Iets anders"), old.other)
    }

    @Test(expected = AgendaFormatException::class)
    fun missingProgrammesIsAFormatError() {
        StudyParser.parseProgrammes("<html><body></body></html>")
    }

    /** The /studievoortgang structure, with made-up subjects and points. */
    private val progress = """
        <html><body>
        <div class="chart--donut">
          <div class="chart--multiple"><div class="card-chart chart">
            <div class="card-header"><h2 class="chartTitel">Totale studievoortgang</h2></div>
            <div class="card-body"><canvas></canvas>
              <div class="legend"><ul>
                <li><svg></svg> Behaalde studiepunten (12)</li>
                <li><svg></svg> Voorlopige studiepunten (2,5)</li>
                <li><svg></svg> Niet behaalde studiepunten (0)</li>
              </ul></div></div>
          </div></div>
          <div class="chart--multiple"><div class="card-chart chart">
            <div class="card-header"><h2 class="chartTitel">Studievoortgang fase</h2></div>
            <div class="card-body"><div class="legend"><ul>
              <li> Behaalde studiepunten (0)</li><li> Voorlopige studiepunten (0)</li><li> Niet behaalde studiepunten (0)</li>
            </ul></div></div>
          </div></div>
        </div>
        <table class="table s5-table">
          <thead><tr>
            <th class="table--sortable"><span> Vak <svg></svg></span></th><th>Resultaat</th>
            <th class="table--sortable"><span> Totaal SP </span></th>
            <th class="table--sortable"><span> Behaald SP </span></th>
            <th class="table--sortable"><span> Voorlopig SP </span></th>
          </tr></thead>
          <tbody class="data">
            <tr><td>LES-Wiskunde</td><td><span class="badge">-</span></td><td>0</td><td>0</td><td>0</td></tr>
            <tr><td>LES-Engels</td><td><span class="badge">7,5</span></td><td>4</td><td>4</td><td>0</td></tr>
          </tbody>
          <tfoot><tr><td>Totaal</td><td></td><td>4</td><td>4</td><td>0</td></tr></tfoot>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun readsStudyProgress() {
        val result = StudyParser.parseProgress(progress)
        assertEquals(
            listOf(
                StudyPointsRow("LES-Wiskunde", null, 0.0, 0.0, 0.0),
                StudyPointsRow("LES-Engels", "7,5", 4.0, 4.0, 0.0),
            ),
            result.rows,
        )
        assertEquals(StudyPointsRow("Totaal", null, 4.0, 4.0, 0.0), result.total)
        assertEquals(
            listOf(
                StudyPointsChart("Totale studievoortgang", 12.0, 2.5, 0.0),
                StudyPointsChart("Studievoortgang fase", 0.0, 0.0, 0.0),
            ),
            result.charts,
        )
    }

    @Test(expected = AgendaFormatException::class)
    fun missingProgressTableIsAFormatError() {
        StudyParser.parseProgress("<table><tr><th>Naam</th></tr></table>")
    }

    private fun workList(rows: String) = """
        <table class="table s5-table tblClick">
          <thead><tr>
            <th class="table--sortable"><span> Naam <svg></svg></span></th><th>Status</th>
            <th class="s5-hide-small table--sortable"><span> Type </span></th>
            <th class="s5-hide-small table--sortable is-sorted"><span> Deadline </span></th>
            <th class="s5-hide-medium">Feedback</th><th class="s5-hide-large">Resultaat</th>
          </tr></thead>
          <tbody>$rows</tbody>
        </table>
    """

    @Test
    fun emptyWorkList() {
        val items = StudyParser.parseWorkList(workList("""<tr><td colspan="6">Er zijn geen resultaten.</td></tr>"""))
        assertTrue(items.isEmpty())
    }

    @Test
    fun readsWorkListRows() {
        val items = StudyParser.parseWorkList(
            workList("<tr><td>Verslag H2</td><td>Ingeleverd</td><td>Opdracht</td><td>12-11-2026</td><td></td><td>8,0</td></tr>")
        )
        val item = items.single()
        assertEquals("Verslag H2", item.name)
        assertEquals("Ingeleverd", item.status)
        assertEquals("Opdracht", item.type)
        assertEquals("12-11-2026", item.deadline)
        assertNull(item.feedback)
        assertEquals("8,0", item.result)
        assertNull(item.subjects)
    }
}
