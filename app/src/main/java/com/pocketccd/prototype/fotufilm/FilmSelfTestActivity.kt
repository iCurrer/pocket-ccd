package com.pocketccd.prototype.fotufilm

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.ScrollView
import android.widget.TextView
import com.pocketccd.prototype.BuildConfig
import kotlin.concurrent.thread

/**
 * 真机自测入口，只用于 FOT-004 验收，不属于产品界面。
 *
 * 用法（先把 pack 推进去）：
 * ```
 * adb push prebuilt/fotufilm/packs/. /sdcard/Android/data/com.pocketccd.prototype/files/film/
 * adb shell am start -n com.pocketccd.prototype/.fotufilm.FilmSelfTestActivity
 * adb logcat -s FotufilmSelfTest
 * adb pull /sdcard/Android/data/com.pocketccd.prototype/files/fotufilm-selftest.json
 * ```
 *
 * Release 包里直接 `finish()`，避免诊断入口泄漏到发布版（对应 TC-P1-018）。
 */
class FilmSelfTestActivity : Activity() {

    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // release 包里没有这个入口；benchmark 是专门用来量性能的非 debuggable 构建，
        // 必须放行，否则量到的永远是 debuggable 那个慢一个量级的数字。
        if (!BuildConfig.DEBUG && BuildConfig.BUILD_TYPE != "benchmark") {
            Log.w(TAG, "Release 构建不提供自测入口")
            finish()
            return
        }

        output = TextView(this).apply {
            textSize = 11f
            setPadding(32, 48, 32, 48)
            gravity = Gravity.START or Gravity.TOP
            typeface = android.graphics.Typeface.MONOSPACE
        }
        setContentView(ScrollView(this).apply { addView(output) })

        output.text = "正在自测…\n（12MP 分块处理需要十几秒，请勿退出）"

        thread(name = "fotufilm-selftest") {
            val started = System.currentTimeMillis()
            val report = try {
                FilmSelfTest(this).run()
            } catch (error: Throwable) {
                Log.e(TAG, "自测崩溃", error)
                org.json.JSONObject()
                    .put("verdict", "CRASH: $error")
                    .put("stack", error.stackTraceToString())
            }
            val elapsed = System.currentTimeMillis() - started

            val file = runCatching { FilmSelfTest.writeReport(this, report) }.getOrNull()
            val text = buildString {
                appendLine("verdict: ${report.optString("verdict")}")
                appendLine("耗时: ${elapsed}ms")
                appendLine("报告: ${file?.absolutePath ?: "写入失败"}")
                appendLine()
                append(report.toString(2))
            }
            Log.i(TAG, "verdict=${report.optString("verdict")} 用时 ${elapsed}ms")

            runOnUiThread { output.text = text }
        }
    }

    private companion object {
        const val TAG = "FotufilmSelfTest"
    }
}
