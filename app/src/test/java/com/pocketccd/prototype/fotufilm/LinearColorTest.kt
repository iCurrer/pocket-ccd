package com.pocketccd.prototype.fotufilm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 色彩链的行为契约。
 *
 * 这些断言盯的是**基色矩阵**和**抖动**，不是数值本身好看不好看：矩阵系数抄自上游
 * `ColorScience.swift`，任何一位被改动都应该在这里失败。
 */
class LinearColorTest {

    private fun decodeOne(argb: Int): Triple<Float, Float, Float> {
        val r = FloatArray(1)
        val g = FloatArray(1)
        val b = FloatArray(1)
        LinearColor.decodeInto(intArrayOf(argb), 1, r, g, b)
        return Triple(r[0], g[0], b[0])
    }

    /**
     * 矩阵行和精确为 1、白点同为 D65 → 中性灰在 sRGB → Rec.2020 之后仍是中性灰。
     *
     * 这是"饱和度错配只影响彩色、不影响灰阶"这条结论的守门断言：一旦它红了，
     * 说明有人改了矩阵却破坏了白点，所有中性色都会偏。
     */
    @Test
    fun `grey stays neutral through the Rec2020 conversion`() {
        for (level in intArrayOf(0x00, 0x20, 0x40, 0x80, 0xC0, 0xFF)) {
            val (r, g, b) = decodeOne(0xFF000000.toInt() or (level shl 16) or (level shl 8) or level)
            assertEquals("level=$level R/G", r, g, 1e-6f)
            assertEquals("level=$level G/B", g, b, 1e-6f)
        }
    }

    /**
     * 纯红落在 Rec.2020 基色上：数值逐位对上游 `linearSRGBToRec2020` 的第一列。
     *
     * 这个测试是防回归的核心 —— 上游若升级矩阵，这里必须跟着改，而不是被悄悄放过。
     */
    @Test
    fun `sRGB primaries land on the Rec2020 matrix`() {
        val (r, g, b) = decodeOne(0xFFFF0000.toInt())
        assertEquals(0.627403896f, r, 1e-5f)
        assertEquals(0.069097289f, g, 1e-5f)
        assertEquals(0.016391439f, b, 1e-5f)
    }

    /** 中性灰往返：解码成 Rec.2020 再按 P3 → sRGB 编码回来，应回到同一个 8bit 值。 */
    @Test
    fun `neutral grey round trips to its own code`() {
        for (level in intArrayOf(0x10, 0x40, 0x80, 0xC0, 0xF0)) {
            val (r, g, b) = decodeOne(0xFF000000.toInt() or (level shl 16) or (level shl 8) or level)
            val out = LinearColor.encodeRgb(r, g, b, dither = null)
            assertEquals("level=$level R", level, (out shr 16) and 0xFF)
            assertEquals("level=$level G", level, (out shr 8) and 0xFF)
            assertEquals("level=$level B", level, out and 0xFF)
        }
    }

    /** 白以上的光夹到 255，不溢出成别的通道。 */
    @Test
    fun `light above white clips at 255`() {
        assertEquals(0xFFFFFFFF.toInt(), LinearColor.encodeRgb(4f, 4f, 4f, dither = null))
    }

    /**
     * P3 的纯蓝在 sRGB 里是超色域的：B 越过 1、R/G 落到负区。
     * 现在的策略是 clip，所以结果应当是 sRGB 的纯蓝。
     */
    @Test
    fun `saturated P3 blue clips into the sRGB cube`() {
        val out = LinearColor.encodeRgb(0f, 0f, 1f, dither = null)
        assertEquals(0, (out shr 16) and 0xFF)
        assertEquals(0, (out shr 8) and 0xFF)
        assertEquals(255, out and 0xFF)
    }

    /** NaN 归零，不抛出、不污染其它通道。 */
    @Test
    fun `NaN encodes to black`() {
        assertEquals(0xFF000000.toInt(), LinearColor.encodeRgb(Float.NaN, Float.NaN, Float.NaN, dither = null))
    }

    /**
     * 抖动：同一个 seed 必须逐值相同（基准与交叉验证依赖可复现），
     * 取值落在 ±1 个码值内，均值为 0（三角分布，不抬高也不压低整体亮度）。
     */
    @Test
    fun `dither is deterministic, bounded and unbiased`() {
        val first = LinearColor.Dither(0x2d9f)
        val second = LinearColor.Dither(0x2d9f)
        val a = FloatArray(3)
        val b = FloatArray(3)

        var sum = 0.0
        var squares = 0.0
        var n = 0
        repeat(20_000) {
            first.offsets(a)
            second.offsets(b)
            for (c in 0..2) {
                assertEquals("同 seed 必须逐值相同", a[c], b[c], 0f)
                assertTrue("偏移越界：${a[c]}", a[c] >= -1f && a[c] <= 1f)
                sum += a[c]
                squares += a[c].toDouble() * a[c]
                n++
            }
        }
        assertEquals("均值应接近 0", 0.0, sum / n, 0.01)
        // 两个 U[0,1) 之差的理论方差是 1/6，标准差约 0.408。
        val variance = squares / n - (sum / n) * (sum / n)
        assertEquals("三角分布方差", 1.0 / 6.0, variance, 0.01)
    }

