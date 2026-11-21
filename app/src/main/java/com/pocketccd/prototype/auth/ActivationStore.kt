package com.pocketccd.prototype.auth

import android.content.Context

/**
 * 内测激活凭证的本地存储。
 *
 * ## 只存"通过"这件事，不存任何验签材料
 *
 * 服务端签回来的 token 是个不透明随机串，客户端**不解释**它的内容，只判断"在不在"。
 * 这是刻意的（理由见 `docs/BETA_ACTIVATION.md` §3.1）：能给 APK 打补丁的人，
 * 删掉 `if (!activated)` 那一行比伪造签名容易得多，所以客户端不背这个包袱。
 *
 * ## 卸载即失效
 *
 * `SharedPreferences` 跟着 `/data/data/<pkg>` 走，卸载就没了。换机/重装要找管理端
 * 补码（服务端 `admin/unbind` 可以释放旧设备）。`android:allowBackup` 未显式关闭，
 * 所以 Auto Backup 还原到新机时 token 可能一起回来 —— 内测阶段当作"多送一台"，不堵。
 */
class ActivationStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 有 token 就算通过。空串/null 一律当未激活。 */
    fun isActivated(): Boolean = !token().isNullOrBlank()

    fun token(): String? = prefs.getString(KEY_TOKEN, null)

    /** 激活时用的码（裸 8 位）。用于设置页展示与"我当初买的是哪个码"的追溯。 */
    fun code(): String? = prefs.getString(KEY_CODE, null)

    /** 毫秒时间戳；0 表示没有记录。 */
    fun activatedAt(): Long = prefs.getLong(KEY_AT, 0L)

    fun save(token: String, code: String, activatedAtMs: Long) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_CODE, code)
            .putLong(KEY_AT, activatedAtMs)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS = "beta_activation"
        const val KEY_TOKEN = "token"
        const val KEY_CODE = "code"
        const val KEY_AT = "activated_at"
    }
}
