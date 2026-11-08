package dev.eduartesync.ui.more

import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.eduartesync.Config
import dev.eduartesync.R
import dev.eduartesync.portal.PresencePeriod
import dev.eduartesync.presence.PresenceStore
import dev.eduartesync.presence.formatPercent
import dev.eduartesync.ui.MainActivity
import dev.eduartesync.ui.SubjectsActivity
import dev.eduartesync.ui.portal.CoursesActivity
import dev.eduartesync.ui.portal.DocumentsActivity
import dev.eduartesync.ui.portal.DossierActivity
import dev.eduartesync.ui.portal.GroupsActivity
import dev.eduartesync.ui.portal.PresenceActivity
import dev.eduartesync.ui.portal.StudyActivity
import dev.eduartesync.ui.showLoginLog
import dev.eduartesync.update.UpdateChecker

/**
 * Meer tab: everything that isn't the rooster or grades. Rows are plain data in [sections], so a
 * new portal feature is one more [Entry] in the Eduarte section.
 */
class MoreFragment : Fragment(R.layout.fragment_more) {
    private class Entry(
        @DrawableRes val icon: Int,
        val title: String,
        val summary: String?,
        /** Leaves the app (browser), shown with a different icon than the chevron. */
        val external: Boolean = false,
        val onClick: () -> Unit,
    )

    private class Section(val title: String, val entries: List<Entry>)

    /**
     * Drawn on every resume, not once: the Presentie row shows the last read percentage, which
     * changes after visiting that screen (the tab stays alive in the background meanwhile).
     */
    override fun onResume() {
        super.onResume()
        val content = requireView().findViewById<LinearLayout>(R.id.content)
        content.removeAllViews()
        sections().forEach { addSection(content, it) }
    }

    private fun sections() = listOf(
        Section("Eduarte", listOf(
            Entry(R.drawable.ic_event_busy, "Presentie", presenceSummary()) { start(PresenceActivity::class.java) },
            Entry(R.drawable.ic_school, "Studie", "Your programmes and study points") { start(StudyActivity::class.java) },
            Entry(R.drawable.ic_book, "Vakken", "Subjects and their codes") { start(CoursesActivity::class.java) },
            Entry(R.drawable.ic_group, "Groepen", "Your classes and who is in them") { start(GroupsActivity::class.java) },
            Entry(R.drawable.ic_assignment, "Dossier", "Notes from talks with your mentor") { start(DossierActivity::class.java) },
            Entry(R.drawable.ic_description, "Documents", "Letters and forms from school") { start(DocumentsActivity::class.java) },
            Entry(R.drawable.ic_public, "Open Eduarte in browser", Config.PORTAL_HOST, external = true) { open(Config.PORTAL_URL) },
        )),
        Section("Rooster", listOf(
            Entry(R.drawable.ic_book, "Subjects", "Your own name, emoji and color per subject") {
                startActivity(Intent(requireContext(), SubjectsActivity::class.java))
            },
        )),
        Section("App", listOf(
            Entry(R.drawable.ic_settings, "Settings", "Sync, notifications, login") { (requireActivity() as MainActivity).openSettings() },
            Entry(R.drawable.ic_history, "Login log", "What happened during recent logins") { showLoginLog(requireContext()) },
            Entry(R.drawable.ic_info, "About", "Version ${UpdateChecker.currentVersion(requireContext())}") { showAbout() },
        )),
    )

    private fun addSection(parent: LinearLayout, section: Section) {
        val inflater = layoutInflater
        val header = inflater.inflate(R.layout.item_section_header, parent, false) as TextView
        header.text = section.title
        parent.addView(header)

        val card = inflater.inflate(R.layout.item_card, parent, false) as MaterialCardView
        val rows = card.findViewById<ViewGroup>(R.id.rows)
        section.entries.forEachIndexed { i, entry ->
            if (i > 0) rows.addView(inflater.inflate(R.layout.item_row_divider, rows, false))
            val row = inflater.inflate(R.layout.item_more_row, rows, false)
            row.findViewById<ImageView>(R.id.icon).setImageResource(entry.icon)
            row.findViewById<TextView>(R.id.title).text = entry.title
            row.findViewById<TextView>(R.id.summary).apply {
                text = entry.summary
                visibility = if (entry.summary == null) View.GONE else View.VISIBLE
            }
            if (entry.external) row.findViewById<ImageView>(R.id.trailing).setImageResource(R.drawable.ic_open_in_new)
            row.setOnClickListener { entry.onClick() }
            rows.addView(row)
        }
        parent.addView(card)
    }

    private fun showAbout() {
        val context = requireContext()
        MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.app_name))
            .setMessage(
                "Version ${UpdateChecker.currentVersion(context)}\n\n" +
                    "Copies your Talnet Eduarte rooster into a calendar on your phone. An unofficial app, " +
                    "not made by Eduarte or your school. No server, no analytics, no ads."
            )
            .setPositiveButton("Source code") { _, _ -> open("https://github.com/${Config.GITHUB_REPO}") }
            .setNegativeButton("Close", null)
            .show()
    }

    /** "88% present this school year" from the last read, or what the screen is about. */
    private fun presenceSummary(): String {
        val overview = PresenceStore(requireContext()).load(PresencePeriod.SCHOOL_YEAR)?.value
        val percent = overview?.presence?.takeIf { it.hasData }?.percent
            ?: return "Your attendance, also per subject"
        return "${formatPercent(percent)} present ${PresencePeriod.SCHOOL_YEAR.phrase}"
    }

    private fun start(activity: Class<*>) = startActivity(Intent(requireContext(), activity))

    private fun open(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
