package dev.eduartesync.ui

import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import dev.eduartesync.R

/**
 * Shared setup for the app's screens. Call before super.onCreate():
 * wallpaper colors on Android 12+ and drawing behind the system bars, which Android 15+ does
 * anyway for apps targeting it.
 */
fun AppCompatActivity.prepareScreen() {
    DynamicColors.applyToActivityIfAvailable(this)
    enableEdgeToEdge()
}

/** Color of the status dot on the settings page and the status icon on the Rooster tab. */
@ColorRes
fun stateColor(state: SyncState): Int = when (state) {
    SyncState.OK -> R.color.state_ok
    SyncState.STALE, SyncState.WAITING -> R.color.state_warning
    SyncState.FAILED, SyncState.LOGIN_NEEDED -> R.color.state_error
    SyncState.NEVER, SyncState.SYNCING -> R.color.state_idle
}

/**
 * The top bar of a secondary screen: a compact toolbar with a back arrow and [title]. Also keeps
 * the content out from under the status bar, navigation bar and camera cutout: the top bar gets
 * the top inset (its background still runs behind the status bar), the scrolling content the
 * bottom inset, so the last item can scroll above the navigation bar.
 */
fun AppCompatActivity.setUpScreen(title: String) {
    val root = findViewById<View>(R.id.root)
    val appBar = findViewById<View>(R.id.appBar)
    val scroll = findViewById<View>(R.id.scroll)
    val baseBottom = scroll.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        appBar.setPadding(bars.left, bars.top, bars.right, 0)
        scroll.setPadding(bars.left, scroll.paddingTop, bars.right, baseBottom + bars.bottom)
        WindowInsetsCompat.CONSUMED
    }

    this.title = title
    findViewById<MaterialToolbar>(R.id.toolbar).apply {
        this.title = title
        setNavigationIcon(R.drawable.ic_arrow_back)
        setNavigationContentDescription("Back")
        setNavigationOnClickListener { finish() }
    }
}

/**
 * "Updated 5 min ago" under the title of a screen that shows a stored copy of a portal page, so
 * it takes no room in the content; nothing when [fetchedAt] is null.
 */
fun AppCompatActivity.showUpdated(fetchedAt: Long?) {
    findViewById<MaterialToolbar>(R.id.toolbar).subtitle =
        fetchedAt?.let { updatedText(it, System.currentTimeMillis()) }
}

fun updatedText(fetchedAt: Long, now: Long): String = "Updated ${SyncStatus.ago(fetchedAt, now)}"
