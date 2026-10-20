package com.pocketccd.prototype.fotufilm

import android.content.Context
import android.util.Log
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
    private const val TAG = "FilmStockCatalog"

    /** 内置 pack 的账单：每行 `文件名 字节数`。见 [provisionBundled]。 */
    private const val BUNDLED_MANIFEST = "bundled_packs_v1.txt"

    /**
     * 可拍胶卷的最小帧面积。低于它的 pack 是**引擎夹具**，不是胶卷。
     *
     * 这不是拍脑袋的门槛，是一次真实故障留下的：CI 的 parity 用
     * `example-negative-400__96x64.fswp` 跑 64 个 case，这个文件被 push 到手机
     * 的 `film/` 后，因为文件名按字典序排在最前，被当成了默认卷。于是相机被
     * 约束到 96×64，CameraX 退到 1920×1440 绑定，随后三卷 12MP 全部精确不匹配
     * （D-037），表现为"快门按不动、且屏幕上没有任何提示"。
     *
     * 1 MP 是地板，远低于任何真实成片尺寸，只用来挡住 96×64 这类验收夹具。
     */
    private const val MIN_FRAME_PIXELS = 1_000_000L

    /**
     * 卷名可读化。文件名是 `gold200__4096x3072.fswp` 这种形式，
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
     * 把 APK 内置的 pack 落到 [directory]，让"装完就能拍"成立。
     *
     * 之前 pack 只能靠 `adb push`，别人装同一个 APK 会在胶卷库里看到 0 卷、快门永久
     * 置灰。assets 里带一份之后，第一次启动自己补齐；运行期仍以 `files/film/` 为准，
     * 所以开发者照旧可以 push 覆盖，也可以用更小的 pack 替换。
     *
     * **只增不删是错的一半**：pack 尺寸会变（换基准尺寸、去掉某支卷），旧文件留着会在
     * 胶卷库里多出几支"这台设备拍不了"的卷。所以用 [BUNDLED_MANIFEST] 记账，只清理
     * **自己复制过、而这一版 APK 里已经没有**的那些；`adb push` 进来的不在这本账上，
     * 不会被误删。
     *
     * 判定"要不要重写"靠清单里的字节数：每次启动都重写 1.2MB 是无谓的 IO，
     * 而 `assets.open()` 的可用长度在压缩资源上不可靠，落盘后取 `length()` 才准。
     *
     * @return 本次实际写入的文件数（0 表示已是最新）。
     */
    fun provisionBundled(context: Context, directory: File): Int {
        val assetNames = runCatching {
            context.assets.list("")?.filter { it.endsWith(EXTENSION, ignoreCase = true) }.orEmpty()
        }.getOrElse { error ->
            Log.w(TAG, "列不出内置 pack：${error.message}")
            emptyList()
        }
        if (assetNames.isEmpty()) return 0

        val manifest = File(directory, BUNDLED_MANIFEST)
        val previous = HashMap<String, Long>()
        manifest.takeIf { it.isFile }?.forEachLine { line ->
            val parts = line.trim().split(' ')
            val size = parts.getOrNull(1)?.toLongOrNull()
            if (parts.size == 2 && size != null) previous[parts[0]] = size
        }

        var copied = 0
        val current = LinkedHashMap<String, Long>()
        for (name in assetNames) {
            val target = File(directory, name)
            val expected = previous[name]
            if (target.isFile && expected != null && target.length() == expected) {
                current[name] = expected
                continue
            }
            runCatching {
                // 先写临时文件再换名：中途被杀不会留下半个 pack 被 scan 读到。
                val temp = File(directory, "$name.tmp")
                context.assets.open(name).use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                current[name] = target.length()
                copied++
            }.onFailure { Log.w(TAG, "内置 pack 落盘失败：$name", it) }
        }

        for (name in previous.keys - current.keys) {
            val stale = File(directory, name)
            if (stale.isFile && stale.delete()) Log.i(TAG, "移除已下线的内置 pack：$name")
        }

        runCatching {
            manifest.writeText(current.entries.joinToString("\n") { "${it.key} ${it.value}" })
        }
        if (copied > 0) Log.i(TAG, "内置 pack 补齐 $copied 个，共 ${current.size} 个")
        return copied
    }

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
                    if (pack.width.toLong() * pack.height < MIN_FRAME_PIXELS) {
                        // 明确记一笔：静默丢弃会让"我明明 push 了为什么没有这卷"再演一次。
                        Log.i(
                            TAG,
                            "跳过 $id：${pack.width}×${pack.height} 是引擎夹具，不是可拍胶卷",
                        )
                        null
                    } else {
                        FilmStock(
                            id = id,
                            label = labelOf(id),
                            file = file,
                            width = pack.width,
                            height = pack.height,
                            maxHalo = pack.maxHalo,
                        )
                    }
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
