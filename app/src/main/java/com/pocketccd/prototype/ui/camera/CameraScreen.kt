package com.pocketccd.prototype.ui.camera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.components.ToyRoundButton
import com.pocketccd.prototype.ui.theme.PocketCcdColors
import com.pocketccd.prototype.ui.theme.PocketCcdRadius
import com.pocketccd.prototype.ui.theme.PocketCcdSizes
import com.pocketccd.prototype.ui.theme.PocketCcdSpacing
import com.pocketccd.prototype.ui.theme.PocketCcdTheme
import com.pocketccd.prototype.ui.theme.PocketCcdType

/**
 * Retro red toy-camera front per the visual brief:
 * red plastic shell, heavy black viewfinder, green flash, orange shutter,
 * red cycle button, compact logo. The preview is static in phase 1; no
 * CameraX/Camera2 or permissions are wired yet.
 */
@Composable
fun CameraScreen(
    onShutter: () -> Unit,
    modifier: Modifier = Modifier,
    panelModifier: Modifier = Modifier,
) {
    var flashOn by remember { mutableStateOf(false) }
    var frontCamera by remember { mutableStateOf(false) }
    val flashAvailable = !frontCamera

    Box(
        modifier = panelModifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PocketCcdColors.RetroRedLight,
                        PocketCcdColors.RetroRed,
                        PocketCcdColors.RetroRedDeep,
                    ),
                ),
            ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = PocketCcdSpacing.xl,
                    vertical = PocketCcdSpacing.md,
                ),
        ) {
            val viewfinderSize = minOf(
                maxWidth - PocketCcdSpacing.xl,
                maxHeight * 0.52f,
            )
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                RetroViewfinder(size = viewfinderSize)
                Spacer(modifier = Modifier.height(PocketCcdSpacing.xl))
                RetroControls(
                    flashOn = flashOn,
                    flashAvailable = flashAvailable,
                    frontCamera = frontCamera,
                    onToggleFlash = {
                        if (flashAvailable) flashOn = !flashOn
                    },
                    onToggleCamera = {
                        val nextFront = !frontCamera
                        frontCamera = nextFront
                        if (nextFront) flashOn = false
                    },
                    onShutter = onShutter,
                )
                Spacer(modifier = Modifier.height(PocketCcdSpacing.lg))
                Text(
                    text = "POCKET CCD",
                    style = PocketCcdType.Brand.copy(
                        fontSize = 18.sp,
                        letterSpacing = 2.sp,
                    ),
                    color = PocketCcdColors.RetroCream,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RetroViewfinder(size: Dp) {
    val outerShape = RoundedCornerShape(PocketCcdRadius.body)
    val innerShape = RoundedCornerShape(PocketCcdRadius.xl)
    Box(
        modifier = Modifier
            .size(size)
            .shadow(
                elevation = 20.dp,
                shape = outerShape,
                clip = false,
                ambientColor = PocketCcdColors.RetroInk.copy(alpha = 0.42f),
                spotColor = PocketCcdColors.RetroInk.copy(alpha = 0.5f),
            )
            .clip(outerShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PocketCcdColors.RetroRedLight,
                        PocketCcdColors.RetroRed,
                        PocketCcdColors.RetroRedDeep,
                    ),
                ),
            )
            .border(
                width = 2.dp,
                color = Color.White.copy(alpha = 0.28f),
                shape = outerShape,
            )
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(innerShape)
                .background(PocketCcdColors.RetroBlack)
                .border(
                    width = 2.dp,
                    color = PocketCcdColors.RetroInk,
                    shape = innerShape,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(PocketCcdSpacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(PocketCcdRadius.md))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1B2C2A),
                                    Color(0xFF0B1211),
                                ),
                            ),
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(PocketCcdRadius.md),
                        ),
                ) {
                    StaticPreview()
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = PocketCcdSpacing.sm)
                    .size(9.dp)
                    .shadow(
                        elevation = 4.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = PocketCcdColors.RetroGreen.copy(alpha = 0.8f),
                        spotColor = PocketCcdColors.RetroGreen.copy(alpha = 0.9f),
                    )
                    .clip(CircleShape)
                    .background(PocketCcdColors.RetroGreen)
                    .border(
                        width = 1.dp,
                        color = PocketCcdColors.RetroInk.copy(alpha = 0.6f),
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun StaticPreview() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF5E7B71),
                    Color(0xFFB9A17E),
                ),
            ),
        )
        drawRect(
            color = Color(0xFF344D43),
            topLeft = Offset(0f, size.height * 0.62f),
            size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.38f),
        )
        drawCircle(
            color = Color(0xFFE8C56A).copy(alpha = 0.75f),
            radius = size.width * 0.055f,
            center = Offset(size.width * 0.7f, size.height * 0.28f),
        )
    }
}

