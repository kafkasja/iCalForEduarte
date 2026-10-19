package dev.eduartesync.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import dev.eduartesync.Config
import dev.eduartesync.sync.SyncScheduler

/**
 * Shows the normal Eduarte -> school Microsoft login in a WebView. Once the portal loads,
 * the session cookies are saved (on this phone only) and the first sync starts.
 * When the user chose to save their login, the email and password are filled in for them.
 */
class LoginActivity : Activity() {
    private lateinit var webView: WebView
    private var done = false
    private var fillLogin = false
    // The saved password is filled in once per login screen, so a wrong one isn't tried over and over.
    private var passwordFilled = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fillLogin = SavedLogin.isEnabled(this)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    if (MicrosoftLogin.isMicrosoftPage(url)) LoginLog.page(this@LoginActivity, url)
                    if (!done && SessionManager.isPortalPage(url)) onLoggedIn()
                    if (MicrosoftLogin.isMicrosoftPage(url)) {
                        // Always "stay signed in", otherwise the login expires after a short while.
                        view.evaluateJavascript(MicrosoftLogin.script(pickAccount = fillLogin), null)
                        if (fillLogin) view.evaluateJavascript(fillScript(), null)
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    val text = message.message()
                    if (!text.startsWith(LoginLog.CONSOLE_PREFIX)) return false
                    LoginLog.add(this@LoginActivity, text.removePrefix(LoginLog.CONSOLE_PREFIX))
                    return true
                }
            }
        }
        if (fillLogin) webView.addJavascriptInterface(SavedLoginBridge(), MicrosoftLogin.BRIDGE)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        setContentView(webView)
        LoginLog.add(this, "login screen opened")
        webView.loadUrl(Config.PORTAL_URL)
    }

    private fun fillScript() = MicrosoftLogin.fillScript(
        email = SavedLogin.email(this),
        password = if (passwordFilled) null else SavedLogin.password(this),
    )

    /** Called from [MicrosoftLogin.fillScript]. */
    private inner class SavedLoginBridge {
        @JavascriptInterface
        fun saveEmail(email: String) = onMicrosoftPage { SavedLogin.saveEmail(this@LoginActivity, email) }

        @JavascriptInterface
        fun savePassword(password: String) = onMicrosoftPage { SavedLogin.savePassword(this@LoginActivity, password) }

        @JavascriptInterface
        fun passwordFilled() = onMicrosoftPage { passwordFilled = true }
    }

    // Every page in the WebView can call the bridge, so only the Microsoft login is listened to.
    private fun onMicrosoftPage(action: () -> Unit) {
        webView.post { if (!isDestroyed && MicrosoftLogin.isMicrosoftPage(webView.url)) action() }
    }

    private fun onLoggedIn() {
        done = true
        CookieManager.getInstance().flush()
        LoginLog.add(this, "logged in")
        LoginLog.microsoftCookies(this)
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
