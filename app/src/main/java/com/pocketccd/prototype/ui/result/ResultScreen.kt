package com.pocketccd.prototype.ui.result

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pocketccd.prototype.camera.PhotoExif
import com.pocketccd.prototype.camera.SavedPhoto
import com.pocketccd.prototype.camera.readPhotoExif
import com.pocketccd.prototype.ui.film.FilmIconKind
import com.pocketccd.prototype.ui.film.FilmRoundButton
import com.pocketccd.prototype.ui.film.FilmTheme
import com.pocketccd.prototype.ui.film.LocalFilmTheme
import java.util.Calendar

enum class ResultStatus {
    PROCESSING,
    OUTPUT_WRITING,
    SAVED,
    FAILED,
}

/** 页面实际呈现的四种形态。`status` + `photo` + `demo` 三个输入都折到这里。 */
private enum class Stage { SAVED, DEVELOPING, FAILED, EMPTY }

/**
 * 成片页（T9-018 / D-043 / D-045）。
 *
 * 视觉照 `docs/reference/pages/gallery.html` 的单张详情重排：顶部标题栏、
 * 居中的画框、`FRAME n / capacity` 行、三列参数网格、底部动作行。
 *
 * **数据只显示真的**：
 * - 画幅比例取 [SavedPhoto.width]/[SavedPhoto.height]，不写死 4:3；
 * - 参数网格全部来自 [readPhotoExif]，读不到的那一项显示 `—`；
 * - `FRAME` 行只在拿到真实本卷张数（[frameIndex]）时才画；
 * - 原型右上角的 `3:2 · 6000×4000` 换成真实像素尺寸，`DNG + JPG` 直接删掉（只出 JPEG）。
 *
 * 这个页面仍然不碰相机、文件和 MediaStore：只渲染传进来的状态。
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
    /** 这张照片是**本卷**第几张，来自 `AppViewModel.rollShots`。0 表示未知。 */
    frameIndex: Int = 0,
    rollCapacity: Int = 0,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    val stage = when {
        status == ResultStatus.FAILED -> Stage.FAILED
        photo != null -> Stage.SAVED
        demo -> Stage.EMPTY
        else -> Stage.DEVELOPING
    }

    val exif = remember(photo?.fullFile?.absolutePath) {
        photo?.fullFile?.let { readPhotoExif(it) } ?: PhotoExif()
    }
    val resolvedStock = photo?.stockLabel ?: stockLabel

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(p.appBg)
            .padding(horizontal = spec.metrics.gutterWide),
    ) {
        ResultTopBar(
            stage = stage,
            stockLabel = resolvedStock,
            canOpenWall = stage == Stage.SAVED,
            onBackToCamera = onBackToCamera,
            onOpenPhotoWall = onOpenPhotoWall,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            PhotoFrame(stage = stage, photo = photo, stockLabel = resolvedStock)
        }

        Spacer(modifier = Modifier.height(10.dp))

        FrameCaption(
            photo = photo,
            frameIndex = frameIndex,
            rollCapacity = rollCapacity,
        )

        Spacer(modifier = Modifier.height(10.dp))

        ParameterGrid(
            capturedAt = photo?.capturedAt,
            exif = exif,
            stockLabel = resolvedStock,
        )

        if (stage == Stage.FAILED && failureMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = failureMessage,
                style = MonoSmall.copy(color = p.error),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        StatusRow(status = status, stage = stage, photo = photo)

        Spacer(modifier = Modifier.height(14.dp))

        ActionRow(
            stage = stage,
            onPrimary = if (stage == Stage.FAILED) onRetry else onBackToCamera,
            onSecondary = if (stage == Stage.SAVED) onOpenPhotoWall else onBackToCamera,
            primaryLabel = if (stage == Stage.FAILED) "重试" else "继续拍摄",
            secondaryLabel = if (stage == Stage.SAVED) "打开相册" else "返回相机",
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ResultTopBar(
    stage: Stage,
    stockLabel: String?,
    canOpenWall: Boolean,
    onBackToCamera: () -> Unit,
    onOpenPhotoWall: () -> Unit,
) {
    val p = LocalFilmTheme.current.palette
    // 左右两侧都是 48dp 热区，标题因此天然居中，不需要再算偏移。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilmRoundButton(
            icon = FilmIconKind.Close,
            onClick = onBackToCamera,
            iconSize = 15.dp,
        )
        Spacer(modifier = Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(4f),
        ) {
            Text(
                text = when (stage) {
                    Stage.SAVED -> "已冲洗"
                    Stage.DEVELOPING -> "正在冲洗"
                    Stage.FAILED -> "冲洗失败"
                    Stage.EMPTY -> "还没有照片"
                },
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.04.em,
                    color = p.textPrimary,
                ),
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = stockLabel ?: "POCKET CCD",
                style = MonoSmall.copy(color = p.inkFaint),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        FilmRoundButton(
            icon = FilmIconKind.ImageMultiple,
            onClick = onOpenPhotoWall,
            enabled = canOpenWall,
            iconSize = 15.dp,
        )
    }
}

/**
 * 画框。宽度取 `min(可用宽, 可用高 × 画幅比)`，保证 4:3 的框在任何屏幕上
 * 都不会被压扁或溢出 —— 直接用 `aspectRatio` + `fillMaxWidth` 在小屏上会顶出父容器。
 */
@Composable
private fun PhotoFrame(
    stage: Stage,
    photo: SavedPhoto?,
    stockLabel: String?,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = RoundedCornerShape(10.dp)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val ratio = if (photo != null && photo.width > 0 && photo.height > 0) {
            photo.width.toFloat() / photo.height.toFloat()
        } else {
            4f / 3f
        }
        val frameWidth = minOf(maxWidth, maxHeight * ratio)
        val frameHeight = frameWidth / ratio

        Box(
            modifier = Modifier
                .size(frameWidth, frameHeight)
                .clip(shape)
                .background(spec.palette.windowBg)
                .border(spec.metrics.hairline, p.hairlineStrong, shape),
        ) {
            when (stage) {
                Stage.SAVED -> {
                    val bitmap = remember(photo?.thumbnailFile?.absolutePath) {
                        photo?.thumbnailFile?.absolutePath
                            ?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // 缩略图丢了但记录还在：说明文件被外部删了。照实说，不画假画面。
                        MissingFrameNote("缩略图缺失")
                    }
                    if (photo != null) {
                        FrameTag(
                            text = "${photo.width}×${photo.height}",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                        )
                    }
                    if (stockLabel != null) {
                        Text(
                            text = stockLabel,
                            style = MonoTag.copy(color = Color.White.copy(alpha = 0.66f)),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp),
                        )
                    }
                }

                Stage.DEVELOPING -> DevelopingFrame(stockLabel)
                Stage.FAILED -> MissingFrameNote("本次冲洗没有产出照片")
                Stage.EMPTY -> MissingFrameNote("按快门开始拍摄")
            }
        }
    }
}

