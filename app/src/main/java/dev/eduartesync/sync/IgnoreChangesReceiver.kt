package dev.eduartesync.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Handles the "Ignore" button on the schedule changes notification. */
class IgnoreChangesReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = if (Notifier.muteChanges(context)) {
            "Change notifications are off. Turn them back on in Android settings: " +
                "Apps > Eduarte Sync > Notifications > Schedule changes."
        } else {
            "Android didn't allow muting from here. Turn them off in Android settings: " +
                "Apps > Eduarte Sync > Notifications > Schedule changes."
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
