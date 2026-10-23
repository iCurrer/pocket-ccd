package com.pocketccd.prototype.ui.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pocketccd.prototype.camera.FavoritesStore
import com.pocketccd.prototype.ui.film.FilmDarkSpec
import com.pocketccd.prototype.ui.film.FilmIcon
import com.pocketccd.prototype.ui.film.FilmIconKind
import com.pocketccd.prototype.ui.film.FilmRoundButton
import com.pocketccd.prototype.ui.film.FilmTheme
import com.pocketccd.prototype.ui.film.LocalFilmTheme
import com.pocketccd.prototype.ui.film.filmClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 胶片相册（T9-015 / T9-019 / D-043 / D-045）。
 *
 * 视觉照 `docs/reference/pages/gallery.html` 的列表重排：居中标题栏、顶部筛选行、
 * 按卷分组、三列缩略图、每格右下角时间戳，收藏的格子右上角一颗金 ★。
 *
 * **分组口径**：按胶卷名分组，组内按拍摄时间倒序。这是能对得上数据的唯一分法 ——
 * 历史照片没有持久化「第几卷」的边界，按时间间隔猜一个新卷属于编造。
 * 因此同一款卷换两次卷会合成一组，界面上写「按胶卷分组」把这件事讲明白。
 *
 * **筛选**：`全部` / `★ 收藏` / 每个真实出现过的胶卷名（动态生成，没有照片的卷不出现）。
 * 筛选与分组联动 —— 筛"收藏"时组内一张不剩的卷整组隐藏（逻辑在
 * `GalleryLogic.applyGalleryFilter` / `toRollGroups`，有 JVM 单测）。
 *
 * **点开大图**：格子可点，进入 `PhotoViewer`。查看器拿的是「筛选后的扁平列表」的拷贝
 * 和当前索引 —— 查看器开着的时候用户切收藏，背后的列表会变，但查看器里左右滑的顺序
 * 不能跟着跳，否则 `HorizontalPager` 的当前位置会莫名其妙换人。
 *
 * **缩略图**：`produceState` + `Dispatchers.IO`，按格子实际像素算 `inSampleSize`，
 * 并进 `GalleryBitmapCache`。原先 `remember { BitmapFactory.decodeFile }` 在主线程解
 * 1440px 缩略图，快速滚动必掉帧（review P1-4）。缩略图文件缺失时回退到全尺寸原图，
 * 但仍按格子尺寸采样 —— 12MP 原图在这里也只是几百 KB 的解码结果。
 *
 * **不画的**：原型顶部那张「KODAK GOLD 200 · 冲扫中 72% · 已扫描 18/36」卡片 ——
 * 本项目没有外部冲扫服务（D-042），进度数字没有真实来源。底部 `SCANNED BY 光屋 LAB`
 * 水印同理（不是本应用产出的）。
 */
