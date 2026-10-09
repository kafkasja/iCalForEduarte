package dev.eduartesync.source

import android.content.Context
import android.util.Log
import dev.eduartesync.Config
import dev.eduartesync.model.Lesson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/**
 * Reads the schedule from the Talnet Eduarte student portal's agenda page. The portal opens on
 * the current week; the previous/next arrows are followed week by week, like clicking them.
 */
class EduarteSource(private val context: Context) {

    /** Lessons starting from [from] up to and including [to]. */
    suspend fun fetch(from: LocalDate, to: LocalDate): List<Lesson> {
        val browser = withContext(Dispatchers.Main) { PortalBrowser(context) }
        try {
            var page = load(browser, AGENDA_URL)
            val lessons = page.lessons.toMutableList()

            // Walk back until `from` is covered, then forward until `to` is covered.
            var steps = 0
            while (page.days.first() > from && steps++ < MAX_WEEKS) {
                page = load(browser, page.previousWeekUrl ?: break)
                lessons += page.lessons
            }
            while (page.days.last() < to && steps++ < MAX_WEEKS) {
                val next = load(browser, page.nextWeekUrl ?: break)
                lessons += next.lessons
                val advanced = next.days.last() > page.days.last()
                page = next
                if (!advanced) break
            }
            // Never sync a partial schedule: missing weeks would look like "no lessons" and
            // their events would be deleted.
            if (page.days.last() < to) {
                throw IOException(
                    "Could not load the schedule up to $to (got until ${page.days.last()}, " +
                        "next week link ${if (page.nextWeekUrl == null) "missing" else "did not advance"})"
                )
            }

            val fromMillis = from.atStartOfDay(Config.ZONE).toInstant().toEpochMilli()
            val toMillis = to.plusDays(1).atStartOfDay(Config.ZONE).toInstant().toEpochMilli()
            return lessons.filter { it.startMillis in fromMillis until toMillis }.distinctBy { it.id }
        } finally {
            withContext(Dispatchers.Main) { browser.destroy() }
        }
    }

    private suspend fun load(browser: PortalBrowser, url: String): AgendaWeek {
        val (finalUrl, html) = withContext(Dispatchers.Main) { browser.open(url) }
        val week = withContext(Dispatchers.Default) { AgendaParser.parse(html, finalUrl, Config.ZONE) }
        Log.i(TAG, "Loaded ${week.days.first()} to ${week.days.last()}: ${week.lessons.size} lessons")
        return week
    }

    private companion object {
        const val TAG = "EduarteSync"
        const val AGENDA_URL = Config.PORTAL_URL + "agenda"
        const val MAX_WEEKS = 12
    }
}
