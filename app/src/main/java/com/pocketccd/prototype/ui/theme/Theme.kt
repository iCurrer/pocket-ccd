package com.pocketccd.prototype.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Temporary prototype palette. Values are structural placeholders until the
// visual prototype review (T1-004/T1-014); do not treat them as final design tokens.
private val PocketCcdDarkColors = darkColorScheme(
    primary = Color(0xFFFF5A36),
    onPrimary = Color(0xFF2A0B04),
    background = Color(0xFF0E0E10),
    onBackground = Color(0xFFF2F0EC),
    surface = Color(0xFF1A1A1D),
    onSurface = Color(0xFFF2F0EC),
    surfaceVariant = Color(0xFF26262A),
    onSurfaceVariant = Color(0xFFC9C5BE),
    outline = Color(0xFF6F6A62),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun PocketCcdTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PocketCcdDarkColors,
        typography = Typography(),
        content = content,
    )
}
