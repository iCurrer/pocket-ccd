package com.pocketccd.prototype.ui.gallery

import java.util.Calendar

/**
 * 胶片相册的**纯判定逻辑**：筛选、分组、查看器首尾循环、采样率计算、时间格式。
 *
 * 为什么单独一个文件、且不 import 任何 Compose / Android 类：
 * 这些规则决定"用户点开的是哪张照片""左右滑会不会跳出边界""12MP 原图会不会整张进堆"，
 * 是最需要 JVM 单测覆盖的部分（见 `GalleryFilterTest`）。放在 `PhotoWallScreen.kt` 里
 * 会让测试顺带加载 Compose 运行时；放在这里则一个 `java.util.Calendar` 就够。
 *
 * **不做的**：不在这里做任何 IO、不碰文件、不持有 SharedPreferences ——
 * 那属于 `camera/FavoritesStore.kt`（Android 依赖）与 `GalleryImages.kt`（解码）。
 */

/**
 * 相册条目。
 *
 * 只保留能对得上文件的字段：卷名、拍摄时间、缩略图路径、全尺寸路径。
 * 原型里的 `filter` / 占位渐变色没有数据来源，已删除。
 *
 * 注意 [fullImagePath] 必须带默认值：`AppRoot` 之外还有预览/测试调用点，
 * 加一个必填字段会把它们全部打断。
 */
