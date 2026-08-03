package io.hex128.uproxconcierge

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class Settings(
    context: Context
) {
    companion object {
        const val SHARED_PREFS_NAME = "settings"
        const val PREFS_KEY_URL = "url"
        const val PREFS_KEY_USER = "user"
        const val PREFS_KEY_PASSWORD = "password"
        const val PREFS_KEY_UPDATES = "updates"
        const val PREFS_KEY_TRUSTED_FINGERPRINTS = "trusted_fingerprints"
        const val SETTINGS_DIALOG_TAG = "settings"
    }

    inline fun <T> SharedPreferences.getOrReset(
        key: String,
        defaultValue: T,
        getter: SharedPreferences.(String, T) -> T
    ): T {
        return try {
            getter(key, defaultValue)
        } catch (_: ClassCastException) {
            edit { remove(key) }
            defaultValue
        }
    }

    private val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)

    var url: String
        get() = prefs.getOrReset(PREFS_KEY_URL, "") { key, def ->
            getString(key, def)!!
        }
        set(url) {
            prefs.edit { putString(PREFS_KEY_URL, url) }
        }

    var user: String
        get() = prefs.getOrReset(PREFS_KEY_USER, "") { key, def ->
            getString(key, def)!!
        }
        set(user) {
            prefs.edit { putString(PREFS_KEY_USER, user) }
        }

    var password: String
        get() = prefs.getOrReset(PREFS_KEY_PASSWORD, "") { key, def ->
            getString(key, def)!!
        }
        set(password) {
            prefs.edit { putString(PREFS_KEY_PASSWORD, password) }
        }

    var isAutoUpdateCheckEnabled: Boolean
        get() = prefs.getOrReset(PREFS_KEY_UPDATES, true) { key, def ->
            getBoolean(key, def)
        }
        set(checkForUpdates) {
            prefs.edit { putBoolean(PREFS_KEY_UPDATES, checkForUpdates) }
        }

    var trustedFingerprints: Set<String>
        get() = prefs.getOrReset(PREFS_KEY_TRUSTED_FINGERPRINTS, setOf()) { key, def ->
            getStringSet(key, def)!!
        }
        set(trustedFingerprints) {
            prefs.edit {
                putStringSet(PREFS_KEY_TRUSTED_FINGERPRINTS, trustedFingerprints)
            }
        }
}
