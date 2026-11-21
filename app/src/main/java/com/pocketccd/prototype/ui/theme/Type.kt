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

    /** 极少数页面的展示标题。 */
    val display = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.6).sp,
    )

    /** 大标题：页面标题、胶卷名。 */
    val title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.4).sp,
    )

    /** 卡片标题。 */
    val cardTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    )

    /** 正文 / 列表主标签。 */
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )

    /** 次要说明。 */
    val caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )

    /** 芯片文字、按钮文字。 */
    val label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    )

    /** 参数读数（张数、EV、倍率、EXIF）。等宽让数字不左右跳。 */
    val numeric = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp,
    )

    /** 大号读数：快门计时、片数总计。 */
    val numericLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.3.sp,
    )

    /** 极大读数：页面上只能出现一个主数字。 */
    val numericHero = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.2.sp,
    )

    /** 小号读数：角标、单位。11sp 是地板，不再往下探。 */
    val badge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )

    /**
     * 主按钮文字（D-094）：比 [label] 高一档。
     *
     * 一屏只允许有一个主按钮，它得压得住版式；用 12sp 的芯片字号去撑 52dp 的通栏键，
     * 读起来像"卡片里的一行小字"，不像"这个页面唯一的动作"。
     */
    val action = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    )

    /**
     * 邀请码**字符格**里的单个字符（D-094）。
     *
     * 与 [codeInput] 的区别：字距归零。格子的间距已经做进布局，再叠 3sp 字距会让
     * 字符在格里偏左 1.5sp —— 八个格一起偏，就是一眼能看出的不居中。
     */
    val codeCell = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )

    /**
     * 邀请码输入：等宽 + 宽字距。
     *
     * 字距 3sp 是为了让 8 个字符读起来像一排**冲压字符**而不是一串数字：用户在聊天记录里
     * 看到 `PCCD-7K3M-9QX2`，要能一位一位对着抄。这是全 App 唯一需要"逐字符对齐"的输入，
     * 所以它单独占一档，不并进 [numericLarge]。
     */
    val codeInput = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 3.sp,
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