@Composable
private fun DevelopingFrame(stockLabel: String?) {
    val p = LocalFilmTheme.current.palette
    val transition = rememberInfiniteTransition(label = "developing")
    val pulse by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "developing-pulse",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(p.windowBg, p.sheetBg, p.windowBg),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "DEVELOPING",
                style = MonoTag.copy(color = p.accent.copy(alpha = pulse)),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stockLabel ?: "POCKET CCD",
                style = MonoSmall.copy(color = p.inkGhost),
            )
        }
    }
}

@Composable
private fun MissingFrameNote(text: String) {
    val p = LocalFilmTheme.current.palette
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(p.windowBg, p.appBg)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MonoSmall.copy(color = p.inkFaint),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** 画框右上角的像素尺寸角标。原型这里写的是 `3:2 · 6000×4000`，改成真实值。 */
@Composable
private fun FrameTag(text: String, modifier: Modifier = Modifier) {
    val p = LocalFilmTheme.current.palette
    Text(
        text = text,
        style = MonoTag.copy(color = p.ink),
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = 0.30f))
            .border(1.dp, p.hairlineStrong, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/**
 * `FRAME 03 / 36` + 本机标识。
 *
 * 原型写死 `01 / 24`、右边 `KODAK 135 · DNG + JPG`。我们不产 DNG，
 * 也不按 24 张成卷，所以左边用真实的卷内序号，右边只留本机标识。
 */
@Composable
private fun FrameCaption(
    photo: SavedPhoto?,
    frameIndex: Int,
    rollCapacity: Int,
) {
    val p = LocalFilmTheme.current.palette
    val hasIndex = photo != null && frameIndex > 0 && rollCapacity > 0
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (hasIndex) {
                "FRAME ${"%02d".format(frameIndex)} / $rollCapacity"
            } else {
                // 没有真实序号就不画片号，不拿 01 充数。
                "POCKET CCD"
            },
            style = MonoSmall.copy(color = p.inkGhost),
        )
        Text(
            // 原型右侧是 `KODAK 135 · DNG + JPG`。我们只出 JPEG，也不存在"135"这个卷型，
            // 所以留能对得上的部分；条带数属于引擎内部指标，放在状态行右侧。
            text = "JPEG · 4:3",
            style = MonoSmall.copy(color = p.inkGhost),
        )
    }
}

