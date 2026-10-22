package com.pocketccd.prototype.ui.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

/**
 * 相册的位图解码：**一律在 IO 线程、一律按显示尺寸采样**。
 *
 * 为什么单独抽出来：照片墙原先是 `remember { BitmapFactory.decodeFile(path) }` ——
 * 主线程解码，快速滚动就是掉帧（review P1-4）。现在调用方用 `produceState` 在
 * `Dispatchers.IO` 上解码，这里只负责"怎么解"这一件纯技术问题。
 *
 * 目标尺寸由调用方按实际显示框算好传进来（网格格子约 110dp，查看器是整块屏幕），
 * 采样率交给 [calculateInSampleSize]。12MP 原图直解是 48MB，任何一处漏掉采样都会
 * 在低端机上直接 OOM，所以这里**没有**"不传尺寸"的重载。
 */

/**
 * 缩略图内存缓存。
 *
 * 为什么需要：`LazyVerticalGrid` 快速滚动时同一格会被反复回收/重解，每次解一张
 * 1440px 缩略图要几毫秒到十几毫秒，来回滚就掉帧。
 *
 * 只缓存照片墙缩略图（约 400KB/张）**不缓存查看器大图**（约 12MB/张）：
 * 大图缓存两页就可能把堆吃掉四分之一，而查看器每次只显示一页、滑回去重解一次
 * 比赌内存安全更划算。
 */
internal object GalleryBitmapCache {

    private val cache = object : LruCache<String, Bitmap>(maxBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(path: String): Bitmap? = cache.get(path)

    fun put(path: String, bitmap: Bitmap) {
        cache.put(path, bitmap)
    }

    /** 上限取堆的 1/8，并夹在 8–32MB：给别的位图（取景/成片）留出余量。 */
    private fun maxBytes(): Int {
        val heap = Runtime.getRuntime().maxMemory()
        return (heap / 8).coerceIn(8L * 1024 * 1024, 32L * 1024 * 1024).toInt()
    }
}

/**
 * 在 IO 线程上调用：先读尺寸边界（`inJustDecodeBounds`，不分配像素），再按采样率解码。
 *
 * @param path JPEG 绝对路径。
 * @param reqWidth 目标显示宽（px）。传显示框尺寸即可，不需要乘屏幕密度 —— 单位已经是 px。
 * @param reqHeight 目标显示高（px）。
 * @return 解码失败的位图；文件不存在、JPEG 损坏、内存不足都返回 null，由调用方画空态。
 */
internal fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
    if (reqWidth <= 0 || reqHeight <= 0) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
    }
    // decodeFile 对损坏文件返回 null；OOM 会抛 Error。两者都只该让这一格显示空态，
    // 不能让整个相册页崩掉 —— 用户拍过的照片不能因为一张坏图全部看不见。
    return runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
}
