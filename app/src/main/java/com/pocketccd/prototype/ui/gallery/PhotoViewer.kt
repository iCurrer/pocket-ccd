package com.pocketccd.prototype.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pocketccd.prototype.ui.film.FilmDarkSpec
import com.pocketccd.prototype.ui.film.FilmIcon
import com.pocketccd.prototype.ui.film.FilmIconKind
import com.pocketccd.prototype.ui.film.FilmTheme
import com.pocketccd.prototype.ui.film.LocalFilmTheme
import com.pocketccd.prototype.ui.film.filmClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 全屏底片查看器（T9-015）。
 *
 * **为什么这么做**
 * - 大图优先读 `fullImagePath`，缺失/解码失败时回退 `imagePath`（缩略图）。照片墙只读缩略图，
 *   查看器要的是原图；但历史照片/外部删除会让原图缺失，回退一张能看的图好过一片黑。
 * - 解码走 `Dispatchers.IO` + `inSampleSize`（按实际显示框算）：12MP 的 ARGB_8888 是 48MB，
 *   直接 `decodeFile` 在低端机上必 OOM。这里 1080p 屏通常 sample=2，解出约 12MB。
 * - 左右滑动**首尾相连**：`HorizontalPager` 没有循环开关，用「大页数 + 取模」模拟
 *   （[loopPageCount] / [loopStartPage] / [circularIndex]），起点落在循环中段，两个方向都有余量。
 *   没有采用"首尾各复制一页"的方案 —— 那会让首屏多解一张图，且滑到边界时位置会跳。
 * - `BackHandler` 先关查看器；查看器关闭后这个 composable 被移除，返回键才交还给相册页
 *   （由 `AppRoot` 的 BackHandler 处理）。
 *
 * **不做的**
 * - 不做双击/捏合缩放：查看器只负责"看清这张"，缩放是另一个交互问题，加了还要处理与
 *   左右滑动手势的冲突（HorizontalPager 会抢横向拖动）。
 * - 不做上一张/下一张按钮：屏幕小，底部只留收藏和关闭；滑动已经表达了同一件事。
 * - 不显示 EXIF/尺寸/曝光假数据：`PhotoStore.list()` 从文件重建的记录 `width/height` 是 0，
 *   曝光参数从未持久化。界面上出现的每个数字都必须有真实来源，所以这里只显示
 *   卷名、`序号 / 总数`、拍摄时间 —— 三项都有数据。
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
    val spec = LocalFilmTheme.current
    val p = spec.palette

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
            // 查看器铺满 AppRoot 交给相册页的安全区（状态栏/手势条由 AppRoot 统一避让），
            // 用比照片墙更深的底色，让照片"浮"起来。
            .background(p.windowBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ViewerHeader(item = current, index = currentIndex, total = total)

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

            ViewerBottomBar(
                isFavorite = current.id in favoriteIds,
                onToggleFavorite = { onToggleFavorite(current) },
                onClose = onClose,
            )
        }
    }
}

/** 顶部：卷名 + 拍摄时间（左），`序号 / 总数`（右）。数字全部来自真实列表。 */
@Composable
private fun ViewerHeader(item: PhotoWallItem, index: Int, total: Int) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = spec.metrics.gutterWide,
                end = spec.metrics.gutterWide,
                top = 10.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.stockLabel,
                style = TextStyle(
                    fontSize = spec.type.optionTitle,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.02.em,
                    color = p.textPrimary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${formatDay(item.capturedAt)}  ${formatClock(item.capturedAt)}",
                style = GalleryMonoCaption.copy(color = p.textTertiary),
            )
        }
        Text(
            text = "${index + 1} / $total",
            style = GalleryMonoCaption.copy(color = p.accent),
        )
    }
}

/**
 * 单页大图。`produceState` 负责"页面被回收时取消解码"——快速滑动不会留下一串
 * 后台解码任务把 CPU/内存吃满。
 */
