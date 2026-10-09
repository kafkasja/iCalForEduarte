package dev.eduartesync.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class AgendaParserTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val pageUrl = "https://talnet-student.educus.nl/agenda?2"

    /** Trimmed copy of the real page structure ("Week" view), with made-up names. */
    private val html = """
        <html><head>
        <style type="text/css" id="r1-style">#r1 { top: 0px; height: 27px; }</style>
        <style type="text/css" id="r2-style">#r2 { top: 27px; height: 27px; }</style>
        <style type="text/css" id="l1-style">#l1 { top: 54px; left: 0%; width: 100%; height: 108px; }</style>
        <style type="text/css" id="l2-style">#l2 { top: 297px; left: 0%; width: 100%; height: 216px; }</style>
        <style type="text/css" id="l3-style">#l3 { top: 54px; left: 0%; width: 100%; height: 108px; }</style>
        </head><body>
        <div class="agenda-filter time-filter"><div class="filter--top">
          <a class="time-filter--left" href="./agenda?2-3.-filter-terug&amp;op=2"></a>
          <a class="time-filter--right" href="./agenda?2-3.-filter-vooruit&amp;op=2"></a>
        </div>
        <input class="form-control-sm" type="text" value="31-12-2026" name="filter:datum" id="id65"/>
        </div>
        <div class="agenda">
          <div class="agenda--schedule"><h4 class="lead">&nbsp;</h4><ul>
            <li id="r1"><div><span>1</span></div></li><li id="r2"><div><span>2</span></div></li>
          </ul></div>
          <div class="agenda--time"><h4>&nbsp;</h4><ul>
            <li><span>08:00</span></li><li><span>09:00</span></li>
          </ul></div>
          <div class="agenda--days has-horizontal-scroll">
            <div class="agenda--day"><h4 class="lead">ma 28 dec</h4><ul>
              <li class="js-agenda-detail agenda--course" id="l1"><div>
                <span class="is-subject">PDO-Expert  KLAS01</span>
                <span class="is-location">O_235</span>
                <span class="is-participant">TEACH</span>
              </div></li>
              <li class="js-agenda-detail agenda--course" id="l2"><div>
                <span class="is-subject">NOSQL  KLAS01</span>
                <span class="is-location">O_318</span>
                <span class="is-participant">OTHER</span>
              </div></li>
            </ul></div>
            <div class="agenda--day"><h4 class="lead">vr 1 jan</h4><ul>
              <li class="js-agenda-detail agenda--course" id="l3"><div>
                <span class="is-subject">PDO-Expert  KLAS01</span>
                <span class="is-location">O_239</span>
                <span class="is-participant">TEACH</span>
              </div></li>
            </ul></div>
            <div class="agenda--day"><h4 class="lead">zo 3 jan</h4><ul></ul></div>
          </div>
        </div>
        </body></html>
    """.trimIndent()

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `parses subject, location, teacher and times from grid position`() {
        val week = AgendaParser.parse(html, pageUrl, zone)
        assertEquals(3, week.lessons.size)

        val pdo = week.lessons[0]
        assertEquals("PDO-Expert", pdo.subject)
        assertEquals("KLAS01", pdo.group)
        assertEquals("O_235", pdo.location)
        assertEquals("TEACH", pdo.teacher)
        // top 54px / (27px per 15 min) = 30 min after 08:00; 108px = 60 min
        assertEquals(at(2026, 12, 28, 8, 30), pdo.startMillis)
        assertEquals(at(2026, 12, 28, 9, 30), pdo.endMillis)

        val nosql = week.lessons[1]
        assertEquals(at(2026, 12, 28, 10, 45), nosql.startMillis)
        assertEquals(at(2026, 12, 28, 12, 45), nosql.endMillis)
    }

    @Test
    fun `day headers across new year get the right year`() {
        val week = AgendaParser.parse(html, pageUrl, zone)
        assertEquals(
            listOf(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3)),
            week.days,
        )
        assertEquals(at(2027, 1, 1, 8, 30), week.lessons[2].startMillis)
    }

    @Test
    fun `week navigation links are absolute`() {
        val week = AgendaParser.parse(html, pageUrl, zone)
        assertEquals("https://talnet-student.educus.nl/agenda?2-3.-filter-terug&op=2", week.previousWeekUrl)
        assertEquals("https://talnet-student.educus.nl/agenda?2-3.-filter-vooruit&op=2", week.nextWeekUrl)
    }

    @Test
    fun `ids are stable and unique`() {
        val first = AgendaParser.parse(html, pageUrl, zone).lessons.map { it.id }
        val again = AgendaParser.parse(html, pageUrl, zone).lessons.map { it.id }
        assertEquals(first, again)
        assertEquals(first.size, first.toSet().size)
        assertNotEquals(first[0], first[2])
    }

    @Test
    fun `page without the week grid is rejected`() {
        val result = runCatching { AgendaParser.parse("<html><body>Inloggen</body></html>", pageUrl, zone) }
        assertTrue(result.exceptionOrNull() is AgendaFormatException)
    }

    @Test
    fun `subject splitting`() {
        assertEquals("PDO-Expert" to "KLAS01", AgendaParser.splitSubject("PDO-Expert  KLAS01"))
        assertEquals("Engels" to null, AgendaParser.splitSubject("Engels"))
    }
}
