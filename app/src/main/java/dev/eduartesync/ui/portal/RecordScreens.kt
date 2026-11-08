package dev.eduartesync.ui.portal

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dev.eduartesync.R
import dev.eduartesync.grades.formatDutch
import dev.eduartesync.portal.DossierEntry
import dev.eduartesync.portal.PortalDocument
import dev.eduartesync.portal.PortalGroup
import dev.eduartesync.portal.PortalSubject
import dev.eduartesync.portal.Programme
import dev.eduartesync.portal.StudyInfo
import dev.eduartesync.portal.StudyProgress
import dev.eduartesync.portal.StudyRecordsSource
import dev.eduartesync.portal.StudySource
import dev.eduartesync.records.DocumentDownloader
import dev.eduartesync.records.Records
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Dossier: notes the school keeps about you, e.g. progress talks, newest first. */
class DossierActivity : PortalListActivity<List<DossierEntry>>() {
    override val screenTitle = "Dossier"
    override val record = Records.DOSSIER
    override val emptyTitle = "Nothing in your dossier"
    override val emptyMessage = "Notes from talks with your mentor show up here."
    override val emptyIcon = R.drawable.ic_assignment

    override suspend fun fetch() = StudyRecordsSource(this).fetchDossier()

    override fun isEmpty(value: List<DossierEntry>) = value.isEmpty()

    override fun render(content: LinearLayout, value: List<DossierEntry>) {
        val rows = value.map { entry ->
            recordRow(
                layoutInflater, content,
                title = entry.subject,
                summary = listOfNotNull(entry.category, entry.date?.display(), entry.author?.let { "by $it" }).joinToString(" · "),
            )
        }
        content.addView(cardOf(layoutInflater, content, rows))
    }
}

/** Documenten: letters and forms from school (PDFs); tapping one opens it in the phone's viewer. */
class DocumentsActivity : PortalListActivity<List<PortalDocument>>() {
    override val screenTitle = "Documents"
    override val record = Records.DOCUMENTS
    override val emptyTitle = "No documents"
    override val emptyMessage = "Letters and forms from school show up here."
    override val emptyIcon = R.drawable.ic_description

    /** A document is being downloaded; a second tap waits for it. */
    private var opening = false

    override suspend fun fetch() = StudyRecordsSource(this).fetchDocuments()

    override fun isEmpty(value: List<PortalDocument>) = value.isEmpty()

    override fun render(content: LinearLayout, value: List<PortalDocument>) {
        val rows = value.map { document ->
            recordRow(
                layoutInflater, content,
                title = document.name,
                summary = listOfNotNull(document.type.ifEmpty { null }, document.date?.display()).joinToString(" · "),
                trailingIcon = R.drawable.ic_open_in_new,
            ) { open(document) }
        }
        content.addView(cardOf(layoutInflater, content, rows))
        content.addView(footnote(layoutInflater, content, "Opened with your Eduarte login, in your PDF app."))
    }

