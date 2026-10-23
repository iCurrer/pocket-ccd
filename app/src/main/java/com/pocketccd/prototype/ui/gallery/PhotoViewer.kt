package com.pocketccd.prototype.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.ui.film.FilmIcon
import com.pocketccd.prototype.ui.film.FilmIconKind
import com.pocketccd.prototype.ui.film.FilmPaperSpec
import com.pocketccd.prototype.ui.film.FilmTheme
import com.pocketccd.prototype.ui.film.LocalFilmTheme
import com.pocketccd.prototype.ui.film.PaperButton
import com.pocketccd.prototype.ui.film.filmClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 全屏底片查看器（D-063「白纸档案」C 方向：黑底灯箱 + 纸面 caption）。
 *
 * **为什么这么做**
 * - 灯箱是黑底 `windowBg`：照片是唯一的光源，纸面只留在底部一条 caption 上
 *   （Serif 卷名 + mono 日期/帧号 + `收藏` / `关闭` 两个纸面按钮）。
 * - 大图优先读 `fullImagePath`，缺失/解码失败时回退 `imagePath`（缩略图）。照片墙只读缩略图，
 *   查看器要的是原图；但历史照片/外部删除会让原图缺失，回退一张能看的图好过一片黑。
 * - 解码走 `Dispatchers.IO` + `inSampleSize`（按实际显示框算）：12MP 的 ARGB_8888 是 48MB，
 *   直接 `decodeFile` 在低端机上必 OOM。这里 1080p 屏通常 sample=2，解出约 12MB。
 * - 左右滑动**首尾相连**：`HorizontalPager` 没有循环开关，用「大页数 + 取模」模拟
 *   （[loopPageCount] / [loopStartPage] / [circularIndex]），起点落在循环中段，两个方向都有余量。
 * - `BackHandler` 先关查看器；查看器关闭后这个 composable 被移除，返回键才交还给相册页
 *   （由 `AppRoot` 的 BackHandler 处理）。
 *
 * **不做的**
 * - 不做双击/捏合缩放：查看器只负责"看清这张"，缩放是另一个交互问题，加了还要处理与
 *   左右滑动手势的冲突（HorizontalPager 会抢横向拖动）。
 * - 不做上一张/下一张按钮：屏幕小，底部只留收藏和关闭；滑动已经表达了同一件事。
 * - 不显示 EXIF/尺寸/曝光假数据：`PhotoStore.list()` 从文件重建的记录 `width/height` 是 0，
 *   曝光参数从未持久化。底部的每个数字都有真实来源：日期、时间、组内帧号、`03 / 12`。
 * - 不用金色星：收藏是石板蓝小方块 + 文案「已收藏」，和照片墙角标是同一个记号。
 */
@Composable
fun PhotoViewer(
    photos: List<PhotoWallItem>,
    initialIndex: Int,
    favoriteIds: Set<String>,
    onToggleFavorite: (PhotoWallItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalFilmTheme.current.palette

    // 空列表时查看器不该存在（调用点只在有照片时打开）。防御：自动关闭而不是画一个
    // 除关闭按钮外什么都没有的页面。
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val total = photos.size
    val safeInitialIndex = initialIndex.coerceIn(0, total - 1)
    val pagerState = rememberPagerState(
        initialPage = loopStartPage(total, safeInitialIndex),
        pageCount = { loopPageCount(total) },
    )
    val currentIndex = circularIndex(pagerState.currentPage, total)
    val current = photos[currentIndex]

    BackHandler { onClose() }

    // 吞掉背景上的点击：查看器是不透明的一层，但 Compose 的点击命中不会因为"被盖住"
    // 就自动失效——不加这个空 clickable，点头部空白会点到背后照片墙的格子、拖动会滚动
    // 背后的网格。子级（pager / 按钮）先拿事件，不受影响。
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(p.windowBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LightboxTopBar(index = currentIndex, total = total, onClose = onClose)

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                // 解码目标直接取图片区的像素尺寸：宽高都是 px，不需要再乘 density。
                val density = LocalDensity.current
                val reqWidth = with(density) { maxWidth.toPx().toInt() }.coerceAtLeast(1)
                val reqHeight = with(density) { maxHeight.toPx().toInt() }.coerceAtLeast(1)

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    ViewerPage(
                        item = photos[circularIndex(page, total)],
                        reqWidth = reqWidth,
                        reqHeight = reqHeight,
                    )
                }
            }

            LightboxCaption(
                item = current,
                frameNumber = archiveFrameNumber(photos, currentIndex),
                isFavorite = current.id in favoriteIds,
                onToggleFavorite = { onToggleFavorite(current) },
                onClose = onClose,
            )
        }
    }
}

/**
 * 顶部：返回（左，48dp 热区）+ `03 / 12`（右，mono）。
 *
 * 黑底上不能复用 `FilmRoundButton`：它的图标色取纸面令牌（墨色），在灯箱里会消失。
 * 这里手写 48dp 圆形热区，文字/图标用纸白 `appBg` 派生。
 */
