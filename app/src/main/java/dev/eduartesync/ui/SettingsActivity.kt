package dev.eduartesync.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import dev.eduartesync.R
import dev.eduartesync.auth.LoginActivity
import dev.eduartesync.auth.SessionManager
import dev.eduartesync.calendar.CalendarWriter
import dev.eduartesync.settings.AppSettings
import dev.eduartesync.settings.SyncRange
import dev.eduartesync.sync.Notifier
import dev.eduartesync.sync.SyncScheduler
import dev.eduartesync.update.Release
import dev.eduartesync.update.UpdateChecker

/** Opened by tapping the app icon twice quickly. */
class SettingsActivity : Activity() {
    private lateinit var settings: AppSettings
    private lateinit var status: TextView
    private lateinit var notificationStatus: TextView

    // Refreshes the status when a sync finishes while this screen is open.
    private val statusListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { showStatus() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = "Eduarte Sync settings"
        settings = AppSettings(this)
        status = findViewById(R.id.status)
        notificationStatus = findViewById(R.id.notificationStatus)

        findViewById<Button>(R.id.syncNow).setOnClickListener {
            SyncScheduler.syncNow(this)
            status.text = "Syncing..."
        }

        findViewById<Switch>(R.id.autoSync).apply {
            isChecked = settings.autoSync
            setOnCheckedChangeListener { _, on ->
                settings.autoSync = on
                SyncScheduler.updatePeriodic(this@SettingsActivity)
            }
        }

        val range = findViewById<RadioGroup>(R.id.range)
        SyncRange.entries.forEachIndexed { i, option ->
            range.addView(RadioButton(this).apply {
                id = i + 1
                text = option.label
                isChecked = option == settings.range
            })
        }
        range.setOnCheckedChangeListener { _, id ->
            settings.range = SyncRange.entries[id - 1]
            SyncScheduler.syncNow(this)
            status.text = "Syncing with the new range..."
        }

        findViewById<Button>(R.id.notificationSettings).setOnClickListener {
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

        findViewById<Button>(R.id.subjects).setOnClickListener {
            startActivity(Intent(this, SubjectsActivity::class.java))
        }

        loadVersions()
    }

    /** Asks GitHub for the releases in the background and fills in the version section. */
    private fun loadVersions() {
        val current = UpdateChecker.currentVersion(this)
        val version = findViewById<TextView>(R.id.version)
        version.text = "You have $current. Checking for updates..."

        Thread {
            val releases = runCatching { UpdateChecker.fetchReleases() }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                releases.onFailure {
                    version.text = "You have $current. Couldn't check for updates (no connection to GitHub)."
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
            version.text = "You have $current. This is the newest version."
        }

        if (releases.isEmpty()) return
        findViewById<Button>(R.id.allVersions).apply {
            visibility = View.VISIBLE
            setOnClickListener {
                val labels = releases.map { if (it.version == current) "${it.version} (installed)" else it.version }
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("All versions")
                    .setItems(labels.toTypedArray()) { _, i -> open(releases[i].apkUrl ?: releases[i].pageUrl) }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }
    }

    private fun open(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    override fun onResume() {
        super.onResume()
        settings.registerListener(statusListener)
        showStatus()
        Notifier.ensureChannels(this)
        notificationStatus.text = if (Notifier.changesEnabled(this)) {
            "Schedule change notifications are on."
        } else {
            "Schedule change notifications are off."
        }
    }

    override fun onPause() {
        settings.unregisterListener(statusListener)
        super.onPause()
    }

    private fun showStatus() {
        val at = settings.lastSyncAt
        status.text = if (at == 0L) {
            "Not synced yet."
        } else {
            val time = DateUtils.getRelativeDateTimeString(this, at, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0)
            "Last sync: $time\n${settings.lastSyncResult.orEmpty()}"
        }
    }

    private fun confirm(title: String, message: String, onYes: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Yes") { _, _ -> onYes() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
