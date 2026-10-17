package com.pocketccd.prototype.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.ui.theme.PocketCcdColors
import com.pocketccd.prototype.ui.theme.PocketCcdRadius
import com.pocketccd.prototype.ui.theme.PocketCcdSizes
import com.pocketccd.prototype.ui.theme.PocketCcdSpacing

/**
 * Chunky plastic camera body with an inset cream faceplate. Used by the home
 * camera object and by the camera page so the shared-element transition morphs
 * one physical shell instead of two unrelated screens.
 */
@Composable
fun ToyCameraBody(
    modifier: Modifier = Modifier,
    contentPadding: Dp = PocketCcdSizes.cameraPanelPadding,
    content: @Composable BoxScope.() -> Unit,
) {
    val bodyShape = RoundedCornerShape(PocketCcdRadius.body)
    val faceShape = RoundedCornerShape(PocketCcdRadius.xl)
    Box(
        modifier = modifier
            .shadow(
                elevation = 18.dp,
                shape = bodyShape,
                clip = false,
                ambientColor = PocketCcdColors.TextPrimary.copy(alpha = 0.24f),
                spotColor = PocketCcdColors.TextPrimary.copy(alpha = 0.32f),
            )
            .clip(bodyShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PocketCcdColors.CameraPanelTop,
                        PocketCcdColors.CameraPanelBottom,
                    ),
                ),
            )
            .border(
                width = 2.dp,
                color = PocketCcdColors.CameraPanelHighlight,
                shape = bodyShape,
            )
            .padding(contentPadding),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(faceShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PocketCcdColors.Surface,
                            PocketCcdColors.SurfaceHigh,
                        ),
                    ),
                )
                .border(
                    width = PocketCcdSizes.hairline,
                    color = PocketCcdColors.OutlineSoft,
                    shape = faceShape,
                ),
            content = content,
        )
        ToyScrew(modifier = Modifier.align(Alignment.TopStart))
        ToyScrew(modifier = Modifier.align(Alignment.TopEnd))
        ToyScrew(modifier = Modifier.align(Alignment.BottomStart))
        ToyScrew(modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
fun ToyScreen(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(PocketCcdRadius.md)
    Box(
        modifier = modifier
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
                width = 3.dp,
                color = PocketCcdColors.TextPrimary.copy(alpha = 0.72f),
                shape = shape,
            )
            .padding(PocketCcdSpacing.sm),
        content = content,
    )
}

@Composable
fun ToyRoundButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = PocketCcdSizes.sideControl,
    color: Color = PocketCcdColors.Mint,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 6.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = PocketCcdColors.TextPrimary.copy(alpha = 0.28f),
                spotColor = PocketCcdColors.TextPrimary.copy(alpha = 0.34f),
            )
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.35f),
                        color,
                        color.copy(alpha = 0.92f),
                    ),
                ),
            )
            .border(
                width = 2.dp,
                color = Color.White.copy(alpha = 0.45f),
                shape = CircleShape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun ToyScrew(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(PocketCcdColors.CameraPanelBottom.copy(alpha = 0.75f))
            .border(
                width = PocketCcdSizes.hairline,
                color = PocketCcdColors.TextPrimary.copy(alpha = 0.35f),
                shape = CircleShape,
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 3.dp, height = 1.dp)
                .background(PocketCcdColors.TextPrimary.copy(alpha = 0.45f)),
        )
    }
}
