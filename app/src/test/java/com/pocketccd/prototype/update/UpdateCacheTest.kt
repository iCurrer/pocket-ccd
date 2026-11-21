package com.pocketccd.prototype.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 本地记忆（D-097）：结果缓存、限流锁、feed 冷却。
 *
 * 这三样存在的意义不是"优化"，是**让检查更新在被限流时还能工作**。所以用例都围绕
 * 时间边界打 —— 缓存过早失效等于没缓存（每次点击都发请求），过晚失效等于永远看不到
 * 新版本；限流锁早失效等于用户继续空点，晚失效等于明明恢复了还点不动。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class UpdateCacheTest {

    private val cache = UpdateCache(RuntimeEnvironment.getApplication())

    private fun release(version: String, apk: ApkAsset? = null) = ReleaseInfo(
        tag = "v$version",
        version = version,
        htmlUrl = "https://github.com/iCurrer/pocket-ccd/releases/tag/v$version",
        publishedAt = "2026-11-11T08:46:30Z",
        apk = apk,
    )

    @Test
    fun `记住的更新在两分钟内仍然有效`() {
        val now = 1_800_000_000_000L
        cache.remember(release("0.9.1"), now)

        val hit = cache.freshOutcome("0.9.0", now + 30_000L)
        assertTrue(hit is UpdateCheck.Available)
        assertEquals("0.9.1", (hit as UpdateCheck.Available).release.version)

        // 设备上已经是这个版本了：缓存仍然有效，但结论是"已是最新"。
        assertTrue(cache.freshOutcome("0.9.1", now + 30_000L) is UpdateCheck.UpToDate)
    }

    @Test
    fun `超过两分钟缓存失效`() {
        val now = 1_800_000_000_000L
        cache.remember(release("0.9.1"), now)
        assertNull(cache.freshOutcome("0.9.0", now + 3 * 60_000L))
    }

    @Test
    fun `设备时间往回跳时不能把缓存当成永远有效`() {
        val now = 1_800_000_000_000L
        cache.remember(release("0.9.1"), now)
        // checked_at 在未来（用户手动改时间）：旧实现会算出负年龄，永远命中缓存。
        assertNull(cache.freshOutcome("0.9.0", now - 10 * 60_000L))
    }

    @Test
    fun `附件信息能原样存回来`() {
        val now = 1_800_000_000_000L
        cache.remember(
            release(
                "0.9.1",
                ApkAsset("app-release.apk", "https://e.com/app.apk", 46_261_331L),
            ),
            now,
        )
        val apk = (cache.freshOutcome("0.9.0", now) as UpdateCheck.Available).release.apk
        assertEquals("app-release.apk", apk?.name)
        assertEquals(46_261_331L, apk?.bytes)
    }

    @Test
    fun `限流锁到点自动失效`() {
        val now = 1_800_000_000_000L
        cache.rememberLimit(now + 60_000L)
        assertEquals(now + 60_000L, cache.limitUntil(now))
        // 窗口过后必须放行，否则用户要等下一次启动才能再检查。
        assertEquals(0L, cache.limitUntil(now + 61_000L))
    }

    @Test
    fun `feed 冷却十分钟`() {
        val now = 1_800_000_000_000L
        assertFalse(cache.feedIsDown(now))
        cache.rememberFeedDown(now)
        assertTrue(cache.feedIsDown(now + 60_000L))
        assertFalse(cache.feedIsDown(now + 11 * 60_000L))
    }
}
