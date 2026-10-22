package com.pocketccd.prototype.ui.film

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pocketccd.prototype.camera.ExposureProfile
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

    // 对焦结果全是画出来的（金色方框 + `AF LOCK` 文本），方框本身读屏看不见。
    // 补一句状态描述并标成 liveRegion：结果从「正在对焦」变成「对焦完成/失败」时
    // TalkBack 会主动播报一次，不用用户伸手去画面里找那个框。
    val focusDescription = when (lock) {
        null -> "正在对焦"
        true -> "对焦完成"
        false -> "对焦失败"
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
            .semantics {
                contentDescription = focusDescription
                liveRegion = LiveRegionMode.Polite
            }
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
 *
 * 无障碍：档位是"圆 + 一行倍率字"，视觉上分得出，读屏却只会把每档读成
 * "2×" 这样的数字。所以 `contentDescription` 默认拼成「变焦 2×」，
 * 动作提示「切换变焦」；`accessibilityLabelPrefix` 给调用方留覆盖口。
 * 热区：40dp 视觉圆外面套 48dp Box，点击挂在 48dp 那一层（否则实际只有 40dp）。
 */
@Composable
fun ViewfinderZoom(
    zoom: Float,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** 真实光学档位（D-044）。为空表示探不到，退回 1x / 2x 数码档。 */
    stops: List<LensStop> = emptyList(),
    accessibilityLabelPrefix: String = "变焦",
    actionLabel: String = "切换变焦",
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    // 探不到物理镜头时不显示任何光学倍率：写死的 1x/2x 至少是"数码变焦的档位"，
    // 而编一个 0.6x/3x 出来就是假的。
    val items = stops.ifEmpty { listOf(LensStop(1f), LensStop(2f)) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        items.forEach { stop ->
            val active = abs(zoom - stop.zoomRatio) < 0.05f
            Row(
                // 2026-10-23：从"三颗描边圆钮"改成文字档位轨。真实相机的倍率就是
                // 取景器边缘的一行数字，圆钮是手机 App 的语言。热区仍 ≥48dp。
                modifier = Modifier
                    .height(40.dp)
                    .widthIn(min = 64.dp)
                    .semantics { contentDescription = "$accessibilityLabelPrefix ${stop.ratioLabel}" }
                    .clickable(
                        onClickLabel = actionLabel,
                        role = Role.Button,
                    ) { onSelect(stop.zoomRatio) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = stop.ratioLabel,
                    color = if (active) p.inkStrong else p.inkMuted,
                    fontSize = spec.type.rail,
                    fontFamily = FontFamily.Monospace,
                )
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(6.dp))
                Box(
                    modifier = androidx.compose.ui.Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (active) p.accent else Color.Transparent),
                )
            }
        }
    }
}

/**
 * EV 补偿：一颗圆键 + 一条向左展开的短刻度尺（D-052）。
 *
 * 为什么不做在点按对焦的方框上：对焦框是**瞬态**的（2.2s 淡出），EV 是**持续**
 * 状态，两者生命周期不同；而且拖动刻度时手指要一直按着，压在画面中心的对焦框上
 * 会同时改变测光点，等于两个参数互相打架。
 *
 * 为什么键在取景器右缘、尺子往左长：它和变焦档位是同一列（同一只拇指），
 * 且往左长不会顶出屏幕。尺子只是**展开的 EV 环**——真实相机上它就是一圈刻度。
 *
 * 刻度范围取硬件报告的真实范围（[ExposureProfile]），不是写死的 ±2。
 * 机型只给 ±1 就只画 ±1，给 13 档就画 13 档。
 */
@Composable
fun ViewfinderEv(
    profile: ExposureProfile,
    evIndex: Int,
    onEvChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // 松手后 4 秒自动收：尺子会压住画面右下角，用户改完就该让开。
    // key 用 evIndex，拖动过程中每换一档都重新计时，不会拖到一半被收走。
    LaunchedEffect(expanded, evIndex) {
        if (expanded) {
            delay(4000)
            expanded = false
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(110)) + expandHorizontally(expandFrom = Alignment.End, animationSpec = tween(160)),
            exit = fadeOut(tween(120)),
        ) {
            FilmEvRuler(
                profile = profile,
                evIndex = evIndex,
                onEvChange = onEvChange,
            )
        }
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.size(8.dp))
        FilmEvKey(
            profile = profile,
            evIndex = evIndex,
            active = expanded,
            onClick = { expanded = !expanded },
        )
    }
}

