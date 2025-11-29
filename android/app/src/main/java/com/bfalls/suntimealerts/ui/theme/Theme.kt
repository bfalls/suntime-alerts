package com.bfalls.suntimealerts.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SunriseAccent,
    secondary = SunsetAccent,
    background = SurfacePrimary,
    surface = SurfaceSecondary,
    onPrimary = DeepNavy,
    onSecondary = DeepNavy,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    tertiary = OutlineMuted,
    outline = OutlineMuted
)

private val LightColorScheme = lightColorScheme(
    primary = SunriseAccent,
    secondary = SunsetAccent,
    background = SurfacePrimary,
    surface = SurfaceSecondary,
    onPrimary = DeepNavy,
    onSecondary = DeepNavy,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    tertiary = OutlineMuted,
    outline = OutlineMuted
)

@Composable
fun SuntimeAlertsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
