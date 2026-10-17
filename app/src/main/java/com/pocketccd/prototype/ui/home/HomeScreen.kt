package com.pocketccd.prototype.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.components.ToyCameraBody
import com.pocketccd.prototype.ui.components.ToyRoundButton
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
                        dragDistance.floatValue += dragAmount
                        change.consume()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragDistance.floatValue * 0.08f
                }
                .padding(horizontal = PocketCcdSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ToyCameraBody(
                modifier = cameraBodyModifier
                    .size(
                        width = PocketCcdSizes.cameraBodyWidth,
                        height = PocketCcdSizes.cameraBodyHeight,
                    ),
            ) {
                HomeCameraFront(
                    onOpenCamera = onOpenCamera,
                    onOpenGallery = onOpenGallery,
                    onOpenSettings = onOpenSettings,
                )
            }
            Spacer(modifier = Modifier.height(PocketCcdSpacing.lg))
            Text(
                text = "PULL DOWN · SHOOT",
                style = PocketCcdType.MonoMicro,
                color = PocketCcdColors.TextTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HomeCameraFront(
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PocketCcdSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "POCKET",
                    style = PocketCcdType.Brand,
                    color = PocketCcdColors.TextPrimary,
                )
                Spacer(modifier = Modifier.width(PocketCcdSpacing.xs))
                Text(
                    text = "CCD",
                    style = PocketCcdType.Brand,
                    color = PocketCcdColors.AccentText,
                )
            }
            FlashWindow()
        }

        Spacer(modifier = Modifier.height(PocketCcdSpacing.sm))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToyLens()
            Spacer(modifier = Modifier.width(PocketCcdSpacing.md))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                ToyRoundButton(
                    onClick = onOpenCamera,
                    contentDescription = stringResource(R.string.home_open_camera),
                    size = PocketCcdSizes.shutter,
                    color = PocketCcdColors.Accent,
                ) {
                    Box(
                        modifier = Modifier
                            .size(PocketCcdSizes.shutter * 0.62f)
                            .clip(CircleShape)
                            .border(
                                width = 2.dp,
                                color = PocketCcdColors.Surface.copy(alpha = 0.75f),
                                shape = CircleShape,
                            ),
                    )
                }
                Spacer(modifier = Modifier.height(PocketCcdSpacing.sm))
                Text(
                    text = "PRESS",
                    style = PocketCcdType.MonoMicro,
                    color = PocketCcdColors.TextTertiary,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilmButton(onClick = onOpenGallery)
            ToyRoundButton(
                onClick = onOpenSettings,
                contentDescription = stringResource(R.string.action_settings),
                size = 40.dp,
                color = PocketCcdColors.Butter,
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = PocketCcdColors.TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun FilmButton(onClick: () -> Unit) {
    val description = stringResource(R.string.home_photo_wall)
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(PocketCcdRadius.pill))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PocketCcdColors.Mint,
                        PocketCcdColors.MintDeep,
                    ),
                ),
            )
            .border(
                width = 2.dp,
                color = Color.White.copy(alpha = 0.45f),
                shape = RoundedCornerShape(PocketCcdRadius.pill),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = PocketCcdSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "FILM",
            style = PocketCcdType.MonoLabel,
            color = PocketCcdColors.TextPrimary,
        )
    }
}

@Composable
private fun FlashWindow() {
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 22.dp)
            .clip(RoundedCornerShape(PocketCcdRadius.xs))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PocketCcdColors.Flash,
                        PocketCcdColors.Butter,
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = PocketCcdColors.Outline,
                shape = RoundedCornerShape(PocketCcdRadius.xs),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.7f)
                .height(1.dp)
                .background(PocketCcdColors.TextPrimary.copy(alpha = 0.18f)),
        )
    }
}

@Composable
private fun ToyLens() {
    Box(
        modifier = Modifier
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
                        start = PocketCcdSpacing.md,
                        top = PocketCcdSpacing.md,
                    )
                    .size(PocketCcdSizes.lensHighlightDot)
                    .clip(CircleShape)
                    .background(PocketCcdColors.LensHighlight),
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFE6F0EA)
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
