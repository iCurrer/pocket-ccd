package com.pocketccd.prototype.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.util.Size
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 帧几何：尺寸够不够、竖置帧该往哪边转、够不到 pack 时怎么出片。
 *
 * 这一层是「这张帧能怎么处理」的唯一口径。历史上它被写在两处：`AppViewModel`
 * 按 width/height 直接比，`decodeJpeg` 只认严格互换，于是 3072×4096 的帧被两边
 * 分别判成"太小"和"转置但没方向标记"—— 同一台机器、同一帧、两个都错的结论。
 *
 * D-098 之后分两段：`frameServesPack` 只回答「能不能满幅」（决定画幅标注），
 * 真正的出片策略是 `decodeJpeg` 的三种情况：精确 / 裁切 / 小帧原生直通。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrameGeometryTest {

    // ------------------------------------------------------------ 尺寸判定

    @Test
    fun landscapeFrameEqualToPackServes() {
        assertTrue(frameServesPack(4000, 3000, 4000, 3000))
    }

    @Test
    fun largerLandscapeFrameServesByCropping() {
        assertTrue(frameServesPack(4096, 3072, 4000, 3000))
        assertTrue(frameServesPack(8000, 6000, 4000, 3000))
    }

    /**
     * 真机现场：设备交来 3072×4096（竖置），转 90° 是 4096×3072，
     * 裁 96×72 正好 4000×3000 —— 这一帧**完全够用**，不能判成"拍不了"。
     */
    @Test
    fun portraitFrameThatTransposesIntoPackServes() {
        assertTrue(frameServesPack(3072, 4096, 4000, 3000))
    }

    @Test
    fun exactTransposedFrameServes() {
        assertTrue(frameServesPack(3000, 4000, 4000, 3000))
    }

    /**
     * 3840×2880 与 3264×2448 都**到不了 pack 的满幅**（分别短 160px / 736px）。
     *
     * 注意这个断言问的是「能不能满幅」，不是「能不能拍」。D-098 之后它们照样能出片，
     * 走的是 `decodeJpeg` 的原生直通；`frameServesPack` 现在只用来决定画幅标注
     * 该印 pack 尺寸还是设备尺寸。
     */
    @Test
    fun undersizedFrameDoesNotReachFullFrame() {
        assertFalse(frameServesPack(3840, 2880, 4000, 3000))
        assertFalse(frameServesPack(3264, 2448, 4000, 3000))
    }

    /** 竖置也不能免掉满幅门槛：2448×3264 转正后仍是 3264×2448。 */
    @Test
    fun undersizedPortraitFrameAlsoMissesFullFrame() {
        assertFalse(frameServesPack(2448, 3264, 4000, 3000))
    }

    @Test
    fun normalizationPutsLongEdgeFirst() {
        assertEquals(Size(4096, 3072), Size(3072, 4096).landscapeNormalized())
        assertEquals(Size(4000, 3000), Size(4000, 3000).landscapeNormalized())
    }

    // ------------------------------------------------------------ 转正方向

    @Test
    fun exifRotationWinsOverSensorMount() {
        assertEquals(90, unTransposeRotationOf(ExifInterface.ORIENTATION_ROTATE_90, 270))
        assertEquals(270, unTransposeRotationOf(ExifInterface.ORIENTATION_ROTATE_270, 90))
    }

    /**
     * EXIF 缺失时的推导：CameraX 在 `targetRotation = ROTATION_0` 下按
     * `(sensorOrientation - 0 + 360) % 360` 顺时针转过像素，转回去取补角。
     */
    @Test
    fun sensorMountDerivesInverseRotationWhenExifMissing() {
        assertEquals(270, unTransposeRotationOf(ExifInterface.ORIENTATION_NORMAL, 90))
        assertEquals(90, unTransposeRotationOf(ExifInterface.ORIENTATION_NORMAL, 270))
    }

    /** 两条都拿不到就必须返回 null —— 猜方向会得到一张 180° 倒着的照片。 */
    @Test
    fun noSignalMeansNoRotation() {
        assertNull(unTransposeRotationOf(ExifInterface.ORIENTATION_NORMAL, null))
        assertNull(unTransposeRotationOf(ExifInterface.ORIENTATION_NORMAL, 0))
        assertNull(unTransposeRotationOf(ExifInterface.ORIENTATION_UNDEFINED, null))
    }

    /**
     * EXIF 说的"正立"与"这一帧是竖置的"自相矛盾时，**必须继续问传感器安装角**。
     *
     * 真机场景：竖置帧 + EXIF 标记 90 + 设备报握持 90，`标记 − 握持 = 0` 得到"本来
     * 就横着"，可帧明明是竖的。旧实现在这里直接返回 null，于是整张照片被判成
     * "没有方向标记、无法转正"。矛盾只能说明 EXIF 那一路不可信，传感器安装角还是硬的。
     */
    @Test
    fun contradictoryExifFallsBackToSensorMount() {
        assertEquals(
            270,
            unTransposeRotationOf(ExifInterface.ORIENTATION_ROTATE_90, 90, deviceRotationDegrees = 90),
        )
        assertEquals(
            90,
            unTransposeRotationOf(ExifInterface.ORIENTATION_ROTATE_270, 270, deviceRotationDegrees = 270),
        )
        // 连安装角都没有才是真的没辙。
        assertNull(
            unTransposeRotationOf(ExifInterface.ORIENTATION_ROTATE_90, null, deviceRotationDegrees = 90),
        )
    }

    // ------------------------------------------------ 落盘旋转：横版构图不可翻成竖版

    /**
     * 横帧 + 90/270 的握持角 = 竖版成品，必须收回 0。
     *
     * 真机复现：横版取景框里构好图按快门，冲洗出的帧是 4000×3000（横的），
     * 落盘又按握持角转了 90°，交出去是 3000×4000 的竖版照片。一加 13 上正常，
     * 另一台机器上必现 —— 差别只在它报的 `Display.rotation` 与实际握持不符。
     */
    @Test
    fun landscapeFrameNeverGetsFlippedToPortrait() {
        assertEquals(0, finalRotationFor(frameIsLandscape = true, requestedRotation = 90))
        assertEquals(0, finalRotationFor(frameIsLandscape = true, requestedRotation = 270))
        assertEquals(0, finalRotationFor(frameIsLandscape = true, requestedRotation = -90))
    }

    /** 横帧的 0/180 是"哪一头朝上"，这一位要保留：上下颠倒也是硬伤。 */
    @Test
    fun landscapeFrameKeepsTheUpsideDownCorrection() {
        assertEquals(0, finalRotationFor(frameIsLandscape = true, requestedRotation = 0))
        assertEquals(180, finalRotationFor(frameIsLandscape = true, requestedRotation = 180))
        assertEquals(180, finalRotationFor(frameIsLandscape = true, requestedRotation = 540))
    }

    /** 帧本身就是竖的：90/270 是正常输出，不能动。 */
    @Test
    fun portraitFrameKeepsItsRotation() {
        assertEquals(90, finalRotationFor(frameIsLandscape = false, requestedRotation = 90))
        assertEquals(270, finalRotationFor(frameIsLandscape = false, requestedRotation = 270))
        assertEquals(0, finalRotationFor(frameIsLandscape = false, requestedRotation = 0))
        assertEquals(180, finalRotationFor(frameIsLandscape = false, requestedRotation = -180))
    }

    // ------------------------------------------------------ 解码：竖置帧转正

    /**
     * 端到端：竖置帧先转正、再居中裁切，成品尺寸必须精确等于 pack。
     *
     * 像素级的方向验证放在 [clockwiseRotationMovesAboveCenterToTheRight]：
     * Robolectric 的 `BitmapRegionDecoder.decodeRegion` 只给一张同尺寸的空图，
     * 端到端这条路上量不到颜色（真机上没有这个问题）。
     */
    @Test
    fun portraitFrameIsRotatedAndCroppedToPackSize() {
        // 24×32 竖置（真机 3072×4096 的等比例缩小版），目标 15×10。
        val bytes = jpegOf(withRedAboveCenter(24, 32))

        val decoded = decodeJpeg(
            bytes = bytes,
            target = Size(15, 10),
            stockLabel = "ACROS 100 II",
            // 传感器安装角 270 → 转回去要顺时针 90°，最终是 4096×3072 那种横向帧。
            sensorMountRotation = 270,
        )

        assertEquals(15, decoded.bitmap.width)
        assertEquals(10, decoded.bitmap.height)
        assertEquals("归一化角要如实报出来（诊断与日志靠它）", 90, decoded.appliedRotationDegrees)
        assertEquals(24, decoded.sourceWidth)
        assertEquals(32, decoded.sourceHeight)
        assertTrue("源尺寸与 pack 不同，必须走裁切", decoded.cropped)
        decoded.bitmap.recycle()
    }

    /**
     * 顺时针 90° 的语义：源图中心偏上（dy<0）→ 输出中心偏右（dx>0）。
     *
     * 方向不能只靠真机肉眼：顺转、逆转的**尺寸**完全一样，选错就是一张倒着的照片。
     */
    @Test
    fun clockwiseRotationMovesAboveCenterToTheRight() {
        val source = withRedAboveCenter(24, 32)
        val rotated = rotateExact(source, 90)

        assertEquals(32, rotated.width)
        assertEquals(24, rotated.height)

        var redCount = 0
        var redSumX = 0
        var redInLeftThird = 0
        for (y in 0 until rotated.height) {
            for (x in 0 until rotated.width) {
                if (isReddish(rotated.getPixel(x, y))) {
                    redCount++
                    redSumX += x
                    if (x < rotated.width / 3) redInLeftThird++
                }
            }
        }
        assertTrue("旋转后的图必须有内容，不能是空图", redCount > 0)
        assertTrue(
            "红块重心应落在右半（实际 ${redSumX / redCount} / 宽 ${rotated.width}）",
            redSumX / redCount > rotated.width / 2,
        )
        assertEquals("红块不该出现在左侧三分之一", 0, redInLeftThird)
        rotated.recycle()
        source.recycle()
    }

    // -------------------------------------------------- 解码：小帧原生直通（D-098）

    /**
     * 低像素传感器机型的主路径：帧比 pack 小，**不许缩放、不许报错**，
     * 按原生尺寸出片并打上 [DecodedFrame.undersized]。
     *
     * 32×24 对 40×30 是 3264×2448 对 4000×3000 的等比例缩小版。
     */
    @Test
    fun undersizedLandscapeFramePassesThroughAtNativeSize() {
        val bytes = jpegOf(withRedAboveCenter(32, 24))

        val decoded = decodeJpeg(
            bytes = bytes,
            target = Size(40, 30),
            stockLabel = "TRI-X 400",
        )

        assertEquals("不许放大到 pack 尺寸", 32, decoded.bitmap.width)
        assertEquals(24, decoded.bitmap.height)
        assertTrue("小帧必须被标成 undersized，日志与画幅标注都靠它", decoded.undersized)
        assertFalse("没裁也没转，不能记成裁切", decoded.cropped)
        assertEquals(0, decoded.appliedRotationDegrees)
        decoded.bitmap.recycle()
    }

    /**
     * 小帧 + 竖置：仍然要转正（否则交出去是躺着的），但不裁也不放大。
     * 32×24 的横置结果比 40×30 的 pack 小 → 走原生直通，且转过的角度要记账。
     */
    @Test
    fun undersizedPortraitFrameIsRotatedThenPassedThrough() {
        val bytes = jpegOf(withRedAboveCenter(24, 32))

        val decoded = decodeJpeg(
            bytes = bytes,
            target = Size(40, 30),
            stockLabel = "ACROS 100 II",
            sensorMountRotation = 270,
        )

        assertEquals(32, decoded.bitmap.width)
        assertEquals(24, decoded.bitmap.height)
        assertEquals("归一化角不是落盘角，落盘还要叠握持角", 90, decoded.appliedRotationDegrees)
        assertTrue(decoded.undersized)
        assertFalse("旋转不是裁切", decoded.cropped)
        decoded.bitmap.recycle()
    }

    /**
     * 「横屏拍完得到竖版照片」的回归（真机：这台机竖置输出 2880×3840，
     * 横持拍完交出一张躺倒的竖版）。
     *
     * 钉的是**两段旋转的合成方式**：
     * 解码只把竖置帧归一化回传感器帧（这里 270°），落盘再叠设备握持角
     * （`AppViewModel.capture` 直接传 `frame.rotationDegrees`），两者相加才是总旋转。
     *
     * 旧实现把落盘角写成 `握持 − 归一化`：横持（握持 0°）算出 90°，
     * 合计 360° —— 等于原地不动，正是那张躺着的竖版。
     */
    @Test
    fun deviceRotationIsAppliedOnTopOfNormalization() {
        // 传感器帧：40×30 横向，左上角一块红。
        val sensorFrame = landscapeWithRedAtTopLeft(40, 30)
        // 设备把它按顺时针 90° 存成竖置帧 30×40 —— 与 mount=90 的
        // `(360 - mount)` 约定一致。
        val raw = rotateExact(sensorFrame, 90)
        val bytes = jpegOf(raw)
        sensorFrame.recycle()

        val decoded = decodeJpeg(
            bytes = bytes,
            target = Size(40, 30),
            stockLabel = "PORTRA 400",
            sensorMountRotation = 90,
        )
        assertEquals("竖置帧归一化回横向要转 270°", 270, decoded.appliedRotationDegrees)
        assertEquals(40, decoded.bitmap.width)
        assertEquals(30, decoded.bitmap.height)

        // 横持 A 面：握持角 0° → 成品就是归一化后的这一帧，红块仍在左上。
        assertTrue(
            "握持 0° 时红块应在左上角（实际 ${hex(decoded.bitmap.getPixel(4, 3))}）",
            isReddish(decoded.bitmap.getPixel(4, 3)),
        )

        // 横持 B 面：握持角 180° → 整体倒转，红块跑到右下。
        // 旧算式（180−270+360=270）会把它转到别处，这条就是那时的红。
        val flipped = turn(decoded.bitmap, 180)
        assertTrue(
            "握持角要叠在归一化之上（实际 ${hex(flipped.getPixel(35, 26))}）",
            isReddish(flipped.getPixel(35, 26)),
        )
        assertFalse("倒转后左上角不该还有红块", isReddish(flipped.getPixel(4, 3)))

        flipped.recycle()
        decoded.bitmap.recycle()
    }

    /**
     * 唯一还会失败的尺寸类分支：竖置且**方向未知**。
     * 这时猜一个角度就是一张 180° 倒着的照片，所以宁可失败。
     */
    @Test
    fun portraitWithoutAnyDirectionSignalStillFails() {
        val bytes = jpegOf(withRedAboveCenter(24, 32))

        val failure = runCatching {
            decodeJpeg(
                bytes = bytes,
                target = Size(40, 30),
                stockLabel = "ACROS 100 II",
                sensorMountRotation = null,
            )
        }.exceptionOrNull()

        assertTrue("竖置 + 无方向标记必须抛错，不能猜", failure is IllegalStateException)
        assertTrue(
            "消息要说明缺的是方向，而不是尺寸（实际：${failure?.message}）",
            failure?.message?.contains("无法转正") == true,
        )
    }

    private fun withRedAboveCenter(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            // 中心 (12,16) 偏上的一小块。裁切区是居中的 10×15，这块整块落在里面。
            drawRect(9f, 8f, 15f, 13f, Paint().apply { color = Color.RED })
        }
        return bitmap
    }

    /** 任意角度整转，画布按需换宽高。生产代码的 [rotateExact] 只接受 90/270。 */
    private fun turn(source: Bitmap, degrees: Int): Bitmap {
        val swap = degrees % 180 != 0
        val out = Bitmap.createBitmap(
            if (swap) source.height else source.width,
            if (swap) source.width else source.height,
            Bitmap.Config.ARGB_8888,
        )
        Canvas(out).apply {
            translate(out.width / 2f, out.height / 2f)
            rotate(degrees.toFloat())
            translate(-source.width / 2f, -source.height / 2f)
            drawBitmap(source, 0f, 0f, Paint().apply { isFilterBitmap = false })
        }
        return out
    }

    /** 横置传感器帧，左上角一块红 —— 用来验证"整体转了多少度"。 */
    private fun landscapeWithRedAtTopLeft(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawRect(0f, 0f, 7f, 6f, Paint().apply { color = Color.RED })
        }
        return bitmap
    }

    private fun hex(pixel: Int): String = "#%08X".format(pixel)

    private fun jpegOf(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out))
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun isReddish(pixel: Int): Boolean =
        Color.red(pixel) > 180 && Color.green(pixel) < 110 && Color.blue(pixel) < 110
}
