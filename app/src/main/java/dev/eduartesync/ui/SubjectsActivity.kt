package dev.eduartesync.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import dev.eduartesync.R
import dev.eduartesync.calendar.SubjectColors
import dev.eduartesync.subjects.SubjectStore
import dev.eduartesync.subjects.SubjectStyle
import dev.eduartesync.sync.SyncScheduler

/**
 * Beta: every subject seen in the schedule, with its own name, emoji and color in the
 * calendar. Changes are applied with a sync right away.
 */
class SubjectsActivity : Activity() {
    private lateinit var store: SubjectStore
    private lateinit var adapter: ArrayAdapter<SubjectStyle>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Subjects (beta)"
        store = SubjectStore(this)

        adapter = object : ArrayAdapter<SubjectStyle>(this, R.layout.item_subject) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: layoutInflater.inflate(R.layout.item_subject, parent, false)
                val subject = getItem(position)!!
                row.findViewById<View>(R.id.color).background = dot(colorOf(subject))
                row.findViewById<TextView>(R.id.title).text = displayTitle(subject)
                row.findViewById<TextView>(R.id.original).text =
                    if (subject.displayName != null || subject.emoji != null) "Eduarte: ${subject.name}" else "Tap to customize"
                return row
            }
        }
        val list = ListView(this).apply {
            adapter = this@SubjectsActivity.adapter
            emptyView = TextView(this@SubjectsActivity).apply {
                text = "No subjects yet. They show up here after a sync."
                gravity = Gravity.CENTER
                setPadding(48, 48, 48, 48)
            }
            setOnItemClickListener { _, _, position, _ -> edit(this@SubjectsActivity.adapter.getItem(position)!!) }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(list.emptyView)
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        adapter.clear()
        adapter.addAll(store.all())
    }

    private fun edit(subject: SubjectStyle) {
        val view = layoutInflater.inflate(R.layout.dialog_subject, null)
        val emoji = view.findViewById<EditText>(R.id.emoji).apply { setText(subject.emoji.orEmpty()) }
        val name = view.findViewById<EditText>(R.id.name).apply {
            hint = subject.name
            setText(subject.displayName.orEmpty())
        }

        // null = the subject's default color.
        var color: Int? = subject.color
        val swatches = view.findViewById<LinearLayout>(R.id.colors)
        val choices = listOf<Int?>(null) + SubjectColors.palette
        fun markSelected() {
            for (i in 0 until swatches.childCount) swatches.getChildAt(i).alpha = if (choices[i] == color) 1f else 0.35f
        }
        choices.forEach { choice ->
            swatches.addView(View(this).apply {
                background = dot(choice ?: SubjectColors.forSubject(subject.name), outlined = choice == null)
                contentDescription = if (choice == null) "Default color" else "Color"
                setOnClickListener { color = choice; markSelected() }
            }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(8) })
        }
        markSelected()

        AlertDialog.Builder(this)
            .setTitle(subject.name)
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                store.save(subject.copy(
                    displayName = name.text.toString().trim().ifEmpty { null },
                    emoji = emoji.text.toString().trim().ifEmpty { null },
                    color = color,
                ))
                applyChanges()
            }
            .setNeutralButton("Reset") { _, _ ->
                store.save(SubjectStyle(subject.name))
                applyChanges()
            }
            .setNegativeButton("Remove") { _, _ ->
                store.remove(subject.name)
                applyChanges()
            }
            .show()
    }

    private fun applyChanges() {
        refresh()
        SyncScheduler.syncNow(this)
    }

    private fun displayTitle(subject: SubjectStyle) =
        listOfNotNull(subject.emoji, subject.displayName ?: subject.name).joinToString(" ")

    private fun colorOf(subject: SubjectStyle) = subject.color ?: SubjectColors.forSubject(subject.name)

    private fun dot(color: Int, outlined: Boolean = false) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        if (outlined) setStroke(dp(3), Color.GRAY)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
