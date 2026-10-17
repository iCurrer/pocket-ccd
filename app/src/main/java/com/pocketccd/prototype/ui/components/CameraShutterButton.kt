package com.pocketccd.prototype.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.theme.PocketCcdColors
import com.pocketccd.prototype.ui.theme.PocketCcdSizes

@Composable
fun CameraShutterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "shutter-scale",
    )
    val glow by animateDpAsState(
        targetValue = if (pressed) 4.dp else 14.dp,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "shutter-glow",
    )
    val description = stringResource(R.string.camera_shutter)

    Box(
        modifier = modifier
            .size(PocketCcdSizes.shutterOuter)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = glow,
                shape = CircleShape,
                clip = false,
                ambientColor = PocketCcdColors.Accent.copy(alpha = 0.35f),
                spotColor = PocketCcdColors.Accent.copy(alpha = 0.45f),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(PocketCcdSizes.shutterRing)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PocketCcdColors.ShutterOuter,
                            Color(0xFF141417),
                        ),
                    ),
                )
                .border(1.dp, Color(0x33FFFFFF), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(PocketCcdSizes.shutter)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PocketCcdColors.ShutterInner,
                            PocketCcdColors.ShutterInnerPressed,
                        ),
                    ),
                )
                .border(1.dp, Color(0x55FFFFFF), CircleShape),
        )
        Canvas(modifier = Modifier.size(PocketCcdSizes.shutter)) {
            drawArc(
                color = PocketCcdColors.ShutterHighlight,
                startAngle = 200f,
                sweepAngle = 85f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(
                color = PocketCcdColors.ShutterHighlight,
                radius = 3.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(
                    x = size.width * 0.34f,
                    y = size.height * 0.28f,
                ),
            )
        }
    }
}
