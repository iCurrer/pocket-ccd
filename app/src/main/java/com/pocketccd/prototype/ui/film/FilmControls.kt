package com.pocketccd.prototype.ui.film

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * ============================ 交互语法（2026-10-23） ============================
 *
 * 一屏里的"可点东西"按**层级**分四类，每类只有一种尺寸/状态/反馈，不允许临时发明：
 *
 * 1. **主操作**：快门（88dp 热区，唯一会闪白/冲洗的动作）；
 * 2. **图标操作**：顶栏/页头的返回、闪光灯、九宫格、相册、设置（48dp 热区 / 40dp 视觉圆）；
 * 3. **文字操作**：低频、破坏性或确认类（成片页重拍/回相册、权限去设置、重新扫描）
 *    （48dp 高，主/次两种，见 [FilmButton]）；
 * 4. **可选项**：筛选 chip、档位轨、面板副键（热区≥48dp，视觉更小）。
 *
 * 所有可点控件的**按下反馈统一三件事**（缺一件就算没做完）：
 * - 视觉：0.96 缩放（快门 0.94 + 1dp 下压），并叠一层 5% 白/黑覆盖；
 * - 触觉：按下瞬间 `KEYBOARD_TAP` 轻震动（禁用态不震）；
 * - 时序：按下 90ms 到位、松开 spring 回弹；状态切换（开/关、选中）用 160ms 颜色过渡。
 *
 * 为什么不在深色相机界面用 Material ripple：涟漪在近黑底上几乎不可见，而且它是
 * "App 组件"的语言；这台相机的反馈应该像机械键——先缩、再回弹。
 * =============================================================================
 */

/**
 * 统一按压反馈：缩放 + 覆盖层 + 轻震动 + 语义。
 *
 * 用法：先 `clip(shape)`/`background()`，再 `.filmClickable { … }`，覆盖层会被裁进形状。
 *
 * @param scaleTo 按下时缩到多少。图标/文字 0.96，可选项 0.97，列表行 1.0（只叠色）。
 * @param overlay 覆盖色：深色面上用白，浅色金属面上用黑。
 * @param haptic 列表行这类高频控件可关掉，避免一路嗡嗡响。
 */
@Composable
fun Modifier.filmClickable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClickLabel: String? = null,
    scaleTo: Float = 0.96f,
    overlay: Color = Color.White,
    overlayAlpha: Float = 0.05f,
    haptic: Boolean = true,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) scaleTo else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 900f),
        label = "film-press-scale",
    )
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed && enabled && haptic) {
            runCatching { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
        }
    }
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (pressed && enabled) drawRect(overlay.copy(alpha = overlayAlpha))
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}

/** 按钮的语气。主 = 实心浅色（一屏最多一个）；次 = 玻璃；幽灵 = 只有字。 */
enum class FilmButtonStyle { Primary, Secondary, Ghost }

/**
 * 文字按钮（主/次/幽灵）。用于成片页、权限空态、胶卷库空态等"读得出来"的动作。
 *
 * 与 [FilmRoundButton] 的分工：图标说不清的动作必须写出来，且写出来的动作不能做成
 * 一个圆。Sizes: 主/次 48dp 高、圆角 12dp（拟物机身上的"键"），幽灵 40dp 高无底。
 */
