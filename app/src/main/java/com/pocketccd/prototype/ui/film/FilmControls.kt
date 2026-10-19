package com.pocketccd.prototype.ui.film

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * The shutter. Three stacked circles, exactly like the design:
 *
 * 1. `sweepGradient` ring — this is what makes it read as machined aluminium
 *    rather than a grey circle. Compose has no `conic-gradient`, but
 *    `Brush.sweepGradient` is the same thing.
 * 2. Radial face, lit from the upper-left (34%, 28%).
 * 3. A small red dot with a faint halo — the only saturated colour on screen.
 */
@Composable
fun FilmShutter(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spec = LocalFilmTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(120),
        label = "shutter-press",
    )

    Box(
        modifier = modifier
            .size(spec.metrics.shutter)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.35f
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .drawBehind {
                val rad = size.minDimension / 2f
                // Ring width is what sells "metal"; 4dp vanished at 3x density.
                val ringWidth = 6.dp.toPx()
                val faceR = rad - ringWidth
                val c = Offset(size.width / 2f, size.height / 2f)

                drawCircle(
                    brush = Brush.sweepGradient(spec.palette.shutterRing, center = c),
                    radius = rad,
                )
                drawCircle(
                    color = Color(0x40000000),
                    radius = rad,
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = spec.palette.shutterFace,
                        center = Offset(size.width * 0.34f, size.height * 0.28f),
                        radius = faceR * 1.45f,
                    ),
                    radius = faceR,
                )
                // Concave rim: the face sits *below* the ring, so its edge darkens.
                // Keep the band narrow — at 0.70 it covered most of the face and
                // the whole button read grey instead of white.
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to Color.Transparent,
                        0.86f to Color.Transparent,
                        1.0f to Color(0x33000000),
                        center = c,
                        radius = faceR,
                    ),
                    radius = faceR,
                )
                // 红点直接落在白色面上。原先这里有一圈 accent 色 12% 的晕
                // （radius 11dp），在白底上混成粉色光晕，看着像"按钮已被按下"。
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = spec.palette.shutterDot,
                        center = Offset(size.width * 0.47f, size.height * 0.45f),
                        radius = 9.dp.toPx(),
                    ),
                    radius = 6.dp.toPx(),
                    center = c,
                )
            },
    )
}

/**
 * 36dp circular control in a 48dp touch target.
 *
 * This split is deliberate (spec §2.4): the design's 36px button is below the
 * 48dp minimum, so the visible circle keeps its size while the hit area grows.
 */
@Composable
fun FilmRoundButton(
    icon: FilmIconKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    iconSize: androidx.compose.ui.unit.Dp = 17.dp,
) {
    val spec = LocalFilmTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = tween(180),
        label = "round-button-press",
    )

    val fill = if (active) spec.palette.accent else spec.palette.controlFill
    val stroke = if (active) spec.palette.accent else spec.palette.hairline
    val tint = when {
        !enabled -> spec.palette.inkFaint
        active -> spec.palette.onAccent
        else -> spec.palette.inkMuted
    }

    Box(
        modifier = modifier.size(spec.metrics.touchTarget),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(spec.metrics.controlSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(fill)
                .border(spec.metrics.hairline, stroke, CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            FilmIcon(kind = icon, tint = tint, iconSize = iconSize)
        }
    }
}

/**
 * 50dp rounded tile used for the album thumbnail and the "change roll" action.
 * `content` is a slot so the caller can hand in a real thumbnail or an icon.
 */
@Composable
fun FilmActionTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int? = null,
    content: @Composable () -> Unit,
) {
    val spec = LocalFilmTheme.current
    val shape = RoundedCornerShape(12.dp)

    androidx.compose.foundation.layout.Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(spec.metrics.thumbnail)
                    .clip(shape)
                    .background(spec.palette.controlFill)
                    .border(spec.metrics.hairline, spec.palette.hairlineStrong, shape),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(spec.palette.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Text(
                        text = badge.toString(),
                        color = spec.palette.onAccent,
                        fontSize = spec.type.monoSmall,
                    )
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        androidx.compose.material3.Text(
            text = label,
            color = spec.palette.inkFaint,
            fontSize = spec.type.label,
        )
    }
}
