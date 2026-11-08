package dev.eduartesync.portal

import android.content.Context
import android.util.Log
import dev.eduartesync.source.AgendaFormatException

/**
 * Reads the study info from the portal: the programmes (/studie/opleiding), the study points
 * (/studievoortgang) and the work list (/werklijst).
 *
 * Throws [dev.eduartesync.source.SessionExpiredException], [java.io.IOException] or
 * [AgendaFormatException] like the agenda does.
 */
class StudySource(private val context: Context) {

    /** Programmes and study progress. The progress is null when its page has no points table. */
    suspend fun fetch(): StudyInfo = withPortalBrowser(context) { browser ->
        browser.openPage(PortalPages.PROGRAMMES)
        browser.waitForSelector(".study-card")
        val programmes = browser.parseCurrent(StudyParser::parseProgrammes)

        browser.openPage(PortalPages.STUDY_PROGRESS)
        browser.waitForSelector("table thead th")
        val progress = try {
            browser.parseCurrent(StudyParser::parseProgress)
        } catch (e: AgendaFormatException) {
            Log.w(PortalPages.TAG, "No study progress", e)
            null
        }
        Log.i(PortalPages.TAG, "Loaded ${programmes.size} programmes, ${progress?.rows?.size ?: 0} study point rows")
        StudyInfo(programmes, progress)
    }

    /** The homework/assignments list; empty when the portal says "Er zijn geen resultaten". */
    suspend fun fetchWorkList(): List<WorkItem> = withPortalBrowser(context) { browser ->
        browser.openPage(PortalPages.WORK_LIST)
        browser.waitForSelector("table thead th")
        browser.parseCurrent(StudyParser::parseWorkList)
    }
}
