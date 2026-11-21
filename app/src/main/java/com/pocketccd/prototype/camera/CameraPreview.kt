package com.pocketccd.prototype.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.ExifInterface
import android.util.Size
import android.util.Log
import android.view.Surface
import android.view.ViewGroup
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 快门在 [CameraHandle.CAPTURE_TIMEOUT_MS] 内没拿到画面。
 *
 * **这不是"相机坏了"，而是"我们不想再等"**。真机上确实会遇到 `takePicture`
 * 的回调永远不来：按快门后一秒内切到别的 App、转屏重建 Activity、或者相机会话被
 * 系统抢走。CameraX 没有取消 `takePicture` 的 API，也没有超时 —— 没有这个兜底，
 * 界面会永远停在"正在冲洗"，`capturing` 标志永不释放，快门永久置灰，只能杀进程。
 */
class CaptureTimeoutException(
    seconds: Int,
) : RuntimeException("相机没有响应（${seconds} 秒内未返回画面），已重连相机。")

/**
 * 一张刚拍下、尚未冲洗的帧。
 *
 * [rotationDegrees] 是**成品需要顺时针旋转的角度**（0/90/180/270），不是 EXIF 标记。
 * 为什么必须由相机侧算出来：JPEG 的像素恒为传感器原始方向（见
 * [CameraHandle.takeJpeg]），而"正立"是相对于用户此刻的握持姿势的，
 * 只有拍照那一瞬间读得到 display rotation。
 *
 * [exif] 是**相机原始 JPEG 里真实读到**的四项硬件参数（P1-14）。成片管线会把
 * JPEG 解成 ARGB 再重新编码，`Bitmap.compress` 不写 EXIF —— 不在这一帧上顺手
 * 带走，这些参数就永久丢了。读不到是 null：成片页显示 `—`，绝不编数。
 */
data class CapturedFrame(
    val jpeg: ByteArray,
    val rotationDegrees: Int,
    val exif: PhotoExif? = null,
    /**
     * **按下快门那一刻**的墙钟时间（毫秒）。
     *
     * 不能在落盘时取 `System.currentTimeMillis()`：冲洗要 2–4 秒，那个时间戳会同时
     * 变成文件名、`DATE_TAKEN`、详情页的"拍摄时间"，跨分钟/跨天时用户能看出来。
     */
    val takenAt: Long = System.currentTimeMillis(),
    /**
     * 拍摄时的 `SENSOR_ORIENTATION`（硬件事实，通常 90 或 270）。
     *
     * 只在一种情况下用到：相机交来的帧是**竖置**（如 3072×4096）且 JPEG 里没有
     * 方向标记 —— 那时解码阶段要靠它反推该转多少度
     * （见 [unTransposeRotationOf]）。读不到是 null，解码就照实报错，不猜方向。
     */
    val sensorMountRotation: Int? = null,
)

/**
 * EV 补偿的真实范围（D-052）。
 *
 * 单位不统一是 CameraX 的 API 逼出来的：`setExposureCompensationIndex` 只收**整数
 * index**，而刻度上画的是 **EV**，两者差一个 `stepEv`（真机普遍 1/3，也有 1/6）。
 * 换算收在这里，界面不要自己乘 —— 一旦两处各算一次，某天换了 step 就只错一半。
 *
 * [minIndex] .. [maxIndex] 是**硬件报告的范围**，不是我们编的 `-2..+2`。
 */
data class ExposureProfile(
    val minIndex: Int,
    val maxIndex: Int,
    val stepEv: Float,
    /** 绑定时相机报告的当前 index。 */
    val index: Int,
) {
    fun evOf(index: Int): Float = index * stepEv
    val minEv: Float get() = evOf(minIndex)
    val maxEv: Float get() = evOf(maxIndex)
}

/**
 * 一次相机会话。由 [CameraPreview] 在绑定时产生，解绑时作废。
 *
 * 存在的意义是把 CameraX 挡在 UI 之外：Compose 层只拿到一个
 * `suspend () -> CapturedFrame`，ViewModel 因此不需要认识 CameraX。
 */
