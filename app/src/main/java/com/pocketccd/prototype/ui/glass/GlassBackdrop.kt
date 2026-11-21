package com.pocketccd.prototype.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.pocketccd.prototype.ui.theme.glass

/**
 * 页面壳：暖白机身底 + 一层极淡的顶部暖光 + 浮层槽。
 *
 * ## 为什么不再录图层（D-090）
 *
 * 旧实现用 `Modifier.layerBackdrop` 把整页内容录成一张离屏图层，让底部 dock 的玻璃
 * 去采样它（真模糊 + 折射）。换成浅色实体材质之后，dock 是一块**不透明**的暖白面板 ——
 * 它不采样任何东西，录制那一遍就是纯浪费：一张全屏离屏缓冲 + 每帧一次合成，
 * 换来的是零像素差异。所以整条 backdrop 链路拆掉，`kyant0:backdrop` 依赖也随之删除。
 *
 * ## 两个槽仍然分开
 *
 * [content] 是页面本身，[overlay] 是浮在它上面的层（底部 dock、查看器）。
 * 分开的理由从"采样"变成了**层级**：dock 必须压住列表最后一行，查看器必须压住 dock。
 *
 * `Modifier.swallowTaps()` 由调用方加在最外层：这层壳铺满全屏，不吞掉空白处的点击，
 * 下层的取景页会收到本不该属于它的事件。
 */
@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val p = MaterialTheme.glass

    Box(modifier.fillMaxSize().background(p.background)) {
        Box(Modifier.fillMaxSize().drawBehind { drawChassisLight(p) }) {
            content()
        }
        Box(Modifier.fillMaxSize()) { overlay() }
    }
}

/**
 * 机身光：顶部一层几乎看不见的暖白，右上角一颗极淡的金色光斑。
 *
 * 位置与半径**按实际尺寸算**，不写死像素 —— 写死的坐标在任何非 1080×2400 的屏幕上
 * 都会偏出画面或糊成一片。这层只负责"不是一张纯色纸"，浓度必须低到看不出边界。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChassisLight(
    p: com.pocketccd.prototype.ui.theme.GlassPalette,
) {
    drawRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.14f),
            0.20f to Color.Transparent,
            startY = 0f,
            endY = size.height,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(p.glowB.copy(alpha = 0.06f), Color.Transparent),
            center = Offset(size.width * 1.02f, -size.height * 0.04f),
            radius = size.maxDimension * 0.55f,
        ),
    )
}
