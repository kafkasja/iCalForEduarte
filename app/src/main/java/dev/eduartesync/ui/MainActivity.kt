package dev.eduartesync.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import dev.eduartesync.R
import dev.eduartesync.auth.LoginActivity
import dev.eduartesync.auth.SessionManager
import dev.eduartesync.calendar.CalendarWriter
import dev.eduartesync.settings.AppSettings
import dev.eduartesync.sync.SyncScheduler
import dev.eduartesync.ui.grades.GradesFragment
import dev.eduartesync.ui.more.MoreFragment
import dev.eduartesync.ui.rooster.RoosterFragment

/**
 * The app's main screen, opened by the launcher icon, notifications and the widget. Opening it
 * asks for missing permissions, sends you to the login when needed, and otherwise syncs once
 * (see [syncIfDue]), so tapping the icon still means "get my latest rooster".
 */
class MainActivity : AppCompatActivity() {
    private enum class Tab(val itemId: Int, val tag: String, val title: Int) {
        ROOSTER(R.id.tab_rooster, "rooster", R.string.tab_rooster),
        CIJFERS(R.id.tab_cijfers, "cijfers", R.string.tab_cijfers),
        MEER(R.id.tab_meer, "meer", R.string.tab_meer),
    }

    private lateinit var toolbar: MaterialToolbar

    /** When the screen was last left for another app (or the home screen); 0 while shown. */
    private var stoppedAt = 0L

    /** The permission request came from the "Allow access" button, not from opening the app. */
    private var askedFromButton = false

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val fromButton = askedFromButton
        askedFromButton = false
        if (!CalendarWriter.hasPermission(this) && fromButton &&
            !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)
        ) {
            // Android stops showing the dialog after two refusals; only its settings can grant it now.
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        if (fromButton) syncIfDue() else continueOpening()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toolbar = findViewById(R.id.toolbar)
        applyInsets()

        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) openSettings()
            item.itemId == R.id.action_settings
        }

        askedFromButton = savedInstanceState?.getBoolean(STATE_ASKED_FROM_BUTTON) ?: false
        stoppedAt = savedInstanceState?.getLong(STATE_STOPPED_AT) ?: 0L
        val tab = Tab.entries.firstOrNull { it.tag == savedInstanceState?.getString(STATE_TAB) } ?: Tab.ROOSTER

        // Selected before the listener is set, so the tab is shown exactly once.
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.selectedItemId = tab.itemId
        showTab(tab)
        nav.setOnItemSelectedListener { item ->
            showTab(Tab.entries.first { it.itemId == item.itemId })
            true
        }
        // Tapping the selected tab again does nothing (no reload, no scroll jump).
        nav.setOnItemReselectedListener { }

        // Only on a real open, not when the screen is recreated after rotating.
        if (savedInstanceState == null) startOpening()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TAB, Tab.entries.first { it.itemId == findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId }.tag)
        outState.putBoolean(STATE_ASKED_FROM_BUTTON, askedFromButton)
        outState.putLong(STATE_STOPPED_AT, stoppedAt)
    }

    /**
     * Coming back to the app after a while counts as opening it again: Android often keeps the
     * screen alive in the background, and then the icon only brings it back instead of creating it.
     */
    override fun onRestart() {
        super.onRestart()
        if (stoppedAt != 0L && System.currentTimeMillis() - stoppedAt >= RETURN_SYNC_AFTER_MS) syncIfDue()
        stoppedAt = 0L
    }

    /** A notification or the widget opened the screen that was already showing. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        syncIfDue()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) stoppedAt = System.currentTimeMillis()
    }

    private fun startOpening() {
        val missing = requiredPermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) continueOpening() else askPermissions.launch(missing.toTypedArray())
    }

    private fun continueOpening() {
        if (!SessionManager.isLoggedIn(this)) {
            // Logging in syncs by itself; coming back without logging in shows "Log in needed".
            openLogin()
            return
        }
        SyncScheduler.updatePeriodic(this)
        syncIfDue()
    }

    /** Syncs, unless that can't work right now or the last sync succeeded moments ago. */
    private fun syncIfDue() {
        if (!SessionManager.isLoggedIn(this) || !CalendarWriter.hasPermission(this)) return
        val settings = AppSettings(this)
        if (SyncStatus.shouldSyncOnOpen(settings.lastSyncAt, settings.lastSyncResult, System.currentTimeMillis())) {
            SyncScheduler.syncNow(this, quiet = true)
        }
    }

    /** "Allow access" on the Rooster tab. */
    fun requestCalendarAccess() {
        askedFromButton = true
        askPermissions.launch(requiredPermissions().toTypedArray())
    }

    fun openLogin() = startActivity(Intent(this, LoginActivity::class.java))

    fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    /** Shows [tab] and hides the others; each tab keeps its state (scroll position, chosen day). */
    private fun showTab(tab: Tab) {
        toolbar.setTitle(tab.title)
        val fm = supportFragmentManager
        val transaction = fm.beginTransaction().setReorderingAllowed(true)
        for (other in Tab.entries) {
            val fragment = fm.findFragmentByTag(other.tag)
            when {
                other != tab -> fragment?.let { transaction.hide(it) }
                fragment == null -> transaction.add(R.id.tabContainer, create(other), other.tag)
                else -> transaction.show(fragment)
            }
        }
        transaction.commit()
    }

    private fun create(tab: Tab): Fragment = when (tab) {
        Tab.ROOSTER -> RoosterFragment()
        Tab.CIJFERS -> GradesFragment()
        Tab.MEER -> MoreFragment()
    }

    /**
     * Edge to edge: the top bar takes the status bar inset, the bottom navigation pads itself for
     * the navigation bar (insets are passed on, not consumed), the tabs stay clear of side cutouts.
     */
    private fun applyInsets() {
        val appBar = findViewById<View>(R.id.appBar)
        val tabs = findViewById<View>(R.id.tabContainer)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            appBar.setPadding(bars.left, bars.top, bars.right, 0)
            tabs.setPadding(bars.left, 0, bars.right, 0)
            insets
        }
    }

    private fun requiredPermissions() = buildList {
        add(Manifest.permission.READ_CALENDAR)
        add(Manifest.permission.WRITE_CALENDAR)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val STATE_TAB = "tab"
        const val STATE_ASKED_FROM_BUTTON = "asked_from_button"
        const val STATE_STOPPED_AT = "stopped_at"

        /** Away from the app at least this long, then coming back syncs like opening it does. */
        const val RETURN_SYNC_AFTER_MS = 15 * 60 * 1000L
    }
}
