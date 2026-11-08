package dev.eduartesync.portal

import android.content.Context
import android.os.SystemClock
import android.util.Log
import dev.eduartesync.presence.labelDuplicates
import dev.eduartesync.presence.presenceNumbers
import dev.eduartesync.source.AgendaFormatException
import dev.eduartesync.source.PortalBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

/** The charts of the "Presentie" and "Absentie" tabs for one period, and the subjects in the filter. */
data class OverallPresence(
    val period: PresencePeriod,
    val presence: List<PresenceChart>,
    val absence: List<PresenceChart>,
    val filters: PresenceFilters,
)

/** The "Presentie" charts with only one subject chosen in the filter. */
data class SubjectCharts(
    /** The subject's name, with "(2)" added when the portal lists that name more than once. */
    val label: String,
    val option: FilterOption,
    val charts: List<PresenceChart>,
)

/** What [PresenceSource.fetchPerSubject] got through before it finished or ran out of time. */
data class PerSubjectPresence(
    /** Only subjects with registered lessons, in the portal's order. */
    val subjects: List<SubjectCharts>,
    /** How many subjects were looked at, and how many the filter has. */
    val read: Int,
    val total: Int,
)

/**
 * Reads the chart pages under Presentie (/presentie/presentie and /presentie/absentie).
 *
 * Choosing a subject or period in their filter makes the portal redraw the charts in place
 * with a Wicket AJAX call (no page load), so after each choice this waits for that call to
 * complete, see [chooseScript].
 *
 * Throws [dev.eduartesync.source.SessionExpiredException], [IOException] or
 * [AgendaFormatException] like the agenda does.
 */
class PresenceSource(private val context: Context) {

    /** Presence and absence for the whole selection (all subjects) in [period]. */
    suspend fun fetchOverall(period: PresencePeriod): OverallPresence = withPortalBrowser(context) { browser ->
        openCharts(browser, PortalPages.PRESENCE_CHARTS, period)
        val presence = browser.parseCurrent(PresenceChartsParser::parseCharts)
        val filters = browser.parseCurrent(PresenceChartsParser::parseFilters)

        openCharts(browser, PortalPages.ABSENCE_CHARTS, period)
        val absence = browser.parseCurrent(PresenceChartsParser::parseCharts)
        Log.i(PortalPages.TAG, "Presence $period: ${presence.size} + ${absence.size} charts, ${filters.subjects.size} subjects")
        OverallPresence(period, presence, absence, filters)
    }

    /**
     * Chooses every subject of the filter in turn (after setting [period] once) and reads the
     * charts for it. Subjects without registered lessons ("Geen waarnemingen") are left out.
     * [onProgress] is called on the main thread after each subject with what was found so far.
     *
     * Stops after [BUDGET_MS] or after a few subjects in a row didn't update, and returns
     * what it has; [PerSubjectPresence.read] then is less than [PerSubjectPresence.total].
     */
    suspend fun fetchPerSubject(
        period: PresencePeriod,
        onProgress: (PerSubjectPresence) -> Unit = {},
    ): PerSubjectPresence = withPortalBrowser(context) { browser ->
        openCharts(browser, PortalPages.PRESENCE_CHARTS, period)
        val options = browser.parseCurrent(PresenceChartsParser::parseFilters).subjects
        val deadline = SystemClock.elapsedRealtime() + BUDGET_MS
        val found = mutableListOf<Pair<FilterOption, List<PresenceChart>>>()
        var read = 0
        var missedInARow = 0
        fun result() = PerSubjectPresence(labelled(found), read, options.size)

        for (option in options) {
            if (SystemClock.elapsedRealtime() > deadline || missedInARow >= MAX_MISSED_IN_A_ROW) break
            val outcome = try {
                choose(browser, SUBJECT_FIELD, option.value)
            } catch (e: IOException) {
                null // counts as not updated; the next subject may work again
            }
            if (outcome != null && outcome.updated) {
                missedInARow = 0
                val charts = withContext(Dispatchers.Default) { PresenceChartsParser.parseCharts(outcome.chartsHtml) }
                if (presenceNumbers(charts).hasData) found += option to charts
            } else {
                missedInARow++
                Log.w(PortalPages.TAG, "Presence: subject ${option.value} did not update in time")
            }
            read++
            onProgress(result())
        }

        // Back to all subjects, in case the portal remembers the filter for the next visit.
        runCatching { choose(browser, SUBJECT_FIELD, "") }
        Log.i(PortalPages.TAG, "Presence per subject: $read of ${options.size} read, ${found.size} with lessons")
        result()
    }

