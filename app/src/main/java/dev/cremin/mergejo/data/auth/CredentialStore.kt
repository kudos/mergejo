package dev.cremin.mergejo.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the Forgejo server URL and personal access token in
 * EncryptedSharedPreferences (AES256-GCM master key in the Android Keystore).
 */
class CredentialStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "credentials_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val serverUrl: String? get() = prefs.getString(KEY_URL, null)
    val token: String? get() = prefs.getString(KEY_TOKEN, null)

    fun save(serverUrl: String, token: String) {
        prefs.edit().putString(KEY_URL, serverUrl).putString(KEY_TOKEN, token).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_URL = "server_url"
        const val KEY_TOKEN = "token"
    }
}
