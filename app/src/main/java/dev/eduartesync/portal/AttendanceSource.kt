package dev.eduartesync.portal

import android.content.Context
import android.util.Log
import dev.eduartesync.source.AgendaFormatException
import dev.eduartesync.source.PortalBrowser
import org.json.JSONArray
import java.io.IOException
import java.time.LocalDate

/**
 * Reads attendance from /presentie/presentie.overzicht. The page opens on the current week;
 * another period is chosen with the filter's "Vanaf" and "Tot" date fields, like a user would.
 *
 * Throws [dev.eduartesync.source.SessionExpiredException], [IOException] or
 * [AgendaFormatException] like the agenda does.
 */
class AttendanceSource(private val context: Context) {

    /** Attendance from [from] up to and including [to], as far as the portal shows it. */
    suspend fun fetch(from: LocalDate, to: LocalDate): Attendance {
        require(!to.isBefore(from)) { "to is before from" }
        return withPortalBrowser(context) { browser -> load(browser, from, to) }
    }

    private suspend fun load(browser: PortalBrowser, from: LocalDate, to: LocalDate): Attendance {
        browser.openPage(PortalPages.ATTENDANCE)
        if (!browser.waitForSelector(AttendanceParser.PERIOD_HEADER)) {
            throw AgendaFormatException("attendance period not found")
        }
        val shown = AttendanceParser.parsePeriod(browser.run(HEADER_TEXT))
        if (shown != from to to) {
            // "Zoeken" reloads the whole page, so the waiting happens here, not in the page script.
            if (browser.run(filterScript(from, to)) != "ok") throw AgendaFormatException("attendance date fields not found")
            val fromText = from.format(DutchDates.FIELD_FORMAT)
            val header = "(document.querySelector(${jsString(AttendanceParser.PERIOD_HEADER)}) || {}).textContent"
            val switched = browser.waitFor("document.readyState === 'complete' && ($header || '').indexOf('Vanaf $fromText') >= 0", FILTER_TIMEOUT_MS)
            if (!switched) throw IOException("Eduarte did not switch attendance to $from - $to")
        }
        val attendance = browser.parseCurrent(AttendanceParser::parse)
        Log.i(PortalPages.TAG, "Attendance ${attendance.from} to ${attendance.to}: ${attendance.entries.size} entries")
        return attendance
    }

    /**
     * Types both dates into the filter (each change is sent to the portal), presses the hidden
     * "Zoeken" button, and waits until the period header shows the new start date. The end date
     * isn't checked, in case the portal limits it.
     */
    private fun filterScript(from: LocalDate, to: LocalDate): String {
        val fromText = from.format(DutchDates.FIELD_FORMAT)
        val toText = to.format(DutchDates.FIELD_FORMAT)
        return """
            (function () {
              var byName = function (n) {
                return document.querySelector('input[name$="inputFields:' + n + ':controlGroup:controlGroup_body:formField"]');
              };
              var dates = document.querySelectorAll('input.hasDatepicker');
              var fromField = byName(0) || dates[0];
              var toField = byName(1) || dates[1];
              if (!fromField || !toField) return 'missing';
              var set = function (field, value) {
                field.value = value;
                if (window.jQuery) jQuery(field).trigger('change');
                else field.dispatchEvent(new Event('change', { bubbles: true }));
              };
              set(fromField, '$fromText');
              set(toField, '$toText');
              // Give the portal a moment to take in both dates, then search.
              setTimeout(function () {
                var button = (fromField.form || document).querySelector('input[value="Zoeken"], button[type="submit"], input[type="submit"]');
                if (!button) return;
                if (window.jQuery) jQuery(button).trigger('click'); else button.click();
              }, 800);
              return 'ok';
            })()
        """.trimIndent()
    }

    private companion object {
        const val FILTER_TIMEOUT_MS = 40_000L
        val HEADER_TEXT = "(document.querySelector(${jsString(AttendanceParser.PERIOD_HEADER)}) || {}).textContent"
    }
}
