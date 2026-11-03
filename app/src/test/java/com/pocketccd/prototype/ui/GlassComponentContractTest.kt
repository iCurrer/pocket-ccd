package com.pocketccd.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 玻璃组件层的两条静态契约。
 *
 * 这个测试取代了旧体系里的 `InteractiveIconLabelsTest`。那一条扫描的是
 * `ui/film/FilmControls.kt` 里三个"纯图标可点控件"的**默认参数**，因为旧体系允许
 * `accessibilityLabel: String = <推导值>` —— 语义文案可以缺省，只能靠静态扫描兜住。
 *
 * 新体系把这件事挪进了**类型系统**：[GlassIconButton] 的 `label` 是必填且没有默认值，
 * 少写一个文案编译不过，不需要测试盯着。于是真正还值得测的只剩两件：
 *
 * 1. 那个"没有默认值"本身别被谁好心加回去（加了就等于把编译器这道闸拆了）；
 * 2. `GlassIconKind` 每加一个图标都要在 `drawGlassIcon` 里有分支 —— 漏画不会报错，
 *    只会在界面上留一块空白，肉眼很难在深色玻璃上发现。
 */
class GlassComponentContractTest {

    /** 单测工作目录是 `app/`（Gradle 约定），与 NoBareFontSizeTest 一致。 */
    private val glassDir = File("src/main/java/com/pocketccd/prototype/ui/glass")

    private val sources: Map<String, String> by lazy {
        assertTrue("找不到 ui/glass 源码目录：${glassDir.absolutePath}", glassDir.isDirectory)
        glassDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { it.name to stripComments(it.readText()) }
    }

    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", setOf(RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("""//[^\n]*"""), "")

    @Test
    fun `GlassIconButton 的语义文案必须保持必填`() {
        val source = sources["Glass.kt"]
            ?: throw AssertionError("找不到 ui/glass/Glass.kt")

        val header = functionHeader(source, "GlassIconButton")
        assertTrue(
            "GlassIconButton 的 label 必须是必填参数（`label: String,`）：\n$header",
            Regex("""label\s*:\s*String\s*,""").containsMatchIn(header),
        )
        assertTrue(
            "GlassIconButton 的 label 不能有默认值 —— 有默认值就意味着可以只画图标不写语义，" +
                "编译器那道理所当然的闸门会重新变成一道需要测试来守的墙：\n$header",
            !Regex("""label\s*:\s*String\s*=""").containsMatchIn(header),
        )
        assertTrue(
            "GlassIconButton 必须把 label 接进 contentDescription（读屏才念得出这是什么钮）",
            functionBody(source, "GlassIconButton").contains("contentDescription = label"),
        )
    }

    @Test
    fun `每个图标枚举都有绘制分支`() {
        val icons = sources["GlassIcons.kt"]
            ?: throw AssertionError("找不到 ui/glass/GlassIcons.kt")

        val kinds = enumConstants(icons, "GlassIconKind")
        assertTrue("没解析到 GlassIconKind 枚举值", kinds.isNotEmpty())

        // 图标已换成 Material Symbols（D-073）：资源映射表从 drawGlassIcon 改名为 drawableOf，
        // 但契约不变 —— 每个枚举值都必须有落点，漏画在界面上是一块空白，肉眼很难发现。
        val body = functionBody(icons, "drawableOf")
        val drawn = Regex("""GlassIconKind\.([A-Za-z]+)\s*->""")
            .findAll(body)
            .map { it.groupValues[1] }
            .toSet()

        assertTrue("drawableOf 里一个分支都没解析到，解析规则可能失效了", drawn.isNotEmpty())

        val missing = kinds.filterNot { it in drawn }
        assertTrue("这些图标没有绘制分支，界面上会是一块空白：$missing", missing.isEmpty())
        assertEquals(
            "drawableOf 引用了不存在的图标种类：${drawn - kinds.toSet()}",
            drawn.size,
            drawn.intersect(kinds.toSet()).size,
        )
    }

    @Test
    fun `界面代码里没有空的读屏文案`() {
        val offenders = sources.filterValues { text ->
            Regex("""contentDescription\s*=\s*"""").containsMatchIn(text)
        }.keys
        assertTrue("这些文件的 contentDescription 是空串，等于什么都没说：$offenders", offenders.isEmpty())
    }

    // ---------------------------------------------------------------- 解析工具

    /**
     * 找 `fun <name>(` 的起始位置。
     *
     * 必须容忍**扩展函数的接收者**：`private fun DrawScope.drawGlassIcon(` 里
     * `fun drawGlassIcon(` 是不存在的，按字面找会直接断言失败（自己踩过一次）。
     */
    private fun declarationStart(source: String, name: String): Int {
        val match = Regex("""fun\s+(?:[A-Za-z0-9_.]+\.)?${Regex.escape(name)}\s*\(""")
            .find(source)
        assertTrue("找不到 fun $name( 的定义（含扩展函数写法）", match != null)
        return match!!.range.first
    }

    private fun functionHeader(source: String, name: String): String {
        val start = declarationStart(source, name)
        var depth = 0
        var i = source.indexOf('(', start)
        while (i < source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return source.substring(start, i + 1)
                }
            }
            i++
        }
        throw AssertionError("fun $name( 的参数列表没有闭合")
    }

    /** 从参数列表之后找第一个 `{`，做括号配对取函数体。 */
    private fun functionBody(source: String, name: String): String {
        val start = declarationStart(source, name)
        val header = functionHeader(source, name)
        val bodyOpen = source.indexOf('{', start + header.length)
        assertTrue("fun $name 没有函数体", bodyOpen >= 0)
        var depth = 0
        var i = bodyOpen
        while (i < source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(bodyOpen, i + 1)
                }
            }
            i++
        }
        throw AssertionError("fun $name 的函数体没有闭合")
    }

    /** 枚举常量名（`enum class X { A, B, }` 里的 A/B），忽略 KDoc 与属性。 */
    private fun enumConstants(source: String, enumName: String): List<String> {
        val bodyStart = source.indexOf("enum class $enumName {")
        assertTrue("找不到 enum class $enumName", bodyStart >= 0)
        val body = source.substring(bodyStart).substringBefore("\n}")
        return Regex("""(?m)^\s{4}([A-Z][A-Za-z]*)\s*,?\s*$""")
            .findAll(body)
            .map { it.groupValues[1] }
            .toList()
    }
}
