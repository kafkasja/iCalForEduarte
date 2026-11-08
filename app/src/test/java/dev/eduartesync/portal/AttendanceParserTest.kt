package dev.eduartesync.portal

import dev.eduartesync.source.AgendaFormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AttendanceParserTest {

    private fun block(hour: Int, status: String, lesson: String, reported: String = "") = """
        <div class="student-absence--hour">
          <span class="student-absence--hour-title">$hour</span>
          <div class="student-absence--course">
            <small class="student-absence--reported">$reported</small>
            <p class=""><strong>$status</strong></p>
            <small>$lesson</small>
          </div>
        </div>
    """

    private val mentor = "11:45 - 12:45 Mentoruur  KLAS01 - dhr. Jansen"
    private val workshop = "13:15 - 14:15 Workshop Netwerken  KLAS01,KLAS02 - mevr. de Vries"
    private val info = "14:15 - 15:15 Info sessie  KLAS01 - dhr. Jansen"

    /** Trimmed copy of the real page structure, with made-up lessons and teachers. */
    private val html = """
        <html><body><div class="content">
        <div class="abstract-absentie-page">
          <div class="week-header"><h2 class="is-header">Vanaf 28-12-2026 tot en met 10-01-2027</h2></div>
          <div class="presence-total-view">
            <div class="presence-total">
              <div class="presence-total--title"><h2> Aanwezig </h2></div>
              <div class="presence-total--content">
                <div class="presence-total--content-title"> Aantal lesuren </div>
                <p class="presence-total--content-text show-hours"><label>8</label>&nbsp;uur</p>
                <div class="presence-total--content-title"> Aantal uren </div>
                <p class="presence-total--content-text show-hours"><label>2</label>&nbsp;uur</p>
              </div>
            </div>
            <div class="presence-total">
              <div class="presence-total--title"><h2> Ongeoorloofd afwezig </h2></div>
              <div class="presence-total--content">
                <div class="presence-total--content-title"> Aantal lesuren </div>
                <p class="presence-total--content-text show-hours red"><label>1,5</label>&nbsp; uur&nbsp;<svg></svg></p>
                <div class="presence-total--content-title"> Aantal uren </div>
                <p class="presence-total--content-text show-hours red"><label>0</label>&nbsp; uur&nbsp;</p>
              </div>
            </div>
          </div>
        </div>
        <div class="content--part">
          <div class="week-header"><h2 class="is-header">
            <span>28 december</span> - <span>1 januari</span>
            <span class="is-soft">/ Week <span>53</span> </span>
          </h2></div>
          <div class="tasks student-absence">
            <div class="student-absence--day">
              <span class="student-absence--day-title">wo</span>
              ${block(16, "Aanwezig", mentor)}
              ${block(17, "Aanwezig", mentor)}
              ${block(18, "Aanwezig", mentor)}
              ${block(19, "Aanwezig", mentor)}
            </div>
          </div>
        </div>
        <div class="content--part">
          <div class="week-header"><h2 class="is-header">
            <span>4 januari</span> - <span>8 januari</span>
            <span class="is-soft">/ Week <span>1</span> </span>
          </h2></div>
          <div class="tasks student-absence">
            <div class="student-absence--day">
              <span class="student-absence--day-title">vr</span>
              ${block(22, "Aanwezig", workshop)}
              ${block(23, "Ongeoorloofd afwezig", workshop, "Te laat")}
              ${block(24, "Aanwezig", workshop)}
              ${block(25, "Aanwezig", workshop)}
              ${block(26, "N.v.t. (14:15 - 14:30)", info)}
              ${block(27, "N.v.t. (14:30 - 14:45)", info)}
            </div>
          </div>
        </div>
        </div></body></html>
    """.trimIndent()

    @Test
    fun readsPeriodAndTotals() {
        val attendance = AttendanceParser.parse(html)
        assertEquals(LocalDate.of(2026, 12, 28), attendance.from)
        assertEquals(LocalDate.of(2027, 1, 10), attendance.to)
        assertEquals(
            listOf(
                AttendanceTotal("Aanwezig", 8.0, 2.0),
                AttendanceTotal("Ongeoorloofd afwezig", 1.5, 0.0),
            ),
            attendance.totals,
        )
    }

    @Test
    fun mergesBlocksIntoLessons() {
        val entries = AttendanceParser.parse(html).entries
        assertEquals(5, entries.size)

        val mentorEntry = entries[0]
        assertEquals(LocalDate.of(2026, 12, 30), mentorEntry.date) // "wo" in the week of 28 december
        assertEquals(LocalTime.of(11, 45), mentorEntry.start)
        assertEquals(LocalTime.of(12, 45), mentorEntry.end)
        assertEquals(LocalTime.of(11, 45), mentorEntry.lessonStart)
        assertEquals(LocalTime.of(12, 45), mentorEntry.lessonEnd)
        assertEquals("Mentoruur", mentorEntry.lesson)
        assertEquals("KLAS01", mentorEntry.group)
        assertEquals("dhr. Jansen", mentorEntry.teacher)
        assertEquals("Aanwezig", mentorEntry.status)
        assertNull(mentorEntry.reported)

        // A different status splits the lesson into parts.
        val friday = LocalDate.of(2027, 1, 8)
        val (a, b, c) = entries.subList(1, 4)
        assertEquals(listOf(friday, friday, friday), listOf(a.date, b.date, c.date))
        assertEquals(LocalTime.of(13, 15) to LocalTime.of(13, 30), a.start to a.end)
        assertEquals("Ongeoorloofd afwezig", b.status)
        assertEquals("Te laat", b.reported)
        assertEquals(LocalTime.of(13, 30) to LocalTime.of(13, 45), b.start to b.end)
        assertEquals(LocalTime.of(13, 45) to LocalTime.of(14, 15), c.start to c.end)
        assertEquals("Workshop Netwerken", c.lesson)
        assertEquals("KLAS01,KLAS02", c.group)
        assertEquals("mevr. de Vries", c.teacher)

        // The block time in brackets is taken out of the status.
        val last = entries[4]
        assertEquals("N.v.t.", last.status)
        assertEquals(LocalTime.of(14, 15), last.start)
        assertEquals(LocalTime.of(14, 45), last.end)
        assertEquals(LocalTime.of(15, 15), last.lessonEnd)
        assertEquals("Info sessie", last.lesson)
    }

    @Test
    fun emptyWeekHasNoEntries() {
        val attendance = AttendanceParser.parse(
            """<div class="abstract-absentie-page"><div class="week-header">
               <h2 class="is-header">Vanaf 02-11-2026 tot en met 08-11-2026</h2></div></div>"""
        )
        assertEquals(LocalDate.of(2026, 11, 2), attendance.from)
        assertEquals(emptyList<AttendanceEntry>(), attendance.entries)
    }

    @Test(expected = AgendaFormatException::class)
    fun missingPeriodIsAFormatError() {
        AttendanceParser.parse("<html><body><h2>Presentie</h2></body></html>")
    }

    @Test
    fun splitsLessonDescriptions() {
        assertEquals(
            Triple("Engels A2 - B1", "KLAS01", "dhr. Jansen"),
            AttendanceParser.splitDescription("Engels A2 - B1  KLAS01 - dhr. Jansen"),
        )
        assertEquals(
            Triple("Mentoruur", null, "dhr. Jansen"),
            AttendanceParser.splitDescription("Mentoruur - dhr. Jansen"),
        )
        assertEquals(Triple("Mentoruur", null, null), AttendanceParser.splitDescription("Mentoruur"))
    }
}
