package dev.eduartesync.ui.portal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.eduartesync.Config
import dev.eduartesync.portal.AttendanceSource
import dev.eduartesync.portal.PerSubjectPresence
import dev.eduartesync.portal.PresencePeriod
import dev.eduartesync.portal.PresenceSource
import dev.eduartesync.presence.PresenceOverview
import dev.eduartesync.presence.PresenceStore
import dev.eduartesync.presence.SubjectPresence
import dev.eduartesync.presence.absenceNumbers
import dev.eduartesync.presence.presenceNumbers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Reads attendance for the Presentie screen, in a ViewModel so a read that takes a minute (all
 * subjects) keeps going when the screen rotates. Results go to [PresenceStore]; the screen
 * draws from there and from [state], which says what is being read right now.
 *
 * Order: the totals first (one page each for Presentie and Absentie, a few seconds), then per
 * subject (about a second per subject) and, at the same time in a second hidden browser, the
 * recent lessons. Those two only start after the totals worked, so a login that needs renewing
 * is renewed once, not by two browsers at the same time.
 */
class PresenceViewModel(application: Application) : AndroidViewModel(application) {
    data class State(
        val period: PresencePeriod = PresencePeriod.SCHOOL_YEAR,
        val loadingTotals: Boolean = false,
        /** Per-subject read underway: what it found so far, or null when not running. */
        val perSubject: PerSubjectPresence? = null,
        val loadingRecent: Boolean = false,
        /** The totals couldn't be read (shown when nothing is stored yet). */
        val problem: PortalProblem? = null,
        val subjectsFailed: Boolean = false,
        val recentFailed: Boolean = false,
        /** Goes up whenever something new was stored, so the screen draws again. */
        val version: Int = 0,
    )

    private val app = application
    private val store = PresenceStore(application)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** Problems to show as a message once (not again after rotating). */
    private val _messages = MutableSharedFlow<PortalProblem>(extraBufferCapacity = 1)
    val messages: SharedFlow<PortalProblem> = _messages

    private var job: Job? = null
    private var recentJob: Job? = null

    /** Shows [period]; reads it when it was never read, is old, or has no per-subject list yet. */
    fun choose(period: PresencePeriod, force: Boolean = false) {
        val changed = period != _state.value.period
        if (changed) {
            job?.cancel()
            _state.update { it.copy(period = period, loadingTotals = false, perSubject = null, problem = null, subjectsFailed = false, version = it.version + 1) }
        }
        val stored = store.load(period)
        val due = stored == null || System.currentTimeMillis() - stored.fetchedAt > PortalListActivity.STALE_MS ||
            stored.value.subjects == null
        if (force || (due && (changed || job?.isActive != true))) refresh(period, userAsked = force)
    }

    /** Pull to refresh: everything again, the recent lessons too. */
    fun refreshAll() {
        choose(_state.value.period, force = true)
    }

    private fun refresh(period: PresencePeriod, userAsked: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loadingTotals = true, problem = null, subjectsFailed = false) }
            val totals = try {
                val overall = PresenceSource(app).fetchOverall(period)
                val previous = store.load(period)?.value
                val overview = PresenceOverview(
                    period = period,
                    presence = presenceNumbers(overall.presence),
                    absence = absenceNumbers(overall.absence),
                    // The old list stays until the new one is complete.
                    subjects = previous?.subjects,
                    subjectsRead = previous?.subjectsRead ?: 0,
                    subjectsTotal = previous?.subjectsTotal ?: 0,
                )
                store.save(overview, System.currentTimeMillis())
                overview to overall.filters.subjects.size
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val problem = PortalProblem.from(app, e)
                _state.update { it.copy(loadingTotals = false, problem = problem, version = it.version + 1) }
                // Like the Cijfers tab: a failed background read stays quiet, the old numbers stay.
                if (userAsked || problem.needsLogin) _messages.tryEmit(problem)
                return@launch
            }
            _state.update {
                it.copy(
                    loadingTotals = false,
                    perSubject = PerSubjectPresence(emptyList(), 0, totals.second),
                    version = it.version + 1,
                )
            }

            if (userAsked || recentDue()) refreshRecent()

            try {
                val result = PresenceSource(app).fetchPerSubject(period) { partial ->
                    _state.update { it.copy(perSubject = partial) }
                }
                val overview = totals.first.copy(
                    subjects = result.subjects.map { SubjectPresence(it.label, presenceNumbers(it.charts)) },
                    subjectsRead = result.read,
                    subjectsTotal = result.total,
                )
                store.save(overview, System.currentTimeMillis())
                _state.update { it.copy(perSubject = null, version = it.version + 1) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(perSubject = null, subjectsFailed = true, version = it.version + 1) }
                if (userAsked) _messages.tryEmit(PortalProblem.from(app, e))
            }
        }
    }

    private fun recentDue(): Boolean {
        val stored = store.loadRecent() ?: return true
        return System.currentTimeMillis() - stored.fetchedAt > PortalListActivity.STALE_MS
    }

    /** The lesson-by-lesson overview of the last [RECENT_DAYS] days (/presentie/presentie.overzicht). */
    private fun refreshRecent() {
        if (recentJob?.isActive == true) return
        recentJob = viewModelScope.launch {
            _state.update { it.copy(loadingRecent = true, recentFailed = false) }
            try {
                val today = LocalDate.now(Config.ZONE)
                val attendance = AttendanceSource(app).fetch(today.minusDays(RECENT_DAYS - 1), today)
                store.saveRecent(attendance.entries, System.currentTimeMillis())
                _state.update { it.copy(loadingRecent = false, version = it.version + 1) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingRecent = false, recentFailed = true, version = it.version + 1) }
            }
        }
    }

    private companion object {
        /** How far back the recent lessons go. */
        const val RECENT_DAYS = 28L
    }
}
