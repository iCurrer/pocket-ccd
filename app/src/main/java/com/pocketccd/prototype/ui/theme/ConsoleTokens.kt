package com.pocketccd.prototype.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * **Light Camera Console**：横屏拍摄页专用的第二套令牌。
 *
 * ## 为什么拍摄页要另起一套色板
 *
 * 相册/胶卷/发现/设置是"照片的衬底"，深色玻璃（[GlassPalette]）让照片跳出来，这是对的。
 * 但取景页不是衬底 —— 它本身就是**一台相机**。相机的外壳是有材质的实体：暖白机身、
 * 银灰面板、黄铜色标记。深色玻璃放在这里，读起来像一块盖在画面上的手机 UI，而不是
 * 一台拿在手里的机器（D-089）。
 *
 * 所以这一套色板**只服务于拍摄页**，不参与 CompositionLocal，不跟随主题，也不影响
 * 其它页面：`PocketCcdTheme` 仍然只提供深色玻璃。
 *
 * ## 三条硬约束
 *
 * 1. **浅底深字**。机身是暖白，正文必须是深石墨；不允许在这套色板上再叠白色文字
 *    （只有取景器内部是深底，那一片用 [onViewfinder]）。
 * 2. **只有一个强调色**。黄铜金只出现在"当前值/当前档/可按下"三种语义上，
 *    不做渐变装饰 —— 满屏金色会让读数失去重点。
 * 3. **描边即结构**。浅色系没有阴影能撑起层次，面板边界靠 1dp 描边 + 极浅的点状阴影。
 *    所有圆角都必须在 [ConsoleShape] 里，不在调用点写裸 dp。
 */
object ConsoleColor {

    /** 机身底色：暖象牙。整页铺它，其余面板都叠在上面。 */
    val body = Color(0xFFE9E7E0)

    /** 机身下缘（取景窗外的压边、分隔线）。比机身深半档。 */
    val bodyShade = Color(0xFFDCD9D1)

    /** 中性面板：需要"凹进去"的槽位（取景窗边框内侧、缩略图底）。 */
    val panel = Color(0xFFDAD8D0)

    /**
     * 抬高的浅面板：胶卷卡、状态栏、圆形按键底。
     *
     * D-094 起比机身亮一档（原来只亮 7 级，叠上曲率渐变后仍然读成"同一块板"）。
     * 现在是 13 级 —— 卡片浮起来靠的是明度差，不是描边。
     */
    val panelLight = Color(0xFFF6F4ED)

    /** 深一档的浅面板：按下态、未激活按键。 */
    val panelDark = Color(0xFFC9C7BF)

    /** 取景器底色（深石墨）。 */
    val viewfinder = Color(0xFF25282A)

    /** 取景器外圈描边：比取景器略亮，勾出"窗"的厚度。 */
    val viewfinderEdge = Color(0xFF3C4043)

    /** 机身主文字。 */
    val primaryText = Color(0xFF34332D)

    /** 次级文字（单位、说明）。 */
    val secondaryText = Color(0xFF77746B)

    /** 三级文字（不可用、占位）。 */
    val mutedText = Color(0xFF9B988F)

    /** 黄铜金：当前档、当前值、可按下。 */
    val accent = Color(0xFFC5A66B)

    /** 亮金：金色上的高光与选中描边。 */
    val accentLight = Color(0xFFDCC18A)

    /** 暗金：金色底的阴影侧、金色文字的深色版（保证浅底上的对比度）。 */
    val accentDeep = Color(0xFF8A6C36)

    /** 面板描边。 */
    val border = Color(0xFFC4C1B8)

    /**
     * 倒角边界（D-094）。比 [border] 淡半档：轮廓现在由**倒角的明暗两圈**承担，
     * 描边只负责把圆角收干净，太深会读成"贴了张带黑边的纸"。
     */
    val bevelEdge = Color(0xFFCFCCC3)

    /** 更淡的分隔线（状态栏内）。 */
    val borderSoft = Color(0xFFD5D2C9)

