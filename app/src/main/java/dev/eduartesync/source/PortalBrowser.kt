package dev.eduartesync.source

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.eduartesync.auth.LoginLog
import dev.eduartesync.auth.MicrosoftLogin
import dev.eduartesync.auth.SessionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
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
    private val appContext = context.applicationContext
    private var onPageFinished: ((String) -> Unit)? = null

    private val webView = WebView(appContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                onPageFinished?.invoke(url)
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                val text = message.message()
                if (!text.startsWith(LoginLog.CONSOLE_PREFIX)) return false
                LoginLog.add(appContext, text.removePrefix(LoginLog.CONSOLE_PREFIX))
                return true
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    }

    /**
     * Opens [url] and returns the final URL and HTML once a portal page whose path starts with
     * [targetPath] has loaded (by default the agenda).
     */
    suspend fun open(url: String, targetPath: String = AGENDA_PATH): Pair<String, String> {
        val landed = CompletableDeferred<String>()
        var reopened = false
        var renewing = false
        onPageFinished = { loaded ->
            when {
                isTargetPage(loaded, targetPath) -> landed.complete(loaded)
                // Session renewal stopped at "Stay signed in?" or "Pick an account": click through.
                MicrosoftLogin.isMicrosoftPage(loaded) -> {
                    if (!renewing) {
                        renewing = true
                        LoginLog.add(appContext, "sync: session renewal through Microsoft")
                        LoginLog.microsoftCookies(appContext)
                    }
                    LoginLog.page(appContext, loaded)
                    webView.evaluateJavascript(MicrosoftLogin.script(pickAccount = true), null)
                }
                // Came back from a session renewal on the home page: open the target page again.
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
            if (!SessionManager.isPortalPage(webView.url)) {
                LoginLog.add(appContext, "sync: login expired, stuck on this page:")
                LoginLog.page(appContext, webView.url)
                throw SessionExpiredException()
            }
            throw IOException("Eduarte did not load $targetPath in time")
        }
        CookieManager.getInstance().flush()
        if (renewing) LoginLog.add(appContext, "sync: session renewed without the user")
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

    /** The current URL and HTML of the page, as it is now (including background updates). */
    suspend fun currentPage(): Pair<String, String> = webView.url.orEmpty() to evaluate(PAGE_HTML)

    /** Runs [script] in the page and returns its result as a string ("" for null). */
    suspend fun run(script: String): String = evaluate(script)

    /**
     * Polls the JavaScript expression [condition] until it is truthy. Returns false if that
     * didn't happen within [timeoutMs]. For parts that the portal loads in the background.
     */
    suspend fun waitFor(condition: String, timeoutMs: Long = TIMEOUT_MS): Boolean =
        withTimeoutOrNull(timeoutMs) {
            while (evaluate("!!($condition)") != "true") delay(POLL_MS)
            true
        } ?: false

    /**
     * Runs [body] as the body of an async JavaScript function (it may use `await` and must
     * `return` a JSON-serializable value) and returns that value JSON encoded.
     *
     * evaluateJavascript can't wait for promises, so the script stores its outcome in a window
     * variable that is polled until it shows up. Throws [IOException] when the script didn't
     * finish within [timeoutMs] (for example because the page navigated away), and
     * [AgendaFormatException] when it threw.
     */
    suspend fun runAsync(body: String, timeoutMs: Long): String {
        val key = "__eduarteSync" + System.nanoTime()
        evaluate(asyncWrapper(key, body))
        val poll = "(function(){var r=window['$key'];if(r===undefined)return null;" +
            "delete window['$key'];return JSON.stringify(r);})()"
        val raw = withTimeoutOrNull(timeoutMs) {
            var result = ""
            while (result.isEmpty()) {
                delay(POLL_MS)
                result = evaluate(poll)
            }
            result
        } ?: throw IOException("Eduarte page script did not finish in time")
        val outcome = JSONObject(raw)
        if (!outcome.optBoolean("ok")) {
            throw AgendaFormatException("page script failed: ${outcome.optString("error")}")
        }
        return outcome.optString("value", "null")
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

    private fun asyncWrapper(key: String, body: String) = """
(function () {
  window['$key'] = undefined;
  Promise.resolve().then(async function () {
$body
  }).then(function (v) {
    window['$key'] = { ok: true, value: JSON.stringify(v === undefined ? null : v) };
  }, function (e) {
    window['$key'] = { ok: false, error: String((e && e.message) || e) };
  });
  return 'started';
})()
"""

    private fun isTargetPage(url: String, targetPath: String) =
        SessionManager.isPortalPage(url) && (Uri.parse(url).path ?: "").startsWith(targetPath)

    companion object {
        const val AGENDA_PATH = "/agenda"
        private const val TIMEOUT_MS = 45_000L
        private const val POLL_MS = 250L
        private const val SETTLE_MS = 750L
        private const val PAGE_HTML = "document.documentElement.outerHTML"
        private const val FIRST_DAY_HEADER = "(document.querySelector('div.agenda--day h4') || {}).textContent"
        private val DATE_FIELD_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    }
}

/** The login is gone: Microsoft wants the user to sign in again. */
class SessionExpiredException : Exception("Eduarte session expired")
