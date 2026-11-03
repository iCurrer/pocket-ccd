package com.pocketccd.prototype.ui.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.ui.glass.GlassButton
import com.pocketccd.prototype.ui.glass.GlassChip
import com.pocketccd.prototype.ui.glass.GlassIcon
import com.pocketccd.prototype.ui.glass.GlassIconButton
import com.pocketccd.prototype.ui.glass.GlassIconKind
import com.pocketccd.prototype.ui.theme.GlassShape
import com.pocketccd.prototype.ui.theme.GlassSpace
import com.pocketccd.prototype.ui.theme.GlassType
import com.pocketccd.prototype.ui.theme.glass

/**
 * 相册页：玻璃网格 + 筛选芯片。
 *
 * 与旧版（`PhotoWallScreen` 的相纸接触印相 + 分组标题）的区别：
 * - 不再画"卷分组标题"——卷模型已移除（D-070），分组只剩筛选意义；
 * - 网格用等宽圆角格 + 4dp 间距，页签只做筛选不做计数；
 * - 收藏角标贴在右上，和查看器里的收藏按钮共用同一个 id 集合。
 */
@Composable
fun GalleryScreen(
    photos: List<PhotoWallItem>,
    favoriteIds: Set<String>,
    modifier: Modifier = Modifier,
    onToggleFavorite: (PhotoWallItem) -> Unit = {},
    onOpenDetail: (PhotoWallItem) -> Unit = {},
    onOpenCamera: () -> Unit = {},
    onRefresh: () -> Unit = {},
    /**
     * 点开一张：把请求交给外层（`AppRoot`），由它决定查看器的层级（D-077）。
     * 页面自己不再持有查看器状态。
     */
    onOpenViewer: (ViewerRequest) -> Unit = {},
) {
    val p = MaterialTheme.glass
    var filter by remember { mutableStateOf<GalleryFilter>(GalleryFilter.All) }

    val options = remember(photos) { galleryFilters(photos) }
    // 当前筛选可能因为照片被删而失效（例如"收藏"筛出来为空，或者某支卷一张不剩）。
    val active = options.firstOrNull { it == filter } ?: GalleryFilter.All
    val visible = remember(photos, active, favoriteIds) {
        applyGalleryFilter(photos, active, favoriteIds)
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = GlassSpace.page, end = GlassSpace.page, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = "相册", style = GlassType.title, color = p.onGlass)
                Text(
                    text = if (photos.isEmpty()) {
                        "还没有照片"
                    } else {
                        "${photos.size} 张 · 收藏 ${photos.count { it.id in favoriteIds }} 张"
                    },
                    style = GlassType.caption,
                    color = p.onGlassMuted,
                )
            }
            GlassIconButton(
                label = "刷新相册",
                onClick = onRefresh,
                icon = { tint -> GlassIcon(GlassIconKind.Refresh, tint) },
            )
        }

        if (photos.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = GlassSpace.page),
                horizontalArrangement = Arrangement.spacedBy(GlassSpace.tight),
                modifier = Modifier.padding(bottom = GlassSpace.gap),
            ) {
                items(options.size) { index ->
                    val option = options[index]
                    GlassChip(
                        text = option.label(),
                        selected = option == active,
                        onClick = { filter = option },
                    )
                }
            }
        }

        when {
            photos.isEmpty() -> EmptyGallery(onOpenCamera)
            visible.isEmpty() -> EmptyFavorites(onShowAll = { filter = GalleryFilter.All })
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = GlassSpace.page,
                    end = GlassSpace.page,
                    // 浮层 TabBar 不占布局，必须自己让出这一截，否则最后一排被压在玻璃底下。
                    bottom = GlassSpace.dockReserve,
                ),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    items = visible,
                    key = { _, item -> item.id },
                ) { index, item ->
                    Thumb(
                        item = item,
                        favorite = item.id in favoriteIds,
                        onClick = { onOpenViewer(ViewerRequest(visible, index)) },
                    )
                }
            }
        }
    }
}

