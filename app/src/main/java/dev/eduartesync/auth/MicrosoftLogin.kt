package dev.eduartesync.auth

import android.net.Uri
import org.json.JSONObject

/**
 * Helps the school's Microsoft login along on the pages that need a click but no password.
 *
 * - "Stay signed in?": answered with Yes, so Microsoft keeps a long-lived login cookie on the
 *   phone. With No (or no answer) the Microsoft login only lasts a short while and every
 *   background sync after that fails with "login expired".
 * - "Pick an account": when exactly one account is listed, it is picked, like a user would.
 *
 * A page that asks for the password is left alone; then the user really has to log in again,
 * unless they chose to save their login, see [fillScript].
 * What the script finds and does is reported with console messages starting with
 * [LoginLog.CONSOLE_PREFIX].
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
          var log = function (m) { console.log('${LoginLog.CONSOLE_PREFIX}' + m); };
          var tries = 0, sawPassword = false;
          (function attempt() {
            if (!sawPassword && document.querySelector('input[name="passwd"]:not([type="hidden"])')) {
              sawPassword = true;
              log('password asked');
            }
            var keep = document.getElementById('KmsiCheckboxField');
            var yes = document.getElementById('idSIButton9');
            if (keep && yes) {
              keep.checked = true;
              yes.click();
              log('clicked "stay signed in"');
              return;
            }
            if ($pickAccount && !sawPassword) {
              var tiles = Array.prototype.filter.call(
                document.querySelectorAll('#tilesHolder [role="button"][data-test-id]'),
                function (tile) { return tile.id !== 'otherTile'; });
              if (tiles.length === 1) {
                tiles[0].click();
                log('picked the only account');
                return;
              }
            }
            // The authenticator and password pages wait for the user, so keep watching for a while.
            if (++tries < 240) setTimeout(attempt, 500);
            else log('nothing to click; page title: ' + document.title);
          })();
        })()
    """.trimIndent()
    /** Name of the bridge the login screen adds when the user saves their login, see [SavedLogin]. */
    const val BRIDGE = "EduarteSavedLogin"

    /**
     * Script for the login screen when the user saves their login. Passes what the user types in
     * the email and password fields to the [BRIDGE], and fills in the saved [email] and [password]
     * (null when not saved, or already tried) and presses Next / Sign in. The authenticator
     * stays with the user.
     */
    fun fillScript(email: String?, password: String?) = """
        (function () {
          if (window.__eduarteFill) return;
          window.__eduarteFill = true;
          var log = function (m) { console.log('${LoginLog.CONSOLE_PREFIX}' + m); };
          var bridge = window.$BRIDGE;
          var EMAIL = 'input[name="loginfmt"]', PASSWORD = 'input[name="passwd"]';
          var email = ${js(email)}, password = ${js(password)};

          // Remember what is typed, and save it when the user moves on (click, Enter, leaving the page).
          var typed = {};
          document.addEventListener('input', function (e) {
            if (e.target.matches(EMAIL)) typed.email = e.target.value;
            if (e.target.matches(PASSWORD)) typed.password = e.target.value;
          }, true);
          var send = function () {
            if (typed.email) bridge.saveEmail(typed.email);
            if (typed.password) bridge.savePassword(typed.password);
            typed = {};
          };
          document.addEventListener('click', send, true);
          document.addEventListener('keydown', function (e) { if (e.key === 'Enter') send(); }, true);
          window.addEventListener('pagehide', send);

          var visible = function (el) { return el && el.offsetParent !== null; };
          var fill = function (input, value) {
            // The page's own code only notices a value set like this when it gets an input event.
            Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);
            input.dispatchEvent(new Event('input', { bubbles: true }));
            input.dispatchEvent(new Event('change', { bubbles: true }));
            setTimeout(function () {
              var button = document.getElementById('idSIButton9') ||
                (input.form && input.form.querySelector('[type="submit"]'));
              if (button) button.click();
            }, 300);
          };
          var tries = 0;
          (function attempt() {
            var field = document.querySelector(PASSWORD);
            if (password && visible(field) && !field.value) {
              fill(field, password);
              password = null;
              bridge.passwordFilled();
              log('filled in the saved password');
            }
            field = document.querySelector(EMAIL);
            if (email && visible(field) && !field.value) {
              fill(field, email);
              email = null;
              log('filled in the saved email');
            }
            if ((email || password) && ++tries < 240) setTimeout(attempt, 500);
          })();
        })()
    """.trimIndent()

    private fun js(value: String?) = value?.let(JSONObject::quote) ?: "null"
}
