package com.pocketccd.prototype.camera

import android.util.Size
import com.pocketccd.prototype.fotufilm.FilmStock

/**
 * 一次快门之后的处理阶段。
 *
 * `Capturing → Developing → Saving → Done` 是正常路径；任何一步失败都进
 * [Failed] 并保留 [Failed.cause] 供成片页展示。成片页在 [Capturing] 之后
 * 就已经可见（而不是等冲洗完才跳转），否则 2–3 秒的冲洗过程会表现为
 * "按了快门没反应"。
 */
sealed interface CaptureState {
    data object Idle : CaptureState

    data class Capturing(val stock: FilmStock) : CaptureState

    data class Developing(val stock: FilmStock) : CaptureState

    data class Saving(val stock: FilmStock) : CaptureState

    data class Done(val photo: SavedPhoto) : CaptureState

    data class Failed(val message: String, val stock: FilmStock?) : CaptureState

    val isBusy: Boolean
        get() = this is Capturing || this is Developing || this is Saving
}

/** 相机会话的可用性。CameraX 绑定完成前是 [Pending]。 */
sealed interface CameraState {
    data object Pending : CameraState

    data class Bound(
        val supportedSizes: Set<Size>,
        val hasFlash: Boolean,
    ) : CameraState

    data class Unavailable(val reason: String) : CameraState
}

/**
 * 相机页真正需要的那一屏状态。
 *
 * [canShoot] 为 false 时 [blockReason] 一定会给出可读原因 —— 这个字段存在的
 * 唯一目的是让"为什么按不下去"永远有答案，而不是让快门静默失灵。
 */
data class CameraUiState(
    val stocks: List<FilmStock> = emptyList(),
    val activeStock: FilmStock? = null,
    val camera: CameraState = CameraState.Pending,
    val canShoot: Boolean = false,
    val blockReason: String? = null,
    /** 当前会话能给出的最大采集尺寸。没有能用的卷时，用它告诉用户这台设备能给多大。 */
    val largestSupported: Size? = null,
    /**
     * 下发给 CameraX 的目标采集尺寸 = 活跃卷的 pack 尺寸。
     *
     * 为什么只要知道是哪卷就一定下发、不再等"已验证能服务"：等验证过的旧逻辑在
     * 相机还是 [CameraState.Pending] 时传 null，于是先无约束绑一次、拿到真实尺寸后
     * 再按 pack 尺寸重绑一次 —— 每次冷启动/回取景页都绑两次，中间预览黑一下，
     * 而 `handle` 已置空、`canShoot` 仍是 true，按快门静默无反应。
     *
     * 下发时填的是 **pack 的尺寸**（不是设备实际尺寸）：CameraX 若有这一档就直接给
     * 精确尺寸，没有也会给最接近的更高档，多出来的部分由 `decodeJpeg` 中心裁切。
     * 设备连这个尺寸都给不了时，CameraX 退到最接近的一档，`decodeJpeg` 会用
     * "这台设备只能给 X×Y" 的明确失败信息挡住——绝不缩放。
     */
    val programmedSize: Size? = null,
)