@Composable
private fun LightboxTopBar(index: Int, total: Int, onClose: () -> Unit) {
    val spec = LocalFilmTheme.current
    val ink = spec.palette.appBg
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(spec.metrics.touchTarget)
                .clip(CircleShape)
                .filmClickable(
                    onClick = onClose,
                    onClickLabel = "关闭查看器",
                    scaleTo = 0.97f,
                    overlay = ink,
                    overlayAlpha = 0.10f,
                )
                .semantics { contentDescription = "返回" },
            contentAlignment = Alignment.Center,
        ) {
            FilmIcon(
                kind = FilmIconKind.Close,
                tint = ink.copy(alpha = 0.86f),
                iconSize = 16.dp,
                strokeWidth = 1.5.dp,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "${(index + 1).toString().padStart(2, '0')} / ${total.toString().padStart(2, '0')}",
            style = paperMonoStyle(spec, ink.copy(alpha = 0.86f), size = spec.type.mono),
        )
    }
}

/**
 * 底部纸面 caption：Serif 卷名（+ 收藏方块）+ mono 日期/帧号 + `收藏` / `关闭` 纸面按钮。
 *
 * 按钮用共享 `PaperButton`：收藏是唯一的主操作（墨底），已收藏后降为 1px 描边 + 文案
 * 「已收藏」；关闭始终是描边次按钮。
 */
@Composable
private fun LightboxCaption(
    item: PhotoWallItem,
    frameNumber: Int,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.sheetBg)
            .navigationBarsPadding()
            .padding(horizontal = spec.metrics.pageMargin, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.stockLabel,
                color = p.textPrimary,
                fontFamily = spec.type.titleFamily,
                fontSize = spec.type.optionTitle,
                lineHeight = spec.type.bodyLineHeight,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isFavorite) {
                Spacer(Modifier.width(8.dp))
                // 收藏态用石板蓝小方块表达：和照片墙格子上的角标是同一个记号。
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(p.accent, RoundedCornerShape(spec.metrics.cardRadius)),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${formatDay(item.capturedAt)}  ${formatClock(item.capturedAt)}  ·  " +
                "FRAME ${frameNumber.toString().padStart(2, '0')}",
            style = paperMonoStyle(spec, p.textTertiary),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PaperButton(
                text = if (isFavorite) "已收藏" else "收藏",
                onClick = onToggleFavorite,
                primary = !isFavorite,
                secondary = isFavorite,
                modifier = Modifier.weight(1f),
            )
            PaperButton(
                text = "关闭",
                onClick = onClose,
                primary = false,
                secondary = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 单页大图。`produceState` 负责"页面被回收时取消解码"——快速滑动不会留下一串
 * 后台解码任务把 CPU/内存吃满。解码本身在 `Dispatchers.IO` 上，主线程只做包装。
 */
@Composable
private fun ViewerPage(item: PhotoWallItem, reqWidth: Int, reqHeight: Int) {
    val spec = LocalFilmTheme.current
    val ink = spec.palette.appBg
    val primaryPath = item.fullImagePath ?: item.imagePath
    val fallbackPath = item.imagePath

    val state by produceState<ViewerImageState>(
        ViewerImageState.Loading,
        primaryPath,
        fallbackPath,
        reqWidth to reqHeight,
    ) {
        if (primaryPath == null) {
            value = ViewerImageState.Missing
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            val decoded = decodeSampledBitmap(primaryPath, reqWidth, reqHeight)
                ?: fallbackPath
                    ?.takeIf { it != primaryPath }
                    ?.let { decodeSampledBitmap(it, reqWidth, reqHeight) }
            if (decoded != null) {
                ViewerImageState.Ready(decoded.asImageBitmap())
            } else {
                ViewerImageState.Missing
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (val imageState = state) {
            ViewerImageState.Loading -> Text(
                text = "正在读取底片…",
                style = paperMonoStyle(spec, ink.copy(alpha = 0.62f), size = spec.type.mono),
            )

            ViewerImageState.Missing -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "底片文件读不到",
                    color = ink,
                    fontFamily = spec.type.bodyFamily,
                    fontSize = spec.type.body,
                    lineHeight = spec.type.bodyLineHeight,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "可能已经在系统相册里被删掉",
                    style = paperMonoStyle(spec, ink.copy(alpha = 0.62f)),
                )
            }

            is ViewerImageState.Ready -> Image(
                bitmap = imageState.bitmap,
                // 照片本身是装饰信息：卷名/序号/时间已经在顶部与底部用文字讲清楚了，
                // 再给图片一段重复的 contentDescription 只会让读屏更啰嗦。
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private sealed interface ViewerImageState {
    data object Loading : ViewerImageState
    data class Ready(val bitmap: ImageBitmap) : ViewerImageState
    data object Missing : ViewerImageState
}

// ---------------------------------------------------------------- preview

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFF0A0B0C)
@Composable
private fun PhotoViewerPreview() {
    FilmTheme(FilmPaperSpec) {
        PhotoViewer(
            photos = listOf(
                PhotoWallItem(
                    id = "preview-1",
                    stockLabel = "GOLD 200",
                    capturedAt = 1_760_700_000_000,
                ),
                PhotoWallItem(
                    id = "preview-2",
                    stockLabel = "GOLD 200",
                    capturedAt = 1_760_694_000_000,
                ),
            ),
            initialIndex = 0,
            favoriteIds = setOf("preview-1"),
            onToggleFavorite = {},
            onClose = {},
        )
    }
}
