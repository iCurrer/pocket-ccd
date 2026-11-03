package com.pocketccd.prototype.camera

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 把"片边 / 日期后背"烧进成片。
 *
 * ## 为什么不是"画个框 + 写行字"
 *
 * 第一版就是这么干的，看着很假。真实胶片上这两样东西都**不是图形**，是**曝光**：
 *
 * - 日期是 5×7 点阵的后背透过琥珀色滤片打在负片上，每个点都有一圈晕光，
 *   显影后边缘是软的、颜色会被胶片染；
 * - 片边是胶片本身：片基有厚度和轻微不匀，齿孔是打穿的（边缘有光渗），
 *   片边字是厂商用点阵/凸版印上去的，墨有扩散。
 *
 * 所以这里照上游（SpektraLab `overscan.cpp` + `imprint_raster`）的**手法**做：
 * 先算**覆盖率掩膜**，再模糊，最后按"曝光"合成 —— 而不是直接画实心字。
 * 上游把掩膜喂进引擎按胶片感光度积分（我们插不进去，引擎是黑盒），
 * 这里退一步，在成品上模拟同样的视觉结果。
 */
internal object ImprintRenderer {

    private const val TAG = "Imprint"

    // 片基：近黑但带一点暖，纯黑会显得像 UI 边框而不是塑料片基。
    private const val COLOR_BASE = 0xFF17130F.toInt()
    private const val COLOR_BASE_LIGHT = 0xFF241D16.toInt()
    private const val COLOR_HOLE = 0xFF000000.toInt()
    private const val COLOR_HOLE_BLEED = 0xFF52381C.toInt()
    private const val COLOR_EDGE_TEXT = 0xFF8B8377.toInt()
    private const val COLOR_EDGE_PRINT = 0xFFE8873C.toInt()
    private const val EDGE_PRINT_MM = 1.6f
    private const val COLOR_DATE = 0xFFFFA23C.toInt()

    /** 日期合成强度：不是 100%，留一点底下的画面透出来才像"曝"上去的。 */
    private const val DATE_ALPHA = 0.88f

    /**
     * 返回**最终**要落盘的位图。
     *
     * 可能是入参本身（只盖日期且入参本来就可变），也可能是新位图（合成片边）。
     * 调用方负责：如果返回的不是入参，就用完回收；入参由调用方自行回收。
     */
    fun apply(photo: Bitmap, capturedAt: Long, settings: ImprintSettings): Bitmap {
        if (!settings.any) return photo
        val dateText = if (settings.dateBack) DateStamp.text(capturedAt, settings.dateOrder) else null
        return if (settings.filmEdge) {
            composeFilmEdge(photo, dateText)
        } else if (dateText != null) {
            stampDate(photo, dateText)
        } else {
            photo
        }
    }

    // ------------------------------------------------------------------
    // 日期（只盖章，不合成片边）
    // ------------------------------------------------------------------

    private fun stampDate(photo: Bitmap, text: String): Bitmap {
        val target = if (photo.isMutable) photo else photo.copy(Bitmap.Config.ARGB_8888, true)
        if (!target.isMutable) return photo // 连 copy 都给不出可变位图，宁可不盖

        val layout = FilmEdgeLayout(target.width, target.height)
        // 只盖日期时画布就是照片本身，日期贴**照片**右下角（不是胶片条片门的坐标）。
        drawDate(
            canvas = Canvas(target),
            layout = layout,
            frame = FilmEdgeLayout.FramePx.of(target.width, target.height),
            text = text,
        )
        return target
    }

    // ------------------------------------------------------------------
    // 片边（135 半格胶片条）
    // ------------------------------------------------------------------

