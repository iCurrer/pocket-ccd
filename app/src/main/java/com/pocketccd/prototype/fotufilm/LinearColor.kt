package com.pocketccd.prototype.fotufilm

import kotlin.math.pow

/**
 * sRGB(8bit) ↔ 线性光 的转换表。
 *
 * 为什么必须做：Fotufilm 引擎的输入输出都是 **scene-linear / display-linear** 的
 * float，`native/fotufilm_jni.cpp` 明确不做任何颜色变换。而 Android 侧拿到的
 * Bitmap 是 sRGB 编码的 8bit，两边口径不同，直接喂进去等于把整条色调曲线
 * 压进了底片，影调会明显发灰发亮。
 *
 * 12MP 一帧是 4000×3000×3 ≈ 3600 万次转换，所以走查表而不是 `pow()`。
 * 解码只有 256 种输入，表是精确的；编码是 float → 8bit，用 4096 级表加线性
 * 插值，误差在 1 个色阶以内。
 *
 * **注意约定边界**：这里按 sRGB 传递函数（IEC 61966-2-1）做，但**没有**做
 * Rec.2020 / Rec.709 基色矩阵换算 —— 上游 pipeline 的基色口径属于 FOT-006，
 * 本文件不替它做假设。现阶段相当于"sRGB 基色 + 线性光"，样片看不出问题，
 * 但拿来做色彩比对前必须先补 FOT-006。
 */
internal object LinearColor {

    /** sRGB 8bit → 线性光。 */
    private val DECODE = FloatArray(256) { index ->
        val c = index / 255f
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }

    private const val ENCODE_STEPS = 4096

    /** 线性光 → sRGB 8bit 的采样表，值是 0..255 的浮点。 */
    private val ENCODE = FloatArray(ENCODE_STEPS + 1) { index ->
        val c = index / ENCODE_STEPS.toFloat()
        val s = if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f
        s * 255f
    }

    /** 线性光 → 0..255。NaN 当 0 处理，负值和过曝都夹到端点。 */
    fun encode(linear: Float): Int {
        if (linear.isNaN()) return 0
        val clamped = if (linear < 0f) 0f else if (linear > 1f) 1f else linear
        val position = clamped * ENCODE_STEPS
        val index = position.toInt()
        if (index >= ENCODE_STEPS) return 255
        val fraction = position - index
        val low = ENCODE[index]
        val value = low + (ENCODE[index + 1] - low) * fraction
        val rounded = (value + 0.5f).toInt()
        return if (rounded < 0) 0 else if (rounded > 255) 255 else rounded
    }

    /** sRGB 8bit → 线性光。 */
    fun decode(srgb8: Int): Float = DECODE[srgb8 and 0xFF]

    /**
     * 把一块 ARGB_8888 像素解码进三个 float 平面。
     *
     * 平面是**分离**存放的（先 R 全块、再 G、再 B），因为 native 侧分别接三个指针；
     * 交错 RGB 会需要一次重排，12MP 下就是白白多走一遍内存。
     *
     * alpha 在这里被丢弃：引擎只处理三通道光强，合成/透明度属于上层。
     */
    fun decodeInto(pixels: IntArray, count: Int, r: FloatArray, g: FloatArray, b: FloatArray) {
        for (i in 0 until count) {
            val argb = pixels[i]
            r[i] = DECODE[(argb shr 16) and 0xFF]
            g[i] = DECODE[(argb shr 8) and 0xFF]
            b[i] = DECODE[argb and 0xFF]
        }
    }

    /**
     * 把三个 float 平面编码成 ARGB_8888。输出恒为不透明（alpha = 0xFF）。
     */
    fun encodeInto(r: FloatArray, g: FloatArray, b: FloatArray, count: Int, pixels: IntArray) {
        for (i in 0 until count) {
            pixels[i] = (0xFF shl 24) or (encode(r[i]) shl 16) or (encode(g[i]) shl 8) or encode(b[i])
        }
    }
}
