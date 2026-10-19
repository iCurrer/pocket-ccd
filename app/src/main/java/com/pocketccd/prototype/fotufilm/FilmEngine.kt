package com.pocketccd.prototype.fotufilm

import android.graphics.Bitmap
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 一张 12MP 的 float 平面是 `4096 × 3072 × 4 = 48MB`，RGB 三个平面就是 144MB。
 * 输入输出各一份、再加 Halide 自己的 scratch，整帧做法在真机上会直接撞 LMK
 * （实测设备 12GB 内存常态只剩 2.4GB 可用）。所以这里一律走**行带分块**：
 * 每次只让一小段行驻留，处理完立刻编码进目标 Bitmap。
 *
 * 分块的正确性全押在 halo 上：扩散（diffusion）、晕光（halation）、MTF 都是有
 * 物理半径的卷积，一条带子如果只看自己那 128 行，接缝处会明显断裂。halo 由
 * [FilmPack.maxHalo] 给出，它在 native 侧用与内核**同一个** `ResolvedFrameParams`
 * 推出来，不是宿主重算的一套公式。
 */
class FilmEngine(private val pack: FilmPack) {

    /** 引擎是否真的能跑。库没载入或内核不可用时返回 false，调用方应降级而不是崩。 */
    fun isUsable(): Boolean =
        FotufilmNative.loaded && runCatching { FotufilmNative.nativeAvailable() }.getOrDefault(false)

    /**
     * 把 [source] 冲洗并印相到 [destination]。
     *
     * 尺寸必须与 pack 声明的一致（D-037）：pack 里的空间参数是"毫米 × pixels-per-mm"，
     * 换尺寸等于换了光学系统，效果会整体跑偏。所以这里宁可抛异常也不静默缩放。
     *
     * @param stripRows 每条带子真正输出的行数。越小峰值内存越低、但 halo 重叠越多、
     *   总耗时越高。128 行在 halo 几十像素时重叠率约 30%，是内存与速度的折中。
     */
    fun render(
        source: Bitmap,
        destination: Bitmap? = null,
        stripRows: Int = DEFAULT_STRIP_ROWS,
    ): RenderResult {
        require(FotufilmNative.loaded) { "libfotufilm_android.so 未加载" }
        require(source.width == pack.width && source.height == pack.height) {
            "输入 ${source.width}x${source.height} 与 pack 的 ${pack.width}x${pack.height} 不一致"
        }
        val target = destination ?: Bitmap.createBitmap(
            pack.width, pack.height, Bitmap.Config.ARGB_8888,
        )
        require(target.width == pack.width && target.height == pack.height) { "目标画布尺寸不符" }
        require(!target.isRecycled) { "目标画布已回收" }

        return render(BitmapSource(source), target, stripRows, source.width, source.height)
    }

    /**
     * 用确定性程序纹理跑一遍，不依赖相机。用于真机基准：内容、尺寸、种子全固定，
     * 两次运行可以逐像素比对，也能量出稳定的耗时。
     */
    fun renderSynthetic(
        destination: Bitmap? = null,
        stripRows: Int = DEFAULT_STRIP_ROWS,
        textureSeed: Int = 1,
    ): RenderResult {
        require(FotufilmNative.loaded) { "libfotufilm_android.so 未加载" }
        val target = destination ?: Bitmap.createBitmap(
            pack.width, pack.height, Bitmap.Config.ARGB_8888,
        )
        return render(
            SyntheticSource(pack.width, pack.height, textureSeed),
            target, stripRows, pack.width, pack.height,
        )
    }

    /**
     * 整帧一次性处理，只用于小图交叉验证 —— 它要同时持有输入和输出两套全帧
     * float 平面，12MP 下是 274MB。超过 [FULL_FRAME_PIXEL_LIMIT] 直接拒绝，
     * 免得"验证代码"自己先把内存吃光。
     */
    fun renderFullFrame(source: Bitmap, destination: Bitmap? = null): Bitmap {
        require(source.width == pack.width && source.height == pack.height) { "输入与 pack 尺寸不一致" }
        return renderFullFrameFrom(BitmapSource(source), pack.width, pack.height, destination)
    }

    /**
     * 与 [renderSynthetic] **吃同一份程序纹理**，但走整帧路径。
     * 交叉验证要用这个，否则两条路径的输入内容不同，比出来的差异没有意义。
     */
    fun renderFullFrameSynthetic(destination: Bitmap? = null, textureSeed: Int = 1): Bitmap =
        renderFullFrameFrom(
            SyntheticSource(pack.width, pack.height, textureSeed),
            pack.width, pack.height, destination,
        )

