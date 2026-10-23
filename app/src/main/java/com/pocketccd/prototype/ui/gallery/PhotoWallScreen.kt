package com.pocketccd.prototype.ui.gallery

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pocketccd.prototype.camera.FavoritesStore
import com.pocketccd.prototype.ui.film.FilmIcon
import com.pocketccd.prototype.ui.film.FilmIconKind
import com.pocketccd.prototype.ui.film.FilmPaperSpec
import com.pocketccd.prototype.ui.film.FilmTheme
import com.pocketccd.prototype.ui.film.FilmThemeSpec
import com.pocketccd.prototype.ui.film.LocalFilmTheme
import com.pocketccd.prototype.ui.film.PaperEmptyState
import com.pocketccd.prototype.ui.film.PaperHairline
import com.pocketccd.prototype.ui.film.PaperIndexTab
import com.pocketccd.prototype.ui.film.filmClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 照片墙（D-063「白纸档案」1:1 版面 / B 方案 §4）。
 *
 * **版式**：纸面整页。页头是英文 `Photo Wall`（Serif）+ 右侧档案图标；下面是索引页签
 * （`全部` / `★ 收藏` / 每个真实卷名）；再下面是 3 列 1:1 接触印相网格。
 * 底部由 AppRoot 的 `PaperNavBar` 覆盖，所以页面内容统一留 88dp（见 [BOTTOM_CONTENT_INSET]）。
 *
 * **导航**：页头**没有**返回箭头 —— 纸面页之间由底部 TabBar 切换。系统返回由本页的
 * `BackHandler` 接住并转给 [onBack]（→ `NavigateBack`），因此 [onBack] 参数保留；
 * 本页另外把它用在空态的「去取景」动作上。
 *
 * **筛选**：`全部` / `★ 收藏` / 每个真实出现过的胶卷名（动态生成，没有照片的卷不出现）。
 * 筛选与分组仍由 `GalleryLogic` 决定（`galleryFilters` / `applyGalleryFilter` /
 * `toRollGroups`），这里只画页签和网格，不重新实现任何一条判断。
 *
 * **帧号**：照片没有持久化的「卷内第几张」，所以帧号是**组内曝光顺序**：
 * 组内最早的一张是 `01`，最新一张等于组内张数。查看器里的帧号用同一口径
 * （`archiveFrameNumber`），两边不会打架。
 *
 * **点开大图**：格子可点，进入 `PhotoViewer`（v0.5.0 的黑底灯箱，本文件未改）。
 * 查看器拿的是「筛选后的扁平列表」的拷贝和当前索引 —— 查看器开着的时候用户切收藏，
 * 背后的列表会变，但查看器里左右滑的顺序不能跟着跳。
 *
 * **缩略图**：`produceState` + `Dispatchers.IO`，按格子实际像素算 `inSampleSize`，
 * 并进 `GalleryBitmapCache`。主线程只做 `Bitmap → ImageBitmap` 的包装。
 * 缩略图文件缺失时回退到全尺寸原图，但仍按格子尺寸采样。
 *
 * **不画的**：金色星、白色卡片底、阴影/渐变；原型顶部的「冲扫 72% / 已扫描 18/36」
 * 假进度（本项目没有外部冲扫服务，D-042）。
 */