/**
 * 打开查看器的请求：**筛选后的可见列表** + 点中的下标。
 *
 * 由外层（`AppRoot`）持有而不是 GalleryScreen 自己持有：查看器是**模态**，
 * 必须画在 TabBar 之上、并且让 TabBar 让位（D-077）。状态留在页面内部的话，
 * 外层就不知道"现在该不该画 TabBar"，于是 TabBar 压在查看器上 —— 这正是用户报的
 * "相册点开大图后导航栏和内容重叠"。
 */
data class ViewerRequest(val photos: List<PhotoWallItem>, val index: Int)

/** 筛选芯片上的文字。卷名直接用胶卷名，不加"卷"字后缀 —— 页签本身就是筛选语义。 */
private fun GalleryFilter.label(): String = when (this) {
    GalleryFilter.All -> "全部"
    GalleryFilter.Favorites -> "收藏"
    is GalleryFilter.Stock -> label
}

@Composable
private fun Thumb(
    item: PhotoWallItem,
    favorite: Boolean,
    onClick: () -> Unit,
) {
    val p = MaterialTheme.glass
    val bitmap = rememberThumbnail(item.imagePath)

    Box(
        Modifier
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(14.dp))
            .background(p.fill)
            .clickable(
                role = Role.Button,
                onClickLabel = "查看这张照片",
                onClick = onClick,
            )
            .semantics { contentDescription = "${item.stockLabel} 拍摄的照片" },
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassIcon(GlassIconKind.Gallery, p.onGlassMuted, size = 20.dp)
            }
        }
        if (favorite) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                GlassIcon(GlassIconKind.Heart, p.accent, size = 11.dp, filled = true)
            }
        }
    }
}

/**
 * 网格缩略图解码：IO 线程 + 采样 + 内存 LRU 缓存。
 *
 * 缓存上限由 [GalleryBitmapCache] 控制在堆的 1/8（8–32MB），滚回去不重解；
 * 采样率按格子实际像素算 —— 12MP 原图直解是 48MB，一格就够把堆打满。
 */
@Composable
internal fun rememberThumbnail(path: String?): ImageBitmap? {
    val density = LocalDensity.current
    val px = with(density) { 120.dp.roundToPx() }
    val state = androidx.compose.runtime.produceState<ImageBitmap?>(
        initialValue = null,
        key1 = path,
        key2 = px,
    ) {
        if (path == null) {
            value = null
            return@produceState
        }
        GalleryBitmapCache.get(path)?.let {
            value = it.asImageBitmapOrNull()
            return@produceState
        }
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val bitmap = decodeSampledBitmap(path, px, px) ?: return@withContext null
            GalleryBitmapCache.put(path, bitmap)
            bitmap.asImageBitmapOrNull()
        }
    }
    return state.value
}

/** `Bitmap` → `ImageBitmap`。已经回收的位图会抛异常，这里降级成"没解出来"。 */
private fun android.graphics.Bitmap.asImageBitmapOrNull(): ImageBitmap? =
    runCatching { asImageBitmap() }.getOrNull()

@Composable
private fun EmptyGallery(onOpenCamera: () -> Unit) {
    val p = MaterialTheme.glass
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = GlassSpace.page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlassIcon(GlassIconKind.Gallery, p.onGlassMuted, size = 34.dp)
        Spacer(Modifier.height(GlassSpace.gap))
        Text(text = "相册里还没有照片", style = GlassType.cardTitle, color = p.onGlass)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "回到取景页按快门，第一张就会落在这里。",
            style = GlassType.caption,
            color = p.onGlassMuted,
        )
        Spacer(Modifier.height(GlassSpace.inner))
        GlassButton(text = "去拍摄", onClick = onOpenCamera)
    }
}

@Composable
private fun EmptyFavorites(onShowAll: () -> Unit) {
    val p = MaterialTheme.glass
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = GlassSpace.page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlassIcon(GlassIconKind.Heart, p.onGlassMuted, size = 30.dp)
        Spacer(Modifier.height(GlassSpace.gap))
        Text(text = "还没有收藏", style = GlassType.cardTitle, color = p.onGlass)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "在照片详情里点收藏，它就会出现在这里。",
            style = GlassType.caption,
            color = p.onGlassMuted,
        )
        Spacer(Modifier.height(GlassSpace.inner))
        GlassButton(text = "看全部", primary = false, onClick = onShowAll)
    }
}
