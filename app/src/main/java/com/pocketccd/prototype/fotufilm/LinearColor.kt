package com.pocketccd.prototype.fotufilm

import kotlin.math.pow

/**
 * sRGB(8bit) ↔ 引擎工作空间（linear Rec.2020）↔ 输出（sRGB）的完整色彩链。
 *
 * ## 为什么这里有矩阵，而不是"反个 gamma 就行"
 *
 * 上游 `FotufilmCore/ColorScience.swift` 写明：**engine works in linear Rec.2020**，
 * 而印相结果是在 **Display P3** basis 上积出来的（`displayP3LuminanceWeights` 的注释：
 * "The finished print is integrated and output in P3"）。Android 侧拿到的 Bitmap 却是
 * sRGB 编码的 8bit。三方基色彼此不同，中间必须过矩阵：
 *
 * ```text
 * sRGB8 ──srgbToLinear──▶ 线性 sRGB ──linearSRGBToRec2020──▶ Rec.2020（引擎输入）
 *                                       [ 引擎：光谱重建 → 显影 → 印相 ]
 * 引擎输出 = 线性 Display P3 ──linearDisplayP3ToSRGB──▶ 线性 sRGB
 *                            ──linearToSrgb────────────▶ sRGB 编码
 *                            ──三角抖动───────────────▶ 8bit 量化
 * ```
 *
 * **不做这两步会发生什么**：把 sRGB 的线性值直接当 Rec.2020 喂进去，等于宣称"这束光
 * 比实际更纯"——Rec.2020 的基色坐在光谱轨迹上，同一个三元组代表的物理光更饱和。实测
 * 一片天空 `sRGB(122,189,245)`：正确转换后 Rec.2020 是 `(0.325, 0.478, 0.864)`，不做
 * 转换则是 `(0.196, 0.494, 0.912)` —— **R 少了 40%、B 多了 5.6%**，引擎因此认为这束光
 * 比真实更蓝更暗，成片就是"特别亮特别纯的蓝"。输出侧同理：P3 的饱和色装不进 sRGB，
 * 而本文件只做 clip，超色域部分被硬裁。
 *
 * 两个矩阵的错误**只随饱和度放大、不影响中性灰**（两者行和都精确为 1、白点同为 D65），
 * 所以症状集中在天空这类高饱和区域，灰阶反而看不出问题。
 *
 * ## 抖动
 *
 * 上游 `DisplayEncoding.swift`：8bit 输出的定义就是 "the SDR delivery shoulder, the sRGB
 * transfer, and **triangular dither of one quantizer step**"。一个量化步的三角抖动把
 * 色带打散成噪声——大面积平滑渐变（天空、塑料壳、暗部）没有它就是一圈一圈的阶梯。
 * 抖动必须是**确定性**的：同一个 pack seed 冲洗两次要逐像素相同，否则
 * [FilmEngine.renderSynthetic] 的交叉验证会失去意义。
 *
 * ## SDR shoulder（D-067 的第二批）
 *
 * 上游 `FilmSDRDelivery`：**负片/印相**的拐点在显示白（`boundedShoulderKnee = 1`），
 * 曲线退化为纯 clip —— 相纸特性曲线自带肩部，不需要宿主再压一次；**反转片**
 * （`transparencyShoulderKnee = 0.7`）直接观看、没有印相阶段吸收它的物理肩部，
 * 携带超过显示白的光，必须提前滚降，否则高光硬切、多个不同的高光曝光挤在码值 255 附近。
 *
 * 公式逐字抄自上游 `ColorScience.highlightShoulder`：
 *
 * ```text
 * knee = clamp(knee, 0, 1)
 * x <= knee          → x
 * room = 1 - knee
 * room <= 0          → 1                （knee = 1 时退化成 clip）
 * 否则                → knee + room·(x-knee)/((x-knee)+room)
 * ```
 *
 * 曲线在 `knee` 处与恒等线斜率连续（一阶相接），所以拐点本身看不出来。
 *
 * ## 还没做的
 *
 * - **gamut mapping**：P3 → sRGB 溢出时上游把责任留给调用方（"for the caller to
 *   gamut-map or clip"）。这里选 clip —— 它不引入任何创作性决策，但也意味着超色域的
 *   饱和色会丢掉色相梯度。补真正的 mapping 是后续独立决策。
 *
 * 12MP 一帧是 4000×3000×3 ≈ 3600 万次转换，所以反 gamma 走查表而不是 `pow()`。
 * 解码只有 256 种输入，表是精确的；编码是 float → 8bit，用 4096 级表加线性插值。
 */
internal object LinearColor {

    /**
     * 负片 / 印相的拐点：落在显示白上，肩部退化为纯 clip。
     *
     * 这不是"没做"，是上游定义的正确行为 —— 相纸的印相曲线已经把物理肩部吸收掉了，
     * 宿主再压一次等于压两遍。
     */
    const val KNEE_BOUNDED = 1f

    /** 反转片（正片）的拐点：0.7 起开始滚降，把 0.7 以上的高光摊进剩下 30% 的码值。 */
    const val KNEE_TRANSPARENCY = 0.7f

