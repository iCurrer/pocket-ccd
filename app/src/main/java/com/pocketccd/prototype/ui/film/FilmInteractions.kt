package com.pocketccd.prototype.ui.film

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pocketccd.prototype.camera.LensStop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 一次点按对焦。`id` 用 `System.nanoTime()`：同一个位置连续点两次也要重播动画，
 * 否则第二次点按没有任何反馈，用户会以为没点中。
 */
data class FocusPoint(val x: Float, val y: Float, val id: Long)

/**
 * 点按对焦的确认环。
 *
 * 设计稿是 66px 方框 + 四个 14px 拐角 + 下方 `AF LOCK`，入场从 1.28 缩回 1。
 * 这里照做，只有两处不同，都是"不撒谎"要求的：
 *
 * 1. **标签显示真实结果**：设计稿永远写 `AF LOCK`，但我们真的调了
 *    `startFocusAndMetering`，所以对焦中显示 `AF`、成功 `AF LOCK`、失败 `AF FAIL`。
 * 2. **2.2 秒后淡出**：设计稿的环一旦出现就永久留在原地，换个角度它就变成
 *    画面里一个没意义的框。真机相机都是淡出的。
 */
@Composable
fun FocusRing(
    point: FocusPoint?,
    lock: Boolean?,
    modifier: Modifier = Modifier,
) {
    if (point == null) return
    val spec = LocalFilmTheme.current
    val tint = spec.palette.accent
    val density = LocalDensity.current
    val halfPx = with(density) { 33.dp.toPx() }
    val labelOffset = with(density) { 34.dp.toPx() }

    val scale = remember { Animatable(1.28f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(point.id) {
        scale.snapTo(1.28f)
        alpha.snapTo(0f)
        launch { scale.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
        alpha.animateTo(1f, tween(130))
        delay(2200)
        alpha.animateTo(0f, tween(320))
    }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    (point.x - halfPx).roundToInt(),
                    (point.y - halfPx).roundToInt(),
                )
            }
            .size(66.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .drawBehind {
                val arm = 14.dp.toPx()
                val w = 2.dp.toPx()
                val right = size.width
                val bottom = size.height
                val segments = listOf(
                    Offset(0f, 0f) to Offset(arm, 0f),
                    Offset(0f, 0f) to Offset(0f, arm),
                    Offset(right, 0f) to Offset(right - arm, 0f),
                    Offset(right, 0f) to Offset(right, arm),
                    Offset(0f, bottom) to Offset(arm, bottom),
                    Offset(0f, bottom) to Offset(0f, bottom - arm),
                    Offset(right, bottom) to Offset(right - arm, bottom),
                    Offset(right, bottom) to Offset(right, bottom - arm),
                )
                // 与构图角标同理：先垫一层暗底，否则白墙背景上金色细线会消失。
                segments.forEach { (a, b) ->
                    drawLine(Color.Black.copy(alpha = 0.45f), a, b, w * 2.6f)
                }
                segments.forEach { (a, b) -> drawLine(tint, a, b, w) }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = when (lock) {
                null -> "AF"
                true -> "AF LOCK"
                false -> "AF FAIL"
            },
            modifier = Modifier.offset { IntOffset(0, labelOffset.roundToInt()) },
            color = tint,
            fontSize = spec.type.label,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.1.em,
        )
    }
}

/**
 * 轻提示，对应设计稿的 `#toast`：2.2 秒后自动消失，位于快门上方。
 *
 * 文本永远来自真实状态（开关切换、变焦档位），不是"操作成功"这类空话。
 */
@Composable
fun FilmToast(
    text: String?,
    modifier: Modifier = Modifier,
) {
    val spec = LocalFilmTheme.current
    AnimatedVisibility(
        visible = text != null,
        modifier = modifier,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(260)),
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.80f))
                .border(1.dp, spec.palette.hairline, CircleShape)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = text ?: "",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = spec.type.mono,
                letterSpacing = 0.06.em,
                maxLines = 1,
            )
        }
    }
}

/**
 * 快门闪白：按下时瞬间到 0.85，保持 90ms 后归零。
 *
 * 设计稿是 `opacity .85` → 90ms 后直接跳到 0。这里多给 120ms 淡出——
 * 纯瞬灭在全屏白上会有明显的"闪一下"，比真机快门还跳。
 */
@Composable
fun ShutterFlash(trigger: Int, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        alpha.snapTo(0.85f)
        delay(90)
        alpha.animateTo(0f, tween(120))
    }
    if (alpha.value <= 0.001f) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = alpha.value)),
    )
}

/**
 * 取景器右侧的变焦档位。
 *
 * 设计稿只有 1x / 2x 两档，2x 是假的 `scale(1.28)`。这里保留两档的视觉，
 * 但真正能否到 2x 由相机决定 —— 所以按钮**不做禁用态**：
 * `CameraInfo.zoomState` 是异步填的，靠它决定灰度会在某些启动里把可用的
 * 2x 画成不可用（真机实测：同一台机器支持 20x，界面却把 2x 画灰）。
 * 点击后由调用方按真实上限 clamp，并回报实际倍率。
 */
@Composable
fun ViewfinderZoom(
    zoom: Float,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** 真实光学档位（D-044）。为空表示探不到，退回 1x / 2x 数码档。 */
    stops: List<LensStop> = emptyList(),
) {
    val spec = LocalFilmTheme.current
    // 探不到物理镜头时不显示任何光学倍率：写死的 1x/2x 至少是"数码变焦的档位"，
    // 而编一个 0.6x/3x 出来就是假的。
    val items = stops.ifEmpty { listOf(LensStop(1f), LensStop(2f)) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEach { stop ->
            val active = abs(zoom - stop.zoomRatio) < 0.05f
            // 40dp 视觉塞进 48dp 热区。三档竖排共 144dp，取景器右缘放得下。
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) Color.White.copy(alpha = 0.92f) else spec.palette.chipFill,
                        )
                        .border(
                            1.dp,
                            if (active) Color.White.copy(alpha = 0.92f) else spec.palette.hairline,
                            CircleShape,
                        )
                        .clickable { onSelect(stop.zoomRatio) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stop.ratioLabel,
                            color = if (active) Color(0xFF151515) else spec.palette.ink,
                            fontSize = spec.type.monoSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        // 等效焦距只在真有传感器尺寸时才有值；没有就只显示倍率，
                        // 不要用"标准镜头"之类的词糊弄过去。
                        stop.focalLabel?.let { label ->
                            Text(
                                text = label,
                                color = if (active) {
                                    Color(0xFF151515).copy(alpha = 0.62f)
                                } else {
                                    spec.palette.ink
                                },
                                fontSize = spec.type.label,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}
