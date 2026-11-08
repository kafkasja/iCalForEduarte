package dev.eduartesync.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import dev.eduartesync.Config
import dev.eduartesync.R
import dev.eduartesync.auth.LoginActivity
import dev.eduartesync.auth.LoginLog
import dev.eduartesync.auth.SavedLogin
import dev.eduartesync.auth.SessionManager
import dev.eduartesync.calendar.CalendarWriter
import dev.eduartesync.settings.AppSettings
import dev.eduartesync.settings.RoomInTitle
import dev.eduartesync.settings.SyncRange
import dev.eduartesync.sync.Notifier
import dev.eduartesync.sync.SyncScheduler
import dev.eduartesync.update.Release
import dev.eduartesync.update.UpdateChecker
import java.time.LocalDate

/**
 * Opened by tapping the app icon twice quickly. A status card at the top answers "is my
 * calendar up to date?", the settings are grouped in cards below it.
 */
class SettingsActivity : AppCompatActivity() {
    private lateinit var settings: AppSettings

    // What WorkManager says about the manual and the background sync.
    private var manualWork: List<WorkInfo> = emptyList()
    private var periodicWork: List<WorkInfo> = emptyList()

    // Refreshes the status when a sync finishes while this screen is open.
    private val statusListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { showStatus() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        applyScreenInsets(showBack = false)
        title = "Eduarte Sync"
        settings = AppSettings(this)

        findViewById<View>(R.id.syncNow).setOnClickListener { SyncScheduler.syncNow(this) }
        findViewById<View>(R.id.logIn).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        val workManager = WorkManager.getInstance(this)
        workManager.getWorkInfosForUniqueWorkLiveData(SyncScheduler.MANUAL_WORK).observe(this) {
            manualWork = it
            showStatus()
        }
        workManager.getWorkInfosForUniqueWorkLiveData(SyncScheduler.PERIODIC_WORK).observe(this) {
            periodicWork = it
            showStatus()
        }

        val autoSync = findViewById<MaterialSwitch>(R.id.autoSync).apply {
            isChecked = settings.autoSync
            setOnCheckedChangeListener { _, on ->
                settings.autoSync = on
                SyncScheduler.updatePeriodic(this@SettingsActivity)
                showStatus()
            }
        }
        findViewById<View>(R.id.autoSyncRow).setOnClickListener { autoSync.toggle() }

        findViewById<View>(R.id.rangeRow).setOnClickListener {
            choose("How far ahead", SyncRange.entries.map { it.label }, SyncRange.entries.indexOf(settings.range)) { i ->
                val option = SyncRange.entries[i]
                if (option == settings.range) return@choose
                settings.range = option
                showChoices()
                SyncScheduler.syncNow(this)
            }
        }

        findViewById<View>(R.id.roomRow).setOnClickListener {
            choose("Room in the event name", RoomInTitle.entries.map { it.label }, RoomInTitle.entries.indexOf(settings.roomInTitle)) { i ->
                val option = RoomInTitle.entries[i]
                if (option == settings.roomInTitle) return@choose
                settings.roomInTitle = option
                showChoices()
                SyncScheduler.syncNow(this)
            }
        }
        showChoices()

        findViewById<View>(R.id.notificationSettings).setOnClickListener {
            startActivity(Notifier.changesSettingsIntent(this))
        }

        findViewById<Button>(R.id.clearCalendar).setOnClickListener {
            confirm("Clear calendar?", "Removes all lessons this app added to your calendar. Your other calendars are not touched.") {
                val removed = CalendarWriter(this).deleteCalendar() > 0
                settings.syncedUntil = null
                toast(if (removed) "Calendar cleared. Tap Sync now to get it back." else "There was nothing to clear.")
            }
        }

        findViewById<Button>(R.id.logOut).setOnClickListener {
            confirm("Log out?", "Removes your Eduarte login from this phone. The calendar stays until you clear it.") {
                SessionManager.logOut(this)
                SyncScheduler.cancelPeriodic(this)
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }

        val saveLogin = findViewById<MaterialSwitch>(R.id.saveLogin).apply {
            isChecked = SavedLogin.isEnabled(this@SettingsActivity)
            setOnCheckedChangeListener { _, on ->
                SavedLogin.setEnabled(this@SettingsActivity, on)
                if (on) toast("Your email and password are saved the next time you log in.")
            }
        }
        findViewById<View>(R.id.saveLoginRow).setOnClickListener { saveLogin.toggle() }

        findViewById<View>(R.id.loginLog).setOnClickListener { showLoginLog() }

        findViewById<View>(R.id.subjects).setOnClickListener {
            startActivity(Intent(this, SubjectsActivity::class.java))
        }

        loadVersions()
    }

    /** Fills in the current choice under "How far ahead" and "Room in the event name". */
    private fun showChoices() {
        findViewById<TextView>(R.id.rangeValue).text = settings.range.label
        findViewById<TextView>(R.id.roomValue).text = settings.roomInTitle.label
    }

    /** Asks GitHub for the releases in the background and fills in the version section. */
    private fun loadVersions() {
        val current = UpdateChecker.currentVersion(this)
        val version = findViewById<TextView>(R.id.version)
        version.text = "$current · checking for updates…"

        Thread {
            val releases = runCatching { UpdateChecker.fetchReleases() }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                releases.onFailure {
                    version.text = "$current · couldn't check for updates (no connection to GitHub)"
                }
                releases.onSuccess { list -> showVersions(current, list) }
            }
        }.start()
    }

