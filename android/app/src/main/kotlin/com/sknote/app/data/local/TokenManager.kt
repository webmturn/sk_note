package com.sknote.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sk_note_prefs")

class TokenManager(private val context: Context) {

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val USER_ROLE_KEY = stringPreferencesKey("user_role")
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }

    fun getToken(): Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }
    fun getUsername(): Flow<String?> = context.dataStore.data.map { it[USERNAME_KEY] }
    fun getUserRole(): Flow<String?> = context.dataStore.data.map { it[USER_ROLE_KEY] }

    suspend fun saveAuth(token: String, username: String, role: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
            prefs[USERNAME_KEY] = username
            prefs[USER_ROLE_KEY] = role
        }
    }

    suspend fun clearAuth() {
        context.dataStore.edit { prefs ->
            prefs.remove(TOKEN_KEY)
            prefs.remove(USERNAME_KEY)
            prefs.remove(USER_ROLE_KEY)
        }
    }

    fun isLoggedIn(): Flow<Boolean> = context.dataStore.data.map {
        !it[TOKEN_KEY].isNullOrEmpty()
    }

    fun getThemeMode(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY] ?: run {
            // migrate from old boolean dark_mode key
            val oldDark = prefs[DARK_MODE_KEY] ?: false
            if (oldDark) THEME_DARK else THEME_SYSTEM
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode
            prefs.remove(DARK_MODE_KEY)
        }
    }

    fun getDarkMode(): Flow<Boolean> = context.dataStore.data.map {
        it[DARK_MODE_KEY] ?: false
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DARK_MODE_KEY] = enabled
        }
    }
}
