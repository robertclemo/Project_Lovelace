package com.roanokeresistance.lovelace.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = ResistanceBlue,
    secondary = ResistanceBlueDark,
    background = NightBackground,
    surface = NightSurface
)

private val LightColors = lightColorScheme(
    primary = ResistanceBlue,
    secondary = ResistanceBlueDark,
    background = DayBackground,
    surface = DaySurface
)

@Composable
fun LovelaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
