package com.pocketccd.prototype.ui.result

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.R
import com.pocketccd.prototype.camera.SavedPhoto
import com.pocketccd.prototype.ui.components.ToyCameraBody
import com.pocketccd.prototype.ui.components.ToyRoundButton
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
 * Toy-camera print page.
 *
 * 这个页面只显示传入的状态，不碰相机、文件和 MediaStore：
 * [photo] 有值就显示真实成片（缩略图），为空则回到测试帧并标 `DEMO`，
 * 绝不把占位画面冒充成刚拍的照片。
 */
@Composable
fun ResultScreen(
    status: ResultStatus,
    onBackToCamera: () -> Unit,
    onOpenPhotoWall: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    demo: Boolean = false,
    photo: SavedPhoto? = null,
    failureMessage: String? = null,
    stockLabel: String? = null,
) {
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
            )
            .padding(PocketCcdSizes.cameraPanelMargin),
        contentAlignment = Alignment.Center,
    ) {
        ToyCameraBody(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(PocketCcdSpacing.md),
            ) {
                ResultHeader(
                    demo = demo,
                    onBackToCamera = onBackToCamera,
                )
                Spacer(modifier = Modifier.height(PocketCcdSpacing.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    PhotoPrint(photo = photo, stockLabel = stockLabel)
                }
                Spacer(modifier = Modifier.height(PocketCcdSpacing.sm))
                ResultStatusRow(
                    status = status,
                    demo = demo,
                    onRetry = onRetry,
                )
                if (failureMessage != null) {
                    Spacer(modifier = Modifier.height(PocketCcdSpacing.xs))
                    Text(
                        text = failureMessage,
                        style = PocketCcdType.MonoMicro,
                        color = PocketCcdColors.Error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (photo != null) {
                    Spacer(modifier = Modifier.height(PocketCcdSpacing.xs))
                    Text(
                        // 诊断行：冲洗耗时与条带数。都是实测值，不写估值。
                        text = "${photo.width}×${photo.height} · ${photo.renderMillis}ms · ${photo.strips} strips",
                        style = PocketCcdType.MonoMicro,
                        color = PocketCcdColors.TextTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(PocketCcdSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToyRoundButton(
                        onClick = onBackToCamera,
                        contentDescription = stringResource(R.string.result_back_to_camera),
                        size = 52.dp,
                        color = PocketCcdColors.Mint,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = PocketCcdColors.TextPrimary,
                            modifier = Modifier.size(PocketCcdSizes.icon),
                        )
                    }
                    ToyRoundButton(
                        onClick = onOpenPhotoWall,
                        contentDescription = stringResource(R.string.result_open_wall),
                        size = 52.dp,
                        color = PocketCcdColors.Butter,
                    ) {
                        FilmIcon()
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultHeader(
    demo: Boolean,
    onBackToCamera: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToyRoundButton(
            onClick = onBackToCamera,
            contentDescription = stringResource(R.string.result_back_to_camera),
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
        Spacer(modifier = Modifier.width(PocketCcdSpacing.sm))
        Text(
            text = stringResource(R.string.result_header),
            style = PocketCcdType.Brand,
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
private fun PhotoPrint(photo: SavedPhoto?, stockLabel: String?) {
    val paperShape = RoundedCornerShape(PocketCcdRadius.xs)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PocketCcdSpacing.sm)
            .rotate(-1.2f)
            .shadow(
                elevation = 10.dp,
                shape = paperShape,
                clip = false,
                ambientColor = PocketCcdColors.TextPrimary.copy(alpha = 0.25f),
                spotColor = PocketCcdColors.TextPrimary.copy(alpha = 0.35f),
            )
            .clip(paperShape)
            .background(Color(0xFFFFFBF2))
            .border(
                width = PocketCcdSizes.hairline,
                color = PocketCcdColors.Outline,
                shape = paperShape,
            )
            .padding(PocketCcdSpacing.sm),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(PocketCcdRadius.xs)),
            ) {
                // 有真实成片就显示缩略图。加载的是长边 1440 的 thumb，不是 12MP 原图：
                // 成片页的实际显示尺寸不到 400dp，解原图只是白烧 48MB。
                val image = remember(photo?.thumbnailFile?.absolutePath) {
                    photo?.thumbnailFile?.absolutePath
                        ?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
                }
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0xFF8FB7AE),
                                    Color(0xFFF2D9A9),
                                ),
                            ),
                        )
                        drawRect(
                            color = Color(0xFF6C9A83),
                            topLeft = Offset(0f, size.height * 0.64f),
                            size = Size(size.width, size.height * 0.36f),
                        )
                        drawCircle(
                            color = Color(0xFFF7D774),
                            radius = size.width * 0.06f,
                            center = Offset(size.width * 0.72f, size.height * 0.28f),
                        )
                    }
                    Text(
                        // 只有这里真的没有照片时才挂 TEST FRAME。这张字是在说
                        // "画面是假的"，不能出现在真成片上。
                        text = stringResource(R.string.result_frame_test),
                        style = PocketCcdType.MonoMicro,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(PocketCcdSpacing.sm),
                    )
                }
            }
            Spacer(modifier = Modifier.height(PocketCcdSpacing.sm))
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
                    text = stockLabel ?: stringResource(R.string.result_filter),
                    style = PocketCcdType.MonoMicro,
                    color = PocketCcdColors.TextSecondary,
                )
            }
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
                .size(8.dp)
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
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(PocketCcdRadius.pill))
                    .background(PocketCcdColors.Accent)
                    .clickable(role = Role.Button, onClick = onRetry)
                    .padding(
                        horizontal = PocketCcdSpacing.md,
                        vertical = PocketCcdSpacing.xs,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.result_retry),
                    style = PocketCcdType.MonoMicro,
                    color = PocketCcdColors.OnAccent,
                )
            }
        }
    }
}

@Composable
private fun FilmIcon() {
    val color = PocketCcdColors.TextPrimary
    Canvas(modifier = Modifier.size(PocketCcdSizes.icon)) {
        drawRect(
            color = color,
            topLeft = Offset(0f, size.height * 0.18f),
            size = Size(size.width, size.height * 0.64f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx()),
        )
        val hole = 2.dp.toPx()
        listOf(0.08f, 0.3f, 0.52f, 0.74f, 0.96f).forEach { fraction ->
            drawRect(
                color = color,
                topLeft = Offset(size.width * fraction - hole / 2, size.height * 0.08f),
                size = Size(hole, hole),
            )
            drawRect(
                color = color,
                topLeft = Offset(size.width * fraction - hole / 2, size.height * 0.88f),
                size = Size(hole, hole),
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFE6F0EA)
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

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFE6F0EA)
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
