package com.example.bai1.auth.session

import android.content.Context

interface TokenStorage {
    /** Returns true only after both tokens have been durably committed. */
    fun save(accessToken: String, refreshToken: String): Boolean
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun clear()
    fun isLoggedIn(): Boolean
}

/** The only class that owns auth_session SharedPreferences access. */
class SharedPreferencesTokenStorage(context: Context) : TokenStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun save(accessToken: String, refreshToken: String): Boolean =
        preferences.edit()
            .putString(ACCESS_TOKEN_KEY, accessToken)
            .putString(REFRESH_TOKEN_KEY, refreshToken)
            .commit()

    override fun getAccessToken(): String? = preferences.getString(ACCESS_TOKEN_KEY, null)

    override fun getRefreshToken(): String? = preferences.getString(REFRESH_TOKEN_KEY, null)

    override fun clear() {
        preferences.edit()
            .remove(ACCESS_TOKEN_KEY)
            .remove(REFRESH_TOKEN_KEY)
            .commit()
    }

    override fun isLoggedIn(): Boolean = !getAccessToken().isNullOrBlank()

    companion object {
        const val PREFERENCES_NAME = "auth_session"
        const val ACCESS_TOKEN_KEY = "access_token"
        const val REFRESH_TOKEN_KEY = "refresh_token"
    }
}
