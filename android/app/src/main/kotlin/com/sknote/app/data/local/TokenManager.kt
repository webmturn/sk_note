package com.sknote.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sk_note_prefs")

class TokenManager(private val context: Context) {

    private val appContext = context.applicationContext

    private val authPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            AUTH_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val tokenFlow = MutableStateFlow<String?>(null)
    private val usernameFlow = MutableStateFlow<String?>(null)
    private val nicknameFlow = MutableStateFlow<String?>(null)
    private val userRoleFlow = MutableStateFlow<String?>(null)
    private val userIdFlow = MutableStateFlow<Long?>(null)
    private val createdAtFlow = MutableStateFlow<String?>(null)

    @Volatile
    var cachedToken: String? = null
        private set

    suspend fun preloadToken() {
        migrateLegacyAuthIfNeeded()
        refreshAuthCache()
    }

    companion object {
        private const val AUTH_PREFS_NAME = "sk_note_secure_auth"
        private const val TOKEN_KEY = "auth_token"
        private const val USERNAME_KEY = "username"
        private const val NICKNAME_KEY = "nickname"
        private const val USER_ROLE_KEY = "user_role"
        private const val USER_ID_KEY = "user_id"
        private const val CREATED_AT_KEY = "created_at"
        private const val LEGACY_MIGRATED_KEY = "legacy_migrated"

        private val LEGACY_TOKEN_KEY = stringPreferencesKey("auth_token")
        private val LEGACY_USERNAME_KEY = stringPreferencesKey("username")
        private val LEGACY_NICKNAME_KEY = stringPreferencesKey("nickname")
        private val LEGACY_USER_ROLE_KEY = stringPreferencesKey("user_role")
        private val LEGACY_USER_ID_KEY = longPreferencesKey("user_id")
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }

    private fun refreshAuthCache() {
        val token = authPrefs.getString(TOKEN_KEY, null)
        val username = authPrefs.getString(USERNAME_KEY, null)
        val nickname = authPrefs.getString(NICKNAME_KEY, null)
        val role = authPrefs.getString(USER_ROLE_KEY, null)
        val userId = if (authPrefs.contains(USER_ID_KEY)) authPrefs.getLong(USER_ID_KEY, 0L).takeIf { it > 0L } else null
        val createdAt = authPrefs.getString(CREATED_AT_KEY, null)

        cachedToken = token
        tokenFlow.value = token
        usernameFlow.value = username
        nicknameFlow.value = nickname
        userRoleFlow.value = role
        userIdFlow.value = userId
        createdAtFlow.value = createdAt
    }

    private suspend fun migrateLegacyAuthIfNeeded() {
        if (authPrefs.getBoolean(LEGACY_MIGRATED_KEY, false)) return

        val legacy = appContext.dataStore.data.first()
        val token = legacy[LEGACY_TOKEN_KEY]
        if (token.isNullOrEmpty()) {
            authPrefs.edit().putBoolean(LEGACY_MIGRATED_KEY, true).apply()
            return
        }

        authPrefs.edit()
            .putBoolean(LEGACY_MIGRATED_KEY, true)
            .putString(TOKEN_KEY, token)
            .putString(USERNAME_KEY, legacy[LEGACY_USERNAME_KEY])
            .putString(NICKNAME_KEY, legacy[LEGACY_NICKNAME_KEY])
            .putString(USER_ROLE_KEY, legacy[LEGACY_USER_ROLE_KEY])
            .apply {
                val userId = legacy[LEGACY_USER_ID_KEY]
                if (userId != null && userId > 0L) {
                    putLong(USER_ID_KEY, userId)
                } else {
                    remove(USER_ID_KEY)
                }
            }
            .apply()

        appContext.dataStore.edit { prefs ->
            prefs.remove(LEGACY_TOKEN_KEY)
            prefs.remove(LEGACY_USERNAME_KEY)
            prefs.remove(LEGACY_NICKNAME_KEY)
            prefs.remove(LEGACY_USER_ROLE_KEY)
            prefs.remove(LEGACY_USER_ID_KEY)
        }
    }