    /**
     * 上游 `ColorScience.highlightShoulder(x, ceiling: 1, knee:)`。
     *
     * 曲线在 `knee` 处与恒等线一阶相接、渐近 1 但不到达 —— 所以拐点本身看不出来，
     * 高光只是"慢下来"而不是"被压平"。
     */
    fun shoulder(x: Float, knee: Float): Float {
        if (knee >= 1f) return x // 退化为 clip，交给 encodeCode 的 clamp
        val k = if (knee < 0f) 0f else knee
        if (x <= k) return x
        val over = x - k
        val room = 1f - k
        return k + room * over / (over + room)
    }

    // --- 基色矩阵（数值逐位抄自上游 ColorScience.swift，不要手改） ---

    /**
     * `linearSRGBToRec2020`。行和精确为 1 → sRGB 白就是工作空间白。
     */
    private val SRGB_TO_REC2020 = floatArrayOf(
        0.627403896f, 0.329283038f, 0.043313066f,
        0.069097289f, 0.919540395f, 0.011362316f,
        0.016391439f, 0.088013308f, 0.895595253f,
    )

    /**
     * `linearDisplayP3ToSRGB`。行和精确为 1，白点同为 D65。
     *
     * 上游注释："Values outside 0...1 are retained for the caller to gamut-map or clip at the
     * delivery boundary." —— 这里 clip。
     */
    private val P3_TO_SRGB = floatArrayOf(
        1.22494018f, -0.224940176f, 0f,
        -0.042056955f, 1.04205695f, 0f,
        -0.019637555f, -0.078636046f, 1.09827360f,
    )

    /** sRGB 8bit → 线性光（IEC 61966-2-1 传递函数）。 */
    private val DECODE = FloatArray(256) { index ->
        val c = index / 255f
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }

    private const val ENCODE_STEPS = 4096

    /** 线性光 → sRGB 8bit 的采样表，值是 0..255 的浮点。 */
    private val ENCODE = FloatArray(ENCODE_STEPS + 1) { index ->
        val c = index / ENCODE_STEPS.toFloat()
        val s = if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f
        s * 255f
    }

    /**
     * 一个量化步的三角抖动（TPDF）：两个均匀分布相减，取值落在 `[-1, 1]`，单位是 8bit 码值。
     *
     * 状态推进用 xorshift32 —— 确定性、无分配、比 `Random` 快一个量级。每次 [offsets]
     * 只走三步，每步的 32 位拆成两个 16 位均匀数（16 位精度对 1 个码值的抖动绰绰有余）。
     *
     * seed = 0 会被换成一个非零常数：xorshift 的零态是吸收态，喂 0 进去永远出 0。
     */
    class Dither(seed: Int) {
        private var state: Int = if (seed == 0) 0x2545F491 else seed

        private fun step(): Int {
            var x = state
            x = x xor (x shl 13)
            x = x xor (x ushr 17)
            x = x xor (x shl 5)
            state = x
            return x
        }

        /** 把三个通道的偏移写进 [out]（长度 ≥ 3），复用数组避免每像素分配。 */
        fun offsets(out: FloatArray) {
            val a = step()
            val b = step()
            val c = step()
            out[0] = ((a ushr 16) - (a and 0xFFFF)) / 65536f
            out[1] = ((b ushr 16) - (b and 0xFFFF)) / 65536f
            out[2] = ((c ushr 16) - (c and 0xFFFF)) / 65536f
        }
    }

    /**
     * 线性光 → sRGB 编码值（0..255 的浮点，未量化）。
     *
     * 先过 SDR 肩部再查表：肩部是"把超过拐点的高光摊开"，发生在反 gamma **之前**
     * （上游 `linearToSrgb(displayShoulder(x, knee))` 的顺序）。[knee] 传
     * [KNEE_BOUNDED] 时 [shoulder] 是恒等变换，等同于直接 clamp。
     *
     * NaN 当 0 处理：引擎在病态输入上可能吐出 NaN，这里静默归零比让整张图变黑更好，
     * 计数由 [FilmEngine] 的 `nonFinite` 负责。
     */
    private fun encodeCode(linear: Float, knee: Float): Float {
        if (linear.isNaN()) return 0f
        val shouldered = shoulder(linear, knee)
        val clamped = if (shouldered < 0f) 0f else if (shouldered > 1f) 1f else shouldered
        val position = clamped * ENCODE_STEPS
        val index = position.toInt()
        if (index >= ENCODE_STEPS) return 255f
        val fraction = position - index
        val low = ENCODE[index]
        return low + (ENCODE[index + 1] - low) * fraction
    }

    /** sRGB 8bit → 线性光。注意：**不含**基色转换，仅供单通道数值参考。 */
    fun decode(srgb8: Int): Float = DECODE[srgb8 and 0xFF]

