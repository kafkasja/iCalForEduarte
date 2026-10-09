package dev.eduartesync.source

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Lets OkHttp use the WebView's cookies, so requests run with the session from the in-app
 * login, and refreshed cookies are written back.
 */
class WebViewCookieJar : CookieJar {
    private val store get() = CookieManager.getInstance()

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store.getCookie(url.toString())
            ?.split(";")
            ?.mapNotNull { Cookie.parse(url, it.trim()) }
            ?: emptyList()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { store.setCookie(url.toString(), it.toString()) }
        store.flush()
    }
}
