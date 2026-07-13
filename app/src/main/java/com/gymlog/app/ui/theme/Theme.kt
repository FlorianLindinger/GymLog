package com.gymlog.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B52),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F1DA),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF4C6359),
    background = Color(0xFFF7F9F5),
    surface = Color(0xFFF7F9F5),
    surfaceVariant = Color(0xFFE1EAE4),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DD5BE),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF00513C),
    secondary = Color(0xFFB4CCC0),
    background = Color(0xFF101512),
    surface = Color(0xFF101512),
    surfaceVariant = Color(0xFF3F4944),
)

@Composable
fun GymLogTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
