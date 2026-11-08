package dev.eduartesync.ui.grades

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import dev.eduartesync.R
import dev.eduartesync.grades.GradesStore
import dev.eduartesync.grades.gradesFetcher
import dev.eduartesync.grades.groupBySubject
import dev.eduartesync.grades.recentGrades
import dev.eduartesync.ui.SyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.google.android.material.R as MaterialR

/**
 * Cijfers tab: the grades of the last fetch (kept, so they show offline), newest first and per
 * subject. Pull down to fetch again; opening the tab fetches when the last fetch is old.
 */
class GradesFragment : Fragment(R.layout.fragment_grades) {
    private lateinit var store: GradesStore
    private lateinit var refresh: SwipeRefreshLayout

    /** Subjects whose card is open. */
    private val expanded = mutableSetOf<String>()

    /** The fetcher said reading grades isn't built yet. */
    private var notAvailable = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        store = GradesStore(requireContext())
        savedInstanceState?.getStringArrayList(STATE_EXPANDED)?.let(expanded::addAll)
        notAvailable = savedInstanceState?.getBoolean(STATE_NOT_AVAILABLE) ?: false

        refresh = view.findViewById(R.id.refresh)
        refresh.setColorSchemeColors(MaterialColors.getColor(view, MaterialR.attr.colorPrimary))
        refresh.setProgressBackgroundColorSchemeColor(MaterialColors.getColor(view, MaterialR.attr.colorSurfaceContainerHigh))
        refresh.setOnRefreshListener { fetch(userAsked = true) }

        render()
        val stored = store.load()
        if (savedInstanceState == null && (stored == null || System.currentTimeMillis() - stored.fetchedAt > STALE_MS)) {
            fetch(userAsked = false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_EXPANDED, ArrayList(expanded))
        outState.putBoolean(STATE_NOT_AVAILABLE, notAvailable)
    }

    private fun fetch(userAsked: Boolean) {
        refresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val grades = gradesFetcher(requireContext()).fetch()
                store.save(grades, System.currentTimeMillis())
                notAvailable = false
            } catch (e: NotImplementedError) {
                notAvailable = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep showing the last grades; only say something when the user pulled.
                if (userAsked) {
                    Snackbar.make(requireView(), "Couldn't get your cijfers. ${e.message.orEmpty()}".trim(), Snackbar.LENGTH_LONG)
                        .setAnchorView(requireActivity().findViewById(R.id.bottomNav))
                        .show()
                }
            } finally {
                refresh.isRefreshing = false
                render()
            }
        }
    }

    private fun render() {
        val view = view ?: return
        val stored = store.load()
        val grades = stored?.grades.orEmpty()
        val inflater = layoutInflater

        view.findViewById<TextView>(R.id.updated).apply {
            text = stored?.let { "Updated ${SyncStatus.ago(it.fetchedAt, System.currentTimeMillis())}" }
            visibility = if (stored != null && grades.isNotEmpty()) View.VISIBLE else View.GONE
        }

        val recent = recentGrades(grades)
        val recentList = view.findViewById<ViewGroup>(R.id.recentList)
        recentList.removeAllViews()
        recent.forEachIndexed { i, grade ->
            if (i > 0) recentList.addView(inflater.inflate(R.layout.item_row_divider, recentList, false))
            recentList.addView(gradeRow(inflater, recentList, grade, showSubject = true))
        }
        view.findViewById<View>(R.id.recentSection).visibility = if (recent.isEmpty()) View.GONE else View.VISIBLE

        val subjectList = view.findViewById<ViewGroup>(R.id.subjectList)
        subjectList.removeAllViews()
        groupBySubject(grades).forEach { subject ->
            val open = subject.subject in expanded
            subjectList.addView(subjectCard(inflater, subjectList, subject, open) {
                if (open) expanded -= subject.subject else expanded += subject.subject
                render()
            })
        }
        view.findViewById<View>(R.id.subjectSection).visibility = if (grades.isEmpty()) View.GONE else View.VISIBLE

        showEmpty(view, grades.isEmpty(), fetched = stored != null)
    }

    private fun showEmpty(view: View, empty: Boolean, fetched: Boolean) {
        val (title, message) = when {
            notAvailable -> "Not available yet" to
                "Viewing your cijfers in the app is coming in a future version. Your rooster keeps syncing as usual."
            fetched -> "No cijfers yet" to "Your school hasn't posted any grades yet."
            else -> "Getting your cijfers…" to "Pull down to try again."
        }
        view.findViewById<TextView>(R.id.emptyTitle).text = title
        view.findViewById<TextView>(R.id.emptyMessage).text = message
        view.findViewById<View>(R.id.empty).visibility = if (empty) View.VISIBLE else View.GONE
    }

    private companion object {
        const val STATE_EXPANDED = "expanded"
        const val STATE_NOT_AVAILABLE = "not_available"

        /** Opening the tab fetches again when the grades are older than this. */
        const val STALE_MS = 30 * 60 * 1000L
    }
}
