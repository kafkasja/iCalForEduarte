package dev.eduartesync.portal

import dev.eduartesync.grades.Grade
import dev.eduartesync.source.AgendaFormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GradesParserTest {

    /** Trimmed copy of the "Matrix" tab structure, with made-up subjects. */
    private val matrix = """
        <html><body>
        <div class="popover defaultPopoverWidth" id="p1"></div>
        <table class="result-overview--table table mb-0"><thead><tr><th colspan="4">Toets</th></tr></thead><tbody>
          <tr>
            <td class="result-overview--course">Wiskunde</td>
            <td class="result--overview-structure"></td>
            <td class="result-overview--grades"><div class="result-overview--grades-container">
              <a href="#" class="result-overview--carousel-prev inactive"></a>
              <div class="result-overview--carousel"><ul>
                <li><div class="result-overview--grade is-positive"><a class="lozange" id="a1" href="#">
                  <span class="grade-structure--grade">6,1</span></a></div></li>
                <li><div class="result-overview--grade is-negative"><a class="lozange" id="a2" href="#">
                  <span class="grade-structure--grade">4,5</span></a></div></li>
              </ul></div>
            </div></td>
            <td><div class="result-overview--grade is-average is-positive"><a class="lozange" id="a3" href="#">
              <span class="grade-structure--grade">5,3</span></a></div></td>
          </tr>
          <tr>
            <td class="result-overview--course">Burgerschap</td>
            <td class="result--overview-structure"></td>
            <td class="result-overview--grades"><div class="result-overview--carousel"><ul>
              <li><div class="result-overview--grade is-positive"><a class="lozange" id="a4" href="#">
                <span class="grade-structure--grade">V</span></a></div></li>
              <li><div class="result-overview--grade"><a class="lozange" id="a5" href="#">
                <span class="grade-structure--grade">10,0</span></a></div></li>
            </ul></div></td>
            <td> </td>
          </tr>
        </tbody></table>
        </body></html>
    """.trimIndent()

    @Test
    fun readsTheMatrixInClickOrder() {
        val grades = GradesParser.parseMatrix(matrix)
        assertEquals(5, grades.size)
        assertEquals(listOf(0, 1, 2, 3, 4), grades.map { it.index })

        val first = grades[0]
        assertEquals("Wiskunde", first.subject)
        assertEquals("6,1", first.value)
        assertEquals(6.1, first.numeric!!, 1e-9)
        assertEquals(true, first.passed)
        assertFalse(first.isAverage)

        assertEquals(false, grades[1].passed)
        assertTrue(grades[2].isAverage)
        assertEquals(5.3, grades[2].numeric!!, 1e-9)

        assertEquals("Burgerschap", grades[3].subject)
        assertEquals("V", grades[3].value)
        assertNull(grades[3].numeric)
        assertNull(grades[4].passed)
        assertEquals(10.0, grades[4].numeric!!, 1e-9)
    }

    @Test(expected = AgendaFormatException::class)
    fun missingMatrixIsAFormatError() {
        GradesParser.parseMatrix("<html><body><p>Laden...</p></body></html>")
    }

    /** The popover after tapping a grade, as in the real page (made-up values). */
    private val popover = """
        <div class="popover defaultPopoverWidth is-open" id="p2">
          <div class="popover-header"><h2>
            Eindresultaat
          </h2></div>
          <div class="popover-body">
            <dl class="popover--dl advanced result-entry--popover-try">
              <dt>
                Eindresultaat
                (
                Hoogste geldt
                )
              </dt>
              <dd>7,4</dd>
            </dl>
            <dl class="popover--dl advanced result-entry--popover-try">
              <dt>Toetscode</dt><dd>EIND</dd>
              <dt>Datum behaald</dt><dd>8 april 2026</dd>
              <dt>Toetssoort</dt><dd>Toets</dd>
              <dt>Status</dt><dd>Geëxamineerd</dd>
            </dl>
            <dl class="popover--dl advanced result-entry--popover-try pogingen">
              <dt>Behaalde resultaten voor deze toets</dt>
              <dd>
                <span class="poging-title">Poging 1</span>
                <span class="poging-resultaat"> 5,2 </span>
                <span class="poging-title">Poging 2</span>
                <span class="poging-resultaat"> 7,4 </span>
              </dd>
            </dl>
          </div>
          <div class="popover-footer"><a class="btn btn-primary" href="#">Sluiten</a></div>
        </div>
    """.trimIndent()

    @Test
    fun readsTheGradePopover() {
        val detail = GradesParser.parseDetail(popover)
        assertEquals("Eindresultaat", detail.title)
        assertEquals("Eindresultaat (Hoogste geldt)", detail.resultLabel)
        assertEquals("7,4", detail.resultValue)
        assertEquals("EIND", detail.code)
        assertEquals(LocalDate.of(2026, 4, 8), detail.date)
        assertEquals("Toets", detail.type)
        assertEquals("Geëxamineerd", detail.status)
        assertNull(detail.weight)
        assertEquals(listOf(Attempt("Poging 1", "5,2"), Attempt("Poging 2", "7,4")), detail.attempts)
        assertEquals("EIND", detail.fields["Toetscode"])
    }

    @Test
    fun readsThePopoverFromAWholePageAndOptionalFields() {
        val page = """
            <html><body>
            <div class="popover defaultPopoverWidth" id="old"><div class="popover-header"><h2>Oud</h2></div></div>
            <div class="popover is-open"><div class="popover-header"><h2>Proefwerk H3</h2></div>
              <div class="popover-body"><dl>
                <dt>Resultaat</dt><dd>6,8</dd>
                <dt>Weging</dt><dd>2x</dd>
                <dt>Periode</dt><dd>Periode 1</dd>
                <dt>Datum behaald</dt><dd>12 mrt 2026</dd>
              </dl></div></div>
            </body></html>
        """.trimIndent()
        val detail = GradesParser.parseDetail(page)
        assertEquals("Proefwerk H3", detail.title)
        assertEquals("Resultaat", detail.resultLabel)
        assertEquals("6,8", detail.resultValue)
        assertEquals(2.0, detail.weight!!, 1e-9)
        assertEquals("Periode 1", detail.period)
        assertEquals(LocalDate.of(2026, 3, 12), detail.date)
        assertTrue(detail.attempts.isEmpty())
    }

    /** The "Overzicht" tab structure, with made-up subjects. */
    private val overview = """
        <html><body><div class="container-card-body">
          <div class="grade-structure">
            <h2 class="result-structure--title">Toetsen</h2>
            <ul class="result-structure">
              <li class="is-clickable"><div class="results-item">
                <a class="results-item--link">Wiskunde </a>
                <div class="student-progress">
                  <div class="student-progress--bar">
                    <span class="student-progress--average"><span class="">7,2</span></span>
                    <div class="student-progress--achieved"></div>
                  </div>
                  <div class="student-progress--achieved-label">Behaald</div>
                </div>
              </div></li>
              <li class="is-clickable"><div class="results-item">
                <a class="results-item--link">Sport</a>
                <div class="student-progress"><div class="student-progress--achieved-label"></div></div>
              </div></li>
            </ul>
          </div>
          <div class="grade-structure">
            <h2 class="result-structure--title">Examengericht</h2>
            <ul class="result-structure">
              <li class="is-clickable"><div class="results-item">
                <a class="results-item--link">Realiseert software</a>
                <div class="student-progress"><div class="student-progress--achieved-label">Behaald</div></div>
              </div></li>
            </ul>
          </div>
        </div></body></html>
    """.trimIndent()

    @Test
    fun readsTheOverview() {
        val results = GradesParser.parseOverview(overview)
        assertEquals(
            listOf(
                SubjectResult("Wiskunde", "7,2", 7.2, "Behaald", "Toetsen"),
                SubjectResult("Sport", null, null, null, "Toetsen"),
                SubjectResult("Realiseert software", null, null, "Behaald", "Examengericht"),
            ),
            results,
        )
    }

    @Test
    fun mapsToTheUiGradeShape() {
        val grades = GradesParser.parseMatrix(matrix).map {
            if (it.index == 0) it.copy(detail = GradesParser.parseDetail(popover)) else it
        }
        val mapped = toGrades(grades)
        assertEquals(4, mapped.size) // the average is left out
        assertEquals(
            Grade("Wiskunde", "Eindresultaat", "6,1", 6.1, null, LocalDate.of(2026, 4, 8), null),
            mapped[0],
        )
        assertEquals("", mapped[1].description)
        assertEquals("Gemiddelde", toGrades(grades, includeAverages = true)[2].description)
    }
}
