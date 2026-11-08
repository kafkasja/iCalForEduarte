package dev.eduartesync.ui.grades

import dev.eduartesync.ui.LoadingLine
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import dev.eduartesync.R
import dev.eduartesync.grades.GradesStore
import dev.eduartesync.grades.gradesFetcher
import dev.eduartesync.grades.groupBySubject
import dev.eduartesync.grades.recentGrades
import dev.eduartesync.ui.MainActivity
import dev.eduartesync.ui.updatedText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Cijfers tab: the grades of the last fetch (kept, so they show offline), newest first and per
 * subject. Pull down to fetch again; opening the tab fetches when the last fetch is old.
 */
class GradesFragment : Fragment(R.layout.fragment_grades) {
    private lateinit var store: GradesStore
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var loadingLine: LoadingLine

    /** Subjects whose card is open. */
    private val expanded = mutableSetOf<String>()

    /** The fetcher said reading grades isn't built yet. */
    private var notAvailable = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        store = GradesStore(requireContext())
        savedInstanceState?.getStringArrayList(STATE_EXPANDED)?.let(expanded::addAll)
        notAvailable = savedInstanceState?.getBoolean(STATE_NOT_AVAILABLE) ?: false

        refresh = view.findViewById(R.id.refresh)
        loadingLine = LoadingLine.attach(refresh) { fetch(userAsked = true) }

        render()
        val stored = store.load()
        if (savedInstanceState == null && (stored == null || System.currentTimeMillis() - stored.fetchedAt > STALE_MS)) {
            fetch(userAsked = false)
        }
    }

    /** Coming back to this tab: the main screen cleared the subtitle when switching. */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_EXPANDED, ArrayList(expanded))
        outState.putBoolean(STATE_NOT_AVAILABLE, notAvailable)
    }

    private fun fetch(userAsked: Boolean) {
        loadingLine.loading = true
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
                loadingLine.loading = false
                render()
            }
        }
    }

    private fun render() {
        val view = view ?: return
        val stored = store.load()
        val grades = stored?.grades.orEmpty()
        val inflater = layoutInflater

        // Under the tab's title, like on the portal screens; only this tab's while it shows.
        if (!isHidden) {
            (requireActivity() as MainActivity).setToolbarSubtitle(
                stored?.takeIf { grades.isNotEmpty() }?.let { updatedText(it.fetchedAt, System.currentTimeMillis()) },
            )
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