/**
 * 三列参数网格。三个格子固定存在（缺项写 `—`），因为网格是三列布局，
 * 少一列会让整块塌掉；`—` 在等宽字体里读作"未知"，不是伪造的值。
 */
@Composable
private fun ParameterGrid(
    capturedAt: Long?,
    exif: PhotoExif,
    stockLabel: String?,
) {
    val p = LocalFilmTheme.current.palette
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.controlFill)
            .border(1.dp, p.hairline, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        ParamCell(
            label = "DATE",
            value = capturedAt?.let(::formatDateBlock) ?: "—",
            modifier = Modifier.weight(1f),
        )
        ParamCell(
            label = "EXPOSURE",
            value = buildString {
                val line = exif.exposureLine()
                if (line != null) append(line) else append("—")
                exif.isoLabel()?.let { append("\n").append(it) }
            },
            modifier = Modifier.weight(1.5f),
        )
        ParamCell(
            label = "FILM",
            value = stockLabel?.split(' ')?.joinToString("\n") ?: "—",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ParamCell(label: String, value: String, modifier: Modifier = Modifier) {
    val p = LocalFilmTheme.current.palette
    Column(modifier = modifier) {
        Text(text = label, style = MonoLabel.copy(color = p.inkGhost))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = MonoValue.copy(color = p.ink),
        )
    }
}

@Composable
private fun StatusRow(status: ResultStatus, stage: Stage, photo: SavedPhoto?) {
    val p = LocalFilmTheme.current.palette
    val dotColor = when (stage) {
        Stage.SAVED -> p.accent
        Stage.DEVELOPING -> p.inkMuted
        Stage.FAILED -> p.error
        Stage.EMPTY -> p.inkGhost
    }
    val label = when (stage) {
        Stage.SAVED -> if (photo?.galleryUri != null) "已存入系统相册" else "已保存到本机"
        Stage.DEVELOPING -> when (status) {
            ResultStatus.OUTPUT_WRITING -> "正在写入相册"
            else -> "正在冲洗"
        }
        Stage.FAILED -> "未产出照片"
        Stage.EMPTY -> "等待拍摄"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MonoSmall.copy(color = p.inkMuted))
        Spacer(modifier = Modifier.weight(1f))
        // 诊断行：冲洗耗时与条带数，都是实测值。
        if (photo != null) {
            Text(
                text = "${photo.renderMillis}ms · ${photo.strips} strips",
                style = MonoSmall.copy(color = p.inkGhost),
            )
        }
    }
}

@Composable
private fun ActionRow(
    stage: Stage,
    primaryLabel: String,
    secondaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    val p = LocalFilmTheme.current.palette
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(shape)
                .background(p.controlFill)
                .border(1.dp, p.hairlineStrong, shape)
                .clickable(role = Role.Button, onClick = onSecondary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = secondaryLabel,
                style = MonoLabel.copy(color = p.ink),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(shape)
                .background(if (stage == Stage.FAILED) p.error else p.textPrimary)
                .clickable(role = Role.Button, onClick = onPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = primaryLabel,
                style = MonoLabel.copy(color = p.windowBg),
            )
        }
    }
}

private fun formatDateBlock(epochMillis: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    return "%02d.%02d\n%02d:%02d".format(month, day, hour, minute)
}

private val MonoSmall = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 8.5.sp,
    letterSpacing = 0.08.em,
)

private val MonoTag = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 7.5.sp,
    letterSpacing = 0.16.em,
)

private val MonoLabel = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 9.sp,
    letterSpacing = 0.12.em,
)

private val MonoValue = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 9.5.sp,
    lineHeight = 12.sp,
    letterSpacing = 0.04.em,
)

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFF0B0C0E)
@Composable
private fun ResultDevelopingPreview() {
    FilmTheme {
        ResultScreen(
            status = ResultStatus.PROCESSING,
            onBackToCamera = {},
            onOpenPhotoWall = {},
            onRetry = {},
            stockLabel = "GOLD 200",
            frameIndex = 3,
            rollCapacity = 36,
        )
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFF0B0C0E)
@Composable
private fun ResultFailedPreview() {
    FilmTheme {
        ResultScreen(
            status = ResultStatus.FAILED,
            onBackToCamera = {},
            onOpenPhotoWall = {},
            onRetry = {},
            stockLabel = "GOLD 200",
            failureMessage = "「GOLD 200」需要 4096×3072，这台设备的采集尺寸只有 3264×2448。",
        )
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFF0B0C0E)
@Composable
private fun ResultEmptyPreview() {
    FilmTheme {
        ResultScreen(
            status = ResultStatus.SAVED,
            onBackToCamera = {},
            onOpenPhotoWall = {},
            onRetry = {},
            demo = true,
        )
    }
}
