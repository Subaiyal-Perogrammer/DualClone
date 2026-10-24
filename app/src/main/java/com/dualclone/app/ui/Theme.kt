package com.dualclone.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// DualClone's own palette: deep violet on near-black, light theme kept bright.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9D8FFF),
    onPrimary = Color(0xFF1B1440),
    secondary = Color(0xFFB7AEFF),
    background = Color(0xFF0B0B12),
    onBackground = Color(0xFFF2F0FA),
    surface = Color(0xFF171724),
    onSurface = Color(0xFFF2F0FA),
    surfaceVariant = Color(0xFF23232F),
    onSurfaceVariant = Color(0xFFC9C4D6),
    error = Color(0xFFFF8A80)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4BD6),
    onPrimary = Color.White,
    secondary = Color(0xFF7C6CF0),
    background = Color(0xFFF7F5FC),
    onBackground = Color(0xFF191423),
    surface = Color.White,
    onSurface = Color(0xFF191423),
    surfaceVariant = Color(0xFFE9E5F5),
    onSurfaceVariant = Color(0xFF4B4560),
    error = Color(0xFFB3261E)
)

@Composable
fun DualCloneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
