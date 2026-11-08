package dev.eduartesync.auth

import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.util.zip.Inflater

/**
 * Helps the school's Microsoft login along on the pages that need a click but no password.
 *
 * - "Stay signed in?": answered with Yes, so Microsoft keeps a long-lived login cookie on the
 *   phone. With No (or no answer) the Microsoft login only lasts a short while.
 * - "Pick an account": when exactly one account is listed, it is picked, like a user would.
 *
 * The school's login asks for the password again every time Eduarte's own session runs out,
 * even with "stay signed in" (seen in the login log). So when the user chose to save their
 * login, [fillScript] fills it in, also during a background sync.
 *
 * The script reports which step of the login is on screen with console messages
 * "state: <step>" (see [State]), and other findings with free text; all start with
 * [LoginLog.CONSOLE_PREFIX].
 */
object MicrosoftLogin {

    /** Login steps the script recognizes. */
    enum class State(val key: String) {
        EMAIL("email"),
        PASSWORD("password"),
        /** Authenticator: tap the number shown on the login page. */
        MFA_NUMBER("mfa-number"),
        /** Authenticator: approve without a number, a text message, or a phone call. */
        MFA_APPROVE("mfa-approve"),
        /** Type a code from the Authenticator or a text message. */
        MFA_CODE("mfa-code"),
        /** The page shows an error, like a wrong password. */
        ERROR("error");

        val needsUser get() = this != ERROR

        companion object {
            fun fromKey(key: String) = entries.firstOrNull { it.key == key }
        }
    }

    /** A "state: ..." message from the script, or null for any other console message. */
    data class StateMessage(val state: State, val detail: String)

    fun parseState(message: String): StateMessage? {
        if (!message.startsWith(STATE_PREFIX)) return null
        val rest = message.removePrefix(STATE_PREFIX)
        val state = State.fromKey(rest.substringBefore(' ')) ?: return null
        return StateMessage(state, rest.substringAfter(' ', "").trim())
    }

    fun isMicrosoftPage(url: String?): Boolean {
        val host = url?.let(Uri::parse)?.host ?: return false
        return host == "login.microsoftonline.com" || host.endsWith(".microsoftonline.com") ||
            host == "login.live.com"
    }

    /**
     * For the login log: whether a SAML login request from Eduarte forces Microsoft to ask for
     * the password ("ForceAuthn"), which would explain why "stay signed in" doesn't help.
     * Null when [url] carries no SAML request (or it can't be read). Never returns the request itself.
     */
    fun describeSamlRequest(url: String?): String? {
        val encoded = url?.let(Uri::parse)?.getQueryParameter("SAMLRequest") ?: return null
        val xml = runCatching {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val inflater = Inflater(true).apply { setInput(bytes) }
            val out = ByteArray(16 * 1024)
            val n = inflater.inflate(out)
            inflater.end()
            String(out, 0, n)
        }.getOrNull() ?: return "SAML request (unreadable)"
        val attr = { name: String -> Regex("""\b$name="([^"]*)"""").find(xml)?.groupValues?.get(1) ?: "not set" }
        return "SAML request: ForceAuthn=${attr("ForceAuthn")}, IsPassive=${attr("IsPassive")}"
    }

    /**
     * Script for [android.webkit.WebView.evaluateJavascript]; keeps watching while the page
     * renders. Runs once per page, however often it is injected.
     */
    fun script(pickAccount: Boolean) = """
        (function () {
          if (window.__eduarteHelper) return;
          window.__eduarteHelper = true;
          var log = function (m) { console.log('${LoginLog.CONSOLE_PREFIX}' + m); };
          var visible = function (el) { return el && el.offsetParent !== null; };
          var text = function (sel) { var el = document.querySelector(sel); return visible(el) ? el.textContent.trim() : ''; };
          var tries = 0, reported = '';
          var report = function (state) {
            if (state === reported) return;
            reported = state;
            log('$STATE_PREFIX' + state);
          };
          (function attempt() {
            var keep = document.getElementById('KmsiCheckboxField');
            var yes = document.getElementById('idSIButton9');
            if (keep && yes) {
              keep.checked = true;
              yes.click();
              log('clicked "stay signed in"');
              return;
            }

            var error = text('#passwordError') || text('#usernameError') || text('#idTD_Error') || text('.alert-error');
            var number = text('#idRichContext_DisplaySign');
            if (error) report('error ' + error.slice(0, 120));
            else if (number) report('mfa-number ' + number);
            else if (visible(document.querySelector('input[name="otc"]'))) report('mfa-code');
            else if (visible(document.getElementById('idDiv_SAOTCAS_Title')) || visible(document.getElementById('idDiv_SAOTCC_Title'))) report('mfa-approve');
            else if (visible(document.querySelector('input[name="passwd"]'))) report('password');
            else if (visible(document.querySelector('input[name="loginfmt"]'))) report('email');

            if ($pickAccount && !reported) {
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
            if (++tries < 480) setTimeout(attempt, 500);
            else log('nothing to click; page title: ' + document.title);
          })();
        })()
    """.trimIndent()

    /** Name of the bridge the login screen adds when the user saves their login, see [SavedLogin]. */
    const val BRIDGE = "EduarteSavedLogin"

    /**
     * Script for when the user saves their login. Passes what the user types in the email and
     * password fields to the [BRIDGE] (when there is one: the background sync has none), and
     * fills in the saved [email] and [password] (null when not saved, or already tried) and
     * presses Next / Sign in. The authenticator stays with the user.
     */
    fun fillScript(email: String?, password: String?) = """
        (function () {
          if (window.__eduarteFill) return;
          window.__eduarteFill = true;
          var log = function (m) { console.log('${LoginLog.CONSOLE_PREFIX}' + m); };
          var noop = function () {};
          var bridge = window.$BRIDGE || { saveEmail: noop, savePassword: noop, passwordFilled: noop };
          var EMAIL = 'input[name="loginfmt"]', PASSWORD = 'input[name="passwd"]';
          var email = ${js(email)}, password = ${js(password)};

          // Remember what is typed, and save it when the user moves on (click, Enter, leaving the page).
          var typed = {};
          document.addEventListener('input', function (e) {
            if (!e.isTrusted) return; // our own fill() below, not the user
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

    private const val STATE_PREFIX = "state: "

    private fun js(value: String?) = value?.let(JSONObject::quote) ?: "null"
}
