package dev.eduartesync.ui

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import dev.eduartesync.R
import dev.eduartesync.calendar.SubjectColors
import dev.eduartesync.subjects.SubjectStore
import dev.eduartesync.subjects.SubjectStyle
import dev.eduartesync.sync.SyncScheduler
import com.google.android.material.R as MaterialR

/**
 * Beta: every subject seen in the schedule, with its own name, emoji and color in the
 * calendar. Changes are applied with a sync right away.
 */
class SubjectsActivity : AppCompatActivity() {
    private lateinit var store: SubjectStore
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subjects)
        applyScreenInsets(showBack = true)
        title = "Subjects"
        store = SubjectStore(this)
        list = findViewById(R.id.subjectList)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val subjects = store.all()
        findViewById<View>(R.id.listCard).visibility = if (subjects.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.empty).visibility = if (subjects.isEmpty()) View.VISIBLE else View.GONE

        list.removeAllViews()
        subjects.forEachIndexed { i, subject ->
            if (i > 0) list.addView(layoutInflater.inflate(R.layout.item_subject_divider, list, false))
            val row = layoutInflater.inflate(R.layout.item_subject, list, false)
            showSwatch(row.findViewById(R.id.swatch), subject)
            row.findViewById<TextView>(R.id.title).text = displayTitle(subject)
            row.findViewById<TextView>(R.id.original).text =
                if (isCustomized(subject)) "Eduarte: ${subject.name}" else "Tap to customize"
            row.setOnClickListener { edit(subject) }
            list.addView(row)
        }
    }

    private fun edit(subject: SubjectStyle) {
        val view = layoutInflater.inflate(R.layout.dialog_subject, null)
        val swatch = view.findViewById<TextView>(R.id.swatch)
        val preview = view.findViewById<TextView>(R.id.preview)
        view.findViewById<TextView>(R.id.original).text = "Eduarte: ${subject.name}"
        view.findViewById<TextInputLayout>(R.id.nameLayout).apply {
            placeholderText = subject.name
            helperText = "Leave empty to keep \"${subject.name}\""
        }
        val name = view.findViewById<EditText>(R.id.name).apply { setText(subject.displayName.orEmpty()) }
        val emoji = view.findViewById<EditText>(R.id.emoji).apply { setText(subject.emoji.orEmpty()) }
        val colorLabel = view.findViewById<TextView>(R.id.colorLabel)

        // null = the subject's default color.
        var color: Int? = subject.color
        val defaultColor = SubjectColors.forSubject(subject.name)
        val choices = listOf<Int?>(null) + SubjectColors.palette
        val swatches = mutableListOf<TextView>()

        fun current() = subject.copy(
            displayName = name.text.toString().trim().ifEmpty { null },
            emoji = emoji.text.toString().trim().ifEmpty { null },
            color = color,
        )

        fun update() {
            val style = current()
            showSwatch(swatch, style)
            preview.text = displayTitle(style)
            colorLabel.text = if (color == null) "Color · Default (${colorName(defaultColor)})" else "Color · ${colorName(color!!)}"
            choices.forEachIndexed { i, choice ->
                val selected = choice == color
                swatches[i].background = swatchDrawable(choice ?: defaultColor, selected, dashed = choice == null)
                swatches[i].text = if (selected) "✓" else ""
                swatches[i].isSelected = selected
            }
        }

        // Two rows of 7 equal cells: the default color first, then the palette.
        val grid = view.findViewById<LinearLayout>(R.id.colors)
        choices.chunked(COLUMNS).forEach { rowChoices ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (column in 0 until COLUMNS) {
                val cell = FrameLayout(this)
                val choice = rowChoices.getOrNull(column)
                if (column < rowChoices.size) {
                    val circle = TextView(this).apply {
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        textSize = 16f
                        contentDescription = if (choice == null) "Default color" else colorName(choice)
                        setOnClickListener { color = choice; update() }
                    }
                    swatches += circle
                    cell.addView(circle, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER))
                }
                row.addView(cell, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            grid.addView(row)
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = update()
        }
        name.addTextChangedListener(watcher)
        emoji.addTextChangedListener(watcher)
        update()

        val dialog: AlertDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Edit subject")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                store.save(current())
                applyChanges()
            }
            .setNegativeButton("Cancel", null)
            .create()

        view.findViewById<View>(R.id.reset).setOnClickListener {
            store.save(SubjectStyle(subject.name))
            applyChanges()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.remove).setOnClickListener {
            store.remove(subject.name)
            applyChanges()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun applyChanges() {
        refresh()
        SyncScheduler.syncNow(this)
    }

    private fun isCustomized(subject: SubjectStyle) =
        subject.displayName != null || subject.emoji != null || subject.color != null

    private fun displayTitle(subject: SubjectStyle) =
        listOfNotNull(subject.emoji, subject.displayName ?: subject.name).joinToString(" ")

    private fun colorOf(subject: SubjectStyle) = subject.color ?: SubjectColors.forSubject(subject.name)

    /** Color circle with the emoji in it, or the first letter of the name without one. */
    private fun showSwatch(view: TextView, subject: SubjectStyle) {
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colorOf(subject))
        }
        view.text = subject.emoji ?: (subject.displayName ?: subject.name).trim().take(1).uppercase()
    }

    /** A color choice; the selected one gets a ring around it, the default color a dashed ring. */
    private fun swatchDrawable(color: Int, selected: Boolean, dashed: Boolean): Drawable {
        val fill = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        if (!selected && !dashed) return fill
        val ringColor = MaterialColors.getColor(list, if (selected) MaterialR.attr.colorOnSurface else MaterialR.attr.colorOutline)
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            if (dashed && !selected) setStroke(dp(2), ringColor, dp(4).toFloat(), dp(3).toFloat())
            else setStroke(dp(2), ringColor)
        }
        return LayerDrawable(arrayOf(ring, fill)).apply { setLayerInset(1, dp(5), dp(5), dp(5), dp(5)) }
    }

    private fun colorName(color: Int): String {
        val i = SubjectColors.palette.indexOf(color)
        return COLOR_NAMES.getOrNull(i) ?: "Custom"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val COLUMNS = 7

        /** Same order as [SubjectColors.palette]. */
        val COLOR_NAMES = listOf(
            "Blue", "Green", "Red", "Orange", "Purple", "Cyan",
            "Pink", "Brown", "Indigo", "Light green", "Deep orange", "Blue grey",
        )
    }
}
