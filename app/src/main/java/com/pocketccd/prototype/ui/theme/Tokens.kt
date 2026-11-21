package com.pocketccd.prototype.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Darkroom Instrument 的形状与间距令牌。
 *
 * 造型以摄影器材的克制几何为基准：面板只做轻微圆角，照片画框接近直角，
 * 圆形只留给快门和确有实体隐喻的图标键。所有可点控件仍必须满足 48dp 热区。
 */
object GlassShape {
    /** 圆形（图标钮、快门、头像）。 */
    val pill = RoundedCornerShape(percent = 50)

    /** 紧凑仪器键：变焦档、筛选、读数窗。 */
    val control = RoundedCornerShape(10.dp)

    /** 胶囊状选择件，仅用于确需文字横排的标签。 */
    val chip = RoundedCornerShape(12.dp)

    /** 卡片：胶卷行、参数卡、设置分组。 */
    val card = RoundedCornerShape(16.dp)

    /** 大面板：底部 dock、抽屉、详情动作条。 */
    val panel = RoundedCornerShape(24.dp)

    /** 照片画框保持克制，避免把照片裹成普通 App 卡片。 */
    val image = RoundedCornerShape(6.dp)
}

object GlassSpace {
    /** 屏幕左右安全边距。 */
    val page = 20.dp

    /** 仪器内部的极小间距。 */
    val micro = 4.dp

    /** 面板内的常规留白。 */
    val inner = 16.dp

    /** 相邻元素间距。 */
    val gap = 12.dp

    /** 同组元素紧邻间距。 */
    val tight = 8.dp

    /**
     * 底部浮层（TabBar / 相机 dock）占用的高度。
     *
     * 列表页必须主动让出这么多：浮层是叠在内容上的，不属于布局，Compose 不会自动
     * 为它留空间 —— 少让这一截，最后一行就被压在浮层底下。
     *
     * 108 → 100（D-099）：dock 去掉文字标签后本体从 69dp 降到 60dp，剩下的构成是
     * 手势导航条 24 + 底下留 10 + 本体 60 + 6 的余量。这个常数**没有**跟着
     * `WindowInsets.navigationBars` 走，三键导航的机型上本来就不够 —— 那时应该给它
     * 换成 Insets 而不是继续调大数字。
     */
    val dockReserve = 100.dp
}
