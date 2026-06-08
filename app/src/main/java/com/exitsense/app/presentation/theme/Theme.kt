package com.exitsense.app.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = PrimaryBlueLight,
    secondary = SecondaryTeal,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = SecondaryTealLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    error = ErrorRed
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueLight,
    onPrimary = OnSurfaceLight,
    primaryContainer = PrimaryBlueDark,
    secondary = SecondaryTealLight,
    onSecondary = OnSurfaceLight,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    error = ErrorRed
)

@Composable
fun ExitSenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