@Composable
private fun RetroControls(
    flashOn: Boolean,
    flashAvailable: Boolean,
    frontCamera: Boolean,
    onToggleFlash: () -> Unit,
    onToggleCamera: () -> Unit,
    onShutter: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToyRoundButton(
            onClick = onToggleFlash,
            contentDescription = stringResource(
                when {
                    !flashAvailable -> R.string.camera_flash_unavailable
                    flashOn -> R.string.camera_flash_on
                    else -> R.string.camera_flash_off
                },
            ),
            size = 58.dp,
            color = if (flashAvailable) {
                if (flashOn) PocketCcdColors.RetroGreen else PocketCcdColors.RetroGreenDeep
            } else {
                PocketCcdColors.RetroRedDeep
            },
            enabled = flashAvailable,
        ) {
            FlashIcon(
                on = flashOn,
                enabled = flashAvailable,
            )
        }
        RetroShutterButton(onClick = onShutter)
        ToyRoundButton(
            onClick = onToggleCamera,
            contentDescription = stringResource(
                if (frontCamera) R.string.camera_flip_front else R.string.camera_flip_back,
            ),
            size = 58.dp,
            color = PocketCcdColors.RetroRedDeep,
        ) {
            FlipCameraIcon(front = frontCamera)
        }
    }
}

@Composable
private fun RetroShutterButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "retro-shutter-scale",
    )
    val description = stringResource(R.string.camera_shutter)

    Box(
        modifier = Modifier
            .size(104.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 18.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = PocketCcdColors.RetroInk.copy(alpha = 0.5f),
                spotColor = PocketCcdColors.RetroInk.copy(alpha = 0.62f),
            )
            .clip(CircleShape)
            .background(PocketCcdColors.RetroInk)
            .border(
                width = 3.dp,
                color = PocketCcdColors.RetroInk,
                shape = CircleShape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description }
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PocketCcdColors.RetroOrange,
                            PocketCcdColors.RetroOrangeDeep,
                        ),
                    ),
                )
                .border(
                    width = 2.dp,
                    color = PocketCcdColors.RetroCream.copy(alpha = 0.58f),
                    shape = CircleShape,
                ),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = PocketCcdSpacing.lg, top = PocketCcdSpacing.md)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
            )
        }
    }
}

@Composable
private fun FlashIcon(on: Boolean, enabled: Boolean) {
    val color = when {
        !enabled -> PocketCcdColors.RetroCream.copy(alpha = 0.35f)
        on -> PocketCcdColors.RetroInk
        else -> PocketCcdColors.RetroCream
    }
    Canvas(modifier = Modifier.size(PocketCcdSizes.icon)) {
        val path = Path().apply {
            moveTo(size.width * 0.56f, 0f)
            lineTo(size.width * 0.16f, size.height * 0.56f)
            lineTo(size.width * 0.47f, size.height * 0.56f)
            lineTo(size.width * 0.38f, size.height)
            lineTo(size.width * 0.86f, size.height * 0.42f)
            lineTo(size.width * 0.54f, size.height * 0.42f)
            close()
        }
        drawPath(path = path, color = color)
    }
}

@Composable
private fun FlipCameraIcon(front: Boolean) {
    Canvas(modifier = Modifier.size(PocketCcdSizes.icon)) {
        val color = PocketCcdColors.RetroCream
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val inset = 2.dp.toPx()
        val arcSize = androidx.compose.ui.geometry.Size(
            width = size.width - inset * 2,
            height = size.height - inset * 2,
        )
        drawArc(
            color = color,
            startAngle = if (front) 205f else 25f,
            sweepAngle = 145f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
        drawArc(
            color = color,
            startAngle = if (front) 25f else 205f,
            sweepAngle = 145f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
        val arrow = 3.5.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width - inset, size.height * 0.34f),
            end = Offset(size.width - inset - arrow, size.height * 0.34f - arrow),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(inset, size.height * 0.66f),
            end = Offset(inset + arrow, size.height * 0.66f + arrow),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFF12D27)
@Composable
private fun CameraScreenPreview() {
    PocketCcdTheme {
        CameraScreen(onShutter = {})
    }
}
