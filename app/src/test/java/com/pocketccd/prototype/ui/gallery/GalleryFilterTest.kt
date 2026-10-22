package com.pocketccd.prototype.ui.gallery

import com.pocketccd.prototype.camera.toggleFavoriteId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 相册纯逻辑的单测：筛选 / 分组顺序 / 查看器首尾循环 / 采样率 / 收藏翻转。
 *
 * 这些规则错了不会崩，但会以很难查的方式错：筛"收藏"少一张、左右滑到最后一页
 * 直接卡住、12MP 原图整张进堆在低端机 OOM。全部是 JVM 纯函数，不需要 Robolectric、
 * 不需要新增依赖（只用 junit）。
 */
class GalleryFilterTest {

    private fun photo(
        id: String,
        stock: String,
        capturedAt: Long,
    ) = PhotoWallItem(
        id = id,
        stockLabel = stock,
        capturedAt = capturedAt,
        // 路径不属于判定逻辑，留空即可；也顺带证明这些函数不碰文件系统。
        imagePath = null,
        fullImagePath = null,
    )

    // 卷内新旧交错、卷间新旧交错，才能测出"组内倒序"和"组间按最新张倒序"是两件事。
    private val goldOld = photo("g-old", "GOLD 200", 1_760_600_000_000)
    private val goldNew = photo("g-new", "GOLD 200", 1_760_800_000_000)
    private val portraNew = photo("p-new", "PORTRA 400", 1_760_900_000_000)
    private val portraOld = photo("p-old", "PORTRA 400", 1_760_100_000_000)
    private val allPhotos = listOf(goldOld, goldNew, portraNew, portraOld)

    // ------------------------------------------------------------ 筛选

    @Test
    fun `全部筛选保留所有照片`() {
        assertEquals(
            listOf("p-new", "p-old", "g-new", "g-old"),
            applyGalleryFilter(allPhotos, GalleryFilter.All, emptySet()).map { it.id },
        )
    }

    @Test
    fun `收藏筛选只留收藏的照片`() {
        assertEquals(
            listOf("g-new", "g-old"),
            applyGalleryFilter(
                photos = allPhotos,
                filter = GalleryFilter.Favorites,
                favoriteIds = setOf("g-old", "g-new"),
            ).map { it.id },
        )
    }

    @Test
    fun `收藏筛选下没有收藏的卷整组隐藏`() {
        // 只收藏 GOLD：PORTRA 组一张不剩，结果里不能出现它的任何照片。
        val result = applyGalleryFilter(allPhotos, GalleryFilter.Favorites, setOf("g-new"))
        assertEquals(listOf(goldNew), result)
        assertTrue(result.none { it.stockLabel == "PORTRA 400" })
    }

    @Test
    fun `收藏集合里不存在的照片 id 不产生任何行`() {
        // 历史遗留 id（照片被系统相册删掉）不能让筛选结果凭空多一张。
        assertEquals(
            emptyList<PhotoWallItem>(),
            applyGalleryFilter(allPhotos, GalleryFilter.Favorites, setOf("ghost")),
        )
    }

    @Test
    fun `按卷筛选只留该卷`() {
        assertEquals(
            listOf("p-new", "p-old"),
            applyGalleryFilter(
                photos = allPhotos,
                filter = GalleryFilter.Stock("PORTRA 400"),
                favoriteIds = emptySet(),
            ).map { it.id },
        )
        assertEquals(
            listOf("g-new", "g-old"),
            applyGalleryFilter(
                photos = allPhotos,
                filter = GalleryFilter.Stock("GOLD 200"),
                favoriteIds = emptySet(),
            ).map { it.id },
        )
    }

    @Test
    fun `筛选不存在的卷得到空列表而不是崩溃`() {
        assertEquals(
            emptyList<PhotoWallItem>(),
            applyGalleryFilter(allPhotos, GalleryFilter.Stock("NEVER SHOT"), emptySet()),
        )
    }

    // ------------------------------------------------------------ 分组顺序

    @Test
    fun `分组按最新一张倒序且组内倒序`() {
        val groups = toRollGroups(allPhotos)
        assertEquals(listOf("PORTRA 400", "GOLD 200"), groups.map { it.label })
        assertEquals(listOf("p-new", "p-old"), groups[0].photos.map { it.id })
        assertEquals(listOf("g-new", "g-old"), groups[1].photos.map { it.id })
    }

    @Test
    fun `筛选后仍然按卷分组`() {
        // 收藏筛选是跨卷的，结果必须重新分组，而不是按收藏时间排成一条。
        val groups = toRollGroups(applyGalleryFilter(allPhotos, GalleryFilter.Favorites, setOf("g-old", "p-new")))
        assertEquals(listOf("PORTRA 400", "GOLD 200"), groups.map { it.label })
    }

    @Test
    fun `筛选行由真实卷名动态生成`() {
        // 全部 / 收藏 恒在，之后按分组顺序列出真实的卷，没有照片的卷不出现。
        assertEquals(
            listOf(
                GalleryFilter.All,
                GalleryFilter.Favorites,
                GalleryFilter.Stock("PORTRA 400"),
                GalleryFilter.Stock("GOLD 200"),
            ),
            galleryFilters(allPhotos),
        )
        assertEquals(
            listOf(GalleryFilter.All, GalleryFilter.Favorites),
            galleryFilters(emptyList()),
        )
    }

