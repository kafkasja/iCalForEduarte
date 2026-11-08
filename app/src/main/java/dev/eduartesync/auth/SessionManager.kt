package dev.eduartesync.auth

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import dev.eduartesync.Config

/**
 * Login state. The actual credentials are the WebView's cookies (Eduarte + Microsoft session),
 * stored in the app's private storage. The password is only stored when the user turns that on,
 * see [SavedLogin].
 * "Clear storage" in Android settings wipes everything and brings back the login screen.
 */
object SessionManager {
    private const val PREFS = "session"
    private const val KEY_LOGGED_IN = "logged_in"
    private const val KEY_NEEDS_AUTHENTICATOR = "background_needs_authenticator"

    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOGGED_IN, false)

    fun setLoggedIn(context: Context, loggedIn: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOGGED_IN, loggedIn).apply()
    }

    /**
     * Set when a background sync found that renewing the login needs the Authenticator, even
     * with the saved password. Background syncs then stop trying, so the user never gets an
     * Authenticator request they didn't ask for. Cleared once a renewal works without it.
     */
    fun backgroundNeedsAuthenticator(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NEEDS_AUTHENTICATOR, false)

    fun setBackgroundNeedsAuthenticator(context: Context, needed: Boolean) {
        prefs(context).edit().putBoolean(KEY_NEEDS_AUTHENTICATOR, needed).apply()
    }

    /** True once the OAuth flow has landed back on the portal itself (not the callback). */
    fun isPortalPage(url: String?): Boolean {
        val uri = url?.let(Uri::parse) ?: return false
        return uri.host == Config.PORTAL_HOST && !(uri.path ?: "").startsWith("/oauth-callback")
    }

    /** Removes the Eduarte and Microsoft login, and a saved email and password, from this phone. */
    fun logOut(context: Context) {
        SavedLogin.forget(context)
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        setLoggedIn(context, false)
        setBackgroundNeedsAuthenticator(context, false)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
