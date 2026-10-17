package com.pocketccd.prototype.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.components.CameraShutterButton
import com.pocketccd.prototype.ui.theme.PocketCcdColors
import com.pocketccd.prototype.ui.theme.PocketCcdRadius
import com.pocketccd.prototype.ui.theme.PocketCcdSizes
import com.pocketccd.prototype.ui.theme.PocketCcdSpacing
import com.pocketccd.prototype.ui.theme.PocketCcdTheme
import com.pocketccd.prototype.ui.theme.PocketCcdType

/**
 * Static camera-page prototype. No CameraX/Camera2, no permissions, no preview
 * screenshot. The body is deliberately inset from the screen edges; the
 * viewfinder sits in the upper half with visible body material around it.
 */
@Composable
fun CameraScreen(
    onCollapse: () -> Unit,
    onOpenGallery: () -> Unit,
    onShutter: () -> Unit,
    modifier: Modifier = Modifier,
    panelModifier: Modifier = Modifier,
) {
    var flashOn by remember { mutableStateOf(false) }
    var frontCamera by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PocketCcdColors.Background,
                        PocketCcdColors.BackgroundElevated,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(PocketCcdSizes.cameraPanelMargin),
            contentAlignment = Alignment.Center,
        ) {
            val panelWidth = minOf(maxWidth, 520.dp)
            Box(
                modifier = panelModifier
                    .width(panelWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(PocketCcdRadius.xl))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                PocketCcdColors.CameraPanelTop,
                                PocketCcdColors.CameraPanelBottom,
                            ),
                        ),
                    )
                    .border(
                        width = PocketCcdSizes.hairline,
                        color = PocketCcdColors.CameraPanelHighlight,
                        shape = RoundedCornerShape(PocketCcdRadius.xl),
                    )
                    .padding(PocketCcdSizes.cameraPanelPadding),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    CameraTopBar(
                        onCollapse = onCollapse,
                        onOpenGallery = onOpenGallery,
                    )
                    Spacer(modifier = Modifier.height(PocketCcdSpacing.md))
                    Viewfinder()
                    Spacer(modifier = Modifier.height(PocketCcdSpacing.sm))
                    Nameplate()
                    Spacer(modifier = Modifier.weight(1f))
                    Controls(
                        flashOn = flashOn,
                        frontCamera = frontCamera,
                        onToggleFlash = { flashOn = !flashOn },
                        onToggleCamera = { frontCamera = !frontCamera },
                        onShutter = onShutter,
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraTopBar(
    onCollapse: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PocketCcdSizes.topBarHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCollapse) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.camera_collapse),
                tint = PocketCcdColors.TextSecondary,
                modifier = Modifier.size(PocketCcdSizes.icon),
            )
        }
        Text(
            text = "DC-01",
            style = PocketCcdType.MonoLabel,
            color = PocketCcdColors.TextTertiary,
        )
        val galleryDescription = stringResource(R.string.camera_gallery)
        IconButton(
            onClick = onOpenGallery,
            modifier = Modifier.semantics { contentDescription = galleryDescription },
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(PocketCcdRadius.xs))
                    .background(PocketCcdColors.Surface)
                    .border(
                        width = PocketCcdSizes.hairline,
                        color = PocketCcdColors.Outline,
                        shape = RoundedCornerShape(PocketCcdRadius.xs),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(PocketCcdColors.LensGlass)
                        .border(
                            width = PocketCcdSizes.hairline,
                            color = PocketCcdColors.LensRing,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun Viewfinder() {
    val shape = RoundedCornerShape(PocketCcdRadius.viewfinder)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PocketCcdColors.ViewfinderTop,
                        PocketCcdColors.ViewfinderBottom,
                    ),
                ),
            )
            .border(
                width = PocketCcdSizes.viewfinderInnerBorder,
                color = PocketCcdColors.Outline,
                shape = shape,
            )
            .padding(PocketCcdSizes.viewfinderPadding),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = PocketCcdSizes.hairline,
                    color = PocketCcdColors.CameraPanelHighlight,
                    shape = RoundedCornerShape(PocketCcdRadius.xs),
                ),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = 12.dp.toPx()
            val length = 10.dp.toPx()
            val color = PocketCcdColors.TextTertiary.copy(alpha = 0.5f)
            val stroke = 1.dp.toPx()

            // Four small corner marks, not a full HUD.
            drawLine(color, Offset(inset, inset), Offset(inset + length, inset), stroke)
            drawLine(color, Offset(inset, inset), Offset(inset, inset + length), stroke)
            drawLine(
                color,
                Offset(size.width - inset, inset),
                Offset(size.width - inset - length, inset),
                stroke,
            )
            drawLine(
                color,
                Offset(size.width - inset, inset),
                Offset(size.width - inset, inset + length),
                stroke,
            )
            drawLine(
                color,
                Offset(inset, size.height - inset),
                Offset(inset + length, size.height - inset),
                stroke,
            )
            drawLine(
                color,
                Offset(inset, size.height - inset),
                Offset(inset, size.height - inset - length),
                stroke,
            )
            drawLine(
                color,
                Offset(size.width - inset, size.height - inset),
                Offset(size.width - inset - length, size.height - inset),
                stroke,
            )
            drawLine(
                color,
                Offset(size.width - inset, size.height - inset),
                Offset(size.width - inset, size.height - inset - length),
                stroke,
            )
        }
        Text(
            text = "4:3",
            style = PocketCcdType.MonoMicro,
            color = PocketCcdColors.TextTertiary,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            text = "PREVIEW",
            style = PocketCcdType.MonoMicro,
            color = PocketCcdColors.TextTertiary,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun Nameplate() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(PocketCcdSizes.nameplateHeight),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PocketCcdSizes.hairline)
                .background(PocketCcdColors.OutlineSoft),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = PocketCcdSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "POCKET CCD",
                style = PocketCcdType.MonoMicro,
                color = PocketCcdColors.TextSecondary,
            )
            Text(
                text = "DC-01",
                style = PocketCcdType.MonoMicro,
                color = PocketCcdColors.TextTertiary,
            )
            Text(
                text = "DAYLIGHT",
                style = PocketCcdType.MonoMicro,
                color = PocketCcdColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun Controls(
    flashOn: Boolean,
    frontCamera: Boolean,
    onToggleFlash: () -> Unit,
    onToggleCamera: () -> Unit,
    onShutter: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PocketCcdSizes.shutterOuter),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SideControl(
            onClick = onToggleFlash,
            contentDescription = stringResource(
                if (flashOn) R.string.camera_flash_on else R.string.camera_flash_off,
            ),
            highlighted = flashOn,
        ) {
            FlashIcon(on = flashOn)
        }
        CameraShutterButton(onClick = onShutter)
        SideControl(
            onClick = onToggleCamera,
            contentDescription = stringResource(
                if (frontCamera) R.string.camera_flip_front else R.string.camera_flip_back,
            ),
            highlighted = frontCamera,
        ) {
            FlipCameraIcon(front = frontCamera)
        }
    }
}

