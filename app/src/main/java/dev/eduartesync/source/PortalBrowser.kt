package dev.eduartesync.source

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.eduartesync.auth.SessionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
        return finalUrl to evaluate(PAGE_HTML)
    }

    /**
     * Switches the open agenda to the week containing [date] by typing the date into the
     * agenda's date field, like a user would. Eduarte then reloads the agenda part of the page
     * in the background. Returns the URL and HTML once the new week is shown.
     */
    suspend fun showWeekOf(date: LocalDate): Pair<String, String> {
        val before = evaluate(FIRST_DAY_HEADER)
        val result = evaluate(setDate(date.format(DATE_FIELD_FORMAT)))
        if (result != "ok") throw AgendaFormatException("date field not found")

        val switched = withTimeoutOrNull(TIMEOUT_MS) {
            while (evaluate(FIRST_DAY_HEADER) == before) delay(POLL_MS)
        } != null
        if (!switched) throw IOException("Eduarte did not switch to the week of $date")
        delay(SETTLE_MS) // the new week's lesson positions arrive just after the lessons
        return webView.url.orEmpty() to evaluate(PAGE_HTML)
    }

    fun destroy() {
        webView.stopLoading()
        webView.destroy()
    }

    /** Runs [script] in the page and returns its result as a string ("" for null). */
    private suspend fun evaluate(script: String): String = suspendCancellableCoroutine { cont ->
        webView.evaluateJavascript(script) { json ->
            // The result comes back JSON encoded.
            val value = JSONArray("[$json]")
            cont.resume(if (value.isNull(0)) "" else value.get(0).toString())
        }
    }

    private fun setDate(date: String) = """
        (function () {
          var field = document.querySelector('input[name="filter:datum"]');
          if (!field) return 'missing';
          field.value = '$date';
          if (window.jQuery) jQuery(field).trigger('change');
          else field.dispatchEvent(new Event('change', { bubbles: true }));
          return 'ok';
        })()
    """.trimIndent()

    private fun isAgendaPage(url: String) =
        SessionManager.isPortalPage(url) && (Uri.parse(url).path ?: "").startsWith("/agenda")

    private companion object {
        const val TIMEOUT_MS = 45_000L
        const val POLL_MS = 250L
        const val SETTLE_MS = 750L
        const val PAGE_HTML = "document.documentElement.outerHTML"
        const val FIRST_DAY_HEADER = "(document.querySelector('div.agenda--day h4') || {}).textContent"
        val DATE_FIELD_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    }
}

/** The login is gone: Microsoft wants the user to sign in again. */
class SessionExpiredException : Exception("Eduarte session expired")