@Composable
fun FilmButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: FilmButtonStyle = FilmButtonStyle.Secondary,
    enabled: Boolean = true,
    leadingIcon: FilmIconKind? = null,
    pill: Boolean = false,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape: Shape = if (pill) CircleShape else RoundedCornerShape(12.dp)
    val fill = when (style) {
        FilmButtonStyle.Primary -> if (enabled) p.textPrimary else p.textPrimary.copy(alpha = 0.25f)
        FilmButtonStyle.Secondary -> p.controlFill
        FilmButtonStyle.Ghost -> Color.Transparent
    }
    val textColor = when (style) {
        FilmButtonStyle.Primary -> p.appBg
        FilmButtonStyle.Secondary, FilmButtonStyle.Ghost -> p.inkStrong
    }
    val height: Dp = if (style == FilmButtonStyle.Ghost) 40.dp else 48.dp

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(fill)
            .then(
                if (style == FilmButtonStyle.Secondary) {
                    Modifier.border(spec.metrics.hairline, p.hairlineStrong, shape)
                } else {
                    Modifier
                },
            )
            .filmClickable(
                onClick = onClick,
                enabled = enabled,
                overlay = if (style == FilmButtonStyle.Primary) Color.Black else Color.White,
                overlayAlpha = if (style == FilmButtonStyle.Primary) 0.06f else 0.06f,
            )
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            if (leadingIcon != null) {
                FilmIcon(kind = leadingIcon, tint = textColor, iconSize = 14.dp)
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = label,
                color = textColor,
                fontSize = spec.type.body,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * The shutter, inset into a well cut in the body panel (D-051).
 *
 * The button itself is three stacked circles, exactly like the design:
 *
 * 1. `sweepGradient` ring — this is what makes it read as machined aluminium
 *    rather than a grey circle. Compose has no `conic-gradient`, but
 *    `Brush.sweepGradient` is the same thing.
 * 2. Radial face, lit from the upper-left (34%, 28%).
 * 3. A small red dot with a faint halo — the only saturated colour on screen.
 *
 * The well around it is what changed in the skeuomorphic pass: a 5dp ring of
 * [FilmPalette.wellFill] whose *upper* wall is shaded (light comes from above,
 * so the inside of a recess goes dark at the top) with a 1px catch-light along
 * the *lower* rim. Those two gradients in opposite directions are the entire
 * difference between "a circle drawn on a panel" and "a button in a hole".
 *
 * The click target is the well, not the button: a finger aiming at a physical
 * shutter lands on the rim as often as the face.
 *
 * 无障碍（T1-013）：快门是整屏最关键的控件，但按钮上没有一个字。所以语义必须
 * 由组件自己给：`contentDescription` 默认「快门」，动作提示「拍摄」（读屏会念
 * "快门，按钮，双击即可拍摄"）。两个参数都有默认值，调用点不用改。
 * 热区 = 78dp 快门 + 两侧各 5dp 凹槽 = 88dp，远超 48dp 下限。
 */
@Composable
fun FilmShutter(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accessibilityLabel: String = "快门",
    actionLabel: String = "拍摄",
) {
    val spec = LocalFilmTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "shutter-press",
    )
    // 机械键的第一层反馈是触觉：按下瞬间轻震，比任何动画都先被感知到。
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed && enabled) {
            runCatching { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
        }
    }

    Box(
        modifier = modifier
            .size(spec.metrics.shutter + spec.metrics.panelWell * 2)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .semantics { contentDescription = accessibilityLabel }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClickLabel = actionLabel,
                role = Role.Button,
                onClick = onClick,
            )
            .drawBehind { drawShutterWell(spec) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(spec.metrics.shutter)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    // 1dp 下压：真实快门有行程，纯缩放读起来像"图片变小"。
                    translationY = if (pressed) 1.dp.toPx() else 0f
                }
                .drawBehind { drawShutterButton(spec, pressed = pressed) },
        )
    }
}

/**
 * 快门凹槽。抽成独立函数只是为了让上面那个 `Modifier` 链读得下去 ——
 * 它没有任何参数以外的状态。
 */