    @Test
    fun `收藏角标只统计当前相册里存在的照片`() {
        // "ghost" 是历史遗留 id：不在照片列表里，不能算进角标。
        assertEquals(2, favoriteCount(allPhotos, setOf("g-new", "p-old", "ghost")))
        assertEquals(0, favoriteCount(allPhotos, emptySet()))
    }

    // ------------------------------------------------------------ 首尾循环

    @Test
    fun `环形索引首尾相接`() {
        assertEquals(0, circularIndex(0, 3))
        assertEquals(2, circularIndex(2, 3))
        assertEquals(0, circularIndex(3, 3))
        assertEquals(1, circularIndex(4, 3))
        assertEquals(2, circularIndex(-1, 3))
        assertEquals(0, circularIndex(-3, 3))
        assertEquals(2, circularIndex(5, 3))
    }

    @Test
    fun `单张照片的循环永远是第零张`() {
        assertEquals(0, circularIndex(0, 1))
        assertEquals(0, circularIndex(1, 1))
        assertEquals(0, circularIndex(999, 1))
        assertEquals(0, circularIndex(-5, 1))
        // 单张也要有可滑动的页：否则 pager 会画成"拖不动"，看起来像卡死。
        assertTrue(loopPageCount(1) > 1)
        assertTrue(loopStartPage(1, 0) > 0)
    }

    @Test
    fun `空列表的循环索引安全且页数为零`() {
        // 空列表时查看器不会打开，但过渡动画/重组期间仍可能调用一次；崩溃比空页面更糟。
        assertEquals(0, circularIndex(0, 0))
        assertEquals(0, circularIndex(7, 0))
        assertEquals(0, circularIndex(-7, 0))
        assertEquals(0, loopPageCount(0))
        assertEquals(0, loopPageCount(-3))
        assertEquals(0, loopStartPage(0, 5))
    }

    @Test
    fun `起始页映射回用户点的那一张`() {
        for (index in 0 until 3) {
            assertEquals(index, circularIndex(loopStartPage(3, index), 3))
        }
        // 起点两侧都要有余量，否则一打开就没法往回滑。
        assertTrue(loopStartPage(3, 0) > 0)
        assertTrue(loopStartPage(3, 2) < loopPageCount(3) - 1)
    }

    @Test
    fun `左右滑动首尾循环`() {
        val count = 3
        // 最后一张往右（+1）回到第一张，第一张往左（-1）回到最后一张。
        assertEquals(0, circularIndex(loopStartPage(count, count - 1) + 1, count))
        assertEquals(count - 1, circularIndex(loopStartPage(count, 0) - 1, count))
    }

    // ------------------------------------------------------------ 采样率

    @Test
    fun `12MP 原图按 1080p 屏幕采样后不是全尺寸`() {
        // 4000×3000 直解是 48MB；1080p 屏上要降到 2000×1500（约 12MB）。
        val sample = calculateInSampleSize(
            outWidth = 4000,
            outHeight = 3000,
            reqWidth = 1080,
            reqHeight = 1920,
        )
        assertEquals(2, sample)
        assertTrue(sample >= 2)
        // 解码后的长边（2000）仍不小于屏幕宽（1080），不会糊。
        assertTrue(4000 / sample >= 1080)
    }

    @Test
    fun `照片墙约 110dp 的格子解码 1440px 缩略图时继续降采样`() {
        // 3 列布局下每格约 330px（3x 屏），1440px 缩略图再降一级，省 3/4 内存。
        assertEquals(
            4,
            calculateInSampleSize(
                outWidth = 1440,
                outHeight = 1080,
                reqWidth = 330,
                reqHeight = 330,
            ),
        )
    }

    @Test
    fun `比目标框还小的图不放大采样`() {
        assertEquals(1, calculateInSampleSize(100, 80, 330, 330))
    }

    @Test
    fun `尺寸非法时退化为不采样而不是除零`() {
        assertEquals(1, calculateInSampleSize(0, 0, 330, 330))
        assertEquals(1, calculateInSampleSize(4000, 3000, 0, 0))
        assertEquals(1, calculateInSampleSize(-1, 3000, 1080, 1920))
    }

    // ------------------------------------------------------------ 收藏翻转

    @Test
    fun `收藏翻转往返`() {
        val afterAdd = toggleFavoriteId(emptySet(), "film_1_gold200")
        assertEquals(setOf("film_1_gold200"), afterAdd)

        val afterRemove = toggleFavoriteId(afterAdd, "film_1_gold200")
        assertEquals(emptySet<String>(), afterRemove)
    }

    @Test
    fun `收藏翻转不修改传入的集合`() {
        val original = setOf("a")
        val next = toggleFavoriteId(original, "b")
        assertEquals(setOf("a"), original)
        assertEquals(setOf("a", "b"), next)
    }

    @Test
    fun `空 id 不写进收藏`() {
        val current = setOf("a")
        assertEquals(current, toggleFavoriteId(current, ""))
        assertEquals(current, toggleFavoriteId(current, "   "))
    }
}