    private fun composeFilmEdge(photo: Bitmap, dateText: String?): Bitmap {
        val layout = FilmEdgeLayout(photo.width, photo.height)
        val canvasBitmap = Bitmap.createBitmap(
            layout.canvasWidth,
            layout.canvasHeight,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(canvasBitmap)
        val pxPerMm = layout.pxPerMm

        drawFilmBase(canvas, layout)
        drawGate(canvas, layout, photo)
        drawPerforations(canvas, layout)
        drawEdgeText(canvas, layout)

        if (dateText != null) {
            drawDate(canvas, layout, layout.gatePx, dateText)
        }
        return canvasBitmap
    }

    /**
     * 片基：底色 + 一层极轻的噪点。
     *
     * 纯色填充是"假"的头号来源 —— 真实片基有厚度、有轻微不匀和灰尘。
     * 噪点用 128×128 的瓦片平铺（`BitmapShader`），不逐像素算，23MP 也不慢。
     */
    private fun drawFilmBase(canvas: Canvas, layout: FilmEdgeLayout) {
        val rect = RectF(0f, 0f, layout.canvasWidth.toFloat(), layout.canvasHeight.toFloat())
        canvas.drawRect(rect, Paint().apply { color = COLOR_BASE })

        // 片基中央比边缘略亮一点：模拟透射光下的片基厚度。
        canvas.drawRect(
            rect,
            Paint().apply {
                shader = android.graphics.LinearGradient(
                    rect.left, rect.top, rect.left, rect.bottom,
                    intArrayOf(
                        COLOR_BASE,
                        COLOR_BASE_LIGHT,
                        COLOR_BASE,
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
                alpha = 150
            },
        )

        canvas.drawRect(
            rect,
            Paint().apply {
                shader = BitmapShader(noiseTile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                alpha = 26
            },
        )
    }

    private var noise: Bitmap? = null

    /** 确定性噪点（固定种子）：同一张照片每次渲染都一样，测试可复现。 */
    private fun noiseTile(): Bitmap = noise ?: run {
        val size = 128
        val random = Random(0x5EED)
        val pixels = IntArray(size * size) { index ->
            // 只动亮度、不动色相：白/黑交替的半透明点
            val v = random.nextInt(0, 2)
            if (v == 0) 0x40FFFFFF else 0x00000000
        }
        noise = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        noise!!
    }

    private fun drawGate(canvas: Canvas, layout: FilmEdgeLayout, photo: Bitmap) {
        val gate = layout.gatePx
        val sameSize = (gate.width.roundToInt() == photo.width) &&
            (gate.height.roundToInt() == photo.height)
        canvas.drawBitmap(
            photo,
            Rect(0, 0, photo.width, photo.height),
            RectF(gate.left, gate.top, gate.right, gate.bottom),
            Paint().apply { isFilterBitmap = !sameSize; isAntiAlias = !sameSize },
        )
        // 片门边缘的一圈暗：感光乳剂在画门边上会有一点点遮光，纯直角看着像抠图。
        canvas.drawRect(
            RectF(gate.left, gate.top, gate.right, gate.bottom),
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = max(1f, 0.12f * layout.pxPerMm)
                color = 0x55000000
                isAntiAlias = true
            },
        )
    }

    /**
     * 齿孔：先铺一圈"光渗"（孔边缘的光在片基里散开），再打穿成黑。
     *
     * 每个孔的位置有亚毫米级抖动（真实打孔机不是机床），抖动由序号哈希决定 ——
     * 同一张图每次渲染一致，不会让两张照片长得不一样。
     */
    private fun drawPerforations(canvas: Canvas, layout: FilmEdgeLayout) {
        val pxPerMm = layout.pxPerMm
        val bleed = Paint().apply { color = COLOR_HOLE_BLEED; isAntiAlias = true }
        val hole = Paint().apply { color = COLOR_HOLE; isAntiAlias = true }
        val radius = FilmEdgeLayout.PERF_RADIUS_MM * pxPerMm

        layout.perforations().forEachIndexed { index, box ->
            val jitter = jitterMm(index)
            val inflate = 0.06f
            val rect = RectF(
                (box.leftMm + jitter.first - inflate) * pxPerMm,
                (box.topMm + jitter.second - inflate) * pxPerMm,
                (box.rightMm + jitter.first + inflate) * pxPerMm,
                (box.bottomMm + jitter.second + inflate) * pxPerMm,
            )
            canvas.drawRoundRect(rect, radius, radius, bleed)

            val holeRect = RectF(
                (box.leftMm + jitter.first) * pxPerMm,
                (box.topMm + jitter.second) * pxPerMm,
                (box.rightMm + jitter.first) * pxPerMm,
                (box.bottomMm + jitter.second) * pxPerMm,
            )
            canvas.drawRoundRect(holeRect, radius, radius, hole)
        }
    }

    /** 亚毫米抖动：确定性哈希，范围 ±0.06mm。 */
    private fun jitterMm(index: Int): Pair<Float, Float> {
        val hash = index * 2654435761L
        val dx = ((hash and 0xFF).toFloat() / 255f - 0.5f) * 0.12f
        val dy = (((hash shr 8) and 0xFF).toFloat() / 255f - 0.5f) * 0.12f
        return dx to dy
    }

    /**
     * 片边字。没有 stock 参数，所以用中性字：真实胶片这一侧印的是厂商与型号，
     * 编造会变成假信息（按卷印是 v2，要一张 stock → 片边字的表）。
     */
    private fun drawEdgeText(canvas: Canvas, layout: FilmEdgeLayout) {
        val paint = Paint().apply {
            color = COLOR_EDGE_PRINT
            textSize = EDGE_PRINT_MM * layout.pxPerMm
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.BOLD,
            )
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
        // 参考真实片基：片边字压在左上角（竖片时贴在左边），不是居中。
        val text = "POCKET 135"
        val pxPerMm = layout.pxPerMm
        val margin = 2.2f * pxPerMm
        // 基线压在片门上方、齿孔下方那 2mm 里（再高就压到画面上了）
        val baselineMm = 5.35f
        if (layout.filmRunsHorizontally) {
            canvas.drawText(text, margin, baselineMm * pxPerMm, paint)
        } else {
            canvas.save()
            canvas.rotate(-90f)
            canvas.drawText(text, -layout.canvasHeight + margin, baselineMm * pxPerMm, paint)
            canvas.restore()
        }
    }

    /** 底部那条"条码"：真实片基上的 DX 码，用确定性伪随机画，纯粹为了像。 */
    private fun drawBarcode(canvas: Canvas, layout: FilmEdgeLayout) {
        val paint = Paint().apply { color = COLOR_EDGE_PRINT; isAntiAlias = false }
        val pxPerMm = layout.pxPerMm
        val random = Random(0xB4C0DE)
        val barMm = 0.16f
        val heightMm = 1.6f
        val startMm = 3f
        var cursor = startMm
        val limit = layout.canvasAlongMm - 6f
        val top = (layout.canvasAcrossMm - FilmEdgeLayout.PERF_INSET_MM - heightMm) * pxPerMm
        while (cursor < limit) {
            val widthBars = random.nextInt(1, 4)
            for (index in 0 until widthBars) {
                val x = cursor * pxPerMm
                if (layout.filmRunsHorizontally) {
                    canvas.drawRect(x, top, x + barMm * pxPerMm, top + heightMm * pxPerMm, paint)
                } else {
                    canvas.drawRect(top, x, top + heightMm * pxPerMm, x + barMm * pxPerMm, paint)
                }
                cursor += barMm
            }
            cursor += barMm * (1 + random.nextInt(0, 3))
        }
    }
    // ------------------------------------------------------------------
    // 日期：点阵掩膜 → 模糊 → 曝光合成
    // ------------------------------------------------------------------

    private fun drawDate(
        canvas: Canvas,
        layout: FilmEdgeLayout,
        frame: FilmEdgeLayout.FramePx,
        text: String,
    ) {
        val mask = renderDateMask(text, layout.dateSizePx) ?: return
        val (x, baseline) = layout.dateBaseline(frame, mask.width.toFloat())
        val top = baseline - mask.height

        // 1) 晕光：孔/点的光在乳剂里散开，比本体大一圈、很淡
        val glow = mask.copy(Bitmap.Config.ARGB_8888, true)
        blurAlpha(glow, radius = max(3, (layout.dateSizePx * 0.07f).roundToInt()), passes = 2)
        canvas.drawBitmap(glow, x, top, tintedPaint(alpha = 0.22f))
        glow.recycle()

        // 2) 本体：已经模糊过一次，再按 88% 合成 —— 留一点画面透出来
        canvas.drawBitmap(mask, x, top, tintedPaint(alpha = DATE_ALPHA))
        mask.recycle()
    }

    private fun tintedPaint(alpha: Float): Paint = Paint().apply {
        colorFilter = PorterDuffColorFilter(COLOR_DATE, PorterDuff.Mode.SRC_IN)
        this.alpha = (255 * alpha).roundToInt().coerceIn(0, 255)
        isFilterBitmap = false
    }

    /**
     * 把文本排成 5×7 点阵掩膜（白点 + 透明底），再轻微模糊。
     *
     * 模糊半径取 0.06mm：这是"点边缘的光渗"的量级，再大就不像打上去的了。
     */
    private fun renderDateMask(text: String, textSizePx: Float): Bitmap? {
        val dot = textSizePx / DotMatrixFont.ROWS
        // 点与点之间留 18% 的缝：真点阵就是这个密度
        val step = dot * 1.12f
        val columns = text.length * (DotMatrixFont.COLUMNS + 1) - 1
        if (columns <= 0) return null
        val width = (columns * step + dot).toInt()
        val height = (DotMatrixFont.ROWS * step).toInt()
        if (width <= 0 || height <= 0) return null

        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        val dotPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            isAntiAlias = true
        }
        val radius = dot * 0.18f
        text.forEachIndexed { index, character ->
            val originX = index * (DotMatrixFont.COLUMNS + 1) * step
            DotMatrixFont.forEachDot(character) { column, row ->
                val left = originX + column * step
                val top = row * step
                canvas.drawRoundRect(
                    left, top, left + dot, top + dot, radius, radius, dotPaint,
                )
            }
        }
        // 模糊半径只有点直径的 14%：点阵的每个点必须还能看出是方的，\n        // 一溶就成了橙色糊块（第一版就是这样，实测很难看）。\n        blurAlpha(mask, radius = max(1, (dot * 0.14f).roundToInt()), passes = 1)
        return mask
    }

    /**
     * 只模糊 alpha 通道的可分离盒式模糊（三次近似高斯）。
     *
     * 掩膜只有日期那么大（几十万像素），所以直接算；不用 `BlurMaskFilter` 是因为它
     * 依赖渲染后端，Robolectric 与真机行为不完全一致，而这个必须两处一致。
     */
    private fun blurAlpha(bitmap: Bitmap, radius: Int, passes: Int) {
        if (radius <= 0) return
        val width = bitmap.width
        val height = bitmap.height
        var alpha = IntArray(width * height)
        bitmap.getPixels(alpha, 0, width, 0, 0, width, height)
        val buffer = IntArray(alpha.size)
        repeat(passes) {
            blurRows(alpha, buffer, width, height, radius)
            blurColumns(buffer, alpha, width, height, radius)
        }
        // 两趟之后 alpha 已经在高字节里（每次写回都是 `(均值 shl 24) or 0x00FFFFFF`），
        // 直接写回。第一版这里又 `shl 24` 了一次，等于把整个掩膜变成不透明满色块 ——
        // 日期渲染成了一个橙色矩形（D-081 第二版修正）。
        bitmap.setPixels(alpha, 0, width, 0, 0, width, height)
    }

    private fun blurRows(source: IntArray, target: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        for (y in 0 until height) {
            val rowStart = y * width
            var sum = 0
            for (x in 0..radius) {
                val clamped = x.coerceAtMost(width - 1)
                sum += (source[rowStart + clamped] ushr 24) and 0xFF
            }
            for (x in 0 until width) {
                target[rowStart + x] = ((sum / window) shl 24) or 0x00FFFFFF
                val add = (x + radius + 1).coerceAtMost(width - 1)
                val remove = (x - radius).coerceAtLeast(0)
                sum += (source[rowStart + add] ushr 24) and 0xFF
                sum -= (source[rowStart + remove] ushr 24) and 0xFF
            }
        }
    }

    private fun blurColumns(source: IntArray, target: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        for (x in 0 until width) {
            var sum = 0
            for (y in 0..radius) {
                val clamped = y.coerceAtMost(height - 1)
                sum += (source[clamped * width + x] ushr 24) and 0xFF
            }
            for (y in 0 until height) {
                target[y * width + x] = ((sum / window) shl 24) or 0x00FFFFFF
                val add = (y + radius + 1).coerceAtMost(height - 1)
                val remove = (y - radius).coerceAtLeast(0)
                sum += (source[add * width + x] ushr 24) and 0xFF
                sum -= (source[remove * width + x] ushr 24) and 0xFF
            }
        }
    }

    /** 诊断用：贴花失败时调用方打一条日志。 */
    fun logFailure(error: Throwable) {
        Log.w(TAG, "片边/日期没有贴上，按未贴花的图保存", error)
    }
}
