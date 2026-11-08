package dev.eduartesync.debug

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.eduartesync.Config
import java.io.File

/**
 * Debug builds only. Opens a portal page with the app's login cookies and saves the page's
 * HTML (after an optional [EXTRA_JS] ran) to files/explore/<name>.html, to work out what the
 * portal offers. Start it with:
 *
 *   adb shell am start -n dev.eduartesync/.debug.PortalExplorerActivity --es url <url> --es name <name> [--es js <script>]
 *
 * and fetch the file with `adb shell run-as dev.eduartesync cat files/explore/<name>.html`.
 */
class PortalExplorerActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: Config.PORTAL_URL
        val name = intent.getStringExtra(EXTRA_NAME) ?: "page"
        val js = intent.getStringExtra(EXTRA_JS)
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = object : WebViewClient() {
            var saved = false
            override fun onPageFinished(view: WebView, loaded: String) {
                Log.i(TAG, "finished $loaded")
                if (saved || loaded.startsWith("about:")) return
                handler.removeCallbacksAndMessages(null)
                // Wait a moment for scripts that fill the page after loading.
                handler.postDelayed({
                    saved = true
                    val save = { save(view, name, loaded) }
                    if (js != null) view.evaluateJavascript(js) { result ->
                        Log.i(TAG, "js result: $result")
                        handler.postDelayed(save, SETTLE_MS)
                    } else save()
                }, SETTLE_MS)
            }
        }
        setContentView(webView)
        webView.loadUrl(url)
    }

    private fun save(view: WebView, name: String, url: String) {
        view.evaluateJavascript("document.documentElement.outerHTML") { json ->
            val html = org.json.JSONArray("[$json]").optString(0)
            val dir = File(filesDir, "explore").apply { mkdirs() }
            File(dir, "$name.html").writeText("<!-- $url -->\n$html")
            Log.i(TAG, "saved $name.html (${html.length} chars) from $url")
        }
    }

    private companion object {
        const val TAG = "PortalExplorer"
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
        const val EXTRA_JS = "js"
        const val SETTLE_MS = 2500L
    }
}
