package com.pocketccd.prototype.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 邀请码输入归一化。
 *
 * 这段代码决定"用户从聊天记录里抄错一个字母，会不会白跑一趟"，是整条激活链路里
 * 唯一能在本地穷举验证的部分 —— 真机上试不出 10 种抄错方式。
 *
 * 口径来自 Crockford Base32：`O`→`0`，`I`/`L`→`1`。这是标准读法，不是"随便宽容一下"。
 */
class CodeFormatTest {

    @Test
    fun `小写与分隔符被丢弃`() {
        assertEquals("PP1SR5XK", CodeFormat.sanitize("pccd-pp1s r5xk"))
    }

    @Test
    fun `O 归一化成 0`() {
        assertEquals("0", CodeFormat.sanitize("o"))
        assertEquals("0123", CodeFormat.sanitize("O123"))
    }

    @Test
    fun `I 与 L 归一化成 1`() {
        assertEquals("11", CodeFormat.sanitize("IL"))
        assertEquals("12345678", CodeFormat.sanitize("I2345678"))
    }

    @Test
    fun `超出八位直接截断`() {
        assertEquals(CodeFormat.LENGTH, CodeFormat.sanitize("ABCDEFGHJKM").length)
        assertEquals("ABCDEFGH", CodeFormat.sanitize("ABCDEFGHJKM"))
    }

    @Test
    fun `中文标点与全角符号被丢弃`() {
        assertEquals("AB12", CodeFormat.sanitize("A，B。1　2！"))
    }

    @Test
    fun `分组显示是四四`() {
        assertEquals("PP1S-R5XK", CodeFormat.grouped("PP1SR5XK"))
        assertEquals("PP1S", CodeFormat.grouped("PP1S"))
    }

    @Test
    fun `带前缀的完整展示`() {
        assertEquals("PCCD-PP1S-R5XK", CodeFormat.display("PP1SR5XK"))
        assertEquals("", CodeFormat.display(""))
    }

    @Test
    fun `未满八位时用点占位`() {
        assertEquals("PCCD-AB12-····", CodeFormat.display("AB12"))
    }

    @Test
    fun `完整性判定`() {
        assertFalse(CodeFormat.isComplete("PP1SR5X"))
        assertTrue(CodeFormat.isComplete("PP1SR5XK"))
        assertFalse(CodeFormat.isComplete(""))
    }

    @Test
    fun `粘贴整段带前缀的码不会丢字符`() {
        // 这是最常见的失败姿势：用户直接把 "PCCD-7K3M-9QX2" 整段粘进来，
        // 若只截前 8 位就会得到 "PCCD7K3M"（四个前缀字符白占位）。
        assertEquals("7K3M9QX2", CodeFormat.sanitize("PCCD-7K3M-9QX2"))
    }
}
