package dev.eduartesync.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import dev.eduartesync.Config
import dev.eduartesync.portal.AttendanceSource
import dev.eduartesync.portal.PortalGradesSource
import dev.eduartesync.portal.StudySource
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Debug builds only. Runs one portal source on the device and logs a summary (tag
 * "PortalCheck"), to test the readers against the live portal:
 *
 *   adb shell am start -n dev.eduartesync/.debug.PortalSourceCheckActivity --es source grades|attendance|study
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
