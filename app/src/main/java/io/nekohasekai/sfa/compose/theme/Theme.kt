package io.nekohasekai.sfa.compose.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme =
    darkColorScheme(
        primary = ViphoonNeon,
        onPrimary = ViphoonBgDark,
        primaryContainer = ViphoonNeonDark,
        onPrimaryContainer = Color(0xFFDCFFEC),
        secondary = ViphoonNeonLight,
        onSecondary = ViphoonBgDark,
        tertiary = LogBlue,
        background = ViphoonBgDark,
        surface = ViphoonBgDark,
        surfaceVariant = ViphoonSurfaceDark,
        surfaceContainer = ViphoonSurfaceDark,
        surfaceContainerLow = Color(0xFF121B1E),
        surfaceContainerHigh = Color(0xFF1B262A),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = ViphoonNeonDark,
        secondary = ViphoonNeon,
        tertiary = LogBlue,
    )

@Composable
fun SFATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Фирменная тема ViPhooN вместо Material You: акцент всегда неон-зелёный
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= 31 -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
