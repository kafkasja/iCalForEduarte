package dev.eduartesync.auth

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.eduartesync.Config
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Login state. The actual credentials are the WebView's cookies (Eduarte + Microsoft session),
 * stored in the app's private storage. The password is never seen or stored by this app.
 * "Clear storage" in Android settings wipes everything and brings back the login screen.
 */
object SessionManager {
    private const val PREFS = "session"
    private const val KEY_LOGGED_IN = "logged_in"
    private const val SILENT_LOGIN_TIMEOUT_MS = 45_000L

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

    /**
     * Re-runs the OAuth redirect chain in a hidden WebView. If the Microsoft session is still
     * valid this completes without user input and refreshes the Eduarte session cookie.
     * Returns false if Microsoft wants interaction (MFA, password expired, ...).
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun silentRelogin(context: Context): Boolean = withContext(Dispatchers.Main) {
        val landed = CompletableDeferred<Boolean>()
        val webView = WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    if (isPortalPage(url)) landed.complete(true)
                }
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.loadUrl(Config.PORTAL_URL)

        val ok = withTimeoutOrNull(SILENT_LOGIN_TIMEOUT_MS) { landed.await() } ?: false
        webView.stopLoading()
        webView.destroy()
        if (ok) CookieManager.getInstance().flush()
        ok
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
