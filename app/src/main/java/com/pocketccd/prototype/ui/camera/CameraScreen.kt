package com.pocketccd.prototype.ui.camera

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.components.CameraShutterButton
import com.pocketccd.prototype.ui.components.ToyCameraBody
import com.pocketccd.prototype.ui.components.ToyRoundButton
import com.pocketccd.prototype.ui.components.ToyScreen
import com.pocketccd.prototype.ui.theme.PocketCcdColors
import com.pocketccd.prototype.ui.theme.PocketCcdSizes
import com.pocketccd.prototype.ui.theme.PocketCcdSpacing
import com.pocketccd.prototype.ui.theme.PocketCcdTheme
import com.pocketccd.prototype.ui.theme.PocketCcdType

/**
 * Toy-camera back panel: deep-teal viewfinder window, stamped nameplate,
 * chunky mint/coral controls. No CameraX/Camera2, no permissions.
 */
@Composable
fun CameraScreen(
    onCollapse: () -> Unit,
    onShutter: () -> Unit,
    modifier: Modifier = Modifier,
    panelModifier: Modifier = Modifier,
) {
    var flashOn by remember { mutableStateOf(false) }
    var frontCamera by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(PocketCcdSizes.cameraPanelMargin),
        contentAlignment = Alignment.Center,
    ) {
        val panelWidth = minOf(maxWidth, 520.dp)
        ToyCameraBody(
            modifier = panelModifier
                .width(panelWidth)
                .fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(PocketCcdSpacing.md),
            ) {
                CameraHeader(onCollapse = onCollapse)
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

@Composable
private fun CameraHeader(onCollapse: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PocketCcdSizes.topBarHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToyRoundButton(
            onClick = onCollapse,
            contentDescription = stringResource(R.string.camera_collapse),
            size = 38.dp,
            color = PocketCcdColors.Mint,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = PocketCcdColors.TextPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = "POCKET CCD",
            style = PocketCcdType.Brand,
            color = PocketCcdColors.TextPrimary,
        )
        Spacer(modifier = Modifier.width(38.dp))
    }
}

@Composable
private fun Viewfinder() {
    ToyScreen(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = 12.dp.toPx()
            val length = 12.dp.toPx()
            val color = PocketCcdColors.ViewfinderInk.copy(alpha = 0.55f)
            val stroke = 1.5.dp.toPx()

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
            color = PocketCcdColors.ViewfinderInk,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            text = "PREVIEW",
            style = PocketCcdType.MonoMicro,
            color = PocketCcdColors.ViewfinderInk.copy(alpha = 0.72f),
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun Nameplate() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PocketCcdSizes.nameplateHeight),
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
            color = PocketCcdColors.TextSecondary,
        )
        Text(
            text = "DAYLIGHT",
            style = PocketCcdType.MonoMicro,
            color = PocketCcdColors.TextSecondary,
        )
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
        ToyRoundButton(
            onClick = onToggleFlash,
            contentDescription = stringResource(
                if (flashOn) R.string.camera_flash_on else R.string.camera_flash_off,
            ),
            size = PocketCcdSizes.sideControl,
            color = PocketCcdColors.Mint,
        ) {
            FlashIcon(on = flashOn)
        }
        CameraShutterButton(onClick = onShutter)
        ToyRoundButton(
            onClick = onToggleCamera,
            contentDescription = stringResource(
                if (frontCamera) R.string.camera_flip_front else R.string.camera_flip_back,
            ),
            size = PocketCcdSizes.sideControl,
            color = PocketCcdColors.Butter,
        ) {
            FlipCameraIcon(front = frontCamera)
        }
    }
}

@Composable
private fun FlashIcon(on: Boolean) {
    val color = if (on) PocketCcdColors.AccentText else PocketCcdColors.TextPrimary
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
    val color = if (front) PocketCcdColors.AccentText else PocketCcdColors.TextPrimary
    Canvas(modifier = Modifier.size(PocketCcdSizes.icon)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
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

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFE6F0EA)
@Composable
private fun CameraScreenPreview() {
    PocketCcdTheme {
        CameraScreen(
            onCollapse = {},
            onShutter = {},
        )
    }
}
