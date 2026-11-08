package dev.eduartesync.portal

import android.content.Context
import android.util.Log
import dev.eduartesync.source.AgendaFormatException
import dev.eduartesync.source.PortalBrowser
import org.json.JSONArray
import java.io.IOException

/**
 * Reads the grades from the portal: the "Matrix" tab of /resultaten, the details of each grade
 * (by tapping it, which opens a popover), and the final results from /alleresultaten.
 *
 * Throws [dev.eduartesync.source.SessionExpiredException], [IOException] or
 * [AgendaFormatException] like the agenda does. Details are best effort: a grade whose popover
 * didn't open in time keeps `detail = null`.
 */
class PortalGradesSource(private val context: Context) {

    suspend fun fetch(): PortalGrades = withPortalBrowser(context) { browser ->
        browser.openPage(PortalPages.RESULTS)
        // The matrix is loaded in the background about a second after the page.
        val grades = if (browser.waitForSelector(GradesParser.MATRIX_TABLE)) {
            withDetails(browser, browser.parseCurrent(GradesParser::parseMatrix))
        } else {
            val (_, html) = browser.currentPage()
            if (!html.contains(NO_RESULTS, ignoreCase = true)) {
                throw AgendaFormatException("results matrix did not load")
            }
            emptyList()
        }

        browser.openPage(PortalPages.RESULTS_OVERVIEW)
        browser.waitForSelector("ul.result-structure")
        val results = try {
            browser.parseCurrent(GradesParser::parseOverview)
        } catch (e: AgendaFormatException) {
            Log.w(PortalPages.TAG, "No results overview", e)
            emptyList()
        }
        Log.i(PortalPages.TAG, "Loaded ${grades.size} grades (${grades.count { it.detail != null }} with details), ${results.size} results")
        PortalGrades(grades, results)
    }

    /** Taps each grade in turn, in one script in the page, and parses the popovers it saw. */
    private suspend fun withDetails(browser: PortalBrowser, grades: List<PortalGrade>): List<PortalGrade> {
        if (grades.isEmpty()) return grades
        val popovers = try {
            JSONArray(browser.runAsync(clickScript(grades.map { it.index }), CLICK_BUDGET_MS + SCRIPT_MARGIN_MS))
        } catch (e: IOException) {
            Log.w(PortalPages.TAG, "Grade details failed", e)
            return grades
        } catch (e: AgendaFormatException) {
            Log.w(PortalPages.TAG, "Grade details failed", e)
            return grades
        }
        val details = HashMap<Int, GradeDetail>()
        for (i in 0 until popovers.length()) {
            val item = popovers.optJSONObject(i) ?: continue
            val html = item.optString("html").ifEmpty { null } ?: continue
            runCatching { GradesParser.parseDetail(html) }
                .onSuccess { details[item.getInt("i")] = it }
                .onFailure { Log.w(PortalPages.TAG, "Grade detail ${item.optInt("i")} unreadable", it) }
        }
        return grades.map { grade -> details[grade.index]?.let { grade.copy(detail = it) } ?: grade }
    }

    /**
     * Clicks the grade links with the given indexes one by one. Before each click, open popovers
     * are closed (like the popover's own close button does); the popover that opens next is
     * the clicked grade's. Stops after [CLICK_BUDGET_MS]; returns [{i, html} or {i, error}].
     */
    private fun clickScript(indexes: List<Int>) = """
        var selector = ${jsString(GradesParser.GRADE_LINKS)};
        var wanted = ${JSONArray(indexes)};
        var deadline = Date.now() + $CLICK_BUDGET_MS;
        var sleep = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };
        var closeAll = function () {
          if (window.jQuery) jQuery('.popover').removeClass('is-open');
          document.querySelectorAll('.popover.is-open').forEach(function (p) { p.classList.remove('is-open'); });
        };
        var out = [];
        // The click handlers arrive with the matrix, in scripts that run just after it is inserted.
        await sleep(500);
        for (var k = 0; k < wanted.length && Date.now() < deadline; k++) {
          var i = wanted[k];
          // Query again each time, in case the portal redrew the matrix.
          var link = document.querySelectorAll(selector)[i];
          if (!link) { out.push({ i: i, error: 'link not found' }); continue; }
          closeAll();
          link.click();
          var html = null;
          var until = Math.min(deadline, Date.now() + $PER_GRADE_MS);
          while (Date.now() < until) {
            await sleep(100);
            if (document.querySelector('.popover.is-open')) {
              await sleep(150); // let the rest of the response settle
              var open = document.querySelector('.popover.is-open');
              if (open) html = open.outerHTML;
              break;
            }
          }
          out.push(html === null ? { i: i, error: 'no popover' } : { i: i, html: html });
        }
        closeAll();
        return out;
    """.trimIndent()

    private companion object {
        const val NO_RESULTS = "geen resultaten"
        const val CLICK_BUDGET_MS = 55_000L
        const val SCRIPT_MARGIN_MS = 10_000L
        const val PER_GRADE_MS = 6_000L
    }
}
