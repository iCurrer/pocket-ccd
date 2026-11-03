package com.pocketccd.prototype.camera

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 135 半格胶片条的几何计算。**纯 Kotlin，不碰 android.graphics**。
 *
 * ## 两条硬规则（D-084 修正过一次）
 *
 * 1. **齿孔必须在画布的长边上**。第一版把片宽 35mm 当成了画布的短边，横版成片就成了
 *    "左右两列齿孔"—— 一眼就不像胶片。要齿孔落在长边上，"沿片长"必须比片宽 35mm 更长，
 *    所以片头/片尾各留 [EDGE_MARGIN_MM] = 9mm（42mm 的胶片段，中间 24mm 是画面）；
 * 2. **胶片方向跟着照片走**：横幅照片 → 胶片横着走（齿孔上下）；竖幅 → 竖着走（齿孔左右）。
 *    这样照片永远是正的，齿孔永远在长边。
 *
 * ## 坐标约定
 *
 * 长度都以**毫米**定义（真实规格），输出时乘 `pxPerMm`：
 * - `along`：沿片长方向；`across`：跨片宽方向（固定 35mm）。
 */
class FilmEdgeLayout(
    val photoWidth: Int,
    val photoHeight: Int,
) {

    /** 横幅照片 → 胶片横着走；竖幅 → 竖着走。 */
    val filmRunsHorizontally: Boolean = photoWidth > photoHeight

    /**
     * 每毫米多少像素：由照片**短边**决定，因为 135 的画门高度永远是 24mm
     * （全画幅 36×24、半格 18×24 都是 24mm 跨片宽）。这样照片 1:1 贴进画门，
     * 画面能占片宽的 69%，片基不会宽得像块黑板（D-084）。
     */
    val basePxPerMm: Float = min(photoWidth, photoHeight).toFloat() / GATE_ACROSS_MM

    /** 画门沿片长方向的毫米数：由照片长宽比决定（4:3 → 32mm）。 */
    val frameAlongMm: Float = max(photoWidth, photoHeight) / basePxPerMm

    /** 画门跨片宽方向：135 恒为 24mm。 */
    val frameAcrossMm: Float get() = GATE_ACROSS_MM

    val canvasAlongMm: Float get() = frameAlongMm + 2 * EDGE_MARGIN_MM

    val canvasAcrossMm: Float get() = FILM_WIDTH_MM

    val canvasWidthMm: Float
        get() = if (filmRunsHorizontally) canvasAlongMm else canvasAcrossMm

    val canvasHeightMm: Float
        get() = if (filmRunsHorizontally) canvasAcrossMm else canvasAlongMm

    /**
     * 超过 [MAX_PIXELS] 时整张按比例缩小的系数（1f = 不缩）。
     *
     * 一条"齿孔在长边"的胶片段是 42 × 35mm；换算到 12MP 照片的 166.7 px/mm 是
     * 7000 × 5833 ≈ 41MP，位图 160MB。所以这里自动缩到 24MP 以内 ——
     * 12MP 照片会变成约 7.4MP（长边 3067px）。这是"像胶片"的代价（D-084）。
     */
    val scale: Float = run {
        val w = canvasWidthMm * basePxPerMm
        val h = canvasHeightMm * basePxPerMm
        val pixels = w.toDouble() * h
        if (pixels <= MAX_PIXELS) 1f else sqrt(MAX_PIXELS * 0.99 / pixels).toFloat()
    }

    val pxPerMm: Float get() = basePxPerMm * scale

    val canvasWidth: Int get() = (canvasWidthMm * pxPerMm).roundToInt()
    val canvasHeight: Int get() = (canvasHeightMm * pxPerMm).roundToInt()

    /** 片门：照片按比例贴进去。 */
    val gate: Box
        get() {
            val gateWidth = if (filmRunsHorizontally) frameAlongMm else frameAcrossMm
            val gateHeight = if (filmRunsHorizontally) frameAcrossMm else frameAlongMm
            return Box(
                leftMm = (canvasWidthMm - gateWidth) / 2f,
                topMm = (canvasHeightMm - gateHeight) / 2f,
                widthMm = gateWidth,
                heightMm = gateHeight,
            )
        }

    /**
     * 两排齿孔，**贴着画布的两条长边**。
     *
     * 135 的齿孔：沿片长 2.8mm × 跨片宽 1.98mm，节距 4.75mm，距片边 1.5mm。
     */
    fun perforations(): List<Box> {
        val holes = ArrayList<Box>()
        val alongMm = canvasAlongMm
        val count = (alongMm / PERF_PITCH_MM).toInt()
        val start = (alongMm - count * PERF_PITCH_MM) / 2f
        for (index in 0 until count) {
            val offset = start + index * PERF_PITCH_MM + (PERF_PITCH_MM - PERF_LONG_MM) / 2f
            if (filmRunsHorizontally) {
                holes += Box(offset, PERF_INSET_MM, PERF_LONG_MM, PERF_SHORT_MM)
                holes += Box(
                    offset,
                    FILM_WIDTH_MM - PERF_INSET_MM - PERF_SHORT_MM,
                    PERF_LONG_MM,
                    PERF_SHORT_MM,
                )
            } else {
                holes += Box(PERF_INSET_MM, offset, PERF_SHORT_MM, PERF_LONG_MM)
                holes += Box(
                    FILM_WIDTH_MM - PERF_INSET_MM - PERF_SHORT_MM,
                    offset,
                    PERF_SHORT_MM,
                    PERF_LONG_MM,
                )
            }
        }
        return holes
    }

    /** 片边字：印在齿孔与片门之间那条窄带上，沿片长居中。 */
    fun edgeTextBaselineMm(): Pair<Float, Float> {
        val baseline = PERF_INSET_MM + PERF_SHORT_MM + EDGE_TEXT_GAP_MM + EDGE_TEXT_MM
        return if (filmRunsHorizontally) {
            (canvasWidthMm / 2f) to baseline
        } else {
            // 竖片：沿片长是"高"，所以居中轴是 canvasHeight/2；baseline 仍然是距片边。
            // 写反过一次 —— 片边字会跑到画面里（D-084）。
            (canvasHeightMm / 2f) to baseline
        }
    }

    val edgeTextSizePx: Float get() = EDGE_TEXT_MM * pxPerMm

    /**
     * 日期基线：画在 [frame] 内右下角，距边 [DATE_MARGIN_MM]。
     *
     * [frame] 必须显式传：只盖日期时画布就是照片本身，而片门是"照片被缩进胶片条"时才有的坐标。
     */
    fun dateBaseline(frame: FramePx, measuredWidthPx: Float): Pair<Float, Float> {
        val margin = DATE_MARGIN_MM * pxPerMm
        return (frame.right - margin - measuredWidthPx) to (frame.bottom - margin)
    }

    val gatePx: FramePx
        get() {
            val gateBox = gate
            return FramePx(
                left = gateBox.leftMm * pxPerMm,
                top = gateBox.topMm * pxPerMm,
                right = gateBox.rightMm * pxPerMm,
                bottom = gateBox.bottomMm * pxPerMm,
            )
        }

    val dateSizePx: Float get() = DATE_MM * pxPerMm

    data class FramePx(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top

        companion object {
            fun of(width: Int, height: Int) = FramePx(0f, 0f, width.toFloat(), height.toFloat())
        }
    }

    data class Box(val leftMm: Float, val topMm: Float, val widthMm: Float, val heightMm: Float) {
        val rightMm: Float get() = leftMm + widthMm
        val bottomMm: Float get() = topMm + heightMm
    }

    companion object {
        /** 真实 135 片宽。 */
        const val FILM_WIDTH_MM = 35f
        /** 135 画门高度：跨片宽方向永远是 24mm（全画幅、半格都是 24）。 */
        const val GATE_ACROSS_MM = 24f
        /** 片头/片尾留白：必须让"沿片长"超过片宽 35mm，齿孔才会落在画布长边上。 */
        const val EDGE_MARGIN_MM = 2f
        const val PERF_PITCH_MM = 4.75f
        const val PERF_LONG_MM = 2.8f
        const val PERF_SHORT_MM = 1.98f
        const val PERF_INSET_MM = 1.5f
        const val PERF_RADIUS_MM = 0.5f
        const val EDGE_TEXT_MM = 1.15f
        const val EDGE_TEXT_GAP_MM = 0.35f
        const val DATE_MM = 1.5f
        const val DATE_MARGIN_MM = 1.6f
        const val MAX_PIXELS = 24_000_000.0
    }
}