class CameraHandle internal constructor(
    private val imageCapture: ImageCapture,
    private val cameraControl: CameraControl,
    private val executor: Executor,
    /** 该镜头在 ImageCapture 用例下**支持的全部输出尺寸**，用于匹配 pack。 */
    val supportedSizes: Set<Size>,
    /** 该镜头是否有闪光灯。前置通常没有。 */
    val hasFlash: Boolean,
    /**
     * 取景器坐标 → 传感器坐标的换算器。必须是 `PreviewView.meteringPointFactory`：
     * 它知道 FIT_CENTER 的留边，手算会偏。
     */
    private val meteringPointFactory: () -> MeteringPointFactory,
    private val cameraInfo: CameraInfo,
    private val lensFacing: Int,
    /** 传感器安装角（`CameraCharacteristics.SENSOR_ORIENTATION`）。读不到时为 null。 */
    private val sensorOrientation: Int?,
    /** 拍照瞬间读当前显示方向，返回 `Surface.ROTATION_*`。 */
    private val displayRotation: () -> Int,
    /**
     * 该逻辑相机的镜头能力快照（D-088）。含档位表、原生长焦档、探测来源。
     * `source == NONE` 表示一条路都没走通（单摄 / 厂商裁掉 / 非 camera2 后端），
     * 此时界面必须退回数码档，不能凭空标一个光学倍率。
     */
    val lensCapability: LensCapability = LensCapability(),
) {
    /** 档位表的简写。旧调用点只关心"有哪几档"，不关心探测来源。 */
    val lensStops: List<LensStop> get() = lensCapability.stops
    /**
     * 这张会话的拍照回调线程。用进程级单线程，避免每次重绑新建线程。
     * 仅在 [takeJpeg] 里用；对焦回调仍走主线程 executor（要更新 UI 状态）。
     */
    private val captureExecutor: Executor get() = CAPTURE_EXECUTOR

    /**
     * 拍一张 JPEG，返回原始字节 + 方向补偿。
     *
     * 走内存回调而不是写文件：12MP 的 JPEG 只有 3–5MB，而多一次落盘再读回
     * 会在内存最紧张的时刻多占一份。EXIF 方向与尺寸适配（含中心裁切）由
     * [decodeJpeg] 负责。
     *
     * **为什么图像永远是横的**：ImageCapture 固定 `targetRotation = ROTATION_0`，
     * 为的是让像素尺寸恒等于 pack 声明的 4096×3072（D-037 要求精确相等）。
     * CameraX 在这个组合下不写 EXIF、也不旋转像素，于是竖持拍出来的成品侧躺 90°。
     * 方向因此分两步补：这里只算出该转多少度，真正旋转发生在**冲洗完成之后**
     * （`PhotoStore.save`），因为 pack 的空间参数绑定的是像素尺寸，不能在冲洗前转。
     */
    suspend fun takeJpeg(): CapturedFrame {
        val frame = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
            suspendCancellableCoroutine<CapturedFrame> { continuation ->
                // 角度和时间都必须在按下快门的瞬间取：用户可能拍完就转手机，
                // 冲洗要 2–3 秒，等到那时再读 display / 墙钟，拿到的是错的姿势和时间。
                val rotation = rotationDegrees()
                val takenAt = System.currentTimeMillis()
                imageCapture.takePicture(
                    // 专用后台线程，不是主线程：这里要做 3–5MB 的字节拷贝 + EXIF 解析，
                    // 挂在主线程会正好落在快门闪白那一帧（review 第二轮 P2#2）。
                    // 协程 resume 后仍回到调用方 dispatcher，UI 不受影响。
                    captureExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            try {
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                // P1-14：在原始字节还在手上时解析一次 EXIF（含焦距/光圈/
                                // 快门/ISO）。这里**绝不能抛**：拍摄是主链路，参数只是装饰。
                                // 空 EXIF（相机没写这几项）也归成 null，让成片页照实显示 —。
                                val exif = runCatching {
                                    readPhotoExif(bytes).takeUnless { it.isEmpty }
                                }.getOrNull()
                                if (exif == null) {
                                    Log.i(TAG, "拍摄帧没有可读 EXIF，成片页参数将显示 —")
                                }
                                if (continuation.isActive) {
                                    continuation.resume(
                                        CapturedFrame(
                                            jpeg = bytes,
                                            rotationDegrees = rotation,
                                            exif = exif,
                                            takenAt = takenAt,
                                            sensorMountRotation = sensorOrientation,
                                        ),
                                    )
                                }
                            } catch (error: Throwable) {
                                if (continuation.isActive) continuation.resumeWithException(error)
                            } finally {
                                // 超时后 continuation 已取消，但 ImageProxy 仍必须归还，
                                // 否则缓冲区不释放，下一次拍摄会直接卡住。
                                image.close()
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            if (continuation.isActive) continuation.resumeWithException(exception)
                        }
                    },
                )
            }
        }
        return frame ?: throw CaptureTimeoutException((CAPTURE_TIMEOUT_MS / 1000).toInt())
    }

    fun setFlash(on: Boolean) {
        imageCapture.flashMode = if (on && hasFlash) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
    }

    /**
     * 这条会话是否还挂在相机上。
     *
     * CameraX 没有公开的 `isBound`，但 `attachedSurfaceResolution` 在未 attach 时
     * 会抛 `IllegalStateException` —— 这是唯一可用的探针。真机上遇到过：
     * 进程被反复强停/重启后，相机会话被别的客户端抢走，预览变黑、`ImageCapture`
     * 失效，而界面仍然亮着 `LIVE`，按快门只会弹一句 "Not bound to a valid Camera"。
     *
     * 探针只在回到前台和按快门时调，不进每帧组合。
     */
    fun isAttached(): Boolean =
        runCatching { imageCapture.attachedSurfaceResolution }.isSuccess

    /**
     * 在取景器坐标系内点按对焦 / 测光。
     *
     * 坐标是**取景器 View 的像素**，不是屏幕像素：`PreviewView.meteringPointFactory`
     * 会把 FIT_CENTER 的留边算进去，所以上层只要把 `pointerInput` 拿到的
     * offset 原样传进来就不会偏。
     *
     * [onResult] 在主线程回调真实结果。界面据此显示 `AF LOCK` 或 `AF FAIL` ——
     * 设计稿的点按只是把光圈挪到手指下面，永远显示 `AF LOCK`；
     * 我们真的调了 `startFocusAndMetering`，就该照实说。
     */
    fun focusAt(x: Float, y: Float, onResult: (Boolean) -> Unit = {}) {
        val point = runCatching { meteringPointFactory().createPoint(x, y) }.getOrNull()
        if (point == null) {
            onResult(false)
            return
        }
        val action = FocusMeteringAction
            .Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            // 5 秒后自动取消：没有这一步，对焦点会永久锁在旧位置，
            // 用户移动手机后画面就一直是虚的。
            .setAutoCancelDuration(5, TimeUnit.SECONDS)
            .build()
        val future = runCatching { cameraControl.startFocusAndMetering(action) }.getOrNull()
        if (future == null) {
            onResult(false)
            return
        }
        future.addListener(
            {
                val ok = runCatching { future.get().isFocusSuccessful }.getOrDefault(false)
                Log.i(TAG, "tap to focus ($x,$y) -> $ok")
                onResult(ok)
            },
            executor,
        )
    }

    /**
     * 真实变焦。
     *
     * 设计稿的 2x 是 `transform: scale(1.28)` —— 只是把画面拉伸 1.28 倍，
     * 既没换镜头也没改采样，属于假变焦。这里走 `CameraControl.setZoomRatio`，
     * 由相机管线自己裁切/采样。**输出尺寸**仍由 ResolutionSelector 决定，
     * 所以 pack 要求的 W×H 不变（D-037 的尺寸这一半成立）。
     *
     * ### 但像素密度可能变 —— 这是 D-088 明确接受的代价
     *
     * 倍率跨过某颗长焦的视场时，HAL **会**真的切物理传感器（这正是"原生长焦"
     * 的含义）。而 pack 的扩散/晕光/颗粒半径是按主摄的 pixels-per-mm 烘焙的
     * （D-037/048）。主摄 1/1.4" 装 50MP 约 890 px/mm，73mm 长焦 1/1.95" 约 1240 px/mm，
     * 颗粒会细约 29%、晕光半径错位 —— 画质不崩，但物理上不再精确。
     *
     * 早先这里写过"走 setZoomRatio 时像素密度恒等于主摄"，那句是错的：
     * 逻辑相机的 `CONTROL_ZOOM_RATIO` 就是 HAL 换镜的开关。想彻底解决只能为
     * 长焦另产一套 pack（按该传感器的 px/mm 烘焙），现在没有。
     *
     * 返回**实际生效**的倍率（同步 clamp）。为什么不在绑定时就把上限存成常量：
     * `CameraInfo.zoomState` 是 LiveData，初始化时机不稳定 —— 实测同一台机器，
     * 有时绑定后 0ms 就有值，有时 500ms 还是 null。存成常量会导致某些启动里
     * 2x 直接变灰；改成"调用时现读"就永远是对的。
     */
    fun requestZoom(requested: Float): Float {
        val max = maxZoomRatio()
        val snapped = snapToOptical(requested)
        val applied = snapped.coerceIn(1f, max)
        runCatching { cameraControl.setZoomRatio(applied) }
            .onSuccess {
                val where = when {
                    applied < snapped - 0.01f -> "（被 max=${"%.2f".format(max)} 压低）"
                    snapped > 1f && applied >= opticalTeleRatio() -> "，已进入长焦视场"
                    else -> ""
                }
                Log.i(TAG, "zoom -> ${"%.2f".format(applied)}x (max=${"%.2f".format(max)})$where")
            }
        return applied
    }

    /**
     * 把请求倍率**吸附**到最近的物理镜头视场比（±8% 内）。
     *
     * 为什么需要：几何算出来的原生长焦是 2.86× 这种值，而用户/其他调用方可能传
     * 3.0× 这种"整数口径"。差 5% 在 HAL 看来就是"长焦上再做 1.05 倍数码裁切"——
     * 肉眼几乎看不出，但"原生"这两个字就没了。吸附保证点档位等于精确视场。
     *
     * ±8% 而不是 ±2%：3× 潜望的实测几何比常见 2.7–3.1，口径差距比想象中大。
     */
    private fun snapToOptical(requested: Float): Float {
        val optical = lensStops.filter { it.optical && it.zoomRatio >= 1f }
        if (optical.isEmpty()) return requested
        val nearest = optical.minByOrNull { kotlin.math.abs(it.zoomRatio - requested) }
            ?: return requested
        val tolerance = nearest.zoomRatio * 0.08f
        return if (kotlin.math.abs(nearest.zoomRatio - requested) <= tolerance) {
            nearest.zoomRatio
        } else {
            requested
        }
    }

    /** 原生长焦的视场比；没有长焦时返回 [Float.MAX_VALUE]（任何倍率都不算"进入长焦"）。 */
    private fun opticalTeleRatio(): Float =
        lensCapability.telephoto?.zoomRatio ?: Float.MAX_VALUE

    /** 该倍率是否已经跨进长焦那颗镜头的视场。界面用来决定要不要标"T"。 */
    fun zoomsIntoTelephoto(zoom: Float): Boolean =
        zoom >= opticalTeleRatio() * 0.95f

    /** 该镜头当前的最大变焦比。读不到就当 1x（不变焦）。 */
    fun maxZoomRatio(): Float =
        cameraInfo.zoomState.value?.maxZoomRatio?.coerceAtLeast(1f) ?: 1f

    /**
     * 曝光补偿的真实范围与步长；不支持时返回 null。
     *
     * **null 必须让界面把控件整个藏掉**，不能画一条拖不动的刻度：用户会以为是
     * 自己没拖对，而不是"这台机器没有这个功能"。这和 D-044 对变焦档位的处理是同一条规矩。
     *
     * `exposureState` 是同步读的（不是 LiveData），但相机解绑后再读可能抛异常，
     * 所以整段 `runCatching`。
     */
    fun exposureProfile(): ExposureProfile? = runCatching {
        val state = cameraInfo.exposureState
        if (!state.isExposureCompensationSupported) return@runCatching null
        val step = state.exposureCompensationStep.toFloat()
        val range = state.exposureCompensationRange
        // step <= 0 或上下限相同 = 这台机器其实没给范围，画刻度就是画假的。
        if (step <= 0f || range.lower >= range.upper) return@runCatching null
        ExposureProfile(
            minIndex = range.lower,
            maxIndex = range.upper,
            stepEv = step,
            index = state.exposureCompensationIndex,
        )
    }.getOrNull()

    /**
     * 设置 EV 补偿，参数是**档位 index**（`EV = index × stepEv`，见 [ExposureProfile]）。
     *
     * 返回实际生效的 index（已按硬件范围 clamp）；相机不在了返回 null。
     *
     * 为什么返回 clamp 后的值而不等相机回读：`setExposureCompensationIndex` 是异步的，
     * 立刻回读拿到的是旧值。界面按返回值先把指针挪到位，相机那边随后跟上 ——
     * 拖动时指针不能"卡一下再跳"。
     */
    fun requestExposure(index: Int): Int? {
        val profile = exposureProfile() ?: return null
        val applied = index.coerceIn(profile.minIndex, profile.maxIndex)
        runCatching { cameraControl.setExposureCompensationIndex(applied) }
            .onSuccess {
                Log.i(TAG, "ev -> index $applied (${"%.2f".format(profile.evOf(applied))} EV)")
            }
            .onFailure { Log.w(TAG, "ev 设置失败: ${it.message}") }
        return applied
    }

    /**
     * 成品需要顺时针旋转的度数。
     *
     * 用 Android 官方的传感器公式，而不是 CameraX 的 `sensorRotationDegrees`：
     * 后者在 `targetRotation = ROTATION_0` 时返回 0（它认为已经正立），
     * 与本机实测"像素是传感器原始方向"矛盾。`SENSOR_ORIENTATION` 是硬件事实，不会被推导规则影响：
     *
     * - 后置：`(sensor - device + 360) % 360`
     * - 前置：`(sensor + device) % 360`（前置是镜像的，方向相加而非相减）
     */
    private fun rotationDegrees(): Int {
        val sensor = sensorOrientation ?: run {
            Log.w(TAG, "读不到 SENSOR_ORIENTATION，成片方向不做补偿")
            return 0
        }
        val device = displayRotation() * 90
        val degrees = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            (sensor + device) % 360
        } else {
            (sensor - device + 360) % 360
        }
        Log.i(TAG, "capture rotation: sensor=$sensor display=${displayRotation()} -> $degrees°")
        return degrees
    }

    companion object {
        /**
         * 快门等画面的上限。
         *
         * 取 5 秒而不是 2 秒：12MP `CAPTURE_MODE_MAXIMIZE_QUALITY` 在慢机上单次
         * JPEG 编码偶尔要 1–2 秒，压太紧会把正常拍摄判成失败。5 秒足够覆盖正常
         * 路径，也足够短到"卡住"这件事还能被用户感知成一次失败而不是死机。
         */
        const val CAPTURE_TIMEOUT_MS = 5_000L

        /**
         * ImageCapture 的回调线程。进程内单线程、daemon，不随相机重绑反复创建。
         *
         * 为什么不能再用主线程 executor：`onCaptureSuccess` 里要 `buffer.get()` 拷贝
         * 3–5MB 的 JPEG 字节并解析 EXIF，正好压在快门闪白动画那一帧上；相机重绑
         * 频繁时主线程会被这些拷贝切碎。回调里没有 View 操作，放后台线程是安全的。
         */
        private val CAPTURE_EXECUTOR: Executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "pocketccd-capture").apply { isDaemon = true }
        }
    }
}

