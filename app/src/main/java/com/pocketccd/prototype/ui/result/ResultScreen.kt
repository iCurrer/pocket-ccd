package com.pocketccd.prototype.ui.result

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

enum class ResultStatus {
    PROCESSING,
    OUTPUT_WRITING,
    SAVED,
    FAILED,
}

/**
 * Result page prototype. It is a pure UI surface: it receives a status and
 * callbacks, and never touches the camera, files, or MediaStore.
 *
 * `demo = true` is used while the capture/save pipeline does not exist yet;
 * the UI must not claim a real save in that mode.
 */
@Composable
fun ResultScreen(
    status: ResultStatus,
    onBackToCamera: () -> Unit,
    onOpenPhotoWall: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    demo: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PocketCcdColors.Background)
            .padding(
                horizontal = PocketCcdSpacing.xl,
                vertical = PocketCcdSpacing.md,
            ),
    ) {
        ResultTopBar(
            demo = demo,
            onBackToCamera = onBackToCamera,
        )
        Spacer(modifier = Modifier.height(PocketCcdSpacing.md))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TestPhotoFrame()
            Spacer(modifier = Modifier.height(PocketCcdSpacing.md))
            ResultNameplate()
            Spacer(modifier = Modifier.height(PocketCcdSpacing.md))
            ResultStatusRow(
                status = status,
                demo = demo,
                onRetry = onRetry,
            )
        }
        Spacer(modifier = Modifier.height(PocketCcdSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onBackToCamera,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = PocketCcdColors.TextSecondary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.result_back_to_camera),
                    style = PocketCcdType.MonoLabel,
                )
            }
            TextButton(
                onClick = onOpenPhotoWall,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = PocketCcdColors.AccentText,
                ),
            ) {
                Text(
                    text = stringResource(R.string.result_open_wall),
                    style = PocketCcdType.MonoLabel,
                )
            }
        }
    }
}

@Composable
private fun ResultTopBar(
    demo: Boolean,
    onBackToCamera: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackToCamera) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.result_back_to_camera),
                tint = PocketCcdColors.TextSecondary,
                modifier = Modifier.size(PocketCcdSizes.icon),
            )
        }
        Text(
            text = stringResource(R.string.result_header),
            style = PocketCcdType.MonoLabel,
            color = PocketCcdColors.TextPrimary,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (demo) {
            Text(
                text = stringResource(R.string.result_demo),
                style = PocketCcdType.MonoMicro,
                color = PocketCcdColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun TestPhotoFrame() {
    val outerShape = RoundedCornerShape(PocketCcdRadius.sm)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(outerShape)
            .background(PocketCcdColors.Surface)
            .border(
                width = PocketCcdSizes.hairline,
                color = PocketCcdColors.Outline,
                shape = outerShape,
            )
            .padding(PocketCcdSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(PocketCcdRadius.xs))
                .border(
                    width = PocketCcdSizes.hairline,
                    color = PocketCcdColors.OutlineSoft,
                    shape = RoundedCornerShape(PocketCcdRadius.xs),
                ),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Muted synthetic test frame: sky, sun, ground. It is clearly
                // a prototype image, not a real photograph.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF9FB3AA),
                            Color(0xFFD9C6A6),
                        ),
                    ),
                )
                drawRect(
                    color = Color(0xFF6E7F6A),
                    topLeft = Offset(0f, size.height * 0.62f),
                    size = Size(size.width, size.height * 0.38f),
                )
                drawCircle(
                    color = Color(0xFFE8C56A).copy(alpha = 0.9f),
                    radius = size.width * 0.055f,
                    center = Offset(size.width * 0.72f, size.height * 0.28f),
                )
            }
            Text(
                text = stringResource(R.string.result_frame_test),
                style = PocketCcdType.MonoMicro,
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(PocketCcdSpacing.sm),
            )
            Text(
                text = "4:3",
                style = PocketCcdType.MonoMicro,
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(PocketCcdSpacing.sm),
            )
        }
    }
}

@Composable
private fun ResultNameplate() {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                text = stringResource(R.string.result_filter),
                style = PocketCcdType.MonoMicro,
                color = PocketCcdColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun ResultStatusRow(
    status: ResultStatus,
    demo: Boolean,
    onRetry: () -> Unit,
) {
    val (label, color) = if (demo) {
        stringResource(R.string.result_status_demo) to PocketCcdColors.TextTertiary
    } else {
        when (status) {
            ResultStatus.PROCESSING -> stringResource(R.string.result_status_processing) to
                PocketCcdColors.Warning
            ResultStatus.OUTPUT_WRITING -> stringResource(R.string.result_status_writing) to
                PocketCcdColors.Warning
            ResultStatus.SAVED -> stringResource(R.string.result_status_saved) to
                PocketCcdColors.AccentText
            ResultStatus.FAILED -> stringResource(R.string.result_status_failed) to
                PocketCcdColors.Error
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(PocketCcdSpacing.sm))
        Text(
            text = label,
            style = PocketCcdType.MonoLabel,
            color = PocketCcdColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (status == ResultStatus.FAILED) {
            Spacer(modifier = Modifier.width(PocketCcdSpacing.sm))
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = PocketCcdColors.AccentText,
                ),
            ) {
                Text(
                    text = stringResource(R.string.result_retry),
                    style = PocketCcdType.MonoLabel,
                )
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFF4EFE6)
@Composable
private fun ResultSavedPreview() {
    PocketCcdTheme {
        ResultScreen(
            status = ResultStatus.SAVED,
            onBackToCamera = {},
            onOpenPhotoWall = {},
            onRetry = {},
            demo = false,
        )
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFF4EFE6)
@Composable
private fun ResultFailedPreview() {
    PocketCcdTheme {
        ResultScreen(
            status = ResultStatus.FAILED,
            onBackToCamera = {},
            onOpenPhotoWall = {},
            onRetry = {},
            demo = false,
        )
    }
}
