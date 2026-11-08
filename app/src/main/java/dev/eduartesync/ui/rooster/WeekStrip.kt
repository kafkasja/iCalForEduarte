package dev.eduartesync.ui.rooster

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import dev.eduartesync.R
import com.google.android.material.R as MaterialR

/**
 * The row of days under the week header: the chosen day filled in, today outlined, a dot under
 * days with lessons. Tapping a day picks it.
 */
class WeekStrip(private val container: LinearLayout, private val onPick: (LocalDate) -> Unit) {
    private val longDay = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

    fun show(days: List<LocalDate>, selected: LocalDate, today: LocalDate, lessonCount: (LocalDate) -> Int) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        for (day in days) {
            val cell = inflater.inflate(R.layout.item_week_day, container, false)
            val number = cell.findViewById<TextView>(R.id.number)
            cell.findViewById<TextView>(R.id.weekday).text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            number.text = day.dayOfMonth.toString()
            style(number, isSelected = day == selected, isToday = day == today)

            val count = lessonCount(day)
            cell.findViewById<View>(R.id.dot).apply {
                visibility = if (count > 0) View.VISIBLE else View.INVISIBLE
                backgroundTintList = ColorStateList.valueOf(color(MaterialR.attr.colorOutline))
            }
            cell.contentDescription = buildString {
                append(day.format(longDay))
                if (day == today) append(", today")
                append(if (count == 1) ", 1 lesson" else ", $count lessons")
            }
            cell.isSelected = day == selected
            cell.setOnClickListener { onPick(day) }
            container.addView(cell)
        }
    }

    private fun style(number: TextView, isSelected: Boolean, isToday: Boolean) {
        val circle = GradientDrawable().apply { shape = GradientDrawable.OVAL }
        when {
            isSelected -> {
                circle.setColor(color(MaterialR.attr.colorPrimary))
                number.setTextColor(color(MaterialR.attr.colorOnPrimary))
            }
            isToday -> {
                circle.setStroke(number.resources.displayMetrics.density.toInt() * 2, color(MaterialR.attr.colorPrimary))
                number.setTextColor(color(MaterialR.attr.colorPrimary))
            }
            else -> number.setTextColor(color(MaterialR.attr.colorOnSurface))
        }
        number.background = if (isSelected || isToday) circle else null
    }

    private fun color(attr: Int) = MaterialColors.getColor(container, attr)
}
