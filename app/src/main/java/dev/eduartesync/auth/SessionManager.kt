package dev.eduartesync.auth

import android.content.Context
import android.net.Uri
import dev.eduartesync.Config

/**
 * Login state. The actual credentials are the WebView's cookies (Eduarte + Microsoft session),
 * stored in the app's private storage. The password is never seen or stored by this app.
 * "Clear storage" in Android settings wipes everything and brings back the login screen.
 */
object SessionManager {
    private const val PREFS = "session"
    private const val KEY_LOGGED_IN = "logged_in"

    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOGGED_IN, false)

    fun setLoggedIn(context: Context, loggedIn: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOGGED_IN, loggedIn).apply()
    }

    /** True once the OAuth flow has landed back on the portal itself (not the callback). */
    fun isPortalPage(url: String?): Boolean {
        val uri = url?.let(Uri::parse) ?: return false
        return uri.host == Config.PORTAL_HOST && !(uri.path ?: "").startsWith("/oauth-callback")
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
