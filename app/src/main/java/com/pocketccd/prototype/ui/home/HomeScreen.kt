package com.pocketccd.prototype.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.theme.PocketCcdColors
import com.pocketccd.prototype.ui.theme.PocketCcdRadius
import com.pocketccd.prototype.ui.theme.PocketCcdSizes
import com.pocketccd.prototype.ui.theme.PocketCcdSpacing
import com.pocketccd.prototype.ui.theme.PocketCcdTheme
import com.pocketccd.prototype.ui.theme.PocketCcdType

@Composable
fun HomeScreen(
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    cameraBodyModifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val pullThresholdPx = with(density) { PocketCcdSizes.pullThreshold.toPx() }
    val dragDistance = remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PocketCcdColors.Background,
                        PocketCcdColors.BackgroundElevated,
                    ),
                ),
            )
            .pointerInput(pullThresholdPx) {
                detectVerticalDragGestures(
                    onDragStart = { dragDistance.floatValue = 0f },
                    onDragEnd = {
                        when {
                            dragDistance.floatValue >= pullThresholdPx -> onOpenCamera()
                            dragDistance.floatValue <= -pullThresholdPx -> onOpenGallery()
                        }
                        dragDistance.floatValue = 0f
                    },
                    onDragCancel = { dragDistance.floatValue = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        // Down = camera, up = app-owned photo wall.
                        dragDistance.floatValue += dragAmount
                        change.consume()
                    },
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragDistance.floatValue * 0.08f
                }
                .padding(
                    horizontal = PocketCcdSpacing.xl,
                    vertical = PocketCcdSpacing.lg,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.action_settings),
                        tint = PocketCcdColors.TextSecondary,
                        modifier = Modifier.size(PocketCcdSizes.icon),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "POCKET",
                        style = PocketCcdType.Brand,
                        color = PocketCcdColors.TextPrimary,
                    )
                    Spacer(modifier = Modifier.width(PocketCcdSpacing.sm))
                    Text(
                        text = "CCD",
                        style = PocketCcdType.Brand,
                        color = PocketCcdColors.AccentText,
                    )
                }
                Spacer(modifier = Modifier.height(PocketCcdSpacing.xxl))
                CompactCameraBody(modifier = cameraBodyModifier)
                Spacer(modifier = Modifier.height(PocketCcdSpacing.xl))
                PullDownHint()
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onOpenGallery,
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = PocketCcdColors.TextSecondary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.home_photo_wall),
                        style = PocketCcdType.MonoLabel,
                    )
                }
                Text(
                    text = "LOCAL ONLY",
                    style = PocketCcdType.MonoMicro,
                    color = PocketCcdColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                TextButton(
                    onClick = onOpenCamera,
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = PocketCcdColors.AccentText,
                    ),
                ) {
                    Text(text = "OPEN CAMERA", style = PocketCcdType.MonoLabel)
                }
            }
        }
    }
}

@Composable
private fun CompactCameraBody(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(PocketCcdRadius.xl)
    Box(
        modifier = modifier
            .size(
                width = PocketCcdSizes.cameraBodyWidth,
                height = PocketCcdSizes.cameraBodyHeight,
            )
            .clip(shape)
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
                shape = shape,
            )
            .padding(PocketCcdSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "POCKET CCD",
                style = PocketCcdType.MonoMicro,
                color = PocketCcdColors.TextSecondary,
            )
            Text(
                text = "01",
                style = PocketCcdType.MonoMicro,
                color = PocketCcdColors.TextSecondary,
            )
        }

        Lens(modifier = Modifier.align(Alignment.Center))

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(PocketCcdSizes.flashDot)
                .clip(CircleShape)
                .background(PocketCcdColors.Flash),
        )
    }
}

@Composable
private fun Lens(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(PocketCcdSizes.lensOuter)
            .clip(CircleShape)
            .background(PocketCcdColors.LensGlass)
            .border(PocketCcdSizes.lensBorder, PocketCcdColors.LensRing, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = PocketCcdColors.LensHighlight,
                radius = size.minDimension / 2f - PocketCcdSizes.lensCanvasInset.toPx(),
                style = Stroke(width = PocketCcdSizes.hairline.toPx()),
            )
            drawArc(
                color = PocketCcdColors.LensHighlight,
                startAngle = 205f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(
                    PocketCcdSizes.lensArcInset.toPx(),
                    PocketCcdSizes.lensArcInset.toPx(),
                ),
                size = androidx.compose.ui.geometry.Size(
                    width = size.width - PocketCcdSizes.lensArcSizeInset.toPx(),
                    height = size.height - PocketCcdSizes.lensArcSizeInset.toPx(),
                ),
                style = Stroke(
                    width = PocketCcdSizes.lensArcStroke.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
        }
        Box(
            modifier = Modifier
                .size(PocketCcdSizes.lensInner)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PocketCcdColors.LensBlue,
                            PocketCcdColors.LensBlueDeep,
                            PocketCcdColors.LensGlass,
                        ),
                    ),
                )
                .border(PocketCcdSizes.hairline, PocketCcdColors.LensRing, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(PocketCcdSizes.lensCore)
                    .clip(CircleShape)
                    .background(PocketCcdColors.LensCore)
                    .border(PocketCcdSizes.hairline, PocketCcdColors.LensRing, CircleShape),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = PocketCcdSpacing.sm,
                        top = PocketCcdSpacing.sm,
                    )
                    .size(PocketCcdSizes.lensHighlightDot)
                    .clip(CircleShape)
                    .background(PocketCcdColors.LensHighlight),
            )
        }
    }
}

@Composable
private fun PullDownHint() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = PocketCcdColors.TextSecondary,
            modifier = Modifier.size(PocketCcdSizes.hintIcon),
        )
        Text(
            text = "PULL DOWN",
            style = PocketCcdType.MonoMicro,
            color = PocketCcdColors.TextSecondary,
        )
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFF070708)
@Composable
private fun HomeScreenPreview() {
    PocketCcdTheme {
        HomeScreen(
            onOpenCamera = {},
            onOpenGallery = {},
            onOpenSettings = {},
        )
    }
}
