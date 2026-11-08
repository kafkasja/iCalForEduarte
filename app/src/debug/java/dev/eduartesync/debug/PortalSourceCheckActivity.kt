package dev.eduartesync.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import dev.eduartesync.Config
import dev.eduartesync.portal.AttendanceSource
import dev.eduartesync.portal.PortalGradesSource
import dev.eduartesync.portal.PresencePeriod
import dev.eduartesync.portal.PresenceSource
import dev.eduartesync.portal.StudyRecordsSource
import dev.eduartesync.portal.StudySource
import dev.eduartesync.portal.openPage
import dev.eduartesync.portal.withPortalBrowser
import dev.eduartesync.presence.absenceNumbers
import dev.eduartesync.presence.presenceNumbers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Debug builds only. Runs one portal source on the device and logs a summary (tag
 * "PortalCheck"), to test the readers against the live portal:
 *
 *   adb shell am start -n dev.eduartesync/.debug.PortalSourceCheckActivity --es source grades|attendance|study|presence|records
 */
class PortalSourceCheckActivity : Activity() {
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent.getStringExtra("source") ?: "grades"
        scope.launch {
            val started = System.currentTimeMillis()
            runCatching {
                when (source) {
                    "grades" -> PortalGradesSource(this@PortalSourceCheckActivity).fetch().let { g ->
                        log("grades=${g.grades.size} withDetail=${g.grades.count { it.detail != null }} averages=${g.grades.count { it.isAverage }} results=${g.results.size}")
                        g.grades.filter { it.detail != null }.distinctBy { it.detail?.title }.take(6).forEach { log("detail sample: title=${it.detail?.title} code=${it.detail?.code} date=${it.detail?.date} type=${it.detail?.type} weight=${it.detail?.weight} fields=${it.detail?.fields?.keys}") }
                    }
                    "attendance" -> {
                        val today = LocalDate.now(Config.ZONE)
                        AttendanceSource(this@PortalSourceCheckActivity).fetch(today.minusMonths(2), today).let { a ->
                            log("attendance period=${a.from}..${a.to} totals=${a.totals.map { "${it.category}:${it.lessonHours}" }} entries=${a.entries.size}")
                        }
                    }
                    "presence" -> {
                        // --es period SCHOOL_YEAR|THIS_MONTH|LAST_MONTH|THIS_WEEK|LAST_WEEK
                        val period = intent.getStringExtra("period")?.let { PresencePeriod.valueOf(it) } ?: PresencePeriod.SCHOOL_YEAR
                        val presence = PresenceSource(this@PortalSourceCheckActivity)
                        presence.fetchOverall(period).let { o ->
                            log("presence overall $period: ${presenceNumbers(o.presence)} absence=${absenceNumbers(o.absence)} subjectOptions=${o.filters.subjects.size} periods=${o.filters.periods.map { it.name }}")
                            o.presence.plus(o.absence).forEach { log("chart ${it.title}: ${it.centerValue} ${it.centerText} ${it.items.map { i -> "${i.label}=${i.count}" }}") }
                        }
                        val loopStarted = System.currentTimeMillis()
                        presence.fetchPerSubject(period) { log("per subject progress ${it.read}/${it.total}, ${it.subjects.size} with lessons") }.let { r ->
                            log("per subject read=${r.read}/${r.total} withLessons=${r.subjects.size} in ${System.currentTimeMillis() - loopStarted} ms")
                            r.subjects.forEach { log("subject value=${it.option.value} ${presenceNumbers(it.charts)}") }
                        }
                    }
                    // Saves a page exactly as the readers see it: --es source dump --es path studie/dossier
                    "dump" -> withPortalBrowser(this@PortalSourceCheckActivity) { browser ->
                        val path = intent.getStringExtra("path") ?: "studie/dossier"
                        browser.openPage(path)
                        kotlinx.coroutines.delay(3000)
                        val (url, html) = browser.currentPage()
                        java.io.File(java.io.File(filesDir, "explore").apply { mkdirs() }, "dump.html").writeText("<!-- $url -->\n$html")
                        log("dumped $url (${html.length} chars)")
                    }
                    "records" -> StudyRecordsSource(this@PortalSourceCheckActivity).let { r ->
                        log("dossier=${r.fetchDossier().size} documents=${r.fetchDocuments().size} subjects=${r.fetchSubjects().size} groups=${r.fetchGroups().map { it.size }}")
                    }
                    else -> StudySource(this@PortalSourceCheckActivity).fetch().let { s ->
                        log("study programmes=${s.programmes.map { it.name }} progressRows=${s.progress?.rows?.size}")
                    }
                }
            }.onFailure { Log.e(TAG, "$source failed", it) }
            log("$source done in ${System.currentTimeMillis() - started} ms")
            finish()
        }
    }

    private fun log(message: String) = Log.i(TAG, message)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object { const val TAG = "PortalCheck" }
}
