package dev.eduartesync.ui.rooster

import dev.eduartesync.ui.LoadingLine
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import dev.eduartesync.Config
import dev.eduartesync.R
import dev.eduartesync.auth.SessionManager
import dev.eduartesync.calendar.CalendarWriter
import dev.eduartesync.settings.AppSettings
import dev.eduartesync.sync.SyncScheduler
import dev.eduartesync.ui.MainActivity
import dev.eduartesync.ui.SyncStatus
import dev.eduartesync.ui.SyncWork
import dev.eduartesync.ui.observeSyncWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import java.time.LocalDate

/**
 * Rooster tab: the lessons from the "Eduarte rooster" calendar, a day per page, under its own
 * top bar with the week, the arrows and the sync status. Pull down to sync. Reloads by itself
 * whenever the calendar changes.
 */
class RoosterFragment : Fragment(R.layout.fragment_rooster) {
    private lateinit var settings: AppSettings
    private lateinit var pager: ViewPager2
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var loadingLine: LoadingLine
    private lateinit var weekStrip: WeekStrip
    private lateinit var status: StatusIcon
    private val pages = DayPagerAdapter(onLesson = { showLessonSheet(requireContext(), it) }, onEmptyAction = ::onEmptyAction)

    private var data: ScheduleData? = null
    private var selected: LocalDate? = null
    private var work = SyncWork(running = false, waiting = false, nextPeriodicAt = null)

    /** When the user last pulled to refresh, to keep the spinner up until the sync really starts. */
    private var pulledAt = 0L

    /** The running sync was started by the user here, so its result is always shown. */
    private var userStarted = false

    private val handler = Handler(Looper.getMainLooper())
    private val reloadSoon = Runnable { reload() }
    private val updateSpinner = Runnable { showSpinner() }

    /** Once a minute: "3 min ago" and the "now" marker move on. */
    private val tick = object : Runnable {
        override fun run() {
            status.show(work)
            render()
            handler.postDelayed(this, MINUTE)
        }
    }

