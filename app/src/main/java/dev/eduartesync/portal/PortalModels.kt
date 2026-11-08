package dev.eduartesync.portal

import java.time.LocalDate
import java.time.LocalTime

// ---- Grades (/resultaten "Matrix" and /alleresultaten "Overzicht") ----

/** One grade from the results matrix, optionally with the details from its popover. */
data class PortalGrade(
    val subject: String,
    /** As shown: "6,1", "10,0", "V". */
    val value: String,
    /** [value] as a number, null for "V", "O" and the like. */
    val numeric: Double?,
    /** true for a sufficient grade (is-positive), false for insufficient (is-negative), null if unmarked. */
    val passed: Boolean?,
    /** The subject's average/final result from the matrix's last column. */
    val isAverage: Boolean,
    /** Position of this grade's link among all grade links of the matrix (click order). */
    val index: Int,
    val detail: GradeDetail? = null,
)

/** The popover that opens when tapping a grade. */
data class GradeDetail(
    /** Popover title, e.g. the test name or "Eindresultaat". */
    val title: String?,
    /** The result line's label, e.g. "Eindresultaat (Hoogste geldt)". */
    val resultLabel: String?,
    val resultValue: String?,
    /** "Toetscode". */
    val code: String?,
    /** "Datum behaald". */
    val date: LocalDate?,
    /** "Toetssoort". */
    val type: String?,
    /** "Status", e.g. "Geëxamineerd". */
    val status: String?,
    /** "Weging", if the portal shows it (not seen in a sample yet). */
    val weight: Double?,
    /** "Periode", if the portal shows it (not seen in a sample yet). */
    val period: String?,
    /** "Behaalde resultaten voor deze toets". */
    val attempts: List<Attempt>,
    /** Every label/value pair of the popover, also the ones without a field above. */
    val fields: Map<String, String>,
)

data class Attempt(val label: String, val value: String)

/** One subject or exam part from the results overview. */
data class SubjectResult(
    val subject: String,
    /** The final/average result, null when the overview shows none. */
    val finalValue: String?,
    val numeric: Double?,
    /** e.g. "Behaald"; null when empty. */
    val status: String?,
    /** The heading it is listed under: "Toetsen" or "Examengericht". */
    val group: String?,
)

/** Everything [PortalGradesSource] reads. */
data class PortalGrades(
    val grades: List<PortalGrade>,
    val results: List<SubjectResult>,
)

// ---- Study progress (/studievoortgang) ----

data class StudyProgress(
    val rows: List<StudyPointsRow>,
    /** The donut charts above the table ("Totale studievoortgang", "Studievoortgang fase"). */
    val charts: List<StudyPointsChart>,
    /** A totals row, if the table has one. */
    val total: StudyPointsRow?,
)

data class StudyPointsRow(
    val subject: String,
    /** "Resultaat"; null for "-". */
    val result: String?,
    val totalPoints: Double?,
    val achievedPoints: Double?,
    val provisionalPoints: Double?,
)

data class StudyPointsChart(
    val title: String,
    val achieved: Double?,
    val provisional: Double?,
    val notAchieved: Double?,
)

// ---- Programmes (/studie/opleiding) ----

data class Programme(
    val name: String,
    /** Programme (crebo) code. */
    val code: String?,
    /** e.g. "actief", "beëindigd". */
    val status: String?,
    /** "BOL" or "BBL". */
    val pathway: String?,
    val level: Int?,
    /** e.g. "Voltijd". */
    val studyMode: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val location: String?,
    /** Info lines that didn't match any field above. */
    val other: List<String>,
)

data class StudyInfo(
    val programmes: List<Programme>,
    val progress: StudyProgress?,
)

// ---- Work list (/werklijst) ----

data class WorkItem(
    val name: String,
    val status: String?,
    val type: String?,
    val deadline: String?,
    val subjects: String?,
    val feedback: String?,
    val result: String?,
    /** Every column by header text. */
    val columns: Map<String, String>,
)

// ---- Attendance (/presentie/presentie.overzicht) ----

data class Attendance(
    val from: LocalDate,
    val to: LocalDate,
    /** Aanwezig, Geoorloofd afwezig, Ongeoorloofd afwezig, Niet geregistreerd. */
    val totals: List<AttendanceTotal>,
    val entries: List<AttendanceEntry>,
)

data class AttendanceTotal(
    val category: String,
    /** "Aantal lesuren". */
    val lessonHours: Double?,
    /** "Aantal uren". */
    val hours: Double?,
)

/** One lesson (or the part of it with the same status), merged from the portal's 15-minute blocks. */
data class AttendanceEntry(
    val date: LocalDate,
    /** Start and end of the registered blocks. */
    val start: LocalTime,
    val end: LocalTime,
    /** The lesson's own times, from its description. */
    val lessonStart: LocalTime?,
    val lessonEnd: LocalTime?,
    val lesson: String,
    val group: String?,
    val teacher: String?,
    /** e.g. "Aanwezig", "N.v.t.", without the block time the portal adds in brackets. */
    val status: String,
    /** The small note above the status, if any (e.g. a reported absence). */
    val reported: String?,
)
