package dev.eduartesync.portal

import android.content.Context
import android.util.Log
import dev.eduartesync.Config
import dev.eduartesync.source.PortalBrowser
import java.time.LocalDate

/**
 * Reads the record pages under Studie: the dossier, the documents, the subjects ("Vakken") and
 * the groups. Each is a plain page open; none of them loads parts in the background as far as
 * seen, but each still waits for its list in case it does.
 *
 * Throws [dev.eduartesync.source.SessionExpiredException], [java.io.IOException] or
 * [dev.eduartesync.source.AgendaFormatException] like the agenda does.
 */
class StudyRecordsSource(private val context: Context) {

    suspend fun fetchDossier(): List<DossierEntry> = read(PortalPages.DOSSIER, ".dossier-card, .dossier-table, $EMPTY") { html ->
        StudyRecordsParser.parseDossier(html, LocalDate.now(Config.ZONE))
    }

    suspend fun fetchDocuments(): List<PortalDocument> = withPortalBrowser(context) { browser -> documents(browser) }

    suspend fun fetchSubjects(): List<PortalSubject> = read(PortalPages.SUBJECTS, "table thead th, $EMPTY", StudyRecordsParser::parseSubjects)

    suspend fun fetchGroups(): List<PortalGroup> = read(PortalPages.GROUPS, ".students-list-table, $EMPTY", StudyRecordsParser::parseGroups)

    /**
     * Reads the documents page again and returns a fresh link for [document]: the links point
     * into the page as the portal rendered it for this session, so a saved one goes stale.
     * Matched by name, type and date. Null when the document isn't listed anymore.
     */
    suspend fun freshDocumentUrl(document: PortalDocument): String? = withPortalBrowser(context) { browser ->
        val documents = documents(browser)
        (documents.firstOrNull { it.name == document.name && it.type == document.type && it.date == document.date }
            ?: documents.firstOrNull { it.name == document.name })?.url
    }

    private suspend fun documents(browser: PortalBrowser): List<PortalDocument> {
        browser.openPage(PortalPages.DOCUMENTS)
        browser.waitForSelector("table thead th, $EMPTY")
        val documents = browser.parseCurrent { StudyRecordsParser.parseDocuments(it, Config.PORTAL_URL + PortalPages.DOCUMENTS) }
        Log.i(PortalPages.TAG, "Loaded ${documents.size} documents")
        return documents
    }

    private suspend fun <T> read(path: String, selector: String, parse: (String) -> List<T>): List<T> =
        withPortalBrowser(context) { browser ->
            browser.openPage(path)
            browser.waitForSelector(selector)
            val items = browser.parseCurrent(parse)
            Log.i(PortalPages.TAG, "Loaded ${items.size} items from $path")
            items
        }

    private companion object {
        /** The portal's "Er zijn geen ..." block, so an empty page doesn't wait for the time limit. */
        const val EMPTY = StudyRecordsParser.EMPTY_PAGE
    }
}
