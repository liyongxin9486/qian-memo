package com.example.beiwang.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主题模式：跟随系统 / 浅色 / 深色。
 * 持久化到 SharedPreferences（与登录态使用同一份 beiwang_prefs）。
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    val label: String
        get() = when (this) {
            SYSTEM -> "跟随系统"
            LIGHT -> "浅色"
            DARK -> "深色"
        }
}

object ThemeManager {
    private const val PREFS_NAME = "beiwang_prefs"
    private const val KEY_THEME = "theme_mode"

    private var appContext: Context? = null
    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        val saved = appContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(KEY_THEME, ThemeMode.SYSTEM.name)
            ?: ThemeMode.SYSTEM.name
        _mode.value = runCatching { ThemeMode.valueOf(saved) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setMode(mode: ThemeMode) {
        _mode.value = mode
        appContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(KEY_THEME, mode.name)
            ?.apply()
    }

    @Composable
    fun collectAsState(): State<ThemeMode> = mode.collectAsState()
}
