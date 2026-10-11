package dev.eduartesync.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import dev.eduartesync.auth.LoginActivity
import dev.eduartesync.auth.SessionManager
import dev.eduartesync.calendar.CalendarWriter
import dev.eduartesync.sync.SyncScheduler

/**
 * Launcher entry with no visible UI: first time -> login; after that one tap syncs and closes,
 * and two quick taps open the settings.
 *
 * The second tap often never reaches the launcher (it is ignored while the app opens), so this
 * activity stays on screen, invisible, for a moment after the first tap and catches the second
 * tap itself.
 */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val close = Runnable { finishQuietly() }

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
            finish()
            return
        }
        SyncScheduler.updatePeriodic(this)
        SyncScheduler.syncNow(this)
        Toast.makeText(this, "Syncing schedule... (tap twice quickly for settings)", Toast.LENGTH_SHORT).show()
        waitForSecondTap()
    }

    /** Covers the screen with an invisible layer; a tap on it within the window opens settings. */
    @SuppressLint("ClickableViewAccessibility")
    private fun waitForSecondTap() {
        setContentView(View(this).apply {
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) openSettings()
                true
            }
        })
        handler.postDelayed(close, DOUBLE_TAP_MS)
    }

    private fun openSettings() {
        handler.removeCallbacks(close)
        // Own task, so a settings page left open never takes over the app icon's sync.
        startActivity(
            Intent(this, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finishQuietly()
    }

    private fun finishQuietly() {
        if (isFinishing) return
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        handler.removeCallbacks(close)
        super.onDestroy()
    }

    private fun requiredPermissions() = buildList {
        add(Manifest.permission.READ_CALENDAR)
        add(Manifest.permission.WRITE_CALENDAR)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val REQUEST_PERMISSIONS = 1
        const val DOUBLE_TAP_MS = 1200L
    }
}
