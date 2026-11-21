package com.pocketccd.prototype.camera

import android.media.ExifInterface
import android.util.Size

/**
 * 帧几何：**方向无关**的尺寸比较，以及竖置帧该转多少度。
 *
 * 为什么单独成文件：这两个判断是纯函数，是"这支卷在这台机上能不能拍"与
 * "这张帧该往哪边转"的唯一口径。
 *
 * 它们以前散在两处、各写一份，结论还会互相矛盾：
 * `AppViewModel.canServe` 要求 `width ≥ 4000 && height ≥ 3000`，把设备交来的
 * `3072×4096`（竖置）判成"拍不了"；`decodeJpeg` 的转置分支又只认**严格互换**
 * （`3072×4096` 对 `4000×3000` 不算互换），于是同一台机器的同一帧，一处说
 * "比 pack 小"，另一处说"转置但没方向标记"。两个说法都不对：
 * 3072×4096 转 90° 就是 4096×3072，裁掉 96×72 正好是 4000×3000。
 */

/** 归一化成「长边×短边」。竖置帧与横置帧在"能不能服务"这件事上完全等价。 */
internal fun Size.landscapeNormalized(): Size =
    if (width >= height) this else Size(height, width)

/**
 * 这一帧能否服务 pack。
 *
 * 只回答**像素够不够**，不回答横竖 —— 竖置帧转 90° 后像素总数不变，
 * 所以方向不该参与这个判断（真机上正是这里把可用帧判死了）。
 */
internal fun frameServesPack(frame: Size, pack: Size): Boolean =
    frameServesPack(frame.width, frame.height, pack.width, pack.height)

/** 与 [frameServesPack] 同一口径的纯整数版本：不碰 `android.util.Size`，可在普通 JVM 单测里跑。 */
internal fun frameServesPack(
    frameWidth: Int,
    frameHeight: Int,
    packWidth: Int,
    packHeight: Int,
): Boolean {
    val frameLong = maxOf(frameWidth, frameHeight)
    val frameShort = minOf(frameWidth, frameHeight)
    val packLong = maxOf(packWidth, packHeight)
    val packShort = minOf(packWidth, packHeight)
    return frameLong >= packLong && frameShort >= packShort
}

/**
 * 竖置帧**归一化回传感器帧**所需的顺时针角度。注意：不是"转到正立"。
 *
 * 这两件事必须分清，混淆过一次就交出过一张躺倒的竖版照片（见 D-100）：
 * - 归一化：把竖着存的帧转回横向，让像素回到"相机芯片看到的样子"；
 * - 正立：在归一化之上再叠设备握持角（[deviceRotationDegrees]），落盘时做。
 *
 * 优先级：
 * 1. **EXIF 方向标记**：能读到就采信。标记说的是"转多少度才正立"，
 *    正立 = 握持角 ∘ 传感器帧，所以回传感器帧 = `标记角 − 握持角`。
 * 2. **`SENSOR_ORIENTATION` 推导**：EXIF 缺失时（真机实测确实会有）用安装角反推。
 *    ImageCapture 固定 `targetRotation = ROTATION_0`，CameraX 按
 *    `(sensorOrientation - 0 + 360) % 360` 顺时针转过像素，转回去就是
 *    `(360 - sensorOrientation) % 360`。
 * 3. 两条都拿不到 → `null`。调用方必须报错，**不许猜**：猜错就是一张 180° 倒着的照片。
 *
 * 只处理 90/270：180 与镜像不改变横竖关系，对"转置帧"没有意义 ——
 * 算出来是 0/180 时说明这一帧本来就横着，不该走这条路，返回 null 交给调用方判。
 */
internal fun unTransposeRotationOf(
    exifOrientation: Int,
    sensorMountRotation: Int?,
    deviceRotationDegrees: Int = 0,
): Int? {
    val markedUpright = when (exifOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
    if (markedUpright != 0) {
        val back = ((markedUpright - deviceRotationDegrees) % 360 + 360) % 360
        // 只有当反推结果确实是个 90 的奇数倍时才采信 EXIF；算出 0/180 说明
        // "EXIF 说的正立"与"这一帧是竖置的"自相矛盾（设备的标记与实际握持
        // 对不上），这时**必须继续往下走**去问传感器安装角，而不是就此返回
        // null 让整张照片失败。
        if (back == 90 || back == 270) return back
    }
    val mount = sensorMountRotation ?: return null
    if (mount != 90 && mount != 270) return null
    return (360 - mount) % 360
}

/**
 * 落盘该转多少度 —— 只允许修正"哪一头朝上"，**不许把横版构图翻成竖版**。
 *
 * @param frameIsLandscape 冲洗用的那一帧是不是横的（[com.pocketccd.prototype.camera.decodeJpeg]
 *   归一化之后的尺寸）。
 * @param requestedRotation 设备握持角（`(SENSOR_ORIENTATION - displayRotation)`）。
 *
 * ### 为什么需要这条硬约束（真机复现过）
 *
 * 取景页是 `SENSOR_LANDSCAPE` 锁死的横版界面，预览流也是 4:3 横置 —— 用户按下快门时
 * 看到的构图**永远是横的**，成品就只能是横的。可 `requestedRotation` 依赖
 * `Display.getRotation()`，而这个值在两种常见情况下会说谎：
 *
 * - 界面锁横版时，某些设备（尤其是俯拍、平放，或 `previewView.display` 取不到值而
 *   退回 `ROTATION_0`）报回设备自然方向的 0/180；
 * - 传感器安装角为 0/180 的机器（平板、部分国产机型）本身就是"竖着装"。
 *
 * 两种情况下 `(sensor - display)` 都会算出 90/270，而帧却是横的：冲洗完是
 * 4000×3000，落盘再转 90° 就变成 3000×4000 的**竖版照片**。用户在横版取景框里
 * 构的图，交出去却躺倒成竖的 —— 一加 13 上正常、另一台机器上必现，正是出在这里。
 *
 * 所以：横帧只接受 0/180（朝上还是朝下，由握持角决定，这一位当前是可信的）；
 * 90/270 一定意味着"握持角在说谎"，收回 0，绝不让它改变构图方向。
 *
 * 竖帧（帧本身就是竖的）不在此列：那种情况下 90/270 是正常输出。
 */
internal fun finalRotationFor(frameIsLandscape: Boolean, requestedRotation: Int): Int {
    val normalized = ((requestedRotation % 360) + 360) % 360
    if (!frameIsLandscape) return normalized
    return if (normalized == 90 || normalized == 270) 0 else normalized
}
