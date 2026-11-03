package com.pocketccd.prototype.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 玻璃拟态的形状与间距令牌。
 *
 * 与旧纸面体系（`ui/film/FilmMetrics`）没有继承关系：这里全部以"浮在内容之上的圆角
 * 面板"为前提取值 —— 大圆角、内留白大于外间距、可点控件高度 ≥48dp。
 */
object GlassShape {
    /** 圆形（图标钮、快门、头像）。 */
    val pill = RoundedCornerShape(percent = 50)

    /** 浮层芯片：胶卷名、变焦档、筛选。 */
    val chip = RoundedCornerShape(18.dp)

    /** 卡片：胶卷行、设置分组。 */
    val card = RoundedCornerShape(24.dp)

    /** 大面板：底部 dock、详情页动作条。 */
    val panel = RoundedCornerShape(32.dp)

    /** 图片相框。 */
    val image = RoundedCornerShape(20.dp)
}

object GlassSpace {
    /** 屏幕左右安全边距。 */
    val page = 20.dp

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
     * 为它留空间 —— 少让这一截，最后一行就被压在玻璃底下。
     */
    val dockReserve = 108.dp
}
