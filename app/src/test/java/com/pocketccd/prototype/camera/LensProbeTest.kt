package com.pocketccd.prototype.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长焦探测的算术与筛选规则（D-088）。
 *
 * 这段逻辑决定"取景页上出现哪几档、点下去跳到多少倍"，错了的表现不是崩溃，
 * 而是**点 3× 却停在主摄的数码裁切上** —— 画质掉一档，用户还以为是镜头不行。
 * 所以重点不在"正常机型能算出 3×"，而在几条容易错的边界：
 *
 * - 长焦传感器比主摄小时，裸焦距比会**低估**倍率；
 * - 数码档不能被认成长焦；
 * - 档位超长时不能把最长焦砍掉。
 */
class LensProbeTest {

    /** 1/1.5" 级别的传感器：8.0 × 6.0mm，对角线 10mm。 */
    private val wideSensor = 8.0f to 6.0f

    /** 3× 长焦用的小底：4.0 × 3.0mm，对角线 5mm。 */
    private val teleSensor = 4.0f to 3.0f

    // ------------------------------------------------------------------
    // 等效焦距
    // ------------------------------------------------------------------

    @Test
    fun equivalentFocalUsesSensorDiagonal() {
        // 6mm 焦距 + 10mm 对角线 → 6 × 43.2666 / 10 ≈ 25.96mm
        val eq = equivalentFocalMmOf(6f, wideSensor.first, wideSensor.second)
        assertEquals(25.96f, eq!!, 0.05f)
    }

    @Test
    fun absurdFocalLengthsAreRejected() {
        // 没有传感器尺寸 / 焦距非正 → 算不出来，必须返回 null 而不是瞎猜。
        assertNull(equivalentFocalMmOf(6f, 0f, 6f))
        assertNull(equivalentFocalMmOf(0f, 8f, 6f))
        // 等效 300mm 以上是厂商报送的假数据。
        assertNull(equivalentFocalMmOf(80f, 4f, 3f))
    }

    // ------------------------------------------------------------------
    // 倍率：必须用等效焦距之比
    // ------------------------------------------------------------------

    @Test
    fun telephotoRatioUsesEquivalentFocalNotRawFocal() {
        // 主摄：6mm / 对角线 10mm → 25.96mm
        val mainEq = equivalentFocalMmOf(6f, wideSensor.first, wideSensor.second)!!
        // 长焦：15mm / 对角线 5mm → 129.8mm
        val tele = lensStopOf(15f, teleSensor.first, teleSensor.second, mainEq)!!

        // 等效比 ≈ 5.0×；而裸焦距比 15/6 只有 2.5×。
        // 若实现退回裸比值，这里会拿到 2.5 —— 那正是"有长焦却切不过去"的成因。
        assertEquals(5.0f, tele.zoomRatio, 0.06f)
        assertEquals(130, tele.equivalentFocalMm)
        assertTrue(tele.optical)
    }

    @Test
    fun sameEquivalentFocalCollapsesToOneStop() {
        val mainEq = equivalentFocalMmOf(6f, wideSensor.first, wideSensor.second)!!
        // 与主摄同视场的第二颗（厂商的辅助相机）→ 1×，不是 1.1×。
        val duplicate = lensStopOf(6f, wideSensor.first, wideSensor.second, mainEq)!!
        assertEquals(1.0f, duplicate.zoomRatio, 0.001f)
    }

    @Test
    fun ultraWideIsDropped() {
        val mainEq = equivalentFocalMmOf(6f, wideSensor.first, wideSensor.second)!!
        // 超广角：更短的焦距 + 更大的底 → 等效更小，倍率 < 1，必须被丢掉（D-037）。
        assertNull(lensStopOf(2f, 9.6f, 7.2f, mainEq))
    }