    /**
     * 线性 Display P3 的三通道 → 打包的 ARGB_8888。
     *
     * [dither] 传 null 就是纯量化（单元测试、逐像素比对用）；正常冲洗必须传，否则大片
     * 渐变会出现色带。[scratch] 是长度 ≥ 3 的复用缓冲。
     */
    fun encodeRgb(
        r: Float,
        g: Float,
        b: Float,
        dither: Dither? = null,
        scratch: FloatArray? = null,
        knee: Float = KNEE_BOUNDED,
    ): Int {
        // Display P3 -> sRGB，在**线性光**空间做。超色域的值先留着，交给编码前的 clamp。
        val sr = P3_TO_SRGB[0] * r + P3_TO_SRGB[1] * g + P3_TO_SRGB[2] * b
        val sg = P3_TO_SRGB[3] * r + P3_TO_SRGB[4] * g + P3_TO_SRGB[5] * b
        val sb = P3_TO_SRGB[6] * r + P3_TO_SRGB[7] * g + P3_TO_SRGB[8] * b

        var cr = encodeCode(sr, knee)
        var cg = encodeCode(sg, knee)
        var cb = encodeCode(sb, knee)

        if (dither != null) {
            val d = scratch ?: FloatArray(3)
            dither.offsets(d)
            cr += d[0]
            cg += d[1]
            cb += d[2]
        }

        return (0xFF shl 24) or
            (quantize(cr) shl 16) or
            (quantize(cg) shl 8) or
            quantize(cb)
    }

    /** 编码值 → 0..255。加 0.5 后截断即四舍五入；抖动会让值越过端点，这里夹回来。 */
    private fun quantize(code: Float): Int {
        val v = code + 0.5f
        return if (v < 0f) 0 else if (v > 255f) 255 else v.toInt()
    }

    /**
     * 把一块 ARGB_8888 像素解码进三个 float 平面。
     *
     * 平面是**分离**存放的（先 R 全块、再 G、再 B），因为 native 侧分别接三个指针；
     * 交错 RGB 会需要一次重排，12MP 下就是白白多走一遍内存。
     *
     * 反 gamma 之后立刻做 sRGB → Rec.2020 的基色转换：引擎的工作空间是 Rec.2020，
     * 少了这一步等于把颜色喂得比真实更饱和（见文件头）。alpha 在这里丢弃。
     */
    fun decodeInto(pixels: IntArray, count: Int, r: FloatArray, g: FloatArray, b: FloatArray) {
        // 矩阵系数提到局部：12MP 循环里每像素读 9 个数组元素是 1 亿次边界检查。
        val m0 = SRGB_TO_REC2020[0]; val m1 = SRGB_TO_REC2020[1]; val m2 = SRGB_TO_REC2020[2]
        val m3 = SRGB_TO_REC2020[3]; val m4 = SRGB_TO_REC2020[4]; val m5 = SRGB_TO_REC2020[5]
        val m6 = SRGB_TO_REC2020[6]; val m7 = SRGB_TO_REC2020[7]; val m8 = SRGB_TO_REC2020[8]
        val table = DECODE

        for (i in 0 until count) {
            val argb = pixels[i]
            val s0 = table[(argb shr 16) and 0xFF]
            val s1 = table[(argb shr 8) and 0xFF]
            val s2 = table[argb and 0xFF]
            r[i] = m0 * s0 + m1 * s1 + m2 * s2
            g[i] = m3 * s0 + m4 * s1 + m5 * s2
            b[i] = m6 * s0 + m7 * s1 + m8 * s2
        }
    }

    /**
     * 三个 float 平面 → ARGB_8888，走完整的输出链（P3 → sRGB 矩阵、sRGB 编码、抖动）。
     *
     * 输出恒为不透明（alpha = 0xFF）。[seed] 决定抖动序列，正常传 pack 的 seed 以保证
     * 同一张图可复现；传 0 会换成固定常数，仍然确定。
     */
    fun encodeInto(
        r: FloatArray,
        g: FloatArray,
        b: FloatArray,
        count: Int,
        pixels: IntArray,
        seed: Int = 0,
        knee: Float = KNEE_BOUNDED,
    ) {
        val m0 = P3_TO_SRGB[0]; val m1 = P3_TO_SRGB[1]; val m2 = P3_TO_SRGB[2]
        val m3 = P3_TO_SRGB[3]; val m4 = P3_TO_SRGB[4]; val m5 = P3_TO_SRGB[5]
        val m6 = P3_TO_SRGB[6]; val m7 = P3_TO_SRGB[7]; val m8 = P3_TO_SRGB[8]
        val dither = Dither(seed)
        val offsets = FloatArray(3)

        for (i in 0 until count) {
            val p3r = r[i]
            val p3g = g[i]
            val p3b = b[i]
            dither.offsets(offsets)
            val cr = encodeCode(m0 * p3r + m1 * p3g + m2 * p3b, knee) + offsets[0]
            val cg = encodeCode(m3 * p3r + m4 * p3g + m5 * p3b, knee) + offsets[1]
            val cb = encodeCode(m6 * p3r + m7 * p3g + m8 * p3b, knee) + offsets[2]
            pixels[i] = (0xFF shl 24) or (quantize(cr) shl 16) or (quantize(cg) shl 8) or quantize(cb)
        }
    }
}