    private fun renderFullFrameFrom(
        source: RgbSource,
        width: Int,
        height: Int,
        destination: Bitmap?,
    ): Bitmap {
        require(FotufilmNative.loaded) { "libfotufilm_android.so 未加载" }
        val pixels = width.toLong() * height
        require(pixels <= FULL_FRAME_PIXEL_LIMIT) {
            "整帧路径只用于验证，$pixels 像素超过上限 $FULL_FRAME_PIXEL_LIMIT"
        }

        val w = width
        val h = height
        val count = w * h
        val staging = Array(3) { FloatArray(count) }
        source.read(0, h, staging[0], staging[1], staging[2])

        val input = Array(3) { directFloats(count) }
        val output = Array(3) { directFloats(count) }
        for (c in 0..2) {
            input[c].asFloatBuffer().put(staging[c], 0, count)
            output[c].asFloatBuffer().clear()
        }

        val status = FotufilmNative.nativeProcess(
            pack.nativeHandle,
            input[0], input[1], input[2],
            output[0], output[1], output[2],
            w, h, USE_PACK_MASK, USE_PACK_SEED,
        )
        check(status == FotufilmNative.OK) { "整帧处理失败：${FotufilmNative.describe(status)}" }

        for (c in 0..2) {
            val buffer = output[c].asFloatBuffer()
            buffer.position(0)
            buffer.get(staging[c], 0, count)
        }
        val encoded = IntArray(count)
        LinearColor.encodeInto(staging[0], staging[1], staging[2], count, encoded)

        val target = destination ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        target.setPixels(encoded, 0, w, 0, 0, w, h)
        return target
    }

    // --- 内部实现 ---

