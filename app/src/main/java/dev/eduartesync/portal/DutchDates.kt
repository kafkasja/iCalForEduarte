package dev.eduartesync.portal

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Dates, times and numbers as the portal writes them (Dutch). */
object DutchDates {
    private val MONTHS = mapOf(
        "januari" to 1, "jan" to 1,
        "februari" to 2, "feb" to 2,
        "maart" to 3, "mrt" to 3, "maa" to 3,
        "april" to 4, "apr" to 4,
        "mei" to 5,
        "juni" to 6, "jun" to 6,
        "juli" to 7, "jul" to 7,
        "augustus" to 8, "aug" to 8,
        "september" to 9, "sep" to 9, "sept" to 9,
        "oktober" to 10, "okt" to 10,
        "november" to 11, "nov" to 11,
        "december" to 12, "dec" to 12,
    )
    private val WEEKDAYS = mapOf(
        "ma" to DayOfWeek.MONDAY, "di" to DayOfWeek.TUESDAY, "wo" to DayOfWeek.WEDNESDAY,
        "do" to DayOfWeek.THURSDAY, "vr" to DayOfWeek.FRIDAY, "za" to DayOfWeek.SATURDAY,
        "zo" to DayOfWeek.SUNDAY,
    )
    private val WHITESPACE = Regex("""\s+""")
    private val NUMERIC_DATE = Regex("""(\d{1,2})-(\d{1,2})-(\d{4})""")
    private val TIME = Regex("""(\d{1,2}):(\d{2})""")
    private val NUMBER = Regex("""-?\d+(?:[.,]\d+)?""")

    /** The format of the portal's date fields and period headers ("02-11-2026"). */
    val FIELD_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    /** "april", "apr", "mrt." -> 4, 4, 3; null when it isn't a month. */
    fun month(name: String): Int? = MONTHS[name.trim().trimEnd('.').lowercase()]

    /** "wo" (or "woensdag") -> WEDNESDAY. */
    fun weekday(name: String): DayOfWeek? = WEEKDAYS[name.trim().lowercase().take(2)]

    /** "8 april 2026" -> 2026-04-08. */
    fun parseDate(text: String?): LocalDate? {
        val tokens = text?.trim()?.split(WHITESPACE) ?: return null
        if (tokens.size < 3) return null
        val day = tokens[0].toIntOrNull() ?: return null
        val month = month(tokens[1]) ?: return null
        val year = tokens[2].toIntOrNull() ?: return null
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    /** "2 november" -> the date with that day and month nearest to [near]. */
    fun parseDayMonth(text: String?, near: LocalDate): LocalDate? {
        val tokens = text?.trim()?.split(WHITESPACE) ?: return null
        if (tokens.size < 2) return null
        val day = tokens[0].toIntOrNull() ?: return null
        val month = month(tokens[1]) ?: return null
        return (near.year - 1..near.year + 1)
            .mapNotNull { runCatching { LocalDate.of(it, month, day) }.getOrNull() }
            .minByOrNull { abs(ChronoUnit.DAYS.between(near, it)) }
    }

    /** The first "1-8-2026" or "02-11-2026" in [text]. */
    fun parseNumericDate(text: String?): LocalDate? {
        val match = NUMERIC_DATE.find(text ?: return null) ?: return null
        val (d, m, y) = match.destructured
        return runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
    }

    /** All "d-M-yyyy" dates in [text], in order. */
    fun numericDates(text: String): List<LocalDate> = NUMERIC_DATE.findAll(text).mapNotNull {
        val (d, m, y) = it.destructured
        runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
    }.toList()

    /** "9:05" or "14:15" -> a time. */
    fun parseTime(text: String?): LocalTime? {
        val match = TIME.find(text ?: return null) ?: return null
        val (h, m) = match.destructured
        return runCatching { LocalTime.of(h.toInt(), m.toInt()) }.getOrNull()
    }

    /** "6,1" -> 6.1, "10" -> 10.0, "V" or "-" -> null. Only when the whole text is a number. */
    fun parseNumber(text: String?): Double? {
        val trimmed = text?.trim() ?: return null
        if (!NUMBER.matches(trimmed)) return null
        return trimmed.replace(',', '.').toDoubleOrNull()
    }

    /** The first number in [text]: "8 uur" -> 8.0, "Behaalde studiepunten (2,5)" -> 2.5. */
    fun firstNumber(text: String?): Double? =
        text?.let { NUMBER.find(it) }?.value?.replace(',', '.')?.toDoubleOrNull()

    internal fun clean(text: String?): String = text.orEmpty().replace(' ', ' ').trim().replace(WHITESPACE, " ")
}
