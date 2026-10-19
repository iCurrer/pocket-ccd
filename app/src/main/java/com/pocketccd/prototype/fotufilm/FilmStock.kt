package com.pocketccd.prototype.fotufilm

import android.content.Context
import java.io.File

/**
 * 一卷可用的胶片：一份 FSWP pack 加上它的展示信息。
 *
 * [width]/[height] 是 pack 头部声明的帧尺寸。它同时是**唯一的**合法采集尺寸 ——
 * pack 里的扩散/晕光/颗粒半径已经烘焙成像素数，换尺寸等于换了光学系统（D-037）。
 * 所以相机分辨率必须精确等于它，见 [FilmStockCatalog.scan] 的调用方。
 */
data class FilmStock(
    /** 文件名去掉扩展名，作为稳定 ID。 */
    val id: String,
    /** UI 上显示的卷名，如 `GOLD 200`。 */
    val label: String,
    val file: File,
    val width: Int,
    val height: Int,
    val maxHalo: Int,
) {
    val sizeLabel: String get() = "${width}×$height"
}

/**
 * pack 目录的扫描器。
 *
 * pack 不进 APK（体积 + 许可口径），由 `adb push` 推到
 * `Android/data/<pkg>/files/film/`。目录不存在时这里**自己建**，因为
 * `adb shell mkdir` 建出来的目录属主是 shell、模式 `drwxrws---`，App 透过
 * FUSE 能 push 进去却列不出内容，表现为「4 个 pack 在设备上但 packCount=0」。
 * 这个坑已经踩过一次，别把 mkdirs() 删掉。
 */
object FilmStockCatalog {

    const val DIRECTORY_NAME = "film"
    private const val EXTENSION = ".fswp"

    /**
     * 卷名可读化。文件名是 `gold200__4000x3000.fswp` 这种形式，
     * 直接大写不好看（`TRIX400`），所以给常用卷一张显式表，其余走兜底规则。
     */
    private val KNOWN_LABELS = mapOf(
        "gold200" to "GOLD 200",
        "portra400" to "PORTRA 400",
        "trix400" to "TRI-X 400",
        "ultramax400" to "ULTRAMAX 400",
        "colorplus200" to "COLORPLUS 200",
        "superia400" to "SUPERIA 400",
        "example-negative-400" to "EXAMPLE 400",
    )

    fun directory(context: Context): File =
        File(context.getExternalFilesDir(null), DIRECTORY_NAME).apply { mkdirs() }

    /**
     * 列出目录里所有**能解析**的 pack。解析失败的直接跳过 —— 一个坏文件
     * 不应该让整个胶卷目录消失，但也不该被静默当成可用卷。
     */
    fun scan(directory: File): List<FilmStock> {
        val files = directory.listFiles { file ->
            file.isFile && file.name.endsWith(EXTENSION, ignoreCase = true)
        }?.sortedBy { it.name } ?: return emptyList()

        return files.mapNotNull { file ->
            runCatching {
                FilmPack.fromFile(file).use { pack ->
                    val id = file.name.removeSuffix(EXTENSION)
                    FilmStock(
                        id = id,
                        label = labelOf(id),
                        file = file,
                        width = pack.width,
                        height = pack.height,
                        maxHalo = pack.maxHalo,
                    )
                }
            }.getOrNull()
        }
    }

    /** 从文件名（去掉扩展名）推出展示名。照片墙读回旧文件时也要用同一套规则。 */
    internal fun labelOf(id: String): String {
        val base = id.substringBefore("__").lowercase()
        KNOWN_LABELS[base]?.let { return it }
        return base.uppercase().replace('_', ' ')
    }
}
