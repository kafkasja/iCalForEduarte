package dev.eduartesync.source

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.eduartesync.auth.SessionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Hidden WebView that browses the portal like a normal browser, using the session from the
 * in-app login. If the session expired, the portal sends it through the Microsoft login, which
 * completes on its own as long as the Microsoft session is still valid.
 *
 * Must be created and used on the main thread.
 */
@SuppressLint("SetJavaScriptEnabled")
class PortalBrowser(context: Context) {
    private var onPageFinished: ((String) -> Unit)? = null

    private val webView = WebView(context.applicationContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                onPageFinished?.invoke(url)
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    }

    /** Opens [url] and returns the final URL and HTML once an agenda page has loaded. */
    suspend fun open(url: String): Pair<String, String> {
        val landed = CompletableDeferred<String>()
        var reopened = false
        onPageFinished = { loaded ->
            when {
                isAgendaPage(loaded) -> landed.complete(loaded)
                // Came back from a session renewal on the home page: open the agenda again.
                SessionManager.isPortalPage(loaded) && !reopened -> {
                    reopened = true
                    webView.loadUrl(url)
                }
            }
        }
        webView.loadUrl(url)
        val finalUrl = withTimeoutOrNull(TIMEOUT_MS) { landed.await() }
        onPageFinished = null

        if (finalUrl == null) {
            // Stuck on the Microsoft or Eduarte login page: the user has to log in again.
            if (!SessionManager.isPortalPage(webView.url)) throw SessionExpiredException()
            throw IOException("Eduarte did not load the agenda in time")
        }
        CookieManager.getInstance().flush()
        return finalUrl to currentHtml()
    }

    fun destroy() {
        webView.stopLoading()
        webView.destroy()
    }

    private suspend fun currentHtml(): String = suspendCancellableCoroutine { cont ->
        webView.evaluateJavascript("document.documentElement.outerHTML") { json ->
            // The result is a JSON string literal.
            cont.resume(JSONArray("[$json]").getString(0))
        }
    }

    private fun isAgendaPage(url: String) =
        SessionManager.isPortalPage(url) && (Uri.parse(url).path ?: "").startsWith("/agenda")

    private companion object {
        const val TIMEOUT_MS = 45_000L
    }
}

/** The login is gone: Microsoft wants the user to sign in again. */
class SessionExpiredException : Exception("Eduarte session expired")
