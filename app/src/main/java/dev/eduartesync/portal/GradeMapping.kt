package dev.eduartesync.portal

import dev.eduartesync.grades.Grade

/**
 * Maps the portal's grades to the UI's [Grade]. Averages from the matrix's last column are
 * left out unless [includeAverages]; their description is "Gemiddelde".
 */
fun toGrades(grades: List<PortalGrade>, includeAverages: Boolean = false): List<Grade> =
    grades.filter { includeAverages || !it.isAverage }.map { grade ->
        val detail = grade.detail
        Grade(
            subject = grade.subject,
            description = detail?.title ?: detail?.code ?: if (grade.isAverage) "Gemiddelde" else "",
            value = grade.value,
            numeric = grade.numeric,
            weight = detail?.weight,
            date = detail?.date,
            period = detail?.period,
        )
    }
