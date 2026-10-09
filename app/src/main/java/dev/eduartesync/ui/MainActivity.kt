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
import dev.eduartesync.sync.SyncScheduler

/**
 * Launcher entry with no UI: first time -> login; every time after -> sync now, then close.
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
        if (!SessionManager.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            SyncScheduler.schedulePeriodic(this)
            SyncScheduler.syncNow(this)
            Toast.makeText(this, "Syncing schedule...", Toast.LENGTH_SHORT).show()
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
    }
}
