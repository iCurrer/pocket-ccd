package com.pocketccd.prototype.auth

import android.os.Build
import android.util.Log
import com.pocketccd.prototype.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** 一次激活请求的结局。分三类是因为界面文案完全不同（码的问题 / 网络 / 服务器）。 */
sealed interface ActivateOutcome {

    /** 服务端认可，[token] 存起来即可。 */
    data class Granted(val token: String, val again: Boolean) : ActivateOutcome

    /** 服务端明确拒绝 —— 问题出在码或设备上，重试没有意义。 */
    data class Denied(val reason: Reason) : ActivateOutcome

    /** 没拿到答案：超时、无网、5xx、响应无法解析。**可以重试**。 */
    data class Unreachable(val detail: String) : ActivateOutcome

    /** 服务端的拒绝理由。字符串与 `server/app.py` 一一对应，不要各自发明。 */
    enum class Reason(val message: String) {
        INVALID_CODE("邀请码无效，仔细核对一下字母和数字"),
        CODE_USED_UP("这个码已经在别的设备上用过了"),
        REVOKED("这个邀请码已停用"),
        RATE_LIMITED("尝试次数太多，等一会儿再试"),
        UNKNOWN("服务器返回了无法识别的结果"),
    }
}

/**
 * 内测激活的 HTTP 客户端。
 *
 * ## 只用 `HttpURLConnection`，不引 OkHttp
 *
 * 整个 App 的网络需求只有两处（检查更新、激活），两处都是"一次请求、一个短 JSON"。
 * 为这个体积引一个网络库不值得 —— `update/UpdateChecker.kt` 已经确立了这条口径。
 *
 * ## 超时给到 8 秒，比检查更新宽
 *
 * 检查更新失败只是没更新；激活失败意味着用户**打不开相机**，所以在弱网下多等一会儿
 * 比秒失败有用。但必须有上限：激活页是阻塞的，等 30 秒不如果断告诉用户"连不上"。
 *
 * ## 不重试
 *
 * 自动重试会让"码已被占用"这类确定性错误被重复提交，也会放大限速计数。交给用户按键。
 */
object ActivationClient {

    private const val TAG = "ActivationClient"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    suspend fun activate(code: String, device: String): ActivateOutcome =
        withContext(Dispatchers.IO) {
            val url = BuildConfig.ACTIVATION_URL
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    // 服务端不校验 UA，但带上版本方便在 journalctl 里区分是哪一版出的问题。
                    setRequestProperty("User-Agent", "PocketCCD/${BuildConfig.VERSION_NAME}")
                }

                val body = JSONObject().apply {
                    put("code", code)
                    put("device", device)
                    put("model", Build.MODEL)
                    put("ver", BuildConfig.VERSION_NAME)
                }.toString()

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

                val status = conn.responseCode
                // 4xx 也要读 errorStream：服务端的错误体在那边，不是空。
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (text.isBlank()) {
                    return@withContext ActivateOutcome.Unreachable("服务器返回空响应（HTTP $status）")
                }
                parse(text)
            } catch (e: Exception) {
                Log.w(TAG, "activate failed", e)
                ActivateOutcome.Unreachable(e.message ?: e::class.java.simpleName)
            } finally {
                conn?.disconnect()
            }
        }

    /**
     * 解析响应体。
     *
     * 约定是 `{"ok":true,"token":...}` 或 `{"ok":false,"err":"..."}`；`ok` 缺失时
     * 不猜 —— 当作"无法识别"而不是"失败"，因为两者给用户的下一步完全不同。
     */
    private fun parse(text: String): ActivateOutcome {
        val json = runCatching { JSONObject(text) }.getOrNull()
            ?: return ActivateOutcome.Unreachable("响应不是合法 JSON")

        if (json.optBoolean("ok", false)) {
            val token = json.optString("token")
            if (token.isBlank()) return ActivateOutcome.Unreachable("响应里没有 token")
            return ActivateOutcome.Granted(token = token, again = json.optBoolean("again", false))
        }

        val reason = when (json.optString("err")) {
            "INVALID_CODE" -> ActivateOutcome.Reason.INVALID_CODE
            "CODE_USED_UP" -> ActivateOutcome.Reason.CODE_USED_UP
            "REVOKED" -> ActivateOutcome.Reason.REVOKED
            "RATE_LIMITED" -> ActivateOutcome.Reason.RATE_LIMITED
            // BAD_DEVICE / BAD_JSON 是客户端 bug，对用户没有可操作性，按未知处理。
            else -> ActivateOutcome.Reason.UNKNOWN
        }
        return ActivateOutcome.Denied(reason)
    }
}
