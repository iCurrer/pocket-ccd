package com.pocketccd.prototype.ui.film

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Semantic design tokens for the film-dark UI (M9 / D-038).
 *
 * Naming rule: tokens describe the **role**, never the material. The previous
 * theme named colours `RetroRed` / `Butter` / `Cream`, which meant every new
 * skin made the names lie. `bodyBase` / `controlAccent` survive a re-skin;
 * `RetroRed` does not.
 */
@Immutable
data class FilmPalette(
    val appBg: Color,
    val windowBg: Color,
    val sheetBg: Color,
    val viewfinderBg: Color,
    val textPrimary: Color,
    val accent: Color,
    val onAccent: Color,
    /** Camera-overlay text. Reads on top of arbitrary imagery, so always white. */
    val ink: Color,
    val inkStrong: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val inkGhost: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val controlFill: Color,
    val chipFill: Color,
    val error: Color,
    /** Four-stop brushed metal for the viewfinder bezel (160deg, per spec §2.2). */
    val bezelMetal: List<Color>,
    /** Sweep gradient for the shutter ring. */
    val shutterRing: List<Color>,
    val shutterFace: List<Color>,
    val shutterDot: List<Color>,
)

@Immutable
data class FilmMetrics(
    val gutter: Dp = 12.dp,
    val gutterWide: Dp = 16.dp,
    val gutterWidest: Dp = 24.dp,
    val topBarHeight: Dp = 48.dp,
    val controlSize: Dp = 36.dp,
    /** Spec §2.4: visual size may be small, the touch target may not. */
    val touchTarget: Dp = 48.dp,
    val viewfinderRadius: Dp = 20.dp,
    val bezel: Dp = 3.dp,
    val chipRadius: Dp = 3.dp,
    val shutter: Dp = 78.dp,
    val thumbnail: Dp = 50.dp,
    val sheetRadius: Dp = 26.dp,
    val hairline: Dp = 1.dp,
    val cornerArm: Dp = 20.dp,
    val cornerInsetX: Dp = 20.dp,
    val cornerInsetY: Dp = 56.dp,
    val reticle: Dp = 58.dp,
)

@Immutable
data class FilmType(
    val sheetTitle: androidx.compose.ui.unit.TextUnit = 16.sp,
    val optionTitle: androidx.compose.ui.unit.TextUnit = 13.sp,
    val body: androidx.compose.ui.unit.TextUnit = 12.sp,
    val mono: androidx.compose.ui.unit.TextUnit = 10.sp,
    val monoSmall: androidx.compose.ui.unit.TextUnit = 9.sp,
    val label: androidx.compose.ui.unit.TextUnit = 10.sp,
)

@Immutable
data class FilmThemeSpec(
    val palette: FilmPalette,
    val metrics: FilmMetrics = FilmMetrics(),
    val type: FilmType = FilmType(),
)

/** Design baseline: `docs/reference/film-dark-home-2026-10-19.html`. */
val FilmDarkSpec = FilmThemeSpec(
    palette = FilmPalette(
        appBg = Color(0xFF0B0C0E),
        windowBg = Color(0xFF050506),
        sheetBg = Color(0xFF14161A),
        viewfinderBg = Color(0xFF061021),
        textPrimary = Color(0xFFEDEFF2),
        accent = Color(0xFFE7B37A),
        onAccent = Color(0xFF141414),
        ink = Color(0xFFFFFFFF).copy(alpha = 0.75f),
        inkStrong = Color(0xFFFFFFFF).copy(alpha = 0.90f),
        inkMuted = Color(0xFFFFFFFF).copy(alpha = 0.45f),
        inkFaint = Color(0xFFFFFFFF).copy(alpha = 0.35f),
        inkGhost = Color(0xFFFFFFFF).copy(alpha = 0.25f),
        hairline = Color(0xFFFFFFFF).copy(alpha = 0.10f),
        hairlineStrong = Color(0xFFFFFFFF).copy(alpha = 0.15f),
        controlFill = Color(0xFFFFFFFF).copy(alpha = 0.04f),
        chipFill = Color(0xFF000000).copy(alpha = 0.32f),
        error = Color(0xFFB84A3A),
        bezelMetal = listOf(
            Color(0xFF4B4F55),
            Color(0xFF1B1D20),
            Color(0xFF2D3136),
            Color(0xFF141619),
        ),
        // Deeper darks than the design's literal values: at 3x display density the
        // ring is only ~13 physical px wide, and the original #8D939A/#767D84 were
        // too close to the face colour to survive downscaling — the shutter read as
        // one white blob on the first real-device screenshot.
        shutterRing = listOf(
            Color(0xFFF4F6F8),
            Color(0xFF5E656C),
            Color(0xFFE9EDF0),
            Color(0xFF474E55),
            Color(0xFFE2E6E9),
        ),
        shutterFace = listOf(
            Color(0xFFFFFFFF),
            Color(0xFFE2E6E9),
            Color(0xFFC2C7CD),
        ),
        shutterDot = listOf(
            Color(0xFFE8705F),
            Color(0xFFB5382C),
        ),
    ),
)

val LocalFilmTheme = staticCompositionLocalOf { FilmDarkSpec }

@Composable
fun FilmTheme(
    spec: FilmThemeSpec = FilmDarkSpec,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFilmTheme provides spec, content = content)
}