private fun DrawScope.drawShutterWell(spec: FilmThemeSpec) {
    val p = spec.palette
    val rad = size.minDimension / 2f
    val c = Offset(size.width / 2f, size.height / 2f)

    drawCircle(color = p.wellFill, radius = rad)

    // 凹槽上壁背光。gradient 的坐标是绘制区的，不是圆的，所以 startY=0 正好是
    // 凹槽顶端 —— 越往下越亮，和"光从上方来"一致。
    drawCircle(
        brush = Brush.verticalGradient(
            0.0f to Color.Black.copy(alpha = 0.45f),
            0.42f to Color.Transparent,
            startY = 0f,
            endY = size.height,
        ),
        radius = rad,
    )
    // 外缘一圈暗线：没有它，凹槽底和面板之间没有断口，读成一块贴片。
    drawCircle(
        color = Color.Black.copy(alpha = 0.45f),
        radius = rad,
        style = Stroke(1.dp.toPx()),
    )
    // 凹槽下缘内侧接光。30°→150° 扫过下半圈（Canvas 角度顺时针，90° 是正下方）。
    val litR = rad - 1.5.dp.toPx()
    drawArc(
        color = Color.White.copy(alpha = 0.10f),
        startAngle = 30f,
        sweepAngle = 120f,
        useCenter = false,
        topLeft = Offset(c.x - litR, c.y - litR),
        size = Size(litR * 2f, litR * 2f),
        style = Stroke(1.dp.toPx()),
    )
}

/** 快门本体（金属环 + 暖白面 + 红点），画在自己的 72dp 方框里。按下时环变亮、面变暗。 */
private fun DrawScope.drawShutterButton(spec: FilmThemeSpec, pressed: Boolean) {
    val rad = size.minDimension / 2f
    val ringWidth = 6.dp.toPx()
    val faceR = rad - ringWidth
    val c = Offset(size.width / 2f, size.height / 2f)

    // 香槟金 + 暖灰扫掠。比旧版黑白高对比安静得多：真机 3x 下高对比会读成"脏边"。
    drawCircle(
        brush = Brush.sweepGradient(spec.palette.shutterRing, center = c),
        radius = rad,
    )

    // 滚花（D-053/2026-10-23 修订）：90 齿、明暗差只有 10%/4%。
    // 旧版 72 齿 + 18%/22% 在真机截图上像一圈"齿轮"，不是"手握的滚花"。
    val teeth = 90
    val toothInner = faceR + 1.dp.toPx()
    val toothOuter = rad - 0.5.dp.toPx()
    for (i in 0 until teeth) {
        val angle = i / teeth.toFloat() * 2f * PI.toFloat()
        val cos = cos(angle)
        val sin = sin(angle)
        drawLine(
            color = if (i % 2 == 0) {
                Color.White.copy(alpha = 0.10f)
            } else {
                Color.Black.copy(alpha = 0.05f)
            },
            start = Offset(c.x + cos * toothInner, c.y + sin * toothInner),
            end = Offset(c.x + cos * toothOuter, c.y + sin * toothOuter),
            strokeWidth = 1.dp.toPx(),
        )
    }

    // 环外缘一圈极细的金色：全屏唯一"贵"的材质暗示，只在快门环上出现一次。
    // 按下时这圈金线变亮 —— 机械键被按下去的瞬间，金属边缘会吃光。
    drawCircle(
        color = spec.palette.accent.copy(alpha = if (pressed) 0.62f else 0.30f),
        radius = rad - 0.5.dp.toPx(),
        style = Stroke(width = if (pressed) 1.5.dp.toPx() else 1.dp.toPx()),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = spec.palette.shutterFace,
            center = Offset(size.width * 0.36f, size.height * 0.30f),
            radius = faceR * 1.45f,
        ),
        radius = faceR,
    )
    // 面比环低一档：边缘一圈很窄的暗边，避免整颗按钮读成一个亮圆盘。
    // 按下时再压暗 6%：手指压住的地方收光，这是"行程"的第二层视觉。
    drawCircle(
        brush = Brush.radialGradient(
            0.0f to Color.Transparent,
            0.88f to Color.Transparent,
            1.0f to Color(if (pressed) 0x55000000 else 0x33000000),
            center = c,
            radius = faceR,
        ),
        radius = faceR,
    )
    // 顶部 1px 接光：光从上方来，圆面在 12 点方向最亮。
    drawArc(
        color = Color.White.copy(alpha = 0.45f),
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(c.x - faceR + 2.dp.toPx(), c.y - faceR + 2.dp.toPx()),
        size = Size((faceR - 2.dp.toPx()) * 2f, (faceR - 2.dp.toPx()) * 2f),
        style = Stroke(1.dp.toPx()),
    )
    // 红点缩小到 4dp：它是"录制/快门"符号，不是按钮上的装饰灯。
    drawCircle(
        brush = Brush.radialGradient(
            colors = spec.palette.shutterDot,
            center = Offset(size.width * 0.47f, size.height * 0.46f),
            radius = 7.dp.toPx(),
        ),
        radius = 4.dp.toPx(),
        center = c,
    )
}

