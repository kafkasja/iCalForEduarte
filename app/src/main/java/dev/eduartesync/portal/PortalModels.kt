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

// ---- Presence charts (/presentie/presentie and /presentie/absentie) ----

/**
 * One donut chart card, e.g. "Presentie" with "137.0 uur present" in the middle and the legend
 * "Afwezig (19)", "Aanwezig (137)".
 */
data class PresenceChart(
    val title: String,
    /** The text in the middle of the donut as given, e.g. "137.0" or "88%". */
    val centerValue: String?,
    /** [centerValue] as a number (without the "%"). */
    val centerNumber: Double?,
    /** The words under the middle number, e.g. "uur present". */
    val centerText: String?,
    val items: List<ChartItem>,
) {
    /**
     * False when the portal would show its "Geen waarnemingen" label: every legend count is 0.
     * That label is in the page also when there is data, so it can't be used to tell.
     */
    val hasData: Boolean
        get() = if (items.isEmpty()) (centerNumber ?: 0.0) > 0.0 else items.any { it.count > 0.0 }

    /** The count of the legend item whose label starts with [label] (ignoring case). */
    fun count(label: String): Double? = items.firstOrNull { it.label.startsWith(label, ignoreCase = true) }?.count
}

/** A legend line: "Aanwezig (137)" -> label "Aanwezig", count 137. */
data class ChartItem(val label: String, val count: Double)

/**
 * The "Periode" choices of the presence filter, with the value the portal's select posts.
 * The portal also has "Stageperiode" (5), which is left out: it is about the internship.
 */
enum class PresencePeriod(val portalValue: String, val label: String, val phrase: String) {
    SCHOOL_YEAR("0", "School year", "this school year"),
    THIS_MONTH("1", "This month", "this month"),
    LAST_MONTH("2", "Last month", "last month"),
    THIS_WEEK("3", "This week", "this week"),
    LAST_WEEK("4", "Last week", "last week"),
}

/** An option of a filter select: the value the portal posts and the name it shows. */
data class FilterOption(val value: String, val name: String)

/** The filter in the sidebar of the presence pages. */
data class PresenceFilters(
    /** "Vak": every subject, without the empty "Vak..." option. Names can repeat. */
    val subjects: List<FilterOption>,
    /** "Periode": Huidig schooljaar, Huidige maand, ... */
    val periods: List<FilterOption>,
)

// ---- Study records (/studie/dossier, /studie/documenten, /studie/archief, /studie/groepen) ----

/** A dossier entry, e.g. "B_Loopbaan (Voortgangsgesprek)" on 5 November by a teacher. */
data class DossierEntry(
    /** The whole title as shown. */
    val title: String,
    /** The part before the brackets, e.g. "B_Loopbaan"; null when there are no brackets. */
    val category: String?,
    /** The part in brackets, e.g. "Voortgangsgesprek"; the whole title when there are none. */
    val subject: String,
    val date: LocalDate?,
    val author: String?,
)

/**
 * A document from /studie/documenten. [url] is a link into the portal page it was read from,
 * so it only works for a while; opening a document reads the page again for a fresh one.
 */
data class PortalDocument(
    val type: String,
    val date: LocalDate?,
    /** File name as shown, e.g. "brief.pdf". */
    val name: String,
    val url: String?,
)

/** A subject from /studie/archief ("Vakken"). */
data class PortalSubject(
    val name: String,
    val code: String?,
    val abbreviation: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
)

/** A group/class from /studie/groepen, with the names of its students. */
data class PortalGroup(
    val name: String,
    val size: Int?,
    val members: List<String>,
)
