package com.pocketccd.prototype.ui.detail

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.camera.PhotoExif
import com.pocketccd.prototype.camera.SavedPhoto
import com.pocketccd.prototype.camera.readPhotoExif
import com.pocketccd.prototype.ui.gallery.rememberThumbnail
import com.pocketccd.prototype.ui.glass.GlassButton
import com.pocketccd.prototype.ui.glass.GlassDivider
import com.pocketccd.prototype.ui.glass.GlassIcon
import com.pocketccd.prototype.ui.glass.GlassIconButton
import com.pocketccd.prototype.ui.glass.GlassIconKind
import com.pocketccd.prototype.ui.glass.GlassSurface
import com.pocketccd.prototype.ui.theme.GlassShape
import com.pocketccd.prototype.ui.theme.GlassSpace
import com.pocketccd.prototype.ui.theme.GlassType
import com.pocketccd.prototype.ui.theme.glass
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 成片页的阶段。与 `CaptureState` 一一对应，但 UI 不需要认识 ViewModel 的类。 */
enum class DetailStatus { Processing, Writing, Saved, Failed }

/**
 * 单张详情：**刚拍完**和**从相册翻回来**共用这一屏。
 *
 * 这是相对旧版最重要的一处结构修正（复审 P1-4）：旧版把"成片页"绑死在快门路径上，
 * 从照片墙点开任何历史照片只能看到日期和序号，EXIF 与动作（分享/收藏）一整块缺失。
 * 现在两条入口都进这里，参数网格、动作行、状态行全部同源。
 *
 * 数据只显示真的（D-002 / D-043）：EXIF 从文件里读，读不到写"—"，机身型号取
 * `Build.MODEL`，不补 ISO / 光圈 / 快门，也不编"第几张"（卷模型已移除，D-070）。
 */
