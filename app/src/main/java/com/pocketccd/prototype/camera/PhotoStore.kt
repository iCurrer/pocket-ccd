package com.pocketccd.prototype.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Environment
import java.io.File

/**
 * 一张已经冲洗完成的成片。
 *
 * 全尺寸原图与缩略图**分开放**：照片墙和成片页只加载缩略图。12MP 的
 * ARGB_8888 是 48MB，在列表里解一张就够把堆打满。
 */
data class SavedPhoto(
    val id: String,
    val stockId: String,
    val stockLabel: String,
    val fullFile: File,
    val thumbnailFile: File,
    val width: Int,
    val height: Int,
    val capturedAt: Long,
    /** 引擎耗时，用于成片页的诊断行。 */
    val renderMillis: Long,
    val strips: Int,
)

/**
 * 成片落盘。
 *
 * 只写应用自己的外部私有目录（`Android/data/<pkg>/files/Pictures/`）：
 * 不申请任何存储权限、不写系统相册、不需要 MediaStore。因此照片墙
 * 天然「只显示本应用拍的照片」，不需要扫描用户相册。
 *
 * 写盘一律 `临时文件 → rename`，避免进程被杀时留下半张 JPEG 让下次读取烧掉。
 */
class PhotoStore(context: Context) {

    private val root = File(
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
        "pocket-ccd",
    )

    private val fullDir = File(root, "full").apply { mkdirs() }
    private val thumbDir = File(root, "thumb").apply { mkdirs() }

    /**
     * 把成片写盘。返回的记录里缩略图已经生成。
     *
     * @param rotationDegrees 成品需要顺时针旋转的度数。**旋转发生在这里，而不是冲洗前**：
     *   pack 的扩散/晕光/颗粒半径已按 4096×3072 烘成像素数（D-037），先转成 3072×4096
     *   会让冲洗直接失败。所以冲洗永远按 pack 尺寸跑，效果烘焙完再转——
     *   此时像素只是换个摆法，颗粒尺度不受影响。
     * @param quality JPEG 质量。92 是设计稿定的值（设置页「高 · 92」）。
     */
    fun save(
        bitmap: Bitmap,
        stock: com.pocketccd.prototype.fotufilm.FilmStock,
        capturedAt: Long,
        renderMillis: Long,
        strips: Int,
        rotationDegrees: Int = 0,
        quality: Int = 92,
    ): SavedPhoto {
        val id = "film_${capturedAt}_${stock.id}"
        val upright = rotate(bitmap, rotationDegrees)

        try {
            val full = File(fullDir, "$id.jpg")
            writeAtomically(full) { upright.compress(Bitmap.CompressFormat.JPEG, quality, it) }

            // 缩略图长边 1440：够成片页铺满、够照片墙网格，内存只有原图的 1/7。
            // 从**旋转之后**的图生成，否则会出现"大图正、小图躺"。
            val thumbBitmap = scaleForThumbnail(upright)
            val thumb = File(thumbDir, "$id.jpg")
            writeAtomically(thumb) { thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            if (thumbBitmap !== upright) thumbBitmap.recycle()

            return SavedPhoto(
                id = id,
                stockId = stock.id,
                stockLabel = stock.label,
                fullFile = full,
                thumbnailFile = thumb,
                width = upright.width,
                height = upright.height,
                capturedAt = capturedAt,
                renderMillis = renderMillis,
                strips = strips,
            )
        } finally {
            // 只有真的转过才有新对象；没转时返回的就是入参，回收权仍归调用方。
            if (upright !== bitmap) upright.recycle()
        }
    }

    /** 顺时针旋转。0° 时原样返回，不复制 —— 12MP 的 ARGB 复制一次就是 50MB。 */
    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return source
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /** 按拍摄时间倒序列出所有成片。解析不出卷名的文件跳过，不让坏文件污染列表。 */
    fun list(): List<SavedPhoto> {
        val files = fullDir.listFiles { file -> file.isFile && file.name.endsWith(".jpg") }
            ?: return emptyList()

        return files.mapNotNull { file ->
            val parsed = parseId(file.name) ?: return@mapNotNull null
            SavedPhoto(
                id = parsed.first,
                stockId = parsed.second,
                stockLabel = com.pocketccd.prototype.fotufilm.FilmStockCatalog
                    .labelOf(parsed.second),
                fullFile = file,
                thumbnailFile = File(thumbDir, file.name).takeIf { it.isFile } ?: file,
                width = 0,
                height = 0,
                capturedAt = parsed.third,
                renderMillis = 0L,
                strips = 0,
            )
        }.sortedByDescending { it.capturedAt }
    }

    /** `film_<毫秒时间戳>_<stockId>.jpg` → (id, stockId, 时间戳)。 */
    private fun parseId(fileName: String): Triple<String, String, Long>? {
        val stem = fileName.removeSuffix(".jpg")
        val parts = stem.split('_', limit = 3)
        if (parts.size < 3 || parts[0] != "film") return null
        val timestamp = parts[1].toLongOrNull() ?: return null
        return Triple(stem, parts[2], timestamp)
    }

    private fun scaleForThumbnail(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= THUMBNAIL_LONG_EDGE) return source
        val scale = THUMBNAIL_LONG_EDGE.toFloat() / longest
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private inline fun writeAtomically(target: File, block: (java.io.OutputStream) -> Boolean) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.outputStream().use { stream ->
            val ok = block(stream)
            stream.flush()
            check(ok) { "JPEG 编码失败：${target.name}" }
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    companion object {
        /** 缩略图长边。1440 在 3x 屏上仍比成片页的实际显示尺寸大。 */
        const val THUMBNAIL_LONG_EDGE = 1440
    }
}
