package com.pocketccd.prototype.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 守住 T1-013 的一条规则：**纯图标可点控件必须自带读屏文案**。
 *
 * 为什么要有这条测试：`ui/film/` 里的快门、闪光灯、网格、相册、换卷、变焦档位全部
 * 是"只画图标/圆点、不写字"的控件。它们视觉上靠位置和颜色可辨，读屏却只会念
 * "按钮"，用户根本不知道按下去会发生什么。这类遗漏**编译不报错、单测不报错、
 * 视觉验收也看不出来**，只有一条盯着源码的静态测试能挡住下一次"顺手加个圆钮"。
 *
 * 做法与 [NoBareFontSizeTest] 一致：不引 Compose 测试依赖，纯文本扫描 + 契约断言。
 *
 * 三个组件的契约（[FilmControls.kt]）：
 * - `FilmRoundButton` / `FilmPanelKey` / `FilmShutter` 的定义必须有
 *   `accessibilityLabel: String = <非空默认>`、`actionLabel: String = <非空默认>`；
 * - 函数体必须把两者分别接到 `contentDescription` 与 `onClickLabel`，并给
 *   `role = Role.Button`（读屏才会报"按钮"而不是"文本"）。
 *
 * ### 白名单：默认值允许是"推导表达式"，但必须每个都能推出非空中文
 *
 * 1. `label`（[FilmPanelKey]）—— 可见丝印文字本身就是最好的描述，默认继承它。
 * 2. `icon.accessibilityLabel(active)` / `icon.actionLabel()`（[FilmRoundButton]）——
 *    图标 + 开关态决定文案（闪光灯要说"已开/已关"）。映射表在 [FilmIcons.kt]，
 *    本测试单独验证它覆盖 `FilmIconKind` 的**每一个枚举值**且每句都是非空中文，
 *    所以"推导默认值"不是逃避检查的后门。
 *
 * 除此之外的默认值一律必须是中文字符串字面量 —— 不许写 `""`、`null`、英文占位。
 */
class InteractiveIconLabelsTest {

    /** 测试工作目录是 `app/`（Gradle 单测约定），和 NoBareFontSizeTest 一致。 */
    private val filmDir = File("src/main/java/com/pocketccd/prototype/ui/film")

    private val sources: Map<String, String> by lazy {
        assertTrue("找不到 ui/film 源码目录：${filmDir.absolutePath}", filmDir.isDirectory)
        filmDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            // 扫描结构（括号/花括号配对）前先剥掉注释：注释里出现 `{ FilmIcon(…) }`
            // 这类示例会让"函数体从第一个 { 开始"的朴素解析跑偏。字符串里的 `//`
            // （例如 URL）会被多剥一点，但本测试只解析参数列表/函数体结构，不影响断言。
            .associate { it.name to stripComments(it.readText()) }
    }

    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", setOf(RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("""//[^\n]*"""), "")

    private fun sourceOfWhateverDefines(name: String): String {
        val hits = sources.filterValues { it.contains("fun $name(") }
        assertEquals("$name 应当恰好在一个文件里定义（多份定义会让语义分叉）", 1, hits.size)
        return hits.values.single()
    }

    /** 三个"纯图标可点控件"及其允许的推导默认值（白名单，理由见类注释）。 */
    private val allowedDynamicDefaults = mapOf(
        "label" to "FilmPanelKey：可见丝印文字即语义，默认继承 label",
        "icon.accessibilityLabel(active)" to "FilmRoundButton：图标+开关态映射，见 FilmIcons.kt（下方逐枚举验证）",
        "icon.actionLabel()" to "FilmRoundButton：图标决定动作提示，见 FilmIcons.kt（下方逐枚举验证）",
    )

    @Test
    fun `纯图标可点控件的定义必须有非空默认语义标签`() {
        listOf("FilmRoundButton", "FilmPanelKey", "FilmShutter").forEach { name ->
            val source = sourceOfWhateverDefines(name)
            val header = functionHeader(source, name)
            val body = functionBody(source, name)

            assertNonEmptyDefault(name, "accessibilityLabel", header)
            assertNonEmptyDefault(name, "actionLabel", header)

            assertTrue(
                "$name 必须把 accessibilityLabel 接进 contentDescription：\n$body",
                body.contains("contentDescription = accessibilityLabel"),
            )
            assertTrue(
                "$name 必须把 actionLabel 接进 onClickLabel（读屏的\"双击即可…\"提示）：\n$body",
                body.contains("onClickLabel = actionLabel"),
            )
            assertTrue(
                "$name 必须给 clickable 传 role = Role.Button：\n$body",
                body.contains("role = Role.Button"),
            )
        }
    }

