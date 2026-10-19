package com.pocketccd.prototype.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.media.ExifInterface
import android.util.Size
import android.util.Log
import android.view.Surface
import android.view.ViewGroup
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 一张刚拍下、尚未冲洗的帧。
 *
 * [rotationDegrees] 是**成品需要顺时针旋转的角度**（0/90/180/270），不是 EXIF 标记。
 * 为什么必须由相机侧算出来：JPEG 的像素恒为传感器原始方向（见
 * [CameraHandle.takeJpeg]），而"正立"是相对于用户此刻的握持姿势的，
 * 只有拍照那一瞬间读得到 display rotation。
 */
data class CapturedFrame(
    val jpeg: ByteArray,
    val rotationDegrees: Int,
)

/**
 * 一次相机会话。由 [CameraPreview] 在绑定时产生，解绑时作废。
 *
 * 存在的意义是把 CameraX 挡在 UI 之外：Compose 层只拿到一个
 * `suspend () -> CapturedFrame`，ViewModel 因此不需要认识 CameraX。
 */
class CameraHandle internal constructor(
    private val imageCapture: ImageCapture,
    private val executor: Executor,
    /** 该镜头在 ImageCapture 用例下**支持的全部输出尺寸**，用于匹配 pack。 */
    val supportedSizes: Set<Size>,
    /** 该镜头是否有闪光灯。前置通常没有。 */
    val hasFlash: Boolean,
    private val lensFacing: Int,
    /** 传感器安装角（`CameraCharacteristics.SENSOR_ORIENTATION`）。读不到时为 null。 */
    private val sensorOrientation: Int?,
    /** 拍照瞬间读当前显示方向，返回 `Surface.ROTATION_*`。 */
    private val displayRotation: () -> Int,
) {
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
    suspend fun takeJpeg(): CapturedFrame = suspendCancellableCoroutine { continuation ->
        // 角度必须在按下快门的瞬间取：用户可能拍完就转手机，
        // 冲洗要 2–3 秒，等到那时再读 display 会拿到错的姿势。
        val rotation = rotationDegrees()
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        if (continuation.isActive) continuation.resume(CapturedFrame(bytes, rotation))
                    } catch (error: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            },
        )
    }

    fun setFlash(on: Boolean) {
        imageCapture.flashMode = if (on && hasFlash) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
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
        PreviewView(context).apply {
            // FIT_CENTER：完整显示 4:3 传感器画面。取景框比 4:3 高，所以上下会留一点
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

    LaunchedEffect(lensFacing, targetSize) {
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

            val preview = Preview.Builder().build().apply {
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

            val attachedSize = attached ?: targetSize

            // 传感器安装角。这是硬件事实（通常 90 或 270），成片方向补偿全靠它 ——
            // CameraX 的 sensorRotationDegrees 在 targetRotation=ROTATION_0 时给 0，
            // 与本机实测"像素是传感器原始方向"矛盾，不能拿来用。
            val sensorOrientation = runCatching {
                Camera2CameraInfo.from(camera.cameraInfo)
                    .getCameraCharacteristic(CameraCharacteristics.SENSOR_ORIENTATION)
            }.getOrNull()

            Log.i(
                TAG,
                "camera bound: lens=$lensFacing target=${targetSize?.let { "${it.width}x${it.height}" }} " +
                    "attached=${attached?.let { "${it.width}x${it.height}" }} " +
                    "effective=${attachedSize?.let { "${it.width}x${it.height}" }} " +
                    "sensorOrientation=$sensorOrientation " +
                    "flash=${camera.cameraInfo.hasFlashUnit()} waited=${attempts * 25}ms",
            )

            readyState.value(
                CameraHandle(
                    imageCapture = capture,
                    executor = ContextCompat.getMainExecutor(context),
                    // 拿不到实际尺寸时退回 targetSize：宁可让用户按下去、由
                    // decodeJpeg 拿真实头像去校验，也不要在这里把快门锁死。
                    // 那种失败至少带着"实际 X 与 pack 的 Y 不一致"的可读信息。
                    supportedSizes = setOfNotNull(attachedSize),
                    hasFlash = camera.cameraInfo.hasFlashUnit(),
                    lensFacing = lensFacing,
                    sensorOrientation = sensorOrientation,
                    displayRotation = { previewView.display?.rotation ?: Surface.ROTATION_0 },
                ),
            )
        }.onFailure { error ->
            errorState.value(error.message ?: error.javaClass.simpleName)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
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
 * 解码后、尺寸**精确等于** pack 声明值的一帧。
 *
 * [sourceWidth] / [sourceHeight] 是相机实际交来的像素尺寸。与 [bitmap] 不同时
 * 说明做过中心裁切 —— 这不是差异，而是让同一份 pack 能服务多种机型的唯一办法。
 */
data class DecodedFrame(
    val bitmap: Bitmap,
    val sourceWidth: Int,
    val sourceHeight: Int,
) {
    val cropped: Boolean
        get() = sourceWidth != bitmap.width || sourceHeight != bitmap.height

    /** 诊断行用。没有裁切时返回 null，调用方就不打印。 */
    fun describeCrop(): String? = if (!cropped) null else {
        "${sourceWidth}x$sourceHeight → crop ${bitmap.width}x${bitmap.height}"
    }
}

/**
 * JPEG 字节 → 尺寸精确等于 [target] 的 Bitmap。
 *
 * 三种情况，共同点是**绝不缩放**：
 *
 * 1. **采集尺寸 == target**：直接用，不看 EXIF。CameraX 在 `targetRotation=ROTATION_0`
 *    下不写方向标记；跟着 EXIF 转反而会把好好的横向图转成竖的。
 * 2. **采集尺寸是 target 的转置**（竖置）：按 EXIF 转回横置。这是 90° 整转，
 *    像素总数不变，不是缩放。
 * 3. **两个方向都 ≥ target**：中心裁切。
 *
 * ### 为什么可以裁切，但不能缩放
 *
 * pack 里的扩散 / 晕光 / 颗粒半径是「毫米 × 该帧的 pixels-per-mm」（D-037）。
 *
 * - 裁切不改变 pixels-per-mm：裁出来的还是同一块传感器、同一颗镜头，
 *   同样的物理长度依然对应同样的像素数，所以空间参数原封不动成立，代价只是
 *   取景范围变窄（等同于换了个裁切系数）。
 * - 缩放会改 pixels-per-mm，等于换了一套光学系统：颗粒变粗或变细、
 *   晕光半径错位、MTF 曲线整体平移。出来的图"看起来像"，但物理上是假的。
 *
 * ### 尺寸不够时
 *
 * 直接失败，并在消息里同时给出「设备能给什么」和「这卷要什么」——
 * 这是唯一能让用户自己判断该换哪卷的信息。静默出一张缩放图比失败糟得多。
 */
fun decodeJpeg(bytes: ByteArray, target: Size, stockLabel: String): DecodedFrame {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val rawWidth = bounds.outWidth
    val rawHeight = bounds.outHeight
    require(rawWidth > 0 && rawHeight > 0) { "JPEG 无法解析（${bytes.size} 字节）" }

    // 情况 1：精确命中。真机上这是唯一走到的主路径。
    if (rawWidth == target.width && rawHeight == target.height) {
        return DecodedFrame(decodeFull(bytes), rawWidth, rawHeight)
    }

    // 情况 2：竖置。只在两个方向恰好互换时才尝试，避免把 16:9 之类的
    // 非常规尺寸误当成"转一下就行"。
    if (rawWidth == target.height && rawHeight == target.width) {
        val rotated = decodeFull(bytes)
        val upright = applyExifOrientation(rotated, exifOrientation(bytes))
        if (upright !== rotated) rotated.recycle()
        if (upright.width == target.width && upright.height == target.height) {
            return DecodedFrame(upright, rawWidth, rawHeight)
        }
        upright.recycle()
    }

    // 情况 3：中心裁切。
    if (rawWidth >= target.width && rawHeight >= target.height) {
        val cropped = cropCenter(bytes, rawWidth, rawHeight, target)
            ?: error("JPEG 解码失败（${bytes.size} 字节）")
        return DecodedFrame(cropped, rawWidth, rawHeight)
    }

    error(
        "「$stockLabel」需要 ${target.width}×${target.height}，" +
            "这台设备的采集尺寸只有 ${rawWidth}×${rawHeight}。" +
            "请换一卷尺寸更小的胶卷。",
    )
}

private fun decodeFull(bytes: ByteArray): Bitmap =
    BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
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

private fun applyExifOrientation(source: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> return source
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}