    private fun showVersions(current: String, releases: List<Release>) {
        val version = findViewById<TextView>(R.id.version)
        val latest = releases.firstOrNull()
        if (latest != null && UpdateChecker.isNewer(latest.version, current)) {
            version.text = "You have $current. Version ${latest.version} is available."
            findViewById<Button>(R.id.downloadUpdate).apply {
                text = "Download ${latest.version}"
                visibility = View.VISIBLE
                setOnClickListener { open(latest.apkUrl ?: latest.pageUrl) }
            }
        } else {
            version.text = "$current · this is the newest version"
        }

        if (releases.isEmpty()) return
        findViewById<View>(R.id.allVersionsDivider).visibility = View.VISIBLE
        findViewById<View>(R.id.allVersions).apply {
            visibility = View.VISIBLE
            setOnClickListener {
                val labels = releases.map { if (it.version == current) "${it.version} (installed)" else it.version }
                MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle("All versions")
                    .setItems(labels.toTypedArray()) { _, i -> open(releases[i].apkUrl ?: releases[i].pageUrl) }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }
    }

    private fun showLoginLog() {
        val log = LoginLog.text(this).ifEmpty { "Nothing logged yet." }
        MaterialAlertDialogBuilder(this)
            .setTitle("Login log")
            .setMessage(log)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("Login log", log))
                toast("Copied")
            }
            .setNeutralButton("Clear") { _, _ -> LoginLog.clear(this) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun open(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    override fun onResume() {
        super.onResume()
        settings.registerListener(statusListener)
        showStatus()
        Notifier.ensureChannels(this)
        findViewById<TextView>(R.id.notificationStatus).text = if (Notifier.changesEnabled(this)) "On" else "Off"
    }

    override fun onPause() {
        settings.unregisterListener(statusListener)
        super.onPause()
    }

    /** The status card: state dot, headline, which days are covered and the last result. */
    private fun showStatus() {
        val now = System.currentTimeMillis()
        val manual = manualWork.firstOrNull { !it.state.isFinished }
        val periodic = periodicWork.firstOrNull { !it.state.isFinished }
        val running = manual?.state == WorkInfo.State.RUNNING || periodic?.state == WorkInfo.State.RUNNING
        val summary = SyncStatus.summarize(
            loggedIn = SessionManager.isLoggedIn(this),
            lastSyncAt = settings.lastSyncAt,
            lastResult = settings.lastSyncResult,
            running = running,
            waiting = manual != null && !running,
            now = now,
        )

        findViewById<TextView>(R.id.statusHeadline).text = summary.headline
        findViewById<TextView>(R.id.statusSubline).text = summary.subline
        findViewById<View>(R.id.stateDot).apply {
            setBackgroundResource(R.drawable.state_dot)
            backgroundTintList = ColorStateList.valueOf(getColor(stateColor(summary.state)))
        }
        findViewById<View>(R.id.syncProgress).visibility =
            if (summary.state == SyncState.SYNCING) View.VISIBLE else View.GONE

        val today = LocalDate.now(Config.ZONE)
        findViewById<TextView>(R.id.coverage).text =
            SyncStatus.coverage(settings.syncedUntil, settings.range.lastDay(today), today)

        findViewById<TextView>(R.id.nextSync).text = when {
            !settings.autoSync -> "Automatic sync is off"
            periodic != null && periodic.state == WorkInfo.State.ENQUEUED && periodic.nextScheduleTimeMillis != Long.MAX_VALUE ->
                SyncStatus.nextSync(periodic.nextScheduleTimeMillis, now, Config.ZONE)
            else -> "Syncs automatically about every 12 hours"
        }

        findViewById<TextView>(R.id.status).apply {
            text = settings.lastSyncResult.orEmpty()
            visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }

        val logIn = summary.action == StatusAction.LOG_IN
        findViewById<View>(R.id.logIn).visibility = if (logIn) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.syncNow).apply {
            visibility = if (logIn) View.GONE else View.VISIBLE
            val busy = summary.state == SyncState.SYNCING || summary.state == SyncState.WAITING
            isEnabled = !busy
            text = if (busy) "Syncing…" else "Sync now"
        }
    }

    private fun stateColor(state: SyncState) = when (state) {
        SyncState.OK -> R.color.state_ok
        SyncState.STALE, SyncState.WAITING -> R.color.state_warning
        SyncState.FAILED, SyncState.LOGIN_NEEDED -> R.color.state_error
        SyncState.NEVER, SyncState.SYNCING -> R.color.state_idle
    }

    private fun choose(title: String, labels: List<String>, selected: Int, onChoose: (Int) -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, i ->
                dialog.dismiss()
                onChoose(i)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirm(title: String, message: String, onYes: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Yes") { _, _ -> onYes() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
