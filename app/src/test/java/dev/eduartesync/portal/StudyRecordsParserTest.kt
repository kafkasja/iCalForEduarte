package dev.eduartesync.portal

import dev.eduartesync.source.AgendaFormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** All names, titles and files below are made up. */
class StudyRecordsParserTest {

    private fun dossierCard(title: String, line: String) = """
        <tr><td><a class="signal removestyle-signal dossier-card" href="#">
          <figure></figure>
          <div class="dossier-card-body">
            <h3 class="signal--title dossier-card--title">$title</h3>
            <small class="dossier-card--text">
              $line
            </small>
          </div>
        </a></td></tr>
    """.trimIndent()

    private val dossier = """
        <html><body><div class="dossier-table"><table class="table s5-table"><thead><tr><th></th></tr></thead>
        <tbody class="data">
          ${dossierCard("C_Mentor (Gesprek over planning)", "Dinsdag 3 november door Docent Een")}
          ${dossierCard("C_Mentor (Cijfers periode 2)", "Vrijdag 20 februari door Docent Twee")}
          ${dossierCard("Losse notitie", "Woensdag 10 december door Docent Drie")}
          ${dossierCard("Oud (Intake)", "Maandag 2 september 2024 door Docent Vier")}
        </tbody></table></div></body></html>
    """.trimIndent()

    @Test
    fun readsDossierEntriesAndGivesDatesAYearInThePast() {
        val entries = StudyRecordsParser.parseDossier(dossier, today = LocalDate.of(2026, 11, 8))
        assertEquals(4, entries.size)
        assertEquals(
            DossierEntry("C_Mentor (Gesprek over planning)", "C_Mentor", "Gesprek over planning", LocalDate.of(2026, 11, 3), "Docent Een"),
            entries[0],
        )
        assertEquals(LocalDate.of(2026, 2, 20), entries[1].date)
        // Below February 2026 in the list, so December is the one before it.
        assertEquals(LocalDate.of(2025, 12, 10), entries[2].date)
        assertNull(entries[2].category)
        assertEquals("Losse notitie", entries[2].subject)
        assertEquals(LocalDate.of(2024, 9, 2), entries[3].date)
        assertEquals("Docent Vier", entries[3].author)
    }

    @Test
    fun emptyDossierTableIsAnEmptyList() {
        val html = """<html><body><div class="dossier-table"><table><tbody></tbody></table></div></body></html>"""
        assertTrue(StudyRecordsParser.parseDossier(html, LocalDate.of(2026, 1, 1)).isEmpty())
    }

    @Test(expected = AgendaFormatException::class)
    fun pageWithoutDossierIsAFormatError() {
        StudyRecordsParser.parseDossier("<html><body><p>Iets anders</p></body></html>", LocalDate.of(2026, 1, 1))
    }

    private val documents = """
        <html><body><table class="table s5-table">
          <thead><tr><th>Documenttype</th><th>Datum</th><th>Link</th><th>Verwijderen</th></tr></thead>
          <tbody class="data">
            <tr><td>Verzuim</td><td><time>03-06-2026</time></td>
              <td><div><a href="./documenten?12-1.-bijlagen-table-bodies-0-skeletons-1-bones-3-document-open&amp;op=12" target="_blank">
                <svg></svg><span>voorbeeldbrief.pdf</span></a></div></td><td><div></div></td></tr>
            <tr><td>Overeenkomst</td><td><time>22-04-2025</time></td>
              <td><div><a href="./documenten?12-1.-bijlagen-table-bodies-0-skeletons-2-bones-3-document-open&amp;op=12">
                <span>contract 2025.pdf</span></a></div></td><td></td></tr>
          </tbody>
        </table></body></html>
    """.trimIndent()

    @Test
    fun readsDocumentsWithAbsoluteLinks() {
        val result = StudyRecordsParser.parseDocuments(documents, "https://portal.example/studie/documenten")
        assertEquals(2, result.size)
        assertEquals("Verzuim", result[0].type)
        assertEquals(LocalDate.of(2026, 6, 3), result[0].date)
        assertEquals("voorbeeldbrief.pdf", result[0].name)
        assertEquals(
            "https://portal.example/studie/documenten?12-1.-bijlagen-table-bodies-0-skeletons-1-bones-3-document-open&op=12",
            result[0].url,
        )
        assertEquals("contract 2025.pdf", result[1].name)
    }

    @Test
    fun emptyPageMeansNoDocuments() {
        val html = """<html><body><div class="page-is-empty"><small>Er zijn op dit moment geen documenten.</small></div></body></html>"""
        assertTrue(StudyRecordsParser.parseDocuments(html, "https://portal.example/studie/documenten").isEmpty())
    }

    @Test
    fun readsSubjectsFromEveryTableBody() {
        val html = """
            <html><body><table class="table s5-table">
              <thead><tr>
                <th class="table--sortable"><span> Vak <svg></svg></span></th><th><span>Code</span></th>
                <th><span>Afkorting</span></th><th class="s5-hide-medium"><span>Begindatum</span></th><th><span>Einddatum</span></th>
              </tr></thead>
              <tbody class="data">
                <tr><td>Biologie</td><td>BIO-01</td><td></td><td>01-08-2025</td><td></td></tr>
              </tbody>
              <tbody class="data">
                <tr><td>Frans</td><td>FRA-02</td><td>FA</td><td>01-08-2025</td><td>31-07-2026</td></tr>
              </tbody>
            </table></body></html>
        """.trimIndent()
        val subjects = StudyRecordsParser.parseSubjects(html)
        assertEquals(
            listOf(
                PortalSubject("Biologie", "BIO-01", null, LocalDate.of(2025, 8, 1), null),
                PortalSubject("Frans", "FRA-02", "FA", LocalDate.of(2025, 8, 1), LocalDate.of(2026, 7, 31)),
            ),
            subjects,
        )
    }

    @Test
    fun readsGroupsWithTheirStudents() {
        val html = """
            <html><body><div class="students-list-table">
              <div class="students-list--group students-list-header"><small>Naam</small><small class="group--count">Aantal</small></div>
              <div class="students-list-body">
                <div class="task has-popout">
                  <div class="task--summary"><p class="popout--toggle">KLAS1A - Voorbeeldopleiding</p><small class="task--course task--overview">3</small></div>
                  <div class="popout">
                    <div class="popout--body"><svg></svg><span>Student Een</span></div>
                    <div class="popout--body"><svg></svg><span>Student Twee</span></div>
                    <div class="popout--body"><svg></svg><span>Student Drie</span></div>
                  </div>
                </div>
                <div class="task has-popout">
                  <div class="task--summary"><p class="popout--toggle">Keuzegroep</p><small class="task--course">1</small></div>
                  <div class="popout"><div class="popout--body"><span>Student Vier</span></div></div>
                </div>
              </div>
            </div></body></html>
        """.trimIndent()
        val groups = StudyRecordsParser.parseGroups(html)
        assertEquals(
            listOf(
                PortalGroup("KLAS1A - Voorbeeldopleiding", 3, listOf("Student Een", "Student Twee", "Student Drie")),
                PortalGroup("Keuzegroep", 1, listOf("Student Vier")),
            ),
            groups,
        )
    }

    @Test(expected = AgendaFormatException::class)
    fun pageWithoutGroupsIsAFormatError() {
        StudyRecordsParser.parseGroups("<html><body><p>Iets anders</p></body></html>")
    }
}
