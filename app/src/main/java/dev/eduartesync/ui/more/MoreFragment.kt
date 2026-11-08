package dev.eduartesync.ui.more

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import dev.eduartesync.ui.MainActivity
import dev.eduartesync.ui.SubjectsActivity
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val content = view.findViewById<LinearLayout>(R.id.content)
        sections().forEach { addSection(content, it) }
    }

    private fun sections() = listOf(
        // Portal features (absences, messages, ...) get a row here once the app can read them.
        Section("Eduarte", listOf(
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

    private fun open(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
