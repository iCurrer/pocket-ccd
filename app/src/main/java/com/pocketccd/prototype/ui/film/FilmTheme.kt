package com.pocketccd.prototype.ui.film

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
    /**
     * 说明文字（≥13sp）。白 60%，在 [appBg] 上约 7.2:1。
     * 不要用于取景器内文字 —— 那里压着真实画面，见 [FilmPalette.ink]。
     */
    val textSecondary: Color,
    /**
     * 标签 / 单位（≥11sp）。白 50%，约 5.3:1。
     * 不要用于取景器内文字，也不要落在胶卷库的浅色金属面板上（白字浅底等于消失）。
     */
    val textTertiary: Color,
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

/**
 * 字阶。**字号下限 11sp**，只有 [microPrint] 可以低于它，且只用于装饰性印刷仿真。
 *
 * 这条规则是 2026-10-21 的密度整改定下的（见
 * `docs/reviews/2026-10-21-ui-density-change-spec.md`）：此前 25 处裸 `fontSize`
 * 把设置项说明、EXIF 参数、文件大小压到 6.5–9.5sp，且恰好配了最淡的颜色
 * （`inkGhost` 白 25% 在近黑底上只有 2.17:1，连 WCAG 的 3:1 都不到）。
 * 不要再写裸字号，加一条单测盯着（`NoBareFontSizeTest`）。
 */
@Immutable
data class FilmType(
    /** 页面标题。 */
    val sheetTitle: TextUnit = 16.sp,
    /** 行标题 / 强调信息。 */
    val optionTitle: TextUnit = 15.sp,
    /** 参数值、设置项右侧值、计数。 */
    val value: TextUnit = 14.sp,
    /** 说明文案。 */
    val body: TextUnit = 13.sp,
    /** 等宽标签。 */
    val mono: TextUnit = 11.sp,
    /** 等宽小标签。不得低于 11sp。 */
    val monoSmall: TextUnit = 11.sp,
    /** 通用标签 / 单位。 */
    val label: TextUnit = 11.sp,
    /** 只允许用于胶卷筒 / 包装上的仿真印刷字，**不得承载信息**。 */
    val microPrint: TextUnit = 9.sp,
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
        textSecondary = Color(0xFFFFFFFF).copy(alpha = 0.60f),
        textTertiary = Color(0xFFFFFFFF).copy(alpha = 0.50f),
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
