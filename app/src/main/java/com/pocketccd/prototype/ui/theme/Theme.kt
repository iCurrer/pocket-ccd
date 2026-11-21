package com.pocketccd.prototype.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * **Warm Chassis** 主题：只有一套固定色板，浅色。
 *
 * D-069 起这套令牌是"深色玻璃"（Darkroom Instrument），照片浮在近黑的衬底上。
 * D-090 换掉了这个方向：整台机器是**暖白机身 + 银灰面板 + 黄铜标记**，
 * 相册、胶卷、发现、设置与横屏拍摄页共用同一套材质 —— 原来只有拍摄页是浅色，
 * 一进相册就跳回深色玻璃，读起来像两个 App 拼在一起。
 *
 * ## 为什么浅色也敢用在看图的页面
 *
 * 深色衬底的传统理由是"让照片跳出来"。但这里的隐喻不是屏幕，是**一台相机**：
 * 相机的机身是浅色的，取景窗才是深色的。所以规矩改成：
 *
 * - 页面底 = 机身暖白，控件浮在机身上；
 * - **影像本身所在的框是深色**（取景窗 `ConsoleColor.viewfinder`、查看器底色），
 *   照片永远贴着深色出画，对比度不丢。
 *
 * ## 三条硬约束（与拍摄页同一套）
 *
 * 1. **浅底深字**：正文必须是深石墨，不允许在这套底上再叠白色文字。
 * 2. **只有一个强调色**：黄铜金只出现在"当前值/选中/可按下"三种语义上。
 * 3. **描边即结构**：层次靠 1dp 描边 + 大半径低浓度阴影（见 [consolePanel]）。
 */
@Immutable
data class GlassPalette(
    /** 页面底色（机身暖白）。 */
    val background: Color,
    /** 凹槽底：需要"陷进去"的容器（参数卡、列表行底）。 */
    val surface: Color,
    /** 抬高的浅面板：卡片、按键、dock。 */
    val surfaceRaised: Color,
    /** 极淡的暗色叠加（叠在 [surfaceRaised] 上做分组）。 */
    val fill: Color,
    /** 稍重的暗色叠加（选中行、按下底色）。 */
    val fillStrong: Color,
    /**
     * 压在**影像**上时的近实心浅板。
     *
     * 影像亮度不受控，半透明浅板在亮部会糊掉、在暗部会消失。压在照片上的控件用
     * 92% 的暖白，读起来像"贴在照片上的实体塑料片"，而不是一层滤镜。
     */
    val veil: Color,
    /** 面板描边。 */
    val border: Color,
    /** 顶边受光（面板上表面反的光）。 */
    val highlight: Color,
    /** 主文字：深石墨。 */
    val onGlass: Color,
    /** 次级文字（说明、单位）。 */
    val onGlassMuted: Color,
    /** 强调色（选中底色、指示条、实心主按钮）。 */
    val accent: Color,
    /** 强调色的**可读版本**：浅底上当文字/图标用的深金。 */
    val accentBright: Color,
    /** 更暗的强调色，用于按压与阴影侧。 */
    val accentDeep: Color,
    /** 强调色实底上的文字。 */
    val onAccent: Color,
    /** 失败 / 危险态文字（冲洗失败、这台设备拍不了这卷）。 */
    val error: Color,
    /** 页面顶部的一层极淡暖光，给平涂的机身一点纵深。 */
    val glowA: Color,
    val glowB: Color,
    /** 压暗层：查看器、对话框背后。 */
    val scrim: Color,
)

private val PocketCcdColors = lightColorScheme(
    primary = ConsoleColor.accentDeep,
    onPrimary = Color(0xFFFBF6EA),
    primaryContainer = Color(0xFFEBD9B8),
    onPrimaryContainer = Color(0xFF3A2C12),
    secondary = ConsoleColor.secondaryText,
    onSecondary = Color(0xFFFBF6EA),
    background = ConsoleColor.body,
    onBackground = ConsoleColor.primaryText,
    surface = ConsoleColor.panelLight,
    onSurface = ConsoleColor.primaryText,
    surfaceVariant = ConsoleColor.panel,
    onSurfaceVariant = ConsoleColor.secondaryText,
    outline = ConsoleColor.border,
    outlineVariant = ConsoleColor.borderSoft,
    error = ConsoleColor.danger,
    onError = Color(0xFFFDF6EF),
)

private fun glassOf(scheme: androidx.compose.material3.ColorScheme): GlassPalette = GlassPalette(
    background = scheme.background,
    // 从 M3 scheme 取，而不是再从 ConsoleColor 抄一遍：两处各写一遍迟早会不一致。
    surface = scheme.surfaceVariant,
    surfaceRaised = scheme.surface,
    fill = Color.Black.copy(alpha = 0.030f),
    fillStrong = Color.Black.copy(alpha = 0.065f),
    veil = Color(0xF2F4F2EB),
    border = ConsoleColor.border,
    highlight = Color.White.copy(alpha = 0.60f),
    onGlass = ConsoleColor.primaryText,
    onGlassMuted = ConsoleColor.secondaryText,
    accent = ConsoleColor.accent,
    // 浅底上的"亮金"读不出来（对比度约 1.8:1），可读版换成深金（约 4.9:1）。
    accentBright = ConsoleColor.accentDeep,
    accentDeep = ConsoleColor.accentDeep,
    onAccent = Color(0xFF2A2113),
    error = scheme.error,
    glowA = Color(0xFFFFF6E2),
    glowB = ConsoleColor.accentLight,
    scrim = Color.Black.copy(alpha = 0.62f),
)

private val LocalGlassPalette = staticCompositionLocalOf { glassOf(PocketCcdColors) }

/** 当前色板。页面里一律读它，不要直接读 `MaterialTheme.colorScheme`。 */
val MaterialTheme.glass: GlassPalette
    @Composable get() = LocalGlassPalette.current

private val GlassShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = GlassShape.chip,
    medium = GlassShape.card,
    large = GlassShape.panel,
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * 只有一套固定色板：不读系统壁纸，也不看系统深/浅色。
 *
 * 相机是器材，同一张照片的衬底不该由壁纸决定。
 */
@Composable
fun PocketCcdTheme(content: @Composable () -> Unit) {
    val palette = remember { glassOf(PocketCcdColors) }
    CompositionLocalProvider(LocalGlassPalette provides palette) {
        MaterialTheme(
            colorScheme = PocketCcdColors,
            typography = PocketCcdTypography,
            shapes = GlassShapes,
            content = content,
        )
    }
}
