package com.pocketccd.prototype.fotufilm

import java.nio.ByteBuffer

/**
 * `libfotufilm_android.so` 的 JNI 边界。
 *
 * 符号名遵循 `Java_com_pocketccd_prototype_fotufilm_FotufilmNative_<method>`，
 * 由 `native/fotufilm_jni.cpp` 用 `extern "C"` 手工导出。这里的 9 个 external
 * 声明必须与那边逐条对齐 —— 多一个或少一个都不会在编译期报错，只会在运行时
 * 抛 `UnsatisfiedLinkError`，所以改动请成对进行。
 *
 * **库缺失不是致命错误。** `.so` 是 CI 产物，不进仓库（见 `.gitignore` 的
 * `prebuilt/`）。没下载 artifact 时 [loaded] 为 false，上层据此把胶片功能
 * 标记为不可用，而不是让 App 崩掉。
 */
internal object FotufilmNative {

    private const val LIBRARY = "fotufilm_android"

    private val loadResult: Result<Unit> = runCatching { System.loadLibrary(LIBRARY) }

    /** `.so` 是否成功载入。false 时下面所有 external 方法都不能调用。 */
    val loaded: Boolean get() = loadResult.isSuccess

    /** 载入失败的原因，用于诊断；成功时为 null。 */
    val loadFailure: Throwable? get() = loadResult.exceptionOrNull()

    // ---- 错误码，与 native/fotufilm_jni.cpp 的 enum 一致 ----
    const val OK = 0
    const val ERROR_INVALID_ARGUMENT = -1
    const val ERROR_BAD_PACK = -2
    const val ERROR_BUFFER_TOO_SMALL = -3
    const val ERROR_KERNEL_FAILED = -4

    /**
     * JNI ABI 版本。1 = 支持 nativeProcess / nativeProcessStrip / nativeMaxHalo / nativePackInfo。
     * 加参数就要涨这个号，避免 Kotlin 侧悄悄调到旧实现。
     */
    external fun nativeAbiVersion(): Int

    /** Halide AOT 内核在当前进程里是否可用。 */
    external fun nativeAvailable(): Boolean

    /**
     * 解析 FSWP pack，返回非 0 句柄；失败返回 0，原因见 [nativeLastError]。
     * 句柄必须在用完后交给 [nativeReleasePack]，否则整份 pack（约 1.8MB）泄漏。
     */
    external fun nativeOpenPack(bytes: ByteArray): Long

    external fun nativeReleasePack(handle: Long)

    /** `[width, height, featureMask, seed]`；句柄无效返回 null。seed 需 `toUInt()` 还原。 */
    external fun nativePackInfo(handle: Long): IntArray?

    /**
     * 该 pack 实际会扩散的像素半径上界。strip 分块必须按它加 halo，
     * 否则带子接缝会出现扩散/晕光断裂。句柄无效返回 -1。
     */
    external fun nativeMaxHalo(handle: Long): Int

    /**
     * 整帧一次性处理。输入输出都是 **direct** `ByteBuffer`，内容为 float32 分量平面
     * （不是交错 RGB），三个平面分开传。
     *
     * @param featureMask 传 -1 表示用 pack 里记录的 mask
     * @param seed 传 -1 表示用 pack 里记录的 seed
     */
    external fun nativeProcess(
        handle: Long,
        inputR: ByteBuffer,
        inputG: ByteBuffer,
        inputB: ByteBuffer,
        outputR: ByteBuffer,
        outputG: ByteBuffer,
        outputB: ByteBuffer,
        width: Int,
        height: Int,
        featureMask: Int,
        seed: Int,
    ): Int

    /**
     * 行带状分块处理，语义见 `native/fotufilm_jni.cpp` 里 `nativeProcessStrip` 的注释：
     * 输入块是 `inputWidth × inputHeight`，它在输出画布上的位置是 `(originX, originY)`，
     * 其中只有 `[interiorTop, interiorTop + interiorHeight)` 这些行是本带真正要的。
     *
     * `originX + inputWidth <= outputWidth` 且 `originY + inputHeight <= outputHeight`，
     * 否则返回 [ERROR_INVALID_ARGUMENT]。
     */
    external fun nativeProcessStrip(
        handle: Long,
        inputR: ByteBuffer,
        inputG: ByteBuffer,
        inputB: ByteBuffer,
        outputR: ByteBuffer,
        outputG: ByteBuffer,
        outputB: ByteBuffer,
        inputWidth: Int,
        inputHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
        originX: Int,
        originY: Int,
        interiorTop: Int,
        interiorHeight: Int,
        featureMask: Int,
        seed: Int,
    ): Int

    /** 最近一次失败原因。成功调用不会清零，便于在异常之外排查。 */
    external fun nativeLastError(): String?

    /** 把 native 错误码翻成人话，附上 native 侧记录的原因。 */
    fun describe(code: Int): String {
        val what = when (code) {
            OK -> "成功"
            ERROR_INVALID_ARGUMENT -> "参数非法"
            ERROR_BAD_PACK -> "pack 不合法"
            ERROR_BUFFER_TOO_SMALL -> "缓冲区太小"
            ERROR_KERNEL_FAILED -> "Halide 内核失败"
            else -> "未知错误码 $code"
        }
        val detail = runCatching { nativeLastError() }.getOrNull()
        return if (detail.isNullOrBlank()) what else "$what：$detail"
    }
}
