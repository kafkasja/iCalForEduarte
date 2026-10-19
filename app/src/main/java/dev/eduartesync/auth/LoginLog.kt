package dev.eduartesync.auth

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Short history of what happened during logins and session renewals, shown in settings to
 * find out why Microsoft keeps asking for the authenticator. Only page addresses (without the
 * query, which holds tokens) and cookie names are kept, never cookie values.
 */
object LoginLog {
    private const val PREFS = "login_log"
    private const val KEY_LINES = "lines"
    private const val MAX_LINES = 80
    private const val TAG = "EduarteSync"
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM HH:mm:ss")

    /** Prefix the login script puts in front of its console messages. */
    const val CONSOLE_PREFIX = "EduarteLogin: "

    @Synchronized
    fun add(context: Context, message: String) {
        Log.i(TAG, "Login: $message")
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lines = prefs.getString(KEY_LINES, "").orEmpty().lines().filter { it.isNotEmpty() }
        val line = "${LocalDateTime.now().format(TIME)}  $message"
        prefs.edit().putString(KEY_LINES, (lines + line).takeLast(MAX_LINES).joinToString("\n")).apply()
    }

    fun page(context: Context, url: String?) {
        val uri = url?.let(Uri::parse) ?: return
        add(context, "page ${uri.host}${uri.path.orEmpty()}")
    }

    /** Logs which Microsoft login cookies are on the phone; ESTSAUTHPERSISTENT is the long-lived one. */
    fun microsoftCookies(context: Context) {
        val names = CookieManager.getInstance().getCookie("https://login.microsoftonline.com")
            ?.split(";")?.map { it.substringBefore("=").trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val persistent = if ("ESTSAUTHPERSISTENT" in names) "yes" else "NO"
        add(context, "Microsoft cookies (stay signed in: $persistent): ${names.joinToString(", ").ifEmpty { "none" }}")
    }

    fun text(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LINES, "").orEmpty()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
