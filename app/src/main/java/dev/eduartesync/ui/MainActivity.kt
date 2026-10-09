package dev.eduartesync.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import dev.eduartesync.auth.LoginActivity
import dev.eduartesync.auth.SessionManager
import dev.eduartesync.calendar.CalendarWriter
import dev.eduartesync.settings.AppSettings
import dev.eduartesync.sync.SyncScheduler

/**
 * Launcher entry with no UI: first time -> login; after that one tap syncs and closes,
 * and two quick taps open the settings.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val missing = requiredPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) proceed() else requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (CalendarWriter.hasPermission(this)) {
            proceed()
        } else {
            Toast.makeText(this, "Calendar access is needed to add your schedule", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun proceed() {
        val settings = AppSettings(this)
        val now = System.currentTimeMillis()
        val doubleTap = now - settings.lastLaunchAt < DOUBLE_TAP_MS
        // Reset after a double tap, so a third tap doesn't count as another one.
        settings.lastLaunchAt = if (doubleTap) 0 else now

        when {
            !SessionManager.isLoggedIn(this) -> startActivity(Intent(this, LoginActivity::class.java))
            doubleTap -> startActivity(Intent(this, SettingsActivity::class.java))
            else -> {
                SyncScheduler.updatePeriodic(this)
                SyncScheduler.syncNow(this)
                Toast.makeText(this, "Syncing schedule... (tap twice quickly for settings)", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    private fun requiredPermissions() = buildList {
        add(Manifest.permission.READ_CALENDAR)
        add(Manifest.permission.WRITE_CALENDAR)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val REQUEST_PERMISSIONS = 1
        const val DOUBLE_TAP_MS = 1500L
    }
}