/**
 * CameraX 取景器。
 *
 * 只做绑定与解绑；闪光灯、快门都在外部通过 [CameraHandle] 驱动，这样相机
 * 状态不必在两个 Compose 层之间来回同步。
 *
 * @param lensFacing `CameraSelector.LENS_FACING_BACK` / `LENS_FACING_FRONT`
 * @param targetSize 期望的采集尺寸。**必须精确等于 pack 的尺寸**（D-037）：
 *   这里用 `CLOSEST_HIGHER_THEN_LOWER` 只是给 CameraX 一个排序依据，真正的
 *   保证来自调用方先用 [CameraHandle.supportedSizes] 确认该尺寸在列表里。
 *   传 null 表示不约束（还没有可用 pack 时）。
 */
@Composable
fun CameraPreview(
    lensFacing: Int,
    targetSize: Size?,
    modifier: Modifier = Modifier,
    /** 自增即强制重绑。相机被系统或其他客户端抢走后，靠它恢复。 */
    rebindNonce: Int = 0,
    onReady: (CameraHandle?) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // 回调每次重组都是新 lambda，用 rememberUpdatedState 保证 effect 里拿到最新的，
    // 又不会因为 lambda 变了就重新绑定相机。
    val readyState = rememberUpdatedState(onReady)
    val errorState = rememberUpdatedState(onError)

    val previewView = remember {
        PreviewView(context).apply {            // FIT_CENTER：完整显示 4:3 传感器画面。取景框比 4:3 高，所以上下会留一点
            // 边 —— 这是刻意的。用 FILL_CENTER 虽然铺满好看，但会裁掉左右，导致
            // "看到的构图"和"拍到的画面"不一致，而 FOT-009 的要求正是不许预览冒充成片。
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)

    /**
     * 上一次真正 attach 成功的采集尺寸。
     *
     * `attachedSurfaceResolution` 只给 40×25ms 的窗口，慢机/相机服务刚启动时会超时；
     * 超时后如果退回 `null`，[CameraHandle.supportedSizes] 就是空集，快门**永久**置灰
     * 且本轮页面不会再重试。留一份上次的值，至少让"上次能拍、这次也能拍"。
     */
    var lastAttached by remember { mutableStateOf<Size?>(null) }

    /**
     * 最近一次**真实读到**的显示旋转。
     *
     * `previewView.display` 在视图刚挂载 / 刚重绑的那一瞬间会是 null。那时要是当成
     * `ROTATION_0`，握持角就恒等于 `SENSOR_ORIENTATION`（90/270）：横版取景框里构的图，
     * 冲洗完 4000×3000，落盘再转 90° 就成了竖版。宁可沿用上一次的真值，也不要编一个 0。
     */
    val knownDisplayRotation = remember { intArrayOf(Surface.ROTATION_0) }

    LaunchedEffect(lensFacing, targetSize, rebindNonce) {
        readyState.value(null)
        runCatching {
            val provider = context.awaitCameraProvider()
            boundProvider = provider

            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            if (!provider.hasCamera(selector)) {
                error(
                    if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        "设备没有前置镜头"
                    } else {
                        "设备没有后置镜头"
                    }
                )
            }

            // 取景窗是 3:4（成片比例），所以预览流必须是 **4:3**：传感器横置、
            // 显示时转 90°，4:3 的流正好铺满 3:4 的窗，窗里不会再留一层黑边（D-080）。
            // 不写这一行，CameraX 可能给 16:9，窗里就会上下各切一条。
            val preview = Preview.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(
                            AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY,
                        )
                        .build(),
                )
                .build()
                .apply {
                    surfaceProvider = previewView.surfaceProvider
                }

            val capture = ImageCapture.Builder()
                // MAXIMIZE_QUALITY：这个模式不会在内存里长期保留一帧全尺寸缓冲，
                // 12MP 下比 MINIMIZE_LATENCY 安全得多。代价是快门后多几百毫秒，
                // 而冲洗本身就要 2–3 秒，这点延迟可以忽略。
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                // 固定 ROTATION_0：让 JPEG 的像素方向永远等于传感器自然方向。
                // 否则竖屏拍照时 CameraX 会把 4000×3000 转成 3000×4000，
                // 与 pack 声明的横向尺寸对不上（D-037 要求精确相等）。
                .setTargetRotation(Surface.ROTATION_0)
                .apply {
                    if (targetSize != null) {
                        setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        targetSize,
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                    ),
                                )
                                .build(),
                        )
                    }
                }
                .build()

            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner, selector, preview, capture,
            )

            // CameraX 没有"列出某用例支持的尺寸"的公开 API（CameraInfo 上不存在
            // getSupportedResolutions）。这里改用**实际选中的采集尺寸**来判断 ——
            // 它比支持列表更接近事实：如果 CameraX 把 4000×3000 换成了别的，
            // 这里立刻就能看出来，而不是等到拍完解码才发现。
            //
            // 绑定是异步的，attachedSurfaceResolution 要等 attach 之后才有值。
            var attached: Size? = null
            var attempts = 0
            while (attached == null && attempts < 40) {
                attached = runCatching { capture.attachedSurfaceResolution }.getOrNull()
                if (attached == null) {
                    delay(25)
                    attempts++
                }
            }

            val attachedSize = attached ?: targetSize ?: lastAttached
                // 三条都拿不到就别发布一个空尺寸集合：那会让快门永久置灰且本轮页面
                // 再也不会重试（LaunchedEffect 的 key 没变）。抛出去交给上层重绑。
                ?: error("相机没有报告可用的采集尺寸")
            lastAttached = attachedSize

            // 传感器安装角。这是硬件事实（通常 90 或 270），成片方向补偿全靠它 ——
            // CameraX 的 sensorRotationDegrees 在 targetRotation=ROTATION_0 时给 0，
            // 与本机实测"像素是传感器原始方向"矛盾，不能拿来用。
            val sensorOrientation = runCatching {
                Camera2CameraInfo.from(camera.cameraInfo)
                    .getCameraCharacteristic(CameraCharacteristics.SENSOR_ORIENTATION)
            }.getOrNull()

            // 设备真实的 JPEG 输出尺寸表，**只用于诊断**。
            //
            // CameraX 没有"列出某用例支持哪些尺寸"的公开 API，`attachedSurfaceResolution`
            // 只说这一轮落到了哪一档。它极容易被误读成"设备上限"——真机上就报过
            // "只能给 3840×2880"，而同一台机器另一轮又交出了 3072×4096 的帧。
            // 把 `SCALER_STREAM_CONFIGURATION_MAP` 里的 JPEG 档全打出来，
            // 下一次只需看一行日志就能分清"硬件真不够"和"协商没选对"。
            val jpegSizes = runCatching {
                @Suppress("DEPRECATION")
                Camera2CameraInfo.from(camera.cameraInfo)
                    .getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(ImageFormat.JPEG)
                    ?.map { "${it.width}x${it.height}" }
            }.getOrNull()
            Log.i(
                TAG,
                "jpeg sizes: " + (
                    jpegSizes?.takeIf { it.isNotEmpty() }
                        ?.joinToString(", ")
                        ?: "读不到（非 camera2 后端）"
                    ),
            )

            Log.i(
                TAG,
                "camera bound: lens=$lensFacing target=${targetSize?.let { "${it.width}x${it.height}" }} " +
                    "attached=${attached?.let { "${it.width}x${it.height}" }} " +
                    "effective=${attachedSize.width}x${attachedSize.height} " +
                    "sensorOrientation=$sensorOrientation " +
                    "flash=${camera.cameraInfo.hasFlashUnit()} waited=${attempts * 25}ms",
            )

            // D-088：把镜头能力探出来。先用 Camera2 的物理 id + 几何视场比，
            // 走不通再退回 CameraX 上报的 intrinsicZoomRatio。两条都不通就是空列表，
            // 界面看到空列表会退回 1x/2x 数码档，不会显示假的光学倍率。
            val capability = probeLenses(
                camera.cameraInfo,
                context.getSystemService(CameraManager::class.java),
            )
            Log.i(
                TAG,
                "lens capability: source=${capability.source} " +
                    "main=${capability.mainEquivalentFocalMm?.let { "${it}mm" } ?: "?"} " +
                    "tele=${capability.telephoto?.let { "${it.ratioLabel}@${it.focalLabel}" } ?: "none"} " +
                    "stops=${capability.describe()}",
            )

            readyState.value(
                CameraHandle(
                    imageCapture = capture,
                    cameraControl = camera.cameraControl,
                    executor = ContextCompat.getMainExecutor(context),
                    // 拿不到实际尺寸时退回 targetSize：宁可让用户按下去、由
                    // decodeJpeg 拿真实头像去校验，也不要在这里把快门锁死。
                    // 那种失败至少带着"实际 X 与 pack 的 Y 不一致"的可读信息。
                    supportedSizes = setOfNotNull(attachedSize),
                    hasFlash = camera.cameraInfo.hasFlashUnit(),
                    meteringPointFactory = { previewView.meteringPointFactory },
                    cameraInfo = camera.cameraInfo,
                    lensFacing = lensFacing,
                    sensorOrientation = sensorOrientation,
                    displayRotation = {
                        val fresh = previewView.display?.rotation
                        if (fresh != null) {
                            knownDisplayRotation[0] = fresh
                            fresh
                        } else {
                            Log.w(
                                TAG,
                                "读不到 display.rotation，沿用上一次 ${knownDisplayRotation[0]}",
                            )
                            knownDisplayRotation[0]
                        }
                    },
                    lensCapability = capability,
                ),
            )
        }.onFailure { error ->
            errorState.value(error.message ?: error.javaClass.simpleName)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // 先作废句柄再解绑：否则上层会留着一条指向已解绑 ImageCapture 的引用，
            // 按快门时才炸（真机日志 "Not bound to a valid Camera"）。
            readyState.value(null)
            runCatching { boundProvider?.unbindAll() }
            boundProvider = null
        }
    }
}

