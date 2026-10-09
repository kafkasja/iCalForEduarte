package dev.eduartesync.sync

import dev.eduartesync.source.AgendaFormatException
import java.io.IOException

/** A failed sync explained in plain language, with the technical error kept as details. */
data class SyncProblem(val message: String, val details: String) {
    companion object {
        fun from(e: Throwable): SyncProblem {
            val message = when (e) {
                is IOException ->
                    "Couldn't reach Eduarte. Check your internet connection. Your calendar was not changed."
                is AgendaFormatException ->
                    "Eduarte's schedule page looked different than expected, so your calendar was not changed."
                else ->
                    "Something went wrong while syncing. Your calendar was not changed."
            }
            return SyncProblem(message, describe(e))
        }

        /** Error type and message including causes. */
        private fun describe(e: Throwable): String =
            generateSequence(e) { it.cause }.take(3)
                .joinToString(" / caused by ") { "${it.javaClass.simpleName}: ${it.message}" }
    }
}