data class PhotoWallItem(
    val id: String,
    val stockLabel: String,
    val capturedAt: Long,
    /** 缩略图的绝对路径。为空表示文件缺失或这是预览条目。 */
    val imagePath: String? = null,
    /**
     * 全尺寸原图的绝对路径，供全屏查看器使用。
     *
     * 为空时查看器回退到 [imagePath]（缩略图长边 1440，铺满 1080p 屏幕够用，只是放大后软）。
     * 之所以不拿 `imagePath` 当唯一大图来源：照片墙刻意只读缩略图，而查看器需要真原图。
     */
    val fullImagePath: String? = null,
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

/**
 * 顶部筛选状态。
 *
 * 用密封接口而不是 String：`全部` / `收藏` 不是胶卷名，若混进字符串集合，
 * 某卷真的叫「全部」时就会撞车。原型用 `data-filter="all/fav/portra"` 也有同样隐患。
 */
sealed interface GalleryFilter {
    data object All : GalleryFilter
    data object Favorites : GalleryFilter
    data class Stock(val label: String) : GalleryFilter
}

/**
 * 筛选行的选项，顺序与内容都来自**真实数据**：
 *
 * 1. `全部` 永远第一；
 * 2. `★ 收藏` 永远第二（没有收藏时点进去显示空态，而不是把入口藏起来）；
 * 3. 之后是当前相册里真实出现过的胶卷名，按「该卷最新一张的拍摄时间」倒序，
 *    与下方分组顺序一致。没有照片的胶卷不会出现 —— 胶卷库里有 8 卷不代表相册里就有 8 卷。
 */
fun galleryFilters(photos: List<PhotoWallItem>): List<GalleryFilter> = buildList {
    add(GalleryFilter.All)
    add(GalleryFilter.Favorites)
    toRollGroups(photos).forEach { add(GalleryFilter.Stock(it.label)) }
}

/**
 * 应用筛选，返回**相册展示顺序**的扁平列表：先按卷分组（组按最新拍摄时间倒序），
 * 组内按拍摄时间倒序。
 *
 * 查看器直接吃这个列表，所以"照片墙上的顺序"和"左右滑的顺序"永远是同一个顺序；
 * 网格那边再用 [toRollGroups] 切组，两者不会打架。
 */
fun applyGalleryFilter(
    photos: List<PhotoWallItem>,
    filter: GalleryFilter,
    favoriteIds: Set<String>,
): List<PhotoWallItem> {
    val selected = when (filter) {
        GalleryFilter.All -> photos
        GalleryFilter.Favorites -> photos.filter { it.id in favoriteIds }
        is GalleryFilter.Stock -> photos.filter { it.stockLabel == filter.label }
    }
    return toRollGroups(selected).flatMap { it.photos }
}

/**
 * 按卷分组。**分组口径**：按胶卷名分组，组内按拍摄时间倒序，组间按该组最新一张倒序。
 *
 * 历史照片没有持久化「第几卷」的边界，按时间间隔猜一个新卷属于编造；因此同一款卷
 * 换两次卷会合成一组，界面上写「按胶卷分组」把这件事讲明白。
 */
fun toRollGroups(photos: List<PhotoWallItem>): List<PhotoGroup> =
    photos.groupBy { it.stockLabel }
        .map { (label, items) -> PhotoGroup(label, items.sortedByDescending { it.capturedAt }) }
        .sortedByDescending { it.photos.first().capturedAt }

/** 收藏数只统计"当前相册里真实存在的照片"，避免历史遗留 id 让角标虚高。 */
fun favoriteCount(photos: List<PhotoWallItem>, favoriteIds: Set<String>): Int =
    photos.count { it.id in favoriteIds }

/**
 * 环形索引：把任意整数页号折回 `0 until count`。
 *
 * `HorizontalPager` 没有"首尾相连"开关，所以查看器用一个大页数（[loopPageCount]）+
 * 取模来模拟无限循环。这个函数必须对负数和 count==0 都安全：空列表时查看器不会打开，
 * 但过渡动画/重组期间仍可能被调用一次，崩溃比显示空查看器更糟。
 */
fun circularIndex(index: Int, count: Int): Int {
    if (count <= 0) return 0
    val mod = index % count
    return if (mod < 0) mod + count else mod
}

/**
 * 虚拟页数。`HorizontalPager` 需要有限 pageCount，这里把真实张数重复 [LOOP_CYCLES] 遍：
 * 用户从中间往两边滑，等效于无限循环，又不会踩 `Int.MAX_VALUE` 的溢出风险。
 */
fun loopPageCount(photoCount: Int): Int =
    if (photoCount <= 0) 0 else photoCount * LOOP_CYCLES

/**
 * 打开查看器时的起始虚拟页：落在循环中段，并映射回用户点的那一张。
 *
 * 单测要保证 `circularIndex(loopStartPage(n, i), n) == i`（对任意 0 <= i < n）。
 */
fun loopStartPage(photoCount: Int, photoIndex: Int): Int =
    if (photoCount <= 0) {
        0
    } else {
        LOOP_CYCLES / 2 * photoCount + circularIndex(photoIndex, photoCount)
    }

/**
 * 按目标显示尺寸计算 `inSampleSize`。
 *
 * 规则：先把原图按比例缩到**刚好铺满目标框**（`fit`，不放大），再取"解码结果仍不小于
 * 该目标尺寸"的最大 2 的幂。这样：
 * - 12MP（4000×3000）给 1080p 屏幕看时 sample=2，解出 2000×1500（约 12MB），
 *   绝不把 48MB 的全尺寸位图塞进堆；
 * - 小图（小于目标框）sample=1，不会为了凑尺寸去放大解码。
 *
 * `inSampleSize` 只能是 2 的幂（BitmapFactory 会把非 2 的幂向下取整），所以不用
 * `ceil(原图/目标)` —— 那会得到 3.7 这种值，实际仍按 2 解，白算。
 */
fun calculateInSampleSize(
    outWidth: Int,
    outHeight: Int,
    reqWidth: Int,
    reqHeight: Int,
): Int {
    if (outWidth <= 0 || outHeight <= 0 || reqWidth <= 0 || reqHeight <= 0) return 1
    val fit = minOf(reqWidth.toFloat() / outWidth, reqHeight.toFloat() / outHeight)
        .coerceAtMost(1f)
    val targetWidth = outWidth * fit
    val targetHeight = outHeight * fit
    var sample = 1
    while (
        outWidth / (sample * 2) >= targetWidth &&
        outHeight / (sample * 2) >= targetHeight
    ) {
        sample *= 2
    }
    return sample
}

/** `06:42`。 */
internal fun formatClock(epochMillis: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
    return "%02d:%02d".format(
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
    )
}

/** `2026.10.12`。 */
internal fun formatDay(epochMillis: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
    return "%04d.%02d.%02d".format(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
    )
}

/**
 * 虚拟循环的遍数。1000 遍足够任何真机滑到手酸；取偶数是因为
 * [loopStartPage] 用 `LOOP_CYCLES / 2` 把起点放在正中间，两边都有同样多的余量。
 */
private const val LOOP_CYCLES = 1000