/**
 * 当前绑定的 provider。进程内只有一个相机，用模块级引用给 `onDispose` 解绑即可；
 * 不用 Compose 状态是为了避免 unbind 反过来触发重组。
 */
private var boundProvider: ProcessCameraProvider? = null

/** 手测时用 `adb logcat -s PocketCcdCamera` 就能看到实际采集尺寸。 */
private const val TAG = "PocketCcdCamera"

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        val executor = ContextCompat.getMainExecutor(this)
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { if (continuation.isActive) continuation.resume(it) }
                    .onFailure { if (continuation.isActive) continuation.resumeWithException(it) }
            },
            executor,
        )
    }

/**
 * 解码后、准备交给引擎冲洗的一帧。
 *
 * [sourceWidth] / [sourceHeight] 是相机实际交来的像素尺寸。它与 [bitmap] 不同时，
 * 可能是做过中心裁切，也可能只是整转过 —— 前者在 D-037 下仍然成立（像素密度不变），
 * 后者是像素一一对应，两者都不是缩放。
 */
data class DecodedFrame(
    val bitmap: Bitmap,
    val sourceWidth: Int,
    val sourceHeight: Int,
    /**
     * 解码阶段已经把像素转过的角度（0/90/180/270）。**只用于诊断**。
     *
     * 语义是「把这一帧**归一化回传感器帧**」——竖置帧转回横向，仅此而已。
     * 它**不是**"已经转到正立"：正立还要再叠上设备握持角
     * （`CapturedFrame.rotationDegrees`），那一步在落盘时做。
     *
     * 所以落盘**不能**减掉这个值。曾经减过，结果这台竖置输出的机型交出
     * 一张躺倒的竖版照片：归一化 270° + 设备角 0° 变成 360°，等于没转。
     */
    val appliedRotationDegrees: Int = 0,
    /**
     * 这一帧比 pack 声明尺寸小，按**原生尺寸**出片（D-098）。
     *
     * 不是错误，也不是降级到缩放：引擎按传入的行列数解算空间参数，小帧自洽。
     * 唯一的下游影响是成片尺寸随设备变，而不再恒等于 pack 的尺寸。
     */
    val undersized: Boolean = false,
) {
    /** 是否被中心裁切过。按「长边×短边」比，避免把 90° 整转误记成裁切。 */
    val cropped: Boolean
        get() {
            val source = Size(sourceWidth, sourceHeight).landscapeNormalized()
            val out = Size(bitmap.width, bitmap.height).landscapeNormalized()
            return source != out
        }

    /** 诊断行用。既没转也没裁、尺寸也够时返回 null，调用方就不打印。 */
    fun describeCrop(): String? {
        val parts = listOfNotNull(
            appliedRotationDegrees.takeIf { it != 0 }?.let { "rotate $it°" },
            if (cropped) "crop ${bitmap.width}x${bitmap.height}" else null,
            if (undersized) "undersized → ${bitmap.width}x${bitmap.height}（原生尺寸，未缩放）" else null,
        )
        if (parts.isEmpty()) return null
        return "${sourceWidth}x$sourceHeight → ${parts.joinToString(" + ")}"
    }
}

