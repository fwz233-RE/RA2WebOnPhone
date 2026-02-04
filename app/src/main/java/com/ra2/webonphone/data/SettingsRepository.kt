package com.ra2.webonphone.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(val code: String, val displayName: String) {
    SYSTEM("system", "跟随系统"),
    SIMPLIFIED_CHINESE("zh-CN", "简体中文"),
    TRADITIONAL_CHINESE("zh-TW", "繁體中文"),
    ENGLISH("en", "English"),
    JAPANESE("ja", "日本語")
}

enum class ControlMethod(val value: String) {
    UNKNOWN("unknown"),
    JOYSTICK("joystick"),
    KEYBOARD("keyboard")
}

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    private val _showSystemStats = MutableStateFlow(prefs.getBoolean(KEY_SHOW_SYSTEM_STATS, true))
    val showSystemStats: StateFlow<Boolean> = _showSystemStats.asStateFlow()

    private val _appLanguage = MutableStateFlow(
        AppLanguage.entries.find { it.code == prefs.getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.code) }
            ?: AppLanguage.SYSTEM
    )
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _controlMethod = MutableStateFlow(
        ControlMethod.entries.find { it.value == prefs.getString(KEY_CONTROL_METHOD, ControlMethod.UNKNOWN.value) }
            ?: ControlMethod.UNKNOWN
    )
    val controlMethod: StateFlow<ControlMethod> = _controlMethod.asStateFlow()

    companion object {
        private const val PREFS_NAME = "ra2_settings_prefs"
        private const val KEY_SHOW_SYSTEM_STATS = "show_system_stats"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_CONTROL_METHOD = "control_method"
    }

    fun setShowSystemStats(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SYSTEM_STATS, show).apply()
        _showSystemStats.value = show
    }

    fun getShowSystemStats(): Boolean {
        return prefs.getBoolean(KEY_SHOW_SYSTEM_STATS, true)
    }

    fun setAppLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language.code).apply()
        _appLanguage.value = language
    }

    fun getAppLanguage(): AppLanguage {
        val code = prefs.getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.code)
        return AppLanguage.entries.find { it.code == code } ?: AppLanguage.SYSTEM
    }

    fun setControlMethod(method: ControlMethod) {
        prefs.edit().putString(KEY_CONTROL_METHOD, method.value).apply()
        _controlMethod.value = method
    }

    fun getControlMethod(): ControlMethod {
        val value = prefs.getString(KEY_CONTROL_METHOD, ControlMethod.UNKNOWN.value)
        return ControlMethod.entries.find { it.value == value } ?: ControlMethod.UNKNOWN
    }
}
