package com.pocketccd.prototype.camera

import android.media.ExifInterface
import java.io.File
import kotlin.math.roundToInt

/**
 * 成片 JPEG 里**真实读到**的拍摄参数（D-043）。
 *
 * 只读这几项，理由是它们属于硬件事实：相机管线一定会写，用户也能在系统相册的
 * 「详情」里逐条核对。设计稿写死的 `1/125 · f/2.8 · ISO 400` 不在其列 —— 那是
 * 配图参数，不是这台机器拍的，抄过来就是伪造（D-002）。
 *
 * 读不到一律是 null，界面按「未知」处理，**不许兜底成设计稿的数值**。
 */
data class PhotoExif(
    /**
     * 物理焦距（mm）。这个值还能反推当前用的是哪颗镜头：
     * 一加 13 主摄约 6 mm、73 mm 长焦约 16 mm。D-044 的倍率标签靠它自证。
     */
    val focalLengthMm: Float? = null,
    val fNumber: Float? = null,
    val exposureSeconds: Double? = null,
    val iso: Int? = null,
) {
    val isEmpty: Boolean
        get() = focalLengthMm == null && fNumber == null &&
            exposureSeconds == null && iso == null

    /** `1/125 · f/1.6`。两项都缺时返回 null，调用方整行不画。 */
    fun exposureLine(): String? {
        val parts = buildList {
            shutterLabel()?.let(::add)
            fNumber?.let { add("f/${oneDecimal(it)}") }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** `1/125` / `2.5s`。EXIF 存的是秒，界面要的是相机惯用的倒数写法。 */
    fun shutterLabel(): String? = exposureSeconds?.let { seconds ->
        when {
            seconds <= 0.0 -> null
            seconds >= 1.0 -> "${oneDecimal(seconds.toFloat())}s"
            else -> "1/${(1.0 / seconds).roundToInt()}"
        }
    }

    fun isoLabel(): String? = iso?.let { "ISO $it" }

    fun focalLabel(): String? = focalLengthMm?.let { "${oneDecimal(it)}mm" }
}

/** 去掉 `1.60` 这种尾巴，同时保留 `1.6`。 */
private fun oneDecimal(value: Float): String {
    val rounded = (value * 10f).roundToInt() / 10f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else "%.1f".format(rounded)
}

/**
 * 读成片文件的 EXIF。任何异常都退化成空对象 —— 成片页宁可少显示一行参数，
 * 也不能因为一张图的 EXIF 损坏就崩在"刚拍完"这一屏。
 */
fun readPhotoExif(file: File): PhotoExif = runCatching {
    val exif = ExifInterface(file.absolutePath)
    PhotoExif(
        focalLengthMm = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
            .takeIf { it > 0.0 }?.toFloat(),
        fNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
            .takeIf { it > 0.0 }?.toFloat(),
        exposureSeconds = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
            .takeIf { it > 0.0 },
        // `android.media.ExifInterface` 没有导出 TAG_PHOTOGRAPHIC_SENSITIVITY 常量
        // （那是 androidx.exifinterface 才补上的），所以按规范的 tag 名直接读；
        // 老机型只写已经废弃的 ISO_SPEED_RATINGS，兜底再读一次。
        iso = exif.getAttributeInt("PhotographicSensitivity", 0).takeIf { it > 0 }
            ?: exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0).takeIf { it > 0 },
    )
}.getOrDefault(PhotoExif())