@Composable
fun PhotoDetailScreen(
    status: DetailStatus,
    modifier: Modifier = Modifier,
    photo: SavedPhoto? = null,
    stockLabel: String? = null,
    failureMessage: String? = null,
    isFavorite: Boolean = false,
    /**
     * 这张是否已经写进系统相册。
     *
     * **不能由本页从 `photo.galleryUri` 推**：那个值只在"刚拍完"的内存记录里有，
     * 从私有目录重建的历史照片一律为 null，于是每一张老照片都会显示"相册未写入"。
     * 调用方以 `PhotoStore.isPublished(id)` 为准，判定归数据层。
     */
    galleryPublished: Boolean = photo?.galleryUri != null,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onShare: () -> Unit = {},
    onOpenInGallery: () -> Unit = {},
) {
    val p = MaterialTheme.glass
    val file = photo?.fullFile

    // EXIF 不能在 composition 里同步读：一张 3–5MB 的 JPEG 要开文件、解析头。
    val exif by produceState(initialValue = PhotoExif(), key1 = file?.absolutePath) {
        value = if (file == null) PhotoExif() else withContext(Dispatchers.IO) { readPhotoExif(file) }
    }
    val bytes by produceState(initialValue = 0L, key1 = file?.absolutePath) {
        value = if (file == null) 0L else withContext(Dispatchers.IO) { file.length() }
    }
    val deviceModel = remember {
        Build.MODEL?.trim()?.takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
    }

    // 秒表只在冲洗途中走：完成/失败之后停在最后一个值，不再跳。
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(status) {
        if (status != DetailStatus.Processing && status != DetailStatus.Writing) {
            return@LaunchedEffect
        }
        while (true) {
            elapsed++
            delay(1_000)
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = GlassSpace.page, end = GlassSpace.page, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                label = "返回",
                onClick = onBack,
                icon = { tint -> GlassIcon(GlassIconKind.Back, tint) },
            )
            Spacer(Modifier.width(GlassSpace.gap))
            Column(Modifier.weight(1f)) {
                Text(
                    text = photo?.stockLabel ?: stockLabel ?: "成片",
                    style = GlassType.cardTitle,
                    color = p.onGlass,
                )
                Text(
                    text = when (status) {
                        DetailStatus.Processing -> "显影中"
                        DetailStatus.Writing -> "写入中"
                        DetailStatus.Saved -> "已完成"
                        DetailStatus.Failed -> "冲洗失败"
                    },
                    style = GlassType.badge,
                    color = p.onGlassMuted,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GlassSpace.page),
            verticalArrangement = Arrangement.spacedBy(GlassSpace.gap),
        ) {
            PrintBox(
                photo = photo,
                status = status,
                elapsedSeconds = elapsed,
                failureMessage = failureMessage,
            )

            when (status) {
                DetailStatus.Failed -> {
                    GlassButton(
                        text = "重试",
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "重试会重新按一次快门，生成一张新的照片；刚才那张若已落盘，" +
                            "会留在相册里。",
                        style = GlassType.caption,
                        color = p.onGlassMuted,
                    )
                }

                DetailStatus.Processing, DetailStatus.Writing -> Unit

                DetailStatus.Saved -> InfoCard(
                    exif = exif,
                    capturedAt = photo?.capturedAt,
                    deviceModel = deviceModel,
                    bytes = bytes,
                    galleryPublished = galleryPublished,
                )
            }
            // 动作条不再放在滚动内容里：3:4 的成片一占就 540dp，收藏/分享/相册
            // 三个按钮在 891dp 高的屏幕上永远要滚一下才看得见。它们现在是固定底栏。
            Spacer(Modifier.height(GlassSpace.inner))
        }

        if (status == DetailStatus.Saved) {
            ActionCard(
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onShare = onShare,
                onOpenInGallery = onOpenInGallery,
                galleryPublished = galleryPublished,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(
                        start = GlassSpace.page,
                        end = GlassSpace.page,
                        top = GlassSpace.tight,
                        bottom = GlassSpace.inner,
                    ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 印相区
// ---------------------------------------------------------------------------

/**
 * 印相框：冲洗中转进度环，完成显示成片，失败显示原因。
 *
 * 成片只读缩略图（长边 1440）：详情页最大显示宽度就是屏幕宽，
 * 解原图 12MP（48MB ARGB）在这里没有任何收益，只会让低端机掉帧。
 */
@Composable
private fun PrintBox(
    photo: SavedPhoto?,
    status: DetailStatus,
    elapsedSeconds: Int,
    failureMessage: String?,
) {
    val p = MaterialTheme.glass
    val shape = GlassShape.image
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (photo != null) 3f / 4f else 4f / 3f)
            .clip(shape)
            .background(p.fill),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            DetailStatus.Failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                GlassIcon(GlassIconKind.Warning, p.error, size = 30.dp)
                Spacer(Modifier.height(GlassSpace.gap))
                Text(
                    text = failureMessage ?: "冲洗失败",
                    style = GlassType.body,
                    color = p.onGlass,
                    textAlign = TextAlign.Center,
                )
            }

            DetailStatus.Processing, DetailStatus.Writing -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DevelopingRing(progressHint = elapsedSeconds)
                Spacer(Modifier.height(GlassSpace.inner))
                Text(
                    text = if (status == DetailStatus.Writing) "正在写入" else "正在显影",
                    style = GlassType.cardTitle,
                    color = p.onGlass,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "已用 %02d 秒".format(elapsedSeconds),
                    style = GlassType.numeric,
                    color = p.onGlassMuted,
                )
            }

            DetailStatus.Saved -> {
                val bitmap = rememberThumbnail(photo?.thumbnailFile?.absolutePath)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "成片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GlassIcon(GlassIconKind.Gallery, p.onGlassMuted, size = 26.dp)
                        Spacer(Modifier.height(GlassSpace.tight))
                        Text(
                            text = "缩略图缺失，原图仍在应用目录里",
                            style = GlassType.caption,
                            color = p.onGlassMuted,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 显影进度环。
 *
 * **不是百分比**：冲洗耗时与机型/内容强相关（实测 3.9–5.8 秒，跨度不大但没有上限），
 * 画一个"37%"就是在编数据。这里是一个持续旋转的弧 —— 表达"在动"，不承诺"到哪了"。
 */
@Composable
private fun DevelopingRing(progressHint: Int) {
    val p = MaterialTheme.glass
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60)
            tick++
        }
    }
    val angle by animateFloatAsState(
        targetValue = (tick * 9f) % 360f,
        animationSpec = tween(60),
        label = "develop-spin",
    )
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(72.dp)) {
            val stroke = 3.dp.toPx()
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = (size.minDimension - stroke) / 2f,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = p.accent,
                startAngle = angle,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = androidx.compose.ui.geometry.Size(
                    size.minDimension - stroke,
                    size.minDimension - stroke,
                ),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "%02d".format(progressHint),
            style = GlassType.numericLarge,
            color = p.onGlass,
        )
    }
}