    /**
     * 图标映射表必须覆盖每一个 `FilmIconKind`，且每句都是非空中文。
     *
     * 这条断言是上面白名单的"抵押物"：允许 `FilmRoundButton` 用推导默认值，
     * 前提是推导函数真的对每种图标都给出了人话。新增图标忘了补文案，这里就红。
     */
    @Test
    fun `图标语义映射覆盖全部图标种类且文案非空`() {
        val icons = sources["FilmIcons.kt"]
            ?: throw AssertionError("找不到 FilmIcons.kt（图标语义映射表应该在那里）")

        val kinds = enumConstants(icons, "FilmIconKind")
        assertTrue("没解析到 FilmIconKind 枚举值", kinds.isNotEmpty())

        mapOf(
            "accessibilityLabel" to functionBody(icons, "FilmIconKind.accessibilityLabel"),
            "actionLabel" to functionBody(icons, "FilmIconKind.actionLabel"),
        ).forEach { (fnName, body) ->
            val branches = whenBranches(body)
            kinds.forEach { kind ->
                val texts = branches[kind]
                    ?: throw AssertionError("$fnName 没有 FilmIconKind.$kind 的分支")
                assertTrue("$fnName 的 $kind 分支没有任何字符串文案", texts.isNotEmpty())
                texts.forEach { text ->
                    assertTrue(
                        "$fnName 的 $kind 文案不能为空：\"$text\"",
                        text.isNotBlank(),
                    )
                    assertTrue(
                        "$fnName 的 $kind 文案必须是中文（读屏是给中文用户听的）：\"$text\"",
                        text.any { it in '\u4e00'..'\u9fff' },
                    )
                }
            }
            val unknown = branches.keys - kinds.toSet()
            assertTrue("$fnName 引用了不存在的图标种类：$unknown", unknown.isEmpty())
        }
    }

    // ---------------------------------------------------------------- 解析工具

    /**
     * 取 `fun <name>(` 到与之配对的 `)`，返回整个参数列表（含括号）。
     *
     * 必须做括号配对而不是找行尾：默认值里可能有函数调用
     * （`icon.accessibilityLabel(active)`），按逗号/行切会把参数切碎。
     */
    private fun functionHeader(source: String, name: String): String {
        val start = source.indexOf("fun $name(")
        assertTrue("找不到 fun $name( 的定义", start >= 0)
        var depth = 0
        var i = start
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

    /** 取函数体（从头括号到配对的尾括号）。表达式体函数返回 `= when { }` 的块。 */
    private fun functionBody(source: String, name: String): String {
        val header = functionHeader(source, name)
        val start = source.indexOf("fun $name(")
        // 必须从**参数列表结束后**再找 `{`：KDoc/行内注释里可能出现花括号示例。
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

    private fun assertNonEmptyDefault(owner: String, param: String, header: String) {
        // 一行一个参数是本仓库的排版约定（ktlint 会在逗号后换行）；按行抓默认值比
        // 写一个"能处理任意表达式的正则"可靠：`icon.accessibilityLabel(active)` 的
        // 括号、`"打开$label"` 的模板都是常规字符。
        val match = Regex("""$param\s*:\s*String\s*=\s*([^\n]+)""").find(header)
        assertNotNull("$owner 缺少 `$param: String = <默认值>` 参数：\n$header", match)
        val raw = match!!.groupValues[1].trim().removeSuffix(",").trim()
        assertTrue("$owner 的 $param 默认值不能为空/占位：`$raw`", raw.isNotEmpty())
        assertTrue("$owner 的 $param 默认值不能是空串或 null：`$raw`", raw != "\"\"" && raw != "null")

        val literal = Regex("""^"([^"]*)"$""").matchEntire(raw)
        if (literal != null) {
            val text = literal.groupValues[1]
            assertTrue("$owner 的 $param 默认文案不能为空：`$raw`", text.isNotBlank())
            assertTrue(
                "$owner 的 $param 默认文案必须是中文：`$raw`",
                text.any { it in '\u4e00'..'\u9fff' },
            )
        } else {
            assertTrue(
                "$owner 的 $param 默认值是推导表达式 `$raw`，但没有登记在类注释的白名单里。" +
                    "新增推导必须在那里写明理由，否则请直接用中文字符串字面量。",
                allowedDynamicDefaults.containsKey(raw),
            )
        }
    }

    /** 枚举常量名（`enum class X { A, B, }` 里的 A/B），忽略 KDoc 与属性。 */
    private fun enumConstants(source: String, enumName: String): List<String> {
        val bodyStart = source.indexOf("enum class $enumName {")
        assertTrue("找不到 enum class $enumName", bodyStart >= 0)
        val tail = source.substring(bodyStart)
        val body = tail.substringBefore("\n}")
        return Regex("""(?m)^\s{4}([A-Z][A-Za-z]*)\s*,?\s*$""")
            .findAll(body)
            .map { it.groupValues[1] }
            .toList()
    }

    /** `when (this) { FilmIconKind.X -> "..." }` 的分支：枚举值 → 该分支里的所有字符串。 */
    private fun whenBranches(body: String): Map<String, List<String>> {
        val pattern = Regex(
            """FilmIconKind\.([A-Za-z]+)\s*->\s*(.+?)(?=\n\s*FilmIconKind\.|\n\s*\})""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        return pattern.findAll(body).associate { match ->
            val strings = Regex("\"([^\"]*)\"")
                .findAll(match.groupValues[2])
                .map { it.groupValues[1] }
                .toList()
            match.groupValues[1] to strings
        }
    }
}
