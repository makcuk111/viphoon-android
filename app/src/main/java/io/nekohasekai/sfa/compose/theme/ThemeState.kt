package io.nekohasekai.sfa.compose.theme

import androidx.compose.runtime.mutableStateOf
import io.nekohasekai.sfa.database.Settings

// Выбранная тема приложения; хранится в настройках, меняется мгновенно.
object ThemeState {
    val mode = mutableStateOf(Settings.viphoonAppTheme)

    fun set(value: String) {
        mode.value = value
        Settings.viphoonAppTheme = value
    }
}