    /**
     * Opens a chart page and sets its filter to all subjects, all appointments and [period].
     * A field already on the right value is left alone, so usually nothing needs to reload.
     */
    private suspend fun openCharts(browser: PortalBrowser, path: String, period: PresencePeriod) {
        browser.openPage(path)
        if (!browser.waitForSelector(PresenceChartsParser.CHART_CARD)) throw AgendaFormatException("$path charts not found")
        for ((field, value) in listOf(SUBJECT_FIELD to "", PERIOD_FIELD to period.portalValue, APPOINTMENTS_FIELD to "0")) {
            val outcome = choose(browser, field, value)
            if (outcome.missing && field != APPOINTMENTS_FIELD) throw AgendaFormatException("$path filter ${field.label} not found")
            if (!outcome.updated) throw IOException("Eduarte did not update the charts for ${field.label} = $value")
        }
    }

    private class Outcome(val missing: Boolean, val updated: Boolean, val chartsHtml: String)

    private suspend fun choose(browser: PortalBrowser, field: Field, value: String): Outcome {
        val json = JSONObject(browser.runAsync(chooseScript(field, value), PER_CHOICE_MS + SCRIPT_MARGIN_MS))
        return Outcome(
            missing = json.optBoolean("missing"),
            updated = json.optBoolean("updated"),
            chartsHtml = json.optString("html"),
        )
    }

    /** A filter field: the heading above it and its position, as a fallback. */
    private class Field(val label: String, val index: Int)

    /**
     * Sets one filter select to [value] and waits until the portal has redrawn the charts.
     *
     * The select's change event makes Wicket send an AJAX request; when its response has been
     * applied to the page (charts replaced and drawn), Wicket publishes "/ajax/call/complete".
     * The script counts those events, and the "/ajax/call/beforeSend" ones, with listeners it
     * installs once per page. It is done when at least one request completed since the change
     * and none is still underway, for [QUIET_MS] (a second request may follow the first, e.g.
     * from the portal's own dropdown script). It gives up after [PER_CHOICE_MS].
     *
     * Returns {missing} when there is no such select, {updated: true} right away when it
     * already has the value, otherwise {updated, html} with the chart cards' HTML.
     */
    private fun chooseScript(field: Field, value: String) = """
        var sleep = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };
        var charts = function () {
          return Array.prototype.map.call(document.querySelectorAll(${jsString(PresenceChartsParser.CHART_CARD)}),
            function (e) { return e.outerHTML; }).join('');
        };
        var select = null;
        document.querySelectorAll('select').forEach(function (s) {
          var box = s.parentElement && s.parentElement.parentElement;
          var h = box && box.querySelector('h5');
          if (!select && h && h.textContent.trim().toLowerCase() === ${jsString(field.label.lowercase())}) select = s;
        });
        if (!select) select = document.querySelector('select[name*="inputFields:${field.index}:"]');
        if (!select) return { missing: true, updated: true, html: charts() };
        var wanted = ${jsString(value)};
        if (select.value === wanted) return { updated: true, html: charts() };
        if (!window.Wicket || !Wicket.Event || !window.jQuery) throw new Error('Wicket or jQuery missing');

        var c = window.__eduarteSyncAjax;
        if (!c) {
          c = window.__eduarteSyncAjax = { sent: 0, done: 0 };
          var topic = Wicket.Event.Topic || {};
          Wicket.Event.subscribe(topic.AJAX_CALL_BEFORE_SEND || '/ajax/call/beforeSend', function () { c.sent++; });
          Wicket.Event.subscribe(topic.AJAX_CALL_COMPLETE || '/ajax/call/complete', function () { c.done++; });
        }
        var doneBefore = c.done;
        select.value = wanted;
        jQuery(select).trigger('change');

        var until = Date.now() + $PER_CHOICE_MS;
        var quietSince = 0;
        var settled = false;
        while (Date.now() < until) {
          await sleep(50);
          if (c.done > doneBefore && c.sent <= c.done) {
            if (!quietSince) quietSince = Date.now();
            if (Date.now() - quietSince >= $QUIET_MS) { settled = true; break; }
          } else {
            quietSince = 0;
          }
        }
        return { updated: settled, html: settled ? charts() : '' };
    """.trimIndent()

    private companion object {
        val SUBJECT_FIELD = Field("Vak", 0)
        val PERIOD_FIELD = Field("Periode", 1)
        val APPOINTMENTS_FIELD = Field("Afspraken", 2)

        /** Longest wait for the charts after one choice. */
        const val PER_CHOICE_MS = 6_000L
        const val QUIET_MS = 200L
        const val SCRIPT_MARGIN_MS = 4_000L

        /** All subjects together; what isn't read by then is left out. */
        const val BUDGET_MS = 90_000L

        /** This many subjects in a row without an update means the page stopped responding. */
        const val MAX_MISSED_IN_A_ROW = 3

        fun labelled(found: List<Pair<FilterOption, List<PresenceChart>>>): List<SubjectCharts> {
            val labels = labelDuplicates(found.map { it.first.name })
            return found.mapIndexed { i, (option, charts) -> SubjectCharts(labels[i], option, charts) }
        }
    }
}
