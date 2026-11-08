package dev.eduartesync.grades

import android.content.Context

/** Reads the current grades from the portal. Throws when they couldn't be read. */
interface GradesFetcher {
    suspend fun fetch(): List<Grade>
}

/**
 * The fetcher the Cijfers tab uses. Swap in the real portal scraper here once it exists; until
 * then the tab shows "Not available yet".
 */
@Suppress("UNUSED_PARAMETER") // the real scraper will need the context for its WebView
fun gradesFetcher(context: Context): GradesFetcher = NotAvailableGradesFetcher

/** Placeholder until the grades page of the portal is scraped. */
object NotAvailableGradesFetcher : GradesFetcher {
    override suspend fun fetch(): List<Grade> = throw NotImplementedError("Reading grades from Eduarte isn't built yet")
}
