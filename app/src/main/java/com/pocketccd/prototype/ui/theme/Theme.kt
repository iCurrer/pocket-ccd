package com.pocketccd.prototype.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PocketCcdColorsScheme = lightColorScheme(
    primary = PocketCcdColors.Accent,
    onPrimary = PocketCcdColors.OnAccent,
    primaryContainer = PocketCcdColors.AccentSoft,
    onPrimaryContainer = PocketCcdColors.TextPrimary,
    background = PocketCcdColors.Background,
    onBackground = PocketCcdColors.TextPrimary,
    surface = PocketCcdColors.Surface,
    onSurface = PocketCcdColors.TextPrimary,
    surfaceVariant = PocketCcdColors.SurfaceHigh,
    onSurfaceVariant = PocketCcdColors.TextSecondary,
    outline = PocketCcdColors.Outline,
    outlineVariant = PocketCcdColors.OutlineSoft,
    error = PocketCcdColors.Error,
    onError = PocketCcdColors.OnError,
)

private val PocketCcdShapes = Shapes(
    extraSmall = RoundedCornerShape(PocketCcdRadius.xs),
    small = RoundedCornerShape(PocketCcdRadius.sm),
    medium = RoundedCornerShape(PocketCcdRadius.md),
    large = RoundedCornerShape(PocketCcdRadius.lg),
    extraLarge = RoundedCornerShape(PocketCcdRadius.xl),
)

@Composable
fun PocketCcdTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PocketCcdColorsScheme,
        typography = PocketCcdTypography,
        shapes = PocketCcdShapes,
        content = content,
    )
}