    /** 快门银盘的三档：高光 / 本体 / 背面。 */
    val silverHigh = Color(0xFFFCFBF7)
    val silverMid = Color(0xFFDBD8D0)
    val silverLow = Color(0xFF8E8B83)

    /** 取景器内的文字与描边。 */
    val onViewfinder = Color(0xFFF4F1E8)

    /** 不可拍 / 失败：偏砖红，不用纯红（纯红在暖白底上像报错弹窗）。 */
    val danger = Color(0xFFA6493C)

    /**
     * 环境阴影：**低浓度、大半径**。
     *
     * 浅色底上最忌讳"小半径高浓度"——那圈灰影会把面板边缘一圈圈描黑，整页像贴纸。
     * 20% 的暖灰摊到 8–12dp 才是环境光的样子；需要"贴住机身"的地方用 [shadowContact]。
     */
    val shadow = Color(0x1F2A2620)

    /** 接触阴影：取景窗这种"嵌进去的深色件"要到 30%，否则压不住暖白机身。 */
    val shadowContact = Color(0x4D23201A)
}

/**
 * 拍摄页形状令牌。
 *
 * 参考图的圆角层级是：机身 > 取景器 > 胶卷卡 > 信息条。差一档就够，
 * 全部做成大圆角会让浅色面板糊成一堆药丸。
 */
object ConsoleShape {
    /** 机身外框（只在需要"整块机器"的地方用）。 */
    val body = RoundedCornerShape(26.dp)

    /** 大面板：胶卷卡、控制区底板。 */
    val panel = RoundedCornerShape(20.dp)

    /** 取景窗。比面板大一点，它是整页的视觉中心。 */
    val viewfinder = RoundedCornerShape(22.dp)

    /**
     * 取景窗**画面**的圆角：比外框小一档，等于外框减去那圈亮边。
     *
     * 两层圆角必须同心且差一个边框宽度，否则亮边在圆角处会一头粗一头细。
     */
    val viewfinderInner = RoundedCornerShape(19.dp)

    /** 信息条、迷你面板。 */
    val info = RoundedCornerShape(11.dp)

    /** 胶卷包装图的容器（跟正文卡片不是一套，单独留一格）。 */
    val artwork = RoundedCornerShape(10.dp)

    /** 圆形键（快门、工具键、缩略图）。 */
    val round = RoundedCornerShape(percent = 50)
}

/** 拍摄页间距。横向只有 360dp 高，所有间距比玻璃页紧一档。 */
object ConsoleSpace {
    /** 屏幕左右安全边距。 */
    val page = 12.dp

    /** 面板内留白。 */
    val inner = 12.dp

    /** 相邻面板间距。 */
    val gap = 10.dp

    /** 同组元素间距。 */
    val tight = 6.dp

    /** 极小间距（刻度与读数之间）。 */
    val micro = 4.dp
}

/**
 * 拍摄页字阶。下限仍是 11sp（和 [GlassType] 同一条地板）。
 *
 * 与玻璃页的差别：这里**等宽字体用得更凶** —— 变焦倍率、EV 读数、张数都是会变化的
 * 数字，必须等宽才不会让整行读数随着数值左右跳。胶卷名和按钮文字仍用系统无衬线。
 */
object ConsoleType {

    /** 品牌字：左上角 "Pocket CCD"。 */
    val brand = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.2).sp,
    )

    /** 胶卷名。 */
    val filmName = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.2).sp,
    )

    /** 胶卷副行（类型、张数、画幅）。 */
    val filmMeta = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp,
    )

    /** 胶囊按钮文字（切换胶卷、归零）。 */
    val button = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
    )

    /** 读数：EV 刻度标签、档位。 */
    val readout = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
    )

    /** 主读数：EV 当前值。 */
    val readoutHero = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.4.sp,
    )

    /** 状态条文字。 */
    val status = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
    )

    /** 取景器内的角标（4:3、变焦档）。 */
    val tag = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.2.sp,
    )
}
