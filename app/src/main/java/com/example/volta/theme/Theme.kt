package com.example.volta.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = VoltaBlue,
    onPrimary = Color.White,
    primaryContainer = VoltaBlueDark,
    onPrimaryContainer = Color.White,
    secondary = VoltaYellow,
    onSecondary = VoltaNavy,
    background = VoltaDarkBg,
    onBackground = VoltaTextDark,
    surface = VoltaDarkCard,
    onSurface = VoltaTextDark,
    surfaceVariant = VoltaDarkBorder,
    onSurfaceVariant = VoltaMutedDark,
    outline = VoltaDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = VoltaBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = VoltaBlueDark,
    secondary = VoltaYellow,
    onSecondary = VoltaNavy,
    background = VoltaLightBg,
    onBackground = VoltaTextLight,
    surface = VoltaLightCard,
    onSurface = VoltaTextLight,
    surfaceVariant = VoltaLightBorder,
    onSurfaceVariant = VoltaMutedLight,
    outline = VoltaLightBorder
)

@Composable
fun VoltaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
