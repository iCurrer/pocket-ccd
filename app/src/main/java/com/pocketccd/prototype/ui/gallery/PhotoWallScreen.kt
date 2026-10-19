package com.pocketccd.prototype.ui.gallery

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pocketccd.prototype.ui.film.FilmIcon
import com.pocketccd.prototype.ui.film.FilmIconKind
import com.pocketccd.prototype.ui.film.FilmRoundButton
import com.pocketccd.prototype.ui.film.FilmTheme
import com.pocketccd.prototype.ui.film.LocalFilmTheme
import java.util.Calendar

/**
 * 胶片相册（T9-019 / D-043 / D-045）。
 *
 * 视觉照 `docs/reference/pages/gallery.html` 的列表重排：居中标题栏、按卷分组、
 * 三列缩略图、每格右下角时间戳。
 *
 * **分组口径**：按胶卷名分组，组内按拍摄时间倒序。这是能对得上数据的唯一分法 ——
 * 历史照片没有持久化「第几卷」的边界，按时间间隔猜一个新卷属于编造。
 * 因此同一款卷换两次卷会合成一组，界面上写「按胶卷分组」把这件事讲明白。
 *
 * **不画的**：原型顶部那张「KODAK GOLD 200 · 冲扫中 72% · 已扫描 18/36」卡片，
 * 以及底部 `SCANNED BY 光屋 LAB` 水印。本项目没有外部冲扫服务（D-042）。
 */
@Composable
fun PhotoWallScreen(
    onBack: () -> Unit,
    photos: List<PhotoWallItem> = emptyList(),
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    val groups = remember(photos) { photos.toRollGroups() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.appBg)
            .padding(horizontal = spec.metrics.gutterWide),
    ) {
        WallTopBar(
            total = photos.size,
            onBack = onBack,
        )

        if (groups.isEmpty()) {
            EmptyArchive(modifier = Modifier.weight(1f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                groups.forEach { group ->
                    item(
                        key = "header-${group.label}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        GroupHeader(group = group)
                    }
                    items(items = group.photos, key = { it.id }) { item ->
                        PhotoTile(item = item)
                    }
                    // 组间空一行的视觉呼吸，靠一个跨列空 item 实现；
                    // 直接在组头加 top padding 会让第一组顶到标题栏上。
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WallTopBar(total: Int, onBack: () -> Unit) {
    val p = LocalFilmTheme.current.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilmRoundButton(
            icon = FilmIconKind.Close,
            onClick = onBack,
            iconSize = 15.dp,
        )
        Spacer(modifier = Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(4f),
        ) {
            Text(
                text = "胶片相册",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.04.em,
                    color = p.textPrimary,
                ),
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "ARCHIVE · 2026",
                style = MonoCaption.copy(color = p.inkFaint),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        // 数量固定占 48dp，和左侧关闭按钮等宽，标题因此是真的居中。
        Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
            Text(
                text = total.toString().padStart(2, '0'),
                style = MonoCaption.copy(color = p.inkMuted),
            )
        }
    }
}

/** 组头：卷名 + 张数 + 日期区间，上沿一条细线把组隔开。 */
@Composable
private fun GroupHeader(group: PhotoGroup) {
    val p = LocalFilmTheme.current.palette
    Column(modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(p.hairline),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.label,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.02.em,
                        color = p.textPrimary,
                    ),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = group.rangeLabel(),
                    style = MonoCaption.copy(color = p.inkGhost),
                )
            }
            Text(
                text = "${group.photos.size} 张",
                style = MonoCaption.copy(color = p.inkFaint),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun PhotoTile(item: PhotoWallItem) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = RoundedCornerShape(9.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(p.windowBg)
            .border(spec.metrics.hairline, p.hairline, shape),
    ) {
        // 只解缩略图（长边 1440、约 200KB）。列表里解 12MP 原图会瞬间打满堆。
        val bitmap = remember(item.imagePath) {
            item.imagePath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // 缩略图缺失（文件被外部删了 / 预览条目）。画一片更暗的底，
            // 不画任何"像照片"的渐变，避免把占位当成真片子。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(p.sheetBg, p.windowBg),
                        ),
                    ),
            )
        }
        // 底部压深，保证时间戳在亮画面上也读得清。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f),
                    ),
                ),
        )
        Text(
            text = formatClock(item.capturedAt),
            style = MonoCaption.copy(color = Color.White.copy(alpha = 0.78f)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 6.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun EmptyArchive(modifier: Modifier = Modifier) {
    val p = LocalFilmTheme.current.palette
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, p.hairline, RoundedCornerShape(18.dp))
                .padding(horizontal = 24.dp, vertical = 40.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(p.controlFill)
                    .border(1.dp, p.hairline, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                FilmRoundButtonIcon()
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "还没有底片",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = p.textPrimary,
                ),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "按下快门，第一卷从这里开始",
                style = MonoCaption.copy(color = p.inkFaint),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 空态里的小胶片图标。复用取景页那套手绘几何，不再引第二套资源。 */
@Composable
private fun FilmRoundButtonIcon() {
    val p = LocalFilmTheme.current.palette
    FilmIcon(
        kind = FilmIconKind.Filmstrip,
        tint = p.inkMuted,
        iconSize = 20.dp,
    )
}

// ---------------------------------------------------------------- data

/**
 * 相册条目。只保留能对得上文件的部分：卷名、拍摄时间、缩略图路径。
 * 原型里的 `filter` / 占位渐变色没有数据来源，已删除。
 */
data class PhotoWallItem(
    val id: String,
    val stockLabel: String,
    val capturedAt: Long,
    /** 缩略图的绝对路径。为空表示文件缺失或这是预览条目。 */
    val imagePath: String? = null,
)

/** 一组同款胶卷的片子。 */
data class PhotoGroup(
    val label: String,
    val photos: List<PhotoWallItem>,
) {
    /** `2026.10.12 — 10.19`；同一天时只显示一个日期。 */
    fun rangeLabel(): String {
        val first = photos.minOf { it.capturedAt }
        val last = photos.maxOf { it.capturedAt }
        val firstDay = formatDay(first)
        val lastDay = formatDay(last)
        return if (firstDay == lastDay) firstDay else "$firstDay — $lastDay"
    }
}

private fun List<PhotoWallItem>.toRollGroups(): List<PhotoGroup> =
    groupBy { it.stockLabel }
        .map { (label, items) -> PhotoGroup(label, items.sortedByDescending { it.capturedAt }) }
        .sortedByDescending { it.photos.first().capturedAt }

private fun formatClock(epochMillis: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
    return "%02d:%02d".format(
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
    )
}

private fun formatDay(epochMillis: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
    return "%04d.%02d.%02d".format(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
    )
}

private val MonoCaption = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 8.sp,
    letterSpacing = 0.14.em,
)

// ---------------------------------------------------------------- preview

private val PreviewPhotos = listOf(
    PhotoWallItem("p1", "GOLD 200", 1_760_700_000_000),
    PhotoWallItem("p2", "GOLD 200", 1_760_694_000_000),
    PhotoWallItem("p3", "GOLD 200", 1_760_688_000_000),
    PhotoWallItem("p4", "PORTRA 400", 1_760_200_000_000),
    PhotoWallItem("p5", "PORTRA 400", 1_760_194_000_000),
)

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFF0B0C0E)
@Composable
private fun PhotoWallPreview() {
    FilmTheme {
        PhotoWallScreen(onBack = {}, photos = PreviewPhotos)
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFF0B0C0E)
@Composable
private fun EmptyPhotoWallPreview() {
    FilmTheme {
        PhotoWallScreen(onBack = {}, photos = emptyList())
    }
}