// ---------------------------------------------------------------------------
// 参数 / 动作
// ---------------------------------------------------------------------------

@Composable
private fun InfoCard(
    exif: PhotoExif,
    capturedAt: Long?,
    deviceModel: String?,
    bytes: Long,
    galleryPublished: Boolean,
) {
    val p = MaterialTheme.glass
    GlassSurface(
        shape = GlassShape.card,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column {
            Text(text = "拍摄参数", style = GlassType.caption, color = p.onGlassMuted)
            Spacer(Modifier.height(GlassSpace.tight))
            InfoRow("时间", capturedAt?.let(::fullStamp) ?: "—")
            InfoRow("曝光", exif.exposureLine() ?: "—")
            InfoRow("感光度", exif.isoLabel() ?: "—")
            InfoRow("焦距", exif.focalLabel() ?: "—")
            InfoRow("机身", deviceModel ?: "—")
            InfoRow("文件", if (bytes > 0) humanBytes(bytes) else "—")
            Spacer(Modifier.height(GlassSpace.tight))
            GlassDivider()
            Spacer(Modifier.height(GlassSpace.tight))
            Text(
                text = if (galleryPublished) {
                    "已写入系统相册：分享、备份、在其它应用里打开都能看到它。"
                } else {
                    "只存在本应用目录里（相册写入没成功）。分享仍然可用 —— " +
                        "走应用自己的文件授权，不依赖相册副本。"
                },
                style = GlassType.caption,
                color = if (galleryPublished) p.onGlassMuted else p.error,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val p = MaterialTheme.glass
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = GlassType.caption, color = p.onGlassMuted, modifier = Modifier.width(64.dp))
        Text(text = value, style = GlassType.numeric, color = p.onGlass)
    }
}

@Composable
private fun ActionCard(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onOpenInGallery: () -> Unit,
    galleryPublished: Boolean,
    modifier: Modifier = Modifier,
) {
    val p = MaterialTheme.glass
    GlassSurface(
        shape = GlassShape.panel,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionItem(
                label = if (isFavorite) "已收藏" else "收藏",
                kind = GlassIconKind.Heart,
                active = isFavorite,
                onClick = onToggleFavorite,
            )
            ActionItem(label = "分享", kind = GlassIconKind.Share, onClick = onShare)
            ActionItem(
                label = if (galleryPublished) "在相册打开" else "相册未写入",
                kind = GlassIconKind.Gallery,
                onClick = onOpenInGallery,
                enabled = galleryPublished,
            )
        }
    }
}

@Composable
private fun ActionItem(
    label: String,
    kind: GlassIconKind,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    val p = MaterialTheme.glass
    Column(
        modifier = Modifier
            .width(104.dp)
            .clip(GlassShape.chip)
            .background(if (active) p.accent.copy(alpha = 0.16f) else Color.Transparent)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassIconButton(
            label = label,
            active = active,
            enabled = enabled,
            onClick = onClick,
            icon = { tint -> GlassIcon(kind, tint, filled = active) },
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = GlassType.badge,
            color = if (!enabled) p.onGlassMuted.copy(alpha = 0.5f) else if (active) p.accent else p.onGlassMuted,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// 格式化
// ---------------------------------------------------------------------------

private fun fullStamp(epochMillis: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
    return "%04d.%02d.%02d %02d:%02d".format(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
    )
}

/** `3.1 MB` / `812 KB`。按 1024 进制，和系统文件管理器口径一致。 */
private fun humanBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%d KB".format(bytes / 1024)
    else -> "$bytes B"
}

