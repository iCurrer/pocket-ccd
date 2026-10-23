package com.pocketccd.prototype.ui.film

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 取景器内的绘制层：金属外框（[FilmViewfinder]）、暗角、四角角标、九宫格、准星。
 *
 * **无障碍审计结论（T1-013）：本文件没有任何可点控件，全部是纯 Canvas 装饰，
 * 因此刻意不挂任何语义。** 给装饰性 Canvas 加 `contentDescription` 只会让读屏
 * 在取景页多停几个没有动作的焦点，反而是噪音。取景器中唯一可交互的是"点按对焦"，
 * 它由 `FilmCaptureScreen.kt` 的 `pointerInput` 层实现（那个文件不在本次改动范围）。
 * 对焦**结果**的语义在 [FocusRing]（FilmInteractions.kt）里，用 liveRegion 播报。
 */

/**
 * 取景窗（2026-10-23 重做）。
 *
 * 旧版是"3dp 金属 bezel + 20dp 外投影 + 灰色描边"，在真机截图里读成了一圈手机壳：
 * 高级感不是靠加框，是靠**明暗阶梯**。现在只做三件事：
 *
 * 1. 窗口比页面底色更黑（[FilmPalette.windowBg] vs [FilmPalette.appBg]），
 *    边缘自然分界，不再画描边；
 * 2. 顶部 1px 白 10% 内高光 + 底部 1px 纯黑：光从上方来，窗口像被"切"进机身；
 * 3. 全部内容裁进 16dp 圆角，不加任何外投影。
 *
 * 暗角（[ViewfinderVignette]）仍单独叠在画面之上，保证亮画面里角标/准星可读。
 */
@Composable
fun FilmViewfinder(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val m = spec.metrics
    val shape = RoundedCornerShape(m.viewfinderRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .background(p.windowBg)
            .drawWithContent {
                drawContent()
                val r = m.viewfinderRadius.toPx()
                val h = m.hairline.toPx()
                drawLine(
                    // D-063：闸门上的分隔线统一白 10%（hairline），不再是 8% 的"内高光"。
                    color = p.hairline,
                    start = Offset(r, h / 2f),
                    end = Offset(size.width - r, h / 2f),
                    strokeWidth = h,
                )
                drawLine(
                    color = Color.Black.copy(alpha = 0.85f),
                    start = Offset(r, size.height - h / 2f),
                    end = Offset(size.width - r, size.height - h / 2f),
                    strokeWidth = h,
                )
            },
        content = content,
    )
}

/**
 * Darkens the edges of the live preview.
 *
 * The design has this layer (`.vf-vignette`) and it was skipped in the first
 * implementation — the device immediately showed why it matters: on a white
 * wall the framing corners and the reticle are invisible without it. Composited
 * under the chrome, over the preview.
 */
@Composable
fun ViewfinderVignette(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to Color.Black.copy(alpha = 0.45f),
                0.18f to Color.Transparent,
                0.72f to Color.Transparent,
                1.0f to Color.Black.copy(alpha = 0.55f),
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                0.0f to Color.Transparent,
                0.48f to Color.Transparent,
                1.0f to Color.Black.copy(alpha = 0.5f),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = maxOf(size.width, size.height) * 0.75f,
            ),
        )
    }
}

/**
 * 四角刻线（D-063「白纸档案」）。
 *
 * 方向 C 只刻纸白线：臂长 16dp、线宽 1px、白 62%，黑晕仍垫在下面（2.5x 宽）
 * 保证白墙背景上不会消失。不再有"UI 括号"的粗线，也没有任何颜色。
 */
@Composable
fun ViewfinderCorners(modifier: Modifier = Modifier) {
    val m = LocalFilmTheme.current.metrics
    Canvas(modifier.fillMaxSize()) {
        val ix = m.cornerInsetX.toPx()
        val iy = m.cornerInsetY.toPx()
        // 规格 §5.1：16dp 臂长、1px。FilmMetrics.cornerArm 是旧皮肤默认值，不跟这里改。
        val arm = 16.dp.toPx()
        val w = 1.dp.toPx()
        val right = size.width - ix
        val bottom = size.height - iy

        val segments = listOf(
            Offset(ix, iy) to Offset(ix + arm, iy),
            Offset(ix, iy) to Offset(ix, iy + arm),
            Offset(right, iy) to Offset(right - arm, iy),
            Offset(right, iy) to Offset(right, iy + arm),
            Offset(ix, bottom) to Offset(ix + arm, bottom),
            Offset(ix, bottom) to Offset(ix, bottom - arm),
            Offset(right, bottom) to Offset(right - arm, bottom),
            Offset(right, bottom) to Offset(right, bottom - arm),
        )

        // 黑晕先画，白线后画：亮画面里白线不会消失，暗画面里黑晕也不会显形。
        segments.forEach { (a, b) ->
            drawLine(Color.Black.copy(alpha = 0.35f), a, b, w * 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        segments.forEach { (a, b) ->
            drawLine(Color.White.copy(alpha = 0.62f), a, b, w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** Rule-of-thirds guides. Off by default — a clean finder is the point. */
@Composable
fun ViewfinderGrid(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val c = Color.White.copy(alpha = 0.15f)
        val w = 1.dp.toPx()
        val thirdW = size.width / 3f
        val thirdH = size.height / 3f
        drawLine(c, Offset(thirdW, 0f), Offset(thirdW, size.height), w)
        drawLine(c, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), w)
        drawLine(c, Offset(0f, thirdH), Offset(size.width, thirdH), w)
        drawLine(c, Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), w)
    }
}

/**
 * 中央准星（D-063）：20dp 四向刻线，纸白 62%，中心一颗 1.4dp 纸白点。
 *
 * 不再用 `palette.accent` 画中心点 —— 黑闸门里 accent 恰好就是纸白，但写成
 * `palette.textPrimary` 才能保证换皮肤时不会又长出一颗彩色点。
 */
@Composable
fun ViewfinderReticle(modifier: Modifier = Modifier) {
    val p = LocalFilmTheme.current.palette
    Canvas(modifier.size(20.dp)) {
        val w = 1.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val inner = 4.dp.toPx()
        val outer = 10.dp.toPx()

        val segments = listOf(
            Offset(cx, cy - inner) to Offset(cx, cy - outer),
            Offset(cx, cy + inner) to Offset(cx, cy + outer),
            Offset(cx - inner, cy) to Offset(cx - outer, cy),
            Offset(cx + inner, cy) to Offset(cx + outer, cy),
        )

        segments.forEach { (a, b) -> drawLine(Color.Black.copy(alpha = 0.30f), a, b, w * 2.5f) }
        segments.forEach { (a, b) -> drawLine(p.textPrimary.copy(alpha = 0.62f), a, b, w) }

        drawCircle(p.textPrimary, radius = 1.4.dp.toPx(), center = Offset(cx, cy))
    }
}