    @Test
    fun quantizeAbsorbsFloatNoise() {
        assertEquals(3.0f, quantizeZoomRatio(2.9999f), 0.0001f)
        assertEquals(3.2f, quantizeZoomRatio(3.21f), 0.0001f)
        assertEquals(1.0f, quantizeZoomRatio(1.0001f), 0.0001f)
    }

    // ------------------------------------------------------------------
    // 档位表整理
    // ------------------------------------------------------------------

    @Test
    fun missingOneXIsFilledIn() {
        // 只探到长焦（主摄被厂商隐藏）时，界面不能出现"没有 1×"的表。
        val stops = tidyLensStops(listOf(LensStop(3f, 70, optical = true)))
        assertEquals(listOf(1f, 3f), stops.map { it.zoomRatio })
        // 补出来的那一档是数码档，不能标光学。
        assertTrue(stops.first().optical.not())
    }

    @Test
    fun duplicateRatiosCollapse() {
        // 同一颗镜头以两个 camera id 出现（multi-camera 别名）。
        val stops = tidyLensStops(
            listOf(
                LensStop(1f, 24, optical = true),
                LensStop(3f, 70, optical = true),
                LensStop(3.0f, 70, optical = true),
            ),
        )
        assertEquals(2, stops.size)
    }

    @Test
    fun fiveStopsKeepTheLongest() {
        // 五档机型上 `take(4)` 会恰好砍掉 10×，用户点不到最长焦。
        val stops = tidyLensStops(
            listOf(
                LensStop(1f, 24, optical = true),
                LensStop(2f, 48, optical = true),
                LensStop(3f, 70, optical = true),
                LensStop(5f, 120, optical = true),
                LensStop(10f, 240, optical = true),
            ),
        )
        assertEquals(4, stops.size)
        assertEquals(10f, stops.last().zoomRatio, 0.001f)
    }

    @Test
    fun emptyInputStaysEmpty() {
        // 探不到就是空：不能返回一个只有 1× 的假表。
        assertTrue(tidyLensStops(emptyList()).isEmpty())
    }

    // ------------------------------------------------------------------
    // 主长焦判定
    // ------------------------------------------------------------------

    @Test
    fun telephotoIsShortestOpticalAboveThreshold() {
        val stops = listOf(
            LensStop(1f, 24, optical = true),
            LensStop(3f, 70, optical = true),
            LensStop(5f, 120, optical = true),
        )
        assertEquals(3f, pickTelephotoLens(stops)!!.zoomRatio, 0.001f)
    }

    @Test
    fun digitalStopIsNeverTelephoto() {
        // 2× 数码档倍率虽大，但不是独立镜头 —— 标成"长焦"就是骗人。
        val stops = listOf(LensStop(1f), LensStop(2f), LensStop(10f))
        assertNull(pickTelephotoLens(stops))
    }

    @Test
    fun noTelephotoOnSingleCamera() {
        assertNull(pickTelephotoLens(listOf(LensStop(1f, 24, optical = true))))
    }

    // ------------------------------------------------------------------
    // 展示
    // ------------------------------------------------------------------

    @Test
    fun ratioLabelDropsTrailingZero() {
        assertEquals("1×", LensStop(1f).ratioLabel)
        assertEquals("3×", LensStop(3f).ratioLabel)
        assertEquals("3.2×", LensStop(3.2f).ratioLabel)
    }

    @Test
    fun describeMarksDigitalStops() {
        val capability = LensCapability(
            stops = listOf(LensStop(1f, 24, optical = true), LensStop(3f, 70, optical = true)),
            telephoto = LensStop(3f, 70, optical = true),
            source = LensProbeSource.CAMERA2_IDS,
            mainEquivalentFocalMm = 24,
        )
        assertTrue(capability.hasTelephoto)
        assertEquals("1×/24mm，3×/70mm", capability.describe())

        val digital = LensCapability(stops = listOf(LensStop(1f), LensStop(2f)))
        assertEquals("1×·数码，2×·数码", digital.describe())
        assertTrue(digital.hasTelephoto.not())
    }
}
