package dev.eduartesync.portal

import dev.eduartesync.portal.DutchDates.clean
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Parses the chart pages under Presentie: the "Presentie" tab (/presentie/presentie) and the
 * "Absentie" tab (/presentie/absentie). Both show donut chart cards and the same sidebar filter.
 *
 * A card is `div.chart--multiple` with an `h2.chartTitel`, a canvas whose attributes hold the
 * middle of the donut (number="88%" centertext="present"), and a legend that the page's script
 * fills in ("Aanwezig (88)"). Right after a page load the legend may not be there yet, so the
 * numbers are then taken from the script that draws the chart.
 */
object PresenceChartsParser {
    /** One chart card; also what the per-subject loop copies out of the page. */
    const val CHART_CARD = "div.chart--multiple"

    private val LEGEND_COUNT = Regex("""^(.*?)\s*\(\s*(-?\d+(?:[.,]\d+)?)\s*\)\s*$""")

    /** EduarteChartJs.drawChartJsDonutChart('id', { labels: [...], datasets: [ { data: [...] ... */
    private val DRAW_CALL = Regex(
        """drawChartJsDonutChart\(\s*'([^']+)'\s*,\s*\{\s*labels:\s*\[([^\]]*)]\s*,\s*datasets:\s*\[\s*\{\s*data:\s*\[([^\]]*)]""",
    )
    private val QUOTED = Regex(""""((?:[^"\\]|\\.)*)"""")

    /** Every chart card in [html], in page order. Cards without a title are skipped. */
    fun parseCharts(html: String): List<PresenceChart> {
        val doc = Jsoup.parse(html)
        val drawn = drawnData(doc)
        return doc.select(CHART_CARD).mapNotNull { parseCard(it, drawn) }
    }

    /** The "Vak" and "Periode" options of the sidebar filter; empty lists when it isn't there. */
    fun parseFilters(html: String): PresenceFilters {
        val doc = Jsoup.parse(html)
        return PresenceFilters(
            subjects = options(filterSelect(doc, "Vak", 0)),
            periods = options(filterSelect(doc, "Periode", 1)),
        )
    }

    private fun parseCard(card: Element, drawn: Map<String, List<ChartItem>>): PresenceChart? {
        val title = clean(card.selectFirst(".chartTitel, h2")?.text()).ifEmpty { return null }
        val canvas = card.selectFirst("canvas")
        val centerValue = clean(canvas?.attr("number")).ifEmpty { null }
        val legend = card.select(".legend li").mapNotNull { legendItem(clean(it.text())) }
        val items = legend.ifEmpty { canvas?.id()?.let { drawn[it] }.orEmpty() }
        return PresenceChart(
            title = title,
            centerValue = centerValue,
            centerNumber = DutchDates.firstNumber(centerValue),
            centerText = clean(canvas?.attr("centertext")).ifEmpty { null },
            items = items,
        )
    }

    /** "Aanwezig (137)" -> Aanwezig, 137. */
    internal fun legendItem(text: String): ChartItem? {
        val match = LEGEND_COUNT.find(text) ?: return null
        val count = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
        return ChartItem(clean(match.groupValues[1]), count)
    }

    /** Legend items per canvas id, from the inline scripts that draw the charts. */
    private fun drawnData(doc: Document): Map<String, List<ChartItem>> {
        val result = HashMap<String, List<ChartItem>>()
        for (script in doc.select("script")) {
            for (match in DRAW_CALL.findAll(script.data())) {
                val labels = QUOTED.findAll(match.groupValues[2]).map { it.groupValues[1] }.toList()
                val values = match.groupValues[3].split(',').map { it.trim().toDoubleOrNull() }
                if (labels.size != values.size || values.any { it == null }) continue
                result[match.groupValues[1]] = labels.zip(values) { label, value -> ChartItem(clean(label), value!!) }
            }
        }
        return result
    }

    /** The select under the heading [label] ("Vak"), or else the [index]th filter field. */
    private fun filterSelect(doc: Document, label: String, index: Int): Element? =
        doc.select("select").firstOrNull { select ->
            // The heading sits in the select's grandparent: div.mb-4 > h5 + div.form-select > select.
            select.parents().take(2).any { clean(it.selectFirst("h5")?.text()).equals(label, ignoreCase = true) }
        } ?: doc.selectFirst("select[name*=inputFields:$index:]")

    private fun options(select: Element?): List<FilterOption> =
        select?.select("option").orEmpty()
            .map { FilterOption(it.attr("value"), clean(it.text())) }
            .filter { it.value.isNotEmpty() && it.name.isNotEmpty() }
}