@Composable
private fun SideControl(
    onClick: () -> Unit,
    contentDescription: String,
    highlighted: Boolean,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(PocketCcdSizes.sideControl)
            .clip(CircleShape)
            .background(PocketCcdColors.Surface)
            .border(
                width = PocketCcdSizes.hairline,
                color = if (highlighted) {
                    PocketCcdColors.Accent.copy(alpha = 0.55f)
                } else {
                    PocketCcdColors.OutlineSoft
                },
                shape = CircleShape,
            )
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
private fun FlashIcon(on: Boolean) {
    val color = if (on) PocketCcdColors.Accent else PocketCcdColors.TextSecondary
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
    val color = if (front) PocketCcdColors.Accent else PocketCcdColors.TextSecondary
    Canvas(modifier = Modifier.size(PocketCcdSizes.icon)) {
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        val inset = 2.dp.toPx()
        val arcSize = Size(
            width = size.width - inset * 2,
            height = size.height - inset * 2,
        )
        drawArc(
            color = color,
            startAngle = 25f,
            sweepAngle = 145f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
        drawArc(
            color = color,
            startAngle = 205f,
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
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(inset, size.height * 0.66f),
            end = Offset(inset + arrow, size.height * 0.66f + arrow),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFF070708)
@Composable
private fun CameraScreenPreview() {
    PocketCcdTheme {
        CameraScreen(
            onCollapse = {},
            onOpenGallery = {},
            onShutter = {},
        )
    }
}
