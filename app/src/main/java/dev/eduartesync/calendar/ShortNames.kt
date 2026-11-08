package dev.eduartesync.calendar

/**
 * Shorter lesson names for the calendar, so lock screens and widgets that cut the title off
 * still have room for what matters. Only drops parts that say little on their own; a name the
 * user picked on the Subjects page always wins over this.
 *
 * - Level suffixes: "Testing-Advanced" -> "Testing", "DOCKER-INTRO" -> "DOCKER", "PDO-Expert" -> "PDO".
 * - The "LES-" prefix some lessons have: "LES-Solliciteren" -> "Solliciteren".
 * - Long names of three or more capitalized words become initials:
 *   "Object Oriented Programming" -> "OOP". Dutch names with lowercase words
 *   ("Duurzaamheid in het beroep") are left alone, initials of those would be unreadable.
 */
object ShortNames {
    private val PREFIX = Regex("""^les-\s*""", RegexOption.IGNORE_CASE)
    private val LEVEL_SUFFIX = Regex("""[\s-]+(advanced|intro|expert|basis|gevorderd|beginners?)$""", RegexOption.IGNORE_CASE)
    private const val INITIALS_FROM_LENGTH = 18

    fun of(name: String): String {
        val trimmed = name.trim()
        val stripped = trimmed.replace(PREFIX, "").replace(LEVEL_SUFFIX, "").trim()
        val words = stripped.split(Regex("""\s+"""))
        val short = if (stripped.length >= INITIALS_FROM_LENGTH && words.size >= 3 && words.all { it.first().isUpperCase() }) {
            words.joinToString("") { it.first().toString() }
        } else {
            stripped
        }
        return short.ifEmpty { trimmed }
    }
}
