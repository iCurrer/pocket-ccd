package com.pocketccd.prototype.update

import android.content.res.XmlResourceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 应用内更新里**不联网、也不点界面**的那部分（D-092）。
 *
 * ## 为什么盯着 FileProvider 的配置
 *
 * 这条链路最容易出事的地方不是下载，而是"文件在不在 provider 的暴露范围内"。配错的
 * 表现是：45 MB 下完、校验通过，最后一步 `getUriForFile` 抛
 * `IllegalArgumentException: Failed to find configured root` —— 编译期看不出来，
 * 单元测试里点不出来，只有真机走到最后一步才炸。而它修起来只是加一行 XML。
 *
 * 所以这里直接读**打包后的** `file_provider_paths.xml`（走 `resources.getXml`，
 * 不是读源码目录），断言两件事：`cache/updates` 这一根还在、相册分享用的那一根没被
 * 顺手删掉。
 *
 * 不在这里调 `FileProvider.getUriForFile`：Robolectric 在 Windows 上会把 app 的
 * cacheDir 造在临时目录里，`SimplePathStrategy` 的根与文件路径虽然逐字符相同，
 * 匹配仍会失败 —— 那是测试环境的路径处理，不是应用的配置问题，照着它调试只会
 * 把配置改坏。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ApkUpdaterTest {

    private val context = RuntimeEnvironment.getApplication()

    /** 把 `<paths>` 下的每个节点读成 (标签名 → 属性表)。 */
    private fun providerPaths(): List<Pair<String, Map<String, String>>> {
        val res = context.resources
        val id = res.getIdentifier("file_provider_paths", "xml", context.packageName)
        assertTrue("找不到 file_provider_paths.xml", id != 0)
        val parser: XmlResourceParser = res.getXml(id)
        val out = mutableListOf<Pair<String, Map<String, String>>>()
        var event = parser.eventType
        while (event != XmlResourceParser.END_DOCUMENT) {
            if (event == XmlResourceParser.START_TAG && parser.name != "paths") {
                val attrs = (0 until parser.attributeCount).associate { i ->
                    parser.getAttributeName(i) to parser.getAttributeValue(i)
                }
                out += parser.name to attrs
            }
            event = parser.next()
        }
        parser.close()
        return out
    }

    @Test
    fun `更新包的缓存目录在 FileProvider 的暴露范围内`() {
        val cache = providerPaths().firstOrNull { it.first == "cache-path" }
        assertNotNull("缺少 <cache-path>：安装包无法交给系统安装器", cache)
        assertEquals("updates", cache!!.second["name"])
        assertEquals("updates/", cache.second["path"])
    }

    @Test
    fun `相册分享用的外部目录仍然在`() {
        // 这条是防止"为了塞更新包把 provider 配置整个重写"时把分享弄坏的回归守卫。
        val share = providerPaths().firstOrNull { it.first == "external-files-path" }
        assertNotNull(share)
        assertEquals("Pictures/pocket-ccd/", share!!.second["path"])
    }

    @Test
    fun `进度比例在总数未知时是 0 且标记为未知`() {
        val unknown = InstallState.Downloading(received = 1024, total = 0)
        assertEquals(0f, unknown.fraction, 0.0001f)
        assertTrue(!unknown.known)

        val half = InstallState.Downloading(received = 50, total = 100)
        assertEquals(0.5f, half.fraction, 0.0001f)
        assertTrue(half.known)

        // 服务器给了比实际收到更小的总数时，进度条也不能超过 1。
        val over = InstallState.Downloading(received = 200, total = 100)
        assertEquals(1f, over.fraction, 0.0001f)
    }
}
