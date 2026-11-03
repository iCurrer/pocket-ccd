package com.pocketccd.prototype.camera

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 片边 / 日期后背的纯计算部分（D-081，D-084 修正齿孔朝向）。
 *
 * 这一层最容易"看起来对、其实错"：毫米到像素的换算、齿孔节距、齿孔到底贴在哪条边。
 * 全部不碰 android.graphics，所以能在普通 JVM 单测里跑。
 */
class ImprintTest {

    // ---------------------------------------------------------------- 日期

    private fun epoch(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
        zone: String = "Asia/Shanghai",
    ): Long = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
        clear()
        set(year, month - 1, day, hour, 0, 0)
    }.timeInMillis

    @Test
    fun dateUsesTwoDigitYearAndUnpaddedMonthDay() {
        assertEquals("'26 11 2", DateStamp.text(epoch(2026, 11, 2), DateOrder.YearMonthDay))
    }

    @Test
    fun dateUsOrderPutsYearLast() {
        assertEquals("11 2 '26", DateStamp.text(epoch(2026, 11, 2), DateOrder.MonthDayYear))
    }

    @Test
    fun dateRejectsClockBefore2000() {
        assertNull(DateStamp.text(0L, DateOrder.YearMonthDay))
        assertNull(DateStamp.text(epoch(1979, 5, 4), DateOrder.YearMonthDay))
    }

    @Test
    fun dateHandlesLeapDay() {
        assertEquals("'24 2 29", DateStamp.text(epoch(2024, 2, 29), DateOrder.YearMonthDay))
    }

    @Test
    fun dateHandlesYearBoundary() {
        assertEquals("'27 1 1", DateStamp.text(epoch(2027, 1, 1, hour = 0), DateOrder.YearMonthDay))
        assertEquals(
            "'26 12 31",
            DateStamp.text(epoch(2026, 12, 31, hour = 23), DateOrder.YearMonthDay),
        )
    }

    @Test
    fun settingsLabelOnlyWhenSomethingIsOn() {
        assertNull(ImprintSettings().label())
        assertEquals("135 半格", ImprintSettings(filmEdge = true).label())
        assertEquals("日期", ImprintSettings(dateBack = true).label())
        assertEquals(
            "135 半格 · 日期",
            ImprintSettings(filmEdge = true, dateBack = true).label(),
        )
    }

    @Test
    fun dateOrderRoundTripsThroughPreferences() {
        for (order in DateOrder.entries) assertEquals(order, DateOrder.fromId(order.id))
        assertEquals(DateOrder.YearMonthDay, DateOrder.fromId(null))
        assertEquals(DateOrder.YearMonthDay, DateOrder.fromId("nonsense"))
    }

    // ---------------------------------------------------------------- 片边几何

    @Test
    fun filmRunsAlongThePhotoOrientation() {
        // 竖幅照片 → 胶片竖着走（齿孔在左右）；横幅 → 横着走（齿孔在上下）。
        // 第一版这里反了，横版成片两边长出一排齿孔（D-084）。
        assertFalse(FilmEdgeLayout(3000, 4000).filmRunsHorizontally)
        assertTrue(FilmEdgeLayout(4000, 3000).filmRunsHorizontally)
    }

    @Test
    fun perforationsSitOnTheLongEdges() {
        // 竖幅：画布比宽高 → 齿孔贴左右两条长边
        val portrait = FilmEdgeLayout(3000, 4000)
        assertTrue("竖幅画布应该是高的", portrait.canvasHeight > portrait.canvasWidth)
        portrait.perforations().forEach { hole ->
            val onLeft = hole.leftMm < 3f
            val onRight = hole.rightMm > portrait.canvasWidthMm - 3f
            assertTrue("齿孔没贴在左右长边上：$hole", onLeft || onRight)
        }

        // 横幅：齿孔贴上下两条长边
        val landscape = FilmEdgeLayout(4000, 3000)
        assertTrue("横幅画布应该是宽的", landscape.canvasWidth > landscape.canvasHeight)
        landscape.perforations().forEach { hole ->
            val onTop = hole.topMm < 3f
            val onBottom = hole.bottomMm > landscape.canvasHeightMm - 3f
            assertTrue("齿孔没贴在上下长边上：$hole", onTop || onBottom)
        }
    }

    @Test
    fun gateIsCentredAndKeepsThePhotoAspect() {
        val layout = FilmEdgeLayout(3000, 4000)
        val gate = layout.gate
        assertEquals(layout.canvasWidthMm / 2f, gate.leftMm + gate.widthMm / 2f, 0.01f)
        assertEquals(layout.canvasHeightMm / 2f, gate.topMm + gate.heightMm / 2f, 0.01f)
        // 画门比例必须等于照片比例，否则会变形
        assertEquals(
            layout.frameAcrossMm / layout.frameAlongMm,
            gate.widthMm / gate.heightMm,
            0.001f,
        )
        // 135 的画门高度永远 24mm；4:3 照片 → 沿片长 32mm
        assertEquals(24f, layout.frameAcrossMm, 0.01f)
        assertEquals(32f, layout.frameAlongMm, 0.01f)
    }

    @Test
    fun perforationsUseRealPitchAndStayInsideTheFilm() {
        val layout = FilmEdgeLayout(3000, 4000)
        val holes = layout.perforations()
        // 42mm 沿片长 / 4.75mm 节距 = 8 个，两排 → 16 个
        assertEquals(16, holes.size)
        holes.forEach { hole ->
            assertTrue(hole.leftMm >= 0f)
            assertTrue(hole.rightMm <= layout.canvasWidthMm + 0.001f)
            assertTrue(hole.topMm >= 0f)
            assertTrue(hole.bottomMm <= layout.canvasHeightMm + 0.001f)
            assertEquals(FilmEdgeLayout.PERF_SHORT_MM, hole.widthMm, 0.001f)
            assertEquals(FilmEdgeLayout.PERF_LONG_MM, hole.heightMm, 0.001f)
        }
        // 左右两列关于片宽中线对称
        val first = holes[0]
        val second = holes[1]
        assertEquals(FilmEdgeLayout.FILM_WIDTH_MM - first.leftMm, second.rightMm, 0.001f)
    }

    @Test
    fun canvasStaysUnderThePixelCap() {
        // 42 × 35mm 在 125 px/mm 下是 5250 × 4375 ≈ 23MP，正好在上限内（照片 1:1 不缩）
        val layout = FilmEdgeLayout(3000, 4000)
        val pixels = layout.canvasWidth.toLong() * layout.canvasHeight
        assertTrue("画布 $pixels 像素超过上限", pixels <= FilmEdgeLayout.MAX_PIXELS.toLong() + 1)
        assertEquals(1f, layout.scale, 0.0001f)
        // 竖幅 → 胶片竖着走：画布 35mm 宽 × 42mm 高
        assertEquals(4375, layout.canvasWidth)
        assertEquals(5250, layout.canvasHeight)
    }

    @Test
    fun dateOnBarePhotoStaysInsideThePhoto() {
        // 回归：只开日期、不开片边时，画布就是照片本身，不能沿用片门坐标（D-081）。
        val layout = FilmEdgeLayout(900, 1200)
        val frame = FilmEdgeLayout.FramePx.of(900, 1200)
        val (x, baseline) = layout.dateBaseline(frame, measuredWidthPx = 300f)
        assertTrue("x=$x 跑到画面左边", x >= 0f)
        assertTrue("右边缘溢出", x + 300f <= 900f)
        assertTrue("基线跑到画面外：$baseline", baseline <= 1200f)
        assertTrue("日期贴得太靠上", baseline > 1200f - layout.dateSizePx * 2)
    }

    @Test
    fun dateSitsInsideTheGate() {
        val layout = FilmEdgeLayout(3000, 4000)
        val (x, baseline) = layout.dateBaseline(layout.gatePx, measuredWidthPx = 400f)
        val gate = layout.gatePx
        assertTrue(x > gate.left)
        assertTrue(x + 400f <= gate.right + 1f)
        assertTrue(baseline <= gate.bottom + 1f)
        assertTrue(baseline > gate.top)
    }
}