/**
 * JPEG 字节 → 交给引擎冲洗的一帧。
 *
 * 三条路径，共同点是**绝不缩放**：
 *
 * 1. **归一化后尺寸 == [target]**：直接用。设备有与 pack 精确匹配的档位时走这里。
 * 2. **归一化后 ≥ [target]**：中心裁切到 [target]。裁切不改变 pixels-per-mm，
 *    pack 的空间参数依然成立，代价只是取景范围变窄。
 * 3. **归一化后 < [target]**：**原生直通**，按帧自己的尺寸出片（[DecodedFrame.undersized]）。
 *
 * 竖置帧（如 3072×4096）先按 [unTransposeRotationOf] 整转 90° **回传感器帧**再判断；
 * 90° 是整转，像素总数不变，不是缩放。**这一步不负责让照片正立** —— 正立是落盘时
 * 叠上握持角才完成的（D-100），中间千万别在这里"顺手也转正"。
 *
 * ### 为什么可以裁切、可以转，但不能缩放
 *
 * pack 里的扩散 / 晕光 / 颗粒半径是「毫米 × 该帧的 pixels-per-mm」（D-037）。
 *
 * - 裁切不改变 pixels-per-mm：裁出来的还是同一块传感器、同一颗镜头，
 *   同样的物理长度依然对应同样的像素数，所以空间参数原封不动成立。
 * - 90° 旋转更是无损：像素一一对应，只是换了排列方向。
 * - 缩放会改 pixels-per-mm，等于换了一套光学系统：颗粒变粗或变细、
 *   晕光半径错位、MTF 曲线整体平移。出来的图"看起来像"，但物理上是假的。
 *
 * ### 尺寸不够时为什么不报错（D-098）
 *
 * native 的 `processFrame` 从不比对 `pack->width/height`，内核按**传入的行列数**
 * 解算 mm→px，所以小帧自洽：像素少 → 半径同比缩小 → 物理尺度不变。
 * 唯一真正该死的情况是"竖置且读不到方向"——那交出去就是躺着的照片，
 * 此时宁可失败也不猜（[undirectedPortraitMessage]）。
 *
 * [sensorMountRotation] 是拍摄时的 `SENSOR_ORIENTATION`。只有"竖置帧 + EXIF 缺
 * 方向标记"这一条路会用到它；给了 null 而 EXIF 又读不到时**宁可失败也不猜**。
 */
