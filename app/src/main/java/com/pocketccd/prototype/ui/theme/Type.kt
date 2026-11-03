package com.pocketccd.prototype.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字阶。**下限 11sp**，与旧体系（D-050 / T9-023）同一条地板，不因为换皮而放宽：
 * 11sp 是 360dp 宽屏幕上一行中文还能读清的最小值；`NoBareFontSizeTest` 继续盯着
 * `ui/` 下不许出现裸字号。
 *
 * 与旧体系的差别不在大小，而在**层次来源**：旧体系靠等宽字体 + 大字距 + 全大写区分
 * 层级，读起来像设备丝印；这里靠字重与字号两档跳变，正文交给系统无衬线，
 * 只有数字/参数用等宽（等宽的作用是让读数不随数值变化而左右跳动）。
 */
object GlassType {

    /** 大标题：页面标题、胶卷名。 */
    val title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
    )

    /** 卡片标题。 */
    val cardTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )

    /** 正文 / 列表主标签。 */
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    /** 次要说明。 */
    val caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )

    /** 芯片文字、按钮文字。 */
    val label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    )

    /** 参数读数（张数、EV、倍率、EXIF）。等宽让数字不左右跳。 */
    val numeric = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    )

    /** 大号读数：快门计时、片数总计。 */
    val numericLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp,
    )

    /** 小号读数：角标、单位。11sp 是地板，不再往下探。 */
    val badge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )
}

/**
 * M3 兜底字型：对话框、Snackbar、文本选择柄等系统组件走这一套，保证它们和
 * [GlassType] 同一个字族、同一档字重，不会在玻璃面板上跳出另一套排版。
 */
val PocketCcdTypography = Typography(
    headlineSmall = GlassType.title,
    titleLarge = GlassType.title,
    titleMedium = GlassType.cardTitle,
    bodyLarge = GlassType.body,
    bodyMedium = GlassType.body,
    bodySmall = GlassType.caption,
    labelLarge = GlassType.label,
    labelMedium = GlassType.label,
    labelSmall = GlassType.badge,
)
