package com.pocketccd.prototype.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 片边 / 日期后背的**渲染**检查（D-081）。
 *
 * 单测只能验几何数字，验不了"画出来好不好看、会不会盖住画面、齿孔是不是在两边"。
 * 这里用 Robolectric 的原生 Skia 真渲染一张，产物落到 `app/build/screenshots/`，
 * 由人看（和 ScreenshotTest 一个思路：不做像素断言，只做"能看"）。
 *
 * 尺寸用 900×1200 而不是 3000×4000：几何是等比例的，渲染同样的结果，
 * 但测试快几十倍、内存也不至于撞 Robolectric 的默认堆。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31])
class ImprintRenderTest {

    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun photo(width: Int = 900, height: Int = 1200): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // 上半部像天空、下半部像地面，再压一个白点当高光：能看到日期压在中间调上
        // 是否还读得出来，也能看到片边是不是把画面切掉了。
        canvas.drawRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(
                        Color.rgb(96, 140, 200),
                        Color.rgb(232, 226, 210),
                        Color.rgb(64, 72, 56),
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )
        canvas.drawCircle(
            width * 0.72f, height * 0.18f, width * 0.06f,
            Paint().apply { color = Color.WHITE },
        )
        return bitmap
    }

    @Test
    fun rendersFilmEdgeWithDate() {
        val source = photo()
        val result = ImprintRenderer.apply(
            source,
            capturedAt = 1_793_596_175_042L, // 2026-11-02 13:09 本地
            settings = ImprintSettings(filmEdge = true, dateBack = true),
        )
        save("imprint-edge-date.png", result)

        val layout = FilmEdgeLayout(source.width, source.height)
        assertEquals("画布宽 = 沿片长 24mm", layout.canvasWidth, result.width)
        assertEquals("画布高 = 片宽 35mm", layout.canvasHeight, result.height)
        assertTrue("片边必须比原图大", result.width > source.width && result.height > source.height)
        result.recycle()
        source.recycle()
    }

    @Test
    fun rendersDateOnlyOnTheOriginalFrame() {
        val source = photo()
        val result = ImprintRenderer.apply(
            source,
            capturedAt = 1_793_596_175_042L,
            settings = ImprintSettings(dateBack = true),
        )
        save("imprint-date-only.png", result)
        // 只盖日期不该改变尺寸（更不能悄悄缩图）
        assertEquals(source.width, result.width)
        assertEquals(source.height, result.height)
        result.recycle()
        source.recycle()
    }

    /** 横版成片（强制横版拍摄之后就是这种）：看片边是不是横着包住画面。 */
    @Test
    fun rendersLandscapePhoto() {
        val source = photo(width = 1200, height = 900)
        val result = ImprintRenderer.apply(
            source,
            capturedAt = 1_793_596_175_042L,
            settings = ImprintSettings(filmEdge = true, dateBack = true),
        )
        save("imprint-edge-landscape.png", result)
        result.recycle()
        source.recycle()
    }

    @Test
    fun noSettingsReturnsTheSameBitmap() {
        val source = photo()
        val result = ImprintRenderer.apply(source, 1_793_596_175_042L, ImprintSettings())
        assertTrue("什么都没开时必须原样返回，不能白白复制一张 12MP", result === source)
        source.recycle()
    }

    @Test
    fun dateIsSkippedWhenClockLooksUnsynced() {
        val source = photo()
        val result = ImprintRenderer.apply(
            source,
            capturedAt = 0L,
            settings = ImprintSettings(dateBack = true),
        )
        // 时钟不可信 → 只返回原图（片边关着的时候连复制都不做）
        assertTrue(result === source)
        source.recycle()
    }

    private fun save(name: String, bitmap: Bitmap) {
        val file = File(outDir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("imprint sample → ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
    }
}
