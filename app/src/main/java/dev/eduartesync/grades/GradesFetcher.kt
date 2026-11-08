package dev.eduartesync.grades

import dev.eduartesync.portal.PortalGradesSource
import dev.eduartesync.portal.toGrades

import android.content.Context

/** Reads the current grades from the portal. Throws when they couldn't be read. */
interface GradesFetcher {
    suspend fun fetch(): List<Grade>
}

/**
 * The fetcher the Cijfers tab uses: the portal's Resultaten page.
 */
fun gradesFetcher(context: Context): GradesFetcher = PortalGradesFetcher(context.applicationContext)

/** Reads the grades from the portal's Resultaten page, see [PortalGradesSource]. */
class PortalGradesFetcher(private val context: Context) : GradesFetcher {
    override suspend fun fetch(): List<Grade> = toGrades(PortalGradesSource(context).fetch().grades)
}

