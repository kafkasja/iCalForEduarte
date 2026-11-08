package dev.eduartesync.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Email and password for the Microsoft login, only when the user turned this on in settings.
 * Both are encrypted with a key in the Android Keystore that never leaves the phone, and stored
 * in the app's private storage (excluded from backups). Turning it off or logging out deletes them.
 */
object SavedLogin {
    private const val PREFS = "saved_login"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_EMAIL = "email"
    private const val KEY_PASSWORD = "password"
    private const val KEY_REJECTED = "password_rejected"
    private const val KEY_ALIAS = "eduarte_saved_login"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        if (!enabled) forget(context)
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun email(context: Context): String? = read(context, KEY_EMAIL)
    fun password(context: Context): String? = read(context, KEY_PASSWORD)

    fun saveEmail(context: Context, email: String) = write(context, KEY_EMAIL, email)
    fun savePassword(context: Context, password: String) = write(context, KEY_PASSWORD, password)

    /**
     * The saved password to fill in automatically, or null when none is saved or Microsoft
     * rejected it ([setPasswordRejected]).
     */
    fun passwordToFill(context: Context): String? =
        if (isPasswordRejected(context)) null else password(context)

    fun isPasswordRejected(context: Context): Boolean = prefs(context).getBoolean(KEY_REJECTED, false)

    /**
     * Set when Microsoft said the saved password is wrong (it was probably changed). It is then
     * not filled in anymore, in syncs or on the login screen, so a wrong password isn't tried
     * again and again, which could lock the account. Cleared when the user logs in by hand.
     */
    fun setPasswordRejected(context: Context, rejected: Boolean) {
        prefs(context).edit().putBoolean(KEY_REJECTED, rejected).apply()
    }

    /** Deletes the saved email and password (the setting itself stays). */
    fun forget(context: Context) {
        prefs(context).edit().remove(KEY_EMAIL).remove(KEY_PASSWORD).remove(KEY_REJECTED).apply()
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    private fun write(context: Context, key: String, value: String) {
        if (!isEnabled(context) || value.isEmpty()) return
        val cipher = Cipher.getInstance(CIPHER).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray())
        prefs(context).edit().putString(key, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    /** Null when nothing is saved, or when it can't be decrypted anymore (e.g. the key is gone). */
    private fun read(context: Context, key: String): String? {
        if (!isEnabled(context)) return null
        val stored = prefs(context).getString(key, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
            String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE))
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun keyStore() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
