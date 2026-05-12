package com.example.a20260310.data.auth

import android.content.Context

object TokenManager {
    private const val PREFS_NAME = "moa_auth"
    private const val KEY_ACCESS_TOKEN = "access_token"

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun saveAccessToken(token: String) {
        prefs().edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    fun getAccessToken(): String? {
        return appContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(KEY_ACCESS_TOKEN, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun clear() {
        prefs().edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = getAccessToken() != null

    private fun prefs() =
        requireNotNull(appContext) { "TokenManager.init(context) must be called first." }
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
