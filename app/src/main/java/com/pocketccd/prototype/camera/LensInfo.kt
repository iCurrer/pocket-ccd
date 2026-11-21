package com.pocketccd.prototype.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 一颗**物理镜头**在逻辑相机里的档位（D-044）。
 *
 * 这里是"这颗镜头对应多少倍"，不是"当前正在用哪颗" —— 后者没有公开接口，
 * 详见 [probeLenses] 的说明。
 */
data class LensStop(
    /** 相对主摄的倍率。几何视场比（见 [probeLenses]）或厂商上报的 intrinsicZoomRatio。 */
    val zoomRatio: Float,
    /**
     * 35mm 等效焦距。由物理焦距 × 43.2666 ÷ 传感器对角线现算，
     * 读不到传感器尺寸时为 null（该档只显示倍率）。
     */
    val equivalentFocalMm: Int? = null,
    /**
     * 这一档是否对应一块**真实成像的传感器**。
     *
     * `false` 表示它是界面兜底画出来的数码档（单摄机 / 探不到物理镜头时的 1×/2×），
     * 点下去只是 `setZoomRatio` 裁切，不涉及换镜。界面据此决定要不要标 `T`。
     */
    val optical: Boolean = false,
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
 * 低于这个倍率的不算长焦 —— 1.4× 是"中焦裁切"与"独立长焦"的分界经验值。
 * 2× 档在多数机型上是主摄中心裁切（同传感器、数码变焦），不是换镜。
 */
private const val MIN_TELEPHOTO_RATIO = 1.4f

/** 超过这个倍率的"物理镜头"基本是厂商报送的假数据（或潜望 + 数码混合），不认。 */
private const val MAX_STOP_RATIO = 15f

/** 超广角的下界：比 1× 广的镜头是另一块传感器，pack 不成立，一律过滤（D-037）。 */
private const val MIN_STOP_RATIO = 0.95f

/**
 * 档位倍率的量化步长。0.05 足以吃掉浮点误差（2.9999 → 3.00），
 * 又不会把 3.0 和 3.2 这种真实相邻的两颗长焦揉成一档。
 */
private const val RATIO_STEP = 0.05f

/**
 * 长焦探测的结果从哪来 —— 这是**可信度等级**，不是实现细节。
 *
 * 界面和日志都要能分辨"厂商 HAL 亲自报的"和"我们从焦距算出来的"，
 * 因为两者在真机上成功率差别很大。
 */
enum class LensProbeSource {
    /**
     * CameraX `physicalCameraInfos` 里每颗镜头的 `intrinsicZoomRatio`。
     *
     * 最省事的一条路，但实测在部分国产 ROM 上整组恒为 1.0（厂商没填
     * `CONTROL_ZOOM_RATIO_RANGE`），或者 `isLogicalMultiCameraSupported()` 直接给 false。
     */
    CAMERAX_PHYSICAL,

    /**
     * Camera2 `LOGICAL_MULTI_CAMERA_PHYSICAL_IDS` + 每颗物理相机的焦距/传感器尺寸现算。
     *
     * 绕开厂商上报口径，只要 HAL 愿意把物理 id 列出来就能算。**这是主路径**。
     */
    CAMERA2_IDS,

    /** 一条都没探到。界面必须退回数码档，不能凭空标一个光学倍率。 */
    NONE,
}

/**
 * 相机的镜头能力快照。在**绑定那一刻**读一次，随 [CameraHandle] 一起交出去。
 *
 * 之所以要包一层而不是直接给 `List<LensStop>`：「这台机器有没有长焦」是一个
 * 三态问题（有 / 没有 / 探不出来），只给列表会把后两者混为一谈 ——
 * 界面就没法区分"这台机是单摄"和"我们没读到"。
 */
data class LensCapability(
    val stops: List<LensStop> = emptyList(),
    /** 主长焦档（最短的那颗 ≥1.4× 物理镜头）。null = 没有或没探到。 */
    val telephoto: LensStop? = null,
    val source: LensProbeSource = LensProbeSource.NONE,
    /** 主摄的 35mm 等效焦距，长焦倍率的换算基准。读不到为 null。 */
    val mainEquivalentFocalMm: Int? = null,
) {
    val hasTelephoto: Boolean get() = telephoto != null

    /** 诊断行：`3×/70mm·光学`, `1×`, `2×·数码`。日志与设置页共用同一份措辞。 */
    fun describe(): String = if (stops.isEmpty()) {
        "none（退回数码档）"
    } else {
        stops.joinToString("，") { stop ->
            buildString {
                append(stop.ratioLabel)
                stop.focalLabel?.let { append("/").append(it) }
                if (!stop.optical) append("·数码")
            }
        }
    }
}

/**
 * 读出后置逻辑相机底下的真实光学档位，并判定哪一档是原生长焦。
 *
 * ### 为什么不再只走 CameraX
 *
 * 旧实现（D-044）只读 `CameraInfo.getPhysicalCameraInfos()` 的
 * `intrinsicZoomRatio`。这条路依赖两件厂商未必配合的事：
 *
 * 1. `isLogicalMultiCameraSupported()` 必须为 true —— 部分 ROM 会直接给 false；
 * 2. 每颗物理相机必须上报 `CONTROL_ZOOM_RATIO_RANGE` —— 没报时
 *    `intrinsicZoomRatio` 整组退化成 1.0，去重后只剩一档 1×，
 *    界面上表现为"这台手机没有长焦"。
 *
 * 两条都不满足时，用户手里明明有 3× 潜望，App 只给 1×/2× 数码。
 *
 * ### 主路径：几何视场比
 *
 * 35mm 等效焦距的定义就是视场角，所以**倍率 = 等效焦距之比**：
 *
 * ```
 * eq = f × 43.2666 / √(sw² + sh²)          // 单颗镜头的等效焦距
 * ratio = eq(长焦) / eq(主摄)              // 相对主摄的倍率
 * ```
 *
 * 必须用等效焦距而不是裸焦距：长焦传感器普遍比主摄小（1/2.55" vs 1/1.4"），
 * 直接比 `f` 会把 3× 算成 2.2×，点过去就停在主摄的数码裁切上。
 *
 * ### 也算不出来的情况
 *
 * `LOGICAL_MULTI_CAMERA_PHYSICAL_IDS` 也读不到（单摄、外接 USB 相机、
 * 非 camera2 后端）。此时返回 [LensProbeSource.NONE] 与空列表，
 * 调用方必须退回数码档 —— **不要凭空造光学倍率**。
 *
 * ### 已知边界：这不等于"强制换镜"
 *
 * 我们只是把 `setZoomRatio` 设到该物理镜头的精确视场比。真正切不切物理传感器
 * 由 HAL 决定，CameraX 与 Camera2 都不公开"当前在用的物理相机"。
 * 想验证只能读成片的 EXIF 焦距（主摄约 6mm、长焦约 16mm），
 * 见 [PhotoExif.focalLengthMm]。
 */
fun probeLenses(cameraInfo: CameraInfo, cameraManager: CameraManager?): LensCapability {
    // ---- 主路径：Camera2 物理 id + 几何视场比 ----
    val direct = runCatching { readCamera2Lenses(cameraInfo, cameraManager) }.getOrNull()
    if (direct != null && direct.stops.size >= 2) {
        val stops = tidyLensStops(direct.stops)
        return LensCapability(
            stops = stops,
            telephoto = pickTelephotoLens(stops),
            source = LensProbeSource.CAMERA2_IDS,
            mainEquivalentFocalMm = direct.mainEq.roundToInt(),
        )
    }

    // ---- 兜底：CameraX 上报的 intrinsicZoomRatio ----
    val physical = readCameraXStops(cameraInfo)
    val stops = tidyLensStops(physical)
    return LensCapability(
        stops = stops,
        telephoto = pickTelephotoLens(stops),
        source = if (stops.isEmpty()) LensProbeSource.NONE else LensProbeSource.CAMERAX_PHYSICAL,
        mainEquivalentFocalMm = stops
            .firstOrNull { abs(it.zoomRatio - 1f) < RATIO_STEP }
            ?.equivalentFocalMm,
    )
}

/** Camera2 直读的一批候选镜头（含主摄），[mainEq] 是主摄等效焦距。 */
private class DirectLenses(val mainEq: Float, val stops: List<LensStop>)

/**
 * 用 `CameraCharacteristics.getPhysicalCameraIds()` 把逻辑相机拆成物理镜头，
 * 逐颗算等效焦距。
 *
 * 返回 null（而不是空列表）表示**这条路走不通**，让上层的兜底分支能区分
 * "没有长焦"和"没读到"。
 */
private fun readCamera2Lenses(cameraInfo: CameraInfo, manager: CameraManager?): DirectLenses? {
    if (manager == null) return null

    val logicalId = runCatching { Camera2CameraInfo.from(cameraInfo).cameraId }.getOrNull()
        ?: return null
    val logical = runCatching { manager.getCameraCharacteristics(logicalId) }.getOrNull()
        ?: return null

    // 逻辑相机自己报的焦距/传感器尺寸就是主摄的那套 —— 这是倍率的基准。
    val mainEq = logical.equivalentFocalMm() ?: return null

    // 用 getPhysicalCameraIds()（API 28 公开）而不是 LOGICAL_MULTI_CAMERA_PHYSICAL_IDS
    // —— 后者是 @SystemApi，android.jar 里根本没有这个字段，直接引用编译不过。
    // 两者读的是同一份 HAL 数据：逻辑相机底下的物理相机 id 集合。
    val physicalIds = runCatching { logical.physicalCameraIds }.getOrNull()
    // 空集 / 只有一个 id 说明这台机器不是逻辑多摄，这条路无意义，交给兜底。
    if (physicalIds == null || physicalIds.size < 2) return null

    val facing = logical.get(CameraCharacteristics.LENS_FACING)

    val lenses = physicalIds.mapNotNull { id ->
        // 逻辑 id 自己会出现在列表里，跳过（主摄在下面显式补）。
        if (id == logicalId) return@mapNotNull null

        // 隐藏 id / 权限不足时 getCameraCharacteristics 会抛 IllegalArgumentException。
        val chars = runCatching { manager.getCameraCharacteristics(id) }.getOrNull()
            ?: return@mapNotNull null

        if (facing != null && chars.get(CameraCharacteristics.LENS_FACING) != facing) {
            return@mapNotNull null
        }

        // 深度 / 辅助镜头通常没有光圈 —— 它们根本不成像，收进来就是假档位。
        val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
        if (apertures == null || apertures.isEmpty()) return@mapNotNull null

        chars.lensStopOrNull(mainEq)
    }

    // 主摄必须显式补进来：它可能以逻辑 id 出现（上面跳过了），也可能是列表外的默认相机。
    return DirectLenses(
        mainEq = mainEq,
        stops = lenses + LensStop(1f, mainEq.roundToInt(), optical = true),
    )
}

/** 旧路径：CameraX 上报的每颗物理镜头自带 `intrinsicZoomRatio`。 */
private fun readCameraXStops(cameraInfo: CameraInfo): List<LensStop> {
    val supported = runCatching { cameraInfo.isLogicalMultiCameraSupported() }
        .getOrDefault(false)
    if (!supported) return emptyList()

    return runCatching { cameraInfo.physicalCameraInfos }.getOrNull()
        ?.toList()
        .orEmpty()
        .mapNotNull { it.toLensStop() }
}

private fun CameraInfo.toLensStop(): LensStop? {
    val ratio = runCatching { intrinsicZoomRatio }.getOrNull() ?: return null
    if (ratio < MIN_STOP_RATIO || ratio > MAX_STOP_RATIO) return null

    // Camera2CameraInfo 才是 CameraCharacteristics 的桥；它自己不是 Characteristics，
    // 不能直接调上面的扩展，要先用 getCameraCharacteristic 取字段。
    val info = runCatching { Camera2CameraInfo.from(this) }.getOrNull() ?: return null
    val focal = info.getCameraCharacteristic(
        CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS,
    )?.filter { it > 0f }?.minOrNull() ?: return null
    val size = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        ?: return null
    val eq = equivalentFocalMmOf(focal, size.width, size.height) ?: return null

    return LensStop(zoomRatio = ratio, equivalentFocalMm = eq.roundToInt(), optical = true)
}

/**
 * 一颗物理相机的 35mm 等效焦距（mm）。
 *
 * 焦距取最短的那个：`LENS_INFO_AVAILABLE_FOCAL_LENGTHS` 在少数机型上是多值
 * （变焦镜头 / 逻辑相机聚合），最短焦距对应最广视场，与该相机的主用途一致。
 * 读不到传感器物理尺寸时返回 null —— 没有基准就没法换算，只能放弃这一颗。
 */
private fun CameraCharacteristics.equivalentFocalMm(): Float? {
    val focal = get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        ?.filter { it > 0f }
        ?.minOrNull()
        ?: return null
    val size = get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
    return equivalentFocalMmOf(focal, size.width, size.height)
}

/** 一颗物理相机相对主摄的档位。焦距或传感器尺寸缺失时返回 null。 */
private fun CameraCharacteristics.lensStopOrNull(mainEq: Float): LensStop? {
    val focal = get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        ?.filter { it > 0f }
        ?.minOrNull()
        ?: return null
    val size = get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
    return lensStopOf(focal, size.width, size.height, mainEq)
}

/**
 * 等效焦距的纯算术部分，抽出来是为了能单测。
 *
 * `eq = f × 43.2666 / √(w² + h²)`，36×24 画幅对角线是换算基准。
 * 落在 8–300mm 之外的一律判 null：8mm 以下是鱼眼/微距/深度辅助（本来就不成像），
 * 300mm 以上是厂商报送的假数据。这两种混进档位表，用户点过去只会得到黑屏或糊片。
 */
internal fun equivalentFocalMmOf(focalMm: Float, sensorWidthMm: Float, sensorHeightMm: Float): Float? {
    if (focalMm <= 0f || sensorWidthMm <= 0f || sensorHeightMm <= 0f) return null
    val diagonal = hypot(sensorWidthMm, sensorHeightMm)
    if (diagonal <= 0f) return null
    val equivalent = focalMm * FULL_FRAME_DIAGONAL_MM / diagonal
    return if (equivalent < 8f || equivalent > 300f) null else equivalent
}

/**
 * 由「焦距 + 传感器尺寸 + 主摄等效焦距」算一颗镜头的档位。
 *
 * 倍率用**等效焦距之比**而不是裸焦距比：长焦传感器普遍比主摄小
 * （1/2.55" vs 1/1.4"），直接比 `f` 会把 3× 算成 2.2×，点过去就停在主摄的
 * 数码裁切上 —— 这正是"明明有长焦却切不过去"最常见的成因。
 */
internal fun lensStopOf(
    focalMm: Float,
    sensorWidthMm: Float,
    sensorHeightMm: Float,
    mainEquivalentFocalMm: Float,
): LensStop? {
    if (mainEquivalentFocalMm <= 0f) return null
    val equivalent = equivalentFocalMmOf(focalMm, sensorWidthMm, sensorHeightMm) ?: return null
    val ratio = quantizeZoomRatio(equivalent / mainEquivalentFocalMm)
    if (ratio < MIN_STOP_RATIO || ratio > MAX_STOP_RATIO) return null
    return LensStop(zoomRatio = ratio, equivalentFocalMm = equivalent.roundToInt(), optical = true)
}

/** 把倍率量化到 [RATIO_STEP]，消掉 2.9999 这种浮点噪声，好让界面按精确值命中。 */
internal fun quantizeZoomRatio(raw: Float): Float =
    (raw / RATIO_STEP).roundToInt() * RATIO_STEP

/**
 * 去重、排序、限长、补 1×。
 *
 * - 去重：同一颗镜头可能以多个 camera id 出现（厂商的 multi-camera 别名），倍率相同只留一个；
 * - 限长：档位太多时最多留 4 个，但**不能砍掉最长焦** —— 老写法 `take(4)`
 *   在五档机型上恰好丢掉 10×，用户点不到长焦。保留最短 3 档 + 最长一档；
 * - 补 1×：主摄缺失会让档位表看起来像"没有 1×"，补一个纯倍率档，
 *   总比少一档让人以为相机坏了强。
 */
internal fun tidyLensStops(raw: List<LensStop>): List<LensStop> {
    val stops = raw
        .filter { it.zoomRatio >= MIN_STOP_RATIO && it.zoomRatio <= MAX_STOP_RATIO }
        .distinctBy { (it.zoomRatio * 100f).roundToInt() }
        .sortedBy { it.zoomRatio }
        .let { sorted -> if (sorted.size <= 4) sorted else sorted.take(3) + sorted.last() }

    if (stops.isEmpty()) return emptyList()
    return if (stops.none { abs(it.zoomRatio - 1f) < RATIO_STEP }) {
        (stops + LensStop(1f)).sortedBy { it.zoomRatio }
    } else {
        stops
    }
}

/**
 * 主长焦 = 最短的那颗 ≥[MIN_TELEPHOTO_RATIO] 的物理镜头。
 *
 * 取最短而不是最长：多长焦机型（3× + 5× 潜望）上，"长焦"这个按钮该落到
 * 那颗画质与构图的日常平衡点，3× 通常才是。要最长焦的档位在列表里也点得到。
 *
 * 只认 [LensStop.optical]：数码档即使倍率够大也不算长焦 —— 那是裁切，不是换镜。
 */
internal fun pickTelephotoLens(stops: List<LensStop>): LensStop? =
    stops.firstOrNull { it.optical && it.zoomRatio >= MIN_TELEPHOTO_RATIO }