@Composable
fun PhotoWallScreen(
    onBack: () -> Unit,
    photos: List<PhotoWallItem> = emptyList(),
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val context = LocalContext.current

    // 收藏是本地状态：从 SharedPreferences 读一次，之后由 viewer 的回调维护。
    // 读取失败时 FavoritesStore 自己降级成空集合（预览环境没有真实 prefs）。
    val favoritesStore = remember(context) { FavoritesStore(context) }
    var favoriteIds by remember(favoritesStore) { mutableStateOf(favoritesStore.ids()) }

    var filter by remember { mutableStateOf<GalleryFilter>(GalleryFilter.All) }
    var viewerRequest by remember { mutableStateOf<ViewerRequest?>(null) }

    // 胶卷筛选 chip 只由真实照片生成；照片库被外部删空后，之前选中的某个卷会失效，
    // 此时退回「全部」而不是显示一个永远空白的页面。
    val filterOptions = remember(photos) { galleryFilters(photos) }
    val activeFilter = filter.takeIf { it in filterOptions } ?: GalleryFilter.All

    val visiblePhotos = remember(photos, activeFilter, favoriteIds) {
        applyGalleryFilter(photos, activeFilter, favoriteIds)
    }
    val groups = remember(visiblePhotos) { toRollGroups(visiblePhotos) }

    Box(modifier = Modifier.fillMaxSize()) {
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

            if (photos.isNotEmpty()) {
                FilterRow(
                    options = filterOptions,
                    selected = activeFilter,
                    totalCount = photos.size,
                    favoriteCount = favoriteCount(photos, favoriteIds),
                    onSelect = { filter = it },
                )
            }

            when {
                photos.isEmpty() -> EmptyArchive(modifier = Modifier.weight(1f))

                // 只有「收藏」筛选能走到这里：胶卷 chip 都由现存照片生成，
                // 筛完至少剩一张。空态文案因此可以专门讲收藏。
                visiblePhotos.isEmpty() -> EmptyFavorites(modifier = Modifier.weight(1f))

                else -> BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    // 每格解码目标 = 网格实际格宽（px）。3 列、格间距 2 个 TILE_GAP。
                    val cellWidthPx = with(LocalDensity.current) {
                        ((maxWidth - TILE_GAP * 2) / 3).toPx().toInt()
                    }.coerceAtLeast(1)

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
                        verticalArrangement = Arrangement.spacedBy(TILE_GAP),
                    ) {
                        groups.forEach { group ->
                            item(
                                key = "header-${group.label}",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                GroupHeader(group = group)
                            }
                            items(items = group.photos, key = { it.id }) { item ->
                                PhotoTile(
                                    item = item,
                                    isFavorite = item.id in favoriteIds,
                                    targetPx = cellWidthPx,
                                    onClick = {
                                        // 索引按「筛选后的展示顺序」算，和查看器吃的列表同源。
                                        val index = visiblePhotos.indexOfFirst { it.id == item.id }
                                        if (index >= 0) {
                                            viewerRequest = ViewerRequest(visiblePhotos, index)
                                        }
                                    },
                                )
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

        // 查看器盖在照片墙之上；关闭后 disposes，返回键重新交给 AppRoot 的 BackHandler。
        viewerRequest?.let { request ->
            PhotoViewer(
                photos = request.photos,
                initialIndex = request.index,
                favoriteIds = favoriteIds,
                onToggleFavorite = { item -> favoriteIds = favoritesStore.toggle(item.id) },
                onClose = { viewerRequest = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun WallTopBar(total: Int, onBack: () -> Unit) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
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
                    fontSize = spec.type.optionTitle,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.04.em,
                    color = p.textPrimary,
                ),
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "ARCHIVE · 2026",
                style = MonoCaption.copy(color = p.textTertiary),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        // 数量固定占 48dp，和左侧关闭按钮等宽，标题因此是真的居中。
        Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
            Text(
                text = total.toString().padStart(2, '0'),
                style = MonoCaption.copy(color = p.textTertiary),
            )
        }
    }
}

/**
 * 顶部筛选行。横向可滚，避免卷多时把「收藏」挤出屏幕。
 *
 * 数量角标只画在「全部」和「★ 收藏」上，且两个数字都来自真实数据：
 * 全部 = 相册总张数，收藏 = 当前相册里被收藏的张数（历史遗留 id 不计入）。
 * 胶卷 chip 不画数量 —— 组头已经写了「N 张」，同一屏重复两次没有信息增量。
 */
@Composable
private fun FilterRow(
    options: List<GalleryFilter>,
    selected: GalleryFilter,
    totalCount: Int,
    favoriteCount: Int,
    onSelect: (GalleryFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val label = when (option) {
                GalleryFilter.All -> "全部"
                GalleryFilter.Favorites -> "★ 收藏"
                is GalleryFilter.Stock -> option.label
            }
            val count = when (option) {
                GalleryFilter.All -> totalCount
                GalleryFilter.Favorites -> favoriteCount
                is GalleryFilter.Stock -> null
            }
            FilterChip(
                label = label,
                count = count,
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

/**
 * 筛选 chip：48dp 热区 + 34dp 视觉胶囊。
 *
 * 选中态用香槟金实底 + 深色字（不是金色的文字）：一圈金色描边在近黑底上太弱，
 * 而金色实底和取景页的 active 圆钮是同一个视觉语言。
 */
@Composable
private fun FilterChip(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = CircleShape
    // 选中态用 160ms 颜色过渡，而不是瞬间换底色：chip 是"档位"，档位切换应该有行程感。
    val labelColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) p.accent else p.textSecondary,
        animationSpec = androidx.compose.animation.core.tween(160),
        label = "chip-label",
    )
    val chipBg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) p.accent.copy(alpha = 0.14f) else p.controlFill,
        animationSpec = androidx.compose.animation.core.tween(160),
        label = "chip-bg",
    )

    Box(
        modifier = Modifier
            .height(spec.metrics.touchTarget)
            .clip(shape)
            .filmClickable(
                onClick = onClick,
                onClickLabel = "筛选 $label",
                overlayAlpha = 0.06f,
            )
            .padding(vertical = (spec.metrics.touchTarget - CHIP_HEIGHT) * 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(CHIP_HEIGHT)
                .clip(shape)
                .background(chipBg)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // chip 文案用无衬线：等宽字体的中文回退到系统字体后，0.14em 的字距会把
            // 「全部」拉得很散；数字角标才保留等宽。
            Text(
                text = label,
                style = TextStyle(
                    fontSize = spec.type.label,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    letterSpacing = 0.04.em,
                    color = labelColor,
                ),
            )
            if (count != null) {
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = count.toString(),
                    style = MonoCaption.copy(
                        color = if (selected) p.accent.copy(alpha = 0.70f) else p.textTertiary,
                    ),
                )
            }
        }
    }
}

/** 组头：卷名 + 张数 + 日期区间，上沿一条细线把组隔开。 */
@Composable
private fun GroupHeader(group: PhotoGroup) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
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
                        fontSize = spec.type.optionTitle,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.02.em,
                        color = p.textPrimary,
                    ),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = group.rangeLabel(),
                    style = MonoCaption.copy(color = p.textTertiary),
                )
            }
            Text(
                text = "${group.photos.size} 张",
                style = MonoCaption.copy(color = p.textTertiary),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

/**
 * 一格缩略图。
 *
 * 解码目标 [targetPx] 由调用方按格宽算好；`produceState` 在 IO 线程解，页面上只做
 * `Bitmap → ImageBitmap` 转换。缓存命中时首帧直接有图，不会闪一下占位渐变色。
 *
 * 缩略图文件可能不存在（历史照片的缩略图被系统清理，见 `PhotoStore.list` 的注释），
 * 这时退回 `fullImagePath` —— 但**仍然按格子尺寸采样解码**，不是把 12MP 整张读进堆
 * （采样后约 360×270，几百 KB）。宁可多花一次大文件 IO 也要把照片显示出来，
 * 总比让用户以为照片丢了强。
 */
@Composable
private fun PhotoTile(
    item: PhotoWallItem,
    isFavorite: Boolean,
    targetPx: Int,
    onClick: () -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = RoundedCornerShape(8.dp)
    val cacheKey = item.imagePath ?: item.fullImagePath
    val fallbackPath = item.fullImagePath

    val bitmap by produceState<Bitmap?>(
        initialValue = cacheKey?.let(GalleryBitmapCache::get),
        cacheKey,
        fallbackPath,
        targetPx,
    ) {
        if (cacheKey == null) {
            value = null
            return@produceState
        }
        val cached = GalleryBitmapCache.get(cacheKey)
        if (cached != null) {
            value = cached
            return@produceState
        }
        val decoded = withContext(Dispatchers.IO) {
            decodeSampledBitmap(cacheKey, targetPx, targetPx)
                ?: fallbackPath
                    ?.takeIf { it != cacheKey }
                    ?.let { decodeSampledBitmap(it, targetPx, targetPx) }
        }
        if (decoded != null) GalleryBitmapCache.put(cacheKey, decoded)
        value = decoded
    }

    // 委派属性（by produceState）不能直接 smart cast，先取一次值。
    val thumbnail = bitmap
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(p.windowBg)
            .border(spec.metrics.hairline, p.hairline, shape)
            .filmClickable(
                onClick = onClick,
                onClickLabel = "查看大图",
                scaleTo = 0.97f,
                overlayAlpha = 0.06f,
                haptic = false,
            )
            .semantics {
                contentDescription = "${item.stockLabel} 拍摄于 ${formatClock(item.capturedAt)}"
            },
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // 缩略图和原图都读不到（文件被外部删了 / 预览条目），或还在解码。
            // 画一片更暗的底，不画任何"像照片"的渐变，避免把占位当成真片子。
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
        if (isFavorite) {
            StarGlyph(
                filled = true,
                tint = p.accent,
                size = 13.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp),
            )
        }
    }
}