    /**
     * The saved link may have gone stale (it points into the portal page as it was rendered),
     * so the documents page is read again for a fresh one, then the file is downloaded.
     */
    private fun open(document: PortalDocument) {
        if (opening) return
        opening = true
        val root = findViewById<View>(R.id.root)
        val progress = Snackbar.make(root, "Opening ${document.name}…", Snackbar.LENGTH_INDEFINITE).also { it.show() }
        lifecycleScope.launch {
            try {
                val url = StudyRecordsSource(this@DocumentsActivity).freshDocumentUrl(document)
                if (url == null) {
                    progress.dismiss()
                    Snackbar.make(root, "This document isn't on Eduarte anymore.", Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                val download = DocumentDownloader.download(this@DocumentsActivity, url, document.name)
                progress.dismiss()
                view(download)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                progress.dismiss()
                showProblem(PortalProblem.from(this@DocumentsActivity, e), "Couldn't open the document.")
            } finally {
                opening = false
            }
        }
    }

    private fun view(download: DocumentDownloader.Download) {
        val uri = FileProvider.getUriForFile(this, "$packageName.documents", download.file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, download.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(findViewById(R.id.root), "No app found to open this document.", Snackbar.LENGTH_LONG).show()
        }
    }
}

/** Vakken: the subjects of your current programme, with their codes. */
class CoursesActivity : PortalListActivity<List<PortalSubject>>() {
    override val screenTitle = "Vakken"
    override val record = Records.SUBJECTS
    override val emptyTitle = "No subjects"
    override val emptyMessage = "Eduarte lists no active subjects for you."
    override val emptyIcon = R.drawable.ic_book

    override suspend fun fetch() = StudyRecordsSource(this).fetchSubjects()

    override fun isEmpty(value: List<PortalSubject>) = value.isEmpty()

    override fun render(content: LinearLayout, value: List<PortalSubject>) {
        val rows = value.map { subject ->
            val dates = when {
                subject.startDate != null && subject.endDate != null -> "${subject.startDate.display()} – ${subject.endDate.display()}"
                subject.startDate != null -> "Since ${subject.startDate.display()}"
                subject.endDate != null -> "Until ${subject.endDate.display()}"
                else -> null
            }
            recordRow(
                layoutInflater, content,
                title = subject.name,
                summary = listOfNotNull(subject.code, subject.abbreviation, dates).joinToString(" · "),
            )
        }
        content.addView(cardOf(layoutInflater, content, rows))
    }
}

/** Groepen: your classes and groups; tap one to see who is in it. */
class GroupsActivity : PortalListActivity<List<PortalGroup>>() {
    override val screenTitle = "Groepen"
    override val record = Records.GROUPS
    override val emptyTitle = "No groups"
    override val emptyMessage = "Eduarte lists no groups for you."
    override val emptyIcon = R.drawable.ic_group

    private val expanded = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        savedInstanceState?.getStringArrayList(STATE_EXPANDED)?.let(expanded::addAll)
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_EXPANDED, ArrayList(expanded))
    }

    override suspend fun fetch() = StudyRecordsSource(this).fetchGroups()

    override fun isEmpty(value: List<PortalGroup>) = value.isEmpty()

    override fun render(content: LinearLayout, value: List<PortalGroup>) {
        value.forEachIndexed { i, group ->
            val open = group.name in expanded
            val size = group.size ?: group.members.size
            val rows = mutableListOf(
                recordRow(
                    layoutInflater, content,
                    title = group.name,
                    summary = if (size == 1) "1 student" else "$size students",
                    trailingIcon = R.drawable.ic_expand_more,
                ) {
                    if (open) expanded -= group.name else expanded += group.name
                    show()
                }.also { row ->
                    row.findViewById<View>(R.id.trailing).rotation = if (open) 180f else 0f
                    row.contentDescription = "${group.name}, $size students, " + if (open) "expanded" else "collapsed"
                },
            )
            if (open && group.members.isNotEmpty()) rows += memberList(content, group.members)
            content.addView(cardOf(layoutInflater, content, rows).also { card ->
                if (i > 0) (card.layoutParams as ViewGroup.MarginLayoutParams).topMargin = dp(8)
            })
        }
    }

    /** The names, one per line, compact (a class easily has 30 students). */
    private fun memberList(parent: ViewGroup, members: List<String>): View =
        TextView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(20), dp(12), dp(20), dp(16))
            setLineSpacing(dp(6).toFloat(), 1f)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(com.google.android.material.color.MaterialColors.getColor(parent, com.google.android.material.R.attr.colorOnSurfaceVariant))
            text = members.joinToString("\n")
        }

    private fun dp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        const val STATE_EXPANDED = "expanded"
    }
}

/** Studie: your programmes (opleidingen) and, when Eduarte shows them, your study points. */
class StudyActivity : PortalListActivity<StudyInfo>() {
    override val screenTitle = "Studie"
    override val record = Records.STUDY
    override val emptyTitle = "No programmes"
    override val emptyMessage = "Eduarte lists no programmes for you."

    override suspend fun fetch() = StudySource(this).fetch()

