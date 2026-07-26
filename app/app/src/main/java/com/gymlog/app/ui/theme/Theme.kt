package com.gymlog.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

private fun darkColors(accentArgb: Long): androidx.compose.material3.ColorScheme {
    val accent = Color(accentArgb)
    return darkColorScheme(
        primary = accent,
        onPrimary = if (accent.luminance() > .45f) Color(0xFF071318) else Color.White,
        primaryContainer = lerp(Color.Black, accent, .34f),
        onPrimaryContainer = lerp(Color.White, accent, .18f),
        secondary = lerp(Color.White, accent, .68f),
        background = Color(0xFF0F0F0F),
        surface = Color(0xFF181818),
        surfaceVariant = Color(0xFF2B2B2B),
        error = Color(0xFFFF6B6B),
    )
}

@Composable
fun GymLogTheme(accentColor: Long = 0xFF64B5F6, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColors(accentColor),
        typography = MaterialTheme.typography,
        content = content,
    )
}
