package com.pocketccd.prototype.update

import android.content.Context

/**
 * 检查更新的本地记忆：**结果缓存** + **限流锁** + **feed 冷却**。
 *
 * 存在的理由是两个具体的坑，都不是"优化体验"，而是"让功能还能用"：
 *
 * 1. **手抖连点**。旧实现在设置页 `remember` 一个状态，离开页面就归零；同一屏连点
 *    两下就是两次真网络请求。GitHub 未认证额度按出口 IP 计，点十下有二十次请求
 *    飞出去，而用户看到的结论一次都没变。现在两分钟内的重复检查直接读本地。
 *
 * 2. **被限流后还在重试**。额度用尽时，每点一次都是注定 403 的一次请求，
 *    界面只回一句"限流了，过一会儿再试"—— 用户当然会再点，然后把剩余的希望
 *    也点光。这里记住服务端给的恢复时刻，期间直接回倒计时，一个字节都不发。
 *
 * 所有值都能过期：缓存两分钟，限流锁到服务端给的恢复时刻，feed 冷却十分钟。
 * 没有"永久记住某个版本"，因为那会让用户永远停在旧版。
 */
class UpdateCache(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /**
     * 两分钟内的成功结果。也用于"已是最新"—— 那同样是成功，重复问一遍没有意义。
     *
     * 时间是按 **wall clock** 存的：设备改时间会让缓存瞬间失效或长时间有效，
     * 但两种偏差都只影响"要不要多发一次请求"，不影响正确性，为此引入
     * elapsedRealtime 只会让跨进程/跨重启的语义更绕。
     */
    fun freshOutcome(
        currentVersion: String,
        now: Long = System.currentTimeMillis(),
    ): UpdateCheck? {
        val checkedAt = prefs.getLong(KEY_CHECKED_AT, 0L)
        if (checkedAt <= 0L || now - checkedAt > TTL_MS || checkedAt > now + TTL_MS) return null
        val release = readRelease() ?: return null
        return if (UpdateChecker.compareVersions(release.version, currentVersion) > 0) {
            UpdateCheck.Available(currentVersion, release)
        } else {
            UpdateCheck.UpToDate(currentVersion)
        }
    }

    /** 记下一次成功的检查结论（"已是最新"也走这里：那条 release 就是当前最新）。 */
    fun remember(release: ReleaseInfo, now: Long = System.currentTimeMillis()) {
        val editor = prefs.edit()
            .putLong(KEY_CHECKED_AT, now)
            .putString(KEY_TAG, release.tag)
            .putString(KEY_VERSION, release.version)
            .putString(KEY_HTML_URL, release.htmlUrl)
            .putString(KEY_PUBLISHED_AT, release.publishedAt)
        val apk = release.apk
        if (apk == null) {
            editor.remove(KEY_APK_NAME).remove(KEY_APK_URL).remove(KEY_APK_BYTES)
        } else {
            editor.putString(KEY_APK_NAME, apk.name)
                .putString(KEY_APK_URL, apk.url)
                .putLong(KEY_APK_BYTES, apk.bytes)
        }
        editor.apply()
    }

    /** 限流窗口的结束时刻（epoch 毫秒）；0 或已过期返回 0。 */
    fun limitUntil(now: Long = System.currentTimeMillis()): Long {
        val until = prefs.getLong(KEY_LIMIT_UNTIL, 0L)
        if (until <= now) {
            if (until != 0L) prefs.edit().remove(KEY_LIMIT_UNTIL).apply()
            return 0L
        }
        return until
    }

    fun rememberLimit(until: Long) {
        prefs.edit().putLong(KEY_LIMIT_UNTIL, until).apply()
    }

    /**
     * atom 通道最近是否走不通。
     *
     * 没有它的话，被墙的网络下每次检查都要先白等两个 4 秒超时才落到 API —— 而用户
     * 完全不知道那 8 秒花在哪。冷却十分钟，够一次网络切换发生，也不至于让 feed
     * 恢复后迟迟回不来。
     */
    fun feedIsDown(now: Long = System.currentTimeMillis()): Boolean =
        prefs.getLong(KEY_FEED_DOWN_UNTIL, 0L) > now

    fun rememberFeedDown(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_FEED_DOWN_UNTIL, now + FEED_COOLDOWN_MS).apply()
    }

    private fun readRelease(): ReleaseInfo? {
        val tag = prefs.getString(KEY_TAG, null)?.takeIf { it.isNotBlank() } ?: return null
        val version = prefs.getString(KEY_VERSION, null)?.takeIf { it.isNotBlank() } ?: return null
        val html = prefs.getString(KEY_HTML_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        val apkName = prefs.getString(KEY_APK_NAME, null)?.takeIf { it.isNotBlank() }
        val apkUrl = prefs.getString(KEY_APK_URL, null)?.takeIf { it.isNotBlank() }
        return ReleaseInfo(
            tag = tag,
            version = version,
            htmlUrl = html,
            publishedAt = prefs.getString(KEY_PUBLISHED_AT, null)?.takeIf { it.isNotBlank() },
            apk = if (apkName != null && apkUrl != null) {
                ApkAsset(name = apkName, url = apkUrl, bytes = prefs.getLong(KEY_APK_BYTES, 0L))
            } else {
                null
            },
        )
    }

    private companion object {
        const val FILE_NAME = "update_cache"

        /** 两分钟：比"手抖"长，比"想立刻看到新发布"短。 */
        const val TTL_MS = 2 * 60_000L
        const val FEED_COOLDOWN_MS = 10 * 60_000L

        const val KEY_CHECKED_AT = "checked_at"
        const val KEY_TAG = "tag"
        const val KEY_VERSION = "version"
        const val KEY_HTML_URL = "html_url"
        const val KEY_PUBLISHED_AT = "published_at"
        const val KEY_APK_NAME = "apk_name"
        const val KEY_APK_URL = "apk_url"
        const val KEY_APK_BYTES = "apk_bytes"
        const val KEY_LIMIT_UNTIL = "limit_until"
        const val KEY_FEED_DOWN_UNTIL = "feed_down_until"
    }
}
