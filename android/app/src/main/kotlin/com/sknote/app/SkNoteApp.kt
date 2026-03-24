package com.sknote.app

import android.app.Application
import android.app.UiModeManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.local.TokenManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SkNoteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)

        runBlocking {
            ApiClient.getTokenManager().preloadToken()
            val mode = ApiClient.getTokenManager().getThemeMode().first()
            applyThemeMode(this@SkNoteApp, mode)
        }
    }

    companion object {
        fun applyThemeMode(application: Application, mode: String) {
            AppCompatDelegate.setDefaultNightMode(themeModeToNightMode(mode))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                application.getSystemService(UiModeManager::class.java)
                    ?.setApplicationNightMode(themeModeToSystemNightMode(mode))
            }
        }

        fun themeModeToNightMode(mode: String): Int = when (mode) {
            TokenManager.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            TokenManager.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

        private fun themeModeToSystemNightMode(mode: String): Int = when (mode) {
            TokenManager.THEME_DARK -> UiModeManager.MODE_NIGHT_YES
            TokenManager.THEME_LIGHT -> UiModeManager.MODE_NIGHT_NO
            else -> UiModeManager.MODE_NIGHT_AUTO
        }

        fun themeModeLabel(mode: String): String = when (mode) {
            TokenManager.THEME_DARK -> "深色模式"
            TokenManager.THEME_LIGHT -> "浅色模式"
            else -> "跟随系统"
        }
    }
}
