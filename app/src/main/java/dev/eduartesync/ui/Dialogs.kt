package dev.eduartesync.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.eduartesync.auth.LoginLog

/** The login log, opened from the Meer tab and from the settings, with Copy to share it when a login breaks. */
fun showLoginLog(context: Context) {
    val log = LoginLog.text(context).ifEmpty { "Nothing logged yet." }
    MaterialAlertDialogBuilder(context)
        .setTitle("Login log")
        .setMessage(log)
        .setPositiveButton("Copy") { _, _ ->
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("Login log", log))
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        }
        .setNeutralButton("Clear") { _, _ -> LoginLog.clear(context) }
        .setNegativeButton("Close", null)
        .show()
}
