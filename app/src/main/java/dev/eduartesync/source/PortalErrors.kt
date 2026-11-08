package dev.eduartesync.source

import kotlinx.coroutines.sync.Mutex
import java.io.IOException

/**
 * The portal (or its hidden browser) didn't get where it should within the time limit: a page
 * that didn't load, a week that didn't switch, a script that never answered. Not a network
 * error as such, so the user is told Eduarte was slow rather than to check their connection.
 */
class PortalTimeoutException(message: String) : IOException(message)

/** The hidden browser couldn't connect at all (no internet, DNS failure). [message] is WebView's own. */
class NoConnectionException(message: String) : IOException(message)

/**
 * Microsoft's sign-in page showed an error that isn't about the email or password, like
 * "too many attempts, try again later". Temporary, so a later sync may work.
 */
class SignInPageException(val detail: String) : IOException("Microsoft's sign-in page showed an error: $detail")

/**
 * One portal session at a time. Every hidden [PortalBrowser] (the calendar sync and the
 * portal readers like the Cijfers tab) shares the same cookies, so two of them renewing the
 * login at the same moment could log each other out or trigger two Authenticator requests.
 * Hold [mutex] for as long as a browser is alive.
 */
object PortalLock {
    val mutex = Mutex()
}
