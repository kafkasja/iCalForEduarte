package dev.eduartesync.ui.rooster

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.eduartesync.Config
import dev.eduartesync.R
import java.time.LocalDate

/** One page per day for the Rooster pager; each page is a [DayAdapter] list. */
class DayPagerAdapter(
    private val onLesson: (ScheduleLesson) -> Unit,
    private val onEmptyAction: (EmptyDay) -> Unit,
) : RecyclerView.Adapter<DayPagerAdapter.Page>() {
    class Page(val list: RecyclerView) : RecyclerView.ViewHolder(list)

    private var data: ScheduleData? = null
    private var now = 0L

    // The pages' lists use the same kinds of rows, so they share recycled rows.
    private val rowPool = RecyclerView.RecycledViewPool()

    init {
        // A page is its date, so pages keep their scroll position when the data is refreshed.
        setHasStableIds(true)
    }

    // Days can appear or disappear (a weekend with lessons), so everything is redone.
    @SuppressLint("NotifyDataSetChanged")
    fun update(data: ScheduleData, now: Long) {
        this.data = data
        this.now = now
        notifyDataSetChanged()
    }

    override fun getItemCount() = data?.days?.size ?: 0

    override fun getItemId(position: Int) = data!!.days[position].toEpochDay()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Page {
        val list = LayoutInflater.from(parent.context).inflate(R.layout.item_day_page, parent, false) as RecyclerView
        list.layoutManager = LinearLayoutManager(parent.context)
        list.setRecycledViewPool(rowPool)
        list.adapter = DayAdapter(onLesson, onEmptyAction)
        return Page(list)
    }

    override fun onBindViewHolder(holder: Page, position: Int) {
        val data = data ?: return
        (holder.list.adapter as DayAdapter).submit(rowsFor(data, data.days[position]))
    }

    private fun rowsFor(data: ScheduleData, date: LocalDate): List<DayRow> {
        val today = dateOf(now, Config.ZONE)
        val lessons = data.lessonsOn(date)
        val rows = mutableListOf<DayRow>(DayRow.Header(date, lessons, today))
        val empty = emptyDay(date, lessons.isNotEmpty(), data.access, data.syncedUntil)
        if (empty != null) {
            rows += DayRow.Empty(empty, past = date.isBefore(today))
        } else {
            val isToday = date == today
            rows += dayItems(lessons, now.takeIf { isToday }).map { item ->
                val ended = isToday && item is DayItem.Lesson && item.lesson.endMillis <= now
                DayRow.Item(item, ended)
            }
        }
        return rows
    }
}
