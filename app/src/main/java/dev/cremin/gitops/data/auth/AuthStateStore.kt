package dev.cremin.gitops.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.openid.appauth.AuthState

/**
 * Persists the AppAuth [AuthState] (which holds access/refresh tokens and the
 * resolved service config) in EncryptedSharedPreferences, backed by an
 * AES256-GCM master key in the Android Keystore.
 */
class AuthStateStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "auth_state_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): AuthState =
        prefs.getString(KEY, null)
            ?.let { runCatching { AuthState.jsonDeserialize(it) }.getOrNull() }
            ?: AuthState()

    fun save(state: AuthState) {
        prefs.edit().putString(KEY, state.jsonSerializeString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "auth_state"
    }
}