@Composable
private fun ViewerPage(item: PhotoWallItem, reqWidth: Int, reqHeight: Int) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
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
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (val imageState = state) {
            ViewerImageState.Loading -> Text(
                text = "正在读取底片…",
                style = GalleryMonoCaption.copy(color = p.textTertiary),
            )

            ViewerImageState.Missing -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "底片文件读不到",
                    style = TextStyle(fontSize = spec.type.body, color = p.textSecondary),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "可能已经在系统相册里被删掉",
                    style = GalleryMonoCaption.copy(color = p.textTertiary),
                )
            }

            is ViewerImageState.Ready -> Image(
                bitmap = imageState.bitmap,
                // 照片本身是装饰信息：卷名/序号/时间已经在顶部用文字讲清楚了，
                // 再给图片一段重复的 contentDescription 只会让读屏更啰嗦。
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 底部：收藏切换（左）+ 关闭（右）。 */
@Composable
private fun ViewerBottomBar(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = spec.metrics.gutterWide,
                end = spec.metrics.gutterWide,
                top = 8.dp,
                bottom = 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ViewerAction(
            label = if (isFavorite) "已收藏" else "收藏",
            active = isFavorite,
            onClick = onToggleFavorite,
        ) {
            StarGlyph(
                filled = isFavorite,
                tint = if (isFavorite) p.accent else p.textSecondary,
                size = 14.dp,
            )
        }
        ViewerAction(label = "关闭", active = false, onClick = onClose) {
            FilmIcon(kind = FilmIconKind.Close, tint = p.textSecondary, iconSize = 13.dp)
        }
    }
}

/** 48dp 热区的胶囊按钮；选中态用香槟金描边 + 淡金底（和筛选 chip 同一套语义）。 */
@Composable
private fun ViewerAction(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = CircleShape
    Row(
        modifier = Modifier
            .height(spec.metrics.touchTarget)
            .clip(shape)
            .background(if (active) p.accent.copy(alpha = 0.12f) else p.controlFill)
            .border(
                spec.metrics.hairline,
                if (active) p.accent.copy(alpha = 0.45f) else p.hairline,
                shape,
            )
            .filmClickable(
                onClick = onClick,
                onClickLabel = if (active) "取消收藏" else "收藏这张",
                overlayAlpha = 0.06f,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = label,
            style = TextStyle(
                fontSize = spec.type.body,
                color = if (active) p.accent else p.textSecondary,
            ),
        )
    }
}

/**
 * 五角星（自绘，不引 material-icons-extended）。
 *
 * 填充态在照片墙格子上要压得住亮画面：先画一层半透明黑阴影再画金，否则浅色照片上
 * 金星星几乎消失。空心态只用于"没有收藏"的空态插画。
 */
@Composable
internal fun StarGlyph(
    filled: Boolean,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val radius = this.size.minDimension * 0.46f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        if (filled) {
            val shadowOffset = 0.5.dp.toPx()
            drawPath(
                path = starPath(center + Offset(shadowOffset, shadowOffset), radius),
                color = Color.Black.copy(alpha = 0.45f),
            )
            drawPath(path = starPath(center, radius), color = tint)
        } else {
            drawPath(
                path = starPath(center, radius * 0.94f),
                color = tint,
                style = Stroke(width = 1.2.dp.toPx()),
            )
        }
    }
}

/** 10 个顶点交替外/内半径，从正上方（-90°）起笔。 */
private fun starPath(center: Offset, radius: Float): Path {
    val innerRadius = radius * 0.42f
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else innerRadius
        val angle = (-PI / 2.0 + i * PI / 5.0).toFloat()
        val x = center.x + cos(angle) * r
        val y = center.y + sin(angle) * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** 查看器自己的等宽小字：和照片墙的 `MonoCaption` 同规格，避免两个页面标签长得不一样。 */
private val GalleryMonoCaption = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = FilmDarkSpec.type.label,
    letterSpacing = 0.14.em,
)

private sealed interface ViewerImageState {
    data object Loading : ViewerImageState
    data class Ready(val bitmap: ImageBitmap) : ViewerImageState
    data object Missing : ViewerImageState
}

// ---------------------------------------------------------------- preview

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFF050506)
@Composable
private fun PhotoViewerPreview() {
    FilmTheme {
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
