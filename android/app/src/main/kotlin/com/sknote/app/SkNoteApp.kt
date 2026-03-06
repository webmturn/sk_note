package com.sknote.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.local.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SkNoteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)

        CoroutineScope(Dispatchers.Main).launch {
            val mode = ApiClient.getTokenManager().getThemeMode().first()
            AppCompatDelegate.setDefaultNightMode(themeModeToNightMode(mode))
        }
    }

    companion object {
        fun themeModeToNightMode(mode: String): Int = when (mode) {
            TokenManager.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            TokenManager.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

        fun themeModeLabel(mode: String): String = when (mode) {
            TokenManager.THEME_DARK -> "深色模式"
            TokenManager.THEME_LIGHT -> "浅色模式"
            else -> "跟随系统"
        }
    }
}
