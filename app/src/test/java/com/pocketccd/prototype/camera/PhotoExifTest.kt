package com.pocketccd.prototype.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 成片页参数格式化的纯逻辑测试（P1-14 / D-043）。
 *
 * 只测 [PhotoExif] 的**格式化**，不碰 android.media.ExifInterface —— 环境是纯 JVM，
 * 那个类只有桩，调用会炸；而这里要守的规矩本来就是"没有值就不显示"，不是读文件。
 *
 * 最要紧的一条：缺项必须是 null，让界面画 `—`。一旦有人为了"好看"给个兜底值
 * （比如设计稿的 1/125 · f/2.8），成片页就开始撒谎，这几条断言会先挂。
 */
class PhotoExifTest {

    @Test
    fun `快门按相机惯用写法显示`() {
        // EXIF 存秒，界面写倒数：1/125 秒的原始值就是 0.008。
        assertEquals("1/125", PhotoExif(exposureSeconds = 1.0 / 125).shutterLabel())
        // 整秒及以上写成 2.5s，不再取倒数。
        assertEquals("2.5s", PhotoExif(exposureSeconds = 2.5).shutterLabel())
        assertEquals("1s", PhotoExif(exposureSeconds = 1.0).shutterLabel())
    }

    @Test
    fun `快门非法值不显示`() {
        // 0 会算出 1/Infinity，负数没有物理意义：两种都必须退化成"未知"。
        assertNull(PhotoExif(exposureSeconds = 0.0).shutterLabel())
        assertNull(PhotoExif(exposureSeconds = -0.5).shutterLabel())
    }

    @Test
    fun `曝光行把快门和光圈拼在一起`() {
        assertEquals(
            "1/125 · f/1.6",
            PhotoExif(exposureSeconds = 1.0 / 125, fNumber = 1.6f).exposureLine(),
        )
    }

    @Test
    fun `曝光行只有一项时只显示那一项`() {
        assertEquals("1/125", PhotoExif(exposureSeconds = 1.0 / 125).exposureLine())
        // f/1.6 的十进制尾巴要去掉，不能出现 f/1.60。
        assertEquals("f/1.6", PhotoExif(fNumber = 1.6f).exposureLine())
    }

    @Test
    fun `曝光行全缺时返回 null`() {
        // null 让调用方整格写 `—`，而不是抹掉这一列（三列布局会塌）。
        assertNull(PhotoExif().exposureLine())
        assertNull(PhotoExif(iso = 400).exposureLine())
    }

    @Test
    fun `ISO 标签`() {
        assertEquals("ISO 400", PhotoExif(iso = 400).isoLabel())
        assertNull(PhotoExif().isoLabel())
    }

    @Test
    fun `焦距标签`() {
        assertEquals("6mm", PhotoExif(focalLengthMm = 6.0f).focalLabel())
        assertEquals("16.5mm", PhotoExif(focalLengthMm = 16.5f).focalLabel())
        assertNull(PhotoExif().focalLabel())
    }

    @Test
    fun `四项全缺时 isEmpty 为真`() {
        assertTrue(PhotoExif().isEmpty)
        // 任意一项有值就不是空对象 —— 只要有一项真实参数，就值得显示。
        assertFalse(PhotoExif(iso = 400).isEmpty)
        assertFalse(PhotoExif(focalLengthMm = 6.0f).isEmpty)
        assertFalse(PhotoExif(fNumber = 1.6f).isEmpty)
        assertFalse(PhotoExif(exposureSeconds = 0.008).isEmpty)
    }
}
