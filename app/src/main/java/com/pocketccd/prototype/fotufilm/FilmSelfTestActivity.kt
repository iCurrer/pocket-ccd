package com.pocketccd.prototype.fotufilm

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.pocketccd.prototype.BuildConfig
import kotlin.concurrent.thread

/**
 * 真机自测入口，用于 FOT-004 验收与"成像效果"排查。
 *
 * 从设置页「诊断 · 运行成像自测」进入（debug / benchmark 包才有这一行），也可以：
 * ```
 * adb shell am start -n com.pocketccd.prototype/.fotufilm.FilmSelfTestActivity
 * ```
 *
 * 跑完会做两件事：
 * 1. 把 JSON 报告写到 `files/fotufilm-selftest.json`（数字：耗时、内存、nonFinite、一致性）；
 * 2. 把 bitmap 路径的渲染结果**直接显示在屏幕上**（同时落盘成 JPEG）——
 *    "成像效果"好不好，看这一张比看一堆数字快。
 *
 * Release 包里直接 `finish()`，避免诊断入口泄漏到发布版（TC-P1-018）。
 */
class FilmSelfTestActivity : Activity() {

    private lateinit var output: TextView
    private lateinit var preview: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // release 包里没有这个入口；benchmark 是专门用来量性能的非 debuggable 构建，
        // 必须放行，否则量到的永远是 debuggable 那个慢一个量级的版本。
        if (!BuildConfig.DEBUG && BuildConfig.BUILD_TYPE != "benchmark") {
            Log.w(TAG, "Release 构建不提供自测入口")
            finish()
            return
        }

        preview = ImageView(this).apply {
            adjustViewBounds = true
            visibility = ImageView.GONE
        }
        output = TextView(this).apply {
            textSize = 11f
            gravity = Gravity.START or Gravity.TOP
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 48)
        }
        column.addView(preview)
        column.addView(output)
        setContentView(ScrollView(this).apply { addView(column) })

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
            val sample = report.optJSONObject("bitmapPath")?.optString("sample").orEmpty()
            val text = buildString {
                appendLine("verdict: ${report.optString("verdict")}")
                appendLine("耗时: ${elapsed}ms")
                appendLine("报告: ${file?.absolutePath ?: "写入失败"}")
                if (sample.isNotEmpty()) appendLine("样张: $sample")
                appendLine()
                append(report.toString(2))
            }
            Log.i(TAG, "verdict=${report.optString("verdict")} 用时 ${elapsed}ms")

            // 样张 4000×3000 直接解码是 48MB，按 1/4 采样够看观感又不至于把堆吃光。
            val bitmap = if (sample.isNotEmpty()) {
                runCatching {
                    BitmapFactory.decodeFile(
                        sample,
                        BitmapFactory.Options().apply { inSampleSize = 4 },
                    )
                }.getOrNull()
            } else {
                null
            }

            runOnUiThread {
                if (bitmap != null) {
                    preview.setImageBitmap(bitmap)
                    preview.visibility = ImageView.VISIBLE
                }
                output.text = text
            }
        }
    }

    private companion object {
        const val TAG = "FotufilmSelfTest"
    }
}