/**
 * 36dp 视觉圆钮 + **48dp 热区**。
 *
 * 视觉尺寸必须保持 36dp（设计稿如此），但 48dp 的最小触摸目标不能只是"外面套了一个
 * 48dp 的 Box"：2026-10-22 的无障碍审计发现 `clickable` 之前挂在里面那个 36dp 的
 * 圆上，外面 48dp 的 Box 只负责居中，**压根不接收点击**。看起来 48dp、实际 36dp，
 * 这正是 Accessibility Scanner 会报"触摸目标过小"的典型形态。
 *
 * 现在点击/按压/hover 全部挂在 48dp 的外层 Box 上，内层圆只负责画。视觉零变化，
 * 拇指的命中率变了。
 *
 * 语义：图标本身没有文字，`accessibilityLabel` / `actionLabel` 的默认值由
 * [FilmIconKind.accessibilityLabel] / [FilmIconKind.actionLabel] 按图标 + 开关态推导
 * （如闪光灯会读"闪光灯已开/已关"）。调用点不传也能拿到正确语义；要覆盖时显式传参。
 */
@Composable
fun FilmRoundButton(
    icon: FilmIconKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    iconSize: androidx.compose.ui.unit.Dp = 17.dp,
    accessibilityLabel: String = icon.accessibilityLabel(active),
    actionLabel: String = icon.actionLabel(),
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    // 状态切换用 160ms 颜色过渡，而不是瞬间换色：开/关是"硬件灯亮了"，
    // 瞬间跳变读起来像开关坏了。
    val fill by animateColorAsState(
        targetValue = if (active && enabled) p.accent.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = tween(160),
        label = "icon-button-fill",
    )
    val tint by animateColorAsState(
        targetValue = when {
            !enabled -> p.inkFaint
            active -> p.accent
            else -> p.inkMuted
        },
        animationSpec = tween(160),
        label = "icon-button-tint",
    )

    Box(
        modifier = modifier
            .size(spec.metrics.touchTarget)
            .clip(CircleShape)
            .filmClickable(
                onClick = onClick,
                enabled = enabled,
                onClickLabel = actionLabel,
                // 显式传 role：这是「纯图标控件必须报按钮」的契约（静态测试盯着）。
                role = Role.Button,
            )
            .semantics { contentDescription = accessibilityLabel },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(fill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FilmIcon(kind = icon, tint = tint, iconSize = iconSize)
        }
    }
}

/**
 * 底盖上的**凹凸实体键**（相册 / 换卷，2026-10-23 第三版）。
 *
 * 前两版都错：第一版是"凹槽里的圆键"（第三套圆形控件），第二版是"幽灵图标"
 * （只剩文字，失去实体感）。相机机身上的键应该是**凸起的块**：
 *
 * - 面上竖直渐变（顶受光 → 底沉），顶边 1px 高光、底边 1px 暗线；
 * - 键下方 2dp 黑影，让它"坐"在机身上而不是贴上去；
 * - 按下时整体**下沉 1.6dp**、渐变压平、顶边高光消失、底影收缩 —— 这是"按进去"的全部；
 * - 图标 + 丝印标签都刻在键面上。
 */
