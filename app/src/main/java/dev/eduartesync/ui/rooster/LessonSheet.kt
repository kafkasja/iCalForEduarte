package dev.eduartesync.ui.rooster

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.eduartesync.Config
import dev.eduartesync.R
import dev.eduartesync.calendar.ShortNames
import dev.eduartesync.settings.AppSettings
import dev.eduartesync.subjects.SubjectStore
import dev.eduartesync.subjects.SubjectStyle
import dev.eduartesync.sync.SyncScheduler
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.google.android.material.R as MaterialR

private val sheetDay = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

/** Bottom sheet with everything known about a tapped lesson, and a way into the calendar app. */
@SuppressLint("InflateParams") // the sheet's content has no parent until setContentView
fun showLessonSheet(context: Context, lesson: ScheduleLesson) {
    val view = LayoutInflater.from(context).inflate(R.layout.sheet_lesson, null)
    val dialog = BottomSheetDialog(context)

    view.findViewById<View>(R.id.colorDot).backgroundTintList = ColorStateList.valueOf(lesson.color)
    view.findViewById<TextView>(R.id.name).text = lesson.name
    view.findViewById<TextView>(R.id.cancelled).apply {
        visibility = if (lesson.cancelled) View.VISIBLE else View.GONE
        backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(this, MaterialR.attr.colorErrorContainer))
        setTextColor(MaterialColors.getColor(this, MaterialR.attr.colorOnErrorContainer))
    }

    val date = dateOf(lesson.startMillis, Config.ZONE).format(sheetDay)
    val minutes = (lesson.endMillis - lesson.startMillis) / 60_000
    line(view, R.id.time, "$date\n${clock(lesson.startMillis)} – ${clock(lesson.endMillis)} (${formatDuration(minutes)})")
    line(view, R.id.room, lesson.room.takeIf { it.isNotBlank() }?.let { "Room $it" })
    line(view, R.id.teacher, lesson.teacher)
    line(view, R.id.group, lesson.group?.let { "Group $it" })

    view.findViewById<View>(R.id.rename).setOnClickListener {
        dialog.dismiss()
        renameLesson(context, lesson)
    }
    view.findViewById<View>(R.id.openInCalendar).setOnClickListener {
        openInCalendar(context, lesson)
        dialog.dismiss()
    }
    dialog.setContentView(view)
    dialog.show()
}

/**
 * Lets the user give the lesson's subject their own name, or go back to the automatic one. The
 * name is the same one the Subjects page edits, so it applies to every lesson of the subject;
 * the sync that follows renames the events.
 */
private fun renameLesson(context: Context, lesson: ScheduleLesson) {
    val subject = originalSubject(context, lesson.eventId)
    if (subject == null) {
        Toast.makeText(context, "This lesson can't be renamed. Sync once and try again.", Toast.LENGTH_LONG).show()
        return
    }
    val store = SubjectStore(context)
    val style = store.byName()[subject] ?: SubjectStyle(subject)
    val custom = style.displayName?.takeIf { it.isNotBlank() }
    val automatic = if (AppSettings(context).shortNames) ShortNames.of(subject) else subject

    val field = TextInputEditText(context).apply {
        setText(custom.orEmpty())
        setSelection(text?.length ?: 0)
        isSingleLine = true
    }
    val layout = TextInputLayout(context, null, MaterialR.attr.textInputOutlinedStyle).apply {
        hint = "Name in calendar"
        placeholderText = automatic
        helperText = if (automatic == subject) "Leave empty to use \"$subject\"."
        else "Eduarte calls it \"$subject\". Leave empty to use \"$automatic\"."
        endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
        addView(field)
    }
    val pad = (24 * context.resources.displayMetrics.density).toInt()
    val container = FrameLayout(context).apply { setPadding(pad, pad / 3, pad, 0); addView(layout) }

    val save = { name: String? ->
        store.save(style.copy(displayName = name?.trim()?.takeIf { it.isNotEmpty() && it != subject }))
        SyncScheduler.syncNow(context, quiet = true) // renames the events of this subject
        Toast.makeText(context, "Renaming in your calendar…", Toast.LENGTH_SHORT).show()
    }
    MaterialAlertDialogBuilder(context)
        .setTitle("Rename lesson")
        .setView(container)
        .setPositiveButton("Save") { _, _ -> save(field.text?.toString()) }
        // No third "original name" button: three buttons stack vertically. Clearing the field
        // (its x) and saving does the same.
        .setNegativeButton("Cancel", null)
        .show()
    field.requestFocus()
}

/**
 * The subject as Eduarte names it, from the key the sync stored with the event
 * ("date|time|subject|group"), since the title may be shortened or renamed.
 */
private fun originalSubject(context: Context, eventId: Long): String? = runCatching {
    context.contentResolver.query(
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
        arrayOf(CalendarContract.Events.SYNC_DATA1), null, null, null,
    )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull()?.split('|')?.getOrNull(2)?.takeIf { it.isNotBlank() }

private fun line(view: View, id: Int, text: String?) {
    view.findViewById<TextView>(id).apply {
        this.text = text
        visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }
}

/** Opens the event in the phone's calendar app, the same way a tapped widget row does. */
private fun openInCalendar(context: Context, lesson: ScheduleLesson) {
    val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, lesson.eventId))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, lesson.startMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, lesson.endMillis)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.widget_no_calendar_app, Toast.LENGTH_LONG).show()
    }
}
