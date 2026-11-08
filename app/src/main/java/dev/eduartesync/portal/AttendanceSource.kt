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
            val result = JSONArray("[" + browser.runAsync(filterScript(from, to), FILTER_TIMEOUT_MS + 5_000) + "]").get(0)
            if (result != "ok") {
                throw IOException("Eduarte did not switch attendance to $from - $to (shows: $result)")
            }
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
            var sleep = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };
            var byName = function (n) {
              return document.querySelector('input[name$="inputFields:' + n + ':controlGroup:controlGroup_body:formField"]');
            };
            var dates = document.querySelectorAll('.sidebar-date input');
            var fromField = byName(0) || dates[0];
            var toField = byName(1) || dates[1];
            if (!fromField || !toField) throw new Error('attendance date fields not found');
            var header = function () {
              var h = document.querySelector(${jsString(AttendanceParser.PERIOD_HEADER)});
              return h ? h.textContent.replace(/\s+/g, ' ').trim() : '';
            };
            var set = function (field, value) {
              field.value = value;
              if (window.jQuery) jQuery(field).trigger('change');
              else field.dispatchEvent(new Event('change', { bubbles: true }));
            };
            set(fromField, '$fromText');
            set(toField, '$toText');
            await sleep(300);
            var form = fromField.form || document;
            var button = form.querySelector('input[type="button"][value="Zoeken"], button[type="submit"], input[type="submit"]');
            if (button) button.click();
            var until = Date.now() + $FILTER_TIMEOUT_MS;
            while (Date.now() < until) {
              await sleep(250);
              if (header().indexOf('Vanaf $fromText') >= 0) {
                await sleep(500); // the weeks are redrawn with the header
                return 'ok';
              }
            }
            return header();
        """.trimIndent()
    }

    private companion object {
        const val FILTER_TIMEOUT_MS = 40_000L
        val HEADER_TEXT = "(document.querySelector(${jsString(AttendanceParser.PERIOD_HEADER)}) || {}).textContent"
    }
}
