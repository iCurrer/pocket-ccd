package com.pocketccd.prototype.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.withContext

/**
 * 全屏查看器。
 *
 * 保留旧版真正好用的一点：**左右首尾相连**（`HorizontalPager` 没有循环开关，
 * 用「大页数 + 取模」模拟，起点落在中段，两个方向都有余量）。
 *
 * 与旧版的三处不同：
 * 1. 底部不再是纸面 caption，而是一块玻璃：卷名 / 日期时间 / 收藏 / 详情；
 * 2. **增加「详情」入口** —— 旧版只能看到日期和列表序号，EXIF 完全拿不到
 *    （复审 P1-4）；现在从查看器可以进入统一的单张详情页，和刚拍完那一屏是同一个组件；
 * 3. 大图按实际显示框采样解码，12MP 直解 48MB 在低端机上必 OOM。
 */
@Composable
fun PhotoViewer(
    photos: List<PhotoWallItem>,
    initialIndex: Int,
    favoriteIds: Set<String>,
    modifier: Modifier = Modifier,
    onToggleFavorite: (PhotoWallItem) -> Unit = {},
    onOpenDetail: (PhotoWallItem) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val p = MaterialTheme.glass

    // 列表被删空时查看器不该存在：自动关闭，而不是画一个只剩关闭按钮的页面。
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val total = photos.size
    val safeIndex = initialIndex.coerceIn(0, total - 1)
    val pagerState = rememberPagerState(
        initialPage = loopStartPage(total, safeIndex),
        pageCount = { loopPageCount(total) },
    )
    val currentIndex = circularIndex(pagerState.currentPage, total)
    val current = photos[currentIndex]

    BackHandler { onClose() }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val reqWidth = with(density) { maxWidth.toPx().toInt() }.coerceAtLeast(1)
            val reqHeight = with(density) { maxHeight.toPx().toInt() }.coerceAtLeast(1)

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                ViewerPage(
                    item = photos[circularIndex(page, total)],
                    reqWidth = reqWidth,
                    reqHeight = reqHeight,
                )
            }
        }

        // 顶部：关闭 + 位置。用玻璃圆钮而不是裸图标 —— 亮画面上纯白图标会消失。
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = GlassSpace.page, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                label = "关闭",
                overPhoto = true,
                onClick = onClose,
                icon = { tint -> GlassIcon(GlassIconKind.Close, tint) },
            )
            Spacer(Modifier.weight(1f))
            GlassSurface(
                shape = GlassShape.chip,
                overPhoto = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "%02d / %02d".format(currentIndex + 1, total),
                    style = GlassType.numeric,
                    color = p.onGlass,
                )
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GlassSpace.page),
                shape = GlassShape.panel,
                overPhoto = true,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = current.stockLabel,
                                style = GlassType.cardTitle,
                                color = p.onGlass,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = fullTime(current.capturedAt),
                                style = GlassType.numeric,
                                color = p.onGlassMuted,
                            )
                        }
                        GlassIconButton(
                            label = if (current.id in favoriteIds) "取消收藏" else "收藏",
                            active = current.id in favoriteIds,
                            overPhoto = true,
                            onClick = { onToggleFavorite(current) },
                            icon = { tint ->
                                GlassIcon(
                                    kind = GlassIconKind.Heart,
                                    tint = tint,
                                    filled = current.id in favoriteIds,
                                )
                            },
                        )
                        Spacer(Modifier.width(GlassSpace.tight))
                        GlassIconButton(
                            label = "查看详情",
                            overPhoto = true,
                            onClick = { onOpenDetail(current) },
                            icon = { tint -> GlassIcon(GlassIconKind.Info, tint) },
                        )
                    }
                    Spacer(Modifier.height(GlassSpace.gap))
                    GlassDivider()
                    Spacer(Modifier.height(GlassSpace.gap))
                    Text(
                        text = "左右滑动切换 · 详情里能看到真实的拍摄参数",
                        style = GlassType.caption,
                        color = p.onGlassMuted,
                    )
                }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(Modifier.height(GlassSpace.gap))
        }
    }
}

@Composable
private fun ViewerPage(item: PhotoWallItem, reqWidth: Int, reqHeight: Int) {
    val bitmap = rememberViewerBitmap(item, reqWidth, reqHeight)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "${item.stockLabel} 拍摄的照片",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
            )
        } else {
            Text(
                text = "这张照片读不出来",
                style = GlassType.caption,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * 查看器大图：优先原图，缺失/损坏时退回缩略图。
 *
 * 不做 LRU 缓存是刻意的：一张采样后的大图约 12MB，缓存两页就会把堆吃掉四分之一；
 * 而查看器一次只显示一页，滑回去重解一次比赌内存安全。
 */
@Composable
private fun rememberViewerBitmap(
    item: PhotoWallItem,
    reqWidth: Int,
    reqHeight: Int,
): ImageBitmap? {
    val key = item.fullImagePath ?: item.imagePath
    val state = produceState<ImageBitmap?>(
        initialValue = null,
        key1 = key,
        key2 = reqWidth,
        key3 = reqHeight,
    ) {
        value = withContext(Dispatchers.IO) {
            val primary = item.fullImagePath
            val fallback = item.imagePath
            val decoded = primary?.let { decodeSampledBitmap(it, reqWidth, reqHeight) }
                ?: fallback?.let { decodeSampledBitmap(it, reqWidth, reqHeight) }
                ?: return@withContext null
            runCatching { decoded.asImageBitmap() }.getOrNull()
        }
    }
    return state.value
}

/** `2026.10.12 06:42`。日期与时间分开取，跨年也读得懂。 */
private fun fullTime(epochMillis: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
    return "%04d.%02d.%02d %02d:%02d".format(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
    )
}
