package dev.eduartesync.ui.portal

import android.content.Context
import dev.eduartesync.source.AgendaFormatException
import dev.eduartesync.source.SessionExpiredException
import dev.eduartesync.sync.Notifier
import java.io.IOException

/**
 * A failed read of a portal page in plain language, like SyncProblem for the sync but without
 * the calendar (nothing is written anywhere when reading these pages fails).
 */
data class PortalProblem(val message: String, val details: String, val needsLogin: Boolean) {
    companion object {
        fun from(context: Context, e: Throwable): PortalProblem {
            val message = when (e) {
                is SessionExpiredException -> Notifier.loginRequiredText(context, e.reason)
                is IOException -> "Couldn't reach Eduarte. Check your internet connection."
                is AgendaFormatException -> "Eduarte's page looked different than expected, so it couldn't be read."
                else -> "Something went wrong while reading Eduarte."
            }
            val details = generateSequence(e) { it.cause }.take(3)
                .joinToString(" / caused by ") { "${it.javaClass.simpleName}: ${it.message}" }
            return PortalProblem(message, details, needsLogin = e is SessionExpiredException)
        }
    }
}
