package dev.eduartesync.ui.portal

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import com.google.android.material.color.MaterialColors
import dev.eduartesync.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.google.android.material.R as MaterialR

/** "5 Nov 2026", like the dates on the Cijfers tab but with the year (these lists go back years). */
internal val recordDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

internal fun LocalDate.display(): String = format(recordDate)

/** A section title, as on the Meer tab. */
internal fun sectionHeader(inflater: LayoutInflater, parent: ViewGroup, title: String): TextView =
    (inflater.inflate(R.layout.item_section_header, parent, false) as TextView).also { it.text = title }

/**
 * A card holding [rows], with a divider between each two. Returns the card; [parent] isn't
 * changed, so the caller decides where it goes.
 */
internal fun cardOf(inflater: LayoutInflater, parent: ViewGroup, rows: List<View>): View {
    val card = inflater.inflate(R.layout.item_card, parent, false)
    val list = card.findViewById<ViewGroup>(R.id.rows)
    rows.forEachIndexed { i, row ->
        if (i > 0) list.addView(inflater.inflate(R.layout.item_row_divider, list, false))
        list.addView(row)
    }
    return card
}

/** Colors of a value label: container background and the text on it. */
internal enum class Tone(@AttrRes val background: Int, @AttrRes val foreground: Int) {
    NEUTRAL(MaterialR.attr.colorSurfaceContainerHighest, MaterialR.attr.colorOnSurface),
    ACCENT(MaterialR.attr.colorSecondaryContainer, MaterialR.attr.colorOnSecondaryContainer),
    PRIMARY(MaterialR.attr.colorPrimaryContainer, MaterialR.attr.colorOnPrimaryContainer),
    ERROR(MaterialR.attr.colorErrorContainer, MaterialR.attr.colorOnErrorContainer),
}

/**
 * One line of a card: [title], [summary] underneath (hidden when null), an optional [value]
 * label on the right, and a chevron (or [trailingIcon]) when it can be tapped.
 */
internal fun recordRow(
    inflater: LayoutInflater,
    parent: ViewGroup,
    title: String,
    summary: String? = null,
    value: String? = null,
    tone: Tone = Tone.NEUTRAL,
    @DrawableRes trailingIcon: Int = R.drawable.ic_chevron_right,
    onClick: (() -> Unit)? = null,
): View {
    val row = inflater.inflate(R.layout.item_record_row, parent, false)
    row.findViewById<TextView>(R.id.title).text = title
    row.findViewById<TextView>(R.id.summary).apply {
        text = summary
        visibility = if (summary.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
    row.findViewById<TextView>(R.id.value).let { if (value != null) styleBadge(it, value, tone) }
    if (onClick != null) {
        row.findViewById<ImageView>(R.id.trailing).apply {
            setImageResource(trailingIcon)
            visibility = View.VISIBLE
        }
        val outValue = android.util.TypedValue()
        row.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        row.setBackgroundResource(outValue.resourceId)
        row.isClickable = true
        row.isFocusable = true
        row.setOnClickListener { onClick() }
    }
    return row
}

/** Fills a Badge-styled label and shows it. */
internal fun styleBadge(view: TextView, text: String, tone: Tone) {
    view.text = text
    view.backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(view, tone.background))
    view.setTextColor(MaterialColors.getColor(view, tone.foreground))
    view.visibility = View.VISIBLE
}

/** Small print between cards, e.g. what a list leaves out. */
internal fun footnote(inflater: LayoutInflater, parent: ViewGroup, text: String): TextView =
    TextView(inflater.context, null, 0, R.style.Footnote).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        this.text = text
    }
