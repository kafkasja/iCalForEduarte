package dev.eduartesync.ui.portal

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dev.eduartesync.R
import dev.eduartesync.auth.LoginActivity
import dev.eduartesync.records.PortalStore
import dev.eduartesync.records.Record
import dev.eduartesync.ui.SyncStatus
import dev.eduartesync.ui.applyScreenInsets
import dev.eduartesync.ui.prepareScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.google.android.material.R as MaterialR

/**
 * A screen that shows one portal page, opened from the Meer tab. It works like the Cijfers
 * tab: what was read last time shows right away (also offline), opening the screen reads the
 * portal again when that is older than [STALE_MS], and pulling down always does.
 *
 * Subclasses say what to read ([fetch]), where to keep it ([record]) and how to show it ([render]).
 */
abstract class PortalListActivity<T> : AppCompatActivity() {
    protected abstract val screenTitle: String
    protected abstract val record: Record<T>
    protected abstract suspend fun fetch(): T
    protected abstract fun isEmpty(value: T): Boolean
    protected abstract fun render(content: LinearLayout, value: T)

    /** Shown when the page was read and has nothing, e.g. "No documents". */
    protected abstract val emptyTitle: String
    protected abstract val emptyMessage: String

    @get:DrawableRes
    protected open val emptyIcon: Int = R.drawable.ic_school

    protected lateinit var store: PortalStore
    private lateinit var refresh: SwipeRefreshLayout

    /** The last read failed; shown in the empty state when there's nothing to show instead. */
    private var problem: PortalProblem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_portal_list)
        applyScreenInsets(showBack = true)
        findViewById<CollapsingToolbarLayout>(R.id.collapsing).title = screenTitle
        title = screenTitle
        store = PortalStore(this)

        refresh = findViewById(R.id.refresh)
        refresh.setColorSchemeColors(MaterialColors.getColor(refresh, MaterialR.attr.colorPrimary))
        refresh.setProgressBackgroundColorSchemeColor(MaterialColors.getColor(refresh, MaterialR.attr.colorSurfaceContainerHigh))
        refresh.setOnRefreshListener { load(userAsked = true) }

        show()
        val stored = store.load(record)
        if (savedInstanceState == null && (stored == null || System.currentTimeMillis() - stored.fetchedAt > STALE_MS)) {
            load(userAsked = false)
        }
    }

    private fun load(userAsked: Boolean) {
        refresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                store.save(record, fetch(), System.currentTimeMillis())
                problem = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val found = PortalProblem.from(this@PortalListActivity, e)
                problem = found
                // With something on screen, only say so when asked; a login problem always, for its button.
                if (found.needsLogin || (userAsked && store.load(record) != null)) showProblem(found)
            } finally {
                refresh.isRefreshing = false
                show()
            }
        }
    }

    /** Draws the stored value, or the empty state. Subclasses call it again after a local change. */
    protected fun show() {
        val stored = store.load(record)
        val content = findViewById<LinearLayout>(R.id.content)
        content.removeAllViews()
        val hasContent = stored != null && !isEmpty(stored.value)
        if (stored != null && hasContent) render(content, stored.value)

        findViewById<TextView>(R.id.updated).apply {
            text = stored?.let { "Updated ${SyncStatus.ago(it.fetchedAt, System.currentTimeMillis())}" }
            visibility = if (hasContent) View.VISIBLE else View.GONE
        }

        val failed = problem
        val (title, message) = when {
            stored != null -> emptyTitle to emptyMessage
            failed != null -> "Couldn't read Eduarte" to failed.message + "\n\nPull down to try again."
            else -> "Reading Eduarte…" to "This takes a few seconds."
        }
        findViewById<ImageView>(R.id.emptyIcon).setImageResource(emptyIcon)
        findViewById<TextView>(R.id.emptyTitle).text = title
        findViewById<TextView>(R.id.emptyMessage).text = message
        val empty = findViewById<View>(R.id.empty)
        empty.visibility = if (hasContent) View.GONE else View.VISIBLE
        if (failed != null && stored == null) {
            empty.setOnClickListener { showDetails(failed) }
        } else {
            empty.setOnClickListener(null)
            empty.isClickable = false
        }
    }

    /** A short message at the bottom, with "Log in" when only that helps, otherwise "Details". */
    protected fun showProblem(problem: PortalProblem, prefix: String = "Couldn't update.") {
        val bar = Snackbar.make(findViewById(R.id.root), "$prefix ${problem.message}", Snackbar.LENGTH_LONG)
        if (problem.needsLogin) bar.setAction("Log in") { startActivity(Intent(this, LoginActivity::class.java)) }
        else bar.setAction("Details") { showDetails(problem) }
        bar.show()
    }

    private fun showDetails(problem: PortalProblem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Couldn't read Eduarte")
            .setMessage("${problem.message}\n\nDetails: ${problem.details}")
            .setPositiveButton("Close", null)
            .show()
    }

    companion object {
        /** Opening a screen reads the portal again when what it shows is older than this. */
        const val STALE_MS = 60 * 60 * 1000L
    }
}
