package dev.eduartesync.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import dev.eduartesync.Config
import dev.eduartesync.sync.SyncScheduler

/**
 * Shows the normal Eduarte -> school Microsoft login in a WebView. Once the portal loads,
 * the session cookies are saved (on this phone only) and the first sync starts.
 */
class LoginActivity : Activity() {
    private lateinit var webView: WebView
    private var done = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    if (!done && SessionManager.isPortalPage(url)) onLoggedIn()
                    // Always "stay signed in", otherwise the login expires after a short while.
                    if (MicrosoftLogin.isMicrosoftPage(url)) view.evaluateJavascript(MicrosoftLogin.script(pickAccount = false), null)
                }
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        setContentView(webView)
        webView.loadUrl(Config.PORTAL_URL)
    }

    private fun onLoggedIn() {
        done = true
        CookieManager.getInstance().flush()
        SessionManager.setLoggedIn(this, true)
        SyncScheduler.updatePeriodic(this)
        SyncScheduler.syncNow(this)
        Toast.makeText(this, "Logged in, syncing your schedule...", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