@Composable
fun PhotoWallScreen(
    onBack: () -> Unit,
    photos: List<PhotoWallItem> = emptyList(),
    /** 查看器是黑底灯箱，打开时系统栏图标要换成浅色；由 AppRoot 统一改。 */
    onViewerOpenChange: (Boolean) -> Unit = {},
) {
    val p = LocalFilmTheme.current.palette
    val context = LocalContext.current

    // 收藏是本地状态：从 SharedPreferences 读一次，之后由 viewer 的回调维护。
    // 读取失败时 FavoritesStore 自己降级成空集合（预览环境没有真实 prefs）。
    val favoritesStore = remember(context) { FavoritesStore(context) }
    var favoriteIds by remember(favoritesStore) { mutableStateOf(favoritesStore.ids()) }

    var filter by remember { mutableStateOf<GalleryFilter>(GalleryFilter.All) }
    var viewerRequest by remember { mutableStateOf<ViewerRequest?>(null) }

    // 系统返回走本页：纸面页之间是 TabBar 平级切换，返回交给 route 级导航（onBack）。
    // 查看器打开时 PhotoViewer 自己的 BackHandler 更靠内，会先关查看器再轮到这一条。
    BackHandler { onBack() }

    LaunchedEffect(viewerRequest != null) {
        onViewerOpenChange(viewerRequest != null)
    }
    DisposableEffect(Unit) {
        onDispose { onViewerOpenChange(false) }
    }

    // 页签只由真实照片生成；照片库被外部删空后，之前选中的某个卷会失效，
    // 此时退回「全部」而不是显示一个永远空白的页面。
    val filterOptions = remember(photos) { galleryFilters(photos) }
    val activeFilter = filter.takeIf { it in filterOptions } ?: GalleryFilter.All

    val visiblePhotos = remember(photos, activeFilter, favoriteIds) {
        applyGalleryFilter(photos, activeFilter, favoriteIds)
    }

    // 帧号按「真实卷分组」算，和查看器的 archiveFrameNumber 同一口径：
    // 组内按拍摄时间倒序，最旧的一张 = 01。
    val frameNumbers = remember(visiblePhotos) {
        buildMap {
            toRollGroups(visiblePhotos).forEach { group ->
                group.photos.forEachIndexed { index, photo ->
                    put(photo.id, group.photos.size - index)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(p.appBg)
                // 页面内容让开系统栏；查看器是 Box 的独立子层，因此能真正铺满整屏
                // （AppRoot 对 GALLERY 不再统一吃 safeDrawing）。
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            PhotoWallHeader()

            if (photos.isNotEmpty()) {
                FilterTabs(
                    options = filterOptions,
                    selected = activeFilter,
                    onSelect = { filter = it },
                )
            }

            when {
                photos.isEmpty() -> PaperEmptyState(
                    title = "相册里还没有照片",
                    body = "按下快门，第一卷会从这里开始。",
                    action = "去取景",
                    onAction = onBack,
                    // 底部 TabBar 是覆盖层，空态也要让开 88dp，否则按钮会被压住。
                    modifier = Modifier.weight(1f).padding(bottom = BOTTOM_CONTENT_INSET),
                )

                // 只有「收藏」筛选能走到这里：卷页签都由现存照片生成，筛完至少剩一张。
                // 空态文案因此专门讲收藏，并给一个真实出路（退回全部）。
                visiblePhotos.isEmpty() -> PaperEmptyState(
                    title = "还没有收藏的底片",
                    body = "打开任意一张底片，点底部的「收藏」，它就会出现在这里。",
                    action = "看全部底片",
                    onAction = { filter = GalleryFilter.All },
                    modifier = Modifier.weight(1f).padding(bottom = BOTTOM_CONTENT_INSET),
                )

                else -> ContactSheet(
                    photos = visiblePhotos,
                    frameNumbers = frameNumbers,
                    favoriteIds = favoriteIds,
                    onOpenPhoto = { index -> viewerRequest = ViewerRequest(visiblePhotos, index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 查看器盖在照片墙之上；关闭后 disposes，返回键重新交给本页的 BackHandler。
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

/**
 * 1:1 页头：`Photo Wall`（Serif）+ 右侧档案图标。
 *
 * 不画返回箭头：纸面页之间由 TabBar 切换。
 *
 * 标题设计值是 26sp；当前 `FilmType` 没有 26sp 令牌，而 `NoBareFontSizeTest` 禁止裸字号，
 * 所以取最接近的 `sheetTitle`（24sp）。要精确 26sp 需要在 FilmTheme.kt 新增页面标题令牌。
 *
 * 右侧档案图标只占 48dp 槽位、不接点击：相册本身就是这一页，做成按钮会是一个
 * 「点了没反应」的假控件（本轮没有第二个入口可接）。
 */
@Composable
private fun PhotoWallHeader() {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = spec.metrics.pageMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Photo Wall",
                color = p.textPrimary,
                fontFamily = spec.type.titleFamily,
                fontSize = spec.type.sheetTitle,
                lineHeight = spec.type.sheetTitleLineHeight,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                FilmIcon(
                    kind = FilmIconKind.ImageMultiple,
                    tint = p.textPrimary,
                    iconSize = 18.dp,
                    strokeWidth = 1.5.dp,
                )
            }
        }
        PaperHairline()
    }
}

/**
 * 索引页签行。横向可滚，避免卷多时把「收藏」挤出屏幕。
 *
 * 页签只负责筛选，不画数量角标：张数不是这一行要回答的问题，查看器的 `03 / 12`
 * 才是「当前这张在列表里的位置」。
 */
@Composable
private fun FilterTabs(
    options: List<GalleryFilter>,
    selected: GalleryFilter,
    onSelect: (GalleryFilter) -> Unit,
) {
    val spec = LocalFilmTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spec.metrics.pageMargin),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            PaperIndexTab(
                text = option.tabLabel(),
                selected = option == selected,
                onClick = { onSelect(option) },
                // 共享组件画 44dp 高，但页签热区必须 ≥48dp。外层给定 48dp 后，
                // 组件内部的 height(44) 会被外层固定约束拉满，点击区域因此是 48dp。
                // 选中态额外补一条 semantics：页签视觉上是下划线，读屏需要听到「已选择」。
                modifier = Modifier
                    .height(spec.metrics.touchTarget)
                    .semantics { this.selected = option == selected },
            )
        }
    }
}

private fun GalleryFilter.tabLabel(): String = when (this) {
    GalleryFilter.All -> "全部"
    GalleryFilter.Favorites -> "★ 收藏"
    is GalleryFilter.Stock -> label
}

/**
 * 1:1 接触印相网格：3 列、列距 8dp、行距 14dp、页边距 24dp，底部留 88dp 给 TabBar。
 *
 * 照片按 `applyGalleryFilter` 的输出顺序铺进网格（内部仍按真实卷分组），
 * 和查看器的左右滑顺序同源：格子上点开的是哪张、下一张是哪张，两边永远一致。
 */
@Composable
private fun ContactSheet(
    photos: List<PhotoWallItem>,
    frameNumbers: Map<String, Int>,
    favoriteIds: Set<String>,
    onOpenPhoto: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = LocalFilmTheme.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // 每格解码目标 = 网格实际格宽（px）：3 列、左右各一个内容边距、2 个列间距。
        val cellWidthPx = with(LocalDensity.current) {
            ((maxWidth - spec.metrics.pageMargin * 2f - TILE_GAP * 2f) / 3f).toPx().toInt()
        }.coerceAtLeast(1)

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = spec.metrics.pageMargin,
                end = spec.metrics.pageMargin,
                top = 8.dp,
                bottom = BOTTOM_CONTENT_INSET,
            ),
            horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
            verticalArrangement = Arrangement.spacedBy(ROW_GAP),
        ) {
            itemsIndexed(items = photos, key = { _, item -> item.id }) { index, item ->
                PhotoTile(
                    item = item,
                    frameNumber = frameNumbers[item.id] ?: (index + 1),
                    isFavorite = item.id in favoriteIds,
                    targetPx = cellWidthPx,
                    onClick = { onOpenPhoto(index) },
                )
            }
        }
    }
}