fun decodeJpeg(
    bytes: ByteArray,
    target: Size,
    stockLabel: String,
    sensorMountRotation: Int? = null,
    /**
     * 握持角（[CapturedFrame.rotationDegrees]）。只用于把 EXIF 的"转到正立"
     * 换算成"回传感器帧"；传感器推导那一路不需要它。
     */
    deviceRotationDegrees: Int = 0,
): DecodedFrame {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val rawWidth = bounds.outWidth
    val rawHeight = bounds.outHeight
    require(rawWidth > 0 && rawHeight > 0) { "JPEG 无法解析（${bytes.size} 字节）" }

    // 先把"方向"解开（归一回传感器帧），再拿**归一化后的横向尺寸**去比。
    // 竖置帧转 90° 后像素一个不少，直接拿 3072×4096 比 4000×3000 会得出
    // "比 pack 小"的错误结论（D-096）。
    //
    // 这一行日志是"设备到底交了什么"的唯一证据：不同厂商在 EXIF 标记、像素摆法、
    // SENSOR_ORIENTATION 上各不相同，出方向问题时先看它再改代码。
    val exifTag = exifOrientation(bytes)
    val rotation = if (rawWidth < rawHeight) {
        unTransposeRotationOf(exifTag, sensorMountRotation, deviceRotationDegrees)
    } else {
        null
    }
    Log.i(
        TAG,
        "frame ${rawWidth}x$rawHeight / exif=$exifTag / mount=${sensorMountRotation ?: "?"} " +
            "/ 握持=$deviceRotationDegrees → 归一化=${rotation ?: "无（本来就是横帧）"}",
    )
    val uprightWidth = if (rotation != null) rawHeight else rawWidth
    val uprightHeight = if (rotation != null) rawWidth else rawHeight

    // 情况 1：精确命中。真机上这是主路径（pack 尺寸与采集尺寸一致时）。
    if (uprightWidth == target.width && uprightHeight == target.height) {
        if (rotation == null) return DecodedFrame(decodeFull(bytes), rawWidth, rawHeight)
        val full = decodeFull(bytes)
        val upright = rotateExact(full, rotation)
        if (upright !== full) full.recycle()
        return DecodedFrame(upright, rawWidth, rawHeight, appliedRotationDegrees = rotation)
    }

    // 情况 2：转正后仍 ≥ pack → 中心裁切到 pack 尺寸。
    //
    // 旧版还要求"两个方向严格互换"才算转置（3072×4096 对 4000×3000 不算），
    // 方向又只信 EXIF —— 真机上这一帧转 90° 就是 4096×3072，裁 96×72 正好够用，
    // 却被判成"无方向标记、无法转正"。同一支卷在别的机器上能拍，只在这台被判死。
    if (uprightWidth >= target.width && uprightHeight >= target.height) {
        if (rotation != null) {
            val upright = cropRotatedCenter(bytes, rawWidth, rawHeight, target, rotation)
                ?: error("JPEG 解码失败（${bytes.size} 字节）")
            // 这里已经替成品转过了：把实际转过的角度带回去，否则落盘时还会再转一次
            // （两处都声称"转到正立"，叠起来就是多转 90/180°）。
            return DecodedFrame(upright, rawWidth, rawHeight, appliedRotationDegrees = rotation)
        }
        val cropped = cropCenter(bytes, rawWidth, rawHeight, target)
            ?: error("JPEG 解码失败（${bytes.size} 字节）")
        return DecodedFrame(cropped, rawWidth, rawHeight)
    }

    // 情况 3：转正后仍 < pack → **原生直通**，零几何处理（D-098）。
    //
    // 以前这里抛错，等于宣布"8MP 及以下的机型用不了这卷"。但 native 的 `processFrame`
    // 从头到尾没有比对过 `pack->width/height`，内核按**传入的行列数**解算
    // 「毫米 × 该帧 pixels-per-mm」。所以小帧是自洽的：像素少 → 半径同比缩小 →
    // 颗粒/晕光/扩散的物理尺度不变。
    //
    // 反过来，缩放到 pack 尺寸才是错的：那会改 pixels-per-mm（D-037 明令禁止），
    // 而且 8MP 放大到 12MP 里有一半像素是插值出来的。宁可交一张 3264×2448 的真图。
    if (rotation == null && rawWidth < rawHeight) {
        // 竖置且读不到方向：转不了正，交出去就是一张躺着的照片。仍然失败，不猜（D-096）。
        error(undirectedPortraitMessage(Size(rawWidth, rawHeight), target, stockLabel))
    }
    if (rotation == null) {
        return DecodedFrame(decodeFull(bytes), rawWidth, rawHeight, undersized = true)
    }
    val full = decodeFull(bytes)
    val upright = rotateExact(full, rotation)
    if (upright !== full) full.recycle()
    return DecodedFrame(
        bitmap = upright,
        sourceWidth = rawWidth,
        sourceHeight = rawHeight,
        appliedRotationDegrees = rotation,
        undersized = true,
    )
}

