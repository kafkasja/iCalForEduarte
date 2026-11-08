package dev.eduartesync.ui.grades

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import dev.eduartesync.R
import dev.eduartesync.grades.Grade
import dev.eduartesync.grades.SubjectGrades
import dev.eduartesync.grades.formatDutch
import dev.eduartesync.grades.formatWeight
import dev.eduartesync.grades.isInsufficient
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.google.android.material.R as MaterialR

private val gradeDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** One grade row. [showSubject] in the Recent list, where grades of all subjects are mixed. */
internal fun gradeRow(inflater: LayoutInflater, parent: ViewGroup, grade: Grade, showSubject: Boolean): View {
    val row = inflater.inflate(R.layout.item_grade, parent, false)
    row.findViewById<TextView>(R.id.description).text = grade.description.ifBlank { grade.subject }
    val meta = listOfNotNull(
        grade.subject.takeIf { showSubject },
        grade.date?.format(gradeDate),
        grade.period.takeIf { !showSubject },
        formatWeight(grade.weight),
    ).joinToString(" · ")
    row.findViewById<TextView>(R.id.meta).apply {
        text = meta
        visibility = if (meta.isEmpty()) View.GONE else View.VISIBLE
    }
    styleValue(row.findViewById(R.id.value), grade.value, insufficient = isInsufficient(grade), numeric = grade.numeric != null)
    return row
}

/** A subject card; the header toggles the list of its grades. */
internal fun subjectCard(
    inflater: LayoutInflater,
    parent: ViewGroup,
    subject: SubjectGrades,
    expanded: Boolean,
    onToggle: () -> Unit,
): View {
    val card = inflater.inflate(R.layout.item_grade_subject, parent, false)
    card.findViewById<TextView>(R.id.subject).text = subject.subject
    val count = subject.grades.size
    card.findViewById<TextView>(R.id.count).text = if (count == 1) "1 grade" else "$count grades"

    val average = card.findViewById<TextView>(R.id.average)
    if (subject.average == null) {
        average.visibility = View.GONE
    } else {
        styleValue(average, formatDutch(subject.average), insufficient = isInsufficient(subject.average), numeric = true)
        average.contentDescription = "Average ${formatDutch(subject.average)}"
    }

    val list = card.findViewById<ViewGroup>(R.id.grades)
    if (expanded) {
        subject.grades.forEach { grade ->
            list.addView(inflater.inflate(R.layout.item_row_divider, list, false))
            list.addView(gradeRow(inflater, list, grade, showSubject = false))
        }
    }
    list.visibility = if (expanded) View.VISIBLE else View.GONE
    card.findViewById<View>(R.id.expand).rotation = if (expanded) 180f else 0f

    card.findViewById<View>(R.id.header).apply {
        setOnClickListener { onToggle() }
        contentDescription = "${subject.subject}, $count grades" +
            (subject.average?.let { ", average ${formatDutch(it)}" } ?: "") +
            if (expanded) ", expanded" else ", collapsed"
    }
    return card
}

/** Red below 5,5; numbers tinted, letters ("V", "G") neutral. */
private fun styleValue(view: TextView, text: String, insufficient: Boolean, numeric: Boolean) {
    val (background, foreground) = when {
        insufficient -> MaterialR.attr.colorErrorContainer to MaterialR.attr.colorOnErrorContainer
        numeric -> MaterialR.attr.colorSecondaryContainer to MaterialR.attr.colorOnSecondaryContainer
        else -> MaterialR.attr.colorSurfaceContainerHighest to MaterialR.attr.colorOnSurface
    }
    view.text = text
    view.backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(view, background))
    view.setTextColor(MaterialColors.getColor(view, foreground))
    view.visibility = View.VISIBLE
}