    fun getToken(): Flow<String?> = tokenFlow
    fun getUsername(): Flow<String?> = usernameFlow
    fun getNickname(): Flow<String?> = nicknameFlow
    fun getUserRole(): Flow<String?> = userRoleFlow
    fun getUserId(): Flow<Long?> = userIdFlow
    fun getCreatedAt(): Flow<String?> = createdAtFlow

    val cachedUsername: String? get() = usernameFlow.value
    val cachedNickname: String? get() = nicknameFlow.value
    val cachedRole: String? get() = userRoleFlow.value
    val cachedUserId: Long? get() = userIdFlow.value
    val cachedCreatedAt: String? get() = createdAtFlow.value

    suspend fun saveAuth(token: String, username: String, role: String, userId: Long = 0L, nickname: String = "", createdAt: String? = null) {
        authPrefs.edit()
            .putString(TOKEN_KEY, token)
            .putString(USERNAME_KEY, username)
            .putString(NICKNAME_KEY, nickname.ifEmpty { username })
            .putString(USER_ROLE_KEY, role)
            .apply {
                if (userId > 0L) {
                    putLong(USER_ID_KEY, userId)
                } else {
                    remove(USER_ID_KEY)
                }
                if (!createdAt.isNullOrEmpty()) {
                    putString(CREATED_AT_KEY, createdAt)
                }
            }
            .apply()
        refreshAuthCache()
    }

    suspend fun updateUsername(username: String) {
        authPrefs.edit().putString(USERNAME_KEY, username).apply()
        refreshAuthCache()
    }

    suspend fun updateNickname(nickname: String) {
        authPrefs.edit().putString(NICKNAME_KEY, nickname).apply()
        refreshAuthCache()
    }

    suspend fun updateUserRole(role: String) {
        authPrefs.edit().putString(USER_ROLE_KEY, role).apply()
        refreshAuthCache()
    }

    suspend fun updateCreatedAt(createdAt: String) {
        authPrefs.edit().putString(CREATED_AT_KEY, createdAt).apply()
        refreshAuthCache()
    }

    suspend fun updateCurrentUser(userId: Long, username: String, nickname: String, role: String) {
        authPrefs.edit()
            .putString(USERNAME_KEY, username)
            .putString(NICKNAME_KEY, nickname.ifEmpty { username })
            .putString(USER_ROLE_KEY, role)
            .apply {
                if (userId > 0L) {
                    putLong(USER_ID_KEY, userId)
                } else {
                    remove(USER_ID_KEY)
                }
            }
            .apply()
        refreshAuthCache()
    }

    suspend fun clearAuth() {
        authPrefs.edit()
            .remove(TOKEN_KEY)
            .remove(USERNAME_KEY)
            .remove(NICKNAME_KEY)
            .remove(USER_ROLE_KEY)
            .remove(USER_ID_KEY)
            .remove(CREATED_AT_KEY)
            .apply()
        appContext.dataStore.edit { prefs ->
            prefs.remove(LEGACY_TOKEN_KEY)
            prefs.remove(LEGACY_USERNAME_KEY)
            prefs.remove(LEGACY_NICKNAME_KEY)
            prefs.remove(LEGACY_USER_ROLE_KEY)
            prefs.remove(LEGACY_USER_ID_KEY)
        }
        refreshAuthCache()
    }

    fun isLoggedIn(): Flow<Boolean> = tokenFlow.map { !it.isNullOrEmpty() }

    fun getThemeMode(): Flow<String> = appContext.dataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY] ?: run {
            val oldDark = prefs[DARK_MODE_KEY] ?: false
            if (oldDark) THEME_DARK else THEME_SYSTEM
        }
    }

    suspend fun setThemeMode(mode: String) {
        appContext.dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode
            prefs.remove(DARK_MODE_KEY)
        }
    }

    fun getDarkMode(): Flow<Boolean> = appContext.dataStore.data.map {
        it[DARK_MODE_KEY] ?: false
    }

    suspend fun setDarkMode(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[DARK_MODE_KEY] = enabled
        }
    }
}
