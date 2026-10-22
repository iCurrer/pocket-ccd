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
    /**
     * 机身底盖的四停竖直渐变（顶受光 → 底沉下去）。D-051。
     *
     * 比 [bezelMetal] 更暗、更中性：bezel 是围着画面的窄条，需要一点亮度才看得出
     * 是金属；底盖是一大片，同样亮度就成了灰色塑料板。
     */
    val panelMetal: List<Color>,
    /**
     * 凹槽 / 内嵌键的底。
     *
     * 必须比 [panelMetal] 最暗一停还要暗 —— 凹进去的地方收不到光。用面板色画凹槽，
     * 看着像贴了张圆纸片，这是拟物最容易翻车的一步。
     */
    val wellFill: Color,
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
    val topBarHeight: Dp = 56.dp,
    val controlSize: Dp = 36.dp,
    /** Spec §2.4: visual size may be small, the touch target may not. */
    val touchTarget: Dp = 48.dp,
    val viewfinderRadius: Dp = 16.dp,
    val bezel: Dp = 0.dp,
    val chipRadius: Dp = 3.dp,
    /** 视觉直径（含滚花环）。凹槽尺寸 = shutter + panelWell×2。 */
    val shutter: Dp = 72.dp,
    /** 内嵌副键的视觉直径。热区仍是 [touchTarget]。 */
    val panelKey: Dp = 34.dp,
    /** 快门凹槽比快门本体外扩多少（单边）。8dp 让凹槽读成"坑"而不是描边。 */
    val panelWell: Dp = 8.dp,
    val thumbnail: Dp = 50.dp,
    val sheetRadius: Dp = 26.dp,
    val hairline: Dp = 1.dp,
    /** 角标臂长 / 内缩。2026-10-23：变细变短，像取景器刻线而不是 UI 括号。 */
    val cornerArm: Dp = 18.dp,
    val cornerInsetX: Dp = 18.dp,
    val cornerInsetY: Dp = 18.dp,
    val reticle: Dp = 24.dp,
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
    /** 取景器右缘档位轨（变焦 / EV）的数字。比 label 大一档，站得住。 */
    val rail: TextUnit = 13.sp,
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
        windowBg = Color(0xFF030304),
        sheetBg = Color(0xFF121417),
        viewfinderBg = Color(0xFF061021),
        textPrimary = Color(0xFFEDEFF2),
        textSecondary = Color(0xFFFFFFFF).copy(alpha = 0.64f),
        textTertiary = Color(0xFFFFFFFF).copy(alpha = 0.46f),
        accent = Color(0xFFE7B37A),
        onAccent = Color(0xFF161310),
        ink = Color(0xFFFFFFFF).copy(alpha = 0.78f),
        inkStrong = Color(0xFFFFFFFF).copy(alpha = 0.92f),
        inkMuted = Color(0xFFFFFFFF).copy(alpha = 0.50f),
        inkFaint = Color(0xFFFFFFFF).copy(alpha = 0.34f),
        inkGhost = Color(0xFFFFFFFF).copy(alpha = 0.22f),
        hairline = Color(0xFFFFFFFF).copy(alpha = 0.07f),
        hairlineStrong = Color(0xFFFFFFFF).copy(alpha = 0.14f),
        controlFill = Color(0xFFFFFFFF).copy(alpha = 0.05f),
        chipFill = Color(0xFF000000).copy(alpha = 0.28f),
        error = Color(0xFFB84A3A),
        bezelMetal = listOf(
            Color(0xFF2A2D31),
            Color(0xFF141619),
            Color(0xFF1E2124),
            Color(0xFF0E1012),
        ),
        // 2026-10-23：底盖不再是"浅一档的灰板"，而是与机身同族、只在顶部受光。
        // 第一停比旧版暗 30%：大片同色值一铺开，亮度差 5% 就足以被读成塑料。
        panelMetal = listOf(
            Color(0xFF1A1D20),
            Color(0xFF15171A),
            Color(0xFF101214),
            Color(0xFF0C0D0F),
        ),
        wellFill = Color(0xFF08090A),
        // 快门环收敛成香槟金 + 暖灰的扫掠：金属感靠一圈更细的齿和面光，
        // 不靠高对比黑白条（旧版在真机 3x 下读成一圈脏边）。
        shutterRing = listOf(
            Color(0xFFC9A87C),
            Color(0xFF4A433B),
            Color(0xFFE7B37A),
            Color(0xFF2E2A25),
            Color(0xFFB08E63),
        ),
        shutterFace = listOf(
            Color(0xFFF2EEE6),
            Color(0xFFE4DED2),
            Color(0xFFC9C2B4),
        ),
        shutterDot = listOf(
            Color(0xFFD95C4A),
            Color(0xFFA83226),
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
