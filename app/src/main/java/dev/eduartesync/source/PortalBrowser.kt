package dev.eduartesync.source

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.eduartesync.auth.LoginLog
import dev.eduartesync.auth.MicrosoftLogin
import dev.eduartesync.auth.MicrosoftLogin.State
import dev.eduartesync.auth.SavedLogin
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
 * in-app login. When Eduarte's session ran out, the portal sends it through the school's
 * Microsoft login. That login asks for the password again, so it is filled in when the user
 * chose to save it ([SavedLogin]).
 *
 * If Microsoft then also wants the Authenticator:
 * - [interactive] (the user tapped the app icon): [onApproveSignIn] is called with the number
 *   to tap in the Authenticator (or null), and the sign-in is given a few minutes.
 * - background sync: it stops right away, so the user never gets an Authenticator request
 *   they didn't ask for, and remembers that background renewal doesn't work for this account.
 *
 * Must be created and used on the main thread.
 */
@SuppressLint("SetJavaScriptEnabled")
class PortalBrowser(
    context: Context,
    private val interactive: Boolean = false,
    private val onApproveSignIn: (number: String?) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private var onPageFinished: ((String) -> Unit)? = null
    private var onState: ((MicrosoftLogin.StateMessage) -> Unit)? = null

    private val webView = WebView(appContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                MicrosoftLogin.describeSamlRequest(url)?.let { LoginLog.add(appContext, "sync: $it") }
            }

            override fun onPageFinished(view: WebView, url: String) {
                onPageFinished?.invoke(url)
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                val text = message.message()
                if (!text.startsWith(LoginLog.CONSOLE_PREFIX)) return false
                val content = text.removePrefix(LoginLog.CONSOLE_PREFIX)
                LoginLog.add(appContext, "sync: $content")
                MicrosoftLogin.parseState(content)?.let { onState?.invoke(it) }
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
        var failure: Throwable? = null
        var reopened = false
        var renewing = false
        var passwordTried = false
        var passwordPages = 0
        var lastState: State? = null
        var deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        val fillLogin = SavedLogin.password(appContext) != null &&
            (interactive || !SessionManager.backgroundNeedsAuthenticator(appContext))
        val fail = { e: Throwable -> if (failure == null) failure = e }

        onState = { message ->
            lastState = message.state
            when (message.state) {
                // The saved password is tried once: a second password page means it didn't work.
                State.EMAIL -> if (!fillLogin) fail(SessionExpiredException(Reason.PASSWORD))
                State.PASSWORD -> if (!fillLogin || ++passwordPages > 1) fail(SessionExpiredException(Reason.PASSWORD))
                State.MFA_NUMBER, State.MFA_APPROVE, State.MFA_CODE -> if (interactive) {
                    // Approving takes a moment: give the user a few minutes.
                    deadline = SystemClock.elapsedRealtime() + APPROVE_TIMEOUT_MS
                    if (message.state != State.MFA_CODE) onApproveSignIn(message.detail.ifEmpty { null })
                    else fail(SessionExpiredException(Reason.AUTHENTICATOR))
                } else {
                    SessionManager.setBackgroundNeedsAuthenticator(appContext, true)
                    LoginLog.add(appContext, "sync: Microsoft wants the Authenticator; not renewing in the background anymore")
                    fail(SessionExpiredException(Reason.AUTHENTICATOR))
                }
                State.ERROR -> fail(SessionExpiredException(Reason.ERROR, message.detail))
            }
        }
        onPageFinished = { loaded ->
            when {
                isTargetPage(loaded, targetPath) -> landed.complete(loaded)
                MicrosoftLogin.isMicrosoftPage(loaded) -> {
                    if (!renewing) {
                        renewing = true
                        LoginLog.add(appContext, "sync: session renewal through Microsoft" + if (interactive) " (app icon)" else " (background)")
                        LoginLog.microsoftCookies(appContext)
                    }
                    LoginLog.page(appContext, loaded)
                    webView.evaluateJavascript(MicrosoftLogin.script(pickAccount = true), null)
                    if (fillLogin) {
                        val password = if (passwordTried) null else SavedLogin.password(appContext)
                        if (password != null) passwordTried = true
                        webView.evaluateJavascript(MicrosoftLogin.fillScript(SavedLogin.email(appContext), password), null)
                    }
                }
                // Came back from a session renewal on the home page: open the target page again.
                SessionManager.isPortalPage(loaded) && !reopened -> {
                    reopened = true
                    webView.loadUrl(url)
                }
            }
        }
        webView.loadUrl(url)

        // Wait for the agenda, a reason to give up, or the deadline (which approving moves).
        var finalUrl: String? = null
        while (finalUrl == null && failure == null && SystemClock.elapsedRealtime() < deadline) {
            finalUrl = withTimeoutOrNull(POLL_MS) { landed.await() }
        }
        onPageFinished = null
        onState = null

        if (finalUrl == null) {
            webView.stopLoading()
            failure?.let {
                LoginLog.add(appContext, "sync: login needed, stopped on this page:")
                LoginLog.page(appContext, webView.url)
                throw it
            }
            // Stuck on the Microsoft or Eduarte login page: the user has to log in again.
            if (!SessionManager.isPortalPage(webView.url) && lastState != null) {
                LoginLog.add(appContext, "sync: login expired (last step: ${lastState?.key}), stuck on this page:")
                LoginLog.page(appContext, webView.url)
                throw SessionExpiredException(if (lastState == State.PASSWORD) Reason.PASSWORD else Reason.AUTHENTICATOR)
            }
            LoginLog.add(appContext, "sync: timed out on this page:")
            LoginLog.page(appContext, webView.url)
            throw IOException("Eduarte did not load $targetPath in time")
        }
        CookieManager.getInstance().flush()
        if (renewing) {
            LoginLog.add(appContext, "sync: session renewed" + if (passwordTried) " with the saved password" else " without the user")
            // It worked: a later background renewal may try again.
            if (!interactive || lastState == null) SessionManager.setBackgroundNeedsAuthenticator(appContext, false)
        }
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
        private const val APPROVE_TIMEOUT_MS = 180_000L
        private const val POLL_MS = 250L
        private const val SETTLE_MS = 750L
        private const val PAGE_HTML = "document.documentElement.outerHTML"
        private const val FIRST_DAY_HEADER = "(document.querySelector('div.agenda--day h4') || {}).textContent"
        private val DATE_FIELD_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    }
}

/** The login is gone: Microsoft wants the user to sign in again. [reason] says what for. */
class SessionExpiredException(val reason: Reason, val detail: String = "") :
    Exception("Eduarte session expired: ${reason.name.lowercase()}" + if (detail.isNotEmpty()) " ($detail)" else "")

enum class Reason {
    /** Microsoft asked for the password, and none is saved (or the saved one didn't work). */
    PASSWORD,
    /** The password was fine but Microsoft wants the Authenticator, which needs the user. */
    AUTHENTICATOR,
    /** Microsoft showed an error, like a wrong password. */
    ERROR,
}