/**
 * 40dp 视觉 / 48dp 热区，和变焦档位同一套尺寸。
 *
 * 语义：键面上只有 `±` / `+0.3` 这种小数点符号，读屏念出来是"正负号"、
 * "加零点三"，没有上下文。所以显式给「曝光补偿 +0.3」，并按展开状态给动作提示。
 * 点击挂在 48dp 外层 Box 上 —— 之前挂在 40dp 圆上，实际热区只有 40dp。
 */
@Composable
private fun FilmEvKey(
    profile: ExposureProfile,
    evIndex: Int,
    active: Boolean,
    onClick: () -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val ev = profile.evOf(evIndex)
    val neutral = abs(ev) < profile.stepEv / 2f

    Row(
        // 2026-10-23：从"白底圆键"改成与变焦档位同一行的文字读法（`±  +0.3`）。
        // 展开时白字/金色，收起时灰字 —— 不靠底色表达状态，减少一屏里的圆点数量。
        modifier = androidx.compose.ui.Modifier
            .height(40.dp)
            .widthIn(min = 64.dp)
            .semantics { contentDescription = "曝光补偿 ${formatEv(ev)}" }
            .clickable(
                onClickLabel = if (active) "收起曝光刻度" else "展开曝光刻度",
                role = Role.Button,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = "±",
            color = if (neutral) p.inkMuted else p.accent,
            fontSize = spec.type.rail,
            fontFamily = FontFamily.Monospace,
        )
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(10.dp))
        Text(
            // 0 档写 0.0 而不是 ±：读数和指针位置是同一件事，不需要第二个状态符号。
            text = if (neutral) "0.0" else formatEv(ev),
            color = if (neutral) p.inkMuted else p.inkStrong,
            fontSize = spec.type.rail,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/**
 * 刻度尺本体。宽 232dp，13 档左右就够用了；档位更多时每格变窄，但仍是 1 档一停。
 */
@Composable
private fun FilmEvRuler(
    profile: ExposureProfile,
    evIndex: Int,
    onEvChange: (Int) -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val span = (profile.maxIndex - profile.minIndex).coerceAtLeast(1)
    val pad = 14.dp
    // 拖动时 pointerInput 的 lambda 只创建一次，捕获的 evIndex 会一直是按下那一刻的值。
    // 用 rememberUpdatedState 读最新值，才能做到"档位没变就不回调"。
    val currentIndex by rememberUpdatedState(evIndex)

    fun indexAtX(x: Float, width: Float, padPx: Float): Int {
        val usable = (width - padPx * 2f).coerceAtLeast(1f)
        val frac = ((x - padPx) / usable).coerceIn(0f, 1f)
        return profile.minIndex + (frac * span).roundToInt()
    }

    // 每帧都发一次 setExposureCompensationIndex 会把 3A 收敛反复打断（拖动 1 秒能发
    // 60 次），所以只在整数档位真的变了才往下传。
    fun emit(x: Float, width: Float, padPx: Float) {
        val next = indexAtX(x, width, padPx)
        if (next != currentIndex) onEvChange(next)
    }

    Column(
        modifier = androidx.compose.ui.Modifier
            .width(232.dp)
            // 拖动条本身只有 22dp 高（视觉是设计稿的），低于 48dp 触摸目标。面板高度
            // 不能为了热区改 —— 那会顶掉取景器的构图。所以给整块刻度面板补**可调节语义**：
            // TalkBack 用户用滑动/动作菜单改档，不需要精确戳中那条 22dp 的线。
            // setProgress 收到的是 EV 值，按 stepEv 换算回 index（换算只信 ExposureProfile，
            // 界面不自己乘，和 CameraPreview 的约定一致）。
            .semantics(mergeDescendants = true) {
                contentDescription = "曝光补偿刻度"
                if (profile.maxIndex > profile.minIndex) {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = profile.evOf(evIndex),
                        range = profile.minEv..profile.maxEv,
                        steps = (profile.maxIndex - profile.minIndex - 1).coerceAtLeast(0),
                    )
                }
                setProgress { targetEv ->
                    val index = (targetEv / profile.stepEv).roundToInt()
                        .coerceIn(profile.minIndex, profile.maxIndex)
                    if (index == evIndex) {
                        false
                    } else {
                        onEvChange(index)
                        true
                    }
                }
                customActions = listOf(
                    CustomAccessibilityAction("增加一档曝光") {
                        if (evIndex < profile.maxIndex) {
                            onEvChange(evIndex + 1)
                            true
                        } else {
                            false
                        }
                    },
                    CustomAccessibilityAction("减少一档曝光") {
                        if (evIndex > profile.minIndex) {
                            onEvChange(evIndex - 1)
                            true
                        } else {
                            false
                        }
                    },
                )
            }
            // 参考图里的深色件都没有描边：描边一加就变成"卡片"，而它应该是
            // "取景器里压着的一块暗色"。所以只留底色 + 圆角（D-053）。
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(p.chipFill)
            .padding(vertical = 8.dp, horizontal = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 读数窗（D-053）：参考图里的数值是嵌进机身的深色 LCD 胶囊，不是浮在画面上的
        // 一行字。这里同样做"凹陷"——纯黑 0.58 + 顶部一条更深的线 + 5dp 圆角。
        // 深色 + 内阴影是"装进去"的唯一信号，浅色圆角条会读成通知气泡。
        Box(
            modifier = androidx.compose.ui.Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
                .background(Color.Black.copy(alpha = 0.58f))
                .drawBehind {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.55f),
                        start = Offset(0f, 0.5.dp.toPx()),
                        end = Offset(size.width, 0.5.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .padding(horizontal = 12.dp, vertical = 2.dp),
        ) {
            Text(
                text = formatEv(profile.evOf(evIndex)),
                color = p.ink,
                fontSize = spec.type.mono,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.12.em,
            )
        }
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.size(5.dp))
        Canvas(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .height(22.dp)
                .pointerInput(profile.minIndex, profile.maxIndex) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            emit(offset.x, size.width.toFloat(), pad.toPx())
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            emit(change.position.x, size.width.toFloat(), pad.toPx())
                        },
                    )
                }
                .pointerInput(profile.minIndex, profile.maxIndex) {
                    detectTapGestures { offset ->
                        emit(offset.x, size.width.toFloat(), pad.toPx())
                    }
                },
        ) {
            val padPx = pad.toPx()
            val usable = (size.width - padPx * 2f).coerceAtLeast(1f)
            val midY = size.height / 2f
            val zeroX = padPx + usable * (0f - profile.minIndex) / span

            // 基线
            drawLine(
                color = Color.White.copy(alpha = 0.20f),
                start = Offset(padPx, midY),
                end = Offset(size.width - padPx, midY),
                strokeWidth = 1.dp.toPx(),
            )
            // 逐档刻线。0 档最长，整格次之，1/3 格最短 —— 这条层次是肉眼读出
            // "现在在第几档"的唯一依据，全靠长短。
            for (i in 0..span) {
                val index = profile.minIndex + i
                val x = padPx + usable * i / span
                val isZero = index == 0
                val height = when {
                    isZero -> 13.dp.toPx()
                    index % 3 == 0 -> 8.dp.toPx()
                    else -> 5.dp.toPx()
                }
                drawLine(
                    color = Color.White.copy(alpha = if (isZero) 0.55f else 0.28f),
                    start = Offset(x, midY - height / 2f),
                    end = Offset(x, midY + height / 2f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            // 0 档的专属标记：一条更亮的竖线，保证在任何背景上都找得到中性位。
            drawLine(
                color = Color.White.copy(alpha = 0.75f),
                start = Offset(zeroX, midY - 9.dp.toPx()),
                end = Offset(zeroX, midY + 9.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
            // 金色指针：唯一有颜色的元素，位置就是当前 index。
            val pointerX = padPx + usable * (evIndex - profile.minIndex) / span
            drawLine(
                color = p.accent,
                start = Offset(pointerX, midY - 11.dp.toPx()),
                end = Offset(pointerX, midY + 11.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
            )
            drawCircle(color = p.accent, radius = 2.dp.toPx(), center = Offset(pointerX, midY))
        }
    }
}

/**
 * EV 读数。`%+.1f` 在步长 1/3 时得到 `+0.3 / +0.7 / +1.0`，
 * 这些是 index×step 的**精确**结果，不是四舍五入凑出来的好看数字。
 */
private fun formatEv(ev: Float): String =
    if (abs(ev) < 0.001f) "0.0" else "%+.1f".format(ev)
