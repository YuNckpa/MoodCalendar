package com.moodcalendar.app.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionStore: DataStore<Preferences> by preferencesDataStore(name = "auth_session")

data class UserSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
    val displayName: String = "",
    val avatarUrl: String? = null,
    val friendCode: String = ""
)

class SessionStore(private val context: Context) {
    private val userIdKey = stringPreferencesKey("user_id")
    private val emailKey = stringPreferencesKey("email")
    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")
    private val expiresKey = longPreferencesKey("expires_at")
    private val displayNameKey = stringPreferencesKey("display_name")
    private val avatarKey = stringPreferencesKey("avatar_url")
    private val friendCodeKey = stringPreferencesKey("friend_code")

    val sessionFlow: Flow<UserSession?> = context.sessionStore.data.map { prefs ->
        val userId = prefs[userIdKey] ?: return@map null
        val access = prefs[accessKey] ?: return@map null
        val refresh = prefs[refreshKey] ?: return@map null
        UserSession(
            userId = userId,
            email = prefs[emailKey].orEmpty(),
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochMs = prefs[expiresKey] ?: 0L,
            displayName = prefs[displayNameKey].orEmpty(),
            avatarUrl = prefs[avatarKey],
            friendCode = prefs[friendCodeKey].orEmpty()
        )
    }

    suspend fun save(session: UserSession) {
        context.sessionStore.edit { prefs ->
            prefs[userIdKey] = session.userId
            prefs[emailKey] = session.email
            prefs[accessKey] = session.accessToken
            prefs[refreshKey] = session.refreshToken
            prefs[expiresKey] = session.expiresAtEpochMs
            prefs[displayNameKey] = session.displayName
            if (session.avatarUrl == null) prefs.remove(avatarKey) else prefs[avatarKey] = session.avatarUrl
            prefs[friendCodeKey] = session.friendCode
        }
    }

    suspend fun clear() {
        context.sessionStore.edit { it.clear() }
    }
}
