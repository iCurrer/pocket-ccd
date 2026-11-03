package com.pocketccd.prototype.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * **玻璃拟态**主题：只有一套固定色板。
 *
 * 与旧体系的差别：
 *
 * 1. **只有一套基底，不再分"取景页暗 / 纸面页亮"**。全 App 是深色玻璃，相机预览是
 *    唯一的光源；相册、胶卷库、设置页在同一个深底上叠玻璃面板。
 * 2. **不跟随壁纸取色**（D-074）。相机 App 的界面是照片的衬底：强调色跟着壁纸变，
 *    同一张照片在不同桌面下会显得不一样；而取色只影响选中态和强调文字，收益
 *    远小于"不可预期"的代价。强调色固定为冷蓝种子色。
 * 3. **玻璃是叠加层，不是背景**：面板 = 白色低透明度填充 + 库画的边缘高光；
 *    背景模糊/折射由 `io.github.kyant0:backdrop` 完成（D-072）。
 */
@Immutable
data class GlassPalette(
    /** 页面底色（近黑，带一点色相）。 */
    val background: Color,
    /** 玻璃面板填充（叠在照片/背景之上时用得到的低透明度白）。 */
    val fill: Color,
    /** 更实一点的玻璃：需要压住底下花哨内容的面板（底部 dock、动作条）。 */
    val fillStrong: Color,
    /**
     * 压在**影像**上时的深色蒙版，垫在 [fill] 下面。
     *
     * 影像的亮度不受控：只靠 6% 白叠在亮部，白字会糊掉。这层黑让玻璃在任何照片上
     * 都还读得清，同时保留背后被模糊的内容。
     */
    val veil: Color,
    /** 玻璃描边。 */
    val border: Color,
    /** 顶部高光（玻璃厚度感，从 12% 白渐变到透明）。 */
    val highlight: Color,
    /** 玻璃上的主文字。 */
    val onGlass: Color,
    /** 玻璃上的次级文字（说明、单位）。 */
    val onGlassMuted: Color,
    /** 强调色（选中态、快门、链接）。 */
    val accent: Color,
    val onAccent: Color,
    /** 失败 / 危险态文字（冲洗失败、这台设备拍不了这卷）。 */
    val error: Color,
    /** 背景上的两颗光斑，给 Material You 一点色。 */
    val glowA: Color,
    val glowB: Color,
    /** 压暗层：查看器、对话框背后。 */
    val scrim: Color,
)

private val PocketCcdColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF0A2233),
    primaryContainer = Color(0xFF2B4A6F),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBFC6DC),
    tertiary = Color(0xFFDDBCE0),
    background = Color(0xFF0B0C0F),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF2A2C33),
    onSurfaceVariant = Color(0xFFC4C6CF),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private fun glassOf(scheme: androidx.compose.material3.ColorScheme): GlassPalette {
    // 强调色的"亮不亮"决定玻璃上的文字要不要翻成 dark：动态取色可能给出浅黄之类的
    // primary，直接用 onPrimary 会和白色玻璃冲突。
    val onGlass = Color.White.copy(alpha = 0.95f)
    return GlassPalette(
        background = scheme.background,
        fill = Color.White.copy(alpha = 0.06f),
        fillStrong = Color.White.copy(alpha = 0.10f),
        veil = Color.Black.copy(alpha = 0.48f),
        border = Color.White.copy(alpha = 0.14f),
        highlight = Color.White.copy(alpha = 0.12f),
        onGlass = onGlass,
        onGlassMuted = onGlass.copy(alpha = 0.62f),
        accent = scheme.primary,
        onAccent = scheme.onPrimary,
        error = scheme.error,
        glowA = scheme.primary,
        glowB = scheme.tertiary,
        scrim = Color.Black.copy(alpha = 0.55f),
    )
}

private val LocalGlassPalette = staticCompositionLocalOf { glassOf(PocketCcdColors) }

/** 当前玻璃色板。页面里一律读它，不要直接读 `MaterialTheme.colorScheme`。 */
val MaterialTheme.glass: GlassPalette
    @Composable get() = LocalGlassPalette.current

private val GlassShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = GlassShape.chip,
    medium = GlassShape.card,
    large = GlassShape.panel,
    extraLarge = RoundedCornerShape(40.dp),
)

/**
 * 只有深色一套基底：相机 App 的所有页面都在看照片，白底会把成片衬得发灰。
 *
 * 色板**固定**，不读系统壁纸、也不看系统深/浅色 —— 相机界面必须是中性的衬底，
 * 同一张照片在任何桌面、任何主题下都该长一个样（D-074）。
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
