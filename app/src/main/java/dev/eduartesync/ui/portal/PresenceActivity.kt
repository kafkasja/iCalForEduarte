package dev.eduartesync.ui.portal

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import dev.eduartesync.Config
import dev.eduartesync.R
import dev.eduartesync.auth.LoginActivity
import dev.eduartesync.portal.AttendanceEntry
import dev.eduartesync.portal.PresencePeriod
import dev.eduartesync.presence.LessonStatus
import dev.eduartesync.presence.PresenceLevel
import dev.eduartesync.presence.PresenceOverview
import dev.eduartesync.presence.PresenceStore
import dev.eduartesync.presence.SubjectPresence
import dev.eduartesync.presence.formatHours
import dev.eduartesync.presence.formatPercent
import dev.eduartesync.presence.lessonStatus
import dev.eduartesync.presence.lessonsByDay
import dev.eduartesync.presence.lowestFirst
import dev.eduartesync.presence.presenceLevel
import dev.eduartesync.presence.presenceNumbers
import dev.eduartesync.ui.SyncStatus
import dev.eduartesync.ui.applyScreenInsets
import dev.eduartesync.ui.prepareScreen
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.google.android.material.R as MaterialR

/**
 * Presentie: how much you were there, as the portal's Presentie pages show it. A ring with the
 * present percentage for the chosen period, the excused and unexcused absence, every subject
 * from lowest percentage up, and the recent lessons one by one.
 *
 * Shows what was read last time right away; see [PresenceViewModel] for what is read when.
 */
class PresenceActivity : AppCompatActivity() {
    private val model: PresenceViewModel by viewModels()
    private lateinit var store: PresenceStore
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var content: LinearLayout