@Composable
fun FilmBodyKey(
    label: String,
    icon: FilmIconKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accessibilityLabel: String = label,
    actionLabel: String = "打开$label",
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val travel by animateFloatAsState(
        targetValue = if (pressed) 1.6f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 760f),
        label = "body-key-travel",
    )
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed) {
            runCatching { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
        }
    }
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .size(width = 84.dp, height = 54.dp)
            .graphicsLayer { translationY = travel.dp.toPx() }
            .clip(shape)
            .drawBehind {
                val r = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                drawRoundRect(
                    color = Color.Black.copy(alpha = if (pressed) 0.25f else 0.45f),
                    topLeft = Offset(0f, 1.5.dp.toPx()),
                    size = Size(size.width, size.height),
                    cornerRadius = r,
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = if (pressed) {
                            listOf(Color(0xFF151719), Color(0xFF101214))
                        } else {
                            listOf(Color(0xFF262A2E), Color(0xFF121416))
                        },
                        startY = 0f,
                        endY = size.height,
                    ),
                    cornerRadius = r,
                )
                val w = 1.dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = if (pressed) 0.02f else 0.12f),
                    start = Offset(14.dp.toPx(), w / 2f),
                    end = Offset(size.width - 14.dp.toPx(), w / 2f),
                    strokeWidth = w,
                )
                drawLine(
                    color = Color.Black.copy(alpha = if (pressed) 0.75f else 0.55f),
                    start = Offset(14.dp.toPx(), size.height - w / 2f),
                    end = Offset(size.width - 14.dp.toPx(), size.height - w / 2f),
                    strokeWidth = w,
                )
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = actionLabel,
                onClick = onClick,
            )
            .semantics { contentDescription = accessibilityLabel },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilmIcon(
                kind = icon,
                tint = if (pressed) p.inkStrong else p.inkMuted,
                iconSize = 18.dp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                color = if (pressed) p.textSecondary else p.textTertiary,
                fontSize = spec.type.label,
                letterSpacing = 0.16.em,
                fontFamily = FontFamily.Default,
            )
        }
    }
}

/**
 * 机身底盖（D-051）。
 *
 * 一整块金属板，把快门、两个副键和曝光刻度槽装进去 —— 取景窗是"窗口"，这块是
 * "机身"，两者之间用一道缝（顶部 1px 高光 + 紧贴其下的暗线）分开。没有那道缝，
 * 面板就只是屏幕底部一条深色区域，白做。
 *
 * 拉丝纹理用 28 条错开的横线，不是满铺细纹：满铺在 3x 屏上会摩尔纹，而且每帧
 * 上百条 drawLine 只为了一点几乎看不见的质感。线段参数在 [drawWithCache] 里算一次，
 * 只在面板尺寸变化时重建。
 *
 * 面板自己吃下导航栏高度的空白（调用方在 content 末尾放
 * `windowInsetsBottomHeight`），否则金属板会在手势条上方断掉，露出底下的页面底色。
 */
@Composable
fun FilmBodyPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Column(
        modifier = modifier
            .fillMaxWidth()
            // 顶角 6dp 而不是 18dp：底盖是"机身的一部分"，圆角一大就读成一张浮卡。
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            .drawWithCache {
                val gap = 6.dp.toPx()
                val streaks = ArrayList<FloatArray>()
                var y = gap
                var seed = 20261021L
                while (y < size.height) {
                    seed = seed * 6364136223846793005L + 1442695040888963407L
                    val s = (seed ushr 33).toInt() and 0x7fffffff
                    val start = (s % 26) / 100f
                    val len = 0.55f + ((s / 26) % 40) / 100f
                    streaks.add(floatArrayOf(y, start, (start + len).coerceAtMost(1f)))
                    y += gap
                }
                onDrawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            p.panelMetal,
                            startY = 0f,
                            endY = size.height,
                        ),
                    )
                    // 拉丝更细更淡：满铺的强纹理在整块面板上会显脏。
                    streaks.forEachIndexed { index, line ->
                        drawLine(
                            color = if (index % 2 == 0) {
                                Color.White.copy(alpha = 0.015f)
                            } else {
                                Color.Black.copy(alpha = 0.03f)
                            },
                            start = Offset(size.width * line[1], line[0]),
                            end = Offset(size.width * line[2], line[0]),
                            strokeWidth = 1f,
                        )
                    }
                    // 缝：上面一条受光高光，紧跟着一条暗线。顺序不能反 ——
                    // 反了就变成"贴了一块板上去"，而不是"板从机身里切出来"。
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(0f, 0.5.dp.toPx()),
                        end = Offset(size.width, 0.5.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawLine(
                        color = Color.Black.copy(alpha = 0.55f),
                        start = Offset(0f, 2.dp.toPx()),
                        end = Offset(size.width, 2.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            },
        content = content,
    )
}

