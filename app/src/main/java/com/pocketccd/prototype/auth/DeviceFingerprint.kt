package com.pocketccd.prototype.auth

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

/**
 * 设备指纹：**32 位十六进制**，只用于服务端"一码一机"绑定。
 *
 * ## 为什么不用 `ANDROID_ID` 原值
 *
 * 1. 它虽然不是硬件号，但对同一 App 是稳定标识；原样上传等于把"这台设备是谁"
 *    这件事交出去。哈希之后服务端只能比对，无法反查。
 * 2. 单靠 `ANDROID_ID` 会在恢复出厂后归零，跟机型/Fingerprint 混入可以降低碰撞。
 *
 * ## 边界（不要指望它防篡改）
 *
 * root 用户可以改 `Settings.Secure.ANDROID_ID`，也可以直接改本地存储。内测分发的
 * 威胁模型是"APK 被转群、陌生人白嫖"，不是"有人定向攻击"—— 这个强度足够，
 * 详见 `docs/BETA_ACTIVATION.md` §3。
 */
object DeviceFingerprint {

    /** @return 32 位小写十六进制。任何一步失败都退化成"不稳定的值"，不至于崩溃。 */
    @SuppressLint("HardwareIds")
    fun of(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()

        val raw = buildString {
            append(androidId)
            append('|')
            append(Build.FINGERPRINT)
            append('|')
            append(Build.MODEL)
            append('|')
            append(context.packageName)
        }

        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(32)
        for (i in 0 until 16) {
            val b = digest[i].toInt() and 0xFF
            hex.append(HEX[b ushr 4]).append(HEX[b and 0x0F])
        }
        return hex.toString()
    }

    private const val HEX = "0123456789abcdef"
}
