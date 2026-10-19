package com.pocketccd.prototype.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
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
    /**
     * 写进系统相册后的 Uri。为 null 表示这次没写进去（相册不可用、空间不足等），
     * 但**本地文件一定在**，照片墙照常可见。
     *
     * [PhotoStore.list] 从私有目录重建的记录没有这个值：每次启动都去 MediaStore
     * 反查一遍代价太高，而只有"刚拍完"这一屏需要它。
     */
    val galleryUri: Uri? = null,
)

/**
 * 成片落盘：**写两份**。
 *
 * 1. **应用私有目录**（`Android/data/<pkg>/files/Pictures/pocket-ccd/`）是权威副本，
 *    照片墙只读这里。好处是照片墙天然「只显示本应用拍的照片」，既不用扫描用户相册，
 *    也不需要任何读取权限。
 * 2. **系统相册**（`Pictures/Pocket CCD/`）是给外面看的：出门拍完要分享、要在别的
 *    应用里打开，都得走相册。API 29+ 的分区存储下，写**自己创建的**媒体条目
 *    不需要任何权限。
 *
 * 早先只写第 1 份，结果是照片墙里有、系统相册里没有 —— 相机"拍出来"的照片
 * 在相机之外完全不可见，等于没拍。
 *
 * 写盘一律 `临时文件 → rename`，避免进程被杀时留下半张 JPEG 让下次读取烧掉。
 */
class PhotoStore(context: Context) {

    private val resolver = context.contentResolver

    /** 只存"历史补齐做过了没有"这类一次性标记，不存业务数据。 */
    private val prefs = context.getSharedPreferences("photo_store", Context.MODE_PRIVATE)

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

            // 相册副本从**已经落盘的文件**复制，不重新编码位图：12MP 再压一次要多花
            // 约 300ms 和一份 48MB 临时位图，而字节内容完全一样。
            val galleryUri = publishToGallery(full, "$id.jpg", capturedAt)

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
                galleryUri = galleryUri,
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

    /**
     * 把已经落盘的成片再写一份到系统相册。
     *
     * 直接复制文件流，不碰位图。`IS_PENDING` 是必须的：先设 1 写入，完成后再置 0，
     * 否则相册里会出现半张图 —— Android 10 起媒体库会把 pending 的条目对第三方隐藏。
     *
     * 任何一步失败都只降级成"照片墙里有、相册里没有"，绝不抛：拍照结果不能因为
     * 相册不可写而丢失。
     *
     * @return 相册条目 Uri，失败为 null。
     */
    private fun publishToGallery(source: File, displayName: String, capturedAt: Long): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, GALLERY_RELATIVE_PATH)
            put(MediaStore.Images.Media.DATE_TAKEN, capturedAt)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = runCatching {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull()
        if (uri == null) {
            Log.w(TAG, "相册插入失败：$displayName")
            return null
        }

        return runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("openOutputStream 返回 null")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
            uri
        }.getOrElse { error ->
            Log.w(TAG, "相册写入失败：$displayName", error)
            // 清掉半成品，否则相册里会留一条永远 pending 的僵尸记录。
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    /**
     * 把私有目录里已有、但相册里还没有的成片补进去。**只补一次**。
     *
     * 早先拍的照片只存在 `Android/data/` 下 —— 那个目录被媒体扫描明确排除，
     * 系统相册永远看不到。这里做一次性补齐，让历史照片也能在相册里翻到。
     *
     * **必须只做一次**：否则用户从系统相册里删掉某张照片，下次启动又会被补回来，
     * 变成删不掉的鬼照片。标记写在循环之后，中途异常会让下次启动重试，
     * 而不是永久放弃补齐。
     *
     * @return 本次补进去的张数；已经做过迁移时返回 0。
     */
    fun syncToGallery(): Int {
        if (prefs.getBoolean(KEY_BACKFILL_DONE, false)) return 0

        var added = 0
        val files = fullDir.listFiles { file -> file.isFile && file.name.endsWith(".jpg") }
        for (file in files.orEmpty()) {
            if (existsInGallery(file.name)) continue
            val capturedAt = parseId(file.name)?.third ?: file.lastModified()
            if (publishToGallery(file, file.name, capturedAt) != null) added++
        }

        prefs.edit().putBoolean(KEY_BACKFILL_DONE, true).apply()
        if (added > 0) Log.i(TAG, "相册补齐 $added 张")
        return added
    }

    /**
     * 相册里是否已有同名条目。
     *
     * `RELATIVE_PATH` 存进去时带不带尾斜杠因厂商而异，所以用 `LIKE` 前缀匹配。
     * 文件名含毫秒时间戳与卷名，重复概率可以忽略。
     */
    private fun existsInGallery(displayName: String): Boolean = runCatching {
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND " +
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            arrayOf(displayName, "$GALLERY_RELATIVE_PATH%"),
            null,
        )?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

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

        /** 相册里的子目录。放在 `Pictures/` 下，系统相册与其他应用都能直接看到。 */
        private val GALLERY_RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/Pocket CCD"

        /** 历史补齐的一次性标记。改名等于重新跑一次迁移。 */
        private const val KEY_BACKFILL_DONE = "gallery_backfill_done_v1"

        private const val TAG = "PocketCcdGallery"
    }
}
