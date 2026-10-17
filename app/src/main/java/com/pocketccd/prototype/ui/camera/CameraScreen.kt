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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.components.CameraShutterButton
import com.pocketccd.prototype.ui.theme.PocketCcdColors
import com.pocketccd.prototype.ui.theme.PocketCcdRadius
import com.pocketccd.prototype.ui.theme.PocketCcdSizes
import com.pocketccd.prototype.ui.theme.PocketCcdSpacing
import com.pocketccd.prototype.ui.theme.PocketCcdTheme

/**
 * Static camera-page prototype. It deliberately contains no CameraX/Camera2
 * references: preview, capture and processing are separate modules.
 *
 * The body keeps a visible margin around it (the brief forbids a full-screen
 * camera viewfinder). The viewfinder occupies the upper part of the body;
 * controls stay at the bottom, like a small digital camera.
 */
@Composable
fun CameraScreen(
    onCollapse: () -> Unit,
    onOpenGallery: () -> Unit,
    onShutter: () -> Unit,
    modifier: Modifier = Modifier,
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
                        PocketCcdColors.Background,
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
                modifier = Modifier
                    .width(panelWidth)
                    .fillMaxHeight()
                    .shadow(
                        elevation = 28.dp,
                        shape = RoundedCornerShape(PocketCcdRadius.xxl),
                        clip = false,
                        ambientColor = Color.Black,
                        spotColor = Color.Black,
                    )
                    .clip(RoundedCornerShape(PocketCcdRadius.xxl))
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
                        shape = RoundedCornerShape(PocketCcdRadius.xxl),
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
                    Spacer(modifier = Modifier.height(PocketCcdSpacing.md))
                    Nameplate()
                    Spacer(modifier = Modifier.weight(1f))
                    Controls(
                        flashOn = flashOn,
                        frontCamera = frontCamera,
                        onToggleFlash = { flashOn = !flashOn },
                        onToggleCamera = { frontCamera = !frontCamera },
                        onShutter = onShutter,
                    )
                    Spacer(modifier = Modifier.height(PocketCcdSpacing.sm))
                    Text(
                        text = stringResource(R.string.camera_static_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = PocketCcdColors.TextTertiary,
                        letterSpacing = 1.4.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "POCKET CCD",
                style = MaterialTheme.typography.labelSmall,
                color = PocketCcdColors.TextPrimary,
                letterSpacing = 2.sp,
            )
            Text(
                text = stringResource(R.string.camera_top_label),
                style = MaterialTheme.typography.labelSmall,
                color = PocketCcdColors.TextTertiary,
                letterSpacing = 1.sp,
            )
        }
        val galleryDescription = stringResource(R.string.camera_gallery)
        IconButton(
            onClick = onOpenGallery,
            modifier = Modifier.semantics { contentDescription = galleryDescription },
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(PocketCcdRadius.sm))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                PocketCcdColors.SurfaceHigh,
                                PocketCcdColors.BackgroundElevated,
                            ),
                        ),
                    )
                    .border(
                        width = PocketCcdSizes.hairline,
                        color = PocketCcdColors.Outline,
                        shape = RoundedCornerShape(PocketCcdRadius.sm),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
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
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(PocketCcdRadius.md))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PocketCcdColors.ViewfinderGlow,
                            Color.Transparent,
                        ),
                    ),
                )
                .border(
                    width = PocketCcdSizes.hairline,
                    color = PocketCcdColors.CameraPanelHighlight,
                    shape = RoundedCornerShape(PocketCcdRadius.md),
                ),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.camera_preview_placeholder),
                style = MaterialTheme.typography.labelLarge,
                color = PocketCcdColors.TextTertiary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(PocketCcdSpacing.xs))
            Text(
                text = stringResource(R.string.camera_preview_caption),
                style = MaterialTheme.typography.labelSmall,
                color = PocketCcdColors.TextTertiary.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Nameplate() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PocketCcdSizes.hairline)
                .background(PocketCcdColors.OutlineSoft),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PocketCcdSizes.nameplateHeight),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.camera_model),
                    style = MaterialTheme.typography.labelSmall,
                    color = PocketCcdColors.TextSecondary,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    text = "POCKET CCD",
                    style = MaterialTheme.typography.labelSmall,
                    color = PocketCcdColors.TextTertiary,
                    letterSpacing = 1.sp,
                )
            }
            Text(
                text = stringResource(R.string.camera_filter),
                style = MaterialTheme.typography.labelSmall,
                color = PocketCcdColors.TextTertiary,
                letterSpacing = 1.sp,
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
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(PocketCcdSizes.sideControl)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PocketCcdColors.SurfaceHigh,
                        PocketCcdColors.Surface,
                    ),
                ),
            )
            .border(
                width = PocketCcdSizes.hairline,
                color = if (highlighted) {
                    PocketCcdColors.Accent.copy(alpha = 0.55f)
                } else {
                    PocketCcdColors.OutlineSoft
                },
                shape = shape,
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
        val stroke = Stroke(
            width = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
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
        val arrow = 4.dp.toPx()
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

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFF0B0B0D)
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
