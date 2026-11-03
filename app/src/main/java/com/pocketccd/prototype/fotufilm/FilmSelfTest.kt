package com.pocketccd.prototype.fotufilm

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * 真机自测。目的只有一个：把 FOT-004 的验收条件变成可复现的数字。
 *
 * 跑三件事：
 * 1. **native 状态** —— 库是否载入、ABI 版本、Halide 内核是否可用。
 * 2. **分块正确性** —— 用 96×64 的 pack 同时走"整帧"和"分块"两条路径，算两者的
 *    逐通道 RMS。分块几何（origin / interiorTop / halo）一旦算错，接缝会立刻在
 *    RMS 上暴露；这一项比肉眼看样片可靠得多。
 * 3. **12MP 基准** —— 每个 12MP pack 用确定性程序纹理跑整帧分块，记录耗时分解和
 *    进程峰值内存（/proc/self/status 的 VmHWM）。
 *
 * 结果写成 JSON，落在 App 的外部私有目录，`adb pull` 即可取走。
 */
class FilmSelfTest(private val context: Context) {

    fun run(): JSONObject {
        val report = JSONObject()
        report.put("generatedAt", System.currentTimeMillis())
        report.put("device", deviceInfo())

        val native = JSONObject()
        native.put("libraryLoaded", FotufilmNative.loaded)
        FotufilmNative.loadFailure?.let { native.put("loadFailure", it.toString()) }
        if (FotufilmNative.loaded) {
            native.put("abiVersion", runCatching { FotufilmNative.nativeAbiVersion() }.getOrNull())
            native.put("available", runCatching { FotufilmNative.nativeAvailable() }.getOrNull())
        }
        report.put("native", native)

        if (!FotufilmNative.loaded) {
            report.put("packs", JSONArray())
            report.put("verdict", "FAIL: libfotufilm_android.so 未载入，无法验证任何引擎行为")
            return report
        }

        val packs = findPacks()
        report.put("packDirectory", packs.directory.absolutePath)
        report.put("packCount", packs.files.size)

        val parity = runParity(packs.files)
        report.put("parity", parity)

        val results = JSONArray()
        for (file in packs.files) {
            results.put(benchmarkPack(file))
        }
        report.put("packs", results)
        report.put("bitmapPath", runBitmapPath(packs.files))

        report.put("verdict", verdictOf(parity, results, report.optJSONObject("bitmapPath")))
        return report
    }

    // --- 各项 ---

    private class PackSet(val directory: File, val files: List<File>)

