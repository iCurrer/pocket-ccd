package com.pocketccd.prototype.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 版本比较与 Release 解析（D-091）。
 *
 * 这两件事都**只在联网时出问题**，而联网恰恰是测试里最难复现的部分。所以把它们
 * 拆成纯函数打：比较逻辑错了会得出"该更新却说已是最新"，解析错了会把整页更新入口
 * 变成永远失败 —— 两种毛病在界面上都长得像"网络不好"。
 *
 * 用 Robolectric 是因为解析走 `org.json`，纯 JVM 单测里它只是抛 `Stub!` 的空壳。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class UpdateCheckerTest {

    @Test
    fun `正式版比同号预发布版更新`() {
        assertTrue(UpdateChecker.compareVersions("0.9.0", "0.9.0-beta1") > 0)
        assertTrue(UpdateChecker.compareVersions("0.9.0-beta1", "0.9.0") < 0)
    }

    @Test
    fun `次版本号按数值比而不是按字符串`() {
        // 字符串比较会得出 "0.9" > "0.10"，这是版本检查最经典的翻车点。
        assertTrue(UpdateChecker.compareVersions("0.10.0", "0.9.0") > 0)
        assertTrue(UpdateChecker.compareVersions("0.9.0", "0.10.0") < 0)
    }

    @Test
    fun `预发布序号按数值比`() {
        assertTrue(UpdateChecker.compareVersions("0.9.0-beta10", "0.9.0-beta2") > 0)
        assertTrue(UpdateChecker.compareVersions("0.9.0-beta2", "0.9.0-beta10") < 0)
    }

    @Test
    fun `v 前缀与缺省段不影响比较`() {
        assertEquals(0, UpdateChecker.compareVersions("v0.9.0", "0.9.0"))
        assertEquals(0, UpdateChecker.compareVersions("1.0", "1.0.0"))
    }

    @Test
    fun `解析 latest 响应`() {
        val release = UpdateChecker.parseRelease(
            """
            {
              "tag_name": "v0.10.0",
              "html_url": "https://github.com/iCurrer/pocket-ccd/releases/tag/v0.10.0",
              "published_at": "2026-11-20T10:00:00Z",
              "prerelease": false
            }
            """.trimIndent(),
        )
        assertEquals("v0.10.0", release?.tag)
        // version 是去掉前缀的可比较形态，界面显示 tag、比较用 version。
        assertEquals("0.10.0", release?.version)
        assertEquals("https://github.com/iCurrer/pocket-ccd/releases/tag/v0.10.0", release?.htmlUrl)
    }

    @Test
    fun `缺 tag 或链接时判定为无法解析`() {
        assertNull(UpdateChecker.parseRelease("""{"html_url":"https://example.com"}"""))
        assertNull(UpdateChecker.parseRelease("""{"tag_name":"v1.0.0"}"""))
        assertNull(UpdateChecker.parseRelease("<html>502 Bad Gateway</html>"))
    }

    @Test
    fun `预发布列表取第一条`() {
        val body = """
            [
              {"tag_name":"v0.9.1-beta2","html_url":"https://github.com/x/y/releases/tag/v0.9.1-beta2"},
              {"tag_name":"v0.9.0","html_url":"https://github.com/x/y/releases/tag/v0.9.0"}
            ]
        """.trimIndent()
        val release = UpdateChecker.parseNewestOfList(
            UpdateChecker.HttpResult(code = 200, body = body),
        )
        assertEquals("0.9.1-beta2", release?.version)
    }

    @Test
    fun `列表为空或请求失败时返回 null`() {
        assertNull(
            UpdateChecker.parseNewestOfList(UpdateChecker.HttpResult(code = 200, body = "[]")),
        )
        assertNull(
            UpdateChecker.parseNewestOfList(UpdateChecker.HttpResult(code = 403, body = "[]")),
        )
        assertNull(UpdateChecker.parseNewestOfList(UpdateChecker.HttpResult(code = 200, body = null)))
    }

    // ---------------------------------------------------------------------
    // APK 附件（D-092）：应用内下载靠这几行挑包，挑错就是把别的东西递给安装器
    // ---------------------------------------------------------------------

    @Test
    fun `解析 Release 里的 apk 附件`() {
        val release = UpdateChecker.parseRelease(
            """
            {
              "tag_name": "v0.9.0-beta2",
              "html_url": "https://github.com/iCurrer/pocket-ccd/releases/tag/v0.9.0-beta2",
              "assets": [
                {
                  "name": "pocket-ccd-0.9.0-beta2.apk",
                  "size": 45789583,
                  "browser_download_url": "https://github.com/iCurrer/pocket-ccd/releases/download/v0.9.0-beta2/pocket-ccd-0.9.0-beta2.apk"
                },
                {
                  "name": "checksums.txt",
                  "size": 128,
                  "browser_download_url": "https://github.com/iCurrer/pocket-ccd/releases/download/v0.9.0-beta2/checksums.txt"
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals("pocket-ccd-0.9.0-beta2.apk", release?.apk?.name)
        assertEquals(45_789_583L, release?.apk?.bytes)
        assertTrue(release?.apk?.url?.endsWith(".apk") == true)
    }

    @Test
    fun `没有附件时 apk 为 null 而不是坏链接`() {
        val release = UpdateChecker.parseRelease(
            """{"tag_name":"v1.0.0","html_url":"https://example.com","assets":[]}""",
        )
        assertNull(release?.apk)
        // 连 assets 字段都没有（老接口/被裁剪的响应）也要能解析出来。
        assertNull(UpdateChecker.parseRelease(
            """{"tag_name":"v1.0.0","html_url":"https://example.com"}""",
        )?.apk)
    }

    @Test
    fun `同时挂了调试包和正式包时挑正式包`() {
        // 按数组顺序取第一个会挑到 debug 包 —— 那是一份能被覆盖安装、但带着调试
        // 标志、体积还更大的包。
        val picked = UpdateChecker.pickApk(
            listOf(
                ApkAsset("pocket-ccd-debug.apk", "https://e.com/debug.apk", 60_000_000),
                ApkAsset("pocket-ccd-release.apk", "https://e.com/release.apk", 45_000_000),
            ),
        )
        assertEquals("pocket-ccd-release.apk", picked?.name)
    }

    @Test
    fun `只有调试包时仍然可用`() {
        val picked = UpdateChecker.pickApk(
            listOf(ApkAsset("app-debug.apk", "https://e.com/a.apk", 1)),
        )
        assertEquals("app-debug.apk", picked?.name)
    }

    @Test
    fun `没有 apk 扩展名时不给下载入口`() {
        assertNull(
            UpdateChecker.pickApk(
                listOf(
                    ApkAsset("app.aab", "https://e.com/app.aab", 1),
                    ApkAsset("source.zip", "https://e.com/source.zip", 1),
                ),
            ),
        )
    }

    @Test
    fun `字节数格式化`() {
        // 与相册/发现页同一套口径：1024 进制、MB 保留一位（下载体积差 1 MB 值得说清）。
        assertEquals("未知大小", formatBytes(0))
        assertEquals("未知大小", formatBytes(-1))
        assertEquals("820 KB", formatBytes(839_680))
        assertEquals("43.7 MB", formatBytes(45_789_583))
        assertEquals("1.2 GB", formatBytes(1_288_490_189))
    }

    // ---------------------------------------------------------------------
    // atom 通道（D-097）：不占 API 配额的那条路
    // ---------------------------------------------------------------------

    /**
     * 一段**真实结构**的 feed：feed 自己也有 `<id>` 和 `<link rel="self">`，
     * 而且 id 以 `/releases` 结尾。如果实现里没有"只在 entry 内部读"的边界，
     * 就会把 feed 的 id 当成 tag（得到字符串 `releases`），然后判成"没有新版"
     * 或拼出一个 404 的下载地址 —— 两种错都表现为"检查更新永远没反应"。
     */
    private val feed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom" xml:lang="en-US">
          <id>tag:github.com,2008:https://github.com/iCurrer/pocket-ccd/releases</id>
          <link type="text/html" rel="alternate" href="https://github.com/iCurrer/pocket-ccd/releases"/>
          <link type="application/atom+xml" rel="self" href="https://github.com/iCurrer/pocket-ccd/releases.atom"/>
          <title>Release notes from pocket-ccd</title>
          <updated>2026-11-03T03:16:10Z</updated>
          <entry>
            <id>tag:github.com,2008:Repository/1424510124/v0.9.0-beta3</id>
            <updated>2026-11-11T08:46:30Z</updated>
            <link rel="alternate" type="text/html" href="https://github.com/iCurrer/pocket-ccd/releases/tag/v0.9.0-beta3"/>
            <title>0.9.0-beta3：材质升到零件级</title>
            <content type="html">&lt;p&gt;内测版&lt;/p&gt;</content>
            <author><name>iCurrer</name></author>
          </entry>
          <entry>
            <id>tag:github.com,2008:Repository/1424510124/v0.9.0-beta2</id>
            <updated>2026-11-10T13:33:10Z</updated>
            <link rel="alternate" type="text/html" href="https://github.com/iCurrer/pocket-ccd/releases/tag/v0.9.0-beta2"/>
            <title>v0.9.0-beta2</title>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun `atom 取第一条 entry 并从 id 里取 tag`() {
        val release = UpdateChecker.parseAtom(feed)
        // tag 必须来自 <id>，不能来自 <title>：title 是人话标题，里面没有版本号。
        assertEquals("v0.9.0-beta3", release?.tag)
        assertEquals("0.9.0-beta3", release?.version)
        assertEquals(
            "https://github.com/iCurrer/pocket-ccd/releases/tag/v0.9.0-beta3",
            release?.htmlUrl,
        )
        assertEquals("2026-11-11T08:46:30Z", release?.publishedAt)
    }

    @Test
    fun `atom 没有附件列表时按固定名拼下载地址`() {
        val apk = UpdateChecker.parseAtom(feed)?.apk
        // feed 不含 assets，但下载地址是可以推导的：发版脚本用的就是这个名字。
        assertEquals("app-release.apk", apk?.name)
        assertEquals(
            "https://github.com/iCurrer/pocket-ccd/releases/download/v0.9.0-beta3/app-release.apk",
            apk?.url,
        )
        // 体积未知 —— 下载端会退化成读 Content-Length，不能编一个数出来。
        assertEquals(0L, apk?.bytes)
    }

    @Test
    fun `空 feed 与非 xml 都返回 null 而不是崩`() {
        assertNull(UpdateChecker.parseAtom(""))
        assertNull(
            UpdateChecker.parseAtom(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <id>tag:github.com,2008:https://github.com/iCurrer/pocket-ccd/releases</id>
                  <title>Release notes from pocket-ccd</title>
                </feed>
                """.trimIndent(),
            ),
        )
        assertNull(UpdateChecker.parseAtom("<html>502 Bad Gateway</html>"))
    }

    // ---------------------------------------------------------------------
    // 限流（D-097）：403 之后要给得出一个确切的"何时再来"
    // ---------------------------------------------------------------------

    @Test
    fun `限流时用 X-RateLimit-Reset 算恢复时刻`() {
        val now = 1_800_000_000_000L
        val result = UpdateChecker.HttpResult(
            code = 403,
            body = null,
            // GitHub 给的是 epoch **秒**，直接用毫秒会得到 1970 年。
            rateLimitResetAtEpochSeconds = (now + 12 * 60_000L) / 1000,
        )
        // 取整到秒，允许 1 秒误差。
        assertTrue(kotlin.math.abs(result.retryAt(now) - (now + 12 * 60_000L)) <= 1000L)
    }

    @Test
    fun `Retry-After 比 Reset 更晚时用更晚的那个`() {
        val now = 1_800_000_000_000L
        val result = UpdateChecker.HttpResult(
            code = 429,
            body = null,
            rateLimitResetAtEpochSeconds = (now + 60_000L) / 1000,
            retryAfterSeconds = 300,
        )
        assertEquals(now + 300_000L, result.retryAt(now))
    }

    @Test
    fun `恢复时刻缺失或已是过去时给保守值而不是荒谬倒计时`() {
        val now = 1_800_000_000_000L
        // 头全缺：不能返回 0（界面会显示"0 秒后可试"然后立刻又失败）。
        assertTrue(UpdateChecker.HttpResult(403, null).retryAt(now) > now)
        // 已经是过去的时间点（时钟漂移 / 缓存了旧响应）：一样不能用。
        assertTrue(
            UpdateChecker.HttpResult(
                code = 403,
                body = null,
                rateLimitResetAtEpochSeconds = (now - 60_000L) / 1000,
            ).retryAt(now) > now,
        )
        // 超过一小时一定是坏数据：未认证窗口就是一小时。
        assertTrue(
            UpdateChecker.HttpResult(
                code = 403,
                body = null,
                rateLimitResetAtEpochSeconds = (now + 7 * 24 * 3_600_000L) / 1000,
            ).retryAt(now) <= now + 3_600_000L,
        )
    }
}
