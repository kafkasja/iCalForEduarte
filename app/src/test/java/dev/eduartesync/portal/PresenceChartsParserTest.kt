package dev.eduartesync.portal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceChartsParserTest {

    private fun card(id: String, title: String, number: String, text: String, legend: List<String>?) = """
        <div class="chart--multiple" id="c$id">
          <div class="card-chart chart">
            <div class="card-header"><h2 class="chartTitel">$title</h2></div>
            <div class="card-body">
              <canvas id="$id" number="$number" centertext="$text" nodatalabel="Geen waarnemingen"></canvas>
              ${legend?.let { items -> "<div class=\"legend\"><ul>" + items.joinToString("") { "<li><svg></svg> $it</li>" } + "</ul></div>" } ?: ""}
            </div>
          </div>
        </div>
    """.trimIndent()

    /** The Presentie tab with made-up numbers, legends drawn. */
    private val presencePage = """
        <html><body>
        <aside><form><fieldset>
          <div class="mb-4"><h5>Vak</h5><div class="form-select input-group">
            <select name="fieldset:fieldSetMarkup:inputFields:0:controlGroup:controlGroup_body:formField">
              <option selected="selected" value="">Vak...</option>
              <option value="0">Biologie</option>
              <option value="1">Frans</option>
              <option value="2">Frans</option>
              <option value="3">Tekenen </option>
            </select></div></div>
          <div class="mb-4"><h5>Periode</h5><div class="form-select input-group">
            <select name="fieldset:fieldSetMarkup:inputFields:1:controlGroup:controlGroup_body:formField">
              <option selected="selected" value="0">Huidig schooljaar</option>
              <option value="1">Huidige maand</option>
              <option value="5">Stageperiode</option>
            </select></div></div>
          <div class="mb-4"><h5>Afspraken</h5><div class="form-select input-group">
            <select name="fieldset:fieldSetMarkup:inputFields:2:controlGroup:controlGroup_body:formField">
              <option selected="selected" value="0">Alle afspraken</option>
            </select></div></div>
        </fieldset></form></aside>
        <div class="content"><div class="chart--donut">
          ${card("a1", "Presentie", "40.5", "uur present", listOf("Afwezig (4,5)", "Aanwezig (40.5)"))}
          ${card("a2", "Percentage", "90%", "present", listOf("Afwezig (10)", "Aanwezig (90)"))}
          ${card("a3", "Stage", "0.0", "uur goedgekeurd", listOf("Nog gepland (0)", "Geregistreerd (0)", "Goedgekeurd (0)"))}
        </div></div>
        </body></html>
    """.trimIndent()

    @Test
    fun readsEveryChartCard() {
        val charts = PresenceChartsParser.parseCharts(presencePage)
        assertEquals(listOf("Presentie", "Percentage", "Stage"), charts.map { it.title })

        val hours = charts[0]
        assertEquals("40.5", hours.centerValue)
        assertEquals(40.5, hours.centerNumber!!, 0.001)
        assertEquals("uur present", hours.centerText)
        assertEquals(listOf(ChartItem("Afwezig", 4.5), ChartItem("Aanwezig", 40.5)), hours.items)
        assertEquals(40.5, hours.count("aanwezig")!!, 0.001)
        assertTrue(hours.hasData)

        assertEquals("90%", charts[1].centerValue)
        assertEquals(90.0, charts[1].centerNumber!!, 0.001)
    }

    @Test
    fun allZeroLegendMeansNoData() {
        val stage = PresenceChartsParser.parseCharts(presencePage)[2]
        assertFalse(stage.hasData)
        assertEquals(3, stage.items.size)
    }

    @Test
    fun takesTheNumbersFromTheDrawingScriptWhenTheLegendIsMissing() {
        val html = """
            <html><body>
            ${card("x1", "Urenoverzicht", "7.0", "uur absent", legend = null)}
            <script>
            Wicket.Event.add(window, "domready", function(event) {
            EduarteChartJs.drawChartJsDonutChart('x1', { labels: ["Geoorloofd", "Ongeoorloofd"],  datasets: [ { data: [5.0, 2.0], backgroundColor: ["#111111", "#222222"] }] }, null, '7.0', 'uur absent', 'Geen absentiemeldingen');;
            });
            </script>
            </body></html>
        """.trimIndent()
        val chart = PresenceChartsParser.parseCharts(html).single()
        assertEquals(listOf(ChartItem("Geoorloofd", 5.0), ChartItem("Ongeoorloofd", 2.0)), chart.items)
    }

    @Test
    fun withoutLegendOrScriptOnlyTheMiddleNumberCounts() {
        val empty = PresenceChartsParser.parseCharts("<html><body>${card("y", "Presentie", "0.0", "uur present", null)}</body></html>").single()
        assertTrue(empty.items.isEmpty())
        assertFalse(empty.hasData)
        val some = PresenceChartsParser.parseCharts("<html><body>${card("y", "Presentie", "3.0", "uur present", null)}</body></html>").single()
        assertTrue(some.hasData)
    }

    @Test
    fun readsTheFilterOptionsWithoutThePlaceholder() {
        val filters = PresenceChartsParser.parseFilters(presencePage)
        assertEquals(
            listOf(FilterOption("0", "Biologie"), FilterOption("1", "Frans"), FilterOption("2", "Frans"), FilterOption("3", "Tekenen")),
            filters.subjects,
        )
        assertEquals(listOf("0", "1", "5"), filters.periods.map { it.value })
        assertEquals("Huidig schooljaar", filters.periods[0].name)
    }

    @Test
    fun noFilterGivesEmptyLists() {
        val filters = PresenceChartsParser.parseFilters("<html><body></body></html>")
        assertTrue(filters.subjects.isEmpty())
        assertTrue(filters.periods.isEmpty())
    }

    @Test
    fun legendLines() {
        assertEquals(ChartItem("Aanwezig", 137.0), PresenceChartsParser.legendItem("Aanwezig (137)"))
        assertEquals(ChartItem("Nog gepland", 2.25), PresenceChartsParser.legendItem("Nog gepland (2,25)"))
        assertNull(PresenceChartsParser.legendItem("Aanwezig"))
    }
}