    // A sync (or the Subjects page, or another calendar app) changed the calendar.
    private val calendarObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(reloadSoon)
            handler.postDelayed(reloadSoon, RELOAD_DELAY_MS) // a sync writes in bursts
        }
    }
    private var observingCalendar = false

    // The sync result or the synced range changed.
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post {
            if (view != null) {
                status.show(work)
                reload()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        settings = AppSettings(requireContext())
        selected = savedInstanceState?.getString(STATE_SELECTED)?.let(LocalDate::parse)

        status = StatusIcon(
            view.findViewById(R.id.syncStatus),
            onSync = ::startSync,
            onLogIn = { main().openLogin() },
            onSettings = { main().openSettings() },
        )
        weekStrip = WeekStrip(view.findViewById<LinearLayout>(R.id.weekStrip)) { goTo(it, smooth = true) }

        pager = view.findViewById(R.id.pager)
        pager.adapter = pages
        pager.offscreenPageLimit = 1
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val days = data?.days ?: return
                selected = days[position]
                showWeek()
            }

            // While a day is being swiped, a downward drift must not start a pull-to-refresh.
            override fun onPageScrollStateChanged(state: Int) {
                refresh.isEnabled = state == ViewPager2.SCROLL_STATE_IDLE
            }
        })

        refresh = view.findViewById(R.id.refresh)
        loadingLine = LoadingLine.attach(refresh, over = view.findViewById(R.id.body)) { startSync() }
        // The pager can't scroll up itself; ask the list of the day that is showing.
        refresh.setOnChildScrollUpCallback { _, _ -> currentDayList()?.canScrollVertically(-1) == true }

        view.findViewById<View>(R.id.prevWeek).setOnClickListener { jumpWeeks(-1) }
        view.findViewById<View>(R.id.nextWeek).setOnClickListener { jumpWeeks(1) }
        view.findViewById<View>(R.id.weekTitle).setOnClickListener { goTo(today(), smooth = true) }
        view.findViewById<View>(R.id.settings).setOnClickListener { main().openSettings() }

        observeSyncWork(requireContext(), viewLifecycleOwner, ::onSyncWork)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selected?.let { outState.putString(STATE_SELECTED, it.toString()) }
    }

    override fun onStart() {
        super.onStart()
        settings.registerListener(settingsListener)
        handler.post(tick)
        reload()
    }

    override fun onStop() {
        settings.unregisterListener(settingsListener)
        if (observingCalendar) requireContext().contentResolver.unregisterContentObserver(calendarObserver)
        observingCalendar = false
        handler.removeCallbacksAndMessages(null)
        super.onStop()
    }

    /** Reads the calendar in the background and shows it. */
    private fun reload() {
        if (view == null) return
        val context = requireContext().applicationContext
        observeCalendar()
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { loadSchedule(context, today()) }
            data = loaded
            render()
        }
    }

    /** Only possible with calendar access, so tried again on every reload until it works. */
    private fun observeCalendar() {
        if (observingCalendar || !CalendarWriter.hasPermission(requireContext())) return
        requireContext().contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, calendarObserver)
        observingCalendar = true
    }

    private fun render() {
        val data = data ?: return
        val day = selected ?: today()
        pages.update(data, System.currentTimeMillis())
        pager.setCurrentItem(data.indexOf(day), false)
        selected = data.days[pager.currentItem]
        showWeek()
    }

    /** Title, strip and arrows for the week of the chosen day. */
    private fun showWeek() {
        val data = data ?: return
        val day = selected ?: return
        val view = view ?: return
        val monday = mondayOf(day)
        val week = data.days.filter { mondayOf(it) == monday }
        // All seven days: a weekend lesson counts even on a week whose weekend has no page.
        val minutes = lessonMinutes((0L..6L).flatMap { data.lessonsOn(monday.plusDays(it)) })
        view.findViewById<TextView>(R.id.weekTitle).apply {
            text = weekTitle(monday, minutes)
            contentDescription = "$text. Go to today"
        }
        weekStrip.show(week, day, today()) { data.lessonsOn(it).size }

        view.findViewById<View>(R.id.prevWeek).isEnabled = data.days.first() < monday
        view.findViewById<View>(R.id.nextWeek).isEnabled = data.days.last() >= monday.plusWeeks(1)
    }

    private fun jumpWeeks(weeks: Long) {
        val day = selected ?: return
        goTo(mondayOf(day).plusWeeks(weeks), smooth = false)
    }

    private fun goTo(date: LocalDate, smooth: Boolean) {
        val data = data ?: return
        val index = data.indexOf(date)
        // Scrolling past many pages looks busy; only nearby days slide in.
        pager.setCurrentItem(index, smooth && abs(index - pager.currentItem) <= 5)
    }

    private fun currentDayList(): RecyclerView? {
        val pagesList = pager.getChildAt(0) as? RecyclerView ?: return null
        return pagesList.findViewHolderForAdapterPosition(pager.currentItem)?.itemView as? RecyclerView
    }

    /** Pull to refresh, "Sync now" in the status details, or "Sync now" on an empty day. */
    private fun startSync() {
        val context = requireContext()
        when {
            !SessionManager.isLoggedIn(context) -> {
                loadingLine.loading = false
                main().openLogin()
            }
            !CalendarWriter.hasPermission(context) -> {
                loadingLine.loading = false
                main().requestCalendarAccess()
            }
            else -> {
                SyncScheduler.syncNow(context, quiet = true)
                userStarted = true
                pulledAt = System.currentTimeMillis()
                loadingLine.loading = true
                handler.postDelayed(updateSpinner, WAIT_SPINNER_MS)
            }
        }
    }

    private fun onSyncWork(new: SyncWork) {
        val finished = work.manualActive && !new.manualActive
        work = new
        showSpinner()
        status.show(work)
        if (finished) {
            if (!isHidden && isResumed) showResult(userStarted)
            userStarted = false
        }
    }

    /**
     * The spinner follows the sync. A sync waiting for internet keeps it up only briefly after a
     * pull; the status details then explain it starts once you're online.
     */
    private fun showSpinner() {
        val justPulled = System.currentTimeMillis() - pulledAt < WAIT_SPINNER_MS
        loadingLine.loading = work.running || (work.waiting && justPulled)
    }

    /**
     * What the toast says for a sync started outside the app, as a snackbar above the tabs; see
     * [SyncStatus.shouldAnnounce] for when.
     */
    private fun showResult(userStarted: Boolean) {
        val result = settings.lastSyncResult?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() } ?: return
        if (!SyncStatus.shouldAnnounce(result, userStarted)) return
        val snackbar = Snackbar.make(requireView(), result, Snackbar.LENGTH_LONG)
            .setAnchorView(requireActivity().findViewById(R.id.bottomNav))
        if (!result.startsWith("Synced")) snackbar.setAction("Details") { status.showDetails() }
        snackbar.show()
    }

    private fun onEmptyAction(kind: EmptyDay) = when (kind) {
        EmptyDay.NO_ACCESS -> main().requestCalendarAccess()
        EmptyDay.NOT_SYNCED_YET -> startSync()
        EmptyDay.NOT_SYNCED_THIS_FAR -> main().openSettings()
        EmptyDay.NO_LESSONS -> Unit
    }

    private fun main() = requireActivity() as MainActivity

    private fun today() = LocalDate.now(Config.ZONE)

    private companion object {
        const val STATE_SELECTED = "selected_day"
        const val MINUTE = 60_000L
        const val RELOAD_DELAY_MS = 400L
        const val WAIT_SPINNER_MS = 4_000L
    }
}