    /** 抖动确实把一片纯色打散成多个码值 —— 这正是消除色带的作用。 */
    @Test
    fun `dither spreads a flat field across neighbouring codes`() {
        val count = 4096
        val r = FloatArray(count) { 0.2f }
        val g = FloatArray(count) { 0.2f }
        val b = FloatArray(count) { 0.2f }
        val pixels = IntArray(count)

        LinearColor.encodeInto(r, g, b, count, pixels, seed = 7)

        val levels = HashSet<Int>()
        for (pixel in pixels) levels.add(pixel and 0xFF)
        assertTrue("一片纯色应被抖开成多个码值，实际只有 $levels", levels.size >= 2)
    }

    // ------------------------------------------------------------------
    // SDR shoulder（上游 FilmSDRDelivery）
    // ------------------------------------------------------------------

    /**
     * 拐点取 1（负片/印相）时肩部必须是**恒等**：这一条是"加了 shoulder 不影响现役三卷"
     * 的抵押物。若哪天 [LinearColor.KNEE_BOUNDED] 被改动，现有成片的观感会整体变，
     * 而这不会有任何别的征兆。
     */
    @Test
    fun `bounded knee is a no-op`() {
        val values = floatArrayOf(0f, 0.25f, 0.5f, 0.7f, 0.9f, 1f, 1.5f, 4f)
        for (x in values) {
            assertEquals("knee=1 时 $x 必须原样返回", x, LinearColor.shoulder(x, LinearColor.KNEE_BOUNDED), 0f)
        }
    }

    /**
     * 拐点 0.7（反转片）：低于拐点原样，高于拐点被压进 0.7..1，且**永不到达 1**。
     *
     * "永不到达 1"是这条曲线的定义性质（有理函数渐近），也是它比 clip 强的地方：
     * 无限亮的高光之间仍然保持区分度，而不是全挤在码值 255。
     */
    @Test
    fun `transparency knee rolls highlights off without clipping`() {
        val knee = LinearColor.KNEE_TRANSPARENCY
        assertEquals("拐点处一阶相接", knee, LinearColor.shoulder(knee, knee), 0f)

        var previous = knee
        for (x in floatArrayOf(0.75f, 0.85f, 1f, 1.5f, 2f, 8f, 100f)) {
            val y = LinearColor.shoulder(x, knee)
            assertTrue("肩部必须单调：x=$x 下 $y < $previous", y > previous)
            assertTrue("肩部输出必须 < 1（渐近而不到达）：x=$x 得到 $y", y < 1f)
            previous = y
        }

        // 线性 1.0（恰好显示白）要明显比 clip 低，否则等于没压。
        // knee=0.7 时 shoulder(1) = 0.7 + 0.3·0.3/(0.3+0.3) = 0.85。
        assertEquals(
            "线性 1.0 必须滚降到 0.85",
            0.85f,
            LinearColor.shoulder(1f, knee),
            1e-6f,
        )
    }

    /** 拐点是交付参数，越界值要夹回来，不能反过来把中间调压暗。 */
    @Test
    fun `knee is clamped into range`() {
        // 负拐点夹到 0，等于整条曲线从 0 起滚降：shoulder(0.5, 0) = 0.5/1.5。
        assertEquals(1f / 3f, LinearColor.shoulder(0.5f, -0.4f), 1e-6f)
        assertEquals("超 1 的拐点等于不压", 0.9f, LinearColor.shoulder(0.9f, 2f), 0f)
    }

    /** 反转片的成片在编码后必须与负片不同 —— 否则传进来的 knee 根本没生效。 */
    @Test
    fun `encode honours the knee`() {
        val bounded = LinearColor.encodeRgb(1.4f, 1.4f, 1.4f, dither = null, knee = LinearColor.KNEE_BOUNDED)
        val transparency = LinearColor.encodeRgb(1.4f, 1.4f, 1.4f, dither = null, knee = LinearColor.KNEE_TRANSPARENCY)

        assertEquals("knee=1 时超白直接 clip 到 255", 255, bounded and 0xFF)
        assertTrue(
            "knee=0.7 时同一份超白必须落在 255 以下，实际 ${transparency and 0xFF}",
            (transparency and 0xFF) < 255,
        )
    }
}
