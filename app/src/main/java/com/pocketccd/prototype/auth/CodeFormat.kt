package com.pocketccd.prototype.auth

/**
 * 邀请码的输入归一化与显示格式化。
 *
 * 抽成纯函数是为了能被单测覆盖 —— 这段代码决定"用户抄错一个字母会不会白跑一趟"，
 * 而它在真机上很难穷举验证。
 *
 * ## 字符集
 *
 * 服务端用 **Crockford Base32**（去掉 I / L / O / U）。所以这里把它们当**别名**处理：
 * 用户抄到 `O` 就是 `0`，抄到 `I`、`L` 就是 `1`。这不是"宽容"，是这套编码的标准读法。
 *
 * ## 为什么不在输入框里直接插连字符
 *
 * `BasicTextField` 的 `VisualTransformation` 要自己写 `OffsetMapping`，光标在
 * 第 4 位前后跳错一格是最常见的 bug。这里改成"输入纯 8 位、下面另起一行显示格式化结果"，
 * 光标行为完全由框架负责，代价只是多一行灰字。
 */
object CodeFormat {

    /** 码本体长度（不含 `PCCD-` 前缀与连字符）。必须与服务端 `CODE_LEN` 一致。 */
    const val LENGTH = 8

    private const val PREFIX = "PCCD"

    private val alias = mapOf('O' to '0', 'I' to '1', 'L' to '1')

    /**
     * 把任意粘贴内容收敛成 8 位大写字母数字。
     *
     * 容忍：小写、空格、连字符、全角字符、以及带着 `PCCD-` 前缀整段粘贴。
     *
     * **顺序很重要**：先把所有字母数字收齐、再剥前缀、最后才截断。
     * 若先截断，整段粘贴 `PCCD-7K3M-9QX2` 会得到 `PCCD7K3M` —— 四个前缀字符白占位，
     * 真码只剩一半，用户会以为是码错了。
     */
    fun sanitize(raw: String): String {
        val all = StringBuilder(raw.length)
        for (ch in raw.uppercase()) {
            when {
                ch in '0'..'9' -> all.append(ch)
                ch in 'A'..'Z' -> all.append(alias[ch] ?: ch)
                // 其余（空格、连字符、中文标点、全角符号）一律丢弃
            }
        }
        var body = all.toString()
        // 只在"明显是整段带前缀粘贴"时剥：刚好 8 位时，"PCCD" 可能本身就是码的开头。
        if (body.length > LENGTH && body.startsWith(PREFIX)) body = body.substring(PREFIX.length)
        return body.take(LENGTH)
    }

    /** `XXXX-XXXX`，只用于展示核对；输入框里存的是裸 8 位。 */
    fun grouped(code: String): String =
        if (code.length <= LENGTH / 2) code
        else code.substring(0, LENGTH / 2) + "-" + code.substring(LENGTH / 2)

    /** `PCCD-XXXX-XXXX`，带前缀。卡片上那行灰字用它。 */
    fun display(code: String): String =
        if (code.isEmpty()) "" else "$PREFIX-${grouped(code.padEnd(LENGTH, '·'))}"

    fun isComplete(code: String): Boolean = code.length == LENGTH
}
