package com.pocketccd.prototype.camera

import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 一颗**物理镜头**在逻辑相机里的档位（D-044）。
 *
 * 这里是"这颗镜头对应多少倍"，不是"当前正在用哪颗" —— 后者没有公开接口，
 * 详见 [readLensStops] 的说明。
 */
data class LensStop(
    /** 相对主摄的倍率，直接取 `CameraInfo.intrinsicZoomRatio`，不自己算。 */
    val zoomRatio: Float,
    /**
     * 35mm 等效焦距。由物理焦距 × 43.2666 ÷ 传感器对角线现算，
     * 读不到传感器尺寸时为 null（该档只显示倍率）。
     */
    val equivalentFocalMm: Int? = null,
) {
    /** `3.2×` / `1×`。一位小数，整数不补零。 */
    val ratioLabel: String
        get() {
            val rounded = (zoomRatio * 10f).roundToInt() / 10f
            return if (rounded % 1f == 0f) {
                "${rounded.toInt()}×"
            } else {
                "${"%.1f".format(rounded)}×"
            }
        }

    val focalLabel: String? get() = equivalentFocalMm?.let { "${it}mm" }
}

/** 36×24 画幅的对角线长度（mm），等效焦距的换算基准。 */
private const val FULL_FRAME_DIAGONAL_MM = 43.2666f

/**
 * 读出后置逻辑相机底下的真实光学档位。
 *
 * ### 为什么要走 physicalCameraInfos
 *
 * 取景页以前只有 `1x` / `2x` 两个写死的档，`2x` 是 `setZoomRatio(2f)` 的纯数码裁切，
 * 跟手机上那三颗镜头一点关系都没有。CameraX 1.3+ 起可以用
 * `CameraInfo.getPhysicalCameraInfos()` 拿到逻辑相机底下每颗物理镜头，
 * 每颗自带 `intrinsicZoomRatio`（相对主摄的倍率），比任何硬编码都准。
 *
 * ### 为什么不做物理镜头绑定
 *
 * FSWP pack 的空间参数（扩散 / 晕光 / 颗粒半径）是按「毫米 × 该帧 pixels-per-mm」
 * 烘焙的（D-037）。主摄 1/1.4" 装 50MP 约 890 px/mm，73 mm 长焦 1/1.95" 装 50MP
 * 约 1240 px/mm —— 换物理镜头等于换像素密度，颗粒会细 29%、晕光半径错位。
 * 走 `setZoomRatio` 时像素密度恒等于主摄，pack 才成立。
 *
 * ### 边界
 *
 * 返回的倍率是"这颗镜头的等效倍率"，**不保证**系统此刻正在用它。CameraX 与
 * HAL 都不公开"当前物理相机"。真实用了哪颗可以读成片 EXIF 的焦距反推
 * （主摄约 6 mm、长焦约 16 mm），见 [PhotoExif.focalLengthMm]。
 *
 * 探不到（单摄 / 厂商裁掉 / 非 camera2 后端）时返回空列表，调用方应退回数码档，
 * 不要凭空造光学倍率。
 */
fun readLensStops(cameraInfo: CameraInfo): List<LensStop> {
    val supported = runCatching { cameraInfo.isLogicalMultiCameraSupported() }
        .getOrDefault(false)
    if (!supported) return emptyList()

    val physical = runCatching { cameraInfo.physicalCameraInfos }
        .getOrNull()
        ?.toList()
        .orEmpty()
    if (physical.isEmpty()) return emptyList()

    val stops = physical.mapNotNull { info -> info.toLensStop() }
        // 同一颗镜头可能同时以多个 camera id 出现（厂商的 multi-camera 别名），
        // 倍率相同就只留一个。
        .distinctBy { (it.zoomRatio * 100f).roundToInt() }
        .sortedBy { it.zoomRatio }
        .take(4)

    if (stops.isEmpty()) return emptyList()
    // 主摄缺失会让档位表看起来像"没有 1×"，补一个纯倍率档（mm 由主摄自己算不出来
    // 就不写），总比少一档让人以为相机坏了强。
    return if (stops.none { kotlin.math.abs(it.zoomRatio - 1f) < 0.02f }) {
        (stops + LensStop(1f)).sortedBy { it.zoomRatio }
    } else {
        stops
    }
}

private fun CameraInfo.toLensStop(): LensStop? {
    val ratio = runCatching { intrinsicZoomRatio }.getOrNull() ?: return null
    if (ratio < 0.3f || ratio > 15f) return null

    val characteristics = runCatching { Camera2CameraInfo.from(this) }.getOrNull()
    val focalLengths = characteristics?.getCameraCharacteristic(
        CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS,
    )
    val sensorSize = characteristics?.getCameraCharacteristic(
        CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE,
    )
    val focal = focalLengths?.filter { it > 0f }?.minOrNull()

    // 深度 / 辅助镜头往往没有物理焦距或传感器尺寸。没有尺寸就没法算等效焦距，
    // 一并丢掉 —— 它们本来也不成像。
    if (focal == null || sensorSize == null) return null
    val diagonal = hypot(sensorSize.width, sensorSize.height)
    if (diagonal <= 0f) return null

    val equivalent = (focal * FULL_FRAME_DIAGONAL_MM / diagonal).roundToInt()
    if (equivalent !in 8..300) return null

    return LensStop(zoomRatio = ratio, equivalentFocalMm = equivalent)
}
