package dev.eduartesync.sync

import dev.eduartesync.source.AgendaFormatException
import dev.eduartesync.source.NoConnectionException
import dev.eduartesync.source.SignInPageException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** A failed sync explained in plain language, with the technical error kept as details. */
data class SyncProblem(val message: String, val details: String) {
    companion object {
        fun from(e: Throwable): SyncProblem {
            val message = when {
                isNetworkError(e) ->
                    "Couldn't reach Eduarte. Check your internet connection. Your calendar was not changed."
                e is SignInPageException ->
                    "Microsoft's sign-in page showed an error, often \"try again later\". Your calendar was not changed."
                // The app's own time limits (pages that didn't load, a week that didn't switch,
                // a page that stopped answering): the connection itself was fine.
                e is IOException ->
                    "Eduarte didn't respond in time. Try again later. Your calendar was not changed."
                e is AgendaFormatException ->
                    "Eduarte's schedule page looked different than expected, so your calendar was not changed."
                else ->
                    "Something went wrong while syncing. Your calendar was not changed."
            }
            return SyncProblem(message, describe(e))
        }

        /** The schedule was read, but writing it to the calendar failed. */
        fun fromCalendar(e: Throwable): SyncProblem {
            val message = if (e is SecurityException) {
                "Couldn't write to your calendar: Eduarte Sync has no calendar access. Open the app to allow it."
            } else {
                "Couldn't write to your calendar. Try again later; if it keeps happening, clear the calendar in settings."
            }
            return SyncProblem(message, describe(e))
        }

        /**
         * A real connection problem (no internet, DNS, refused or dropped connection), anywhere
         * in the cause chain. Not the app's own time limits, see [from].
         */
        internal fun isNetworkError(e: Throwable): Boolean =
            generateSequence(e) { it.cause }.take(5).any {
                it is NoConnectionException || it is UnknownHostException || it is SocketException ||
                    it is SocketTimeoutException || it is SSLException
            }

        /** Error type and message including causes. */
        private fun describe(e: Throwable): String =
            generateSequence(e) { it.cause }.take(3)
                .joinToString(" / caused by ") { "${it.javaClass.simpleName}: ${it.message}" }
    }
}