/**
 * 面板上的内嵌副键：34dp 视觉 + 48dp 热区，圆钮下面一行 11sp 丝印。
 *
 * 和 [FilmRoundButton] 的区别不是尺寸而是**方向**：那个是浮在页面上的圆钮（顶部亮、
 * 底部暗），这个是嵌进面板的键（顶部暗、底部亮）。两套高光方向必须相反，
 * 否则一眼看出是同一个组件换了颜色。
 *
 * 语义：丝印文字 `label` 就是最好的描述，所以 `accessibilityLabel` 默认继承它；
 * 但丝印是画了两遍的（暗底高光 + 正文），读屏会把合并后的子节点读成"相册 相册"，
 * 所以这里显式给 `contentDescription` —— 同一句话只读一次。
 * `actionLabel` 默认「打开$label」（双击即可打开相册 / 打开换卷）。
 * 热区是整个 64dp 宽的 Column（含丝印行），高度约 67dp，达标。
 */
@Composable
fun FilmPanelKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accessibilityLabel: String = label,
    actionLabel: String = "打开$label",
    // content 必须留在最后一个参数：取景页用尾随 lambda 传图标
    // （`FilmPanelKey(label = "相册", onClick = …) { FilmIcon(…) }`），
    // 把默认参数排在它后面会让尾随 lambda 绑错参数、调用点全部编译失败。
    // 无障碍参数因此排在 content 之前 —— 这也是"加默认值不能改调用点"的硬约束。
    content: @Composable () -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(110),
        label = "panel-key-press",
    )

    Column(
        modifier = modifier
            // 固定 64dp 宽：热区不能只裹住 34dp 的圆，也不能窄到标签放不下。
            .width(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .filmClickable(
                onClick = onClick,
                onClickLabel = actionLabel,
                overlayAlpha = 0.05f,
                role = Role.Button,
            )
            .semantics { contentDescription = accessibilityLabel },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(spec.metrics.touchTarget),
            contentAlignment = Alignment.Center,
        ) {
            // 2026-10-23：不再画"凹槽里的圆键"（那是第三套高光方向，三种圆形控件
            // 叠在一屏上最显廉价）。副键改为机身丝印式幽灵图标：只有图标和标签，
            // 按下去只有 0.92 缩放，不再有圆面。
            Box(
                modifier = Modifier
                    .size(spec.metrics.panelKey)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
        // 丝印（D-053）。参考里的标签不是"TextView 写了个字"，是印在机身上的油墨：
        // 这行字本身没有阴影，但它底下 1dp 处有一道极淡的高光——那是机身的受光面
        // 被字挡住之后露出来的边。少了这一层，标签就只是"界面文字"。
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.material3.Text(
                text = label,
                color = Color.White.copy(alpha = 0.07f),
                fontSize = spec.type.label,
                modifier = Modifier.offset(y = 1.dp),
            )
            androidx.compose.material3.Text(
                text = label,
                color = p.textTertiary,
                fontSize = spec.type.label,
            )
        }
    }
}

/**
 * 曝光刻度槽：刻在面板上的一道凹线，金色填充。
 *
 * 上沿一条暗线是唯一必需的：没有它这就是根 2dp 进度条；有了它才是"金属上刻出来的槽"。
 */
@Composable
fun FilmExposureSlot(fraction: Float, modifier: Modifier = Modifier) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(2.dp)
            .clip(CircleShape)
            .background(p.wellFill)
            .drawBehind {
                drawLine(
                    color = Color.Black.copy(alpha = 0.40f),
                    start = Offset(0f, 0.5.dp.toPx()),
                    end = Offset(size.width, 0.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(p.accent.copy(alpha = 0.55f)),
        )
    }
}
