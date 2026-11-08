package dev.eduartesync.portal

import android.content.Context
import dev.eduartesync.Config
import dev.eduartesync.source.PortalBrowser
import dev.eduartesync.source.PortalLock
import dev.eduartesync.sync.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Portal pages, relative to [Config.PORTAL_URL]. */
internal object PortalPages {
    const val RESULTS = "resultaten"
    const val RESULTS_OVERVIEW = "alleresultaten"
    const val STUDY_PROGRESS = "studievoortgang"
    const val PROGRAMMES = "studie/opleiding"
    const val WORK_LIST = "werklijst"
    const val ATTENDANCE = "presentie/presentie.overzicht"
    const val PRESENCE_CHARTS = "presentie/presentie"
    const val ABSENCE_CHARTS = "presentie/absentie"
    const val DOSSIER = "studie/dossier"
    const val DOCUMENTS = "studie/documenten"
    const val SUBJECTS = "studie/archief"
    const val GROUPS = "studie/groepen"

    /** How long to wait for parts of a page that the portal loads in the background. */
    const val CONTENT_TIMEOUT_MS = 20_000L

    const val TAG = "EduarteSync"
}

/**
 * Runs [block] with a fresh hidden [PortalBrowser] on the main thread, and destroys it afterwards.
 *
 * Takes the [PortalLock], so it never renews the login at the same time as a sync. The portal
 * readers run while the user has the app open, so the browser is interactive: when Microsoft
 * wants the Authenticator, a notification shows what to tap (and is removed afterwards).
 */
internal suspend fun <T> withPortalBrowser(context: Context, block: suspend (PortalBrowser) -> T): T =
    PortalLock.mutex.withLock {
        withContext(Dispatchers.Main) {
            val appContext = context.applicationContext
            val browser = PortalBrowser(appContext, interactive = true) { number ->
                Notifier.approveSignIn(appContext, number, reading = "your Eduarte information")
            }
            try {
                block(browser)
            } finally {
                // Also when cancelled (e.g. the screen was closed): a WebView left alive keeps running.
                withContext(NonCancellable + Dispatchers.Main) {
                    browser.destroy()
                    Notifier.clearApproveSignIn(appContext)
                }
            }
        }
    }

/** Opens a portal page (see [PortalPages]) with the same session renewal as the agenda. */
internal suspend fun PortalBrowser.openPage(path: String): Pair<String, String> =
    open(Config.PORTAL_URL + path, "/$path")

/** Waits until [selector] matches something on the page. */
internal suspend fun PortalBrowser.waitForSelector(selector: String, timeoutMs: Long = PortalPages.CONTENT_TIMEOUT_MS) =
    waitFor("document.querySelector(${jsString(selector)})", timeoutMs)

/** The current page HTML, parsed off the main thread. */
internal suspend fun <T> PortalBrowser.parseCurrent(parser: (String) -> T): T {
    val (_, html) = currentPage()
    return withContext(Dispatchers.Default) { parser(html) }
}

/** [value] as a JavaScript string literal. */
internal fun jsString(value: String): String = buildString {
    append('\'')
    for (c in value) {
        when (c) {
            '\\' -> append("\\\\")
            '\'' -> append("\\'")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '<' -> append("\\x3c")
            else -> append(c)
        }
    }
    append('\'')
}
