package com.pocketccd.prototype.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.pocketccd.prototype.ui.film.FilmDarkSpec

/**
 * Material 兜底色板。真正的页面配色走 [FilmDarkSpec]（`LocalFilmTheme`），
 * 这里只为 Compose 内建的 M3 组件（对话框、Snackbar、文本选择柄）提供一套
 * **与近黑界面同调**的兜底值。
 *
 * 为什么不直接删掉这层：`MaterialTheme` 一旦缺席，M3 组件会退回框架默认的
 * 浅色方案，在近黑界面上闪出白底或珊瑚红。所以它留着，但改成暗色。
 * 曾经的 `lightColorScheme` + 旧红色/米色令牌就是「闪一下红」的第二个来源。
 */
private val Film = FilmDarkSpec.palette

private val PocketCcdColorsScheme = darkColorScheme(
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