    private fun render(
        source: RgbSource,
        target: Bitmap,
        stripRows: Int,
        width: Int,
        height: Int,
    ): RenderResult {
        require(stripRows >= 1) { "stripRows 必须 >= 1" }

        val halo = pack.maxHalo
        // halo 是 pack 的属性，不受我们控制。极端情况下 `stripRows + 2*halo` 会把
        // 一条带子撑到比整帧还高，缓冲区反而成了内存炸弹。这里按一条带子 12 份
        // 平面缓冲（输入/输出各 3 个 FloatArray + 3 个 direct ByteBuffer）估预算，
        // 超了就砍 stripRows —— halo 是下限，最终 tileRows 至少是 2*halo+1。
        var effectiveStripRows = stripRows
        while (effectiveStripRows > 8 &&
            estimatedTileBytes(width, effectiveStripRows, halo) > TILE_BUDGET_BYTES
        ) {
            effectiveStripRows /= 2
        }

        val maxTileRows = minOf(height, effectiveStripRows + 2 * halo)
        val maxTileFloats = width.toLong() * maxTileRows
        require(maxTileFloats * 4 <= Int.MAX_VALUE) { "单条带子的浮点平面超出 int 可寻址范围" }

        val tileFloats = (width * maxTileRows)
        val inStaging = Array(3) { FloatArray(tileFloats) }
        val outStaging = Array(3) { FloatArray(tileFloats) }
        val inBuffers = Array(3) { directFloats(tileFloats) }
        val outBuffers = Array(3) { directFloats(tileFloats) }

        val rssBefore = readPeakRssKb()
        var peakRss = rssBefore

        var fillNanos = 0L
        var kernelNanos = 0L
        var encodeNanos = 0L
        var copyNanos = 0L
        var statsNanos = 0L
        var blitNanos = 0L
        var strips = 0
        // 复用同一条像素缓冲：每条带子新分配 2MB 会让 GC 在 12MP 下不停工作。
        val pixelBuffer = IntArray(width * effectiveStripRows)
        var nonFinite = 0L
        var minR = Float.MAX_VALUE
        var minG = Float.MAX_VALUE
        var minB = Float.MAX_VALUE
        var maxR = -Float.MAX_VALUE
        var maxG = -Float.MAX_VALUE
        var maxB = -Float.MAX_VALUE
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0

        val started = System.nanoTime()

        var top = 0
        while (top < height) {
            val interiorRows = minOf(effectiveStripRows, height - top)
            // 上下各留 halo；图像边界处直接截断，不复制边缘行 —— native 那边
            // 本来就把输入块的边界当作图像边界做延拓，这里再加一层会算重。
            val tileTop = maxOf(0, top - halo)
            val tileBottom = minOf(height, top + interiorRows + halo)
            val tileRows = tileBottom - tileTop
            val interiorTop = top - tileTop

            val fillStart = System.nanoTime()
            source.read(tileTop, tileRows, inStaging[0], inStaging[1], inStaging[2])
            val used = width * tileRows
            for (c in 0..2) {
                val buffer = inBuffers[c].asFloatBuffer()
                buffer.clear()
                buffer.put(inStaging[c], 0, used)
                outBuffers[c].asFloatBuffer().clear()
            }
            fillNanos += System.nanoTime() - fillStart

            val kernelStart = System.nanoTime()
            // 输出画布就取这条带子自己（origin 全 0），这样不必为了"写入正确位置"
            // 而持有一份全帧输出平面 —— 那是 144MB × 1 的纯浪费。
            val status = FotufilmNative.nativeProcessStrip(
                pack.nativeHandle,
                inBuffers[0], inBuffers[1], inBuffers[2],
                outBuffers[0], outBuffers[1], outBuffers[2],
                width, tileRows,
                width, tileRows,
                0, 0,
                interiorTop, interiorRows,
                USE_PACK_MASK, USE_PACK_SEED,
            )
            check(status == FotufilmNative.OK) { "第 $strips 条带子处理失败：${FotufilmNative.describe(status)}" }
            kernelNanos += System.nanoTime() - kernelStart

            val encodeStart = System.nanoTime()
            val copyStart = encodeStart
            for (c in 0..2) {
                val buffer = outBuffers[c].asFloatBuffer()
                buffer.position(0)
                buffer.get(outStaging[c], 0, used)
            }
            copyNanos += System.nanoTime() - copyStart

            val statsStart = System.nanoTime()
            val planeCount = width * interiorRows
            val offset = interiorTop * width
            val outR = outStaging[0]
            val outG = outStaging[1]
            val outB = outStaging[2]
            // 统计与编码合并成一次遍历：12MP 下多走一遍平面就是多一次 45MB 的内存往返。
            // pixels 复用到分配在循环外的缓冲区，避免每条带子都新分配 2MB 触发 GC。
            val pixels = pixelBuffer
            for (i in 0 until planeCount) {
                val r = outR[offset + i]
                val g = outG[offset + i]
                val b = outB[offset + i]
                if (!r.isFinite() || !g.isFinite() || !b.isFinite()) {
                    nonFinite++
                } else {
                    if (r < minR) minR = r
                    if (r > maxR) maxR = r
                    if (g < minG) minG = g
                    if (g > maxG) maxG = g
                    if (b < minB) minB = b
                    if (b > maxB) maxB = b
                    sumR += r
                    sumG += g
                    sumB += b
                }
                pixels[i] = (0xFF shl 24) or
                    (LinearColor.encode(r) shl 16) or
                    (LinearColor.encode(g) shl 8) or
                    LinearColor.encode(b)
            }
            statsNanos += System.nanoTime() - statsStart

            val blitStart = System.nanoTime()
            target.setPixels(pixels, 0, width, 0, top, width, interiorRows)
            blitNanos += System.nanoTime() - blitStart
            encodeNanos += System.nanoTime() - encodeStart

            peakRss = maxOf(peakRss, readPeakRssKb())
            strips++
            top += interiorRows
        }

        return RenderResult(
            width = width,
            height = height,
            strips = strips,
            totalMillis = (System.nanoTime() - started) / 1_000_000,
            fillMillis = fillNanos / 1_000_000,
            kernelMillis = kernelNanos / 1_000_000,
            encodeMillis = encodeNanos / 1_000_000,
            copyMillis = copyNanos / 1_000_000,
            statsMillis = statsNanos / 1_000_000,
            blitMillis = blitNanos / 1_000_000,
            peakRssKb = peakRss,
            rssBeforeKb = rssBefore,
            nonFinite = nonFinite,
            min = floatArrayOf(minR, minG, minB),
            max = floatArrayOf(maxR, maxG, maxB),
            mean = floatArrayOf(
                safeMean(sumR, width.toLong() * height - nonFinite),
                safeMean(sumG, width.toLong() * height - nonFinite),
                safeMean(sumB, width.toLong() * height - nonFinite),
            ),
        )
    }

    /** 全帧都是 NaN 时分母会归零，这时报 0 而不是 Infinity。 */
    private fun safeMean(sum: Double, count: Long): Float =
        if (count <= 0L) 0f else (sum / count).toFloat()

    /** 输入源：按行区间把一块像素填进三个分离的 float 平面。 */
    private interface RgbSource {
        fun read(tileTop: Int, rows: Int, r: FloatArray, g: FloatArray, b: FloatArray)
    }

