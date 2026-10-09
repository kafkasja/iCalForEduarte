package dev.eduartesync.update

import android.content.Context
import dev.eduartesync.Config
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A release on the GitHub Releases page. */
data class Release(val version: String, val pageUrl: String, val apkUrl: String?)

/**
 * Reads the list of releases from GitHub, for the update section of the settings page.
 * Only runs while the settings page is open; it's the only request that doesn't go to Eduarte.
 */
object UpdateChecker {

    /** All published releases, newest first. Blocking network call: don't run on the main thread. */
    fun fetchReleases(): List<Release> {
        val connection = URL("https://api.github.com/repos/${Config.GITHUB_REPO}/releases")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode != 200) throw IOException("GitHub answered HTTP ${connection.responseCode}")
            return parseReleases(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    fun currentVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** Drafts and pre-releases are skipped; newest version first. */
    internal fun parseReleases(json: String): List<Release> {
        val list = JSONArray(json)
        return (0 until list.length())
            .map { list.getJSONObject(it) }
            .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
            .map(::toRelease)
            .sortedWith { a, b -> compareVersions(b.version, a.version) }
    }

    private fun toRelease(release: JSONObject): Release {
        val assets = release.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0))
            .map { assets!!.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") }
        return Release(
            version = release.getString("tag_name"),
            pageUrl = release.getString("html_url"),
            apkUrl = apk?.getString("browser_download_url"),
        )
    }

    /** "v0.10" is newer than "v0.9". Anything that isn't a version number counts as 0. */
    internal fun isNewer(latest: String, current: String) = compareVersions(latest, current) > 0

    private fun compareVersions(a: String, b: String): Int {
        val x = numbers(a)
        val y = numbers(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val diff = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }

    private fun numbers(version: String) =
        version.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
}
