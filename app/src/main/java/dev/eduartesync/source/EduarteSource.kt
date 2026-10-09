package dev.eduartesync.source

import android.content.Context
import android.util.Log
import dev.eduartesync.Config
import dev.eduartesync.model.Lesson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Reads the schedule from the Talnet Eduarte student portal's agenda page. The page opens on
 * the current week; other weeks are shown by entering a date in the agenda's date field.
 */
class EduarteSource(private val context: Context) {

    /** Lessons starting from [from] up to and including [to]. */
    suspend fun fetch(from: LocalDate, to: LocalDate): List<Lesson> {
        val browser = withContext(Dispatchers.Main) { PortalBrowser(context) }
        try {
            var page = parse(withContext(Dispatchers.Main) { browser.open(AGENDA_URL) })
            val lessons = page.lessons.toMutableList()

            // Earlier weeks are only needed if the page opened after `from`.
            var steps = 0
            while (page.days.first() > from && steps++ < MAX_WEEKS) {
                page = showWeekOf(browser, page.days.first().minusWeeks(1))
                lessons += page.lessons
            }
            while (page.days.last() < to && steps++ < MAX_WEEKS) {
                val next = showWeekOf(browser, page.days.last().plusDays(1))
                // Never sync a partial schedule: a missing week would look like "no lessons"
                // and its events would be deleted.
                if (next.days.last() <= page.days.last()) {
                    throw AgendaFormatException("asked for the week after ${page.days.last()}, got ${next.days.first()} to ${next.days.last()}")
                }
                page = next
                lessons += page.lessons
            }

            val fromMillis = from.atStartOfDay(Config.ZONE).toInstant().toEpochMilli()
            val toMillis = to.plusDays(1).atStartOfDay(Config.ZONE).toInstant().toEpochMilli()
            return lessons.filter { it.startMillis in fromMillis until toMillis }.distinctBy { it.id }
        } finally {
            withContext(Dispatchers.Main) { browser.destroy() }
        }
    }

    private suspend fun showWeekOf(browser: PortalBrowser, date: LocalDate): AgendaWeek =
        parse(withContext(Dispatchers.Main) { browser.showWeekOf(date) })

    private suspend fun parse(page: Pair<String, String>): AgendaWeek {
        val (url, html) = page
        val week = withContext(Dispatchers.Default) { AgendaParser.parse(html, url, Config.ZONE) }
        Log.i(TAG, "Loaded ${week.days.first()} to ${week.days.last()}: ${week.lessons.size} lessons")
        return week
    }

    private companion object {
        const val TAG = "EduarteSync"
        const val AGENDA_URL = Config.PORTAL_URL + "agenda"
        const val MAX_WEEKS = 12
    }
}