    override fun isEmpty(value: StudyInfo) = value.programmes.isEmpty() && value.progress?.hasPoints() != true

    override fun render(content: LinearLayout, value: StudyInfo) {
        if (value.programmes.isNotEmpty()) {
            // Right under the top bar, which already leaves room above it.
            content.addView(sectionHeader(layoutInflater, content, "Programmes").apply { setPadding(paddingLeft, 0, paddingRight, paddingBottom) })
            content.addView(cardOf(layoutInflater, content, value.programmes.map { programmeRow(content, it) }))
        }
        val progress = value.progress?.takeIf { it.hasPoints() } ?: return
        val charts = progress.charts.filter { it.achieved != null || it.provisional != null || it.notAchieved != null }
        if (charts.isNotEmpty() || progress.rows.isNotEmpty()) {
            content.addView(sectionHeader(layoutInflater, content, "Study points"))
        }
        if (charts.isNotEmpty()) {
            content.addView(cardOf(layoutInflater, content, charts.map { chart ->
                recordRow(
                    layoutInflater, content,
                    title = chart.title,
                    summary = listOfNotNull(
                        chart.achieved?.let { "${points(it)} achieved" },
                        chart.provisional?.let { "${points(it)} provisional" },
                        chart.notAchieved?.let { "${points(it)} not achieved" },
                    ).joinToString(" · "),
                )
            }))
        }
        val rows = progress.rows + listOfNotNull(progress.total)
        if (rows.isNotEmpty()) {
            if (charts.isNotEmpty()) content.addView(View(this).apply { minimumHeight = 8.dpToPx() })
            content.addView(cardOf(layoutInflater, content, rows.map { row ->
                val value = when {
                    row.achievedPoints != null && row.totalPoints != null -> "${points(row.achievedPoints)} / ${points(row.totalPoints)}"
                    row.achievedPoints != null -> points(row.achievedPoints)
                    else -> null
                }
                recordRow(
                    layoutInflater, content,
                    title = row.subject,
                    summary = listOfNotNull(
                        row.result?.let { "Result $it" },
                        row.provisionalPoints?.takeIf { it > 0 }?.let { "${points(it)} provisional" },
                    ).joinToString(" · ").ifEmpty { null },
                    value = value,
                    tone = Tone.ACCENT,
                )
            }))
            content.addView(footnote(layoutInflater, content, "Study points (SP): achieved / total."))
        }
    }

    private fun programmeRow(parent: ViewGroup, p: Programme): View {
        val dates = when {
            p.startDate != null && p.endDate != null -> "${p.startDate.display()} – ${p.endDate.display()}"
            p.startDate != null -> "Since ${p.startDate.display()}"
            else -> null
        }
        val lines = listOfNotNull(
            listOfNotNull(p.level?.let { "Niveau $it" }, p.pathway, p.studyMode).joinToString(" · ").ifEmpty { null },
            dates,
            p.location,
            p.code?.let { "Crebo $it" },
        ) + p.other
        val active = p.status?.lowercase()?.startsWith("actief") == true
        return recordRow(
            layoutInflater, parent,
            title = p.name,
            summary = lines.joinToString("\n"),
            value = p.status?.replaceFirstChar { it.uppercase() },
            tone = if (active) Tone.PRIMARY else Tone.NEUTRAL,
        )
    }

    private fun points(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else formatDutch(value)

    private fun Int.dpToPx() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, toFloat(), resources.displayMetrics).toInt()
}

/**
 * Whether the study points say anything. Eduarte shows the table and charts with all zeros
 * before any points are earned, which is just an empty section.
 */
internal fun StudyProgress.hasPoints(): Boolean {
    fun counts(value: Double?) = value != null && value != 0.0
    return charts.any { counts(it.achieved) || counts(it.provisional) || counts(it.notAchieved) } ||
        (rows + listOfNotNull(total)).any {
            counts(it.achievedPoints) || counts(it.totalPoints) || counts(it.provisionalPoints) || !it.result.isNullOrBlank()
        }
}