/**
 * 一格接触印相：1:1 图片 + 图下 mono 帧号 + 收藏角标。
 *
 * 无卡片底、无阴影、无渐变：图片本身只有 2dp 圆角 + 1px hairline 描边。
 * [targetPx] 由调用方按格宽算好；解码在 `produceState` 的 IO 线程上做，
 * 主线程只把 `Bitmap` 包成 `ImageBitmap`。
 *
 * 缩略图文件可能不存在（历史照片的缩略图被系统清理，见 `PhotoStore.list`），
 * 这时退回 `fullImagePath` —— 但**仍然按格子尺寸采样解码**，不会把 12MP 整张读进堆。
 */
@Composable
private fun PhotoTile(
    item: PhotoWallItem,
    frameNumber: Int,
    isFavorite: Boolean,
    targetPx: Int,
    onClick: () -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = RoundedCornerShape(spec.metrics.printRadius)
    val frameLabel = frameNumber.toString().padStart(2, '0')
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .filmClickable(
                onClick = onClick,
                onClickLabel = "查看大图",
                scaleTo = 0.97f,
                overlayAlpha = 0.05f,
                haptic = false,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(item.stockLabel)
                    append("，")
                    append(formatDay(item.capturedAt))
                    append(" ")
                    append(formatClock(item.capturedAt))
                    append("，第 ")
                    append(frameLabel)
                    append(" 帧")
                    if (isFavorite) append("，已收藏")
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(p.controlFill)
                .border(spec.metrics.hairline, p.hairline, shape),
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isFavorite) {
                // 石板蓝 6dp 方块 = 收藏角标（B 版面 §4.3）。不做金色星、不做红心。
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(6.dp)
                        .background(p.accent),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 帧号是图下的印刷记号；整格的 contentDescription 已经讲了卷名/时间/帧号，
                // 这里清掉重复的「05」，避免 TalkBack 连读两遍。
                .clearAndSetSemantics { },
        ) {
            // 设计值 10sp；字阶下限是 11sp 且禁止裸字号，取 monoSmall（11sp）。
            Text(
                text = frameLabel,
                style = paperMonoStyle(spec, p.textTertiary, size = spec.type.monoSmall),
            )
        }
    }
}

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

