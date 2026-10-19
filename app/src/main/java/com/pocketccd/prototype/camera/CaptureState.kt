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
    /** 该镜头支持的最大 4:3 尺寸，用于在"没有匹配 pack"时告诉用户该出什么尺寸。 */
    val largestSupported: Size? = null,
)
