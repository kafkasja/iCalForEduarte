package dev.eduartesync.presence

import dev.eduartesync.grades.formatDutch
import dev.eduartesync.portal.AttendanceEntry
import dev.eduartesync.portal.PresenceChart
import dev.eduartesync.portal.PresencePeriod
import java.time.LocalDate
import kotlin.math.roundToInt

/** From this percentage up, attendance shows green. */
const val GOOD_PRESENCE_PERCENT = 90.0

/** From this percentage up (and below [GOOD_PRESENCE_PERCENT]), attendance shows amber; below it red. */
const val WARNING_PRESENCE_PERCENT = 80.0

/** Hours present and absent and the present percentage, for everything or for one subject. */
data class PresenceNumbers(
    /** 0 to 100; null when there were no lessons to count. */
    val percent: Double?,
    val presentHours: Double?,
    val absentHours: Double?,
) {
    /** False for a subject without any registered lessons ("Geen waarnemingen"). */
    val hasData: Boolean
        get() = (presentHours ?: 0.0) + (absentHours ?: 0.0) > 0.0 || (percent ?: 0.0) > 0.0
}

/** The Absentie tab: hours and reports, each split into geoorloofd (excused) and ongeoorloofd. */
data class AbsenceNumbers(
    val authorizedHours: Double?,
    val unauthorizedHours: Double?,
    val authorizedReports: Double?,
    val unauthorizedReports: Double?,
)

data class SubjectPresence(val name: String, val numbers: PresenceNumbers)

/**
 * Everything the Presentie screen shows for one period. [subjects] is null until the
 * per-subject read finished once; [subjectsRead] of [subjectsTotal] says whether it got through
 * all subjects (it has a time limit).
 */
data class PresenceOverview(
    val period: PresencePeriod,
    val presence: PresenceNumbers,
    val absence: AbsenceNumbers?,
    val subjects: List<SubjectPresence>?,
    val subjectsRead: Int = 0,
    val subjectsTotal: Int = 0,
)

enum class PresenceLevel { GOOD, WARNING, LOW }

fun presenceLevel(percent: Double): PresenceLevel = when {
    percent >= GOOD_PRESENCE_PERCENT -> PresenceLevel.GOOD
    percent >= WARNING_PRESENCE_PERCENT -> PresenceLevel.WARNING
    else -> PresenceLevel.LOW
}

/**
 * Reads the "Presentie" tab's charts: "Presentie" (hours, legend Afwezig/Aanwezig) and
 * "Percentage" ("88%" in the middle, legend Afwezig (12)/Aanwezig (88)). When the percentage
 * chart is missing it is worked out from the hours.
 */
fun presenceNumbers(charts: List<PresenceChart>): PresenceNumbers {
    val hoursChart = charts.firstOrNull { it.title.equals("Presentie", ignoreCase = true) }
    val percentChart = charts.firstOrNull { it.title.equals("Percentage", ignoreCase = true) }
    val present = hoursChart?.count("Aanwezig") ?: hoursChart?.centerNumber
    val absent = hoursChart?.count("Afwezig")
    val fromHours = if (present != null && absent != null && present + absent > 0.0) present * 100.0 / (present + absent) else null
    val percent = when {
        percentChart == null || !percentChart.hasData -> fromHours
        percentChart.centerValue?.contains('%') == true && percentChart.centerNumber != null -> percentChart.centerNumber
        else -> percentChart.count("Aanwezig") ?: fromHours
    }
    return PresenceNumbers(percent, present, absent)
}

/** Reads the "Absentie" tab's charts "Absentiemeldingen" (reports) and "Urenoverzicht" (hours). */
fun absenceNumbers(charts: List<PresenceChart>): AbsenceNumbers? {
    val reports = charts.firstOrNull { it.title.equals("Absentiemeldingen", ignoreCase = true) }
    val hours = charts.firstOrNull { it.title.equals("Urenoverzicht", ignoreCase = true) }
    if (reports == null && hours == null) return null
    return AbsenceNumbers(
        authorizedHours = hours?.count("Geoorloofd"),
        unauthorizedHours = hours?.count("Ongeoorloofd"),
        authorizedReports = reports?.count("Geoorloofd"),
        unauthorizedReports = reports?.count("Ongeoorloofd"),
    )
}

/**
 * Subject names for the list. The portal can list the same name twice (two "Engels" with
 * different codes); those are kept apart as "Engels" and "Engels (2)". Names are compared
 * without surrounding spaces, since the portal also has "Rekenen" and "Rekenen ".
 */
fun labelDuplicates(names: List<String>): List<String> {
    val seen = HashMap<String, Int>()
    return names.map { raw ->
        val name = raw.trim()
        val n = (seen[name.lowercase()] ?: 0) + 1
        seen[name.lowercase()] = n
        if (n == 1) name else "$name ($n)"
    }
}

/** Lowest percentage first, so the subjects that need attention are on top; unknown last. */
fun lowestFirst(subjects: List<SubjectPresence>): List<SubjectPresence> =
    subjects.sortedWith(
        compareBy<SubjectPresence> { it.numbers.percent == null }
            .thenBy { it.numbers.percent ?: 0.0 }
            .thenBy { it.name.lowercase() },
    )

/** How a lesson in the attendance overview went, as shown in its chip. */
enum class LessonStatus(val label: String) {
    PRESENT("Aanwezig"),
    AUTHORIZED("Geoorloofd"),
    UNAUTHORIZED("Ongeoorloofd"),
    /** Absent, without saying whether it was excused. */
    ABSENT("Afwezig"),
    NOT_APPLICABLE("N.v.t."),
    OTHER(""),
}

/**
 * "Aanwezig", "N.v.t.", or "Afwezig" with a note like "Ongeoorloofd, niet afgehandeld" above
 * it. Whether an absence was excused is in the status or in that note.
 */
fun lessonStatus(entry: AttendanceEntry): LessonStatus {
    val status = entry.status.lowercase()
    val all = (status + " " + entry.reported.orEmpty()).lowercase()
    return when {
        "ongeoorloofd" in all -> LessonStatus.UNAUTHORIZED
        "geoorloofd" in all -> LessonStatus.AUTHORIZED
        "aanwezig" in status -> LessonStatus.PRESENT
        "afwezig" in status -> LessonStatus.ABSENT
        "n.v.t" in status || status == "nvt" -> LessonStatus.NOT_APPLICABLE
        else -> LessonStatus.OTHER
    }
}

/** The lessons up to [today], one group per day, newest day first and lessons in time order. */
fun lessonsByDay(entries: List<AttendanceEntry>, today: LocalDate): List<Pair<LocalDate, List<AttendanceEntry>>> =
    entries.filter { !it.date.isAfter(today) }
        .groupBy { it.date }
        .toSortedMap(compareByDescending { it })
        .map { (date, lessons) -> date to lessons.sortedBy { it.lessonStart ?: it.start } }

/** "88%". */
fun formatPercent(percent: Double): String = "${percent.roundToInt()}%"

/** "19" or "18,5" hours, the Dutch way. */
fun formatHours(hours: Double): String = formatDutch(hours, decimals = 2).trimEnd('0').trimEnd(',')
