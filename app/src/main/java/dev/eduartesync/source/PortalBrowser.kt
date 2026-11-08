package dev.eduartesync.source

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
    private var onConnectionError: ((String) -> Unit)? = null

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

            // Without internet the page never loads; say so right away instead of timing out.
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && error.errorCode in CONNECTION_ERRORS) {
                    onConnectionError?.invoke("${error.description} (${request.url.host})")
                }
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
        // A WebView that is never on screen is 0x0 pixels. Some portal lists (dossier,
        // documents) only draw the rows that fit on screen, so they came out empty: give it the
        // size of the phone's screen, as if it were shown.
        val screen = appContext.resources.displayMetrics
        measure(
            View.MeasureSpec.makeMeasureSpec(screen.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(screen.heightPixels, View.MeasureSpec.EXACTLY),
        )
        layout(0, 0, screen.widthPixels, screen.heightPixels)
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
        // Whether Microsoft asked for the Authenticator during this renewal, see the end.
        var authenticatorSeen = false
        var lastState: State? = null
        var deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        val fillLogin = SavedLogin.passwordToFill(appContext) != null &&
            (interactive || !SessionManager.backgroundNeedsAuthenticator(appContext))
        val fail = { e: Throwable -> if (failure == null) failure = e }

        var finalUrl: String? = null
        // The callbacks are cleared however this ends, also when the coroutine is cancelled.
        try {
            onState = { message ->
                lastState = message.state
                when (message.state) {
                    // The saved password is tried once: a second password page means it didn't work.
                    State.EMAIL -> if (!fillLogin) fail(SessionExpiredException(Reason.PASSWORD))
                    State.PASSWORD -> if (!fillLogin || ++passwordPages > 1) fail(SessionExpiredException(Reason.PASSWORD))
                    State.MFA_NUMBER, State.MFA_APPROVE, State.MFA_CODE -> {
                        authenticatorSeen = true
                        if (interactive) {
                            // Approving takes a moment: give the user a few minutes.
                            deadline = SystemClock.elapsedRealtime() + APPROVE_TIMEOUT_MS
                            if (message.state != State.MFA_CODE) onApproveSignIn(message.detail.ifEmpty { null })
                            else fail(SessionExpiredException(Reason.AUTHENTICATOR))
                        } else {
                            SessionManager.setBackgroundNeedsAuthenticator(appContext, true)
                            LoginLog.add(appContext, "sync: Microsoft wants the Authenticator; not renewing in the background anymore")
                            fail(SessionExpiredException(Reason.AUTHENTICATOR))
                        }
                    }
                    State.ERROR -> {
                        // Most likely the saved password is wrong (changed). Trying it again on
                        // every sync could lock the account, so it isn't filled in anymore until
                        // the user logs in by hand.
                        if (passwordTried) {
                            SavedLogin.setPasswordRejected(appContext, true)
                            LoginLog.add(appContext, "sync: saved password rejected; not filling it in until the next manual login")
                        }
                        fail(SessionExpiredException(Reason.ERROR, message.detail))
                    }
                    // "Try again later" and the like: nothing wrong with the login itself.
                    State.PAGE_ERROR -> fail(SignInPageException(message.detail))
                }
            }
            onConnectionError = { description -> fail(NoConnectionException(description)) }
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
                            val password = if (passwordTried) null else SavedLogin.passwordToFill(appContext)
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
            while (finalUrl == null && failure == null && SystemClock.elapsedRealtime() < deadline) {
                finalUrl = withTimeoutOrNull(POLL_MS) { landed.await() }
            }
        } finally {
            onPageFinished = null
            onState = null
            onConnectionError = null
        }

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
                // Stuck on the email or password page means the password is what's missing.
                val reason = if (lastState?.asksForPassword == true) Reason.PASSWORD else Reason.AUTHENTICATOR
                throw SessionExpiredException(reason)
            }
            LoginLog.add(appContext, "sync: timed out on this page:")
            LoginLog.page(appContext, webView.url)
            throw PortalTimeoutException("Eduarte did not load $targetPath in time")
        }
        CookieManager.getInstance().flush()
        if (renewing) {
            LoginLog.add(appContext, "sync: session renewed" + if (passwordTried) " with the saved password" else " without the user")
            // It worked without the Authenticator: a later background renewal may try again.
            if (!authenticatorSeen) SessionManager.setBackgroundNeedsAuthenticator(appContext, false)
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
        if (!switched) throw PortalTimeoutException("Eduarte did not switch to the week of $date")
        // The new week's lesson positions (style rules) arrive just after the lessons. Parsing
        // before they're all there would drop or misplace lessons, so wait for every one.
        if (!waitFor(WEEK_READY, TIMEOUT_MS)) {
            throw PortalTimeoutException("Eduarte did not show the lesson times for the week of $date")
        }
        return webView.url.orEmpty() to evaluate(PAGE_HTML)
    }

    /**
     * Makes sure the agenda shows the "Week" view with "Toon lesuren" ticked, the only layout
     * [AgendaParser] understands. Both are remembered by the portal, so switching to "Lijst"
     * in the browser (or unticking the box) used to make every sync fail with "unexpected
     * agenda page". Each switch reloads the agenda; returns once it shows the right layout.
     *
     * The fix script changes the filter inputs right away (the checkbox ticks on click), but
     * the agenda is redrawn later in the background. So success is judged by the drawn agenda
     * ([WEEK_READY]), and after each fix it waits until the agenda was actually redrawn (the
     * parts shown before the fix are marked, see [NEW_AGENDA]).
     */
    suspend fun ensureWeekView() {
        // The filter is part of the page, but may still be loading in the background.
        if (!waitFor(FILTER_INPUTS, CONTENT_TIMEOUT_MS)) throw AgendaFormatException("agenda view switch not found")
        repeat(3) {
            if (evaluate(AGENDA_LAYOUT) == "ok" && waitFor(WEEK_READY, CONTENT_TIMEOUT_MS)) return
            evaluate(MARK_AGENDA)
            val action = evaluate(FIX_AGENDA_LAYOUT)
            LoginLog.add(appContext, "sync: agenda layout was changed, switching back ($action)")
            if (action == "missing") throw AgendaFormatException("agenda view switch not found")
            // The filter says Week with lesson hours, yet no week grid is drawn.
            if (action == "nothing") throw AgendaFormatException("agenda shows no week grid")
            // Done once a new agenda is drawn that is either the week grid, or still needs
            // another fix (each fix changes one thing).
            val redrawn = waitFor(
                "document.readyState === 'complete' && $NEW_AGENDA && ($AGENDA_LAYOUT !== 'ok' || $WEEK_READY)",
                TIMEOUT_MS,
            )
            if (!redrawn) throw PortalTimeoutException("Eduarte did not redraw the agenda after switching the view ($action)")
        }
        if (evaluate(AGENDA_LAYOUT) != "ok" || evaluate(WEEK_READY) != "true") {
            throw AgendaFormatException("could not switch the agenda to the Week view")
        }
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
            // A page that is being replaced may never answer, so each check gets its own time limit.
            while (withTimeoutOrNull(CHECK_MS) { evaluate("!!($condition)") } != "true") delay(POLL_MS)
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
        } ?: throw PortalTimeoutException("Eduarte page script did not finish in time")
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

    /**
     * Runs [script] in the page and returns its result as a string ("" for null).
     *
     * WebView never answers a script that was pending when the page navigated away, so this
     * gives up after [EVALUATE_TIMEOUT_MS] instead of waiting forever.
     */
    private suspend fun evaluate(script: String): String = withTimeoutOrNull(EVALUATE_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript(script) { json ->
                // The result comes back JSON encoded.
                val value = JSONArray("[$json]")
                cont.resume(if (value.isNull(0)) "" else value.get(0).toString())
            }
        }
    } ?: throw PortalTimeoutException("Eduarte stopped responding")

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
        private const val CHECK_MS = 2_000L
        private const val EVALUATE_TIMEOUT_MS = 10_000L
        /** For parts of the agenda that load in the background. */
        private const val CONTENT_TIMEOUT_MS = 20_000L
        /** WebView errors that mean there is no connection, see onReceivedError. */
        private val CONNECTION_ERRORS = setOf(
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_TIMEOUT,
        )
        private const val PAGE_HTML = "document.documentElement.outerHTML"
        private const val FILTER_INPUTS = "document.querySelector('input[name=\"filter:weergaveKiezer:choicesContainer:enumHiddenField\"]') && " +
            "document.querySelector('input[name=\"filter:lesuren\"]')"
        /**
         * The agenda and its columns. In the captured page `div.agenda` itself has no id, so the
         * portal may redraw just a part of it (the days, or the lesson hour column); all parts
         * are watched.
         */
        private const val AGENDA_PARTS = "div.agenda, div.agenda--days, div.agenda--schedule, div.agenda--time, div.agenda--day"
        /** Marks the agenda parts shown now, so [NEW_AGENDA] can tell when they were replaced. */
        private const val MARK_AGENDA = """(function () {
            var parts = document.querySelectorAll('$AGENDA_PARTS');
            parts.forEach(function (p) { p.__eduarteOld = true; });
            window.__eduarteMarked = parts.length;
            return parts.length;
        })()"""
        /**
         * True once the agenda was redrawn after [MARK_AGENDA]: a marked part is gone or an
         * unmarked one appeared (also when the whole page was loaded again). Needs a `div.agenda`.
         */
        private const val NEW_AGENDA = """(function () {
            if (!document.querySelector('div.agenda')) return false;
            if (window.__eduarteMarked === undefined) return true;
            var parts = document.querySelectorAll('$AGENDA_PARTS'), old = 0;
            for (var i = 0; i < parts.length; i++) {
                if (!parts[i].__eduarteOld) return true;
                old++;
            }
            return old < window.__eduarteMarked;
        })()"""
        /**
         * True when the agenda is drawn the way [AgendaParser] reads it: a time column starting
         * with an HH:mm label, lesson hour rows, and a position (style rule with top and
         * height) for every row and every lesson. Looks at what is drawn, not at the filter.
         */
        private const val WEEK_READY = """(function () {
            var agenda = document.querySelector('div.agenda');
            if (!agenda) return false;
            var time = agenda.querySelector('div.agenda--time li span');
            if (!time || !/^\d{2}:\d{2}$/.test(time.textContent.trim())) return false;
            var rows = agenda.querySelectorAll('div.agenda--schedule li[id]');
            if (!rows.length) return false;
            var css = Array.prototype.map.call(document.querySelectorAll('style'), function (s) { return s.textContent; }).join('\n');
            var placed = function (el) {
                var rule = new RegExp('#' + el.id.replace(/[^\w-]/g, '') + '\\s*\\{([^}]*)\\}').exec(css);
                return !!rule && /top\s*:\s*-?[\d.]+px/.test(rule[1]) && /height\s*:\s*[\d.]+px/.test(rule[1]);
            };
            for (var i = 0; i < rows.length; i++) if (!placed(rows[i])) return false;
            var lessons = agenda.querySelectorAll('li.agenda--course');
            for (var j = 0; j < lessons.length; j++) if (!placed(lessons[j])) return false;
            return true;
        })()"""
        /** "ok" when the agenda is in the Week view with lesson hours shown, else what's off. */
        private const val AGENDA_LAYOUT = """(function () {
            var view = document.querySelector('input[name="filter:weergaveKiezer:choicesContainer:enumHiddenField"]');
            var hours = document.querySelector('input[name="filter:lesuren"]');
            if (!view || !hours) return 'missing';
            if (view.value !== 'Week') return 'view ' + view.value;
            if (!hours.checked) return 'no lesson hours';
            return 'ok';
        })()"""
        /** Fixes one thing at a time (each one reloads the agenda) and says what it did. */
        private const val FIX_AGENDA_LAYOUT = """(function () {
            var view = document.querySelector('input[name="filter:weergaveKiezer:choicesContainer:enumHiddenField"]');
            var hours = document.querySelector('input[name="filter:lesuren"]');
            if (!view || !hours) return 'missing';
            if (view.value !== 'Week') {
                var was = view.value;
                var tab = document.querySelector('li[data-enumvalue="Week"] a');
                if (tab) tab.click();
                if (view.value !== 'Week') { view.value = 'Week'; if (window.jQuery) jQuery(view).trigger('change'); }
                return 'view was ' + was;
            }
            if (!hours.checked) { hours.click(); return 'ticked lesson hours'; }
            return 'nothing';
        })()"""
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
