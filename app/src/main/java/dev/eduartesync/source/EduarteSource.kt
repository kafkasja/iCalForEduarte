package dev.eduartesync.source

import dev.eduartesync.Config
import dev.eduartesync.model.Lesson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Reads the schedule from the Talnet Eduarte student portal's agenda page using the logged-in
 * session. The portal is a stateful Wicket app: it opens on the current week, and the
 * previous/next arrows are links to new page versions, which are followed week by week.
 */
class EduarteSource(
    private val http: OkHttpClient = defaultClient,
) {

    /** Lessons starting from [from] up to and including [to]. */
    suspend fun fetch(from: LocalDate, to: LocalDate): List<Lesson> =
        withContext(Dispatchers.IO) {
            var page = load(AGENDA_URL)
            val lessons = page.lessons.toMutableList()

            // Walk back until `from` is covered, then forward until `to` is covered.
            var steps = 0
            while (page.days.first() > from && steps++ < MAX_WEEKS) {
                page = load(page.previousWeekUrl ?: break)
                lessons += page.lessons
            }
            while (page.days.last() < to && steps++ < MAX_WEEKS) {
                page = load(page.nextWeekUrl ?: break)
                lessons += page.lessons
            }
            // Never sync a partial schedule: missing weeks would look like "no lessons" and
            // their events would be deleted.
            if (page.days.last() < to) throw IOException("Could not load the schedule up to $to")

            val fromMillis = from.atStartOfDay(Config.ZONE).toInstant().toEpochMilli()
            val toMillis = to.plusDays(1).atStartOfDay(Config.ZONE).toInstant().toEpochMilli()
            lessons.filter { it.startMillis in fromMillis until toMillis }.distinctBy { it.id }
        }

    private fun load(url: String): AgendaWeek {
        val (finalUrl, html) = get(url)
        return AgendaParser.parse(html, finalUrl, Config.ZONE)
    }

    /**
     * GET [url] with the session cookies, following Wicket's own redirects by hand.
     * Returns the final URL and body. Throws [SessionExpiredException] when sent to the login page.
     */
    private fun get(url: String): Pair<String, String> {
        var current = url
        repeat(MAX_REDIRECTS) {
            val request = Request.Builder().url(current).build()
            http.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    val location = response.header("Location") ?: throw IOException("Redirect without location")
                    val next = current.toHttpUrl().resolve(location) ?: throw IOException("Bad redirect: $location")
                    if (next.host != Config.PORTAL_HOST || next.encodedPath.startsWith("/oauth")) {
                        throw SessionExpiredException()
                    }
                    current = next.toString()
                } else {
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $current")
                    val body = response.body?.string() ?: throw IOException("Empty response for $current")
                    return current to body
                }
            }
        }
        throw IOException("Too many redirects for $url")
    }

    companion object {
        private const val AGENDA_URL = Config.PORTAL_URL + "agenda"
        private const val MAX_REDIRECTS = 6
        private const val MAX_WEEKS = 12

        // Redirects are handled by hand: a redirect off the portal means the session expired.
        private val defaultClient = OkHttpClient.Builder()
            .cookieJar(WebViewCookieJar())
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

/** Eduarte sent us to the login page: the session cookie is gone or expired. */
class SessionExpiredException : Exception("Eduarte session expired")
