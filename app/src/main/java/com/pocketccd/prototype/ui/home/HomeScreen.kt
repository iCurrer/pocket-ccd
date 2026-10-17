package com.pocketccd.prototype.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.theme.PocketCcdColors
import com.pocketccd.prototype.ui.theme.PocketCcdRadius
import com.pocketccd.prototype.ui.theme.PocketCcdSizes
import com.pocketccd.prototype.ui.theme.PocketCcdSpacing
import com.pocketccd.prototype.ui.theme.PocketCcdTheme

@Composable
fun HomeScreen(
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = PocketCcdSpacing.xl,
                    vertical = PocketCcdSpacing.lg,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(onOpenSettings = onOpenSettings)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BrandWordmark()
                Spacer(modifier = Modifier.height(PocketCcdSpacing.sm))
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PocketCcdColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(PocketCcdSpacing.xxl))
                CameraIllustration()
                Spacer(modifier = Modifier.height(PocketCcdSpacing.xl))
                PullDownHint()
            }

            BottomActions(
                onOpenCamera = onOpenCamera,
                onOpenGallery = onOpenGallery,
            )
            Spacer(modifier = Modifier.height(PocketCcdSpacing.md))
            Text(
                text = stringResource(R.string.home_privacy_note),
                style = MaterialTheme.typography.labelSmall,
                color = PocketCcdColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun TopBar(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "POCKET CCD",
            style = MaterialTheme.typography.labelSmall,
            color = PocketCcdColors.TextSecondary,
            letterSpacing = 2.sp,
        )
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.action_settings),
                tint = PocketCcdColors.TextSecondary,
                modifier = Modifier.size(PocketCcdSizes.icon),
            )
        }
    }
}

@Composable
private fun BrandWordmark() {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "POCKET",
            style = MaterialTheme.typography.displayLarge,
            color = PocketCcdColors.TextPrimary,
        )
        Spacer(modifier = Modifier.width(PocketCcdSpacing.sm))
        Text(
            text = "CCD",
            style = MaterialTheme.typography.displayLarge,
            color = PocketCcdColors.Accent,
        )
    }
}

@Composable
private fun CameraIllustration() {
    val bodyShape = RoundedCornerShape(PocketCcdRadius.xl)
    Box(
        modifier = Modifier
            .size(
                width = PocketCcdSizes.cameraBodyWidth,
                height = PocketCcdSizes.cameraBodyHeight,
            )
            .clip(bodyShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        PocketCcdColors.SurfaceHigh,
                        PocketCcdColors.Surface,
                        PocketCcdColors.BackgroundElevated,
                    ),
                ),
            )
            .border(PocketCcdSizes.hairline, PocketCcdColors.OutlineSoft, bodyShape)
            .padding(PocketCcdSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "POCKET CCD",
                style = MaterialTheme.typography.labelSmall,
                color = PocketCcdColors.TextTertiary,
                letterSpacing = 1.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(PocketCcdSizes.flashDot)
                        .clip(CircleShape)
                        .background(PocketCcdColors.Flash),
                )
                Spacer(modifier = Modifier.width(PocketCcdSpacing.xs))
                Text(
                    text = "DIGITAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = PocketCcdColors.TextTertiary,
                    letterSpacing = 1.sp,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
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
                    ),
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

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(
                    width = PocketCcdSizes.viewfinderWidth,
                    height = PocketCcdSizes.viewfinderHeight,
                )
                .border(
                    PocketCcdSizes.hairline,
                    PocketCcdColors.Outline,
                    RoundedCornerShape(PocketCcdRadius.xs),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(PocketCcdSizes.accentStripWidth)
                .height(PocketCcdSizes.accentStripHeight)
                .clip(CircleShape)
                .background(PocketCcdColors.Accent),
        )
    }
}

@Composable
private fun PullDownHint() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = PocketCcdColors.TextTertiary,
            modifier = Modifier.size(PocketCcdSizes.hintIcon),
        )
        Text(
            text = "下拉进入相机",
            style = MaterialTheme.typography.labelSmall,
            color = PocketCcdColors.TextTertiary,
        )
    }
}

@Composable
private fun BottomActions(
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GalleryCard(
            onClick = onOpenGallery,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(PocketCcdSpacing.md))
        Button(
            onClick = onOpenCamera,
            modifier = Modifier
                .height(PocketCcdSizes.actionHeight)
                .widthIn(min = PocketCcdSizes.ctaMinWidth),
            shape = RoundedCornerShape(PocketCcdRadius.pill),
            colors = ButtonDefaults.buttonColors(
                containerColor = PocketCcdColors.Accent,
                contentColor = PocketCcdColors.OnAccent,
            ),
        ) {
            Text(
                text = stringResource(R.string.home_open_camera),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun GalleryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(PocketCcdRadius.lg)
    Row(
        modifier = modifier
            .height(PocketCcdSizes.actionHeight)
            .clip(shape)
            .background(PocketCcdColors.Surface)
            .border(PocketCcdSizes.hairline, PocketCcdColors.OutlineSoft, shape)
            .clickable(onClick = onClick)
            .padding(PocketCcdSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(PocketCcdSizes.galleryThumb)
                .clip(RoundedCornerShape(PocketCcdRadius.md))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            PocketCcdColors.SurfaceHigh,
                            PocketCcdColors.BackgroundElevated,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(PocketCcdSizes.galleryLens)
                    .clip(CircleShape)
                    .background(PocketCcdColors.LensGlass)
                    .border(PocketCcdSizes.hairline, PocketCcdColors.LensRing, CircleShape),
            )
        }
        Spacer(modifier = Modifier.width(PocketCcdSpacing.sm))
        Column {
            Text(
                text = stringResource(R.string.action_gallery),
                style = MaterialTheme.typography.labelLarge,
                color = PocketCcdColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.gallery_count_zero),
                style = MaterialTheme.typography.labelSmall,
                color = PocketCcdColors.TextTertiary,
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFF0B0B0D)
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
