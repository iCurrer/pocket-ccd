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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * Home is the same red toy-camera family as the camera page: red plastic
 * body, dark lens, green flash window, orange shutter, green FILM key and a
 * compact settings button. No viewfinder on home, no web-style text links.
 */
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
                        PocketCcdColors.RetroRedLight,
                        PocketCcdColors.RetroRed,
                        PocketCcdColors.RetroRedDeep,
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
                    translationY = dragDistance.floatValue * 0.06f
                }
                .padding(horizontal = PocketCcdSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = cameraBodyModifier
                    .size(
                        width = PocketCcdSizes.cameraBodyWidth,
                        height = PocketCcdSizes.cameraBodyHeight,
                    )
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(PocketCcdRadius.body),
                        clip = false,
                        ambientColor = PocketCcdColors.RetroInk.copy(alpha = 0.4f),
                        spotColor = PocketCcdColors.RetroInk.copy(alpha = 0.48f),
                    )
                    .clip(RoundedCornerShape(PocketCcdRadius.body))
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
                        color = Color.White.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(PocketCcdRadius.body),
                    )
                    .padding(PocketCcdSpacing.lg),
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
                color = PocketCcdColors.RetroInk,
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
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "POCKET CCD",
            style = PocketCcdType.Brand.copy(
                fontSize = 20.sp,
                letterSpacing = 4.sp,
            ),
            color = PocketCcdColors.RetroCream,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(PocketCcdSpacing.md))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeLens()
            Spacer(modifier = Modifier.width(PocketCcdSpacing.md))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                FlashWindow()
                Spacer(modifier = Modifier.height(PocketCcdSpacing.md))
                ToyRoundButton(
                    onClick = onOpenCamera,
                    contentDescription = stringResource(R.string.home_open_camera),
                    size = 88.dp,
                    color = PocketCcdColors.RetroOrange,
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .border(
                                width = 2.dp,
                                color = PocketCcdColors.RetroCream.copy(alpha = 0.62f),
                                shape = CircleShape,
                            ),
                    )
                }
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
                color = PocketCcdColors.RetroRedDeep,
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = PocketCcdColors.RetroCream,
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
                        PocketCcdColors.RetroGreen,
                        PocketCcdColors.RetroGreenDeep,
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
            color = PocketCcdColors.RetroInk,
        )
    }
}

@Composable
private fun FlashWindow() {
    Box(
        modifier = Modifier
            .size(width = 58.dp, height = 24.dp)
            .clip(RoundedCornerShape(PocketCcdRadius.xs))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PocketCcdColors.RetroGreen,
                        PocketCcdColors.RetroGreenDeep,
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = PocketCcdColors.RetroInk.copy(alpha = 0.5f),
                shape = RoundedCornerShape(PocketCcdRadius.xs),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.7f)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.28f)),
        )
    }
}

@Composable
private fun HomeLens() {
    Box(
        modifier = Modifier
            .size(154.dp)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = PocketCcdColors.RetroInk.copy(alpha = 0.45f),
                spotColor = PocketCcdColors.RetroInk.copy(alpha = 0.55f),
            )
            .clip(CircleShape)
            .background(PocketCcdColors.RetroBlack)
            .border(PocketCcdSizes.lensBorder, PocketCcdColors.RetroGreenDeep, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = size.minDimension / 2f - 8.dp.toPx(),
                style = Stroke(width = 1.dp.toPx()),
            )
            drawArc(
                color = Color.White.copy(alpha = 0.28f),
                startAngle = 205f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(8.dp.toPx(), 8.dp.toPx()),
                size = Size(
                    width = size.width - 16.dp.toPx(),
                    height = size.height - 16.dp.toPx(),
                ),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2E5E59),
                            Color(0xFF143C39),
                            PocketCcdColors.RetroBlack,
                        ),
                    ),
                )
                .border(1.dp, PocketCcdColors.RetroGreen.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(PocketCcdColors.RetroBlack)
                    .border(1.dp, PocketCcdColors.RetroGreen.copy(alpha = 0.5f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = PocketCcdSpacing.lg, top = PocketCcdSpacing.md)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFF12D27)
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
