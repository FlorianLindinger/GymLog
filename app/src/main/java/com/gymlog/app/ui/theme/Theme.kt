package com.gymlog.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.gymlog.app.data.AccentColor

private fun darkColors(accent: AccentColor) = darkColorScheme(
    primary = if (accent == AccentColor.BLUE) Color(0xFF64B5F6) else Color(0xFFEF5350),
    onPrimary = if (accent == AccentColor.BLUE) Color(0xFF071923) else Color.White,
    primaryContainer = if (accent == AccentColor.BLUE) Color(0xFF123A57) else Color(0xFF761313),
    onPrimaryContainer = if (accent == AccentColor.BLUE) Color(0xFFD5ECFF) else Color(0xFFFFDAD6),
    secondary = if (accent == AccentColor.BLUE) Color(0xFF90CAF9) else Color(0xFFFF6B63),
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF181818),
    surfaceVariant = Color(0xFF2B2B2B),
    error = Color(0xFFFF6B6B),
)

@Composable
fun GymLogTheme(accentColor: AccentColor = AccentColor.BLUE, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColors(accentColor),
        typography = MaterialTheme.typography,
        content = content,
    )
}