@Composable
private fun EmptyArchive(modifier: Modifier = Modifier) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    EmptyCard(
        title = "还没有底片",
        hint = "按下快门，第一卷从这里开始",
        modifier = modifier,
    ) {
        FilmIcon(
            kind = FilmIconKind.Filmstrip,
            tint = p.inkMuted,
            iconSize = 20.dp,
        )
    }
}

/**
 * 收藏筛选为空时的空态。和「还没有底片」共用卡片骨架，但文案必须指向**真实存在的
 * 操作**：收藏入口在查看器底部（那颗 ★），不是原型里的"右上角心形"。
 */
@Composable
private fun EmptyFavorites(modifier: Modifier = Modifier) {
    val p = LocalFilmTheme.current.palette
    EmptyCard(
        title = "还没有收藏的底片",
        hint = "打开任意一张底片，点底部的「★ 收藏」",
        modifier = modifier,
    ) {
        StarGlyph(filled = false, tint = p.inkMuted, size = 20.dp)
    }
}

@Composable
private fun EmptyCard(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
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
                icon()
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = TextStyle(
                    fontSize = spec.type.optionTitle,
                    fontWeight = FontWeight.Medium,
                    color = p.textPrimary,
                ),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = hint,
                style = MonoCaption.copy(color = p.textTertiary),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------- data

/**
 * 查看器的一次打开请求：**冻结**筛选后的列表 + 当前索引。
 *
 * 为什么不直接把 `visiblePhotos` 传给查看器：查看器开着时用户可能切换收藏，
 * 在「★ 收藏」筛选下当前这张会立刻从列表里消失，索引跟着变 —— 正在看的照片会
 * 突然换成下一张。列表按打开那一刻冻结，关闭后照片墙再按最新筛选重排。
 */
private data class ViewerRequest(
    val photos: List<PhotoWallItem>,
    val index: Int,
)

private val MonoCaption = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = FilmDarkSpec.type.label,
    letterSpacing = 0.14.em,
)

/** 网格格间距。解码目标宽度计算与 LazyVerticalGrid 的实际间距必须用同一个值。 */
private val TILE_GAP = 6.dp

/** 筛选 chip 的视觉高度；热区仍是 48dp（`FilmMetrics.touchTarget`）。 */
private val CHIP_HEIGHT = 34.dp

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
