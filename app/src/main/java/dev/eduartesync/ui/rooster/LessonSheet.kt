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
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import dev.eduartesync.Config
import dev.eduartesync.R
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

    view.findViewById<View>(R.id.openInCalendar).setOnClickListener {
        openInCalendar(context, lesson)
        dialog.dismiss()
    }
    dialog.setContentView(view)
    dialog.show()
}

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