/**
 * 扁平列表里某一张照片的**组内帧号**（查看器 caption 用）。
 *
 * 同卷照片在 `applyGalleryFilter` 的结果里是连续的一段；组内按拍摄时间倒序，
 * 所以帧号从组内最旧的一张算 01，最新一张等于组内张数。照片没有持久化的
 * 「卷内第几张」，这里不假装有 —— 显示的是这组照片内部的曝光顺序。
 */
internal fun archiveFrameNumber(photos: List<PhotoWallItem>, index: Int): Int {
    if (index !in photos.indices) return 1
    val stock = photos[index].stockLabel
    var first = index
    while (first > 0 && photos[first - 1].stockLabel == stock) first--
    var last = index
    while (last < photos.lastIndex && photos[last + 1].stockLabel == stock) last++
    return last - index + 1
}

/**
 * 纸面页的等宽小字（帧号 / 日期 / 元数据）。
 *
 * 字族、字号、行高都取当前 spec，并打开等宽数字（`tnum`），避免帧号在滚动时抖动。
 */
internal fun paperMonoStyle(
    spec: FilmThemeSpec,
    color: Color,
    size: TextUnit = spec.type.monoSmall,
): TextStyle = TextStyle(
    fontFamily = spec.type.monoFamily,
    fontSize = size,
    lineHeight = spec.type.monoLineHeight,
    letterSpacing = 0.08.em,
    fontFeatureSettings = "tnum",
    color = color,
)

/** 网格列间距。解码目标宽度计算与 LazyVerticalGrid 的实际间距必须用同一个值。 */
private val TILE_GAP = 8.dp

/** 接触印相的行间距。 */
private val ROW_GAP = 14.dp

/**
 * 页面底部留白：56dp TabBar + 24dp 呼吸 + 余量。
 * AppRoot 的 `PaperNavBar` 是覆盖层，页面内容必须自己让开这一段（1:1 版面规格 §1）。
 */
private val BOTTOM_CONTENT_INSET = 88.dp

// ---------------------------------------------------------------- preview

private val PreviewPhotos = listOf(
    PhotoWallItem("p1", "GOLD 200", 1_760_700_000_000),
    PhotoWallItem("p2", "GOLD 200", 1_760_694_000_000),
    PhotoWallItem("p3", "GOLD 200", 1_760_688_000_000),
    PhotoWallItem("p4", "PORTRA 400", 1_760_200_000_000),
    PhotoWallItem("p5", "PORTRA 400", 1_760_194_000_000),
)

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFECEAE4)
@Composable
private fun PhotoWallPreview() {
    FilmTheme(FilmPaperSpec) {
        PhotoWallScreen(onBack = {}, photos = PreviewPhotos)
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFECEAE4)
@Composable
private fun EmptyPhotoWallPreview() {
    FilmTheme(FilmPaperSpec) {
        PhotoWallScreen(onBack = {}, photos = emptyList())
    }
}
