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
 * 一次相册对账的结果。
 *
 * `queryOk == false` 表示 MediaStore 索引读不到，本轮**什么都没删**——这种情况
 * 要能区分出来，否则"照片还在"和"对账失败"在日志里长得一样。
 */
data class ReconcileReport(
    val removed: Int,
    val kept: Int,
    val queryOk: Boolean,
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
            // 记账：这张到底进没进过相册。对账时"相册里没有"才敢当删除证据。
            markPublished(id, galleryUri != null)

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
        var failed = 0
        val files = fullDir.listFiles { file -> file.isFile && file.name.endsWith(".jpg") }
        for (file in files.orEmpty()) {
            val id = parseId(file.name)?.first ?: file.name.removeSuffix(".jpg")
            if (existsInGallery(file.name)) {
                // 已经在相册里：明确记成"发布过"，否则下一轮对账会把它当"从没进过相册"。
                markPublished(id, true)
                continue
            }
            val capturedAt = parseId(file.name)?.third ?: file.lastModified()
            val published = publishToGallery(file, file.name, capturedAt)
            // 补传失败必须记账（D-047 安全阀③）：这张既不在相册、又没有"没发布"的记录，
            // 下一轮对账就会认定"用户删了它"，而它是唯一一份。
            markPublished(id, published != null)
            if (published != null) added++ else failed++
        }

        prefs.edit().putBoolean(KEY_BACKFILL_DONE, true).apply()
        if (added > 0 || failed > 0) Log.i(TAG, "相册补齐 $added 张，失败 $failed 张")
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

    /**
     * 和系统相册对账：**用户在系统相册里删掉的成片，私有副本也一起删掉**。
     *
     * 为什么需要它：成片写两份（D-019）——私有目录是照片墙的数据源，系统相册是给
     * 外面看的。用户在相册里删掉一张，私有副本不会自己消失，照片墙就永远显示
     * 那张"已经删掉的照片"。这是两份副本分叉的唯一现实出口。
     *
     * 判定规则在 [GalleryReconciler.staleIds]（可单测）；这里只做"读索引 + 删文件"。
     *
     * 四条安全阀，任何一条不满足就整批放弃：
     * 0. **相册补齐没跑完 → 不对账**。补传与对账并发时，历史照片会在补传前被删光。
     * 1. **查询失败、或查询成功但一行都没有 → 整批不删**。MediaStore 抖一下、
     *    索引重建、owner 记录丢失，都不能变成清空照片库。
     * 2. **等待期内不删**（[GalleryReconciler.GRACE_MILLIS]）：刚写完的照片可能还没索引。
     * 3. **没发布成功的不删**：相册里从来没有过它，不能拿"相册没有"当删除证据。
     *
     * @return 本次删掉的张数；`queryOk == false` 表示相册读不到、本轮什么都没做。
     */
    fun reconcileWithGallery(now: Long = System.currentTimeMillis()): ReconcileReport {
        val files = fullDir.listFiles { file -> file.isFile && file.name.endsWith(".jpg") }
            .orEmpty()
        if (files.isEmpty()) return ReconcileReport(removed = 0, kept = 0, queryOk = true)

        // 安全阀⓪：相册补齐还没跑完时**绝不**对账。
        //
        // 两条路径没有任何顺序约束：`AppViewModel.init` 在 IO 上异步启动 syncToGallery，
        // 而 AppRoot 的 lifecycle 观察者挂在已经 RESUMED 的 lifecycle 上，会立刻补发
        // ON_RESUME → reconcile 同时开跑。这时索引里只有"已补传完的那几张"，其余历史
        // 照片"名字不在 + 时间不在 + 早过等待期" → 被当成用户已删除，而补传循环随后
        // 读不到源文件（FileNotFoundException 被吞成 null），标记却照样置位 → 永不恢复。
        //
        // 这条只在"从只写私有目录的旧版本升级上来"的第一次启动触发，开发者本机永远不会
        // 中招，是典型的"只在真实用户那儿响的雷"。
        if (!prefs.getBoolean(KEY_BACKFILL_DONE, false)) {
            Log.i(TAG, "对账跳过：相册补齐还没做完")
            return ReconcileReport(removed = 0, kept = files.size, queryOk = false)
        }

        val index = readGalleryIndex()
            ?: run {
                Log.w(TAG, "对账跳过：读不到 MediaStore 索引（不删任何照片）")
                return ReconcileReport(removed = 0, kept = files.size, queryOk = false)
            }

        // 安全阀①：查询"成功"但一行都没有，同样不下判断。见 GalleryReconciler 的类注释。
        if (index.names.isEmpty() && index.timestamps.isEmpty()) {
            Log.w(TAG, "对账跳过：相册索引为空（可能是索引重建，不删任何照片）")
            return ReconcileReport(removed = 0, kept = files.size, queryOk = false)
        }

        val unpublished = prefs.getStringSet(KEY_GALLERY_MISSING, emptySet())
            ?.toHashSet()
            .orEmpty()

        val candidates = files.mapNotNull { file ->
            val parsed = parseId(file.name) ?: return@mapNotNull null
            GalleryReconciler.Candidate(
                id = parsed.first,
                fileName = file.name,
                capturedAt = parsed.third,
                published = parsed.first !in unpublished,
            )
        }

        val stale = GalleryReconciler.staleIds(
            candidates = candidates,
            galleryNames = index.names,
            galleryTimestampSeconds = index.timestamps,
            now = now,
        )

        var removed = 0
        for (id in stale) {
            val name = "$id.jpg"
            val full = File(fullDir, name)
            val thumb = File(thumbDir, name)
            // 以全尺寸副本的删除结果为准：`list()` 只看 full，thumb 残留只是垃圾不致命。
            if (full.delete()) {
                thumb.delete()
                removed++
            } else {
                Log.w(TAG, "对账：删除失败 $name")
            }
        }

        if (removed > 0) {
            Log.i(TAG, "对账：相册里已删除 $removed 张，私有副本同步移除")
        }
        return ReconcileReport(removed = removed, kept = candidates.size - removed, queryOk = true)
    }

    /**
     * 读相册索引：现存条目的文件名 + 拍摄时间（秒）。
     *
     * **不加任何 selection**，靠分区存储的可见性规则拿"本应用自己的媒体条目"：
     * 我们没有申请读媒体权限，系统只会返回本应用插入的那些，不会扫到用户的相册。
     * 不加 `RELATIVE_PATH` 过滤是故意的——用户可能把照片移到别的相册，路径一变
     * 就查不到，会被误判成删除。
     *
     * @return 查询失败返回 null（调用方据此整批放弃）。
     */
    private fun readGalleryIndex(): GalleryIndex? = runCatching {
        val names = HashSet<String>()
        val timestamps = HashSet<Long>()

        val cursor = resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
            ),
            null,
            null,
            null,
        ) ?: error("MediaStore 查询返回 null 游标")

        cursor.use {
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            while (it.moveToNext()) {
                it.getString(nameColumn)?.let(names::add)
                val taken = it.getLong(dateColumn)
                // 秒级归一：部分机型把 DATE_TAKEN 截断到秒，毫秒比对会全部失配。
                if (taken > 0L) timestamps.add(taken / 1000L)
            }
        }

        GalleryIndex(names, timestamps)
    }.getOrNull()

    private data class GalleryIndex(
        val names: Set<String>,
        val timestamps: Set<Long>,
    )

    /** 记录一张成片的相册发布结果。见 [ReconcileReport] 的第三条安全阀。 */
    private fun markPublished(id: String, published: Boolean) {
        val current = prefs.getStringSet(KEY_GALLERY_MISSING, emptySet())?.toHashSet()
            ?: HashSet()
        val changed = if (published) current.remove(id) else current.add(id)
        // SharedPreferences 返回的 Set 不允许原地改，必须复制后整份写回。
        if (changed) prefs.edit().putStringSet(KEY_GALLERY_MISSING, current).apply()
    }

    /**
     * 本卷的起始时间戳（毫秒）。
     *
     * 「一卷 36 张」要成立，计数就必须有起点——否则计数器显示的是整个照片库的
     * 张数，那是在撒谎（库里有 35 张并不代表这卷只剩 1 张）。首次调用时把起点
     * 落成"现在"：老照片属于之前的卷，不该算进新卷。
     */
    fun rollStartedAt(): Long {
        val stored = prefs.getLong(KEY_ROLL_STARTED_AT, 0L)
        if (stored > 0L) return stored
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_ROLL_STARTED_AT, now).apply()
        return now
    }

    /** 换卷：把起点推到此刻，计数器归零。 */
    fun startNewRoll(): Long {
        val now = System.currentTimeMillis()
        // 用 commit 而不是 apply：换卷后马上会读计数，异步落盘会有一次闪回旧值。
        prefs.edit().putLong(KEY_ROLL_STARTED_AT, now).commit()
        return now
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

        /** 相册里的子目录。放在 `Pictures/` 下，系统相册与其他应用都能直接看到。 */
        private val GALLERY_RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/Pocket CCD"

        /** 历史补齐的一次性标记。改名等于重新跑一次迁移。 */
        private const val KEY_BACKFILL_DONE = "gallery_backfill_done_v1"

        /** 本卷起点。0 表示还没初始化过，首次读取时落成"现在"。 */
        private const val KEY_ROLL_STARTED_AT = "roll_started_at_v1"

        /**
         * 没能写进系统相册的成片 id。
         *
         * 对账要判断"相册里没有 = 用户删了"，而这些照片从来没进过相册，
         * 混进去就会被当成已删除、把唯一一份删掉。
         */
        private const val KEY_GALLERY_MISSING = "gallery_missing_ids_v1"

        private const val TAG = "PocketCcdGallery"
    }
}