    /**
     * pack 不放 assets（体积 + 许可口径见 .gitignore 的说明），
     * 由 `adb push` 推到外部私有目录。
     *
     * **这里必须先 `mkdirs()`**：如果目录是 `adb shell mkdir` 建的，属主会是
     * `shell`、模式 `drwxrws---`，本 App 走 FUSE 视角时会被当成"其他身份"而
     * 列不出内容（`listFiles` 返回 null，表现为 packCount=0，不报错）。
     * 让 App 自己建目录，属主才是自己，后续 `adb push` 仍旧能写进去。
     */
    private fun findPacks(): PackSet {
        val directory = File(context.getExternalFilesDir(null), "film")
        if (!directory.isDirectory) directory.mkdirs()
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(".fswp") }
            ?.sortedBy { it.name }
            ?.toList()
            .orEmpty()
        return PackSet(directory, files)
    }

    /**
     * 用最小的 pack 做整帧 vs 分块的交叉验证，并且**用两套不同的带子高度各跑一次**。
     *
     * 两套带子高度是这个探针的关键：如果差异来自接缝（halo 不够、interiorTop 偏移），
     * 差异行会跟着带子边界走 —— 8 行时在 8/16/24…，16 行时在 16/32/48…，两张
     * 差异图的分布必然不同。如果差异来自胶卷颗粒这类逐像素的内容，两张图会高度重合。
     * 只看单次 rms 分不清这两件事，多跑一次就能分。
     */
    private fun runParity(files: List<File>): JSONObject {
        val result = JSONObject()
        // 整帧路径要同时持两套全帧平面，只能挑小图；96×64 那个 pack 正好。
        val small = files.minByOrNull { file ->
            runCatching {
                FilmPack.fromFile(file).use { it.width.toLong() * it.height }
            }.getOrDefault(Long.MAX_VALUE)
        }
        if (small == null) {
            result.put("status", "SKIPPED")
            result.put("reason", "没有可用 pack")
            return result
        }

        // 只带内置 12MP pack 的机器上，最小的 pack 就是 12MP，整帧参考路径跑不了
        // （`FULL_FRAME_PIXEL_LIMIT` 是 4MP）。**这是夹具缺失，不是引擎出错**：
        // 以前这里会抛异常 → verdict 变成 FAIL，用户装完自测看到"失败"完全无从解释（D-078）。
        val pixels = runCatching {
            FilmPack.fromFile(small).use { it.width.toLong() * it.height }
        }.getOrDefault(Long.MAX_VALUE)
        if (pixels > FilmEngine.FULL_FRAME_PIXEL_LIMIT) {
            result.put("status", "SKIPPED")
            result.put("pack", small.name)
            result.put("size", "$pixels px")
            result.put(
                "reason",
                "没有小尺寸夹具 pack：最小的 ${small.name} 是 $pixels 像素，" +
                    "整帧参考路径上限 ${FilmEngine.FULL_FRAME_PIXEL_LIMIT}。" +
                    "把 96×64 的引擎夹具 push 到 files/film/ 后重跑即可。",
            )
            return result
        }

        return runCatching {
            FilmPack.fromFile(small).use { pack ->
                val engine = FilmEngine(pack)
                result.put("pack", small.name)
                result.put("size", "${pack.width}x${pack.height}")
                result.put("maxHalo", pack.maxHalo)

                // 两条路径必须吃同一份程序纹理，否则比出来的差异只反映输入不同。
                val reference = engine.renderFullFrameSynthetic()
                val canvas = Bitmap.createBitmap(pack.width, pack.height, Bitmap.Config.ARGB_8888)

                for ((index, stripRows) in PARITY_STRIP_ROWS.withIndex()) {
                    // 两套带子高度用同一个 canvas 复用，省一次 96×64 的分配。
                    val render = engine.renderSynthetic(destination = canvas, stripRows = stripRows)
                    val diff = compare(reference, canvas)
                    diff.put("stripRows", stripRows)
                    diff.put("strips", render.strips)
                    diff.put("stripMillis", render.totalMillis)
                    diff.put("nonFinite", render.nonFinite)
                    diff.put("analysis", analyzeDiff(diff))
                    result.put(PARITY_RUNS[index], diff)
                }

                result.put("status", "RAN")
                reference.recycle()
                canvas.recycle()
            }
            result
        }.getOrElse { error ->
            result.put("status", "ERROR")
            result.put("reason", error.toString())
            result
        }
    }

    /**
     * 走一遍**产品真实路径**：`Bitmap` 输入 → 分块冲洗 → `Bitmap` 输出。
     *
     * 上面的 [benchmarkPack] 全部用 `SyntheticSource`（直接产出 scene-linear 浮点），
     * 绕过了 `BitmapSource` 与 8 位 sRGB → 线性的解码。那条解码路径才是接上相机之后
     * 真正会走的路，没跑过就等于没验证。这里补上，内容用能产生真实负载的
     * "渐变 + 高光点"（纯平坦色块激不出扩散与晕光）。
     */
    private fun runBitmapPath(files: List<File>): JSONObject {
        val result = JSONObject()
        // 不写死尺寸：pack 尺寸跟着目标机的最大 JPEG 走（PJZ110 是 4096x3072），
        // 换机型后这里若还写 4000x3000 就会静默 SKIPPED。按文件名里的 __WxH 取最大的那卷。
        val sized = files.mapNotNull { file ->
            val spec = file.name.substringAfter("__", "").removeSuffix(".fswp")
            val parts = spec.split('x')
            val width = parts.getOrNull(0)?.toIntOrNull()
            val height = parts.getOrNull(1)?.toIntOrNull()
            if (width != null && height != null) file to width.toLong() * height else null
        }
        // 样张优先**彩色负片**：第一版挑的是"最大的一卷"，在真机上正好选中 acros100，
        // 于是样张是一张灰图 —— 看色彩问题的人拿到黑白样张等于没给（D-078）。
        val candidate = (
            sized.filter { FilmStockCatalog.filmIdOf(it.first.name) in SAMPLE_PREFERRED_STOCKS }
                .maxByOrNull { it.second }
                ?: sized.maxByOrNull { it.second }
            )?.first
            ?: return result.also {
                it.put("status", "SKIPPED")
                it.put("reason", "没有 12MP pack")
            }

        return runCatching {
            FilmPack.fromFile(candidate).use { pack ->
                result.put("pack", candidate.name)
                result.put("size", "${pack.width}x${pack.height}")

                val source = Bitmap.createBitmap(pack.width, pack.height, Bitmap.Config.ARGB_8888)
                fillPhotoLike(source)
                val target = Bitmap.createBitmap(pack.width, pack.height, Bitmap.Config.ARGB_8888)

                // 用这支卷**真实**的交付曲线跑：反转片要 SDR 肩部，负片不要。
                // 之前一律用默认 1.0，等于拿负片的曲线给正片测参数（D-074）。
                val knee = if (FilmStockCatalog.isTransparency(candidate.name)) {
                    LinearColor.KNEE_TRANSPARENCY
                } else {
                    LinearColor.KNEE_BOUNDED
                }
                result.put("shoulderKnee", knee.toDouble())

                val render = FilmEngine(pack, shoulderKnee = knee).render(source, destination = target)
                result.put("totalMillis", render.totalMillis)
                result.put("kernelMillis", render.kernelMillis)
                result.put("encodeMillis", render.encodeMillis)
                result.put("copyMillis", render.copyMillis)
                result.put("statsMillis", render.statsMillis)
                result.put("blitMillis", render.blitMillis)
                result.put("strips", render.strips)
                result.put("peakRssKb", render.peakRssKb)
                result.put("nonFinite", render.nonFinite)
                result.put("min", JSONArray(render.min.map { it.toDouble() }))
                result.put("max", JSONArray(render.max.map { it.toDouble() }))
                result.put("mean", JSONArray(render.mean.map { it.toDouble() }))
                // 输出不能是纯黑或纯白：那说明解码或编码有一端错了（比如把 sRGB 当线性用）。
                result.put("plausible", render.max.all { it > 0.05f } && render.mean.all { it in 0.01f..0.95f })

                // 把这一帧**存成 JPEG**：自测报告只有数字，"观感"得看图。
                // 这张图是"程序纹理 → 解码 → 引擎 → 编码"的完整输出，adb pull 出来就能判断
                // 色彩/颗粒/反差有没有问题，不需要用户先拍一张。
                val sample = File(context.getExternalFilesDir(null), "fotufilm-selftest-render.jpg")
                runCatching {
                    FileOutputStream(sample).use { out ->
                        target.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    result.put("sample", sample.absolutePath)
                }.onFailure { Log.w(TAG, "自测样张写盘失败: ${it.message}") }

                result.put("status", "OK")

                source.recycle()
                target.recycle()
            }
            result
        }.getOrElse { error ->
            result.put("status", "ERROR")
            result.put("reason", error.toString())
            Log.e(TAG, "Bitmap 路径失败", error)
            result
        }
    }

    /** 分块填充一张"像照片"的图：低频渐变 + 高光点 + 轻微棋盘。分块填充避免再要一份 48MB 数组。 */
    private fun fillPhotoLike(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val chunk = 256
        val buffer = IntArray(w * chunk)
        var y = 0
        while (y < h) {
            val rows = minOf(chunk, h - y)
            for (r in 0 until rows) {
                val gy = y + r
                val fy = gy.toFloat() / h
                for (x in 0 until w) {
                    val fx = x.toFloat() / w
                    // 上暗下亮的天空渐变，横向加一点偏色
                    var v = 0.12f + 0.62f * fy * (0.6f + 0.4f * fx)
                    val checker = if (((x ushr 4) + (gy ushr 4)) and 1 == 0) 0.03f else 0f
                    // 高光点：制造 halation 与局部过曝
                    val dx = fx - 0.62f
                    val dy = fy - 0.38f
                    val spot = 1f - (dx * dx + dy * dy) * 260f
                    if (spot > 0f) v += spot * 1.4f
                    val r8 = to8(v)
                    val g8 = to8(v * 0.97f + checker)
                    val b8 = to8(v * 0.88f + checker * 0.6f)
                    buffer[r * w + x] = (0xFF shl 24) or (r8 shl 16) or (g8 shl 8) or b8
                }
            }
            bitmap.setPixels(buffer, 0, w, 0, y, w, rows)
            y += rows
        }
    }

    /** 线性值 → 8 位 sRGB 编码（这里只是造测试图，用近似 gamma 即可）。 */
    private fun to8(linear: Float): Int {
        val clamped = linear.coerceIn(0f, 1f)
        val srgb = if (clamped <= 0.0031308f) {
            clamped * 12.92f
        } else {
            1.055f * Math.pow(clamped.toDouble(), 1.0 / 2.4).toFloat() - 0.055f
        }
        return (srgb * 255f + 0.5f).toInt().coerceIn(0, 255)
    }

    private fun benchmarkPack(file: File): JSONObject {
        val entry = JSONObject()
        entry.put("name", file.name)
        entry.put("bytes", file.length())
        return runCatching {
            FilmPack.fromFile(file).use { pack ->
                entry.put("width", pack.width)
                entry.put("height", pack.height)
                entry.put("maxHalo", pack.maxHalo)
                entry.put("featureMask", pack.featureMask)
                entry.put("seed", pack.seed.toString())

                val engine = FilmEngine(pack)
                entry.put("usable", engine.isUsable())

                val bitmap = Bitmap.createBitmap(pack.width, pack.height, Bitmap.Config.ARGB_8888)
                val render = engine.renderSynthetic(destination = bitmap)
                entry.put("totalMillis", render.totalMillis)
                entry.put("fillMillis", render.fillMillis)
                entry.put("kernelMillis", render.kernelMillis)
                entry.put("encodeMillis", render.encodeMillis)
                entry.put("copyMillis", render.copyMillis)
                entry.put("statsMillis", render.statsMillis)
                entry.put("blitMillis", render.blitMillis)
                entry.put("strips", render.strips)
                entry.put("peakRssKb", render.peakRssKb)

                val stats = JSONObject()
                stats.put("nonFinite", render.nonFinite)
                stats.put("min", JSONArray(render.min.map { it.toDouble() }))
                stats.put("max", JSONArray(render.max.map { it.toDouble() }))
                stats.put("mean", JSONArray(render.mean.map { it.toDouble() }))
                entry.put("output", stats)

                entry.put("status", "OK")
                Log.i(TAG, "${file.name}: ${render.summary()}")
                bitmap.recycle()
            }
            entry
        }.getOrElse { error ->
            entry.put("status", "ERROR")
            entry.put("reason", error.toString())
            Log.e(TAG, "${file.name} 失败", error)
            entry
        }
    }

    // --- 工具 ---

    private fun compare(a: Bitmap, b: Bitmap): JSONObject {
        val result = JSONObject()
        val w = minOf(a.width, b.width)
        val h = minOf(a.height, b.height)
        val pa = IntArray(w * h)
        val pb = IntArray(w * h)
        a.getPixels(pa, 0, w, 0, 0, w, h)
        b.getPixels(pb, 0, w, 0, 0, w, h)
        var sumSq = 0.0
        var worst = 0
        var differing = 0
        // 逐行的差异分布：分辨"颗粒"和"接缝"全靠它。接缝会让某几行的差异突然集中。
        val differingByRow = IntArray(h)
        for (y in 0 until h) {
            var rowDiff = 0
            for (x in 0 until w) {
                val i = y * w + x
                for (shift in intArrayOf(16, 8, 0)) {
                    val d = ((pa[i] shr shift) and 0xFF) - ((pb[i] shr shift) and 0xFF)
                    if (d != 0) {
                        differing++
                        rowDiff++
                    }
                    if (abs(d) > worst) worst = abs(d)
                    sumSq += (d * d).toDouble()
                }
            }
            differingByRow[y] = rowDiff
        }
        result.put("pixels", w * h)
        result.put("channelSamples", pa.size * 3)
        result.put("rms", kotlin.math.sqrt(sumSq / (pa.size * 3)))
        result.put("maxAbsDiff", worst)
        result.put("differingChannels", differing)
        result.put("differingByRow", JSONArray(differingByRow.toList()))
        return result
    }

    private fun deviceInfo(): JSONObject = JSONObject().apply {
        put("manufacturer", Build.MANUFACTURER)
        put("model", Build.MODEL)
        put("device", Build.DEVICE)
        put("sdkInt", Build.VERSION.SDK_INT)
        put("abis", Build.SUPPORTED_ABIS.joinToString(","))
        put("maxHeapMb", Runtime.getRuntime().maxMemory() / (1024 * 1024))
        put("memTotalKb", memInfoValue("MemTotal"))
        put("memAvailableKb", memInfoValue("MemAvailable"))
    }

    private fun memInfoValue(key: String): Long = runCatching {
        File("/proc/meminfo").readLines()
            .firstOrNull { it.startsWith("$key:") }
            ?.trim()?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull() ?: 0L
    }.getOrDefault(0L)

    /**
     * 判断一张差异图里是否存在"接缝热点"。
     *
     * 接缝的特征不是"有差异"，而是**差异集中在某几行**，且这几行正好落在带子边界上
     * （`y % stripRows == 0` 或 `(y+1) % stripRows == 0`）。颗粒是逐像素的，逐行差异
     * 会很均匀，不会出现 3 倍于中位数的尖峰。所以 `hotRowsOnSeam == 0` 就足以排除接缝。
     */
    private fun analyzeDiff(diff: JSONObject): JSONObject {
        val rows = diff.optJSONArray("differingByRow") ?: return JSONObject()
        val stripRows = diff.optInt("stripRows", 0)
        val values = IntArray(rows.length()) { rows.optInt(it) }
        if (values.isEmpty() || stripRows <= 0) return JSONObject()

        val sorted = values.sortedArray()
        val median = sorted[sorted.size / 2]
        val threshold = maxOf(median * 3, 4)
        val hot = values.indices.filter { values[it] > threshold }
        val hotOnSeam = hot.count { it % stripRows == 0 || (it + 1) % stripRows == 0 }

        return JSONObject().apply {
            put("medianRowDiff", median)
            put("hotRowThreshold", threshold)
            put("hotRows", JSONArray(hot.toList()))
            put("hotRowsOnSeam", hotOnSeam)
        }
    }

    private fun verdictOf(parity: JSONObject, results: JSONArray, bitmapPath: JSONObject?): String {
        val failures = ArrayList<String>()
        if (!FotufilmNative.loaded) failures.add("native 库未载入")
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            if (item.optString("status") != "OK") {
                failures.add("${item.optString("name")}: ${item.optString("reason")}")
                continue
            }
            val output = item.optJSONObject("output")
            if (output != null && output.optLong("nonFinite") > 0) {
                failures.add("${item.optString("name")}: 输出含 ${output.optLong("nonFinite")} 个 NaN/Inf")
            }
        }
        if (bitmapPath != null) {
            when (bitmapPath.optString("status")) {
                "OK" -> {
                    if (!bitmapPath.optBoolean("plausible")) {
                        failures.add("Bitmap 路径输出范围异常（纯黑或纯白），sRGB↔线性可能用反了")
                    }
                    // 产品路径（相机帧 → Bitmap → 引擎）的 NaN 以前没人查：
                    // synthetic 那几条是干净的，真机上吐 NaN 的恰恰是这一条（D-074）。
                    val nan = bitmapPath.optLong("nonFinite")
                    if (nan > 0) {
                        failures.add("Bitmap 路径输出含 $nan 个 NaN/Inf（成片会是黑点/白点）")
                    }
                }

                "SKIPPED" -> failures.add("Bitmap 路径未运行：${bitmapPath.optString("reason")}")
                else -> failures.add("Bitmap 路径失败：${bitmapPath.optString("reason")}")
            }
        }
        // 分块交叉验证要一个 96×64 的夹具 pack 才跑得动（整帧参考路径上限 4MP）。
        // 只带内置 12MP pack 的机器上它会 SKIPPED —— 那是"缺夹具"，不是"引擎坏了"，
        // 所以只作为提示附在 verdict 后面，不算失败（D-078）。
        var note: String? = null
        if (parity.optString("status") != "RAN") {
            note = "分块交叉验证未运行（${parity.optString("status")}）：${parity.optString("reason")}"
        } else {
            for (key in PARITY_RUNS) {
                val run = parity.optJSONObject(key) ?: continue
                val label = "${run.optInt("stripRows")} 行带子"
                val maxDiff = run.optInt("maxAbsDiff")
                if (maxDiff > PARITY_MAX_ABS_DIFF) {
                    failures.add("$label 与整帧最大差 $maxDiff 色阶，超过容差 $PARITY_MAX_ABS_DIFF")
                }
                val hotOnSeam = run.optJSONObject("analysis")?.optInt("hotRowsOnSeam") ?: 0
                if (hotOnSeam > 0) {
                    failures.add("$label 有 $hotOnSeam 处差异热点正好落在带子边界 = 接缝")
                }
            }
        }
        return when {
            failures.isNotEmpty() -> "FAIL: ${failures.joinToString("; ")}"
            note != null -> "PASS（$note）"
            else -> "PASS"
        }
    }

    companion object {
        private const val TAG = "FotufilmSelfTest"
        const val REPORT_NAME = "fotufilm-selftest.json"

        /**
         * 样张优先用这些卷（都是**彩色**负片）。
         *
         * 不在这里的卷（黑白、正片）也能跑，只是样张不适合用来看色彩 ——
         * 第一版按"最大的一卷"挑，真机上正好选中 acros100，样张是灰的（D-078）。
         */
        private val SAMPLE_PREFERRED_STOCKS = setOf(
            "gold200", "portra400", "ektar100", "cinestill800t", "ultramax400",
            "colorplus200", "superia200", "superia400", "c200", "pro400h", "reala100",
        )

        /**
         * 两套带子高度。必须互质或至少不互相整除，否则两套的接缝行会重叠，
         * "差异图跟着边界走"这个判据就失效了。8 和 10 的最小公倍数是 40，
         * 在 64 行里只有 40 处重叠，足以区分。
         */
        private val PARITY_STRIP_ROWS = intArrayOf(8, 10)
        private val PARITY_RUNS = arrayOf("primary", "secondary")

        /** 分块与整帧允许的最大色阶差。取 3 是给舍入留余量，不是给几何错误留余量。 */
        private const val PARITY_MAX_ABS_DIFF = 3

        /** 跑完把报告写到外部私有目录，`adb pull` 可读。 */
        fun writeReport(context: Context, report: JSONObject): File {
            val file = File(context.getExternalFilesDir(null), REPORT_NAME)
            file.writeText(report.toString(2))
            return file
        }
    }
}
