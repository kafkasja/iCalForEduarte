package dev.eduartesync.records

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import dev.eduartesync.source.AgendaFormatException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a document from the portal with the login session of the hidden browser: the
 * WebView keeps its cookies in the app-wide [CookieManager], so a plain request that sends
 * them is logged in too. The file goes to the app's cache (only the last opened document is
 * kept), from where the phone's PDF viewer opens it.
 */
object DocumentDownloader {
    class Download(val file: File, val mimeType: String)

    /** Must be called on the main thread (the cookies and user agent are read there). */
    suspend fun download(context: Context, url: String, name: String): Download {
        val cookies = CookieManager.getInstance().getCookie(url)
        val userAgent = WebSettings.getDefaultUserAgent(context)
        val dir = File(context.cacheDir, DIRECTORY)
        return withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = true
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                cookies?.let { connection.setRequestProperty("Cookie", it) }
                connection.setRequestProperty("User-Agent", userAgent)
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("Eduarte answered $code for the document")
                val type = connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
                // A page instead of a file: the link went stale or the login is gone.
                if (type == "text/html") throw AgendaFormatException("Eduarte sent a page instead of the document")

                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, safeFileName(name))
                connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                Download(file, mimeType(type, file.name))
            } finally {
                connection.disconnect()
            }
        }
    }

    /** The name as shown, without characters a file name can't have. */
    internal fun safeFileName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().trimStart('.').ifEmpty { "document.pdf" }.take(120)

    /** The server's type when it says something useful, otherwise guessed from the extension. */
    internal fun mimeType(serverType: String, fileName: String): String = when {
        serverType.isNotEmpty() && serverType != "application/octet-stream" && serverType != "application/force-download" -> serverType
        fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
        else -> android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase()) ?: "application/pdf"
    }

    /** Also in res/xml/document_paths.xml. */
    const val DIRECTORY = "documents"
    private const val TIMEOUT_MS = 30_000
}
