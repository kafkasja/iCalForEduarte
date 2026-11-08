package dev.eduartesync.records

import dev.eduartesync.portal.AttendanceEntry
import dev.eduartesync.portal.DossierEntry
import dev.eduartesync.portal.PortalDocument
import dev.eduartesync.portal.PortalGroup
import dev.eduartesync.portal.PortalSubject
import dev.eduartesync.portal.PresencePeriod
import dev.eduartesync.portal.Programme
import dev.eduartesync.portal.StudyInfo
import dev.eduartesync.portal.StudyPointsChart
import dev.eduartesync.portal.StudyPointsRow
import dev.eduartesync.portal.StudyProgress
import dev.eduartesync.presence.AbsenceNumbers
import dev.eduartesync.presence.PresenceNumbers
import dev.eduartesync.presence.PresenceOverview
import dev.eduartesync.presence.SubjectPresence
import dev.eduartesync.presence.entriesFromJson
import dev.eduartesync.presence.entriesToJson
import dev.eduartesync.presence.overviewFromJson
import dev.eduartesync.presence.overviewToJson
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** What is stored survives being read back, including missing fields. */
class RecordsJsonTest {
    private val day = LocalDate.of(2026, 3, 4)

    @Test
    fun dossier() {
        val entries = listOf(DossierEntry("A (B)", "A", "B", day, "Docent"), DossierEntry("C", null, "C", null, null))
        assertEquals(entries, dossierFromJson(dossierToJson(entries)))
    }

    @Test
    fun documents() {
        val documents = listOf(PortalDocument("Brief", day, "brief.pdf", "https://portal.example/x?1"), PortalDocument("", null, "y.pdf", null))
        assertEquals(documents, documentsFromJson(documentsToJson(documents)))
    }

    @Test
    fun subjectsAndGroups() {
        val subjects = listOf(PortalSubject("Biologie", "BIO", null, day, null))
        assertEquals(subjects, subjectsFromJson(subjectsToJson(subjects)))
        val groups = listOf(PortalGroup("KLAS1A", 2, listOf("Een", "Twee")), PortalGroup("Leeg", null, emptyList()))
        assertEquals(groups, groupsFromJson(groupsToJson(groups)))
    }

    @Test
    fun study() {
        val study = StudyInfo(
            listOf(Programme("Opleiding", "12345", "actief", "BOL", 4, "Voltijd", day, null, "Ergens", listOf("Iets"))),
            StudyProgress(
                rows = listOf(StudyPointsRow("Biologie", "7,0", 5.0, 2.5, null)),
                charts = listOf(StudyPointsChart("Totaal", 10.0, null, 3.0)),
                total = StudyPointsRow("Totaal", null, 5.0, 2.5, 0.0),
            ),
        )
        assertEquals(study, studyFromJson(studyToJson(study)))
        val bare = StudyInfo(emptyList(), null)
        assertEquals(bare, studyFromJson(studyToJson(bare)))
    }

    @Test
    fun presenceOverview() {
        val overview = PresenceOverview(
            period = PresencePeriod.LAST_MONTH,
            presence = PresenceNumbers(88.0, 137.0, 19.0),
            absence = AbsenceNumbers(11.0, 8.0, 2.0, null),
            subjects = listOf(SubjectPresence("Frans (2)", PresenceNumbers(null, 2.0, null))),
            subjectsRead = 30,
            subjectsTotal = 40,
        )
        assertEquals(overview, overviewFromJson(overviewToJson(overview)))
        val fresh = overview.copy(absence = null, subjects = null, subjectsRead = 0, subjectsTotal = 0)
        assertEquals(fresh, overviewFromJson(overviewToJson(fresh)))
    }

    @Test
    fun recentLessons() {
        val entries = listOf(
            AttendanceEntry(day, LocalTime.of(9, 0), LocalTime.of(9, 45), LocalTime.of(9, 0), LocalTime.of(10, 0), "Les", "KLAS1A", "Docent", "Aanwezig", null),
            AttendanceEntry(day, LocalTime.of(11, 0), LocalTime.of(12, 0), null, null, "Les 2", null, null, "Afwezig", "Ongeoorloofd"),
        )
        assertEquals(entries, entriesFromJson(entriesToJson(entries)))
    }

    @Test
    fun documentFileNames() {
        assertEquals("brief_1.pdf", DocumentDownloader.safeFileName("brief/1.pdf"))
        assertEquals("a_b.pdf", DocumentDownloader.safeFileName("a:b.pdf"))
        assertEquals("document.pdf", DocumentDownloader.safeFileName("  "))
        assertEquals("application/pdf", DocumentDownloader.mimeType("application/octet-stream", "x.PDF"))
        assertEquals("application/pdf", DocumentDownloader.mimeType("application/pdf", "x"))
    }
}
