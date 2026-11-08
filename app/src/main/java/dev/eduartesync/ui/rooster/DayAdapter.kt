package dev.eduartesync.ui.rooster

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import dev.eduartesync.Config
import dev.eduartesync.R
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.google.android.material.R as MaterialR

/** A row in one day's list. */
sealed interface DayRow {
    data class Header(val date: LocalDate, val lessons: List<ScheduleLesson>, val today: LocalDate) : DayRow
    data class Item(val item: DayItem, val ended: Boolean) : DayRow
    data class Empty(val kind: EmptyDay, val past: Boolean) : DayRow
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

internal fun clock(millis: Long): String = Instant.ofEpochMilli(millis).atZone(Config.ZONE).format(timeFormat)

/** The list of one day in the Rooster pager: header, then lessons with free time, or why it's empty. */
class DayAdapter(
    private val onLesson: (ScheduleLesson) -> Unit,
    private val onEmptyAction: (EmptyDay) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var rows: List<DayRow> = emptyList()

    // The rows are rebuilt from scratch (every minute for "now") and lists are short.
    @SuppressLint("NotifyDataSetChanged")
    fun submit(rows: List<DayRow>) {
        this.rows = rows
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) = when (val row = rows[position]) {
        is DayRow.Header -> R.layout.item_day_header
        is DayRow.Empty -> R.layout.item_day_empty
        is DayRow.Item -> when (row.item) {
            is DayItem.Lesson -> R.layout.item_lesson
            is DayItem.Free -> R.layout.item_free
            is DayItem.Now -> R.layout.item_now
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        object : RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(viewType, parent, false)) {}

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val view = holder.itemView
        when (val row = rows[position]) {
            is DayRow.Header -> bindHeader(view, row)
            is DayRow.Empty -> bindEmpty(view, row)
            is DayRow.Item -> when (val item = row.item) {
                is DayItem.Lesson -> bindLesson(view, item, row.ended)
                is DayItem.Free -> bindFree(view, item)
                is DayItem.Now -> view.findViewById<TextView>(R.id.text).text = "Now ${clock(item.millis)}"
            }
        }
    }

    private fun bindHeader(view: View, row: DayRow.Header) {
        view.findViewById<TextView>(R.id.date).text = row.date.format(dayFormat)
        val relative = when (row.date) {
            row.today -> "Today"
            row.today.plusDays(1) -> "Tomorrow"
            row.today.minusDays(1) -> "Yesterday"
            else -> null
        }
        val lessons = row.lessons
        val count = when {
            lessons.isEmpty() -> null
            else -> {
                val cancelled = lessons.count { it.cancelled }
                val base = if (lessons.size == 1) "1 lesson" else "${lessons.size} lessons"
                if (cancelled > 0) "$base ($cancelled cancelled)" else base
            }
        }
        val hours = if (lessons.isEmpty()) null else "${clock(lessons.minOf { it.startMillis })} – ${clock(lessons.maxOf { it.endMillis })}"
        val summary = listOfNotNull(relative, count, hours).joinToString(" · ")
        view.findViewById<TextView>(R.id.summary).apply {
            text = summary
            visibility = if (summary.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun bindLesson(view: View, item: DayItem.Lesson, ended: Boolean) {
        val lesson = item.lesson
        val card = view as MaterialCardView
        card.strokeWidth = if (item.now) view.resources.getDimensionPixelSize(R.dimen.now_stroke) else 0
        // Lessons that are over fade back a little, so the eye goes to what's next.
        card.getChildAt(0).alpha = if (ended) 0.6f else 1f

        view.findViewById<TextView>(R.id.start).text = clock(lesson.startMillis)
        view.findViewById<TextView>(R.id.end).text = clock(lesson.endMillis)
        view.findViewById<View>(R.id.colorBar).backgroundTintList = ColorStateList.valueOf(lesson.color)

        view.findViewById<TextView>(R.id.name).apply {
            text = lesson.name
            paintFlags = if (lesson.cancelled) paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
        val badge = view.findViewById<TextView>(R.id.badge)
        when {
            lesson.cancelled -> showBadge(badge, "Cancelled", MaterialR.attr.colorErrorContainer, MaterialR.attr.colorOnErrorContainer)
            item.now -> showBadge(badge, "Now", MaterialR.attr.colorPrimary, MaterialR.attr.colorOnPrimary)
            else -> badge.visibility = View.GONE
        }

        view.findViewById<TextView>(R.id.room).apply {
            text = lesson.room
            visibility = if (lesson.room.isBlank()) View.GONE else View.VISIBLE
        }
        val who = listOfNotNull(lesson.teacher, lesson.group).joinToString(" · ")
        view.findViewById<TextView>(R.id.teacher).apply {
            text = who
            visibility = if (who.isEmpty()) View.GONE else View.VISIBLE
        }

        view.contentDescription = listOfNotNull(
            lesson.name,
            "${clock(lesson.startMillis)} to ${clock(lesson.endMillis)}",
            lesson.room.takeIf { it.isNotBlank() }?.let { "room $it" },
            who.takeIf { it.isNotEmpty() },
            "cancelled".takeIf { lesson.cancelled },
            "now".takeIf { item.now },
        ).joinToString(", ")
        view.setOnClickListener { onLesson(lesson) }
    }

    private fun bindFree(view: View, item: DayItem.Free) {
        view.findViewById<TextView>(R.id.text).apply {
            text = if (item.now) "Free now · until ${clock(item.endMillis)}" else "Free · ${formatDuration(item.minutes)}"
            setTextColor(MaterialColors.getColor(this, if (item.now) MaterialR.attr.colorPrimary else MaterialR.attr.colorOnSurfaceVariant))
        }
    }

    private fun bindEmpty(view: View, row: DayRow.Empty) {
        val (icon, title, message, action) = when (row.kind) {
            EmptyDay.NO_ACCESS -> EmptyText(
                R.drawable.ic_lock, "Calendar access needed",
                "Your rooster is kept in a calendar on this phone. Allow access to see it here.", "Allow access",
            )
            EmptyDay.NOT_SYNCED_YET -> EmptyText(
                R.drawable.ic_sync, "Not synced yet", "Pull down to get your rooster from Eduarte.", "Sync now",
            )
            EmptyDay.NOT_SYNCED_THIS_FAR -> EmptyText(
                R.drawable.ic_calendar, "Not synced this far ahead",
                "Change the range in settings to see more weeks.", "Open settings",
            )
            EmptyDay.NO_LESSONS -> EmptyText(
                R.drawable.ic_event_busy, "No lessons",
                if (row.past) "There were no lessons on this day." else "Nothing on your rooster. Enjoy the free day.", null,
            )
        }
        view.findViewById<ImageView>(R.id.icon).setImageResource(icon)
        view.findViewById<TextView>(R.id.title).text = title
        view.findViewById<TextView>(R.id.message).text = message
        view.findViewById<Button>(R.id.action).apply {
            visibility = if (action == null) View.GONE else View.VISIBLE
            text = action
            setOnClickListener { onEmptyAction(row.kind) }
        }
    }

    private data class EmptyText(val icon: Int, val title: String, val message: String, val action: String?)

    private fun showBadge(badge: TextView, text: String, background: Int, foreground: Int) {
        badge.text = text
        badge.backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(badge, background))
        badge.setTextColor(MaterialColors.getColor(badge, foreground))
        badge.visibility = View.VISIBLE
    }
}