/**
 * 竖置帧 + 方向未知时给用户的交代。
 *
 * 这是 [decodeJpeg] **唯一**还会失败的尺寸类分支（D-098 之后，单纯"帧比 pack 小"
 * 已经走原生直通）。两条硬规则：
 *
 * 1. **只陈述事实，不给无效建议**。曾经写过"请重启相机后再试"—— 方向是确定性
 *    属性，重启不会让它多出方向标记，那句话把用户引向一个永远不会成功的动作。
 * 2. **说清是哪一环缺了**：EXIF 方向标记、`SENSOR_ORIENTATION`、还是两者都没有，
 *    否则真机上没有任何可追的线索。
 */
private fun undirectedPortraitMessage(
    raw: Size,
    target: Size,
    stockLabel: String,
): String {
    val frame = raw.landscapeNormalized()
    val pack = target.landscapeNormalized()
    return "「$stockLabel」需要横向的 ${pack.width}×${pack.height}，" +
        "但相机交来的这一帧是竖置的 ${raw.width}×${raw.height}，" +
        "读不到 EXIF 方向标记、也没有传感器安装角，无法转正。" +
        "转正后本应是 ${frame.width}×${frame.height}，像素够用，缺的只是方向。"
}

/**
 * 竖置帧：先在**原始竖帧**里抠出居中、尺寸为 `target.height × target.width` 的一块，
 * 再整转 [rotation] 度。
 *
 * 为什么不是"整帧转 90° 再裁"：12MP 的 ARGB 是 48MB，整转一次就是又一份 48MB，
 * 峰值会顶到 96MB 以上（引擎自己还要一份 float 平面）。裁切与旋转都以画面中心为基准，
 * 所以「先抠后转」与「先转后裁」结果完全一致，但峰值只多出目标尺寸那 48MB。
 */
