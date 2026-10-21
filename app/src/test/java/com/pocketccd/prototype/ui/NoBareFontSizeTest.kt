package com.pocketccd.prototype.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 守住一条规则：**UI 层不许写裸字号**，一律走 `spec.type.*`。
 *
 * 为什么要有这条测试：2026-10-20 的审计发现 `FilmType` 已经定义了字阶，但
 * 25 处裸 `fontSize` 把设置项说明、EXIF 参数、文件大小压到 6.5–9.5sp，且恰好
 * 配了最淡的颜色（`inkGhost` 白 25%，近黑底上 2.17:1，连 WCAG 的 3:1 都不到）。
 * 令牌存在不等于被使用 —— 只有测试能挡住下一次"顺手写个 8sp"。
 *
 * 见 `docs/reviews/2026-10-21-ui-density-change-spec.md`。
 */
class NoBareFontSizeTest {

    @Test
    fun `UI 层不许出现裸字号`() {
        val root = File("src/main/java/com/pocketccd/prototype/ui")
        assertTrue("找不到 UI 源码目录：${root.absolutePath}", root.isDirectory)

        val pattern = Regex("""fontSize\s*=\s*[0-9.]+\s*\.sp""")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            // ui/theme 是 M3 兜底字型（PocketCcdTypography），不在令牌体系内
            .filterNot { it.path.replace('\\', '/').contains("/ui/theme/") }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> pattern.containsMatchIn(line) }
                    .map { (i, line) -> "${file.name}:${i + 1}  ${line.trim()}" }
            }
            .toList()

        assertTrue(
            "UI 层出现裸字号，请改用 spec.type.*（只有 microPrint 可以低于 11sp）：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