    /** "Show all days" was tapped under the recent lessons. */
    private var allDays = false

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_portal_list)
        applyScreenInsets(showBack = true)
        findViewById<CollapsingToolbarLayout>(R.id.collapsing).title = TITLE
        title = TITLE
        store = PresenceStore(this)
        content = findViewById(R.id.content)
        allDays = savedInstanceState?.getBoolean(STATE_ALL_DAYS) ?: false

        refresh = findViewById(R.id.refresh)
        refresh.setColorSchemeColors(MaterialColors.getColor(refresh, MaterialR.attr.colorPrimary))
        refresh.setProgressBackgroundColorSchemeColor(MaterialColors.getColor(refresh, MaterialR.attr.colorSurfaceContainerHigh))
        refresh.setOnRefreshListener { model.refreshAll() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.state.collect(::render) }
                launch { model.messages.collect(::showProblem) }
            }
        }
        if (savedInstanceState == null) model.choose(model.state.value.period)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ALL_DAYS, allDays)
    }

    private fun render(state: PresenceViewModel.State) {
        val stored = store.load(state.period)
        val overview = stored?.value
        refresh.isRefreshing = state.loadingTotals

        findViewById<TextView>(R.id.updated).apply {
            text = stored?.let { "Updated ${SyncStatus.ago(it.fetchedAt, System.currentTimeMillis())}" }
            visibility = if (stored != null) View.VISIBLE else View.GONE
        }

        content.removeAllViews()
        content.addView(header(state.period, overview))
        if (overview != null) {
            absenceSection(overview)
            subjectSection(state, overview)
        }
        recentSection(state)
        showEmpty(state, overview == null)
    }

    // ---- Header: ring, hours, period chips ----

    private fun header(period: PresencePeriod, overview: PresenceOverview?): View {
        val view = layoutInflater.inflate(R.layout.view_presence_header, content, false)
        val numbers = overview?.presence
        val percent = numbers?.percent?.takeIf { numbers.hasData }
        val ring = view.findViewById<RingView>(R.id.ring)
        ring.percent = percent
        percent?.let { ring.color = barColor(presenceLevel(it)) }

        view.findViewById<TextView>(R.id.periodLabel).text = period.phrase.replaceFirstChar { it.uppercase() }
        val present = view.findViewById<TextView>(R.id.presentHours)
        val absent = view.findViewById<TextView>(R.id.absentHours)
        when {
            numbers == null -> {
                present.text = "Not read yet"
                absent.visibility = View.GONE
            }
            !numbers.hasData -> {
                present.text = "No lessons registered"
                absent.visibility = View.GONE
            }
            else -> {
                present.text = numbers.presentHours?.let { "${hours(it)} present" } ?: "Present hours unknown"
                absent.text = numbers.absentHours?.let { "${hours(it)} absent" }
                absent.visibility = if (numbers.absentHours == null) View.GONE else View.VISIBLE
            }
        }
        val level = view.findViewById<TextView>(R.id.level)
        if (percent != null) {
            val l = presenceLevel(percent)
            val text = when (l) {
                PresenceLevel.GOOD -> "On track"
                PresenceLevel.WARNING -> "Below 90%"
                PresenceLevel.LOW -> "Below 80%"
            }
            styleLevel(level, text, l)
        }
        view.findViewById<View>(R.id.summaryArea).apply {
            isFocusable = true
            contentDescription = if (percent != null) {
                "${formatPercent(percent)} present ${period.phrase}. ${present.text}. ${if (absent.visibility == View.VISIBLE) absent.text else ""}"
            } else {
                "${period.phrase}: ${present.text}"
            }
        }

        val chips = view.findViewById<ChipGroup>(R.id.periods)
        for (p in PresencePeriod.entries) {
            val chip = layoutInflater.inflate(R.layout.item_period_chip, chips, false) as Chip
            chip.id = View.generateViewId()
            chip.text = p.label
            chip.tag = p
            chips.addView(chip)
            if (p == period) chip.isChecked = true
        }
        chips.setOnCheckedStateChangeListener { group, ids ->
            val chosen = ids.firstOrNull()?.let { group.findViewById<Chip>(it)?.tag as? PresencePeriod } ?: return@setOnCheckedStateChangeListener
            if (chosen != model.state.value.period) model.choose(chosen)
        }
        return view
    }

    // ---- Absence ----

    private fun absenceSection(overview: PresenceOverview) {
        val absence = overview.absence ?: return
        if (listOf(absence.authorizedHours, absence.unauthorizedHours, absence.authorizedReports, absence.unauthorizedReports).all { it == null }) return
        content.addView(sectionHeader(layoutInflater, content, "Absence"))
        val rows = listOf(
            absenceRow("Geoorloofd", "Excused", absence.authorizedHours, absence.authorizedReports, alarming = false),
            absenceRow("Ongeoorloofd", "Unexcused", absence.unauthorizedHours, absence.unauthorizedReports, alarming = true),
        )
        content.addView(cardOf(layoutInflater, content, rows))
    }

    private fun absenceRow(title: String, meaning: String, hours: Double?, reports: Double?, alarming: Boolean): View {
        val reportText = reports?.let { val n = it.toLong(); if (n == 1L) "1 report" else "$n reports" }
        return recordRow(
            layoutInflater, content,
            title = title,
            summary = listOfNotNull(meaning, reportText).joinToString(" · "),
            value = hours?.let { hours(it) },
            tone = if (alarming && (hours ?: 0.0) > 0.0) Tone.ERROR else Tone.NEUTRAL,
        )
    }

    // ---- Per subject ----

    private fun subjectSection(state: PresenceViewModel.State, overview: PresenceOverview) {
        content.addView(sectionHeader(layoutInflater, content, "Per subject"))
        val running = state.perSubject
        val subjects = overview.subjects
            ?: running?.subjects?.map { SubjectPresence(it.label, presenceNumbers(it.charts)) }

        if (running != null) {
            val progress = if (running.total > 0) " ${running.read}/${running.total}" else ""
            content.addView(footnote(layoutInflater, content, "Reading per subject…$progress").apply { setPadding(paddingLeft, 0, paddingRight, dp(8)) })
        }

        if (!subjects.isNullOrEmpty()) {
            val rows = lowestFirst(subjects).map(::subjectRow)
            content.addView(cardOf(layoutInflater, content, rows))
        }

        val note = when {
            running != null -> null
            state.subjectsFailed && subjects == null -> "Couldn't read per subject. Pull down to try again."
            subjects == null -> "Not read yet. Pull down to read per subject."
            overview.subjectsRead < overview.subjectsTotal ->
                "Read ${overview.subjectsRead} of ${overview.subjectsTotal} subjects before the time limit. Pull down to try again."
            subjects.isEmpty() -> "No subject has registered lessons ${overview.period.phrase}."
            else -> "Lowest first. Subjects without registered lessons are left out."
        }
        note?.let { content.addView(footnote(layoutInflater, content, it)) }
    }

    private fun subjectRow(subject: SubjectPresence): View {
        val row = layoutInflater.inflate(R.layout.item_subject_presence, content, false)
        val numbers = subject.numbers
        row.findViewById<TextView>(R.id.subject).text = subject.name
        val percentView = row.findViewById<TextView>(R.id.percent)
        val bar = row.findViewById<LinearProgressIndicator>(R.id.bar)
        bar.trackColor = MaterialColors.getColor(bar, MaterialR.attr.colorSurfaceContainerHighest)
        val percent = numbers.percent
        if (percent != null) {
            val level = presenceLevel(percent)
            percentView.text = formatPercent(percent)
            percentView.setTextColor(textColor(level))
            bar.setIndicatorColor(barColor(level))
            bar.setProgressCompat(Math.round(percent).toInt(), false)
        } else {
            percentView.text = "–"
            percentView.setTextColor(MaterialColors.getColor(percentView, MaterialR.attr.colorOnSurfaceVariant))
            bar.setProgressCompat(0, false)
        }
        val hours = listOfNotNull(
            numbers.presentHours?.let { "${hours(it)} present" },
            numbers.absentHours?.takeIf { it > 0.0 }?.let { "${hours(it)} absent" },
        ).joinToString(" · ")
        row.findViewById<TextView>(R.id.hours).apply {
            text = hours
            visibility = if (hours.isEmpty()) View.GONE else View.VISIBLE
        }
        row.contentDescription = "${subject.name}, ${percent?.let { formatPercent(it) + " present" } ?: "percentage unknown"}" +
            if (hours.isNotEmpty()) ", $hours" else ""
        return row
    }

    // ---- Recent lessons ----

    private fun recentSection(state: PresenceViewModel.State) {
        val stored = store.loadRecent()
        val days = lessonsByDay(stored?.value.orEmpty(), LocalDate.now(Config.ZONE))
        val note = when {
            days.isNotEmpty() -> null
            state.loadingRecent -> "Reading recent lessons…"
            state.recentFailed -> "Couldn't read the recent lessons. Pull down to try again."
            stored != null -> "No lessons in the last four weeks."
            else -> null
        }
        if (days.isEmpty() && note == null) return

        content.addView(sectionHeader(layoutInflater, content, "Recent lessons"))
        note?.let { content.addView(footnote(layoutInflater, content, it)) }
        val shown = if (allDays) days else days.take(DAYS_SHOWN)
        shown.forEachIndexed { i, (date, lessons) ->
            val rows = listOf(dayTitle(date)) + lessons.map(::lessonRow)
            content.addView(cardOf(layoutInflater, content, rows).also { card ->
                if (i > 0) (card.layoutParams as ViewGroup.MarginLayoutParams).topMargin = dp(8)
            })
        }
        if (days.size > DAYS_SHOWN) {
            content.addView(MaterialButton(this, null, MaterialR.attr.borderlessButtonStyle).apply {
                text = if (allDays) "Show fewer days" else "Show all ${days.size} days"
                setOnClickListener {
                    allDays = !allDays
                    render(model.state.value)
                }
            })
        }
        if (state.loadingRecent && days.isNotEmpty()) content.addView(footnote(layoutInflater, content, "Updating recent lessons…"))
    }

    private fun dayTitle(date: LocalDate): View = TextView(this).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setPadding(dp(20), dp(14), dp(20), dp(10))
        setTextAppearance(MaterialR.style.TextAppearance_Material3_TitleSmall)
        setTextColor(MaterialColors.getColor(this, MaterialR.attr.colorPrimary))
        text = date.format(DAY_FORMAT)
        isFocusable = true
        if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
    }

    private fun lessonRow(entry: AttendanceEntry): View {
        val status = lessonStatus(entry)
        val time = "${entry.start.format(TIME_FORMAT)} – ${entry.end.format(TIME_FORMAT)}"
        val row = recordRow(
            layoutInflater, content,
            title = entry.lesson.ifEmpty { "Lesson" },
            summary = listOfNotNull(time, entry.reported).joinToString(" · "),
        )
        val label = row.findViewById<TextView>(R.id.value)
        val text = status.label.ifEmpty { entry.status }
        if (text.isNotEmpty()) styleStatus(label, text, status)
        return row
    }

    // ---- Empty state and messages ----

    private fun showEmpty(state: PresenceViewModel.State, empty: Boolean) {
        val problem = state.problem
        val (title, message) = when {
            problem != null -> "Couldn't read your attendance" to problem.message + "\n\nPull down to try again."
            else -> "Reading your attendance…" to "This takes a few seconds."
        }
        findViewById<TextView>(R.id.emptyTitle).text = title
        findViewById<TextView>(R.id.emptyMessage).text = message
        findViewById<android.widget.ImageView>(R.id.emptyIcon).setImageResource(R.drawable.ic_event_busy)
        findViewById<View>(R.id.empty).visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun showProblem(problem: PortalProblem) {
        val bar = Snackbar.make(findViewById(R.id.root), "Couldn't update. ${problem.message}", Snackbar.LENGTH_LONG)
        if (problem.needsLogin) {
            bar.setAction("Log in") { startActivity(Intent(this, LoginActivity::class.java)) }
        } else {
            bar.setAction("Details") {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Couldn't read Eduarte")
                    .setMessage("${problem.message}\n\nDetails: ${problem.details}")
                    .setPositiveButton("Close", null)
                    .show()
            }
        }
        bar.show()
    }

    // ---- Colors ----

    @ColorInt
    private fun barColor(level: PresenceLevel): Int = ContextCompat.getColor(
        this,
        when (level) {
            PresenceLevel.GOOD -> R.color.state_ok
            PresenceLevel.WARNING -> R.color.state_warning
            PresenceLevel.LOW -> R.color.state_error
        },
    )

    @ColorInt
    private fun textColor(level: PresenceLevel): Int = when (level) {
        PresenceLevel.GOOD -> ContextCompat.getColor(this, R.color.state_ok)
        PresenceLevel.WARNING -> ContextCompat.getColor(this, R.color.presence_warning_text)
        PresenceLevel.LOW -> MaterialColors.getColor(content, MaterialR.attr.colorError)
    }

    private fun styleLevel(view: TextView, text: String, level: PresenceLevel) = when (level) {
        PresenceLevel.GOOD -> paint(view, text, R.color.presence_good_container, R.color.presence_on_good_container)
        PresenceLevel.WARNING -> paint(view, text, R.color.presence_warning_container, R.color.presence_on_warning_container)
        PresenceLevel.LOW -> styleBadge(view, text, Tone.ERROR)
    }

    private fun styleStatus(view: TextView, text: String, status: LessonStatus) = when (status) {
        LessonStatus.PRESENT -> paint(view, text, R.color.presence_good_container, R.color.presence_on_good_container)
        LessonStatus.AUTHORIZED -> paint(view, text, R.color.presence_warning_container, R.color.presence_on_warning_container)
        LessonStatus.UNAUTHORIZED, LessonStatus.ABSENT -> styleBadge(view, text, Tone.ERROR)
        LessonStatus.NOT_APPLICABLE, LessonStatus.OTHER -> styleBadge(view, text, Tone.NEUTRAL)
    }

    private fun paint(view: TextView, text: String, background: Int, foreground: Int) {
        view.text = text
        view.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, background))
        view.setTextColor(ContextCompat.getColor(this, foreground))
        view.visibility = View.VISIBLE
    }

    private fun hours(value: Double) = if (value == 1.0) "1 hour" else "${formatHours(value)} hours"

    private fun dp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        const val TITLE = "Presentie"
        const val STATE_ALL_DAYS = "all_days"

        /** Days of recent lessons shown before "Show all days". */
        const val DAYS_SHOWN = 5

        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
    }
}