private fun cropRotatedCenter(
    bytes: ByteArray,
    rawWidth: Int,
    rawHeight: Int,
    target: Size,
    rotation: Int,
): Bitmap? {
    // 竖帧里要抠的这一块，长宽与目标互换。
    val regionWidth = minOf(target.height, rawWidth)
    val regionHeight = minOf(target.width, rawHeight)
    val left = (rawWidth - regionWidth) / 2
    val top = (rawHeight - regionHeight) / 2

    val region = decodeRegion(bytes, left, top, regionWidth, regionHeight)
        ?: run {
            // 退化路径：渐进式 JPEG 等场景 region decoder 可能失败，
            // 整帧解码再抠。多占一份源图内存，但一定能成。
            val full = runCatching { decodeFull(bytes) }.getOrNull() ?: return null
            val cut = Bitmap.createBitmap(full, left, top, regionWidth, regionHeight)
            if (cut !== full) full.recycle()
            cut
        }

    // 90° 整转是像素一一对应：关滤波、关抗锯齿，一个字节都不插值（D-074）。
    val rotated = rotateExact(region, rotation)
    if (rotated !== region) region.recycle()

    if (rotated.width != target.width || rotated.height != target.height) {
        // 只在调用方已经校验过"转正后 ≥ target"时才会走到这里；对不上说明
        // 设备的实际像素与它自己报的边界不一致 —— 宁可失败，也不丢给下游一张错尺寸的图。
        rotated.recycle()
        return null
    }
    return rotated
}

/**
 * 整转（90/270）一张位图，像素一一对应、零重采样。
 *
 * 用 `Canvas` 而不是 `Bitmap.createBitmap(src, ..., matrix, false)`：两者在真机上等价，
 * 但后者的 matrix 重载在 Robolectric 图形栈下会返回一张全透明的空图 —— 那意味着
 * "方向对不对"这件事没法被单测钉住，只能靠真机肉眼。走 Canvas 后同一份代码
 * 在单测里能跑出真实像素。
 */
internal fun rotateExact(source: Bitmap, rotation: Int): Bitmap {
    // 画布按"交换宽高"开的，只对 90/270 成立：0/180 传进来会得到一张
    // 尺寸错位、内容被裁掉的空图（而且不会报错）。宁可当场炸掉。
    require(rotation == 90 || rotation == 270) {
        "rotateExact 只处理 90/270，收到 $rotation"
    }
    val out = Bitmap.createBitmap(
        source.height,
        source.width,
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(out)
    canvas.save()
    canvas.translate(out.width / 2f, out.height / 2f)
    canvas.rotate(rotation.toFloat())
    canvas.translate(-source.width / 2f, -source.height / 2f)
    canvas.drawBitmap(
        source,
        0f,
        0f,
        Paint().apply {
            isFilterBitmap = false
            isAntiAlias = false
        },
    )
    canvas.restore()
    return out
}

/** 只解一块区域。失败（渐进式 JPEG、头损坏）返回 null，由调用方退化。 */
@Suppress("DEPRECATION")
private fun decodeRegion(
    bytes: ByteArray,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
): Bitmap? = runCatching {
    // newInstance 在 JPEG 头损坏时返回 null，而不是抛异常。
    val decoder = BitmapRegionDecoder.newInstance(ByteArrayInputStream(bytes), false)
        ?: return@runCatching null
    try {
        decoder.decodeRegion(
            Rect(left, top, left + width, top + height),
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                // 与 decodeFull 同一个理由：解码必须先落到 sRGB，否则色彩链第一步就错。
                inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
                inScaled = false
            },
        )
    } finally {
        decoder.recycle()
    }
}.getOrNull()

private fun decodeFull(bytes: ByteArray): Bitmap =
    BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            // 固定按 sRGB 解码：`LinearColor.decodeInto` 的反 gamma 表就是 sRGB 表。
            // 不写这一行，带 Display P3 ICC 的 JPEG 会被解成 P3 位图，P3 的码值当成
            // sRGB 喂进 sRGB→Rec.2020，整条精确色彩链从第一步就错了。
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            inScaled = false
        },
    ) ?: error("JPEG 解码失败（${bytes.size} 字节）")

/**
 * 从 [bytes] 里抠出居中、尺寸恰为 [target] 的一块。
 *
 * 优先只解码目标区域：设备给 8000×6000 时整帧 ARGB 是 192MB，而裁出来只要 48MB。
 * JPEG 按 MCU 分块编码，解码器可以只处理覆盖这块矩形的那些块。
 * 失败（渐进式 JPEG 等）再退回"整帧解码后裁"，多占一份源图内存但一定能成。
 */
@Suppress("DEPRECATION")
private fun cropCenter(bytes: ByteArray, sourceWidth: Int, sourceHeight: Int, target: Size): Bitmap? {
    val left = (sourceWidth - target.width) / 2
    val top = (sourceHeight - target.height) / 2

    val region = runCatching {
        // newInstance 在 JPEG 头损坏时返回 null，而不是抛异常。
        val decoder = BitmapRegionDecoder.newInstance(ByteArrayInputStream(bytes), false)
            ?: return@runCatching null
        try {
            decoder.decodeRegion(
                Rect(left, top, left + target.width, top + target.height),
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    // 与 decodeFull 同一个理由：解码必须先落到 sRGB，否则色彩链第一步就错。
                    inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
                    inScaled = false
                },
            )
        } finally {
            decoder.recycle()
        }
    }.getOrNull()
    if (region != null) return region

    val full = runCatching { decodeFull(bytes) }.getOrNull() ?: return null
    return try {
        Bitmap.createBitmap(full, left, top, target.width, target.height)
    } finally {
        full.recycle()
    }
}

private fun exifOrientation(bytes: ByteArray): Int = runCatching {
    ExifInterface(ByteArrayInputStream(bytes))
        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
