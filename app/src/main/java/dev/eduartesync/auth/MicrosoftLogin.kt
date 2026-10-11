package dev.eduartesync.auth

import android.net.Uri

/**
 * Helps the school's Microsoft login along on the pages that need a click but no password.
 *
 * - "Stay signed in?": answered with Yes, so Microsoft keeps a long-lived login cookie on the
 *   phone. With No (or no answer) the Microsoft login only lasts a short while and every
 *   background sync after that fails with "login expired".
 * - "Pick an account": when exactly one account is listed, it is picked, like a user would.
 *
 * A page that asks for the password is left alone; then the user really has to log in again.
 */
object MicrosoftLogin {

    fun isMicrosoftPage(url: String?): Boolean {
        val host = url?.let(Uri::parse)?.host ?: return false
        return host == "login.microsoftonline.com" || host.endsWith(".microsoftonline.com") ||
            host == "login.live.com"
    }

    /** Script for [android.webkit.WebView.evaluateJavascript]; keeps trying while the page renders. */
    fun script(pickAccount: Boolean) = """
        (function () {
          var tries = 0;
          (function attempt() {
            if (document.querySelector('input[name="passwd"]:not([type="hidden"])')) return;
            var keep = document.getElementById('KmsiCheckboxField');
            var yes = document.getElementById('idSIButton9');
            if (keep && yes) {
              keep.checked = true;
              yes.click();
              return;
            }
            if ($pickAccount) {
              var tiles = Array.prototype.filter.call(
                document.querySelectorAll('#tilesHolder [role="button"][data-test-id]'),
                function (tile) { return tile.id !== 'otherTile'; });
              if (tiles.length === 1) {
                tiles[0].click();
                return;
              }
            }
            if (++tries < 20) setTimeout(attempt, 500);
          })();
        })()
    """.trimIndent()
}
