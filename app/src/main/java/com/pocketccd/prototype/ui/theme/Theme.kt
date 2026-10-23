package com.pocketccd.prototype.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.pocketccd.prototype.ui.film.FilmDarkSpec
import com.pocketccd.prototype.ui.film.FilmPaperSpec

/**
 * Material 兜底色板。真正的页面配色走 [com.pocketccd.prototype.ui.film.LocalFilmTheme]，
 * 这里只为 Compose 内建的 M3 组件（对话框、Snackbar、文本选择柄）提供一套
 * **与当前页面材质同调**的兜底值。
 *
 * D-063「白纸档案」之后 App 有两套材质：取景页是黑闸门（暗），其余四页是相纸（亮）。
 * 所以这个 Theme 增加 `darkTheme` 参数，由 AppRoot 按目的地选择；不再假定全 App 近黑。
 */
private val Film = FilmDarkSpec.palette
private val Paper = FilmPaperSpec.palette

private val PocketCcdDarkScheme = darkColorScheme(
    primary = Film.accent,
    onPrimary = Film.onAccent,
    primaryContainer = Film.sheetBg,
    onPrimaryContainer = Film.textPrimary,
    background = Film.appBg,
    onBackground = Film.textPrimary,
    surface = Film.sheetBg,
    onSurface = Film.textPrimary,
    surfaceVariant = Film.controlFill,
    onSurfaceVariant = Film.textPrimary.copy(alpha = 0.72f),
    outline = Film.hairlineStrong,
    outlineVariant = Film.hairline,
    error = Film.error,
    onError = Film.textPrimary,
)

private val PocketCcdPaperScheme = lightColorScheme(
    primary = Paper.accent,
    onPrimary = Paper.onAccent,
    primaryContainer = Paper.chipFill,
    onPrimaryContainer = Paper.textPrimary,
    background = Paper.appBg,
    onBackground = Paper.textPrimary,
    surface = Paper.sheetBg,
    onSurface = Paper.textPrimary,
    surfaceVariant = Paper.controlFill,
    onSurfaceVariant = Paper.textSecondary,
    outline = Paper.hairlineStrong,
    outlineVariant = Paper.hairline,
    error = Paper.error,
    onError = Paper.onAccent,
)

private val PocketCcdShapes = Shapes(
    extraSmall = RoundedCornerShape(PocketCcdRadius.xs),
    small = RoundedCornerShape(PocketCcdRadius.sm),
    medium = RoundedCornerShape(PocketCcdRadius.md),
    large = RoundedCornerShape(PocketCcdRadius.lg),
    extraLarge = RoundedCornerShape(PocketCcdRadius.xl),
)

@Composable
fun PocketCcdTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PocketCcdDarkScheme else PocketCcdPaperScheme,
        typography = PocketCcdTypography,
        shapes = PocketCcdShapes,
        content = content,
    )
}
