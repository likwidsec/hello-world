package com.example.locationsharing

import android.content.Context

/**
 * Stores the small amount of setup the app needs to remember between launches:
 * the shared pair code (which "room" the two phones meet in) and this person's
 * display name. Backed by SharedPreferences.
 */
class PairSettings(context: Context) {

    private val preferences =
        context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)

    var pairCode: String
        get() = preferences.getString(KEY_PAIR_CODE, "") ?: ""
        set(value) = preferences.edit().putString(KEY_PAIR_CODE, value.trim()).apply()

    var displayName: String
        get() = preferences.getString(KEY_DISPLAY_NAME, "") ?: ""
        set(value) = preferences.edit().putString(KEY_DISPLAY_NAME, value.trim()).apply()

    fun hasCompletedPairing(): Boolean =
        pairCode.isNotBlank() && displayName.isNotBlank()

    companion object {
        private const val PREFERENCES_FILE_NAME = "location_sharing_preferences"
        private const val KEY_PAIR_CODE = "pair_code"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}