    private inner class BitmapSource(private val bitmap: Bitmap) : RgbSource {
        private var scratch = IntArray(0)

        override fun read(tileTop: Int, rows: Int, r: FloatArray, g: FloatArray, b: FloatArray) {
            val need = bitmap.width * rows
            if (scratch.size < need) scratch = IntArray(need)
            bitmap.getPixels(scratch, 0, bitmap.width, 0, tileTop, bitmap.width, rows)
            LinearColor.decodeInto(scratch, need, r, g, b)
        }
    }

    /**
     * 确定性程序纹理：低频渐变 + 高频棋盘 + 少量过曝高光。
     * 不用纯渐变，因为扩散/晕光在平坦区域几乎看不出来，测不出真实负载。
     */
    private class SyntheticSource(
        private val width: Int,
        private val height: Int,
        seed: Int,
    ) : RgbSource {
        private val phase = seed * 37

        override fun read(tileTop: Int, rows: Int, r: FloatArray, g: FloatArray, b: FloatArray) {
            var index = 0
            for (y in 0 until rows) {
                val gy = tileTop + y
                val fy = gy.toFloat() / height
                for (x in 0 until width) {
                    val fx = x.toFloat() / width
                    val base = 0.02f + 0.55f * fx * (0.4f + 0.6f * fy)
                    val checker = if (((x ushr 3) + (gy ushr 3) + phase) and 1 == 0) 0.10f else 0.0f
                    // 少量高位：制造 halation（高光溢出）的激发条件
                    val specular = if ((x * 31 + gy * 17 + phase) % 997 == 0) 2.5f else 0f
                    r[index] = base + checker + specular
                    g[index] = base * 0.92f + checker * 0.85f + specular
                    b[index] = base * 0.78f + checker * 0.7f + specular * 0.6f
                    index++
                }
            }
        }
    }

    companion object {
        const val DEFAULT_STRIP_ROWS = 128
        const val FULL_FRAME_PIXEL_LIMIT = 4_000_000L

        /** 一条带子允许占用的平面缓冲上限。256MB 在 12GB 设备上仍有足够余量。 */
        private const val TILE_BUDGET_BYTES = 256L * 1024 * 1024

        private const val USE_PACK_MASK = -1
        private const val USE_PACK_SEED = -1

        /** 一条带子的缓冲总量估算：6 个 FloatArray + 6 个 direct buffer，共 12 份平面。 */
        private fun estimatedTileBytes(width: Int, stripRows: Int, halo: Int): Long =
            width.toLong() * (stripRows + 2 * halo).toLong() * 4L * 12L

        private fun directFloats(floats: Int): ByteBuffer =
            ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder())

        /** `/proc/self/status` 的 VmHWM，进程生命周期内的常驻内存峰值（KB）。 */
        internal fun readPeakRssKb(): Long = runCatching {
            File("/proc/self/status").readLines()
                .firstOrNull { it.startsWith("VmHWM:") }
                ?.trim()?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull() ?: 0L
        }.getOrDefault(0L)
    }
}

/** 一次渲染的耗时、内存与输出统计。 */
data class RenderResult(
    val width: Int,
    val height: Int,
    val strips: Int,
    val totalMillis: Long,
    val fillMillis: Long,
    val kernelMillis: Long,
    val encodeMillis: Long,
    /** 编码阶段内部拆分：direct buffer → FloatArray 的拷贝。 */
    val copyMillis: Long,
    /** 编码阶段内部拆分：统计 + sRGB 编码的逐像素遍历。 */
    val statsMillis: Long,
    /** 编码阶段内部拆分：`Bitmap.setPixels` 写回画布。 */
    val blitMillis: Long,
    /** 进程峰值常驻内存（KB），来自 `/proc/self/status` 的 VmHWM。 */
    val peakRssKb: Long,
    val rssBeforeKb: Long,
    /** 输出平面里 NaN/Inf 的个数。正常应为 0。 */
    val nonFinite: Long,
    val min: FloatArray,
    val max: FloatArray,
    val mean: FloatArray,
) {
    fun summary(): String = buildString {
        append("${width}x$height in ${totalMillis}ms")
        append("（填充 ${fillMillis} / 内核 ${kernelMillis} / 编码 ${encodeMillis}")
        append("：拷贝 ${copyMillis} + 逐像素 ${statsMillis} + 写回 ${blitMillis}）")
        append("，${strips} 条带子，峰值 RSS ${peakRssKb / 1024}MB")
        if (nonFinite > 0) append("，非有限值 $nonFinite 个")
    }
}
