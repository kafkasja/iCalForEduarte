package dev.eduartesync.widget

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import dev.eduartesync.R

/**
 * Invisible stop between a tapped widget row and the calendar app: opens the event the row
 * filled in and closes. Needed because the row's PendingIntent has to target this app.
 */
class OpenLessonActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val event = intent.data
        if (event != null) {
            val view = Intent(Intent.ACTION_VIEW, event)
                .putExtras(intent)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(view)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.widget_no_calendar_app, Toast.LENGTH_LONG).show()
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
