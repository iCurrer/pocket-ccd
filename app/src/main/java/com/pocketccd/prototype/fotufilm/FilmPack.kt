package com.pocketccd.prototype.fotufilm

import android.content.Context
import java.io.File

/** native 侧拿不到 pack 时抛这个，避免上层看到裸的返回码。 */
class FilmPackException(message: String) : Exception(message)

/**
 * 一个已解析的 FSWP v2 pack。
 *
 * pack 里有三样东西是**绑定到具体分辨率**的：三段 33³ 色彩 LUT、以及一批以
 * "毫米 × 该帧 pixels-per-mm" 表示的空间半径。所以一个 pack 只对头部记录的
 * `width × height` 有效 —— 拿 4000×3000 的 pack 去渲染 1600×1200 的图，
 * 扩散和颗粒的物理尺度会整体跑偏（D-037）。要换尺寸就换 pack。
 *
 * 内部持有一个 native 句柄（约 1.8MB），必须 [close]。
 */
class FilmPack private constructor(
    private val handle: Long,
    /** pack 声明的帧宽；输入图像宽必须与之相等。 */
    val width: Int,
    /** pack 声明的帧高；输入图像高必须与之相等。 */
    val height: Int,
    /** 变体位掩码，决定用哪几个 AOT 内核。 */
    val featureMask: Int,
    /** 颗粒种子。同一张图同一种子结果可复现。 */
    val seed: UInt,
    /** 该 pack 会扩散的像素半径上界，strip 分块按它加 halo。 */
    val maxHalo: Int,
) : AutoCloseable {

    private var closed = false

    internal val nativeHandle: Long
        get() {
            check(!closed) { "FilmPack 已释放" }
            return handle
        }

    val isClosed: Boolean get() = closed

    override fun close() {
        if (closed) return
        closed = true
        FotufilmNative.nativeReleasePack(handle)
    }

    override fun toString(): String =
        "FilmPack(${width}x$height, mask=0x${featureMask.toString(16)}, seed=$seed, halo=$maxHalo)"

    companion object {
        /** 单个 pack 的尺寸上界，与 native 侧的守卫保持一致。 */
        const val MAX_DIMENSION = 20_000

        fun fromBytes(bytes: ByteArray): FilmPack {
            if (!FotufilmNative.loaded) {
                throw FilmPackException(
                    "libfotufilm_android.so 未加载：${FotufilmNative.loadFailure?.message ?: "未知原因"}"
                )
            }
            val handle = FotufilmNative.nativeOpenPack(bytes)
            if (handle == 0L) {
                throw FilmPackException("pack 解析失败：${FotufilmNative.describe(FotufilmNative.ERROR_BAD_PACK)}")
            }
            val info = FotufilmNative.nativePackInfo(handle)
            if (info == null || info.size < 4) {
                FotufilmNative.nativeReleasePack(handle)
                throw FilmPackException("pack 已打开但读不到头部信息")
            }
            val halo = FotufilmNative.nativeMaxHalo(handle)
            if (halo < 0) {
                FotufilmNative.nativeReleasePack(handle)
                throw FilmPackException("pack 已打开但推导不出 halo 半径")
            }
            return FilmPack(
                handle = handle,
                width = info[0],
                height = info[1],
                featureMask = info[2],
                seed = info[3].toUInt(),
                maxHalo = halo,
            )
        }

        fun fromFile(file: File): FilmPack {
            if (!file.isFile) throw FilmPackException("pack 文件不存在：${file.absolutePath}")
            return fromBytes(file.readBytes())
        }

        /**
         * 从 assets 读。目前 pack 不进仓库（体积 + 许可口径），这条只为
         * 以后把 pack 打进 APK 时留的口子。
         */
        fun fromAsset(context: Context, assetName: String): FilmPack =
            fromBytes(context.assets.open(assetName).use { it.readBytes() })
    }
}
