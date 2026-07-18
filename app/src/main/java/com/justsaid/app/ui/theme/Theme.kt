package com.justsaid.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = DeepBlue,
    onPrimary = OnPrimaryLight,
    secondary = DeepBlueDark,
    onSecondary = OnPrimaryLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    error = ErrorLight,
    onError = OnErrorLight,
)

private val DarkColors = darkColorScheme(
    primary = LightBlue,
    onPrimary = OnPrimaryDark,
    secondary = LightBlue,
    onSecondary = OnPrimaryDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    error = ErrorDark,
    onError = OnErrorDark,
)

/**
 * App theme: high-contrast colors + large typography. Deliberately no dynamic
 * (wallpaper) color, to keep contrast predictable for accessibility.
 */
@Composable
fun JustSaidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = JustSaidTypography,
        content = content,
    )
}
