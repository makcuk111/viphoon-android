package io.nekohasekai.sfa.compose.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

// Навигация только из состояния RESUMED. Если дёргать navigate()/navigateUp()
// во время незавершённого перехода (быстрый двойной тап, тап во время
// анимации), NavHost оставляет старый экран скомпонованным поверх нового —
// невидимый, но кликабельный слой («призрачные» настройки).
private fun NavController.isResumed(): Boolean =
    currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

fun NavController.safeNavigateUp(): Boolean {
    if (!isResumed()) return false
    return navigateUp()
}

fun NavController.safeNavigate(route: String, builder: NavOptionsBuilder.() -> Unit = { launchSingleTop = true }) {
    if (!isResumed()) return
    navigate(route, builder)
}
